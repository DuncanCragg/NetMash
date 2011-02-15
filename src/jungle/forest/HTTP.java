package jungle.forest;

import static java.util.Arrays.*;

import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

import jungle.Version;
import jungle.lib.JSON;
import jungle.platform.*;

/** HTTP and REST: the Object Web.
  */
public class HTTP implements ChannelUser {

    static public final String  WURLRE = "http://([^:]+)(:([0-9]+))?(/.*/uid-[-0-9a-f]+.json)";
    static public final Pattern WURLPA = Pattern.compile(WURLRE);
    static public final String   URLRE = "http://([^:/]+)(:([0-9]+))?(/.*)";
    static public final Pattern  URLPA = Pattern.compile(URLRE);

    // ----------------------------------------

    public HTTP(){
        int port = Kernel.config.intPathN("network:port");
        Kernel.listen(port, this);
        System.out.println("HTTP: initialised. Listening on port "+port);
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

    private HTTPClient poolClient(String host, int port){
        String key = host+":"+port;
        HTTPClient client = connectionPool.get(key);
        if(client!=null) return client;
        client=new HTTPClient(host, port);
        connectionPool.put(key, client);
        return client;
    }

    private List getClient(WebObject w){
        Matcher m = WURLPA.matcher(w.uid);
        if(!m.matches()){ FunctionalObserver.log("Remote pull UID isn't a good URL: "+w.uid); return null; }
        String host = m.group(1);
        int    port = m.group(3)!=null? Integer.parseInt(m.group(3)): 80;
        String path = m.group(4);
        return asList(poolClient(host, port), encodeSpacesAndUTF8IntoPercents(path));
    }

    private List getClient(String url){
        Matcher m = URLPA.matcher(url);
        if(!m.matches()){ FunctionalObserver.log("Remote GET URL syntax: "+url); return null; }
        String host = m.group(1);
        int    port = m.group(3)!=null? Integer.parseInt(m.group(3)): 80;
        String path = m.group(4);
        return asList(poolClient(host, port), encodeSpacesAndUTF8IntoPercents(path));
    }

    void pull(WebObject s){
        List clientpath = getClient(s);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        client.get(path);
    }

    void push(WebObject w){
        List clientpath = getClient(w);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        for(String notifieruid: w.alertedin){
            client.post(path, notifieruid);
        }
    }

    void poll(WebObject w){
FunctionalObserver.log("poll\n"+w);
    }

    void getJSON(String url, WebObject w){
        List clientpath = getClient(url);
        if(clientpath==null) return;
        HTTPClient client = (HTTPClient)clientpath.get(0);
        String     path   = (String)    clientpath.get(1);
        client.get(path, w);
    }

    // ----------------------------------------------

    static public String encodeSpacesAndUTF8IntoPercents(String path){
        return path;
    }

    static public String queryAndFormEncode(String path){
        String epath=null;
        try{ epath=URLEncoder.encode(path,"UTF-8"); }catch(Exception e){}
        return epath;
    }
}


abstract class HTTPCommon {

    static public final Charset UTF8  = Charset.forName("UTF-8");
    static public final Charset ASCII = Charset.forName("US-ASCII");

    static public final String  UIDRE = Kernel.config.stringPathN("network:pathprefix")+"(uid-[-0-9a-f]+).json";
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
    protected String httpIfNoneMatch=null;
    protected String httpContentLocation=null;
    protected String httpEtag=null;
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
        if(Kernel.config.boolPathN("network:log")) FunctionalObserver.log("<---------------\n"+headchars);
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
        while(chars[chp++]!='\n');
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
        if(tag.equals("If-None-Match")){    httpIfNoneMatch=val; return; }
        if(tag.equals("Content-Location")){ httpContentLocation=val; return; }
        if(tag.equals("Etag")){             httpEtag=val.substring(1,val.length()-1); return; }
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

