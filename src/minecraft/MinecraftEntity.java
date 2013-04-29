package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;

import cyrus.forest.CyrusLanguage;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;

public class MinecraftEntity extends CyrusLanguage implements mod_Cyrus.Tickable {

    public MinecraftEntity(){
        super("{ \"is\": [ \"3d\", \"minecraft\", \"player\", \"entity\" ],\n}");
        mod_Cyrus.modCyrus.registerTicks(this);
    }

    public MinecraftEntity(String type, String name, LinkedList position){
        super("{ \"is\": [ \"3d\", \"minecraft\", \""+type+"\", \"entity\" ],\n"+
              "  \"name\": \""+name+"\",\n"+
              "  \"position\": "+nonStringListToListString(position)+"\n"+
              "}");
    }

    private double x=30000000,y=30000000,z=30000000;

    public void tick(float var1, Minecraft minecraft){
        EntityPlayer player=minecraft.thePlayer;
        double px=player.posX;
        double py=player.posY;
        double pz=player.posZ;
        if(vvdist(list(x,y,z),list(px,py,pz)) >= 1){
            x=px; y=py; z=pz;
            new Evaluator(this){ public void evaluate(){ try{
                contentList("position",list((int)(x-1.0),(int)(y-2.5),(int)(z-1.0)));
                self.evaluate();
            }catch(Exception e){ e.printStackTrace(); } refreshObserves(); }};
        }
    }
}


