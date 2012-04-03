
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

/** Convertors from std OTS JSON to common GUI OTS JSON.
  */
public class OTS2GUI {

    private User user;
    public OTS2GUI(User user){ this.user=user; }

    public LinkedHashMap user2GUI(){ logrule();
        String useruid = user.content("private:viewing");

        String fullname = user.content("private:viewing:contact:fullName");
        if(fullname==null) fullname="Waiting for contact details "+user.content("private:viewing:contact");

        String contactuid = UID.normaliseUID(useruid, user.content("private:viewing:contact"));
        LinkedList contact=null;
        if(contactuid!=null) contact = list(style("direction","horizontal", "options","jump", "proportions","75%"), "Contact Info:", contactuid);

        String contactsuid = UID.normaliseUID(useruid, user.content("private:viewing:private:contacts"));
        LinkedList contacts=null;
        if(contactsuid!=null) contacts = list(style("direction","horizontal", "options","jump", "proportions","75%"),"Phone Contacts:", contactsuid);

        LinkedHashMap loc = user.contentHash("private:viewing:location");
        LinkedList location=null;
        if(loc!=null) location = list(style("direction","horizontal"), "Location:", loc.get("lat"), loc.get("lon"));

        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightpink"));
        viewhash.put("#title", fullname);
        if(contact    !=null) viewhash.put("#contact",    contact);
        if(contacts !=null) viewhash.put("#contacts", contacts);
        if(location !=null) viewhash.put("#location", location);
        return viewhash;
    }

