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
        boolean ok=scanHash(rule, "");
        if(ok) doRewrites();
        if(ok) log("Rule fired: \""+contentOr(String.format("%%rules:%d:when", r),"")+"\"");
        if(extralogging) log("==========\nscanRuleHash="+ok+"\n"+rule+"\n"+contentHash("#")+"\n===========");
        contentTemp("%alerted", null);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private boolean scanHash(LinkedHashMap<String,Object> hash, String path){
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
    private boolean scanList(LinkedList list, String path){
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
        if(v instanceof LinkedHashMap) return scanHash((LinkedHashMap<String,Object>)v, pk+":");
        if(v instanceof LinkedList)    return scanList((LinkedList)v, pk+(hmmm? "": ":"));
        log("oh noes "+v);
        return false;
    }

    private boolean scanString(String vs, String pk){
        if(vs.equals("*")) return true;
        if(contentIsOrListContains(pk,vs)) return true;
        if(foundObjectSameOrNot(pk,vs)) return true;
        if(vs.equals("number") && contentObject(pk) instanceof Number) return true;
        return false;
    }

    private boolean scanNumber(Number vb, String pk){
        return contentDouble(pk)==vb.doubleValue();
    }

    private boolean scanBoolean(Boolean vb, String pk){
        return contentBool(pk)==vb;
    }

    private boolean foundObjectSameOrNot(String pk, String vs){
        boolean var=vs.startsWith("$:");
        boolean nov=vs.startsWith("!$:");
        if(!var && !nov) return false;
        Object pko=contentObject(pk);
        Object vso;
        if(var) vso=findObject(vs);
        else    vso=findObject(vs.substring(1));
        if(vso.equals(pko)) return var;
        if(pko instanceof Number && vso instanceof Number){
            return (((Number)pko).doubleValue()==((Number)vso).doubleValue())? var: nov;
        }
        return false;
    }

    // ----------------------------------------------------

    String currentRewritePath=null;

    private void doRewrites(){
        for(Map.Entry<String,Object> entry: rewrites.entrySet()){
            currentRewritePath=entry.getKey();
            LinkedList ll=(LinkedList)entry.getValue();
            if(ll.size()==2 && ll.get(0).equals("has")){
                Object e=findObject(ll.get(1));
                if(e==null) continue;
                if(currentRewritePath.equals("%notifying")) notifying(e.toString());
                else contentSetAdd(currentRewritePath, e);
            }
            else{
                Object e=eval(ll);
                if(e==null) continue;
                contentObject(currentRewritePath, e);
            }
        }
    }

    private Object eval(LinkedList ll){ try{
        if(ll.size()==1) return copyObject(ll.get(0));
        String ll0=findString(ll.get(0));
        String ll1=findString(ll.get(1));
        if(ll.size()==3 && ll1.equals("-"))       return Double.valueOf(findDouble(ll.get(0)) - findDouble(ll.get(2)));
        if(ll.size()==3 && ll1.equals("+"))       return Double.valueOf(findDouble(ll.get(0)) + findDouble(ll.get(2)));
        if(ll.size()==3 && ll1.equals("×"))       return Double.valueOf(findDouble(ll.get(0)) * findDouble(ll.get(2)));
        if(ll.size()==3 && ll1.equals("*"))       return Double.valueOf(findDouble(ll.get(0)) * findDouble(ll.get(2)));
        if(ll.size()==3 && ll1.equals("/"))       return Double.valueOf(findDouble(ll.get(0)) / findDouble(ll.get(2)));
        if(ll.size()==2 && ll0.equals("count"))   return Double.valueOf(findList(ll.get(1)).size());
        if(ll.size()==3 && ll0.equals("random"))  return Double.valueOf(random(findDouble(ll.get(1)), findDouble(ll.get(2))));
        if(ll.size()==4 && ll0.equals("clamp"))   return Double.valueOf(clamp(findDouble(ll.get(1)), findDouble(ll.get(2)), findDouble(ll.get(3))));
        if(ll.size()==3 && ll0.equals("format"))  return String.format(findObject(ll.get(1)).toString(), ll.get(2));
        if(ll.size()==4 && ll1.equals("chooses")) return findBoolean(ll.get(0))? findObject(ll.get(2)): findObject(ll.get(3));
        return ll;
    }catch(Throwable t){ return ll; } }

    private double random(double lo, double hi){
        double x=Math.random();
        return (int)(lo+x*(hi+0.5-lo));
    }

    private double clamp(double lo, double hi, double x){
        if(x>hi) return hi;
        if(x<lo) return lo;
        return x;
    }

    // ----------------------------------------------------

    private Object findObject(Object o){
        if(o==null) return null;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentObject(((String)o).substring(2));
        if(o instanceof LinkedList) return eval((LinkedList)o);
        return o;
    }

    private String findString(Object o){
        if(o==null) return null;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentString(((String)o).substring(2));
        if(o instanceof LinkedList) return eval((LinkedList)o).toString();
        return o.toString();
    }

    private double findDouble(Object o){
        if(o==null) return 0;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentDouble(((String)o).substring(2));
        if(o instanceof LinkedList){ Object e=eval((LinkedList)o); return (e instanceof Number)? ((Number)e).doubleValue(): 0; }
        return findNumberIn(o);
    }

    private boolean findBoolean(Object o){
        if(o==null) return false;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentBool(((String)o).substring(2));
        if(o instanceof Boolean) return (Boolean)o;
        if(o instanceof String)  return o.toString()=="true";
        return false;
    }

    private LinkedList findList(Object o){
        if(o==null) return null;
        if(o instanceof LinkedList) return (LinkedList)o;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentList(((String)o).substring(2));
        return null;
    }

    private Object eitherBindingOrContentObject(String path){
        if(path.startsWith(":")) return getBinding(path.substring(1));
        if(path.startsWith("!")) return contentObject(currentRewritePath);
        return contentObject(path);
    }

    private String eitherBindingOrContentString(String path){
        if(path.startsWith(":")) return getBinding(path.substring(1)).toString();
        if(path.startsWith("!")) return content(currentRewritePath);
        return contentString(path);
    }

    private double eitherBindingOrContentDouble(String path){
        if(path.startsWith("!")) return contentDouble(currentRewritePath);
        return contentDouble(path);
    }

    private boolean eitherBindingOrContentBool(String path){
        if(path.startsWith("!")) return contentBool(currentRewritePath);
        return contentBool(path);
    }

    private LinkedList eitherBindingOrContentList(String path){
        if(path.startsWith(":")) return bindings.get(path.substring(1));
        if(path.startsWith("!")) return contentList(currentRewritePath);
        return contentList(path);
    }

    private Object getBinding(String path){
        String[] bits=path.split(":");
        if(bits.length>2) return null;
        if(bits.length==1) return bindings.get(path);
        LinkedList ll=bindings.get(bits[0]);
        return ll.get(Integer.parseInt(bits[1]));
    }

    public static <T> Iterable<T> in(Iterable<T> l){ return l!=null? l: Collections.<T>emptyList(); }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    public Object copyObject(Object o){
        if(o instanceof String)  return findObject(o);
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return copyHash(((LinkedHashMap)o));
        if(o instanceof LinkedList)    return copyList(((LinkedList)o));
        return o;
    }

    @SuppressWarnings("unchecked")
    public Object copyHash(LinkedHashMap<String,Object> hm){
        boolean spawned=false;
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            if(k.equals("%uid")){ if(o.equals("new")){ spawned=true; }}
            else r.put(k,copyObject(o));
        }
        if(spawned) try{ return spawn(getClass().newInstance().construct(r)); } catch(Throwable t){ t.printStackTrace(); }
        return r;
    }

    @SuppressWarnings("unchecked")
    public LinkedList copyList(LinkedList ll){
        LinkedList r=new LinkedList();
        for(Object o: ll){
            r.add(copyObject(o));
        }
        return r;
    }

    // ----------------------------------------------------
}


