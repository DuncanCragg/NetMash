
package netmash.lib;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import netmash.platform.*;

public class Utils{

    static public final boolean enableLogging=true;
    static private long firstStamp=0;
    static private long lastStamp=0;

    static public void log(Object o){ log(enableLogging, o); }

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
        for(Object o: strings){ sb.append(o.toString()); sb.append(joinwith); }
        String all=sb.toString();
        return all.substring(0,all.length()-joinwith.length());
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

    static public LinkedList findListIn(Object o){
        if(o==null) return null;
        if(o instanceof LinkedList) return (LinkedList)o;
        return list(o);
    }

    static public String findStringIn(Object o){
        if(o==null) return null;
        if(o instanceof String) return (String)o;
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

    static public double findNumberIn(Object o){
        if(o==null) return 0;
        if(o instanceof String){
            Date d = dateFormat.parse((String)o, new ParsePosition(0));
            if(d!=null) return d.getTime();
        }
        return tryDouble(o,0);
    }

    static public double tryDouble(Object o, double d){
        if(o==null) return d;
        if(o instanceof Number) return ((Number)o).doubleValue();
        try{ return Double.parseDouble(o.toString()); } catch(NumberFormatException e){ return d; }
    }

    static public Object makeBestObject(String s){
        if(s==null) return null;
        try{ return Double.parseDouble(s); } catch(NumberFormatException e){}
        if(s.toLowerCase().equals("true" )) return Boolean.valueOf(true);
        if(s.toLowerCase().equals("false")) return Boolean.valueOf(false);
        return s;
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

    static public double sumAll(LinkedList ll){
        if(ll==null || ll.isEmpty()) return 0;
        double d=0;
        for(Object o: ll) d+=tryDouble(o,0);
        return d;
    }

    static public String getStringFrom(LinkedHashMap hm, String tag){
        Object o=hm.get(tag);
        if(o==null) return null;
        return o.toString();
    }
}


