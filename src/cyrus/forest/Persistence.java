package cyrus.forest;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.regex.*;

import cyrus.platform.*;
import cyrus.lib.JSON;

import static cyrus.lib.Utils.*;

/** Persistence of WebObjects.
  * A NoSQL JSON in-memory Database!
  */
public class Persistence implements FileUser {

    // ----------------------------------------

    static public final Charset UTF8 = Charset.forName("UTF-8");

    public  FunctionalObserver funcobs;

    private String directory = null;
    private String db = null;
    private File dbfile=null;
    private InputStream      topdbis=null;
    private FileOutputStream topdbos=null;
    private JSON cyrusconfig=null;
    private boolean cyrus=false;

    private ConcurrentHashMap<String,CharBuffer> jsoncache = new ConcurrentHashMap<String,CharBuffer>();
    private CopyOnWriteArraySet<WebObject>       syncable  = new CopyOnWriteArraySet<WebObject>();

    // ----------------------------------------

    public Persistence(InputStream topdbis, FileOutputStream topdbos){

        funcobs = FunctionalObserver.funcobs;
        this.topdbis = topdbis;
        this.topdbos = topdbos;

        directory = Kernel.config.stringPathN("persist:directory");
        db        = Kernel.config.stringPathN("persist:db");
        if(topdbis==null){
           dbfile = new File(directory+"/"+db);
           try{ Kernel.readFile(dbfile, this); }
           catch(Exception e){ log("Persistence: Failure reading DB: "+e.getMessage()); }
           log("Persistence: Database at "+directory+"/"+db);
        }
        else{
           try{ Kernel.readFile(topdbis, this); }
           catch(Exception e){ log("Persistence: Failure reading DB: "+e.getMessage()); }
           log("Persistence: Local database at "+db);
        }

        final int syncrate = Kernel.config.intPathN("persist:syncrate");
        new Thread(){ public void run(){ runSync(syncrate); } }.start();

        preload(Kernel.config.listPathN("persist:preload"));
        if(cyrusconfig!=null){
            funcobs.hereIsTheConfigBack(cyrusconfig);
            preload(cyrusconfig.listPathN("persist:preload"));
        }

        log("Persistence: initialised.");
    }

    boolean isUnix=true;

    public void readable(ByteBuffer bytebuffer, int len){
        if(len == -1) return;
        while(true){
            ByteBuffer jsonbytes=null;
            if(isUnix) jsonbytes = Kernel.chopAtDivider(bytebuffer, "\n\n".getBytes(), true);
            boolean unix=jsonbytes!=null;
            if(!unix)  jsonbytes = Kernel.chopAtDivider(bytebuffer, "\r\n\r\n".getBytes(), true);
            if(jsonbytes==null) return;
            isUnix=unix;
            CharBuffer jsonchars = UTF8.decode(jsonbytes);
            String uid = findUIDAndDetectCyrus(jsonchars);
            if(uid.equals("cyrusconfig")) cyrusconfig = new JSON(jsonchars,cyrus);
            else jsoncache.put(uid, jsonchars);
        }
    }

    void preload(LinkedList preloadlist){
        if(preloadlist==null) return;
        for(Object o: preloadlist) funcobs.cachePut(cache(o.toString()));
    }

    public void writable(ByteBuffer bytebuffer, int len){
    }

    // ----------------------------------------

    public WebObject cache(String uid){
        CharBuffer jsonchars = jsoncache.get(uid);
        if(jsonchars==null) return null;
        jsonchars.position(0);
        JSON json = new JSON(jsonchars,cyrus);
        String classname = json.stringPathN("Class"); json.removePath("Class");
        WebObject w=null;
        try{
            if(classname!=null && classname.length() >0){
                w=(WebObject)Class.forName(classname).newInstance();
            } else w=new CyrusLanguage();
            w.construct(json);
            return w;
        }catch(Exception e){
            log("Persistence: Could not build an instance of WebObject ("+uid+" classname="+classname+"):\n"+json);
            e.printStackTrace();
        }
        return null;
    }

    // ----------------------------------------

    public void save(WebObject w){
        if(!w.nopersist) syncable.add(w);
    }

    private void runSync(int syncrate){
        for(int i=0; ; i++){
            if(i%20==0) writeTheLot();
            for(WebObject w: syncable){
                syncable.remove(w);
                CharBuffer jsonchars;
                synchronized(w){ jsonchars=CharBuffer.wrap(w.toString(cyrus)); }
                jsoncache.put(w.uid, jsonchars);
                ByteBuffer bytebuffer = UTF8.encode(jsonchars);
                try{
                    if(topdbos==null) Kernel.writeFile(dbfile, true, bytebuffer, this);
                    else              Kernel.writeFile(topdbos,      bytebuffer, this);
                }catch(Exception e){ log("Persistence: Failure writing to DB: "+e.getMessage()); }
            }
            Kernel.sleep(syncrate!=0? syncrate: 100);
        }
    }

    private void writeTheLot(){ try{
        File ddbfile = new File(directory+"/snapshot."+db);
        Kernel.writeFile(ddbfile, false, UTF8.encode(""), this);
        for(Map.Entry<String,CharBuffer> entry: jsoncache.entrySet()){
            CharBuffer jsonchars=entry.getValue();
            jsonchars.position(0);
            Kernel.writeFile(ddbfile, true, UTF8.encode(jsonchars), this);
        }
    }catch(Exception e){ log("Persistence: Failure writing to DB: "+e.getMessage()); } }

    // ----------------------------------------

    static public final String  UIDREJ = "^\\s*\\{\\s*\"UID\":\\s*\"([^\"]+)\".*";
    static public final Pattern UIDPAJ = Pattern.compile(UIDREJ, Pattern.MULTILINE | Pattern.DOTALL);
    static public final String  UIDRES = "^\\s*\\{\\s*UID:\\s*([^\\s]+).*";
    static public final Pattern UIDPAS = Pattern.compile(UIDRES, Pattern.MULTILINE | Pattern.DOTALL);

    public String findUIDAndDetectCyrus(CharBuffer chars){
        Matcher m = UIDPAJ.matcher(chars);
        if(!m.matches()){
                m = UIDPAS.matcher(chars);
                if(!m.matches()) return null;
                cyrus=true;
        }
        return m.group(1);
    }

    // ----------------------------------------
}

