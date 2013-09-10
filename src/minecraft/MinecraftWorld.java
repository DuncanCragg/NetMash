package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;

import cyrus.forest.CyrusLanguage;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.server.MinecraftServer;

public class MinecraftWorld extends CyrusLanguage implements MinecraftCyrus.Tickable {

    public MinecraftWorld(){}

    public MinecraftWorld(String name, World world){
        super("{ \"is\": [ \"editable\", \"queryable\", \"updatable\", \"3d\", \"minecraft\", \"world\" ],\n"+
              "  \"name\": \""+name+"\"\n"+
              "}");
        setWorld(world);
    }

    public MinecraftWorld(String worlduid, String scanneruid, boolean isplayer){
        super("{ \"is\": [ \"3d\", \"minecraft\", \"structure\" ],\n"+
              "  \"world\": \""+worlduid+"\",\n"+
              "  \"scanner\": \""+scanneruid+"\"\n"+
              "}");
        if(!isplayer) noPersist();
    }

    public MinecraftWorld(String worlduid, LinkedList position){
        super("{ is: 3d minecraft cube structure\n"+
              "  world: "+worlduid+"\n"+
              "  within: "+worlduid+"\n"+
              "  position: "+setToListString(position,true)+"\n"+
              "  trail: true\n"+
              "}",true);
    }

    private String hasType;

    boolean running=false;

    public void evaluate(){
        if(contentIsOrListContains("is","world"))     hasType="world";
        if(contentIsOrListContains("is","structure")) hasType="structure";
        if("world".equals(hasType)){
            pullInNeighbours();
            evaluateWorld();
        }
        else
        if("structure".equals(hasType)) evaluateStructure();
        if(!running){ running=true;
            MinecraftCyrus.self.registerTicks(this);
            if("world".equals(hasType)) MinecraftCyrus.self.registerWorld(content("name"),this);
        }
    }

    void pullInNeighbours(){
        LinkedList subs=contentAsList("sub-items");
        if(subs==null) return;
        for(int i=0; i< subs.size(); i++){
            String p=String.format("sub-items:%d:item:is",i);
            contentAsList(p);
        }
        for(int i=0; i< subs.size(); i++){
            String s=String.format("sub-items:%d:item:sub-items",i);
            LinkedList subsubs=contentAsList(s);
            if(subsubs==null) continue;
            for(int j=0; j< subsubs.size(); j++){
                String q=String.format("sub-items:%d:item:sub-items:%d:item:is",i,j);
                contentAsList(q);
            }
        }
    }

    Boolean isRaining=null;
    Boolean isThundering=null;
    Boolean isDaytime=null;
    Number  timeOfDay=null;

    private void evaluateWorld(){
        addScanAndPlace();

        Boolean ra=contentBoolean("raining");
        Boolean th=contentBoolean("thundering");
        Boolean da=contentBoolean("daytime");
        Number  ti=contentNumber( "time-of-day");

        super.evaluate();

        Boolean ra2=contentBoolean("raining");
        Boolean th2=contentBoolean("thundering");
        Boolean da2=contentBoolean("daytime");
        Number  ti2=contentNumber( "time-of-day");

        isRaining   =(ra2!=null && !ra2.equals(ra))? ra2: null;
        isThundering=(th2!=null && !th2.equals(th))? th2: null;
        isDaytime   =(da2!=null && !da2.equals(da))? da2: null;
        timeOfDay   =(ti2!=null && !ti2.equals(ti))? ti2: null;
    }

    private void evaluateStructure(){
        super.evaluate();
    }

    private void addScanAndPlace(){
        if(blockNames.get("air")==null) setUpBlockNames();
        for(String alerted: alerted()){
            contentTempObserve("Alerted", alerted);
            if(contentIsOrListContains("Alerted:is", "minecraft")){
                if(contentIsOrListContains("is","queryable")){
                    addForScanning(alerted, contentHash("Alerted:scanning"), contentIsOrListContains("Alerted:is", "player"));
                }
                if(contentIsOrListContains("is","updatable")){
                    addForPushing(alerted, contentHash("Alerted:pushing"));
                    addForPlacing(alerted, contentHash("Alerted:placing"));
                    if(structureNotOurCube()) addForPlacing(alerted, contentHash("Alerted:#")); // add to sub-items
                }
            }
            contentTemp("Alerted", null);
        }
    }

