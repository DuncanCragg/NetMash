package cyrus.types;

import java.util.*;
import cyrus.forest.*;
import static cyrus.lib.Utils.*;

/** Tracks Presence of Users viewing it.
  */
public class PresenceTracker extends CyrusLanguage {

    public PresenceTracker(){}

    public void evaluate(){
        trackPresence();
        contentAll("sub-items:item:is");
        super.evaluate();
    }

    @SuppressWarnings("unchecked")
    private void trackPresence(){
        for(String trackuid: alerted()){
            contentTemp("Alerted", trackuid);
            if(!contentSet("Alerted:place")) continue;
            LinkedList subuids=contentAll("sub-items:item");
            int i=(subuids==null)? -1: subuids.indexOf(trackuid);
            if(contentIsThis("Alerted:place")){
                LinkedList coords=contentListClone("Alerted:coords");
                if(coords==null) coords=list(0,0,0);
                if(i== -1){
                    LinkedHashMap hm=new LinkedHashMap();
                    hm.put("item", trackuid);
                    hm.put("coords", coords);
                    contentListAdd("sub-items", hm);
                    contentInc("present");
                }
                else{
                    String mycrdpath=String.format("sub-items:%d:coords",i);
                    LinkedList mycoords=contentList(mycrdpath);
                    if(mycoords==null || !mycoords.equals(coords)) contentList(mycrdpath,coords);
                }
            }
            else{
                if(i!= -1){
                    contentListRemove("sub-items", i);
                    contentDec("present");
                }
            }
            contentTemp("Alerted", null);
        }
    }
}


