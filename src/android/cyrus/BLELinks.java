package cyrus;

import java.util.*;
import java.nio.charset.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;

import android.content.*;
import android.bluetooth.*;
import android.bluetooth.BluetoothAdapter.*;

import cyrus.platform.Kernel;
import cyrus.forest.*;
import cyrus.gui.NetMash;

import static cyrus.lib.Utils.*;

public class BLELinks extends WebObject implements BluetoothAdapter.LeScanCallback {

    public BLELinks(){ NetMash.user.linksaround=this; }

    public BLELinks(String s){ super(s,true); }

    private boolean running=false;
    private BluetoothAdapter bluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;

    public void evaluate(){
        if(!running) runBLEScan();
    }

    private void runBLEScan(){
        running=true;
        BluetoothManager bm=(BluetoothManager)NetMash.top.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter=bm.getAdapter(); if(bluetoothAdapter==null) return;
        new Thread(){ public void run(){
            while(running){
                checkOnBroadcast();
            }
        }}.start();
        new Thread(){ public void run(){
            while(running){
                checkOnScanning();
                Kernel.sleep(400);
        }}}.start();
    }

    private void checkOnBroadcast(){
        if(suspended) return;
        String placeURL=Kernel.listenUDP(24589);
        if(placeURL!=null) onPlaceURL(placeURL);
        else Kernel.sleep(2000);
    }

    boolean scanning=false;
    boolean suspended=false;
    boolean dodgyChipsetLikeNexus4and7=false;

    boolean notifiedEnableBT=false;
    synchronized private void checkOnScanning(){
        logXX("checkOnScanning",suspended? "suspended":"", scanning? "scanning":"", isBTEnabled()? "BT enabled":"");
        if(suspended) return;
        if(!isBTEnabled()){
            if(scanning) try{ bluetoothAdapter.stopLeScan(this); } catch(Throwable t){}
            scanning=false;
            if(!notifiedEnableBT){
                notifiedEnableBT=true;
                NetMash.top.toast("Enable Bluetooth to detect local objects", false);
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                NetMash.top.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            return;
        }
        if(scanning && !dodgyChipsetLikeNexus4and7) return;
        if(scanning){ scanning=false;try{ bluetoothAdapter.stopLeScan(this); } catch(Throwable t){ t.printStackTrace(); } }
        else {        scanning=true;      bluetoothAdapter.startLeScan(this); }
    }

    boolean isBTEnabled(){ try{ return bluetoothAdapter.isEnabled(); }catch(Throwable t){ logXX("Something funky in BT",t); return false; } }

    synchronized public void enableScanning(){
        suspended=false;
    }

    synchronized public void disableScanning(){
        suspended=true;
        scanning=false;
        bluetoothAdapter.stopLeScan(this);
    }

    private void onPlaceURL(final String placeURL){
        new Evaluator(this){ public void evaluate(){ setPlace(placeURL); }};
    }

    LinkedHashMap<String,BluetoothDevice> url2mac = new LinkedHashMap<String,BluetoothDevice>();

    @Override
    synchronized public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] ad){
        String s=""; for(int i=0; i< ad.length; i++) s+=String.format("%02x ",ad[i]); logXX("onLeScan",s);
        new Evaluator(this){ public void evaluate(){
            String url="";
            if(ad[4]==(byte)0xff && ad[5]==0x4c && ad[9]!=0){ // Apple iBeacon
                url=String.format("http://%d.%d.%d.%d:%d/o/uid-%02x%02x-%02x%02x-%02x%02x-%02x%02x.json",
                                   0xff&ad[9],0xff&ad[10],0xff&ad[11],0xff&ad[12],
                                   ((0xff & ad[13])*256)+(0xff & ad[14]),
                                   ad[15],ad[16],ad[17],ad[18],ad[19],ad[20],ad[21],ad[22]);
            }
            logXX("BLE adv scan found: ", device.toString(), url, rssi);
            if(url.equals("")) return;
            url2mac.put(UID.toUID(url),device);
            contentSetAdd("list", url);
            contentHash(UID.toUID(url), hash("distance",-rssi-25, "mac",device.toString().replaceAll(":","-")));
            LinkedList allplaces=contentAll("list:within");
            if(allplaces!=null) for(Object o: allplaces){
                if(!(o instanceof String)) continue;
                String placeURL=(String)o;
                setPlace(placeURL);
            }
        }};
    }

    void setPlace(String placeURL){
        contentSetAdd("list", placeURL);
        contentHash(UID.toUID(placeURL), hash("distance",25));
        content("place", placeURL);
    }

}


