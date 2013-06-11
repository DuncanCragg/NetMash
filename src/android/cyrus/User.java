
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

import cyrus.gui.Cyrus;
import cyrus.gui.Mesh;
import cyrus.lib.*;
import cyrus.forest.*;
import cyrus.platform.Kernel;

import static cyrus.lib.Utils.*;

/** User viewing the Object Web.
  */
public class User extends CyrusLanguage {

    // ---------------------------------------------------------

    static public User currentUser=null;

    static public void createUserAndDevice(){

        String fullName=UserContacts.getUsersFullName();
        String your=fullName.equals("You")? "Your": fullName+"'s";

        CyrusLanguage contact = new CyrusLanguage(
              "{ \"is\": [ \"editable\", \"contact\" ], \n"+
              "  \"full-name\": \""+fullName+"\", \n"+
              "  \"address\": { } \n"+
              "}");

        CyrusLanguage links = new CyrusLanguage(
              "{ \"is\": [ \"link\", \"list\", \"editable\" ], \n"+
              "  \"list\": null \n"+
              "}");

        LinkedList cyruslinks=Kernel.config.listPathN("cyrus:links");
        links.publicState.listPath("list", cyruslinks);

        User contacts = new User(
              "{ \"is\": [ \"private\", \"contact\", \"list\" ], \n"+
              "  \"title\": \"Phone Contacts\", \n"+
              "  \"list\": null \n"+
              "}");

        // -----------------------------------------------------

        String homeusers=Kernel.config.stringPathN("cyrus:homeusers");
        currentUser = new User(homeusers, contact.uid, links.uid, contacts.uid);

        cyruslinks.addFirst(currentUser.uid);

        currentUser.funcobs.setCacheNotifyAndSaveConfig(currentUser);

        currentUser.funcobs.cacheSaveAndEvaluate(contact, true);
        currentUser.funcobs.cacheSaveAndEvaluate(links);
        currentUser.funcobs.cacheSaveAndEvaluate(contacts);
        currentUser.funcobs.cacheSaveAndEvaluate(currentUser, true);

        if(homeusers!=null) currentUser.notifying(list(homeusers));
        Cyrus.top.onUserReady(currentUser);
    }

    public User(String jsonstring){ super(jsonstring); }

    public User(JSON json){ super(json); }

    public User(String homeusers, String contactuid, String linksuid, String contactsuid){
        super("{   \"is\": \"user\", \n"+
              "    \"homeusers\": \""+homeusers+"\", \n"+
              "    \"saying\": \"\", \n"+
              "    \"within\": null, \n"+
              "    \"position\": [ 0.0, 0.0, 0.0 ], \n"+
              "    \"avatar\": \"http://10.0.2.2:8082/o/uid-7794-3aa8-2192-7a60.json\", \n"+
              "    \"location\": { \"lat\": 54.106037, \"lon\": -1.579163 }, \n"+
              "    \"contact\": \""+contactuid+"\", \n"+
              "    \"private\": { \n"+
              "        \"viewing\": null, \n"+
              "        \"editing\": null, \n"+
              "        \"viewas\": \"gui\", \n"+
              "        \"links\": \""+linksuid+"\", \n"+
              "        \"history\": null, \n"+
              "        \"contacts\":  \""+contactsuid+"\", \n"+
              "        \"responses\": { }, \n"+
              "        \"position\": { } \n"+
              "    }\n"+
              "}");
    }

    public User(){ if(currentUser==null){ currentUser=this; if(Cyrus.top!=null) Cyrus.top.onUserReady(currentUser); } }

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

    static User newSwipe(String itemuid, String useruid, float dx, float dy){
        return new User("{ \"is\": \"swipe\", \n"+
                        "  \"item\": \""+itemuid+"\",\n"+
                        "  \"user\": \""+useruid+"\",\n"+
                        "  \"dx\": \""+dx+"\",\n"+
                        "  \"dy\": \""+dy+"\"\n"+
                        "}");
    }

    // ---------------------------------------------------------

    Cyrus2GUI cyrus2gui;
    CurrentLocation currentlocation=null;
    boolean trackGPS=true;

    public void onTopCreate(String url){
        if(trackGPS) currentlocation = new CurrentLocation(this);
        if(url!=null) jumpToUID(url,"gui",false);
    }

    public void onTopResume(){
        new Evaluator(this){
            public void evaluate(){
                showWhatIAmViewing();
            }
        };
        if(currentlocation!=null) currentlocation.getLocationUpdates();
    }

    public void onTopPause(){
        if(currentlocation!=null) currentlocation.stopLocationUpdates();
    }

    public void onTopDestroy(){
    }

    // ---------------------------------------------------------

