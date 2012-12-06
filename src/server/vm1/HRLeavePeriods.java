
import java.util.*;
import cyrus.forest.WebObject;

/** .
  */
public class HRLeavePeriods extends WebObject {

    public HRLeavePeriods(){}

    public HRLeavePeriods(String leaveRequestuid){
        super("{ \"is\": [ \"hr\", \"leave-period\", \"event\" ],\n"+
              "  \"created\": \"2011-05-05T16:23:25.761+01:00\",\n"+
              "  \"status\": \"created\",\n"+
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
        for(String alertedUid: alerted()){ logrule();
            content("alerted", alertedUid);
            if(!contentSet("alerted:leavePeriod")){ // !!
                contentListAddURL("leavePeriods", spawn(new HRLeavePeriods(alertedUid)));
            }
            content("alerted", null);
        }
    }

    private void createOnBackEnd(){
        if(contentIs("status", "created")){ logrule();
            content("status", "new");
            contentURL("leaveRequest", content("leaveRequest"));
            notifying(content("leaveRequest"));
            triggerStatusRoundtrip(content("leaveRequest:status"));
        }
    }

    private void approveOnBackEnd(){
        for(String alertedUid: alerted()){ logrule();
            content("alerted", alertedUid);
            if(contentListContains("alerted:is", "leave-response") &&
               contentIs("status", "requested")                       ){ logrule();

                contentURL("leaveResponse", alertedUid);
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
            try{ Thread.sleep(1000); }catch(Exception e){}
            backEndStatus(status);
        }}.start();
    }

    // ----------------------------------------------------
}


