package netmash.forest;

import java.util.*;

import netmash.lib.JSON;

import static netmash.lib.Utils.*;

public class Editable extends ObjectMash {

    public Editable(){}
    public Editable(String jsonstring){ super(jsonstring); }
    public Editable(JSON json){ super(json); }

    public void evaluate(){
        LinkedList<String> evalrules=new LinkedList<String>();
        for(String alerted: alerted()){
            contentTemp("%temp", alerted);
            if(contentListContainsAll("%temp:is",list("editable","rule"))){
                evalrules.add(alerted);
            }
            contentTemp("%temp", null);
        }
// first rule will be skipped first time
        if(!evalrules.isEmpty()) contentSetPushAll("%rules",evalrules);
        super.evaluate();
        if(!evalrules.isEmpty()) contentSetPushAll("%rules",evalrules);
    }
}

