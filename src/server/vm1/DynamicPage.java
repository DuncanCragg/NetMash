
import netmash.forest.WebObject;

public class DynamicPage extends WebObject {

    public DynamicPage(){}

    public void evaluate(){
        if(contentIs("view:#incre", "1")){
            runTicker();
            contentInt("view:#incre", 2);
        }
    }

    private void tick(final int i){
        new Evaluator(this){
            public void evaluate(){
                contentInt("view:#incre", i);
                refreshObserves();
            }
        };
    }

    private void runTicker(){
        new Thread(){ public void run(){
            for(int i=1000; true; i++){
                try{ Thread.sleep(5000); }catch(Exception e){}
                tick(i);
            }
        }}.start();
    }

}

