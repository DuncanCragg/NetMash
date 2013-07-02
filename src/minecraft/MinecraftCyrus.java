package net.minecraft.src;

import java.awt.*;
import java.net.*;
import java.util.*;

import cyrus.forest.*;

import static cyrus.lib.Utils.*;
import static cyrus.forest.UID.*;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public class MinecraftCyrus extends WebObject {

    static MinecraftCyrus that=null;

    public MinecraftCyrus(){
        super("{ is: minecraft site }",true);
        that=this;
    }

    public MinecraftCyrus(String s){ super(s,true); }

    static WebObject globalrules;
    boolean first=true;

    public void evaluate(){ try{
        if(contentIsOrListContains("is","site") && first){ first=false;
            String guiuid=content("gui");
            String gruid=content("global-rules");
            if(guiuid==null){
                guiuid=spawn(new MinecraftCyrus(
                    "{ is: gui\n"+
                    "  title: \"Cyrus Minecraft\"\n"+
                    "  view:\n"+
                    "    { is: style direction: horizontal }\n"+
                    "    (\n"+
                    "      {\n"+
                    "        title: \"Choose mods to include\"\n"+
                    "        helpfulmod: { input: checkbox label: \"Helpful animals\" }\n"+
                    "        relaxmod:   { input: checkbox label: \"Relaxation\" }\n"+
                    "      }\n"+
                    "      { view: closed  item: http://localhost:8081/o/uid-5a7a-16e9-508f-2f65.json }\n"+
                    "    )\n"+
                    "    { view: open raw  item: "+toURL(uid)+" }\n"+
                    "}\n"));
                globalrules=new CyrusLanguage("{ is: editable rule list title: \"Global Rules\" }", true);
                gruid=spawn(globalrules);
                content("gui", guiuid);
                content("global-rules", gruid);
            }
            CyrusLanguage.addGlobalRules(gruid);
            contentAll("worlds:name");
            Desktop.getDesktop().browse(URI.create(localPre()+"/#"+toURL(guiuid)));
        }
        else
        if(contentIsOrListContains("is","gui")){
            for(String alerted: alerted()){
                contentTemp("Alerted", alerted);
                if(contentIsOrListContains("Alerted:is", "form")){
                    final LinkedList<String> gr=new LinkedList<String>();
                    if(contentBool("Alerted:form:helpfulmod")) gr.add("http://localhost:8081/o/uid-01b4-33f4-ff45-4d95.json");
                    if(contentBool("Alerted:form:relaxmod"  )) gr.add("http://localhost:8081/o/uid-1111-2222-3333-4444.json");
                    new Evaluator(globalrules){ public void evaluate(){
                        globalrules.contentList("list", gr);
                    }};
                }
                contentTemp("Alerted", null);
            }
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

