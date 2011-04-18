package netmash.forest;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import netmash.platform.*;
import netmash.lib.*;

/** Holds object cache; manages notify and observe; runs evaluate; sorts out
  * persistence and networking and cacheing-in objects; drives retry and timeout.
  */
public class FunctionalObserver implements Module {

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
    private void startPollingThread(){
        pollingThread=new Thread(){ public void run(){
            boolean visible=Kernel.config.isAtPathN("network:host");
            while(ispolling){
                Kernel.sleep(1000);
                HashSet<String> cachenotifies=new HashSet<String>();
                for(String uid: polling){
                    WebObject w=cacheGet(uid);
                    if(w.isShell()){ polling.remove(uid); continue; }
                    if(w.notify.isEmpty()) continue;
                    if(visible || w.cachenotify==null) http.poll(w);
                    else cachenotifies.add(w.cachenotify);
                }
                http.longpoll(cachenotifies);
            }
        }}; pollingThread.start();
    }

    private void addToPolling(WebObject w){
        if(!w.uid.startsWith("http://")) return;
        polling.add(w.uid);
        pollingThread.interrupt();
    }

    WebObject cacheGet(String uid){
        synchronized(cache){
            WebObject w = cache.get(uid);
            if(w!=null) return w;
            else        return cachePut(new WebObject(new UID(uid)));
        }
    }

    WebObject cachePut(WebObject w){
        cache.put(w.uid, w);
        return w;
    }

    // -------------------------------

    void evaluatable(WebObject w){
        Kernel.threadObject(w);
    }

    /** Module Interface; callback from Kernel.
      * Object o is locked. */
    public void threadedObject(Object o){
        WebObject w = (WebObject)o;
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

    void cacheAndSaveSpawned(WebObject w){
        for(WebObject n: w.spawned){
            cachePut(n);
            persistence.save(n);
        }
    }

    void evalSpawned(WebObject w){
        for(WebObject n: w.spawned){
            evaluatable(n);
        }
    }

    void saveAndNotifyUpdated(WebObject notifier){
        persistence.save(notifier);
        notifyUpdated(notifier);
        notifyHTTPUpdated(notifier);
    }

    void saveAndAlertFirstTime(WebObject notifier){
        persistence.save(notifier);
        alertFirst(notifier);
    }

    private void notifyUpdated(WebObject notifier){
        for(String notifieduid: notifier.notify){
            WebObject notified = cacheGet(notifieduid);
            if(!notified.observe.contains(notifier.uid)){
                notified.alertedin.add(notifier.uid);
            }
            if(notified.isLocal()) evaluatable(notified);
            else http.push(notified);
        }
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
            if(!alerted.observe.contains(notifier.uid)){
                alerted.alertedin.add(notifier.uid);
                if(alerted.isLocal()) evaluatable(alerted);
                else http.push(alerted);
            }
        }
    }

    WebObject observing(WebObject observer, String observeduid){
        observer.newobserve.add(observeduid);
        WebObject observed = cacheGet(observeduid);
        observed.notify.add(observer.uid);
        if(!observed.isShell()){
            persistence.save(observed);
            return observed;
        }
        evaluatable(observed);
        return null;
    }

    // GET rq and POST rs: send object out when it's ready
    WebObject httpObserve(Notifiable observer, String observeduid){
        WebObject observed = cacheGet(observeduid);
        if(!observed.isShell()) return observed;
        observed.httpnotify.add(observer);
        evaluatable(observed);
        return null;
    }

    // POST rq and GET rs
    String httpNotify(WebObject w){   // must check it's not one of ours!
        String location=null;
        WebObject s=cacheGet(w.uid);  // must look in db
        if(w.uid.startsWith("uid-") && s.isShell()) location=UID.toURL(w.uid);
        if(s.etag>=w.etag){ log("Old content:\n"+w+"\nIncoming for:\n"+s+"\n"); return location; }
        cachePut(w);
        transferNotifyAndAlerted(s,w);
        addToPolling(w);
        saveAndNotifyUpdated(w);
        return location;
    }

    private void handleShell(WebObject s){
        if(s.shellstate!=ShellState.NEW) return;
        if(inCache(s) || inPersistence(s) || inRemote(s)) return;
    }

    private boolean inCache(WebObject s){
        WebObject w=cacheGet(s.uid);
        if(w==s) return false;
        transferNotifyAndAlerted(s,w);
        saveAndNotifyUpdated(w);
        return true;
    }

    private boolean inPersistence(WebObject s){
        s.shellstate = ShellState.TRYDB;
        WebObject w=persistence.cache(s.uid);
        if(w==null) return false;
        transferNotifyAndAlerted(s,w);
        saveAndNotifyUpdated(w);
        if(w.isLocal()){
            w.handleEval();
        }
        else{
            if(!w.notify.isEmpty())    http.poll(w);
            if(!w.alertedin.isEmpty()) http.push(w);
        }
        return true;
    }

    private boolean inRemote(WebObject s){
        s.shellstate = ShellState.TRYREMOTE;
        if(!s.httpnotify.isEmpty()){ log("GET of object not local: "+s.uid); notifyHTTPUpdated(s); }
        if(!s.notify.isEmpty())    http.pull(s);
        if(!s.alertedin.isEmpty()) http.push(s); // need to snapshot alertedin
        return true;
    }

    private void transferNotifyAndAlerted(WebObject s, WebObject w){
        w.notify.addAll(s.notify);
        w.httpnotify.addAll(s.httpnotify);
        for(String notifieruid: s.alertedin){
            if(!w.observe.contains(notifieruid)){
                w.alertedin.add(notifieruid);
            }
        }
    }

    // -------------------------------

    static public final boolean enableLogging=true;

    static public void log(Object o){
        log(enableLogging, o);
    }
        
    static public void log(boolean doit, Object o){
        if(!doit) return;
        String thread=Thread.currentThread().toString();
        System.out.println("---"+Kernel.config.stringPathN("name")+"---"+thread+"-----------\n"+o);
    }

    static public void whereAmI(String message){
        try{ throw new Exception(); } catch(Exception e){ log(message+": "+Arrays.asList(e.getStackTrace())); }
    }

    // -------------------------------

}

