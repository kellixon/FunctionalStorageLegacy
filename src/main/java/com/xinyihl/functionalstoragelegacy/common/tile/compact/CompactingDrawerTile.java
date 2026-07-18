package com.xinyihl.functionalstoragelegacy.common.tile.compact;

import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.inventory.CompactingInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.util.CompactingUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * TileEntity for compacting drawers (3-slot compression storage).
 * Handles nugget <-> ingot <-> block style compaction.
 */
public class CompactingDrawerTile extends ControllableDrawerTile {

    private static final HashMap<UUID, Long> INTERACTION_LOGGER = new HashMap<>();

    private CompactingInventoryHandler handler;
    private boolean hasCheckedRecipes;
    private int removeTicks = 0;

    public CompactingDrawerTile() {
        this(3);
    }

    public CompactingDrawerTile(int slots) {
        super();
        this.handler = createHandler(slots);
        this.hasCheckedRecipes = false;
        bindStorageHandler(this.handler, () -> this.handler.onChange(StorageChange.reset()));
    }

    protected CompactingInventoryHandler createHandler(int slots) {
        return new CompactingInventoryHandler(slots) {
            @Override
            public double getMultiplier() {
                return CompactingDrawerTile.this.getStorageMultiplier(8.0D);
            }

            @Override
            protected boolean allowsEquivalentItems() {
                return CompactingDrawerTile.this.hasOreDictionaryUpgrade();
            }

            @Override
            protected boolean hasMaxStorage() {
                return CompactingDrawerTile.this.hasMaxStorageUpgrade();
            }

            @Override
            public boolean voidsOverflow() {
                return CompactingDrawerTile.this.voidsOverflow();
            }

            @Override
            public boolean isCreative() {
                return CompactingDrawerTile.this.isCreative();
            }

            @Override
            public boolean isLocked() {
                return CompactingDrawerTile.this.isLocked();
            }
        };
    }

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote) {
            removeTicks = Math.max(removeTicks - 1, 0);

            // Check recipes on first tick
            if (!hasCheckedRecipes) {
                if (handler.isConfigured() && !getParentStack().isEmpty()) {
                    int anchorSlot = getFirstNonEmptySlot();
                    List<CompactingInventoryHandler.Tier> results = CompactingUtil.getCompactingResults(this.world, getParentStack(), getSlotCount(), anchorSlot);
                    if (!results.isEmpty()) {
                        applyCompactingResults(results);
                    }
                }
                hasCheckedRecipes = true;
            }
        }
    }

    /**
     * Get "parent" item stack - the item used to set up compaction.
     * Returns the base tier item if setup, empty otherwise.
     */
    private ItemStack getParentStack() {
        List<CompactingInventoryHandler.Tier> tiers = handler.getTiers();
        if (tiers.isEmpty()) return ItemStack.EMPTY;
        // Return the highest tier non-empty result
        for (CompactingInventoryHandler.Tier tier : tiers) {
            if (tier.hasTemplate()) return tier.getTemplate();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        if (super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot)) {
            return true;
        }

        if (slot != -1 && !world.isRemote) {
            // Setup compacting if not yet configured
            if (!handler.isConfigured() && !heldStack.isEmpty()) {
                ItemStack template = heldStack.copy();
                template.setCount(1);
                List<CompactingInventoryHandler.Tier> results = CompactingUtil.getCompactingResults(this.world, template, getSlotCount(), slot);
                if (!results.isEmpty()) {
                    applyCompactingResults(results);
                    markDirty();
                    requestUpdatePacket();
                }
            }

            // Insert items
            if (!heldStack.isEmpty() && handler.isConfigured()) {
                ItemStack result = handler.insertItem(slot, heldStack, true);
                if (result.getCount() != heldStack.getCount()) {
                    player.setHeldItem(hand, handler.insertItem(slot, heldStack, false));
                    return true;
                }
            }

            // Double-click fast insert
            if (System.currentTimeMillis() - INTERACTION_LOGGER.getOrDefault(player.getUniqueID(), System.currentTimeMillis()) < 300 && handler.canDoubleClickSlot(slot)) {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack invStack = player.inventory.getStackInSlot(i);
                    if (!invStack.isEmpty()) {
                        ItemStack testResult = handler.insertItem(slot, invStack, true);
                        if (testResult.getCount() != invStack.getCount()) {
                            ItemStack leftover = handler.insertItem(slot, invStack.copy(), false);
                            player.inventory.setInventorySlotContents(i, leftover);
                        }
                    }
                }
            }

            INTERACTION_LOGGER.put(player.getUniqueID(), System.currentTimeMillis());
        }

        return true;
    }

    @Override
    public void onClicked(EntityPlayer player, int slot) {
        if (!world.isRemote && slot != -1 && removeTicks == 0) {
            removeTicks = 3;
            int amount = player.isSneaking() ? handler.getStackInSlot(slot).getMaxStackSize() : 1;
            ItemStack extracted = handler.extractItem(slot, amount, false);
            if (!extracted.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, extracted);
            }
        }
    }

    private int getFirstNonEmptySlot() {
        List<CompactingInventoryHandler.Tier> tiers = handler.getTiers();
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).hasTemplate()) {
                return i;
            }
        }
        return 0;
    }

    private void applyCompactingResults(List<CompactingInventoryHandler.Tier> compactingTiers) {
        handler.configureTiers(compactingTiers);
    }

    protected int getSlotCount() {
        return 3;
    }

    @Override
    protected boolean canApplyUpgradeState(UpgradeState state) {
        if (state.hasFeature(StorageFeature.CREATIVE) || state.hasFeature(StorageFeature.MAX_CAPACITY)) {
            return true;
        }
        double calculated = state.calculate(UpgradeAttribute.ITEM_CAPACITY, 8.0D);
        return handler.getStoredBaseAmount() <= handler.getTotalBaseCapacity(calculated);
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        writeStorage(nbt);
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        handler = createHandler(getSlotCount());
        handler.deserializeNBT(nbt);
        finishStorageRead(handler, () -> handler.onChange(StorageChange.reset()));
        hasCheckedRecipes = false;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        writeStorage(compound);
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        beginStorageRead();
        super.readFromNBT(compound);
        handler = createHandler(getSlotCount());
        handler.deserializeNBT(compound);
        finishStorageRead(handler, () -> handler.onChange(StorageChange.reset()));
        hasCheckedRecipes = false;
    }

    @Override
    public IBigItemHandler getItemHandler() {
        return handler;
    }

    @Override
    protected void onLockStateChanged(boolean locked) {
        handler.applyLockConfiguration(locked);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean isEverythingEmpty() {
        if (!super.isEverythingEmpty()) return false;
        return handler.getStoredBaseAmount() == 0L && !handler.isConfigured();
    }

    @Override
    protected int calculateRedstoneSignal() {
        if (!handler.isConfigured()) return 0;
        long totalCapacity = handler.getTotalBaseCapacity();
        long totalStored = handler.getStoredBaseAmount();
        if (totalCapacity == 0) return 0;
        return (int) ((totalStored / (double) totalCapacity) * 15);
    }

    @Override
    public int getStorageUpgradesAmount() {
        return 3;
    }

    private void writeStorage(NBTTagCompound nbt) {
        nbt.setTag("StorageV2", handler.serializeNBT().getCompoundTag("StorageV2"));
    }
}
