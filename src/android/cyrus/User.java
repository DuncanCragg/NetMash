
package cyrus;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

import android.os.*;
import android.util.*;
import android.graphics.*;

import android.content.*;
import android.database.Cursor;
import android.location.*;
import android.accounts.*;

import com.google.android.maps.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.*;

import static android.content.Context.*;
import static android.location.LocationManager.*;

import cyrus.gui.NetMash;
import cyrus.gui.Mesh;
import cyrus.lib.*;
import cyrus.forest.*;
import cyrus.platform.Kernel;
import cyrus.types.*;

import static cyrus.lib.Utils.*;

/** User viewing the Object Web.
  */
public class User extends CyrusLanguage {

    // ---------------------------------------------------------

    static public User currentUser=null;

    static public BLELinks linksaround=null;
    static public Place place=null;

    static public void createUserAndDevice(){
        String fullName=UserContacts.getUsersFullName();
        String your=fullName.equals("You")? "Your": fullName+"'s";

        CyrusLanguage contact = new CyrusLanguage(
              "{ is: editable contact\n"+
              "  full-name: "+JSON.cyrusToString(fullName)+"\n"+
              "  address: { }\n"+
              "}", true);

        User contacts = new User(
              "{ is: private contact list\n"+
              "  title: \"Phone Contacts\", \n"+
              "}", true);

        CyrusLanguage links = new CyrusLanguage(
              "{ is: link list editable\n"+
              "}", true);

        linksaround = new BLELinks(
              "{ is: broadcast link list\n"+
              "  title: \"Objects Around\" \n"+
              "}");

        Light light = new Light(linksaround.uid);

        place = new Place(
              "{ is: place 3d mesh editable\n"+
              "  title: \"Place for Things\"\n"+
              "  scale: 20 20 20\n"+
              "  vertices: ( 1 0 0 ) ( 1 0 1 ) ( 0 0 1 ) ( 0 0 0 )\n"+
              "  texturepoints: ( 0 0 ) ( 5 0 ) ( 5 5 ) ( 0 5 )\n"+
              "  normals: ( 0 1 0 )\n"+
              "  faces: ( 2/3/1 1/2/1 4/1/1 ) ( 2/3/1 4/1/1 3/4/1 )\n"+
              "  textures: http://www.textures123.com/free-texture/sand/sand-texture4.jpg\n"+
              "}\n");

        // -----------------------------------------------------

        String homeusers=Kernel.config.stringPathN("app:homeusers");
        currentUser = new User(homeusers, contact.uid, links.uid, linksaround.uid, contacts.uid);

        LinkedList cyruslinks=Kernel.config.listPathN("app:links");
        cyruslinks.addFirst(place.uid);
        cyruslinks.addFirst(light.uid);
        cyruslinks.addFirst(linksaround.uid);
        cyruslinks.addFirst(currentUser.uid);
        links.publicState.listPath("list", cyruslinks);

        String cn="c-n-"+currentUser.uid.substring(4);
        WebObject cyrusconfig = new WebObject(
              "{   \"persist\": { \"preload\": [ \""+currentUser.uid+"\" ] }, \n"+
              "    \"network\": { \"cache-notify\": \""+cn+"\"}\n"+
              "}");
        currentUser.funcobs.setCacheNotifyAndSaveConfig(cn, cyrusconfig);

        currentUser.funcobs.cacheSaveAndEvaluate(contact, true);
        currentUser.funcobs.cacheSaveAndEvaluate(links);
        currentUser.funcobs.cacheSaveAndEvaluate(light);
        currentUser.funcobs.cacheSaveAndEvaluate(place);
        currentUser.funcobs.cacheSaveAndEvaluate(linksaround);
        currentUser.funcobs.cacheSaveAndEvaluate(contacts);
        currentUser.funcobs.cacheSaveAndEvaluate(currentUser, true);

        if(homeusers!=null) currentUser.notifying(list(homeusers));

        if(NetMash.top!=null) NetMash.top.onUserReady(currentUser);
    }

    public User(){
        if(currentUser!=null) return;
        currentUser=this;
        if(NetMash.top!=null) NetMash.top.onUserReady(currentUser);
    }

    public User(String jsonstring){ super(jsonstring); }

    public User(String jsonstring, boolean cyrus){ super(jsonstring,cyrus); }

    public User(JSON json){ super(json); }

    public User(String homeusers, String contactuid, String linksuid, String linksarounduid, String contactsuid){
        super("{   \"is\": \"user\", \n"+
              "    \"homeusers\": \""+homeusers+"\", \n"+
              "    \"saying\": \"\", \n"+
              "    \"within\": null, \n"+
              "    \"position\": [ 0.0, 0.0, 0.0 ], \n"+
              "    \"avatar\": \"http://10.0.2.2:8081/o/uid-7794-3aa8-2192-7a60.json\", \n"+
              "    \"location\": { }, \n"+
              "    \"contact\": \""+contactuid+"\", \n"+
              "    \"private\": { \n"+
              "        \"viewing\": null, \n"+
              "        \"editing\": null, \n"+
              "        \"viewas\": \"gui\", \n"+
              "        \"links\": \""+linksuid+"\", \n"+
              "        \"links-around\": \""+linksarounduid+"\", \n"+
              "        \"history\": null, \n"+
              "        \"contacts\":  \""+contactsuid+"\", \n"+
              "        \"responses\": { }, \n"+
              "        \"position\": { }, \n"+
              "        \"orientation\": { } \n"+
              "    }\n"+
              "}");
    }

