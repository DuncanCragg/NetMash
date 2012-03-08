
import netmash.forest.WebObject;

/** .
  */
public class HRRequestResponse extends WebObject {

    public HRRequestResponse(){}

    public HRRequestResponse(String leavePerioduid){
        super("{ \"is\": [ \"hr\", \"leave-response\" ],\n"+
              "  \"manager\": \"/employees/32323424\",\n"+
              "  \"created\": \"2011-05-05T16:23:25.761+01:00\",\n"+
              "  \"status\": \"approved\",\n"+
              "  \"leavePeriod\": \""+leavePerioduid+"\"\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "leave-request")){
            alertLeaveRecords();
            setLeavePeriod();
            managerApproves();
        }
        else
        if(contentListContains("is", "leave-response")){
            alertLeavePeriod();
        }
    }

    private void alertLeaveRecords(){
        if(!contentSet("leavePeriod") && contentSet("leaveRecords:is:0")){ logrule();
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

    private void managerApproves(){
        if( contentIs("leavePeriod:status", "requested") &&
           !contentSet("leaveResponse")                    ){ logrule();
            content("leaveResponse", spawn(new HRRequestResponse(content("leavePeriod"))));
        }
    }

    private void alertLeavePeriod(){
        if(!contentSet("leavePeriod:leaveResponse")){ logrule();
            notifying(content("leavePeriod"));
        }
    }
}

