
package netmash.lib;

import java.util.*;

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
        if(strings==null) return "";
        StringBuilder sb=new StringBuilder();
        for(Object o: strings){ sb.append(o.toString()); sb.append(joinwith); }
        String all=sb.toString();
        return all.substring(0,all.length()-joinwith.length());
    }

    // -------------------------------
}


