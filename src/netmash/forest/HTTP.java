package netmash.forest;

import static java.util.Arrays.*;

import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

import netmash.Version;
import netmash.lib.JSON;
import netmash.platform.*;

/** HTTP and REST: the Object Web.
  */
public class HTTP implements ChannelUser {

    static public final String  WURLRE = "http://([^:]+)(:([0-9]+))?(/.*/(uid-[-0-9a-f]+.json|c-n-[-0-9a-f]+))$";
    static public final Pattern WURLPA = Pattern.compile(WURLRE);
    static public final String   URLRE = "http://([^:/]+)(:([0-9]+))?(/.*)";
    static public final Pattern  URLPA = Pattern.compile(URLRE);

    // ----------------------------------------

    public HTTP(){
        int port = Kernel.config.intPathN("network:port");
        if(port>0){
            Kernel.listen(port, this);
            log("HTTP: initialised. Listening on port "+port);
        }
        else{
            log("HTTP: initialised. No port; not listening");
        }
    }

    // ----------------------------------------

    static HashMap<SocketChannel,HTTPServer> httpservers = new HashMap<SocketChannel,HTTPServer>();

    // -------------------------------
    // ChannelUser Interface

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len){

        HTTPServer httpserver = httpservers.get(channel);
        if(httpserver==null){
            httpserver = new HTTPServer(channel);
            httpservers.put(channel, httpserver);
        }
        httpserver.readable(channel, bytebuffer, len);
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){

        HTTPServer httpserver = httpservers.get(channel);
        httpserver.writable(channel, bytebuffers, len);
    }

    // ----------------------------------------

    private HashMap<String,HTTPClient> connectionPool = new HashMap<String,HTTPClient>();
    private HashMap<String,HTTPClient> longPollerPool = new HashMap<String,HTTPClient>();

    private HTTPClient poolClient(String host, int port){
        String key = host+":"+port;
        HTTPClient client = connectionPool.get(key);
        if(client!=null) return client;
        client=new HTTPClient(host, port);
        connectionPool.put(key, client);
        return client;
    }

    private HTTPClient pollClient(String host, int port){
        String key = host+":"+port;
        HTTPClient client = longPollerPool.get(key);
        if(client!=null) return client;
        client=new HTTPClient(host, port);
        longPollerPool.put(key, client);
        return client;
    }

    private List getClient(WebObject w){
        Matcher m = WURLPA.matcher(w.uid);
        if(!m.matches()){ FunctionalObserver.whereAmI("Remote UID isn't a good URL: "+w.uid); return null; }
        String host = m.group(1);
        int    port = m.group(3)!=null? Integer.parseInt(m.group(3)): 80;
        String path = m.group(4);
        return asList(poolClient(host, port), encodeSpacesAndUTF8IntoPercents(path));
    }

    private List getClient(String url, boolean poll){
        Matcher m = URLPA.matcher(url);
        if(!m.matches()){ log("Remote GET URL syntax: "+url); return null; }
        String host = m.group(1);
        int    port = m.group(3)!=null? Integer.parseInt(m.group(3)): 80;
        String path = m.group(4);
        return asList(poll? pollClient(host,port): poolClient(host,port), encodeSpacesAndUTF8IntoPercents(path));
    }

    void pull(WebObject s){
        List clientpath = getClient(s);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        client.get(path, 0);
    }

    void push(WebObject w){
        List clientpath = getClient(w);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        for(String notifieruid: w.alertedin){
            client.post(path, notifieruid);
        }
        w.alertedin = new CopyOnWriteArraySet<String>();
    }

    void poll(WebObject w){
        List clientpath = getClient(w);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        client.get(path, w.etag);
    }

    void longpoll(HashSet<String> cachenotifies){
        for(String key: longPollerPool.keySet()) longPollerPool.get(key).inactive=true;
        openNewConnections(cachenotifies);
        closeOldConnections();
    }

    void openNewConnections(HashSet<String> cachenotifies){
        for(String cachenotify: cachenotifies){
            List clientpath = getClient(cachenotify, true);
            if(clientpath==null) continue;
            HTTPClient client = (HTTPClient)clientpath.get(0);
            String     path   = (String)    clientpath.get(1);
            client.inactive=false;
            client.get(path);
        }
    }

    void closeOldConnections(){
        for(String key: longPollerPool.keySet()){
            if(longPollerPool.get(key).inactive){
log("close longpoll\n"+key);
                longPollerPool.get(key).stop();
                longPollerPool.remove(key);
            }
        }
    }

    void getJSON(String url, WebObject w, String param){
        List clientpath = getClient(url, false);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        client.get(path, w, param);
    }

    // ----------------------------------------------

    static public String encodeSpacesAndUTF8IntoPercents(String path){
        try{ return new URI(null, path, null).toASCIIString(); }catch(Exception e){ return path; }
    }

    static public String queryAndFormEncode(String path){
        String epath=null;
        try{ epath=URLEncoder.encode(path,"UTF-8"); }catch(Exception e){}
        return epath;
    }

    static public void log(Object s){ FunctionalObserver.log(s); }
}


