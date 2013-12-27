
import java.io.File;
import java.io.FileWriter;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** .
  */
public class PiBeaconLight extends CyrusLanguage {

    public PiBeaconLight(){ }

    private boolean running=false;

    public void evaluate(){
        if(!running){ running=true;
            initialisationCeremony();
            new Thread(){ public void run(){ doit(); }}.start();
        }
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

        FileWriter l1 = new FileWriter("/sys/class/gpio/gpio23/value");
        FileWriter l2 = new FileWriter("/sys/class/gpio/gpio24/value");

        int total = 1024;
        int mark = 0;
        int d = 8;

        while(true){
            int m=mark/64;
            int s=(total-mark)/64;

            l1.write("1");
            l1.flush();
            l2.write("0");
            l2.flush();
            java.lang.Thread.sleep(m);

            l1.write("0");
            l1.flush();
            l2.write("1");
            l2.flush();
            java.lang.Thread.sleep(s);

            mark+=d;
            if(mark>=total) d= -d;
            if(mark<=0) d= -d;
        }

        }catch(Exception e){ e.printStackTrace(); }
    }
}

