
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

    static public void createUserAndDevice(){
        String fullName=getUsersFullName();
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
              "        \"contacts\":  \""+contactsuid+"\" \n"+
              "    }\n"+
              "}");
    }

    public User(){ if(me==null){ me=this; if(NetMash.top!=null) NetMash.top.onUserReady(me); } }

    static User newVcard(String name, String phonenumber, String emailaddress, String address){
        return new User(         "{ \"is\": \"vcard\", \n"+
                                 "  \"fullName\": \""+name+"\""+
            (phonenumber!=null?  ",\n  \"tel\": \""+phonenumber+"\"": "")+
            (emailaddress!=null? ",\n  \"email\": \""+emailaddress+"\"": "")+
            (address!=null?      ",\n  \"address\": \""+address+"\"": "")+
                                 "\n}");
    }

    static String getUsersFullName(){
        AccountManager acctmgr = AccountManager.get(NetMash.top);
        Account[] accounts = acctmgr.getAccountsByType("com.google");
        for(Account account: accounts){
            String name=account.name;
            String[] parts=name.split("@");
            if(parts.length!=0) return toFullNameFromEmail(parts[0]);
        }
        return "You";
    }

    static public String toFullNameFromEmail(String emailname){
        String[] parts=emailname.split("[ \\.\\-_]");
        StringBuilder sb=new StringBuilder();
        for(String part: parts){
            if(part.length()==0) continue;
            char[] partcha = part.toCharArray();
            partcha[0]=Character.toUpperCase(partcha[0]);
            sb.append(partcha);
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------

    CurrentLocation currentlocation;

    public void onTopCreate(){
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

    // ---------------------------------------------------------

    public void evaluate(){
        if(contentIs("is", "user") && this==me){
            showWhatIAmViewing();
        }
        else
        if(contentIsOrListContains("is", "user")){ log("other user: "+this);
        }
        else
        if(contentListContainsAll("is", list("private", "vcardlist"))){ log("contacts: "+this);
            populateContacts();
        }
        else{ log("no evaluate: "+this);
        }
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
                viewhash=user2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcard")){
                viewhash=vCard2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "userlist")){
                viewhash=vCardList2GUI("vcard:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcardlist")){
                viewhash=vCardList2GUI("");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "links")){
                viewhash=links2GUI();
            }
            else
            if(contentIsOrListContains("private:viewing:is", "gui")){
                viewhash=contentHash("private:viewing:view");
            }
            else{
                viewhash=guifyHash(contentHash("private:viewing:#"), content("private:viewing"));
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
                viewlist=vCard2Map("vcard:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcard")){
                viewlist=vCard2Map("");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "userlist")){
                viewlist=vCardList2Map("vcard:");
            }
            else
            if(contentIsOrListContains("private:viewing:is", "vcardlist")){
                viewlist=vCardList2Map("");
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

    private LinkedHashMap user2GUI(){ logrule();
        String useruid = content("private:viewing");

        String fullname = content("private:viewing:vcard:fullName");
        if(fullname==null) fullname="Waiting for contact details "+content("private:viewing:vcard");

        String vcarduid = UID.normaliseUID(useruid, content("private:viewing:vcard"));
        LinkedList vcard=null;
        if(vcarduid!=null) vcard = list("direction:horizontal", "options:jump", "proportions:75%", "Contact Info:", vcarduid);

        String contactsuid = UID.normaliseUID(useruid, content("private:viewing:private:contacts"));
        LinkedList contacts=null;
        if(contactsuid!=null) contacts = list("direction:horizontal", "options:jump", "proportions:75%", "Phone Contacts:", contactsuid);

        LinkedHashMap loc = contentHash("private:viewing:location");
        LinkedList location=null;
        if(loc!=null) location = list("direction:horizontal", "Location:", loc.get("lat"), loc.get("lon"));

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightpink");
        guitop.put("#title", fullname);
        if(vcard    !=null) guitop.put("#vcard",    vcard);
        if(contacts !=null) guitop.put("#contacts", contacts);
        if(location !=null) guitop.put("#location", location);
        return guitop;
    }

    private LinkedHashMap links2GUI(){ logrule();
        String listuid = content("private:viewing");
        LinkedList<String> links = contentList("private:viewing:list");
        if(links==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: links){ i++;
            String bmtext=null;
            if(contentSet("private:viewing:list:"+i+":is")){
                if(bmtext==null) bmtext=content("private:viewing:list:"+i+":title");
                if(bmtext==null) bmtext=content("private:viewing:list:"+i+":fullName");
                if(bmtext==null) bmtext=content("private:viewing:list:"+i+":vcard:fullName");
                if(bmtext==null) bmtext=content("private:viewing:list:"+i+":is");
                if(bmtext==null) bmtext=content("private:viewing:list:"+i+":tags");
            }
            if(bmtext==null) bmtext="Loading..";
            String bmuid = UID.normaliseUID(listuid, uid);
            viewlist.add(list("direction:horizontal", "options:jump", "proportions:75%", bmtext, bmuid));
        }
        String title = content("private:viewing:title");
        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightgreen");
        guitop.put("#title", title!=null? title: "Links");
        guitop.put("#links", viewlist);
        return guitop;
    }

    private LinkedHashMap vCardList2GUI(String vcardprefix){ logrule();
        String listuid = content("private:viewing");
        LinkedList<String> contacts = contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: contacts){ i++;
            String contactuid = UID.normaliseUID(listuid, uid);
            String fullname=            content("private:viewing:list:"+i+":"+vcardprefix+"fullName");
            if(fullname==null) fullname=content("private:viewing:list:"+i+":is");
            if(fullname==null) viewlist.add("Loading..");
            else               viewlist.add(list("direction:horizontal", "options:jump", "proportions:75%", fullname, contactuid));
        }
        String title = content("private:viewing:title");
        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightpink");
        guitop.put("#title", title!=null? title: "Contacts List");
        guitop.put("#contactlist", viewlist);
        return guitop;
    }

    private LinkedList vCard2Map(String vcardprefix){ logrule();
        String useruid = content("private:viewing");
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+useruid);
        LinkedHashMap location=contentHash("private:viewing:location");
        if(location==null) location=geoCode(getGeoAddressString("private:viewing:"+vcardprefix+"address"));
        if(location==null) return maplist;
        LinkedHashMap point = new LinkedHashMap();
        String fullname=content(        "private:viewing:"+vcardprefix+"fullName");
        String address=getAddressString("private:viewing:"+vcardprefix+"address");
        point.put("label",    fullname!=null? fullname: "");
        point.put("sublabel", address!=null? address: "");
        point.put("location", location);
        point.put("jump", useruid);
        maplist.add(point);
        return maplist;
    }

    private LinkedList vCardList2Map(String vcardprefix){ logrule();
        String listuid = content("private:viewing");
        LinkedList<String> contacts = contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+listuid);
        int i= -1;
        for(String uid: contacts){ i++;
            LinkedHashMap<String,Double> location=null;
            if(!vcardprefix.equals(""))        location=contentHash("private:viewing:list:"+i+":location");
            if(location==null)                 location=contentHash("private:viewing:list:"+i+":"+vcardprefix+"location");
            if(location==null) location=geoCode(getGeoAddressString("private:viewing:list:"+i+":"+vcardprefix+"address"));
            if(location==null) continue;
            LinkedHashMap point = new LinkedHashMap();
            String fullname=content(        "private:viewing:list:"+i+":"+vcardprefix+"fullName");
            String address=getAddressString("private:viewing:list:"+i+":"+vcardprefix+"address");
            String contactuid = UID.normaliseUID(listuid, uid);
            point.put("label",    fullname!=null? fullname: "");
            point.put("sublabel", address!=null? address: "");
            point.put("location", location);
            point.put("jump", contactuid);
            maplist.add(point);
        }
        return maplist;
    }

    private LinkedHashMap vCard2GUI(){ logrule();

        LinkedList vcarddetail = new LinkedList();
        vcarddetail.add("direction:vertical");

        String mainphone=content("private:viewing:tel:0");
        if(mainphone==null) mainphone=content("private:viewing:tel");
        if(mainphone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Phone:", mainphone));

        String homephone=content("private:viewing:tel:home:0");
        if(homephone==null) homephone=content("private:viewing:tel:home");
        if(homephone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Home phone:", homephone));

        String workphone=content("private:viewing:tel:work:0");
        if(workphone==null) workphone=content("private:viewing:tel:work");
        if(workphone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Work phone:", workphone));

        String mobilephone=content("private:viewing:tel:mobile:0");
        if(mobilephone==null) mobilephone=content("private:viewing:tel:mobile");
        if(mobilephone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Mobile:", mobilephone));

        String faxnumber=content("private:viewing:tel:fax:0");
        if(faxnumber==null) faxnumber=content("private:viewing:tel:fax");
        if(faxnumber!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Fax:", faxnumber));

        String email=content("private:viewing:email:0");
        if(email==null) email=content("private:viewing:email");
        if(email!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Email address:", email));

        String url=content("private:viewing:url:0");
        if(url==null) url=content("private:viewing:url");
        if(url!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Website:", url));

        String address=getAddressString("private:viewing:address");
        if(address!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Address:", address));

        String fullname=content("private:viewing:fullName");
        String photourl=content("private:viewing:photo");
        if(photourl==null) photourl="";

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightpink");
        guitop.put("#title", list("direction:horizontal", "proportions:25%", photourl, fullname));
        guitop.put("#vcard", vcarddetail);
        return guitop;
    }

    private String getGeoAddressString(String path){
        int numberofstreetlines=1;
        LinkedHashMap address = contentHash(path);
        if(address==null) return content(path);
        Object street = address.get("street");
        if(street instanceof List){
            List<String> streetlist = (List<String>)street;
            StringBuilder streetb=new StringBuilder();
            int i=0;
            for(String line: streetlist){ i++; streetb.append(line); if(i==numberofstreetlines) break; }
            street=streetb.toString().trim();
        }
        StringBuilder as=new StringBuilder();
        Object l=street;              if(l!=null){                   as.append(l); }
        l=address.get("postalCode");  if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("countryName"); if(l!=null){ as.append(" ");   as.append(l); }
        return as.toString();
    }

    private String getAddressString(String path){
        String address=getAddressAsString(path);
        if(address==null) return null;
        if(address.length()==0) return null;
        return address;
    }

    private String getAddressAsString(String path){
        LinkedHashMap address = contentHash(path);
        if(address==null) return content(path);
        Object street = address.get("street");
        if(street instanceof List){
            List<String> streetlist = (List<String>)street;
            StringBuilder streetb=new StringBuilder();
            for(String line: streetlist){ streetb.append(line); streetb.append("\n"); }
            street=streetb.toString().trim();
        }
        StringBuilder as=new StringBuilder();
        Object l=street;              if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("locality");    if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("region");      if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("postalCode");  if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("countryName"); if(l!=null){ as.append(l); as.append("\n"); }
        return as.toString();
    }

    private void showWhatIAmViewingAsRawJSON(){ logrule();
        if(contentSet("private:viewing:is")){
            LinkedHashMap viewhash=guifyHash(contentHash("private:viewing:#"), content("private:viewing"));
            viewhash.put("#uid", "uid: "+content("private:viewing"));
            JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
            uiJSON.hashPath("view", viewhash);
            if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON, content("private:viewing"));
        }
    }

    private LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm, String objuid){ logrule();
        LinkedHashMap<String,Object> hm2 = new LinkedHashMap<String,Object>();
        hm2.put("direction", hm.size()<=1? "horizontal": "vertical");
        hm2.put(".open","{");
        for(String tag: hm.keySet()){
            LinkedList ll = new LinkedList();
            ll.add("direction:horizontal");
            ll.add(" "+tag+":");
            Object o=hm.get(tag);
            addToList(ll,o,objuid);
            hm2.put("#"+tag,ll);
        }
        hm2.put(".close","}");
        return hm2;
    }

    private LinkedList guifyList(LinkedList ll, String objuid){
        LinkedList ll2 = new LinkedList();
        ll2.add(ll.size()<=1? "direction:horizontal": "direction:vertical");
        ll2.add("[");
        for(Object o: ll) addToList(ll2,o,objuid);
        ll2.add("]");
        return ll2;
    }

    private void addToList(LinkedList ll, Object o, String objuid){
        if(o instanceof LinkedHashMap) ll.add(guifyHash((LinkedHashMap<String,Object>)o, objuid));
        else
        if(o instanceof LinkedList)    ll.add(guifyList((LinkedList)o, objuid));
        else
        if(UID.isUID(o))               ll.add(UID.normaliseUID(objuid, (String)o));
        else                           ll.add(" "+o);
    }

    // ---------------------------------------------------------

    private void populateContacts(){ logrule();
        if(contentSet("list")) return;
        LinkedList contactslist = new LinkedList();
        if(NetMash.top==null) return;
        Context context = NetMash.top.getApplicationContext();
        ContentResolver cr=context.getContentResolver();
        Cursor concur = cr.query(Contacts.CONTENT_URI, null, null, null, null);
        int idcol   = concur.getColumnIndex(Contacts._ID);
        int namecol = concur.getColumnIndex(Contacts.DISPLAY_NAME);
        int hasphone= concur.getColumnIndex(Contacts.HAS_PHONE_NUMBER);
int i=0;
        if(concur.moveToFirst()) do{
            String id   = concur.getString(idcol);
            String name = concur.getString(namecol);
            if(name==null) continue;
            String phonenumber=null;
            if(Integer.parseInt(concur.getString(hasphone)) >0){
                   phonenumber =getPhoneNumber(cr, id);
            }
            String emailaddress=getEmailAddress(cr, id);
            String address     =getAddress(cr, id);
            if(phonenumber==null && emailaddress==null && address==null) continue;
            log("Contact: "+id+" "+name+" "+phonenumber+" "+emailaddress+" "+address);
            contactslist.add(createVCard(id, name, phonenumber, emailaddress, address));
if(++i>100) break;
        } while(concur.moveToNext());
        concur.close();
        contentList("list", contactslist);
    }

    private String getPhoneNumber(ContentResolver cr, String id){
        Cursor phonecur=cr.query(Phone.CONTENT_URI, null, Phone.CONTACT_ID+" = ?", new String[]{id}, null);
        String phonenumber=null;
        while(phonecur.moveToNext()) {
            phonenumber = phonecur.getString(phonecur.getColumnIndex(Phone.NUMBER));
            break;
        }
        phonecur.close();
        return phonenumber;
    }

    private String getEmailAddress(ContentResolver cr, String id){
        Cursor emailcur = cr.query(Email.CONTENT_URI, null, Email.CONTACT_ID+" = ?", new String[]{id}, null);
        String emailaddress=null;
        while(emailcur.moveToNext()) {
            String emailType = emailcur.getString(emailcur.getColumnIndex(Email.TYPE));
            emailaddress     = emailcur.getString(emailcur.getColumnIndex(Email.DATA));
            break;
        }
        emailcur.close();
        return emailaddress;
    }

    private String getAddress(ContentResolver cr, String id){
        Cursor addcur = cr.query(StructuredPostal.CONTENT_URI, null, StructuredPostal.CONTACT_ID+" = ?", new String[]{id}, null);
        String address=null;
        while(addcur.moveToNext()){
            address = addcur.getString(addcur.getColumnIndex(StructuredPostal.FORMATTED_ADDRESS));
            break;
        }
        addcur.close();
        return address;
    }

    private String createVCard(String id, String name, String phonenumber, String emailaddress, String address){
        String inlineaddress = address!=null? address.replaceAll("\n", ", "): "";
        return spawn(newVcard(name, phonenumber, emailaddress, inlineaddress));
    }

    private HashMap<String,LinkedHashMap<String,Double>> geoCodeCache=new HashMap<String,LinkedHashMap<String,Double>>();
    private LinkedHashMap<String,Double> geoCode(String address){ log("geoCode "+address);
        if(address==null || address.equals("")) return null;
        LinkedHashMap<String,Double> loc=geoCodeCache.get(address);
        if(loc!=null){ log("cached result="+loc); return loc; }
        if(NetMash.top==null){ log("No Activity to geoCode from"); return null; }
        Geocoder geocoder = new Geocoder(NetMash.top.getApplicationContext(), Locale.getDefault());
        try{
            List<Address> geos = geocoder.getFromLocationName(address, 1);
            if(!geos.isEmpty()){
                if(geos.size() >=2) log(geos.size()+" locations found for "+address);
                Address geo1 = geos.get(0);
                loc = new LinkedHashMap<String,Double>();
                loc.put("lat", geo1.getLatitude());
                loc.put("lon", geo1.getLongitude());
                geoCodeCache.put(address,loc);
                return loc;
            }
            else log("No getFromLocationName for "+address);
        }catch(Exception e){ log("No getFromLocationName for "+address); log(e); }
        return null;
    }

    // ---------------------------------------------------------

    private class CurrentLocation implements LocationListener {

        private static final int SECONDS = 1000;
        private static final int MINUTES = 60 * SECONDS;

        private User user;
        private LocationManager locationManager;
        private Location currentLocation;

        CurrentLocation(User user){
            this.user=user;
            locationManager=(LocationManager)NetMash.top.getSystemService(LOCATION_SERVICE);
        }

        public void getLocationUpdates(){
            Location netloc=locationManager.getLastKnownLocation(NETWORK_PROVIDER);
            Location gpsloc=locationManager.getLastKnownLocation(GPS_PROVIDER);
            currentLocation = isBetterLocation(netloc, gpsloc)? netloc: gpsloc;
            if(currentLocation!=null) user.onNewLocation(currentLocation);
            locationManager.requestLocationUpdates(NETWORK_PROVIDER, 15*SECONDS, 0, this);
            locationManager.requestLocationUpdates(GPS_PROVIDER,     15*SECONDS, 0, this);
        }

        public void stopLocationUpdates(){
            locationManager.removeUpdates(this);
        }

        public void onLocationChanged(Location location){
            if(!isBetterLocation(location, currentLocation)) return;
            if( hasMovedLocation(location, currentLocation)) user.onNewLocation(location);
            currentLocation=location;
        }

        public void onStatusChanged(String provider, int status, Bundle extras){}
        public void onProviderEnabled(String provider){}
        public void onProviderDisabled(String provider){}

        protected boolean isBetterLocation(Location newLocation, Location prevLocation) {

            if(newLocation ==null) return false;
            if(prevLocation==null) return true;

            long timeDelta = newLocation.getTime() - prevLocation.getTime();
            boolean isSignificantlyNewer = timeDelta >  (2*MINUTES);
            boolean isSignificantlyOlder = timeDelta < -(2*MINUTES);
            boolean isNewer              = timeDelta > 0;
            if(isSignificantlyNewer) return true;
            if(isSignificantlyOlder) return false;

            int accuracyDelta=(int)(newLocation.getAccuracy() - prevLocation.getAccuracy());
            boolean isMoreAccurate              = accuracyDelta < 0;
            boolean isLessAccurate              = accuracyDelta > 0;
            boolean isSignificantlyLessAccurate = accuracyDelta > 200;
            boolean isFromSameProvider = isSameProvider(newLocation.getProvider(), prevLocation.getProvider());
            if(isMoreAccurate) return true;
            if(isNewer && !isLessAccurate) return true;
            if(isNewer && !isSignificantlyLessAccurate && isFromSameProvider) return true;
            return false;
        }

        private boolean hasMovedLocation(Location newLocation, Location prevLocation){
            if(prevLocation==null) return newLocation!=null;
            if(newLocation.getLatitude() !=prevLocation.getLatitude() ) return true;
            if(newLocation.getLongitude()!=prevLocation.getLongitude()) return true;
            return false;
        }

        private boolean isSameProvider(String provider1, String provider2){
            if(provider1==null) return provider2==null;
            return provider1.equals(provider2);
        }
    }

    // ---------------------------------------------------------
}