    public LinkedHashMap links2GUI(){ logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> links = user.contentList("private:viewing:list");
        if(links==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add(style("direction","vertical"));
        int i= -1;
        for(String uid: links){ i++;
            String bmtext=null;
            if(user.contentSet("private:viewing:list:"+i+":is")){
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":title");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":fullName");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":contact:fullName");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":is");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":tags");
            }
            if(bmtext==null) bmtext="Loading..";
            String bmuid = UID.normaliseUID(listuid, uid);
            viewlist.add(list(style("direction","horizontal", "options","jump", "proportions","75%"), bmtext, bmuid));
        }
        String title = user.content("private:viewing:title");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightgreen"));
        viewhash.put("#title", title!=null? title: "Links");
        viewhash.put("#links", viewlist);
        return viewhash;
    }

    public LinkedHashMap contactList2GUI(String contactprefix){ logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> contacts = user.contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add(style("direction","vertical"));
        int i= -1;
        for(String uid: contacts){ i++;
            String contactuid = UID.normaliseUID(listuid, uid);
            String fullname=            user.content("private:viewing:list:"+i+":"+contactprefix+"fullName");
            if(fullname==null) fullname=user.content("private:viewing:list:"+i+":is");
            if(fullname==null) viewlist.add("Loading..");
            else               viewlist.add(list(style("direction","horizontal", "options","jump", "proportions","75%"), fullname, contactuid));
        }
        String title = user.content("private:viewing:title");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightpink"));
        viewhash.put("#title", title!=null? title: "Contacts List");
        viewhash.put("#contactlist", viewlist);
        return viewhash;
    }

    public LinkedHashMap documentList2GUI(){ logrule();
        String listuid = user.content("private:viewing");
        LinkedList documents = user.contentList("private:viewing:list");
        LinkedList viewlist = new LinkedList();
        viewlist.add(style("direction","vertical"));
        int i= -1;
        if(documents!=null) for(Object inlineoruid: documents){ i++;
            String documentuid=null;
            if(inlineoruid instanceof String){
                String uid = (String)inlineoruid;
                documentuid = UID.normaliseUID(listuid, uid);
            }
            else
            if(inlineoruid instanceof LinkedHashMap){
                LinkedHashMap<String,String> inl = (LinkedHashMap<String,String>)inlineoruid;
                documentuid = UID.normaliseUID(listuid, inl.get("%more"));
            }
            if(documentuid==null) documentuid=listuid;
            String          title=user.content("private:viewing:list:"+i+":title");
            if(title==null) title=user.content("private:viewing:list:"+i+":is");
            if(title==null) viewlist.add("Loading..");
            else            viewlist.add(list(style("direction","horizontal", "options","jump", "proportions","75%"), title, documentuid));
        }
        String title = user.content("private:viewing:title");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightyellow"));
        viewhash.put("#title", title!=null? title: "Document List");
        viewhash.put("#query", "?[Query the collection: /string/]?");
        viewhash.put("#documentlist", viewlist);
        return viewhash;
    }

    public LinkedList contact2Map(String contactprefix){ logrule();
        String useruid = user.content("private:viewing");
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+useruid);
        LinkedHashMap location=user.contentHash("private:viewing:location");
        if(location==null) location=geoCode(getGeoAddressString("private:viewing:"+contactprefix+"address"));
        if(location==null) return maplist;
        LinkedHashMap point = new LinkedHashMap();
        String fullname=user.content(   "private:viewing:"+contactprefix+"fullName");
        String address=getAddressString("private:viewing:"+contactprefix+"address");
        point.put("label",    fullname!=null? fullname: "");
        point.put("sublabel", address!=null? address: "");
        point.put("location", location);
        point.put("jump", useruid);
        maplist.add(point);
        return maplist;
    }

    public LinkedList contactList2Map(String contactprefix){ logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> contacts = user.contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+listuid);
        int i= -1;
        for(String uid: contacts){ i++;
            LinkedHashMap<String,Double> location=null;
            if(!contactprefix.equals("")) location=user.contentHash("private:viewing:list:"+i+":location");
            if(location==null)            location=user.contentHash("private:viewing:list:"+i+":"+contactprefix+"location");
            if(location==null) location=geoCode(getGeoAddressString("private:viewing:list:"+i+":"+contactprefix+"address"));
            if(location==null) continue;
            LinkedHashMap point = new LinkedHashMap();
            String fullname=user.content(   "private:viewing:list:"+i+":"+contactprefix+"fullName");
            String address=getAddressString("private:viewing:list:"+i+":"+contactprefix+"address");
            String contactuid = UID.normaliseUID(listuid, uid);
            point.put("label",    fullname!=null? fullname: "");
            point.put("sublabel", address!=null? address: "");
            point.put("location", location);
            point.put("jump", contactuid);
            maplist.add(point);
        }
        return maplist;
    }

    public LinkedHashMap contact2GUI(){ logrule();

        LinkedList contactdetail = new LinkedList();
        contactdetail.add(style("direction","vertical"));

        String mainphone=user.content("private:viewing:phone:0");
        if(mainphone==null) mainphone=user.content("private:viewing:phone");
        if(mainphone!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Phone:", mainphone));

        String homephone=user.content("private:viewing:phone:home:0");
        if(homephone==null) homephone=user.content("private:viewing:phone:home");
        if(homephone!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Home phone:", homephone));

        String workphone=user.content("private:viewing:phone:work:0");
        if(workphone==null) workphone=user.content("private:viewing:phone:work");
        if(workphone!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Work phone:", workphone));

        String mobilephone=user.content("private:viewing:phone:mobile:0");
        if(mobilephone==null) mobilephone=user.content("private:viewing:phone:mobile");
        if(mobilephone!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Mobile:", mobilephone));

        String faxnumber=user.content("private:viewing:phone:fax:0");
        if(faxnumber==null) faxnumber=user.content("private:viewing:phone:fax");
        if(faxnumber!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Fax:", faxnumber));

        String email=user.content("private:viewing:email:0");
        if(email==null) email=user.content("private:viewing:email");
        if(email!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Email address:", email));

        String webURL=user.content("private:viewing:webURL:0");
        if(webURL==null) webURL=user.content("private:viewing:webURL");
        if(webURL!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Website:", webURL));

        String address=getAddressString("private:viewing:address");
        if(address!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Address:", address));

        String fullname=user.content("private:viewing:fullName");
        String photourl=user.content("private:viewing:photo");
        if(photourl==null) photourl="";

        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightpink"));
        viewhash.put("#title", list(style("direction","horizontal", "proportions","25%"), photourl, fullname));
        viewhash.put("#contact", contactdetail);
        return viewhash;
    }

    public LinkedHashMap event2GUI(){ logrule();
        String eventuid = user.content("private:viewing");
        String locationuid = UID.normaliseUID(eventuid, user.content("private:viewing:location"));
        LinkedList event = list(style("colours","lightmauve"),
                                user.content("private:viewing:content"),
                                list(style("direction","horizontal", "proportions","30%"), "Start:", user.content("private:viewing:start")),
                                list(style("direction","horizontal", "proportions","30%"), "End:",   user.content("private:viewing:end")),
                                list(style("direction","horizontal", "options","jump", "proportions","75%"), "Location:", locationuid)
        );
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        String title = user.content("private:viewing:title");
        viewhash.put("style", style("direction","vertical", "colours","lightblue"));
        viewhash.put("#title", "!["+(title!=null? title: "Event")+"]!");
        viewhash.put("#event", event);
        return viewhash;
    }

    public LinkedHashMap article2GUI(){ logrule();
        String title=user.content("private:viewing:title");
        LinkedList articledetail = new LinkedList();
        articledetail.add(style("direction","vertical"));
        LinkedList content=user.contentList("private:viewing:content");
        if(content!=null) for(Object para: content) articledetail.add(para.toString());
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightpink"));
        viewhash.put("#title", title!=null? title: "Article");
        viewhash.put("#article", articledetail);
        return viewhash;
    }

    public String getGeoAddressString(String path){
        int numberofstreetlines=1;
        LinkedHashMap address = user.contentHash(path);
        if(address==null) return user.content(path);
        Object street = address.get("street");
        if(street instanceof List){
            List<String> streetlist = (List<String>)street;
            StringBuilder streetb=new StringBuilder();
            int i=0;
            for(String line: streetlist){ i++; streetb.append(line); if(i==numberofstreetlines) break; }
            street=streetb.toString().trim();
        }
        StringBuilder as=new StringBuilder();
        Object l=street;             if(l!=null){                   as.append(l); }
        l=address.get("postalCode"); if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("country");    if(l!=null){ as.append(" ");   as.append(l); }
        return as.toString();
    }

    public String getAddressString(String path){
        String address=getAddressAsString(path);
        if(address==null) return null;
        if(address.length()==0) return null;
        return address;
    }

    public String getAddressAsString(String path){
        LinkedHashMap address = user.contentHash(path);
        if(address==null) return user.content(path);
        Object street = address.get("street");
        if(street instanceof List){
            List<String> streetlist = (List<String>)street;
            StringBuilder streetb=new StringBuilder();
            for(String line: streetlist){ streetb.append(line); streetb.append("\n"); }
            street=streetb.toString().trim();
        }
        StringBuilder as=new StringBuilder();
        Object l=street;             if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("locality");   if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("region");     if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("postalCode"); if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("country");    if(l!=null){ as.append(l); as.append("\n"); }
        return as.toString();
    }

    public LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm, String objuid){ logrule();
        LinkedHashMap<String,Object> hm2 = new LinkedHashMap<String,Object>();
        hm2.put("style", style("direction", hm.size()<=1? "horizontal": "vertical"));
        hm2.put(".open","{");
        for(String tag: hm.keySet()){
            LinkedList ll = new LinkedList();
            ll.add(style("direction","horizontal"));
            ll.add(" "+tag+":");
            Object o=hm.get(tag);
            addToList(ll,o,objuid);
            hm2.put("#"+tag,ll);
        }
        hm2.put(".close","}");
        return hm2;
    }

    public LinkedList guifyList(LinkedList ll, String objuid){
        LinkedList ll2 = new LinkedList();
        ll2.add(style("direction", ll.size()<=1? "horizontal": "vertical"));
        ll2.add("[");
        for(Object o: ll) addToList(ll2,o,objuid);
        ll2.add("]");
        return ll2;
    }

    public void addToList(LinkedList ll, Object o, String objuid){
        if(o instanceof LinkedHashMap) ll.add(guifyHash((LinkedHashMap<String,Object>)o, objuid));
        else
        if(o instanceof LinkedList)    ll.add(guifyList((LinkedList)o, objuid));
        else
        if(UID.isUID(o))               ll.add(UID.normaliseUID(objuid, (String)o));
        else                           ll.add(" "+o);
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

    static public void log(Object o){ WebObject.log(o); }
    static public void logrule(){ WebObject.logrule(); }
    static public LinkedList list(Object...args){ return WebObject.list(args); }
    static public LinkedHashMap style(Object...args){ return WebObject.hash(WebObject.hash("is","style"), args); }
}

