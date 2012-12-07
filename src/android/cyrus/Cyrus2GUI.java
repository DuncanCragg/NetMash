
package cyrus;

import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.database.Cursor;
import android.location.*;
import android.accounts.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.*;
import static android.content.Context.*;
import static android.location.LocationManager.*;

import cyrus.lib.*;
import cyrus.forest.*;
import cyrus.platform.Kernel;

import static cyrus.lib.Utils.*;

import cyrus.gui.Cyrus;
import cyrus.gui.Mesh;

/** Convertors from std Cyrus JSON to common GUI JSON.
  */
public class Cyrus2GUI {

    private User user;
    public Cyrus2GUI(User user){ this.user=user; }

    public LinkedHashMap user2GUI(){
        String useruid = user.content("private:viewing");

        String fullname = user.content("private:viewing:contact:full-name");
        if(fullname==null) fullname="Waiting for contact details "+user.content("private:viewing:contact");

        String contactuid = UID.normaliseUID(useruid, user.content("private:viewing:contact")); // remove normaliseUID
        LinkedList contact=null;
        if(contactuid!=null) contact = list(style("direction","horizontal", "options","jump", "proportions","75%"), "Contact Info:", contactuid);

        String contactsuid = UID.normaliseUID(useruid, user.content("private:viewing:private:contacts")); // remove normaliseUID
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

    public LinkedHashMap links2GUI(){
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

    public LinkedHashMap lookup2GUI(){
        LinkedList viewlist = new LinkedList();
        viewlist.add(style("colours","lightgreen"));
        String title = user.content("private:viewing:title");
        viewlist.add((title!=null && title.length()!=0)? title: "Look-up Table");
        LinkedHashMap<String,Object> entries=user.contentHash("private:viewing:#");
        for(Map.Entry<String,Object> entry: entries.entrySet()){
            String key=entry.getKey();
            if(key.equals("is") || key.equals("title")) continue;
            viewlist.add(list(style("direction","horizontal", "proportions","50%"), capitaliseAndSpace(key), entry.getValue()));
        }
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("#items", viewlist);
        return viewhash;
    }

    public LinkedHashMap contactList2GUI(String contactprefix){
        String listuid = user.content("private:viewing");
        LinkedList<String> contacts = user.contentList("private:viewing:list");
        if(contacts==null) return null;
        LinkedList viewlist = new LinkedList();
        viewlist.add(style("direction","vertical"));
        int i= -1;
        for(String uid: contacts){ i++;
            String contactuid = UID.normaliseUID(listuid, uid); // remove normaliseUID
            String fullname=            user.content("private:viewing:list:"+i+":"+contactprefix+"full-name");
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

    public LinkedHashMap documentList2GUI(){
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
                documentuid = UID.normaliseUID(listuid, uid); // remove normaliseUID
            }
            else
            if(inlineoruid instanceof LinkedHashMap){
                LinkedHashMap<String,String> inl = (LinkedHashMap<String,String>)inlineoruid;
                documentuid = UID.normaliseUID(listuid, inl.get("More")); // remove normaliseUID
                contentType=inl.get("is");
                htmlurl = inl.get("web-view");
                published=inl.get("published");
            }
            if(documentuid==null) documentuid=listuid;
            String          title=user.contentString("private:viewing:list:"+i+":title");
            if(title==null) title=user.contentString("private:viewing:list:"+i+":is");
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
        viewhash.put("#query", hash("input","textfield", "label","Search terms"));
        viewhash.put("#documentlist", viewlist);
        return viewhash;
    }

    @SuppressWarnings("unchecked")
    public LinkedHashMap land2GUI(){
        LinkedList valuescol = new LinkedList();
        valuescol.add(style("direction","vertical"));
        addIfPresent(valuescol, "area", null, false, hash("input","textfield", "label","Area (ha):"));
        LinkedHashMap<String,Object> template=user.contentHashMayJump("private:viewing:place:update-template");
        if(template!=null) for(Map.Entry<String,Object> entry: template.entrySet()){
            Object o=entry.getValue();
            if(!(o instanceof LinkedHashMap)) continue;
            addIfPresent(valuescol, entry.getKey(), null, false, (LinkedHashMap)((LinkedHashMap)o).clone());
        }
        String title=user.content("private:viewing:title");
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours", "lightgreen"));
        viewhash.put("#title", hash("input","textfield", "value",title!=null? title: "Land"));
        viewhash.put("#values", valuescol);
        if(user.contentSet("private:viewing:update-template"))
        viewhash.put("#new", hash("input","textfield", "label","Name of new sub-land entry"));
        viewhash.put("#landlist", list2Col());
        return viewhash;
    }

    public LinkedList contact2Map(String contactprefix){
        String useruid = user.content("private:viewing");
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        maplist.add("layerkey:"+useruid);
        LinkedHashMap location=user.contentHash("private:viewing:location");
        if(location!=null) maplist.add(point(contactprefix, "Location", location, null, useruid));
        LinkedList<String> addressStrings=getAddressesAsString("private:viewing:"+contactprefix+"address", false);
        LinkedList<String> geoAddrStrings=getAddressesAsString("private:viewing:"+contactprefix+"address", true);
        int i= -1;
        for(String address: addressStrings){ i++;
            location=geoCode(geoAddrStrings.get(i));
            if(location==null) continue;
            maplist.add(point(contactprefix, address, location, null, useruid));
        }
        return maplist;
    }

    public LinkedList contactList2Map(String contactprefix){
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
            if(location!=null) maplist.add(point("list:"+c+":"+contactprefix, "Location", location, null, contactuid));
            LinkedList<String> addressStrings=getAddressesAsString("private:viewing:list:"+c+":"+contactprefix+"address", false);
            LinkedList<String> geoAddrStrings=getAddressesAsString("private:viewing:list:"+c+":"+contactprefix+"address", true);
            int i= -1;
            for(String address: addressStrings){ i++;
                location=geoCode(geoAddrStrings.get(i));
                if(location==null) continue;
                maplist.add(point("list:"+c+":"+contactprefix, address, location, null, contactuid));
            }
        }
        return maplist;
    }

    public LinkedList land2Map(){
        String landuid = user.content("private:viewing");
        LinkedList maplist = new LinkedList();
        maplist.add("render:map");
        if(user.contentIsOrListContains("private:viewing:is", "updatable"))
        maplist.add("updatable");
        maplist.add("satellite");
        maplist.add("layerkey:"+landuid);
        LinkedHashMap<String,Double> location=user.contentHash("private:viewing:location");
        LinkedList shape=user.contentList("private:viewing:shape");
        if(location!=null) maplist.add(point("", "Land area", location, shape, landuid));
        LinkedList<String> sublands = user.contentList("private:viewing:list");
        if(sublands!=null){
            int c= -1; for(String uid: sublands){ c++;
                String sublanduid = UID.normaliseUID(landuid, uid);
                location=user.contentHash("private:viewing:list:"+c+":location");
                shape=user.contentList("private:viewing:list:"+c+":shape");
                if(location!=null) maplist.add(point("list:"+c+":", "Land area", location, shape, sublanduid));
            }
        }
        return maplist;
    }

    private LinkedHashMap point(String prefix, String sublabel, LinkedHashMap location, LinkedList shape, String uid){
        LinkedHashMap point = new LinkedHashMap();
        String          label=user.content("private:viewing:"+prefix+"full-name");
        if(label==null) label=user.content("private:viewing:"+prefix+"title");
        point.put("label",    label!=null? label: "");
        point.put("sublabel", sublabel!=null? sublabel: "");
        point.put("location", location);
        point.put("shape", shape);
        point.put("jump", uid);
        return point;
    }

    public LinkedHashMap contact2GUI(boolean editable){

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

        String webView=user.content("private:viewing:web-view:0");
        if(webView==null) webView=user.content("private:viewing:web-view");
        if(webView!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Website:", webView));

        String bio=user.content("private:viewing:bio:0");
        if(bio==null) bio=user.content("private:viewing:bio");
        if(bio!=null) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Bio:", bio));

        LinkedList<String> addressStrings=getAddressesAsString("private:viewing:address",false);
        if(addressStrings.size()!=0) contactdetail.add(list(style("direction","horizontal", "proportions","35%"), "Address:", Utils.join(addressStrings, "\n---\n")));

        addListIfPresent(contactdetail, "publications", "Publications");

        String fullname=user.content("private:viewing:full-name");
        String photourl=user.contentOr("private:viewing:photo","");
        LinkedHashMap<String,Object> titlehash=new LinkedHashMap<String,Object>();
        titlehash.put("style", style("direction","horizontal", "proportions","25%", "colours","lightpink*"));
        titlehash.put("#photo", photourl);
        titlehash.put("full-name", editable? hash("input","textfield", "value",fullname): fullname);

        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical"));
        viewhash.put("#title", titlehash);
        viewhash.put("#contact", contactdetail);
        return viewhash;
    }

    public LinkedHashMap event2GUI(){
        String eventuid = user.content("private:viewing");
        String locationuid = UID.normaliseUID(eventuid, user.content("private:viewing:location")); // remove normaliseUID
        LinkedList event = list(style("colours","lightmauve"),
                                user.contentString("private:viewing:text"),
                                list(style("direction","horizontal", "proportions","30%"), "Start:", user.content("private:viewing:start")),
                                list(style("direction","horizontal", "proportions","30%"), "End:",   user.content("private:viewing:end"))
        );
        if(locationuid!=null) event.add(list(style("direction","horizontal", "options","jump", "proportions","75%"), "Location:", locationuid));

        if(user.contentSet("private:viewing:rating")){
            Double rating=Double.valueOf(user.contentDouble("private:viewing:rating"));
            event.add(hash("input","rating", "label","Overall rating for this event:", "value",rating));
        }
        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        String title = user.content("private:viewing:title");
        viewhash.put("style", style("direction","vertical", "colours","lightblue"));
        viewhash.put("#title", "!["+(title!=null? title: "Event")+"]!");
        viewhash.put("#event", event);

        if(user.contentListContains("private:viewing:is", "reviewable")){
            LinkedList valuescol = new LinkedList();
            valuescol.add(style("direction","vertical"));
            valuescol.add(hash("rating", hash("input","rating", "label","Rate this event")));
            LinkedHashMap<String,Object> template=user.contentHashMayJump("private:viewing:place:review-template");
            if(template!=null) for(Map.Entry<String,Object> entry: template.entrySet()){
                Object o=entry.getValue();
                if(!(o instanceof LinkedHashMap)) continue;
                addIfPresent(valuescol, entry.getKey(), null, false, (LinkedHashMap)((LinkedHashMap)o).clone());
            }
            viewhash.put("#values", valuescol);
        }
        LinkedList reviews = new LinkedList();
        reviews.add(style("direction","vertical", "colours","lightgreen"));
        addListIfPresent(reviews, "reviews", "Reviews");
        viewhash.put("#reviews", reviews);

        if(user.contentListContains("private:viewing:is", "attendable"))
        viewhash.put("attending", hash("input","checkbox", "label","Attending"));

        LinkedList attendees = new LinkedList();
        attendees.add(style("direction","vertical", "colours","lightgreen"));
        addListIfPresent(attendees, "attendees", "Attendees");
        viewhash.put("#attendees", attendees);

        viewhash.put("#eventlist", list2Col());
        return viewhash;
    }

    private LinkedList list2Col(){
        LinkedList items = user.contentList("private:viewing:list");
        LinkedList viewlist = new LinkedList();
        viewlist.add(style("direction","vertical"));
        int i= -1;
        if(items!=null) for(Object o: items){ i++;
            String itemuid=null;
            if(o instanceof String) itemuid = (String)o;
            String          title=user.contentString("private:viewing:list:"+i+":title");
            if(title==null) title=user.contentString("private:viewing:list:"+i+":is");
            if(title==null) viewlist.add("Loading..");
            else            viewlist.add(list(style("direction","horizontal", "colours","lightblue", "proportions","75%"), title, itemuid));
        }
        return viewlist;
    }

    public LinkedHashMap article2GUI(){

        boolean article=user.contentIsOrListContains("private:viewing:is", "article");
        String title=user.content("private:viewing:title");

        LinkedList citationcol = new LinkedList();
        citationcol.add(style("direction","vertical"));
        addIfPresent(citationcol, "web-view", "View on Web:", true, null);
        addIfPresent(citationcol, "published", "Published:", false, null);
        addIfPresent(citationcol, "publisher", "Publisher:", false, null);
        addIfPresent(citationcol, "journaltitle", "Journal:", false, null);
        addIfPresent(citationcol, "booktitle", "From:", false, null);
        addIfPresent(citationcol, "pages", "Pages:", false, null);
        addIfPresent(citationcol, "volume", "Volume:", false, null);
        addIfPresent(citationcol, "issue", "Issue:", false, null);
        addIfPresent(citationcol, "doi", "DOI:", false, null);
        addIfPresent(citationcol, "dx-doi", "View via dx.doi:", true, null);

        LinkedList authorsandrefscol = new LinkedList();
        authorsandrefscol.add(style("direction","vertical"));
        addListIfPresent(authorsandrefscol, "authors", "Authors");
        addListIfPresent(authorsandrefscol, "references", "References");

        LinkedList textcol = new LinkedList();
        textcol.add(style("direction","vertical"));
        LinkedList text=user.contentAsList("private:viewing:text");
        if(text!=null) for(Object para: text) textcol.add(para);

        LinkedHashMap<String,Object> viewhash = new LinkedHashMap<String,Object>();
        viewhash.put("style", style("direction","vertical", "colours",article? "lightblue": "lightmauve"));
        viewhash.put("#title", title!=null? title: "Article");
        viewhash.put("#citation", citationcol);
        viewhash.put("#authorsandrefs", authorsandrefscol);
        viewhash.put("#text", textcol);
        return viewhash;
    }

    // --------------------------------------------------

    private void addIfPresent(LinkedList list, String tag, String label, boolean isLink, LinkedHashMap widget){
        Object currentvalue=user.contentObject("private:viewing:"+tag);
        if(label!=null){
            if(currentvalue==null) return;
            list.add(hash("style",style("direction","horizontal", "proportions",isLink? "75%": "50%"), "label",label, "#"+tag,currentvalue));
        }
        else{
            if(currentvalue!=null) widget.put("value",currentvalue);
            list.add(hash("style",style("direction","horizontal", "proportions", "99%"), "#"+tag,widget));
        }
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
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":when");
                if(bmtext!=null) bmtext="When "+bmtext;
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":title");
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":full-name");
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":contact:full-name");
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":user:contact:full-name");
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":is");
                if(bmtext==null) bmtext=user.contentString(prefix+":"+i+":tags");
            }
            if(bmtext==null) bmtext="Loading..";
            String bmuid = UID.normaliseUID(listuid, uid); // remove normaliseUID
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
        l=address.get("postal-code");   if(l!=null){ as.append(" \""); as.append(l); as.append("\""); }
        l=address.get("country");       if(l!=null){ as.append(" ");   as.append(l); }
    }

    public void getAddressAsString(Object o, StringBuilder as){
        if(!(o instanceof LinkedHashMap)){ as.append(o.toString()); as.append("\n");  return; }
        LinkedHashMap address = (LinkedHashMap)o;
        Object l; String s;
        l=address.get("postbox");     if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=addressGetStreet(address);  if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("locality");    if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("region");      if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("postal-code"); if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
        l=address.get("country");     if(l!=null){ s=l.toString().trim(); if(s.length()!=0){ as.append(l); as.append("\n"); }}
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

    // ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm, boolean editable){
        String text=new JSON(hm).toString(true);
        Object o;
        if(!editable) o=hash("input","textfield", "scroll",true, "fixed",true, "value",text);
        else          o=hash("input","textfield", "scroll",true,               "value",text);
        LinkedHashMap<String,Object> hm2 = new LinkedHashMap<String,Object>();
        hm2.put("json", o);
        return hm2;
    }

    // ---------------------------------------------------------------------------

    private HashMap<String,LinkedHashMap<String,Double>> geoCodeCache=new HashMap<String,LinkedHashMap<String,Double>>();
    private LinkedHashMap<String,Double> geoCode(String address){ log("geoCode "+address);
        if(address==null || address.equals("")) return null;
        LinkedHashMap<String,Double> loc=geoCodeCache.get(address);
        if(loc!=null){ log("cached result="+loc); return loc; }
        if(Cyrus.top==null){ log("No Activity to geoCode from"); return null; }
        Geocoder geocoder = new Geocoder(Cyrus.top.getApplicationContext(), Locale.getDefault());
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

    // ---------------------------------------------------------------------------

    public LinkedHashMap scene2GUI(){ logXX("scene2GUI",user.alerted());

        for(String a: user.alerted()) if(a.equals(user.uid) && user.alerted().size()==1){ user.refreshObserves(); return null; }

        LinkedHashMap objhash=object2mesh("private:viewing:",false);
        if(objhash==null) return null;
        mesh2uidPut(objhash, user.content("private:viewing"), user.content("private:viewing"));

        LinkedList subone=new LinkedList();

        addEditingToSubs(subone);

        LinkedList subs=user.contentAsList("private:viewing:sub-objects");

        if(subs==null){ objhash.put("sub-objects", subone); return objhash; }

        LinkedList subtwo=new LinkedList();

        String parentuid=user.content("private:viewing");
        for(int i=0; i< subs.size(); i++){
            String p=String.format("private:viewing:sub-objects:%d",i);
            addObjectToSubs(parentuid,p,subone,subtwo,0,0,0);
        }
        for(int i=0; i< subs.size(); i++){
            String o=String.format("private:viewing:sub-objects:%d:object",i);
            String c=String.format("private:viewing:sub-objects:%d:coords",i);
            String s=String.format("private:viewing:sub-objects:%d:object:sub-objects",i);
            LinkedList subcoords=user.contentAsList(c);
            LinkedList subsubs  =user.contentAsList(s);
            if(subsubs==null) continue;
            float tx=Mesh.getFloatFromList(subcoords,0,0),ty=Mesh.getFloatFromList(subcoords,1,0),tz=Mesh.getFloatFromList(subcoords,2,0);
            parentuid=user.content(o);
            for(int j=0; j< subsubs.size(); j++){
                String q=String.format("private:viewing:sub-objects:%d:object:sub-objects:%d",i,j);
                addObjectToSubs(parentuid,q,subone,subtwo,tx,ty,tz);
            }
        }
        subtwo.addAll(subone);
        objhash.put("sub-objects", subtwo);
        return objhash;
    }

    private void addObjectToSubs(String parentuid, String p, LinkedList subone, LinkedList subtwo, float tx, float ty, float tz){
        LinkedHashMap objhash=object2mesh(p+":object:", true);
        if(objhash==null) return;
        mesh2uidPut(objhash, parentuid, user.content(p+":object"));
        LinkedHashMap hm=new LinkedHashMap();
        hm.put("object",objhash);
        LinkedList coords=new LinkedList();
        LinkedList subcoords=user.contentAsList(p+":coords");
        coords.add(tx+Mesh.getFloatFromList(subcoords,0,0));
        coords.add(ty+Mesh.getFloatFromList(subcoords,1,0));
        coords.add(tz+Mesh.getFloatFromList(subcoords,2,0));
        hm.put("coords",coords);
        ((objhash.get("light")==null)? subone: subtwo).add(hm);
    }

    private void addEditingToSubs(LinkedList subobs){
        LinkedHashMap objhash=object2edit();
        if(objhash==null) return;
        LinkedHashMap hm=new LinkedHashMap();
        hm.put("object",objhash);
        mesh2uidPut(objhash, "", "editing");
        subobs.add(hm);
    }

    private LinkedHashMap object2mesh(String p, boolean shallow){
        LinkedList is=user.contentAsList(p+"is");
        if(is==null) return null;
        if(is.contains("mesh"))   return mesh2mesh(p, shallow);
        if(is.contains("cuboid")) return cuboid2mesh(p);
        if(is.contains("notice")) return notice2mesh(p);
        if(is.contains("user"))   return mesh2mesh(p+"avatar:", shallow);
        return null;
    }

    private LinkedHashMap mesh2mesh(String p, boolean shallow){
        String vs=user.content(p+"vertex-shader");
        String fs=user.content(p+"fragment-shader");
        shadersPut(vs, p+"vertex-shader");
        shadersPut(fs, p+"fragment-shader");

        if(shallow) return user.contentHash(p+"#");

        LinkedHashMap objhash=oldHashIfEtagSame(p);
        if(!objhash.isEmpty()) return objhash;

        objhash.put("is", "mesh");
        objhash.put("title",         user.contentObject(p+"title"));
        objhash.put("rotation",      user.contentObject(p+"rotation"));
        objhash.put("scale",         user.contentObject(p+"scale"));
        objhash.put("light",         user.contentObject(p+"light"));
        objhash.put("vertices",      user.contentObject(p+"vertices"));
        objhash.put("texturepoints", user.contentObject(p+"texturepoints"));
        objhash.put("normals",       user.contentObject(p+"normals"));
        objhash.put("faces",         user.contentObject(p+"faces"));
        objhash.put("textures",      user.contentObject(p+"textures"));
        objhash.put("vertex-shader",  vs);
        objhash.put("fragment-shader",fs);
        return objhash;
    }

    private LinkedHashMap cuboid2mesh(String p){
        String vs=user.content(p+"vertex-shader");
        String fs=user.content(p+"fragment-shader");
        shadersPut(vs, p+"vertex-shader");
        shadersPut(fs, p+"fragment-shader");

        LinkedHashMap objhash=oldHashIfEtagSame(p);
        if(!objhash.isEmpty()) return objhash;

        objhash.put("is", "mesh");
        objhash.put("title",         user.contentObject(p+"title"));
        objhash.put("rotation",      user.contentObject(p+"rotation"));
        objhash.put("scale",         user.contentObject(p+"scale"));
        objhash.put("light",         user.contentObject(p+"light"));
        objhash.put("vertices",      list(list(  1.0, -1.0, -1.0 ), list(  1.0, -1.0,  1.0 ), list( -1.0, -1.0,  1.0 ), list( -1.0, -1.0, -1.0 ),
                                          list(  1.0,  1.0, -1.0 ), list(  1.0,  1.0,  1.0 ), list( -1.0,  1.0,  1.0 ), list( -1.0,  1.0, -1.0 )));
        objhash.put("texturepoints", list(list( 1.0, 1.0 ), list( 1.0, 0.0 ), list( 0.0, 0.0 ), list( 0.0, 1.0 ) ));
        objhash.put("normals",       list(list( -1.0,  0.0,  0.0 ), list( 1.0, 0.0, 0.0 ),
                                          list(  0.0, -1.0,  0.0 ), list( 0.0, 1.0, 0.0 ),
                                          list(  0.0,  0.0, -1.0 ), list( 0.0, 0.0, 1.0 )));
        objhash.put("faces",         list(list( "5/1/5","1/2/5","4/3/5" ), list( "5/1/5","4/3/5","8/4/5" ), list( "3/1/1","7/2/1","8/3/1" ),
                                          list( "3/1/1","8/3/1","4/4/1" ), list( "2/1/6","6/2/6","3/4/6" ), list( "6/2/6","7/3/6","3/4/6" ),
                                          list( "1/1/2","5/2/2","2/4/2" ), list( "5/2/2","6/3/2","2/4/2" ), list( "5/1/4","8/2/4","6/4/4" ),
                                          list( "8/2/4","7/3/4","6/4/4" ), list( "1/1/3","2/2/3","3/3/3" ), list( "1/1/3","3/3/3","4/4/3" )));
        objhash.put("textures",      user.contentObject(p+"textures"));
        objhash.put("vertex-shader",  vs);
        objhash.put("fragment-shader",fs);

        return objhash;
    }

    private LinkedHashMap notice2mesh(String p){
        String vs=user.content(p+"vertex-shader");
        String fs=user.content(p+"fragment-shader");
        shadersPut(vs, p+"vertex-shader");
        shadersPut(fs, p+"fragment-shader");

        LinkedHashMap objhash=oldHashIfEtagSame(p);
        if(!objhash.isEmpty()) return objhash;

        String    title=user.contentString(p+"title");
        LinkedList text=user.contentAsList(p+"text");
        if(title==null) title="No Title";
        if(text ==null){ String t=user.contentString(p+"text"); if(t!=null) text=list(t); }
        if(text ==null) text=list("No Text");
        String key=text2Bitmap(title,text);

        objhash.put("is", "mesh");
        objhash.put("title",         title);
        objhash.put("rotation",      user.contentObject(p+"rotation"));
        objhash.put("scale",         user.contentObject(p+"scale"));
        objhash.put("light",         user.contentObject(p+"light"));
        objhash.put("vertices",      list(list(  1.0,  0.0, -0.1 ), list(  1.0,  0.0,  0.1 ), list( -1.0,  0.0,  0.1 ), list( -1.0,  0.0, -0.1 ),
                                          list(  1.0,  1.0, -0.1 ), list(  1.0,  1.0,  0.1 ), list( -1.0,  1.0,  0.1 ), list( -1.0,  1.0, -0.1 )));
        objhash.put("texturepoints", list(list( 1.0, 0.5 ), list( 1.0, 0.0 ), list( 0.0, 0.0 ), list( 0.0, 0.5 ) ));
        objhash.put("normals",       list(list( -1.0,  0.0,  0.0 ), list( 1.0, 0.0, 0.0 ),
                                          list(  0.0, -1.0,  0.0 ), list( 0.0, 1.0, 0.0 ),
                                          list(  0.0,  0.0, -1.0 ), list( 0.0, 0.0, 1.0 )));
        objhash.put("faces",         list(list( "5/1/5","1/2/5","4/3/5" ), list( "5/1/5","4/3/5","8/4/5" ), list( "3/1/1","7/2/1","8/3/1" ),
                                          list( "3/1/1","8/3/1","4/4/1" ), list( "2/1/6","6/2/6","3/4/6" ), list( "6/2/6","7/3/6","3/4/6" ),
                                          list( "1/1/2","5/2/2","2/4/2" ), list( "5/2/2","6/3/2","2/4/2" ), list( "5/1/4","8/2/4","6/4/4" ),
                                          list( "8/2/4","7/3/4","6/4/4" ), list( "1/1/3","2/2/3","3/3/3" ), list( "1/1/3","3/3/3","4/4/3" )));
        objhash.put("textures", list(key));
        objhash.put("vertex-shader",  vs);
        objhash.put("fragment-shader",fs);

        return objhash;
    }

    private LinkedHashMap object2edit(){

        if(!user.contentSet("private:editing") || user.contentIs("private:editing","")) return null;

        LinkedHashMap objhash=oldHashIfEtagSame("private:editing:");
        if(!objhash.isEmpty()) return objhash;

        String title="Edit Panel";
        String text=user.contentString("private:editing:title");
        if(text==null) text="No Title";
        String key=text2Bitmap(title,list(text));

        objhash.put("is", "mesh");
        objhash.put("title", title);
        objhash.put("vertices",      list(list(  1.0,  0.0, -0.1 ), list(  1.0,  0.0,  0.1 ), list( -1.0,  0.0,  0.1 ), list( -1.0,  0.0, -0.1 ),
                                          list(  1.0,  1.0, -0.1 ), list(  1.0,  1.0,  0.1 ), list( -1.0,  1.0,  0.1 ), list( -1.0,  1.0, -0.1 )));
        objhash.put("texturepoints", list(list( 1.0, 0.5 ), list( 1.0, 0.0 ), list( 0.0, 0.0 ), list( 0.0, 0.5 ) ));
        objhash.put("normals",       list(list( -1.0,  0.0,  0.0 ), list( 1.0, 0.0, 0.0 ),
                                          list(  0.0, -1.0,  0.0 ), list( 0.0, 1.0, 0.0 ),
                                          list(  0.0,  0.0, -1.0 ), list( 0.0, 0.0, 1.0 )));
        objhash.put("faces",         list(list( "2/1/6","6/2/6","3/4/6" ), list( "6/2/6","7/3/6","3/4/6" )));
        objhash.put("textures", list(key));

        return objhash;
    }

    private String text2Bitmap(String title, LinkedList text){
        String key=title+text;
        Bitmap bitmap = user.textBitmaps.get(key);
        if(bitmap!=null) return key;
        bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        if(Cyrus.top!=null){
            Drawable background = Cyrus.top.getPlaceHolderDrawable();
            background.setBounds(0, 0, 256, 256);
            background.draw(canvas);
        }
        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setARGB(0xff, 0xff, 0xff, 0xff);

        textPaint.setTextSize(24);
        canvas.drawText(title, 10,30, textPaint);

        textPaint.setTextSize(20);
        int y=50;
        for(Object o: text){
            canvas.drawText(o.toString(), 10,y, textPaint);
            y+=20;
        }
        user.textBitmaps.put(key, bitmap);
        return key;
    }

    private void mesh2uidPut(LinkedHashMap mesh, String parentuid, String uid){
        if(mesh!=null && uid!=null) user.mesh2uid.put(System.identityHashCode(mesh),UID.normaliseUID(parentuid,uid));
    }

    private void shadersPut(String url, String path){
        if(url==null) return;
        if(user.shaders.get(url)!=null) return;
        LinkedList shader=user.contentListMayJump(path);
        if(shader==null) return;
        user.shaders.put(url, shader);
    }

    public ConcurrentHashMap<String,Integer>       etags  = new ConcurrentHashMap<String,Integer>();
    public ConcurrentHashMap<String,LinkedHashMap> meshes = new ConcurrentHashMap<String,LinkedHashMap>();

    private LinkedHashMap oldHashIfEtagSame(String p){
        String uid=user.content(p);
        int newetag=user.contentInt(p+"Version");
        Integer oldetag=etags.get(uid);
        if(oldetag!=null && oldetag.intValue()==newetag) return meshes.get(uid);
        etags.put(uid, newetag);
        LinkedHashMap objhash=new LinkedHashMap();
        meshes.put(uid, objhash);
        return objhash;
    }

    // ---------------------------------------------------------------------------
}

