
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
        String title = user.content("private:viewing:title");
        linksList2GUI(links, viewlist, "private:viewing:list", listuid, title!=null? title: "Links");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightgreen"));
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
            String contentType="article";
            String htmlurl=null;
            String published=null;
            if(inlineoruid instanceof String){
                String uid = (String)inlineoruid;
                documentuid = UID.normaliseUID(listuid, uid);
            }
            else
            if(inlineoruid instanceof LinkedHashMap){
                LinkedHashMap<String,String> inl = (LinkedHashMap<String,String>)inlineoruid;
                documentuid = UID.normaliseUID(listuid, inl.get("%more"));
                contentType=inl.get("is");
                htmlurl = inl.get("webView");
                published=inl.get("published");
            }
            if(documentuid==null) documentuid=listuid;
            String          title=user.content("private:viewing:list:"+i+":title");
            if(title==null) title=user.content("private:viewing:list:"+i+":is");
            if(title==null) viewlist.add("Loading..");
            else {
                String colour=contentType.equals("article")? "lightblue": "lightmauve";
                viewlist.add(list(style("direction","horizontal", "colours",colour, "proportions","75%"), title, documentuid));
                if(htmlurl!=null) viewlist.add(list(style("direction","horizontal", "colours",colour, "proportions","75%"), "View on Web:", htmlurl));
                if(published!=null) viewlist.add(list(style("direction","horizontal", "colours",colour, "proportions","50%"), "Published:", published));
            }
        }
        String title = user.content("private:viewing:title");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours","lightblue"));
        viewhash.put("#title", title!=null? title: "Document List");
        viewhash.put("#logo", user.content("private:viewing:logo"));
        viewhash.put("#counts", user.contentString("private:viewing:contentCount"));
        if(user.contentIsOrListContains("private:viewing:is", "searchable"))
        viewhash.put("#query", "?[Search terms /string/]?");
        viewhash.put("#documentlist", viewlist);
        return viewhash;
    }

    public LinkedList contact2Map(String contactprefix){ logrule();
        String useruid = user.content("private:viewing");
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+useruid);
        LinkedHashMap location=user.contentHash("private:viewing:location");
        if(location!=null) maplist.add(point(contactprefix, "Location", location, useruid));
        LinkedList<String> addressStrings=getAddressesAsString("private:viewing:"+contactprefix+"address", false);
        LinkedList<String> geoAddrStrings=getAddressesAsString("private:viewing:"+contactprefix+"address", true);
        int i= -1;
        for(String address: addressStrings){ i++;
            location=geoCode(geoAddrStrings.get(i));
            if(location==null) continue;
            maplist.add(point(contactprefix, address, location, useruid));
        }
        return maplist;
    }

    public LinkedList contactList2Map(String contactprefix){ logrule();
        String listuid = user.content("private:viewing");
        LinkedList<String> contacts = user.contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+listuid);
        int c= -1;
        for(String uid: contacts){ c++;
            String contactuid = UID.normaliseUID(listuid, uid);
            LinkedHashMap<String,Double> location=null;
            if(!contactprefix.equals("")) location=user.contentHash("private:viewing:list:"+c+":location");
            if(location==null)            location=user.contentHash("private:viewing:list:"+c+":"+contactprefix+"location");
            if(location!=null) maplist.add(point("list:"+c+":"+contactprefix, "Location", location, contactuid));
            LinkedList<String> addressStrings=getAddressesAsString("private:viewing:list:"+c+":"+contactprefix+"address", false);
            LinkedList<String> geoAddrStrings=getAddressesAsString("private:viewing:list:"+c+":"+contactprefix+"address", true);
            int i= -1;
            for(String address: addressStrings){ i++;
                location=geoCode(geoAddrStrings.get(i));
                if(location==null) continue;
                maplist.add(point("list:"+c+":"+contactprefix, address, location, contactuid));
            }
        }
        return maplist;
    }

    private LinkedHashMap point(String contactprefix, String address, LinkedHashMap location, String uid){
        LinkedHashMap point = new LinkedHashMap();
        String fullname=user.content("private:viewing:"+contactprefix+"fullName");
        point.put("label",    fullname!=null? fullname: "");
        point.put("sublabel", address!=null? address: "");
        point.put("location", location);
        point.put("jump", uid);
        return point;
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

        String webView=user.content("private:viewing:webView:0");
        if(webView==null) webView=user.content("private:viewing:webView");
        if(webView!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Website:", webView));

        LinkedList<String> addressStrings=getAddressesAsString("private:viewing:address",false);
        if(addressStrings.size()!=0) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Address:", join(addressStrings, "\n---\n")));

        addListIfPresent(contactdetail, "publications", "Publications");

        String fullname=user.content("private:viewing:fullName");
        String photourl=user.content("private:viewing:photo");
        if(photourl==null) photourl="";

        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical"));
        viewhash.put("#title", list(style("direction","horizontal", "proportions","25%", "colours","lightpink*"), photourl, fullname));
        viewhash.put("#contact", contactdetail);
        return viewhash;
    }

    public LinkedHashMap event2GUI(){ logrule();
        String eventuid = user.content("private:viewing");
        String locationuid = UID.normaliseUID(eventuid, user.content("private:viewing:location"));
        LinkedList event = list(style("colours","lightmauve"),
                                user.content("private:viewing:content"),
                                list(style("direction","horizontal", "proportions","30%"), "Start:", user.content("private:viewing:start")),
                                list(style("direction","horizontal", "proportions","30%"), "End:",   user.content("private:viewing:end"))
        );
        if(locationuid!=null) event.add(list(style("direction","horizontal", "options","jump", "proportions","75%"), "Location:", locationuid));
        LinkedList attendees = new LinkedList();
        attendees.add(style("direction","vertical"));
        addListIfPresent(attendees, "attendees", "Attendees");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        String title = user.content("private:viewing:title");
        viewhash.put("style", style("direction","vertical", "colours","lightblue"));
        viewhash.put("#title", "!["+(title!=null? title: "Event")+"]!");
        viewhash.put("#event", event);
        viewhash.put("#attendees", attendees);
        if(user.contentIsOrListContains("private:viewing:is", "attendable"))
        viewhash.put("#rsvp", "?[Attending /boolean/]?");
        return viewhash;
    }

    public LinkedHashMap article2GUI(){ logrule();

        boolean article=user.contentIsOrListContains("private:viewing:is", "article");
        String title=user.content("private:viewing:title");

        LinkedList citationcol = new LinkedList();
        citationcol.add(style("direction","vertical"));
        addIfPresent(citationcol, "webView", "View on Web:", true);
        addIfPresent(citationcol, "published", "Published:", false);
        addIfPresent(citationcol, "publisher", "Publisher:", false);
        addIfPresent(citationcol, "journaltitle", "Journal:", false);
        addIfPresent(citationcol, "booktitle", "From:", false);
        addIfPresent(citationcol, "pages", "Pages:", false);
        addIfPresent(citationcol, "volume", "Volume:", false);
        addIfPresent(citationcol, "issue", "Issue:", false);
        addIfPresent(citationcol, "doi", "DOI:", false);
        addIfPresent(citationcol, "dxDoi", "View via dx.doi:", true);

        LinkedList authorsandrefscol = new LinkedList();
        authorsandrefscol.add(style("direction","vertical"));
        addListIfPresent(authorsandrefscol, "authors", "Authors");
        addListIfPresent(authorsandrefscol, "references", "References");

        LinkedList contentcol = new LinkedList();
        contentcol.add(style("direction","vertical"));
        LinkedList content=user.contentList("private:viewing:content");
        if(content!=null) for(Object para: content) contentcol.add(para);

        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours",article? "lightblue": "lightmauve"));
        viewhash.put("#title", title!=null? title: "Article");
        viewhash.put("#citation", citationcol);
        viewhash.put("#authorsandrefs", authorsandrefscol);
        viewhash.put("#content", contentcol);
        return viewhash;
    }

    private void addIfPresent(LinkedList list, String tag, String label, boolean isLink){
        String value=user.content("private:viewing:"+tag);
        if(value!=null) list.add(list(style("direction","horizontal", "proportions",isLink? "75%": "50%"), label, value));
    }

    private void addListIfPresent(LinkedList viewlist, String tag, String label){
        String listuid = user.content("private:viewing");
        String prefix="private:viewing:"+tag;
        LinkedList<String> links = (LinkedList<String>)user.contentList(prefix);
        if(links!=null && links.size()!=0) linksList2GUI(links, viewlist, prefix, listuid, label);
    }

    public void linksList2GUI(LinkedList<String> links, LinkedList viewlist, String prefix, String listuid, String label){
        if(label!=null && label.length()!=0) viewlist.add(label);
        int i= -1;
        for(String uid: links){ i++;
            String bmtext=null;
            if(user.contentSet(prefix+":"+i+":is")){
                if(bmtext==null) bmtext=user.content(prefix+":"+i+":title");
                if(bmtext==null) bmtext=user.content(prefix+":"+i+":fullName");
                if(bmtext==null) bmtext=user.content(prefix+":"+i+":contact:fullName");
                if(bmtext==null) bmtext=user.content(prefix+":"+i+":is");
                if(bmtext==null) bmtext=user.content(prefix+":"+i+":tags");
            }
            if(bmtext==null) bmtext="Loading..";
            String bmuid = UID.normaliseUID(listuid, uid);
            viewlist.add(list(style("direction","horizontal", "options","jump", "proportions","75%"), bmtext, bmuid));
        }
    }

    public LinkedList<String> getAddressesAsString(String path, boolean geo){
        LinkedList<String> addressStrings = new LinkedList<String>();
        LinkedList addresses = user.contentList(path);
        if(addresses==null){
            LinkedHashMap address = user.contentHash(path);
            if(address==null){ String a=user.contentString(path); if(a!=null) addressStrings.add(a); return addressStrings; }
            addresses=new LinkedList();
            addresses.add(address);
        }
        for(Object address: addresses){
            StringBuilder as=new StringBuilder();
            if(geo) getGeoAddressAsString(address, as);
            else    getAddressAsString(address, as);
            addressStrings.add(as.toString().trim());
        }
        return addressStrings;
    }

    public void getGeoAddressAsString(Object o, StringBuilder as){
        if(!(o instanceof LinkedHashMap)){ as.append(o.toString());  return; }
        LinkedHashMap address = (LinkedHashMap)o;
        Object l;
        l=addressGetGeoStreet(address); if(l!=null){                   as.append(l); }
        l=address.get("locality");      if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("region");        if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("postalCode");    if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("country");       if(l!=null){ as.append(" ");   as.append(l); }
    }

    public void getAddressAsString(Object o, StringBuilder as){
        if(!(o instanceof LinkedHashMap)){ as.append(o.toString()); as.append("\n");  return; }
        LinkedHashMap address = (LinkedHashMap)o;
        Object l; String s;
        l=address.get("postbox");    if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=addressGetStreet(address); if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("locality");   if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("region");     if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("postalCode"); if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("country");    if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
    }

    public Object addressGetGeoStreet(LinkedHashMap address){
        Object street = address.get("street");
        if(street==null) return null;
        if(!(street instanceof List)) return street.toString();
        List<String> streetlist = (List<String>)street;
        StringBuilder streetb=new StringBuilder();
        int i=0;
        for(String line: streetlist){ i++; streetb.append(line); streetb.append(" "); if(i==10) break; }
        return streetb.toString().trim();
    }

    public Object addressGetStreet(LinkedHashMap address){
        Object street = address.get("street");
        if(street==null) return null;
        if(!(street instanceof List)) return street.toString();
        List<String> streetlist = (List<String>)street;
        StringBuilder streetb=new StringBuilder();
        for(String line: streetlist){ streetb.append(line); streetb.append("\n"); }
        return streetb.toString().trim();
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

    public String join(LinkedList<String> strings, String joinwith){
	StringBuilder sb=new StringBuilder();
        for(String s: strings){ sb.append(s); sb.append(joinwith); }
        String all=sb.toString();
        return all.substring(0,all.length()-joinwith.length());
    }

    static public void log(Object o){ WebObject.log(o); }
    static public void logrule(){ WebObject.logrule(); }
    static public LinkedList list(Object...args){ return WebObject.list(args); }
    static public LinkedHashMap style(Object...args){ return WebObject.hash(WebObject.hash("is","style"), args); }
}

