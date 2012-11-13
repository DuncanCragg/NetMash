package netmash.forest;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import netmash.platform.Kernel;
import netmash.lib.JSON;

import static netmash.lib.Utils.*;

/** Object Mash Language.
  */
public class ObjectMash extends WebObject {

    private boolean extralogging = false;

    public ObjectMash(){ extralogging = Kernel.config.boolPathN("rules:log"); }

    public ObjectMash(String s){ super(s); extralogging = Kernel.config.boolPathN("rules:log"); }

    public ObjectMash(LinkedHashMap hm){ super(hm); extralogging = Kernel.config.boolPathN("rules:log"); }

    public ObjectMash(JSON json){ super(json); extralogging = Kernel.config.boolPathN("rules:log"); }

    public void evaluate(){
        if(extralogging) log("Running ObjectMash on "+uid+": "+contentHash("#"));
        LinkedList rules=contentList("%rules");
        if(extralogging) log("Rules: "+rules);
        if(rules==null) return;
        int r=0;
        for(Object o: rules){
            LinkedList ruleis=contentList(String.format("%%rules:%d:is", r));
            if(extralogging) log("Rule "+r+" is="+ruleis);
            if(ruleis==null) return;
            boolean ok=true;
            for(Object is: ruleis){
                if("rule".equals(is)) continue;
                if("editable".equals(is)) continue;
                if(!contentIsOrListContains("is", is.toString())){ ok=false; if(extralogging) log("Rule doesn't apply: "+is+" "+contentString("is")); break; }
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

    LinkedHashMap<String,Object>             rewrites=new LinkedHashMap<String,Object>();
    LinkedHashMap<String,LinkedList<String>> bindings=new LinkedHashMap<String,LinkedList<String>>();

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
        if(hash.isEmpty()) return contentHash(path)!=null;
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
            if(!scanType(v,pk)) return false;
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
        if(list.size()==2 && list.get(0).equals("divisible-by")){
            int i=(int)findDouble(list.get(1));
            int j=(int)contentDouble(path);
            return (j % i)==0;
        }
        if(list.size()==2 && list.get(0).equals("list-count")){
            double d=findDouble(list.get(1));
            LinkedList ll=contentList(path);
            return ll!=null && ll.size()==(int)d;
        }
        if(list.size() >= 2 && (list.get(0).equals("=>") || list.get(1).equals("=>"))){
            if(list.get(1).equals("=>")){
                LinkedList rhs=new LinkedList(list.subList(2,list.size()));
                boolean ok=scanType(list.get(0),path);
                if(ok) rewrites.put(path,rhs);
                return ok;
            }
            else{
                LinkedList rhs=new LinkedList(list.subList(1,list.size()));
                rewrites.put(path,rhs);
                return true;
            }
        }
        if(list.size() >= 3 && list.get(1).equals("!=>")){
            LinkedList rhs=new LinkedList(list.subList(2,list.size()));
            boolean ok=scanType(list.get(0),path);
            if(!ok) rewrites.put(path,rhs);
            return !ok;
        }
        LinkedList<String> bl=new LinkedList<String>();
        LinkedList ll=contentList(path);
        if(ll==null) return false;
        int i=0;
        boolean matchEach=list.size()!=1;
        for(Object v: list){
            for(; i<ll.size(); i++){
                String pk=String.format("%s:%d",path,i);
                if(scanType(v,pk)){ bl.add(pk); if(matchEach) break; }
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

    private boolean scanType(Object v, String pk){
        boolean r=doScanType(v,pk);
        if(!r && extralogging) log("Failed to match "+v+" at: "+pk);
        return r;
    }

    @SuppressWarnings("unchecked")
    private boolean doScanType(Object v, String pk){
        if(v instanceof String)        return scanString((String)v, pk);
        if(v instanceof Number)        return scanNumber((Number)v, pk);
        if(v instanceof Boolean)       return scanBoolean((Boolean)v, pk);
        if(v instanceof LinkedHashMap) return scanHash((LinkedHashMap<String,Object>)v, pk+":");
        if(v instanceof LinkedList)    return scanList((LinkedList)v, pk);
        log("oh noes "+v);
        return false;
    }

    private boolean scanString(String vs, String pk){
        if(vs.equals("*")) return  contentSet(pk);
        if(vs.equals("#")) return !contentSet(pk);
        if(vs.startsWith("/") && vs.endsWith("/")) return regexMatch(vs.substring(1,vs.length()-1),pk);
        if(contentIsOrListContains(pk,vs)) return true;
        if(foundObjectSameOrNot(pk,vs)) return true;
        if(vs.equals("number") && isNumber(contentObject(pk))) return true;
        return false;
    }

    private boolean scanNumber(Number vb, String pk){
        return contentDouble(pk)==vb.doubleValue() || contentListContains(pk, vb);
    }

    private boolean scanBoolean(Boolean vb, String pk){
        return contentBool(pk)==vb;
    }

    private boolean regexMatch(String regex, String pk){
        String s=content(pk);
        if(s==null) return false;
        Pattern p=Pattern.compile(regex);
        Matcher m=p.matcher(s);
        return m.find();
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
                Object e=copyFindObject(ll.get(1));
                if(e==null) continue;
                if(currentRewritePath.equals("%notifying")) notifying(e.toString());
                else contentSetAdd(currentRewritePath, e);
            }
            else
            if(ll.size()==2 && ll.get(0).equals("has-no")){
                Object e=findObject(ll.get(1));
                if(e==null) continue;
                if(currentRewritePath.equals("%notifying")) unnotifying(e.toString());
                else contentListRemove(currentRewritePath, e);
            }
            else{
                Object e=(ll.size()==1)? copyFindObject(ll.get(0)): eval(ll);
                if(e==null){ log("failed to rewrite "+currentRewritePath); continue; }
                if(currentRewritePath.equals("")){
                    if(!(e instanceof LinkedHashMap)){ log("failed to rewrite entire object: "+e); continue; }
                    contentReplace(new JSON((LinkedHashMap)e));
                }
                else contentObject(currentRewritePath, e);
            }
        }
    }

    private Object eval(LinkedList ll){ try{
        if(ll.size()==0) return null;
   //   if(ll.size()==1) return copyFindObject(ll.get(0));
        if(ll.size()==1) return ll;
        String ll0=findString(ll.get(0));
        String ll1=findString(ll.get(1));
        if(ll.size()==3 && "-".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) - findDouble(ll.get(2)));
        if(ll.size()==3 && "+".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) + findDouble(ll.get(2)));
        if(ll.size()==2 && "+".equals(ll0))       return Double.valueOf(sumAll(findList(ll.get(1))));
        if(ll.size()==3 && "×".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) * findDouble(ll.get(2)));
        if(ll.size()==3 && "*".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) * findDouble(ll.get(2)));
        if(ll.size()==3 && "÷".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) / findDouble(ll.get(2)));
        if(ll.size()==3 && "/".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) / findDouble(ll.get(2)));
        if(ll.size()==2 && "count".equals(ll0))   return Double.valueOf(findList(ll.get(1)).size());
        if(ll.size()==3 && "random".equals(ll0))  return Double.valueOf(random(findDouble(ll.get(1)), findDouble(ll.get(2))));
        if(ll.size()==4 && "clamp".equals(ll0))   return Double.valueOf(clamp(findDouble(ll.get(1)), findDouble(ll.get(2)), findDouble(ll.get(3))));
        if(ll.size()==2 && "integer".equals(ll0)) return Integer.valueOf((int)(0.5+findDouble(ll.get(1))));
        if(ll.size()==3 && "format".equals(ll0))  return String.format(findString(ll.get(1)), findString(ll.get(2)));
        if(ll.size()==4 && "format".equals(ll0))  return String.format(findString(ll.get(1)), findString(ll.get(2)), findString(ll.get(3)));
        if(ll.size()==5 && "format".equals(ll0))  return String.format(findString(ll.get(1)), findString(ll.get(2)), findString(ll.get(3)), findString(ll.get(4)));
        if(ll.size()==4 && "selects".equals(ll1)) return findBoolean(ll.get(0))? copyFindObject(ll.get(2)): copyFindObject(ll.get(3));
        if(ll.size()==3 && "selects".equals(ll1)) return copyFindObject(findHashOrListAndGet(ll.get(2),ll.get(0)));
        if(ll.size()==2 && "as-is".equals(ll0))   return copyObject(ll.get(1), true);
        return copyFindEach(ll);
    }catch(Throwable t){ t.printStackTrace(); log(ll); return ll; } }

    @SuppressWarnings("unchecked")
    private Object copyFindEach(LinkedList ll){
        LinkedList r=new LinkedList();
        for(Object o: ll) r.add(copyFindObject(o));
        return r;
    }

    // ----------------------------------------------------

    private Object findObject(Object o){
        if(o==null) return null;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentObject(((String)o).substring(2));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return o;
    }

    private String findString(Object o){
        if(o==null) return "";
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentString(((String)o).substring(2));
        if(o instanceof LinkedList){ o=eval((LinkedList)o); if(o==null) return null; }
        if(o instanceof Number) return toNicerString((Number)o);
        return o.toString();
    }

    private double findDouble(Object o){
        if(o==null) return 0;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentDouble(((String)o).substring(2));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findNumberIn(o);
    }

    private boolean findBoolean(Object o){
        if(o==null) return false;
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentBool(((String)o).substring(2));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findBooleanIn(o);
    }

    private LinkedHashMap findHash(Object o){
        if(o==null) return new LinkedHashMap();
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentHash(((String)o).substring(2));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findHashIn(o);
    }

    private LinkedList findList(Object o){
        if(o==null) return new LinkedList();
        if(o instanceof String && ((String)o).startsWith("$:")) return eitherBindingOrContentList(((String)o).substring(2));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findListIn(o);
    }

    private Object findHashOrListAndGet(Object collection, Object index){
        if(collection==null || index==null) return null;
        LinkedHashMap hm=findHash(collection);
        if(hm!=null && hm.size() >0) return hm.get(findObject(index));
        LinkedList    ll=findList(collection);
        int i=(int)findDouble(index);
        if(ll!=null && ll.size() >i) return ll.get(i);
        return null;
    }

    // ----------------------------------------------------

    private Object eitherBindingOrContentObject(String path){
        if(path.startsWith(":")) return getBinding(path.substring(1));
        if(path.startsWith("!")) return contentObject(currentRewritePath);
        Object o=contentObject(path);
        if(o!=null) return o;
        return contentAll(path);
    }

    private String eitherBindingOrContentString(String path){
        if(path.startsWith(":")) return findStringIn(getBinding(path.substring(1)));
        if(path.startsWith("!")) return content(currentRewritePath);
        return contentString(path);
    }

    private double eitherBindingOrContentDouble(String path){
        if(path.startsWith(":")) return findNumberIn(getBinding(path.substring(1)));
        if(path.startsWith("!")) return contentDouble(currentRewritePath);
        return contentDouble(path);
    }

    private boolean eitherBindingOrContentBool(String path){
        if(path.startsWith(":")) return findBooleanIn(getBinding(path.substring(1)));
        if(path.startsWith("!")) return contentBool(currentRewritePath);
        return contentBool(path);
    }

    private LinkedList eitherBindingOrContentList(String path){
        if(path.startsWith(":")) return findListIn(getBinding(path.substring(1)));
        if(path.startsWith("!")) return contentList(currentRewritePath);
        LinkedList ll=contentList(path);
        if(ll!=null) return ll;
        return contentAll(path);
    }

    private LinkedHashMap eitherBindingOrContentHash(String path){
        if(path.startsWith(":")) return findHashIn(getBinding(path.substring(1)));
        if(path.startsWith("!")) return contentHash(currentRewritePath);
        return contentHashMayJump(path);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object getBinding(String path){
        String pk=path;
        LinkedList<String> ll=bindings.get(pk);
        if(ll!=null) return objectsAt(ll,null);
        do{
            int e=pk.lastIndexOf(":");
            if(e== -1) return null;
            String p=pk.substring(e+1);
            pk=pk.substring(0,e);
            ll=bindings.get(pk);
            if(ll==null) continue;

            int i= -1;
            try{ i=Integer.parseInt(p); }catch(Throwable t){}
            if(i>=0) return contentObject(ll.get(i));

            return objectsAt(ll,p);

        }while(true);
    }

    @SuppressWarnings("unchecked")
    private LinkedList objectsAt(LinkedList<String> ll, String p){
        LinkedList r=new LinkedList();
        for(String s: ll){
            Object o=contentObject(s+(p==null? "": ":"+p));
            if(o!=null) r.add(o);
        }
        return r.isEmpty()? null: r;
    }

    // ----------------------------------------------------

    private Object copyFindObject(Object o){
        return copyObject(findObject(o), false);
    }

    @SuppressWarnings("unchecked")
    public Object copyObject(Object o, boolean asis){
        if(o==null) return null;
        if(o instanceof String)  return o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return copyHash(((LinkedHashMap)o), asis);
        if(o instanceof LinkedList)    return copyList(((LinkedList)o), asis);
        return o;
    }

    @SuppressWarnings("unchecked")
    public Object copyHash(LinkedHashMap<String,Object> hm, boolean asis){
        boolean spawned=false;
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            if(k.equals("%uid") && !asis){ if(o.equals("new")){ spawned=true; }}
            else r.put(k, asis? copyObject(o,true): copyFindObject(o));
        }
        if(spawned) try{ return spawn(getClass().newInstance().construct(r)); } catch(Throwable t){ t.printStackTrace(); }
        return r;
    }

    @SuppressWarnings("unchecked")
    public LinkedList copyList(LinkedList ll, boolean asis){
        LinkedList r=new LinkedList();
        for(Object o: ll) r.add(asis? copyObject(o,true): copyFindObject(o));
        return r;
    }

    // ----------------------------------------------------
}


