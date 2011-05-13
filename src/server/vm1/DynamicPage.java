
import netmash.forest.WebObject;

public class DynamicPage extends WebObject {

    public DynamicPage(){}

    private boolean running=false;

    public void evaluate(){ if(!running) runTicker(contentInt("view:#incre")); }

    private void tick(final int i){
        new Evaluator(this){
            public void evaluate(){
                contentInt("view:#incre", i);
                refreshObserves();
            }
        };
    }

    private void runTicker(final int start){
        new Thread(){ public void run(){
            for(int i=start+1; true; i++){
                try{ Thread.sleep(5000); }catch(Exception e){}
                tick(i);
            }
        }}.start();
        running=true;
    }

}

