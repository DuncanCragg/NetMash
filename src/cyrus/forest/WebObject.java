package cyrus.forest;

import java.util.*;
import java.util.concurrent.*;
import java.text.*;
import java.util.regex.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.net.*;

import cyrus.platform.Kernel;
import cyrus.lib.*;
import cyrus.forest.FunctionalObserver;

import static cyrus.lib.Utils.*;

enum ShellState { FOUND, FINDING, TRYREMOTE, NOTFOUND }

/** WebObject: holds a JSON object and an evaluate() call.
  */
public class WebObject {

    //----------------------------------

    public  FunctionalObserver funcobs;

    public  ShellState shellstate = ShellState.FOUND;
    public  boolean evaluatable = false;

    public  String uid;
    public  String url=null;
    public  int    etag=0;
    public  int    maxAge=0;
    public  String cacheNotify;

    public  JSON publicState=null;
    public  JSON updatingState=null;

    private boolean copyshallow = true;
    private LinkedList<String> tempPaths = new LinkedList<String>();
    public  boolean statemod = false;
    public  boolean obsalmod = false;
    public  boolean refreshobserves = false;
    public  boolean nopersist = false;

    HashSet<String>                   newalert   = new HashSet<String>();
    HashSet<String>                   remalert   = new HashSet<String>();
    CopyOnWriteArraySet<String>       notify     = new CopyOnWriteArraySet<String>();
    ConcurrentLinkedQueue<Notifiable> httpnotify = new ConcurrentLinkedQueue<Notifiable>();
    HashSet<String>                   newobserve = new HashSet<String>();
    HashSet<String>                   observe    = new HashSet<String>();
    CopyOnWriteArraySet<String>       alertedin  = new CopyOnWriteArraySet<String>();
    CopyOnWriteArraySet<String>       alerted    = null;
    HashSet<WebObject>                spawned    = new HashSet<WebObject>();

    //----------------------------------

    public WebObject(){}

    /** Construct WebObject after null ctor from Persistence. */
    public void construct(JSON json){
        funcobs = FunctionalObserver.funcobs;
        uid     =          json.stringPathN("UID");          json.removePath("UID");
        url     =          json.stringPathN("URL");          json.removePath("URL");
        etag    =          json.intPathN(   "Version");         json.removePath("Version"); if(etag==0) etag=1;
        maxAge  =          json.intPathN(   "Max-Age");      json.removePath("Max-Age");
        listToSet(notify,  json.listPathN(  "Notify"));      json.removePath("Notify");
        listToSet(observe, json.listPathN(  "Observe"));     json.removePath("Observe");
        cacheNotify =      json.stringPathN("Cache-Notify"); json.removePath("Cache-Notify");
        publicState = json;
        updatingState = publicState;
    }

    /** Create WebObject from HTTP. */
    public WebObject(JSON json, String httpUID, String httpReqURL, String httpETag, String httpMaxAge, String httpCacheNotify, String httpNotify){
        funcobs = FunctionalObserver.funcobs;
        int httpetag=0;   try{ httpetag   = Integer.parseInt(httpETag);   }catch(Throwable t){ httpETag  =null; }
        int httpmaxage=0; try{ httpmaxage = Integer.parseInt(httpMaxAge); }catch(Throwable t){ httpMaxAge=null; }
        uid     = (httpUID   !=null)? httpUID:    json.stringPathN("URL");     json.removePath("URL");
        uid     = (uid       !=null)? uid:        json.stringPathN("UID");     json.removePath("UID");
        uid     = (uid       !=null)? uid:        httpReqURL;
        uid     = (uid       !=null)? uid:        UID.generateUID();
        uid     = UID.toUIDifLocal(uid);
        etag    = (httpETag  !=null)? httpetag:   json.intPathN(   "Version");    json.removePath("Version");
        maxAge  = (httpMaxAge!=null)? httpmaxage: json.intPathN(   "Max-Age"); json.removePath("Max-Age");
        listToSet(notify,                         json.listPathN(  "Notify")); json.removePath("Notify");
        if(httpNotify!=null && !httpNotify.startsWith("c-n-")) notify.add(httpNotify);
        cacheNotify = httpCacheNotify;
        publicState = json;
        updatingState = publicState;
    }

