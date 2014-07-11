
import java.io.*;
import java.net.*;
import java.util.regex.*;

import cyrus.platform.*;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

/** Class to drive an RGB LED and broadcast its URL through a BLE beacon on a Raspberry Pi.
  */
public class PiBeaconLight extends CyrusLanguage {

    public PiBeaconLight(){
        super("{ is: editable 3d cuboid light\n"+
              "  Rules: http://netmash.net/o/uid-16bd-140a-8862-41cd.cyr\n"+
              "         http://netmash.net/o/uid-9011-94df-9feb-e3c2.cyr\n"+
              "         http://netmash.net/o/uid-2f18-945a-c460-9bd7.cyr\n"+
              "  P: { }\n"+
              "  title: Light\n"+
              "  rotation: 45 45 45\n"+
              "  scale: 1 1 1\n"+
              "  light: 1 1 1\n"+
              "  position: 0 0 0\n"+
              "  within: http://localhost:8081/o/uid-41b6-5f8f-f143-b30d.json\n"+
              "}\n", true);
    }

    private boolean running=false;

    double R=1f;
    double G=1f;
    double B=1f;

    public void evaluate(){
        if(!running){ running=true;
            startBroadcasting();
            initialisationCeremony();
            new Thread(){ public void run(){ doit(); }}.start();
        }
        notifying(content("within"));
        super.evaluate();
        R=contentDouble("light:0");
        G=contentDouble("light:1");
        B=contentDouble("light:2");
    }

    void startBroadcasting(){
        logXX("startBroadcasting",uid,UID.toURL(uid));
        InetAddress ip=Kernel.IP();
        byte[] ipbytes= ip==null? new byte[]{127,0,0,1}: ip.getAddress();
        int port=Kernel.config.intPathN("network:port");
        String re="uid-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])-([0-9a-f][0-9a-f])([0-9a-f][0-9a-f])";
        Matcher m = Pattern.compile(re).matcher(uid);
        if(!m.matches()) return;
                 //    hcitool -i hci0 cmd 0x08 0x0008 1c 02 01 1a 18 ff 4c 00 02 15 c0 a8 00 00 00 00 15 01 a7 ed 15 01 a7 ed 00 00 00 00 00
        String advert="hcitool -i hci0 cmd 0x08 0x0008 1c 02 01 1a 18 ff 4c 00 02 15 ";
        advert=advert+String.format("%02x %02x %02x %02x %02x %02x %s %s %s %s %s %s %s %s ",
                                     ipbytes[0], ipbytes[1], ipbytes[2], ipbytes[3],
                                     port/256, port-((port/256)*256),
                                     m.group(1),m.group(2),m.group(3),m.group(4),m.group(5),m.group(6),m.group(7),m.group(8));
        advert=advert+"00 00 00 00 00";
        exec("hciconfig hci0 down");
        exec("hciconfig hci0 up");
        exec(advert);
        // set rate http://stackoverflow.com/questions/21124993/is-there-a-way-to-increase-ble-advertisement-frequency-in-bluez
        //                                    0x0300*0.625ms = 480ms
        exec("hcitool -i hci0 cmd 0x08 0x0006 00 03 00 03 00 00 00 00 00 00 00 00 00 07 00");
        exec("hcitool -i hci0 cmd 0x08 0x000a 01"); // leadv
        exec(advert);                               // override Ubuntu crap set on leadv
        exec("hciconfig hci0 noscan");              // stop more Ubuntu shite
        logXX(advert);
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

