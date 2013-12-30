
import java.io.File;
import java.io.FileWriter;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to drive an RGB LED and broadcast its URL through a BLE beacon on a Raspberry Pi.
  */
public class PiBeaconLight extends CyrusLanguage {

    public PiBeaconLight(){
        super("{ Rules: http://netmash.net/o/uid-16bd-140a-8862-41cd.cyr\n"+
              "         http://netmash.net/o/uid-9011-94df-9feb-e3c2.cyr\n"+
              "         http://netmash.net/o/uid-2f18-945a-c460-9bd7.cyr\n"+
              "  is: 3d cuboid editable\n"+
              "  title: Light\n"+
              "  rotation: 45 45 45\n"+
              "  scale: 1 1 1\n"+
              "  light: 1 1 1\n"+
              "  within: http://192.168.0.8:8081/o/uid-41b6-5f8f-f143-b30d.json\n"+
              "  v: 0\n"+
              "}\n", true);
    }

    private boolean running=false;

    double R=1f;
    double G=1f;
    double B=1f;

    public void evaluate(){
        if(!running){ running=true;
            initialisationCeremony();
            new Thread(){ public void run(){ doit(); }}.start();
        }
        super.evaluate();
        R=contentDouble("light:0");
        G=contentDouble("light:1");
        B=contentDouble("light:2");
    }

    FileWriter unex;
    FileWriter ex;

    public void initialisationCeremony(){

        FileWriter[] commandChannels;

        try {

            unex = new FileWriter("/sys/class/gpio/unexport");
            ex =   new FileWriter("/sys/class/gpio/export");

            grabGPIO("23");
            grabGPIO("24");

            setGPIOout("23");
            setGPIOout("24");

        }catch(Exception e){ e.printStackTrace(); }
    }

    void grabGPIO(String ch) throws Exception{
        File f = new File("/sys/class/gpio/gpio"+ch);
        if(f.exists()){
            unex.write(ch);
            unex.flush();
        }
        ex.write(ch);
        ex.flush();
    }

    void setGPIOout(String ch) throws Exception{
        FileWriter fw = new FileWriter("/sys/class/gpio/gpio"+ch+"/direction");
        fw.write("out");
        fw.flush();
        fw.close();
    }

    void doit(){

        try {

        FileWriter lr = new FileWriter("/sys/class/gpio/gpio23/value");
        FileWriter lg = new FileWriter("/sys/class/gpio/gpio24/value");

        while(true){
            lr.write("1"); lr.flush();
            lg.write("1"); lg.flush();
            int tr=(int)((R/1.4)*16);
            int tg=(int)((G/1.4)*16);
            if(tr<tg){ sleep(tr);    lr.write("0"); lr.flush();
                       sleep(tg-tr); lg.write("0"); lg.flush();
                       sleep(16-tg); }
            else     { sleep(tg);    lg.write("0"); lg.flush();
                       sleep(tr-tg); lr.write("0"); lr.flush();
                       sleep(16-tr); }
        }

        }catch(Exception e){ e.printStackTrace(); }
    }

    void sleep(int ms) throws Exception { if(ms<=0) return; Thread.sleep(ms); }
}

