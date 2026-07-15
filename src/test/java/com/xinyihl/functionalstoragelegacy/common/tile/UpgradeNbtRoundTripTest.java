package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeModifier;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.item.upgrade.DrawerUpgradeBehavior;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UpgradeNbtRoundTripTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(32000);

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void installedUpgradesRoundTripAndRebuildImmutableState() {
        TestUpgradeItem storage = register("storage", new TestUpgradeItem(
                DrawerUpgradeBehavior.SlotType.STORAGE,
                StorageFeature.CREATIVE,
                UpgradeModifier.multiply(2.5D)));
        TestUpgradeItem utility = register("utility", new TestUpgradeItem(
                DrawerUpgradeBehavior.SlotType.UTILITY,
                StorageFeature.VOID_OVERFLOW,
                null));

        WoodDrawerTile source = new WoodDrawerTile();
        assertTrue(source.canInsertStorageUpgrade(0, new ItemStack(storage)));
        assertFalse(source.canInsertUtilityUpgrade(0, new ItemStack(storage)));
        assertTrue(source.canInsertUtilityUpgrade(0, new ItemStack(utility)));
        assertFalse(source.canInsertStorageUpgrade(0, new ItemStack(utility)));
        source.getStorageUpgrades().setStackInSlot(0, new ItemStack(storage));
        source.getUtilityUpgrades().setStackInSlot(0, new ItemStack(utility));
        assertEquals(20.0D, source.getStorageMultiplier(8.0D), 0.0D);
        assertTrue(source.isCreative());
        assertTrue(source.voidsOverflow());

        NBTTagCompound serialized = source.saveTileToNBT();
        assertTrue(serialized.hasKey("StorageUpgrades"));
        assertTrue(serialized.hasKey("UtilityUpgrades"));
        assertFalse(serialized.hasKey("IsCreative"));
        assertFalse(serialized.hasKey("IsVoid"));

        WoodDrawerTile restored = new WoodDrawerTile();
        restored.loadTileFromNBT(serialized);

        assertSame(storage, restored.getStorageUpgrades().getStackInSlot(0).getItem());
        assertSame(utility, restored.getUtilityUpgrades().getStackInSlot(0).getItem());
        assertEquals(20.0D, restored.getStorageMultiplier(8.0D), 0.0D);
        assertTrue(restored.isCreative());
        assertTrue(restored.voidsOverflow());
    }

    private static <T extends Item> T register(String path, T item) {
        int id = NEXT_ITEM_ID.getAndIncrement();
        item.setRegistryName(new ResourceLocation(
                "functionalstoragelegacy_upgrade_test", path + id));
        @SuppressWarnings("unchecked")
        ForgeRegistry<Item> registry = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
        registry.unfreeze();
        try {
            registry.register(item);
        } finally {
            registry.freeze();
        }
        return item;
    }

    private static final class TestUpgradeItem extends Item implements DrawerUpgradeBehavior {
        private final SlotType slotType;
        private final StorageFeature feature;
        private final UpgradeModifier modifier;

        private TestUpgradeItem(SlotType slotType, StorageFeature feature, UpgradeModifier modifier) {
            this.slotType = slotType;
            this.feature = feature;
            this.modifier = modifier;
        }

        @Override
        public SlotType getSlotType() {
            return slotType;
        }

        @Override
        public void applyUpgrade(@Nonnull ItemStack stack, @Nonnull UpgradeState.Builder builder) {
            builder.addFeature(feature);
            if (modifier != null) {
                builder.addModifier(UpgradeAttribute.ITEM_CAPACITY, modifier);
            }
        }
    }
}
