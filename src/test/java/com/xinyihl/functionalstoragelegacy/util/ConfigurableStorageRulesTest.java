package com.xinyihl.functionalstoragelegacy.util;

import com.xinyihl.functionalstoragelegacy.common.inventory.CompactingInventoryHandler;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ConfigurableStorageRulesTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(32000);

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    private static void assertTier(CompactingInventoryHandler.Tier tier, Item item, int metadata, long units) {
        assertTrue(tier.hasTemplate());
        assertEquals(item, tier.getTemplate().getItem());
        assertEquals(metadata, tier.getTemplate().getMetadata());
        assertEquals(units, tier.getBaseUnits());
    }

    private static Item registeredItem(String path, int ignoredMetadata) {
        ResourceLocation name = new ResourceLocation(
                "functionalstoragelegacy_test", path + NEXT_ITEM_ID.getAndIncrement());
        Item item = new Item().setRegistryName(name);
        @SuppressWarnings("unchecked")
        ForgeRegistry<Item> registry = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
        boolean wasFrozen = registry.isLocked();
        if (wasFrozen) registry.unfreeze();
        try {
            registry.register(item);
            return item;
        } finally {
            if (wasFrozen) registry.freeze();
        }
    }

    @Test
    public void oreDictionaryBlacklistTakesPriorityAndWhitelistRestrictsMatches() {
        Item first = registeredItem("ore_first", 0);
        Item second = registeredItem("ore_second", 0);
        String allowed = "functionalStorageLegacyAllowed" + NEXT_ITEM_ID.get();
        String blocked = "functionalStorageLegacyBlocked" + NEXT_ITEM_ID.get();
        OreDictionary.registerOre(allowed, new ItemStack(first));
        OreDictionary.registerOre(allowed, new ItemStack(second));
        OreDictionary.registerOre(blocked, new ItemStack(first));
        OreDictionary.registerOre(blocked, new ItemStack(second));

        String[] oldBlacklist = Configurations.GENERAL.oreDictionaryBlacklist;
        String[] oldWhitelist = Configurations.GENERAL.oreDictionaryWhitelist;
        try {
            Configurations.GENERAL.oreDictionaryBlacklist = new String[]{blocked};
            Configurations.GENERAL.oreDictionaryWhitelist = new String[]{allowed};
            assertTrue(ItemUtil.sharesOreDictionary(new ItemStack(first), new ItemStack(second)));

            Configurations.GENERAL.oreDictionaryBlacklist = new String[]{allowed, blocked};
            assertFalse(ItemUtil.sharesOreDictionary(new ItemStack(first), new ItemStack(second)));

            Configurations.GENERAL.oreDictionaryBlacklist = new String[0];
            Configurations.GENERAL.oreDictionaryWhitelist = new String[]{"missingOreName"};
            assertFalse(ItemUtil.sharesOreDictionary(new ItemStack(first), new ItemStack(second)));
        } finally {
            Configurations.GENERAL.oreDictionaryBlacklist = oldBlacklist;
            Configurations.GENERAL.oreDictionaryWhitelist = oldWhitelist;
        }
    }

    @Test
    public void extraCompactingRuleWorksFromEitherConfiguredTierAndHonorsMetadata() {
        Item higher = registeredItem("configured_high", 2);
        Item lower = registeredItem("configured_low", 3);
        String rule = higher.getRegistryName() + ":2, " + lower.getRegistryName() + ":3, 4";
        String[] oldRules = Configurations.GENERAL.extraCompactingRules;
        boolean oldEnabled = Configurations.GENERAL.registerExtraCompactingRules;
        try {
            Configurations.GENERAL.extraCompactingRules = new String[]{rule};
            Configurations.GENERAL.registerExtraCompactingRules = true;

            List<CompactingInventoryHandler.Tier> fromLower = CompactingUtil.getCompactingResults(
                    null, new ItemStack(lower, 1, 3), 2, 1);
            assertTier(fromLower.get(0), higher, 2, 4L);
            assertTier(fromLower.get(1), lower, 3, 1L);

            List<CompactingInventoryHandler.Tier> fromHigher = CompactingUtil.getCompactingResults(
                    null, new ItemStack(higher, 1, 2), 2, 0);
            assertTier(fromHigher.get(0), higher, 2, 4L);
            assertTier(fromHigher.get(1), lower, 3, 1L);
        } finally {
            Configurations.GENERAL.extraCompactingRules = oldRules;
            Configurations.GENERAL.registerExtraCompactingRules = oldEnabled;
        }
    }
}
