package com.xinyihl.functionalstoragelegacy;

import com.xinyihl.functionalstoragelegacy.common.integration.top.TheOneProbeCompat;
import com.xinyihl.functionalstoragelegacy.common.network.NetworkHandler;
import com.xinyihl.functionalstoragelegacy.misc.CommonProxy;
import com.xinyihl.functionalstoragelegacy.misc.GuiHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class FunctionalStorageLegacy {

    @Mod.Instance(Tags.MOD_ID)
    public static FunctionalStorageLegacy INSTANCE;
    @SidedProxy(clientSide = "com.xinyihl.functionalstoragelegacy.misc.ClientProxy", serverSide = "com.xinyihl.functionalstoragelegacy.misc.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        TheOneProbeCompat.register();
        NetworkHandler.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        proxy.preInit(event);
    }
}
