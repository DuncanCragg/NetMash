
import jungle.forest.WebObject;

/** Class of WebObject to test a ping-pong of two objects that set themselves one higher
  * than each other until they reach 200.
  */
public class CountTo200 extends WebObject {

    public CountTo200(){}

    public CountTo200(String pair){
        super("{ \"pair\": \""+pair+"\", \"count\": 1 }");
    }

    public void evaluate(){
        initialStateWithPair();
        initialStateNoPair();
        setOneHigherThanPair();
    }

    private void initialStateWithPair(){
        if(contentInt("count")==1 && content("pair:count")!=null){
            notifying(content("pair"));
            contentInt("count", 2);
        }
    }

    private void initialStateNoPair(){
        if(contentIs("pair","")){
            for(String pairlink: alerted()){
                content("pair", pairlink);
                notifying(pairlink);
                contentInt("count", 2);
                break;
            }
        }
    }

    private void setOneHigherThanPair(){
        if(contentInt("pair:count") >=2 && contentInt("count") >=2 && contentInt("count")< 200){
            contentInt("count", contentInt("pair:count")+1);
            if(contentInt("count") >=200) log("done: \n"+this+"->\n"+this.updatingState+"\n-------------");
        }
    }
}

