
package jungle.lib;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.*;

import jungle.lib.JSON;

/** TestJSON: test the JSON class. */
@SuppressWarnings("unchecked")
public class TestJSON {

    static public void main(String[] args){

        System.out.println("---TestJSON---------------");

        try{

        System.out.println("---Test-1------------------");

        {

        JSON m=new JSON(
            "{ \"kernel\": { \"modules\": {              \"cache\": \"jungle.cache.JSONCache\",\n"+
            "                                            \"http\": \"jungle.drivers.HTTP\",\n"+
            "                                            \"logic\": \"jungle.drivers.TestDriver\" } },\n"+
            "                   \"modules\":  { \"cache\": { \"funky\": \"\\\"\\/\\b\\f\\n\\r\\t\\\\\\\"\\u00a3\u00a3\"},\n"+
            "                                 \"http\": { \"port\": 8080 },\n"+
            "                                 \"logic\": [ true, false, null, true, false, null ],\n"+
            "                                 \"more\": [ true, 35392743408672770, -2147483649, 2147483648, -2147483648, 2147483647, null, true, false, null ] \n"+
            "                   }\n"+
            "      }\n");

        System.out.println(m);
        m=new JSON(m.toString());
        System.out.println(m);
        m=new JSON(m.toString("\"extra\": 33, "));
        System.out.println(m);
        System.out.println(m.toString(33));

        String funky = m.stringPath("modules:cache:funky");
        assert funky.equals("\"/\b\f\n\r\t\\\"\u00a3\u00a3"): "funky should be [\\\"/\\b\\f\\n\\r\\t\\\\\\\"\u00a3\u00a3], but it's ["+JSON.replaceEscapableChars(funky)+"]";

        String portstr = m.stringPath("modules:http:port");
        assert portstr.equals("8080"): "port should be 8080, but it's "+portstr;

        int port = m.intPath("modules:http:port");
        assert port==8080: "port should be 8080, but it's "+port;

        HashMap<String,String> modulenames = m.hashPath("kernel:modules");
        assert "jungle.cache.JSONCache".equals(modulenames.get("cache")): "kernel:modules:cache should be jungle.cache.JSONCache, but it's "+
                                                                                                modulenames.get("cache");
        assert modulenames instanceof LinkedHashMap: "kernel:modules should be ordered hash";

        }

        System.out.println("---Test-2------------------");

        {

        JSON m = new JSON(new File("./src/server/vm1/jungle-config.json"));
        System.out.println(m);

        int port = m.intPath("network:port");
        assert port==8081: "port should be 8081, but it's "+port;

        boolean log = m.boolPath("network:log");
        assert log: "log should be true, but it's false";

        int threadpool = m.intPath("kernel:threadpool");
        assert threadpool == 33: "kernel:threadpool should be 33, but it's "+ threadpool;

        HashMap<String,String> kernel =  m.hashPath("kernel");
        assert kernel instanceof LinkedHashMap: "kernel should be ordered hash";

        }


        System.out.println("---All-Tests-Passed----------------------");

        } catch(Throwable t){
            t.printStackTrace();
        }
    }
}