    private boolean structureNotOurCube(){
        return   contentIsOrListContains("Alerted:is", "structure") &&
               !(contentIsOrListContains("Alerted:is", "cube") && contentIsThis("Alerted:world"));
    }

    LinkedList scanners=new LinkedList();

    private void addForScanning(String scanneruid, LinkedHashMap scanning, boolean isplayer){
        if(scanning==null || scanners.contains(scanneruid)) return;
        scanners.add(scanneruid);
        if(isplayer && contentAllContains("player-views:scanner",scanneruid)) return;
        String viewuid=spawn(new MinecraftWorld(uid,scanneruid,isplayer));
        if(isplayer) contentListAdd("player-views", viewuid);
    }

    ConcurrentLinkedQueue<LinkedList> pushingQ =new ConcurrentLinkedQueue<LinkedList>();
    ConcurrentLinkedQueue<LinkedList> placingQ =new ConcurrentLinkedQueue<LinkedList>();

    private void addForPushing(String pusheruid, LinkedHashMap pushing){ if(pushing !=null) pushingQ.add(list(pusheruid,pushing)); }
    private void addForPlacing(String placeruid, LinkedHashMap placing){ if(placing !=null) placingQ.add(list(placeruid,placing)); }

    LinkedHashMap<String,LinkedHashMap> places = new LinkedHashMap<String,LinkedHashMap>();

    // ------------------------------------

    World world=null;

    int tickNum=0;

    public void tick(){
        if(++tickNum==20) tickNum=0;
        final World  currentworld=world(); if(currentworld==null) return;
        final String currentname=currentworld.worldInfo.getWorldName();
        if("world".equals(hasType)){
            new Evaluator(this){ public void evaluate(){
                if(!contentIs("name",currentname)) return;
                setWorld(currentworld);
                if(tickNum==0){
                    LinkedList players=getPlayers(); // ??
                    if(players.size() >0) contentList("players", players); else content("players",null);
                    setAndGetWorldState();
                    doEntitiesToCyrus();
                    self.evaluate();
                }
            }};
            if(world!=currentworld) return;
            while(true){
                LinkedList uidpushing=pushingQ.poll();
                if(uidpushing==null) break;
                String        pusheruid=(String)       uidpushing.get(0);
                LinkedHashMap pushing  =(LinkedHashMap)uidpushing.get(1);
                doPushing(pusheruid, pushing);
            }
            while(true){
                LinkedList uidplacing=placingQ.poll();
                if(uidplacing==null) break;
                String        placeruid=(String)       uidplacing.get(0);
                LinkedHashMap placing  =(LinkedHashMap)uidplacing.get(1);
                placing=(LinkedHashMap)placing.clone();
                boolean trail=getBooleanFrom(placing,"trail");
                if(!trail){
                    LinkedHashMap oldplacing=places.get(placeruid);
                    if(oldplacing!=null){
                        doPlacing(oldplacing, true);
                    }
                }
                places.put(placeruid,placing);
                doPlacing(placing, false);
            }
        }
        else
        if("structure".equals(hasType)){
            if(tickNum==0){
                new Evaluator(this){ public void evaluate(){
                    if(!contentIs("world:name",currentname)) return;
                    world=currentworld;
                    doScanning(contentHash("scanner:scanning"));
                }};
            }
        }
    }

    static public void onBlockUpdate(World world, int x, int y, int z, int previousId){
        MinecraftWorld mcw=getWorldFor(world);
        if(mcw!=null) mcw.onBlockUpdate(x,y,z, previousId);
    }

    boolean frozenPosAndSize=false;
    int posnx;
    int posnz;
    int sizex;
    int sizez;

    public void onBlockUpdate(final int x, final int y, final int z, int previousId){
        new Evaluator(this){ public void evaluate(){
            if(!frozenPosAndSize){ frozenPosAndSize=true;
                posnx=contentInt("position:0");
                posnz=contentInt("position:2");
                sizex=contentInt("size:0");
                sizez=contentInt("size:1");
            }
            if(x<posnx || x>=posnx+sizex || z<posnz || z>=posnz+sizez) outsideRegion(x,y,z);
            else                                                       insideRegion(x,y,z);
        }};
    }

    private void outsideRegion(int x, int y, int z){}

