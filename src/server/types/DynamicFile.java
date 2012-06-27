package server.types;

import java.io.*;
import netmash.lib.JSON;
import netmash.forest.WebObject;
import netmash.forest.Fjord;

public class DynamicFile extends Fjord {

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
        final WebObject self=this;
        new Evaluator(this){
            public void evaluate(){
                try{
                    String filename=content("watching");
                    File file = new File(filename);
                    if(!(file.exists() && file.canRead())) throw new Exception("Can't read file");
                    long modified=file.lastModified();
                    if(modified > fileModified){
                        fileModified=modified;
                        contentMerge(new JSON(file));
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

