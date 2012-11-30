package netmash.types;

import java.util.*;
import netmash.forest.*;
import static netmash.lib.Utils.*;

/** Tracks Presence of Users viewing it.
  */
public class PresenceTracker extends ObjectMash {

    public PresenceTracker(){}

    public void evaluate(){
        if(contentListContains("is", "3d")){
            trackPresence();
        }
        super.evaluate();
    }

    @SuppressWarnings("unchecked")
    private void trackPresence(){ logrule();

        LinkedList subuids=contentAll("sub-objects:object");
        for(String alerted: alerted()){
            contentTemp("Alerted", alerted);
            if(contentIsThis("Alerted:place") && !subuids.contains(alerted)){
                LinkedHashMap hm=new LinkedHashMap();
                hm.put("object", alerted);
                hm.put("coords", list(0,0,0));
                contentListAdd("sub-objects", hm);
                contentInc("present");
            }
            contentTemp("Alerted", null);
        }
        LinkedList subObjects=contentList("sub-objects");
        if(subObjects==null) return;
        int sosize=subObjects.size();
        for(int i=0; i<sosize; i++){
            String placepath=String.format("sub-objects:%d:object:place",i);
            if(!contentSet(placepath)) continue;
            if(contentIsThis(placepath)){
                String coordpath=String.format("sub-objects:%d:object:coords",i);
                String mycrdpath=String.format("sub-objects:%d:coords",i);
                LinkedList coords=contentListClone(coordpath);
                if(coords!=null && coords.size()==3){
                    LinkedList mycoords=contentList(mycrdpath);
                    if(mycoords==null) contentList(mycrdpath,coords);
                    else if(!mycoords.equals(coords) && mycoords.size()==3){
                        contentObject(mycrdpath+":0", coords.get(0));
                        contentObject(mycrdpath+":1", coords.get(1));
                        contentObject(mycrdpath+":2", coords.get(2));
                    }
                }
            }
            else{
                contentListRemove("sub-objects", i);
                contentDec("present");
                i--; sosize--;
            }
        }
    }
}


