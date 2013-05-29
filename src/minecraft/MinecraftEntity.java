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
    Entity entity2;

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
        setOwnState();
        if(!running){ running=true; mod_Cyrus.modCyrus.registerTicks(this); }
    }

    LinkedList newPosition=null;
    LinkedList newSpeed=null;

    private void setOwnState(){
        newPosition=contentList("set-position");
        newSpeed   =contentList("set-speed");
    }

    int tickNum=0;

    public void tick(float var1, Minecraft minecraft){
        if(++tickNum==8) tickNum=0;
        if(entity instanceof EntityPlayer){
            EntityPlayer player=minecraft.thePlayer;
            if(entity.equals(player)) entity=player;
            entity2=otherPlayer((EntityPlayer)entity);
        }
        new Evaluator(this){ public void evaluate(){ try{
            if(tickNum==0){
                setAndGetState();
                self.evaluate();
            }
        }catch(Exception e){ e.printStackTrace(); } refreshObserves(); }};
    }

    private void setAndGetState(){
        if(newPosition!=null && newPosition.size()==3){
            Integer psx=getIntFromList(newPosition,0);
            Integer psy=getIntFromList(newPosition,1);
            Integer psz=getIntFromList(newPosition,2);
            if(psx!=null && psy!=null && psz!=null){
                // setPosition() doesn't seem to work
            }
        }
        if(newSpeed!=null && newSpeed.size()==3){
            Float spx=getFloatFromList(newSpeed,0,0);
            Float spy=getFloatFromList(newSpeed,1,0);
            Float spz=getFloatFromList(newSpeed,2,0);
            if(spx!=null && spy!=null && spz!=null){
                if(spx< -1) spx= -1f; if(spy< -1) spy= -1f; if(spz< -1) spz= -1f;
                if(spx>  1) spx=  1f; if(spy>  1) spy=  1f; if(spz>  1) spz=  1f;
                entity.motionX+=spx; if(entity.motionX>1) entity.motionX=1f; if(entity.motionX< -1) entity.motionX= -1f;
                entity.motionY+=spy; if(entity.motionY>1) entity.motionY=1f; if(entity.motionY< -1) entity.motionY= -1f;
                entity.motionZ+=spz; if(entity.motionZ>1) entity.motionZ=1f; if(entity.motionZ< -1) entity.motionZ= -1f;
            }
        }
        int ppx=(int)entity.posX;
        int ppy=(int)entity.posY;
        int ppz=(int)entity.posZ;
        contentList("position",list(ppx,ppy,ppz));
        int spx=(int)(entity.motionX*100);
        int spy=(int)(entity.motionY*100);
        int spz=(int)(entity.motionZ*100);
        contentList("speed",list(spx/100f,spy/100f,spz/100f));
        contentBool("on-ground", entity.onGround);
        contentBool("collided",  entity.isCollided);
        if(entity instanceof EntityPlayer){
            ChunkCoordinates   spawnpos=(entity2!=null? ((EntityPlayer)entity2).getBedLocation(): null);
            if(spawnpos==null) spawnpos=                ((EntityPlayer)entity ).getBedLocation();
            if(spawnpos!=null) contentList("spawn-position",list(spawnpos.posX,spawnpos.posY,spawnpos.posZ));
        }
        contentBool("alive", !entity.isDead);
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


