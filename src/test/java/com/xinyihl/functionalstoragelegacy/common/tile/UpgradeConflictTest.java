package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.upgrade.IStorageUpgrade;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertFalse;

public class UpgradeConflictTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void installedUpgradeCanRejectCandidateDirectionally() {
        TestUpgrade candidate = new TestUpgrade();
        TestUpgrade installed = new TestUpgrade();
        installed.rejected = candidate;
        WoodDrawerTile tile = new WoodDrawerTile();
        tile.getStorageUpgrades().setStackInSlot(0, new ItemStack(installed));

        assertFalse(tile.canInsertStorageUpgrade(1, new ItemStack(candidate)));
    }

    @Test
    public void candidateUpgradeCanRejectInstalledDirectionally() {
        TestUpgrade candidate = new TestUpgrade();
        TestUpgrade installed = new TestUpgrade();
        candidate.rejected = installed;
        WoodDrawerTile tile = new WoodDrawerTile();
        tile.getStorageUpgrades().setStackInSlot(0, new ItemStack(installed));

        assertFalse(tile.canInsertStorageUpgrade(1, new ItemStack(candidate)));
    }

    private static final class TestUpgrade extends Item implements IStorageUpgrade {
        private Item rejected;

        @Override
        public void applyUpgrade(@Nonnull ItemStack stack, @Nonnull UpgradeState.Builder builder) {
        }

        @Override
        public boolean conflictsWith(@Nonnull ItemStack stack, @Nonnull ItemStack otherStack) {
            return !otherStack.isEmpty() && otherStack.getItem() == rejected;
        }
    }
}
