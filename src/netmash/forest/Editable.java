package netmash.forest;

public class Editable extends Fjord {

    public Editable(){}

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

