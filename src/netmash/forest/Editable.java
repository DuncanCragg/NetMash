package netmash.forest;

import static netmash.lib.Utils.*;

public class Editable extends ObjectMash {

    public Editable(){}
    public Editable(String jsonstring){ super(jsonstring); }

    public void evaluate(){
        for(String alerted: alerted()){ logrule();
            contentTemp("%alerted", alerted);
            if(contentListContainsAll("%alerted:is",list("editable","rule"))){
                contentSetPush("%rules",alerted);
            }
            contentTemp("%alerted", null);
        }
        super.evaluate();
    }
}

