package net.minecraft.src;

import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;

public class CyrusUser extends WebObject implements mod_Cyrus.Tickable {

    public CyrusUser(){
        mod_Cyrus.modCyrus.registerTicks(this);
    }

    private double x=30000000,y=30000000,z=30000000;

    public void tick(float var1, Minecraft minecraft){
        EntityPlayer player=minecraft.thePlayer;
        double px=player.posX;
        double py=player.posY;
        double pz=player.posZ;
        if(vvdist(list(x,y,z),list(px,py,pz)) > 1){
            x=px; y=py; z=pz;
            new Evaluator(this){ public void evaluate(){
                try{
                    contentList("position",list((int)(x-1.0),(int)(y-2.5),(int)(z-1.0)));
                }catch(Exception e){ e.printStackTrace(); refreshObserves(); }
            }};
        }
    }
}


