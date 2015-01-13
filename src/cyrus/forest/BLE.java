
package cyrus.forest;

import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.net.*;
import java.io.*;

import cyrus.lib.*;
import static cyrus.lib.Utils.*;
import cyrus.platform.*;

public class BLE {

    public static void doAdvert(String uid){
        logXX("BLE.doAdvert",uid,UID.toURL(uid));
        InetAddress ip=Kernel.IP();
        byte[] ipbytes= ip==null? new byte[]{127,0,0,1}: ip.getAddress();
        int port=Kernel.config.intPathN("network:port");
        String re="uid-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])";
        Matcher m = Pattern.compile(re).matcher(uid);
        if(!m.matches()) return;
                 //                                    30          26             21
                 //    hcitool -i hci0 cmd 0x08 0x0008 1e 02 01 1a 1a ff 4c 00 02 15
                 //                   e2   c5   6d   b5   df   fb  48 d2 b0 60 d0 f5 a7 10
                 //    00 00 00 00 00 00 c5 00 00 00 00 00 00 00 00 00 00 00 00 00
        String advert="hcitool -i hci0 cmd 0x08 0x0008 1e 02 01 1a 1a ff 4c 00 02 15 ";
        advert=advert+String.format("%02x %02x %02x %02x %02x %02x %s %s %s %s %s %s %s %s ",
                                     ipbytes[0], ipbytes[1], ipbytes[2], ipbytes[3],
                                     port/256, port-((port/256)*256),
                                     m.group(1),m.group(2),m.group(3),m.group(4),m.group(5),m.group(6),m.group(7),m.group(8));
        advert=advert+"00 00 00 00 00 00 c5 00 00 00 00 00 00 00 00 00 00 00 00 00";
        exec("hciconfig hci0 down");
        exec("hciconfig hci0 up");
        exec("hciconfig hci0 noleadv");
        exec(advert);
        // set rate http://stackoverflow.com/questions/21124993/is-there-a-way-to-increase-ble-advertisement-frequency-in-bluez
        //                                    00 03 = 0x0300 * 0.625ms = 480ms
        exec("hcitool -i hci0 cmd 0x08 0x0006 00 03 00 03 03 00 00 00 00 00 00 00 00 07 00");
        exec("hcitool -i hci0 cmd 0x08 0x000a 01"); // leadv
        exec(advert);                               // override Ubuntu crap set on leadv
        exec("hciconfig hci0 noscan");              // stop more Ubuntu shite
    }
}