abstract class HTTPCommon {

    class PostResponse{ int code; String location; PostResponse(int c, String l){this.code=c;this.location=l;}}

    static public final Charset UTF8  = Charset.forName("UTF-8");
    static public final Charset ASCII = Charset.forName("US-ASCII");

    static public final String  UIDRE = Kernel.config.stringPathN("network:pathprefix")+"((uid-[-0-9a-f]+).json|(c-n-[-0-9a-f]+))$";
    static public final Pattern UIDPA = Pattern.compile(UIDRE);

    protected FunctionalObserver funcobs;
    protected SocketChannel channel;
    protected boolean doingHeaders=true;

    protected String httpMethod=null;
    protected String httpPath=null;
    protected String httpProtocol=null;
    protected String httpStatus=null;
    protected String httpStatusText=null;

    protected String httpHost=null;
    protected String httpConnection=null;
    protected String httpCacheNotify=null;
    protected String httpIfNoneMatch=null;
    protected String httpContentLocation=null;
    protected String httpLocation=null;
    protected String httpEtag=null;
    protected String httpContentType=null;
    protected String httpContentLength=null;

    public void receiveNextEvent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        if(eof) HTTP.httpservers.remove(this.channel);
        if( doingHeaders && eof) return;
        if( doingHeaders) readHeaders(bytebuffer);
        if(!doingHeaders) readContent(bytebuffer, eof);
    }

    private void readHeaders(ByteBuffer bytebuffer) throws Exception{
        ByteBuffer headers = Kernel.chopAtDivider(bytebuffer, "\r\n\r\n".getBytes());
        if(headers==null) return;
        CharBuffer headchars = ASCII.decode(headers);
        if(Kernel.config.boolPathN("network:log")) log("<---------------\n"+headchars);
        getFirstLine(headchars);
        getInterestingHeaders(headchars);
        fixKeepAlive();
        doingHeaders=false;
    }

    static public final String  HTTPRE = "\\A([A-Z]+)\\s+([^\\s]+)\\s+(HTTP/1\\.[0-1])$.*";
    static public final Pattern HTTPPA = Pattern.compile(HTTPRE, Pattern.MULTILINE | Pattern.DOTALL);
    static public final String  STATRE = "\\A(HTTP/1\\.[0-1])\\s+([0-9]+)\\s+(.+?)$.*";
    static public final Pattern STATPA = Pattern.compile(STATRE, Pattern.MULTILINE | Pattern.DOTALL);

    private void getFirstLine(CharBuffer headchars) throws Exception{
        Matcher m = HTTPPA.matcher(headchars);
        if(m.matches()){
            httpMethod   = m.group(1);
            httpPath     = m.group(2);
            httpProtocol = m.group(3);
            if(!httpMethod.equals("GET") && !httpMethod.equals("POST")) throw new Exception("Unsupported method: "+httpMethod);
            if(httpPath.indexOf("..") != -1)                            throw new Exception("Bad path: "+httpPath);
        }
        else{
            m = STATPA.matcher(headchars);
            if(!m.matches()) throw new Exception("Header syntax:\n"+headchars);
            httpProtocol   = m.group(1);
            httpStatus     = m.group(2);
            httpStatusText = m.group(3);
        }
        if(httpProtocol==null) throw new Exception("first line of request/response: \n"+headchars);
    }

    private void getInterestingHeaders(CharBuffer headchars) throws Exception{
        boolean docol=false;
        boolean doval=false;
        boolean donln=false;
        StringBuilder tagbuf=new StringBuilder();
        StringBuilder valbuf=new StringBuilder();
        char[] chars = headchars.array();
        int chp=0;
        while(chars[chp++]!='\n' && chp<chars.length);
        for(; chp<chars.length; chp++){
            if(!docol){
                if(chars[chp]==':'){
                    docol=true;
                    continue;
                }   
                if(chars[chp]>' '){
                    tagbuf.append(chars[chp]);
                    continue;
                }   
                parseError(':', chp, chars);
            }   
        
            if(!doval){
                if(chars[chp]==' ') continue;
                doval=true;
            }   
        
            if(!donln){
                if(chars[chp]>=' '){
                    valbuf.append(chars[chp]);
                    if(chp!=chars.length-1) continue;
                }   
                donln=true;
                String tag = new String(tagbuf); tagbuf = new StringBuilder();
                String val = new String(valbuf); valbuf = new StringBuilder();
                fishOutInterestingHeaders(tag, val);
                continue;
            }   
            
            if(chars[chp]>' '){
                docol=false;
                doval=false;
                donln=false;
                chp--;
                continue;
            }   
        }   
    }

    private void fishOutInterestingHeaders(String tag, String val){
        if(tag.equals("Host")){             httpHost=val; return; }
        if(tag.equals("Connection")){       httpConnection=val; return; }
        if(tag.equals("Cache-Notify")){     httpCacheNotify=val; return; }
        if(tag.equals("If-None-Match")){    httpIfNoneMatch=val; return; }
        if(tag.equals("Content-Location")){ httpContentLocation=val; return; }
        if(tag.equals("Location")){         httpLocation=val; return; }
        if(tag.equals("Etag")){             httpEtag=val.substring(1,val.length()-1); return; }
        if(tag.equals("Content-Type")){     httpContentType=val; return; }
        if(tag.equals("Content-Length")){   httpContentLength=val; return; }
    }

    public void fixKeepAlive(){
        boolean keepalive=false;
        boolean is11=httpProtocol.equals("HTTP/1.1");
        boolean iska="Keep-Alive".equalsIgnoreCase(httpConnection);
        boolean iscl="close"     .equalsIgnoreCase(httpConnection);
        if((!is11 && iska) || (is11 && !iscl)){
                keepalive=true;
        }
        httpConnection = (keepalive? "Keep-Alive": "close");
    }

    private void parseError(char ch, int chp, char[] chars) throws Exception{
        throw new Exception("Syntax error in headers: odd char before '"+ch+"': "+
                            "["+JSON.showContext(chp, chars)+"] at "+chp+ " in\n"+new String(chars));
    }       
                
    protected abstract void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception;
    
    static public final DateFormat RFC1123 = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'");
    static { RFC1123.setTimeZone(TimeZone.getTimeZone("GMT")); }

    protected void topRequestHeaders(StringBuilder sb, String method, String host, int port, String path, int etag){
        sb.append(method); sb.append(path); sb.append(" HTTP/1.1\r\n");
        sb.append("Host: "); sb.append(host); if(port!=80) sb.append(":"+port); sb.append("\r\n");
        sb.append("User-Agent: "+Version.NAME+" "+Version.NUMBERS+"\r\n");
        sb.append("Cache-Notify: "); sb.append(UID.toURL(CacheNotify())); sb.append("\r\n");
        if(etag>0){
        sb.append("If-None-Match: \""); sb.append(etag); sb.append("\"\r\n"); }
    }

    protected void topResponseHeaders(StringBuilder sb, String responseCode){
        sb.append(httpProtocol.equals("HTTP/1.1")? "HTTP/1.1 ": "HTTP/1.0 ");
        sb.append(responseCode); sb.append("\r\n");
        sb.append("Connection: "); sb.append(httpConnection); sb.append("\r\n");
        sb.append("Date: "); sb.append(RFC1123.format(new Date())); sb.append("\r\n");
        sb.append("Server: "+Version.NAME+" "+Version.NUMBERS+"\r\n");
        sb.append("Cache-Notify: "); sb.append(UID.toURL(CacheNotify())); sb.append("\r\n");
    }

    protected void contentHeadersAndBody(StringBuilder sb, WebObject w, HashSet<String> percents){
        if(w==null){ sb.append("Content-Length: 0\r\n\r\n"); return; }
        sb.append("Content-Location: "); sb.append(UID.toURL(w.uid)); sb.append("\r\n");
        sb.append("Etag: \""); sb.append(w.etag); sb.append("\"\r\n");
        if(w.maxAge>=0){
        sb.append("Cache-Control: max-age="); sb.append(w.maxAge); sb.append("\r\n");}
        sb.append("Content-Type: application/json\r\n");
        String content=w.toString(percents);
        sb.append("Content-Length: "); sb.append(content.getBytes().length); sb.append("\r\n\r\n");
        sb.append(content);
    }

    protected PostResponse readWebObject(ByteBuffer bytebuffer, int contentLength, String uid, WebObject webobject, String param){

        ByteBuffer body = Kernel.chopAtLength(bytebuffer, contentLength);
        CharBuffer jsonchars = UTF8.decode(body);
        if(Kernel.config.boolPathN("network:log")) log("<---------------\n"+jsonchars);

        if(webobject==null){

            JSON json = new JSON(jsonchars);
            WebObject w=null;
            try{ w=new WebObject(json, httpContentLocation, httpEtag, null, httpCacheNotify); }
            catch(Exception e){ log(e); }
            if(w==null){ log("Cannot convert to WebObject:\n"+json); return new PostResponse(400,null); }

            if(uid!=null){
                if(!uid.startsWith("c-n-")) w.notify.add(uid);
                else
                if(!uid.equals(CacheNotify())){ log("Cache-Notify is "+CacheNotify()+" not "+uid); return new PostResponse(404,null); }
            }
            String location=funcobs.httpNotify(w);
            return new PostResponse(location==null? 200:201, location);
        }
        else{
            JSON json = null;
            if(httpContentType.startsWith("application/json")){
                json = new JSON(jsonchars);
            }
            webobject.httpNotifyJSON(json, param);
            return null;
        }
    }

    static String cacheNotify=null;
    String CacheNotify(){
        if(cacheNotify==null){
            cacheNotify=Kernel.config.stringPathN("network:cache-notify");
            if(cacheNotify==null) cacheNotify=UID.generateCN();
        }
        return cacheNotify;
    }

    boolean tunnelHeaders=false;
    protected HashSet<String> getPercents(){
        HashSet<String> percents = new HashSet<String>();
        if(tunnelHeaders){
            percents.add("%uid");
            percents.add("%url");
            percents.add("%etag");
            percents.add("%max-age");
        }
        return percents;
    }

    static public void log(Object s){ FunctionalObserver.log(s); }
}


