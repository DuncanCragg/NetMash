
package android;

import java.util.*;
import java.util.regex.*;

import android.gui.*;
import android.os.*;

import android.content.*;
import android.database.Cursor;
import android.location.*;
import android.accounts.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.*;

import static android.content.Context.*;
import static android.location.LocationManager.*;

import netmash.lib.JSON;
import netmash.forest.*;
import netmash.platform.Kernel;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    // ---------------------------------------------------------

    static public User me=null;
    static public User currentform=null;

    static public void createUserAndDevice(){
        String fullName=UserContacts.getUsersFullName();
        User vcard = new User(
              "{   \"is\": \"vcard\", \n"+
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
              "{   \"is\": [ \"private\", \"vcardlist\" ], \n"+
              "    \"title\": \"Phone Contacts\", \n"+
              "    \"list\": null \n"+
              "}");

        String homeusers=Kernel.config.stringPathN("ots:homeusers");
        me = new User(homeusers, vcard.uid, links.uid, contacts.uid);
        otslinks.addFirst(me.uid);

        me.funcobs.setCacheNotifyAndSaveConfig(me);
        me.funcobs.cacheSaveAndEvaluate(vcard, true);
        me.funcobs.cacheSaveAndEvaluate(links);
        me.funcobs.cacheSaveAndEvaluate(contacts);
        me.funcobs.cacheSaveAndEvaluate(me, true);

        me.notifying(list(homeusers));
        NetMash.top.onUserReady(me);
    }

    public User(String jsonstring){
        super(jsonstring);
    }

    public User(String homeusers, String vcarduid, String linksuid, String contactsuid){
        super("{   \"is\": \"user\", \n"+
              "    \"homeusers\": \""+homeusers+"\", \n"+
              "    \"location\": { \"lat\": 0, \"lon\": 0 }, \n"+
              "    \"vcard\": \""+vcarduid+"\", \n"+
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

    static User newForm(String useruid, String guiuid){
        return new User("{ \"is\": \"form\", \n"+
                        "  \"user\": \""+useruid+"\", \n"+
                        "  \"gui\": \""+guiuid+"\", \n"+
                        "  \"form\": { }\n"+
                        "}");
    }

    // ---------------------------------------------------------

    OTS2GUI ots2gui;
    CurrentLocation currentlocation;

    public void onTopCreate(){
        ots2gui = new OTS2GUI(this);
        currentlocation = new CurrentLocation(this);
    }

    public void onTopResume(){
        new Evaluator(this){
            public void evaluate(){ logrule();
                showWhatIAmViewing();
            }
        };
        currentlocation.getLocationUpdates();
    }

    public void onTopPause(){
        currentlocation.stopLocationUpdates();
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

    class View{
        String uid,as; View(String u, String a){ uid=u; as=a; }
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
            public void evaluate(){ logrule();
                history.forward();
                content("private:viewing", uid);
                content("private:viewas", "gui");
                showWhatIAmViewing();
            }
        };
    }

    public void jumpBack(){
        new Evaluator(this){
            public void evaluate(){ logrule();
                if(!history.back()) return;
                showWhatIAmViewing();
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
            }
        };
        return true;
    }

    private String returnstringhack; // so fix it
    public String getFormStringVal(final String guiuid, final String tag){
        new Evaluator(this){
            public void evaluate(){ logrule();
                if(!contentSet("private:forms:"+UID.toUID(guiuid))){
                    content(   "private:forms:"+UID.toUID(guiuid), spawn(newForm(uid, guiuid)));
                    currentform=null;
                    returnstringhack=null;
                }
                else{
                    returnstringhack=content("private:forms:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
            }
        };
        return returnstringhack;
    }

    private boolean returnboolhack; // so fix it
    public boolean getFormBoolVal(final String guiuid, final String tag){
        new Evaluator(this){
            public void evaluate(){ logrule();
                if(!contentSet("private:forms:"+UID.toUID(guiuid))){
                    content(   "private:forms:"+UID.toUID(guiuid), spawn(newForm(uid, guiuid)));
                    currentform=null;
                    returnboolhack=false;
                }
                else{
                    returnboolhack=contentBool("private:forms:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
            }
        };
        return returnboolhack;
    }

    private int returninthack; // so fix it
    public int getFormIntVal(final String guiuid, final String tag){
        new Evaluator(this){
            public void evaluate(){ logrule();
                if(!contentSet("private:forms:"+UID.toUID(guiuid))){
                    content(   "private:forms:"+UID.toUID(guiuid), spawn(newForm(uid, guiuid)));
                    currentform=null;
                    returninthack=0;
                }
                else{
                    returninthack=contentInt("private:forms:"+UID.toUID(guiuid)+":form:"+dehash(tag));
                }
            }
        };
        return returninthack;
    }

    public void setFormVal(final String guiuid, final String tag, final String val){
        if(this==me && currentform!=null) currentform.setFormVal(guiuid, tag, val);
        if(this!=currentform) return;
        new Evaluator(this){
            public void evaluate(){ logrule();
                content("form:"+dehash(tag), val);
                notifying(content("gui"));
            }
        };
    }

    public void setFormVal(final String guiuid, final String tag, final boolean val){
        if(this==me && currentform!=null) currentform.setFormVal(guiuid, tag, val);
        if(this!=currentform) return;
        new Evaluator(this){
            public void evaluate(){ logrule();
                contentBool("form:"+dehash(tag), val);
                notifying(content("gui"));
            }
        };
    }

    public void setFormVal(final String guiuid, final String tag, final int val){
        if(this==me && currentform!=null) currentform.setFormVal(guiuid, tag, val);
        if(this!=currentform) return;
        new Evaluator(this){
            public void evaluate(){ logrule();
                contentInt("form:"+dehash(tag), val);
                notifying(content("gui"));
            }
        };
    }

    private String dehash(String s){ if(s.startsWith("#")) return s.substring(1); return s; }

    // ---------------------------------------------------------

    public void evaluate(){
        if(contentIs("is", "user") && this==me){
            showWhatIAmViewing();
        }
        else
        if(contentListContainsAll("is", list("private", "vcardlist"))){ log("contacts: "+this);
            if(!contentSet("list")) contentList("list", UserContacts.populateContacts(this));
        }
        else
        if(contentIs("is", "form")){ log("form: "+this);
            currentform=this;
        }
        else log("no evaluate: "+this);
    }

    private void showWhatIAmViewing(){
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

    private void showWhatIAmViewingAsGUI(){ logrule();
        if(contentSet("private:viewing:is")){
            LinkedHashMap viewhash=null;
            if(contentIsOrListContains("private:viewing:is", "user")){
                viewhash=ots2gui.user2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcard")){
                viewhash=ots2gui.vCard2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "userlist")){
                viewhash=ots2gui.vCardList2GUI("vcard:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcardlist")){
                viewhash=ots2gui.vCardList2GUI("");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "links")){
                viewhash=ots2gui.links2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "gui")){
                viewhash=contentHash("private:viewing:view");
            }
            else{
                viewhash=ots2gui.guifyHash(contentHash("private:viewing:#"), content("private:viewing"));
                viewhash.put("#uid", "uid: "+content("private:viewing"));
            }
            if(viewhash!=null){
                JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
                uiJSON.hashPath("view", viewhash);
                if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
            }
        }
    }

    private void showWhatIAmViewingOnMap(){ logrule();
        if(contentSet("private:viewing:is")){
            LinkedList viewlist=null;
            if(contentIsOrListContains("private:viewing:is", "user")){
                viewlist=ots2gui.vCard2Map("vcard:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcard")){
                viewlist=ots2gui.vCard2Map("");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "userlist")){
                viewlist=ots2gui.vCardList2Map("vcard:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcardlist")){
                viewlist=ots2gui.vCardList2Map("");
            }
            else{
            }

            if(viewlist!=null){
                JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
                uiJSON.listPath("view", viewlist);
                if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
            }
        }
    }

    private void showWhatIAmViewingAsRawJSON(){ logrule();
        if(contentSet("private:viewing:is")){
            LinkedHashMap viewhash=ots2gui.guifyHash(contentHash("private:viewing:#"), content("private:viewing"));
            viewhash.put("#uid", "uid: "+content("private:viewing"));
            JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
            uiJSON.hashPath("view", viewhash);
            if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    // ---------------------------------------------------------
}

