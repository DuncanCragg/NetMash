package cyrus;

import java.util.*;

import android.content.*;
import android.content.pm.PackageManager;
import android.bluetooth.*;
import android.bluetooth.BluetoothAdapter.*;

import cyrus.platform.Kernel;
import cyrus.forest.*;
import cyrus.gui.NetMash;

import static cyrus.lib.Utils.*;

public class BLELinks extends WebObject /* implements BluetoothAdapter.LeScanCallback */ {

    public BLELinks(){ NetMash.user.linksaround=this; }

    public BLELinks(String s){ super(s,true); }

    private boolean running=false;
//  private BluetoothAdapter bluetoothAdapter;
//  private final static int REQUEST_ENABLE_BT = 1;

    public void evaluate(){
     // if(!NetMash.top.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return;
        if(!running) runBLEScan();
    }

    private void runBLEScan(){
        running=true;
   //   BluetoothManager bm=(BluetoothManager)NetMash.top.getSystemService(Context.BLUETOOTH_SERVICE);
   //   bluetoothAdapter=bm.getAdapter(); if(bluetoothAdapter==null) return;
        new Thread(){ public void run(){
            while(running){
                checkOnBroadcast();
            }
        }}.start();
//      new Thread(){ public void run(){
//          while(running){
//              checkOnScanning();
//              Kernel.sleep(500);
//      }}}.start();
    }

    private void checkOnBroadcast(){
        if(suspended) return;
        String placeURL=Kernel.listenUDP(24589);
        if(placeURL!=null) onPlaceURL(placeURL);
        else Kernel.sleep(2000);
    }

    boolean scanning=false;
    boolean suspended=false;
/*
    boolean notifiedEnableBT=false;
    synchronized private void checkOnScanning(){
        if(suspended) return;
        if(!bluetoothAdapter.isEnabled()){
            if(scanning) try{ bluetoothAdapter.stopLeScan(this); } catch(Throwable t){}
            scanning=false;
            if(!notifiedEnableBT){
                notifiedEnableBT=true;
                NetMash.top.toast("Enable Bluetooth to detect local objects", false);
            }
            return;
            // Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // NetMash.top.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        if(scanning) return;
        scanning=true;
        bluetoothAdapter.startLeScan(this);
    }
*/
    synchronized public void enableScanning(){
        suspended=false;
    }

    synchronized public void disableScanning(){
        suspended=true;
        scanning=false;
//      bluetoothAdapter.stopLeScan(this);
    }

    private void onPlaceURL(final String placeURL){
        new Evaluator(this){ public void evaluate(){ setPlace(placeURL); }};
    }
/*
    @Override
    public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] ad){
        new Evaluator(this){ public void evaluate(){
            String url=String.format("http://%d.%d.%d.%d:%d/o/uid-%02x%02x-%02x%02x-%02x%02x-%02x%02x.json",
                                     0xff&ad[9],0xff&ad[10],0xff&ad[11],0xff&ad[12],
                                     ((0xff & ad[13])*256)+(0xff & ad[14]),
                                     ad[15],ad[16],ad[17],ad[18],ad[19],ad[20],ad[21],ad[22]);
            contentSetAdd("list", url);
logXX(url,device.toString().replaceAll(":","-"),rssi);
            contentHash(UID.toUID(url), hash("distance",-rssi-25, "mac",device.toString().replaceAll(":","-")));
            LinkedList allplaces=contentAll("list:within");
            if(allplaces!=null) for(Object o: allplaces){
                if(!(o instanceof String)) continue;
                String placeURL=(String)o;
                setPlace(placeURL);
            }
        }};
    }
*/
    void setPlace(String placeURL){
logXX("place URL: ",placeURL);
        contentSetAdd("list", placeURL);
        contentHash(UID.toUID(placeURL), hash("distance",25));
        content("place", placeURL);
    }

    // ---------------------------------------------------------
}