class HTTPServer extends HTTPCommon implements ChannelUser, Notifiable {

    public HTTPServer(SocketChannel channel){
        funcobs = FunctionalObserver.funcobs;
        this.channel = channel;
    }

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len){
        boolean sof = (len==  0);
        boolean eof = (len== -1);
        if(sof) return;
        try{
            receiveNextEvent(bytebuffer, eof);

        } catch(Exception e){ close("Failed reading - closing connection", e); }
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){
        try{
            if(bytebuffers.size()==0){
                boolean ka=httpConnection.equalsIgnoreCase("Keep-Alive");
                if(ka) receiveNextEvent(Kernel.rdbuffers.get(channel), false);
                else   Kernel.close(channel);
            }
        } catch(Exception e){ close("Failed reading - closing connection", e); }
    }

    protected void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        if(eof) return;
        Matcher m = UIDPA.matcher(httpPath);
        if(m.matches()){
            String uid=m.group(2); if(uid==null) uid=m.group(3);
            if(uid==null) send404();
            else
            if(httpMethod.equals("GET" )){ if(!readGET(uid)) return; }
            else
            if(httpMethod.equals("POST")){ if(!readPOST(bytebuffer, uid)) return; }
        } else send404();
        doingHeaders=true;
    }

    private boolean readGET(String uid){
        WebObject w=funcobs.httpObserve(this, uid);
        if(w==null) return false;
        if(("\""+w.etag+"\"").equals(httpIfNoneMatch)) send304();
        else send200(w);
        return true;
    }

    private boolean readPOST(ByteBuffer bytebuffer, String uid) throws Exception{
        int contentLength=0;
        if(httpContentLength!=null) contentLength = Integer.parseInt(httpContentLength);
        else throw new Exception("POST without Content-Length");
        if(contentLength > bytebuffer.position()) return false;
        if(contentLength >0){
            PostResponse pr=readWebObject(bytebuffer, contentLength, uid, null, null);
            if(pr.code==200) send200(null);
            else
            if(pr.code==201) send201(pr.location);
            else
            if(pr.code==400) send400();
            else
            if(pr.code==404) send404();
            else             send400();
        }
        return true;
    }

    /** Notifiable callback from FunctionalObserver when object is found. */
    public void notify(WebObject w){
        if(w.isShell()) send404();
        else            send200(w);
        doingHeaders=true;
    }

    public void send200(WebObject w){
        StringBuilder sb=new StringBuilder();
        topResponseHeaders(sb, "200 OK");
        contentHeadersAndBody(sb, w, getPercents());
        if(Kernel.config.boolPathN("network:log")) log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    public void send201(String location){ sendNoBody("201 Created", "Location: "+location+"\r\n"); }

    public void send304(){ sendNoBody("304 Not Modified",null); }

    public void send400(){ sendNoBody("400 Bad Request",null); }

    public void send404(){ sendNoBody("404 Not Found",null); }

    void sendNoBody(String responseCode, String extraHeaders){
        StringBuilder sb=new StringBuilder();
        topResponseHeaders(sb, responseCode);
        if(extraHeaders!=null) sb.append(extraHeaders);
        sb.append("Content-Length: 0\r\n\r\n");
        if(Kernel.config.boolPathN("network:log")) log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    protected void close(String message, Exception e){
        if(message!=null) log(message);
        if(e!=null) e.printStackTrace();
        doingHeaders=true;
        Kernel.close(channel);
    }

    static public void log(Object s){ FunctionalObserver.log(s); }
}

class HTTPClient extends HTTPCommon implements ChannelUser, Runnable {

    class Request { 
        String type, path; int etag; WebObject webobject; String param, notifieruid;
        Request(String t, String p, int e, WebObject o, String m, String n){
            type=t; path=p; etag=e; webobject=o; param=m; notifieruid=n;
        }
        public String toString(){ return "Request: "+type+" "+path+" "+etag; }
    }

    private String host;
    private int    port;

    boolean  needsConnect=true;
    LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<Request>();
    boolean  makingRequest;
    Request  request;
    boolean  inactive=false;
    boolean  running=true;

    public HTTPClient(String host, int port){
        funcobs = FunctionalObserver.funcobs;
        this.host = host;
        this.port = port;
        new Thread(this).start();
    }

    public void get(String path){
        if(!requests.isEmpty()) return;
        try{ requests.put(new Request("LONG", path, 0, null, null, null)); }catch(Exception e){}
    }

    public void get(String path, int etag){
        try{ requests.put(new Request("POLL", path, etag, null, null, null)); }catch(Exception e){}
    }

    public void get(String path, WebObject webobject, String param){
        try{ requests.put(new Request("GETJ", path, 0, webobject, param, null)); }catch(Exception e){}
    }

    public void post(String path, String notifieruid){
        try{ requests.put(new Request("POST", path, 0, null, null, notifieruid)); }catch(Exception e){}
    }

    public void run(){
        while(running){
            try{ request = requests.take(); }catch(Exception e){}
            String longpath=request.type.equals("LONG")? request.path: null;
            makingRequest=true; 
            if(needsConnect){ needsConnect=false; Kernel.channelConnect(host, port, this); }
            else makeRequest(); 
            synchronized(this){ if(makingRequest) try{ wait(); }catch(Exception e){} }
            if(longpath!=null){
                if(needsConnect){
                    log("Long poll connection broken to "+longpath);
                    Kernel.sleep(30000);
                    log("Reconnecting long poll to "+longpath);
                }
                get(longpath);
            }
        }
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){
        this.channel=channel;
        if(request==null) return;
        boolean sof = (len==0);
        if(sof) makeRequest();
    }

    private void makeRequest(){
        StringBuilder sb=new StringBuilder();
        if(request.notifieruid==null){
            topRequestHeaders(sb, "GET ", host, port, request.path, request.etag);
            sb.append("\r\n");
        }
        else{
            topRequestHeaders(sb, "POST ", host, port, request.path, 0);
            contentHeadersAndBody(sb, funcobs.cacheGet(request.notifieruid), getPercents());
        }
        if(Kernel.config.boolPathN("network:log")) log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len){
        boolean eof=(len== -1);
        if(eof){ needsConnect=true; doingHeaders=true; }
        try{
            receiveNextEvent(bytebuffer, eof);

        } catch(Exception e){ close("Failed reading - closing connection", e); }
        // bug: relies on all headers coming in in one go
        if(doingHeaders && "close".equals(httpConnection) && !eof){ close(null,null); return; }
        if(doingHeaders) synchronized(this){ makingRequest=false; notify(); }
    }

    protected void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        int contentLength=0;
        if(httpContentLength!=null) contentLength = Integer.parseInt(httpContentLength);
        if(eof) contentLength = bytebuffer.position();
        if(contentLength == -1 || contentLength > bytebuffer.position()) return;
        if(httpStatus.equals("201") && httpLocation!=null && request.notifieruid!=null){
            WebObject w = funcobs.cacheGet(request.notifieruid);
            w.setURL(httpLocation);
        }
        if(contentLength > 0) readWebObject(bytebuffer, contentLength, null, request.webobject, request.param);
        doingHeaders=true;
        request=null;
    }

    protected void close(String message, Exception e){
        if(message!=null) log(message);
        if(e!=null) e.printStackTrace();
        doingHeaders=true;
        Kernel.close(channel);
        needsConnect=true;
    }

    protected void stop(){ running=false; close(null,null); }

    static public void log(Object s){ FunctionalObserver.log(s); }
}










