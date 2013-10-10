package server.types;

import java.io.*;
import cyrus.lib.JSON;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

public class DynamicFile extends CyrusLanguage {

    public DynamicFile(){}

    private boolean running=false;
    private long fileModified=0;

    public void evaluate(){
        super.evaluate();
        if(!running) runTicker();
    }

    private void runTicker(){
        running=true;
        new Thread(){ public void run(){
            while(running){
                try{ Thread.sleep(200); }catch(Exception e){}
                tick();
            }
        }}.start();
    }

    private void tick(){
        new Evaluator(this){ public void evaluate(){ try{
            String watching=content("watching");
            File file = new File(watching);
            if(!(file.exists() && file.canRead())) throw new Exception("Can't read file");
            long modified=file.lastModified();
            if(modified > fileModified){
                fileModified=modified;
                contentReplace(new JSON(file,true));
                content("watching", watching);
                self.evaluate();

            }
        } catch(Exception e){ e.printStackTrace(); }}};
    }
}

