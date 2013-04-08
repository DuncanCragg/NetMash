package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public class CyrusWorld extends WebObject implements mod_Cyrus.Tickable {

    public CyrusWorld(){
        setUpBlockNames();
        mod_Cyrus.modCyrus.registerTicks(this);
    }

    public void evaluate(){
        super.evaluate();
        for(String alerted: alerted()){
            contentTemp("Alerted", alerted);
            if(contentListContainsAll("Alerted:is",list("minecraft","spell"))){
                addWatchArea( contentList("Alerted:scanning"));
                addForPlacing(contentList("Alerted:placing"));
            }
            contentTemp("Alerted", null);
        }
    }

    // scanning: box ( 50 10 50 ) at ( -500 80 200 )
    private void addWatchArea(LinkedList scanning){
        if(scanning==null) return;
        if(scanning.size()==4 && scanning.get(0).equals("box") && scanning.get(2).equals("at")){
            LinkedList shape=findListIn(scanning.get(1));
            LinkedList at   =findListIn(scanning.get(3));
            if(at!=null && at.size()==3 && shape!=null && shape.size()==3){
                int shx=getIntFromList(shape,0);
                int shy=getIntFromList(shape,1);
                int shz=getIntFromList(shape,2);
                if(shx>0 && shy>0 && shz>0){
                    int atx=getIntFromList(at,0);
                    int aty=getIntFromList(at,1);
                    int atz=getIntFromList(at,2);
                    if(shx>10) shx=10;
                    if(shy>10) shy=10;
                    if(shz>10) shz=10;
                    for(int i=0; i<shx; i++)
                    for(int j=0; j<shy; j++)
                    for(int k=0; k<shz; k++){
                        getBlockAt(atx+i,aty+j,atz+k);
                    }
                }
            }
        }
    }

    ConcurrentLinkedQueue<LinkedList> placingQ=new ConcurrentLinkedQueue<LinkedList>();

    private void addForPlacing(LinkedList placing){ if(placing!=null) placingQ.add(placing); }

    public void tick(float var1, Minecraft minecraft){
        while(true){ LinkedList placing=placingQ.poll(); if(placing==null) return; doPlacing(placing); }
    }

    private void doPlacing(LinkedList placing){
        if(placing.size()==3 && placing.get(1).equals("at")){
            // glass at ( -480 70 202 )
            LinkedList at=findListIn(placing.get(2));
            if(at!=null && at.size()==3){
                int atx=getIntFromList(at,0);
                int aty=getIntFromList(at,1);
                int atz=getIntFromList(at,2);
                String name=placing.get(0).toString();
                ensureBlockAt(atx,aty,atz,name);
            }
        }
        else
        if(placing.size()==5 && placing.get(1).equals("box") && placing.get(3).equals("at")){
            // glass box ( 4 8 16 ) at ( -480 70 202 )
            LinkedList shape=findListIn(placing.get(2));
            LinkedList at   =findListIn(placing.get(4));
            if(at!=null && at.size()==3 && shape!=null && shape.size()==3){
                int shx=getIntFromList(shape,0);
                int shy=getIntFromList(shape,1);
                int shz=getIntFromList(shape,2);
                if(shx>0 && shy>0 && shz>0){
                    int atx=getIntFromList(at,0);
                    int aty=getIntFromList(at,1);
                    int atz=getIntFromList(at,2);
                    String name=placing.get(0).toString();
                    if(shx>100) shx=100;
                    if(shy>100) shy=100;
                    if(shz>100) shz=100;
                    for(int i=0; i<shx; i++)
                    for(int j=0; j<shy; j++)
                    for(int k=0; k<shz; k++){
                        if(i==0 || i==shx-1 || j==0 || j==shy-1 || k==0 || k==shz-1){
                            ensureBlockAt(atx+i,aty+j,atz+k,name);
                        }
                        else ensureBlockAt(atx+i,aty+j,atz+k,"air");
                    }
                }
            }
        }
    }

    public World world(){
        MinecraftServer server=MinecraftServer.getServer();
        return server==null? null: server.worldServerForDimension(0);
    }

    private void ensureBlockAt(int x, int y, int z, String name){
        if(world()==null) return;
        Integer id=blockNames.get(name);
        if(id!=null && id!=world().getBlockId(x,y,z)){ logXX("setBlock",x,y,z,id); world().setBlock(x,y,z, id); }
    }

    private String getBlockAt(int x, int y, int z){
        if(world()==null) return null;
        int id=world().getBlockId(x,y,z);
        if(id<0 || id>=200) return null;
logXX("getBlock",x,y,z,blockIds.get(id));
        return blockIds.get(id);
    }

    LinkedHashMap<String,Integer> blockNames = new LinkedHashMap<String,Integer>();
    ArrayList<String>             blockIds   = new ArrayList<String>(200);

    private void setUpBlockNames(){
        blockNames.put("air", 0); blockIds.add(0, "air");
        blockNames.put("stone", 1); blockIds.add(1, "stone");
        blockNames.put("grass", 2); blockIds.add(2, "grass");
        blockNames.put("dirt", 3); blockIds.add(3, "dirt");
        blockNames.put("cobblestone", 4); blockIds.add(4, "cobblestone");
        blockNames.put("planks", 5); blockIds.add(5, "planks");
        blockNames.put("sapling", 6); blockIds.add(6, "sapling");
        blockNames.put("bedrock", 7); blockIds.add(7, "bedrock");
        blockNames.put("water-moving", 8); blockIds.add(8, "water-moving");
        blockNames.put("water-still", 9); blockIds.add(9, "water-still");
        blockNames.put("lava-moving", 10); blockIds.add(10, "lava-moving");
        blockNames.put("lava-still", 11); blockIds.add(11, "lava-still");
        blockNames.put("sand", 12); blockIds.add(12, "sand");
        blockNames.put("gravel", 13); blockIds.add(13, "gravel");
        blockNames.put("ore-gold", 14); blockIds.add(14, "ore-gold");
        blockNames.put("ore-iron", 15); blockIds.add(15, "ore-iron");
        blockNames.put("ore-coal", 16); blockIds.add(16, "ore-coal");
        blockNames.put("wood", 17); blockIds.add(17, "wood");
        blockNames.put("leaves", 18); blockIds.add(18, "leaves");
        blockNames.put("sponge", 19); blockIds.add(19, "sponge");
        blockNames.put("glass", 20); blockIds.add(20, "glass");
        blockNames.put("ore-lapis", 21); blockIds.add(21, "ore-lapis");
        blockNames.put("block-lapis", 22); blockIds.add(22, "block-lapis");
        blockNames.put("dispenser", 23); blockIds.add(23, "dispenser");
        blockNames.put("sand-stone", 24); blockIds.add(24, "sand-stone");
        blockNames.put("music", 25); blockIds.add(25, "music");
        blockNames.put("bed", 26); blockIds.add(26, "bed");
        blockNames.put("rail-powered", 27); blockIds.add(27, "rail-powered");
        blockNames.put("rail-detector", 28); blockIds.add(28, "rail-detector");
        blockNames.put("piston-sticky-base", 29); blockIds.add(29, "piston-sticky-base");
        blockNames.put("web", 30); blockIds.add(30, "web");
        blockNames.put("tall-grass", 31); blockIds.add(31, "tall-grass");
        blockNames.put("dead-bush", 32); blockIds.add(32, "dead-bush");
        blockNames.put("piston-base", 33); blockIds.add(33, "piston-base");
        blockNames.put("piston-extension", 34); blockIds.add(34, "piston-extension");
        blockNames.put("cloth", 35); blockIds.add(35, "cloth");
        blockNames.put("piston-moving", 36); blockIds.add(36, "piston-moving");
        blockNames.put("plant-yellow", 37); blockIds.add(37, "plant-yellow");
        blockNames.put("plant-red", 38); blockIds.add(38, "plant-red");
        blockNames.put("mushroom-brown", 39); blockIds.add(39, "mushroom-brown");
        blockNames.put("mushroom-red", 40); blockIds.add(40, "mushroom-red");
        blockNames.put("block-gold", 41); blockIds.add(41, "block-gold");
        blockNames.put("block-steel", 42); blockIds.add(42, "block-steel");
        blockNames.put("stone-double-slab", 43); blockIds.add(43, "stone-double-slab");
        blockNames.put("stone-single-slab", 44); blockIds.add(44, "stone-single-slab");
        blockNames.put("brick", 45); blockIds.add(45, "brick");
        blockNames.put("tnt", 46); blockIds.add(46, "tnt");
        blockNames.put("book-shelf", 47); blockIds.add(47, "book-shelf");
        blockNames.put("cobblestone-mossy", 48); blockIds.add(48, "cobblestone-mossy");
        blockNames.put("obsidian", 49); blockIds.add(49, "obsidian");
        blockNames.put("torch-wood", 50); blockIds.add(50, "torch-wood");
        blockNames.put("fire", 51); blockIds.add(51, "fire");
        blockNames.put("mob-spawner", 52); blockIds.add(52, "mob-spawner");
        blockNames.put("stairs-wood-oak", 53); blockIds.add(53, "stairs-wood-oak");
        blockNames.put("chest", 54); blockIds.add(54, "chest");
        blockNames.put("redstone-wire", 55); blockIds.add(55, "redstone-wire");
        blockNames.put("ore-diamond", 56); blockIds.add(56, "ore-diamond");
        blockNames.put("block-diamond", 57); blockIds.add(57, "block-diamond");
        blockNames.put("workbench", 58); blockIds.add(58, "workbench");
        blockNames.put("crops", 59); blockIds.add(59, "crops");
        blockNames.put("tilled-field", 60); blockIds.add(60, "tilled-field");
        blockNames.put("furnace-idle", 61); blockIds.add(61, "furnace-idle");
        blockNames.put("furnace-burning", 62); blockIds.add(62, "furnace-burning");
        blockNames.put("sign-post", 63); blockIds.add(63, "sign-post");
        blockNames.put("door-wood", 64); blockIds.add(64, "door-wood");
        blockNames.put("ladder", 65); blockIds.add(65, "ladder");
        blockNames.put("rail", 66); blockIds.add(66, "rail");
        blockNames.put("stairs-cobblestone", 67); blockIds.add(67, "stairs-cobblestone");
        blockNames.put("sign-wall", 68); blockIds.add(68, "sign-wall");
        blockNames.put("lever", 69); blockIds.add(69, "lever");
        blockNames.put("pressure-plate-stone", 70); blockIds.add(70, "pressure-plate-stone");
        blockNames.put("door-steel", 71); blockIds.add(71, "door-steel");
        blockNames.put("pressure-plate-planks", 72); blockIds.add(72, "pressure-plate-planks");
        blockNames.put("ore-redstone", 73); blockIds.add(73, "ore-redstone");
        blockNames.put("ore-redstone-glowing", 74); blockIds.add(74, "ore-redstone-glowing");
        blockNames.put("torch-redstone-idle", 75); blockIds.add(75, "torch-redstone-idle");
        blockNames.put("torch-redstone-active", 76); blockIds.add(76, "torch-redstone-active");
        blockNames.put("stone-button", 77); blockIds.add(77, "stone-button");
        blockNames.put("snow", 78); blockIds.add(78, "snow");
        blockNames.put("ice", 79); blockIds.add(79, "ice");
        blockNames.put("block-snow", 80); blockIds.add(80, "block-snow");
        blockNames.put("cactus", 81); blockIds.add(81, "cactus");
        blockNames.put("block-clay", 82); blockIds.add(82, "block-clay");
        blockNames.put("reed", 83); blockIds.add(83, "reed");
        blockNames.put("jukebox", 84); blockIds.add(84, "jukebox");
        blockNames.put("fence", 85); blockIds.add(85, "fence");
        blockNames.put("pumpkin", 86); blockIds.add(86, "pumpkin");
        blockNames.put("netherrack", 87); blockIds.add(87, "netherrack");
        blockNames.put("slow-sand", 88); blockIds.add(88, "slow-sand");
        blockNames.put("glow-stone", 89); blockIds.add(89, "glow-stone");
        blockNames.put("portal", 90); blockIds.add(90, "portal");
        blockNames.put("pumpkin-lantern", 91); blockIds.add(91, "pumpkin-lantern");
        blockNames.put("cake", 92); blockIds.add(92, "cake");
        blockNames.put("redstone-repeater-idle", 93); blockIds.add(93, "redstone-repeater-idle");
        blockNames.put("redstone-repeater-active", 94); blockIds.add(94, "redstone-repeater-active");
        blockNames.put("locked-chest", 95); blockIds.add(95, "locked-chest");
        blockNames.put("trapdoor", 96); blockIds.add(96, "trapdoor");
        blockNames.put("silverfish", 97); blockIds.add(97, "silverfish");
        blockNames.put("stone-brick", 98); blockIds.add(98, "stone-brick");
        blockNames.put("mushroom-cap-brown", 99); blockIds.add(99, "mushroom-cap-brown");
        blockNames.put("mushroom-cap-red", 100); blockIds.add(100, "mushroom-cap-red");
        blockNames.put("fence-iron", 101); blockIds.add(101, "fence-iron");
        blockNames.put("thin-glass", 102); blockIds.add(102, "thin-glass");
        blockNames.put("melon", 103); blockIds.add(103, "melon");
        blockNames.put("pumpkin-stem", 104); blockIds.add(104, "pumpkin-stem");
        blockNames.put("melon-stem", 105); blockIds.add(105, "melon-stem");
        blockNames.put("vine", 106); blockIds.add(106, "vine");
        blockNames.put("fence-gate", 107); blockIds.add(107, "fence-gate");
        blockNames.put("stairs-brick", 108); blockIds.add(108, "stairs-brick");
        blockNames.put("stairs-stone-brick", 109); blockIds.add(109, "stairs-stone-brick");
        blockNames.put("mycelium", 110); blockIds.add(110, "mycelium");
        blockNames.put("waterlily", 111); blockIds.add(111, "waterlily");
        blockNames.put("nether-brick", 112); blockIds.add(112, "nether-brick");
        blockNames.put("nether-fence", 113); blockIds.add(113, "nether-fence");
        blockNames.put("stairs-nether-brick", 114); blockIds.add(114, "stairs-nether-brick");
        blockNames.put("nether-stalk", 115); blockIds.add(115, "nether-stalk");
        blockNames.put("enchantment-table", 116); blockIds.add(116, "enchantment-table");
        blockNames.put("brewing-stand", 117); blockIds.add(117, "brewing-stand");
        blockNames.put("cauldron", 118); blockIds.add(118, "cauldron");
        blockNames.put("end-portal", 119); blockIds.add(119, "end-portal");
        blockNames.put("end-portal-frame", 120); blockIds.add(120, "end-portal-frame");
        blockNames.put("white-stone", 121); blockIds.add(121, "white-stone");
        blockNames.put("dragon-egg", 122); blockIds.add(122, "dragon-egg");
        blockNames.put("redstone-lamp-idle", 123); blockIds.add(123, "redstone-lamp-idle");
        blockNames.put("redstone-lamp-active", 124); blockIds.add(124, "redstone-lamp-active");
        blockNames.put("wood-double-slab", 125); blockIds.add(125, "wood-double-slab");
        blockNames.put("wood-single-slab", 126); blockIds.add(126, "wood-single-slab");
        blockNames.put("cocoa-plant", 127); blockIds.add(127, "cocoa-plant");
        blockNames.put("stairs-sand-stone", 128); blockIds.add(128, "stairs-sand-stone");
        blockNames.put("ore-emerald", 129); blockIds.add(129, "ore-emerald");
        blockNames.put("ender-chest", 130); blockIds.add(130, "ender-chest");
        blockNames.put("trip-wire-source", 131); blockIds.add(131, "trip-wire-source");
        blockNames.put("trip-wire", 132); blockIds.add(132, "trip-wire");
        blockNames.put("block-emerald", 133); blockIds.add(133, "block-emerald");
        blockNames.put("stairs-wood-spruce", 134); blockIds.add(134, "stairs-wood-spruce");
        blockNames.put("stairs-wood-birch", 135); blockIds.add(135, "stairs-wood-birch");
        blockNames.put("stairs-wood-jungle", 136); blockIds.add(136, "stairs-wood-jungle");
        blockNames.put("command-block", 137); blockIds.add(137, "command-block");
        blockNames.put("beacon", 138); blockIds.add(138, "beacon");
        blockNames.put("cobblestone-wall", 139); blockIds.add(139, "cobblestone-wall");
        blockNames.put("flower-pot", 140); blockIds.add(140, "flower-pot");
        blockNames.put("carrot", 141); blockIds.add(141, "carrot");
        blockNames.put("potato", 142); blockIds.add(142, "potato");
        blockNames.put("wooden-button", 143); blockIds.add(143, "wooden-button");
        blockNames.put("skull", 144); blockIds.add(144, "skull");
        blockNames.put("anvil", 145); blockIds.add(145, "anvil");
        blockNames.put("chest-trapped", 146); blockIds.add(146, "chest-trapped");
        blockNames.put("pressure-plate-gold", 147); blockIds.add(147, "pressure-plate-gold");
        blockNames.put("pressure-plate-iron", 148); blockIds.add(148, "pressure-plate-iron");
        blockNames.put("redstone-comparator-idle", 149); blockIds.add(149, "redstone-comparator-idle");
        blockNames.put("redstone-comparator-active", 150); blockIds.add(150, "redstone-comparator-active");
        blockNames.put("daylight-sensor", 151); blockIds.add(151, "daylight-sensor");
        blockNames.put("block-redstone", 152); blockIds.add(152, "block-redstone");
        blockNames.put("ore-nether-quartz", 153); blockIds.add(153, "ore-nether-quartz");
        blockNames.put("hopper-block", 154); blockIds.add(154, "hopper-block");
        blockNames.put("block-nether-quartz", 155); blockIds.add(155, "block-nether-quartz");
        blockNames.put("stair-compact-nether-quartz", 156); blockIds.add(156, "stair-compact-nether-quartz");
        blockNames.put("rail-activator", 157); blockIds.add(157, "rail-activator");
        blockNames.put("dropper", 158); blockIds.add(158, "dropper");
    }
}

