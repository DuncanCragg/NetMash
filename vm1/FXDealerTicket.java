
import java.util.*;
import jungle.forest.WebObject;

/** FX Dealer Object from Book Chapter example.
  * In Jungle, these WebObject classes are buckets for animation rules, as there isn't a
  * strict concept of class in FOREST. Here we bundle the rules for two 'classes' - dealers
  * and tickets - into one Java file for convenience.
  */
public class FXDealerTicket extends WebObject {

    public FXDealerTicket(){}

    public FXDealerTicket(String orderuid){
        super("{ \"is\": [ \"fx\", \"ticket\" ],\n"+
              "  \"order\": \""+orderuid+"\",\n"+
              "  \"ask\": 81.9,\n"+
              "  \"status\": \"waiting\"\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "dealer")){
            makeTicket();
        }
        else
        if(contentListContains("is", "ticket")){
            mirrorOrder();
            configTicket();
            checkNotAsOrdered();
            acceptPayment();
        }
    }

    private void makeTicket(){
        for(String orderuid: alerted()){
            String orderticket = contentOf(orderuid, "ticket");
            if(orderticket==null){
                contentListAdd("tickets", spawn(new FXDealerTicket(orderuid)));
            }
        }
    }

    private void configTicket(){
        if(contentList("params")==null){
            LinkedList ll = contentListClone("order:params");
            if(ll!=null){
                contentList("params", ll);
                notifying(content("order"));
                setUpPseudoMarketMoverInterfaceCallback();
            }
        }
    }

    private void mirrorOrder(){
        if(contentIs("status", "waiting") && 
           contentList("params")!=null && 
           content("order:params")!=null){

            content(      "params:0", content(      "order:params:0"));
            content(      "params:1", content(      "order:params:1"));
            contentDouble("params:2", contentDouble("order:params:2"));
            contentDouble("params:3", contentDouble("order:params:3"));
        }
    }

    private void checkNotAsOrdered(){
        if(contentIs("status", "filled") || contentListContains("status", "filled")){
            if(!content(      "params:0").equals(content(      "order:params:0")) ||
               !content(      "params:1").equals(content(      "order:params:1")) ||
                contentDouble("params:2") !=     contentDouble("order:params:2")  ||
                contentDouble("params:3") !=     contentDouble("order:params:3")    ){

                LinkedList<Object> ll = new LinkedList<Object>();
                ll.add("filled"); ll.add("not-as-ordered");
                contentList("status", ll);
            }
            else{
                content("status", "filled");
            }
        }
    }

    private void acceptPayment(){
        if(contentIs("status","filled") || contentListContains("status", "filled")){
            if(contentDouble("order:payment:amount") == contentDouble("ask") * contentDouble("params:3")){
                content("status", "paid");
                content("payment", content("order:payment"));
            }
        }
    }

    // ----------------------------------------------------

    private void marketMoved(final double price){
        new Evaluator(this){
            public void evaluate(){
                contentDouble("ask", price);
                if(price < contentDouble("params:2")){
                    content("status", "filled");
                }
            }
        };
    }

    // ----------------------------------------------------

    static private double[] prices = { 81.8, 81.6 };
    private void setUpPseudoMarketMoverInterfaceCallback(){
        new Thread(){ public void run(){
            for(int i=0; i<prices.length; i++){
                try{ Thread.sleep(500); }catch(Exception e){}
                marketMoved(prices[i]);
            }
        } }.start();
    }

    // ----------------------------------------------------
}

