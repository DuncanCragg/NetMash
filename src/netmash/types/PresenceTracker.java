package netmash.types;

import java.util.*;
import netmash.forest.*;
import static netmash.lib.Utils.*;

/** Tracks Presence of Users viewing it.
  */
public class PresenceTracker extends ObjectMash {

    public PresenceTracker(){}

    public void evaluate(){
        trackPresence();
        contentAll("sub-objects:object:is");
        super.evaluate();
    }

    @SuppressWarnings("unchecked")
    private void trackPresence(){
        for(String trackuid: alerted()){
            contentTemp("Alerted", trackuid);
            if(!contentSet("Alerted:place")) continue;
            LinkedList subuids=contentAll("sub-objects:object");
            int i=subuids.indexOf(trackuid);
            if(contentIsThis("Alerted:place")){
                LinkedList coords=contentListClone("Alerted:coords");
                if(coords==null) coords=list(0,0,0);
                if(i== -1){
                    LinkedHashMap hm=new LinkedHashMap();
                    hm.put("object", trackuid);
                    hm.put("coords", coords);
                    contentListAdd("sub-objects", hm);
                    contentInc("present");
                }
                else{
                    contentList(String.format("sub-objects:%d:coords",i),coords);
                }
            }
            else{
                if(i!= -1){
                    contentListRemove("sub-objects", i);
                    contentDec("present");
                }
            }
            contentTemp("Alerted", null);
        }
    }
}


