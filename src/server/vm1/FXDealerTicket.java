
import java.util.*;
import netmash.forest.WebObject;

/** FX Dealer Object from Book Chapter example.
  * In NetMash, these WebObject classes are buckets for animation rules, as there isn't a
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
        for(String orderuid: alerted()){ logrule();
            content("order", orderuid);
            String orderticket = content("order:ticket");
            if(orderticket==null){
                contentListAdd("tickets", spawn(new FXDealerTicket(orderuid)));
            }
        }
    }

    private void configTicket(){
        if(!contentSet("params")){
            LinkedHashMap lh = contentHashClone("order:params");
            if(lh!=null){ logrule();
                contentHash("params", lh);
                notifying(content("order"));
                setUpPseudoMarketMoverInterfaceCallback();
            }
        }
    }

    private void mirrorOrder(){
        if(contentIs("status", "waiting") && 
           contentSet("params") && 
           contentSet("order:params")){ logrule();

            content(      "params:fxpair",     content(      "order:params:fxpair"));
            content(      "params:fxtype",     content(      "order:params:fxtype"));
            contentDouble("params:price",      contentDouble("order:params:price"));
            contentDouble("params:investment", contentDouble("order:params:investment"));
        }
    }

    private void checkNotAsOrdered(){
        if(contentIs("status", "filled") || contentListContains("status", "filled")){ logrule();
            if(!content(      "params:fxpair").equals(content(      "order:params:fxpair"))  ||
               !content(      "params:fxtype").equals(content(      "order:params:fxtype"))  ||
                contentDouble("params:price")      != contentDouble("order:params:price")    ||
                contentDouble("params:investment") != contentDouble("order:params:investment") ){

                contentList("status", list("filled", "not-as-ordered"));
            }
            else{
                content("status", "filled");
            }
        }
    }

    private void acceptPayment(){
        if(contentIs("status","filled") || contentListContains("status", "filled")){ logrule();
            if(contentDouble("order:payment:amount") == contentDouble("ask") * contentDouble("params:investment")){
                content("status", "paid");
                content("payment", content("order:payment"));
            }
        }
    }

    // ----------------------------------------------------

    private void marketMoved(final double price){
        new Evaluator(this){
            public void evaluate(){ logrule();
                contentDouble("ask", price);
                if(price < contentDouble("params:price")){
                    content("status", "filled");
                }
                refreshObserves();
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

