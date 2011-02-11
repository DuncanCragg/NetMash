package jungle.forest;

import java.util.*;
import java.util.concurrent.*;

import jungle.platform.*;
import jungle.lib.*;

/** Holds object cache; manages notify and observe; runs evaluate; sorts out
  * persistence and networking and cacheing-in objects; drives retry and timeout.
  */
public class FunctionalObserver implements Module {

    static public FunctionalObserver funcobs;

    private Persistence persistence;
    private HTTP http;

    private ConcurrentHashMap<String,WebObject> cache = new ConcurrentHashMap<String,WebObject>();

    /** Constructor called by Kernel. */
    public FunctionalObserver(){
        funcobs = this;
    }

    public void run(){
        persistence = new Persistence();
        http        = new HTTP();
        evaluateCache();
        System.out.println("FunctionalObserver: Module initialised");
    }

    // -------------------------------

    private void evaluateCache(){
        Enumeration<WebObject> e = cache.elements();
        while(e.hasMoreElements()){
           WebObject w=e.nextElement();
           if(w.isLocal()) evaluatable(w);
        }
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

    void dropNotifiesNotNeeded(WebObject w){
        for(String olduid: w.observe){
            if(!w.newobserve.contains(olduid)){
                WebObject wo = cacheGet(olduid);
                wo.notify.remove(w.uid);
                persistence.save(wo);
            }
        }
    }

    void cacheSaveAndEvalSpawned(WebObject w){
        for(WebObject n: w.spawned){
            cachePut(n);
            persistence.save(n);
            evaluatable(n);
        }
    }

    void saveAndNotifyUpdated(WebObject notifier){
        persistence.save(notifier);
        notifyUpdated(notifier);
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
    void httpNotify(WebObject w){     // must check it's not one of ours!
        WebObject s=cacheGet(w.uid);  // must look in db
        if(s.etag>=w.etag) return;
        cachePut(w);
        mergePrevIntoCurrent(s,w);
        saveAndNotifyUpdated(w);
    }

    private void handleShell(WebObject s){
        if(s.shellstate==ShellStates.NEW){
            s.shellstate = ShellStates.TRYDB;
            WebObject w=persistence.cache(s.uid);
            if(w!=null){
                mergePrevIntoCurrent(s,w);
                saveAndNotifyUpdated(w);
                if(w.isLocal()){
                    w.handleEval();
                }
                else{
                    if(!w.notify.isEmpty())    http.poll(w);
                    if(!w.alertedin.isEmpty()) http.push(w);
                }
            }
            else{
                s.shellstate = ShellStates.TRYREMOTE;
                if(!s.notify.isEmpty())    http.pull(s);
                if(!s.alertedin.isEmpty()) http.push(s); // need to snapshot alertedin
            }
        }
    }

    private void mergePrevIntoCurrent(WebObject s, WebObject w){
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

    static public void whereAmI(){
        try{ throw new Exception(); } catch(Exception e){ e.printStackTrace(); }
    }

    // -------------------------------

}

