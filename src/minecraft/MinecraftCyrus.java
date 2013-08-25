package net.minecraft.src;

import java.awt.*;
import java.net.*;
import java.util.*;

import cyrus.platform.Kernel;
import cyrus.forest.*;

import static cyrus.lib.Utils.*;
import static cyrus.forest.UID.*;

import net.minecraft.server.MinecraftServer;

/** Singleton global Cyrus 'site'. */
public class MinecraftCyrus extends WebObject {

    static MinecraftCyrus that=null;

    public MinecraftCyrus(){
        super("{ is: minecraft site }",true);
        that=this;
    }

    public MinecraftCyrus(String s){ super(s,true); }

    static String globalruleuid=null;
    boolean first=true;

    LinkedHashMap mods=new LinkedHashMap();

    public void evaluate(){ try{
        if(contentIsOrListContains("is","site") && first){ first=false;
            String guiuid=content("gui");
            globalruleuid=content("global-rules");
            if(guiuid==null){
                guiuid=spawn(new MinecraftCyrus(
                    "{ is: gui\n"+
                    "  title: \"Cyrus Minecraft\"\n"+
                    "  site: "+toURL(uid)+"\n"+
                    "  top-mods: "+Kernel.config.stringPathN("app:top-mods")+"\n"+
                    "  view:\n"+
                    "    { is: style direction: horizontal }\n"+
                    "    (\n"+
                    "      { pretext: \"Loading mods..\" }\n"+
                    "      { view: closed  item: "+Kernel.config.stringPathN("app:tutorial")+" }\n"+
                    "    )\n"+
                    "    { view: open raw  item: "+toURL(uid)+" }\n"+
                    "}\n"));
                globalruleuid=spawn(new CyrusLanguage("{ is: editable list title: \"Global Rules\" }", true));
                content("gui", guiuid);
                content("global-rules", globalruleuid);
            }
            CyrusLanguage.addGlobalRules(globalruleuid);
            contentAll("worlds:name");
            URI openthis=URI.create(localPre()+"/#"+toURL(guiuid));
            log("Opening "+openthis+" in default browser..");
            Desktop.getDesktop().browse(openthis);
        }
        else
        if(contentIsOrListContains("is","gui")){
            if(!contentSet("view:1:0:modtext") && contentSet("top-mods:title")){
                content("view:1:0:pretext","Choose mods to include: ");
                content("view:1:0:modtext", content("top-mods:title"));
            }
            LinkedList modson=contentListMayJump("site:global-rules");
            LinkedList topmods=contentListMayJump("top-mods");
            int i=0;
            if(topmods!=null) for(Object o: topmods){
                if(!(o instanceof String)) continue;
                String tag="mod-"+i;
                mods.put(tag,o);
                String title=content("top-mods:list:"+i+":title");
                boolean on=modson!=null && modson.contains(o);
                contentHash("view:1:0:"+tag, hash("input","checkbox","label",title,"value",on));
            i++; }
            for(String alerted: alerted()){
                contentTemp("Alerted", alerted);
                if(contentIsOrListContains("Alerted:is", "form")){
                    final LinkedList gr=new LinkedList();
                    LinkedHashMap<String,Object> form=contentHash("Alerted:form");
                    if(form!=null) for(Map.Entry<String,Object> entry: form.entrySet()){
                        String tag=entry.getKey();
                        if(contentBool("Alerted:form:"+tag)) gr.add(mods.get(tag));
                    }
                    final WebObject globalrules=onlyUseThisToHandControlOfThreadToDependent(globalruleuid);
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

