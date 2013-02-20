
package cyrus.lib;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import cyrus.platform.*;

public class Utils{

    static public final boolean enableLogging=true;
    static private long firstStamp=0;
    static private long lastStamp=0;

    static public void log(Object o){ log(enableLogging, o); }

    @SuppressWarnings("unchecked")
    static public void logXX(Object...args){ log(enableLogging, "xxxxxxx "+join(new LinkedList(Arrays.asList(args))," :: ")); }

    static public void logZero(Object o){ firstStamp=System.currentTimeMillis(); lastStamp=firstStamp; log(enableLogging, o); }

    static public void log(boolean doit, Object o){
        if(!doit) return;
        if(firstStamp==0){ firstStamp=System.currentTimeMillis(); lastStamp=firstStamp; }
        long stamp=System.currentTimeMillis();
        String thread=Thread.currentThread().toString();
        String name=Kernel.config==null? "": Kernel.config.stringPathN("name");
        System.out.println("---"+name+"---"+thread+"--- "+(stamp-firstStamp)+","+(stamp-lastStamp)+"\n"+o);
        lastStamp=stamp;
    }

    static public void whereAmI(Object message){
        try{ throw new Exception(); } catch(Exception e){ log(message+": "+Arrays.asList(e.getStackTrace())); }
    }

    // -------------------------------

    static public String join(LinkedList strings, String joinwith){
        if(strings==null || strings.size()==0) return "";
        StringBuilder sb=new StringBuilder();
        for(Object o: strings) sb.append(((o instanceof LinkedList)? join((LinkedList)o,joinwith):  findStringIn(o))+joinwith);
        String all=sb.toString();
        return all.substring(0,all.length()-joinwith.length());
    }

    static public String toString(Object o){
        if(o==null) return "null";
        if(o instanceof float[]){ float[] fa=(float[])o; String r="[ "; for(int i=0; i<fa.length; i++) r+=fa[i]+" "; return r+" ]"; }
        return o.toString();
    }

    // -------------------------------

    /** Matrix operations */

    @SuppressWarnings("unchecked")
    static public LinkedList vmdot(LinkedList v, LinkedList m){
        if(v==null || m==null) return null;
        LinkedList r=new LinkedList();
        for(Object o: m){
            if(!(o instanceof LinkedList)) return null;
            LinkedList w=(LinkedList)o;
            Double d=vvdot(v,w);
            if(d==null) return null;
            r.add(d);
        }
        return r;
    }

    static public Double vvdot(LinkedList v, LinkedList w){
        if(w.size()!=v.size()) return null;
        double r=0;
        int i=0; for(Object p: v){ Object q=w.get(i);
            Double d1=tryDouble(p);
            Double d2=tryDouble(q);
            if(d1==null || d2==null) return null;
            r+=d1*d2;
        i++; }
        return r;
    }

    @SuppressWarnings("unchecked")
    static public LinkedList vvadd(LinkedList a, LinkedList b){
        if(a==null) return b; if(b==null) return a;
        int as=a.size(), bs=b.size();
        if(as==0) return b; if(bs==0) return a;
        boolean abigger=(as>bs);
        LinkedList c=abigger? a: b;
        LinkedList d=abigger? b: a;
        int       ds=abigger? bs: as;
        LinkedList r=new LinkedList();
        int i=0; for(Object o: c){
            Object p=(i< ds)? d.get(i): Double.valueOf(0);
            Double d1=tryDouble(o);
            Double d2=tryDouble(p);
            if(d1==null || d2==null) return null;
            r.add(Double.valueOf(d1+d2));
        i++; }
        return r;
    }

    @SuppressWarnings("unchecked")
    static public LinkedList vsdiv(LinkedList v, double s){
        if(s==0) return v;
        LinkedList r=new LinkedList();
        for(Object o: v){
            Double d=tryDouble(o);
            if(d==null) return null;
            r.add(Double.valueOf(d/s));
        }
        return r;
    }

    static public Boolean withinOf(double r, LinkedList a, LinkedList b){
        return vvdist(a,b)<r;
    }

