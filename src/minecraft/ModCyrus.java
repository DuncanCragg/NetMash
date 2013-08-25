package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import cyrus.Version;
import cyrus.Cyrus;
import cyrus.platform.Kernel;
import cyrus.lib.JSON;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

import net.minecraft.server.MinecraftServer;

public class mod_Cyrus {

    static public mod_Cyrus modCyrus;

    static public void load(){ modCyrus=new mod_Cyrus(); }

    public mod_Cyrus(){

        InputStream configis=this.getClass().getClassLoader().getResourceAsStream("cyrusconfig.db");
        JSON config=null;
        try{ config = new JSON(configis,true); }catch(Exception e){ throw new RuntimeException("Error in config file: "+e); }

        System.out.println("-------------------");
        System.out.println(Version.NAME+" "+Version.NUMBERS);
        Kernel.init(config, new FunctionalObserver());
        Kernel.run();
    }

    public interface Tickable { public void tick(); }

    CopyOnWriteArrayList<Tickable> tickables=new CopyOnWriteArrayList<Tickable>();

    public void registerTicks(Tickable tickable){ tickables.add(tickable); }

    public void onTick(){
        if(!checkIfNewWorld()) return;
        for(Tickable tickable: tickables){
            long s=System.currentTimeMillis();
            tickable.tick();
            long e=System.currentTimeMillis();
            if(e-s > 50) log("***** Tick took "+(e-s)+"ms for:\n"+tickable);
        }
    }

    String worldname=null;

    private boolean checkIfNewWorld(){
        MinecraftServer server=MinecraftServer.getServer();
        if(server==null) return false;
        World world=server.worldServerForDimension(0);
        if(world==null) return false;
        String name=world.worldInfo.getWorldName();
        if(name==null) return false;
        if(!name.equals(worldname)){
            worldname=name;
            MinecraftCyrus.newWorld(worldname,world);
        }
        return true;
    }
}


