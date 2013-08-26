package net.minecraft.src;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;

import static cyrus.lib.Utils.*;

public class mod_Cyrus extends BaseMod {

    public String getName() { return this.getClass().getSimpleName(); }

    public String getVersion(){ return "Cyrus Minecraft Mod 0.01"; }

    public String toString() { return this.getName() + ' ' + this.getVersion(); }

    public void load(){
        MinecraftCyrus.self.runCyrus();
        ModLoader.setInGameHook(this, true, true);
    }

    public boolean onTickInGame(float var1, Minecraft minecraft){
        MinecraftCyrus.self.onTick();
        return true;
    }

/*
    public boolean onTickInGUI(float var1, Minecraft minecraft, GuiScreen var3) { return true; }

    public void onItemPickup(EntityPlayer player, ItemStack itemStack){} //  EntityPlayerMP :: item.egg

    // ModLoader.registerKey(this, new KeyBinding("Alt-Tab", 0xa5), true);

    public void keyboardEvent(KeyBinding var1) { logXX("key "+var1); }

    public void modsLoaded() {}

    public void generateNether(World var1, Random var2, int var3, int var4) {}

    public void generateSurface(World var1, Random var2, int var3, int var4) {}

    public void addRenderer(Map var1) {}

    public String getPriorities() { return ""; }

    public void registerAnimation(Minecraft var1) {}

    public void renderInvBlock(RenderBlocks var1, Block var2, int var3, int var4) {}

    public boolean renderWorldBlock(RenderBlocks var1, IBlockAccess var2, int var3, int var4, int var5, Block var6, int var7) { return false; }

    public void clientDisconnect(NetClientHandler var1) { }

    public int addFuel(int var1, int var2) { return 0; }

    public void takenFromCrafting(EntityPlayer var1, ItemStack var2, IInventory var3) {}

    public void takenFromFurnace(EntityPlayer var1, ItemStack var2) {}

    public GuiContainer getContainerGUI(EntityClientPlayerMP var1, int var2, int var3, int var4, int var5) { return null; }

    public Entity spawnEntity(int var1, World var2, double var3, double var5, double var7) { return null; }

    public void clientChat(String var1) {}

    public void serverChat(NetServerHandler var1, String var2) {}

    public void clientCustomPayload(NetClientHandler var1, Packet250CustomPayload var2) {}

    public void serverCustomPayload(NetServerHandler var1, Packet250CustomPayload var2) {}

    public Packet23VehicleSpawn getSpawnPacket(Entity var1, int var2) { return null; }
*/
}


