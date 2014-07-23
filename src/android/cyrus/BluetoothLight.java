package cyrus;

import cyrus.forest.*;
import cyrus.gui.NetMash;

import static cyrus.lib.Utils.*;

/** Class to drive an RGB LED on Bluetooth.
  */
public class BluetoothLight extends CyrusLanguage {

    BLELinks blelinks;

    public BluetoothLight(){ blelinks=NetMash.user.linksaround; }

    public BluetoothLight(BLELinks ble, String name, String linksarounduid){
        super(
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
            "  links-around: "+linksarounduid+"\n"+
        "}\n", true);
        blelinks=ble;
    }

    public void evaluate(){
        String placeURL=content("links-around:place");
        if(placeURL!=null && !contentIs("within", placeURL)){
            contentList("position", list(random(1,10), random(0,3), random(1,10)));
            content("within", placeURL);
            notifying(placeURL);
        }
        super.evaluate();
        if(modified()) blelinks.setDevice(this);
    }
}

