
import java.util.*;
import java.util.regex.*;
import netmash.forest.WebObject;

/** Fjord Language.
  * .
  */
public class Fjord extends WebObject {

    public Fjord(){}

    public Fjord(LinkedHashMap hm){ super(hm); }

    public Fjord(String orderuid){
        super("{ \"is\": [ \"fx\", \"ticket\" ],\n"+
              "  \"order\": \""+orderuid+"\",\n"+
              "  \"ask\": 81.9,\n"+
              "  \"status\": \"waiting\"\n"+
              "}");
    }

    public void evaluate(){
try{
        LinkedList rules=contentList("%rules");
        if(rules==null) return;
        int r=0;
        for(Object o: rules){
            LinkedList ruleis=contentList(String.format("%%rules:%d:is", r));
            if(ruleis==null) return;
            boolean ok=true;
            for(Object is: ruleis){
                if("rule".equals(is)) continue;
                if(!contentIsOrListContains("is", is.toString())){ ok=false; break; }
            }
            if(ok) runRule(r);
            r++;
        }
}finally{
        if(contentListContains("is", "ticket")){
            mirrorOrder();
            setUpPseudoMarketMover();
            checkNotAsOrdered();
            acceptPayment();
        }
}
    }

    private void runRule(int r){
        if(alerted().size()==0) runRule(r,null);
        else for(String alerted: alerted()) runRule(r,alerted);
    }

    @SuppressWarnings("unchecked")
    private void runRule(int r, String alerted){
//log("Running rule \"When "+content(String.format("%%rules:%d:when", r))+"\"");

        LinkedHashMap<String,Object> rule=contentHash(String.format("%%rules:%d:#", r));

        contentTemp("%alerted", alerted);
        boolean ok=scanRuleHash(rule, "");
        // if(!ok) roll back
        if(ok) log("Rule fired: \"When "+content(String.format("%%rules:%d:when", r))+"\"");
//log("==========\nscanRuleHash="+ok+"\n"+rule+"\n"+contentHash("#")+"===========\n");
        contentTemp("%alerted", null);
    }

    static public final String  REWRITERE = "^<(.*)>(.*)$";
    static public final Pattern REWRITEPA = Pattern.compile(REWRITERE);
    static public final String  FUNCTIONRE = "(^[a-zA-Z][-a-zA-Z0-9]*)\\((.*)\\)$";
    static public final Pattern FUNCTIONPA = Pattern.compile(FUNCTIONRE);

    @SuppressWarnings("unchecked")
    private boolean scanRuleHash(LinkedHashMap<String,Object> hash, String path){
        if(contentHash(path+"#")==null) return false;
        for(Map.Entry<String,Object> entry: hash.entrySet()){
            String pk=path+entry.getKey();
            if(path.equals("")){
                if(pk.equals("is"  )) continue;
                if(pk.equals("when")) continue;
            }
            Object v=entry.getValue();
            if(v instanceof String){
                String vs=(String)v;
                if(vs.startsWith("<")){
                    if(vs.startsWith("<#>")){
                        if(contentSet(pk)) return false;
                        String rhs=vs.substring(3);
                        if(rhs.length()!=0){
                            if(rhs.equals("%alerted")) content(pk,content(rhs));
                            else
                            if(rhs.equals("$:"))       content(pk,uid);
                            else
                            if(rhs.startsWith("$:"))   contentClone(pk, rhs.substring(2));
                            else
                            if(rhs.equals("{}"))       contentHash(pk,new LinkedHashMap());
                            else
                            if(functionCall(pk,rhs));
                            else
                            if(rhs.equals("new") && pk.endsWith("%uid")){
                                String basepath=pk.substring(0,pk.length()-5);
                                content(basepath, spawn(new Fjord(contentHash(basepath))));
                            }
                            else content(pk,rhs);
                        }
                    }
                    else
                    if(vs.startsWith("<>")){
                        String[] rhsparts=vs.substring(2).split(";");
                        for(int i=0; i<rhsparts.length; i++){
                            String rhs=rhsparts[i];
                            if(pk.equals("%notifying")){
                                if(rhs.startsWith("has($:"))     notifying(content(rhs.substring(6,rhs.length()-1)));
                                if(rhs.startsWith("hasno($:")) unnotifying(content(rhs.substring(8,rhs.length()-1)));
                            }
                            else{
                                if(rhs.startsWith("has($:")) contentListAdd(pk, content(rhs.substring(6,rhs.length()-1)));
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

    @SuppressWarnings("unchecked")
    private boolean functionCall(String pk, String rhs){
        Matcher m = FUNCTIONPA.matcher(rhs);
        if(!m.matches()) return false;
        String   func = m.group(1);
        String[] args = m.group(2).split(",");

        if(func.equals("list")){
            LinkedList l=new LinkedList();
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                l.add(makeBestObject(arg));
            }
            contentList(pk,l);
        }
        else
        if(func.equals("prod")){
            double r = 1;
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                r *= contentDouble(arg.substring(2));
            }
            contentDouble(pk,r);
        }
        else content(pk,func+" on "+args);
        return true;
    }

    private Object makeBestObject(String s){
       try{ return Double.parseDouble(s); } catch(NumberFormatException e){}
       if(s.toLowerCase().equals("true" )) return new Boolean(true);
       if(s.toLowerCase().equals("false")) return new Boolean(false);
       return s;
    }

// two-phase
// "<#>payment": { .. }

    private void setUpPseudoMarketMover(){
        if(!contentSet("params")) setUpPseudoMarketMoverInterfaceCallback();
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


