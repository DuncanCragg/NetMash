package netmash.forest;

import java.util.*;

import static netmash.lib.Utils.*;

public class Editable extends ObjectMash {

    public Editable(){}
    public Editable(String jsonstring){ super(jsonstring); }

    public void evaluate(){
        LinkedList<String> evalrules=new LinkedList<String>();
        for(String alerted: alerted()){
            contentTemp("%alerted", alerted);
            if(contentListContainsAll("%alerted:is",list("editable","rule"))){
                evalrules.add(alerted);
            }
            contentTemp("%alerted", null);
        }
// first rule will be skipped first time
        contentSetPushAll("%rules",evalrules);
        super.evaluate();
        contentSetPushAll("%rules",evalrules);
    }
}

