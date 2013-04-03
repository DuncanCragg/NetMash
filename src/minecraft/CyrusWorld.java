package net.minecraft.src;

import java.util.LinkedList;
import cyrus.forest.WebObject;

import static cyrus.lib.Utils.*;

import net.minecraft.server.MinecraftServer;

public class CyrusWorld extends WebObject {

    public CyrusWorld(){ }

    public World world(){
        MinecraftServer server=MinecraftServer.getServer();
        return server==null? null: server.worldServerForDimension(0);
    }

    public void evaluate(){
        super.evaluate();
        for(String alerted: alerted()){
            contentTemp("Alerted", alerted);
            if(contentListContainsAll("Alerted:is",list("minecraft","spell"))){
                LinkedList placing=contentList("Alerted:placing");
                if(placing!=null){
                    if(placing.size()==5 && placing.get(1).equals("at")){
                        int x=(int)findNumberIn(placing.get(2));
                        int y=(int)findNumberIn(placing.get(3));
                        int z=(int)findNumberIn(placing.get(4));
                        String name=placing.get(0).toString();
                        ensureBlockAt(x,y,z,name);
                    }
                }
            }
            contentTemp("Alerted", null);
        }
    }

    private void ensureBlockAt(int x, int y, int z, String name){
        if(world()==null) return;
        int id=name.equals("cobblestone")? 4: 1;
        if(id!=world().getBlockId(x,y,z)) world().setBlock(x,y,z, id);
    }
}

