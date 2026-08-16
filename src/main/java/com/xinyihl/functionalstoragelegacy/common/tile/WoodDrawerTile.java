package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerWoodType;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
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
import java.util.UUID;

/**
 * TileEntity for standard wooden drawer blocks.
 * Holds a BigInventoryHandler for large-capacity item storage.
 */
public class WoodDrawerTile extends ControllableDrawerTile {

    private static final HashMap<UUID, Long> INTERACTION_LOGGER = new HashMap<>();

    private BigItemHandler handler;
    private DrawerLayout drawerLayout;
    private DrawerWoodType woodType;
    private int removeTicks = 0;

    public WoodDrawerTile() {
        this(DrawerLayout.X_1, DrawerWoodType.OAK);
    }

    public WoodDrawerTile(DrawerLayout drawerLayout, DrawerWoodType woodType) {
        super();
        this.drawerLayout = drawerLayout;
        this.woodType = woodType;
        this.handler = createHandler();
        bindStorageHandler(this.handler, () -> this.handler.onChange(StorageChange.reset()));
    }

    private static long capacityFor(double multiplier, ItemStack template) {
        if (Double.isNaN(multiplier) || multiplier <= 0D) {
            return 0L;
        }
        double capacity = multiplier * Math.max(0, template.getMaxStackSize());
        if (Double.isInfinite(capacity) || capacity >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(capacity);
    }

    private BigItemHandler createHandler() {
        return new BigItemHandler(drawerLayout.getSlotCount()) {
            @Override
            public double getMultiplier() {
                return WoodDrawerTile.this.getStorageMultiplier(WoodDrawerTile.this.drawerLayout.getBaseCapacity());
            }

            @Override
            protected boolean allowsEquivalentItems() {
                return WoodDrawerTile.this.hasOreDictionaryUpgrade();
            }

            @Override
            protected boolean hasMaxStorage() {
                return WoodDrawerTile.this.hasMaxStorageUpgrade();
            }

            @Override
            public boolean voidsOverflow() {
                return WoodDrawerTile.this.voidsOverflow();
            }

            @Override
            public boolean isLocked() {
                return WoodDrawerTile.this.isLocked();
            }

            @Override
            public boolean isCreative() {
                return WoodDrawerTile.this.isCreative();
            }
        };
    }

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote) {
            removeTicks = Math.max(removeTicks - 1, 0);
        }
    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        // Let parent handle upgrades and tools
        if (super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot)) {
            return true;
        }

        if (slot != -1 && !world.isRemote) {
            // Set the type filter if empty slot and holding item
            if (!heldStack.isEmpty() && isLocked() && slot < handler.getStorageCount() && !handler.getSnapshot(slot).hasTemplate()) {
                handler.setSlotFilter(slot, heldStack);
            }

            // Try to insert held item
            if (!heldStack.isEmpty()) {
                ItemStack result = insertIntoPhysicalSlot(slot, heldStack, true);
                if (result.getCount() != heldStack.getCount()) {
                    player.setHeldItem(hand, insertIntoPhysicalSlot(slot, heldStack, false));
                    return true;
                }
            }

            // Double-click fast insert from inventory
            if (System.currentTimeMillis() - INTERACTION_LOGGER.getOrDefault(player.getUniqueID(), System.currentTimeMillis()) < 300 && (isLocked() || handler.getSnapshot(slot).hasTemplate())) {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack invStack = player.inventory.getStackInSlot(i);
                    if (!invStack.isEmpty()) {
                        ItemStack testResult = insertIntoPhysicalSlot(slot, invStack, true);
                        if (testResult.getCount() != invStack.getCount()) {
                            ItemStack leftover = insertIntoPhysicalSlot(slot, invStack.copy(), false);
                            player.inventory.setInventorySlotContents(i, leftover);
                        }
                    }
                }
            }

            INTERACTION_LOGGER.put(player.getUniqueID(), System.currentTimeMillis());
        }

        return false;
    }

    @Override
    public void onClicked(EntityPlayer player, int slot) {
        if (!world.isRemote && slot != -1 && removeTicks == 0) {
            removeTicks = 3;
            BigItemStack snapshot = handler.getSnapshot(slot);
            int amount = player.isSneaking() && snapshot.hasTemplate() ? snapshot.getTemplate().getMaxStackSize() : 1;
            ItemStack extracted = extractFromPhysicalSlot(slot, amount, false);
            if (!extracted.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, extracted);
            }
        }
    }

    private ItemStack insertIntoPhysicalSlot(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        BigItemStack request = new BigItemStack(stack, stack.getCount());
        TransferResult<BigItemStack, ItemStorageKey> result = handler.insert(slot, request, StorageAction.fromSimulation(simulate));
        long remaining = result.getRemainingAmount();
        if (remaining <= 0L) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount((int) Math.min(remaining, stack.getCount()));
        return remainder;
    }

    private ItemStack extractFromPhysicalSlot(int slot, int amount, boolean simulate) {
        BigItemStack snapshot = handler.getSnapshot(slot);
        if (!snapshot.hasTemplate() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int requested = Math.min(amount, snapshot.getTemplate().getMaxStackSize());
        TransferResult<BigItemStack, ItemStorageKey> result = handler.extract(slot, requested, StorageAction.fromSimulation(simulate));
        return result.getProcessed().isEmpty() ? ItemStack.EMPTY : result.getProcessed().toItemStack();
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        writeStorage(nbt);
        nbt.setString("DrawerLayout", drawerLayout.getId());
        nbt.setString("DrawerWood", woodType.getId());
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        if (nbt.hasKey("DrawerLayout")) {
            drawerLayout = DrawerLayout.fromId(nbt.getString("DrawerLayout"));
        }
        if (nbt.hasKey("DrawerWood")) {
            woodType = DrawerWoodType.fromId(nbt.getString("DrawerWood"));
        }
        handler = createHandler();
        handler.deserializeNBT(nbt);
        finishStorageRead(handler, () -> handler.onChange(StorageChange.reset()));
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        writeStorage(compound);
        compound.setString("DrawerLayout", drawerLayout.getId());
        compound.setString("DrawerWood", woodType.getId());
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        beginStorageRead();
        if (compound.hasKey("DrawerLayout")) {
            drawerLayout = DrawerLayout.fromId(compound.getString("DrawerLayout"));
        }
        if (compound.hasKey("DrawerWood")) {
            woodType = DrawerWoodType.fromId(compound.getString("DrawerWood"));
        }
        super.readFromNBT(compound);
        handler = createHandler();
        handler.deserializeNBT(compound);
        finishStorageRead(handler, () -> handler.onChange(StorageChange.reset()));
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
        for (int i = 0; i < handler.getStorageCount(); i++) {
            BigItemStack snapshot = handler.getSnapshot(i);
            if (snapshot.hasTemplate()) return false;
        }
        return true;
    }

    @Override
    protected int calculateRedstoneSignal() {
        int active = 0;
        double fillRatio = 0D;

        for (int i = 0; i < handler.getStorageCount(); i++) {
            active++;
            long capacity = handler.getCapacity(i);
            if (capacity <= 0L)
                continue;

            BigItemStack snapshot = handler.getSnapshot(i);
            double slotRatio = snapshot.getAmount() >= capacity
                    ? 1D
                    : (double) snapshot.getAmount() / (double) capacity;
            fillRatio += slotRatio;
        }

        if (active == 0)
            return 0;

        return calculateRedstoneSignalForRatio(fillRatio / active);
    }

    @Override
    protected boolean canApplyUpgradeState(UpgradeState state) {
        if (state.hasFeature(StorageFeature.CREATIVE) || state.hasFeature(StorageFeature.MAX_CAPACITY)) {
            return true;
        }
        double calculated = state.calculate(UpgradeAttribute.ITEM_CAPACITY, drawerLayout.getBaseCapacity());
        for (int i = 0; i < handler.getStorageCount(); i++) {
            BigItemStack snapshot = handler.getSnapshot(i);
            if (snapshot.getAmount() <= 0L) {
                continue;
            }
            long capacity = capacityFor(calculated, snapshot.getTemplate());
            if (snapshot.getAmount() > capacity) {
                return false;
            }
        }
        return true;
    }

    private void writeStorage(NBTTagCompound nbt) {
        nbt.setTag("StorageV2", handler.serializeNBT().getCompoundTag("StorageV2"));
    }

    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    public DrawerWoodType getWoodType() {
        return woodType;
    }
}