    /** Create a shell object. */
    public WebObject(UID shelluid){
        funcobs = FunctionalObserver.funcobs;
        uid = shelluid.toString();
        shellstate = ShellState.FINDING;
    }

    /** For creating new WebObjects inside evaluate(). */
    public WebObject(String jsonstring){
        funcobs = FunctionalObserver.funcobs;
        uid = UID.generateUID();
        etag = 1;
        publicState = new JSON(jsonstring);
        updatingState = publicState;
    }

    /** For creating new WebObjects inside evaluate(). */
    public WebObject(JSON json){
        funcobs = FunctionalObserver.funcobs;
        uid = UID.generateUID();
        etag = 1;
        publicState = json;
        updatingState = publicState;
    }

    /** For spawning. */
    public WebObject(LinkedHashMap hm){
        funcobs = FunctionalObserver.funcobs;
        uid = UID.generateUID();
        etag = 1;
        publicState = new JSON(hm);
        updatingState = publicState;
    }

    /** For spawning. */
    public WebObject construct(LinkedHashMap hm){
        funcobs = FunctionalObserver.funcobs;
        uid = UID.generateUID();
        etag = 1;
        publicState = new JSON(hm);
        updatingState = publicState;
        return this;
    }

    public boolean isShell(){
        return shellstate != ShellState.FOUND;
    }

    public boolean isLocal(){
        return uid.startsWith("uid-") && cacheNotify==null;
    }

    public boolean isVisibleRemote(){
        return uid.startsWith("http://");
    }

    public boolean isAsymmetricCN(){
        return uid.startsWith("c-n-");
    }

    public boolean isAsymmetricRemote(){
        return uid.startsWith("uid-") && cacheNotify!=null;
    }

    public void setURL(final String url){
        new Evaluator(this){
            public void evaluate(){
                self.url=url;
                self.evaluate();
            }
        };
    }

    /* --- Evaluate API ----------------------------------- */

    /** If path crosses over a link, fetch WebObject with
      * that uid, if in the cache.
      * Start or confirm the observation (will be dropped
      * if you don't do this). If not in cache, return
      * null and fire off whatever it takes to get it.
      */

    /** Get Object at this path. */
    public Object contentObject(String path){
        if(pathMatches(path,"UID"  )) return uid;
        if(pathMatches(path,"Version")) return Integer.valueOf(etag);
        try{ return updatingState.objectPath(path);
        }catch(PathOvershot po){
            String parentuid=uid;
            while(true){
                if(!(po.leaf() instanceof String)) return null;
                WebObject w = observing(parentuid, (String)po.leaf(), path);
                if(w==null) return null;
                if(pathMatches(po.path(),"UID"  )) return w.uid;
                if(pathMatches(po.path(),"Version")) return Integer.valueOf(w.etag);
                try{ return w.publicState.objectPath(po.path());
                }catch(PathOvershot po2){ po=po2; parentuid=w.uid; }
            }
        }
    }

    private boolean pathMatches(String path, String match){
        if(path.startsWith(":")) path=path.substring(1);
        if(path.endsWith(  ":")) path=path.substring(0,path.length()-1);
        return path.equals(match);
    }

    /** Get String at this path. */
    public String content(String path){
        Object o=contentObject(path);
        if(o instanceof String) return (String)o;
        return null;
    }

    /** Get String at this path, or given alternative if none. */
    public String contentOr(String path, String or){
        String s=content(path);
        return s!=null? s: or;
    }

    /** Get String, or string form of object, at this path, or given alternative if none. */
    public String contentStringOr(String path, String or){
        String s=contentString(path);
        return s!=null? s: or;
    }

    /** Get String, or string form of object, at this path. */
    public String contentString(String path){
        Object o=contentObject(path);
        if(o==null) return null;
        if(o instanceof String)  return ((String)o);
        if(o instanceof Number)  return toNicerString((Number)o);
        if(o instanceof Boolean) return o.toString();
        if(o instanceof LinkedHashMap){
            LinkedHashMap hm=(LinkedHashMap)o;
            String r="";
            for(Object key: hm.keySet()){ r+=key+": "+hm.get(key)+" "; }
            return r.trim();
        }
        if(o instanceof LinkedList){
            LinkedList ll=(LinkedList)o;
            String r="";
            for(Object i: ll) r+=i+" ";
            return r.trim();
        }
        return o.toString();
    }