    private void insideRegion(int x, int y, int z){
        final int cx=(x>>2)*4;
        final int cy=(y>>2)*4;
        final int cz=(z>>2)*4;
        LinkedList cubepos=list(cx,cy,cz);
        final WebObject cube=getCube(cubepos);
        new Evaluator(cube){ public void evaluate(){
            cube.contentList("materials", getBlockListAround(cx,cy,cz, 4,4,4, true));
        }};
    }

    private WebObject getCube(LinkedList cubepos){
        String cubeuid=null;
        LinkedList subitems=contentList("sub-items");
        int i=0;
        if(subitems!=null) for(Object o: subitems){ LinkedHashMap hm=(LinkedHashMap)o;
            if(cubepos.equals(hm.get("position")) && contentIsOrListContains("sub-items:"+i+":item:is","cube")){
                cubeuid=(String)hm.get("item");
            }
        i++;}
        return (cubeuid==null)? newCube(cubepos): onlyUseThisToHandControlOfThreadToDependent(cubeuid);
    }

    private MinecraftWorld newCube(LinkedList cubepos){
        MinecraftWorld cube=new MinecraftWorld(uid,cubepos);
        String cubeuid=spawn(cube);
        LinkedHashMap hm=new LinkedHashMap();
        hm.put("item", cubeuid);
        hm.put("position", cubepos);
        contentListAdd("sub-items", hm);
        return cube;
    }

    private void setAndGetWorldState(){
        if(isDaytime !=null){
            if( isDaytime && !isDay()) world.setWorldTime(getDaysIn()*24000+500);
            if(!isDaytime &&  isDay()) world.setWorldTime(getDaysIn()*24000+12500);
        }
        if(timeOfDay   !=null) world.setWorldTime(timeOfDay.longValue());
        if(isRaining   !=null) world.getWorldInfo().setRaining(   isRaining);
        if(isThundering!=null) world.getWorldInfo().setThundering(isThundering);
        long ts=100L*(world.getTotalWorldTime()/100L);
        long td=100L*(world.getWorldTime()/100L);
        contentInt(   "time-stamp", (int)ts);
        contentInt(   "time-of-day",(int)td);
        contentBool(  "daytime",       isDay());
        contentBool(  "raining",       world.isRaining());
        contentDouble("rain-strength", world.getRainStrength(1));
        contentBool(  "thundering",    world.isThundering());
        content(      "seed",       ""+world.getSeed());
    }

    /* 0=dawn 6000=midday 12000=dusk 18000=midnight */
    private int getTimeInDay(){ return (int)(world.getWorldTime() % 24000); }
    private int getDaysIn(){    return (int)(world.getWorldTime() / 24000); }
    private boolean isDay(){    return getTimeInDay() < 12000; }

    private void doEntitiesToCyrus(){ // and link them into sub-items if in region
        int six=40; int siy=20; int siz=40;
        for(Object p: world.playerEntities){ EntityPlayer player=(EntityPlayer)p;
            int atx=(int)(player.posX-six/2); int aty=(int)(player.posY-siy/2); int atz=(int)(player.posZ-siz/2);
            for(Object o: world.loadedEntityList){ Entity e=(Entity)o;
                if(e.posX >atx && e.posX<atx+six &&
                   e.posY >aty && e.posY<aty+siy &&
                   e.posZ >atz && e.posZ<atz+siz   ){
                    if(e instanceof EntityPlayer) continue; // ??
                    entityToCyrus(e,uid);
                }
            }
        }
    }

    private LinkedList getPlayers(){
        LinkedList r=new LinkedList();
        for(Object o: world.playerEntities){ EntityPlayer player=(EntityPlayer)o;
            r.add(entityToCyrus(player,uid));
        }
        return r;
    }

    private void doScanning(LinkedHashMap scanning){
        if(scanning==null) return;
        String     scanfor =getStringFromHash(scanning, "for", "blocks");
        LinkedList position=getListFromHash(  scanning, "position");
        LinkedList size    =getListFromHash(  scanning, "size");
        if(position.size()!=3) return;
        Integer psx=getIntFromList(position,0);
        Integer psy=getIntFromList(position,1);
        Integer psz=getIntFromList(position,2);
        if(psx==null || psy==null || psz==null) return;
        if(size.size()!=3) return;
        Integer six=getIntFromList(size,0);
        Integer siy=getIntFromList(size,1);
        Integer siz=getIntFromList(size,2);
        if(six==null || siy==null || siz==null) return;
        if(six<=0)  six=1;   if(siy<=0)  siy=1;   if(siz<=0)  siz=1;
        if(six>100) six=100; if(siy>100) siy=100; if(siz>100) siz=100;
        contentList("position", position);
        if("blocks"  .equals(scanfor)) setBlockListAround(psx, psy, psz, six, siy, siz);
        if("entities".equals(scanfor)) getSubItemsAround( psx, psy, psz, six, siy, siz);
    }

