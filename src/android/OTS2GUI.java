
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

    public LinkedHashMap user2GUI(){ WebObject.logrule();
        String useruid = user.content("private:viewing");

        String fullname = user.content("private:viewing:vcard:fullName");
        if(fullname==null) fullname="Waiting for contact details "+user.content("private:viewing:vcard");

        String vcarduid = UID.normaliseUID(useruid, user.content("private:viewing:vcard"));
        LinkedList vcard=null;
        if(vcarduid!=null) vcard = WebObject.list("direction:horizontal", "options:jump", "proportions:75%", "Contact Info:", vcarduid);

        String contactsuid = UID.normaliseUID(useruid, user.content("private:viewing:private:contacts"));
        LinkedList contacts=null;
        if(contactsuid!=null) contacts = WebObject.list("direction:horizontal", "options:jump", "proportions:75%", "Phone Contacts:", contactsuid);

        LinkedHashMap loc = user.contentHash("private:viewing:location");
        LinkedList location=null;
        if(loc!=null) location = WebObject.list("direction:horizontal", "Location:", loc.get("lat"), loc.get("lon"));

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightpink");
        guitop.put("#title", fullname);
        if(vcard    !=null) guitop.put("#vcard",    vcard);
        if(contacts !=null) guitop.put("#contacts", contacts);
        if(location !=null) guitop.put("#location", location);
        return guitop;
    }

    public LinkedHashMap links2GUI(){ WebObject.logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> links = user.contentList("private:viewing:list");
        if(links==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: links){ i++;
            String bmtext=null;
            if(user.contentSet("private:viewing:list:"+i+":is")){
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":title");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":fullName");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":vcard:fullName");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":is");
                if(bmtext==null) bmtext=user.content("private:viewing:list:"+i+":tags");
            }
            if(bmtext==null) bmtext="Loading..";
            String bmuid = UID.normaliseUID(listuid, uid);
            viewlist.add(WebObject.list("direction:horizontal", "options:jump", "proportions:75%", bmtext, bmuid));
        }
        String title = user.content("private:viewing:title");
        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightgreen");
        guitop.put("#title", title!=null? title: "Links");
        guitop.put("#links", viewlist);
        return guitop;
    }

    public LinkedHashMap vCardList2GUI(String vcardprefix){ WebObject.logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> contacts = user.contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add("direction:vertical");
        int i= -1;
        for(String uid: contacts){ i++;
            String contactuid = UID.normaliseUID(listuid, uid);
            String fullname=            user.content("private:viewing:list:"+i+":"+vcardprefix+"fullName");
            if(fullname==null) fullname=user.content("private:viewing:list:"+i+":is");
            if(fullname==null) viewlist.add("Loading..");
            else               viewlist.add(WebObject.list("direction:horizontal", "options:jump", "proportions:75%", fullname, contactuid));
        }
        String title = user.content("private:viewing:title");
        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightpink");
        guitop.put("#title", title!=null? title: "Contacts List");
        guitop.put("#contactlist", viewlist);
        return guitop;
    }

    public LinkedList vCard2Map(String vcardprefix){ WebObject.logrule();
        String useruid = user.content("private:viewing");
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+useruid);
        LinkedHashMap location=user.contentHash("private:viewing:location");
        if(location==null) location=geoCode(getGeoAddressString("private:viewing:"+vcardprefix+"address"));
        if(location==null) return maplist;
        LinkedHashMap point = new LinkedHashMap();
        String fullname=user.content(   "private:viewing:"+vcardprefix+"fullName");
        String address=getAddressString("private:viewing:"+vcardprefix+"address");
        point.put("label",    fullname!=null? fullname: "");
        point.put("sublabel", address!=null? address: "");
        point.put("location", location);
        point.put("jump", useruid);
        maplist.add(point);
        return maplist;
    }

    public LinkedList vCardList2Map(String vcardprefix){ WebObject.logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> contacts = user.contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+listuid);
        int i= -1;
        for(String uid: contacts){ i++;
            LinkedHashMap<String,Double> location=null;
            if(!vcardprefix.equals(""))   location=user.contentHash("private:viewing:list:"+i+":location");
            if(location==null)            location=user.contentHash("private:viewing:list:"+i+":"+vcardprefix+"location");
            if(location==null) location=geoCode(getGeoAddressString("private:viewing:list:"+i+":"+vcardprefix+"address"));
            if(location==null) continue;
            LinkedHashMap point = new LinkedHashMap();
            String fullname=user.content(   "private:viewing:list:"+i+":"+vcardprefix+"fullName");
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

    public LinkedHashMap vCard2GUI(){ WebObject.logrule();

        LinkedList vcarddetail = new LinkedList();
        vcarddetail.add("direction:vertical");

        String mainphone=user.content("private:viewing:tel:0");
        if(mainphone==null) mainphone=user.content("private:viewing:tel");
        if(mainphone!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Phone:", mainphone));

        String homephone=user.content("private:viewing:tel:home:0");
        if(homephone==null) homephone=user.content("private:viewing:tel:home");
        if(homephone!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Home phone:", homephone));

        String workphone=user.content("private:viewing:tel:work:0");
        if(workphone==null) workphone=user.content("private:viewing:tel:work");
        if(workphone!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Work phone:", workphone));

        String mobilephone=user.content("private:viewing:tel:mobile:0");
        if(mobilephone==null) mobilephone=user.content("private:viewing:tel:mobile");
        if(mobilephone!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Mobile:", mobilephone));

        String faxnumber=user.content("private:viewing:tel:fax:0");
        if(faxnumber==null) faxnumber=user.content("private:viewing:tel:fax");
        if(faxnumber!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Fax:", faxnumber));

        String email=user.content("private:viewing:email:0");
        if(email==null) email=user.content("private:viewing:email");
        if(email!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Email address:", email));

        String url=user.content("private:viewing:url:0");
        if(url==null) url=user.content("private:viewing:url");
        if(url!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Website:", url));

        String address=getAddressString("private:viewing:address");
        if(address!=null) vcarddetail.add(WebObject.list("direction:horizontal", "proportions:35%", "Address:", address));

        String fullname=user.content("private:viewing:fullName");
        String photourl=user.content("private:viewing:photo");
        if(photourl==null) photourl="";

        LinkedHashMap<String,Object> guitop = new LinkedHashMap<String,Object>();
        guitop.put("direction", "vertical");
        guitop.put("colours", "lightpink");
        guitop.put("#title", WebObject.list("direction:horizontal", "proportions:25%", photourl, fullname));
        guitop.put("#vcard", vcarddetail);
        return guitop;
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
        Object l=street;              if(l!=null){                   as.append(l); }
        l=address.get("postalCode");  if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("countryName"); if(l!=null){ as.append(" ");   as.append(l); }
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
        Object l=street;              if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("locality");    if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("region");      if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("postalCode");  if(l!=null){ as.append(l); as.append("\n"); }
        l=address.get("countryName"); if(l!=null){ as.append(l); as.append("\n"); }
        return as.toString();
    }

    public LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm, String objuid){ WebObject.logrule();
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

    public LinkedList guifyList(LinkedList ll, String objuid){
        LinkedList ll2 = new LinkedList();
        ll2.add(ll.size()<=1? "direction:horizontal": "direction:vertical");
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
    private LinkedHashMap<String,Double> geoCode(String address){ WebObject.log("geoCode "+address);
        if(address==null || address.equals("")) return null;
        LinkedHashMap<String,Double> loc=geoCodeCache.get(address);
        if(loc!=null){ WebObject.log("cached result="+loc); return loc; }
        if(NetMash.top==null){ WebObject.log("No Activity to geoCode from"); return null; }
        Geocoder geocoder = new Geocoder(NetMash.top.getApplicationContext(), Locale.getDefault());
        try{
            List<Address> geos = geocoder.getFromLocationName(address, 1);
            if(!geos.isEmpty()){
                if(geos.size() >=2) WebObject.log(geos.size()+" locations found for "+address);
                Address geo1 = geos.get(0);
                loc = new LinkedHashMap<String,Double>();
                loc.put("lat", geo1.getLatitude());
                loc.put("lon", geo1.getLongitude());
                geoCodeCache.put(address,loc);
                return loc;
            }
            else WebObject.log("No getFromLocationName for "+address);
        }catch(Exception e){ WebObject.log("No getFromLocationName for "+address); WebObject.log(e); }
        return null;
    }
}

