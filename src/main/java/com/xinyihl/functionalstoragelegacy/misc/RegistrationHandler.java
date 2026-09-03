package com.xinyihl.functionalstoragelegacy.misc;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.common.block.*;
import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.compact.CompactingDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.compact.SimpleCompactingDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.controller.ControllerExtensionBlock;
import com.xinyihl.functionalstoragelegacy.common.block.controller.DrawerControllerBlock;
import com.xinyihl.functionalstoragelegacy.common.item.ConfigurationToolItem;
import com.xinyihl.functionalstoragelegacy.common.item.DrawerItemBlock;
import com.xinyihl.functionalstoragelegacy.common.item.LinkingToolItem;
import com.xinyihl.functionalstoragelegacy.common.item.upgrade.*;
import com.xinyihl.functionalstoragelegacy.common.recipe.FunctionalStorageRecipes;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.EnderDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.WoodDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

// ====== Registration ======
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public class RegistrationHandler {

    // ====== Blocks ======
    // Wood drawers
    public static final List<WoodDrawerBlock> WOOD_DRAWER_BLOCKS = new ArrayList<>();
    public static final List<FramedDrawerBlock> FRAMED_DRAWER_BLOCKS = new ArrayList<>();
    public static DrawerControllerBlock DRAWER_CONTROLLER_BLOCK;
    // ====== Creative Tab ======
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(Tags.MOD_ID) {
        @Nonnull
        @Override
        @SideOnly(Side.CLIENT)
        public ItemStack createIcon() {
            return new ItemStack(DRAWER_CONTROLLER_BLOCK);
        }
    };
    public static ControllerExtensionBlock CONTROLLER_EXTENSION_BLOCK;
    public static CompactingDrawerBlock COMPACTING_DRAWER_BLOCK;
    public static SimpleCompactingDrawerBlock SIMPLE_COMPACTING_DRAWER_BLOCK;
    public static FluidDrawerBlock FLUID_DRAWER_1;
    public static FluidDrawerBlock FLUID_DRAWER_2;
    public static FluidDrawerBlock FLUID_DRAWER_4;
    public static EnderDrawerBlock ENDER_DRAWER_BLOCK;
    public static ArmoryCabinetBlock ARMORY_CABINET_BLOCK;
    // ====== Items ======
    // Storage Upgrades
    public static StorageUpgradeItem IRON_DOWNGRADE;
    public static StorageUpgradeItem COPPER_UPGRADE;
    public static StorageUpgradeItem GOLD_UPGRADE;
    public static StorageUpgradeItem DIAMOND_UPGRADE;
    public static StorageUpgradeItem NETHERITE_UPGRADE;
    public static StorageUpgradeItem MAX_STORAGE_UPGRADE;
    public static UpgradeItem CREATIVE_VENDING_UPGRADE;
    // Utility Upgrades
    public static UtilityUpgradeItem VOID_UPGRADE;
    public static UtilityUpgradeItem REDSTONE_UPGRADE;
    public static UtilityUpgradeItem PULLING_UPGRADE;
    public static UtilityUpgradeItem PUSHING_UPGRADE;
    public static UtilityUpgradeItem COLLECTOR_UPGRADE;
    public static OreDictionaryUpgradeItem ORE_DICTIONARY_UPGRADE;
    public static UtilityUpgradeItem WIRELESS_PULLING_UPGRADE;
    public static UtilityUpgradeItem WIRELESS_PUSHING_UPGRADE;
    public static StoneGenerationUpgradeItem STONE_GENERATION_UPGRADE_T1;
    public static StoneGenerationUpgradeItem STONE_GENERATION_UPGRADE_T2;
    public static StoneGenerationUpgradeItem STONE_GENERATION_UPGRADE_T3;
    public static StoneGenerationUpgradeItem STONE_GENERATION_UPGRADE_T4;
    public static UniversalItemGeneration UNIVERSAL_ITEM_GENERATION_T1;
    public static UniversalItemGeneration UNIVERSAL_ITEM_GENERATION_T2;
    public static UniversalItemGeneration UNIVERSAL_ITEM_GENERATION_T3;
    public static UniversalItemGeneration UNIVERSAL_ITEM_GENERATION_T4;
    public static WaterGenerationUpgradeItem WATER_GENERATION_UPGRADE_T1;
    public static WaterGenerationUpgradeItem WATER_GENERATION_UPGRADE_T2;
    public static WaterGenerationUpgradeItem WATER_GENERATION_UPGRADE_T3;
    public static WaterGenerationUpgradeItem WATER_GENERATION_UPGRADE_T4;
    // Tools
    public static ConfigurationToolItem CONFIGURATION_TOOL;
    public static LinkingToolItem LINKING_TOOL;

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        // Wood drawers
        for (DrawerWoodType wood : DrawerWoodType.values()) {
            for (DrawerLayout layout : DrawerLayout.values()) {
                WoodDrawerBlock block = new WoodDrawerBlock(wood, layout);
                WOOD_DRAWER_BLOCKS.add(block);
                event.getRegistry().register(block);
            }
        }

        for (DrawerLayout layout : DrawerLayout.values()) {
            FramedDrawerBlock block = new FramedDrawerBlock(layout);
            FRAMED_DRAWER_BLOCKS.add(block);
            event.getRegistry().register(block);
        }

        // Special blocks
        DRAWER_CONTROLLER_BLOCK = new DrawerControllerBlock();
        CONTROLLER_EXTENSION_BLOCK = new ControllerExtensionBlock();
        COMPACTING_DRAWER_BLOCK = new CompactingDrawerBlock();
        SIMPLE_COMPACTING_DRAWER_BLOCK = new SimpleCompactingDrawerBlock();
        FLUID_DRAWER_1 = new FluidDrawerBlock(DrawerLayout.X_1);
        FLUID_DRAWER_2 = new FluidDrawerBlock(DrawerLayout.X_2);
        FLUID_DRAWER_4 = new FluidDrawerBlock(DrawerLayout.X_4);
        ENDER_DRAWER_BLOCK = new EnderDrawerBlock();
        ARMORY_CABINET_BLOCK = new ArmoryCabinetBlock();

        event.getRegistry().registerAll(DRAWER_CONTROLLER_BLOCK, CONTROLLER_EXTENSION_BLOCK, COMPACTING_DRAWER_BLOCK, SIMPLE_COMPACTING_DRAWER_BLOCK, FLUID_DRAWER_1, FLUID_DRAWER_2, FLUID_DRAWER_4, ENDER_DRAWER_BLOCK, ARMORY_CABINET_BLOCK);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // ItemBlocks for all blocks
        for (WoodDrawerBlock block : WOOD_DRAWER_BLOCKS) {
            event.getRegistry().register(createItemBlock(block));
        }
        for (FramedDrawerBlock block : FRAMED_DRAWER_BLOCKS) {
            event.getRegistry().register(createItemBlock(block));
        }
        event.getRegistry().registerAll(createItemBlock(DRAWER_CONTROLLER_BLOCK), createItemBlock(CONTROLLER_EXTENSION_BLOCK), createItemBlock(COMPACTING_DRAWER_BLOCK), createItemBlock(SIMPLE_COMPACTING_DRAWER_BLOCK), createItemBlock(FLUID_DRAWER_1), createItemBlock(FLUID_DRAWER_2), createItemBlock(FLUID_DRAWER_4), createItemBlock(ENDER_DRAWER_BLOCK), createItemBlock(ARMORY_CABINET_BLOCK));

        // Storage Upgrades
        IRON_DOWNGRADE = new StorageUpgradeItem(StorageUpgradeItem.StorageTier.IRON);
        IRON_DOWNGRADE.setRegistryName("iron_downgrade");
        IRON_DOWNGRADE.setTranslationKey(Tags.MOD_ID + ".iron_downgrade");

        COPPER_UPGRADE = new StorageUpgradeItem(StorageUpgradeItem.StorageTier.COPPER);
        COPPER_UPGRADE.setRegistryName("copper_upgrade");
        COPPER_UPGRADE.setTranslationKey(Tags.MOD_ID + ".copper_upgrade");

        GOLD_UPGRADE = new StorageUpgradeItem(StorageUpgradeItem.StorageTier.GOLD);
        GOLD_UPGRADE.setRegistryName("gold_upgrade");
        GOLD_UPGRADE.setTranslationKey(Tags.MOD_ID + ".gold_upgrade");

        DIAMOND_UPGRADE = new StorageUpgradeItem(StorageUpgradeItem.StorageTier.DIAMOND);
        DIAMOND_UPGRADE.setRegistryName("diamond_upgrade");
        DIAMOND_UPGRADE.setTranslationKey(Tags.MOD_ID + ".diamond_upgrade");

        NETHERITE_UPGRADE = new StorageUpgradeItem(StorageUpgradeItem.StorageTier.NETHERITE);
        NETHERITE_UPGRADE.setRegistryName("netherite_upgrade");
        NETHERITE_UPGRADE.setTranslationKey(Tags.MOD_ID + ".netherite_upgrade");

        MAX_STORAGE_UPGRADE = new StorageUpgradeItem(StorageUpgradeItem.StorageTier.MAX);
        MAX_STORAGE_UPGRADE.setRegistryName("max_storage_upgrade");
        MAX_STORAGE_UPGRADE.setTranslationKey(Tags.MOD_ID + ".max_storage_upgrade");
        MAX_STORAGE_UPGRADE.incompatibleWith(MAX_STORAGE_UPGRADE);

        CREATIVE_VENDING_UPGRADE = new UpgradeItem(DrawerUpgradeBehavior.SlotType.STORAGE, StorageFeature.CREATIVE) {
        };
        CREATIVE_VENDING_UPGRADE.setRegistryName("creative_vending_upgrade");
        CREATIVE_VENDING_UPGRADE.setTranslationKey(Tags.MOD_ID + ".creative_vending_upgrade");
        CREATIVE_VENDING_UPGRADE.setMaxStackSize(1);
        CREATIVE_VENDING_UPGRADE.setCreativeTab(CREATIVE_TAB);
        CREATIVE_VENDING_UPGRADE.incompatibleWith(CREATIVE_VENDING_UPGRADE);

        // Utility Upgrades
        VOID_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.VOID);
        VOID_UPGRADE.setRegistryName("void_upgrade");
        VOID_UPGRADE.setTranslationKey(Tags.MOD_ID + ".void_upgrade");
        VOID_UPGRADE.incompatibleWith(VOID_UPGRADE);

        REDSTONE_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.REDSTONE);
        REDSTONE_UPGRADE.setRegistryName("redstone_upgrade");
        REDSTONE_UPGRADE.setTranslationKey(Tags.MOD_ID + ".redstone_upgrade");
        REDSTONE_UPGRADE.incompatibleWith(REDSTONE_UPGRADE);

        PULLING_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.PULLING);
        PULLING_UPGRADE.setRegistryName("pulling_upgrade");
        PULLING_UPGRADE.setTranslationKey(Tags.MOD_ID + ".pulling_upgrade");

        PUSHING_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.PUSHING);
        PUSHING_UPGRADE.setRegistryName("pushing_upgrade");
        PUSHING_UPGRADE.setTranslationKey(Tags.MOD_ID + ".pushing_upgrade");

        COLLECTOR_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.COLLECTOR);
        COLLECTOR_UPGRADE.setRegistryName("collector_upgrade");
        COLLECTOR_UPGRADE.setTranslationKey(Tags.MOD_ID + ".collector_upgrade");

        ORE_DICTIONARY_UPGRADE = new OreDictionaryUpgradeItem();
        ORE_DICTIONARY_UPGRADE.setRegistryName("ore_dictionary_upgrade");
        ORE_DICTIONARY_UPGRADE.setTranslationKey(Tags.MOD_ID + ".ore_dictionary_upgrade");
        ORE_DICTIONARY_UPGRADE.insertableInto(WoodDrawerTile.class, CompactingDrawerTile.class);
        ORE_DICTIONARY_UPGRADE.incompatibleWith(ORE_DICTIONARY_UPGRADE);

        WIRELESS_PULLING_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.WIRELESS_PULLING);
        WIRELESS_PULLING_UPGRADE.setRegistryName("wireless_pulling_upgrade");
        WIRELESS_PULLING_UPGRADE.setTranslationKey(Tags.MOD_ID + ".wireless_pulling_upgrade");

        WIRELESS_PUSHING_UPGRADE = new UtilityUpgradeItem(UtilityUpgradeItem.UtilityAction.WIRELESS_PUSHING);
        WIRELESS_PUSHING_UPGRADE.setRegistryName("wireless_pushing_upgrade");
        WIRELESS_PUSHING_UPGRADE.setTranslationKey(Tags.MOD_ID + ".wireless_pushing_upgrade");

        STONE_GENERATION_UPGRADE_T1 = new StoneGenerationUpgradeItem(StoneGenerationUpgradeItem.StoneTier.T1);
        STONE_GENERATION_UPGRADE_T1.setRegistryName("stone_generation_upgrade_t1");
        STONE_GENERATION_UPGRADE_T1.setTranslationKey(Tags.MOD_ID + ".stone_generation_upgrade_t1");
        STONE_GENERATION_UPGRADE_T1.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

        STONE_GENERATION_UPGRADE_T2 = new StoneGenerationUpgradeItem(StoneGenerationUpgradeItem.StoneTier.T2);
        STONE_GENERATION_UPGRADE_T2.setRegistryName("stone_generation_upgrade_t2");
        STONE_GENERATION_UPGRADE_T2.setTranslationKey(Tags.MOD_ID + ".stone_generation_upgrade_t2");
        STONE_GENERATION_UPGRADE_T2.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

        STONE_GENERATION_UPGRADE_T3 = new StoneGenerationUpgradeItem(StoneGenerationUpgradeItem.StoneTier.T3);
        STONE_GENERATION_UPGRADE_T3.setRegistryName("stone_generation_upgrade_t3");
        STONE_GENERATION_UPGRADE_T3.setTranslationKey(Tags.MOD_ID + ".stone_generation_upgrade_t3");
        STONE_GENERATION_UPGRADE_T3.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

        STONE_GENERATION_UPGRADE_T4 = new StoneGenerationUpgradeItem(StoneGenerationUpgradeItem.StoneTier.T4);
        STONE_GENERATION_UPGRADE_T4.setRegistryName("stone_generation_upgrade_t4");
        STONE_GENERATION_UPGRADE_T4.setTranslationKey(Tags.MOD_ID + ".stone_generation_upgrade_t4");
        STONE_GENERATION_UPGRADE_T4.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

        WATER_GENERATION_UPGRADE_T1 = new WaterGenerationUpgradeItem(WaterGenerationUpgradeItem.WaterGenerationTire.T1);
        WATER_GENERATION_UPGRADE_T1.setRegistryName("water_generation_upgrade_t1");
        WATER_GENERATION_UPGRADE_T1.setTranslationKey(Tags.MOD_ID + ".water_generation_upgrade_t1");
        WATER_GENERATION_UPGRADE_T1.insertableInto(FluidDrawerTile.class);

        WATER_GENERATION_UPGRADE_T2 = new WaterGenerationUpgradeItem(WaterGenerationUpgradeItem.WaterGenerationTire.T2);
        WATER_GENERATION_UPGRADE_T2.setRegistryName("water_generation_upgrade_t2");
        WATER_GENERATION_UPGRADE_T2.setTranslationKey(Tags.MOD_ID + ".water_generation_upgrade_t2");
        WATER_GENERATION_UPGRADE_T2.insertableInto(FluidDrawerTile.class);

        WATER_GENERATION_UPGRADE_T3 = new WaterGenerationUpgradeItem(WaterGenerationUpgradeItem.WaterGenerationTire.T3);
        WATER_GENERATION_UPGRADE_T3.setRegistryName("water_generation_upgrade_t3");
        WATER_GENERATION_UPGRADE_T3.setTranslationKey(Tags.MOD_ID + ".water_generation_upgrade_t3");
        WATER_GENERATION_UPGRADE_T3.insertableInto(FluidDrawerTile.class);

        WATER_GENERATION_UPGRADE_T4 = new WaterGenerationUpgradeItem(WaterGenerationUpgradeItem.WaterGenerationTire.T4);
        WATER_GENERATION_UPGRADE_T4.setRegistryName("water_generation_upgrade_t4");
        WATER_GENERATION_UPGRADE_T4.setTranslationKey(Tags.MOD_ID + ".water_generation_upgrade_t4");
        WATER_GENERATION_UPGRADE_T4.insertableInto(FluidDrawerTile.class);

        if (Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION_REGISTERED) {
            UNIVERSAL_ITEM_GENERATION_T1 = new UniversalItemGeneration(UniversalItemGeneration.GenerationTier.T1);
            UNIVERSAL_ITEM_GENERATION_T1.setRegistryName("universal_item_generation_T1");
            UNIVERSAL_ITEM_GENERATION_T1.setTranslationKey(Tags.MOD_ID + ".universal_item_generation_T1");
            UNIVERSAL_ITEM_GENERATION_T1.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

            UNIVERSAL_ITEM_GENERATION_T2 = new UniversalItemGeneration(UniversalItemGeneration.GenerationTier.T2);
            UNIVERSAL_ITEM_GENERATION_T2.setRegistryName("universal_item_generation_T2");
            UNIVERSAL_ITEM_GENERATION_T2.setTranslationKey(Tags.MOD_ID + ".universal_item_generation_T2");
            UNIVERSAL_ITEM_GENERATION_T2.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

            UNIVERSAL_ITEM_GENERATION_T3 = new UniversalItemGeneration(UniversalItemGeneration.GenerationTier.T3);
            UNIVERSAL_ITEM_GENERATION_T3.setRegistryName("universal_item_generation_T3");
            UNIVERSAL_ITEM_GENERATION_T3.setTranslationKey(Tags.MOD_ID + ".universal_item_generation_T3");
            UNIVERSAL_ITEM_GENERATION_T3.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);

            UNIVERSAL_ITEM_GENERATION_T4 = new UniversalItemGeneration(UniversalItemGeneration.GenerationTier.T4);
            UNIVERSAL_ITEM_GENERATION_T4.setRegistryName("universal_item_generation_T4");
            UNIVERSAL_ITEM_GENERATION_T4.setTranslationKey(Tags.MOD_ID + ".universal_item_generation_T4");
            UNIVERSAL_ITEM_GENERATION_T4.insertableInto(WoodDrawerTile.class, EnderDrawerTile.class, CompactingDrawerTile.class);
        }

        // Tools
        CONFIGURATION_TOOL = new ConfigurationToolItem();
        CONFIGURATION_TOOL.setRegistryName("configuration_tool");
        CONFIGURATION_TOOL.setTranslationKey(Tags.MOD_ID + ".configuration_tool");

        LINKING_TOOL = new LinkingToolItem();
        LINKING_TOOL.setRegistryName("linking_tool");
        LINKING_TOOL.setTranslationKey(Tags.MOD_ID + ".linking_tool");

        event.getRegistry().registerAll(IRON_DOWNGRADE, COPPER_UPGRADE, GOLD_UPGRADE, DIAMOND_UPGRADE, NETHERITE_UPGRADE, MAX_STORAGE_UPGRADE, CREATIVE_VENDING_UPGRADE, VOID_UPGRADE, REDSTONE_UPGRADE, PULLING_UPGRADE, PUSHING_UPGRADE, COLLECTOR_UPGRADE, ORE_DICTIONARY_UPGRADE, WIRELESS_PULLING_UPGRADE, WIRELESS_PUSHING_UPGRADE, CONFIGURATION_TOOL, LINKING_TOOL, STONE_GENERATION_UPGRADE_T1, STONE_GENERATION_UPGRADE_T2, STONE_GENERATION_UPGRADE_T3, STONE_GENERATION_UPGRADE_T4, WATER_GENERATION_UPGRADE_T1, WATER_GENERATION_UPGRADE_T2, WATER_GENERATION_UPGRADE_T3, WATER_GENERATION_UPGRADE_T4);
        if (Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION_REGISTERED) {
            event.getRegistry().registerAll(UNIVERSAL_ITEM_GENERATION_T1, UNIVERSAL_ITEM_GENERATION_T2, UNIVERSAL_ITEM_GENERATION_T3, UNIVERSAL_ITEM_GENERATION_T4);
        }
    }

    private static ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = block instanceof DrawerBlock ? new DrawerItemBlock(block) : new ItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());
        return itemBlock;
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        FunctionalStorageRecipes.register(event.getRegistry());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();

        if (!player.isCreative() || world.isRemote) return;

        BlockPos pos = event.getPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof DrawerBlock) {
            DrawerBlock drawerBlock = (DrawerBlock) block;
            int slot = drawerBlock.getHitSlot(state, world, pos, player);
            if (slot != -1) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof ControllableDrawerTile) {
                    event.setCanceled(true);
                    ((ControllableDrawerTile) te).onClicked(player, slot);
                    return;
                }
            }
        }

        if (block instanceof EnderDrawerBlock) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof EnderDrawerTile) {
                ItemStack stack = player.getHeldItemMainhand();
                if (stack.getItem() == RegistrationHandler.LINKING_TOOL) {
                    event.setCanceled(true);
                    LinkingToolItem.setEnderFrequency(stack, ((EnderDrawerTile) te).getFrequency());
                    player.sendStatusMessage(new TextComponentTranslation("linkingtool.ender.stored").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.AQUA)), true);
                }
            }
        }
    }
}
