
import jungle.forest.WebObject;

/** FX Order Object from Book Chapter example.
  * In Jungle, these WebObject classes are buckets for animation rules, as there isn't a
  * strict concept of class in FOREST. Here we bundle the code for two 'classes' - orders
  * and payments - into one Java file for convenience.
  */
public class FXOrderPayment extends WebObject {

    public FXOrderPayment(){}

    public FXOrderPayment(String orderuid, String ticketuid, double amount){
        super("{ \"is\": [ \"payment\" ],\n"+
              "  \"invoice\": \""+ticketuid+"\",\n"+
              "  \"order\": \""+orderuid+"\",\n"+
              "  \"amount\": "+amount+",\n"+
              "  \"account\": { }\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "order")){
            alertDealer();
            setTicket();
            investMore();
            cheaperPriceSimulatingRace();
            acceptDealAndPay();
        }
    }

    private void alertDealer(){
        if(content("ticket")==null && content("dealer:is:0")!=null){
            notifying(content("dealer"));
        }
    }

    private void setTicket(){
        for(String ticketuid: alerted()){
            content("ticket", ticketuid);
            notifying(ticketuid);
            break;
        }
    }

    private void investMore(){
       if(content("ticket")!=null && contentDouble("params:3")==500.0){
           contentDouble("params:3", 1000.0);
       }
    }

    private void cheaperPriceSimulatingRace(){
        if(contentIs("ticket:status", "filled") && content("payment")==null){
            contentDouble("params:2", 81.5);
        }
    }

    private void acceptDealAndPay(){
        if(contentListContains("ticket:status", "not-as-ordered") &&
           contentDouble("params:2")==81.5 &&
           content("payment")==null              ){

            contentDouble("params:2", 81.7);
            double amount = contentDouble("ticket:ask") * contentDouble("params:3");
            content("payment", spawn(new FXOrderPayment(uid, content("ticket"), amount)));
        }
    }
}