    private void setBlockListAround(int atx, int aty, int atz, int six, int siy, int siz){
        LinkedList il=getBlockListAround(atx, aty, atz, six, siy, siz, false);
        if(!il.equals(contentList("materials"))){
            contentList("sub-items", null);
            contentList("materials",il);
            notifying(content("scanner"));
        }
    }

    private LinkedList getBlockListAround(int atx, int aty, int atz, int six, int siy, int siz, boolean ids){
        final LinkedList il=new LinkedList();
        for(int i=0; i<six; i++){
            LinkedList jl=new LinkedList();
            for(int j=0; j<siy; j++){
                LinkedList kl=new LinkedList();
                for(int k=0; k<siz; k++){
                    kl.add(getBlockAt(atx+i,aty+j,atz+k, ids));
                }
                jl.add(kl);
            }
            il.add(jl);
        }
        return il;
    }

    private void getSubItemsAround(int atx, int aty, int atz, int six, int siy, int siz){
        LinkedList ll=new LinkedList();
        List entities=world.loadedEntityList;
        for(int i=0; i< entities.size(); i++){
            Entity e=(Entity)entities.get(i);
            if(e.posX >atx && e.posX<atx+six &&
               e.posY >aty && e.posY<aty+siy &&
               e.posZ >atz && e.posZ<atz+siz   ){
                if(e instanceof EntityPlayer) continue; // ??
                String euid=entityToCyrus(e,content("world"));
                LinkedList position=list(e.posX, e.posY, e.posZ);
                LinkedHashMap hm=new LinkedHashMap();
                hm.put("item", euid);
                hm.put("position", position);
                ll.add(hm);
            }
        }
        if(!ll.equals(contentList("sub-items"))){
            contentList("materials",null);
            contentList("sub-items", ll);
            notifying(content("scanner"));
        }
    }

    static LinkedHashMap<String,String>          entityUID=new LinkedHashMap<String,String>();
    static LinkedHashMap<String,MinecraftEntity> entityMap=new LinkedHashMap<String,MinecraftEntity>();
    static LinkedHashMap<World,MinecraftWorld>   worldMap =new LinkedHashMap<World,MinecraftWorld>();

    private void setWorld(World world){
        this.world=world;
        worldMap.put(world,this);
    }

    static public MinecraftWorld getWorldFor(World w){
        return worldMap.get(w);
    }

    static public MinecraftEntity getEntityFor(Entity e){
        String euid=entityToUID(e);
        return (euid!=null)? entityMap.get(euid): null;
    }

    static public String entityToUID(Entity e){
        String name=entityName(e);
        return entityUID.get(name);
    }

    private String entityToCyrus(Entity e, String worlduid){
        String name=entityName(e);
        String euid=entityUID.get(name);
        if(euid==null){
            String type=(e instanceof EntityPlayer)? "player": e.getEntityName().toLowerCase();
            MinecraftEntity me=new MinecraftEntity(e,type,name,worlduid);
            euid=spawn(me);
            entityUID.put(name,euid);
            entityMap.put(euid,me);
        }
        return euid;
    }

    static public String entityName(Entity e){
        return e.getEntityName()+"-"+e.entityId;
    }

    private void doPushing(String pusheruid, LinkedHashMap pushing){
        Entity pusher=entityMap.get(pusheruid).entity;
        LinkedList entities=getListFromHash(pushing,"entities");
        LinkedList speed   =getListFromHash(pushing,"speed");
        if(entities.size()==0 || speed.size()!=3) return;
        Float spx=getFloatFromList(speed,0,0);
        Float spy=getFloatFromList(speed,1,0);
        Float spz=getFloatFromList(speed,2,0);
        if(spx==null || spy==null || spz==null) return;
        if(spx< -1) spx= -1f; if(spy< -1) spy= -1f; if(spz< -1) spz= -1f;
        if(spx>  1) spx=  1f; if(spy>  1) spy=  1f; if(spz>  1) spz=  1f;
        for(Object o: entities){ if(!(o instanceof String)) return;
            Entity e=entityMap.get((String)o).entity;
            if(e==null) continue;
            e.motionX+=spx; if(e.motionX>1) e.motionX=1f; if(e.motionX< -1) e.motionX= -1f;
            e.motionY+=spy; if(e.motionY>1) e.motionY=1f; if(e.motionY< -1) e.motionY= -1f;
            e.motionZ+=spz; if(e.motionZ>1) e.motionZ=1f; if(e.motionZ< -1) e.motionZ= -1f;
        }
    }

