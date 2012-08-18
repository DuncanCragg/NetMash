package netmash.forest;

public class Editable extends Fjord {

    public Editable(){}

    public void evaluate(){
        for(String uid: alerted()){ logrule();
            contentSetPush("%rules",uid);
        }
        super.evaluate();
    }
}

