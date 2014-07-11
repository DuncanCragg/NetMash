package cyrus;

import java.util.*;

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
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                NetMash.top.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            return;
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
logXX("url:",url);
            if(url.equals("http://192.168.0.0:0/o/uid-1501-a7ed-1501-a7ed.json")){
                String uid="uid-"+device.toString().replaceAll(":","-").toLowerCase();
logXX("isolated",rssi,uid);
                if(rssi>-45 && !FunctionalObserver.funcobs.oneOfOurs(uid)){
logXX("gotcha", url, uid, rssi);
                    fetchInterestingAttributes(device,uid);
                }
if(FunctionalObserver.funcobs.oneOfOurs(uid)){
contentSetAdd("list", uid);
contentHash(uid, hash("distance",-rssi-25, "mac",device.toString().replaceAll(":","-")));
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

    BluetoothGattCallback bgcb=null;
    BluetoothGatt bg;

    BluetoothDevice pendingdevice=null;
    String pendinguid=null;

    void fetchInterestingAttributes(BluetoothDevice device, String uid){
logXX("fetchInterestingAttributes from", device, uid);
        if(pendingdevice!=null) return;
        pendingdevice=device;
        pendinguid=uid;
        if(bgcb==null){ bgcb=new BluetoothGattCallback(){

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if(newState==BluetoothProfile.STATE_CONNECTED){
                    boolean r=bg.discoverServices();
                    logXX("Connected to GATT server. Started service discovery:" + r);

                } else if(newState==BluetoothProfile.STATE_DISCONNECTED){
                    logXX("Disconnected from GATT server.");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if(status==BluetoothGatt.GATT_SUCCESS){
                    logXX("onServicesDiscovered ok");
                    checkOutServices(gatt);
                } else {
                    logXX("onServicesDiscovered received: " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic charact, int status) {
                if(status==BluetoothGatt.GATT_SUCCESS){
                    logXX("onCharacteristicRead ok");
                    checkOutCharacteristic(charact);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic charact) {
                logXX("onCharacteristicChanged");
                checkOutCharacteristic(charact);
            }
        };}
        bg=device.connectGatt(NetMash.top, false, bgcb);
    }

    public static String UUID_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    public static String UUID_HEART_RATE_MEASUREMENT  = "00002a37-0000-1000-8000-00805f9b34fb";

    void checkOutServices(BluetoothGatt gatt){
        for(BluetoothGattService gattService: gatt.getServices()){
            String u=gattService.getUuid().toString();
            logXX("service:", serviceLookup.get(u), u);
            for(BluetoothGattCharacteristic charact: gattService.getCharacteristics()) {
                u=charact.getUuid().toString();
                logXX("---- characteristic", charactLookup.get(u), u);
                logXX(charact.getProperties(),
                      (charact.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ     )>0? "read": "",
                      (charact.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE    )>0? "write": "",
                      (charact.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY   )>0? "notify": "",
                      (charact.getProperties() & BluetoothGattCharacteristic.PROPERTY_BROADCAST)>0? "broadcast": "");
                if((charact.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) >0){
                    if(UUID_DEVICE_NAME.equals(u)) gatt.readCharacteristic(charact);
                }
         /*     if((charact.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) >0){
                    if(UUID_HEART_RATE_MEASUREMENT.equals(u)){
                        bg.setCharacteristicNotification(charact, true);
                        BluetoothGattDescriptor descriptor = charact.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        bg.writeDescriptor(descriptor);
                    }
                }
         */ }
        }
    }

    void checkOutCharacteristic(BluetoothGattCharacteristic charact){
        String u=charact.getUuid().toString();
        logXX("checkOutCharacteristic", charactLookup.get(u), u);
        if(!UUID_DEVICE_NAME.equals(u)) return;
        String name="Not Set";
        byte[] data = charact.getValue();
        if(data != null && data.length >0) name=new String(data);
        WebObject w=new BluetoothLight(
            "{ is: editable 3d cuboid light\n"+
            "  Rules: http://netmash.net/o/uid-16bd-140a-8862-41cd.cyr\n"+
            "         http://netmash.net/o/uid-0dc6-ad27-05ec-a0b2.cyr\n"+
            "         http://netmash.net/o/uid-e369-6d5d-5283-7bc7.cyr\n"+
            "  P: { }\n"+
            "  Timer: 1000\n"+
            "  title: \""+name+"\"\n"+
            "  rotation: 45 45 45\n"+
            "  scale: 1 1 1\n"+
            "  light: 1 1 1\n"+
            "}\n", this);
        w.uid=pendinguid;
        FunctionalObserver.funcobs.cacheSaveAndEvaluate(w);
        url2mac.put(pendinguid,pendingdevice);
        setURLOnDevice(pendingdevice, UID.toURL(pendinguid));
    }

    void setURLOnDevice(BluetoothDevice device, String url){
logXX("captured object, setting URL:", url);
        pendingdevice=null; pendinguid=null;
        bg.close();
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

    static LinkedHashMap<String, String> serviceLookup = new LinkedHashMap<String, String>();
    static LinkedHashMap<String, String> charactLookup = new LinkedHashMap<String, String>();

    static {
        serviceLookup.put("00001811-0000-1000-8000-00805f9b34fb", "Alert Notification Service");
        serviceLookup.put("0000180f-0000-1000-8000-00805f9b34fb", "Battery Service");
        serviceLookup.put("00001810-0000-1000-8000-00805f9b34fb", "Blood Pressure");
        serviceLookup.put("00001805-0000-1000-8000-00805f9b34fb", "Current Time Service");
        serviceLookup.put("00001818-0000-1000-8000-00805f9b34fb", "Cycling Power");
        serviceLookup.put("00001816-0000-1000-8000-00805f9b34fb", "Cycling Speed and Cadence");
        serviceLookup.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information");
        serviceLookup.put("00001800-0000-1000-8000-00805f9b34fb", "Generic Access");
        serviceLookup.put("00001801-0000-1000-8000-00805f9b34fb", "Generic Attribute");
        serviceLookup.put("00001808-0000-1000-8000-00805f9b34fb", "Glucose");
        serviceLookup.put("00001809-0000-1000-8000-00805f9b34fb", "Health Thermometer");
        serviceLookup.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate");
        serviceLookup.put("00001812-0000-1000-8000-00805f9b34fb", "Human Interface Device");
        serviceLookup.put("00001802-0000-1000-8000-00805f9b34fb", "Immediate Alert");
        serviceLookup.put("00001803-0000-1000-8000-00805f9b34fb", "Link Loss");
        serviceLookup.put("00001819-0000-1000-8000-00805f9b34fb", "Location and Navigation");
        serviceLookup.put("00001807-0000-1000-8000-00805f9b34fb", "Next DST Change Service");
        serviceLookup.put("0000180e-0000-1000-8000-00805f9b34fb", "Phone Alert Status Service");
        serviceLookup.put("00001806-0000-1000-8000-00805f9b34fb", "Reference Time Update Service");
        serviceLookup.put("00001814-0000-1000-8000-00805f9b34fb", "Running Speed and Cadence");
        serviceLookup.put("00001813-0000-1000-8000-00805f9b34fb", "Scan Parameters");
        serviceLookup.put("00001804-0000-1000-8000-00805f9b34fb", "Tx Power");

        charactLookup.put("00002a43-0000-1000-8000-00805f9b34fb", "Alert Category ID");
        charactLookup.put("00002a42-0000-1000-8000-00805f9b34fb", "Alert Category ID Bit Mask");
        charactLookup.put("00002a06-0000-1000-8000-00805f9b34fb", "Alert Level");
        charactLookup.put("00002a44-0000-1000-8000-00805f9b34fb", "Alert Notification Control Point");
        charactLookup.put("00002a3f-0000-1000-8000-00805f9b34fb", "Alert Status");
        charactLookup.put("00002a01-0000-1000-8000-00805f9b34fb", "Appearance");
        charactLookup.put("00002a19-0000-1000-8000-00805f9b34fb", "Battery Level");
        charactLookup.put("00002a49-0000-1000-8000-00805f9b34fb", "Blood Pressure Feature");
        charactLookup.put("00002a35-0000-1000-8000-00805f9b34fb", "Blood Pressure Measurement");
        charactLookup.put("00002a38-0000-1000-8000-00805f9b34fb", "Body Sensor Location");
        charactLookup.put("00002a22-0000-1000-8000-00805f9b34fb", "Boot Keyboard Input Report");
        charactLookup.put("00002a32-0000-1000-8000-00805f9b34fb", "Boot Keyboard Output Report");
        charactLookup.put("00002a33-0000-1000-8000-00805f9b34fb", "Boot Mouse Input Report");
        charactLookup.put("00002a5c-0000-1000-8000-00805f9b34fb", "CSC Feature");
        charactLookup.put("00002a5b-0000-1000-8000-00805f9b34fb", "CSC Measurement");
        charactLookup.put("00002a2b-0000-1000-8000-00805f9b34fb", "Current Time");
        charactLookup.put("00002a66-0000-1000-8000-00805f9b34fb", "Cycling Power Control Point");
        charactLookup.put("00002a65-0000-1000-8000-00805f9b34fb", "Cycling Power Feature");
        charactLookup.put("00002a63-0000-1000-8000-00805f9b34fb", "Cycling Power Measurement");
        charactLookup.put("00002a64-0000-1000-8000-00805f9b34fb", "Cycling Power Vector");
        charactLookup.put("00002a08-0000-1000-8000-00805f9b34fb", "Date Time");
        charactLookup.put("00002a0a-0000-1000-8000-00805f9b34fb", "Day Date Time");
        charactLookup.put("00002a09-0000-1000-8000-00805f9b34fb", "Day of Week");
        charactLookup.put("00002a00-0000-1000-8000-00805f9b34fb", "Device Name");
        charactLookup.put("00002a0d-0000-1000-8000-00805f9b34fb", "DST Offset");
        charactLookup.put("00002a0c-0000-1000-8000-00805f9b34fb", "Exact Time 256");
        charactLookup.put("00002a26-0000-1000-8000-00805f9b34fb", "Firmware Revision String");
        charactLookup.put("00002a51-0000-1000-8000-00805f9b34fb", "Glucose Feature");
        charactLookup.put("00002a18-0000-1000-8000-00805f9b34fb", "Glucose Measurement");
        charactLookup.put("00002a34-0000-1000-8000-00805f9b34fb", "Glucose Measurement Context");
        charactLookup.put("00002a27-0000-1000-8000-00805f9b34fb", "Hardware Revision String");
        charactLookup.put("00002a39-0000-1000-8000-00805f9b34fb", "Heart Rate Control Point");
        charactLookup.put("00002a37-0000-1000-8000-00805f9b34fb", "Heart Rate Measurement");
        charactLookup.put("00002a4c-0000-1000-8000-00805f9b34fb", "HID Control Point");
        charactLookup.put("00002a4a-0000-1000-8000-00805f9b34fb", "HID Information");
        charactLookup.put("00002a2a-0000-1000-8000-00805f9b34fb", "IEEE 11073-20601 Regulatory Certification Data List");
        charactLookup.put("00002a36-0000-1000-8000-00805f9b34fb", "Intermediate Cuff Pressure");
        charactLookup.put("00002a1e-0000-1000-8000-00805f9b34fb", "Intermediate Temperature");
        charactLookup.put("00002a6b-0000-1000-8000-00805f9b34fb", "LN Control Point");
        charactLookup.put("00002a6a-0000-1000-8000-00805f9b34fb", "LN Feature");
        charactLookup.put("00002a0f-0000-1000-8000-00805f9b34fb", "Local Time Information");
        charactLookup.put("00002a67-0000-1000-8000-00805f9b34fb", "Location and Speed");
        charactLookup.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        charactLookup.put("00002a21-0000-1000-8000-00805f9b34fb", "Measurement Interval");
        charactLookup.put("00002a24-0000-1000-8000-00805f9b34fb", "Model Number String");
        charactLookup.put("00002a68-0000-1000-8000-00805f9b34fb", "Navigation");
        charactLookup.put("00002a46-0000-1000-8000-00805f9b34fb", "New Alert");
        charactLookup.put("00002a04-0000-1000-8000-00805f9b34fb", "Peripheral Preferred Connection Parameters");
        charactLookup.put("00002a02-0000-1000-8000-00805f9b34fb", "Peripheral Privacy Flag");
        charactLookup.put("00002a50-0000-1000-8000-00805f9b34fb", "PnP ID");
        charactLookup.put("00002a69-0000-1000-8000-00805f9b34fb", "Position Quality");
        charactLookup.put("00002a4e-0000-1000-8000-00805f9b34fb", "Protocol Mode");
        charactLookup.put("00002a03-0000-1000-8000-00805f9b34fb", "Reconnection Address");
        charactLookup.put("00002a52-0000-1000-8000-00805f9b34fb", "Record Access Control Point");
        charactLookup.put("00002a14-0000-1000-8000-00805f9b34fb", "Reference Time Information");
        charactLookup.put("00002a4d-0000-1000-8000-00805f9b34fb", "Report");
        charactLookup.put("00002a4b-0000-1000-8000-00805f9b34fb", "Report Map");
        charactLookup.put("00002a40-0000-1000-8000-00805f9b34fb", "Ringer Control Point");
        charactLookup.put("00002a41-0000-1000-8000-00805f9b34fb", "Ringer Setting");
        charactLookup.put("00002a54-0000-1000-8000-00805f9b34fb", "RSC Feature");
        charactLookup.put("00002a53-0000-1000-8000-00805f9b34fb", "RSC Measurement");
        charactLookup.put("00002a55-0000-1000-8000-00805f9b34fb", "SC Control Point");
        charactLookup.put("00002a4f-0000-1000-8000-00805f9b34fb", "Scan Interval Window");
        charactLookup.put("00002a31-0000-1000-8000-00805f9b34fb", "Scan Refresh");
        charactLookup.put("00002a5d-0000-1000-8000-00805f9b34fb", "Sensor Location");
        charactLookup.put("00002a25-0000-1000-8000-00805f9b34fb", "Serial Number String");
        charactLookup.put("00002a05-0000-1000-8000-00805f9b34fb", "Service Changed");
        charactLookup.put("00002a28-0000-1000-8000-00805f9b34fb", "Software Revision String");
        charactLookup.put("00002a47-0000-1000-8000-00805f9b34fb", "Supported New Alert Category");
        charactLookup.put("00002a48-0000-1000-8000-00805f9b34fb", "Supported Unread Alert Category");
        charactLookup.put("00002a23-0000-1000-8000-00805f9b34fb", "System ID");
        charactLookup.put("00002a1c-0000-1000-8000-00805f9b34fb", "Temperature Measurement");
        charactLookup.put("00002a1d-0000-1000-8000-00805f9b34fb", "Temperature Type");
        charactLookup.put("00002a12-0000-1000-8000-00805f9b34fb", "Time Accuracy");
        charactLookup.put("00002a13-0000-1000-8000-00805f9b34fb", "Time Source");
        charactLookup.put("00002a16-0000-1000-8000-00805f9b34fb", "Time Update Control Point");
        charactLookup.put("00002a17-0000-1000-8000-00805f9b34fb", "Time Update State");
        charactLookup.put("00002a11-0000-1000-8000-00805f9b34fb", "Time with DST");
        charactLookup.put("00002a0e-0000-1000-8000-00805f9b34fb", "Time Zone");
        charactLookup.put("00002a07-0000-1000-8000-00805f9b34fb", "Tx Power Level");
        charactLookup.put("00002a45-0000-1000-8000-00805f9b34fb", "Unread Alert Status");
    }
}


