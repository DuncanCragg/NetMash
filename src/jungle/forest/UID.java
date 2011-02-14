package jungle.forest;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.regex.*;

import jungle.platform.*;

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

    static public String toURL(String uid){
        return "http://"+Kernel.config.stringPathN("network:host")+":"+
                         Kernel.config.intPathN("network:port")+
                         Kernel.config.stringPathN("network:pathprefix")+uid+".json";
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


