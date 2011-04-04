
package android;

import java.util.*;
import java.util.regex.*;

import android.gui.*;

import android.content.Context;
import android.database.Cursor;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.location.*;

import static android.provider.Contacts.*;
import static android.provider.Contacts.ContactMethods.*;
import static android.provider.Contacts.ContactMethodsColumns.*;

import netmash.lib.JSON;
import netmash.forest.WebObject;
import netmash.forest.UID;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    // ---------------------------------------------------------

    static public User me=null;

    public User(){ if(me==null){ me=this; if(NetMash.top!=null) NetMash.top.onUserReady(this); } }

    public void onTopCreate(){
    }

    public void onTopResume(){ logrule();
        new Evaluator(this){
            public void evaluate(){
                showWhatIAmViewing();
            }
        };
    }

    public void onTopPause(){
    }

    public void onTopDestroy(){
    }

    // ---------------------------------------------------------

    private Stack history = new Stack<String>();

    public void jumpToUID(final String uid){
        new Evaluator(this){
            public void evaluate(){
                history.push(content("links:viewing"));
                content("links:viewing", uid);
                showWhatIAmViewing();
            }
        };
    }

    public void jumpBack(){
        new Evaluator(this){
            public void evaluate(){
                if(history.empty()) return;
                String uid = (String)history.pop();
                content("links:viewing", uid);
                showWhatIAmViewing();
            }
        };
    }

    public boolean menuItem(int itemid){
        new Evaluator(this){
            public void evaluate(){
                history.push(content("links:viewing"));
                showWhatIAmViewingOnMap();
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
        if(contentListContainsAll("is", list("private", "contacts"))){ log("contacts: "+this);
            populateContacts();
        }
        else
        if(contentIsOrListContains("is", "vcard")){ log("vcard: "+this);
        }
        else{ log("!!something else: "+this);
        }
    }

    private void showWhatIAmViewing(){ logrule();
        if(contentSet("links:viewing:is")){
            LinkedHashMap viewhash=null;
            LinkedList    viewlist=null;
            if(contentIsOrListContains("links:viewing:is", "user")){
                viewlist=user2GUI();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "bookmarks")){
                viewhash=bookmarks2GUI();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "contacts")){
                viewhash=contacts2GUI();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "vcardlist")){
                viewhash=vCardList2GUI();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "vcard")){
                viewhash=vCard2GUI();
            }
            else{
                viewhash=guifyHash(contentHash("links:viewing:#"));
            }

            if(viewhash!=null || viewlist!=null){
                JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
                if(viewhash!=null) uiJSON.hashPath("view", viewhash);
                else               uiJSON.listPath("view", viewlist);
                if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON);
            }
        }
    }

    private void showWhatIAmViewingOnMap(){ logrule();
        if(contentSet("links:viewing:is")){
            LinkedList view=null;
            if(contentIsOrListContains("links:viewing:is", "user")){
                //view=user2Map();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "vcard")){
                //view=vCard2Map();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "contacts")){
                view=vCardList2Map("public:vcard:");
            }
            else
            if(contentIsOrListContains("links:viewing:is", "vcardlist")){
                view=vCardList2Map("");
            }
            else{
            }
            if(view!=null){
                JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
                uiJSON.listPath("view", view);
                if(NetMash.top!=null) NetMash.top.drawJSON(uiJSON);
            }
        }
    }

    private LinkedList user2GUI(){ logrule();
        String useruid = content("links:viewing");

        String fullname = content("links:viewing:public:vcard:fullName");
        if(fullname==null) fullname="Waiting for contact details "+content("links:viewing:public:vcard");

        LinkedHashMap standing = contentHash("links:viewing:public:standing");
        if(standing!=null) standing.put("direction", "horizontal");

        String address = getAddressString("links:viewing:public:vcard:address");
        if(address==null) address="Waiting for contact details";

        String vcarduid = UID.normaliseUID(useruid, content("links:viewing:public:vcard"));
        LinkedList vcard=null;
        if(vcarduid!=null) vcard = list("direction:horizontal", "options:jump", "proportions:75%", "Contact Details:", vcarduid);

        String contactsuid = UID.normaliseUID(useruid, content("links:viewing:private:contacts"));
        LinkedList contacts=null;
        if(contactsuid!=null) contacts = list("direction:horizontal", "options:jump", "proportions:75%", "Contacts List:", contactsuid);

        String bmuid=UID.normaliseUID(useruid, content("links:viewing:links:bookmarks"));
        LinkedList bookmarks=null;
        if(bmuid!=null) bookmarks = list("direction:horizontal", "options:jump", "proportions:75%", "Bookmarks:", bmuid);

        LinkedList userlist = new LinkedList();
        userlist.add("direction:vertical");
        userlist.add(fullname);
        if(standing !=null) userlist.add(standing);
        if(address  !=null) userlist.add(address);
        if(vcard    !=null) userlist.add(vcard);
        if(contacts !=null) userlist.add(contacts);
        if(bookmarks!=null) userlist.add(bookmarks);

        return userlist;
    }

    private LinkedHashMap bookmarks2GUI(){ logrule();
        String listuid = content("links:viewing");
        LinkedList<String> bookmarks = contentList("links:viewing:list");
        if(bookmarks==null) return null;

        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: bookmarks){ i++;
            String bmuid = UID.normaliseUID(listuid, uid);
            String           bmtext=content("links:viewing:list:"+i+":title");
            if(bmtext==null) bmtext=content("links:viewing:list:"+i+":fullName");
            if(bmtext==null) bmtext=content("links:viewing:list:"+i+":public:vcard:fullName");
            if(bmtext==null) bmtext=content("links:viewing:list:"+i+":is");
            if(bmtext==null) bmtext=content("links:viewing:list:"+i+":tags");
            if(bmtext==null) bmtext="@"+bmuid;
            viewlist.add(list("direction:horizontal", "options:jump", "proportions:75%", bmtext, bmuid));
        }

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", "Bookmarks");
        guitop.put("#bookmarks", viewlist);
        return guitop;
    }

    private LinkedHashMap contacts2GUI(){ logrule();
        String listuid = content("links:viewing");
        LinkedList<String> contacts = contentList("links:viewing:list");
        if(contacts==null) return null;

        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: contacts){ i++;
            String contactuid = UID.normaliseUID(listuid, uid);
            String fullname=content("links:viewing:list:"+i+":public:vcard:fullName");
            if(fullname==null) viewlist.add("@"+contactuid);
            else               viewlist.add(list("direction:horizontal", "options:jump", "proportions:75%", fullname, contactuid));
        }

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", "Contacts List");
        guitop.put("#contactlist", viewlist);
        return guitop;
    }

    private LinkedHashMap vCardList2GUI(){ logrule();
        String listuid = content("links:viewing");
        LinkedList<String> vcards = contentList("links:viewing:list");
        if(vcards==null) return null;

        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: vcards){ i++;
            String vcarduid = UID.normaliseUID(listuid, uid);
            String fullname=content("links:viewing:list:"+i+":fullName");
            if(fullname==null) viewlist.add("@"+vcarduid);
            else               viewlist.add(list("direction:horizontal", "options:jump", "proportions:75%", fullname, vcarduid));
        }

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", "Contacts List");
        guitop.put("#contactlist", viewlist);
        return guitop;
    }

    private LinkedList vCardList2Map(String vcardprefix){ logrule();
        String listuid = content("links:viewing");
        LinkedList<String> contacts = contentList("links:viewing:list");
        if(contacts==null) return null;

        LinkedList maplist = new LinkedList();
        maplist.add("is:maplist");
        int i= -1;
        for(String uid: contacts){ i++;
            String contactuid = UID.normaliseUID(listuid, uid);
            String fullname=content("links:viewing:list:"+i+":"+vcardprefix+"fullName");
            LinkedHashMap<String,Double> location=contentHash("links:viewing:list:"+i+":"+vcardprefix+"location");
            if(location==null) location=geoCode(getGeoAddressString("links:viewing:list:"+i+":"+vcardprefix+"address"));
            if(location==null) continue;
            LinkedHashMap point = new LinkedHashMap();
            point.put("label", fullname);
            point.put("sublabel", getAddressString("links:viewing:list:"+i+":"+vcardprefix+"address"));
            point.put("location", location);
            point.put("jump", contactuid);
            maplist.add(point);
        }

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", "Contacts List");
        guitop.put("#contactmap", maplist);
        return maplist;
    }

    private LinkedHashMap vCard2GUI(){ logrule();

        LinkedList vcarddetail = new LinkedList();
        vcarddetail.add("direction:vertical");

        String homephone=content("links:viewing:tel:home:0");
        if(homephone==null) homephone=content("links:viewing:tel:home");
        if(homephone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Home phone:", homephone));

        String workphone=content("links:viewing:tel:work:0");
        if(workphone==null) workphone=content("links:viewing:tel:work");
        if(workphone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Work phone:", workphone));

        String mobilephone=content("links:viewing:tel:mobile:0");
        if(mobilephone==null) mobilephone=content("links:viewing:tel:mobile");
        if(mobilephone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Mobile:", mobilephone));

        String faxnumber=content("links:viewing:tel:fax:0");
        if(faxnumber==null) faxnumber=content("links:viewing:tel:fax");
        if(faxnumber!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Fax:", faxnumber));

        String email=content("links:viewing:email:0");
        if(email==null) email=content("links:viewing:email");
        if(email!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Email address:", email));

        String url=content("links:viewing:url:0");
        if(url==null) url=content("links:viewing:url");
        if(url!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Website:", url));

        String address=getAddressString("links:viewing:address");
        if(address!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Address:", address));

        String fullname=content("links:viewing:fullName");
        String photourl=content("links:viewing:photo");
        if(photourl==null) photourl="";

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", list("direction:horizontal", "proportions:25%", photourl, fullname));
        guitop.put("#vcard", vcarddetail);
        return guitop;
    }

    private String getGeoAddressString(String path){
        LinkedHashMap address = contentHash(path);
        if(address==null) return content(path);
        String streetstr;
        Object street = address.get("street");
        if(street instanceof List){
            List<String> streetlist = (List<String>)street;
            StringBuilder streetb=new StringBuilder();
            int i=0;
            for(String line: streetlist){ i++; streetb.append(line); if(i==1) break; }
            streetstr=streetb.toString().trim();
        }
        else streetstr = street==null? "-": street.toString();
        return streetstr+" \""+address.get("postalCode")+"\"";
    }

    private String getAddressString(String path){
        LinkedHashMap address = contentHash(path);
        if(address==null) return content(path);
        String streetstr;
        Object street = address.get("street");
        if(street instanceof List){
            List<String> streetlist = (List<String>)street;
            StringBuilder streetb=new StringBuilder();
            for(String line: streetlist){ streetb.append(line); streetb.append("\n"); }
            streetstr=streetb.toString().trim();
        }
        else streetstr = street==null? "-": street.toString();
        return streetstr+"\n"+address.get("locality")+"\n"+address.get("region")+"\n"+address.get("postalCode");
    }

    private LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm){ logrule();
        LinkedHashMap<String,Object> hm2 = new LinkedHashMap<String,Object>();
        hm2.put("direction", "vertical");
        for(String tag: hm.keySet()){
            Object o=hm.get(tag);
            hm2.put("#"+tag, tag);
            if(o instanceof LinkedHashMap) hm2.put("."+tag, guifyHash((LinkedHashMap<String,Object>)o));
            else
            if(o instanceof LinkedList)    hm2.put("."+tag, guifyList((LinkedList)o));
            else                           hm2.put("."+tag, o);
        }
        return hm2;
    }

    private LinkedList guifyList(LinkedList ll){
        LinkedList ll2 = new LinkedList();
        ll2.add("direction:horizontal");
        for(Object o: ll){
           if(o instanceof LinkedHashMap) ll2.add(guifyHash((LinkedHashMap<String,Object>)o));
           else
           if(o instanceof LinkedList)    ll2.add(guifyList((LinkedList)o));
           else                           ll2.add(o);
        }
        return ll2;
    }

    // ---------------------------------------------------------

    static private final String ADDRESS_WHERE = PERSON_ID+" == %s AND "+KIND+" == "+KIND_POSTAL;

    private void populateContacts(){ logrule();
        if(contentSet("list")) return;
        LinkedList contactslist = new LinkedList();
        if(NetMash.top==null) return;
        Context context = NetMash.top.getApplicationContext();
        Cursor concur = context.getContentResolver().query(People.CONTENT_URI, null, null, null, null);
        int nameind   = concur.getColumnIndexOrThrow(People.NAME);
        int personind = concur.getColumnIndexOrThrow(People._ID);
        if(concur.moveToFirst()) do{
            String id   = concur.getString(personind);
            String name = concur.getString(nameind);
            if(name==null) continue;
            Cursor addcur = context.getContentResolver().query(ContactMethods.CONTENT_URI, null, String.format(ADDRESS_WHERE, id), null, null);
            int addind = addcur.getColumnIndexOrThrow(DATA);
            String address = "";
            if(addcur.moveToFirst()) address = addcur.getString(addind);
            addcur.close();
            if(!"".equals(address)) contactslist.add(createUserAndVCard(id, name, address));
        } while(concur.moveToNext());
        concur.close();
        contentList("list", contactslist);
    }

    public User(String name, String address, LinkedHashMap<String,Double> location){
        super("{ \"is\": \"vcard\", \n"+
              "  \"fullName\": \""+name+"\",\n"+
              "  \"address\": \""+address+"\",\n"+
          (location==null? "":
              "  \"location\": { \"lat\": "+location.get("lat")+", \"lon\": "+location.get("lon")+" }\n")+
              "}");
    }

    public User(String vcarduid){
        super("{ \"is\": \"user\", \n"+
              "  \"public\": { \"vcard\": \""+vcarduid+"\" }\n"+
              "}");
    }

    private String createUserAndVCard(String id, String name, String address){
        String inlineaddress = address.replaceAll("\n", ", ");
        return spawn(new User(spawn(new User(name, inlineaddress, geoCode(inlineaddress)))));
    }

    private LinkedHashMap<String,Double> geoCode(String address){
        log("geoCode "+address);
        if(NetMash.top==null){ log("No Activity to geoCode from"); return null; }
        Context context = NetMash.top.getApplicationContext();
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try{
            List<Address> geos = geocoder.getFromLocationName(address, 1);
            if(!geos.isEmpty()){
                if(geos.size() >=2) log(geos.size()+" locations found for "+address);
                Address geo1 = geos.get(0);
                LinkedHashMap<String,Double> loc = new LinkedHashMap<String,Double>();
                loc.put("lat", geo1.getLatitude());
                loc.put("lon", geo1.getLongitude());
                return loc;
            } 
            else log("No getFromLocationName for "+address);
        }catch(Exception e){ log("No getFromLocationName for "+address); log(e); }
        return null; 
    }

    // ---------------------------------------------------------
}

