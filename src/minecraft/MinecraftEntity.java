package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;

import cyrus.forest.CyrusLanguage;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.*;
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
        noPersist();
    }

    boolean running=false;

    public void evaluate(){
        super.evaluate();
        if(!running){ running=true; mod_Cyrus.modCyrus.registerTicks(this); }
        setOwnState();
    }

    LinkedList newPosition=null;
    LinkedList newSpeed=null;

    EntityAIMoveTowardsCoords ai2coords=null;

    private void setOwnState(){
        newPosition=contentList("set-position");
        newSpeed   =contentList("set-speed");
    }

    int tickNum=0;

    public void tick(float var1, final Minecraft minecraft){
        final MinecraftEntity me=this;
        new Evaluator(this){ public void evaluate(){
            if(++tickNum < 8) return;
            tickNum=0;
            if(!inCurrentWorld()) return;
            if(contentIsOrListContains("is","player")){
                getPlayers(minecraft);
                mod_Cyrus.modCyrus.registerPlayer(me);
                nopersist=false;
            }
            if(entity==null) return;
            setState();
            getState();
            if(modified()) self.evaluate();
        }};
    }

    private boolean inCurrentWorld(){
        World currentworld=world();
        if(currentworld==null) return false;
        String currentname=currentworld.worldInfo.getWorldName();
        return contentIs("world:name",currentname);
    }

    private void getPlayers(Minecraft minecraft){
        EntityPlayer player=minecraft.thePlayer;
     /* if(entity.equals(player)) */
        entity=player;
        entity2=otherPlayer((EntityPlayer)entity);
    }

    private void setState(){
        if(newPosition!=null && newPosition.size()==3){
            Integer psx=getIntFromList(newPosition,0);
            Integer psy=getIntFromList(newPosition,1);
            Integer psz=getIntFromList(newPosition,2);
            if(psx!=null && psy!=null && psz!=null){
                if(entity instanceof EntityLiving){
                    if(ai2coords==null){
                        EntityLiving entityliving=(EntityLiving)entity;
                        ai2coords=new EntityAIMoveTowardsCoords(entityliving);
                        entityliving.tasks.addTask(2, ai2coords);
                    }
                    ai2coords.tryToMoveToXYZ(psx, psy, psz, ModLoader.VERSION.contains("1.5")? 0.3F: 1.0F);
                }
            }
            else if(ai2coords!=null) ai2coords.resetTask();
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
    }

    private void getState(){
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
            EntityPlayer player1=(EntityPlayer)entity;
            EntityPlayer player2=(EntityPlayer)entity2;

            ItemStack holdings=player1.getHeldItem();
            content("holding", holdings!=null? deCamelise(holdings.getItem().getUnlocalizedName().substring(5)): null);

            ChunkCoordinates   spawnpos=(player2!=null? player2.getBedLocation(): null);
            if(spawnpos==null) spawnpos=                player1.getBedLocation();
            if(spawnpos!=null) contentList("spawn-position",list(spawnpos.posX,spawnpos.posY,spawnpos.posZ));
        }
        contentBool("alive", !entity.isDead);
    }

    public void onInteracting(final String style, final int x, final int y, final int z, final int side){
        new Evaluator(this){ public void evaluate(){
            contentList(style, list(x,y,z,side));
            if(modified()) self.evaluate();
        }};
    }

    public void onInteracting(final String style, final Entity e){
        new Evaluator(this){ public void evaluate(){
            content(style, MinecraftWorld.entityToCyrus(e));
            if(modified()) self.evaluate();
        }};
    }

    public void onNotInteracting(final String style){
        new Evaluator(this){ public void evaluate(){
            content(style, null);
            if(modified()) self.evaluate();
        }};
    }

    private EntityPlayer otherPlayer(EntityPlayer player){
        for(Object p: world().playerEntities) if(p.equals(player)) return (EntityPlayer)p;
        return null;
    }

    public World world(){
        MinecraftServer server=MinecraftServer.getServer();
        return server==null? null: server.worldServerForDimension(0);
    }

    class EntityAIMoveTowardsCoords extends EntityAIBase {

        private EntityLiving entity;
        private double psx, psy, psz;
        private float speed;
        private boolean running=true;

        public EntityAIMoveTowardsCoords(EntityLiving entity){
            this.entity=entity;
            this.setMutexBits(1);
        }

        public void tryToMoveToXYZ(double psx, double psy, double psz, float speed){
            if(this.psx==psx && this.psy==psy && this.psz==psz && this.speed==speed) return;
            this.psx=psx; this.psy=psy; this.psz=psz; this.speed=speed;
            this.running=true;
        }

        public boolean shouldExecute(){ return running; }

        public boolean continueExecuting(){ return running && !entity.getNavigator().noPath(); }

        public void resetTask(){ running=false; }

        public void startExecuting(){ entity.getNavigator().tryMoveToXYZ(psx, psy, psz, speed); }
    }
}


