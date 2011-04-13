
import netmash.forest.WebObject;

public class Asymmetry extends WebObject {

    public Asymmetry(){}

    public void evaluate(){
        if(contentIs("self:state", "1")){
            contentInt("state", 2);
        }
        else
        if(contentIs("self:state", "2")){
            contentInt("state", 3);
        }
        else
        if(contentIs("self:state", "3")){
            contentInt("state", 4);
        }
        netmash.platform.Kernel.sleep(100);
    }
}

