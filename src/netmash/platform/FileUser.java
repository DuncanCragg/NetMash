package netmash.platform;

import java.util.*;
import java.nio.*;
import java.nio.channels.*;

/** File User - reads and writes a file.
  */
public interface FileUser {

    public void readable(ByteBuffer bytebuffer, int len);
    public void writable(ByteBuffer bytebuffer, int len);
}


