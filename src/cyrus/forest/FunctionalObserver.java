package cyrus.forest;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import cyrus.platform.*;
import cyrus.lib.*;

import static cyrus.lib.Utils.*;

/** Holds object cache; manages notify and observe; runs evaluate; sorts out
  * persistence and networking and cacheing-in objects; drives retry and timeout.
  */
public class FunctionalObserver implements Module {

    static public boolean tryOutEagerAlertingForABit=true;

    static public FunctionalObserver funcobs;

    public Persistence persistence;
    public HTTP http;

    private InputStream      topdbis=null;
    private FileOutputStream topdbos=null;

    private ConcurrentHashMap<String,WebObject> cache = new ConcurrentHashMap<String,WebObject>();
    private CopyOnWriteArraySet<String>         polling = new CopyOnWriteArraySet<String>();

    // -------------------------------

    public FunctionalObserver(){
        funcobs = this;
    }

    public FunctionalObserver(InputStream topdbis, FileOutputStream topdbos){
        funcobs = this;
        this.topdbis=topdbis;
        this.topdbos=topdbos;
    }

    public void run(){
        persistence = new Persistence(topdbis, topdbos);
        http        = new HTTP();
        log("FunctionalObserver: initialised; evaluating cached objects");
        startPollingThread();
        evaluateCache();
    }

    // -------------------------------

    private void evaluateCache(){
        Enumeration<WebObject> e = cache.elements();
        while(e.hasMoreElements()){
           WebObject w=e.nextElement();
           if(w.isLocal()) evaluatable(w);
        }
    }

    private Thread pollingThread=null;
    boolean ispolling=true;
    boolean isshortpolling=false;
    boolean visible=false;
    private void startPollingThread(){
        visible=Kernel.config.isAtPathN("network:host");
        pollingThread=new Thread(){ public void run(){
            while(ispolling){
                Kernel.sleep(10000);
                HashSet<String> longPollURLs=new HashSet<String>();
                for(String uid: polling){
                    WebObject w=cacheGet(uid);
                    if(w.isShell()){ polling.remove(uid); continue; }
                    if(w.notify.isEmpty()) continue;
                    if(doLongPoll(w)) longPollURLs.add(w.cacheNotify);
                    else { if(isshortpolling) http.poll(w); }
                }
                http.longpoll(longPollURLs);
            }
        }}; pollingThread.start();
    }

    private void addToPolling(WebObject w){
        polling.add(w.uid);
        if(doLongPoll(w)) pollingThread.interrupt();
    }

    private boolean doLongPoll(WebObject w){
        return (!visible && w.cacheNotify!=null);
    }

    WebObject cacheGet(String uid){
        synchronized(cache){
            WebObject w = cache.get(uid);
            if(w!=null) return w;
            else        return cachePut(new WebObject(new UID(uid)));
        }
    }

    WebObject cachePut(WebObject w){
        if(w==null) return null;
        cache.put(w.uid, w);
        return w;
    }

    WebObject cacheOrPersistenceGet(String uid){
        synchronized(cache){
            WebObject w=cache.get(uid);
            if(w!=null) return w;
            w=persistence.cache(uid);
            if(w==null) return null;
            cache.put(uid, w);
            w.handleEval();
            return w;
        }
    }

    void dumpCache(){
        log("-------------- dump of cache ------------");
        for(Map.Entry<String,WebObject> entry: cache.entrySet()){
            log(entry.getKey()+"=>"+entry.getValue().uid);
        }
        log("-----------------------------------------");
    }

    // -------------------------------

    void evaluatable(WebObject w){
        if(w.evaluatable) return;
        w.evaluatable=true;
        Kernel.threadObject(w);
    }

    /** Module Interface; callback from Kernel.
      * Object o is locked. */
    public void threadedObject(Object o){
        WebObject w = (WebObject)o;
        w.evaluatable=false;
        if(!w.isShell()) w.handleEval();
        else             handleShell(w);
    }

    // -------------------------------

    void dropNotifies(WebObject w){
        for(String remuid: w.remalert){
            if(!cacheGet(remuid).isLocal()) w.notify.remove(remuid);
        }
    }

    void setCurrentNotifyAndObserve(WebObject w){
        if(!w.refreshobserves){
            for(String olduid: w.observe){
                if(!w.newobserve.contains(olduid)){
                    WebObject wo = cacheGet(olduid);
                    wo.notify.remove(w.uid);
                    if(!wo.isShell()) persistence.save(wo);
                }
            }
            w.observe = w.newobserve;
        }
        else w.observe.addAll(w.newobserve);
    }

    public void cacheSaveAndEvaluate(WebObject w){
        cacheSaveAndEvaluate(w, false);
    }

    public void cacheSaveAndEvaluate(WebObject w, boolean savehome){
        cachePut(w);
        if(savehome){ http.setHomeCN(w); notifyUpdatedOnAThread(w); }
        persistence.save(w);
        evaluatable(w);
    }

    public void setCacheNotifyAndSaveConfig(WebObject user){
        String cn="c-n-"+user.uid.substring(4);
        http.setCacheNotify(cn);
        WebObject cyrusconfig = new WebObject(
              "{   \"persist\": { \"preload\": [ \""+user.uid+"\" ] }, \n"+
              "    \"network\": { \"cache-notify\": \""+cn+"\"}\n"+
              "}");
        cyrusconfig.uid="cyrusconfig";
        persistence.save(cyrusconfig);
    }

    public void hereIsTheConfigBack(JSON cyrusconfig){
        http.setCacheNotify(cyrusconfig.stringPathN("network:cache-notify"));
    }

