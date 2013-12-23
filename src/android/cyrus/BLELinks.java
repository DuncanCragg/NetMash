package cyrus;

import android.content.*;
import android.content.pm.PackageManager;
import android.bluetooth.*;
import android.bluetooth.BluetoothAdapter.*;

import cyrus.platform.Kernel;
import cyrus.forest.WebObject;
import cyrus.gui.Cyrus;

import static cyrus.lib.Utils.*;

public class BLELinks extends WebObject implements BluetoothAdapter.LeScanCallback {

    public BLELinks(){ Cyrus.user.linksaround=this; }

    public BLELinks(String s){ super(s,true); }

    private boolean running=false;
    private BluetoothAdapter bluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;

    public void evaluate(){
        if(!Cyrus.top.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return;
        if(!running) runBLEScan();
    }

    private void runBLEScan(){
        running=true;
        BluetoothManager bm=(BluetoothManager)Cyrus.top.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter=bm.getAdapter(); if(bluetoothAdapter==null) return;
        new Thread(){ public void run(){
            while(running){
                checkOnScanning();
                Kernel.sleep(500);
        }}}.start();
    }

    boolean notifiedEnableBT=false;
    boolean scanning=false;
    boolean suspended=false;

    synchronized private void checkOnScanning(){
        if(suspended) return;
        if(!bluetoothAdapter.isEnabled()){
            if(scanning) bluetoothAdapter.stopLeScan(this);
            scanning=false;
            if(!notifiedEnableBT){
                notifiedEnableBT=true;
                Cyrus.top.toast("Enable Bluetooth to detect local objects", false);
            }
            return;
            // Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // Cyrus.top.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        if(scanning) return;
        scanning=true;
        bluetoothAdapter.startLeScan(this);
    }

    synchronized public void enableScanning(){
        suspended=false;
    }

    synchronized public void disableScanning(){
        suspended=true;
        scanning=false;
        bluetoothAdapter.stopLeScan(this);
    }

    @Override
    public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] ad){
        new Evaluator(this){ public void evaluate(){
            String data="";
            for(int i=0; i<ad.length; i++) data += String.format("%02x ", ad[i]);
            String url=String.format("http://%d.%d.%d.%d:%d/o/uid-%02x%02x-%02x%02x-%02x%02x-%02x%02x.json",
                                     0xff&ad[9],0xff&ad[10],0xff&ad[11],0xff&ad[12],
                                     ((0xff & ad[13])*256)+(0xff & ad[14]),
                                     ad[15],ad[16],ad[17],ad[18],ad[19],ad[20],ad[21],ad[22]);
            contentList("list", list(url));
            contentInt("rssi", rssi);
        }};
    }
}

