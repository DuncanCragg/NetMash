package net.minecraft.src;

import java.util.*;
import java.io.*;

import cyrus.Version;
import cyrus.Cyrus;
import cyrus.platform.Kernel;
import cyrus.lib.JSON;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;

public class mod_Cyrus extends BaseMod {

    public String getVersion(){ return "Cyrus Minecraft Mod 0.01"; }

    public static mod_Cyrus modCyrus;

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

        modCyrus=this;
        ModLoader.setInGameHook(this, true, true);
        ModLoader.setInGUIHook(this, true, true);
        ModLoader.registerKey(this, new KeyBinding("Alt-Tab", 0xa5), true);

        System.out.println("-------------------");
        System.out.println(Version.NAME+" "+Version.NUMBERS);
        Kernel.init(config, new FunctionalObserver(topdbis, topdbos));
        Kernel.run();
    }

    public void keyboardEvent(KeyBinding var1) {
logXX("key "+var1);
    }

    public interface Tickable { public void tick(float var1, Minecraft var2); }

    LinkedList<Tickable> tickables=new LinkedList<Tickable>();

    public void registerTicks(Tickable tickable){ tickables.add(tickable); }

    public boolean onTickInGame(float var1, Minecraft var2) {
       for(Tickable tickable: tickables) tickable.tick(var1, var2);
       return true;
    }

    public boolean onTickInGUI(float var1, Minecraft var2, GuiScreen var3) {
       return true;
    }

}


