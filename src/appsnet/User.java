
package appsnet;

import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

import appsnet.gui.*;

/** User viewing the Object Web.
  */
public class User extends WebObject {

    private AppsNet top=null;

    public User(){
        top=AppsNet.top;
        top.onUserReady(this);
        onTopCreate();
    }

    public void evaluate(){
        if(contentListContains("is", "user")){
            showWhatIAmViewing();
        }
    }

    @SuppressWarnings("unchecked")
    private void showWhatIAmViewing(){
        if(contentHash("viewing:#")!=null){
            LinkedHashMap<String,Object> json=contentHash("viewing:#");
            JSON uiJSON=new JSON("{ \"is\": [ \"gui\" ] }");
            uiJSON.listPath("view", flattenHash(json));
            top.drawJSON(uiJSON);
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

    public void onTopCreate(){
    }

    public void onTopDestroy(){
    }

    // ------------------------------------------------
}

