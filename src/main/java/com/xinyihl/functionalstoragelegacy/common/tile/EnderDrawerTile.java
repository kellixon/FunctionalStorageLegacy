package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.common.inventory.EnderInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.StorageIdentityProvider;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import com.xinyihl.functionalstoragelegacy.common.world.EnderSavedData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * TileEntity for ender drawers.
 * Shares inventory across all ender drawers with the same frequency.
 * Uses EnderSavedData for cross-dimensional persistence.
 */
public class EnderDrawerTile extends ControllableDrawerTile {

    private static final HashMap<UUID, Long> INTERACTION_LOGGER = new HashMap<>();

    private String frequency;
    private EnderInventoryHandler storage;
    private final IBigItemHandler itemHandlerFacade = new ForwardingItemHandler();
    private int removeTicks = 0;

    public EnderDrawerTile() {
        super();
        this.frequency = UUID.randomUUID().toString();
    }

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote) {
            removeTicks = Math.max(removeTicks - 1, 0);

            if (world.getTotalWorldTime() % 10 == 0 && storage != null) {
                if (storage.isLocked() != isLocked()) {
                    super.setLocked(storage.isLocked());
                }
            }

            if (storage != null && storage.needUpdate()) {
                sendUpdatePacket();
                storage.setUpdate();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote && storage == null) {
            replaceStorage(EnderSavedData.getInstance(world).getFrequency(this.frequency));
        }
    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing,
                                   float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        if (super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot)) {
            return true;
        }

        if (slot != -1 && !world.isRemote && storage != null) {
            boolean changed = false;
            // Insert held item
            if (!heldStack.isEmpty()) {
                ItemStack result = storage.insertItem(0, heldStack, true);
                if (result.getCount() != heldStack.getCount()) {
                    player.setHeldItem(hand, storage.insertItem(0, heldStack, false));
                    changed = true;
                }
            }

            // Double-click fast insert
            if (System.currentTimeMillis() - INTERACTION_LOGGER.getOrDefault(player.getUniqueID(), System.currentTimeMillis()) < 300 && (isLocked() || !storage.getStackInSlot(slot).isEmpty())) {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack invStack = player.inventory.getStackInSlot(i);
                    if (!invStack.isEmpty()) {
                        ItemStack testResult = storage.insertItem(0, invStack, true);
                        if (testResult.getCount() != invStack.getCount()) {
                            ItemStack leftover = storage.insertItem(0, invStack.copy(), false);
                            player.inventory.setInventorySlotContents(i, leftover);
                            changed = true;
                        }
                    }
                }
            }

            INTERACTION_LOGGER.put(player.getUniqueID(), System.currentTimeMillis());

            if (changed) {
                sendUpdatePacket();
            }
        }

        return true;
    }

    @Override
    public void onClicked(EntityPlayer player, int slot) {
        if (!world.isRemote && slot != -1 && removeTicks == 0 && storage != null) {
            removeTicks = 3;
            int amount = player.isSneaking() ? storage.getStackInSlot(0).getMaxStackSize() : 1;
            ItemStack extracted = storage.extractItem(0, amount, false);
            if (!extracted.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, extracted);
                sendUpdatePacket();
            }
        }
    }

    @Override
    public void setLocked(boolean locked) {
        super.setLocked(locked);
        if (world != null && !world.isRemote) {
            EnderSavedData.getInstance(world).getFrequency(frequency).setLocked(locked);
        }
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        nbt.setString("Frequency", frequency);
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        if (nbt.hasKey("Frequency")) {
            String oldFreq = this.frequency;
            this.frequency = nbt.getString("Frequency");
            if (world != null && !world.isRemote && !this.frequency.equals(oldFreq)) {
                replaceStorage(EnderSavedData.getInstance(world).getFrequency(this.frequency));
            }
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setString("Frequency", frequency);
        return compound;
    }

    @Nonnull
    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        writeSyncedInventory(tag);
        return tag;
    }

    void writeSyncedInventory(NBTTagCompound tag) {
        if (storage != null) {
            tag.setTag("EnderInventory", storage.serializeNBTFull());
        }
    }

    @Override
    public void handleUpdateTag(@Nonnull NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readSyncedInventory(tag);
    }

    @Override
    public void onDataPacket(@Nonnull NetworkManager net, SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        readSyncedInventory(pkt.getNbtCompound());
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        if (compound.hasKey("Frequency")) {
            this.frequency = compound.getString("Frequency");
        }
        super.readFromNBT(compound);
    }

    void readSyncedInventory(NBTTagCompound nbt) {
        if (nbt.hasKey("EnderInventory")) {
            if (this.storage == null) {
                replaceStorage(new EnderInventoryHandler() {
                });
            }
            this.storage.deserializeNBTFull(nbt.getCompoundTag("EnderInventory"));
        }
    }

    @Override
    public IBigItemHandler getItemHandler() {
        return itemHandlerFacade;
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
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandlerFacade);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public int getStorageUpgradesAmount() {
        return 0; // No storage upgrades for ender drawers
    }

    @Override
    public boolean isEverythingEmpty() {
        if (!super.isEverythingEmpty()) return false;
        if (storage != null) {
            for (int i = 0; i < storage.getSlots(); i++) {
                if (!storage.getStackInSlot(i).isEmpty()) return false;
            }
        }
        return true;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        if (frequency == null) return;
        this.frequency = frequency;
        if (world != null && !world.isRemote) {
            replaceStorage(EnderSavedData.getInstance(world).getFrequency(this.frequency));
            markDirty();
            sendUpdatePacket();
        }
    }

    void replaceStorage(@Nullable EnderInventoryHandler replacement) {
        if (storage == replacement) {
            return;
        }
        storage = replacement;
        invalidateAE2Accessor();
        requestControllerHandlerRefresh();
    }

    protected void requestControllerHandlerRefresh() {
        if (world == null || world.isRemote || controllerPos == null) {
            return;
        }
        TileEntity controller = world.getTileEntity(controllerPos);
        if (controller instanceof DrawerControllerTile) {
            ((DrawerControllerTile) controller).refreshHandlerMappings();
        }
    }

    private final class ForwardingItemHandler
            implements IBigItemHandler, StorageIdentityProvider {

        @Override
        public int getSlotCount() {
            EnderInventoryHandler target = storage;
            return target == null ? 1 : target.getSlotCount();
        }

        @Nonnull
        @Override
        public BigItemStack getSlotSnapshot(int slot) {
            EnderInventoryHandler target = storage;
            return target == null
                    ? BigItemStack.empty() : target.getSlotSnapshot(slot);
        }

        @Override
        public long getSlotCapacity(int slot) {
            EnderInventoryHandler target = storage;
            return target == null ? 0L : target.getSlotCapacity(slot);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> insertIntoSlot(
                int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
            Objects.requireNonNull(action, "action");
            EnderInventoryHandler target = storage;
            if (target != null) {
                return target.insertIntoSlot(slot, request, action);
            }
            long requested = request == null || request.isEmpty()
                    ? 0L : request.getAmount();
            return new TransferResult<>(requested, BigItemStack.empty(), action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> extractFromSlot(
                int slot, long amount, @Nonnull StorageAction action) {
            Objects.requireNonNull(action, "action");
            EnderInventoryHandler target = storage;
            if (target != null) {
                return target.extractFromSlot(slot, amount, action);
            }
            long requested = Math.max(0L, amount);
            return new TransferResult<>(requested, BigItemStack.empty(), action);
        }

        @Override
        public boolean isLocked() {
            EnderInventoryHandler target = storage;
            return target != null && target.isLocked();
        }

        @Override
        public boolean voidsOverflow() {
            EnderInventoryHandler target = storage;
            return target != null && target.voidsOverflow();
        }

        @Override
        public boolean isCreative() {
            EnderInventoryHandler target = storage;
            return target != null && target.isCreative();
        }

        @Nonnull
        @Override
        public Object getStorageIdentity() {
            EnderInventoryHandler target = storage;
            return target == null ? this : target;
        }
    }
}
