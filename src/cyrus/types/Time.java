package cyrus.types;

import java.lang.*;

import cyrus.forest.*;

public class Time extends WebObject {

    public Time(){}

    private boolean running=false;

    public void evaluate(){
        content("is","time");
        contentObject("timestamp", Long.valueOf(System.currentTimeMillis()));
        if(!running) runTicker();
    }

    private void runTicker(){
        running=true;
        new Thread(){ public void run(){
            while(running){
                try{ Thread.sleep(1000); }catch(Exception e){}
                tick();
        }}}.start();
    }

    private void tick(){
        new Evaluator(this){ public void evaluate(){ contentObject("timestamp", Long.valueOf(System.currentTimeMillis())); } };
    }
}


