package com.xinyihl.functionalstoragelegacy.misc;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.client.model.FramedDrawerBakedModel;
import com.xinyihl.functionalstoragelegacy.client.render.ControllerRenderer;
import com.xinyihl.functionalstoragelegacy.client.render.DrawerRenderer;
import com.xinyihl.functionalstoragelegacy.common.block.FramedDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.WoodDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.tile.EnderDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.FramedDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.WoodDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.SimpleCompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Objects;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Register TESRs
        ClientRegistry.bindTileEntitySpecialRenderer(WoodDrawerTile.class, new DrawerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FramedDrawerTile.class, new DrawerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(CompactingDrawerTile.class, new DrawerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(SimpleCompactingDrawerTile.class, new DrawerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(FluidDrawerTile.class, new DrawerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(EnderDrawerTile.class, new DrawerRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(DrawerControllerTile.class, new ControllerRenderer());
    }

    @Mod.EventBusSubscriber(value = Side.CLIENT, modid = Tags.MOD_ID)
    public static class ModelRegistration {

        @SubscribeEvent
        public static void registerModels(ModelRegistryEvent event) {
            // Wood drawer blocks
            for (WoodDrawerBlock block : RegistrationHandler.WOOD_DRAWER_BLOCKS) {
                registerBlockModel(block);
            }
            for (FramedDrawerBlock block : RegistrationHandler.FRAMED_DRAWER_BLOCKS) {
                registerBlockModel(block);
            }

            // Special blocks
            registerBlockModel(RegistrationHandler.DRAWER_CONTROLLER_BLOCK);
            registerBlockModel(RegistrationHandler.CONTROLLER_EXTENSION_BLOCK);
            registerBlockModel(RegistrationHandler.COMPACTING_DRAWER_BLOCK);
            registerBlockModel(RegistrationHandler.SIMPLE_COMPACTING_DRAWER_BLOCK);
            registerBlockModel(RegistrationHandler.FLUID_DRAWER_1);
            registerBlockModel(RegistrationHandler.FLUID_DRAWER_2);
            registerBlockModel(RegistrationHandler.FLUID_DRAWER_4);
            registerBlockModel(RegistrationHandler.ENDER_DRAWER_BLOCK);
            registerBlockModel(RegistrationHandler.ARMORY_CABINET_BLOCK);

            // Items
            registerItemModel(RegistrationHandler.IRON_DOWNGRADE);
            registerItemModel(RegistrationHandler.COPPER_UPGRADE);
            registerItemModel(RegistrationHandler.GOLD_UPGRADE);
            registerItemModel(RegistrationHandler.DIAMOND_UPGRADE);
            registerItemModel(RegistrationHandler.NETHERITE_UPGRADE);
            registerItemModel(RegistrationHandler.MAX_STORAGE_UPGRADE);
            registerItemModel(RegistrationHandler.CREATIVE_VENDING_UPGRADE);
            registerItemModel(RegistrationHandler.VOID_UPGRADE);
            registerItemModel(RegistrationHandler.REDSTONE_UPGRADE);
            registerItemModel(RegistrationHandler.PULLING_UPGRADE);
            registerItemModel(RegistrationHandler.PUSHING_UPGRADE);
            registerItemModel(RegistrationHandler.COLLECTOR_UPGRADE);
            registerItemModel(RegistrationHandler.ORE_DICTIONARY_UPGRADE);
            registerItemModel(RegistrationHandler.WIRELESS_PULLING_UPGRADE);
            registerItemModel(RegistrationHandler.WIRELESS_PUSHING_UPGRADE);
            registerItemModel(RegistrationHandler.CONFIGURATION_TOOL);
            registerItemModel(RegistrationHandler.LINKING_TOOL);
            registerItemModel(RegistrationHandler.STONE_GENERATION_UPGRADE_T1);
            registerItemModel(RegistrationHandler.STONE_GENERATION_UPGRADE_T2);
            registerItemModel(RegistrationHandler.STONE_GENERATION_UPGRADE_T3);
            registerItemModel(RegistrationHandler.STONE_GENERATION_UPGRADE_T4);
            registerItemModel(RegistrationHandler.WATER_GENERATION_UPGRADE_T1);
            registerItemModel(RegistrationHandler.WATER_GENERATION_UPGRADE_T2);
            registerItemModel(RegistrationHandler.WATER_GENERATION_UPGRADE_T3);
            registerItemModel(RegistrationHandler.WATER_GENERATION_UPGRADE_T4);

            if (Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION_REGISTERED) {
                registerItemModel(RegistrationHandler.UNIVERSAL_ITEM_GENERATION_T1);
                registerItemModel(RegistrationHandler.UNIVERSAL_ITEM_GENERATION_T2);
                registerItemModel(RegistrationHandler.UNIVERSAL_ITEM_GENERATION_T3);
                registerItemModel(RegistrationHandler.UNIVERSAL_ITEM_GENERATION_T4);
            }
        }

        @SubscribeEvent
        public static void bakeFramedModels(ModelBakeEvent event) {
            for (ModelResourceLocation location : new ArrayList<>(event.getModelRegistry().getKeys())) {
                if (!Tags.MOD_ID.equals(location.getNamespace()) || !location.getPath().matches("framed_[124]")) {
                    continue;
                }
                event.getModelRegistry().putObject(location, new FramedDrawerBakedModel(event.getModelRegistry().getObject(location)));
            }
        }

        private static void registerBlockModel(Block block) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, new ModelResourceLocation(Objects.requireNonNull(block.getRegistryName()), "inventory"));
        }

        private static void registerItemModel(Item item) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(Objects.requireNonNull(item.getRegistryName()), "inventory"));
        }
    }
}