    /** Test if anything set at path. */
    public boolean contentSet(String path){
        return contentObject(path)!=null;
    }

    /** Test if Object at path is value, whether String, Number or Boolean. */
    public boolean contentIsString(String path, String val){
        try{
            Object o = contentObject(path);
            if(o==null) return val==null;
            if(o instanceof String)  return ((String)o).equals(val);
            if(o instanceof Number)  return ((Number)o).doubleValue()==Double.parseDouble(val);
            if(o instanceof Boolean) return ((Boolean)o).toString().equals(val.toLowerCase());
            return o.toString().equals(val);
        }catch(Exception e){ return false; }
    }

    /** Test if String at path is value. */
    public boolean contentIs(String path, String val){
        String s = content(path);
        if(s==null) return val==null;
        return s.equals(val);
    }

    /** Test if integer at path is value. */
    public boolean contentIs(String path, int val){
        if(!contentSet(path)) return false;
        int s = contentInt(path);
        return s==val;
    }

    /** Test if there's a link to this object. */
    public boolean contentIsThis(String path){
        String s = content(path);
        if(s==null) return false;
        return UID.toUID(s).equals(uid);
    }

    // --------------------------------------------------------------

    /** Set Object at this path. */
    public void contentObject(String path, Object val){
        doCopyOnWrite(path);
        statemod = updatingState.objectPath(path, val) || statemod;
    }

    /** Set String at this path. */
    public void content(String path, String val){
        doCopyOnWrite(path);
        statemod = updatingState.stringPath(path, val) || statemod;
    }

    /** Set Object at this path: don't mark as Etag change and don't observe through any link. */
    public void contentTemp(String path, Object val){
        if(val!=null) tempPaths.add(path); else tempPaths.remove(path);
        doCopyOnWrite(path);
        updatingState.objectPath(path, val);
    }

    /** Set String at this path: don't mark as Etag change but do observe through any link. */
    public void contentTempObserve(String path, String val){
        doCopyOnWrite(path);
        updatingState.stringPath(path, val);
    }

    /** Clone from source path to path. */
    public boolean contentClone(String path, String source){
        Object o = contentObject(source);
        if(o==null) return false;
        if(o instanceof String)        content(      path, (String)o); else
        if(o instanceof Number)        contentDouble(path,((Number)o).doubleValue()); else
        if(o instanceof Boolean)       contentBool(  path, (Boolean)o); else
        if(o instanceof LinkedHashMap) contentHash(  path, (LinkedHashMap)((LinkedHashMap)o).clone()); else
        if(o instanceof LinkedList)    contentList(  path, (LinkedList)((LinkedList)o).clone());
        return true;
    }

    /** Set UID at this path as a fully-qualified URL. */
    public void contentURL(String path, String val){
        doCopyOnWrite(path);
        statemod = updatingState.stringPath(path, UID.toURL(val)) || statemod;
    }

    // --------------------------------------------------------------

    /** Get int at this path. */
    public int contentInt(String path){
        return (int)findNumberIn(contentObject(path));
    }

    /** Set int at this path. */
    public void contentInt(String path, int val){
        doCopyOnWrite(path);
        statemod = updatingState.intPath(path, val) || statemod;
    }

    /** Increment the number at this path by one. */
    public void contentInc(String path){
        doCopyOnWrite(path);
        statemod = updatingState.incPath(path) || statemod;
    }

    /** Increment the number at this path by given delta. */
    public void contentInc(String path, double delta){
        doCopyOnWrite(path);
        statemod = updatingState.incPath(path,delta) || statemod;
    }

    /** Decrement the number at this path. */
    public void contentDec(String path){
        doCopyOnWrite(path);
        statemod = updatingState.decPath(path) || statemod;
    }

    /** Double value found at path. */
    public double contentDouble(String path){
        return findNumberIn(contentObject(path));
    }

    /** Set double value at path. */
    public void contentDouble(String path, double val){
        doCopyOnWrite(path);
        statemod = updatingState.doublePath(path, val) || statemod;
    }

