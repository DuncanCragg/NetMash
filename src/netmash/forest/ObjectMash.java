package netmash.forest;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import static netmash.lib.Utils.*;

/** Object Mash Language.
  */
public class ObjectMash extends WebObject {

    private boolean extralogging = true;

    public ObjectMash(){}

    public ObjectMash(String s){ super(s); }

    public ObjectMash(LinkedHashMap hm){ super(hm); }

    public void evaluate(){
        if(extralogging) log("Running ObjectMash on "+contentHash("#"));
        LinkedList rules=contentList("%rules");
        if(extralogging) log("Rules: "+rules);
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
    }

    private void runRule(int r){
        if(extralogging) log("Rule "+r+" alerted: "+alerted);
        if(alerted().size()==0) runRule(r,null);
        else for(String alerted: alerted()) runRule(r,alerted);
    }

    @SuppressWarnings("unchecked")
    private void runRule(int r, String alerted){
        if(extralogging) log("Running rule \""+contentOr(String.format("%%rules:%d:when", r),"")+"\"");
        LinkedHashMap<String,Object> rule=contentHash(String.format("%%rules:%d:#", r));
        contentTemp("%alerted", alerted);
        LinkedHashMap<String,Object> rewrites=new LinkedHashMap<String,Object>();
        boolean ok=scanRuleHash(rule, "", rewrites);
        if(ok) doRewrites(rewrites);
        if(ok) log("Rule fired: \""+contentOr(String.format("%%rules:%d:when", r),"")+"\"");
        if(extralogging) log("==========\nscanRuleHash="+ok+"\n"+rule+"\n"+contentHash("#")+"\n===========");
        contentTemp("%alerted", null);
    }

    @SuppressWarnings("unchecked")
    private boolean scanRuleHash(LinkedHashMap<String,Object> hash, String path, LinkedHashMap<String,Object> rewrites){
        for(Map.Entry<String,Object> entry: hash.entrySet()){
            String pk=path+entry.getKey();
            if(path.equals("")){
                if(pk.equals("%rules")) continue;
                if(pk.equals("is")) continue;
                if(pk.equals("when")) continue;
                if(pk.equals("watching")) continue;
                if(pk.equals("editable")) continue;
                if(pk.equals("user")) continue;
            }
            Object v=entry.getValue();
            if(v instanceof String){
                if(!scanString((String)v, pk)) return false;
            }
            else
            if(v instanceof Number){
                if(!scanNumber((Number)v, pk)) return false;
            }
            else
            if(v instanceof Boolean){
                if(!scanBoolean((Boolean)v, pk)) return false;
            }
            else
            if(v instanceof LinkedHashMap){
                if(!scanRuleHash((LinkedHashMap<String,Object>)v, pk+":", rewrites)) return false;
            }
            else
            if(v instanceof LinkedList){
                if(!scanRuleList((LinkedList)v, pk, rewrites)) return false;
            }
            else{ log("oh noes "+v); return false; }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean scanRuleList(LinkedList list, String path, LinkedHashMap<String,Object> rewrites){
        if(list.size() == 2 && list.get(0).equals("<")){
            double v=findDouble(list.get(1));
            return contentDouble(path) < v;
        }
        if(list.size() == 2 && list.get(0).equals(">")){
            double v=findDouble(list.get(1));
            return contentDouble(path) > v;
        }
        if(list.size() >= 2 && list.get(0).equals("=>")){
            LinkedList rhs=new LinkedList(list.subList(1,list.size()));
            rewrites.put(path,rhs);
            return true;
        }
        int i=0;
        int j=0;
        for(Object v: list){
            String pk=String.format("%s:%d",path,j);
            if(!scanType(v,pk,rewrites)) return false;
            i++;
            j++;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean scanType(Object v, String pk, LinkedHashMap<String,Object> rewrites){
        if(v instanceof String)        return scanString((String)v, pk);
        if(v instanceof Number)        return scanNumber((Number)v, pk);
        if(v instanceof Boolean)       return scanBoolean((Boolean)v, pk);
        if(v instanceof LinkedHashMap) return scanRuleHash((LinkedHashMap<String,Object>)v, pk+":", rewrites);
        if(v instanceof LinkedList)    return scanRuleList((LinkedList)v, pk+":", rewrites);
        log("oh noes "+v);
        return false;
    }

    private boolean scanString(String vs, String pk){
        return vs.equals("*") || contentIsOrListContains(pk,vs);
    }

    private boolean scanNumber(Number vb, String pk){
        return contentDouble(pk)==vb.doubleValue();
    }

    private boolean scanBoolean(Boolean vb, String pk){
        return contentBool(pk)==vb;
    }

    private void doRewrites(LinkedHashMap<String,Object> rewrites){
        for(Map.Entry<String,Object> entry: rewrites.entrySet()){
            String path=entry.getKey();
            Object v=entry.getValue();
            if(v instanceof LinkedList){
                LinkedList ll=(LinkedList)v;
                if(ll.size()==1){
                    contentObject(path,ll.get(0));
                }
                else
                if(ll.size()==3 && ll.get(1).equals("+")){
                    contentDouble(path, findDouble(ll.get(0)) + findDouble(ll.get(2)));
                }
                else
                if(ll.size()==3 && ll.get(1).equals("×")){
                    contentDouble(path, findDouble(ll.get(0)) * findDouble(ll.get(2)));
                }
                else contentObject(path,ll);
            }
            else contentObject(path,v);
        }
    }

    private double findDouble(Object o){
        if(o==null) return 0;
        if(o instanceof String && ((String)o).startsWith("$:")) return contentDouble(((String)o).substring(2));
        return findNumberIn(o);
    }

    public static <T> Iterable<T> in(Iterable<T> l){ return l!=null? l: Collections.<T>emptyList(); }

    // ----------------------------------------------------
}