    protected void contentHeadersAndBody(StringBuilder sb, WebObject w, HashSet<String> percents){
        sb.append("Content-Location: "); sb.append(UID.toURL(w.uid)); sb.append("\r\n");
        sb.append("Etag: \""); sb.append(w.etag); sb.append("\"\r\n");
        if(w.maxAge>=0){
        sb.append("Cache-Control: max-age="); sb.append(w.maxAge); sb.append("\r\n");}
        sb.append("Content-Type: application/json\r\n");
        String content=w.toString(percents);
        sb.append("Content-Length: "); sb.append(content.getBytes().length); sb.append("\r\n");
        sb.append("\r\n");
        sb.append(content);
    }

    protected boolean readWebObject(ByteBuffer bytebuffer, String uid, int contentLength, WebObject webobject){

        ByteBuffer body = Kernel.chopAtLength(bytebuffer, contentLength);
        CharBuffer jsonchars = UTF8.decode(body);
        if(Kernel.config.boolPathN("network:log")) FunctionalObserver.log("<---------------\n"+jsonchars);
        JSON json = new JSON(jsonchars);

        if(webobject==null){
            WebObject w=null;
            try{ w=new WebObject(json, httpContentLocation, httpEtag, null); }
            catch(Exception e){ return false; }
            if(w==null) return false;
            if(uid!=null) w.notify.add(uid);
            funcobs.httpNotify(w);
        }
        else{
            webobject.httpNotifyJSON(json);
        }
        return true;
    }

