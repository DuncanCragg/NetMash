package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;

import cyrus.forest.CyrusLanguage;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.server.MinecraftServer;

public class MinecraftEntity extends CyrusLanguage implements MinecraftCyrus.Tickable {

    public MinecraftEntity(){}

    Entity entity;

    public MinecraftEntity(Entity e, String type, String name, String worlduid){
        super("{ \"is\": [ \"editable\", \"3d\", \"minecraft\", \""+type+"\", \"entity\" ],\n"+
              "  \"name\": \""+name+"\",\n"+
              "  \"world\": \""+worlduid+"\"\n"+
              "}");
        entity=e;
        noPersist();
    }

    LinkedList newPosition=null;
    LinkedList newSpeed=null;
    String     newHolding=null;
    LinkedList targetPosition=null;

    boolean running=false;

    public void evaluate(){
        LinkedList pos=contentList("position");
        LinkedList spd=contentList("speed");
        String     hld=content(    "holding");

        super.evaluate();

        LinkedList pos2=contentList("position");
        LinkedList spd2=contentList("speed");
        String     hld2=content(    "holding");

        newPosition=(pos2!=null && !pos2.equals(pos))? pos2: null;
        newSpeed   =(spd2!=null && !spd2.equals(spd))? spd2: null;
        newHolding =(hld2!=null && !hld2.equals(hld))? hld2: null;

        targetPosition=contentList("target-position");

        if(!running){ running=true; MinecraftCyrus.self.registerTicks(this); }
    }

    int tickNum=0;

    public void tick(){
        new Evaluator(this){ public void evaluate(){
            if(++tickNum < 8) return;
            tickNum=0;
            if(!inCurrentWorld()) return;
            if(contentIsOrListContains("is","player")){
                getPlayer();
                nopersist=false;
            }
            if(entity==null) return;
            setState();
            getState();
            if(modified()) self.evaluate();
        }};
        finishInteractions();
    }

    private boolean inCurrentWorld(){
        World currentworld=world();
        if(currentworld==null) return false;
        String currentname=currentworld.worldInfo.getWorldName();
        return contentIs("world:name",currentname);
    }

    private void getPlayer(){
        for(Object o: world().playerEntities){ EntityPlayer player=(EntityPlayer)o;
            if(contentIs("name",MinecraftWorld.entityName(player))) entity=player;
        }
    }

    EntityAIMoveTowardsCoords ai2coords=null;

    private void setState(){
        if(targetPosition!=null && targetPosition.size()==3){
            Integer psx=getIntFromList(targetPosition,0);
            Integer psy=getIntFromList(targetPosition,1);
            Integer psz=getIntFromList(targetPosition,2);
            if(psx!=null && psy!=null && psz!=null){
                if(entity instanceof EntityLiving){
                    if(ai2coords==null){
                        EntityLiving entityliving=(EntityLiving)entity;
                        ai2coords=new EntityAIMoveTowardsCoords(entityliving);
                        entityliving.tasks.addTask(2, ai2coords);
                    }
                    ai2coords.tryToMoveToXYZ(psx, psy, psz, 1.0F);
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
        if(newHolding!=null){
            if(entity instanceof EntityPlayer){
                EntityPlayer player=(EntityPlayer)entity;
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
            EntityPlayer player=(EntityPlayer)entity;

            ItemStack holdings=player.getHeldItem();
            content("holding", holdings!=null? deCamelise(holdings.getItem().getUnlocalizedName().substring(5)): null);

            ChunkCoordinates spawnpos=player.getBedLocation();
            if(spawnpos!=null) contentList("spawn-position",list(spawnpos.posX,spawnpos.posY,spawnpos.posZ));
        }
        contentBool("alive", !entity.isDead);
    }

    static public void onInteracting(EntityPlayer player, String style, int x, int y, int z, int side){
        MinecraftEntity entity=MinecraftWorld.getEntityFor(player);
        if(entity!=null) entity.onInteracting(style, x,y,z, side);
    }

    static public void onInteracting(EntityPlayer player, String style, Entity e){
        MinecraftEntity entity=MinecraftWorld.getEntityFor(player);
        if(entity!=null) entity.onInteracting(style, e);
    }

    int ticks=0;

    public void onInteracting(final String style, final int x, final int y, final int z, final int side){
        new Evaluator(this){ public void evaluate(){
            contentList(style, list(x,y,z,side));
            if(modified()) self.evaluate();
        }};
        ticks=1;
    }

    public void onInteracting(final String style, final Entity e){
        new Evaluator(this){ public void evaluate(){
            content(style, MinecraftWorld.entityToUID(e));
            if(modified()) self.evaluate();
        }};
        ticks=1;
    }

    static final int WAITTICKS=6;

    public void finishInteractions(){
        if(ticks==0) return;
        if(ticks< WAITTICKS){ ticks++; return; }
        onNotInteracting("hitting");
        onNotInteracting("placing");
        onNotInteracting("touching");
        ticks=0;
    }

    public void onNotInteracting(final String style){
        new Evaluator(this){ public void evaluate(){
            content(style, null);
            if(modified()) self.evaluate();
        }};
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


