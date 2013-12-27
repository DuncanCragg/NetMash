
import java.io.File;
import java.io.FileWriter;

import cyrus.forest.WebObject;

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

    static final String GPIO_OUT = "out";
    static final String GPIO_ON = "1";
    static final String GPIO_OFF = "0";
    static String[] GpioChannels = { "23", "24" };

    public void initialisationCeremony(){

        FileWriter[] commandChannels;

        try {

            FileWriter unexportFile = new FileWriter("/sys/class/gpio/unexport");
            FileWriter exportFile =   new FileWriter("/sys/class/gpio/export");

            for(String gpioChannel: GpioChannels){

                System.out.println(gpioChannel);

                File exportFileCheck = new File("/sys/class/gpio/gpio"+gpioChannel);
                if(exportFileCheck.exists()){
                    unexportFile.write(gpioChannel);
                    unexportFile.flush();
                }

                exportFile.write(gpioChannel);
                exportFile.flush();

                FileWriter directionFile = new FileWriter("/sys/class/gpio/gpio" + gpioChannel + "/direction");

                directionFile.write(GPIO_OUT);
                directionFile.flush();
            }

        }catch(Exception e){ e.printStackTrace(); }
    }

    void doit(){

        try {

        FileWriter l1 = new FileWriter("/sys/class/gpio/gpio" + GpioChannels[0] + "/value");
        FileWriter l2 = new FileWriter("/sys/class/gpio/gpio" + GpioChannels[1] + "/value");

        int totalt = 1024;
        int mark = 0;
        int dir = 8;

        while(true){
            float m=mark/64f;
            float s=(totalt-mark)/64f;
            int mt=(int)m;
            int st=(int)s;

            l1.write(GPIO_ON);
            l1.flush();
            l2.write(GPIO_OFF);
            l2.flush();
            java.lang.Thread.sleep(mt);

            l1.write(GPIO_OFF);
            l1.flush();
            l2.write(GPIO_ON);
            l2.flush();
            java.lang.Thread.sleep(st);

            mark+=dir; if(mark>=totalt) dir= -dir; if(mark<=0) dir= -dir;
        }

        }catch(Exception e){ e.printStackTrace(); }
    }
}

