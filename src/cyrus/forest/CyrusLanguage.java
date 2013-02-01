package cyrus.forest;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import cyrus.platform.Kernel;
import cyrus.lib.JSON;

import static cyrus.lib.Utils.*;

/** Object Mash Language.
  */
public class CyrusLanguage extends WebObject {

    private boolean anylogging = false;
    private boolean extralogging = false;

    private void setLogging(){
        int logging=Kernel.config.intPathN("rules:log");
        anylogging   = logging>=1;
        extralogging = logging==2;
    }

    public CyrusLanguage(){ setLogging(); }

    public CyrusLanguage(String s){ super(s); setLogging(); }

    public CyrusLanguage(LinkedHashMap hm){ super(hm); setLogging(); }

    public CyrusLanguage(JSON json){ super(json); setLogging(); }

    static int MAX_LOOPS=50;

    @SuppressWarnings("unchecked")
    public void evaluate(){
        LinkedList rules=getEvalRules(); if(extralogging) log("Running CyrusLanguage on "+uid+": "+contentHash("#"));
        boolean modified=statemod;
        contentRemove("MaxLoopsReached");
        int maxloops=contentInt("MaxLoops"); if(maxloops<=0) maxloops=MAX_LOOPS;
        int i=0; for(; i<maxloops; i++){
            statemod=false;
            LinkedList rs=contentAsList("Rules");
            if(i==0){ if(rs!=null) rules.addAll(rs); } else rules=rs; if(extralogging) log("Rules: "+rules);
            if(rules==null) break;
            for(Object rule: rules){
                if(rule instanceof String) contentTempObserve("Rule", (String)rule);
                else
                if(rule instanceof LinkedHashMap) contentTemp("Rule", rule);
                else continue;
                LinkedList ruleis=contentList("Rule:is"); if(extralogging) log("Rule is="+ruleis);
                if(ruleis==null) continue;
                boolean ok=true;
                for(Object is: ruleis){
                    if("rule".equals(is)) continue;
                    if("editable".equals(is)) continue;
                    if(!contentIsOrListContains("is", is.toString())){ ok=false; if(extralogging) log("Rule doesn't apply: "+is+" "+contentString("is")); break; }
                }
                if(ok) runRule();
            }
            if(!statemod) break;
            modified=true;
        }
        if(i==maxloops){ contentBool("MaxLoopsReached", true); log("*** Maximum loops reached running rules: use self or mutual observation instead ***"); }
        statemod=modified;
        contentTemp("Rule",null);
    }

    @SuppressWarnings("unchecked")
    private LinkedList getEvalRules(){
        LinkedList evalrules=new LinkedList();
        if(!contentIsOrListContains("is", "editable")) return evalrules;
        for(String alerted: alerted()){
            contentTemp("Temp", alerted);
            if(contentListContainsAll("Temp:is",list("editable","rule"))) evalrules.add(alerted);
            contentTemp("Temp", null);
        }
        return evalrules;
    }

    private void runRule(){
        if(extralogging) log("Run rule. alerted="+alerted());
        if(alerted().size()==0) runRule(null);
        else for(String alerted: alerted()) runRule(alerted);
    }

    LinkedHashMap<String,LinkedList>         rewrites=new LinkedHashMap<String,LinkedList>();
    LinkedHashMap<String,LinkedList<String>> bindings=new LinkedHashMap<String,LinkedList<String>>();

