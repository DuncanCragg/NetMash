
import java.util.*;
import java.util.regex.*;
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

    private void runRule(int i){
        if(alerted().size()==0) runRule(i,null);
        else for(String alerted: alerted()) runRule(i,alerted);
    }

    @SuppressWarnings("unchecked")
    private void runRule(int i, String alerted){
        log("Running rule \"When "+content(String.format("%%rules:%d:when", i))+"\"");

        LinkedHashMap<String,Object> rule=contentHash(String.format("%%rules:%d:#", i));

        content("%alerted", alerted);
        boolean ok=scanRuleHash(rule, "");
        // if(!ok) roll back
log("==========\nscanRuleHash="+ok+"\n"+rule+"\n"+contentHash("#")+"===========\n");
        content("%alerted", null);
    }

    static public final String REWRITERE = "^<(.*)>(.*)$";
    static public final Pattern REWRITEPA = Pattern.compile(REWRITERE);

    @SuppressWarnings("unchecked")
    private boolean scanRuleHash(LinkedHashMap<String,Object> hash, String path){
        for(Map.Entry<String,Object> entry: hash.entrySet()){
            String k=entry.getKey();
            String pk=path+k;
            if(path.equals("")){
                if(k.equals("is"  )) continue;
                if(k.equals("when")) continue;
            }
            Object v=entry.getValue();
            if(v instanceof String){
                String vs=(String)v;
                if(vs.startsWith("<")){
                    if(vs.startsWith("<[]>")){
                        if(contentSet(pk)) return false;
                        String rhs=vs.substring(4);
                        if(rhs.equals("%alerted")) content(k,content(rhs));
                    }
                    else
                    if(vs.startsWith("<>")){
                        String[] rhsparts=vs.substring(2).split(";");
                        if(k.equals("%notifying")){
                            for(int i=0; i<rhsparts.length; i++){
                                String rhs=rhsparts[i];
                                if(rhs.startsWith("has($"))     notifying(content(rhs.substring(5,rhs.length()-1)));
                                if(rhs.startsWith("hasno($")) unnotifying(content(rhs.substring(7,rhs.length()-1)));
                            }
                        }
                    }
                    else{
                        Matcher m = REWRITEPA.matcher(vs);
                        if(!m.matches()){ if(!contentIsOrListContains(pk,vs)) return false; }
                        else{
                            String lhs = m.group(1);
                            String rhs = m.group(2);
                            if(!contentIsString(pk,lhs)) return false;
                            content(pk,rhs);
                        }
                    }
                }
                else if(!contentIsOrListContains(pk,vs)) return false;
            }
            else
            if(v instanceof LinkedHashMap){
                LinkedHashMap<String,Object> vh=(LinkedHashMap<String,Object>)v;
                if(!scanRuleHash(vh, pk+":")) return false;
            }
            else return false;
        }
        return true;
    }

// two-phase
// $dealer: not $dealer, cos need $x as well

    private void cheaperPriceSimulatingRace(){
        if(contentIs("ticket:status", "filled") && !contentSet("payment")){ logrule();
            contentDouble("params:price", 81.5);
        }
    }

    private void acceptDealAndPay(){
        if( contentListContains("ticket:status", "not-as-ordered") &&
            contentDouble("params:price")==81.5 &&
           !contentSet("payment")              ){ logrule();

            contentDouble("params:price", 81.7);
            double amount = contentDouble("ticket:ask") * contentDouble("params:investment");
            content("payment", spawn(new Fjord(uid, content("ticket"), amount)));
        }
    }

    private void alertTicket(){
        if(!contentSet("invoice:payment")){ logrule();
            notifying(content("invoice"));
        }
    }
}


