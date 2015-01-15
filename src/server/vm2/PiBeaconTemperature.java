
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

    double T=0f;

    public void evaluate(){
        if(!running){ running=true;
            BLE.doAdvert(uid);
            PiUtils.grabGPIO("4");
            new Thread(){ public void run(){ doit(); }}.start();
        }
        super.evaluate();
        notifying(content("within"));
    }

    void doit(){
        while(true){
            PiUtils.setGPIODir("4", "out");
            PiUtils.setGPIOVal("4", false);
            Kernel.sleep(100);
            PiUtils.setGPIODir("4", "in");
            long x=0;
            while(!PiUtils.getGPIOVal("4") && x<10000) x++;
            T=x;
            new Evaluator(this){ public void evaluate(){
                contentDouble("soil-moisture", T);
                content("text", String.format("Soil Moisture: %.0f", T));
            }};
        }
    }
}

