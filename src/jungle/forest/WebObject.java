package jungle.forest;

import java.util.*;
import java.util.concurrent.*;
import java.text.*;
import java.util.regex.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.net.*;

import jungle.platform.Kernel;
import jungle.lib.*;
import jungle.forest.FunctionalObserver;

enum ShellStates { NEW, TRYDB, TRYREMOTE, READY }

/** WebObject: holds a JSON object and an evaluate() call.
  */
public class WebObject {

    //----------------------------------

    public  ShellStates shellstate = ShellStates.READY;
    private boolean isLocal = true;

    public  FunctionalObserver funcobs;

    public  String uid;
    public  int    etag=0;
    public  int    maxAge= -1;

    public  JSON publicState=null;
    public  JSON updatingState=null;

    private boolean copyshallow = true;
    public  boolean statemod = false;
    public  boolean obsalmod = false;

    public  HashSet<String>                   newalert   = new HashSet<String>();
    public  ConcurrentSkipListSet<String>     notify     = new ConcurrentSkipListSet<String>();
    public  ConcurrentLinkedQueue<Notifiable> httpnotify = new ConcurrentLinkedQueue<Notifiable>();
    public  HashSet<String>                   newobserve = new HashSet<String>();
    public  HashSet<String>                   observe    = new HashSet<String>();
    public  ConcurrentSkipListSet<String>     alertedin  = new ConcurrentSkipListSet<String>();
    public  ConcurrentSkipListSet<String>     alerted    = null;
    public  HashSet<WebObject>                spawned    = new HashSet<WebObject>();

    //----------------------------------

    public WebObject(){}

    /** Construct WebObject after null ctor from Persistence. */
    public void construct(JSON json){
        funcobs = FunctionalObserver.funcobs;
        uid     =          json.stringPathN("%uid");      json.removePath("%uid");
        etag    =          json.intPathN(   "%etag");     json.removePath("%etag");
        listToSet(notify,  json.listPathN(  "%notify"));  json.removePath("%notify");
        listToSet(observe, json.listPathN(  "%observe")); json.removePath("%observe");
        publicState = json;
        updatingState = publicState;
    }

    /** Create WebObject from HTTP. */
    public WebObject(JSON json, String httpUID, String httpEtag, String httpMaxAge){
        funcobs = FunctionalObserver.funcobs;
        int httpetag   = (httpEtag  !=null)? Integer.parseInt(httpEtag): 0;
        int httpmaxage = (httpMaxAge!=null)? Integer.parseInt(httpMaxAge): 0;
        uid     = (httpUID   !=null)? httpUID:    json.stringPathN("%uid");     json.removePath("%uid");
        etag    = (httpEtag  !=null)? httpetag:   json.intPathN(   "%etag");    json.removePath("%etag");
        maxAge  = (httpMaxAge!=null)? httpmaxage: json.intPathN(   "%max-age"); json.removePath("%max-age");
        publicState = json;
        updatingState = publicState;
        isLocal = false;
    }

    /** Create a shell object. */
    public WebObject(UID shelluid){
        funcobs = FunctionalObserver.funcobs;
        uid = shelluid.toString();
        shellstate = ShellStates.NEW;
    }

    /** For creating new WebObjects inside evaluate(). */
    public WebObject(String json){
        funcobs = FunctionalObserver.funcobs;
        uid = UID.generateUID();
        etag = 1;
        publicState = new JSON(json);
        updatingState = publicState;
    }

    public boolean isShell(){
        return shellstate != ShellStates.READY;
    }

    public boolean isLocal(){
        if(!isLocal) return false;
        return !uid.startsWith("http://");
    }

    /* --- Evaluate API ----------------------------------- */

    /** If path crosses over a link, fetch WebObject with
      * that uid, if in the cache.
      * Start or confirm the observation (will be dropped
      * if you don't do this). If not in cache, return
      * null and fire off whatever it takes to get it.
      */