    private void doPlacing(LinkedHashMap placing, boolean air){
        LinkedList position=getListFromHash(  placing,    "position"); if(position.size()!=3) return;
        Object     materials=                 placing.get("materials"); if(materials==null) materials="air";
        String     shape=   getStringFromHash(placing,    "shape", null);
        LinkedList size    =getListFromHash(  placing,    "size");
        Integer psx=getIntFromList(position,0);
        Integer psy=getIntFromList(position,1);
        Integer psz=getIntFromList(position,2);
        if(psx==null || psy==null || psz==null) return;
        if("box".equals(shape) && materials instanceof String){
            if(size.size()!=3) return;
            Integer six=getIntFromList(size,0);
            Integer siy=getIntFromList(size,1);
            Integer siz=getIntFromList(size,2);
            if(six==null || siy==null || siz==null) return;
            if(six<=0)  six=1;   if(siy<=0)  siy=1;   if(siz<=0)  siz=1;
            if(six>100) six=100; if(siy>100) siy=100; if(siz>100) siz=100;
            for(int i=0; i<six; i++)
            for(int j=0; j<siy; j++)
            for(int k=0; k<siz; k++){
                boolean a = air || (i!=0 && i!=six-1 && j!=0 && j!=siy-1 && k!=0 && k!=siz-1);
                ensureBlockAt(psx+i,psy+j,psz+k, a? "air": (String)materials);
            }
        }
        else{
            if(materials instanceof String){
                ensureBlockAt(psx,psy,psz, air? "air": (String)materials);
            }
            else
            if(materials instanceof LinkedList){
                int i=0,j=0,k=0;
                LinkedList l3=(LinkedList)materials;
                for(Object o2: l3){
                    if(o2 instanceof LinkedList){
                        LinkedList l2=(LinkedList)o2;
                        for(Object o1: l2){
                            if(o1 instanceof LinkedList){
                                LinkedList l1=(LinkedList)o1;
                                for(Object o0: l1){
                                    ensureBlockAt(psx+i,psy+j,psz+k, air? "air": o0);
                                k++; }
                            }
                        j++; k=0; }
                    }
                i++; j=0; }
            }
        }
    }

    // ------------------------------------

    public World world(){
        MinecraftServer server=MinecraftServer.getServer();
        return server==null? null: server.worldServerForDimension(0);
    }

    private void ensureBlockAt(int x, int y, int z, Object name){
        Integer id=null;
        if(name instanceof Number) id=((Number)name).intValue();
        if(name instanceof String) try{ id=Integer.parseInt((String)name); } catch(NumberFormatException e){ id=blockNames.get((String)name); }
        if(id!=null && id!=world.getBlockId(x,y,z)) world.setBlock(x,y,z, id);
    }

    private Object getBlockAt(int x, int y, int z, boolean ids){
        int id=world.getBlockId(x,y,z);
        if(id<0 || id>=200) return null;
        return ids? Integer.valueOf(id): blockIds.get(id);
    }

    // ------------------------------------

    static public LinkedHashMap<String,Integer> blockNames = new LinkedHashMap<String,Integer>();
    static public ArrayList<String>             blockIds   = new ArrayList<String>(200);

