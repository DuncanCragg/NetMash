
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

public class Asymmetry extends WebObject {

    public Asymmetry(){}

    public void evaluate(){ logrule();
        if(contentIs("self:state", 1)){
            log("state 1 - move to state 2 to trigger save to home server");
            contentInt("state", 2);
        }
        else
        if(contentIs("self:state", 2)){
            log("state 2 - check URL has arrived from home server");
            if(url!=null){
                log("yes - set in 'watch' and test watch:state");
                content("watch", url);
                if(contentSet("watch:state")){
                    log("got self from home server - move to state 3");
                    contentInt("state", 3);
                }
            }
        }
        else
        if(contentIs("self:state", 33)) System.exit(1);
        else
        if(contentIs("self:state", 3)){
            log("state 3 - move to state 4");
            contentInt("state", 4);
            contentSet("watch:state");
            content("bobby:full-name");
            contentHash("tick:#");
        }
        else
        if(contentIs("self:state", 4)){
            log("state 4 - refresh watch observe");
            contentSet("watch:state");
        }
        cyrus.platform.Kernel.sleep(600);
    }
}

