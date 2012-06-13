package netmash.forest;

import java.util.*;
import java.util.regex.*;
import java.text.*;

/** Fjord Language.
  * .
  */
public class Fjord extends WebObject {

    private boolean extralogging = true;

    public Fjord(){}

    public Fjord(String s){ super(s); }

    public Fjord(LinkedHashMap hm){ super(hm); }

    public void evaluate(){

        if(extralogging) log("Running Fjord on "+contentHash("#"));
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

    @SuppressWarnings("unchecked")
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
        if(lhs.equals("$:"))      return  content(pk).equals(uid) || content(pk).equals(UID.toURL(uid));
        if(lhs.startsWith( "$:")) return  contentObject(pk).equals(contentObject(lhs.substring(2)));
        if(lhs.startsWith("!$:")) return !contentObject(pk).equals(contentObject(lhs.substring(3)));
        if(evalFunction(pk,lhs,true)) return true;
        return contentIsString(pk,lhs) || contentListContains(pk,lhs);
    } catch(Throwable t){ log(pk); log(lhs); t.printStackTrace(); return false; } }

    private void doRHS(String pk, String rhs){ try{
        if(rhs.length()==0) return;
        if(pk.equals("%notifying")){
            if(rhs.startsWith("has($:"))     notifying(content(rhs.substring(6,rhs.length()-1)));
            if(rhs.startsWith("hasno($:")) unnotifying(content(rhs.substring(8,rhs.length()-1)));
            return;
        }
        if(rhs.startsWith("has($:"))   contentSetAdd(    pk, contentObject(rhs.substring(6,rhs.length()-1)));
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
        if(rhs.startsWith("$:"))   getAllContent(pk, rhs.substring(2));
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
    } catch(Throwable t){ log(pk); log(rhs); t.printStackTrace(); } }

    private void getAllContent(String pk, String source){
        if(!contentClone(pk,source)){ LinkedList<String> l=contentAll(source); if(l!=null) contentList(pk,l); }
    }

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
        if(func.equals("join")){
            String s="";
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                if(arg.startsWith("$:")) for(String e: in(contentAll(arg.substring(2)))) s+=e+" ";
                else s+=arg+" ";
            }
            if(match) return content(pk).equals(s);
            else      {      content(pk,s); return true; }
        }
        if(func.equals("sum")){
            double d=0.0;
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                d += arg.startsWith("$:")? contentDouble(arg.substring(2)): Double.parseDouble(arg);
            }
            if(match) return contentDouble(pk)==d;
            else      {      contentDouble(pk,new Double(d)); return true; }
        }
        if(func.equals("prod")){
            double d=1.0;
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                d *= arg.startsWith("$:")? contentDouble(arg.substring(2)): Double.parseDouble(arg);
            }
            if(match) return contentDouble(pk)==d;
            else      {      contentDouble(pk,new Double(d)); return true; }
        }
        if(func.equals("div")){
            String arg=args[0].trim();
            double d=arg.startsWith("$:")? contentDouble(arg.substring(2)): Double.parseDouble(arg);
            for(int i=1; i<args.length; i++){ arg=args[i].trim();
                d /= arg.startsWith("$:")? contentDouble(arg.substring(2)): Double.parseDouble(arg);
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
        if(func.equals("min")){
            String min="";
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                if(arg.startsWith("$:")){
                    for(String s: in(contentAll(arg.substring(2)))) min=minFromString(min,s);
                }
            }
            if(match) return false;
            else{ content(pk,min); return true; }
        }
        if(func.equals("max")){
            String max="";
            for(int i=0; i<args.length; i++){ String arg=args[i].trim();
                if(arg.startsWith("$:")){
                    for(String s: in(contentAll(arg.substring(2)))) max=maxFromString(max,s);
                }
            }
            if(match) return false;
            else{ content(pk,max); return true; }
        }
        if(match) return false;
        else{ content(pk,function); return true; }
    }

    private String minFromString(String a, String b){
        if(a==null || a.length()==0) return b;
        if(b==null || b.length()==0) return a;
        return findNumberIn(a) < findNumberIn(b)? a: b;
    }

    private String maxFromString(String a, String b){
        if(a==null || a.length()==0) return b;
        if(b==null || b.length()==0) return a;
        return findNumberIn(a) > findNumberIn(b)? a: b;
    }

    static SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private double findNumberIn(String s){
        double n=0;
        Date d = dateFormat.parse(s, new ParsePosition(0));
        if(d!=null) n=d.getTime();
        else try{ n=Double.parseDouble(s); } catch(NumberFormatException e){ }
        return n;
    }

    private Object makeBestObject(String s){
        try{ return Double.parseDouble(s); } catch(NumberFormatException e){}
        if(s.toLowerCase().equals("true" )) return new Boolean(true);
        if(s.toLowerCase().equals("false")) return new Boolean(false);
        return s;
    }

    public static <T> Iterable<T> in(Iterable<T> l){ return l!=null? l: Collections.<T>emptyList(); }

    // ----------------------------------------------------
}