    static public Double vvdist(LinkedList a, LinkedList b){
        if(a==null) a=new LinkedList();
        if(b==null) b=new LinkedList();
        int as=a.size(), bs=b.size();
        boolean abigger=(as>bs);
        LinkedList c=abigger? a: b;
        LinkedList d=abigger? b: a;
        int       ds=abigger? bs: as;
        double r=0;
        int i=0; for(Object o: c){
            Object p=(i< ds)? d.get(i): Double.valueOf(0);
            double m=tryDouble(o,0)-tryDouble(p,0);
            r+=m*m;
        i++; }
        return Math.sqrt(r);
    }

    @SuppressWarnings("unchecked")
    static public LinkedList listWith(LinkedList a, LinkedList b){
        LinkedList r=(a==null)? new LinkedList(): new LinkedList(a);
        if(b!=null) for(Object o: b) if(!r.contains(o)) r.add(o);
        return r;
    }

    // -------------------------------

    /** Construct a list utility. */
    @SuppressWarnings("unchecked")
    static public LinkedList list(Object...args){
        return new LinkedList(Arrays.asList(args));
    }

    /** Construct a hash utility. hash("a","b","c","d")={"a":"b", "c":"d"} */
    @SuppressWarnings("unchecked")
    static public LinkedHashMap hash(Object...args){
        return hash(new LinkedHashMap(), args);
    }

    /** Construct a hash utility. As above but pass in hash to fill. */
    @SuppressWarnings("unchecked")
    static public LinkedHashMap hash(LinkedHashMap hm, Object...args){
        int i=0;
        Object tag=null;
        for(Object tagorval: args){
            i++;
            if(i%2==1)  tag=tagorval;
            else hm.put(tag,tagorval);
        }
        return hm;
    }

    /** Construct a hash utility. deephash("x","a","b","c")={"a":{"b":{"c":"x"}}}
                                  deephash("x","a:b:c")={"a":{"b":{"c":"x"}}} */
    @SuppressWarnings("unchecked")
    static public LinkedHashMap deephash(Object val, Object...path){
        if(path.length >0 && path[0] instanceof String && path[0].toString().indexOf(":")!= -1) path=path[0].toString().split(":");
        LinkedHashMap hm = new LinkedHashMap();
        if(path.length==0) return hm;
        if(path.length==1) hm.put(path[0], val);
        else               hm.put(path[0], deephash(val, copyOfRange(path,1,path.length)));
        return hm;
    }

    static public LinkedHashMap style(Object...args){ return hash(hash("is","style"), args); }

    static public Object[] copyOfRange(Object[] a, int start, int end){
        Object[] r = new Object[end-start];
        System.arraycopy(a, start, r, 0, end-start);
        return r;
    }

    static public String minFromString(String a, String b){
        if(a==null || a.length()==0) return b;
        if(b==null || b.length()==0) return a;
        return findNumberIn(a) < findNumberIn(b)? a: b;
    }

    static public String maxFromString(String a, String b){
        if(a==null || a.length()==0) return b;
        if(b==null || b.length()==0) return a;
        return findNumberIn(a) > findNumberIn(b)? a: b;
    }

    static SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static public String getStringFrom(LinkedHashMap hm, String tag){
        return findStringIn(hm.get(tag));
    }

    static public boolean getBooleanFrom(LinkedHashMap hm, String tag){
        return findBooleanIn(hm.get(tag));
    }

    static public LinkedList findListIn(Object o){
        if(o==null) return null;
        if(o instanceof LinkedList) return (LinkedList)o;
        return list(o);
    }

    static public LinkedHashMap findHashIn(Object o){
        if(o==null) return null;
        if(o instanceof LinkedHashMap) return (LinkedHashMap)o;
        return findHashIn(getAnySingle(o));
    }

    static public String findStringIn(Object o){
        if(o==null) return null;
        if(o instanceof String) return (String)o;
        if(o instanceof Number) return toNicerString((Number)o);
        return o.toString();
    }

    static public boolean findBooleanIn(Object o){
        if(o==null) return false;
        if(o instanceof Boolean) return (Boolean)o;
        if(o instanceof String){
            String s=(String)o;
            if(s.toLowerCase().equals("true" )) return Boolean.valueOf(true);
            if(s.toLowerCase().equals("false")) return Boolean.valueOf(false);
        }
        return false;
    }

