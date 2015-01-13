
import java.io.*;
import java.net.*;
import java.util.regex.*;

import cyrus.platform.*;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to fetch the temperature from an I2C sensor on a Raspberry Pi and broadcast its URL through a BLE beacon.
  */
public class PiBeaconTemperature extends CyrusLanguage {

    public PiBeaconTemperature(){
        super("{ is: editable 3d notice temperature sensor\n"+
              "  title: \"Temperature Sensor\"\n"+
              "  text: waiting..\n"+
              "  rotation: 0 45 0\n"+
              "  scale: 1 1 1\n"+
              "  position: 0 0 0\n"+
              "  within: http://localhost:8081/o/uid-41b6-5f8f-f143-b30d.json\n"+
              "}\n", true);
    }

    private boolean running=false;

    double T=22.2f;

    public void evaluate(){
        if(!running){ running=true;
            BLE.doAdvert(uid);
            initialisationCeremony();
            new Thread(){ public void run(){ doit(); }}.start();
        }
        super.evaluate();
        notifying(content("within"));
    }

    public void initialisationCeremony(){
    }

    void doit(){
        while(true){
            Kernel.sleep(2000);
            T+=0.01;
            new Evaluator(this){ public void evaluate(){
                contentDouble("temperature", T);
                content("text", String.format("Temperature: %.2f C", T));
            }};
        }
    }
}

