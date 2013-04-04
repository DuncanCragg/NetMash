package net.minecraft.src;

import java.io.*;

import cyrus.Version;
import cyrus.Cyrus;
import cyrus.platform.Kernel;
import cyrus.lib.JSON;
import cyrus.forest.*;

public class mod_Cyrus extends BaseMod {

    public String getVersion(){ return "Cyrus Minecraft Mod 0.01"; }

    public void load(){

        InputStream configis=this.getClass().getClassLoader().getResourceAsStream("cyrusconfig.db");
        JSON config=null;
        try{ config = new JSON(configis,true); }catch(Exception e){ throw new RuntimeException("Error in config file: "+e); }

        String db = config.stringPathN("persist:db");

        InputStream topdbis=null;
        try{ topdbis = new FileInputStream(db); }catch(Exception e){ }
        if(topdbis==null) topdbis=this.getClass().getClassLoader().getResourceAsStream("top.db");

        FileOutputStream topdbos=null;
        try{ topdbos = new FileOutputStream(db, true); }catch(Exception e){ throw new RuntimeException("Local DB: "+e); }

        System.out.println("-------------------");
        System.out.println(Version.NAME+" "+Version.NUMBERS);
        Kernel.init(config, new FunctionalObserver(topdbis, topdbos));
        Kernel.run();
    }
}


