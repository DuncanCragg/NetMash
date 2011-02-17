
package jungle.platform;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;

import jungle.lib.JSON;

/**  This is the runnable container or kernel class. 
  *  Requires jungle-config.json giving Threadpool size.
  *  Runs event loop, handles NIO, handles Threadpool
  *  and gives events/callbacks to runmodule.
  */
public class Kernel {

    //-----------------------------------------------------

    static public final int SOCKREADBUFFERSIZE = 2048; // * 60,000 = 120Mb
    static public final int FILEREADBUFFERSIZE = 4096;

    //-----------------------------------------------------

    static public JSON   config;
    static public Module runmodule;

    static private ExecutorService threadPool;

    //-----------------------------------------------------

    static public void init(Module runmod){
        initConfig();
        initNetwork();
        threadPool=Executors.newFixedThreadPool(config.intPathN("kernel:threadpool"));
        runmodule=runmod;
    }

    static public void run(){
        runmodule.run();
        eventLoop();
    }

    //-----------------------------------------------------

    /** Post an Object here to put it into a queue to be
      * assigned its own thread. Comes back on threadedObject()
      * callback of Module interface.
    */
    static public void threadObject(final Object o){
        if(o==null) return;
        threadPool.execute(new Runnable(){ public void run(){ synchronized(o){ runmodule.threadedObject(o); } } });
    }

    //-----------------------------------------------------

    static public void listen(int port, ChannelUser channeluser){
        ServerSocketChannel listener = null;
        try {
            listener = ServerSocketChannel.open();
            listener.configureBlocking(false);
            listener.socket().setReuseAddress(true);
            listener.socket().bind(new InetSocketAddress(port), 1024);
            listener.register(selector, SelectionKey.OP_ACCEPT);
            listeners.put(listener, channeluser);

        } catch(BindException e){ bailOut("Could not bind to port "+port, e, listener);
        } catch(IOException   e){ bailOut("Could not bind to port "+port, e, listener); }
    }

    static public SocketChannel channelConnect(String host, int port, ChannelUser channeluser){
        SocketChannel channel = null;
        try{
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().setTcpNoDelay(true);
            channel.connect(new InetSocketAddress(host, port));
            channel.register(selector, SelectionKey.OP_CONNECT);
            channels.put(channel, channeluser);

        } catch(IOException e){ bailOut("Could not connect to "+host+":"+port, e, channel); }

        return channel;
    }

    static public void send(SocketChannel channel, ByteBuffer bytebuffer){
        appendToWriteBuffers(channel, bytebuffer);
        setWriting(channel);
    }

    static public void readFile(File file, FileUser fileuser) throws Exception{
        if(!(file.exists() && file.canRead())) throw new Exception("Can't read file "+file.getPath());

        FileChannel channel    = new FileInputStream(file).getChannel();
        ByteBuffer  bytebuffer = ByteBuffer.allocate(FILEREADBUFFERSIZE);

        int n=0;
        while(n!= -1){
            n=channel.read(bytebuffer);
            fileuser.readable(bytebuffer, n);
        }
    }

    static public void writeFile(File file, boolean append, ByteBuffer bytebuffer, FileUser fileuser) throws Exception{
        if(!(file.exists() && file.canWrite()))  throw new Exception("Can't write file "+file.getPath());

        FileChannel channel = new FileOutputStream(file, append).getChannel();

        int n=channel.write(bytebuffer);
        fileuser.writable(bytebuffer, n);
    }

    static public ByteBuffer chopAtDivider(ByteBuffer bytebuffer, byte[] divider){
        return doChopAtDivider(bytebuffer, divider);
    }

    static public ByteBuffer chopAtLength(ByteBuffer bytebuffer, int length){
        return doChopAtLength(bytebuffer, length);
    }

    static public void close(SocketChannel channel){
        closeSelectableChannel(channel);
    }

    //-----------------------------------------------------

