
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

    int smoothmicros=2000;

    void doit(){ try{
        while(true){
            List<String> strings=Files.readAllLines(Paths.get("/run/moisture.txt"), Charset.forName("UTF-8"));
            LinkedList<Integer> values=new LinkedList<Integer>();
            for(String s: strings) values.add(Integer.parseInt(s));
            Collections.sort(values);

            int sum=0; int num=values.size()/2;
            for(int i=0; i<num; i++) sum+=values.get(i);
            final int ave=sum/num;

            logXX(values, ave);
            new Evaluator(this){ public void evaluate(){
                contentInt("soil-moisture", ave);
                content("text", String.format("Soil Moisture: %d%%", ave));
            }};
            Kernel.sleep(3000);
        }
    }catch(Throwable t){ t.printStackTrace(); } }

    void doitInJavaOrNot(){
        while(true){
            int loops=20;
            int hold=50;
            long startTime=System.nanoTime();
            int waitCount=0;
            for(int l=0; l<loops; l++){
                PiUtils.setGPIODir("4", "out");
                PiUtils.setGPIOVal("4", false);
                Kernel.sleep(hold);
                PiUtils.setGPIODir("4", "in");
                while(PiUtils.getGPIOVal("4")!='1' && waitCount<2000) waitCount++;
                if(waitCount==2000){ logXX("waited too long"); waitCount=0; break; }
            }
            if(waitCount==0){ logXX("unstable"); continue; }

            int microseconds=(int)((System.nanoTime()-startTime)/loops)/1000 - hold*1000 - 2900;

            if(microseconds/waitCount>20){ logXX("unstable", waitCount, "its", microseconds/waitCount, "stability", microseconds, "uS"); continue; }

            smoothmicros=((85*smoothmicros)+(15*microseconds))/100;

            int picofarads=smoothmicros/5;

            double mst=(picofarads-120)/10; if(mst<0) mst=0; if(mst>100) mst=100;

            logXX(waitCount, "its", microseconds/waitCount, "stability", microseconds, "uS", smoothmicros, "uS", picofarads, "pF", mst, "%");

            final double moisture=mst;
            new Evaluator(this){ public void evaluate(){
                contentDouble("soil-moisture", moisture);
                content("text", String.format("Soil Moisture: %.0f %%", moisture));
            }};
        }
    }
}

