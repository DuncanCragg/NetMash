package net.minecraft.src;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public class MinecraftCyrus extends WebObject {

    static MinecraftCyrus that=null;

    public MinecraftCyrus(){
        super("{ \"is\": \"minecraft\" }");
        that=this;
    }

    public void evaluate(){
        String gruid=content("global-rules");
        if(gruid==null){
            gruid=spawn(new CyrusLanguage("{ \"is\": [ \"editable\", \"rule\", \"list\" ], \"title\": \"global rules\" }"));
            content("global-rules", gruid);
        }
    //  setGlobalRules(gruid);
        contentAll("worlds:name");
    }

    static public void newWorld(String worldname, World world){
        if(that==null) return;
        that.doNewWorld(worldname,world);
    }

    private void doNewWorld(final String worldname, final World world){
        new Evaluator(that){ public void evaluate(){
            if(contentAllContains("worlds:name",worldname)) return;
            contentListAdd("worlds", spawn(new MinecraftWorld(worldname,world)));
        }};
    }
}