    static User newForm(String guiuid, String useruid){
        return new User("{ \"is\": \"form\",\n"+
                        "  \"gui\": \""+guiuid+"\",\n"+
                        "  \"user\": \""+useruid+"\",\n"+
                        "  \"form\": { }\n"+
                        "}");
    }

    static User newEditableRule(String editableuid, String useruid){
        return new User("{ \"is\": [ \"editable\", \"rule\" ],\n"+
                        "  \"when\": \"edited\",\n"+
                        "  \"editable\": \""+editableuid+"\",\n"+
                        "  \"user\": \""+useruid+"\"\n"+
                        "}");
    }

    static User newDocumentQuery(String listuid, String useruid){
        return new User("{ \"is\": [ \"document\", \"query\" ], \n"+
                        "  \"list\": \""+listuid+"\",\n"+
                        "  \"user\": \""+useruid+"\"\n"+
                        "}");
    }

    static User newLand(LinkedList rules, boolean updatable, String landlistuid, String useruid, String templateuid){
        User land=new User(
                        "{ "+
            (rules!=null? "\"Rules\": "+setToListString(rules)+",\n  ": "")+
              (updatable? "\"is\": [ \"updatable\", \"land\" ],\n":
                          "\"is\": [ \"land\" ],\n")+
                        "  \"within\": \""+landlistuid+"\",\n"+
                        "  \"user\": \""+useruid+"\",\n"+
    (templateuid!=null? "  \"update-template\": \""+templateuid+"\"\n": "")+
                        "}");
        return land;
    }

    static User newRSVP(String eventuid, String useruid, String placeuid){
        return new User("{ \"is\": \"rsvp\", \n"+
                        "  \"event\": \""+eventuid+"\",\n"+
                        "  \"user\": \""+useruid+"\",\n"+
       (placeuid!=null? "  \"within\": \""+placeuid+"\"\n": "")+
                        "}");
    }

    // ---------------------------------------------------------

    Cyrus2GUI cyrus2gui;
    CurrentLocation currentlocation=null;
    Sensors sensors=null;
    boolean trackGPS=true;

    public void onTopCreate(String url){
        if(trackGPS) currentlocation = new CurrentLocation(this);
        if(sensors==null) sensors=new Sensors(this);
        if(linksaround!=null)     linksaround.enableScanning();
        if(place!=null)           place.broadcastPlaceEnable=true;
        if(url!=null) jumpToUID(url,"gui",false);
    }

    public void onTopResume(){
        new Evaluator(this){ public void evaluate(){ showWhatIAmViewing(); }};
        if(currentlocation!=null) currentlocation.getLocationUpdates();
        if(sensors!=null)         sensors.startWatchingSensors();
        if(linksaround!=null)     linksaround.enableScanning();
        if(place!=null)           place.broadcastPlaceEnable=true;
    }

    public void onTopPause(){
        if(currentlocation!=null) currentlocation.stopLocationUpdates();
        if(sensors!=null)         sensors.stopWatchingSensors();
        if(linksaround!=null)     linksaround.disableScanning();
        if(place!=null)           place.broadcastPlaceEnable=false;
    }

    public void onTopDestroy(){
        if(currentlocation!=null) currentlocation.stopLocationUpdates();
        if(sensors!=null)         sensors.stopWatchingSensors();
        if(linksaround!=null)     linksaround.disableScanning();
        if(place!=null)           place.broadcastPlaceEnable=false;
    }

    // ---------------------------------------------------------

    void onNewLocation(final Location location){
        new Evaluator(this){ public void evaluate(){
            if(false) log("location: "+location);
            contentDouble("location:lat", location.getLatitude());
            contentDouble("location:lon", location.getLongitude());
            contentDouble("location:acc", location.getAccuracy());
            content(      "location:prv", location.getProvider());
        }};
    }

    void onNewOrientation(float azimuth, float pitch, float roll){
        if(trackingAround) NetMash.top.onNewOrientation(azimuth,pitch,roll);
    }

    // ---------------------------------------------------------

    public ConcurrentHashMap<String,Bitmap> textBitmaps = new ConcurrentHashMap<String,Bitmap>();

    public ConcurrentHashMap<String,String> shaders = new ConcurrentHashMap<String,String>();

    // ---------------------------------------------------------

    private long earliest=0;
    private boolean waiting=false;

    public void onObjectTouched(LinkedList touchInfo, final boolean down, final int firstTouchQuadrant, final float x, final float y, final float z){
        final String     objectuid=(String)    touchInfo.get(0);
        final String     withinuid=(String)    touchInfo.get(1);
        final LinkedList withinpos=(LinkedList)touchInfo.get(2);
        if(objectuid==null) return;
        new Evaluator(this){ public void evaluate(){
            if(firstTouchQuadrant==1){
                content("holding",objectuid);
                NetMash.top.toast("Holding "+contentStringOr("holding:title","object"), false);
            }
            else
            if(firstTouchQuadrant==2){
                jumpToHereAndShow(objectuid, "gui", false);
            }
            else {
                if(down){
                    LinkedList posn=list(x,y,z);
                    LinkedHashMap touching=hash("item",objectuid, "position",posn);
                    if(withinuid!=null) touching.put("within", hash("item",withinuid, "position",withinpos));
                    contentHash("touching", touching);
                    LinkedList scale=contentList("touching:item:scale");
                    if(scale!=null){
                        LinkedList rescaledposn=vvcross(scale,posn);
                        if(rescaledposn!=null) touching.put("position", rescaledposn);
                    }
                }
                else{
                    content("touching",null);
                }
                notifying(objectuid);
                if(withinuid!=null) notifying(withinuid);
            }
        }};
    }

