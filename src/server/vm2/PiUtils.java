
import java.io.*;

import static cyrus.lib.Utils.*;

public class PiUtils{

    static FileWriter unex=null;
    static FileWriter ex=null;

    static LinkedHashMap<String,String> pinMapping=null;

    static String getPinMapping(String pin){
        if(pinMapping==null){
            pinMapping = new LinkedHashMap<String,String>();
            pinMapping.put("4","7");
        }
        return pinMapping.get(pin);
    }

    static public boolean grabGPIO(String pin, String mode) { try {
        String pinwire=getPinMapping(pin);
        if(pinwire!=null && mode!=null) exec(String.format("gpio mode %s %s", pinwire, mode);
        if(unex==null){
            unex = new FileWriter("/sys/class/gpio/unexport");
            ex =   new FileWriter("/sys/class/gpio/export");
        }
        File f = new File("/sys/class/gpio/gpio"+pin);
        if(f.exists()){
            unex.write(pin);
            unex.flush();
        }
        ex.write(pin);
        ex.flush();
        return true;
    } catch(Throwable t){ t.printStackTrace(); return false; }}

    static public boolean setGPIODir(String pin, String dir) { try {
        FileWriter fw = new FileWriter("/sys/class/gpio/gpio"+pin+"/direction");
        fw.write(dir);
        fw.flush();
        fw.close();
        return true;
    } catch(Throwable t){ t.printStackTrace(); return false; }}

    static public boolean setGPIOVal(String pin, boolean val) { try {
        FileWriter fw = new FileWriter("/sys/class/gpio/gpio"+pin+"/value");
        fw.write(val? "1": "0");
        fw.flush();
        fw.close();
        return true;
    } catch(Throwable t){ t.printStackTrace(); return false; }}

    static public boolean getGPIOVal(String pin) { try {
        FileReader fr = new FileReader("/sys/class/gpio/gpio"+pin+"/value");
        int r=fr.read();
        fr.close();
        return r=='1';
    } catch(Throwable t){ t.printStackTrace(); return false; }}
}

