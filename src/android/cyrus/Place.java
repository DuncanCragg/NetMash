package cyrus;

import java.net.*;

import android.net.wifi.*;
import android.net.*;

import cyrus.platform.*;
import cyrus.forest.*;
import cyrus.gui.NetMash;
import cyrus.types.*;

import static android.content.Context.*;

import static cyrus.lib.Utils.*;

public class Place extends PresenceTracker {

    public Place(){ NetMash.user.place=this; }

    public Place(String s){ super(s,true); }

    public boolean broadcastPlaceEnable=true;
    public boolean broadcastPlaceSet=false;

    private boolean running=false;

    public void evaluate(){
        if(!running) broadcastPlace();
        super.evaluate();
    }

    void broadcastPlace(){
        running=true;
        new Thread(){ public void run(){
            String placeURL=UID.toURL(uid);
            while(true){
                if(broadcastPlaceEnable && broadcastPlaceSet){
                    Kernel.broadcastUDP(getBroadcastAddress(),24589, placeURL);
                }
                Kernel.sleep(2000);
            }
        }}.start();
    }

    InetAddress broadcastAddress=null;

    public InetAddress getBroadcastAddress(){ try {
        if(broadcastAddress!=null) return broadcastAddress;
        WifiManager wm = (WifiManager)NetMash.top.getSystemService(WIFI_SERVICE);
        DhcpInfo di = wm.getDhcpInfo();
        int bc = (di.ipAddress & di.netmask) | ~di.netmask;
        byte[] ba = new byte[4];
        for(int k=0; k< 4; k++) ba[k]=(byte)(bc >> k*8);
        broadcastAddress=InetAddress.getByAddress(ba);
    } catch(Throwable t){ t.printStackTrace(); }
        return broadcastAddress;
    }
}