    private float lastpx,lastpy,lastpz;
    private float lastdi;
    private int posupdate=0;
    private Thread moveThread=null;

    public void onNewPositionOrOrientation(float px, float py, float pz, float di){
        lastpx=px; lastpy=py; lastpz=pz;
        lastdi=di;
        posupdate++;
        if(moveThread==null){
            moveThread=new Thread(){ public void run(){
                int p= -1;
                while(true){
                    if(p!=posupdate){
                        p=posupdate;
                        doMoveTo(lastpx,lastpy,lastpz, lastdi);
                    }
                    Kernel.sleep(500);
                }
            }}; moveThread.start();
        }
    }

    void doMoveTo(final float px, final float py, final float pz, final float di){
        new Evaluator(this){
            public void evaluate(){
                LinkedList newplace=findNewPlaceNearer(px,py,pz);
                if(newplace==null){
                    LinkedList newposn=list(px,py,pz);
                    LinkedList newornt=list(di);
                    contentList("position", newposn);
                    contentList("private:position:"   +UID.toUID(content("private:viewing")), newposn);
                    contentList("private:orientation:"+UID.toUID(content("private:viewing")), newornt);
                }
                else{
                    String     newplaceuid=(String)  newplace.get(0);
                    LinkedList newposn  =(LinkedList)newplace.get(1);
                    contentList("position", newposn);
                    contentList("private:position:"+UID.toUID(newplaceuid), newposn);
                    jumpToHereAndShow(newplaceuid,"gui", false);
                }
            }
        };
    }

    private LinkedList findNewPlaceNearer(float ux, float uy, float uz){
        LinkedList subObjects=contentList("within:sub-items");
        if(subObjects==null) return null;
        for(int i=0; i< subObjects.size(); i++){
            String objispath=String.format("within:sub-items:%d:item:is",i);
            if(!contentListContains(objispath,"place")) continue;
            LinkedList placeposn=contentList(String.format("within:sub-items:%d:position",i));
            LinkedList placescale=contentList(String.format("within:sub-items:%d:item:scale",i));
            float px=getFloatFromList(placeposn,0,0);
            float py=getFloatFromList(placeposn,1,0);
            float pz=getFloatFromList(placeposn,2,0);
            float sx=getFloatFromList(placescale,0,1);
            float sy=getFloatFromList(placescale,1,1);
            float sz=getFloatFromList(placescale,2,1);
            if(pointWithinSquare(ux,uy,uz, px,py,pz, sx,sy,sz)){
                return list(content(String.format("within:sub-items:%d:item",i)), list(ux-px,uy-py,uz-pz));
            }
        }
        return null;
    }

    private boolean pointWithinSquare(float ux, float uy, float uz, float px, float py, float pz, float sx, float sy, float sz){
        return ux>px && ux<px+sx &&
               uy>py && uy<py+sy &&
               uz>pz && uz<pz+sz;
    }

    // ---------------------------------------------------------

    class View{
        String uid,as; View(String u, String a){ uid=u; as=a; }
        public String toString(){ return String.format("View(%s, %s)", uid, as); }
        public boolean equals(Object v){ return (v instanceof View) && uid.equals(((View)v).uid) && as.equals(((View)v).as); }
    };

    class History extends Stack<View>{
        public History(){}
        public void forward(View n){
            int i=search(n);
            if(empty() || i == -1 || i >1) push(n);
        }
        public View nonBrainDeadPop(){  return empty()? null: pop(); }
        public View nonBrainDeadPeek(){ return empty()? null: peek(); }
        public String toString(){
            StringBuilder sb=new StringBuilder();
            sb.append("[ ");
            for(View v: this){ sb.append(v.uid); sb.append("-"); sb.append(v.as); sb.append(" "); }
            sb.append("]");
            return sb.toString();
        }
    };

    private History history = new History();

    public void jumpToUID(final String uid){
        jumpToUID(uid, null, false);
    }

    public void jumpToUID(final String uid, final String as, final boolean relativeToViewing){
        new Evaluator(this){ public void evaluate(){
            String jumpuid;
            if(oneOfOurs(uid))    jumpuid=UID.toUID(uid); else
            if(relativeToViewing) jumpuid=UID.normaliseUID(content("private:viewing"),uid);
            else                  jumpuid=uid;
                jumpToHereAndShow(jumpuid,as,false);
            }
        };
    }

    boolean trackingAround=false;

    public void goBack(){
        trackingAround=false;
        new Evaluator(this){ public void evaluate(){ jumpBack(); } };
    }

    private void jumpToHereAndShow(String uid, String as, boolean setTrackingAround){
        if(uid==null) return;
        trackingAround=setTrackingAround;
        View end=history.nonBrainDeadPeek();
        if(end!=null && end.uid.equals(uid) && (as==null || end.as.equals(as))) jumpBack();
        else jumpForward(uid, as);
    }

    private void jumpForward(String uid, String as){
        View view=new View(content("private:viewing"), content("private:viewas"));
        history.forward(view);
        setViewingAndAs(uid, as);
        showWhatIAmViewing();
    }

