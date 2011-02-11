package jungle.platform;

import java.util.*;
import java.nio.*;
import java.nio.channels.*;

/** Channel User - reads and writes a channel.
  */
public interface ChannelUser {

    public void readable(SocketChannel channel, ByteBuffer bytebuffer, int len);
    public void writable(SocketChannel channel, Queue<ByteBuffer> bytebuffers, int len);
}

