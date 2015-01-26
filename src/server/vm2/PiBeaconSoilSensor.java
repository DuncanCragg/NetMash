
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.nio.file.*;
import java.nio.charset.*;

import cyrus.platform.*;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to fetch the soil moisture level detected by a sensor on a Raspberry Pi and broadcast its URL through a BLE beacon.
  */
public class PiBeaconSoilSensor extends CyrusLanguage {

    public PiBeaconSoilSensor(){
        super("{ is: editable 3d notice soil moisture sensor\n"+
              "  title: \"Soil Moisture Sensor\"\n"+
              "  text: waiting..\n"+
              "  rotation: 0 45 0\n"+
              "  scale: 1 1 1\n"+
              "  position: 0 0 0\n"+
              "  soil-moisture: 25\n"+
              "  within: http://localhost:8081/o/uid-41b6-5f8f-f143-b30d.json\n"+
              "}\n", true);
    }

    private boolean running=false;

    public void evaluate(){
        if(!running){ running=true;
            BLE.doAdvert(uid);
            PiUtils.grabGPIO("4","tri");
            new Thread(){ public void run(){ doit(); }}.start();
        }
        super.evaluate();
        notifying(content("within"));
    }

    int smoothmicros=2000;

    void doit(){ try{
        while(true){
            Kernel.sleep(3000);
            String ms=new String(Files.readAllBytes(Paths.get("/run/moisture.txt")));
            Number n=findANumberIn(ms);
            if(n==null) continue;
            final int moisture=n.intValue();
            logXX(moisture);
            new Evaluator(this){ public void evaluate(){
                contentInt("soil-moisture", moisture);
                content("text", String.format("Soil Moisture: %d%%", moisture));
            }};
        }
    }catch(Throwable t){ t.printStackTrace(); } }
}