    private void jumpBack(){
        View view=history.nonBrainDeadPop();
        if(view==null) return;
        setViewingAndAs(view.uid, view.as);
        showWhatIAmViewing();
    }

    private void setViewingAndAs(String uid, String as){
        content("private:viewing", uid);
        if(as!=null) content("private:viewas", as);
    }

    public boolean menuItem(final int itemid){
        new Evaluator(this){ public void evaluate(){
            switch(itemid){
            case NetMash.MENU_ITEM_ARD:
                jumpToEnvironment();
            break;
            case NetMash.MENU_ITEM_LNX:
                jumpToHereAndShow(content("private:links"), "gui", false);
            break;
            case NetMash.MENU_ITEM_GUI:
                jumpToHereAndShow(content("private:viewing"), "gui", false);
            break;
            case NetMash.MENU_ITEM_MAP:
                jumpToHereAndShow(content("private:viewing"), "map", false);
            break;
            case NetMash.MENU_ITEM_RAW:
                jumpToHereAndShow(content("private:viewing"), "raw", false);
            break;
            case NetMash.MENU_ITEM_PLC:
                place.broadcastPlaceSet=!place.broadcastPlaceSet;
                NetMash.top.setMenuTitle(itemid, place.broadcastPlaceSet? "Place ✘": "Place ✔");
            break;
            }
        }};
        return true;
    }

    // ---------------------------------------------------------