    boolean tunnelHeaders=false;
    protected HashSet<String> getPercents(){
        HashSet<String> percents = new HashSet<String>();
        if(tunnelHeaders){
            percents.add("%uid");
            percents.add("%etag");
            percents.add("%max-age");
        }
        return percents;
    }
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
        } catch(Exception e){
            if(e.getMessage()==null || e.getMessage().equals("null")) e.printStackTrace();
            System.err.println("Failed reading event ("+e.getMessage()+") - closing connection");
            doingHeaders=true;
            Kernel.close(channel);
        }
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){
        try{
            if(bytebuffers.size()==0){
                boolean ka=httpConnection.equalsIgnoreCase("Keep-Alive");
                if(ka) receiveNextEvent(Kernel.rdbuffers.get(channel), false);
                else   Kernel.close(channel);
            }
        } catch(Exception e){
            System.err.println("Failed reading event ("+e.getMessage()+") - closing connection");
            Kernel.close(channel);
        }
    }

    protected void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        if(eof) return;
        Matcher m = UIDPA.matcher(httpPath);
        if(m.matches()){
            String uid = m.group(1);
            if(httpMethod.equals("GET")){ if(!readGET(uid)) return; }
            else
            if(httpMethod.equals("POST")) if(!readPOST(bytebuffer, uid)) return;
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
            if(readWebObject(bytebuffer, uid, contentLength, null)) send200(null);
            else                                                    send404(); // not 404!
        }
        return true;
    }

    /** Notifiable callback from FunctionalObserver when object is found. */
    public void notify(WebObject w){
        send200(w);
        doingHeaders=true;
    }

    public void send200(WebObject w){
        StringBuilder sb=all200Headers(w, getPercents());
        if(Kernel.config.boolPathN("network:log")) FunctionalObserver.log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    public void send304(){
        StringBuilder sb=topHeaders("304 Not Modified");
        sb.append("Content-Length: 0\r\n\r\n");
        if(Kernel.config.boolPathN("network:log")) FunctionalObserver.log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    public void send404(){
        StringBuilder sb=topHeaders("404 Not Found");
        sb.append("Content-Length: 0\r\n\r\n");
        if(Kernel.config.boolPathN("network:log")) FunctionalObserver.log("--------------->\n"+sb);
        Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
    }

    private StringBuilder all200Headers(WebObject w, HashSet<String> percents){
        StringBuilder sb=topHeaders("200 OK");
        if(w==null) sb.append("Content-Length: 0\r\n\r\n");
        else contentHeadersAndBody(sb, w, percents);
        return sb;
    }
    
    static public final DateFormat RFC1123 = new java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'");
    static { RFC1123.setTimeZone(TimeZone.getTimeZone("GMT")); }

    private StringBuilder topHeaders(String responseCode){
        StringBuilder sb=new StringBuilder();
        sb.append(httpProtocol.equals("HTTP/1.1")? "HTTP/1.1 ": "HTTP/1.0 ");
        sb.append(responseCode); sb.append("\r\n");
        sb.append("Connection: "); sb.append(httpConnection); sb.append("\r\n");
        sb.append("Date: "); sb.append(RFC1123.format(new Date())); sb.append("\r\n");
        sb.append("Server: "+Version.NAME+" "+Version.NUMBERS+"\r\n");
        return sb;
    }
}


class HTTPClient extends HTTPCommon implements ChannelUser  {

    private String    host;
    private int       port;
    private String    path;
    private WebObject webobject;
    private String    notifieruid=null;
    private boolean   connected=false;

    public HTTPClient(String host, int port){
        funcobs = FunctionalObserver.funcobs;
        this.host = host;
        this.port = port;
    }

    public void get(String path){
        this.path = path;
        if(!connected) channel = Kernel.channelConnect(host, port, this);
        else writable(channel, null, 0);
    }

    public void get(String path, WebObject w){
        this.path = path;
        this.webobject = w;
        if(!connected) channel = Kernel.channelConnect(host, port, this);
        else writable(channel, null, 0);
    }

    public void post(String path, String notifieruid){
        this.path = path;
        this.notifieruid = notifieruid;
        if(!connected) channel = Kernel.channelConnect(host, port, this);
        else writable(channel, null, 0);
    }

    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len){
        boolean sof = (len==0);
        if(sof){
            connected = true;
            StringBuilder sb=new StringBuilder();
            if(notifieruid==null){
                sb.append("GET "); sb.append(path); sb.append(" HTTP/1.1\r\n");
                sb.append("Host: "); sb.append(host); sb.append(":"+port); sb.append("\r\n");
                sb.append("User-Agent: "+Version.NAME+" "+Version.NUMBERS+"\r\n");
                sb.append("\r\n");
            }
            else{
                WebObject w = funcobs.cacheGet(notifieruid);
                sb.append("POST "); sb.append(path); sb.append(" HTTP/1.1\r\n");
                sb.append("Host: "); sb.append(host); sb.append(":"+port); sb.append("\r\n");
                sb.append("User-Agent: "+Version.NAME+" "+Version.NUMBERS+"\r\n");
                contentHeadersAndBody(sb, w, getPercents());
                notifieruid=null;
            }
            if(Kernel.config.boolPathN("network:log")) FunctionalObserver.log("--------------->\n"+sb);
            Kernel.send(channel, ByteBuffer.wrap(sb.toString().getBytes()));
        }
    }

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len){
        boolean eof=(len== -1);
        try{
            if(eof && doingHeaders && path!=null) throw new Exception("Connection was closed");
            receiveNextEvent(bytebuffer, eof);
        } catch(Exception e){
            if(e.getMessage()==null || e.getMessage().equals("null")) e.printStackTrace();
            System.err.println("Failed reading event ("+e.getMessage()+") - closing connection");
            path=null;
            doingHeaders=true;
            Kernel.close(channel);
            connected=false;
        }
    }

    protected void readContent(ByteBuffer bytebuffer, boolean eof) throws Exception{
        int contentLength=0;
        if(httpContentLength!=null) contentLength = Integer.parseInt(httpContentLength);
        if(eof) contentLength = bytebuffer.position();
        if(contentLength == -1 || contentLength > bytebuffer.position()) return;
        if(contentLength > 0) readWebObject(bytebuffer, null, contentLength, webobject);
        path=null;
        doingHeaders=true;
    }
}










