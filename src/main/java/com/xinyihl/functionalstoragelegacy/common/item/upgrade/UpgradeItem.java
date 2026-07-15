package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base class for all drawer upgrade items.
 */
public abstract class UpgradeItem extends Item implements DrawerUpgradeBehavior {

    private final SlotType slotType;
    private final Set<Item> incompatibleUpgrades = new LinkedHashSet<>();
    private final Set<StorageFeature> features = new LinkedHashSet<>();

    protected UpgradeItem(SlotType slotType, StorageFeature... features) {
        this.slotType = slotType;
        this.features.addAll(Arrays.asList(features));
        this.setCreativeTab(RegistrationHandler.CREATIVE_TAB);
    }

    @Override
    public SlotType getSlotType() {
        return slotType;
    }

    public void incompatibleWith(Item... upgrades) {
        incompatibleUpgrades.addAll(Arrays.asList(upgrades));
    }

    @Override
    public void applyUpgrade(@Nonnull ItemStack stack, @Nonnull UpgradeState.Builder builder) {
        for (StorageFeature feature : features) {
            builder.addFeature(feature);
        }
    }

    @Override
    public boolean conflictsWith(@Nonnull ItemStack stack, @Nonnull ItemStack otherStack) {
        return !otherStack.isEmpty() && incompatibleUpgrades.contains(otherStack.getItem());
    }

    /** Returns the configured directional conflict declarations for registration diagnostics. */
    public Set<Item> getIncompatibleUpgrades() {
        return Collections.unmodifiableSet(incompatibleUpgrades);
    }

}