    /** Get String at this path in the JSON content. */
    public String content(String path){
        String s=null;
        try{ s=updatingState.stringPath(path);
        }catch(PathOvershot po){
            while(true){
                WebObject w = observeIfUID(po.leaf);
                if(w==null)break;
                try{ s = w.publicState.stringPath(po.path); break;
                }catch(PathOvershot po2){ po=po2; }
            }
        }
        return s;
    }

    /** Test if String at path is value. */
    public boolean contentIs(String path, String val){
        String s = content(path);
        if(s==null) return val==null;
        return s.equals(val);
    }

    /** Set String at this path in the JSON content. */
    public void content(String path, String val){
        doCopyOnWrite(path);
        statemod = updatingState.stringPath(path, val) || statemod;
    }

    /** Get int at this path in the JSON content. */
    public int contentInt(String path){
        int i=0;
        try{ i=updatingState.intPath(path);
        }catch(PathOvershot po){
            while(true){
                WebObject w = observeIfUID(po.leaf);
                if(w==null)break;
                try{ i = w.publicState.intPath(po.path); break;
                }catch(PathOvershot po2){ po=po2; }
            }
        }
        return i;
    }

    /** Set int at this path in the JSON content. */
    public void contentInt(String path, int val){
        doCopyOnWrite(path);
        statemod = updatingState.intPath(path, val) || statemod;
    }

    /** Double value found at path. */
    public double contentDouble(String path){
        double d=0;
        try{ d=updatingState.doublePath(path);
        }catch(PathOvershot po){
            while(true){
                WebObject w = observeIfUID(po.leaf);
                if(w==null)break;
                try{ d = w.publicState.doublePath(po.path); break;
                }catch(PathOvershot po2){ po=po2; }
            }
        }
        return d;
    }

    /** Set double value at path. */
    public void contentDouble(String path, double val){
        doCopyOnWrite(path);
        statemod = updatingState.doublePath(path, val) || statemod;
    }

    /** Get list at path. */
    public LinkedList contentList(String path){
        LinkedList l=null;
        try{ l = updatingState.listPath(path);
        }catch(PathOvershot po){
            while(true){
                WebObject w = observeIfUID(po.leaf);
                if(w==null)break;
                try{ l = w.publicState.listPath(po.path); break;
                }catch(PathOvershot po2){ po=po2; }
            }
        }
        return l;
    }

    /** Get clone copy of list at path. */
    public LinkedList contentListClone(String path){
        LinkedList list=contentList(path);
        if(list==null) return null;
        return (LinkedList)list.clone();
    }

    /** Returns true if list at path contains the value. */
    public boolean contentListContains(String path, Object val){
        LinkedList list=contentList(path);
        if(list==null) return false;
        return list.contains(val);
    }

    /** Set list at path. */
    public void contentList(String path, LinkedList val){
        doCopyOnWrite(path);
        statemod = updatingState.listPath(path, val) || statemod;
    }

    /** Add the value onto the list at the path. */
    public void contentListAdd(String path, Object val){
        doCopyOnWrite(path);
        statemod = updatingState.listPathAdd(path, val) || statemod;
    }

    /** Given a UID, drill into its content as String if 
      * available, else return null.
      */
    public String contentOf(String linkuid, String path){
        WebObject w = observing(linkuid);
        if(w==null) return null;
        return w.content(path);
    }

    /** Set this object up to notify the object at this uid.
      * That object may or may not be observing us already.
      * Won't notify this time unless state also changed.
      */
    public void notifying(String alertuid){
        obsalmod = true;
        newalert.add(alertuid);
    }

    /** List of objects whose state was pushed here without
      * our observing them. If observing() is called on them,
      * this pushing continues without them appearing here
      * any more. Otherwise the push is rejected and removed.
      */
    public AbstractSet<String> alerted(){
        return alerted;
    }

    /** Wrap new WebObject in this to return its UID and set 
      * it up for later evaluation. */
    public String spawn(WebObject w){
        obsalmod = true;
        spawned.add(w);
        return UID.toURL(w.uid);
    }

