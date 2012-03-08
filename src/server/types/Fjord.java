
import java.util.*;
import netmash.forest.WebObject;

/** Fjord Language.
  * .
  */
public class Fjord extends WebObject {

    public Fjord(){}

    public Fjord(String orderuid, String ticketuid, double amount){
        super("{ \"is\": [ \"payment\" ],\n"+
              "  \"invoice\": \""+ticketuid+"\",\n"+
              "  \"order\": \""+orderuid+"\",\n"+
              "  \"amount\": "+amount+",\n"+
              "  \"account\": { }\n"+
              "}");
    }

    public void evaluate(){
        runRules();
        if(contentListContains("is", "order")){
            setTicket();
            investMore();
            cheaperPriceSimulatingRace();
            acceptDealAndPay();
        }
        else
        if(contentListContains("is", "payment")){
            alertTicket();
        }
    }

    private void runRules(){
        LinkedList rules=contentList("%rules");
        if(rules==null) return;
        int r=0;
        for(Object o: rules){
            LinkedList ruleis=contentList(String.format("%%rules:%d:is", r));
            if(ruleis==null) return;
            boolean ok=true;
            for(Object is: ruleis){
                if("rule".equals(is)) continue;
                if(!contentListContains("is", is.toString())){ ok=false; break; }
            }
            if(ok) runRule(r);
            r++;
        }
    }

    @SuppressWarnings("unchecked")
    private void runRule(int i){
        log("Running rule \"When "+content(String.format("%%rules:%d:when", i))+"\"");
        LinkedHashMap<String,Object> rule = contentHash(String.format("%%rules:%d:#", i));
        log("==========\nscanRuleHash="+scanRuleHash(rule, "")+"\n"+rule+"\n"+this+"===========\n");
    }

    @SuppressWarnings("unchecked")
    private boolean scanRuleHash(LinkedHashMap<String,Object> hash, String path){
        for(Map.Entry<String,Object> entry: hash.entrySet()){
            String k=entry.getKey();
            if(path.equals("")){
                if(k.equals("is"  )) continue;
                if(k.equals("when")) continue;
            }
            Object v=entry.getValue();
            if(v instanceof String){
                String vs=(String)v;
                if(vs.startsWith("<")){
                    if(vs.equals("<[]>")){
                        if(contentSet(k)) return false;
                    }
                    else
                    if(vs.startsWith("<>")){
                        if(k.equals("%notifying") && vs.startsWith("<>has(")){
                            String notify=vs.substring(6, vs.length()-1);
                            if(notify.startsWith("$")){
                                notifying(content(notify.substring(1)));
                            }
                        }
                        String setv=vs.substring(2);
                    }
                }
                else if(!contentIsOrListContains(path+k,vs)) return false;
            }
            else
            if(v instanceof LinkedHashMap){
                LinkedHashMap<String,Object> vh=(LinkedHashMap<String,Object>)v;
                if(!scanRuleHash(vh, path+k+":")) return false;
            }
            else return false;
        }
        return true;
    }

    private void setTicket(){
        for(String ticketuid: alerted()){ logrule();
            content("ticket", ticketuid);
            notifying(ticketuid);
            break;
        }
    }

    private void investMore(){
       if(contentSet("ticket") && contentDouble("params:3")==500.0){ logrule();
           contentDouble("params:3", 1000.0);
       }
    }

    private void cheaperPriceSimulatingRace(){
        if(contentIs("ticket:status", "filled") && !contentSet("payment")){ logrule();
            contentDouble("params:2", 81.5);
        }
    }

    private void acceptDealAndPay(){
        if( contentListContains("ticket:status", "not-as-ordered") &&
            contentDouble("params:2")==81.5 &&
           !contentSet("payment")              ){ logrule();

            contentDouble("params:2", 81.7);
            double amount = contentDouble("ticket:ask") * contentDouble("params:3");
            content("payment", spawn(new Fjord(uid, content("ticket"), amount)));
        }
    }

    private void alertTicket(){
        if(!contentSet("invoice:payment")){ logrule();
            notifying(content("invoice"));
        }
    }
}


