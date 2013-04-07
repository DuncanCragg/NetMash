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
                LinkedList placing=contentList("Alerted:placing");
                if(placing!=null){ addForPlacing(placing); }
            }
            contentTemp("Alerted", null);
        }
    }

    ConcurrentLinkedQueue<LinkedList> placingQ=new ConcurrentLinkedQueue<LinkedList>();

    private void addForPlacing(LinkedList placing){ placingQ.add(placing); }

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
        if(id!=null && id!=world().getBlockId(x,y,z)) world().setBlock(x,y,z, id);
    }

    LinkedHashMap<String,Integer> blockNames = new LinkedHashMap<String,Integer>();

    private void setUpBlockNames(){
        blockNames.put("air", 0);
        blockNames.put("stone", 1);
        blockNames.put("grass", 2);
        blockNames.put("dirt", 3);
        blockNames.put("cobblestone", 4);
        blockNames.put("planks", 5);
        blockNames.put("sapling", 6);
        blockNames.put("bedrock", 7);
        blockNames.put("water-moving", 8);
        blockNames.put("water-still", 9);
        blockNames.put("lava-moving", 10);
        blockNames.put("lava-still", 11);
        blockNames.put("sand", 12);
        blockNames.put("gravel", 13);
        blockNames.put("ore-gold", 14);
        blockNames.put("ore-iron", 15);
        blockNames.put("ore-coal", 16);
        blockNames.put("wood", 17);
        blockNames.put("leaves", 18);
        blockNames.put("sponge", 19);
        blockNames.put("glass", 20);
        blockNames.put("ore-lapis", 21);
        blockNames.put("block-lapis", 22);
        blockNames.put("dispenser", 23);
        blockNames.put("sand-stone", 24);
        blockNames.put("music", 25);
        blockNames.put("bed", 26);
        blockNames.put("rail-powered", 27);
        blockNames.put("rail-detector", 28);
        blockNames.put("piston-sticky-base", 29);
        blockNames.put("web", 30);
        blockNames.put("tall-grass", 31);
        blockNames.put("dead-bush", 32);
        blockNames.put("piston-base", 33);
        blockNames.put("piston-extension", 34);
        blockNames.put("cloth", 35);
        blockNames.put("piston-moving", 36);
        blockNames.put("plant-yellow", 37);
        blockNames.put("plant-red", 38);
        blockNames.put("mushroom-brown", 39);
        blockNames.put("mushroom-red", 40);
        blockNames.put("block-gold", 41);
        blockNames.put("block-steel", 42);
        blockNames.put("stone-double-slab", 43);
        blockNames.put("stone-single-slab", 44);
        blockNames.put("brick", 45);
        blockNames.put("tnt", 46);
        blockNames.put("book-shelf", 47);
        blockNames.put("cobblestone-mossy", 48);
        blockNames.put("obsidian", 49);
        blockNames.put("torch-wood", 50);
        blockNames.put("fire", 51);
        blockNames.put("mob-spawner", 52);
        blockNames.put("stairs-wood-oak", 53);
        blockNames.put("chest", 54);
        blockNames.put("redstone-wire", 55);
        blockNames.put("ore-diamond", 56);
        blockNames.put("block-diamond", 57);
        blockNames.put("workbench", 58);
        blockNames.put("crops", 59);
        blockNames.put("tilled-field", 60);
        blockNames.put("furnace-idle", 61);
        blockNames.put("furnace-burning", 62);
        blockNames.put("sign-post", 63);
        blockNames.put("door-wood", 64);
        blockNames.put("ladder", 65);
        blockNames.put("rail", 66);
        blockNames.put("stairs-cobblestone", 67);
        blockNames.put("sign-wall", 68);
        blockNames.put("lever", 69);
        blockNames.put("pressure-plate-stone", 70);
        blockNames.put("door-steel", 71);
        blockNames.put("pressure-plate-planks", 72);
        blockNames.put("ore-redstone", 73);
        blockNames.put("ore-redstone-glowing", 74);
        blockNames.put("torch-redstone-idle", 75);
        blockNames.put("torch-redstone-active", 76);
        blockNames.put("stone-button", 77);
        blockNames.put("snow", 78);
        blockNames.put("ice", 79);
        blockNames.put("block-snow", 80);
        blockNames.put("cactus", 81);
        blockNames.put("block-clay", 82);
        blockNames.put("reed", 83);
        blockNames.put("jukebox", 84);
        blockNames.put("fence", 85);
        blockNames.put("pumpkin", 86);
        blockNames.put("netherrack", 87);
        blockNames.put("slow-sand", 88);
        blockNames.put("glow-stone", 89);
        blockNames.put("portal", 90);
        blockNames.put("pumpkin-lantern", 91);
        blockNames.put("cake", 92);
        blockNames.put("redstone-repeater-idle", 93);
        blockNames.put("redstone-repeater-active", 94);
        blockNames.put("locked-chest", 95);
        blockNames.put("trapdoor", 96);
        blockNames.put("silverfish", 97);
        blockNames.put("stone-brick", 98);
        blockNames.put("mushroom-cap-brown", 99);
        blockNames.put("mushroom-cap-red", 100);
        blockNames.put("fence-iron", 101);
        blockNames.put("thin-glass", 102);
        blockNames.put("melon", 103);
        blockNames.put("pumpkin-stem", 104);
        blockNames.put("melon-stem", 105);
        blockNames.put("vine", 106);
        blockNames.put("fence-gate", 107);
        blockNames.put("stairs-brick", 108);
        blockNames.put("stairs-stone-brick", 109);
        blockNames.put("mycelium", 110);
        blockNames.put("waterlily", 111);
        blockNames.put("nether-brick", 112);
        blockNames.put("nether-fence", 113);
        blockNames.put("stairs-nether-brick", 114);
        blockNames.put("nether-stalk", 115);
        blockNames.put("enchantment-table", 116);
        blockNames.put("brewing-stand", 117);
        blockNames.put("cauldron", 118);
        blockNames.put("end-portal", 119);
        blockNames.put("end-portal-frame", 120);
        blockNames.put("white-stone", 121);
        blockNames.put("dragon-egg", 122);
        blockNames.put("redstone-lamp-idle", 123);
        blockNames.put("redstone-lamp-active", 124);
        blockNames.put("wood-double-slab", 125);
        blockNames.put("wood-single-slab", 126);
        blockNames.put("cocoa-plant", 127);
        blockNames.put("stairs-sand-stone", 128);
        blockNames.put("ore-emerald", 129);
        blockNames.put("ender-chest", 130);
        blockNames.put("trip-wire-source", 131);
        blockNames.put("trip-wire", 132);
        blockNames.put("block-emerald", 133);
        blockNames.put("stairs-wood-spruce", 134);
        blockNames.put("stairs-wood-birch", 135);
        blockNames.put("stairs-wood-jungle", 136);
        blockNames.put("command-block", 137);
        blockNames.put("beacon", 138);
        blockNames.put("cobblestone-wall", 139);
        blockNames.put("flower-pot", 140);
        blockNames.put("carrot", 141);
        blockNames.put("potato", 142);
        blockNames.put("wooden-button", 143);
        blockNames.put("skull", 144);
        blockNames.put("anvil", 145);
        blockNames.put("chest-trapped", 146);
        blockNames.put("pressure-plate-gold", 147);
        blockNames.put("pressure-plate-iron", 148);
        blockNames.put("redstone-comparator-idle", 149);
        blockNames.put("redstone-comparator-active", 150);
        blockNames.put("daylight-sensor", 151);
        blockNames.put("block-redstone", 152);
        blockNames.put("ore-nether-quartz", 153);
        blockNames.put("hopper-block", 154);
        blockNames.put("block-nether-quartz", 155);
        blockNames.put("stair-compact-nether-quartz", 156);
        blockNames.put("rail-activator", 157);
        blockNames.put("dropper", 158);
    }
}