    /** Get list of Objects at path, iterating one intervening list if any. */
    @SuppressWarnings("unchecked")
    public LinkedList contentAll(String path){
        LinkedList l=contentList(path);
        if(l!=null){ return l.isEmpty()? null: l; }
        Object o=contentObject(path);
        l=new LinkedList();
        if(o!=null){ l.add(o); return l; }
        String[] parts=path.split(":");
        for(int x=1; x<=parts.length; x++){
            String ss=pathWithIndexIndicator(parts,x);
            String listpath = ss.replaceFirst(":::.*","");
            LinkedList li=contentList(listpath);
            if(li!=null){
                for(int i=0; i<li.size(); i++){
                    o=contentObject(ss.replaceFirst(":::",String.format(":%d",i)));
                    if(o!=null) l.add(o);
                }
                if(!l.isEmpty()) break;
            }
        }
        return l.isEmpty()? null: l;
    }

    private String pathWithIndexIndicator(String[] parts, int x){
        StringBuilder sb=new StringBuilder();
        int p=0;
        for(; p<x; p++){ sb.append(parts[p]); sb.append(":"); }
        sb.append("::");
        for(; p<parts.length; p++){ sb.append(":"); sb.append(parts[p]); }
        return sb.toString();
    }

    /** Get boolean at this path. */
    public boolean contentBool(String path){
        Object o=contentObject(path);
        if(o instanceof Boolean) return ((Boolean)o).booleanValue();
        return false;
    }

    /** Set boolean at this path. */
    public void contentBool(String path, boolean val){
        doCopyOnWrite(path);
        statemod = updatingState.boolPath(path, val) || statemod;
    }

    /** Get hash at path, jumping over uid to next object if necessary. */
    public LinkedHashMap contentHashMayJump(String path){
        LinkedHashMap hm=contentHash(path);
        if(hm!=null) return hm;
        return contentHash(path+":#");
    }

    /** Get list at path, jumping 'list' at end if necessary. */
    public LinkedList contentListMayJump(String path){
        LinkedList ll=contentList(path);
        if(ll!=null) return ll;
        return contentList(path+":list");
    }

    /** Get list at path. */
    public LinkedList contentList(String path){
        Object o=contentObject(path);
        if(o instanceof LinkedList) return (LinkedList)o;
        return null;
    }

    /** Get list at path, even if it's not a list (wrap non-lists). */
    public LinkedList contentAsList(String path){
        Object o=contentObject(path);
        if(o==null) return null;
        if(o instanceof LinkedList) return (LinkedList)o;
        return list(o);
    }

    /** Get clone copy of list at path. */
    public LinkedList contentListClone(String path){
        LinkedList list=contentList(path);
        if(list==null) return null;
        return (LinkedList)list.clone();
    }

    /** Get clone copy of hash at path. */
    public LinkedHashMap contentHashClone(String path){
        LinkedHashMap hash=contentHash(path);
        if(hash==null) return null;
        return (LinkedHashMap)hash.clone();
    }

    /** Returns true if list at path contains the value. */
    public boolean contentListContains(String path, Object val){
        LinkedList list=contentList(path);
        if(list==null) return false;
        return list.contains(val);
    }

    /** Returns first index in list at path that contains the value, -1 if not found. */
    public int contentListIndexOf(String path, Object val){
        LinkedList list=contentList(path);
        if(list==null) return -1;
        return list.indexOf(val);
    }

    /** Returns true if list at path contains the value. */
    @SuppressWarnings("unchecked")
    public boolean contentListContainsAll(String path, List val){
        LinkedList list=contentList(path);
        if(list==null) return false;
        if(val.isEmpty()) return true;
        return list.containsAll(val);
    }

    /** Returns true if String or list at path is or contains the value. */
    public boolean contentIsOrListContains(String path, String val){
        LinkedList list=contentList(path);
        if(list!=null) return list.contains(val);
        String s = content(path);
        if(s==null) return val==null;
        return s.equals(val);
    }

    /** Set list at path. */
    public void contentList(String path, List val){
        doCopyOnWrite(path);
        statemod = updatingState.listPath(path, val) || statemod;
    }

    /** Add the value onto the list at the path. */
    public void contentListAdd(String path, Object val){
        doCopyOnWrite(path);
        statemod = updatingState.listPathAdd(path, val) || statemod;
    }

    /** Add the value onto the list at the path, making it into a fully-qualified URL. */
    public void contentListAddURL(String path, String val){
        doCopyOnWrite(path);
        statemod = updatingState.listPathAdd(path, UID.toURL(val)) || statemod;
    }