    @SuppressWarnings("unchecked")
    private void runRule(String alerted){
        String when=contentStringOr("Rule:when","");
        if(alerted!=null && !contentSet("Alerted")) contentTemp("Alerted",alerted);
        ; if(extralogging) log("Running rule \""+when+"\"");
        ; if(extralogging) log("alerted:\n"+contentHash("Alerted:#"));
        rewrites.clear(); bindings.clear();
        LinkedHashMap<String,Object> rule=contentHash("Rule:#");
        boolean ok=scanHash(rule, "");
        if(ok) doRewrites();
        ; if(ok && anylogging) log("Rule fired: \""+when+"\"");
        ; if(extralogging) log("==========\nscanRuleHash="+(ok?"pass":"fail")+"\n"+rule+"\n"+contentHash("#")+"\n===========");
        if(alerted!=null && contentIs("Alerted",alerted)) contentTemp("Alerted",null);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private boolean scanHash(LinkedHashMap<String,Object> hash, String path){
        LinkedHashMap hm=contentHashMayJump(path);
        if(hm==null){ if(contentListMayJump(path)==null) return false; return scanList(list(hash), path, null); }
        if(hash.isEmpty()) return true;
        for(Map.Entry<String,Object> entry: hash.entrySet()){
            String pk=(path.equals("")? "": path+":")+entry.getKey();
            if(path.equals("")){
                if(pk.equals("Rules")) continue;
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
    private boolean scanList(LinkedList list, String path, LinkedList rhs){
        if(list.size()==0) return true;
        if(list.size()==2 && list.get(0).equals("<")){
            double d=findDouble(list.get(1));
            return (contentDouble(path) < d);
        }
        if(list.size()==2 && list.get(0).equals(">")){
            double d=findDouble(list.get(1));
            return (contentDouble(path) > d);
        }
        if(list.size()==2 && list.get(0).equals("divisible-by")){
            int i=(int)findDouble(list.get(1));
            int j=(int)contentDouble(path);
            return ((j % i)==0);
        }
        if(list.size()==2 && list.get(0).equals("list-count")){
            double d=findDouble(list.get(1));
            LinkedList ll=contentList(path);
            return (ll!=null && ll.size()==(int)d);
        }
        if(list.size()==2 && list.get(1).equals("**")){
            boolean ok=scanType(list.get(0),path+":0");
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if(list.size()==4 && list.get(0).equals("within")){
            return withinOf(findDouble(list.get(1)), contentList(path), findList(list.get(3)));
        }
        int becomes=list.indexOf("=>");
        if(becomes!= -1){
            LinkedList rh2=new LinkedList(list.subList(becomes+1,list.size()));
            if(becomes==0){ rewrites.put(path,rh2); return true; }
            LinkedList lhs=new LinkedList(list.subList(0,becomes));
            boolean ok=scanList(lhs,path,rh2);
            if(ok && becomes>1) rewrites.put(path,rh2);
            return ok;
        }
        becomes=list.indexOf("!=>");
        if(becomes!= -1){
            if(becomes==0) return false;
            LinkedList rh2=new LinkedList(list.subList(becomes+1,list.size()));
            LinkedList lhs=new LinkedList(list.subList(0,becomes));
            boolean ok=scanList(lhs,path,null);
            if(!ok) rewrites.put(path,rh2);
            return !ok;
        }
        LinkedList ll=contentListMayJump(path);
        boolean matchEach=list.size()!=1;
        if(ll==null){
            if(matchEach) return false;
            boolean ok=scanType(list.get(0),path);
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        LinkedList<String> bl=new LinkedList<String>();
        int i=0;
        for(Object v: list){
            boolean ok=false;
            for(; i<ll.size(); i++){
                String pk=String.format("%s:%d",path,i);
                if(scanTypeMayFail(v,pk)){ ok=true; bl.add(pk); if(matchEach) break; if(rhs!=null) rewrites.put(pk,rhs); }
            }
            if(!ok) return false;
            if(matchEach) i++;
        }
        bindings.put(path,bl);
        return true;
    }

    private boolean scanType(Object v, String pk){ return scanType(v,pk,false); }

    private boolean scanTypeMayFail(Object v, String pk){ return scanType(v,pk,true); }

    private boolean scanType(Object v, String pk, boolean mayfail){
        boolean r=doScanType(v,pk);
        if(!r && extralogging && !mayfail) log("Failed to match "+v+" at: "+pk+" "+contentObject(pk));
        return r;
    }

    @SuppressWarnings("unchecked")
    private boolean doScanType(Object v, String pk){
        if(v instanceof String)        return scanString((String)v, pk);
        if(v instanceof Number)        return scanNumber((Number)v, pk);
        if(v instanceof Boolean)       return scanBoolean((Boolean)v, pk);
        if(v instanceof LinkedHashMap) return scanHash((LinkedHashMap<String,Object>)v, pk);
        if(v instanceof LinkedList)    return scanList((LinkedList)v, pk, null);
        log("oh noes "+v+" "+pk);
        return false;
    }

    private boolean scanString(String v, String pk){
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        if(contentIs(pk,v)) return true;
        if(v.equals("*")) return  contentSet(pk);
        if(v.equals("#")) return !contentSet(pk);
        if(v.equals("@"))       return contentIsThis(pk);
        if(v.equals("number"))  return isNumber( contentObject(pk));
        if(v.equals("boolean")) return isBoolean(contentObject(pk));
        if(v.startsWith("/") && v.endsWith("/")) return regexMatch(v.substring(1,v.length()-1),pk);
        if(foundObjectSameOrNot(pk,v)) return true;
        return false;
    }

    private boolean scanNumber(Number v, String pk){
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        if(contentDouble(pk)==v.doubleValue()) return true;
        return false;
    }

    private boolean scanBoolean(Boolean v, String pk){
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        if(contentBool(pk)==v) return true;
        return false;
    }

    private boolean scanListFromSingleIfNotAlready(Object v, String pk){
        String[] parts=pk.split(":");
        if(isNumber(parts[parts.length-1])) return false;
        return scanList(list(v),pk,null);
    }

    private boolean regexMatch(String regex, String pk){
        String s=content(pk);
        if(s==null) return false;
        Pattern p=Pattern.compile(regex);
        Matcher m=p.matcher(s);
        return m.find();
    }

    private boolean foundObjectSameOrNot(String pk, String vs){
        boolean var=vs.startsWith("@");
        boolean nov=vs.startsWith("!@");
        if(!var && !nov) return false;
        Object pko=contentObject(pk);
        Object vso;
        if(var) vso=findObject(vs);
        else    vso=findObject(vs.substring(1));
        if(vso==null) return false;
        if(vso.equals(pko)) return var;
        if(pko instanceof Number && vso instanceof Number){
            return (((Number)pko).doubleValue()==((Number)vso).doubleValue())? var: nov;
        }
        return false;
    }

    // ----------------------------------------------------

    String currentRewritePath=null;

    @SuppressWarnings("unchecked")
    private void doRewrites(){
        LinkedHashMap<String,Boolean> shufflists=new LinkedHashMap<String,Boolean>();
        for(Map.Entry<String,LinkedList> entry: rewrites.entrySet()){
            currentRewritePath=entry.getKey();
            LinkedList ll=entry.getValue();
            if(ll.size()==0) continue;
            if(ll.size() >=3 && "@.".equals(ll.get(0)) && "with".equals(ll.get(1))){
                LinkedList e=copyFindEach(ll.subList(2,ll.size()));
                if(e==null || e.size()==0) continue;
                if(currentRewritePath.equals("Notifying")) for(Object o: e) notifying(o.toString());
                else contentSetAddAll(currentRewritePath, e);
            }
            else
            if(ll.size() >=3 && "@.".equals(ll.get(0)) && "without".equals(ll.get(1))){
                LinkedList e=findEach(ll.subList(2,ll.size()));
                if(e==null || e.size()==0) continue;
                if(currentRewritePath.equals("Notifying")) for(Object o: e) unnotifying(o.toString());
                else contentListRemoveAll(currentRewritePath, e);
            }
            else{
                Object e=(ll.size()==1)? copyFindObject(ll.get(0)): eval(ll);
                if(e==null){ log("failed to rewrite "+currentRewritePath); continue; }
                if("#".equals(e)){
                    String[] parts=currentRewritePath.split(":");
                    String lastpart=parts[parts.length-1];
                    if(parts.length==1 || !isNumber(lastpart)) contentRemove(currentRewritePath);
                    else { String p=currentRewritePath.substring(0,currentRewritePath.lastIndexOf(":"));
                           if(contentList(p)==null){ if("0".equals(lastpart)) contentRemove(p); else contentRemove(currentRewritePath); }
                           else{ content(currentRewritePath,"#"); shufflists.put(p,true); }
                    }
                }
                else
                if(currentRewritePath.equals("")){
                    if(!(e instanceof LinkedHashMap)){ log("failed to rewrite entire item: "+e); continue; }
                    contentReplace(new JSON((LinkedHashMap)e));
                }
                else contentObject(currentRewritePath, e);
            }
        }
        for(String p: shufflists.keySet()){
            LinkedList ll=contentList(p);
            if(ll==null) continue;
            LinkedList lr=new LinkedList();
            for(Object o: ll) if(!"#".equals(o)) lr.add(o);
            contentList(p,lr);
        }
    }

    private Object eval(LinkedList ll){ try{
        if(ll.size()==0) return null;
   //   if(ll.size()==1) return copyFindObject(ll.get(0));
        if(ll.size()==1) return ll;
        String ll0=findString(ll.get(0));
        String ll1=findString(ll.get(1));
        if(ll.size()==2 && "count".equals(ll0))   return Double.valueOf(sizeOf(findList(ll.get(1))));
        if(ll.size()==3 && "random".equals(ll0))  return Double.valueOf(random(findDouble(ll.get(1)), findDouble(ll.get(2))));
        if(ll.size()==4 && "clamp".equals(ll0))   return Double.valueOf(clamp(findDouble(ll.get(1)), findDouble(ll.get(2)), findDouble(ll.get(3))));
        if(ll.size()==2 && "integer".equals(ll0)) return Integer.valueOf((int)(0.5+findDouble(ll.get(1))));
        if(ll.size()==3 && "format".equals(ll0))  return String.format(findString(ll.get(1)), findString(ll.get(2)));
        if(ll.size()==4 && "format".equals(ll0))  return String.format(findString(ll.get(1)), findString(ll.get(2)), findString(ll.get(3)));
        if(ll.size()==5 && "format".equals(ll0))  return String.format(findString(ll.get(1)), findString(ll.get(2)), findString(ll.get(3)), findString(ll.get(4)));
        if(ll.size()==6 && "if".equals(ll0))      return findBoolean(ll.get(1))? copyFindObject(ll.get(3)): copyFindObject(ll.get(5));
        if(ll.size()==2 && "as-is".equals(ll0))   return copyObject(ll.get(1), true);
        if(ll.size()==3 && "join".equals(ll0))    return join(findList(ll.get(2)), findString(ll.get(1)));
        if(ll.size()==2 && "+".equals(ll0))       return Double.valueOf(sumAll(findList(ll.get(1))));
        if(ll.size()==3 && "-".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) - findDouble(ll.get(2)));
        if(ll.size()==3 && "+".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) + findDouble(ll.get(2)));
        if(ll.size()==3 && "×".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) * findDouble(ll.get(2)));
        if(ll.size()==3 && "*".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) * findDouble(ll.get(2)));
        if(ll.size()==3 && "÷".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) / findDouble(ll.get(2)));
        if(ll.size()==3 && "/".equals(ll1))       return Double.valueOf(findDouble(ll.get(0)) / findDouble(ll.get(2)));
        if(ll.size()==3 && "v.m".equals(ll1))     return vmdot(findList(ll.get(0)), findList(ll.get(2)));
        if(ll.size()==3 && "v+v".equals(ll1))     return vvadd(findList(ll.get(0)), findList(ll.get(2)));
        if(ll.size()==3 && "v~v".equals(ll1))     return vvdist(findList(ll.get(0)), findList(ll.get(2)));
        if(ll.size()==3 && "v/s".equals(ll1))     return vsdiv(findList(ll.get(0)), findDouble(ll.get(2)));
        if(ll.size()==3 && "<".equals(ll1))       return Boolean.valueOf(findDouble(ll.get(0)) < findDouble(ll.get(2)));
        if(ll.size()==3 && ">".equals(ll1))       return Boolean.valueOf(findDouble(ll.get(0)) > findDouble(ll.get(2)));
        if(ll.size()==3 && "select".equals(ll1))  return copyFindObject(findHashOrListAndGet(ll.get(0),ll.get(2)));
        return copyFindEach(ll);
    }catch(Throwable t){ t.printStackTrace(); log("something failed here: "+ll); return ll; } }

    @SuppressWarnings("unchecked")
    private LinkedList copyFindEach(List ll){
        LinkedList r=new LinkedList();
        for(Object o: ll) r.add(copyFindObject(o));
        return r;
    }

    @SuppressWarnings("unchecked")
    private LinkedList findEach(List ll){
        LinkedList r=new LinkedList();
        for(Object o: ll) r.add(findObject(o));
        return r;
    }

    // ----------------------------------------------------

    private Object findObject(Object o){
        if(o==null) return null;
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentObject(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return o;
    }

    private String findString(Object o){
        if(o==null) return "";
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentString(((String)o).substring(1));
        if(o instanceof LinkedList){ o=eval((LinkedList)o); if(o==null) return null; }
        if(o instanceof Number) return toNicerString((Number)o);
        return o.toString();
    }

    private double findDouble(Object o){
        if(o==null) return 0;
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentDouble(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findNumberIn(o);
    }

    private boolean findBoolean(Object o){
        if(o==null) return false;
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentBool(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findBooleanIn(o);
    }

    private LinkedHashMap findHash(Object o){
        if(o==null) return new LinkedHashMap();
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentHash(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findHashIn(o);
    }

    private LinkedList findList(Object o){
        if(o==null) return new LinkedList();
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentList(((String)o).substring(1));
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
        if(path.startsWith("."))  return contentObject(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return getBinding(path.substring(1));
        Object o=contentObject(path);
        if(o!=null) return o;
        return contentAll(path);
    }

    private String eitherBindingOrContentString(String path){
        if(path.startsWith("."))  return content(                currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findStringIn(getBinding(path.substring(1)));
        return contentString(path);
    }

    private double eitherBindingOrContentDouble(String path){
        if(path.startsWith("."))  return contentDouble(          currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findNumberIn(getBinding(path.substring(1)));
        return contentDouble(path);
    }

    private boolean eitherBindingOrContentBool(String path){
        if(path.startsWith("."))  return contentBool(             currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findBooleanIn(getBinding(path.substring(1)));
        return contentBool(path);
    }

    private LinkedList eitherBindingOrContentList(String path){
        if(path.startsWith("."))  return contentList(          currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findListIn(getBinding(path.substring(1)));
        LinkedList ll=contentList(path);
        if(ll!=null) return ll;
        return contentAll(path);
    }

    private LinkedHashMap eitherBindingOrContentHash(String path){
        if(path.startsWith("."))  return contentHash(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findHashIn(getBinding(path.substring(1)));
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
            if(i>=0 && i<ll.size()) return contentObject(ll.get(i));

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
        if(o instanceof String)  return ((String)o).equals("uid-new")? spawn(new CyrusLanguage("{ \"is\": [ \"editable\" ] }")): o;
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
            if(k.equals("UID") && !asis){ if(o.equals("new")){ spawned=true; }}
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


