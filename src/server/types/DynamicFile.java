package server.types;

import java.io.*;
import netmash.lib.JSON;
import netmash.forest.WebObject;

public class DynamicFile extends WebObject {

    public DynamicFile(){}

    private boolean running=false;
    private long fileModified=0;

    public void evaluate(){ if(!running){ running=true; runTicker(); } }

    private void runTicker(){
        new Thread(){ public void run(){
            while(running){
                try{ Thread.sleep(2000); }catch(Exception e){}
                tick();
            }
        }}.start();
    }

    private void tick(){
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
                        refreshObserves();
                    }
                }catch(Exception e){ logFail(e); }
            }
        };
    }

    private void logFail(Exception e){
        log("Exception in DynamicFile"+(e!=null? ": "+e: ""));
        log("Reading file: "+content("watching"));
        if(e!=null) e.printStackTrace();
    }
}