    /** Use this when running from an interface or I/O callback. */
    public class Evaluator{
        public Evaluator(WebObject w){
            synchronized(w){
                w.evalPre();
                evaluate();
                w.evalPost();
            }
        }
        public void evaluate(){}
    }

    /** Call to reset all changes. */
    public void rollback(){
        statemod = false;
        updatingState = publicState;
        obsalmod = false;
        newalert.clear();
        newobserve.clear();
    }

    /** Simple logger for ya. */
    public void log(Object o){ FunctionalObserver.log(o); }

    /* ---------------------------------------------------- */

    private WebObject observing(String observeduid){
        if(!UID.isUID(observeduid)) return null;
        obsalmod = true;
        return funcobs.observing(this, observeduid);
    }

    private WebObject observeIfUID(Object leaf){
        if(!(leaf instanceof String)) return null;
        if(!UID.isUID((String)leaf)) return null;
        return observing((String)leaf);
    }

    private void doCopyOnWrite(String path){
        boolean pathshallow = !path.contains(":");
        if(updatingState==publicState){
            copyshallow = true;
            if(pathshallow) updatingState=new JSON(publicState);
            else{           updatingState=new JSON(publicState.toString());
                            copyshallow = false;
            }
        }
        else{
            if(pathshallow || !copyshallow) return;
            else{         updatingState=new JSON(updatingState.toString());
                          copyshallow = false;
            }
        }
    }

    /* ---------------------------------------------------- */

    void handleEval(){
        evalPre();
        evaluate();
        evalPost();
    }

    void evalPre(){
        obsalmod = false;
        statemod = false;
        newobserve.clear();
        newalert.clear();
        alerted = alertedin;
        alertedin = new ConcurrentSkipListSet<String>();
        spawned.clear();
    }

    public void evaluate(){
        log("WebObject: Evaluate called on WebObject:\n"+this);
    }

    void evalPost(){
        observe.addAll(alerted);
        if(obsalmod){
            notify.addAll(newalert);
            funcobs.dropNotifiesNotNeeded(this);
            observe = newobserve;
            funcobs.cacheSaveAndEvalSpawned(this);
        }
        if(statemod){
            publicState = updatingState;
            etag++;  // should be atomic with state!
            funcobs.saveAndNotifyUpdated(this);
        }
        if(obsalmod && !statemod){
            funcobs.saveAndAlertFirstTime(this);
        }
    }

    /* ---------------------------------------------------- */

    public String toString(){
        if(isShell()) return "{ \"%uid\": \""+uid+
                         "\",\n \"%notify\": "+setToListString(notify)+
                           ",\n \"%alertedin\": "+setToListString(alertedin)+
                           ",\n \"%state\": \""+shellstate+"\"\n}\n";
        String r = publicState.toString(
                                "\"%uid\": \""+uid+
                       "\",\n    \"%etag\": "+etag+
                         ",\n    \"%notify\": "+setToListString(notify)+
                         ",\n    \"%observe\": "+setToListString(observe)+
                         ",\n    \"%class\": \""+this.getClass().toString().substring(6)+
                       "\",\n")+"\n";
        return r;
    }

    public String toString(HashSet<String> percents){
        String r = publicState.toString(
                       (percents.contains("%uid")?     "\"%uid\": \""  +uid+ "\",\n    ": "")+
                       (percents.contains("%etag")?    "\"%etag\": "   +etag+  ",\n    ": "")+
                       (percents.contains("%max-age")? "\"%max-age\": "+maxAge+",\n    ": "")
                   )+"\n";
        return r;
    }

    private String setToListString(Iterable<String> set){
        Iterator<String> i = set.iterator();
        if(!i.hasNext()) return "[]";
        String r = "[";
        do{ r+=" \""+i.next()+"\","; }while(i.hasNext());
        r=r.substring(0, r.length()-1);
        r+=" ]";
        return r;
    }

    private void listToSet(AbstractSet<String> set, LinkedList list){
        Iterator i = list.iterator();
        while(i.hasNext()) set.add((String)i.next());
    }
}

