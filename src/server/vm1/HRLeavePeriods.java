
import java.util.*;
import netmash.forest.WebObject;

/** .
  */
public class HRLeavePeriods extends WebObject {

    public HRLeavePeriods(){}

    public HRLeavePeriods(String leaveRequestuid){
        super("{ \"is\": [ \"hr\", \"leave-period\" ],\n"+
              "  \"leaveRequest\": \""+leaveRequestuid+"\",\n"+
              "  \"ask\": 81.9,\n"+
              "  \"status\": \"waiting\"\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "leave-record")){
            makeLeavePeriod();
        }
        else
        if(contentListContains("is", "leave-period")){
            mirrorLeaveRequest();
            configLeavePeriod();
            checkNotAsRequested();
            acceptLeaveResponse();
        }
    }

    private void makeLeavePeriod(){
        for(String leaveRequestuid: alerted()){ logrule();
            content("leaveRequest", leaveRequestuid);
            String leaveRequestLeavePeriod = content("leaveRequest:leavePeriod");
            if(leaveRequestLeavePeriod==null){
                contentListAdd("leavePeriods", spawn(new HRLeavePeriods(leaveRequestuid)));
            }
        }
    }

    private void configLeavePeriod(){
        if(contentList("params")==null){
            LinkedList ll = contentListClone("leaveRequest:params");
            if(ll!=null){ logrule();
                contentList("params", ll);
                notifying(content("leaveRequest"));
                setUpPseudoMarketMoverInterfaceCallback();
            }
        }
    }

    private void mirrorLeaveRequest(){
        if(contentIs("status", "waiting") && 
           contentSet("params") && 
           contentSet("leaveRequest:params")){ logrule();

            content(      "params:0", content(      "leaveRequest:params:0"));
            content(      "params:1", content(      "leaveRequest:params:1"));
            contentDouble("params:2", contentDouble("leaveRequest:params:2"));
            contentDouble("params:3", contentDouble("leaveRequest:params:3"));
        }
    }

    private void checkNotAsRequested(){
        if(contentIs("status", "filled") || contentListContains("status", "filled")){ logrule();
            if(!content(      "params:0").equals(content(      "leaveRequest:params:0")) ||
               !content(      "params:1").equals(content(      "leaveRequest:params:1")) ||
                contentDouble("params:2") !=     contentDouble("leaveRequest:params:2")  ||
                contentDouble("params:3") !=     contentDouble("leaveRequest:params:3")    ){

                contentList("status", list("filled", "not-as-requested"));
            }
            else{
                content("status", "filled");
            }
        }
    }

    private void acceptLeaveResponse(){
        if(contentIs("status","filled") || contentListContains("status", "filled")){ logrule();
            if(contentDouble("leaveRequest:leaveResponse:amount") == contentDouble("ask") * contentDouble("params:3")){
                content("status", "paid");
                content("leaveResponse", content("leaveRequest:leaveResponse"));
            }
        }
    }

    // ----------------------------------------------------

    private void marketMoved(final double price){
        new Evaluator(this){
            public void evaluate(){ logrule();
                contentDouble("ask", price);
                if(price < contentDouble("params:2")){
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