    /** Add this value as if the list were a set. */
    public void contentSetAdd(String path, Object val){
        doCopyOnWrite(path);
        statemod = updatingState.setPathAdd(path, val) || statemod;
    }

    /** Push this value as if the list were a set. */
    public void contentSetPush(String path, Object val){
        doCopyOnWrite(path);
        statemod = updatingState.setPathPush(path, val) || statemod;
    }

    /** Add all the values onto the list at the path. */
    public void contentListAddAll(String path, List val){
        if(val.isEmpty()) return;
        doCopyOnWrite(path);
        statemod = updatingState.listPathAddAll(path, val) || statemod;
    }

    /** Push all the values as if the list were a set. */
    public void contentSetAddAll(String path, LinkedList val){
        if(val.isEmpty()) return;
        doCopyOnWrite(path);
        statemod = updatingState.setPathAddAll(path, val) || statemod;
    }

    /** Push all the values as if the list were a set. */
    public void contentSetPushAll(String path, LinkedList val){
        if(val.isEmpty()) return;
        doCopyOnWrite(path);
        statemod = updatingState.setPathPushAll(path, val) || statemod;
    }

    /** Remove item at path and return object that was at that path. */
    public Object contentRemove(String path){
        Object o = contentObject(path);
        doCopyOnWrite(path);
        statemod = updatingState.removePath(path) || statemod;
        return o;
    }

    /** Remove the given value in the list at the path. */
    public void contentListRemove(String path, Object val){
        doCopyOnWrite(path);
        statemod = updatingState.listPathRemove(path, val) || statemod;
    }

    /** Remove the indexed value in the list at the path. */
    public void contentListRemove(String path, int ind){
        doCopyOnWrite(path);
        statemod = updatingState.listPathRemove(path, ind) || statemod;
    }

    /** Remove all the values in the list at the path. */
    public void contentListRemoveAll(String path, List val){
        if(val.isEmpty()) return;
        doCopyOnWrite(path);
        statemod = updatingState.listPathRemoveAll(path, val) || statemod;
    }

    /** Return hash at path. */
    public LinkedHashMap contentHash(String path){
        Object o=contentObject(path);
        if(o instanceof LinkedHashMap) return (LinkedHashMap)o;
        return null;
    }
        
    /** Set hash at path. */
    public void contentHash(String path, LinkedHashMap val){
        doCopyOnWrite(path);
        statemod = updatingState.hashPath(path, val) || statemod;
    }

    /** Set JSON at path. */
    public void contentHash(String path, JSON val){
        doCopyOnWrite(path);
        statemod = updatingState.hashPath(path, val.content()) || statemod;
    }

    /** Merge in JSON to this object. */
    public void contentMerge(JSON json){
        doCopyOnWrite("");
        statemod = updatingState.mergeWith(json) || statemod;
    }

    /** Merge in hash to this object. */
    public void contentMerge(LinkedHashMap hm){
        doCopyOnWrite("");
        statemod = updatingState.mergeWith(hm) || statemod;
    }

    /** Replace all content with supplied JSON. */
    public void contentReplace(JSON json){
        doCopyOnWrite("");
        statemod = updatingState.replaceWith(json) || statemod;
    }

    /** Set this object up to notify the object at this uid.
      * That object may or may not be observing us already.
      */
    public void notifying(String alertuid){
        if(alertuid==null) return;
        // what if you pull notifying uid off remote object?
        notifying(uid, alertuid);
    }

    /** Add to notifying set for construction-time. */
    public void notifying(List<String> notifyset){
        listToSet(notify, notifyset);
    }

    /** Remove any across-the-wire notification. Local notify
      * entries are set by local observing.
      */
    public void unnotifying(String alertuid){
        if(alertuid==null) return;
        obsalmod = true;
        remalert.add(alertuid);
    }

    /** List of objects whose state was pushed here without
      * our observing them. If observing() is called on them,
      * this pushing continues without them appearing here
      * any more. Otherwise the push is rejected and removed.
      */
    public AbstractSet<String> alerted(){
        return alerted;
    }

    /** Wrap new WebObject in this to cache it, set it up
      * for later evaluation and return its UID. */
    public String spawn(WebObject w){
        obsalmod = true;
        funcobs.cachePut(w);
        spawned.add(w);
        return w.uid;
    }

