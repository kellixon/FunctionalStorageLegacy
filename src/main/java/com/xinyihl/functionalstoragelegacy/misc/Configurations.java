package com.xinyihl.functionalstoragelegacy.misc;

import com.xinyihl.functionalstoragelegacy.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Tags.MOD_ID, name = Tags.MOD_ID, category = "")
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class Configurations {

    @Config.Name("general")
    public static final General GENERAL = new General();

    @Config.Name("compatibility")
    public static final Compatibility COMPATIBILITY = new Compatibility();

    @Config.Name("storage")
    public static final Storage STORAGE = new Storage();

    @Config.Name("generation")
    public static final Generation GENERATION = new Generation();

    @Config.Name("client")
    public static final Client CLIENT = new Client();

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Tags.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        }
    }

    public static final class General {

        @Config.Name("armoryCabinetSize")
        @Config.Comment("Armory slot amount")
        @Config.RangeInt(min = 1)
        public int armoryCabinetSize = 4096;

        @Config.Name("drawerControllerLinkingRange")
        @Config.Comment("Linking range radius")
        @Config.RangeInt(min = 1, max = 64)
        public int drawerControllerLinkingRange = 8;

        @Config.Name("upgradeTick")
        @Config.Comment("Every how many ticks the drawer upgrades will work")
        @Config.RangeInt(min = 1, max = 200)
        public int upgradeTick = 4;

        @Config.Name("upgradePullItems")
        @Config.Comment("How many items the pulling upgrade will try to pull")
        @Config.RangeInt(min = 1, max = 64)
        public int upgradePullItems = 4;

        @Config.Name("upgradePullFluid")
        @Config.Comment("How much fluid (in mb) the pulling upgrade will try to pull")
        @Config.RangeInt(min = 1, max = 10000)
        public int upgradePullFluid = 500;

        @Config.Name("upgradePushItems")
        @Config.Comment("How many items the pushing upgrade will try to push")
        @Config.RangeInt(min = 1, max = 64)
        public int upgradePushItems = 4;

        @Config.Name("upgradePushFluid")
        @Config.Comment("How much fluid (in mb) the pushing upgrade will try to push")
        @Config.RangeInt(min = 1, max = 10000)
        public int upgradePushFluid = 500;

        @Config.Name("upgradeCollectorItems")
        @Config.Comment("How many items the collector upgrade will try to pull")
        @Config.RangeInt(min = 1, max = 64)
        public int upgradeCollectorItems = 4;

        @Config.Name("upgradeCollectorFluid")
        @Config.Comment("How much fluid (in mb) the collector upgrade will try to collect")
        @Config.RangeInt(min = 1, max = 10000)
        public int upgradeCollectorFluid = 500;
    }

    public static final class Compatibility {

        @Config.Name("enableTOPCompatibility")
        @Config.Comment("Enable The One Probe compatibility integration")
        public boolean enableTOPCompatibility = true;

        @Config.Name("enableAE2Compatibility")
        @Config.Comment("Enable AE2 Extended Life storage bus compatibility for drawers and controllers")
        public boolean enableAE2Compatibility = true;
    }

    public static final class Storage {

        @Config.Name("copperMultiplier")
        @Config.Comment("Copper Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int copperMultiplier = 8;

        @Config.Name("goldMultiplier")
        @Config.Comment("Gold Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int goldMultiplier = 16;

        @Config.Name("diamondMultiplier")
        @Config.Comment("Diamond Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int diamondMultiplier = 24;

        @Config.Name("netheriteMultiplier")
        @Config.Comment("Netherite Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int netheriteMultiplier = 32;

        @Config.Name("fluidDivisor")
        @Config.Comment("Fluid storage divisor for Storage Upgrades")
        @Config.RangeInt(min = 1, max = 64)
        public int fluidDivisor = 2;

        @Config.Name("rangeDivisor")
        @Config.Comment("Range divisor for Storage Upgrades")
        @Config.RangeInt(min = 1, max = 64)
        public int rangeDivisor = 4;
    }

    public static final class Generation {

        @Config.Name("stoneGenerationT1")
        @Config.Comment("Stone Generation Upgrade T1 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT1 = 1;

        @Config.Name("stoneGenerationT2")
        @Config.Comment("Stone Generation Upgrade T2 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT2 = 2;

        @Config.Name("stoneGenerationT3")
        @Config.Comment("Stone Generation Upgrade T3 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT3 = 4;

        @Config.Name("stoneGenerationT4")
        @Config.Comment("Stone Generation Upgrade T4 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT4 = 8;

        @Config.Name("universalGeneratorT1")
        @Config.Comment("Universal Generator T1 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T1 = 1;

        @Config.Name("universalGeneratorT2")
        @Config.Comment("Universal Generator T2 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T2 = 2;

        @Config.Name("universalGeneratorT3")
        @Config.Comment("Universal Generator T3 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T3 = 4;

        @Config.Name("universalGeneratorT4")
        @Config.Comment("Universal Generator T4 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T4 = 8;

        @Config.Name("universalItemsGenerationTick")
        @Config.Comment("Universal Generator Items generation tick")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_ITEMS_GENERATION_TICK = 1;

        @Config.Name("universalItemsGeneration")
        @Config.Comment("Universal Generator Items generation")
        public String UNIVERSAL_ITEMS_GENERATION = "minecraft:sand";

        @Config.Name("universalItemsGenerationRegistered")
        @Config.Comment("Universal Generator Items generation registered")
        public Boolean UNIVERSAL_ITEMS_GENERATION_REGISTERED = false;

        @Config.Name("waterGenerationT1")
        @Config.Comment("Water Generation Upgrade T1 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T1 = 1000;

        @Config.Name("waterGenerationT2")
        @Config.Comment("Water Generation Upgrade T2 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T2 = 2000;

        @Config.Name("waterGenerationT3")
        @Config.Comment("Water Generation Upgrade T3 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T3 = 4000;

        @Config.Name("waterGenerationT4")
        @Config.Comment("Water Generation Upgrade T4 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T4 = 8000;

    }

    public static final class Client {

        @Config.Name("drawerRenderRange")
        @Config.Comment("Drawer content render range in blocks (default: 16)")
        @Config.RangeInt(min = 1, max = 128)
        public int drawerRenderRange = 16;
    }
}