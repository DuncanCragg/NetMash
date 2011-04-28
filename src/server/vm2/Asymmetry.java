
import netmash.forest.WebObject;

public class Asymmetry extends WebObject {

    public Asymmetry(){}

    public void evaluate(){ logrule();
        if(contentIs("self:state", "1")){
            log("state 1 - move to state 2 to trigger save to home server");
            contentInt("state", 2);
        }
        else
        if(contentIs("self:state", "2")){
            log("state 2 - check URL has arrived from home server");
            if(url!=null){
                log("yes - set in 'watch' and test watch:state");
                content("watch", url);
                if(content("watch:state")!=null){
                    log("got self from home server - move to state 3");
                    contentInt("state", 3);
                }
            }
        }
        else
        if(contentIs("self:state", "3")){
            log("state 3 - move to state 4");
            contentInt("state", 4);
            content("watch:state");
            content("bobby:fullName");
        }
        else
        if(contentIs("self:state", "4")){
            log("state 4 - refresh watch observe");
            content("watch:state");
        }
        netmash.platform.Kernel.sleep(600);
    }
}