    void onNewLocation(final Location location){
        new Evaluator(this){
            public void evaluate(){
                if(false) log("location: "+location);
                contentDouble("location:lat", location.getLatitude());
                contentDouble("location:lon", location.getLongitude());
                contentDouble("location:acc", location.getAccuracy());
                content(      "location:prv", location.getProvider());
            }
        };
    }

    // ---------------------------------------------------------

    public ConcurrentHashMap<String,Bitmap> textBitmaps = new ConcurrentHashMap<String,Bitmap>();

    public ConcurrentHashMap<String,String> shaders = new ConcurrentHashMap<String,String>();

    public ConcurrentHashMap<Integer,String> mesh2uid = new ConcurrentHashMap<Integer,String>();

    // ---------------------------------------------------------

    private long earliest=0;
    private boolean waiting=false;

    private float lastx,lasty,lastz;
    private LinkedHashMap lastmesh=null;
    private boolean lastedit;
    private float lastdx, lastdy;

    synchronized public void onObjectTouched(LinkedHashMap mesh, final boolean edit, final float dx, final float dy){
        if(mesh!=lastmesh){ lastdx=0; lastdy=0; earliest=0; waiting=false; }
        lastmesh=mesh; lastedit=edit; lastdx+=dx; lastdy+=dy;
        final long updated=System.currentTimeMillis();
        final User self=this;
        if(waiting) return;
        if(updated<earliest){
            waiting=true;
            new Thread(){ public void run(){
                Kernel.sleep(earliest-updated);
                synchronized(self){
                    waiting=false;
                    if(lastdx+lastdy==0) return;
                    float ldx=lastdx,ldy=lastdy;
                    lastdx=0; lastdy=0;
                    onObjectTouched(lastmesh,lastedit,ldx,ldy);
                }
            }}.start();
            return;
        }
        final float ndx=lastdx, ndy=lastdy;
        lastdx=0; lastdy=0;
        earliest=updated+500;
        final String objectuid=mesh2uid.get(System.identityHashCode(mesh));
        if(objectuid==null) return;
        if(false) log("touched item: "+mesh.get("title")+(edit? " edit": " send")+" uid: "+objectuid+" "+ndx+" "+ndy);
        if(ndx+ndy==0){
// not in Evaluate.
//          history.forward();
//          content("private:viewing",objectuid);
//          content("private:viewas", "raw");
//          showWhatIAmViewing();
        }
        else if(objectuid.equals("editing")){
            String edituid=content("private:editing");
            if(ndy*ndy>ndx*ndx/2) getObjectUpdating(edituid, "", true).setEditVal(edituid,ndy);
            else if(Cyrus.top!=null) Cyrus.top.getKeys(ndx>0);
        }
        else new Evaluator(this){
            public void evaluate(){
                if(edit){
                    setResponse(objectuid, true, 0,0);
                    content("private:editing",objectuid);
                    showWhatIAmViewing();
                }
                else{
                    if(!setResponse(objectuid, false, ndx/30, ndy/30)) getObjectUpdating(objectuid).setSwipeVal(objectuid, ndx/30, ndy/30);
                }
            }
        };
    }

    public void setEditVal(final String edituid, final float d){
        new Evaluator(this){
            public void evaluate(){
                if(contentListContainsAll("is", list("editable", "rule"))){
                    LinkedList oldscale=contentList("editable:scale");
                    LinkedList newscale=list(getFloatFromList(oldscale,0,1)*(1f+d/10f),
                                             getFloatFromList(oldscale,1,1)*(1f+d/10f),
                                             getFloatFromList(oldscale,2,1)*(1f+d/10f));
                    LinkedHashMap rule=makeEditRule("scale",0,newscale);
                    contentMerge(rule);
                    notifying(edituid);
                }
            }
        };
    }

    public void setSwipeVal(final String objectuid, final float dx, final float dy){
        new Evaluator(this){
            public void evaluate(){
                contentDouble("dx", dx);
                contentDouble("dy", dy);
                notifying(objectuid);
            }
        };
    }

