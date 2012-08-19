package server.types;

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

    private void trackPresence(){
        for(String useruid: alerted()){ logrule();
            contentSetPush("mesh:list", useruid);
        }
    }
}