    public void prepareResponse(final String guiuid){
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
            }
        };
    }

    public LinkedList<LinkedList> getPositionAndOrientation(){
        final LinkedList<LinkedList> r = new LinkedList<LinkedList>();
        new Evaluator(this){ public void evaluate(){
            String uid=content("private:viewing");
            String pospath="private:position:"   +UID.toUID(uid);
            String oripath="private:orientation:"+UID.toUID(uid);
            LinkedList newposn=contentList(pospath);
            LinkedList newornt=contentList(oripath);
            if(newposn==null){
                LinkedList scale=contentList("private:viewing:scale");
                float sx=getFloatFromList(scale,0,1);
                float sz=getFloatFromList(scale,2,1);
                newposn=list(sx/2, 1.0, sz+1.0);
                newornt=list(0);
                contentList(pospath, newposn);
                contentList(oripath, newornt);
            }
            contentList("position", newposn);
            r.add(newposn);
            r.add(newornt);
        }};
        return r;
    }

    public String getFormStringVal(final String guiuid, final String tag){
        final String[] val=new String[1];
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
                if(contentListContainsAll("private:viewing:is", list("attendable","event"))||
                   contentListContainsAll("private:viewing:is", list("reviewable","event"))  ){
                    val[0]=content("private:responses:rsvp:"+UID.toUID(guiuid)+":"+dehash(tag));
                }
                else if(contentIsOrListContains("private:viewing:is", "gui")){
                    val[0]=content("private:responses:form:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
                else val[0]=null;
            }
        };
        return val[0];
    }

    public Double getFormDoubleVal(final String guiuid, final String tag){
        final Double[] val=new Double[1];
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
                if(contentListContainsAll("private:viewing:is", list("attendable","event"))||
                   contentListContainsAll("private:viewing:is", list("reviewable","event"))  ){
                    val[0]=contentDouble("private:responses:rsvp:"+UID.toUID(guiuid)+":"+dehash(tag));
                }
                else if(contentIsOrListContains("private:viewing:is", "gui")){
                    val[0]=contentDouble("private:responses:form:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
                else val[0]=null;
            }
        };
        return val[0];
    }

    public Boolean getFormBooleanVal(final String guiuid, final String tag){
        final Boolean[] val=new Boolean[1];
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
                if(contentListContainsAll("private:viewing:is", list("attendable","event"))||
                   contentListContainsAll("private:viewing:is", list("reviewable","event"))  ){
                    val[0]=contentIs("private:responses:rsvp:"+UID.toUID(guiuid)+":"+dehash(tag),"yes");
                }
                else if(contentIsOrListContains("private:viewing:is", "gui")){
                    val[0]=contentBoolean("private:responses:form:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
                else val[0]=null;
            }
        };
        return val[0];
    }

    private boolean setResponse(String guiuid){
        User resp=null;
        String path=null;
        if(contentIs("private:viewas","raw")){
            if(contentIsOrListContains("private:viewing:is", "editable")){
                if(!oneOfOurs(guiuid)){
                    path="private:responses:editable:"+UID.toUID(guiuid);
                    if(contentSet(path)) return false;
                    if(!contentSet("private:responses:editable")) contentHash("private:responses:editable", hash());
                    resp=newEditableRule(guiuid, uid);
                }
            }
        }
        else if(contentListContainsAll("private:viewing:is", list("searchable", "document", "list"))){
            path="private:responses:query:"+UID.toUID(guiuid);
            if(contentSet(path)) return false;
            if(!contentSet("private:responses:query")) contentHash("private:responses:query", hash());
            resp=newDocumentQuery(guiuid, uid);
        }
        else if(contentListContainsAll("private:viewing:is", list("attendable","event"))||
                contentListContainsAll("private:viewing:is", list("reviewable","event"))  ){
            path="private:responses:rsvp:"+UID.toUID(guiuid);
            if(contentSet(path)) return false;
            String evplaceuid=content("private:viewing:within");
            String placeuid=(evplaceuid!=null)? content("private:responses:rsvp:"+UID.toUID(evplaceuid)): null;
            if(!contentSet("private:responses:rsvp")) contentHash("private:responses:rsvp", hash());
            resp=newRSVP(guiuid, uid, placeuid);
        }
        else if(contentIsOrListContains("private:viewing:is", "gui")){
            path="private:responses:form:"+UID.toUID(guiuid);
            if(contentSet(path)) return false;
            if(!contentSet("private:responses:form")) contentHash("private:responses:form", hash());
            resp=newForm(guiuid, uid);
        }
        else if(contentIsOrListContains("private:viewing:is", "land")){
            path="private:responses:land:"+UID.toUID(guiuid);
            if(contentSet(path) && !contentSet(path+":title")) return false;
            if(contentSet("private:viewing:update-template") && !contentSet("private:viewing:update-template:is")) return false;
            String templateuid =UID.normaliseUID(guiuid, content(          "private:viewing:update-template"));
            LinkedList rules   =UID.normaliseUIDs(templateuid, contentList("private:viewing:update-template:Rules"));
            boolean updatable  =contentListContains(                       "private:viewing:update-template:is", "updatable");
            String     template=UID.normaliseUID( templateuid, content(    "private:viewing:update-template:update-template"));
            if(!contentSet("private:responses:land")) contentHash("private:responses:land", hash());
            resp=newLand(rules, updatable, guiuid, uid, template);
        }
        if(resp!=null) content(path, spawn(resp));
        return true;
    }

    private User getObjectUpdating(String guiuid){ return (User)getWebObjectUpdating(guiuid, ""); }

    private User getObjectUpdating(String guiuid, String tag){ return (User)getWebObjectUpdating(guiuid, tag); }

    private WebObject getWebObjectUpdating(final String guiuid, final String tag){
        final WebObject[] r=new WebObject[1]; r[0]=null;
        new Evaluator(this){ public void evaluate(){
            String formuid=null;
            if(contentIs("private:viewas","raw")){
                if(contentIsOrListContains("private:viewing:is", "editable")){
                    if(!oneOfOurs(guiuid)) formuid=content("private:responses:editable:"+UID.toUID(guiuid));
                    else                   formuid=guiuid;
                }
            }
            else if(contentListContainsAll("private:viewing:is", list("searchable", "document", "list"))){
                formuid=content("private:responses:query:"+UID.toUID(guiuid));
            }
            else if(contentListContainsAll("private:viewing:is", list("attendable","event"))||
                    contentListContainsAll("private:viewing:is", list("reviewable","event"))  ){
                formuid=content("private:responses:rsvp:"+UID.toUID(guiuid));
            }
            else if(contentIsOrListContains("private:viewing:is", "gui")){
                formuid=content("private:responses:form:"+UID.toUID(guiuid));
            }
            else if(contentIsOrListContains("private:viewing:is", "land")){
                if(dehash(tag).equals("new")) formuid=content("private:responses:land:"+UID.toUID(guiuid));
                else
                if(oneOfOurs(guiuid))         formuid=guiuid;
            }
            if(formuid!=null) r[0]=onlyUseThisToHandControlOfThreadToDependent(formuid);
        }};
        return r[0];
    }

    public void setUpdateVal(final String guiuid, final String tag, final String val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){ public void evaluate(){
                if(contentListContainsAll("is", list("editable", "rule"))){
                    LinkedHashMap rule=null;
                    try{
                        JSON json=new JSON(NetMash.top.getRawSource(),true);
                        json.setPathAdd("is", "editable");
                        rule=makeEditRule("",contentInt("editable:Version"),json);
                    }catch(JSON.Syntax js){ NetMash.top.toast(js.toString().split("\n")[1], true); }
                    if(rule!=null) contentMerge(rule);
                }
                else
                if(contentListContainsAll("is", list("document", "query"))){
                    content("content", String.format("has-words(%s)",val));
                }
                else
                if(contentIsOrListContains("is", "rsvp")){
                    content(dehash(tag), val);
                }
                else
                if(contentIsOrListContains("is", "land")){
                    if(!dehash(tag).equals("new")){
                        content(dehash(tag), val);
                        if(statemod) self.evaluate();
                        return;
                    }
                    content("title",val);
                    LinkedHashMap      location=contentHashClone("within:location");
                    if(location==null) location=contentHashClone("user:location");
                    if(location!=null){
                        contentHash("location", location);
                        int area=1;
                        contentList("shape", shapeAround(location, area));
                        contentDouble("area", area);
                    }
                    notifyingCN();
                }
                else
                if(contentIsOrListContains("is", "form")){
                    content("form:"+dehash(tag), val);
                }
                notifying(guiuid);
        }};
    }

    private LinkedList shapeAround(LinkedHashMap<String,Number> p, int area){
        int lat=(int)(p.get("lat").doubleValue()*1e6);
        int lon=(int)(p.get("lon").doubleValue()*1e6);
        int latsize=area*600;
        int lonsize=area*1000;
        int wobbley=area*150;
        LinkedList shape = new LinkedList();
        shape.add(hash("lat",(lat+latsize-wobbley)/1e6, "lon",(lon+lonsize+wobbley)/1e6));
        shape.add(hash("lat",(lat+latsize+wobbley)/1e6, "lon",(lon-lonsize-wobbley)/1e6));
        shape.add(hash("lat",(lat-latsize+wobbley)/1e6, "lon",(lon-lonsize+wobbley)/1e6));
        shape.add(hash("lat",(lat-latsize-wobbley)/1e6, "lon",(lon+lonsize-wobbley)/1e6));
        return shape;
    }

    public void setUpdateVal(final String guiuid, final String tag, final boolean val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){
                if(contentIsOrListContains("is", "rsvp")){
                    if(tag.equals("attending")) content(tag, val? "yes": "no");
                    else                        contentBool(dehash(tag),val);
                }
                else
                if(contentIsOrListContains("is", "land")){
                    contentBool(dehash(tag), val);
                    if(statemod) self.evaluate();
                    return;
                }
                else
                if(contentIsOrListContains("is", "form")){
                    contentBool("form:"+dehash(tag), val);
                }
                notifying(guiuid);
            }
        };
    }

    public void setUpdateVal(final String guiuid, final String tag, final int val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){
                if(contentIsOrListContains("is", "rsvp")){
                    contentInt(dehash(tag), val);
                }
                else
                if(contentIsOrListContains("is", "land")){
                    contentInt(dehash(tag), val);
                    if(statemod) self.evaluate();
                    return;
                }
                else
                if(contentIsOrListContains("is", "form")){
                    contentInt("form:"+dehash(tag), val);
                }
                notifying(guiuid);
            }
        };
    }

    public void setUpdateVal(final String guiuid, final GeoPoint val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, val);
        else new Evaluator(this){
            public void evaluate(){
                if(contentIsOrListContains("is", "land")){
                    setNearestShapePointTo(val);
                    if(statemod) self.evaluate();
                    return;
                }
                notifying(guiuid);
            }
        };
    }

    private void setNearestShapePointTo(GeoPoint np){
        double nplat=np.getLatitudeE6()/1e6;
        double nplon=np.getLongitudeE6()/1e6;
        LinkedList shape=contentList("shape");
        int i=0,j= -1;
        double nrlat=Integer.MAX_VALUE/1e6, nrlon=Integer.MAX_VALUE/1e6;
        double ctlat=0, ctlon=0;
        for(Object o: shape){
            LinkedHashMap<String,Number> pp=(LinkedHashMap<String,Number>)o;
            double pplat=pp.get("lat").doubleValue();
            double pplon=pp.get("lon").doubleValue();
            if(closer(nplat,nplon, pplat,pplon, nrlat,nrlon)){
                nrlat=pplat; nrlon=pplon;
                j=i;
            }
            ctlat+=pplat; ctlon+=pplon;
            i++;
        }
        if(j== -1) return;
        double area=contentDouble("area");
        double dist=dist(nplat,nplon, nrlat,nrlon);
        double size=Math.sqrt(area)/1000;
        if(dist < size/2){
            String path=String.format("shape:%d:",j);
            contentDouble(path+"lat", nplat);
            contentDouble(path+"lon", nplon);
            contentDouble("location:lat", ctlat/i);
            contentDouble("location:lon", ctlon/i);
            contentDouble("area", areaInShape(contentList("shape")));
        }
        else
        if(dist > size*2){
            double dlat=nplat-contentDouble("location:lat");
            double dlon=nplon-contentDouble("location:lon");
            for(int k=0; k<i; k++){
                String path=String.format("shape:%d:",k);
                contentInc(path+"lat", dlat);
                contentInc(path+"lon", dlon);
            }
            contentDouble("location:lat", nplat);
            contentDouble("location:lon", nplon);
        }
    }

    private boolean closer(double nplat,double nplon, double pplat,double pplon, double nrlat,double nrlon){
        return dist(nplat,nplon, pplat,pplon) < dist(nplat,nplon, nrlat,nrlon);
    }

    private double areaInShape(LinkedList shape){
        double area=0;
        int n=shape.size();
        int j=n-1;
        for(int i=0; i<n; i++){
            LinkedHashMap<String,Number> pi=(LinkedHashMap<String,Number>)shape.get(i);
            LinkedHashMap<String,Number> pj=(LinkedHashMap<String,Number>)shape.get(j);
            double pilat=pi.get("lat").doubleValue();
            double pilon=pi.get("lon").doubleValue();
            double pjlat=pj.get("lat").doubleValue();
            double pjlon=pj.get("lon").doubleValue();
            area+=((pjlat+pilat)/2)*(pjlon-pilon);
            j=i;
        }
        return Math.abs(area)*1e6;
    }

    private double dist(double x, double y, double a, double b){
        return Math.sqrt((x-a)*(x-a)+(y-b)*(y-b));
    }

    private void setUpdateValOnObjectUpdating(String guiuid, String tag, String val){
        WebObject o=getWebObjectUpdating(guiuid, tag);
        if(o==null) return;
        if(o instanceof User) ((User)o).setUpdateVal(guiuid,tag,val);
        else
            new Evaluator(o){ public void evaluate(){
                if(!self.contentIsOrListContains("is", "editable")) return;
                if(NetMash.top==null) return;
                String source=spawnUIDNew(NetMash.top.getRawSource());
                try{
                self.contentReplace(new JSON(source,true));
                }catch(JSON.Syntax js){ NetMash.top.toast(js.toString().split("\n")[1], true); }
                self.contentSetAdd("is", "editable");
                self.evaluate();
            }};
    }

    private String spawnUIDNew(String source){
        while(source.indexOf("uid-new")!= -1){ source=source.replace("uid-new", " "+spawnNewThing()+" "); }
        return source;
    }

    private void setUpdateValOnObjectUpdating(String guiuid, String tag, boolean val){
        User o=getObjectUpdating(guiuid, tag);
        if(o==null) return;
        o.setUpdateVal(guiuid,tag,val);
    }

    private void setUpdateValOnObjectUpdating(String guiuid, String tag, int val){
        User o=getObjectUpdating(guiuid, tag);
        if(o==null) return;
        o.setUpdateVal(guiuid,tag,val);
    }

    private void setUpdateValOnObjectUpdating(String guiuid, GeoPoint val){
        User o=getObjectUpdating(guiuid);
        if(o==null) return;
        o.setUpdateVal(guiuid,val);
    }


    private LinkedHashMap makeEditRule(String path, int etag, Object val){
        return etag>0? deephash(list(hash("Version",etag),"=>", "as-is",val), path):
                       deephash(list(                     "=>", "as-is",val), path);
    }

    private String dehash(String s){ if(s.startsWith("#")) return s.substring(1); return s; }

    // ---------------------------------------------------------

    public void evaluate(){
        if(contentIs("is", "user") && this==currentUser){
            if(!checkAroundAndShow() && !NetMash.top.editing) showWhatIAmViewing();
        }
        else
        if(contentListContainsAll("is", list("private", "contact", "list"))){
            if(!contentSet("list")) contentList("list", UserContacts.populateContacts(this));
        }
        else
        if(contentListContainsAll("is", list("editable", "rule"))){
        }
        else
        if(contentListContainsAll("is", list("document", "query"))){
            firstAlertedResponseSubscribeForUserFIXMEAndJumpUser();
        }
        else
        if(contentListContainsAll("is", list("updatable", "land"))){
            super.evaluate();
        }
        else
        if(contentIsOrListContains("is", "rsvp")){
        }
        else
        if(contentIs("is", "form")){
            firstAlertedResponseSubscribeForUserFIXMEAndJumpUser();
        }
    }

    boolean checkAroundAndShow(){
        if(!trackingAround) return false;
        refreshObserves();
        for(String alertedUid: alerted()){
            if(alertedUid.equals(content("private:links-around"))){
                return jumpToEnvironment();
            }
        }
        return false;
    }

    boolean jumpToEnvironment(){
        String nearestobj=getNearestObject();
        if(nearestobj==null){ trackingAround=false; return false; }
        trackingAround=true;

        contentTemp("nearest-object",nearestobj);

        String jumpto;
        if(contentIsOrListContains("nearest-object:is","place")){
            jumpto=nearestobj;
            contentList("target-position", getCoordsOfMiddleOfPlace());
        }
        else{
            jumpto=content("nearest-object:within");
            contentList("target-position", getCoordsOfObjectInPlace(nearestobj));
        }
        contentTemp("nearest-object",null);

        ensureUserMoveThread();

        if(jumpto!=null && !jumpto.equals(content("private:viewing"))){
            jumpToHereAndShow(jumpto,"gui",true);
            return true;
        }
        return false;
    }

    LinkedList getCoordsOfMiddleOfPlace(){
        LinkedList s=contentList("nearest-object:scale");
        return list(getFloatFromList(s,0,0)/2, 1, getFloatFromList(s,2,0)/2);
    }

    LinkedList getCoordsOfObjectInPlace(String url){
        LinkedList subObjects=contentList("nearest-object:within:sub-items");
        if(subObjects==null) return list(0,0);
        for(int i=0; i< subObjects.size(); i++){
            if(url.equals(       content(String.format("nearest-object:within:sub-items:%d:item",i)))){
                LinkedList p=contentList(String.format("nearest-object:within:sub-items:%d:position",i));
                return list(getFloatFromList(p,0,0), 1, getFloatFromList(p,2,0));
            }
        }
        return list(2,1,2);
    }

    Thread userMoveThread=null;

    void ensureUserMoveThread(){
        if(userMoveThread!=null) return;
        userMoveThread=new Thread(){ public void run(){ try{
            while(true){
                if(trackingAround) updateUserPosition();
                Kernel.sleep(trackingAround? 100: 500);
            }
        }catch(Throwable t){ t.printStackTrace(); }}};
        userMoveThread.start();
    }

    void updateUserPosition(){
        new Evaluator(this){ public void evaluate(){
            LinkedList currenpos=contentList("position");
            LinkedList targetpos=contentList("target-position");
            if(targetpos==null) return;
            float dx=getFloatFromList(targetpos, 0, 0)-getFloatFromList(currenpos, 0, 0);
            float dz=getFloatFromList(targetpos, 2, 0)-getFloatFromList(currenpos, 2, 0);
            if(dx*dx+dz*dz>5) NetMash.top.onNewMovement(dx/20,dz/20);
        }};
    }

    double currentdistance=10000;
    String currenturl=null;

    String getNearestObject(){
        LinkedList<String> urls=(LinkedList<String>)contentList("private:links-around:list");
        if(urls==null) return null;
        double closestdist=10000;
        String closesturl=null;
        for(String url: urls){
            double d=contentDouble("private:links-around:"+UID.toUID(url)+":distance");
logXX(url,d);
            if(d<closestdist){ closestdist=d; closesturl=url; }
        }
logXX("closestdist",closestdist,"currentdistance",currentdistance);
        if(closestdist<currentdistance){
            currentdistance=closestdist;
            currenturl=closesturl;
        }
        else{
            //if(closestdist>currentdistance*1.1 && closestdist<currentdistance*1.8){
                currentdistance=closestdist;
                currenturl=closesturl;
            //}
            //else currentdistance=0.8*currentdistance+0.2*closestdist;
        }
        return currenturl;
    }

    private void firstAlertedResponseSubscribeForUserFIXMEAndJumpUser(){
        for(String alertedUid: alerted()){
            if(contentSet("response")) break;
            content("response",alertedUid);
            if(contentSet("response:is")) currentUser.jumpToUID(alertedUid,"gui",false);
        }
        refreshObserves();
    }

    private void showWhatIAmViewing(){
        if(cyrus2gui==null) cyrus2gui = new Cyrus2GUI(this);
        if(content("private:viewing")==null) content("private:viewing", content("private:links"));
        if(contentIs("private:viewas","gui")){
            showWhatIAmViewingAsGUI();
        }
        else
        if(contentIs("private:viewas","map")){
            showWhatIAmViewingOnMap();
        }
        else
        if(contentIs("private:viewas","raw")){
            showWhatIAmViewingAsRawJSON();
        }
    }

    private void showWhatIAmViewingAsGUI(){
        if(contentSet("private:viewing:is")){
            LinkedHashMap viewhash=null;
            LinkedHashMap meshhash=null;
            String title=content("private:viewing:title");
            boolean editable=contentIsOrListContains("private:viewing:is","editable");
            if(contentIsOrListContains("private:viewing:is", "rule")){
                content("private:viewas","raw");
                showWhatIAmViewingAsRawJSON();
                return;
            }
            else
            if(contentIsOrListContains("private:viewing:is", "3d")){
                meshhash=cyrus2gui.scene2GUI();
            }
            else
            if(contentListContainsAll("private:viewing:is", list("user", "list"))){
                viewhash=cyrus2gui.contactList2GUI("contact:");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("contact", "list"))){
                viewhash=cyrus2gui.contactList2GUI("");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("document", "list"))||
               contentListContainsAll("private:viewing:is", list("article",  "list"))  ){
                viewhash=cyrus2gui.documentList2GUI();
            }
            else
            if(contentListContainsAll("private:viewing:is", list("link", "list"))){
                viewhash=cyrus2gui.links2GUI();
            }
            else
            if( contentIsOrListContains("private:viewing:is", "land") &&
               !contentIsOrListContains("private:viewing:is", "template")){
                viewhash=cyrus2gui.land2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "user")){
                viewhash=cyrus2gui.user2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "contact")){
                viewhash=cyrus2gui.contact2GUI(editable);
            }
            else
            if(contentIsOrListContains("private:viewing:is", "event")){
                viewhash=cyrus2gui.event2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "article") ||
               contentIsOrListContains("private:viewing:is", "chapter")   ){
                viewhash=cyrus2gui.article2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "look-up")){
                viewhash=cyrus2gui.lookup2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "gui")){
                viewhash=contentHash("private:viewing:view");
            }
            else{
                content("private:viewas","raw");
                showWhatIAmViewingAsRawJSON();
                return;
            }
            JSON uiJSON=null;
            if(viewhash!=null){
                content("within","");
                content("private:editing","");
                uiJSON=new JSON("{ \"is\": \"gui\" }");
                uiJSON.stringPath("title", title);
                uiJSON.hashPath("view", viewhash);
            }
            if(meshhash!=null){
                String viewing=content("private:viewing");
                if(!contentIs("within",viewing)){
                    content(  "within",viewing);
                    content("private:editing","");
                    notifying(viewing);
                }
                uiJSON=new JSON(meshhash);
            }
            if(NetMash.top!=null && uiJSON!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    private void showWhatIAmViewingOnMap(){
        if(contentSet("private:viewing:is")){
            LinkedList viewlist=null;
            String title=content("private:viewing:title");
            if(contentListContainsAll("private:viewing:is", list("user","list"))){
                viewlist=cyrus2gui.contactList2Map("contact:");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("contact","list"))){
                viewlist=cyrus2gui.contactList2Map("");
            }
            else
            if( contentIsOrListContains("private:viewing:is", "land") &&
               !contentIsOrListContains("private:viewing:is", "template")){
                viewlist=cyrus2gui.land2Map();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "user")){
                viewlist=cyrus2gui.contact2Map("contact:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "contact")){
                viewlist=cyrus2gui.contact2Map("");
            }
            else{
            }
            if(viewlist!=null){
                JSON uiJSON=new JSON("{ \"is\": \"gui\" }");
                uiJSON.stringPath("title", title);
                uiJSON.listPath("view", viewlist);
                if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
            }
        }
    }

    private void showWhatIAmViewingAsRawJSON(){
        if(contentSet("private:viewing:is")){
            String title=content("private:viewing:title");
            boolean editable=contentIsOrListContains("private:viewing:is","editable");
            LinkedHashMap viewhash=cyrus2gui.guifyHash(content("private:viewing"),contentHash("private:viewing:#"), editable);
            content("within","");
            content("private:editing","");
            JSON uiJSON=new JSON("{ \"is\": \"gui\" }");
            uiJSON.stringPath("title", title);
            uiJSON.hashPath("view", viewhash);
            if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    // ---------------------------------------------------------
}