    /** This is a transient object so don't persist its changes. */
    public void noPersist(){
        nopersist=true;
    }

    /** Keep all observations as they were. Thus don't need to
      * access anything to keep being notified of its updates. */
    public void refreshObserves(){
        refreshobserves=true;
    }

    /** Use this when running from an interface or I/O callback. */
    public class Evaluator{
        public WebObject self;
        public Evaluator(WebObject w){
            this.self=w;
            synchronized(w){
                w.evalPre();
                evaluate();
                w.evalPost();
            }
        }
        public void evaluate(){}
        public String toString(){ return self.toString(); }
    }

    /** Make sure this object reports back to base - sends updates to network:home-cache-notify. */
    public void notifyingCN(){
        funcobs.http.setHomeCN(this);
    }

    /** Initiate an HTTP fetch of JSON data and callback on httpNotifyJSON(). */
    public void httpGETJSON(String url, String param){
        funcobs.http.getJSON(url, this, param);
    }

    /** Callback for httpGETJSON. */
    public void httpNotifyJSON(final JSON json, String param){}

    /** Call to reset all changes. */
    public void rollback(){
        statemod = false;
        obsalmod = false;
        refreshobserves = false;
        updatingState = publicState;
        newalert.clear();
        remalert.clear();
        newobserve.clear();
    }

    /** See if it's one of ours, even if it's got a full URL. */
    public boolean oneOfOurs(String uid){
        WebObject w=funcobs.cacheOrPersistenceGet(UID.toUID(uid));
        return w!=null && !w.isShell();
    }

    /** Only normalise if it's not ours. */
    public String normaliseUIDIfNotOurs(String baseurl, String uid){
        return oneOfOurs(uid)? UID.toUID(uid): UID.normaliseUID(baseurl, uid);
    }

    /** Don't use this unless you're handing the thread over to an object that's responsible and local. */
    public WebObject onlyUseThisToHandControlOfThreadToDependent(String uid){ return funcobs.cacheOrPersistenceGet(UID.toUID(uid)); }

    /** Simple logger for ya. */
    static public void log(Object o){ Utils.log(o); }

    /** Log which rule you're in. */
    static public void logrule(){
        try{ throw new Exception(); } catch(Exception e){
            StackTraceElement[] stack=e.getStackTrace();
            log("================"+stack[1]+"================");
        }
    }

    static public void whereAmI(Object message){
        try{ throw new Exception(); } catch(Exception e){ log(message+": "+Arrays.asList(e.getStackTrace())); }
    }

    /* ---------------------------------------------------- */

    private WebObject observing(String baseurl, String uid, String path){
        boolean tempObserve = startsWithAnyTemp(path);
        String observeduid=normaliseUIDIfNotOurs(baseurl, uid);
        if(!UID.isUID(observeduid)) return null;
        obsalmod = true;
        return funcobs.observing(this, observeduid, tempObserve);
    }

    private boolean startsWithAnyTemp(String path){
        for(String tempPath: tempPaths) if(path.startsWith(tempPath)) return true;
        return false;
    }

    private void notifying(String baseurl, String uid){
        String alertuid=normaliseUIDIfNotOurs(baseurl, uid);
        if(!UID.isUID(alertuid)) return;
        if(notify.contains(alertuid)) return;
        obsalmod = true;
        newalert.add(alertuid);
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
        statemod = false;
        obsalmod = false;
        refreshobserves = false;
        newobserve = new HashSet<String>();
        newalert.clear();
        remalert.clear();
        alerted = alertedin;
        alertedin = new CopyOnWriteArraySet<String>();
        spawned.clear();
    }

    public void evaluate(){ }

    void evalPost(){
        observe.addAll(alerted);
        notify.addAll(newalert);
        funcobs.dropNotifies(this);
        funcobs.setCurrentNotifyAndObserve(this);
        if(statemod){
            makeNewStatePublicRightNowShouldBeAllAtomicButIsnt();
            funcobs.saveAndNotifyUpdated(this, true);
        }
        else{
            if(obsalmod) funcobs.saveAndAlertFirstTime(this);
        }
        funcobs.evalAndPersistSpawned(this);
    }

    void makeNewStatePublicRightNowShouldBeAllAtomicButIsnt(){
        publicState = updatingState;
        etag++;
    }

    /* ---------------------------------------------------- */

