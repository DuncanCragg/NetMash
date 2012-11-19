
package netmash.lib;

import java.util.*;
import java.util.concurrent.*;
import java.math.*;
import java.text.*;
import java.util.regex.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.net.*;

import static netmash.lib.Utils.*;

/** JSON objects.
  */
public class JSON {

    //----------------------------------

    /** Make from a JSON InputStream. */
    public JSON(InputStream is) throws UnsupportedEncodingException, IOException{
        chars = getStringFromIS(is).toCharArray();
        chp=0;
    }

    /** Make from a JSON InputStream, sumer option. */
    public JSON(InputStream is, boolean sumer) throws UnsupportedEncodingException, IOException{
        this.sumer=sumer;
        chars = getStringFromIS(is).toCharArray();
        chp=0;
    }

    /** Make from a JSON file. */
    public JSON(File file) throws FileNotFoundException, IOException{
        chars = getStringFromFile(file).array();
        chp=0;
    }

    /** Make from a JSON file, sumer option. */
    public JSON(File file, boolean sumer) throws FileNotFoundException, IOException{
        this.sumer=sumer;
        chars = getStringFromFile(file).array();
        chp=0;
    }

    final String listprepend = "{ \"list\": ";

    /** Make from a JSON string. */
    public JSON(String str){
        int hi=str.indexOf('{');
        int li=str.indexOf('[');
        if(li >=0 && !(hi >=0 && hi < li)){
            StringBuilder sb=new StringBuilder(str);
            sb.insert(0, listprepend);
            sb.append(" }");
            str = sb.toString();
        }
        chars = str.toCharArray();
        chp=0;
    }

    /** Make from given String, with switch for Sumer format. */
    public JSON(String str, boolean sumer){
        this.sumer=sumer;
        chars = str.toCharArray();
        chp=0;
    }

    /** Make from given CharBuffer, with switch for Sumer format. */
    public JSON(CharBuffer charbuffer, boolean sumer){
        this.sumer=sumer;
        chars = charbuffer.toString().toCharArray();
        chp=0;
    }

    /** Make from a JSON CharBuffer. */
    public JSON(CharBuffer charbuffer){
        int hi=indexOf(charbuffer, '{');
        int li=indexOf(charbuffer, '[');
        if(li >=0 && !(hi >=0 && hi < li)){
            CharBuffer cb = CharBuffer.allocate(listprepend.length()+charbuffer.length()+2);
            cb.append(listprepend);
            cb.append(charbuffer);
            cb.append(" }");
            charbuffer = cb;
        }
      /*if(charbuffer.hasArray()) chars = charbuffer.array();
        else                    */chars = charbuffer.toString().toCharArray();
        chp=0;
    }

    /** From top-level hash. */
    public JSON(LinkedHashMap hm){
        setTopHash(hm);
    }

    /** Shallow clone of given JSON. */
    @SuppressWarnings("unchecked")
    public JSON(JSON json){
        json.ensureContent();
        setTopHash(new LinkedHashMap(json.tophash));
    }

    //----------------------------------

    /** Get content LinkedHashMap. */
    public LinkedHashMap content(){
        ensureContent();
        return tophash;
    }

    /** Check this JSON is empty. */
    public boolean noContent(){
        LinkedHashMap hm=content();
        return hm==null || hm.size()==0;
    }

    /** Set content text */
    public void content(String json){
        chars=json.toCharArray();
        chp=0;
        setTopHash(readHashMap());
    }

    /** Set content LinkedHashMap. */
    public void content(LinkedHashMap content){
        setTopHash(content);
    }

    /** Merge this JSON with the supplied one. */
    public boolean mergeWith(JSON json){
        ensureContent();
        json.ensureContent();
        mergeHash(tophash, json.tophash);
        return true;
    }

    /** Merge this JSON with the supplied hash. */
    public boolean mergeWith(LinkedHashMap hm){
        ensureContent();
        mergeHash(tophash, hm);
        return true;
    }

    /** Replace this JSON with the supplied one. */
    @SuppressWarnings("unchecked")
    public boolean replaceWith(JSON json){
        json.ensureContent();
        setTopHash(new LinkedHashMap(json.tophash));
        return true;
    }

    //----------------------------------

    /** Get String value at the given path.
      */
    public String stringPath(String path) throws PathOvershot{
        ensureContent();
        return getStringPath(tophash, path);
    }

    /** Get String value (or String form of
      * value) at the given path. No PathOvershot.
      */
    public String stringPathN(String path){
        try{ return stringPath(path); }catch(PathOvershot po){ return null; }
    }

    /** Check whether there's anything at the path. */
    public boolean isAtPath(String path) throws PathOvershot{
        ensureContent();
        return isSet(tophash, path);
    }

    /** Check whether there's anything at the path. No PathOvershot. */
    public boolean isAtPathN(String path){
        try{ return isAtPath(path); }catch(PathOvershot po){ return false; }
    }

    /** Check String value (or String form of
      * value) at the given path is equal to given value.
      */
    public boolean isStringPath(String path, String value) throws PathOvershot{
        ensureContent();
        String pathval=getStringPath(tophash, path);
        return (value==null && pathval==null) || (value!=null && value.equals(pathval));
    }

