package cyrus.forest;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import cyrus.platform.Kernel;
import cyrus.lib.JSON;

import static cyrus.lib.Utils.*;

/** Cyrus Language.
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

    public CyrusLanguage(String s, boolean cyrus){ super(s,cyrus); setLogging(); }

    public CyrusLanguage(JSON json){ super(json); setLogging(); }

    static int MAX_LOOPS=50;

    LinkedList<String> rulesRun=new LinkedList<String>();

    @SuppressWarnings("unchecked")
    public void evaluate(){ try{
        Object am=contentObject("is"); if(am==null) am=contentHash("#");
        if(extralogging) System.out.println("==================================================\nRunning CyrusLanguage on "
                                            +uid+" "+am+(extralogging? ":\n"+contentHash("#"): ""));
        boolean modified=statemod;
        LinkedList rules=getEvalRules();
        contentRemove("MaxLoopsReached");
        int maxloops=contentInt("MaxLoops"); if(maxloops<=0) maxloops=MAX_LOOPS;
        int i=0; for(; i<maxloops; i++){
            statemod=false;
            if(!(i==0 && rules.size() >0)) rules=getGlobalAndLocalRules();   if(extralogging) log("Rules: "+rules);
            if(rules==null || rules.size()==0) break;
            rulesRun.clear();
            runTheRules(rules);
            if(!statemod) break;
            modified=true;
        }
        if(i==maxloops){ contentBool("MaxLoopsReached", true); log("********* Maximum loops reached running rules: use self or mutual observation instead ***"); }
        statemod=modified;
        contentTemp("Rule",null);
    }catch(Throwable t){ log("exception in evaluate()",this); t.printStackTrace(); }}

    private void runTheRules(LinkedList rules){
        for(Object rule: rules){
            if(rule instanceof String){
                contentTempObserve("Rule", (String)rule);
                LinkedList rulelist=contentListMayJump("Rule");
                if(rulelist!=null && !contentIsOrListContains("Rule:is","rule")) runTheRules(rulelist);
                else if(!rulesRun.contains(rule)){ rulesRun.add((String)rule); runTheRule(); }
            }
            else
            if(rule instanceof LinkedList) runTheRules((LinkedList)rule);
            else
            if(rule instanceof LinkedHashMap){
                contentTemp("Rule", rule);
                runTheRule();
            }
        }
    }

    private void runTheRule(){
        LinkedList ruleis=contentList("Rule:is"); if(extralogging) log("Rule is="+ruleis);
        if(ruleis==null) return;
        for(Object is: ruleis){
            if("rule".equals(is)) continue;
            if("editable".equals(is)) continue;
            if(!contentIsOrListContains("is", is.toString())){ if(extralogging) log("Rule doesn't apply: "+is+" "+contentString("is")); return; }
        }
        runRule();
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

    static private LinkedList<String> globalRules=new LinkedList<String>();

    static public void addGlobalRules(String gruid){ if(!globalRules.contains(gruid)) globalRules.add(gruid); }

    private LinkedList getGlobalAndLocalRules(){
        if(contentIsOrListContains("is","rule")) return null;
        LinkedList globlocrules=new LinkedList();
        maybeAddAll(globlocrules, Kernel.config.listPathN("rules:global-rules"));
        maybeAddAll(globlocrules, globalRules);
        maybeAddAll(globlocrules, contentAsList("Rules"));
        return globlocrules;
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
        String when=contentStringOr("Rule:when","(no description)");
        if(alerted!=null && !contentSet("Alerted")) contentTemp("Alerted",alerted);
        ; if(extralogging) System.out.println("-------------------\nRunning rule: \"When "+when+"\"");
        ; if(extralogging) log("alerted:\n"+contentHash("Alerted:#"));
        rewrites.clear(); bindings.clear();
        LinkedHashMap<String,Object> rule=contentHash("Rule:#");
        boolean ok=scanHash(rule, "");
        if(ok) doRewrites();
        ; if(ok && statemod && anylogging) System.out.println("Rule fired: \"When "+when+"\" on "+uid);
        ; if(extralogging) log("==========\nscanRuleHash="+(ok?"pass":"fail")+(statemod?"/":"/un")+"changed\n"+rule+"\n====\n"+contentHash("#")+"\n===========");
        if(alerted!=null && contentIs("Alerted",alerted)) contentTemp("Alerted",null);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private boolean scanHash(LinkedHashMap<String,Object> hash, String path){
        if(hash.get("**")!=null){ if(!scanDeep(hash.get("**"),path)) return false; if(hash.size()==1) return true; }
        if(contentHashMayJump(path)==null) return contentListMayJump(path)!=null && scanList(list(hash), path, null);
        if(hash.isEmpty()) return true;
        for(Map.Entry<String,Object> entry: hash.entrySet()){
            String pk=(path.equals("")? "": path+":")+entry.getKey();
            if(pk.endsWith("**")|| ignoreTopLevelNoise(path,pk)) continue;
            if(!scanTypeMayMayFail(entry.getValue(),pk,pk.equals("Alerted"))) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean scanList(LinkedList list, String path, LinkedList rhs){
        int listsize=list.size();
        if(listsize==0) return true;
        if(listsize==2 && list.get(0).equals("maybe")){
            scanList(new LinkedList(list.subList(1,listsize)),path,rhs);
            return true;
        }
        int becomes=list.indexOf("=>");
        if(becomes!= -1){
            LinkedList rh2=new LinkedList(list.subList(becomes+1,listsize));
            if(becomes==0){ rewrites.put(path,rh2); return true; }
            LinkedList lhs=new LinkedList(list.subList(0,becomes));
            boolean ok=scanList(lhs,path,rh2);
            if(ok && becomes>1) rewrites.put(path,rh2);
            return ok;
        }
        becomes=list.indexOf("!=>");
        if(becomes!= -1){
            if(becomes==0) return false;
            LinkedList rh2=new LinkedList(list.subList(becomes+1,listsize));
            LinkedList lhs=new LinkedList(list.subList(0,becomes));
            boolean ok=scanListTryFail(lhs,path);
            if(!ok) rewrites.put(path,rh2);
            return !ok;
        }
        if(listsize >=1 && list.get(0).equals("not")){
            LinkedList lhs=new LinkedList(list.subList(1,listsize));
            boolean ok=scanListTryFail(lhs,path);
       //   if(!ok && rhs!=null) rewrites.put(path,rhs);
            return !ok;
        }
        if(listsize==2 && list.get(0).equals("<")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) < d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(listsize==2 && list.get(0).equals(">")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) > d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(listsize==2 && list.get(0).equals("<=")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) <= d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(listsize==2 && list.get(0).equals(">=")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) >= d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(listsize==2 && list.get(0).equals("divisible-by")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                int i=d.intValue();
                int j=(int)contentDouble(path);
                boolean ok = ((j % i)==0);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(listsize==2 && (list.get(0).equals("count"))){
            Double d=findDouble(list.get(1));
            if(d!=null){
                int i=d.intValue();
                if(!contentSet(path) && i==0) return true;
                LinkedList ll=contentList(path);
                return (ll==null && contentSet(path) && i==1) ||
                       (ll!=null && ll.size()==i);
            }
        }
        if(listsize==2 && (list.get(0).equals("count"))){
            Double d=contentDouble(path);
            if(d!=null){
                int i=d.intValue();
                LinkedList ll=findList(list.get(1));
                return (ll==null && i==0) ||
                       (ll!=null && ll.size()==i);
            }
        }
        if(listsize==4 && list.get(0).equals("within") && list.get(2).equals("of")){
            Double d=findDouble(list.get(1));
            if(d==null) return false;
            LinkedList a=contentList(path);
            if(a==null || a.size()==0 || !(a.get(0) instanceof Number)) return false;
            LinkedList b=findList(list.get(3));
            if(b==null || b.size()==0 || !(b.get(0) instanceof Number)) return false;
            return withinOf(d, a, b);
        }
        if(listsize==1 && list.get(0).equals("#")){
            boolean ok=!contentSet(path);
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if(listsize==2 && list.get(1).equals("##")){
            boolean ok=scanType(list.get(0),path+":0")
                             && !contentSet(path+":1");
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if("##".equals(list.get(listsize-1))){
            LinkedList ll=contentList(path);
            boolean ok=(ll!=null && ll.size()==listsize-1) &&
                        scanList(new LinkedList(list.subList(0,listsize-1)),path,rhs);
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if(listsize==2 && list.get(1).equals("**")){
            boolean ok=scanType(list.get(0),path+":0");
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if(listsize==3 && list.get(2).equals("**")){
            boolean ok=scanType(list.get(0),path+":0") &&
                       scanType(list.get(1),path+":1");
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        LinkedList ll=contentList(path);
        if(ll==null && listsize==1 && scanType(list.get(0),path)){ if(rhs!=null) rewrites.put(path,rhs); return true; }
        if(ll==null) ll=contentListMayJump(path);
        if(ll==null) return false;
        if(doScanList(list,path,rhs,ll)) return true;
        if(listsize==1 && isRef(list.get(0)) && scanType(findObject(list.get(0)),path)){ if(rhs!=null) rewrites.put(path,rhs); return true; }
        return false;
    }

    private boolean doScanList(LinkedList list, String path, LinkedList rhs, LinkedList ll){
        boolean matchEach=list.size()!=1;
        LinkedList<String> bl=new LinkedList<String>();
        int i=0;
        for(Object v: list){
            boolean ok=false;
            for(; i<ll.size(); i++){
                String pk=path+":"+i;
                if(scanTypeMayFail(v,pk)){ ok=true; bl.add(pk); if(matchEach) break; if(rhs!=null) rewrites.put(pk,rhs); }
            }
            if(!ok) return false;
            if(matchEach) i++;
        }
        bindings.put(path,bl);
        return true;
    }

    private boolean mayfail=false;
    private boolean tryfail=false;

    private boolean scanTypeMayMayFail(Object v, String pk, boolean domay){ return domay? scanTypeMayFail(v,pk): scanType(v,pk); }

    private boolean scanTypeMayFail(Object v, String pk){ boolean m=mayfail; mayfail=true; boolean ok=scanType(v,pk); mayfail=m; return ok; }

    private boolean scanListTryFail(LinkedList ll, String pk){ boolean t=tryfail; tryfail=true; boolean ok=scanList(ll,pk,null); tryfail=t; return ok; }

    private boolean scanStringTryFail(String s, String pk){ boolean t=tryfail; tryfail=true; boolean ok=scanString(s,pk); tryfail=t; return ok; }

    private boolean scanType(Object v, String pk){
        boolean r=doScanType(v,pk);
        String when=contentStringOr("Rule:when","(no description)");
        if(!r && extralogging && !tryfail && !mayfail) log("When "+when+" "+uid+"\nFailed to match "           +v+" @"+pk+"="+contentObject(pk));
        if( r && extralogging &&  tryfail            ) log("When "+when+" "+uid+"\nTrying to fail but matched "+v+" @"+pk+"="+contentObject(pk));
        return r;
    }

    @SuppressWarnings("unchecked")
    private boolean doScanType(Object v, String pk){
        if(v==null) return false;
        if(v instanceof String)        return scanString((String)v, pk);
        if(v instanceof Number)        return scanNumber((Number)v, pk);
        if(v instanceof Boolean)       return scanBoolean((Boolean)v, pk);
        if(v instanceof LinkedHashMap) return scanHash((LinkedHashMap<String,Object>)v, pk);
        if(v instanceof LinkedList)    return scanList((LinkedList)v, pk, null);
        return false;
    }

    private boolean scanString(String v, String pk){
        if(v.startsWith("!")) return !scanStringTryFail(v.substring(1),pk);
        if(v.equals("*")) return  contentSet(pk);
        if(v.equals("#")) return !contentSet(pk);
        if(contentIs(pk,v)) return true;
        if(contentIsMayJump(pk,v)) return true;
        if(v.equals("@") && contentIsThis(pk)) return true;
        if(v.equals("number") && isNumber(contentObject(pk))) return true;
        if(v.equals("boolean") && isBoolean(contentObject(pk))) return true;
        if(v.startsWith("/") && v.endsWith("/") && regexMatch(v.substring(1,v.length()-1),pk)) return true;
        if(isBoolean(v) && scanBoolean(findBooleanIn(v),pk)) return true;
        if(isRef(v) && scanType(findObject(v),pk)) return true;
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        return false;
    }

    private boolean scanNumber(Number v, String pk){
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        Object o=contentObject(pk);
        return isNumber(o) && findNumberIn(o)==v.doubleValue();
    }

    private boolean scanBoolean(Boolean v, String pk){
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        Object o=contentObject(pk);
        return (o==null && v==false) || (o!=null && isBoolean(o) && findBooleanIn(o)==v);
    }

    private boolean scanListFromSingleIfNotAlready(Object v, String pk){
        if(indexingPath(pk)) return false;
        return scanList(list(v),pk,null);
    }

    @SuppressWarnings("unchecked")
    private boolean scanDeep(Object o, String path){
        if(!(o instanceof LinkedList)) return false;
        LinkedList list=(LinkedList)o;
        boolean ok=scanTypeMayFail(list,path);
        LinkedHashMap<String,Object> hm=contentHash(path.equals("")? "#": path);
        if(hm!=null) return scanHashDeep(list,hm,path) || ok;
        LinkedList ll=contentList(path);
        if(ll!=null) return scanListDeep(list,ll,path) || ok;
        return ok;
    }

    private boolean scanHashDeep(LinkedList list, LinkedHashMap<String,Object> hm, String path){
        boolean ok=false;
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String pk=(path.equals("")? "": path+":")+entry.getKey();
            if(ignoreTopLevelNoise(path,pk)) continue;
            ok=scanDeep(list,pk) || ok;
        }
        return ok;
    }

    private boolean scanListDeep(LinkedList list, LinkedList ll, String path){
        boolean ok=false;
        for(int i=0; i<ll.size(); i++) ok=scanDeep(list,path+":"+i) || ok;
        return ok;
    }

    private boolean ignoreTopLevelNoise(String path, String pk){
        if(!path.equals("")) return false;
        if(pk.equals("Rules")) return true;
        if(pk.equals("Rule")) return true;
        if(pk.equals("is")) return true;
        if(pk.equals("when")) return true;
        if(pk.equals("watching")) return true;
        if(pk.equals("editable")) return true;
        if(pk.equals("user")) return true;
        return false;
    }

    private boolean indexingPath(String path){
        if(path.endsWith(":")) path=path.substring(0,path.length()-1);
        int c=path.lastIndexOf(":");
        if(c== -1) return false;
        String post=path.substring(c+1);
        return isNumber(post);
    }

    private boolean regexMatch(String regex, String pk){
        String s=content(pk);
        if(s==null) return false;
        Pattern p=Pattern.compile(regex);
        Matcher m=p.matcher(s);
        return m.find();
    }

    // ----------------------------------------------------

    String currentRewritePath=null;

    @SuppressWarnings("unchecked")
    private void doRewrites(){
        String shufflePath=null;
        LinkedHashMap<String,Boolean> topDowners=new LinkedHashMap<String,Boolean>();
        for(Map.Entry<String,LinkedList> entry: rewrites.entrySet()){
            currentRewritePath=entry.getKey();
            if(currentRewritePath.equals("Alerted")) continue;

            int l=currentRewritePath.lastIndexOf(":");
            String basePath=(l!= -1)? currentRewritePath.substring(0,l): null;
            boolean newBasePathAndShufflingToDo=(shufflePath!=null && !shufflePath.equals(basePath));
            if(newBasePathAndShufflingToDo){ shuffleList(shufflePath); shufflePath=null; }

            LinkedList rhs=entry.getValue();
            if(rhs.size()==0) continue;
            if(rhs.size() >=2 && "@.".equals(rhs.get(0)) && "with".equals(rhs.get(1))){
                LinkedList e=findListIn(deepCopyObject(evalDeepList(subList(rhs,2))));
                if(e==null || e.size()==0) continue;
                if(currentRewritePath.equals("Notifying")) for(Object o: e) notifying(o.toString());
                else contentSetAddAll(currentRewritePath, e);
            }
            else
            if(rhs.size() >=2 && "@.".equals(rhs.get(0)) && "without".equals(rhs.get(1))){
                LinkedList e=findEach(subList(rhs,2));
                if(e==null || e.size()==0) continue;
                if(currentRewritePath.equals("Notifying")) for(Object o: e) unnotifying(o.toString());
                else contentListRemoveAll(currentRewritePath, e);
            }
            else{
                Object e=deepCopyObject(evalDeepList(rhs));
                if(e==null){
                    if(extralogging) log("Deleting "+currentRewritePath+" "+rhs);
                    String[] parts=currentRewritePath.split(":");
                    String lastpart=parts[parts.length-1];
                    if(parts.length==1 || !isNumber(lastpart)) contentRemove(currentRewritePath);
                    else {
                           if(contentList(basePath)==null){ if("0".equals(lastpart)) contentRemove(basePath); else contentRemove(currentRewritePath); }
                           else{ content(currentRewritePath,"#"); shufflePath=basePath; }
                    }
                }
                else
                if(currentRewritePath.equals("")){
                    if(!(e instanceof LinkedHashMap)){ log("failed to rewrite entire item: "+e); continue; }
                    contentReplace(new JSON((LinkedHashMap)e));
                }
                else contentObject(currentRewritePath, e);
            }
            maybePut(topDowners,trimListPath(currentRewritePath),true);
        }
        if(shufflePath!=null) shuffleList(shufflePath);
        for(String p: topDowners.keySet()){
            LinkedList ll=contentList(p);
            if(ll!=null) contentObject(p,deepCopyObject(findObject(eval(ll))));
        }
    }

    static public String  LISTPATHRE=null;
    static public Pattern LISTPATHPA=null;

    private String trimListPath(String path){
        if(LISTPATHRE==null){
            LISTPATHRE = "(.*?):[0-9][:0-9]*$";
            LISTPATHPA = Pattern.compile(LISTPATHRE);
        }
        Matcher m = LISTPATHPA.matcher(path);
        return (m.matches())? m.group(1): null;
    }

    @SuppressWarnings("unchecked")
    private void shuffleList(String path){
        LinkedList ll=contentList(path);
        if(ll==null) return;
        LinkedList lr=new LinkedList();
        for(Object o: ll) if(!"#".equals(o)) lr.add(o);
        contentList(path,lr);
    }

    // ------- New Evaluator ---------------------------------------------------

    private Object evalDeepList(LinkedList ll){
        if(isAsIs(ll)) return ll;
        Object e=evalShallow(ll);
        if(!(e instanceof LinkedList)) return evalDeepObject(e);
        if(isAsIs(e)) return e;
        LinkedList r=new LinkedList();
        for(Object o: (LinkedList)e) maybeAdd(r,evalDeepObject(o));
        return evalShallow(r);
    }

    @SuppressWarnings("unchecked")
    private Object evalDeepObject(Object o){
        if(isRef(o)) return eitherBindingOrContentObject(((String)o).substring(1));
        if(o instanceof String)  return o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedList) return evalDeepList((LinkedList)o);
        if(o instanceof LinkedHashMap) return evalDeepHash((LinkedHashMap)o);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Object evalDeepHash(LinkedHashMap<String,Object> hm){
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            maybePut(r, entry.getKey(), evalDeepObject(entry.getValue()));
        }
        return r;
    }

    private Object evalShallow(LinkedList ll){
        if(isAsIs(ll)) return ll;
        if(ll==null || ll.size()==0) return null;
        if(ll.size()==1) return ll.get(0);
        Object o0=                 findObject(ll.get(0));
        Object o1=                 findObject(ll.get(1));
        Object o2=(ll.size() >=3)? findObject(ll.get(2)): null;
        Object o3=(ll.size() >=4)? findObject(ll.get(3)): null;
        Object o4=(ll.size() >=5)? findObject(ll.get(4)): null;
        if(ll.size()==2 && "count".equals(o0)){
            return Double.valueOf(numberOfElements(o1));
        }
        if(ll.size()==3 && "random".equals(o0)){
            Double d1=findDouble(o1);
            Double d2=findDouble(o2);
            if(d1!=null && d2!=null) return Double.valueOf(random(d1, d2));
        }
        if(ll.size()==4 && "clamp".equals(o0)){
            Double d1=findDouble(o1);
            Double d2=findDouble(o2);
            Double d3=findDouble(o3);
            if(d1!=null && d2!=null && d3!=null) return Double.valueOf(clamp(d1, d2, d3));
        }
        if(ll.size()==2 && "integer".equals(o0)){
            Double d1=findDouble(o1);
            if(d1!=null) return Integer.valueOf((int)(0.5+d1));
        }
        Object r=eval(ll);
        return r;
    }

    // ---------------------------------------

    private Object deepCopyObject(Object o){ return deepCopyObject(o,false); }

    @SuppressWarnings("unchecked")
    private Object deepCopyObject(Object o, boolean asis){
        if(o==null) return null;
        if(o instanceof String && "".equals(o)) return null;
        if(o instanceof String && "#".equals(o) && !asis) return null;
        if(o instanceof String)  return ((String)o).equals("uid-new")? spawnNewThing(): o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return deepCopyHash(((LinkedHashMap)o),asis);
        if(o instanceof LinkedList)    return deepCopyList(((LinkedList)o),asis);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyHash(LinkedHashMap<String,Object> hm, boolean asis){
        boolean spawned=false;
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            if(!asis && k.equals("UID")){ if(o.equals("new")) spawned=true; }
            else maybePut(r,k,deepCopyObject(o,asis));
        }
        return spawned? spawnHash(r): r;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopyList(LinkedList ll, boolean asis){
        if(ll.size()==0) return null;
        if(!asis && isAsIs(ll)) return deepCopyObject(ll.get(1),true);
        LinkedList r=new LinkedList();
        for(Object o: ll) maybeAdd(r,deepCopyObject(o,asis));
        return r;
    }

    private boolean isAsIs(Object o){
        return o instanceof LinkedList && ((LinkedList)o).size()==2 && "as-is".equals(((LinkedList)o).get(0));
    }

    // ----------------------------------------------------

    private Object eval(LinkedList ll){ return eval(ll, null); }

    @SuppressWarnings("unchecked")
    private Object eval(LinkedList ll, String lep){ try{
        if(ll==null || ll.size()==0) return null;
        if(ll.size()==1) return ll;
        String s0=findString(ll.get(0));
        String s1=findString(ll.get(1));
        String s2=null;
        String s3=null;
        String s4=null;
        Double d0=null;
        Double d1=null;
        Double d2=null;
        Double d3=null;
        Boolean b1=null;
        Boolean b2=null;
        LinkedList l0=null;
        LinkedList lh0=null;
        LinkedList l1=null;
        LinkedList l2=null;
        LinkedHashMap h0=null;
        LinkedHashMap h2=null;
        if(ll.size()==2 && "count".equals(s0)){
            Object o1=findObject(ll.get(1));
            return Double.valueOf(numberOfElements(o1));
        }
        if(ll.size()==3 && "random".equals(s0)){
            if(d1==null) d1=findDouble(ll.get(1));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d1!=null && d2!=null) return Double.valueOf(random(d1, d2));
        }
        if(ll.size()==4 && "clamp".equals(s0)){
            if(d1==null) d1=findDouble(ll.get(1));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d3==null) d3=findDouble(ll.get(3));
            if(d1!=null && d2!=null && d3!=null) return Double.valueOf(clamp(d1, d2, d3));
        }
        if(ll.size()==2 && "integer".equals(s0)){
            if(d1==null) d1=findDouble(ll.get(1));
            if(d1!=null) return Integer.valueOf((int)(0.5+d1));
        }
        if(ll.size()==3 && "..".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return expandRange(d0,d2);
        }
        if(ll.size()==3 && "format".equals(s0)){
            if(s1==null) s1=findString(ll.get(1));
            if(s2==null) s2=findString(ll.get(2));
            if(s1!=null && s2!=null) return String.format(s1, s2);
        }
        if(ll.size()==4 && "format".equals(s0)){
            if(s1==null) s1=findString(ll.get(1));
            if(s2==null) s2=findString(ll.get(2));
            if(s3==null) s3=findString(ll.get(3));
            if(s1!=null && s2!=null && s3!=null) return String.format(s1, s2, s3);
        }
        if(ll.size()==5 && "format".equals(s0)){
            if(s1==null) s1=findString(ll.get(1));
            if(s2==null) s2=findString(ll.get(2));
            if(s3==null) s3=findString(ll.get(3));
            if(s4==null) s4=findString(ll.get(4));
            if(s1!=null && s2!=null && s3!=null && s4!=null) return String.format(s1, s2, s3, s4);
        }
        if(ll.size()==3 && "join".equals(s0)){
            if(l2==null) l2=findList(ll.get(2));
            if(s1==null) s1=findString(ll.get(1));
            return join(findEach(l2), s1);
        }
        if(ll.size()==2 && "flatten".equals(s0)){
            if(l1==null) l1=findList(ll.get(1));
            return flatten(l1,new LinkedList());
        }
        if(ll.size() >=2 && "with".equals(s1)){
            if(l0==null) l0=findList(ll.get(0),false);
            return listWith(findEach(l0),findEach(subList(ll,2)));
        }
        if(ll.size()==2 && "+".equals(s0)){
            if(l1==null) l1=findList(ll.get(1));
            Double sl1=sumAll(l1);
            if(sl1!=null) return sl1;
        }
        if(ll.size()==3 && "-".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 - d2);
        }
        if(ll.size()==3 && "+".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 + d2);
        }
        if(ll.size()==3 && "×".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 * d2);
        }
        if(ll.size()==3 && "*".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 * d2);
        }
        if(ll.size()==3 && "÷".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 / d2);
        }
        if(ll.size()==3 && "/".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 / d2);
        }
        if(ll.size()==3 && "%".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Double.valueOf(d0 % d2);
        }
        if(ll.size()==3 && "dot".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null){
                LinkedList lr=vmdot(l0, l2);
                if(lr!=null) return lr;
            }
        }
        if(ll.size()==3 && "+".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null){
                LinkedList lr=vvadd(l0, l2);
                if(lr!=null) return lr;
            }
        }
        if(ll.size()==3 && "-".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null){
                LinkedList lr=vvsub(l0, l2);
                if(lr!=null) return lr;
            }
        }
        if(ll.size()==3 && "~".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null) return vvdist(l0, l2);
        }
        if(ll.size()==3 && "/".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(l0!=null && d2!=null){
                LinkedList lr=vsdiv(l0, d2);
                if(lr!=null) return lr;
            }
        }
        if(ll.size()==3 && "<".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Boolean.valueOf(d0 < d2);
        }
        if(ll.size()==3 && ">".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Boolean.valueOf(d0 > d2);
        }
        if(ll.size()==3 && "<=".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Boolean.valueOf(d0 <= d2);
        }
        if(ll.size()==3 && ">=".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Boolean.valueOf(d0 >= d2);
        }
        if(ll.size()==3 && "==".equals(s1)){
            if(d0==null) d0=findDouble(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(d0!=null && d2!=null) return Boolean.valueOf(d0.doubleValue() == d2.doubleValue());
        }
        if(ll.size()==3 && "==".equals(s1)){
            if(s0==null) s0=findString(ll.get(0));
            if(s2==null) s2=findString(ll.get(2));
            if(s0!=null && s2!=null) return Boolean.valueOf(s0.equals(s2));
        }
        if(ll.size()==6 && "if".equals(s0)){
            if(s2==null) s2=findString(ll.get(2));
            if(s4==null) s4=findString(ll.get(4));
            if("then".equals(s2) && "else".equals(s4)){
                if(b1==null) b1=findBoolean(ll.get(1));
                if(b1!=null) return b1? findObject(ll.get(3)): findObject(ll.get(5));
            }
        }
        if(ll.size()==3 && "select".equals(s1)){
            Object o02=findHashOrListAndGet(ll.get(0),ll.get(2));
            if(o02!=null) return findObject(o02);
        }
        if(ll.size()==5 && "select".equals(s1)){
            if(s3==null) s3=findString(ll.get(3));
            if("else".equals(s3)){
                Object o02=findHashOrListAndGet(ll.get(0),ll.get(2));
                if(o02!=null) return findObject(o02);
                return findObject(ll.get(4));
            }
        }
        boolean trylist0=false;
        boolean trylist2=false;
        Object o0=ll.get(0);
        if(lep==null && isRef(o0)) lep=(String)o0;
        if(ll.size()==3 && "cut-out".equals(s1)){
            if(h0==null) h0=findHash(ll.get(0));
            if(l0==null) l0=findList(ll.get(0));
            trylist0=(l0!=null && l0.size() >1);
            if(h2==null) h2=findHash(ll.get(2));
            if(h0==null && !trylist0) return null;
            if(h0!=null && h2==null) return deepCopyObject(h0);
            if(h0!=null && h2!=null) return copyLessHash(h0,h2);
        }
        if(ll.size()==3 && ("with-more".equals(s1)||"add-more".equals(s1))){
            if(h0==null) h0=findHash(ll.get(0));
            if(l0==null) l0=findList(ll.get(0));
            trylist0=(l0!=null && l0.size() >1);
            if(h2==null) h2=findHash(ll.get(2));
            if(l2==null) l2=findList(ll.get(2));
            trylist2=(l2!=null && l2.size() >1);
            if(h0==null && !trylist0) return null;
            if(h0!=null && h2==null && !trylist2) return deepCopyObject(h0);
            if(h0!=null && h2!=null) return copyMoreHash(h0,h2,lep,null,"with-more".equals(s1));
        }
        if(ll.size()==3 && "each".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            trylist0=(l0!=null && l0.size() >1);
            if(!trylist0){
                Object fo0=findObject(ll.get(0));
                if(fo0==null) return null;
                Object fo00=singleElListEl(fo0);
                Object cmo=findObject(fo00==null? copyMoreObject(ll.get(2),lep,fo0):
                                                  copyMoreObject(ll.get(2),lep+":0",fo00));
                if(cmo!=null) return cmo;
            }
        }
        if(ll.size()==3 && "filter".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            trylist0=(l0!=null && l0.size() >1);
            if(!trylist0){
                Object fo0=findObject(ll.get(0));
                if(fo0==null) return null;
                Object fo00=singleElListEl(fo0);
                if(b2==null) b2=findBoolean(fo00==null? copyMoreObject(ll.get(2),lep,fo0):
                                                        copyMoreObject(ll.get(2),lep+":0",fo00));
                if(b2!=null) return b2? fo0: null;
            }
        }
        if(trylist0){
            Object r=listEval(ll,0,l0);
            if(r!=null) return r;
        }
        if(trylist2){
            Object r=listEvalAccum(ll,0,2,l2);
            if(r!=null) return r;
        }
        return ll;

    }catch(Throwable t){ t.printStackTrace(); log("something failed here: "+ll); return ll; } }

    @SuppressWarnings("unchecked")
    private LinkedList listEval(LinkedList ll, int n, LinkedList ln){
        Object on=ll.get(n);
        boolean isref=isRef(on);
        LinkedList lr=new LinkedList(ll);
        LinkedList r=new LinkedList();
        boolean failed=false;
        int i=0;
        for(Object o: ln){
            String lep=isref? on+("@.".equals(on)? "": ":")+i: "";
            if(UID.isUID(o) && isref) o=lep;
            lr.set(n,o);
            Object e=eval(lr,lep);
            failed=(e==lr);
            if(!(e==null || failed)) r.add(e);
        i++; }
        return failed && r.size()==0? ll: r;
    }

    @SuppressWarnings("unchecked")
    private Object listEvalAccum(LinkedList ll, int m, int n, LinkedList ln){
        Object om=ll.get(m);
        boolean isref=isRef(om);
        LinkedList lr=new LinkedList(ll);
        Object e=om;
        int i=0;
        for(Object o: ln){
            String lep=isref? (String)om: "";
            lr.set(m,e);
            lr.set(n,o);
            e=eval(lr,lep);
        i++; }
        return e;
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private LinkedList findEach(List ll){
        if(ll==null) return null;
        LinkedList r=new LinkedList();
        for(Object o: ll) maybeAdd(r,findObject(o));
        return r;
    }

    private Object findObject(Object o){
        if(o==null) return null;
        if(isRef(o)) return eitherBindingOrContentObject(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return o;
    }

    private String findString(Object o){
        if(o==null) return "";
        if(isRef(o)) return eitherBindingOrContentString(((String)o).substring(1));
        if(o instanceof LinkedList){ o=eval((LinkedList)o); if(o==null) return null; }
        if(o instanceof Number) return toNicerString((Number)o);
        return o.toString();
    }

    private Double findDouble(Object o){
        Number n=findNumber(o);
        if(n==null) return null;
        return n.doubleValue();
    }

    private Number findNumber(Object o){
        if(o==null) return null;
        if(isRef(o)) return eitherBindingOrContentNumber(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return isNumber(o)? findANumberIn(o): null;
    }

    private Boolean findBoolean(Object o){
        if(o==null) return false;
        if(isRef(o)) return eitherBindingOrContentBool(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return isBoolean(o)? findBooleanIn(o): null;
    }

    private LinkedHashMap findHash(Object o){
        if(o==null) return new LinkedHashMap();
        if(isRef(o)) return eitherBindingOrContentHash(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findHashIn(o);
    }

    private LinkedList findList(Object o){ return findList(o,true); }

    private LinkedList findList(Object o, boolean jump){
        if(o==null) return new LinkedList();
        if(isRef(o)) return eitherBindingOrContentList(((String)o).substring(1), jump);
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findListIn(o);
    }

    private Object findHashOrListAndGet(Object collection, Object index){

        if(collection==null || index==null) return null;

        LinkedHashMap hm=findHash(collection);
        String s=findString(index);
        if(s==null) return null;
        if(hm!=null && hm.size() >0){ Object o=hm.get(s); if(o!=null) return o; }

        LinkedList    ll=findList(collection,false);
        Double d=findDouble(index);
        if(d==null) return null;
        int i=d.intValue();
        if(ll!=null && ll.size() >i) return ll.get(i);

        return null;
    }

    // ----------------------------------------------------

    private Object eitherBindingOrContentObject(String path){
        if(path.startsWith("..")) return "@"+path;
        if(path.startsWith("."))  return contentObject(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return getBinding(path.substring(1),false);
        Object o=contentObject(path);
        if(o!=null) return o;
        return contentAll(path);
    }

    private String eitherBindingOrContentString(String path){
        if(path.startsWith("..")) return "@"+path;
        if(path.startsWith("."))  return contentString(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findStringIn(getBinding(path.substring(1),true));
        return contentString(path);
    }

    private Number eitherBindingOrContentNumber(String path){
        if(path.startsWith("..")) return null;
        if(path.startsWith("."))  return contentNumber(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findNumberIn(getBinding(path.substring(1),true));
        return contentNumber(path);
    }

    private Boolean eitherBindingOrContentBool(String path){
        if(path.startsWith("..")) return null;
        if(path.startsWith("."))  return contentBool(  currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findBooleanIn(getBinding(path.substring(1),true));
        return contentBool(path);
    }

    private LinkedList eitherBindingOrContentList(String path, boolean jump){
        if(path.startsWith("..")) return null;
        if(path.startsWith("."))  return contentListMayJump(  currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findListIn(getBinding(path.substring(1),jump));
        LinkedList ll=contentListMayJump(path);
        if(ll!=null) return ll;
        return contentAll(path);
    }

    private LinkedHashMap eitherBindingOrContentHash(String path){
        if(path.startsWith("..")) return null;
        if(path.startsWith("."))  return contentHashMayJump(  currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findHashIn(getBinding(path.substring(1),true));
        return contentHashMayJump(path);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object getBinding(String path, boolean jump){
        String pk=path;
        LinkedList<String> ll=bindings.get(pk);
        if(ll!=null) return objectsAt(ll,null,jump);
        do{
            int e=pk.lastIndexOf(":");
            if(e== -1) return null;
            pk=pk.substring(0,e);
            ll=bindings.get(pk);
            if(ll==null) continue;
            return objectsAt(ll,path.substring(e+1),jump);

        }while(true);
    }

    @SuppressWarnings("unchecked")
    private Object objectsAt(LinkedList<String> ll, String p, boolean jump){
        LinkedList r=new LinkedList();
        String cp=(p==null)? "": ":"+p;
        for(String s: ll) maybeAdd(r, jump? contentObjectMayJump(s+cp): contentObject(s+cp));
        return r.isEmpty()? null: (r.size()==1? r.get(0): r);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object copyLessObject(Object o, Object d){
        if(o==null) return null;
        if(o instanceof String)  return o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return copyLessHash(((LinkedHashMap)o),(d!=null && d instanceof LinkedHashMap)? (LinkedHashMap)d: null);
        if(o instanceof LinkedList)    return copyLessList(((LinkedList)o),d);
        return o;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap copyLessHash(LinkedHashMap<String,Object> hm, LinkedHashMap dl){
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            Object d=(dl!=null)? dl.get(k): null;
            if(d!=null && d instanceof String && (((String)d).equals("*") || o.equals(d))) continue;
            r.put(k, copyLessObject(o,d));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object copyLessList(LinkedList ll, Object d){
        LinkedList r=new LinkedList();
        for(Object o: ll){
            if(d!=null && d instanceof String && (((String)d).equals("*") || o.equals(d))) continue;
            if(d!=null && d instanceof LinkedList && (((LinkedList)d).contains(o))) continue;
            maybeAdd(r,copyLessObject(o,null));
        }
        return r;
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object copyMoreHash(LinkedHashMap<String,Object> hm, LinkedHashMap<String,Object> ha, String lep, Object lev, boolean wm){
        LinkedHashMap r=new LinkedHashMap();
        LinkedHashMap<String,Object> hx=new LinkedHashMap<String,Object>();
        if(ha!=null) for(Map.Entry<String,Object> entry: ha.entrySet()){
            String k=entry.getKey();
            if(!isRef(k)) continue;
            Object rf=findObjectFixRefs(k,lep,lev,false);
            if(rf instanceof String) maybePut(hx, (String)rf, entry.getValue());
        }
        if(hx.size()!=0) ha=new LinkedHashMap<String,Object>(ha);
        for(Map.Entry<String,Object> entry: hx.entrySet()) ha.put(entry.getKey(),entry.getValue());
        if(hm!=null) for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            Object a=(ha!=null)? ha.get(k): null;
            maybePut(r, k, copyMoreObject(o,a,lep,lev,wm,false));
        }
        if(ha!=null) for(Map.Entry<String,Object> entry: ha.entrySet()){
            String k=entry.getKey();
            if(isRef(k)) continue;
            Object a=entry.getValue();
            if(hm!=null && hm.get(k)!=null) continue;
            maybePut(r, k, copyMoreObject(a,null,lep,lev,wm,false));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object copyMoreObject(Object o, String lep, Object lev){
        if(o==null) return null;
        if(o instanceof String) o=findObjectFixRefs(o,lep,lev,false);
        if(o instanceof String)  return o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return copyMoreHash(null,((LinkedHashMap)o),lep,lev,false);
        if(o instanceof LinkedList)    return copyMoreList(((LinkedList)o),null,lep,lev,false);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Object copyMoreObject(Object o, Object a, String lep, Object lev, boolean wm, boolean justref){
        if(o==null) return null;
        if(o instanceof String) o=findObjectFixRefs(o,lep,lev,justref);
 //     if(a instanceof String) a=findObjectFixRefs(a,lep,lev);
        if(o instanceof String)  return listWM(o,a,wm);
        if(o instanceof Number)  return listWM(o,a,wm);
        if(o instanceof Boolean) return listWM(o,a,wm);
        if(o instanceof LinkedHashMap) return copyMoreHash(((LinkedHashMap)o),(a!=null && a instanceof LinkedHashMap)? (LinkedHashMap)a: null,lep,lev,wm);
        if(o instanceof LinkedList)    return copyMoreList(((LinkedList)o),a,lep,lev,wm);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Object copyMoreList(LinkedList ll, Object a, String lep, Object lev, boolean wm){
        LinkedList r=new LinkedList();
        for(Object o: ll) maybeAdd(r,copyMoreObject(o,null,lep,lev,wm,true));
        if(a==null) return r;
   //   if(a instanceof String)        a=findObjectFixRefs(a,lep,lev);
        if(a instanceof LinkedList)    a=copyMoreList(((LinkedList)a),null,lep,lev,wm);
        if(a instanceof LinkedHashMap) a=copyMoreHash((LinkedHashMap)a,null,lep,lev,wm);
        if(a instanceof LinkedList) maybeAddAllWM(r,(LinkedList)a,wm);
        else                        maybeAddWM(r,a,wm);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object spawnHash(LinkedHashMap hm){
        hm.put("within",uid);
        try{ return spawn(getClass().newInstance().construct(hm)); } catch(Throwable t){ t.printStackTrace(); }
        return hm;
    }

    public String spawnNewThing(){
        return spawn(new CyrusLanguage("{ is: editable new-thing within: "+uid+" }", true));
    }

    private Object findObjectFixRefs(Object o, String lep, Object lev, boolean justref){
        boolean needfix=(o instanceof String) && ((String)o).startsWith("@..");
        boolean nolep=(lep==null || lep.length()==0);
        if(needfix && nolep && lev!=null){
            if(!(lev instanceof LinkedHashMap && ((String)o).startsWith("@..:"))) return lev;
            return new JSON((LinkedHashMap)lev).objectPathN(((String)o).substring(4));
        }
        if(!needfix || nolep) return findObject(o);
        String p=((String)o).replace("@..",lep);
        return justref? p: findObject(p);
    }

    private Object listWM(Object a, Object b, boolean wm){
        if(b==null) return a;
        if(a==null) return b;
        boolean alist=(a instanceof LinkedList);
        boolean blist=(b instanceof LinkedList);
        if( alist &&  blist){ maybeAddAllWM((LinkedList)a,(LinkedList)b,wm); return a; }
        if( alist && !blist){ maybeAddWM((LinkedList)a,b,wm); return a; }
        if(!alist &&  blist){ LinkedList l=list(a); maybeAddAllWM(l,(LinkedList)b,wm); return l; }
        if(wm && a.equals(b)) return a;
        return list(a,b);
    }

    @SuppressWarnings("unchecked")
    private void maybePut(LinkedHashMap hm, String key, Object val){ if(key!=null && val!=null) hm.put(key,val); }

    @SuppressWarnings("unchecked")
    private void maybeAdd(LinkedList ll, Object val){ if(val!=null) ll.add(val); }

    @SuppressWarnings("unchecked")
    private void maybeAddAll(LinkedList ll, LinkedList la){ if(la!=null) ll.addAll(la); }

    @SuppressWarnings("unchecked")
    private void maybeAddWM(LinkedList ll, Object val, boolean wm){ if(val!=null && !(wm && ll.contains(val))) ll.add(val); }

    @SuppressWarnings("unchecked")
    private void maybeAddAllWM(LinkedList ll, LinkedList val, boolean wm){ if(val!=null) for(Object o: val) maybeAddWM(ll,o,wm); }

    private boolean isRef(Object o){ return (o instanceof String) && ((String)o).startsWith("@"); }
}