    void evalAndPersistSpawned(WebObject w){
        for(WebObject n: w.spawned){
            evaluatable(n);
            persistence.save(n);
            notifyUpdated(n, true);
        }
    }

    void saveAndNotifyUpdated(WebObject notifier, boolean realupdate){
        persistence.save(notifier);
        notifyUpdated(notifier, realupdate);
        notifyHTTPUpdated(notifier);
    }

    void saveAndAlertFirstTime(WebObject notifier){
        persistence.save(notifier);
        alertFirst(notifier);
    }

    private void notifyUpdated(WebObject notifier, boolean realupdate){
        for(String notifieduid: notifier.notify){
            WebObject notified = cacheGet(notifieduid);
            if(tryOutEagerAlertingForABit || !notified.observe.contains(notifier.uid)){
                notified.alertedin.add(notifier.uid);
            }
            evaluatableOrPush(notified, realupdate);
        }
    }

    private void notifyUpdatedOnAThread(final WebObject notifier){
        new Thread(){ public void run(){ notifyUpdated(notifier, true); } }.start();
    }

    private void notifyHTTPUpdated(WebObject notifier){
        for(Notifiable notified: notifier.httpnotify){
            notified.notify(notifier);
        }
        notifier.httpnotify.clear();
    }

    private void alertFirst(WebObject notifier){
        for(String alertuid: notifier.newalert){
            WebObject alerted = cacheGet(alertuid);
            if(tryOutEagerAlertingForABit || !alerted.observe.contains(notifier.uid)){
                alerted.alertedin.add(notifier.uid);
                evaluatableOrPush(alerted, true);
            }
        }
    }

    private void evaluatableOrPush(WebObject notified, boolean realupdate){
        if(notified.isLocal())           {                evaluatable(notified);   } else
        if(notified.isAsymmetricCN())    { if(realupdate) http.longpush(notified); } else
        if(notified.isAsymmetricRemote()){ if(realupdate) http.longpush(notified); }
        else                             { if(realupdate) http.push(notified);     }
    }

    WebObject observing(WebObject observer, String observeduid, boolean tempObserve){
        if(!tempObserve) observer.newobserve.add(observeduid);
        WebObject observed = cacheGet(observeduid);
        boolean changed=false;
        if(!tempObserve) changed=observed.notify.add(observer.uid);
        if(!observed.isShell()){
            if(changed) persistence.save(observed);
            return observed;
        }
        evaluatable(observed);
        return null;
    }

    // GET rq and POST rs: send object out when it's ready
    WebObject httpObserve(Notifiable observer, String observeduid, String cn){
        WebObject observed = cacheGet(observeduid);
        if(cn!=null) observed.notify.add(cn);
        if(!observed.isShell()){
            if(cn!=null) persistence.save(observed);
            return observed;
        }
        observed.httpnotify.add(observer);
        evaluatable(observed);
        return null;
    }

    // POST rq and GET rs
    String httpNotify(WebObject w){   // must check it's not one of ours being forged!
        String location=null;         // e.g. put flag on those given Location
        WebObject s=cacheGet(w.uid);  // must look in db
        if(!w.isVisibleRemote() && s.isShell()) location=UID.toURL(w.uid);
        if(w.etag>0 && !(w.etag>s.etag) && w.notify.isEmpty()){ log(":\n"+w+"\nnot newer than:\n"+s+"\n"); return location; }
        cachePut(w);
        transferNotifyAndAlerted(s,w);
        if(w.isVisibleRemote()) addToPolling(w);
        saveAndNotifyUpdated(w, !s.isShell());
        return location;
    }

    private void handleShell(WebObject s){
        if(s.shellstate==ShellState.FINDING){
            if(inCache(s) || inPersistence(s) || inRemote(s)) return;
            log("Object not found locally and can't get remotely:\n"+s);
            s.shellstate = ShellState.NOTFOUND;
        }
        if(s.shellstate==ShellState.NOTFOUND){
            notifyHTTPUpdated(s);
        }
    }

    private boolean inCache(WebObject s){
        WebObject w=cacheGet(s.uid);
        if(w==s) return false;
        transferNotifyAndAlerted(s,w);
        saveAndNotifyUpdated(w, false);
        return true;
    }

    private boolean inPersistence(WebObject s){
        WebObject w=persistence.cache(s.uid);
        if(w==null) return false;
        cachePut(w);
        transferNotifyAndAlerted(s,w);
        saveAndNotifyUpdated(w, false);
        if(w.isLocal()){
            w.handleEval();
        }
        else{
            if(!w.alertedin.isEmpty()) http.push(w);
            if(!w.notify.isEmpty())  { http.poll(w); addToPolling(w); }
        }
        return true;
    }

    private boolean inRemote(WebObject s){
        s.shellstate = ShellState.TRYREMOTE;
        if(!s.isVisibleRemote())    return false;
        if(!s.httpnotify.isEmpty()) return false;
        if(!s.alertedin.isEmpty())  if(!http.push(s)) return false;
        if(!s.notify.isEmpty())     if(!http.poll(s)) return false;
        return true; // need to snapshot alertedin
    }

    private void transferNotifyAndAlerted(WebObject s, WebObject w){
        w.notify.addAll(s.notify);
        w.httpnotify.addAll(s.httpnotify);
        for(String notifieruid: s.alertedin){
            if(tryOutEagerAlertingForABit || !w.observe.contains(notifieruid)){
                w.alertedin.add(notifieruid);
            }
        }
    }

    // -------------------------------

    static public void log(Object o){ Utils.log(o); }

    // -------------------------------

}

