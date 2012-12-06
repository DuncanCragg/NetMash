package server.types;

import java.io.*;
import cyrus.lib.JSON;
import cyrus.forest.*;

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
            String watching=content("watching");
            while(running){
                try{ Thread.sleep(200); }catch(Exception e){}
                tick(watching);
            }
        }}.start();
    }

    private void tick(final String watching){
        new Evaluator(this){
            public void evaluate(){
                try{
                    File file = new File(watching);
                    if(!(file.exists() && file.canRead())) throw new Exception("Can't read file");
                    long modified=file.lastModified();
                    if(modified > fileModified){
                        fileModified=modified;
                        contentReplace(new JSON(file,true));
                        content("watching", watching);
                        self.evaluate();
                    }
                    else refreshObserves();
                }catch(Exception e){ logFail(e); refreshObserves(); }
            }
        };
    }

    private void logFail(Exception e){
        log("Exception in DynamicFile"+(e!=null? ": "+e: ""));
        log("Reading file: "+content("watching"));
        if(e!=null) e.printStackTrace();
    }
}

