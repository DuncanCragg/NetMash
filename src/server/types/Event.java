package server.types;

import netmash.forest.WebObject;

/** Event.
  */
public class Event extends WebObject {

    public Event(){}

    public void evaluate(){
        if(contentListContains("is", "event")){
            testit();
        }
    }

    private void testit(){
        for(String useruid: alerted()){ logrule();
        }
    }
}

