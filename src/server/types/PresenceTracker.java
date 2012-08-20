package server.types;

import java.util.*;
import netmash.forest.Editable;

/** Tracks Presence of Users viewing it.
  */
public class PresenceTracker extends Editable {

    public PresenceTracker(){}

    public void evaluate(){
        if(contentListContains("is", "3d")){
            trackPresence();
        }
        super.evaluate();
    }

    @SuppressWarnings("unchecked")
    private void trackPresence(){
/*
        for(String useruid: alerted()){ logrule();
            LinkedHashMap hm=new LinkedHashMap();
            hm.put("object",useruid);
            hm.put("coords", list(0,4,0));
            contentListAdd("mesh:subObjects", hm);
        }
*/
        LinkedList subObjects=contentList("mesh:subObjects");
        if(subObjects!=null) for(int i=0; i< subObjects.size(); i++){
            String placepath=String.format("mesh:subObjects:%d:object:place",i);
            String coordpath=String.format("mesh:subObjects:%d:object:coords",i);
            String mycrdpath=String.format("mesh:subObjects:%d:coords",i);
            if(contentIsThis(placepath)){
                LinkedList coords=contentListClone(coordpath);
                if(coords!=null) contentList(mycrdpath, coords);
            }
        }
    }
}