    static private Selector selector;
    static private HashMap<ServerSocketChannel,ChannelUser> listeners = new HashMap<ServerSocketChannel,ChannelUser>();
    static private HashMap<SocketChannel,ChannelUser>       channels  = new HashMap<SocketChannel,ChannelUser>();
    static public  HashMap<SocketChannel,ByteBuffer>        rdbuffers = new HashMap<SocketChannel,ByteBuffer>();
    static public  HashMap<SocketChannel,Queue<ByteBuffer>> wrbuffers = new HashMap<SocketChannel,Queue<ByteBuffer>>();

    //-----------------------------------------------------

    static private void initConfig(){
        try{
            config = new JSON(new File("./jungle-config.json"));
        } catch(Exception e){ bailOut("Error in config file", e, null); }
    }

    static private void initNetwork(){
        try{
            selector = Selector.open();
        } catch(IOException e){ bailOut("Could not open selector", e, null); }
    }

    //-----------------------------------------------------

    static private void eventLoop(){


        System.out.println("Kernel: running "+config.stringPathN("name"));

        while(true){
            try {
                checkSelector();
                selector.select(1);
            }catch(Throwable t) {
                System.err.println("Kernel: Failure in event loop:");
                t.printStackTrace();
                sleep(1000);
            }
        }
    }

    //-----------------------------------------------------

    static private void checkSelector(){

        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while(iterator.hasNext()){

            SelectionKey key=null;
            try {
                key = iterator.next();
                iterator.remove();
                actOnKey(key);

            }catch(CancelledKeyException e){
                continue;
            }catch(Exception e) {
                if(e.getMessage()!=null) System.err.println("Kernel: checking selectors: "+e.getMessage()+" "+e.getClass());
                else e.printStackTrace();
                cancelKey(key);
                continue;
            }
        }
    }

    static private void actOnKey(SelectionKey key) throws Exception{

        if(key.isAcceptable())  acceptKey(key);
        else
        if(key.isConnectable()) connectKey(key);
        else
        if(key.isReadable())    readKey(key);
        else
        if(key.isWritable())    writeKey(key);
    }

    static private void acceptKey(SelectionKey key) throws Exception{

        ServerSocketChannel listener = (ServerSocketChannel)key.channel();
        SocketChannel channel = listener.accept();
        if(channel==null) return;

        channel.configureBlocking(false);
        channel.socket().setTcpNoDelay(true);
        channel.register(selector, SelectionKey.OP_READ);

        ChannelUser channeluser = listeners.get(listener);
        channeluser.readable(channel, null, 0);

        channels.put(channel, channeluser);
    }

    static private void connectKey(SelectionKey key) throws Exception{
        SocketChannel channel = (SocketChannel)key.channel();
        if(channel.isConnectionPending()) channel.finishConnect();
        writeKey(key);
    }

    static private void readKey(SelectionKey key) throws Exception{

        SocketChannel channel = (SocketChannel)key.channel();
        ByteBuffer    bytebuffer = rdbuffers.get(channel);
        if(bytebuffer == null){
            bytebuffer=ByteBuffer.allocate(SOCKREADBUFFERSIZE);
            rdbuffers.put(channel, bytebuffer);
        }

        int n = channel.read(bytebuffer);

        if(n== -1) {
            cancelKey(key);
            return;
        }
        ChannelUser channeluser = channels.get(channel);
        channeluser.readable(channel, bytebuffer, n);
        ensureBufferBigEnough(channel, bytebuffer);
    }

    static private void writeKey(SelectionKey key) throws Exception{

        SocketChannel     channel  = (SocketChannel)key.channel();
        Queue<ByteBuffer> bytebuffers = wrbuffers.get(channel);
        if(bytebuffers == null || bytebuffers.size()==0){
            ChannelUser channeluser = channels.get(channel);
            channeluser.writable(channel, null, 0);
            bytebuffers = wrbuffers.get(channel);
            if(bytebuffers == null || bytebuffers.size()==0) unSetWriting(channel);
            return;
        }
        ByteBuffer bytebuffer=bytebuffers.element();

        int n = channel.write(bytebuffer);

        chopFromWrbuffers(bytebuffers);
        ChannelUser channeluser = channels.get(channel);
        channeluser.writable(channel, bytebuffers, n);

        if(bytebuffers.size()==0){
            unSetWriting(channel);
        }
    }

