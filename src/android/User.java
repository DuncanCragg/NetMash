
package android;

import java.util.*;
import java.util.regex.*;

import netmash.lib.JSON;
import netmash.forest.WebObject;

import android.gui.*;

import android.content.Context;
import android.database.Cursor;
import android.location.*;
import android.provider.Contacts;
import android.provider.Contacts.People;

import static android.provider.Contacts.*;
import static android.provider.Contacts.ContactMethods.*;
import static android.provider.Contacts.ContactMethodsColumns.*;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    // ---------------------------------------------------------

    static public User me=null;

    public User(){ if(me==null){ me=this; NetMash.top.onUserReady(this); } }

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

    public void jumpToUID(final String uid){
        new Evaluator(this){
            public void evaluate(){
                content("links:viewing", uid);
                showWhatIAmViewing();
            }
        };
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
        if(contentListContainsAll("is", list("private", "contacts"))){
            populateContacts();
        }
        else
        if(contentIsOrListContains("is", "vcard")){ log("vcard: "+this);
        }
        else{ log("!!something else: "+this);
        }
    }

    private void showWhatIAmViewing(){ logrule();
        if(contentSet("links:viewing:is")){ logrule();
            LinkedHashMap view=null;
            if(contentIsOrListContains("links:viewing:is", "user")){
                view=user2GUI();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "vcardlist")){
                view=vCardList2GUI();
            }
            else
            if(contentIsOrListContains("links:viewing:is", "vcard")){
                view=vCard2GUI();
            }
            else{
                view=guifyHash(contentHash("links:viewing:#"));
            }
            if(view!=null){
                JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
                uiJSON.hashPath("view", view);
                NetMash.top.drawJSON(uiJSON);
            }
        }
    }

    private LinkedHashMap user2GUI(){

        String fullname = content("links:viewing:public:vcard:fullName");
        if(fullname==null) return null;

        LinkedHashMap standing = contentHash("links:viewing:public:standing");
        if(standing!=null) standing.put("direction", "horizontal");

        String postcode = content("links:viewing:public:vcard:address:postalCode");
        if(postcode==null) postcode = content("links:viewing:public:vcard:address");
        String vcarduid = content("links:viewing:public:vcard");

        String contactsuid = content("links:viewing:private:contacts");
        LinkedList contacts=null;
        if(contactsuid!=null) contacts = list("direction:horizontal", "options:jump", "proportions:75%", "Contacts", contactsuid);

        LinkedList ll = new LinkedList();
        ll.add("direction:vertical");
        ll.add(fullname);
        if(standing!=null) ll.add(standing);
        if(postcode!=null) ll.add(postcode);
        if(vcarduid!=null) ll.add(vcarduid);
        if(contacts!=null) ll.add(contacts);

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#list", ll);
        return guitop;
    }

    private LinkedHashMap vCardList2GUI(){
        LinkedList<String> vcards = contentList("links:viewing:vcards");
        if(vcards==null) return null;

        LinkedList vcardsgui = new LinkedList();
        vcardsgui.add("direction:vertical");
        int i=0;
        for(String uid: vcards){
            String fullname=content("links:viewing:vcards:"+(i++)+":fullName");
            if(fullname==null) vcardsgui.add("@"+uid);
            else               vcardsgui.add(fullname+" @"+uid);
        }

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", "Contact List");
        guitop.put("#vcardlist", vcardsgui);
        return guitop;
    }

    private LinkedHashMap vCard2GUI(){

        LinkedList vcarddetail = new LinkedList();
        vcarddetail.add("direction:vertical");

        String homephone=content("links:viewing:tel:home:0");
        if(homephone==null) homephone=content("links:viewing:tel:home");
        if(homephone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Home phone:", homephone));

        String workphone=content("links:viewing:tel:work:0");
        if(workphone==null) workphone=content("links:viewing:tel:work");
        if(workphone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Work phone:", workphone));

        String email=content("links:viewing:email:0");
        if(email==null) email=content("links:viewing:email");
        if(email!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Email address:", email));

        String url=content("links:viewing:url:0");
        if(url==null) url=content("links:viewing:url");
        if(url!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Website:", url));

        String addressx=content("links:viewing:address");
        if(addressx!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Address:", addressx));

        String fullname=content("links:viewing:fullName");
        String photourl=content("links:viewing:photo");

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("#title", list("direction:horizontal", "proportions:25%", photourl, fullname));
        guitop.put("#vcard", vcarddetail);
        return guitop;
    }

    private LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm){
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

    public User(String name, String address){
        super("{ \"is\": \"vcard\", \n"+
              "  \"fullName\": \""+name+"\",\n"+
              "  \"address\": \""+address+"\"\n"+
              "}");
    }

    public User(String vcarduid){
        super("{ \"is\": \"user\", \n"+
              "  \"public\": { \"vcard\": \""+vcarduid+"\" }\n"+
              "}");
    }

    private String createUserAndVCard(String id, String name, String address){
        String[] addressbits = address.split("\n");
        if(addressbits.length==0) return "?"+name+address;
        return spawn(new User(spawn(new User(name, addressbits[0]))));
    }

    // ---------------------------------------------------------
}