    /** Check String value (or String form of value) at the
      * given path is equal to given value. No PathOvershot.
      */
    public boolean isStringPathN(String path, String value){
        try{ return isStringPath(path, value); }catch(PathOvershot po){ return false; }
    }

    /** Set String value. */
    public boolean stringPath(String path, String value) {
        ensureContent();
        return setStringPath(tophash, path, value);
    }

    //----------------------------------

    /** Get integer (or int form of string or
      * 0 if not integer) at the given path.
      */
    public int intPath(String path) throws PathOvershot{
        ensureContent();
        return getIntPath(tophash, path);
    }

    /** Get integer (or int form of string or
      * 0 if not integer) at the given path. No PathOvershot.
      */
    public int intPathN(String path){
        try{ return intPath(path); }catch(PathOvershot po){ return 0; }
    }

    /** Set int value.  */
    public boolean intPath(String path, int value) {
        ensureContent();
        return setIntPath(tophash, path, value);
    }

    /** Increment int value. */
    public boolean incPath(String path){
        ensureContent();
        int i; try{ i=getIntPath(tophash, path); }catch(PathOvershot po){ return false; }
        return setIntPath(tophash, path, i+1);
    }

    /** Increment double value by given delta. */
    public boolean incPath(String path, double delta){
        ensureContent();
        double d; try{ d=getDoublePath(tophash, path); }catch(PathOvershot po){ return false; }
        return setDoublePath(tophash, path, d+delta);
    }

    /** Decrement int value. */
    public boolean decPath(String path){
        ensureContent();
        int i; try{ i=getIntPath(tophash, path); }catch(PathOvershot po){ return false; }
        return setIntPath(tophash, path, i-1);
    }

    //----------------------------------

    /** Get boolean at the given path.
      * Returns false if not boolean!
      */
    public boolean boolPath(String path) throws PathOvershot{
        ensureContent();
        return getBoolPath(tophash, path);
    }

    /** Get boolean at the given path.
      * Returns false if not boolean! No PathOvershot.
      */
    public boolean boolPathN(String path){
        try{ return boolPath(path); }catch(PathOvershot po){ return false; }
    }

    /** Set boolean at the given path.
      */
    public boolean boolPath(String path, boolean value){
        ensureContent();
        return setBoolPath(tophash, path, value);
    }

    /** Get double (or 0 if not) at given path. */
    public double doublePath(String path) throws PathOvershot{
        ensureContent();
        return getDoublePath(tophash, path);
    }

    /** Get double (or 0 if not) at given path. No PathOvershot. */
    public double doublePathN(String path){
        try{ return doublePath(path); }catch(PathOvershot po){ return 0.0; }
    }

    /** Set double at given path. */
    public boolean doublePath(String path, double value){
        ensureContent();
        return setDoublePath(tophash, path, value);
    }

    //----------------------------------

    /** Get Object at the given path. */
    public Object objectPath(String path) throws PathOvershot{
        ensureContent();
        return getObjectPath(tophash, path);
    }

    /** Get Object at the given path. No PathOvershot. */
    public Object objectPathN(String path){
        try{ return objectPath(path); }catch(PathOvershot po){ return null; }
    }

    /** Set Object at the given path. */
    public boolean objectPath(String path, Object val){
        ensureContent();
        return setObjectPath(tophash, path, val);
    }

    //----------------------------------

    /** Get hash at the given path.  */
    public LinkedHashMap hashPath(String path) throws PathOvershot{
        ensureContent();
        return getHashPath(tophash, path);
    }

    /** Get hash at the given path. No PathOvershot. */
    public LinkedHashMap hashPathN(String path){
        try{ return hashPath(path); }catch(PathOvershot po){ return null; }
    }

    /** Set hash at the given path. */
    public boolean hashPath(String path, LinkedHashMap value){
        ensureContent();
        return setHashPath(tophash, path, value);
    }

    /** Get list at the given path. */
    public LinkedList listPath(String path) throws PathOvershot{
        ensureContent();
        return getListPath(tophash, path);
    }

    /** Get list at the given path. No PathOvershot. */
    public LinkedList listPathN(String path){
        try{ return listPath(path); }catch(PathOvershot po){ return null; }
    }

    /** Set list at the given path. */
    public boolean listPath(String path, List value){
        ensureContent();
        return setListPath(tophash, path, value);
    }

    /** Add to list at the given path. */
    public boolean listPathAdd(String path, Object value){
        ensureContent();
        return addListPath(tophash, path, value);
    }

    /** Add to list at the given path, taking list as set. */
    public boolean setPathAdd(String path, Object value){
        ensureContent();
        return addSetPath(tophash, path, value);
    }

    /** Push on list at the given path, taking list as set. */
    public boolean setPathPush(String path, Object value){
        ensureContent();
        return pushSetPath(tophash, path, value);
    }

    /** Add to list at the given path. */
    public boolean listPathAddAll(String path, List values){
        ensureContent();
        return addAllListPath(tophash, path, values);
    }

    /** Push on list at the given path, taking list as set. */
    public boolean setPathPushAll(String path, LinkedList values){
        ensureContent();
        return pushAllSetPath(tophash, path, values);
    }

    //----------------------------------

    /** Remove this entry in its hash or list */
    public boolean removePath(String path){
        ensureContent();
        return doRemovePath(tophash, path);
    }

