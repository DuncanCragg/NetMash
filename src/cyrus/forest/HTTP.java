package cyrus.forest;

import static java.util.Arrays.*;

import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

import cyrus.Version;
import cyrus.lib.*;
import cyrus.platform.*;

import static cyrus.lib.Utils.*;

/** HTTP and REST: the Object Web.
  */
public class HTTP implements ChannelUser {

    static public final String  WURLRE = "http://([^:/]+)(:([0-9]+))?(/.*(.json|.cyr|/c-n-[-0-9a-f]+))$";
    static public final String   URLRE = "http://([^:/]+)(:([0-9]+))?(/.*)";
    static public final Pattern WURLPA = Pattern.compile(WURLRE);
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

    HashMap<SocketChannel,HTTPServer> httpservers = new HashMap<SocketChannel,HTTPServer>();

    // -------------------------------
    // ChannelUser Interface

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len){
        HTTPServer httpserver = httpservers.get(channel);
        if(httpserver==null){
            httpserver = new HTTPServer(channel);
            httpservers.put(channel, httpserver);
        }
        httpserver.readable(channel, bytebuffer, len);
        boolean eof = (len== -1);
        if(eof) httpservers.remove(channel);
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){

        HTTPServer httpserver = httpservers.get(channel);
        httpserver.writable(channel, bytebuffers, len);
    }

    // ----------------------------------------

    private ConcurrentHashMap<String,HTTPClient> connectionPool = new ConcurrentHashMap<String,HTTPClient>();
    private ConcurrentHashMap<String,HTTPClient> longPollerPool = new ConcurrentHashMap<String,HTTPClient>();
    private ConcurrentHashMap<String,HTTPServer> longPusherPool = new ConcurrentHashMap<String,HTTPServer>();

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

    public HTTPServer getLongPusher(String cn){
        return longPusherPool.get(cn);
    }

    public void putLongPusher(String cn, HTTPServer server){
        if(cn==null) return;
        HTTPServer oldserver = longPusherPool.get(cn);
        if(oldserver==server) return;
        if(oldserver!=null) server.longQ.addAll(oldserver.longQ);
        longPusherPool.put(cn, server);
    }

    private List getClient(WebObject w){
        Matcher m = WURLPA.matcher(w.uid);
        if(!m.matches()){ Utils.whereAmI("Remote UID isn't a good URL: "+w.uid); return null; }
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

    boolean poll(WebObject w){
        List clientpath = getClient(w);
        if(clientpath==null) return false;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        client.pollRequest(path, w.uid, w.etag);
        return true;
    }

    boolean push(WebObject w){
        List clientpath = getClient(w);
        if(clientpath==null) return false;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        for(String notifieruid: w.alertedin){
            client.postRequest(path, w.uid, notifieruid);
        }
        w.alertedin = new CopyOnWriteArraySet<String>();
        return true;
    }

    boolean longpush(WebObject w){ if(false) log("longpush:\n"+w);
        HTTPServer server = getLongPusher(w.isAsymmetricCN()? w.uid: w.cacheNotify);
        if(server==null){ if(false) log(String.format("!! No longpush AsymmetricCN=%s, cacheNotify=%s\n%s",w.isAsymmetricCN(),w.cacheNotify,w)); return false; }
        for(String notifieruid: w.alertedin){
            server.longRequest(notifieruid);
        }
        w.alertedin = new CopyOnWriteArraySet<String>();
        return true;
    }

    void longpoll(HashSet<String> longPollURLs){
        for(String key: longPollerPool.keySet()) longPollerPool.get(key).inactive=true;
        openNewConnections(longPollURLs);
        closeOldConnections();
    }

    void openNewConnections(HashSet<String> longPollURLs){
        for(String url: longPollURLs){
            List clientpath = getClient(url, true);
            if(clientpath==null) continue;
            HTTPClient client = (HTTPClient)clientpath.get(0);
            String     path   = (String)    clientpath.get(1);
            client.inactive=false;
            client.longPollRequest(path);
        }
    }

    void closeOldConnections(){
        for(String key: longPollerPool.keySet()){
            if(longPollerPool.get(key).inactive){
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
        client.jsonRequest(path, w, param);
    }

    void setHomeCN(WebObject w){
        w.notifying(asList(Kernel.config.stringPathN("network:home-cache-notify")));
    }

    // ----------------------------------------------

    static public void setCacheNotify(String cn){ HTTPCommon.setCacheNotify(cn); }

    static public String encodeSpacesAndUTF8IntoPercents(String path){
        try{ return new URI(null, path, null).toASCIIString(); }catch(Exception e){ return path; }
    }

    static public String queryAndFormEncode(String path){
        String epath=null;
        try{ epath=URLEncoder.encode(path,"UTF-8"); }catch(Exception e){}
        return epath;
    }
}


abstract class HTTPCommon {

    class PostResponse{ int code; String location; PostResponse(int c, String l){this.code=c;this.location=l;}}

    static public final int CLIENT_RETRY_WAIT = 10000;
    static public final int LONG_POLL_TIMEOUT = 30000;

    static public final Charset UTF8  = Charset.forName("UTF-8");
    static public final Charset ASCII = Charset.forName("US-ASCII");

    protected FunctionalObserver funcobs;
    protected SocketChannel channel;

    enum RequestState { HEADERS, CONTENT, RESPONSE }
    protected RequestState requeststate = RequestState.HEADERS;
    protected boolean doingHeaders(){  return requeststate==RequestState.HEADERS; }
    protected boolean doingContent(){  return requeststate==RequestState.CONTENT; }
    protected boolean doingResponse(){ return requeststate==RequestState.RESPONSE; }
    protected String  doing(){ return "Doing "+requeststate; }
    protected void    setDoingHeaders(){  requeststate = RequestState.HEADERS; }
    protected void    setDoingContent(){  requeststate = RequestState.CONTENT; }
    protected void    setDoingResponse(){ requeststate = RequestState.RESPONSE; }

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
    protected String httpETag=null;
    protected String httpMaxAge=null;
    protected String httpContentType=null;
    protected String httpContentLength=null;
    protected String httpTransferEncoding=null;
    protected String httpUserAgent=null;

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len){
        boolean sof = (len==  0);
        boolean eof = (len== -1);
        if(sof) return;
        try{
            receiveNextEvent(bytebuffer, eof);

        } catch(Exception e){ close("Failed reading - closing connection", e); }
    }

    public void receiveNextEvent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        if(!doingContent() && eof){ earlyEOF(); return; }
        if(doingHeaders()) readHeaders(bytebuffer);
        if(doingContent()) readContent(bytebuffer, eof);
    }

    private void readHeaders(ByteBuffer bytebuffer) throws Exception{
        ByteBuffer headers = Kernel.chopAtDivider(bytebuffer, "\r\n\r\n".getBytes());
        if(headers==null) return;
        CharBuffer headchars = ASCII.decode(headers);
        if(Kernel.config.intPathN("network:log")==2) log("<---------------\n"+headchars);
        clearFirstLine();
        getFirstLine(headchars);
        clearInterestingHeaders();
        getInterestingHeaders(headchars);
        if(Kernel.config.intPathN("network:log")==1) logFirstLineBriefly();
        fixKeepAlive();
        setDoingContent();
    }

    private void logFirstLineBriefly(){
        log((httpMethod!=null?          httpMethod:          "")+"|"+
            (httpPath!=null?            httpPath:            "")+"|"+
            (httpStatus!=null?          httpStatus:          "")+"|"+
            (httpContentLocation!=null? httpContentLocation: "")+
            "<--");
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
            if(!httpMethod.equals("GET") && !httpMethod.equals("HEAD") && !httpMethod.equals("POST") && !httpMethod.equals("OPTIONS"))
                                             throw new Exception("Unsupported method: "+httpMethod);
            if(httpPath.indexOf("..") != -1) throw new Exception("Bad path: "+httpPath);
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
                fishOutInterestingHeaders(tag.toLowerCase(), val);
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

    private void clearFirstLine(){
        httpMethod=null;
        httpPath=null;
        httpProtocol=null;
        httpStatus=null;
        httpStatusText=null;
    }

    private void clearInterestingHeaders(){
        httpHost=null;
        httpConnection=null;
        httpCacheNotify=null;
        httpIfNoneMatch=null;
        httpContentLocation=null;
        httpLocation=null;
        httpETag=null;
        httpMaxAge=null;
        httpContentType=null;
        httpContentLength=null;
        httpTransferEncoding=null;
        httpUserAgent=null;
    }

    private void fishOutInterestingHeaders(String tag, String val){
        if(tag.equals("host")){              httpHost=val; return; }
        if(tag.equals("connection")){        httpConnection=val; return; }
        if(tag.equals("cache-notify")){      httpCacheNotify=val; return; }
        if(tag.equals("if-none-match")){     httpIfNoneMatch=val; return; }
        if(tag.equals("content-location")){  httpContentLocation=val; return; }
        if(tag.equals("location")){          httpLocation=val; return; }
        if(tag.equals("etag")){              httpETag=val.substring(1,val.length()-1); return; }
        if(tag.equals("cache-control")){     httpMaxAge=val.substring(8); return; }
        if(tag.equals("content-type")){      httpContentType=val; return; }
        if(tag.equals("content-length")){    httpContentLength=val; return; }
        if(tag.equals("transfer-encoding")){ httpTransferEncoding=val; return; }
        if(tag.equals("user-agent")){        httpUserAgent=val; return; }
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

    protected abstract void earlyEOF();
    protected abstract void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception;

    static public final DateFormat RFC1123 = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'");
    static { RFC1123.setTimeZone(TimeZone.getTimeZone("GMT")); }

    protected void topRequestHeaders(StringBuilder sb, String method, String host, int port, String path, int etag){
        sb.append(method); sb.append(path); sb.append(" HTTP/1.1\r\n");
        if(Kernel.config.intPathN("network:log")==1) log(sb);
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
        sb.append("Cache-Notify: "); sb.append(rewrite10022(UID.toURL(CacheNotify()))); sb.append("\r\n");
    }

    protected void topResponseHeaders(ByteBuffer bb, String responseCode){
        bb.put((httpProtocol.equals("HTTP/1.1")? "HTTP/1.1 ": "HTTP/1.0 ").getBytes());
        bb.put(responseCode.getBytes()); bb.put("\r\n".getBytes());
        bb.put("Connection: ".getBytes()); bb.put(httpConnection.getBytes()); bb.put("\r\n".getBytes());
        bb.put("Date: ".getBytes()); bb.put(RFC1123.format(new Date()).getBytes()); bb.put("\r\n".getBytes());
        bb.put(("Server: "+Version.NAME+" "+Version.NUMBERS+"\r\n").getBytes());
    }

    boolean useBrainDeadSoCalledAccessControlVerboseHeaderCruft=true;

    protected void contentHeadersAndBody(StringBuilder sb, WebObject w, HashSet<String> percents, boolean head, boolean cyrus){
        if(useBrainDeadSoCalledAccessControlVerboseHeaderCruft){
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Allow-Methods: GET, POST, HEAD, OPTIONS\r\n");
        sb.append("Access-Control-Allow-Headers: X-Requested-With, X-Requested-By, Cache-Notify, Origin, Content-Type, Accept\r\n");
        sb.append("Access-Control-Expose-Headers: Content-Location, Location, Cache-Notify, ETag\r\n");
        }
        if(w==null){ sb.append("Content-Length: 0\r\n\r\n"); return; }
        String cl=rewrite10022(w.url==null? UID.toURL(w.uid): w.url);
        sb.append("Content-Location: "); sb.append(cl); sb.append("\r\n");
        if(Kernel.config.intPathN("network:log")==1) log(cl);
        sb.append("ETag: \""); sb.append(w.etag); sb.append("\"\r\n");
        if(w.maxAge>=0){
        sb.append("Cache-Control: max-age="); sb.append(w.maxAge); sb.append("\r\n");}
        sb.append("Content-Type: "); sb.append(cyrus? "text/cyrus": "application/json"); sb.append("\r\n");
        String content=rewriteUIDsToURLs(w.toString(percents,cyrus),cyrus);
        sb.append("Content-Length: "); sb.append(content.getBytes().length); sb.append("\r\n\r\n");
        if(!head) sb.append(content);
    }

    static LinkedHashMap<String,String> mimeTypes=new LinkedHashMap<String,String>(); static { setUpMimeTypes(); }

    @SuppressWarnings("unchecked")
    static void setUpMimeTypes(){
        mimeTypes.put(".html",     "text/html");
        mimeTypes.put(".js",       "application/javascript");
        mimeTypes.put(".json",     "application/json");
        mimeTypes.put(".cyr",      "text/cyrus");
        mimeTypes.put(".db",       "text/cyrus");
        mimeTypes.put(".css",      "text/css");
        mimeTypes.put(".appcache", "text/cache-manifest");

        mimeTypes.put(".jpeg", "image/jpeg");
        mimeTypes.put(".jpg",  "image/jpeg");
        mimeTypes.put(".gif",  "image/gif");
        mimeTypes.put(".ico",  "image/x-icon");
        mimeTypes.put(".png",  "image/png");
    }

    protected ByteBuffer contentHeadersAndFile(ByteBuffer bb, String path){ try{
        if(path.indexOf("..")!= -1) return null;
        if(!path.startsWith("/")) path="/"+path;
        if( path.endsWith(  "/")) path+="index.html";
        path="statics"+path;
        int q=path.indexOf("?"); if(q>=0) path=path.substring(0,q);
        int i=path.lastIndexOf("."); if(i< 1) return null;
        String ext=path.substring(i);
        String ct=mimeTypes.get(ext);
        if(ct==null) ct="application/octet-stream";
        if(ct.equals("text/cache-manifest")) bb.put("Cache-Control: no-cache\r\n".getBytes());
        bb.put("Content-Type: ".getBytes()); bb.put(ct.getBytes()); bb.put("\r\n".getBytes());
        File f=new File(path);
        InputStream fileis;
        if(f.exists()) fileis=new FileInputStream(f);
        else           fileis=this.getClass().getClassLoader().getResourceAsStream(path);
        String len=""+fileis.available();
        bb.put("Content-Length: ".getBytes()); bb.put(len.getBytes()); bb.put("\r\n\r\n".getBytes());
        bb=Kernel.readFile(fileis, bb);
        bb.limit(bb.position());
        bb.position(0);
        return bb;
    } catch(Exception e){ log("HTTP: Failed to read file "+path+" - "+e); return null; }}

    private String rewriteUIDsToURLs(String s, boolean cyrus){
        if(UID.notVisible()) return s;
        String r;
        if(cyrus){
            r=s.replaceAll("(\\s)(uid-[-a-zA-Z0-9]+)(|.json|.cyr)(\\s)",
                           "$1"+UID.localPrePath()+"$2.cyr$4");
        }
        else{
            r=s.replaceAll("\"(uid-[-a-zA-Z0-9]+)(|.json|.cyr)\"",
                           "\""+UID.localPrePath()+"$1.json\"");
        }
        return rewrite10022(r);
    }

    String rewrite10022(String r){
        boolean nonCyrusClient=(httpUserAgent!=null && httpUserAgent.indexOf("Cyrus")== -1);
        return nonCyrusClient? r.replaceAll("10.0.2.2","localhost"): r;
    }

    protected PostResponse readWebObject(ByteBuffer bytebuffer, int contentLength, String httpNotify, String httpReqURL, WebObject webobject, String param){

        ByteBuffer body = Kernel.chopAtLength(bytebuffer, contentLength);
        CharBuffer jsonchars = UTF8.decode(body);
        if(Kernel.config.intPathN("network:log")==2) log("<---------------\n"+jsonchars);

        if(webobject==null){

            JSON json = null;
            if(httpContentType.startsWith("application/json")){
                json = new JSON(jsonchars);
            }
            else
            if(httpContentType.startsWith("text/cyrus")){
                json = new JSON(jsonchars,true);
            }
            else
            if(httpContentType.startsWith("application/x-www-form-urlencoded")){
                json = new JSON(decodeAndGetFormValue(jsonchars));
            }
            WebObject w=null;
            if(json!=null) try{ w=new WebObject(json, httpContentLocation, httpReqURL, httpETag, httpMaxAge, httpCacheNotify, httpNotify); }
            catch(Exception e){ e.printStackTrace(); }
            if(w==null||w.uid==null){ log("Cannot convert to WebObject:\n"+json+"\n"+w); return new PostResponse(400,null); }

            String location=funcobs.httpNotify(w);
            return new PostResponse(location==null? 200:201, location);
        }
        else{
            JSON json = null;
            if(httpContentType.startsWith("application/json")){
                json = new JSON(jsonchars);
            }
            webobject.httpNotifyJSON(json, param);
            return new PostResponse(200,null);
        }
    }

    static private String OurCacheNotify=null;
    synchronized String CacheNotify(){
        if(OurCacheNotify==null){
            OurCacheNotify=Kernel.config.stringPathN("network:cache-notify");
            if(OurCacheNotify==null) OurCacheNotify=UID.generateCN();
        }
        return OurCacheNotify;
    }
    static void setCacheNotify(String cn){
        OurCacheNotify=cn;
    }

    boolean tunnelHeaders=false;
    protected HashSet<String> getPercents(boolean includeNotify){
        HashSet<String> percents = new HashSet<String>();
        if(tunnelHeaders){
            percents.add("UID");
            percents.add("URL");
            percents.add("Version");
            percents.add("Max-Age");
        }
        if(includeNotify){
            percents.add("Notify");
        }
        return percents;
    }

    String decodeAndGetFormValue(CharBuffer cb){
        String t="i=";
        String s=cb.toString();
        if(!s.startsWith(t)) return null;
        s=s.substring(t.length());
        try{ return URLDecoder.decode(s,"UTF-8"); }catch(Throwable e){ return null; }
    }

    protected void close(String message, Exception e){
        if(message!=null) log(message);
        if(e!=null) e.printStackTrace();
        Kernel.close(channel);
    }
}


class HTTPServer extends HTTPCommon implements ChannelUser, Notifiable {

    LinkedBlockingQueue<String> longQ=null;
    boolean longPending=false;
    Thread  timer=null;

    public HTTPServer(SocketChannel channel){
        funcobs = FunctionalObserver.funcobs;
        this.channel = channel;
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){
        if(bytebuffers.size()!=0) return;
        setDoingHeaders();
        try{
            boolean ka="Keep-Alive".equalsIgnoreCase(httpConnection);
            if(ka) receiveNextEvent(Kernel.readBufferForChannel(channel), false);
            else   close(null,null);
        } catch(Exception e){ close("Failed reading - closing connection", e); }
    }

    protected void earlyEOF(){ if(Kernel.config.intPathN("network:log")==2) log("Server earlyEOF"); }

    protected void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        if(eof) return;
        Matcher m = UID.URLPATHPA().matcher(httpPath);
        if(m.matches()){
            String uid=m.group(2); if(uid==null) uid=m.group(3); if(uid==null) uid=m.group(4);
            if(uid==null) send404();
            else
            if(httpMethod.equals("GET" ) && uid.startsWith("uid-")) readGET(uid);
            else
            if(httpMethod.equals("GET" ) && uid.startsWith("c-n-")) readLong(uid);
            else
            if(httpMethod.equals("GET" )) send404();
            else
            if(httpMethod.equals("POST")) readPOST(bytebuffer, uid);
            else
            if(httpMethod.equals("HEAD")) readHEAD(uid);
            else
            if(httpMethod.equals("OPTIONS")) readOPTIONS();
        } else {
            if(httpMethod.equals("GET" )) sendFile();
            else send404();
        }
    }

    private void readGET(String uid){
        setDoingResponse();
        WebObject w=funcobs.httpObserve(this, uid, httpCacheNotify);
        if(w==null) return;
        if(("\""+w.etag+"\"").equals(httpIfNoneMatch)) send304();
        else send200(w,false,false,httpPath.endsWith(".cyr"));
    }

    private void readHEAD(String uid){
        setDoingResponse();
        WebObject w=funcobs.httpObserve(this, uid, httpCacheNotify);
        if(w==null) return;
        if(("\""+w.etag+"\"").equals(httpIfNoneMatch)) send304();
        else send200(w,false,true,false);
    }

    private void readOPTIONS(){
        setDoingResponse();
        send200(null);
    }

    /** Notifiable callback from FunctionalObserver when object is found. */
    public void notify(WebObject w){ // check if closed
        if(w.isShell()) send404();
        else
        if(("\""+w.etag+"\"").equals(httpIfNoneMatch)) send304();
        else send200(w,false,false,httpPath.endsWith(".cyr"));
    }

    synchronized private void readLong(String uid){
        setDoingResponse();
        if(!uid.equals(CacheNotify())){ send404(); return; }
        if(longQ==null) longQ=new LinkedBlockingQueue<String>();
        funcobs.http.putLongPusher(httpCacheNotify, this);
        longPending=longQ.isEmpty();
        if(longPending){
            timer=new Thread(){ public void run(){
                Kernel.sleep(LONG_POLL_TIMEOUT);
                sendNothing();
            }}; timer.start();
        }
        else{
            String notifieruid=null;
            try{ notifieruid=longQ.take(); }catch(Exception e){}
            send200(funcobs.cacheGet(notifieruid), true, false, false);
        }
    }

    synchronized public void sendNothing(){
        if(longPending){ longPending=false; send204(); }
    }

    synchronized public void longRequest(String notifieruid){
        if(longPending){ longPending=false; send200(funcobs.cacheGet(notifieruid), true, false, false); timer.interrupt(); }
        else try{ if(!longQ.contains(notifieruid)) longQ.put(notifieruid); }catch(Exception e){}
    }

    private void readPOST(ByteBuffer bytebuffer, String httpNotify) throws Exception{
        int contentLength=0;
        if(httpContentLength!=null) contentLength = Integer.parseInt(httpContentLength);
        else throw new Exception("POST without Content-Length");
        if(contentLength > bytebuffer.position()) return;
        processContent(bytebuffer, httpNotify, contentLength);
    }

    void processContent(ByteBuffer bytebuffer, String httpNotify, int contentLength){
        setDoingResponse();
        if(httpNotify.startsWith("c-n-") && !httpNotify.equals(CacheNotify())) send404();
        else
        if(contentLength >0){
            PostResponse pr=readWebObject(bytebuffer, contentLength, httpNotify, null, null, null);
            if(pr.code==200) send200(null);
            else
            if(pr.code==201) send201(pr.location);
            else
            if(pr.code==400) send400();
            else
            if(pr.code==404) send404();
            else             send400();
        }
        else send200(null);
    }

    public void send200(WebObject w){
        send200(w, false, false, false);
    }

    public void send200(WebObject w, boolean includeNotify, boolean head, boolean cyrus){
        StringBuilder sb=new StringBuilder();
        topResponseHeaders(sb, "200 OK");
        contentHeadersAndBody(sb, w, getPercents(includeNotify), head, cyrus);
        if(Kernel.config.intPathN("network:log")==1) log("200 OK-->"); else
        if(Kernel.config.intPathN("network:log")==2) log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    public void send201(String location){ sendNoBody("201 Created", "Location: "+rewrite10022(location)+"\r\n"); }

    public void send204(){ sendNoBody("204 No Content", null); }

    public void send304(){ sendNoBody("304 Not Modified",null); }

    public void send400(){ sendNoBody("400 Bad Request",null); }

    public void send404(){ sendNoBody("404 Not Found",null); }

    void sendNoBody(String responseCode, String extraHeaders){
        StringBuilder sb=new StringBuilder();
        topResponseHeaders(sb, responseCode);
        if(extraHeaders!=null) sb.append(extraHeaders);
        sb.append("Content-Length: 0\r\n\r\n");
        if(Kernel.config.intPathN("network:log")==1) log(responseCode+"-->"); else
        if(Kernel.config.intPathN("network:log")==2) log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    public void sendFile(){
        ByteBuffer bb=ByteBuffer.allocate(4096);
        topResponseHeaders(bb, "200 OK");
        if((bb=contentHeadersAndFile(bb, httpPath))==null){ send404(); return; }
        if(Kernel.config.intPathN("network:log")>=1) log("200 OK-->");
        Kernel.send(channel, bb);
    }
}


class HTTPClient extends HTTPCommon implements ChannelUser {

    boolean  inactive=false;
    boolean  running=true;

    String host;
    int    port;

    boolean needsConnect=true;
    boolean idle=true;
    boolean retryRequest;
    boolean longFailedSoWait;
    LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<Request>();
    Request request;

    boolean waitingForChunkLength=true;
    int chunkContentLength=0;
    int currentChunkLength=0;
    ByteBuffer chunkBuffer    =ByteBuffer.allocate(4096);
    ByteBuffer chunkSizeBuffer=ByteBuffer.allocate(32);

    public HTTPClient(String host, int port){
        funcobs = FunctionalObserver.funcobs;
        this.host = host;
        this.port = port;
    }

    //------------------------------------------

    synchronized void pollRequest(String path, String uid, int etag){
        try{ requests.put(new Request("POLL", path, uid, etag, null, null, null)); }catch(Exception e){}
        doNextRequestIfIdle();
    }

    synchronized void postRequest(String path, String uid, String notifieruid){
        try{ requests.put(new Request("POST", path, uid, 0, null, null, notifieruid)); }catch(Exception e){}
        doNextRequestIfIdle();
    }

    synchronized void longPollRequest(String path){
        if(!requests.isEmpty()) return;
        try{ requests.put(new Request("LONG", path, null, 0, null, null, null)); }catch(Exception e){}
        doNextRequestIfIdle();
    }

    synchronized void jsonRequest(String path, WebObject webobject, String param){
        try{ requests.put(new Request("JSON", path, null, 0, webobject, param, null)); }catch(Exception e){}
        doNextRequestIfIdle();
    }

    synchronized void retry(Request r){
        try{ requests.put(r); }catch(Exception e){}
    }

    synchronized Request nextRequest(){
        if(requests.isEmpty()){ idle=true; return null; }
        try{ return requests.take(); }catch(Exception e){} return null;
    }

    //------------------------------------------

    synchronized void doNextRequestIfIdle(){
        if(!idle) return;
        idle=false;
        doNextRequest(0);
    }

    synchronized void doNextRequest(final int sleep){
        if(sleep!=0){
            new Thread(){ public void run(){
                Kernel.sleep(sleep);
                if(Kernel.config.intPathN("network:log")==2) log("Retrying.. needsConnect="+needsConnect);
                doNextRequest(0);
            }}.start();
            return;
        }
        if(!running) return;
        request=nextRequest();
        if(request==null) return;
        setDoingHeaders();
        if(needsConnect){ needsConnect=false; Kernel.channelConnect(host, port, this); }
        else{                                 Kernel.checkWritable(channel); }
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){
        this.channel=channel;
        boolean sof = (len==0);
        if(!sof) return;
        StringBuilder sb=new StringBuilder();
        if(request.notifieruid==null){
            topRequestHeaders(sb, "GET ", host, port, request.path, request.etag);
            sb.append("\r\n");
        }
        else{
            topRequestHeaders(sb, "POST ", host, port, request.path, 0);
            contentHeadersAndBody(sb, funcobs.cacheGet(request.notifieruid), getPercents(false), false, false);
        }
        if(Kernel.config.intPathN("network:log")==2) log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    // refactor/use in incoming POST
    protected void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        if(eof) needsConnect=true;
        if("chunked".equals(httpTransferEncoding)){
            while(true){
                if(waitingForChunkLength){
                    chunkSizeBuffer=Kernel.chopAtDivider(bytebuffer, "\r\n".getBytes(), chunkSizeBuffer);
                    if(chunkSizeBuffer.limit()==0) return;
                    currentChunkLength=Integer.parseInt(ASCII.decode(chunkSizeBuffer).toString(), 16);
                    chunkSizeBuffer.rewind();
                    chunkContentLength+=currentChunkLength;
                    waitingForChunkLength=false;
                }
                if(currentChunkLength==0){
                    int p=bytebuffer.position();
                    chunkSizeBuffer=Kernel.chopAtDivider(bytebuffer, "\r\n".getBytes(), chunkSizeBuffer);
                    if(bytebuffer.position()==p) return;
                    chunkBuffer.position(chunkBuffer.limit());
                    processContent(chunkBuffer, eof, chunkContentLength);
                    chunkBuffer.rewind();
                    chunkContentLength=0;
                    waitingForChunkLength=true;
                    return;
                }
                chunkBuffer=Kernel.chopAtLength(bytebuffer, currentChunkLength, chunkBuffer);
                if(chunkBuffer.limit()!=chunkContentLength) return;
                int p=bytebuffer.position();
                chunkSizeBuffer=Kernel.chopAtDivider(bytebuffer, "\r\n".getBytes(), chunkSizeBuffer);
                if(bytebuffer.position()==p) return;
                waitingForChunkLength=true;
            }
        }
        else{
            int contentLength= -1;
            if(httpContentLength!=null) contentLength = Integer.parseInt(httpContentLength);
            if(eof) contentLength = bytebuffer.position();
            if(contentLength == -1 || contentLength > bytebuffer.position()) return;
            processContent(bytebuffer, eof, contentLength);
        }
    }

    void processContent(ByteBuffer bytebuffer, boolean eof, int contentLength){
        setDoingResponse();
        if(httpStatus.equals("201") && httpLocation!=null && request.notifieruid!=null){
            WebObject w = funcobs.cacheGet(request.notifieruid);
            w.setURL(httpLocation);
        }
        if(contentLength > 0){
            PostResponse pr=readWebObject(bytebuffer, contentLength, null, request.uid, request.webobject, request.param);
            retryRequest=false;
            longFailedSoWait=(pr.code!=200);
        }
        else{
            retryRequest    = httpStatus.startsWith("5");
            longFailedSoWait=!httpStatus.equals("204");
        }
        if(!eof && "close".equals(httpConnection)) close(null,null);

        doNextRequest(doRetryAndGetSleep());
    }

    protected void earlyEOF(){
        if(Kernel.config.intPathN("network:log")==2) log("Client earlyEOF");
        needsConnect=true;
        if(doingResponse()) return;
        setDoingResponse();
        retryRequest=true;
        longFailedSoWait=true;
        doNextRequest(doRetryAndGetSleep());
    }

    int doRetryAndGetSleep(){
        int sleep=0;
        if(!request.type.equals("LONG")){
            if(retryRequest){
                if(Kernel.config.intPathN("network:log")==2) log("Failed request for "+request.path+" - will wait then retry");
                sleep=CLIENT_RETRY_WAIT;
                retry(request);
            }
        }
        else{
            if(longFailedSoWait){
                if(Kernel.config.intPathN("network:log")==2) log("Failed long poll or connection broken to "+request.path);
                sleep=CLIENT_RETRY_WAIT;
            }
            retry(request);
        }
        return sleep;
    }

    protected void stop(){ running=false; close(null,null); }

    class Request {
        String type, path, uid; int etag; WebObject webobject; String param, notifieruid;
        Request(String t, String p, String u, int e, WebObject o, String m, String n){
            type=t; path=p; uid=u; etag=e; webobject=o; param=m; notifieruid=n;
        }
        public String toString(){
            return "Request: { type:"+type+" path:"+path+" uid:"+uid+" etag:"+etag+" webobject:"+(webobject==null?"null":webobject.uid)+
                             " param:"+param+" notifieruid:"+notifieruid+" }";
        }
    }

    public void log(Object s){ FunctionalObserver.log("HTTPClient["+host+":"+port+"] "+s); }
}










