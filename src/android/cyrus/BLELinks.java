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

public class BLELinks extends WebObject implements BluetoothAdapter.LeScanCallback {

    public BLELinks(){ NetMash.user.linksaround=this; }

    public BLELinks(String s){ super(s,true); }

    private boolean running=false;
    private BluetoothAdapter bluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;

    public void evaluate(){
        if(!NetMash.top.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return;
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
    boolean dodgyChipsetLikeNexus4and7=true;

    boolean notifiedEnableBT=false;
    synchronized private void checkOnScanning(){
logXX("checkOnScanning suspended:",suspended,"scanning:",scanning,"bt enabled:",bluetoothAdapter.isEnabled());
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
        if(scanning && !dodgyChipsetLikeNexus4and7) return;
logXX(scanning? "stopLeScan": "startLeScan");
        if(scanning){ scanning=false;try{ bluetoothAdapter.stopLeScan(this); } catch(Throwable t){ t.printStackTrace(); } }
        else {        scanning=true;      bluetoothAdapter.startLeScan(this); }
    }

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
logXX("onLeScan",device,rssi);
        new Evaluator(this){ public void evaluate(){
            String url=String.format("http://%d.%d.%d.%d:%d/o/uid-%02x%02x-%02x%02x-%02x%02x-%02x%02x.json",
                                     0xff&ad[9],0xff&ad[10],0xff&ad[11],0xff&ad[12],
                                     ((0xff & ad[13])*256)+(0xff & ad[14]),
                                     ad[15],ad[16],ad[17],ad[18],ad[19],ad[20],ad[21],ad[22]);
            if(ad[9]==0 || (ad[16]+ad[17]+ad[18]+ad[19]+ad[20]+ad[21]+ad[22]==0)){
                logXX("reject",url,String.format("%02x %02x %02x %02x %02x %02x %02x",ad[9],ad[10],ad[11],ad[12],ad[13],ad[14],ad[15]));
                return;
            }
            if(url.equals("http://192.168.0.0:0/o/uid-1501-a7ed-1501-a7ed.json")){
                if(rssi>-50){
                    String uid="uid-"+device.toString().replaceAll(":","-").toLowerCase();
logXX("gotcha", url, uid, rssi);
                    if(!FunctionalObserver.funcobs.oneOfOurs(uid)){
                        WebObject w=new BluetoothLight(
                            "{ is: editable 3d cuboid light\n"+
                            "  Rules: http://netmash.net/o/uid-16bd-140a-8862-41cd.cyr\n"+
                            "         http://netmash.net/o/uid-0dc6-ad27-05ec-a0b2.cyr\n"+
                            "         http://netmash.net/o/uid-e369-6d5d-5283-7bc7.cyr\n"+
                            "  P: { }\n"+
                            "  Timer: 1000\n"+
                            "  title: \"Bluetooth Light\"\n"+
                            "  rotation: 45 45 45\n"+
                            "  scale: 1 1 1\n"+
                            "  light: 1 1 1\n"+
                            "}\n", (BLELinks)self);
                        w.uid=uid;
                        url2mac.put(uid,device);
                        FunctionalObserver.funcobs.cacheSaveAndEvaluate(w);
logXX("created object",UID.toURL(uid),w);
                    }
                }
                return;
            }
            url2mac.put(url,device);
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

    void setPlace(String placeURL){
logXX("place URL: ",placeURL);
        contentSetAdd("list", placeURL);
        contentHash(UID.toUID(placeURL), hash("distance",25));
        content("place", placeURL);
    }

    // ---------------------------------------------------------

    void setDevice(WebObject w){
        BluetoothDevice device=url2mac.get(w.uid);
logXX("setDevice",w,device);
    }

class BluetoothLight extends CyrusLanguage {
    BLELinks blelinks;
    public BluetoothLight(){ }
    public BluetoothLight(String s, BLELinks ble){ super(s,true); blelinks=ble; }
    public void evaluate(){
        super.evaluate();
logXX("evaluating BluetoothLight",uid);
        if(modified()) blelinks.setDevice(this);
    }
}

}

