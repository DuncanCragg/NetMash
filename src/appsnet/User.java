
package appsnet;

import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

import appsnet.gui.*;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    // ---------------------------------------------------------

    public User(){ AppsNet.top.onUserReady(this); }

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

    public void evaluate(){
        if(contentListContains("is", "user")){
            showWhatIAmViewing();
        }
    }

    private void showWhatIAmViewing(){ logrule();
        if(content("viewing:is")!=null){ logrule();
            LinkedHashMap view=null;
            if(contentListContains("viewing:is", "vcardlist")){
                view=vCardList2GUI();
            }
            else
            if(contentListContains("viewing:is", "vcard")){
                view=vCard2GUI();
            }
            else{
                view=guifyHash(contentHash("viewing:#"));
            }
            JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
            uiJSON.hashPath("view", view);
            AppsNet.top.drawJSON(uiJSON);
        }
    }

    private LinkedHashMap vCardList2GUI(){
        LinkedList<String> vcards = contentList("viewing:vcards");
        if(vcards==null) return null;

        LinkedList vcardsgui = new LinkedList();
        vcardsgui.add("direction:vertical");
        int i=0;
        for(String uid: vcards){
            String fullname=content("viewing:vcards:"+(i++)+":fullName");
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


        String homephone=content("viewing:tel:home:0");
        if(homephone==null) homephone=content("viewing:tel:home");
        if(homephone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Home phone:", homephone));

        String workphone=content("viewing:tel:work:0");
        if(workphone==null) workphone=content("viewing:tel:work");
        if(workphone!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Work phone:", workphone));

        String email=content("viewing:email:0");
        if(email==null) email=content("viewing:email");
        if(email!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Email address:", email));

        String url=content("viewing:url:0");
        if(url==null) url=content("viewing:url");
        if(url!=null) vcarddetail.add(list("direction:horizontal", "proportions:35%", "Website:", url));

        String fullname=content("viewing:fullName");
        String photourl=content("viewing:photo");

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
}

