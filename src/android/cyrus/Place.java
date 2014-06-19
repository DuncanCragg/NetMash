package cyrus;

import java.net.*;
import java.util.*;

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
            while(true){
                if(broadcastPlaceEnable && broadcastPlaceSet){
                    HashSet<InetAddress> ias=addGlobalBroadcastIA(addWifiIA(Kernel.getBroadcastAddresses()));
                    for(InetAddress ia: ias){
                        logXX(UID.toURL(uid), "broadcast to IP", ia);
                        Kernel.broadcastUDP(ia, 24589, UID.toURL(uid));
                    }
                }
                Kernel.sleep(2000);
            }
        }}.start();
    }

    private HashSet<InetAddress> addWifiIA(HashSet<InetAddress> ias){
        try{
            WifiManager wm = (WifiManager)NetMash.top.getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            if(wi.getSSID()==null ||
               wi.getSSID().length()==0 ||
               wi.getSSID().equals("<unknown ssid>") ||
               wi.getSSID().equals("0x")){
                logXX("Wifi not connected",wi.getSSID());
                return ias;
            }
            DhcpInfo di = wm.getDhcpInfo();
            int bc = (di.ipAddress & di.netmask) | ~di.netmask;
            byte[] ba = new byte[4];
            for(int k=0; k< 4; k++) ba[k]=(byte)(bc >> k*8);
            ias.add(InetAddress.getByAddress(ba));
            return ias;
        } catch(Throwable t){ t.printStackTrace(); }
        return ias;
    }

    private HashSet<InetAddress> addGlobalBroadcastIA(HashSet<InetAddress> ias){
        if(!ias.isEmpty()) return ias;
        try{
            byte[] ba = new byte[]{ (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff };
            ias.add(InetAddress.getByAddress(ba));
            return ias;
        } catch(Throwable t){ t.printStackTrace(); }
        return ias;
    }
}

