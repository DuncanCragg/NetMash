
import netmash.forest.WebObject;

/** .
  */
public class HRRequestResponse extends WebObject {

    public HRRequestResponse(){}

    public HRRequestResponse(String leaveRequestuid, String leavePerioduid, double amount){
        super("{ \"is\": [ \"leave-response\" ],\n"+
              "  \"leavePeriod\": \""+leavePerioduid+"\",\n"+
              "  \"leaveRequest\": \""+leaveRequestuid+"\",\n"+
              "  \"amount\": "+amount+",\n"+
              "  \"account\": { }\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "leave-request")){
            alertLeaveRecords();
            setLeavePeriod();
            investMore();
            cheaperPriceSimulatingRace();
            acceptDealAndPay();
        }
        else
        if(contentListContains("is", "leave-response")){
            alertLeavePeriod();
        }
    }

    private void alertLeaveRecords(){
        if(content("leavePeriod")==null && contentSet("leaveRecords:is:0")){ logrule();
            notifying(content("leaveRecords"));
        }
    }

    private void setLeavePeriod(){
        for(String leavePerioduid: alerted()){ logrule();
            content("leavePeriod", leavePerioduid);
            notifying(leavePerioduid);
            break;
        }
    }

    private void investMore(){
       if(contentSet("leavePeriod") && contentDouble("params:3")==500.0){ logrule();
           contentDouble("params:3", 1000.0);
       }
    }

    private void cheaperPriceSimulatingRace(){
        if(contentIs("leavePeriod:status", "filled") && content("leaveResponse")==null){ logrule();
            contentDouble("params:2", 81.5);
        }
    }

    private void acceptDealAndPay(){
        if(contentListContains("leavePeriod:status", "not-as-requested") &&
           contentDouble("params:2")==81.5 &&
           content("leaveResponse")==null              ){ logrule();

            contentDouble("params:2", 81.7);
            double amount = contentDouble("leavePeriod:ask") * contentDouble("params:3");
            content("leaveResponse", spawn(new HRRequestResponse(uid, content("leavePeriod"), amount)));
        }
    }

    private void alertLeavePeriod(){
        if(!contentSet("leavePeriod:leaveResponse")){ logrule();
            notifying(content("leavePeriod"));
        }
    }
}

