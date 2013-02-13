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
            if(!(i==0 && rules.size() >0)) rules=contentAsList("Rules");   if(extralogging) log("Rules: "+rules);
            if(rules==null || rules.size()==0) break;
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
        ; if(extralogging) log("==========\nscanRuleHash="+(ok?"pass":"fail")+"\n"+rule+"\n====\n"+contentHash("#")+"\n===========");
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
            if(ignoreTopLevelNoise(path,pk)) continue;
            Object v=entry.getValue();
            if(pk.endsWith("**")){ if(v instanceof LinkedList) scanDeep((LinkedList)v,pk.substring(0,pk.length()-2)); }
            else
            if(!scanType(v,pk)) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean scanList(LinkedList list, String path, LinkedList rhs){
        if(list.size()==0) return true;
        if(list.size()==2 && list.get(0).equals("maybe")){
            scanList(new LinkedList(list.subList(1,list.size())),path,rhs);
            return true;
        }
        if(list.size()==2 && list.get(0).equals("<")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) < d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(list.size()==2 && list.get(0).equals(">")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) > d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(list.size()==2 && list.get(0).equals("<=")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) <= d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(list.size()==2 && list.get(0).equals(">=")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                boolean ok = (contentDouble(path) >= d);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(list.size()==2 && list.get(0).equals("divisible-by")){
            Double d=findDouble(list.get(1));
            if(d!=null){
                int i=d.intValue();
                int j=(int)contentDouble(path);
                boolean ok = ((j % i)==0);
       //       if(ok && rhs!=null) rewrites.put(path,rhs);
                return ok;
            }
        }
        if(list.size()==2 && (list.get(1).equals("element")|| list.get(1).equals("elements"))){
            Double d=findDouble(list.get(0));
            if(d!=null){
                int i=d.intValue();
                if(!contentSet(path) && i==0) return true;
                LinkedList ll=contentList(path);
                return (ll==null && contentSet(path) && i==1) ||
                       (ll!=null && ll.size()==i);
            }
        }
        if(list.size()==1 && list.get(0).equals("#")){
            boolean ok=!contentSet(path);
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if(list.size()==2 && list.get(1).equals("**")){
            boolean ok=scanType(list.get(0),path+":0");
            if(ok && rhs!=null) rewrites.put(path,rhs);
            return ok;
        }
        if(list.size()==4 && list.get(0).equals("within")){
            return withinOf(findDouble(list.get(1)), contentList(path), findList(list.get(3),"double"));
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
            boolean ok=scanListTryFail(lhs,path);
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

    private boolean mayfail=false;
    private boolean tryfail=false;

    private boolean scanTypeMayFail(Object v, String pk){ boolean m=mayfail; mayfail=true; boolean ok=scanType(v,pk); mayfail=m; return ok; }

    private boolean scanListTryFail(LinkedList ll, String pk){ boolean t=tryfail; tryfail=true; boolean ok=scanList(ll,pk,null); tryfail=t; return ok; }

    private boolean scanStringTryFail(String s, String pk){ boolean t=tryfail; tryfail=true; boolean ok=scanString(s,pk); tryfail=t; return ok; }

    private boolean scanType(Object v, String pk){
        boolean r=doScanType(v,pk);
        if(!r && extralogging && !tryfail && !mayfail) log("Failed to match "+v+" at: "+pk+" "+contentObject(pk));
        if( r && extralogging &&  tryfail            ) log("Failed to match "+v+" at: "+pk+" "+contentObject(pk));
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
        if(v.startsWith("!")) return !scanStringTryFail(v.substring(1),pk);
        if(contentList(pk)!=null) return scanListFromSingleIfNotAlready(v,pk);
        if(contentIs(pk,v)) return true;
        if(contentIsMayJump(pk,v)) return true;
        if(v.equals("*")) return  contentSet(pk);
        if(v.equals("#")) return !contentSet(pk);
        if(v.equals("@"))       return contentIsThis(pk);
        if(v.equals("number"))  return isNumber( contentObject(pk));
        if(v.equals("boolean")) return isBoolean(contentObject(pk));
        if(v.startsWith("/") && v.endsWith("/")) return regexMatch(v.substring(1,v.length()-1),pk);
        if(v.startsWith("@")) return scanType(findObject(v),pk);
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
        return isBoolean(o) && findBooleanIn(o)==v;
    }

    private boolean scanListFromSingleIfNotAlready(Object v, String pk){
        if(indexingPath(pk)) return false;
        return scanList(list(v),pk,null);
    }

    @SuppressWarnings("unchecked")
    private void scanDeep(LinkedList list, String path){
        LinkedHashMap<String,Object> hm=contentHash(path.equals("")? "#": path);
        if(hm==null) return;
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String pk=(path.equals("")? "": path+":")+entry.getKey();
            if(ignoreTopLevelNoise(path,pk)) continue;
            scanListDeep(list,pk);
        }
    }

    private void scanListDeep(LinkedList list, String path){
        scanList(list,path,null);
        LinkedList ll=contentList(path);
        if(ll!=null) for(int i=0; i<ll.size(); i++) scanListDeep(list,path+":"+i);
        else scanDeep(list,path);
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
        for(Map.Entry<String,LinkedList> entry: rewrites.entrySet()){

            currentRewritePath=entry.getKey();
            if(currentRewritePath.equals("Alerted")) continue;

            int l=currentRewritePath.lastIndexOf(":");
            String basePath=(l!= -1)? currentRewritePath.substring(0,l): null;
            boolean newBasePathAndShufflingToDo=(shufflePath!=null && !shufflePath.equals(basePath));
            if(newBasePathAndShufflingToDo){ shuffleList(shufflePath); shufflePath=null; }

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
                if(e==null) log("May delete "+currentRewritePath+" .. but may just be a failed rewrite! "+ll);
                if(e==null || "#".equals(e)){
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
        }
        if(shufflePath!=null) shuffleList(shufflePath);

        LinkedHashMap<String,Boolean> maybeEvals=new LinkedHashMap<String,Boolean>();
        for(Map.Entry<String,LinkedList> entry: rewrites.entrySet()){
            String bp=trimListPaths(entry.getKey());
            if(bp!=null) maybeEvals.put(bp, true);
        }
        for(Map.Entry<String,Boolean> entry: maybeEvals.entrySet()){
            String p=entry.getKey();
logXX("deep list eval: @",p,contentList(p)," => ",eval(contentList(p)));
            contentObject(p,eval(contentList(p)));
        }
    }

    static public String  LISTPATHRE=null;
    static public Pattern LISTPATHPA=null;

    private String trimListPaths(String path){
        if(LISTPATHRE==null){
            LISTPATHRE = "(.*?):[0-9][:0-9]*:[0-9][:0-9]*$";
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

    @SuppressWarnings("unchecked")
    private Object eval(LinkedList ll){ try{
        if(ll.size()==0) return null;
   //   if(ll.size()==1) return copyFindObject(ll.get(0));
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
        LinkedList l0=null;
        LinkedList l1=null;
        LinkedList l2=null;
        LinkedHashMap h2=null;
        if(ll.size()==2 && "count".equals(s0)){
            if(l1==null) l1=findList(ll.get(1));
            if(l1!=null) return Double.valueOf(sizeOf(l1));
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
        if(ll.size()==6 && "if".equals(s0)){
            if(b1==null) b1=findBoolean(ll.get(1));
            if(b1!=null) return b1? copyFindObject(ll.get(3)): copyFindObject(ll.get(5));
        }
        if(ll.size()==2 && "as-is".equals(s0)){
            return copyObject(ll.get(1), true);
        }
        if(ll.size()==3 && "join".equals(s0)){
            if(l2==null) l2=findList(ll.get(2));
            if(s1==null) s1=findString(ll.get(1));
            if(l2!=null && s1!=null) return join(l2, s1);
        }
        if(ll.size()==2 && "+".equals(s0)){
            if(l1==null) l1=findList(ll.get(1));
            if(l1!=null){
                Double sl1=sumAll(l1);
                if(sl1!=null) return Double.valueOf(sl1);
            }
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
        if(ll.size()==3 && "v.m".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null) return vmdot(l0, l2);
        }
        if(ll.size()==3 && "+".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null){
                LinkedList lr=vvadd(l0, l2);
                if(lr!=null) return lr;
            }
        }
        if(ll.size()==3 && "~".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(l2==null) l2=findList(ll.get(2));
            if(l0!=null && l2!=null) return vvdist(l0, l2);
        }
        if(ll.size()==3 && "v/s".equals(s1)){
            if(l0==null) l0=findList(ll.get(0));
            if(d2==null) d2=findDouble(ll.get(2));
            if(l0!=null && d2!=null) return vsdiv(l0, d2);
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
        if(ll.size()==3 && "select".equals(s1)){
            Object o02=findHashOrListAndGet(ll.get(0),ll.get(2));
            if(o02!=null) return copyFindObject(o02);
        }
        if(ll.size()==3 && "cut-out".equals(s1)){
            if(h2==null) h2=findHash(ll.get(2));
            if(h2!=null) return copyCutOutHash(ll.get(0),h2);
        }
        if(ll.size()==3 && "with-more".equals(s1)){
            if(h2==null) h2=findHash(ll.get(2));
            if(h2!=null) return copyWithMoreHash(ll.get(0),h2);
        }
        return copyFindEach(ll);
    }catch(Throwable t){ t.printStackTrace(); log("something failed here: "+ll); return ll; } }

    @SuppressWarnings("unchecked")
    private LinkedList copyFindEach(List ll){
        LinkedList r=new LinkedList();
        for(Object o: ll){ Object fo=copyFindObject(o); if(fo!=null) r.add(fo); }
        return r;
    }

    @SuppressWarnings("unchecked")
    private LinkedList findEach(List ll){
        LinkedList r=new LinkedList();
        for(Object o: ll){ Object fo=findObject(o); if(fo!=null) r.add(fo); }
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

    private Double findDouble(Object o){
        Number n=findNumber(o);
        if(n==null) return null;
        return n.doubleValue();
    }

    private Number findNumber(Object o){
        if(o==null) return null;
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentNumber(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return isNumber(o)? findANumberIn(o): null;
    }

    private Boolean findBoolean(Object o){
        if(o==null) return false;
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentBool(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return isBoolean(o)? findBooleanIn(o): null;
    }

    private LinkedHashMap findHash(Object o){
        if(o==null) return new LinkedHashMap();
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentHash(((String)o).substring(1));
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findHashIn(o);
    }

    private LinkedList findList(Object o){ return findList(o,null); }

    private LinkedList findList(Object o, String type){
        if(o==null) return new LinkedList();
        if(o instanceof String && ((String)o).startsWith("@")) return eitherBindingOrContentList(((String)o).substring(1),type);
        if(o instanceof LinkedList) o=eval((LinkedList)o);
        return findListIn(o);
    }

    private Object findHashOrListAndGet(Object collection, Object index){
        if(collection==null || index==null) return null;
        LinkedHashMap hm=findHash(collection);
        if(hm!=null && hm.size() >0) return hm.get(findObject(index));
        LinkedList    ll=findList(collection);
        Double d=findDouble(index);
        if(d==null) return null;
        int i=d.intValue();
        if(ll!=null && ll.size() >i) return ll.get(i);
        return null;
    }

    // ----------------------------------------------------

    private Object eitherBindingOrContentObject(String path){
        if(path.startsWith("."))  return contentObject(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return getBinding(path.substring(1),"object");
        Object o=contentObject(path);
        if(o!=null) return o;
        return contentAll(path);
    }

    private String eitherBindingOrContentString(String path){
        if(path.startsWith("."))  return contentString(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findStringIn(getBinding(path.substring(1),"string"));
        return contentString(path);
    }

    private Number eitherBindingOrContentNumber(String path){
        if(path.startsWith("."))  return contentNumber(currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findNumberIn(getBinding(path.substring(1),"double"));
        return contentNumber(path);
    }

    private Boolean eitherBindingOrContentBool(String path){
        if(path.startsWith("."))  return contentBool(  currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findBooleanIn(getBinding(path.substring(1),"boolean"));
        return contentBool(path);
    }

    private LinkedList eitherBindingOrContentList(String path, String type){
        if(path.startsWith("."))  return contentListMayJump(  currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findListIn(getBinding(path.substring(1),type));
        LinkedList ll=contentListMayJump(path);
        if(ll!=null) return ll;
        return contentAll(path);
    }

    private LinkedHashMap eitherBindingOrContentHash(String path){
        if(path.startsWith("."))  return contentHashMayJump(  currentRewritePath+(path.equals(".")?  "": ":"+path.substring(1)));
        if(path.startsWith("="))  return findHashIn(getBinding(path.substring(1),"hash"));
        return contentHashMayJump(path);
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object getBinding(String path, String type){
        String pk=path;
        LinkedList<String> ll=bindings.get(pk);
        if(ll!=null) return objectsAt(ll,null,type);
        do{
            int e=pk.lastIndexOf(":");
            if(e== -1) return null;
            String p=pk.substring(e+1);
            pk=pk.substring(0,e);
            ll=bindings.get(pk);
            if(ll==null) continue;
            return objectsAt(ll,p,type);

        }while(true);
    }

    @SuppressWarnings("unchecked")
    private Object objectsAt(LinkedList<String> ll, String p, String type){
        LinkedList r=new LinkedList();
        for(String s: ll){
            Object o=contentObjectMayJump(s+(p==null? "": ":"+p),type);
            if(o!=null) r.add(o);
        }
        return r.isEmpty()? null: (r.size()==1? r.get(0): r);
    }

    private Object contentObjectMayJump(String path, String type){
        Object o=null;
        if(           "list".equals(type)) o=contentListMayJump(path);
        if(o==null && "hash".equals(type)) o=contentHashMayJump(path);
        if(o==null)                        o=contentObject(path);
        return o;
    }

    // ----------------------------------------------------

    private Object copyFindObject(Object o){
        return copyObject(findObject(o), false);
    }

    @SuppressWarnings("unchecked")
    private Object copyObject(Object o, boolean asis){
        if(o==null) return null;
        if(o instanceof String)  return ((String)o).equals("uid-new")? spawn(new CyrusLanguage("{ \"is\": [ \"editable\" ] }")): o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return copyHash(((LinkedHashMap)o), asis);
        if(o instanceof LinkedList)    return copyList(((LinkedList)o), asis);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Object copyHash(LinkedHashMap<String,Object> hm, boolean asis){
        boolean spawned=false;
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            if(k.equals("UID") && !asis){ if(o.equals("new")) spawned=true; }
            else r.put(k, asis? copyObject(o,true): copyFindObject(o));
        }
        if(spawned) try{ return spawn(getClass().newInstance().construct(r)); } catch(Throwable t){ t.printStackTrace(); }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object copyList(LinkedList ll, boolean asis){
        LinkedList r=new LinkedList();
        for(Object o: ll) r.add(asis? copyObject(o,true): copyFindObject(o));
        return r;
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object copyCutOutObject(Object o, Object d){
        if(o==null) return null;
        if(o instanceof String)  return o;
        if(o instanceof Number)  return o;
        if(o instanceof Boolean) return o;
        if(o instanceof LinkedHashMap) return copyCutOutHash(((LinkedHashMap)o),(d!=null && d instanceof LinkedHashMap)? (LinkedHashMap)d: null);
        if(o instanceof LinkedList)    return copyCutOutList(((LinkedList)o),d);
        return o;
    }

    @SuppressWarnings("unchecked")
    private Object copyCutOutHash(Object ll0, LinkedHashMap dl){
        if(ll0==null) return null;
        LinkedHashMap hm=findHash(ll0);
        if(hm!=null) return copyCutOutHash(hm,dl);
        return listCopyCutOutHash(ll0,dl);
    }

    @SuppressWarnings("unchecked")
    private LinkedList listCopyCutOutHash(Object ll0, LinkedHashMap dl){
        LinkedList ll=findList(ll0,"hash");
        if(ll==null) return null;
        LinkedList r=new LinkedList();
        int i=0;
        for(Object o: ll){
            if(o instanceof LinkedHashMap) r.add(copyCutOutHash((LinkedHashMap)o,dl));
            else r.add(o);
        i++; }
        return r;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap copyCutOutHash(LinkedHashMap<String,Object> hm, LinkedHashMap dl){
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            Object d=(dl!=null)? dl.get(k): null;
            if(d!=null && d instanceof String && (((String)d).equals("*") || o.equals(d))) continue;
            r.put(k, copyCutOutObject(o,d));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object copyCutOutList(LinkedList ll, Object d){
        LinkedList r=new LinkedList();
        for(Object o: ll){
            if(d!=null && d instanceof String && (((String)d).equals("*") || o.equals(d))) continue;
            if(d!=null && d instanceof LinkedList && (((LinkedList)d).contains(o))) continue;
            r.add(copyCutOutObject(o,null));
        }
        return r;
    }

    // ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Object copyWithMoreHash(Object ll0, LinkedHashMap ha){
        if(ll0==null) return null;
        LinkedHashMap hm=findHash(ll0);
        if(hm!=null) return copyWithMoreHash(hm,ha);
        return null; // listCopyWithMoreHash(ll0,ha);
    }

    @SuppressWarnings("unchecked")
    private Object copyWithMoreHash(LinkedHashMap<String,Object> hm, LinkedHashMap<String,Object> ha){
        boolean spawned=false;
        LinkedHashMap r=new LinkedHashMap();
        for(Map.Entry<String,Object> entry: hm.entrySet()){
            String k=entry.getKey();
            Object o=entry.getValue();
            Object a=(ha!=null)? ha.get(k): null;
            if(k.equals("UID")){ if(o.equals("new")) spawned=true; }
            else r.put(k, copyWithMoreObject(o,a));
        }
        if(ha!=null) for(Map.Entry<String,Object> entry: ha.entrySet()){
            String k=entry.getKey();
            Object a=entry.getValue();
            if(hm.get(k)!=null) continue;
            if(k.equals("UID")){ if(a.equals("new")) spawned=true; }
            else r.put(k, copyWithMoreObject(a,null));
        }
        if(spawned) try{ return spawn(getClass().newInstance().construct(r)); } catch(Throwable t){ t.printStackTrace(); }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Object copyWithMoreObject(Object o, Object a){
        if(o==null) return null;
        if(o instanceof String)  return (a!=null? list(copyFindObject(o),copyFindObject(a)): copyFindObject(o));
        if(o instanceof Number)  return (a!=null? list(o,copyFindObject(a)): o);
        if(o instanceof Boolean) return (a!=null? list(o,copyFindObject(a)): o);
        if(o instanceof LinkedHashMap) return copyWithMoreHash(((LinkedHashMap)o),(a!=null && a instanceof LinkedHashMap)? (LinkedHashMap)a: null);
        if(o instanceof LinkedList)    return copyWithMoreList(((LinkedList)o),a);
        return o;
    }

    @SuppressWarnings("unchecked")
    private LinkedList copyWithMoreList(LinkedList ll, Object a){
        LinkedList r=new LinkedList();
        for(Object o: ll) r.add(copyWithMoreObject(o,null));
        if(a==null) return r;
        if(a instanceof String)        r.add(copyFindObject(a));
        if(a instanceof Number)        r.add(a);
        if(a instanceof Boolean)       r.add(a);
        if(a instanceof LinkedHashMap) r.add(copyWithMoreHash(((LinkedHashMap)a),null));
        if(a instanceof LinkedList)    r.addAll(copyWithMoreList(((LinkedList)a),null));
        return r;
    }
}


