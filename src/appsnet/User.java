
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

    @SuppressWarnings("unchecked")
    private void showWhatIAmViewing(){ logrule();
        if(contentHash("viewing:#")!=null){ logrule();
            LinkedHashMap<String,Object> json=contentHash("viewing:#");
            JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
            uiJSON.hashPath("view", guifyHash(json));
            AppsNet.top.drawJSON(uiJSON);
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap guifyHash(LinkedHashMap<String,Object> hm){
        LinkedHashMap<String,Object> hm2 = new LinkedHashMap<String,Object>();
        hm2.put("direction", "vertical");
        for(String tag: hm.keySet()){
            Object o=hm.get(tag);
            if(o instanceof LinkedHashMap) hm2.put(tag, guifyHash((LinkedHashMap<String,Object>)o));
            else
            if(o instanceof LinkedList)    hm2.put(tag, guifyList((LinkedList)o));
            else                           hm2.put(tag, o);
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