    public String toString(int maxlength){
        if(maxlength==0) return toString();
        if(isShell()) return   "{ \"UID\": \""+uid+
                             "\", \"Notify\": "+setToListString(notify)+
                               ", \"Alertedin\": "+setToListString(alertedin)+
                               ", \"State\": \""+shellstate+"\"}";
        String r = publicState.toString(
                                 "\"UID\": \""+uid+
                 (url!=null? "\", \"URL\": \""+url: "")+
                             "\", \"Version\": "+etag+
                               ", \"Max-Age\": "+maxAge+
                               ", \"Notify\": "+setToListString(notify)+
                               ", \"Observe\": "+setToListString(observe)+
           (cacheNotify!=null? ", \"Cache-Notify\": \""+cacheNotify+"\"": "")+
                               ", \"Class\": \""+this.getClass().toString().substring(6)+
                             "\",", maxlength);
        return r;
    }

    public String toString(boolean cyrus){
        if(!cyrus) return toString()+"\n";
        if(isShell()) return "{ UID: "+uid+
                           "\n  Notify: "+setToListString(notify, true)+
                           "\n  Alertedin: "+setToListString(alertedin, true)+
                           "\n  State: "+shellstate+"\n}\n\n";
        String r = publicState.toString(
                               "UID: "+uid+
              (url!=null?  "\n  URL: "+url: "")+
                           "\n  Version: "+etag+
                           "\n  Max-Age: "+maxAge+
                           "\n  Notify: "+setToListString(notify, true)+
                           "\n  Observe: "+setToListString(observe, true)+
      (cacheNotify!=null?  "\n  Cache-Notify: "+cacheNotify: "")+
                           "\n  Class: "+this.getClass().toString().substring(6)+
                           "\n",true)+"\n\n";
        return r;
    }

    public String toString(){
        if(isShell()) return "{ \"UID\": \""+uid+
                        "\",\n  \"Notify\": "+setToListString(notify)+
                          ",\n  \"Alertedin\": "+setToListString(alertedin)+
                          ",\n  \"State\": \""+shellstate+"\"\n}\n";
        String r = publicState.toString(
                               "\"UID\": \""+uid+
            (url!=null? "\",\n  \"URL\": \""+url: "")+
                        "\",\n  \"Version\": "+etag+
                          ",\n  \"Max-Age\": "+maxAge+
                          ",\n  \"Notify\": "+setToListString(notify)+
                          ",\n  \"Observe\": "+setToListString(observe)+
      (cacheNotify!=null? ",\n  \"Cache-Notify\": \""+cacheNotify+"\"": "")+
                          ",\n  \"Class\": \""+this.getClass().toString().substring(6)+
                        "\",\n")+"\n";
        return r;
    }

    public String toString(HashSet<String> percents, boolean cyrus){
        if(!cyrus) return toString(percents);
        String r = publicState.toString(
                       ( percents.contains("UID")?               "UID: "    +uid+                         "\n  ": "")+
                       ((percents.contains("URL") && url!=null)? "URL: "    +url+                         "\n  ": "")+
                       ( percents.contains("Version")?           "Version: "+etag+                        "\n  ": "")+
                       ( percents.contains("Max-Age")?           "Max-Age: "+maxAge+                      "\n  ": "")+
                       ( percents.contains("Notify")?            "Notify: " +setToListString(notify,true)+"\n  ": ""),
                       true)+"\n";
        return r;
    }

    public String toString(HashSet<String> percents){
        String r = publicState.toString(
                       ( percents.contains("UID")?               "\"UID\": \""  +uid+                  "\",\n  ": "")+
                       ((percents.contains("URL") && url!=null)? "\"URL\": \""  +url+                  "\",\n  ": "")+
                       ( percents.contains("Version")?           "\"Version\": "+etag+                   ",\n  ": "")+
                       ( percents.contains("Max-Age")?           "\"Max-Age\": "+maxAge+                 ",\n  ": "")+
                       ( percents.contains("Notify")?            "\"Notify\": " +setToListString(notify)+",\n  ": "")
                   )+"\n";
        return r;
    }

    private void listToSet(AbstractSet<String> set, List list){
        if(list==null) return;
        Iterator i = list.iterator();
        while(i.hasNext()){ String s=(String)i.next(); if(s!=null) set.add(s); }
    }
}

