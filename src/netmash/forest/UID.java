package netmash.forest;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.regex.*;

import netmash.platform.*;

public class UID {

    // ----------------------------------------

    private String uid;

    public UID(){
        uid = generateUID();
    }

    public UID(String uid){
        this.uid=uid;
    }

    public String toString(){
        return uid;
    }

    // ----------------------------------------

    static public String generateCN(){
        return ("c-n-"+fourHex()+"-"+fourHex()+"-"+fourHex()+"-"+fourHex());
    }

    static public String generateUID(){
        return ("uid-"+fourHex()+"-"+fourHex()+"-"+fourHex()+"-"+fourHex());
    }

    static private String fourHex(){
        String h = "000"+Integer.toHexString((int)(Math.random()*0x10000));
        return h.substring(h.length()-4);
    }

    static public boolean isUID(Object o){
        if(!(o instanceof String)) return false;
        String uid=(String)o;
        if(uid==null || uid.equals("")) return false;
        if(uid.startsWith("http://") ||
           uid.startsWith("uid-")       ) return true;
        return false;
    }

    static public String  URLPATHRE=null;
    static public Pattern URLPATHPA=null;
    static public Pattern URLPATHPA(){
        if(URLPATHRE==null){
            URLPATHRE = Kernel.config.stringPathN("network:pathprefix")+"((uid-[-0-9a-f]+).json|(c-n-[-0-9a-f]+))$";
            URLPATHPA = Pattern.compile(URLPATHRE);
        }
        return URLPATHPA;
    }

    static public String toURL(String uid2url){
        if(uid2url.startsWith("http://")) return uid2url;
        if(notVisible()) return uid2url;
        boolean dotJSON=uid2url.startsWith("uid-");
        return localPre()+Kernel.config.stringPathN("network:pathprefix")+uid2url+(dotJSON? ".json": "");
    }

    static public String toUID(String url2uid){
        if(!url2uid.startsWith("http://"))         return url2uid;
        int s=url2uid.indexOf("uid-");  if(s== -1) return url2uid;
        int e=url2uid.indexOf(".json"); if(e== -1) return url2uid.substring(s);
        ;                                          return url2uid.substring(s,e);
    }

    static public String normaliseUID(String baseurl, String uid2url){
        if(uid2url==null) return null;
        if(notVisible()) return toURLfromBaseURL(baseurl, uid2url);
        if(baseurl!=null && !baseurl.startsWith(localPre())) uid2url=toURLfromBaseURL(baseurl, uid2url);
        return uid2url.startsWith(localPre())? toUID(uid2url): uid2url;
    }

    static public String toUIDifLocal(String url2uid){
        if(notVisible()) return url2uid;
        return url2uid.startsWith(localPre())? toUID(url2uid): url2uid;
    }

    static public String toURLfromBaseURL(String baseurl, String uid2url){
        if(baseurl==null)                  return uid2url;
        if(!baseurl.startsWith("http://")) return uid2url;
        if(!uid2url.startsWith("uid-"))    return uid2url;
        int s=baseurl.indexOf("uid-");
        return baseurl.substring(0,s)+uid2url+".json";
    }

    static private String localpre=null;
    static Boolean notvisible=null;

    static public String localPre(){
        if(localpre==null){
            localpre="http://"+Kernel.config.stringPathN("network:host")+":"+
                               Kernel.config.intPathN(   "network:port");
        }
        return localpre;
    }

    static boolean notVisible(){
        if(notvisible==null){
            notvisible=!Kernel.config.isAtPathN("network:host");
        }
        return notvisible;
    }

    // ----------------------------------------

    static public void main(String[] args){

        UID uid = new UID("uid-1");
        System.out.println(uid);

        assert !isUID(null);
        assert !isUID("");
        assert  isUID("http://foo");
        assert  isUID("uid-12-12");

        for(int i=1; i<=10; i++) System.out.println(new UID());
    }

    // ----------------------------------------
}


