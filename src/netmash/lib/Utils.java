
package netmash.lib;

import java.util.*;
import java.util.regex.*;
import java.text.*;

import netmash.platform.*;

public class Utils{

    static public final boolean enableLogging=true;

    static public void log(Object o){
        log(enableLogging, o);
    }

    static public void log(boolean doit, Object o){
        if(!doit) return;
        String thread=Thread.currentThread().toString();
        System.out.println("---"+Kernel.config.stringPathN("name")+"---"+thread+"-----------\n"+o);
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

    static public double findNumberIn(Object o){
        if(o instanceof String){
            Date d = dateFormat.parse((String)o, new ParsePosition(0));
            if(d!=null) return d.getTime();
        }
        return tryDouble(o,0);
    }

    static public double tryDouble(Object o, double d){
        if(o instanceof Number) return ((Number)o).doubleValue();
        try{ return Double.parseDouble(o.toString()); } catch(NumberFormatException e){ return d; }
    }

    static public Object makeBestObject(String s){
        try{ return Double.parseDouble(s); } catch(NumberFormatException e){}
        if(s.toLowerCase().equals("true" )) return Boolean.valueOf(true);
        if(s.toLowerCase().equals("false")) return Boolean.valueOf(false);
        return s;
    }

    public static <T> Iterable<T> in(Iterable<T> l){ return l!=null? l: Collections.<T>emptyList(); }
}