    synchronized public void onNewPosition(final float x, final float y, final float z){
        lastx=x; lasty=y; lastz=z;
        final long updated=System.currentTimeMillis();
        final User self=this;
        if(waiting) return;
        if(updated<earliest){
            waiting=true;
            new Thread(){ public void run(){
                Kernel.sleep(earliest-updated);
                synchronized(self){
                    waiting=false;
                    onNewPosition(lastx,lasty,lastz);
                }
            }}.start();
            return;
        }
        earliest=updated+1000;
        new Evaluator(this){
            public void evaluate(){
                LinkedList newplace=findNewPlaceNearer(x,y,z);
                if(newplace==null){
                    LinkedList newposn=list(x,y,z);
                    contentList("position", newposn);
                    contentList("private:position:"+UID.toUID(content("private:viewing")), newposn);
                }
                else{
                    String     newplaceuid=(String)    newplace.get(0);
                    LinkedList newposn  =(LinkedList)newplace.get(1);
                    contentList("position", newposn);
                    contentList("private:position:"+UID.toUID(newplaceuid), newposn);
                    jumpToHereAndShow(newplaceuid,"gui");
                    if(Cyrus.top!=null && Cyrus.top.onerenderer!=null) Cyrus.top.onerenderer.resetPositionAndView(newposn);
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
            float px=getFloatFromList(placeposn,0,0);
            float py=getFloatFromList(placeposn,1,0);
            float pz=getFloatFromList(placeposn,2,0);
            float dx=ux-px; float dy=uy-py; float dz=uz-pz;
            float d=FloatMath.sqrt(dx*dx+dy*dy+dz*dz);
            if(d<10) return list(content(String.format("within:sub-items:%d:item",i)), list(dx,dy,dz));
        }
        return null;
    }

    // ---------------------------------------------------------

    class View{
        String uid,as; View(String u, String a){ uid=u; as=a; }
        public String toString(){ return String.format("View(%s, %s)", uid, as); }
        public boolean equals(Object v){ return (v instanceof View) && uid.equals(((View)v).uid) && as.equals(((View)v).as); }
    };
    class History extends Stack<View>{
        User user;
        public History(User u){ this.user=u; }
        public void forward(){
            View n=new View(user.content("private:viewing"), user.content("private:viewas"));
            int i=search(n);
            if(empty() || i == -1 || i >1) push(n);
        }
        public boolean back(){
            if(history.empty()) return false;
            View view=history.pop();
            user.content("private:viewing", view.uid);
            user.content("private:viewas",  view.as);
            LinkedList newposn=contentList("private:position:"+UID.toUID(view.uid));
            contentList("position", newposn);
            if(newposn!=null && Cyrus.top!=null && Cyrus.top.onerenderer!=null) Cyrus.top.onerenderer.resetPositionAndView(newposn);
            return true;
        }
        public String toString(){
            StringBuilder sb=new StringBuilder();
            sb.append("[ ");
            for(View v: this){ sb.append(v.uid); sb.append("-"); sb.append(v.as); sb.append(" "); }
            sb.append("]");
            return sb.toString();
        }
    };
    private History history = new History(this);

    public void jumpToUID(final String uid){
        jumpToUID(uid, null, false);
    }

    public void jumpToUID(String uid, final String mode, final boolean relativeToViewing){
        final String jumpuid;
        if(oneOfOurs(uid))    jumpuid=UID.toUID(uid); else
        if(relativeToViewing) jumpuid=UID.normaliseUID(content("private:viewing"),uid);
        else                  jumpuid=uid;
        new Evaluator(this){
            public void evaluate(){
                jumpToHereAndShow(jumpuid,mode);
            }
        };
    }

    private void jumpToHereAndShow(String uid, String mode){
        history.forward();
        content("private:viewing", uid);
        if(mode!=null) content("private:viewas", mode);
        showWhatIAmViewing();
    }

    public void jumpBack(){
        new Evaluator(this){
            public void evaluate(){
                if(!history.back()) return;
                showWhatIAmViewing();
            }
        };
    }

    public boolean menuItem(final int itemid){
        new Evaluator(this){
            public void evaluate(){
                switch(itemid){
                    case Cyrus.MENU_ITEM_ADD:
                    break;
                    case Cyrus.MENU_ITEM_LNX:
                        history.forward();
                        content("private:viewing", content("private:links"));
                        content("private:viewas", "gui");
                        showWhatIAmViewing();
                    break;
                    case Cyrus.MENU_ITEM_GUI:
                        history.forward();
                        content("private:viewas", "gui");
                        showWhatIAmViewing();
                    break;
                    case Cyrus.MENU_ITEM_MAP:
                        history.forward();
                        content("private:viewas", "map");
                        showWhatIAmViewing();
                    break;
                    case Cyrus.MENU_ITEM_RAW:
                        history.forward();
                        content("private:viewas", "raw");
                        showWhatIAmViewing();
                    break;
                }
            }
        };
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

    public LinkedList getPosition(final String guiuid){
        final LinkedList[] val=new LinkedList[1];
        new Evaluator(this){
            public void evaluate(){
                String path="private:position:"+UID.toUID(guiuid);
                val[0]=contentList(path);
                if(val[0]==null){
                   val[0]=list(0,0.7,3);
                   contentList(path,val[0]);
                }
                contentList("position", val[0]);
            }
        };
        return val[0];
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
                    val[0]=contentBool("private:responses:form:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
                else val[0]=null;
            }
        };
        return val[0];
    }

    private boolean setResponse(String guiuid){ return setResponse(guiuid, false, 0,0); }

    private boolean setResponse(String guiuid, boolean editable, float dx, float dy){
        User resp=null;
        String path=null;
        editable=editable || contentIs("private:viewas","raw");
        if(editable){
        if(contentIsOrListContains("private:viewing:is", "editable")){
            if(!oneOfOurs(guiuid)){
                path="private:responses:editable:"+UID.toUID(guiuid);
                if(contentSet(path)) return false;
                if(!contentSet("private:responses:editable")) contentHash("private:responses:editable", hash());
                resp=newEditableRule(guiuid, uid);
            }
        }
        }
        else if(contentIsOrListContains("private:viewing:is", "3d")){
            path="private:responses:swipe:"+UID.toUID(guiuid);
            if(contentSet(path)) return false;
            if(!contentSet("private:responses:swipe")) contentHash("private:responses:swipe", hash());
            resp=newSwipe(guiuid, uid, dx, dy);
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

    private User getObjectUpdating(String guiuid){ return (User)getWebObjectUpdating(guiuid, "", false); }

    private User getObjectUpdating(String guiuid, String tag){ return (User)getWebObjectUpdating(guiuid, tag, false); }

    private User getObjectUpdating(String guiuid, String tag, boolean editable){ return (User)getWebObjectUpdating(guiuid, tag, editable); }

    private WebObject getWebObjectUpdating(String guiuid, String tag, boolean editable){
        String formuid=null;
        editable=editable || contentIs("private:viewas","raw");
        if(editable){
        if(contentIsOrListContains("private:viewing:is", "editable")){
            if(!oneOfOurs(guiuid)) formuid=content("private:responses:editable:"+UID.toUID(guiuid));
            else                   formuid=guiuid;
        }
        }
        else if(contentIsOrListContains("private:viewing:is", "3d")){
            formuid=content("private:responses:swipe:"+UID.toUID(guiuid));
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
        if(formuid==null) return null;
        return onlyUseThisToHandControlOfThreadToDependent(formuid);
    }

    public void setUpdateVal(final String guiuid, final String tag, final String val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){
                if(contentListContainsAll("is", list("editable", "rule"))){
                    LinkedHashMap rule=null;
                    try{
                        JSON json=new JSON(Cyrus.top.getRawSource(),true);
                        json.setPathAdd("is", "editable");
                        rule=makeEditRule("",contentInt("editable:Version"),json);
                    }catch(JSON.Syntax js){ Cyrus.top.toast(js.toString().split("\n")[1]); }
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
            }
        };
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
        WebObject o=getWebObjectUpdating(guiuid, tag, false);
        if(o==null) return;
        if(o instanceof User) ((User)o).setUpdateVal(guiuid,tag,val);
        else
            new Evaluator(o){ public void evaluate(){
                if(!self.contentIsOrListContains("is", "editable")) return;
                if(Cyrus.top==null) return;
                String source=spawnUIDNew(Cyrus.top.getRawSource());
                try{
                self.contentReplace(new JSON(source,true));
                }catch(JSON.Syntax js){ Cyrus.top.toast(js.toString().split("\n")[1]); }
                self.contentSetAdd("is", "editable");
                self.evaluate();
            }};
    }

    private String spawnUIDNew(String source){
        while(source.indexOf("uid-new")!= -1){ source=source.replace("uid-new", " "+spawn(new CyrusLanguage("{ \"is\": [ \"editable\" ] }"))+" "); }
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
            showWhatIAmViewing();
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
        if(contentIsOrListContains("is", "swipe")){
            notifying(content("item"));
        }
        else
        if(contentIs("is", "form")){
            firstAlertedResponseSubscribeForUserFIXMEAndJumpUser();
        }
        else log("no evaluate: "+this);
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
            if(Cyrus.top!=null && uiJSON!=null) Cyrus.top.drawJSON(uiJSON, content("private:viewing"));
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
                if(Cyrus.top!=null) Cyrus.top.drawJSON(uiJSON, content("private:viewing"));
            }
        }
    }

    private void showWhatIAmViewingAsRawJSON(){
        if(contentSet("private:viewing:is")){
            String title=content("private:viewing:title");
            boolean editable=contentIsOrListContains("private:viewing:is","editable");
            LinkedHashMap viewhash=cyrus2gui.guifyHash(contentHash("private:viewing:#"), editable);
            viewhash.put("#uid", "uid: "+content("private:viewing"));
            content("within","");
            content("private:editing","");
            JSON uiJSON=new JSON("{ \"is\": \"gui\" }");
            uiJSON.stringPath("title", title);
            uiJSON.hashPath("view", viewhash);
            if(Cyrus.top!=null) Cyrus.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    // ---------------------------------------------------------
}

