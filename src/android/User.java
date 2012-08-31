
package android;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

import android.gui.*;
import android.os.*;
import android.util.*;

import android.content.*;
import android.database.Cursor;
import android.location.*;
import android.accounts.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.*;

import static android.content.Context.*;
import static android.location.LocationManager.*;

import netmash.lib.*;
import netmash.forest.*;
import netmash.platform.Kernel;
import static netmash.platform.Logging.*;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    // ---------------------------------------------------------

    static public User me=null;

    static public void createUserAndDevice(){
        String fullName=UserContacts.getUsersFullName();
        User contact = new User(
              "{   \"is\": \"contact\", \n"+
              "    \"fullName\": \""+fullName+"\", \n"+
              "    \"address\": { } \n"+
              "}");
        User links = new User(
              "{   \"is\": [ \"links\" ], \n"+
              "    \"list\": null \n"+
              "}");
        LinkedList otslinks=Kernel.config.listPathN("ots:links");
        links.publicState.listPath("list", otslinks);

        User contacts = new User(
              "{   \"is\": [ \"private\", \"contact\", \"list\" ], \n"+
              "    \"title\": \"Phone Contacts\", \n"+
              "    \"list\": null \n"+
              "}");

        User room = new User(
              "{   \"is\": [ \"place\", \"3d\", \"mesh\", \"editable\" ], \n"+
              "    \"title\": \""+fullName+"'s Room\", \n"+
              "    \"vertices\": [ [  20.0,  -1.0, -20.0 ], [  20.0,  -1.0,  20.0 ], [ -20.0,  -1.0,  20.0 ], [ -20.0,  -1.0, -20.0 ], [  20.0,  20.0, -20.0 ], [  20.0,  20.0,  20.0 ], [ -20.0,  20.0,  20.0 ], [ -20.0,  20.0, -20.0 ] ], \n"+
              "    \"texturepoints\": [ [ 0.0, 0.0 ], [ 1.0, 0.0 ], [ 1.0, 1.0 ], [ 0.0, 1.0 ] ], \n"+
              "    \"normals\": [ [  0.5,  0.5, -0.5 ], [  0.5, -0.5, -0.5 ], [ -0.5, -0.5, -0.5 ], [ -0.5,  0.5, -0.5 ], [ -0.5, -0.5,  0.5 ], [ -0.5,  0.5,  0.5 ], [  0.5, -0.5,  0.5 ], [  0.5,  0.5,  0.5 ] ], \n"+
              "    \"faces\": [ [ \"5/1/1\",\"4/3/3\",\"1/2/2\" ], [ \"5/1/1\",\"8/4/4\",\"4/3/3\" ], [ \"3/1/5\",\"8/3/4\",\"7/2/6\" ], [ \"3/1/5\",\"4/4/3\",\"8/3/4\" ], [ \"2/1/7\",\"3/4/5\",\"6/2/8\" ], [ \"6/2/8\",\"3/4/5\",\"7/3/6\" ], [ \"1/1/2\",\"2/4/7\",\"5/2/1\" ], [ \"5/2/1\",\"2/4/7\",\"6/3/8\" ], [ \"5/1/1\",\"6/4/8\",\"8/2/4\" ], [ \"8/2/4\",\"6/4/8\",\"7/3/6\" ], [ \"1/1/2\",\"3/3/5\",\"2/2/7\" ], [ \"1/1/2\",\"4/4/3\",\"3/3/5\" ] ], \n"+
              "}");

        String homeusers=Kernel.config.stringPathN("ots:homeusers");
        me = new User(homeusers, contact.uid, links.uid, contacts.uid);
        otslinks.addFirst(room.uid);
        otslinks.addFirst(me.uid);

        me.funcobs.setCacheNotifyAndSaveConfig(me);
        me.funcobs.cacheSaveAndEvaluate(contact, true);
        me.funcobs.cacheSaveAndEvaluate(links);
        me.funcobs.cacheSaveAndEvaluate(contacts);
        me.funcobs.cacheSaveAndEvaluate(room);
        me.funcobs.cacheSaveAndEvaluate(me, true);

        if(homeusers!=null) me.notifying(list(homeusers));
        NetMash.top.onUserReady(me);
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
              "    \"location\": { \"lat\": 0, \"lon\": 0 }, \n"+
              "    \"contact\": \""+contactuid+"\", \n"+
              "    \"private\": { \n"+
              "        \"viewing\": null, \n"+
              "        \"viewas\": \"gui\", \n"+
              "        \"links\": \""+linksuid+"\", \n"+
              "        \"history\": null, \n"+
              "        \"contacts\":  \""+contactsuid+"\", \n"+
              "        \"forms\": { } \n"+
              "    }\n"+
              "}");
    }

    public User(){ if(me==null){ me=this; if(NetMash.top!=null) NetMash.top.onUserReady(me); } }

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

    static User newRSVP(String eventuid, String useruid){
        return new User("{ \"is\": \"rsvp\", \n"+
                        "  \"event\": \""+eventuid+"\",\n"+
                        "  \"user\": \""+useruid+"\"\n"+
                        "}");
    }

    // ---------------------------------------------------------

    OTS2GUI ots2gui;
    CurrentLocation currentlocation=null;
    boolean trackGPS=false;

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

    private float px=0,py=0,pz=0;
    public void onNewCoords(final float x, final float y, final float z){
        float dx=x-px, dy=y-py, dz=z-pz;
        if(dx*dx+dy*dy+dz*dz<1.0) return;
        new Evaluator(this){
            public void evaluate(){ if(false) logrule();
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
                NetMash.top.onerenderer.resetCoordsAndView(dx,dy,dz);
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
        new Evaluator(this){
            public void evaluate(){ if(false) logrule();
                history.forward();
                content("private:viewing", uid);
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

    private String returnstringhack; // so fix it
    public String getFormStringVal(final String guiuid, final String tag){
        new Evaluator(this){
            public void evaluate(){
                returnstringhack=null;
                if(!contentSet("private:forms:"+UID.toUID(guiuid))) spawnResponse(guiuid);
                else returnstringhack=content("private:forms:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                refreshObserves();
            }
        };
        return returnstringhack;
    }

    private boolean returnboolhack; // so fix it
    public boolean getFormBoolVal(final String guiuid, final String tag){
        new Evaluator(this){
            public void evaluate(){
                returnboolhack=false;
                if(!contentSet("private:forms:"+UID.toUID(guiuid))) spawnResponse(guiuid);
                else returnboolhack=contentBool("private:forms:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                refreshObserves();
            }
        };
        return returnboolhack;
    }

    private int returninthack; // so fix it
    public int getFormIntVal(final String guiuid, final String tag){
        new Evaluator(this){
            public void evaluate(){
                returninthack=0;
                if(!contentSet("private:forms:"+UID.toUID(guiuid))) spawnResponse(guiuid);
                else returninthack=contentInt("private:forms:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                refreshObserves();
            }
        };
        return returninthack;
    }

    private void spawnResponse(String guiuid){
        User resp;
        if(contentIsOrListContains("private:viewing:is", "editable")){
            resp=newEditableRule(guiuid, uid);
        }
        else if(contentListContainsAll("private:viewing:is", list("searchable", "document", "list"))){
            resp=newDocumentQuery(guiuid, uid);
        }
        else if(contentListContainsAll("private:viewing:is", list("attendable","event"))){
            resp=newRSVP(guiuid, uid);
        }
        else resp=newForm(guiuid, uid);
        content("private:forms:"+UID.toUID(guiuid), spawn(resp));
    }

    User currentForm(String guiuid){
        String formuid = content("private:forms:"+UID.toUID(guiuid));
        return (User)onlyUseThisToHandControlOfThreadToDependent(formuid);
    }

    public void setFormVal(final String guiuid, final String tag, final String val){
        if(this==me) currentForm(guiuid).setFormVal(guiuid, tag, val);
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
                else content("form:"+dehash(tag), val);
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    public void setFormVal(final String guiuid, final String tag, final boolean val){
        if(this==me) currentForm(guiuid).setFormVal(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){ logrule();
                if(contentIsOrListContains("is", "rsvp")){
                    content("attending", val? "yes": "no");
                }
                else contentBool("form:"+dehash(tag), val);
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    public void setFormVal(final String guiuid, final String tag, final int val){
        if(this==me) currentForm(guiuid).setFormVal(guiuid, tag, val);
        else new Evaluator(this){
            public void evaluate(){ logrule();
                contentInt("form:"+dehash(tag), val);
                notifying(guiuid);
                refreshObserves();
            }
        };
    }

    private LinkedHashMap makeEditRule(String path, String val){
        return deephash(list("=>",val), path);
    }

    private String dehash(String s){ if(s.startsWith("#")) return s.substring(1); return s; }

    // ---------------------------------------------------------

    public void evaluate(){
        if(contentIs("is", "user") && this==me){
            showWhatIAmViewing();
        }
        else
        if(contentListContainsAll("is", list("private", "contact", "list"))){
            log("evaluate contacts: "+this);
            if(!contentSet("list")) contentList("list", UserContacts.populateContacts(this));
        }
        else
        if(contentListContainsAll("is", list("editable", "rule"))){
            log("evaluate editable: "+this);
        }
        else
        if(contentListContainsAll("is", list("document", "query"))){
            log("evaluate query: "+this);
            for(String alertedUid: alerted()){ me.jumpToUID(alertedUid); return; }
        }
        else
        if(contentIsOrListContains("is", "rsvp")){ log("rsvp: "+this);
            log("evaluate rsvp: "+this);
        }
        else
        if(contentIs("is", "form")){
            log("evaluate form: "+this);
        }
        else log("no evaluate: "+this);
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

    private void showWhatIAmViewingAsGUI(){ if(false) logrule();
        if(contentSet("private:viewing:is")){
            LinkedHashMap viewhash=null;
            LinkedHashMap meshhash=null;
            String title=content("private:viewing:title");
            boolean editable=contentIsOrListContains("private:viewing:is","editable");
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
            if(contentIsOrListContains("private:viewing:is", "gui")){
                viewhash=contentHash("private:viewing:view");
            }
            else
            if(contentListContainsAll("private:viewing:is", list("3d", "mesh"))){
                meshhash=contentHash("private:viewing:#");
                cacheVisibleSceneElements();
            }
            else{
                viewhash=ots2gui.guifyHash("",contentHash("private:viewing:#"), content("private:viewing"), editable);
                viewhash.put("#uid", "uid: "+content("private:viewing"));
            }
            JSON uiJSON=null;
            if(viewhash!=null){
                content("place","");
                uiJSON=new JSON("{ \"is\": \"gui\" }");
                uiJSON.stringPath("title", title);
                uiJSON.hashPath("view", viewhash);
            }
            if(meshhash!=null){
                content("place",content("private:viewing"));
                notifying(content("private:viewing"));
                uiJSON=new JSON("{ \"is\": \"mesh\" }");
                uiJSON.stringPath("title", title);
                uiJSON.hashPath("mesh", meshhash);
            }
            if(NetMash.top!=null && uiJSON!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    public ConcurrentHashMap<String,Object> glElements = new ConcurrentHashMap<String,Object>();

    private void glElementsPut(String t, Object o, Object d){
        if(t==null || t.equals("")) return;
        if(o==null || o.toString().equals("")) o=d;
        if(o!=null) glElements.put(t,o);
    }

    static String basicVert="uniform mat4 mvpm, norm; uniform vec4 lightPos; attribute vec4 pos; attribute vec2 tex; attribute vec3 nor; varying vec2 T; varying vec3 N,L; void main(){ T=tex; N=vec3(norm*vec4(nor, 1.0)); L=vec3(mvpm*lightPos); gl_Position=mvpm*pos; }";
    static String basicFrag="precision mediump float; uniform vec4 lightCol; uniform sampler2D texture0; varying vec2 T; varying vec3 N,L; void main(){ gl_FragColor=1.0*lightCol*texture2D(texture0, T)+0.4*max(dot(normalize(N),normalize(-L)), 0.0); }";

    private void cacheVisibleSceneElements(){

        glElementsPut(content(                      "private:viewing:vertexShader"),
                      Utils.join(contentListMayJump("private:viewing:vertexShader")," "), basicVert);
        glElementsPut(content(                      "private:viewing:fragmentShader"),
                      Utils.join(contentListMayJump("private:viewing:fragmentShader")," "), basicFrag);

        LinkedList subs=contentList(                      "private:viewing:subObjects");
        if(subs==null) return;
        for(int i=0; i< subs.size(); i++){
            LinkedHashMap m=contentHash(String.format(    "private:viewing:subObjects:%d:object:avatar:#",i));
            if(m==null)   m=contentHash(String.format(    "private:viewing:subObjects:%d:object:#",i));
            glElementsPut(content(String.format(          "private:viewing:subObjects:%d:object",i)), m, null);

            LinkedList subsubs=contentList(String.format( "private:viewing:subObjects:%d:object:subObjects",i));
            if(subsubs==null) continue;
            for(int j=0; j< subsubs.size(); j++){
                LinkedHashMap n=contentHash(String.format("private:viewing:subObjects:%d:object:subObjects:%d:object:avatar:#",i,j));
                if(n==null)   n=contentHash(String.format("private:viewing:subObjects:%d:object:subObjects:%d:object:#",i,j));
                glElementsPut(content(String.format(      "private:viewing:subObjects:%d:object:subObjects:%d:object",i,j)), n, null);
            }
        }
    }

    private void showWhatIAmViewingOnMap(){ logrule();
        if(contentSet("private:viewing:is")){
            LinkedList viewlist=null;
            if(contentIsOrListContains("private:viewing:is", "user")&&
               contentIsOrListContains("private:viewing:is", "list")  ){

                viewlist=ots2gui.contactList2Map("contact:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "contact")&&
               contentIsOrListContains("private:viewing:is", "list"   )   ){

                viewlist=ots2gui.contactList2Map("");
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
            boolean editable=contentIsOrListContains("private:viewing:is","editable");
            LinkedHashMap viewhash=ots2gui.guifyHash("", contentHash("private:viewing:#"), content("private:viewing"), editable);
            viewhash.put("#uid", "uid: "+content("private:viewing"));
            JSON uiJSON=new JSON("{ \"is\": \"gui\" }");
            uiJSON.hashPath("view", viewhash);
            if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    // ---------------------------------------------------------
}

