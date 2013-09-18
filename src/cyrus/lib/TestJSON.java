
package cyrus.lib;

import java.util.*;
import java.io.*;
import java.nio.*;

import cyrus.lib.JSON;

import static cyrus.lib.Utils.*;

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
            " \t\n \n { \"kernel\": { \"modules\": {              \"cache\": \"cyrus.cache.JSONCache\",\n"+
            "                                            \"http\": \"cyrus.drivers.HTTP\",\n"+
            "                                            \"logic\": \"cyrus.drivers.TestDriver\" } },\n"+
            "                   \"modules\":  { \"cache\": { \"funky\": \""+funkychars+"\"},\n"+
            "                                 \"http\": { \"port\": 8080 },\n"+
            "                                 \"logic\": [ true, false, null, true, false, null, \"stringnospaces\", \" string with  spaces \" ],\n"+
            "                                 \"more\": [ true, 35392743408672770, -2147483649, 2147483648, [-2147483648, 2147483647, null, true], false, null ] \n"+
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
        assert "cyrus.cache.JSONCache".equals(modulenames.get("cache")): "kernel:modules:cache should be cyrus.cache.JSONCache, but it's "+
                                                                                                modulenames.get("cache");
        assert modulenames instanceof LinkedHashMap: "kernel:modules should be ordered hash";

        String liststring=""+m.listPath("modules:more:1..5");
        assert "[35392743408672770, -2147483649, 2147483648, [-2147483648, 2147483647, null, true], false]".equals(liststring): "sub-list "+liststring;

        liststring=""+m.listPath("modules:more:1..5:2..");
        assert "[2147483648, [-2147483648, 2147483647, null, true], false]".equals(liststring): "sub-list "+liststring;

        liststring=""+m.listPath("modules:more:1..5:2..:1:..2");
        assert "[-2147483648, 2147483647, null]".equals(liststring): "sub-list "+liststring;

        }

        System.out.println("---Test-2------------------");

        {

        JSON m = new JSON(new File("./src/server/vm1/cyrusconfig.db"),true);
        System.out.println(m);

        int port = m.intPath("network:port");
        assert port==8081: "network:port should be 8081, but it's "+port;

        boolean log = m.boolPath("network:log");
        assert log: "network:log should be true, but it's false";

        int threadpool = m.intPath("kernel:threadpool");
        assert threadpool == 33: "kernel:threadpool should be 33, but it's "+ threadpool;

        LinkedHashMap<String,String> kernel =  m.hashPath("kernel");
        assert kernel instanceof LinkedHashMap: "kernel should be ordered hash";

        }

        System.out.println("---Test-3------------------");

        {

        try{
            JSON m = new JSON("{ \"a\": \"b\", \"l\": \"m\", \"x\": [ \"y\", \"z\" ] } \n ");
            assert m.stringPath("x:1").equals("z");
            JSON n = new JSON("{ \"a\": \"b\", \"l\": \"m\", \"x\": [ \"y\", \"z\" ] } \n { } ");
            assert !n.stringPath("x:1").equals("z"): "should catch trailing chars";
        }catch(JSON.Syntax s){}

        JSON m = new JSON("{ \"a\": \"b\", \"l\": \"m\", \"x\": [ \"y\", \"z\" ] }");
        System.out.println(m);
        JSON n = new JSON("{ \"c\": \"d\", \"l\": \"n\", \"o\": [ \"p\", \"q\" ] }");
        System.out.println(n);
        m.mergeWith(n);

        String expected = "{ \"a\": \"b\", \"l\": \"n\", \"x\": [ \"y\", \"z\" ], \"c\": \"d\", \"o\": [ \"p\", \"q\" ] }";
        assert m.toString(100).equals(expected): "merge failed: \n"+m.toString(100)+" not \n"+expected;

        }

        System.out.println("---Test-4------------------");

        {

        JSON m = new JSON("{ \"a\": { \"b\": \"c\" }, \"l\": \"m\", \"x\": [ \"y\", \"z\" ] }");
        System.out.println(m);

        assert m.stringPath("x:0").equals("y");
        m.stringPath("x:0","yy");
        assert m.stringPath("x:0").equals("yy");

        assert m.hashPath("#").equals(hash("a",hash("b","c"), "l","m", "x",list("yy","z")));

        assert m.stringPath("x:0").equals("yy");
        m.stringPath("x:0","yyy");
        assert m.stringPath("x:0").equals("yyy");

        assert m.hashPath("#").equals(hash("a",hash("b","c"), "l","m", "x",list("yyy","z")));

        JSON n = new JSON("{ \"c\": \"d\", \"l\": \"n\", \"o\": [ \"p\", \"q\" ] }");
        System.out.println(n);
        m.mergeWith(n);

        assert m.hashPath("#").equals(hash("a",hash("b","c"), "l","n", "x",list("yyy","z"), "c","d", "o",list("p","q")));

        m.hashPath("a",hash("x","y"));
        System.out.println(m);
        assert m.hashPath("#").equals(hash("a",hash("x","y"), "l","n", "x",list("yyy","z"), "c","d", "o",list("p","q")));

        System.out.println(m);

        }

        System.out.println("---Test-1-(Cyrus)----------");

        {

        JSON m=new JSON("{ a: b  c: ( d )  e: (( f ))  g: ((( h ))) i: j k  l: ( m ) n ( o p )  q: ( r s ) t: ( u ( v ) ) }", true);
        String ms=m.toString(true);

        String expected="{\n"+
                        "  a: b\n"+
                        "  c: ( d )\n"+
                        "  e: ( ( f ) )\n"+
                        "  g: ( ( ( h ) ) )\n"+
                        "  i: j k\n"+
                        "  l: ( m ) n ( o p )\n"+
                        "  q: ( r s )\n"+
                        "  t: ( u ( v ) )\n"+
                        "}";
        assert ms.equals(expected): "nested listed not parsed right: expected:\n"+expected+"\nactual:\n"+m+"\n"+ms;

        JSON n=new JSON(ms, true);
        String ns=n.toString(true);

        assert ms.equals(ns): "failed to rountrip nested lists in Cyrus format properly: "+ms+"\n"+ns;

        }

        System.out.println("---Test-2-(Cyrus)----------");

        {

        String funkychars = JSON.replaceEscapableChars("\"quote\" 'quote' ")+"\\\"\\/\\b\\f\\n\\r\\t\\\\\\\"\\u00a3\u00a3";
        String funkycharsout="\"quote\" 'quote' \"/\b\f\n\r\t\\\"\u00a3\u00a3";

        JSON m=new JSON(
            " \t\n \n { kernel:{modules:{cache: cyrus.cache.JSONCache\n"+
            "                            http: cyrus.drivers.HTTP\n"+
            "                            @a:b:c: @d:e:f\n"+
            "                            logic: cyrus.drivers.TestDriver}}\n"+
            "           modules:{cache:{funky: \""+funkychars+"\"}\n"+
            "                    http:{port: 8080}\n"+
            "                    logic: true false{foo: null null}(true false((null stringnospaces))\"string with  spaces\")\n"+
            "                    bits: \"string with  (double ) spaces:\" b: (c \"d)e\")\n"+
            "                    more: true(35392743408672770{a: -2147483649 b: 2147483648 \"d:\" c:(-2147483648 2147483647 y: a:b 1.000)x}false)null\n"+
            "           }\n"+
            "      }\n", true);

        String expected=
            "{\n"+
            "  kernel: {\n"+
            "    modules: {\n"+
            "      cache: cyrus.cache.JSONCache\n"+
            "      http: cyrus.drivers.HTTP\n"+
            "      @a:b:c: @d:e:f\n"+
            "      logic: cyrus.drivers.TestDriver\n"+
            "    }\n"+
            "  }\n"+
            "  modules: {\n"+
            "    cache: { funky: \""+JSON.replaceEscapableChars(funkycharsout)+"\" }\n"+
            "    http: { port: 8080 }\n"+
            "    logic: \n"+
            "      true\n"+
            "      false\n"+
            "      { }\n"+
            "      ( true false ( ( stringnospaces ) ) \"string with  spaces\" )\n"+
            "    bits: \"string with  (double ) spaces:\"\n"+
            "    b: ( c \"d)e\" )\n"+
            "    more: \n"+
            "      true\n"+
            "      (\n"+
            "        35392743408672770\n"+
            "        {\n"+
            "          a: -2147483649\n"+
            "          b: 2147483648 \"d:\"\n"+
            "          c: \n"+
            "            ( -2147483648 2147483647 \"y:\" a:b 1 )\n"+
            "            x\n"+
            "        }\n"+
            "        false\n"+
            "      )\n"+
            "  }\n"+
            "}";

        m=new JSON(m.toString(true),true);
        assert m.toString(true).equals(expected): "parse and back wrong - expected:\n[["+expected+"]]\n\n[["+m.toString(true)+"]]";
        m=new JSON(m.toString());
        assert m.toString(true).equals(expected): "parse and back wrong - expected:\n[["+expected+"]]\n\n[["+m.toString(true)+"]]";
        m=new JSON(m.toString(true),true);
        assert m.toString(true).equals(expected): "parse and back wrong - expected:\n[["+expected+"]]\n\n[["+m.toString(true)+"]]";

        m=new JSON(m.toString("\"extra\": 33, "));
        System.out.println(m.toString(33));
        System.out.println(m.toString(true));

        String funky = m.stringPath("modules:cache:funky");
        assert funky.equals(funkycharsout): "funky should be [\"quote\" 'quote' \\\"/\\b\\f\\n\\r\\t\\\\\\\"\u00a3\u00a3], but it's ["+JSON.replaceEscapableChars(funky)+"]";

        String portstr = m.stringPath("modules:http:port");
        assert portstr==null: "port should be null, but it's "+portstr;

        int port = m.intPath("modules:0:http:port:0");
        assert port==8080: "port should be 8080, but it's "+port;

        LinkedHashMap<String,String> modulenames = m.hashPath("kernel:modules:0");
        assert "cyrus.cache.JSONCache".equals(modulenames.get("cache")): "kernel:modules:cache should be cyrus.cache.JSONCache, but it's "+
                                                                                                modulenames.get("cache");
        assert modulenames instanceof LinkedHashMap: "kernel:modules should be ordered hash";

        }


        System.out.println("---All-Tests-Passed----------------------");

        } catch(Throwable t){
            t.printStackTrace();
        }
    }
}

