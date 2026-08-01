package com.xinyihl.functionalstoragelegacy.misc;

import com.xinyihl.functionalstoragelegacy.Tags;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Tags.MOD_ID, name = Tags.MOD_ID, category = "")
@Config.LangKey("config.functionalstoragelegacy")
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class Configurations {

    @Config.Name("general")
    @Config.LangKey("config.functionalstoragelegacy.general")
    public static final General GENERAL = new General();

    @Config.Name("compatibility")
    @Config.LangKey("config.functionalstoragelegacy.compatibility")
    public static final Compatibility COMPATIBILITY = new Compatibility();

    @Config.Name("storage")
    @Config.LangKey("config.functionalstoragelegacy.storage")
    public static final Storage STORAGE = new Storage();

    @Config.Name("generation")
    @Config.LangKey("config.functionalstoragelegacy.generation")
    public static final Generation GENERATION = new Generation();

    @Config.Name("client")
    @Config.LangKey("config.functionalstoragelegacy.client")
    public static final Client CLIENT = new Client();

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Tags.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        }
    }

    public static final class General {

        @Config.Name("extraCompactingRules")
        @Config.LangKey("config.functionalstoragelegacy.extra_compacting_rules")
        @Config.Comment({
                "Additional compacting rules in the form: higher item, lower item, ratio.",
                "Items must use domain:item or domain:item:meta. Example: minecraft:clay, minecraft:clay_ball, 4"
        })
        public String[] extraCompactingRules = {"minecraft:clay, minecraft:clay_ball, 4"};

        @Config.Name("oreDictionaryBlacklist")
        @Config.LangKey("config.functionalstoragelegacy.ore_dictionary_blacklist")
        @Config.Comment("Ore Dictionary names that the Ore Dictionary Upgrade must never match")
        public String[] oreDictionaryBlacklist = {};

        @Config.Name("oreDictionaryWhitelist")
        @Config.LangKey("config.functionalstoragelegacy.ore_dictionary_whitelist")
        @Config.Comment("Ore Dictionary names that the Ore Dictionary Upgrade may match. Empty allows every non-blacklisted name")
        public String[] oreDictionaryWhitelist = {};

        @Config.Name("registerExtraCompactingRules")
        @Config.LangKey("config.functionalstoragelegacy.register_extra_compacting_rules")
        @Config.Comment("Allow the configured additional compacting rules")
        public boolean registerExtraCompactingRules = true;

        @Config.Name("keepContentsOnBreak")
        @Config.LangKey("config.functionalstoragelegacy.keep_contents_on_break")
        @Config.Comment("Keep stored contents, filters, lock state, and upgrades in the dropped block when broken")
        public boolean keepContentsOnBreak = true;

        @Config.Name("armoryCabinetSize")
        @Config.LangKey("config.functionalstoragelegacy.armory_cabinet_size")
        @Config.Comment("Armory slot amount")
        @Config.RangeInt(min = 1)
        public int armoryCabinetSize = 4096;

        @Config.Name("drawerControllerLinkingRange")
        @Config.LangKey("config.functionalstoragelegacy.drawer_controller_linking_range")
        @Config.Comment("Linking range radius")
        @Config.RangeInt(min = 1, max = 64)
        public int drawerControllerLinkingRange = 8;

        @Config.Name("upgradeTick")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_tick")
        @Config.Comment("Every how many ticks the drawer upgrades will work")
        @Config.RangeInt(min = 1, max = 200)
        public int upgradeTick = 4;

        @Config.Name("upgradePullItems")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_pull_items")
        @Config.Comment("How many items the pulling upgrade will try to pull")
        @Config.RangeInt(min = 1, max = 64)
        public int upgradePullItems = 4;

        @Config.Name("upgradePullFluid")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_pull_fluid")
        @Config.Comment("How much fluid (in mb) the pulling upgrade will try to pull")
        @Config.RangeInt(min = 1, max = 10000)
        public int upgradePullFluid = 500;

        @Config.Name("upgradePushItems")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_push_items")
        @Config.Comment("How many items the pushing upgrade will try to push")
        @Config.RangeInt(min = 1, max = 64)
        public int upgradePushItems = 4;

        @Config.Name("upgradePushFluid")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_push_fluid")
        @Config.Comment("How much fluid (in mb) the pushing upgrade will try to push")
        @Config.RangeInt(min = 1, max = 10000)
        public int upgradePushFluid = 500;

        @Config.Name("upgradeCollectorItems")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_collector_items")
        @Config.Comment("How many items the collector upgrade will try to pull")
        @Config.RangeInt(min = 1, max = 64)
        public int upgradeCollectorItems = 4;

        @Config.Name("upgradeCollectorFluid")
        @Config.LangKey("config.functionalstoragelegacy.upgrade_collector_fluid")
        @Config.Comment("How much fluid (in mb) the collector upgrade will try to collect")
        @Config.RangeInt(min = 1, max = 10000)
        public int upgradeCollectorFluid = 500;
    }

    public static final class Compatibility {

        @Config.Name("enableTOPCompatibility")
        @Config.LangKey("config.functionalstoragelegacy.enable_top_compatibility")
        @Config.Comment("Enable The One Probe compatibility integration")
        public boolean enableTOPCompatibility = true;

        @Config.Name("enableAE2Compatibility")
        @Config.LangKey("config.functionalstoragelegacy.enable_ae2_compatibility")
        @Config.Comment("Enable AE2 Extended Life storage bus compatibility for drawers and controllers")
        public boolean enableAE2Compatibility = true;
    }

    public static final class Storage {

        @Config.Name("copperMultiplier")
        @Config.LangKey("config.functionalstoragelegacy.copper_multiplier")
        @Config.Comment("Copper Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int copperMultiplier = 8;

        @Config.Name("goldMultiplier")
        @Config.LangKey("config.functionalstoragelegacy.gold_multiplier")
        @Config.Comment("Gold Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int goldMultiplier = 16;

        @Config.Name("diamondMultiplier")
        @Config.LangKey("config.functionalstoragelegacy.diamond_multiplier")
        @Config.Comment("Diamond Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int diamondMultiplier = 24;

        @Config.Name("netheriteMultiplier")
        @Config.LangKey("config.functionalstoragelegacy.netherite_multiplier")
        @Config.Comment("Netherite Upgrade storage multiplier")
        @Config.RangeInt(min = 1, max = 1024)
        public int netheriteMultiplier = 32;

        @Config.Name("fluidDivisor")
        @Config.LangKey("config.functionalstoragelegacy.fluid_divisor")
        @Config.Comment("Fluid storage divisor for Storage Upgrades")
        @Config.RangeInt(min = 1, max = 64)
        public int fluidDivisor = 2;

        @Config.Name("rangeDivisor")
        @Config.LangKey("config.functionalstoragelegacy.range_divisor")
        @Config.Comment("Range divisor for Storage Upgrades")
        @Config.RangeInt(min = 1, max = 64)
        public int rangeDivisor = 4;
    }

    public static final class Generation {

        @Config.Name("stoneGenerationT1")
        @Config.LangKey("config.functionalstoragelegacy.stone_generation_t1")
        @Config.Comment("Stone Generation Upgrade T1 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT1 = 1;

        @Config.Name("stoneGenerationT2")
        @Config.LangKey("config.functionalstoragelegacy.stone_generation_t2")
        @Config.Comment("Stone Generation Upgrade T2 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT2 = 2;

        @Config.Name("stoneGenerationT3")
        @Config.LangKey("config.functionalstoragelegacy.stone_generation_t3")
        @Config.Comment("Stone Generation Upgrade T3 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT3 = 4;

        @Config.Name("stoneGenerationT4")
        @Config.LangKey("config.functionalstoragelegacy.stone_generation_t4")
        @Config.Comment("Stone Generation Upgrade T4 generation rate")
        @Config.RangeInt(min = 1)
        public int stoneGenerationT4 = 8;

        @Config.Name("universalGeneratorT1")
        @Config.LangKey("config.functionalstoragelegacy.universal_generator_t1")
        @Config.Comment("Universal Generator T1 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T1 = 1;

        @Config.Name("universalGeneratorT2")
        @Config.LangKey("config.functionalstoragelegacy.universal_generator_t2")
        @Config.Comment("Universal Generator T2 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T2 = 2;

        @Config.Name("universalGeneratorT3")
        @Config.LangKey("config.functionalstoragelegacy.universal_generator_t3")
        @Config.Comment("Universal Generator T3 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T3 = 4;

        @Config.Name("universalGeneratorT4")
        @Config.LangKey("config.functionalstoragelegacy.universal_generator_t4")
        @Config.Comment("Universal Generator T4 generation rate")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_GENERATION_RATE_T4 = 8;

        @Config.Name("universalItemsGenerationTick")
        @Config.LangKey("config.functionalstoragelegacy.universal_items_generation_tick")
        @Config.Comment("Universal Generator Items generation tick")
        @Config.RangeInt(min = 1)
        public int UNIVERSAL_ITEMS_GENERATION_TICK = 1;

        @Config.Name("universalItemsGeneration")
        @Config.LangKey("config.functionalstoragelegacy.universal_items_generation")
        @Config.Comment("Universal Generator Items generation")
        public String UNIVERSAL_ITEMS_GENERATION = "minecraft:sand";

        @Config.Name("universalItemsGenerationRegistered")
        @Config.LangKey("config.functionalstoragelegacy.universal_items_generation_registered")
        @Config.Comment("Universal Generator Items generation registered")
        public Boolean UNIVERSAL_ITEMS_GENERATION_REGISTERED = false;

        @Config.Name("waterGenerationT1")
        @Config.LangKey("config.functionalstoragelegacy.water_generation_t1")
        @Config.Comment("Water Generation Upgrade T1 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T1 = 1000;

        @Config.Name("waterGenerationT2")
        @Config.LangKey("config.functionalstoragelegacy.water_generation_t2")
        @Config.Comment("Water Generation Upgrade T2 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T2 = 2000;

        @Config.Name("waterGenerationT3")
        @Config.LangKey("config.functionalstoragelegacy.water_generation_t3")
        @Config.Comment("Water Generation Upgrade T3 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T3 = 4000;

        @Config.Name("waterGenerationT4")
        @Config.LangKey("config.functionalstoragelegacy.water_generation_t4")
        @Config.Comment("Water Generation Upgrade T4 generation rate")
        @Config.RangeInt(min = 1)
        public int WATER_GENERATION_T4 = 8000;

    }

    public static final class Client {

        @Config.Name("drawerRenderRange")
        @Config.LangKey("config.functionalstoragelegacy.drawer_render_range")
        @Config.Comment("Drawer content render range in blocks (default: 16)")
        @Config.RangeInt(min = 1, max = 128)
        public int drawerRenderRange = 16;
    }
}