    static private void setUpBlockNames(){
        blockNames.put("air", 0); blockIds.add(0, "air");
        blockNames.put("stone", 1); blockIds.add(1, "stone");
        blockNames.put("grass", 2); blockIds.add(2, "grass");
        blockNames.put("dirt", 3); blockIds.add(3, "dirt");
        blockNames.put("cobblestone", 4); blockIds.add(4, "cobblestone");
        blockNames.put("wood-planks", 5); blockIds.add(5, "wood-planks");
        blockNames.put("sapling", 6); blockIds.add(6, "sapling");
        blockNames.put("bedrock", 7); blockIds.add(7, "bedrock");
        blockNames.put("water-moving", 8); blockIds.add(8, "water-moving");
        blockNames.put("water-still", 9); blockIds.add(9, "water-still");
        blockNames.put("lava-moving", 10); blockIds.add(10, "lava-moving");
        blockNames.put("lava-still", 11); blockIds.add(11, "lava-still");
        blockNames.put("sand", 12); blockIds.add(12, "sand");
        blockNames.put("gravel", 13); blockIds.add(13, "gravel");
        blockNames.put("gold-ore", 14); blockIds.add(14, "gold-ore");
        blockNames.put("iron-ore", 15); blockIds.add(15, "iron-ore");
        blockNames.put("coal-ore", 16); blockIds.add(16, "coal-ore");
        blockNames.put("wood", 17); blockIds.add(17, "wood");
        blockNames.put("leaves", 18); blockIds.add(18, "leaves");
        blockNames.put("sponge", 19); blockIds.add(19, "sponge");
        blockNames.put("glass", 20); blockIds.add(20, "glass");
        blockNames.put("lapis-lazuli-ore", 21); blockIds.add(21, "lapis-lazuli-ore");
        blockNames.put("lapis-lazuli-block", 22); blockIds.add(22, "lapis-lazuli-block");
        blockNames.put("dispenser", 23); blockIds.add(23, "dispenser");
        blockNames.put("sandstone", 24); blockIds.add(24, "sandstone");
        blockNames.put("note-block", 25); blockIds.add(25, "note-block");
        blockNames.put("bed", 26); blockIds.add(26, "bed");
        blockNames.put("powered-rail", 27); blockIds.add(27, "powered-rail");
        blockNames.put("detector-rail", 28); blockIds.add(28, "detector-rail");
        blockNames.put("sticky-piston", 29); blockIds.add(29, "sticky-piston");
        blockNames.put("cobweb", 30); blockIds.add(30, "cobweb");
        blockNames.put("tall-grass", 31); blockIds.add(31, "tall-grass");
        blockNames.put("dead-bush", 32); blockIds.add(32, "dead-bush");
        blockNames.put("piston", 33); blockIds.add(33, "piston");
        blockNames.put("piston-extension", 34); blockIds.add(34, "piston-extension");
        blockNames.put("wool", 35); blockIds.add(35, "wool");
        blockNames.put("piston-moving", 36); blockIds.add(36, "piston-moving");
        blockNames.put("flower", 37); blockIds.add(37, "flower");
        blockNames.put("rose", 38); blockIds.add(38, "rose");
        blockNames.put("mushroom-brown", 39); blockIds.add(39, "mushroom-brown");
        blockNames.put("mushroom-red", 40); blockIds.add(40, "mushroom-red");
        blockNames.put("gold-block", 41); blockIds.add(41, "gold-block");
        blockNames.put("iron-block", 42); blockIds.add(42, "iron-block");
        blockNames.put("stone-double-slab", 43); blockIds.add(43, "stone-double-slab");
        blockNames.put("stone-single-slab", 44); blockIds.add(44, "stone-single-slab");
        blockNames.put("bricks", 45); blockIds.add(45, "bricks");
        blockNames.put("tnt", 46); blockIds.add(46, "tnt");
        blockNames.put("bookshelf", 47); blockIds.add(47, "bookshelf");
        blockNames.put("mossy-cobblestone", 48); blockIds.add(48, "mossy-cobblestone");
        blockNames.put("obsidian", 49); blockIds.add(49, "obsidian");
        blockNames.put("torch", 50); blockIds.add(50, "torch");
        blockNames.put("fire", 51); blockIds.add(51, "fire");
        blockNames.put("monster-spawner", 52); blockIds.add(52, "monster-spawner");
        blockNames.put("oak-wood-stairs", 53); blockIds.add(53, "oak-wood-stairs");
        blockNames.put("chest", 54); blockIds.add(54, "chest");
        blockNames.put("redstone-dust", 55); blockIds.add(55, "redstone-dust");
        blockNames.put("diamond-ore", 56); blockIds.add(56, "diamond-ore");
        blockNames.put("diamond-block", 57); blockIds.add(57, "diamond-block");
        blockNames.put("crafting-table", 58); blockIds.add(58, "crafting-table");
        blockNames.put("crops", 59); blockIds.add(59, "crops");
        blockNames.put("farmland", 60); blockIds.add(60, "farmland");
        blockNames.put("furnace-idle", 61); blockIds.add(61, "furnace-idle");
        blockNames.put("furnace-burning", 62); blockIds.add(62, "furnace-burning");
        blockNames.put("sign-post", 63); blockIds.add(63, "sign-post");
        blockNames.put("wooden-door", 64); blockIds.add(64, "wooden-door");
        blockNames.put("ladder", 65); blockIds.add(65, "ladder");
        blockNames.put("rail", 66); blockIds.add(66, "rail");
        blockNames.put("stone-stairs", 67); blockIds.add(67, "stone-stairs");
        blockNames.put("sign-wall", 68); blockIds.add(68, "sign-wall");
        blockNames.put("lever", 69); blockIds.add(69, "lever");
        blockNames.put("pressure-plate-stone", 70); blockIds.add(70, "pressure-plate-stone");
        blockNames.put("iron-door", 71); blockIds.add(71, "iron-door");
        blockNames.put("pressure-plate-planks", 72); blockIds.add(72, "pressure-plate-planks");
        blockNames.put("redstone-ore", 73); blockIds.add(73, "redstone-ore");
        blockNames.put("redstone-ore-glowing", 74); blockIds.add(74, "redstone-ore-glowing");
        blockNames.put("redstone-torch-idle", 75); blockIds.add(75, "redstone-torch-idle");
        blockNames.put("redstone-torch-active", 76); blockIds.add(76, "redstone-torch-active");
        blockNames.put("stone-button", 77); blockIds.add(77, "stone-button");
        blockNames.put("snow", 78); blockIds.add(78, "snow");
        blockNames.put("ice", 79); blockIds.add(79, "ice");
        blockNames.put("block-snow", 80); blockIds.add(80, "block-snow");
        blockNames.put("cactus", 81); blockIds.add(81, "cactus");
        blockNames.put("clay", 82); blockIds.add(82, "clay");
        blockNames.put("sugar-cane", 83); blockIds.add(83, "sugar-cane");
        blockNames.put("jukebox", 84); blockIds.add(84, "jukebox");
        blockNames.put("fence", 85); blockIds.add(85, "fence");
        blockNames.put("pumpkin", 86); blockIds.add(86, "pumpkin");
        blockNames.put("netherrack", 87); blockIds.add(87, "netherrack");
        blockNames.put("soul-sand", 88); blockIds.add(88, "soul-sand");
        blockNames.put("glowstone", 89); blockIds.add(89, "glowstone");
        blockNames.put("portal", 90); blockIds.add(90, "portal");
        blockNames.put("jack-o-lantern", 91); blockIds.add(91, "jack-o-lantern");
        blockNames.put("cake", 92); blockIds.add(92, "cake");
        blockNames.put("redstone-repeater-idle", 93); blockIds.add(93, "redstone-repeater-idle");
        blockNames.put("redstone-repeater-active", 94); blockIds.add(94, "redstone-repeater-active");
        blockNames.put("locked-chest", 95); blockIds.add(95, "locked-chest");
        blockNames.put("trapdoor", 96); blockIds.add(96, "trapdoor");
        blockNames.put("stone-monster-egg", 97); blockIds.add(97, "stone-monster-egg");
        blockNames.put("stone-brick", 98); blockIds.add(98, "stone-brick");
        blockNames.put("mushroom-cap-brown", 99); blockIds.add(99, "mushroom-cap-brown");
        blockNames.put("mushroom-cap-red", 100); blockIds.add(100, "mushroom-cap-red");
        blockNames.put("iron-bars", 101); blockIds.add(101, "iron-bars");
        blockNames.put("glass-pane", 102); blockIds.add(102, "glass-pane");
        blockNames.put("melon", 103); blockIds.add(103, "melon");
        blockNames.put("pumpkin-stem", 104); blockIds.add(104, "pumpkin-stem");
        blockNames.put("melon-stem", 105); blockIds.add(105, "melon-stem");
        blockNames.put("vines", 106); blockIds.add(106, "vines");
        blockNames.put("fence-gate", 107); blockIds.add(107, "fence-gate");
        blockNames.put("brick-stairs", 108); blockIds.add(108, "brick-stairs");
        blockNames.put("stone-brick-stairs", 109); blockIds.add(109, "stone-brick-stairs");
        blockNames.put("mycelium", 110); blockIds.add(110, "mycelium");
        blockNames.put("lily-pad", 111); blockIds.add(111, "lily-pad");
        blockNames.put("nether-brick", 112); blockIds.add(112, "nether-brick");
        blockNames.put("nether-brick-fence", 113); blockIds.add(113, "nether-brick-fence");
        blockNames.put("nether-brick-stairs", 114); blockIds.add(114, "nether-brick-stairs");
        blockNames.put("nether-wart", 115); blockIds.add(115, "nether-wart");
        blockNames.put("enchantment-table", 116); blockIds.add(116, "enchantment-table");
        blockNames.put("brewing-stand", 117); blockIds.add(117, "brewing-stand");
        blockNames.put("cauldron", 118); blockIds.add(118, "cauldron");
        blockNames.put("end-portal", 119); blockIds.add(119, "end-portal");
        blockNames.put("end-portal-frame", 120); blockIds.add(120, "end-portal-frame");
        blockNames.put("end-stone", 121); blockIds.add(121, "end-stone");
        blockNames.put("dragon-egg", 122); blockIds.add(122, "dragon-egg");
        blockNames.put("redstone-lamp-idle", 123); blockIds.add(123, "redstone-lamp-idle");
        blockNames.put("redstone-lamp-active", 124); blockIds.add(124, "redstone-lamp-active");
        blockNames.put("wood-double-slab", 125); blockIds.add(125, "wood-double-slab");
        blockNames.put("wood-single-slab", 126); blockIds.add(126, "wood-single-slab");
        blockNames.put("cocoa", 127); blockIds.add(127, "cocoa");
        blockNames.put("sandstone-stairs", 128); blockIds.add(128, "sandstone-stairs");
        blockNames.put("emerald-ore", 129); blockIds.add(129, "emerald-ore");
        blockNames.put("ender-chest", 130); blockIds.add(130, "ender-chest");
        blockNames.put("trip-wire-hook", 131); blockIds.add(131, "trip-wire-hook");
        blockNames.put("trip-wire", 132); blockIds.add(132, "trip-wire");
        blockNames.put("emerald-block", 133); blockIds.add(133, "emerald-block");
        blockNames.put("spruce-wood-stairs", 134); blockIds.add(134, "spruce-wood-stairs");
        blockNames.put("birch-wood-stairs", 135); blockIds.add(135, "birch-wood-stairs");
        blockNames.put("jungle-wood-stairs", 136); blockIds.add(136, "jungle-wood-stairs");
        blockNames.put("command-block", 137); blockIds.add(137, "command-block");
        blockNames.put("beacon", 138); blockIds.add(138, "beacon");
        blockNames.put("cobblestone-wall", 139); blockIds.add(139, "cobblestone-wall");
        blockNames.put("flower-pot", 140); blockIds.add(140, "flower-pot");
        blockNames.put("carrots", 141); blockIds.add(141, "carrots");
        blockNames.put("potatoes", 142); blockIds.add(142, "potatoes");
        blockNames.put("wooden-button", 143); blockIds.add(143, "wooden-button");
        blockNames.put("skull", 144); blockIds.add(144, "skull");
        blockNames.put("anvil", 145); blockIds.add(145, "anvil");
        blockNames.put("trapped-chest", 146); blockIds.add(146, "trapped-chest");
        blockNames.put("pressure-plate-light", 147); blockIds.add(147, "pressure-plate-light");
        blockNames.put("pressure-plate-heavy", 148); blockIds.add(148, "pressure-plate-heavy");
        blockNames.put("redstone-comparator-idle", 149); blockIds.add(149, "redstone-comparator-idle");
        blockNames.put("redstone-comparator-active", 150); blockIds.add(150, "redstone-comparator-active");
        blockNames.put("daylight-sensor", 151); blockIds.add(151, "daylight-sensor");
        blockNames.put("redstone-block", 152); blockIds.add(152, "redstone-block");
        blockNames.put("nether-quartz-ore", 153); blockIds.add(153, "nether-quartz-ore");
        blockNames.put("hopper", 154); blockIds.add(154, "hopper");
        blockNames.put("quartz-block", 155); blockIds.add(155, "quartz-block");
        blockNames.put("quartz-stairs", 156); blockIds.add(156, "quartz-stairs");
        blockNames.put("activator-rail", 157); blockIds.add(157, "activator-rail");
        blockNames.put("dropper", 158); blockIds.add(158, "dropper");
    }
}

