
import java.io.*;

public class PiUtils{

    static FileWriter unex=null;
    static FileWriter ex=null;

    static public boolean grabGPIO(String ch) { try {
        if(unex==null){
            unex = new FileWriter("/sys/class/gpio/unexport");
            ex =   new FileWriter("/sys/class/gpio/export");
        }
        File f = new File("/sys/class/gpio/gpio"+ch);
        if(f.exists()){
            unex.write(ch);
            unex.flush();
        }
        ex.write(ch);
        ex.flush();
        return true;
    } catch(Throwable t){ t.printStackTrace(); return false; }}

    static public boolean setGPIODir(String ch, boolean out) { try {
        FileWriter fw = new FileWriter("/sys/class/gpio/gpio"+ch+"/direction");
        fw.write(out? "out": "in");
        fw.flush();
        fw.close();
        return true;
    } catch(Throwable t){ t.printStackTrace(); return false; }}

    static public boolean setGPIOVal(String ch, boolean val) { try {
        FileWriter fw = new FileWriter("/sys/class/gpio/gpio"+ch+"/value");
        fw.write(val? "1": "0");
        fw.flush();
        fw.close();
        return true;
    } catch(Throwable t){ t.printStackTrace(); return false; }}

    static public boolean getGPIOVal(String ch) { try {
        FileReader fr = new FileReader("/sys/class/gpio/gpio"+ch+"/value");
        int r=fr.read();
        fr.close();
        return r!=0;
    } catch(Throwable t){ t.printStackTrace(); return false; }}
}

