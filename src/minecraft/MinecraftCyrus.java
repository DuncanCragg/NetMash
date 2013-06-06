package net.minecraft.src;

import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public class MinecraftCyrus extends WebObject {

    static MinecraftCyrus that=null;

    public MinecraftCyrus(){ that=this; }

    public void evaluate(){
        contentAll("worlds:name");
    }

    static public void newWorld(String worldname, World world){
        if(that==null) return;
        that.doNewWorld(worldname,world);
    }

    private void doNewWorld(final String worldname, final World world){
        new Evaluator(that){ public void evaluate(){ try{
            if(contentAllContains("worlds:name",worldname)) return;
            contentListAdd("worlds", spawn(new MinecraftWorld(worldname,world)));
        }catch(Exception e){ e.printStackTrace(); } refreshObserves(); }};
    }
}

