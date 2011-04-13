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

    static public String generateUID(){
        return ("uid-"+fourHex()+"-"+fourHex()+"-"+fourHex()+"-"+fourHex());
    }

    static private String fourHex(){
        String h = "000"+Integer.toHexString((int)(Math.random()*0x10000));
        return h.substring(h.length()-4);
    }

    static public boolean isUID(String uid){
        if(uid==null || uid.equals("")) return false;
        if(uid.startsWith("http://") ||
           uid.startsWith("uid-")       ) return true;
        return false;
    }

    static public String toURL(String uid2url){
        if(uid2url.startsWith("http://")) return uid2url;
        if(Kernel.config.stringPathN("network:host")==null) return uid2url;
        return "http://"+Kernel.config.stringPathN("network:host")+":"+
                         Kernel.config.intPathN(   "network:port")+
                         Kernel.config.stringPathN("network:pathprefix")+uid2url+".json";
    }

    static public String toUID(String url2uid){
        if(!url2uid.startsWith("http://"))         return url2uid;
        int s=url2uid.indexOf("uid-");  if(s== -1) return url2uid;
        int e=url2uid.indexOf(".json"); if(e== -1) return url2uid.substring(s);
        ;                                          return url2uid.substring(s,e);
    }

    static public String normaliseUID(String baseurl, String uid2url){
        if(uid2url==null) return null;
        String localpre="http://"+Kernel.config.stringPathN("network:host")+":"+
                                  Kernel.config.intPathN(   "network:port");
        if(baseurl!=null && !baseurl.startsWith(localpre)) uid2url=toURLfromBaseURL(baseurl, uid2url);
        return uid2url.startsWith(localpre)? toUID(uid2url): uid2url;
    }

    static public String toURLfromBaseURL(String baseurl, String uid2url){
        if(!baseurl.startsWith("http://")) return uid2url;
        if( uid2url.startsWith("http://")) return uid2url;
        int s=baseurl.indexOf("uid-");
        return baseurl.substring(0,s)+uid2url+".json";
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


