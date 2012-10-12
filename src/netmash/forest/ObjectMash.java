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

    LinkedHashMap<String,Object>     rewrites=new LinkedHashMap<String,Object>();
    LinkedHashMap<String,LinkedList> bindings=new LinkedHashMap<String,LinkedList>();

    @SuppressWarnings("unchecked")
    private void runRule(int r, String alerted){
        if(extralogging) log("Running rule \""+contentOr(String.format("%%rules:%d:when", r),"")+"\"");
        LinkedHashMap<String,Object> rule=contentHash(String.format("%%rules:%d:#", r));
        contentTemp("%alerted", alerted);
        rewrites.clear(); bindings.clear();
        boolean ok=scanRuleHash(rule, "");
        if(ok) doRewrites();
        if(ok) log("Rule fired: \""+contentOr(String.format("%%rules:%d:when", r),"")+"\"");
        if(extralogging) log("==========\nscanRuleHash="+ok+"\n"+rule+"\n"+contentHash("#")+"\n===========");
        contentTemp("%alerted", null);
    }

    @SuppressWarnings("unchecked")
    private boolean scanRuleHash(LinkedHashMap<String,Object> hash, String path){
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
            if(!scanType(v,pk,true)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean scanRuleList(LinkedList list, String path){
        if(list.size()==0) return true;
        if(list.size()==2 && list.get(0).equals("<")){
            double d=findDouble(list.get(1));
            return contentDouble(path) < d;
        }
        if(list.size()==2 && list.get(0).equals(">")){
            double d=findDouble(list.get(1));
            return contentDouble(path) > d;
        }
        if(list.size()==2 && list.get(0).equals("list-count")){
            double d=findDouble(list.get(1));
            LinkedList ll=contentList(path);
            return ll!=null && ll.size()==(int)d;
        }
        if(list.size() >= 2 && (list.get(0).equals("=>") || list.get(1).equals("=>"))){
            if(list.get(1).equals("=>")){
                LinkedList rhs=new LinkedList(list.subList(2,list.size()));
                boolean ok=scanType(list.get(0),path,false);
                if(ok) rewrites.put(path,rhs);
                return ok;
            }
            else{
                LinkedList rhs=new LinkedList(list.subList(1,list.size()));
                rewrites.put(path,rhs);
                return true;
            }
        }
        LinkedList bl=new LinkedList();
        LinkedList ll=contentList(path);
        if(ll==null) return false;
        int i=0;
        boolean matchEach=list.size()!=1;
        for(Object v: list){
            for(; i<ll.size(); i++){
                String pk=String.format("%s:%d",path,i);
                if(scanType(v,pk,false)){ bl.add(contentObject(pk)); if(matchEach) break; }
            }
            if(matchEach){
                if(i==ll.size()) return false;
                i++;
            }
            else if(bl.size()==0) return false;
        }
        bindings.put(path,bl);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean scanType(Object v, String pk, boolean hmmm){
        if(v instanceof String)        return scanString((String)v, pk);
        if(v instanceof Number)        return scanNumber((Number)v, pk);
        if(v instanceof Boolean)       return scanBoolean((Boolean)v, pk);
        if(v instanceof LinkedHashMap) return scanRuleHash((LinkedHashMap<String,Object>)v, pk+":");
        if(v instanceof LinkedList)    return scanRuleList((LinkedList)v, pk+(hmmm? "": ":"));
        log("oh noes "+v);
        return false;
    }

    private boolean scanString(String vs, String pk){
        return vs.equals("*") || contentIsOrListContains(pk,vs) || foundObjectSame(pk,vs);
    }

    private boolean foundObjectSame(String pk, String vs){
        Object pko=contentObject(pk);
        Object vso=findObject(vs);
        if(vso.equals(pko)) return true;
        if(pko instanceof Number && vso instanceof Number){
            return ((Number)pko).doubleValue()==((Number)vso).doubleValue();
        }
        return false;
    }

    private boolean scanNumber(Number vb, String pk){
        return contentDouble(pk)==vb.doubleValue();
    }

    private boolean scanBoolean(Boolean vb, String pk){
        return contentBool(pk)==vb;
    }

    private void doRewrites(){
        for(Map.Entry<String,Object> entry: rewrites.entrySet()){
            String path=entry.getKey();
            Object v=entry.getValue();
            if(v instanceof LinkedList){
                LinkedList ll=(LinkedList)v;
                if(ll.size()==1){
                    contentObject(path,findObject(ll.get(0)));
                }
                else
                if(ll.size()==3 && ll.get(1).equals("+")){
                    contentDouble(path, findDouble(ll.get(0)) + findDouble(ll.get(2)));
                }
                else
                if(ll.size()==3 && ll.get(1).equals("×")){
                    contentDouble(path, findDouble(ll.get(0)) * findDouble(ll.get(2)));
                }
                else
                if(ll.size()==2 && ll.get(0).equals("count")){
                    contentDouble(path, findList(ll.get(1)).size());
                }
                else contentObject(path,ll);
            }
            else contentObject(path,v);
        }
    }

    private Object findObject(Object o){
        if(o==null) return null;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentObject(((String)o).substring(2));
        return o;
    }

    private double findDouble(Object o){
        if(o==null) return 0;
        if(o instanceof String && ((String)o).startsWith("$:")) return contentDouble(((String)o).substring(2));
        return findNumberIn(o);
    }

    private LinkedList findList(Object o){
        if(o==null) return null;
        if(o instanceof LinkedList) return (LinkedList)o;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentList(((String)o).substring(2));
        return null;
    }

    private Object eitherBindingOrContentObject(String path){
        if(path.startsWith(":")) return bindings.get(path.substring(1));
        else return contentObject(path);
    }

    private LinkedList eitherBindingOrContentList(String path){
        if(path.startsWith(":")) return bindings.get(path.substring(1));
        else return contentList(path);
    }

    public static <T> Iterable<T> in(Iterable<T> l){ return l!=null? l: Collections.<T>emptyList(); }

    // ----------------------------------------------------
}


