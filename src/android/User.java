
package android;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

import android.gui.*;
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

import netmash.lib.*;
import netmash.forest.*;
import netmash.platform.Kernel;
import static netmash.lib.Utils.*;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    // ---------------------------------------------------------

    static public User currentUser=null;

    static public void createUserAndDevice(){

        String fullName=UserContacts.getUsersFullName();
        String your=fullName.equals("You")? "Your": fullName+"'s";

        User contact = new User(
              "{ \"is\": \"contact\", \n"+
              "  \"fullName\": \""+fullName+"\", \n"+
              "  \"address\": { } \n"+
              "}");

        User links = new User(
              "{ \"is\": [ \"links\" ], \n"+
              "  \"list\": null \n"+
              "}");

        LinkedList otslinks=Kernel.config.listPathN("ots:links");
        links.publicState.listPath("list", otslinks);

        User contacts = new User(
              "{ \"is\": [ \"private\", \"contact\", \"list\" ], \n"+
              "  \"title\": \"Phone Contacts\", \n"+
              "  \"list\": null \n"+
              "}");

        // -----------------------------------------------------

        String homeusers=Kernel.config.stringPathN("ots:homeusers");
        currentUser = new User(homeusers, contact.uid, links.uid, contacts.uid);

        otslinks.addFirst(currentUser.uid);

        currentUser.funcobs.setCacheNotifyAndSaveConfig(currentUser);
        currentUser.funcobs.cacheSaveAndEvaluate(contact, true);
        currentUser.funcobs.cacheSaveAndEvaluate(links);
        currentUser.funcobs.cacheSaveAndEvaluate(contacts);
        currentUser.funcobs.cacheSaveAndEvaluate(currentUser, true);

        if(homeusers!=null) currentUser.notifying(list(homeusers));
        NetMash.top.onUserReady(currentUser);
    }

    public User(String jsonstring){ super(jsonstring); }

    public User(JSON json){ super(json); }

    public User(String homeusers, String contactuid, String linksuid, String contactsuid){
        super("{   \"is\": \"user\", \n"+
              "    \"homeusers\": \""+homeusers+"\", \n"+
              "    \"saying\": \"\", \n"+
              "    \"place\": null, \n"+
              "    \"coords\": [ 0.0, 1.5, 0.0 ], \n"+
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
              "        \"responses\": { } \n"+
              "    }\n"+
              "}");
    }

    public User(){ if(currentUser==null){ currentUser=this; if(NetMash.top!=null) NetMash.top.onUserReady(currentUser); } }

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


    static User newLand(String landlistuid, String useruid, LinkedHashMap location){
        User land=new User(
                        "{ \"is\": [ \"updatable\", \"land\" ],\n"+
                        "  \"place\": \""+landlistuid+"\",\n"+
                        "  \"user\": \""+useruid+"\"\n"+
                        "}");
        land.publicState.hashPath("location", location);
        return land;
    }

    static User newRSVP(String eventuid, String useruid){
        return new User("{ \"is\": \"rsvp\", \n"+
                        "  \"event\": \""+eventuid+"\",\n"+
                        "  \"user\": \""+useruid+"\"\n"+
                        "}");
    }

    static User newSwipe(String objectuid, String useruid, float dx, float dy){
        return new User("{ \"is\": \"swipe\", \n"+
                        "  \"object\": \""+objectuid+"\",\n"+
                        "  \"user\": \""+useruid+"\",\n"+
                        "  \"dx\": \""+dx+"\",\n"+
                        "  \"dy\": \""+dy+"\"\n"+
                        "}");
    }

    // ---------------------------------------------------------

    OTS2GUI ots2gui;
    CurrentLocation currentlocation=null;
    boolean trackGPS=true;

    public void onTopCreate(String url){
        if(trackGPS) currentlocation = new CurrentLocation(this);
        if(url!=null) jumpToUID(url);
    }

    public void onTopResume(){
        new Evaluator(this){
            public void evaluate(){ logrule();
                showWhatIAmViewing();
                refreshObserves();
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
            public void evaluate(){ logrule();
                log("location: "+location);
                contentDouble("location:lat", location.getLatitude());
                contentDouble("location:lon", location.getLongitude());
                contentDouble("location:acc", location.getAccuracy());
                content(      "location:prv", location.getProvider());
                refreshObserves();
            }
        };
    }

    // ---------------------------------------------------------

    public ConcurrentHashMap<String,Bitmap> textBitmaps = new ConcurrentHashMap<String,Bitmap>();

    public ConcurrentHashMap<String,LinkedList> shaders = new ConcurrentHashMap<String,LinkedList>();

    public ConcurrentHashMap<Integer,String> mesh2uid = new ConcurrentHashMap<Integer,String>();

    // ---------------------------------------------------------

    public void onObjectTouched(LinkedHashMap mesh, final boolean edit, final float dx, final float dy){
        final String objectuid=mesh2uid.get(System.identityHashCode(mesh));
logZero("touched object: "+mesh.get("title")+", "+(edit? "edit": "send")+" uid:"+objectuid);
        if(objectuid==null) return;
        if(objectuid.equals("editing")){
            String edituid=content("private:editing");
            if(dy*dy>dx*dx/2) getObjectUpdating(edituid, "", true).setEditVal(edituid,dy);
            else if(NetMash.top!=null) NetMash.top.getKeys(dx>0);
        }
        else new Evaluator(this){
            public void evaluate(){
                if(edit){
                    setResponse(objectuid, true, 0,0);
                    content("private:editing",objectuid);
                    showWhatIAmViewing();
                }
                else{
                    if(!setResponse(objectuid, false, dx/10, dy/10)) getObjectUpdating(objectuid).setSwipeVal(objectuid, dx/10, dy/10);
                }
                refreshObserves();
            }
        };
    }

    public void setEditVal(final String edituid, final float d){
        new Evaluator(this){
            public void evaluate(){
                if(contentListContainsAll("is", list("editable", "rule"))){
                    LinkedList oldscale=contentList("editable:scale");
                    LinkedList newscale=list(Mesh.getFloatFromList(oldscale,0,1)*(1f+d/10f),
                                             Mesh.getFloatFromList(oldscale,1,1)*(1f+d/10f),
                                             Mesh.getFloatFromList(oldscale,2,1)*(1f+d/10f));
                    LinkedHashMap rule=makeEditRule("scale",newscale);
                    contentMerge(rule);
                    notifying(edituid);
                    refreshObserves();
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
                refreshObserves();
            }
        };
    }

    private float px=0,py=0,pz=0;
    public void onNewCoords(final float x, final float y, final float z){
        float dx=x-px, dy=y-py, dz=z-pz;
        if(dx*dx+dy*dy+dz*dz<1.0) return;
        new Evaluator(this){
            public void evaluate(){
                contentList("coords", list(x,y,z));
                px=x; py=y; pz=z;
                String newplaceuid=findNewPlaceNearer();
                if(newplaceuid!=null){
                    history.forward();
                    content("private:viewing", newplaceuid);
                    content("private:viewas", "gui");
                    showWhatIAmViewing();
                }
                refreshObserves();
            }
        };
    }

    private String findNewPlaceNearer(){
        LinkedList usercoords=contentList("coords");
        float ux=Mesh.getFloatFromList(usercoords, 0,0);
        float uy=Mesh.getFloatFromList(usercoords, 1,0);
        float uz=Mesh.getFloatFromList(usercoords, 2,0);
        LinkedList subObjects=contentList("place:subObjects");
        if(subObjects==null) return null;
        for(int i=0; i< subObjects.size(); i++){
            String objispath=String.format("place:subObjects:%d:object:is",i);
            if(!contentListContains(objispath,"place")) continue;
            LinkedList placecoords=contentList(String.format("place:subObjects:%d:coords",i));
            float px=Mesh.getFloatFromList(placecoords,0,0);
            float py=Mesh.getFloatFromList(placecoords,1,0);
            float pz=Mesh.getFloatFromList(placecoords,2,0);
            float dx=ux-px; float dy=uy-py; float dz=uz-pz;
            float d=FloatMath.sqrt(dx*dx+dy*dy+dz*dz);
            if(d<10){
                if(NetMash.top!=null) NetMash.top.onerenderer.resetCoordsAndView(dx,dy,dz);
                contentList("coords", list(dx,dy,dz));
                return content(String.format("place:subObjects:%d:object",i));
            }
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
        jumpToUID(uid, false);
    }

    public void jumpToUID(final String uid, final boolean relativeToViewing){
        new Evaluator(this){
            public void evaluate(){ if(false) logrule();
                history.forward();
                content("private:viewing", relativeToViewing? UID.normaliseUID(content("private:viewing"),uid): uid);
                content("private:viewas", "gui");
                showWhatIAmViewing();
                refreshObserves();
            }
        };
    }

    public void jumpBack(){
        new Evaluator(this){
            public void evaluate(){ logrule();
                if(!history.back()) return;
                showWhatIAmViewing();
                refreshObserves();
            }
        };
    }

    public boolean menuItem(final int itemid){
        new Evaluator(this){
            public void evaluate(){ logrule();
                switch(itemid){
                    case NetMash.MENU_ITEM_ADD:
                    break;
                    case NetMash.MENU_ITEM_LNX:
                        history.forward();
                        content("private:viewing", content("private:links"));
                        content("private:viewas", "gui");
                        showWhatIAmViewing();
                    break;
                    case NetMash.MENU_ITEM_GUI:
                        history.forward();
                        content("private:viewas", "gui");
                        showWhatIAmViewing();
                    break;
                    case NetMash.MENU_ITEM_MAP:
                        history.forward();
                        content("private:viewas", "map");
                        showWhatIAmViewing();
                    break;
                    case NetMash.MENU_ITEM_RAW:
                        history.forward();
                        content("private:viewas", "raw");
                        showWhatIAmViewing();
                    break;
                }
                refreshObserves();
            }
        };
        return true;
    }

    // ---------------------------------------------------------

    public void prepareResponse(final String guiuid){
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
                refreshObserves();
            }
        };
    }

    private boolean setResponse(String guiuid){ return setResponse(guiuid, false, 0,0); }

    private boolean setResponse(String guiuid, boolean editable, float dx, float dy){
        User resp=null;
        String path=null;
        editable=editable || contentIs("private:viewas","raw");
        if(editable){
        if(contentIsOrListContains("private:viewing:is", "editable")){
            path="private:responses:editable:"+UID.toUID(guiuid);
            if(contentSet(path)) return false;
            if(!contentSet("private:responses:editable")) contentHash("private:responses:editable", hash());
            resp=newEditableRule(guiuid, uid);
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
        else if(contentListContainsAll("private:viewing:is", list("attendable","event"))){
            path="private:responses:rsvp:"+UID.toUID(guiuid);
            if(contentSet(path)) return false;
            if(!contentSet("private:responses:rsvp")) contentHash("private:responses:rsvp", hash());
            resp=newRSVP(guiuid, uid);
        }
        else if(contentListContainsAll("private:viewing:is", list("updatable", "land", "list"))){
            path="private:responses:land:"+UID.toUID(guiuid);
            if(contentSet(path) && !contentSet(path+":title")) return false;
            if(!contentSet("private:responses:land")) contentHash("private:responses:land", hash());
            resp=newLand(guiuid, uid, contentHashClone("location"));
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
            if(!contentSet("private:responses:land")) contentHash("private:responses:land", hash());
            resp=newLand(guiuid, uid, contentHashClone("location"));
        }
        if(resp!=null) content(path, spawn(resp));
        return true;
    }

    private User getObjectUpdating(String guiuid){ return getObjectUpdating(guiuid, "", false); }

    private User getObjectUpdating(String guiuid, String tag){ return getObjectUpdating(guiuid, tag, false); }

    private User getObjectUpdating(String guiuid, String tag, boolean editable){
        String formuid=null;
        editable=editable || contentIs("private:viewas","raw");
        if(editable){
        if(contentIsOrListContains("private:viewing:is", "editable")){
            formuid=content("private:responses:editable:"+UID.toUID(guiuid));
        }
        }
        else if(contentIsOrListContains("private:viewing:is", "3d")){
            formuid=content("private:responses:swipe:"+UID.toUID(guiuid));
        }
        else if(contentListContainsAll("private:viewing:is", list("searchable", "document", "list"))){
            formuid=content("private:responses:query:"+UID.toUID(guiuid));
        }
        else if(contentListContainsAll("private:viewing:is", list("attendable","event"))){
            formuid=content("private:responses:rsvp:"+UID.toUID(guiuid));
        }
        else if(contentListContainsAll("private:viewing:is", list("updatable", "land", "list"))){
            formuid=content("private:responses:land:"+UID.toUID(guiuid));
        }
        else if(contentIsOrListContains("private:viewing:is", "gui")){
            formuid=content("private:responses:form:"+UID.toUID(guiuid));
        }
        else if(contentIsOrListContains("private:viewing:is", "land")){
            if(dehash(tag).equals("new")) formuid=content("private:responses:land:"+UID.toUID(guiuid));
            else                          formuid=UID.toUID(guiuid);
        }
        if(formuid==null) return null;
        Object o=onlyUseThisToHandControlOfThreadToDependent(formuid);
        if(o instanceof User) return (User)o;
        log("Not a User: "+formuid+" "+o);
        return null;
    }

    public String getFormStringVal(final String guiuid, final String tag){
        final String[] val=new String[1];
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
                if(contentIsOrListContains("private:viewing:is", "gui")){
                    val[0]=content("private:responses:form:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
                else val[0]=null;
                refreshObserves();
            }
        };
        return val[0];
    }

    public Boolean getFormBooleanVal(final String guiuid, final String tag){
        final Boolean[] val=new Boolean[1];
        new Evaluator(this){
            public void evaluate(){
                setResponse(guiuid);
                if(contentIsOrListContains("private:viewing:is", "gui")){
                    val[0]=contentBool("private:responses:form:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
                else val[0]=null;
                refreshObserves();
            }
        };
        return val[0];
    }

    public void setUpdateVal(final String guiuid, final String tag, final String val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){ logrule();
                if(contentListContainsAll("is", list("editable", "rule"))){
                    LinkedHashMap rule=makeEditRule(tag.substring("#val-".length()),val);
                    contentMerge(rule);
                }
                else
                if(contentListContainsAll("is", list("document", "query"))){
                    content("content", String.format("<hasWords(%s)>",val));
                }
                else
                if(contentIsOrListContains("is", "land")){
                    if(tag.equals("#new")) content("title",val);
                    else                   content(dehash(tag), val);
                }
                else
                if(contentIsOrListContains("is", "form")){
                    content("form:"+dehash(tag), val);
                }
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    public void setUpdateVal(final String guiuid, final String tag, final boolean val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){ logrule();
                if(contentIsOrListContains("is", "rsvp")){
                    content("attending", val? "yes": "no");
                }
                else
                if(contentIsOrListContains("is", "land")){
                    contentBool(dehash(tag), val);
                }
                else
                if(contentIsOrListContains("is", "form")){
                    contentBool("form:"+dehash(tag), val);
                }
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    public void setUpdateVal(final String guiuid, final String tag, final int val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){ logrule();
                if(contentIsOrListContains("is", "land")){
                    contentInt(dehash(tag), val);
                }
                else
                if(contentIsOrListContains("is", "form")){
                    contentInt("form:"+dehash(tag), val);
                }
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    public void setUpdateVal(final String guiuid, final GeoPoint val){
        if(this==currentUser) setUpdateValOnObjectUpdating(guiuid, val);
        else new Evaluator(this){
            public void evaluate(){ logrule();
                if(contentIsOrListContains("is", "land")){
                    contentDouble("location:lat", val.getLatitudeE6()/1e6);
                    contentDouble("location:lon", val.getLongitudeE6()/1e6);
                }
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    private void setUpdateValOnObjectUpdating(String guiuid, GeoPoint val){
        User o=getObjectUpdating(guiuid);
        if(o==null) return;
        o.setUpdateVal(guiuid,val);
    }

    private void setUpdateValOnObjectUpdating(String guiuid, String tag, String val){
        User o=getObjectUpdating(guiuid, tag);
        if(o==null) return;
        o.setUpdateVal(guiuid,tag,val);
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

    private LinkedHashMap makeEditRule(String path, Object val){
        return deephash(list("=>",val), path);
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
        if(contentIsOrListContains("is", "land")){
        }
        else
        if(contentIsOrListContains("is", "rsvp")){
        }
        else
        if(contentIsOrListContains("is", "swipe")){
            notifying(content("object"));
        }
        else
        if(contentIs("is", "form")){
            firstAlertedResponseSubscribeForUserFIXMEAndJumpUser();
        }
        else log("no evaluate: "+this);
    }

    private void firstAlertedResponseSubscribeForUserFIXMEAndJumpUser(){
        for(String alertedUid: alerted()){
            content("response",alertedUid);
            if(contentSet("response:is")) currentUser.jumpToUID(alertedUid);
        }
        refreshObserves();
    }

    private void showWhatIAmViewing(){
        if(ots2gui==null) ots2gui = new OTS2GUI(this);
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
            if(contentListContainsAll("private:viewing:is", list("user", "list"))){
                viewhash=ots2gui.contactList2GUI("contact:");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("contact", "list"))){
                viewhash=ots2gui.contactList2GUI("");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("document", "list"))||
               contentListContainsAll("private:viewing:is", list("article",  "list"))  ){
                viewhash=ots2gui.documentList2GUI();
            }
            else
            if(contentListContainsAll("private:viewing:is", list("land", "list"))){
                viewhash=ots2gui.landList2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "land")){
                viewhash=ots2gui.land2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "user")){
                viewhash=ots2gui.user2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "contact")){
                viewhash=ots2gui.contact2GUI(editable);
            }
            else
            if(contentIsOrListContains("private:viewing:is", "event")){
                viewhash=ots2gui.event2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "article") ||
               contentIsOrListContains("private:viewing:is", "chapter")   ){
                viewhash=ots2gui.article2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "links")){
                viewhash=ots2gui.links2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "look-up")){
                viewhash=ots2gui.lookup2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "gui")){
                viewhash=contentHash("private:viewing:view");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "3d")){
                meshhash=ots2gui.scene2GUI();
            }
            else{
                content("private:viewas","raw");
                showWhatIAmViewingAsRawJSON();
                return;
            }
            JSON uiJSON=null;
            if(viewhash!=null){
                content("place","");
                content("private:editing","");
                uiJSON=new JSON("{ \"is\": \"gui\" }");
                uiJSON.stringPath("title", title);
                uiJSON.hashPath("view", viewhash);
            }
            if(meshhash!=null){
                String viewing=content("private:viewing");
                if(!contentIs("place",viewing)){
                    content(  "place",viewing);
                    content("private:editing","");
                    notifying(viewing);
                }
                uiJSON=new JSON(meshhash);
            }
            if(NetMash.top!=null && uiJSON!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    private void showWhatIAmViewingOnMap(){ logrule();
        if(contentSet("private:viewing:is")){
            LinkedList viewlist=null;
            if(contentListContainsAll("private:viewing:is", list("user","list"))){
                viewlist=ots2gui.contactList2Map("contact:");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("contact","list"))){
                viewlist=ots2gui.contactList2Map("");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("land","list"))){
                viewlist=ots2gui.landList2Map();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "land")){
                viewlist=ots2gui.land2Map();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "user")){
                viewlist=ots2gui.contact2Map("contact:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "contact")){
                viewlist=ots2gui.contact2Map("");
            }
            else{
            }
            if(viewlist!=null){
                JSON uiJSON=new JSON("{ \"is\": \"gui\" }");
                uiJSON.listPath("view", viewlist);
                if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
            }
        }
    }

    private void showWhatIAmViewingAsRawJSON(){ logrule();
        if(contentSet("private:viewing:is")){
            String title=content("private:viewing:title");
            boolean editable=contentIsOrListContains("private:viewing:is","editable");
            LinkedHashMap viewhash=ots2gui.guifyHash("", contentHash("private:viewing:#"), content("private:viewing"), editable);
            viewhash.put("#uid", "uid: "+content("private:viewing"));
            content("place","");
            content("private:editing","");
            JSON uiJSON=new JSON("{ \"is\": \"gui\" }");
            uiJSON.stringPath("title", title);
            uiJSON.hashPath("view", viewhash);
            if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    // ---------------------------------------------------------
}

