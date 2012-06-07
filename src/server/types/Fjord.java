package server.types;

import java.util.*;
import java.util.regex.*;
import netmash.forest.WebObject;

/** Fjord Language.
  * .
  */
public class Fjord extends WebObject {

    public Fjord(){}

    public Fjord(String s){ super(s); }

    public Fjord(LinkedHashMap hm){ super(hm); }

    public void evaluate(){

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
    }

    private void runRule(int r){
        if(alerted().size()==0) runRule(r,null);
        else for(String alerted: alerted()) runRule(r,alerted);
    }

    private boolean extralogging = false;

    @SuppressWarnings("unchecked")
    private void runRule(int r, String alerted){
        if(extralogging) log("Running rule \"When "+content(String.format("%%rules:%d:when", r))+"\"");

        LinkedHashMap<String,Object> rule=contentHash(String.format("%%rules:%d:#", r));

        contentTemp("%alerted", alerted);

        boolean ok=scanRuleHash(rule, "");

        if(ok) log("Rule fired: \"When "+content(String.format("%%rules:%d:when", r))+"\"");
        if(extralogging) log("==========\nscanRuleHash="+ok+"\n"+rule+"\n"+contentHash("#")+"===========\n");

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
                if(pk.equals("is")) continue;
                if(pk.equals("when")) continue;
                if(pk.equals("watching")) continue;
            }
            Object v=entry.getValue();
            if(v instanceof String){
                if(!scanString((String)v, pk)) return false;
            }
            else
            if(v instanceof Boolean){
                if(!scanBoolean((Boolean)v, pk)) return false;
            }
            else
            if(v instanceof LinkedHashMap){
                if(!scanRuleHash((LinkedHashMap<String,Object>)v, pk+":")) return false;
            }
            else
            if(v instanceof LinkedList){
                if(!scanRuleList((LinkedList)v, pk)) return false;
            }
            else{ log("oh noes "+v); return false; }
        }
        return true;
    }

    private boolean scanRuleList(LinkedList list, String pk){
        for(Object v: list){
            if(v instanceof String){
                if(!scanString((String)v, pk)) return false;
            }
            else
            if(v instanceof LinkedHashMap){
                if(!scanRuleHash((LinkedHashMap<String,Object>)v, pk+":")) return false;
            }
            else
            if(v instanceof LinkedList){
                if(!scanRuleList((LinkedList)v, pk)) return false;
            }
            else{ log("oh noes "+v); return false; }
        }
        return true;
    }

    private boolean scanString(String vs, String pk){
        if(vs.startsWith("<")){
            if(vs.startsWith("<#>")){
                if(contentSet(pk)) return false;
                String rhs=vs.substring(3);
                doRHS(pk,rhs);
            }
            else
            if(vs.startsWith("<>")){
                String[] rhsparts=vs.substring(2).split(";");
                for(int i=0; i<rhsparts.length; i++){
                    String rhs=rhsparts[i];
                    doRHS(pk,rhs);
                }
            }
            else{
                Matcher m = REWRITEPA.matcher(vs);
                if(!m.matches()){ if(!contentIsOrListContains(pk,vs)) return false; }
                else{
                    String lhs = m.group(1);
                    if(!doLHS(pk,lhs)) return false;
                    String rhs = m.group(2);
                    doRHS(pk,rhs);
                }
            }
        }
        else if(!contentIsOrListContains(pk,vs)) return false;
        return true;
    }

    private boolean scanBoolean(Boolean vb, String pk){
        return contentBool(pk)==vb;
    }

    private boolean doLHS(String pk, String lhs){ try{
        if(lhs.equals("{}"))      return  contentHash(pk)!=null;
        if(lhs.equals("$:"))      return  content(pk).equals(uid);
        if(lhs.startsWith( "$:")) return  contentObject(pk).equals(contentObject(lhs.substring(2)));
        if(lhs.startsWith("!$:")) return !contentObject(pk).equals(contentObject(lhs.substring(3)));
        if(evalFunction(pk,lhs,true)) return true;
        return contentIsString(pk,lhs) || contentListContains(pk,lhs);
    } catch(Throwable t){ log(t); return false; } }

    private void doRHS(String pk, String rhs){ try{
        if(rhs.length()==0) return;
        if(pk.equals("%notifying")){
            if(rhs.startsWith("has($:"))     notifying(content(rhs.substring(6,rhs.length()-1)));
            if(rhs.startsWith("hasno($:")) unnotifying(content(rhs.substring(8,rhs.length()-1)));
            return;
        }
        if(rhs.startsWith("has($:"))   contentSetAdd(    pk, content(rhs.substring(6,rhs.length()-1)));
        else
        if(rhs.startsWith("hasno($:")) contentListRemove(pk, content(rhs.substring(8,rhs.length()-1)));
        else
        if(rhs.startsWith("has(%"))    contentSetAdd(    pk, content(rhs.substring(4,rhs.length()-1)));
        else
        if(rhs.startsWith("hasno(%"))  contentListRemove(pk, content(rhs.substring(6,rhs.length()-1)));
        else
        if(rhs.startsWith("has("))     contentSetAdd(    pk,         rhs.substring(4,rhs.length()-1));
        else
        if(rhs.startsWith("hasno("))   contentListRemove(pk,         rhs.substring(6,rhs.length()-1));
        else
        if(rhs.equals("%alerted")) content(pk,content(rhs));
        else
        if(rhs.equals("$:"))       content(pk,uid);
        else
        if(rhs.startsWith("$:"))   contentClone(pk, rhs.substring(2));
        else
        if(rhs.equals("{}"))       contentHash(pk, new LinkedHashMap());
        else
        if(evalFunction(pk,rhs,false));
        else
        if(rhs.equals("new") && pk.endsWith("%uid")){
            String basepath=pk.substring(0,pk.length()-5);
            content(basepath, spawn(getClass().newInstance().construct(contentHash(basepath))));
        }
        else content(pk,rhs);
    } catch(Throwable t){ log(t); } }

    @SuppressWarnings("unchecked")
    private boolean evalFunction(String pk, String function, boolean match){

        Matcher m = FUNCTIONPA.matcher(function);
        if(!m.matches()) return false;
        String   func = m.group(1);
        String[] args = m.group(2).split(",");

        if(func.equals("list")){
            LinkedList l=new LinkedList();
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                l.add(makeBestObject(arg));
            }
            if(match) return contentList(pk).equals(l);
            else      {      contentList(pk,l); return true; }
        }
        if(func.equals("prod")){
            double d=1.0;
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                d *= arg.startsWith("$:")? contentDouble(arg.substring(2)): Double.parseDouble(arg);
            }
            if(match) return contentDouble(pk)==d;
            else      {      contentDouble(pk,new Double(d)); return true; }
        }
        if(func.equals("lt")){
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                double v=arg.startsWith("$:")? contentDouble(arg.substring(2)): Double.parseDouble(arg);
                if(match) return contentDouble(pk) < v;
                else      return true;
            }
            return !match;
        }
        if(match) return false;
        else      {      content(pk,function); return true; }
    }

    private Object makeBestObject(String s){
       try{ return Double.parseDouble(s); } catch(NumberFormatException e){}
       if(s.toLowerCase().equals("true" )) return new Boolean(true);
       if(s.toLowerCase().equals("false")) return new Boolean(false);
       return s;
    }

// "<#>payment": { .. }
// two-phase

    // ----------------------------------------------------
}


