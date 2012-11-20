
package netmash.lib;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.*;

import netmash.lib.JSON;

/** TestJSON: test the JSON class. */
@SuppressWarnings("unchecked")
public class TestJSON {

    static public void main(String[] args){

        System.out.println("---TestJSON---------------");

        try{

        System.out.println("---Test-1------------------");

        {

        String funkychars = JSON.replaceEscapableChars("\"quote\" 'quote' ")+"\\\"\\/\\b\\f\\n\\r\\t\\\\\\\"\\u00a3\u00a3";
        String funkycharsout="\"quote\" 'quote' \"/\b\f\n\r\t\\\"\u00a3\u00a3";

        JSON m=new JSON(
            " \t\n \n { \"kernel\": { \"modules\": {              \"cache\": \"netmash.cache.JSONCache\",\n"+
            "                                            \"http\": \"netmash.drivers.HTTP\",\n"+
            "                                            \"logic\": \"netmash.drivers.TestDriver\" } },\n"+
            "                   \"modules\":  { \"cache\": { \"funky\": \""+funkychars+"\"},\n"+
            "                                 \"http\": { \"port\": 8080 },\n"+
            "                                 \"logic\": [ true, false, null, true, false, null, \"stringnospaces\", \" string with  spaces \" ],\n"+
            "                                 \"more\": [ true, 35392743408672770, -2147483649, 2147483648, -2147483648, 2147483647, null, true, false, null ] \n"+
            "                   }\n"+
            "      }\n");

        System.out.println(m);
        m=new JSON(m.toString());
        System.out.println(m);
        m=new JSON(m.toString("\"extra\": 33, "));
        System.out.println(m);
        System.out.println(m.toString(33));
        System.out.println(m.toString(true));

        String funky = m.stringPath("modules:cache:funky");
        assert funky.equals(funkycharsout): "funky should be [\"quote\" 'quote' \\\"/\\b\\f\\n\\r\\t\\\\\\\"\u00a3\u00a3], but it's ["+JSON.replaceEscapableChars(funky)+"]";

        String portstr = m.stringPath("modules:http:port");
        assert portstr==null: "port should be null, but it's "+portstr;

        int port = m.intPath("modules:http:port");
        assert port==8080: "port should be 8080, but it's "+port;

        LinkedHashMap<String,String> modulenames = m.hashPath("kernel:modules");
        assert "netmash.cache.JSONCache".equals(modulenames.get("cache")): "kernel:modules:cache should be netmash.cache.JSONCache, but it's "+
                                                                                                modulenames.get("cache");
        assert modulenames instanceof LinkedHashMap: "kernel:modules should be ordered hash";

        }

        System.out.println("---Test-2------------------");

        {

        JSON m = new JSON(new File("./src/server/vm1/netmashconfig.db"),true);
        System.out.println(m);

        int port = m.intPath("network:port");
        assert port==8081: "port should be 8081, but it's "+port;

        boolean log = m.boolPath("network:log");
        assert !log: "log should be true, but it's false";

        int threadpool = m.intPath("kernel:threadpool");
        assert threadpool == 33: "kernel:threadpool should be 33, but it's "+ threadpool;

        LinkedHashMap<String,String> kernel =  m.hashPath("kernel");
        assert kernel instanceof LinkedHashMap: "kernel should be ordered hash";

        }

        System.out.println("---Test-3------------------");

        {

        JSON m = new JSON("{ \"a\": \"b\", \"l\": \"m\", \"x\": [ \"y\", \"z\" ] }");
        System.out.println(m);
        JSON n = new JSON("{ \"c\": \"d\", \"l\": \"n\", \"o\": [ \"p\", \"q\" ] }");
        System.out.println(n);
        m.mergeWith(n);

        String expected = "{  \"a\": \"b\", \"l\": \"n\", \"x\": [ \"y\", \"z\" ], \"c\": \"d\", \"o\": [ \"p\", \"q\" ] }";
        assert m.toString(100).equals(expected): "merge failed: \n"+m.toString(100)+" not \n"+expected;

        }

        System.out.println("---Test-1-(Sumer)----------");

        {

        String funkychars = JSON.replaceEscapableChars("\"quote\" 'quote' ")+"\\\"\\/\\b\\f\\n\\r\\t\\\\\\\"\\u00a3\u00a3";
        String funkycharsout="\"quote\" 'quote' \"/\b\f\n\r\t\\\"\u00a3\u00a3";

        JSON m=new JSON(
            " \t\n \n { kernel: { modules: {  cache: netmash.cache.JSONCache\n"+
            "                                 http: netmash.drivers.HTTP\n"+
            "                                 logic: netmash.drivers.TestDriver } }\n"+
            "                   modules:  { cache: { funky: \""+funkychars+"\" }\n"+
            "                               http: { port: 8080 }\n"+
            "                               logic: true false { foo: null } ( true false ( ( null stringnospaces ) ) \"string with  spaces\" )\n"+
            "                               bits: \"string with  (double ) spaces:\" b: c \n"+
            "                               more: true ( 35392743408672770 { a: -2147483649 b: 2147483648 c: ( -2147483648 2147483647 y: ) x } false ) null\n"+
            "                   }\n"+
            "      }\n", true);

        System.out.println(m);
        m=new JSON(m.toString());
        System.out.println(m);
        m=new JSON(m.toString("\"extra\": 33, "));
        System.out.println(m);
        System.out.println(m.toString(33));
        System.out.println(m.toString(true));

        String funky = m.stringPath("modules:cache:funky");
        assert funky.equals(funkycharsout): "funky should be [\"quote\" 'quote' \\\"/\\b\\f\\n\\r\\t\\\\\\\"\u00a3\u00a3], but it's ["+JSON.replaceEscapableChars(funky)+"]";

        String portstr = m.stringPath("modules:http:port");
        assert portstr==null: "port should be null, but it's "+portstr;

        int port = m.intPath("modules:0:http:port:0");
        assert port==8080: "port should be 8080, but it's "+port;

        LinkedHashMap<String,String> modulenames = m.hashPath("kernel:modules:0");
        assert "netmash.cache.JSONCache".equals(modulenames.get("cache")): "kernel:modules:cache should be netmash.cache.JSONCache, but it's "+
                                                                                                modulenames.get("cache");
        assert modulenames instanceof LinkedHashMap: "kernel:modules should be ordered hash";

        }


        System.out.println("---All-Tests-Passed----------------------");

        } catch(Throwable t){
            t.printStackTrace();
        }
    }
}

