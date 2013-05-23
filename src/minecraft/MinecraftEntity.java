package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;

import cyrus.forest.CyrusLanguage;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public class MinecraftEntity extends CyrusLanguage implements mod_Cyrus.Tickable {

    public MinecraftEntity(){}

    Entity entity;

    public MinecraftEntity(Entity e, String type, String name, String worlduid){
        super("{ \"is\": [ \"editable\", \"3d\", \"minecraft\", \""+type+"\", \"entity\" ],\n"+
              "  \"name\": \""+name+"\",\n"+
              "  \"world\": \""+worlduid+"\"\n"+
              "}");
        entity=e;
    }

    boolean running=false;

    public void evaluate(){
        super.evaluate();
        if(!running){ running=true; mod_Cyrus.modCyrus.registerTicks(this); }
    }

    private double x=30000000,y=30000000,z=30000000;

    boolean thePlayer=false;
    int currentplayer=0;

    public void tick(float var1, Minecraft minecraft){
        final boolean[] newplayer={false};
        if(entity instanceof EntityPlayer){
            EntityPlayer player=minecraft.thePlayer;
            if(entity==player) thePlayer=true;
            if(thePlayer){
                int i=System.identityHashCode(player);
                if(i!=currentplayer){ currentplayer=i; entity=player; newplayer[0]=true; }
            }
        }
        double px=entity.posX;
        double py=entity.posY;
        double pz=entity.posZ;
        if(vvdist(list(x,y,z),list(px,py,pz)) >= 0.5){
            x=px; y=py; z=pz;
            new Evaluator(this){ public void evaluate(){ try{
                contentList("position",list((int)(x-1.0),(int)(y-2.5),(int)(z-1.0)));
                if(entity instanceof EntityPlayer){
                    EntityPlayer player=(EntityPlayer)entity;
                    if(!contentSet("spawn-position") || newplayer[0]){
                        EntityPlayer player2=otherPlayer(player);
                        ChunkCoordinates spawnpos=(player2==null)? null: player2.getBedLocation();
                        if(spawnpos!=null) contentList("spawn-position",list(spawnpos.posX,spawnpos.posY,spawnpos.posZ));
                    }
                }
                self.evaluate();
            }catch(Exception e){ e.printStackTrace(); } refreshObserves(); }};
        }
    }

    private EntityPlayer otherPlayer(EntityPlayer player){
        for(Object p: world().playerEntities) if(p.equals(player)) return (EntityPlayer)p;
        return null;
    }

    public World world(){
        MinecraftServer server=MinecraftServer.getServer();
        return server==null? null: server.worldServerForDimension(0);
    }
}


