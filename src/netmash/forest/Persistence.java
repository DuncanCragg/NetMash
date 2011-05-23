package netmash.forest;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.regex.*;

import netmash.platform.*;
import netmash.lib.JSON;

/** Persistence of WebObjects.
  * A NoSQL JSON in-memory Database!
  */
public class Persistence implements FileUser {

    // ----------------------------------------

    static public final Charset UTF8 = Charset.forName("UTF-8");

    public  FunctionalObserver funcobs;

    private File dbfile=null;
    private InputStream      topdbis=null;
    private FileOutputStream topdbos=null;
    private JSON netmashconfig=null;

    private ConcurrentHashMap<String,CharBuffer> jsoncache = new ConcurrentHashMap<String,CharBuffer>();
    private CopyOnWriteArraySet<WebObject>       syncable  = new CopyOnWriteArraySet<WebObject>();

    // ----------------------------------------

    public Persistence(InputStream topdbis, FileOutputStream topdbos){

        funcobs = FunctionalObserver.funcobs;
        this.topdbis = topdbis;
        this.topdbos = topdbos;

        String directory = Kernel.config.stringPathN("persist:directory");
        String db        = Kernel.config.stringPathN("persist:db");
        if(topdbis==null){
           dbfile = new File(directory+"/"+db);
           try{ Kernel.readFile(dbfile, this); }
           catch(Exception e){ FunctionalObserver.log("Persistence: Failure reading DB: "+e.getMessage()); }
           FunctionalObserver.log("Persistence: Local database at "+directory+"/"+db);
        }
        else{
           try{ Kernel.readFile(topdbis, this); }
           catch(Exception e){ FunctionalObserver.log("Persistence: Failure reading DB: "+e.getMessage()); }
           FunctionalObserver.log("Persistence: Local database at "+db);
        }

        final int syncrate = Kernel.config.intPathN("persist:syncrate");
        new Thread(){ public void run(){ runSync(syncrate); } }.start();

        preload(Kernel.config.listPathN("persist:preload"));
        if(netmashconfig!=null){
            funcobs.hereIsTheConfigBack(netmashconfig);
            preload(netmashconfig.listPathN("persist:preload"));
        }

        FunctionalObserver.log("Persistence: initialised.");
    }

    public void readable(ByteBuffer bytebuffer, int len){
        if(len == -1) return;
        boolean unix=true;
        while(true){
            ByteBuffer jsonbytes=null;
            if(unix) jsonbytes = Kernel.chopAtDivider(bytebuffer, "\n\n".getBytes());
            if(jsonbytes==null){
                     unix=false;
                     jsonbytes = Kernel.chopAtDivider(bytebuffer, "\r\n\r\n".getBytes());
            }
            if(jsonbytes==null) return;

            CharBuffer jsonchars = UTF8.decode(jsonbytes);
            String uid = findUID(jsonchars);
            if(uid.equals("netmashconfig")) netmashconfig = new JSON(jsonchars);
            else jsoncache.put(uid, jsonchars);
        }
    }

    void preload(LinkedList preloadlist){
        if(preloadlist==null) return;
        Iterator i = preloadlist.iterator();
        while(i.hasNext()) cache((String)i.next());
    }

    public void writable(ByteBuffer bytebuffer, int len){
    }

    // ----------------------------------------

    public WebObject cache(String uid){
        CharBuffer jsonchars = jsoncache.get(uid);
        if(jsonchars==null) return null;
        jsonchars.position(0);
        JSON json = new JSON(jsonchars);
        String classname = json.stringPathN("%class"); json.removePath("%class");
        WebObject w=null;
        try{
            if(classname!=null && classname.length() >0){
                w=(WebObject)Class.forName(classname).newInstance();
            } else w=new WebObject();
            w.construct(json);
            funcobs.cachePut(w);
            return w;
        }catch(Exception e){
            FunctionalObserver.log("Persistence: Could not build an instance of WebObject ("+uid+" classname="+classname+"):\n"+json);
            e.printStackTrace();
        }
        return null;
    }

    // ----------------------------------------

    public void save(WebObject w){
        syncable.add(w);
    }

    private void runSync(int syncrate){
        while(true){
            for(WebObject w: syncable){
                syncable.remove(w);
                CharBuffer jsonchars=CharBuffer.wrap(w.toString()+"\n");
                jsoncache.put(w.uid, jsonchars);
                ByteBuffer bytebuffer = UTF8.encode(jsonchars);
                if(topdbos==null){
                    try{ Kernel.writeFile(dbfile, true, bytebuffer, this); }
                    catch(Exception e){ FunctionalObserver.log("Persistence: Failure writing to DB: "+e.getMessage()); }
                }
                else{
                    try{ Kernel.writeFile(topdbos,      bytebuffer, this); }
                    catch(Exception e){ FunctionalObserver.log("Persistence: Failure writing to DB: "+e.getMessage()); }
                }
            }
            try{ Thread.sleep(syncrate!=0? syncrate: 100); }catch(Exception e){}
        }
    }

    // ----------------------------------------

    static public final String  UIDRE = ".*\"%uid\":\\s*\"([^\"]+)\".*";
    static public final Pattern UIDPA = Pattern.compile(UIDRE, Pattern.MULTILINE | Pattern.DOTALL);

    static public String findUID(CharBuffer chars){
        Matcher m = UIDPA.matcher(chars);
        if(!m.matches()) return null;
        return m.group(1);
    }

    // ----------------------------------------
}