    /** Remove from list at the given path. */
    public boolean listPathRemove(String path, Object value){
        ensureContent();
        return removeListPath(tophash, path, value);
    }

    /** Remove indexed item from list at the given path. */
    public boolean listPathRemove(String path, int index){
        ensureContent();
        return removeListPath(tophash, path, index);
    }

    /** Remove from list at the given path. */
    public boolean listPathRemoveAll(String path, List value){
        ensureContent();
        return removeAllListPath(tophash, path, value);
    }

    //----------------------------------

    /** Apply map to the list of hashes at path and return it. */
    @SuppressWarnings("unchecked")
    public LinkedList<LinkedHashMap> mapList(String path, JSON map){
        LinkedList in = listPathN(path);
        if(in==null) return null;
        LinkedList out = new LinkedList<LinkedHashMap>();
        for(Object o: in){
            if(!(o instanceof LinkedHashMap)) out.add(o);
            else out.add(mapHash((LinkedHashMap)o, map.content()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap mapHash(LinkedHashMap in, LinkedHashMap<String,String> map){
        LinkedHashMap out=new LinkedHashMap();
        for(Object key: map.keySet()){
            Object o = getObjectN(in, map.get(key));
            if(o!=null) out.put(key, o);
        }
        return out;
    }

    //----------------------------------

    /** Format this JSON.  */
    public String toString(){
        ensureContent();
        ensureChars();
        return new String(chars);
    }

    /** Format this JSON: in Sumer notation. */
    public String toString(boolean sumer){
        ensureContent();
        ensureChars(sumer);
        return new String(chars);
    }

    /** Format this JSON: single line of given max length. */
    public String toString(int maxlength){
        if(maxlength==0) return toString();
        ensureContent();
        ensureChars(maxlength);
        return new String(chars);
    }

    /** Format this JSON: prepend given string to top hash content. */
    public String toString(String prepend){
        ensureContent();
        ensureChars();
        if(chars.length< 2){ log("Failed toString with prepend:\n"+prepend+"\n"+this); return new String(chars); }
        return "{ "+prepend.trim()+"\n"+new String(chars).substring(2);
    }

    /** Format this JSON: prepend given string to top hash content; choose sumer. */
    public String toString(String prepend, boolean sumer){
        ensureContent();
        ensureChars(sumer);
        if(chars.length< 2){ log("Failed toString with prepend:\n"+prepend+"\n"+this); return new String(chars); }
        return "{ "+prepend.trim()+"\n"+new String(chars).substring(2);
    }

    /** Format this JSON: prepend given string to top hash content; single line of given max length. */
    public String toString(String prepend, int maxlength){
        if(maxlength==0) return toString(prepend);
        ensureContent();
        ensureChars(maxlength);
        if(chars.length< 2){ log("Failed toString with prepend:\n"+prepend+"\n"+this); return toString(); }
        return "{ "+prepend.trim()+" "+new String(chars).substring(2);
    }

    //----------------------------------
    //----------------------------------

    private char[]        chars;
    private int           chp;
    private LinkedHashMap tophash = null;
    private boolean       sumer=false;

    //----------------------------------

    static public final Charset UTF8 = Charset.forName("UTF-8");
    static public final int FILEREADBUFFERSIZE = 4096;

    private String getStringFromIS(InputStream is) throws UnsupportedEncodingException, IOException{
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"), FILEREADBUFFERSIZE);
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[FILEREADBUFFERSIZE];
        while(br.read(buffer, 0, buffer.length)!= -1) sb.append(buffer);
        return sb.toString();
    }

    private CharBuffer getStringFromFile(File file) throws FileNotFoundException, IOException{

        String path = file.getPath();

        if(!path.endsWith(".json") && !path.endsWith(".db")) throw new FileNotFoundException("JSON file name should end in '.json' or '.db': "+path);
        if(!(file.exists() && file.canRead()))               throw new FileNotFoundException("File not readable: "+path);

        FileChannel channel    = new FileInputStream(file).getChannel();
        ByteBuffer  bytebuffer = ByteBuffer.allocate((int)file.length());

        int n=channel.read(bytebuffer);

        bytebuffer.position(0);
        CharBuffer charbuffer = UTF8.decode(bytebuffer);
        return charbuffer;
    }

    //----------------------------------

    private void ensureContent(){
        if(tophash!=null) return;
        if(chars==null) return;
        setTopHash(!sumer? readHashMap(): readSumerHash());
    }

    private void setTopHash(LinkedHashMap hm){
        tophash = hm;
        chars=null;
        chp=0;
    }

    //----------------------------------

    @SuppressWarnings("unchecked")
    private Object readSumerObject(boolean terminateOnTag){
        LinkedList ll=new LinkedList();
        for(int i=0; i<40; i++, chp++){
            while(chp<chars.length && chars[chp]<=' ') chp++;
            if(chp>=chars.length){ chp--; break; }
            int chpsave=chp;
            if(chars[chp]=='{'){ ll.add(readSumerHash()); continue; }
            if(chars[chp]=='('){ ll.add(readSumerList()); continue; }
            if(chars[chp]=='"'){ ll.add(readString());    continue; }
            String s=readSumerString();
            int chpstringok=chp;
            chp=chpsave;
            if(s==null || ( terminateOnTag && s.endsWith(":")) || s.equals("}") || s.equals(")")){ chp--; chp--; break; }
            try{ ll.add(readBoolean()); continue; }catch(Exception e){ chp=chpsave; }
            try{ ll.add(readNumber());  continue; }catch(Exception e){ chp=chpsave; }
            try{ ll.add(readNull());    continue; }catch(Exception e){ chp=chpsave; }
            chp=chpstringok;
            ll.add(s);
        }
        if(ll.size()==0) return null;
        if(ll.size()==1 && terminateOnTag) return ll.get(0);
        return ll;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap readSumerHash(){
        for(; chp<chars.length; chp++) if(chars[chp]>' '){ if(chars[chp]=='{') break; else parseError('{'); }
        chp++;
        LinkedHashMap hm = new LinkedHashMap();
        boolean dotag=false;
        boolean doval=false;
        boolean doobj=false;
        StringBuilder buf=new StringBuilder();
        String tag="";
        for(; chp<chars.length; chp++){

            if(!dotag){
                if(chars[chp]=='}'){
                    break;
                }
                if(chars[chp]>' '){
                    dotag=true;
                }
                else continue;
            }

            if(!doval){
                if(chars[chp]==':'){
                    tag = new String(buf); buf = new StringBuilder();
                    doval=true;
                    continue;
                }
                if(chars[chp]>' '){
                    if(chars[chp]=='\\'){
                        appendEscapedChar(buf);
                    }
                    else buf.append(chars[chp]);
                    continue;
                }
                parseError(':');
            }

            if(!doobj){
                if(chars[chp]>' '){
                    hm.put(tag, readSumerObject(true));
                    doobj=true;
                }
                continue;
            }

            if(chars[chp]==' '){
                dotag=false;
                doval=false;
                doobj=false;
                continue;
            }

            if(chars[chp]=='}'){
                break;
            }
        }
        return hm;
    }

    @SuppressWarnings("unchecked")
    private LinkedList readSumerList(){
        chp++;
        LinkedList ll = new LinkedList();
        boolean docom=false;
        for(; chp<chars.length; chp++){
            if(chars[chp]==')'||chars[chp]=='}'){
                break;
            }
            if(chars[chp]>' '){
                Object o=readSumerObject(false);
                if(o instanceof LinkedList) ll.addAll((LinkedList)o);
                else ll.add(o);
                continue;
            }
        }
        return ll;
    }

    private String readSumerString(){
        if(chars[chp]=='"') return readString();
        StringBuilder buf = new StringBuilder();
        for(; chp<chars.length; chp++){
            if(chars[chp]<=' '){
                chp--;
                return new String(buf);
            }
            if(chars[chp]=='\\'){
                appendEscapedChar(buf);
            }
            else buf.append(chars[chp]);
        }
        return null;
    }

    //----------------------------------

    private Object readObject(){
        if(chars[chp]=='"'){
            return readString();
        }
        if(chars[chp]=='{'){
            return readHashMap();
        }
        if(chars[chp]=='['){
            return readList();
        }
        if(Character.isDigit(chars[chp]) || chars[chp]=='-'){
            return readNumber();
        }
        if(chars[chp]=='t' || chars[chp]=='f'){
            return readBoolean();
        }
        if(chars[chp]=='n'){
            return readNull();
        }
        parseError(' ');
        return null;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap readHashMap(){
        for(; chp<chars.length; chp++) if(chars[chp]>' '){ if(chars[chp]=='{') break; else parseError('{'); }
        chp++;
        LinkedHashMap hm = new LinkedHashMap();
        boolean dotag=false;
        boolean docol=false;
        boolean doval=false;
        boolean docom=false;
        StringBuilder buf=new StringBuilder();
        String tag="";
        for(; chp<chars.length; chp++){

            if(!dotag){
                if(chars[chp]=='}'){
                    break;
                }
                if(chars[chp]=='"'){
                    dotag=true;
                    continue;
                }
                if(chars[chp]>' ') parseError('"');
                continue;
            }

            if(!docol){
                if(chars[chp]=='"'){
                    tag = new String(buf); buf = new StringBuilder();
                    docol=true;
                    continue;
                }
                if(chars[chp]>=' '){
                    if(chars[chp]=='\\'){
                        appendEscapedChar(buf);
                    }
                    else buf.append(chars[chp]);
                    continue;
                }
                parseError(' ');
            }

            if(!doval){
                if(chars[chp]==':'){
                    doval=true;
                    continue;
                }
                if(chars[chp]>' ') parseError(':');
                continue;
            }

            if(!docom){
                if(chars[chp]>' '){
                    hm.put(tag, readObject());
                    docom=true;
                    continue;
                }
                continue;
            }

            if(chars[chp]==','){
                dotag=false;
                docol=false;
                doval=false;
                docom=false;
                continue;
            }

            if(chars[chp]=='}'){
                break;
            }

            if(chars[chp]>' ') parseError(',');
        }
        return hm;
    }

    private String readString(){
        chp++;
        StringBuilder buf = new StringBuilder();
        for(; chp<chars.length; chp++){
            if(chars[chp]=='"'){
                return new String(buf);
            }
            if(chars[chp]>=' '){
                if(chars[chp]=='\\'){
                    appendEscapedChar(buf);
                }
                else buf.append(chars[chp]);
                continue;
            }
            parseError('"');
        }
        parseError('"');
        return null;
    }

    @SuppressWarnings("unchecked")
    private LinkedList readList(){
        chp++;
        LinkedList ll = new LinkedList();
        boolean docom=false;
        for(; chp<chars.length; chp++){
            if(!docom){
                if(chars[chp]==']'){
                    break;
                }
                if(chars[chp]>' '){
                    ll.add(readObject());
                    docom=true;
                    continue;
                }
                continue;
            }

            if(chars[chp]==','){
                docom=false;
                continue;
            }

            if(chars[chp]==']'){
                break;
            }

            if(chars[chp]>' ') parseError(',');
        }
        return ll;
    }

    private Number readNumber(){
        StringBuilder buf = new StringBuilder();
        for(; chp<chars.length; chp++){
            if(chars[chp]<=' ' || chars[chp]==',' || chars[chp]=='}' || chars[chp]==']'){
                chp--;
                String bufs=new String(buf);
                Number n;
                if(bufs.indexOf('.')== -1){
                    boolean neg = bufs.startsWith("-");
                    int     len = bufs.length();
                    if((!neg && len >=10 ) ||
                       ( neg && len >=11 )   ){
                         n=new BigInteger(bufs);
                    }
                    else n=Integer.valueOf(bufs);
                }else    n=Double.valueOf(bufs);
                return n;
            }
            buf.append(chars[chp]);
        }
        parseError('0');
        return null;
    }

    private Boolean readBoolean(){
        if(chp+3<chars.length &&
           chars[chp+0]=='t' &&
           chars[chp+1]=='r' &&
           chars[chp+2]=='u' &&
           chars[chp+3]=='e'){
            chp+=3;
            return Boolean.valueOf(true);
        }
        if(chp+4<chars.length &&
           chars[chp+0]=='f' &&
           chars[chp+1]=='a' &&
           chars[chp+2]=='l' &&
           chars[chp+3]=='s' &&
           chars[chp+4]=='e'){
            chp+=4;
            return Boolean.valueOf(false);
        }
        parseError('0');
        return null;
    }

    private Object readNull(){
        if(chp+3<chars.length &&
           chars[chp+0]=='n' &&
           chars[chp+1]=='u' &&
           chars[chp+2]=='l' &&
           chars[chp+3]=='l'){
            chp+=3;
            return null;
        }
        parseError('0');
        return null;
    }

    private void appendEscapedChar(StringBuilder buf){
        chp++;
        if(chars[chp]=='"')  buf.append(chars[chp]);
        if(chars[chp]=='\\') buf.append(chars[chp]);
        if(chars[chp]=='/')  buf.append(chars[chp]);
        if(chars[chp]=='b')  buf.append('\b');
        if(chars[chp]=='f')  buf.append('\f');
        if(chars[chp]=='n')  buf.append('\n');
        if(chars[chp]=='r')  buf.append('\r');
        if(chars[chp]=='t')  buf.append('\t');
        if(chars[chp]=='u')  buf.append(getUTF8());
    }

    private char getUTF8(){
        StringBuilder hex = new StringBuilder();
        hex.append(chars[++chp]);
        hex.append(chars[++chp]);
        hex.append(chars[++chp]);
        hex.append(chars[++chp]);
        return (char)Integer.parseInt(hex.toString(),16);
    }

    public class Syntax extends RuntimeException{ public Syntax(String s){ super(s); } }

    private void parseError(char ch){
        throw new Syntax("\nExpecting '"+ch+"': ["+showContext(chp, chars)+"]\nat char "+chp+ " in\n"+new String(chars));
    }

    static public String showContext(int chp, char[] chars){
        String r="";
        int c=chp-10;
        if(c<0) c=0;
        for(; c<chp+10 && c<chars.length; c++){
            if(c==chp) r+=">>"+chars[c]+"<<";
            else       r+=chars[c];
        }
        return r;
    }

    //----------------------------------

    private boolean isSet(LinkedHashMap content, String path) throws PathOvershot {
        return getObject(content, path)!=null;
    }

    private String getStringPath(LinkedHashMap content, String path) throws PathOvershot{
        Object o=getObject(content, path);
        if(o instanceof String){ return (String)o; }
        return null;
    }

    private int getIntPath(LinkedHashMap content, String path) throws PathOvershot{
        Object o=getObject(content, path);
        if(o instanceof Number){ Number n=(Number)o; return n.intValue(); }
        if(o instanceof String) try{ return Integer.parseInt((String)o); } catch(NumberFormatException e){}
        return 0;
    }

    private boolean getBoolPath(LinkedHashMap content, String path) throws PathOvershot{
        return findBooleanIn(getObject(content, path));
    }

    private double getDoublePath(LinkedHashMap content, String path) throws PathOvershot{
        return findNumberIn(getObject(content, path));
    }

    private Object getObjectPath(LinkedHashMap content, String path) throws PathOvershot{
        return getObject(content, path);
    }

    private LinkedHashMap getHashPath(LinkedHashMap content, String path) throws PathOvershot{
        return findHashIn(getObject(content, path));
    }

    private LinkedList getListPath(LinkedHashMap content, String path) throws PathOvershot{
        return findListIn(getObject(content, path));
    }

    static private Object getObjectN(LinkedHashMap hashmap, String path){
        try{ return getObject(hashmap, path); }catch(PathOvershot po){ return null; }
    }

    @SuppressWarnings("unchecked")
    static private Object getObject(LinkedHashMap hashmap, String path) throws PathOvershot{
        path=path.trim();
        if(path.length() >0 && path.charAt(0)==':') path=path.substring(1);
        String[] parts=splitPath(path);
        LinkedHashMap<String,Object> hm=hashmap;
        for(int i=0; i<parts.length; i++){
            Object o=null;
            String part=parts[i];
            if(part.equals("*")) o=getSingleEntry(hm);
            else
            if(part.equals("#")) o=hm;
            else                 o=hm.get(part);
            if(o==null) return null;
            if(i==parts.length-1) return o;
            if(o instanceof LinkedHashMap){
                hm=(LinkedHashMap)o;
                continue;
            }
            if(o instanceof LinkedList){
                LinkedList ll=(LinkedList)o;
                while(true){
                    i++;
                    String sx=parts[i];
                    int x=0; try{ x = Integer.parseInt(sx); }catch(Exception e){}
                    if(!(sx.equals("0") || x>0)) return null;
                    if(x>=ll.size()) return null;
                    o = ll.get(x);
                    if(o==null) return null;
                    if(i==parts.length-1) return o;
                    if(o instanceof LinkedList){ ll=(LinkedList)o; continue; }
                    if(o instanceof LinkedHashMap){
                        hm=(LinkedHashMap)o;
                        break;
                    }
                    throw new PathOvershot(o, parts, i);
                }
                continue;
            }
            if(o instanceof String){
                throw new PathOvershot(o, parts, i);
            }
            if(o instanceof Number){
                throw new PathOvershot(o, parts, i);
            }
            if(o instanceof Boolean){
                throw new PathOvershot(o, parts, i);
            }
        }
        return null;
    }

    static private Object getSingleEntry(LinkedHashMap<String,Object> hm){
        if(hm.size()!=1) return null;
        for(String key: hm.keySet()) return hm.get(key);
        return null;
    }

    private boolean doRemovePath(LinkedHashMap hashmap, String path){
        return setObject(hashmap, path, null);
    }

    private boolean setStringPath(LinkedHashMap hashmap, String path, String value){
        return setObject(hashmap, path, value);
    }

    private boolean setIntPath(LinkedHashMap hashmap, String path, int value){
        return setObject(hashmap, path, Integer.valueOf(value));
    }

    private boolean setDoublePath(LinkedHashMap hashmap, String path, double value){
        return setObject(hashmap, path, Double.valueOf(value));
    }

    private boolean setBoolPath(LinkedHashMap hashmap, String path, boolean value){
        return setObject(hashmap, path, Boolean.valueOf(value));
    }

    private boolean setHashPath(LinkedHashMap hashmap, String path, LinkedHashMap value){
        return setObject(hashmap, path, value);
    }

    private boolean setListPath(LinkedHashMap hashmap, String path, List value){
        return setObject(hashmap, path, value);
    }

    private boolean setObjectPath(LinkedHashMap content, String path, Object value){
        return setObject(content, path, value);
    }

    @SuppressWarnings("unchecked")
    private boolean addListPath(LinkedHashMap hashmap, String path, Object value){
        LinkedList list;
        try{ list=getOrMakeListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        list.add(value);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean addSetPath(LinkedHashMap hashmap, String path, Object value){
        LinkedList list;
        try{ list=getOrMakeListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        if(!list.contains(value)){ list.add(value); return true; }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean pushSetPath(LinkedHashMap hashmap, String path, Object value){
        LinkedList list;
        try{ list=getOrMakeListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        if(!list.contains(value)){ list.addFirst(value); return true; }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean pushAllSetPath(LinkedHashMap hashmap, String path, LinkedList values){
        LinkedList list;
        try{ list=getOrMakeListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        boolean changed=false;
        for(Object o: values) if(!list.contains(o)){ list.addFirst(o); changed=true; }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private boolean addAllListPath(LinkedHashMap hashmap, String path, List values){
        if(values.isEmpty()) return false;
        LinkedList list;
        try{ list=getOrMakeListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        list.addAll(values);
        return true;
    }

    @SuppressWarnings("unchecked")
    private LinkedList getOrMakeListPath(LinkedHashMap hashmap, String path) throws PathOvershot{
        LinkedList list=null;
        Object o=getObject(hashmap, path);
        if(o==null || !(o instanceof LinkedList)){
           list = new LinkedList();
           if(o!=null) list.add(o);
           setListPath(hashmap, path, list);
        }
        else list=(LinkedList)o;
        return list;
    }

    @SuppressWarnings("unchecked")
    private boolean removeListPath(LinkedHashMap hashmap, String path, Object value){
        LinkedList list=null;
        try{ list = getListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        if(list==null) return false;
        return list.remove(value);
    }

    @SuppressWarnings("unchecked")
    private boolean removeListPath(LinkedHashMap hashmap, String path, int index){
        LinkedList list=null;
        try{ list = getListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        if(list==null) return false;
        if(index< 0 || index>=list.size()) return false;
        list.remove(index);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean removeAllListPath(LinkedHashMap hashmap, String path, List value){
        LinkedList list=null;
        try{ list = getListPath(hashmap, path);
        }catch(PathOvershot po){ return false; }
        if(list==null) return false;
        list.removeAll(value);
        return true;
    }

    private void mergeHash(LinkedHashMap hashmap, LinkedHashMap other){
        for(Iterator it=other.keySet().iterator(); it.hasNext(); ){
            String tag = (String)it.next();
            Object val = other.get(tag);
            setObject(hashmap, tag, val);
        }
    }

    @SuppressWarnings("unchecked")
    static private boolean setObject(LinkedHashMap hashmap, String path, Object value){
        path=path.trim();
        boolean changed = false;
        if(path.length() >0 && path.charAt(0)==':') path=path.substring(1);
        String[] parts=splitPath(path);
        LinkedHashMap<String,Object> hm=hashmap;
        for(int i=0; i<parts.length; i++){
            Object o=null;
            String part=parts[i];
            if(!part.equals("*")) o=hm.get(part);
            else                  o=getSingleEntry(hm);
            if(o==null){
                if(i==parts.length-1){
                    if(value!=null){ hm.put(part, value); changed=true; }
                    else changed=false;
                }
                return changed;
            }
            if(o instanceof LinkedHashMap){
                if(i==parts.length-1){
                    if(value!=null){
                        if(!o.equals(value)){
                            hm.put(part, value); changed=true;
                        }
                    }
                    else changed=hm.remove(part)!=null;
                    return changed;
                }
                hm=(LinkedHashMap)o;
                continue;
            }
            if(o instanceof LinkedList){
                if(i==parts.length-1){
                    if(value!=null){
                        if(!o.equals(value)){
                            hm.put(part, value); changed=true;
                        }
                    }
                    else changed=hm.remove(part)!=null;
                    return changed;
                }
                LinkedList ll=(LinkedList)o;
                while(true){
                    String sx=parts[i+1];
                    int x=0; try{ x = Integer.parseInt(sx); }catch(Exception e){}
                    if((sx.equals("0") || x>0) && x<ll.size()){
                       o = ll.get(x);
                       if(i+1==parts.length-1){
                           if(value!=null){
                               if(!o.equals(value)){
                                   ll.set(x, value); changed=true;
                               }
                           }
                           else{ ll.remove(x); changed=true; }
                           return changed;
                       }
                       if(o instanceof LinkedList){
                           ll=(LinkedList)o;
                           i++;
                           continue;
                       }
                       if(o instanceof LinkedHashMap){
                           hm=(LinkedHashMap)o;
                           i++;
                           break;
                       }
                    }
                    return false;
                }
                continue;
            }
            if(o instanceof String){
                if(i==parts.length-1){
                    if(value!=null){
                        String p = (String)o;
                        String n = value.toString();
                        if(!n.equals(p)){
                            hm.put(part, value); changed=true;
                        }
                    }
                    else changed=hm.remove(part)!=null;
                }
                return changed;
            }
            if(o instanceof Number){
                if(i==parts.length-1){
                    if(value!=null){
                        Number p = (Number)o;
                        Number n = toNumber(value);
                        if(!n.equals(p)){
                            hm.put(part, toNumberIfPoss(value)); changed=true;
                        }
                    }
                    else changed=hm.remove(part)!=null;
                }
                return changed;
            }
            if(o instanceof Boolean){
                if(i==parts.length-1){
                    if(value!=null){
                        Boolean p = (Boolean)o;
                        Boolean n = toBoolean(value);
                        if(!n.equals(p)){
                            hm.put(part, toBooleanIfPoss(value)); changed=true;
                        }
                    }
                    else changed=hm.remove(part)!=null;
                }
                return changed;
            }
        }
        return changed;
    }

    //----------------------------------

    private void ensureChars(){
        ensureChars(0);
    }

    private void ensureChars(int maxlength){
        this.sumer=false;
        String str=(tophash!=null)? hashToString(tophash,2,maxlength,false): "";
        chars = str.toCharArray();
        chp=0;
    }

    private void ensureChars(boolean sumer){
        this.sumer=sumer;
        String str=(tophash!=null)? hashToString(tophash,4,0,sumer): "";
        chars = str.toCharArray();
        chp=0;
    }

    private String objectToString(Object o, int indent, int maxlength, boolean sumer){
        if(o==null) return "null";
        if(o instanceof Number)        return toNicerString((Number)o);
        if(o instanceof String)        return stringToString((String)o, sumer);
        if(o instanceof LinkedHashMap) return hashToString((LinkedHashMap)o, indent+2, maxlength, sumer);
        if(o instanceof LinkedList)    return listToString((LinkedList)   o, indent+2, maxlength, sumer);
        return o.toString();
    }

    private String stringToString(String s, boolean sumer){
        if(s.equals("")) return "\"\"";
        String r=replaceEscapableChars(s);
        if(!sumer || r.indexOf(" ")!= -1) return "\""+r+"\"";
        else return r;
    }

    private String hashToString(LinkedHashMap hm, int indent, int maxlength, boolean sumer){
        if(hm==null) return "null";
        if(hm.size()==0) return "{ }";
        String quote=(sumer?"":"\"");
        boolean structured=false;
        if(maxlength==0){
            int i=0;
            int w=0;
            for(Iterator it=hm.keySet().iterator(); it.hasNext(); i++){
                String tag = (String)it.next();
                Object val = hm.get(tag);
                if(val instanceof LinkedHashMap){ structured=true; break; }
                if(val instanceof LinkedList){ structured=true; break; }
                if(val instanceof String){
                    String s=(String)val;
                    w+=tag.length()+3+s.length();
                }
                else w+=tag.length()+3+5;
                if(w>80){ structured=true; break; }
                if(i>10){ structured=true; break; }
            }
        }
        StringBuilder buf=new StringBuilder();
        buf.append(structured? "{\n": "{ ");
        int i=0;
        for(Iterator it=hm.keySet().iterator(); it.hasNext(); i++){
            String tag = (String)it.next();
            Object val = hm.get(tag);
            if(structured){
                if(i==0) buf.append(                  indentation(indent));
                else     buf.append((sumer?"\n":",\n")+indentation(indent));
            } else buf.append(i==0||sumer? " ": ", ");
            buf.append(quote+tag+quote+": "+objectToString(val, indent, maxlength, sumer));
        }
        if(structured) buf.append("\n"+indentation(indent-2)+"}");
        else           buf.append(" }");
        return buf.toString();
    }

    private String listToString(LinkedList ll, int indent, int maxlength, boolean sumer){
        if(ll==null)  return "null";
        if(ll.size()==0) return sumer? "( )": "[ ]";
  //    if(ll.size()==1 && sumer) return objectToString(ll.get(0), indent, maxlength, sumer);
        boolean structured=false;
        String ob=sumer? "(": "[";
        String cb=sumer? ")": "]";
        if(maxlength==0){
            int i=0;
            int w=0;
            for(Object val: ll){
                if(val instanceof LinkedHashMap){ structured=true; break; }
                if(val instanceof LinkedList){
                    LinkedList l=((LinkedList)val);
                    if(l.size()==1 && (l.get(0) instanceof String)) w+=((String)(l.get(0))).length();
                    else
                    if(l.size()==2 && (l.get(0) instanceof String) && (l.get(1) instanceof String)) w+=((String)(l.get(0))).length()+((String)(l.get(1))).length();
                    else
                    if(l.size() >0){ structured=true; break; }
                }
                else
                if(val instanceof String){
                    String s=(String)val;
                    w+=s.length();
                }
                else w+=5;
                if(w>80){ structured=true; break; }
                if(i>10){ structured=true; break; }
                i++;
            }
        }
        StringBuilder buf=new StringBuilder();
        buf.append(structured? ob+"\n": ob);
        int i=0;
        for(Object val: ll){
            if(structured){
                if(i==0) buf.append(                  indentation(indent));
                else     buf.append((sumer?"\n":",\n")+indentation(indent));
            } else buf.append((i==0||sumer)? " ": ", ");
            buf.append(objectToString(val, indent, maxlength, sumer));
            i++;
        }
        if(structured) buf.append("\n"+indentation(indent-2)+" "+cb);
        else           buf.append(" "+cb);
        return buf.toString();
    }

    // -----------------------------

    static private Number toNumber(Object o){
        if(o instanceof Number) return (Number)o;
        if(o instanceof String) try{ return Double.parseDouble((String)o); } catch(NumberFormatException e){}
        return Double.valueOf(0);
    }

    static private Boolean toBoolean(Object o){
        if(o instanceof Boolean) return (Boolean)o;
        if(o instanceof String){
            if(((String)o).toLowerCase().equals("true" )) return Boolean.valueOf(true);
            if(((String)o).toLowerCase().equals("false")) return Boolean.valueOf(false);
        }
        return Boolean.valueOf(false);
    }

    static private Object toNumberIfPoss(Object o){
        if(o instanceof String) try{ return Double.parseDouble((String)o); } catch(NumberFormatException e){}
        return o;
    }

    static private Object toBooleanIfPoss(Object o){
        if(o instanceof String){
            if(((String)o).toLowerCase().equals("true" )) return Boolean.valueOf(true);
            if(((String)o).toLowerCase().equals("false")) return Boolean.valueOf(false);
        }
        return o;
    }

    static public final String INDENTSPACES = "                                                                                                                                                                                                                                                        ";
    private String indentation(int indent){
        if(indent>=INDENTSPACES.length()) return INDENTSPACES;
        return INDENTSPACES.substring(0,indent);
    }

    static public String replaceEscapableChars(String s){
        return s.replace("\\", "\\\\")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    static private ConcurrentHashMap<String,String[]> splitCache=new ConcurrentHashMap<String,String[]>();

    static private String[] splitPath(String path){
        String[] r=splitCache.get(path);
        if(r==null){ r=path.split(":"); splitCache.put(path,r); }
        return r;
    }

    /* ---------------------------------------------------- */

    static public final boolean enableLogging=true;

    static public void log(Object o){
        log(enableLogging, o);
    }

    static public void logXX(Object o){
        log(enableLogging, "xxxxxx "+o);
    }

    static public void log(boolean doit, Object o){
        if(!doit) return;
        String thread=Thread.currentThread().toString();
        System.out.println("["+thread+"]: "+o);
    }

    static public void whereAmI(){
        try{ throw new Exception(); } catch(Exception e){ e.printStackTrace(); }
    }

    static public int indexOf(CharBuffer cb, char ch){
        for(int i=0; i<cb.length(); i++) if(cb.get(i)==ch) return i;
        return -1;
    }

    /* ---------------------------------------------------- */
}


