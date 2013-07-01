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
            String guiuid=content("gui");
            String gruid=content("global-rules");
            if(guiuid==null){
                guiuid=spawn(new CyrusLanguage(
                    "{ is: gui\n"+
                      "title: \"Cyrus Minecraft\"\n"+
                      "view:\n"+
                        "{ is: style direction: horizontal }\n"+
                        "{\n"+
                          "helpfulmod:  { input: checkbox label: \"Helpfyul animals\" }\n"+
                          "helpfullink: http://localhost:8081/o/uid-01b4-33f4-ff45-4d95.json\n"+
                          "tutorial:    { view: open item: http://localhost:8081/o/uid-5a7a-16e9-508f-2f65.json }\n"+
                        "}\n"+
                        "{ view: open raw  item: "+toURL(uid)+" }\n"+
                      "\n"+
                    "}\n", true));
                gruid=spawn(new CyrusLanguage("{ is: editable rule list title: \"Global Rules\" }", true));
                content("gui", guiuid);
                content("global-rules", gruid);
            }
            CyrusLanguage.addGlobalRules(gruid);
            contentAll("worlds:name");
            Desktop.getDesktop().browse(URI.create(localPre()+"/#"+toURL(guiuid)));
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

