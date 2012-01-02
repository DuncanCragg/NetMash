
import java.util.*;
import netmash.forest.WebObject;

/** .
  */
public class HRLeavePeriods extends WebObject {

    public HRLeavePeriods(){}

    public HRLeavePeriods(String leaveRequestuid){
        super("{ \"is\": [ \"hr\", \"leave-period\", \"event\" ],\n"+
              "  \"title\":  \"Trip to Spain\",\n"+
              "  \"start\": \"2011-06-08+01:00\",\n"+
              "  \"end\": \"2011-06-13+01:00\",\n"+
              "  \"attendees\": \"/employees/32323424\",\n"+
              "  \"created\": \"2011-05-05T16:23:25.761+01:00\",\n"+
              "  \"status\": \"created\",\n"+
              "  \"leaveType\": \"Annual Leave\",\n"+
              "  \"leaveAmount\":  5,\n"+
              "  \"leaveUnits\": \"Days\",\n"+
              "  \"leaveRequest\": \""+leaveRequestuid+"\"\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "leave-record")){
            makeLeavePeriod();
        }
        else
        if(contentListContains("is", "leave-period")){
            createOnBackEnd();
            approveOnBackEnd();
        }
    }

    private void makeLeavePeriod(){
        for(String leaveRequestuid: alerted()){ logrule();
            content("leaveRequest", leaveRequestuid);
            if(!contentSet("leaveRequest:leavePeriod")){ // !!
                contentListAdd("leavePeriods", spawn(new HRLeavePeriods(leaveRequestuid)));
            }
        }
    }

    private void createOnBackEnd(){
        if(contentIs("status", "created")){
            content("status", "new");
            notifying(content("leaveRequest"));
            triggerStatusRoundtrip(content("leaveRequest:status"));
        }
    }

    private void approveOnBackEnd(){
        for(String alertedUid: alerted()){ logrule();
            content("alerted", alertedUid);
            if(contentListContains("alerted:is", "leave-response") &&
               contentIs("status", "requested")                       ){

                content("leaveResponse", alertedUid);
                triggerStatusRoundtrip(content("leaveResponse:status"));
            }
            content("alerted", null);
        }
    }

    // ----------------------------------------------------

    private void backEndStatus(final String status){
        new Evaluator(this){
            public void evaluate(){ logrule();
                content("status", status);
                refreshObserves();
            }
        };
    }

    private void triggerStatusRoundtrip(final String status){
        new Thread(){ public void run(){
            try{ Thread.sleep(500); }catch(Exception e){}
            backEndStatus(status);
        }}.start();
    }

    // ----------------------------------------------------
}