    //-----------------------------------------------------

    static private ByteBuffer doChopAtDivider(ByteBuffer bytebuffer, byte[] divider){
        int len;
        int pos=bytebuffer.position();
        for(len=0; len<=pos-divider.length; len++){
            int j;
            for(j=0; j< divider.length; j++){
                if(bytebuffer.get(len+j)!=divider[j]) break;
            }
            if(j!=divider.length) continue;

            ByteBuffer bb=ByteBuffer.allocate(len);
            bytebuffer.position(0);
            bytebuffer.limit(len);

            bb.put(bytebuffer);
            bb.position(0);

            bytebuffer.limit(pos);
            bytebuffer.position(len+divider.length);
            bytebuffer.compact();

            return bb;
        }
        return null;
    }

    static private ByteBuffer doChopAtLength(ByteBuffer bytebuffer, int len){

        int pos=bytebuffer.position();

        ByteBuffer bb=ByteBuffer.allocate(len);
        bytebuffer.position(0);
        bytebuffer.limit(len);

        bb.put(bytebuffer);
        bb.position(0);

        bytebuffer.limit(pos);
        bytebuffer.position(len);
        bytebuffer.compact();

        return bb;
    }

    static private void appendToWriteBuffers(SocketChannel channel, ByteBuffer bytebuffer){
        Queue<ByteBuffer> bytebuffers = wrbuffers.get(channel);
        if(bytebuffers == null){
            bytebuffers = new LinkedList<ByteBuffer>();
            wrbuffers.put(channel, bytebuffers);
        }
        bytebuffers.add(bytebuffer);
    }

    static private void ensureBufferBigEnough(SocketChannel channel, ByteBuffer bytebuffer){
        if(bytebuffer.position() == bytebuffer.capacity()){
            ByteBuffer biggerbuffer = ByteBuffer.allocate(bytebuffer.capacity()*2);
            bytebuffer.flip();
            biggerbuffer.put(bytebuffer);
            bytebuffer = biggerbuffer;
            rdbuffers.put(channel, bytebuffer);
            if(bytebuffer.capacity() >= 64 * 1024){
                System.out.println("Kernel: New read buffer size="+bytebuffer.capacity());
            }
        }
    }

    static private void chopFromWrbuffers(Queue<ByteBuffer> bytebuffers){
        if(bytebuffers.element().remaining() == 0){
            bytebuffers.remove();
        }
    }

    static private void setWriting(SocketChannel channel){
        try{
            channel.register(selector, SelectionKey.OP_WRITE);
        }catch(ClosedChannelException cce){
            System.err.println("Kernel: Attempt to write to closed channel");
            cce.printStackTrace();
        }
    }

    static private void unSetWriting(SocketChannel channel){
        try{
            channel.register(selector, SelectionKey.OP_READ);
        }catch(ClosedChannelException cce){ }
    }

    //-----------------------------------------------------

    static private void cancelKey(SelectionKey key){
        try{ key.cancel(); }catch(Exception e){}
        closeSelectableChannel(key.channel());
    }

    static private void closeSelectableChannel(SelectableChannel sc){
        try{
            sc.close();
        }catch(Exception e){}

        ChannelUser lmodule = listeners.remove(sc);
        if(lmodule != null) return;

        ChannelUser cmodule    = channels.remove(sc);
        ByteBuffer  bytebuffer = rdbuffers.remove(sc); wrbuffers.remove(sc);
        if(cmodule != null) cmodule.readable((SocketChannel)sc, bytebuffer, -1);
    }

    static private void bailOut(String message, Throwable t, AbstractInterruptibleChannel c){
        System.err.println("Kernel: bailing! "+message+":\n"+t);
        try{ if(c!=null) c.close(); } catch(Throwable tt){}
        System.exit(1);
    }

    //-----------------------------------------------------

    static public void sleep(int millis){
        try{ Thread.sleep(millis); }catch(Exception e){}
    }

    //-----------------------------------------------------

}

