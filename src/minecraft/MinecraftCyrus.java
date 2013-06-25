package net.minecraft.src;

import java.awt.*;
import java.net.*;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;
import static cyrus.forest.UID.*;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public class MinecraftCyrus extends WebObject {

    static MinecraftCyrus that=null;

    public MinecraftCyrus(){
        super("{ \"is\": \"minecraft\" }");
        that=this;
    }

    boolean first=true;

    public void evaluate(){ try{
        if(first){ first=false;
            String gruid=content("global-rules");
            if(gruid==null){
                gruid=spawn(new CyrusLanguage("{ \"is\": [ \"editable\", \"rule\", \"list\" ], \"title\": \"global rules\" }"));
                content("global-rules", gruid);
            }
            CyrusLanguage.addGlobalRules(gruid);
            contentAll("worlds:name");

            Desktop.getDesktop().browse(URI.create(localPre()+"/#"+toURL(uid)));
        }
    }catch(Exception e){ e.printStackTrace(); }}

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

