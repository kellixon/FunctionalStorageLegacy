package com.xinyihl.functionalstoragelegacy.misc;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.common.tile.*;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.SimpleCompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.ControllerExtensionTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Register TileEntities
        GameRegistry.registerTileEntity(WoodDrawerTile.class, new ResourceLocation(Tags.MOD_ID, "drawer"));
        GameRegistry.registerTileEntity(FramedDrawerTile.class, new ResourceLocation(Tags.MOD_ID, "framed_drawer"));
        GameRegistry.registerTileEntity(CompactingDrawerTile.class, new ResourceLocation(Tags.MOD_ID, "compacting_drawer"));
        GameRegistry.registerTileEntity(SimpleCompactingDrawerTile.class, new ResourceLocation(Tags.MOD_ID, "simple_compacting_drawer"));
        GameRegistry.registerTileEntity(FluidDrawerTile.class, new ResourceLocation(Tags.MOD_ID, "fluid_drawer"));
        GameRegistry.registerTileEntity(EnderDrawerTile.class, new ResourceLocation(Tags.MOD_ID, "ender_drawer"));
        GameRegistry.registerTileEntity(ArmoryCabinetTile.class, new ResourceLocation(Tags.MOD_ID, "armory_cabinet"));
        GameRegistry.registerTileEntity(DrawerControllerTile.class, new ResourceLocation(Tags.MOD_ID, "storage_controller"));
        GameRegistry.registerTileEntity(ControllerExtensionTile.class, new ResourceLocation(Tags.MOD_ID, "controller_extension"));
    }
}