    static public boolean isBoolean(Object o){
        if(o==null) return false;
        if(o instanceof Boolean) return true;
        if(o instanceof String){
            String s=(String)o;
            if(s.toLowerCase().equals("true" )) return true;
            if(s.toLowerCase().equals("false")) return true;
        }
        return false;
    }

    static public Number findANumberIn(Object o){
        if(o==null) return null;
        if(o instanceof String){
            Date d = dateFormat.parse((String)o, new ParsePosition(0));
            if(d!=null) return Double.valueOf(d.getTime());
        }
        return tryDouble(o);
    }

    static public double findNumberIn(Object o){
        if(o==null) return 0;
        if(o instanceof String){
            Date d = dateFormat.parse((String)o, new ParsePosition(0));
            if(d!=null) return d.getTime();
        }
        return tryDouble(o,0);
    }

    static public boolean isNumber(Object o){
        if(o==null) return false;
        if(o instanceof Number) return true;
        String s=o.toString();
        if(s.length()==0) return false;
        try{
            if(dateFormat.parse(s, new ParsePosition(0))!=null) return true;
            Double.parseDouble(s);
            return true;
        } catch(NumberFormatException e){ return false; }
    }

    static public double tryDouble(Object o, double d){
        Double n=tryDouble(o);
        return n!=null? n: d;
    }

    static public Double tryDouble(Object o){
        if(o==null) return null;
        if(o instanceof Number) return ((Number)o).doubleValue();
        try{ return Double.parseDouble(o.toString()); } catch(NumberFormatException e){ return null; }
    }

    static public Object getAnySingle(Object o){
        if(!(o instanceof LinkedList)) return null;
        LinkedList ll=(LinkedList)o;
        return (ll.size()==1)? ll.get(0): null;
    }

    static public Object makeBestObject(String s){
        if(s==null) return null;
        try{ return Double.parseDouble(s); } catch(NumberFormatException e){}
        if(s.toLowerCase().equals("true" )) return Boolean.valueOf(true);
        if(s.toLowerCase().equals("false")) return Boolean.valueOf(false);
        return s;
    }

    static public String toNicerString(Number n){
        String r=n.toString();
        if(r.endsWith(".0")) return r.substring(0,r.length()-2);
        return r;
    }

    static public String setToListString(Iterable<String> set){ return setToListString(set,false); }

    static public String setToListString(Iterable<String> set, boolean cyrus){
        String q=cyrus? "": "\"";
        String c=cyrus? "": ",";
        String s=cyrus? "(": "[";
        String e=cyrus? ")": "]";
        Iterator<String> i = set.iterator();
        if(!i.hasNext()) return cyrus? "( )": "[ ]";
        String r = s;
        do{ r+=" "+q+i.next()+q+c; }while(i.hasNext());
        if(!cyrus) r=r.substring(0, r.length()-1);
        r+=" "+e;
        return r;
    }

    static public <T> Iterable<T> in(Iterable<T> l){ return l!=null? l: Collections.<T>emptyList(); }

    static public double random(double lo, double hi){
        double x=Math.random();
        return (int)(lo+x*(hi+0.5-lo));
    }

    static public double clamp(double lo, double hi, double x){
        if(x>hi) return hi;
        if(x<lo) return lo;
        return x;
    }

    @SuppressWarnings("unchecked")
    static public LinkedList expandRange(double from, double to){
        LinkedList r=new LinkedList();
        if(from<to) for(int i=(int)from; i<=(int)to; i++) r.add(i);
        else        for(int i=(int)from; i>=(int)to; i--) r.add(i);
        return r;
    }

    static public int sizeOf(LinkedList ll){
        if(ll==null) return 0;
        return ll.size();
    }

    static public Double sumAll(LinkedList ll){
        if(ll==null || ll.isEmpty()) return Double.valueOf(0);
        double d=0;
        for(Object o: ll){ Double n=tryDouble(o); if(n==null) return null; d+=n; }
        return d;
    }

    static public String capitaliseAndSpace(String s){
        String[] chunks=s.split("-");
        String r="";
        for(int i=0; i<chunks.length; i++){
            String chunk=chunks[i];
            r+=(i==0? "":" ")+Character.toUpperCase(chunk.charAt(0))+chunk.substring(1);
        }
        return r;
    }
}


