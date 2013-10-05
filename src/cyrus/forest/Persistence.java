package cyrus.forest;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.regex.*;

import cyrus.platform.*;
import cyrus.lib.JSON;
import static cyrus.forest.UID.*;

import static cyrus.lib.Utils.*;

/** Persistence of WebObjects.
  * A NoSQL JSON in-memory Database!
  */
public class Persistence implements FileUser {

    // ----------------------------------------

    static public final Charset UTF8 = Charset.forName("UTF-8");

    public  FunctionalObserver funcobs;

    private InputStream      dbis=null;
    private FileOutputStream dbos=null;
    private String directory = null;
    private String db = null;
    private File   dbfile=null;
    private LinkedList preload=null;
    private String topclass=null;

    private Pattern toppac=null;
    private Pattern toppaj=null;

    private JSON cyrusconfig=null;
    private boolean cyrus=true;
    private String topuid=null;

    private ConcurrentHashMap<String,CharBuffer> jsoncache = new ConcurrentHashMap<String,CharBuffer>();
    private CopyOnWriteArraySet<WebObject>       syncable  = new CopyOnWriteArraySet<WebObject>();

    // ----------------------------------------

    public Persistence(InputStream dbis, FileOutputStream dbos){ try{

        funcobs = FunctionalObserver.funcobs;
        this.dbis = dbis;
        this.dbos = dbos;
        this.directory = Kernel.config.stringPathN("persist:directory");
        this.directory=(directory==null || directory.trim().equals(""))? "": (!directory.endsWith("/")? directory+"/": directory);
        this.db        = Kernel.config.stringPathN("persist:db");
        this.dbfile  = new File(directory+db);
        this.preload=Kernel.config.listPathN("persist:preload");
        this.topclass=preload!=null &&
                      preload.size() >0 &&
                      preload.get(0) instanceof String &&
                      !isUID(preload.get(0))? (String)preload.get(0): null;

        if(topclass!=null){
            String  toprec = "^\\s*Class:\\s*"+topclass+"$";
            String  toprej = "^\\s*\"Class\":\\s*\""+topclass+"\",$";
            toppac = Pattern.compile(toprec, Pattern.MULTILINE | Pattern.DOTALL);
            toppaj = Pattern.compile(toprej, Pattern.MULTILINE | Pattern.DOTALL);
        }

        boolean dbrd=dbfile.exists() && dbfile.canRead();

        if(dbis!=null) Kernel.readFile(dbis, this);
        else
        if(dbrd)       Kernel.readFile(dbfile, this);

        final int syncrate = Kernel.config.intPathN("persist:syncrate");
        new Thread(){ public void run(){ runSync(syncrate); } }.start();

        if(topclass!=null) getOrCreateTop();

        preload(preload);
        if(cyrusconfig!=null){
            funcobs.hereIsTheConfigBack(cyrusconfig);
            preload(cyrusconfig.listPathN("persist:preload"));
        }
        log("Persistence: initialised.");

    } catch(Exception e){ log("Persistence: Failure reading DB:"); e.printStackTrace(); return; } }

    boolean isUnix=true;

    public void readable(ByteBuffer bytebuffer, int len){
        if(len == -1) return;
        CharBuffer prevchars=null;
        while(true){
            ByteBuffer jsonbytes=null;
            if(isUnix) jsonbytes = Kernel.chopAtDivider(bytebuffer, "\n\n".getBytes(), true);
            boolean unix=jsonbytes!=null;
            if(!unix)  jsonbytes = Kernel.chopAtDivider(bytebuffer, "\r\n\r\n".getBytes(), true);
            if(jsonbytes==null) return;
            isUnix=unix;
            CharBuffer jsonchars = UTF8.decode(jsonbytes);
            String uid = findUIDAndTopAndDetectCyrus(jsonchars);
            if(uid.equals("cyrusconfig")) cyrusconfig = new JSON(jsonchars,cyrus);
            else
            if(isUID(uid)) jsoncache.put(uid, jsonchars);
            else throw new RuntimeException("Data corrupt:\n"+jsonchars+(prevchars!=null? "Previous object:\n"+prevchars: ""));
            prevchars=jsonchars;
        }
    }

    void preload(LinkedList preloadlist){
        if(preloadlist==null) return;
        for(Object o: preloadlist) if(isUID(o)) funcobs.cachePut(cache((String)o));
    }

    private void getOrCreateTop(){
        try{
            if(topuid!=null) funcobs.cachePut(cache(topuid));
            else             funcobs.cachePut((WebObject)Class.forName(topclass).newInstance());
        }catch(Exception e){
            log("Persistence: Could not build an instance of WebObject (classname="+topclass+")");
            e.printStackTrace();
        }
    }

    public void writable(ByteBuffer bytebuffer, int len){ }

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
            if(i%20==0 && dbos==null) compressDB();
            try{ for(WebObject w: syncable){
                syncable.remove(w);
                CharBuffer jsonchars;
                synchronized(w){ jsonchars=CharBuffer.wrap(w.toString(cyrus)); }
                jsoncache.put(w.uid, jsonchars);
                ByteBuffer bytebuffer = UTF8.encode(jsonchars);
                if(dbos!=null) Kernel.writeFile(dbos,         bytebuffer, this);
                else           Kernel.writeFile(dbfile, true, bytebuffer, this);
            }}catch(Exception e){ log("Persistence: Failure writing to DB: "); e.printStackTrace(); }
            Kernel.sleep(syncrate!=0? syncrate: 100);
        }
    }

    private void compressDB(){ try{
        File ddbfile = new File(directory+"compressed."+db);
        Kernel.writeFile(ddbfile, false, UTF8.encode(""), this);
        for(Map.Entry<String,CharBuffer> entry: jsoncache.entrySet()){
            CharBuffer jsonchars=entry.getValue();
            jsonchars.position(0);
            ByteBuffer bytebuffer = UTF8.encode(jsonchars);
            Kernel.writeFile(ddbfile, true, bytebuffer, this);
        }
        if(!ddbfile.renameTo(dbfile)) throw new Exception("Compressed snapshot DB rename failed: "+ddbfile+" to "+dbfile);
    }catch(Exception e){ log("Persistence: Failure compressing DB: "+e.getMessage()); } }

    // ----------------------------------------

    static public final String  UIDREJ = "^\\s*\\{\\s*\"UID\":\\s*\"([^\"]+)\".*";
    static public final Pattern UIDPAJ = Pattern.compile(UIDREJ, Pattern.MULTILINE | Pattern.DOTALL);
    static public final String  UIDREC = "^\\s*\\{\\s*UID:\\s*([^\\s]+).*";
    static public final Pattern UIDPAC = Pattern.compile(UIDREC, Pattern.MULTILINE | Pattern.DOTALL);

    public String findUIDAndTopAndDetectCyrus(CharBuffer chars){
        Matcher m = UIDPAJ.matcher(chars);
        cyrus=!m.matches();
        if(cyrus){
            m = UIDPAC.matcher(chars);
            if(!m.matches()) return null;
        }
        String uid=m.group(1);
        if(toppac!=null){
            Matcher n = cyrus? toppac.matcher(chars): toppaj.matcher(chars);
            if(n.find()) topuid=uid;
        }
        return uid;
    }

    // ----------------------------------------
}

