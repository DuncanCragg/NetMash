
import java.io.*;
import java.net.*;
import java.util.regex.*;

import cyrus.platform.*;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to fetch the soil moisture level detected by a sensor on a Raspberry Pi and broadcast its URL through a BLE beacon.
  */
public class PiBeaconTemperature extends CyrusLanguage {

    public PiBeaconTemperature(){
        super("{ is: editable 3d notice soil moisture sensor\n"+
              "  title: \"Soil Moisture Sensor\"\n"+
              "  text: waiting..\n"+
              "  rotation: 0 45 0\n"+
              "  scale: 1 1 1\n"+
              "  position: 0 0 0\n"+
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

    int scaleFactor=28;
    int[] measurements=new int[20];
    int i=0;
    double avg;

    void doit(){
        for(i=0; i< 20; i++) measurements[i]=0;
        i=0;
        while(true){
            PiUtils.setGPIODir("4", "out");
            PiUtils.setGPIOVal("4", false);
            Kernel.sleep(500);
            PiUtils.setGPIODir("4", "in");

            long startTime=System.nanoTime();
            int waitCount; for(waitCount=0; PiUtils.getGPIOVal("4")=='0' && waitCount<2000; waitCount++);
            int measurement=(int)(System.nanoTime()-startTime)/1000000;
            logXX(waitCount, measurement);

            if(PiUtils.getGPIOVal("4")!='1' ||
               waitCount==2000              ||
               measurement<0                ||
               measurement>300                   ) continue;

            measurements[i]=measurement; i++; if(i==20) i=0;
            int average=0; for(int n=0; n<20; n++) average+=measurements[n]; average/=scaleFactor;
            if(average>100) average=100;

            String measurementsAsString="["; for(int n=0; n<20; n++) measurementsAsString+=" "+measurements[n];
            logXX(measurementsAsString+" ]", average);

            avg=average;
            new Evaluator(this){ public void evaluate(){
                contentDouble("soil-moisture", avg);
                content("text", String.format("Soil Moisture: %.0f %%", avg));
            }};
        }
    }
}

