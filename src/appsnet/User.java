
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
            uiJSON.listPath("view", flattenHash(json));
            AppsNet.top.drawJSON(uiJSON);
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList flattenHash(LinkedHashMap<String,Object> hm){
       LinkedList list = new LinkedList();
       for(String tag: hm.keySet()){
           list.add(tag);
           Object o=hm.get(tag);
           if(o instanceof LinkedHashMap) list.addAll(flattenHash((LinkedHashMap<String,Object>)o));
           else
           if(o instanceof LinkedList)    list.addAll((LinkedList)o);
           else                           list.add(o);
       }
       return list;
    }

    // ---------------------------------------------------------
}

