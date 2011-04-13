
import netmash.forest.WebObject;

public class Asymmetry extends WebObject {

    public Asymmetry(){}

    public void evaluate(){
        if(contentIs("state", "1")){
            contentInt("state", 2);
        }
        else
        if(contentIs("state", "2")){
        }
        else
        if(contentIs("state", "3")){
        }
    }
}

