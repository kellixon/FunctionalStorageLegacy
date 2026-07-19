package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.common.inventory.EnderInventoryHandler;
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
import java.util.function.Consumer;

/**
 * TileEntity for an inventory shared by all drawers on one frequency.
 */
public class EnderDrawerTile extends ControllableDrawerTile {

    private static final HashMap<UUID, Long> INTERACTION_LOGGER = new HashMap<>();
    private final ForwardingItemHandler itemHandlerFacade = new ForwardingItemHandler();
    private String frequency;
    private EnderInventoryHandler storage;
    private int removeTicks;

    public EnderDrawerTile() {
        super();
        this.frequency = UUID.randomUUID().toString();
        bindStorageHandler(itemHandlerFacade, itemHandlerFacade::emitReset);
    }

    private static String normalizeFrequency(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote) {
            removeTicks = Math.max(removeTicks - 1, 0);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        itemHandlerFacade.rebindTarget();
        if (world != null && !world.isRemote && storage == null) {
            replaceStorage(EnderSavedData.getInstance(world).getFrequency(frequency));
        }
    }

    @Override
    public void invalidate() {
        itemHandlerFacade.closeTarget();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        itemHandlerFacade.closeTarget();
        super.onChunkUnload();
    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        if (super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot)) {
            return true;
        }

        if (slot != -1 && world != null && !world.isRemote && storage != null) {
            boolean changed = false;
            if (!heldStack.isEmpty()) {
                BigItemStack request = new BigItemStack(heldStack, heldStack.getCount());
                TransferResult<BigItemStack, ItemStorageKey> simulated = storage.insert(0, request, StorageAction.SIMULATE);
                if (simulated.getProcessedAmount() > 0L) {
                    TransferResult<BigItemStack, ItemStorageKey> result = storage.insert(0, request, StorageAction.EXECUTE);
                    long processed = Math.min(heldStack.getCount(), Math.max(0L, result.getProcessedAmount()));
                    if (processed > 0L) {
                        ItemStack remaining = heldStack.copy();
                        remaining.shrink((int) processed);
                        player.setHeldItem(hand, remaining);
                        changed = true;
                    }
                }
            }

            long lastInteraction = INTERACTION_LOGGER.getOrDefault(player.getUniqueID(), System.currentTimeMillis());
            if (System.currentTimeMillis() - lastInteraction < 300 && (isLocked() || !storage.getSnapshot(0).isEmpty())) {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack invStack = player.inventory.getStackInSlot(i);
                    if (invStack.isEmpty()) {
                        continue;
                    }
                    BigItemStack request = new BigItemStack(invStack, invStack.getCount());
                    TransferResult<BigItemStack, ItemStorageKey> simulated = storage.insert(0, request, StorageAction.SIMULATE);
                    if (simulated.getProcessedAmount() > 0L) {
                        TransferResult<BigItemStack, ItemStorageKey> result = storage.insert(0, request, StorageAction.EXECUTE);
                        long processed = Math.min(invStack.getCount(), Math.max(0L, result.getProcessedAmount()));
                        if (processed > 0L) {
                            ItemStack leftover = invStack.copy();
                            leftover.shrink((int) processed);
                            player.inventory.setInventorySlotContents(i, leftover);
                            changed = true;
                        }
                    }
                }
            }
            INTERACTION_LOGGER.put(player.getUniqueID(), System.currentTimeMillis());
            if (changed) {
                requestUpdatePacket();
            }
        }

        return false;
    }

    @Override
    public void onClicked(EntityPlayer player, int slot) {
        if (world == null || world.isRemote || slot == -1 || removeTicks != 0 || storage == null) {
            return;
        }
        removeTicks = 3;
        BigItemStack snapshot = storage.getSnapshot(0);
        int amount = player.isSneaking() && snapshot.hasTemplate() ? snapshot.getTemplate().getMaxStackSize() : 1;
        TransferResult<BigItemStack, ItemStorageKey> result = storage.extract(0, amount, StorageAction.EXECUTE);
        if (result.getProcessedAmount() > 0L && !result.getProcessed().isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, result.getProcessed().toItemStack());
        }
    }

    @Override
    public boolean isLocked() {
        EnderInventoryHandler target = storage;
        return target == null ? super.isLocked() : target.isLocked();
    }

    /**
     * Updates the shared lock exactly once; all peers receive the handler RESET.
     */
    @Override
    public void setLocked(boolean locked) {
        if (this.isLocked() == locked && (storage == null || storage.isLocked() == locked)) {
            return;
        }
        this.isLocked = locked;
        this.needsUpgradeCache = true;
        EnderInventoryHandler target = storage;
        if (target == null && world != null && !world.isRemote) {
            target = EnderSavedData.getInstance(world).getFrequency(frequency);
            replaceStorage(target);
        }
        if (target != null && (world == null || !world.isRemote)) {
            target.setLocked(locked);
        } else {
            markDirty();
            requestUpdatePacket();
        }
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        nbt.setString("Frequency", frequency);
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        if (nbt.hasKey("Frequency")) {
            frequency = normalizeFrequency(nbt.getString("Frequency"));
        }
        boolean replaced = false;
        if (world != null && !world.isRemote) {
            replaced = replaceStorage(EnderSavedData.getInstance(world).getFrequency(frequency));
        }
        finishStorageRead(itemHandlerFacade, null);
        if (world != null && !world.isRemote && !replaced) {
            itemHandlerFacade.emitReset();
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        if (storage != null) {
            this.isLocked = storage.isLocked();
        }
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
        beginStorageRead();
        if (compound.hasKey("Frequency")) {
            frequency = normalizeFrequency(compound.getString("Frequency"));
        }
        super.readFromNBT(compound);
        // Server storage is loaded from EnderSavedData in readCustomData/item load;
        // client packets carry the full shared inventory separately.
        boolean replaced = false;
        if (world != null && !world.isRemote) {
            replaced = replaceStorage(EnderSavedData.getInstance(world).getFrequency(frequency));
        }
        finishStorageRead(itemHandlerFacade, null);
        if (world != null && !world.isRemote && !replaced) {
            itemHandlerFacade.emitReset();
        }
    }

    void readSyncedInventory(NBTTagCompound nbt) {
        if (!nbt.hasKey("EnderInventory")) {
            return;
        }
        EnderInventoryHandler replacement = new EnderInventoryHandler() {
        };
        replacement.deserializeNBTFull(nbt.getCompoundTag("EnderInventory"));
        String syncedFrequency = replacement.getFrequency();
        if (syncedFrequency != null && !syncedFrequency.isEmpty()) {
            frequency = syncedFrequency;
        }
        replaceStorage(replacement);
    }

    @Override
    public IBigItemHandler getItemHandler() {
        return itemHandlerFacade;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
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
        return 0;
    }

    @Override
    public boolean isEverythingEmpty() {
        if (!super.isEverythingEmpty()) {
            return false;
        }
        for (int i = 0; i < itemHandlerFacade.getStorageCount(); i++) {
            if (itemHandlerFacade.getSnapshot(i).hasTemplate()) {
                return false;
            }
        }
        return true;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String nextFrequency) {
        nextFrequency = normalizeFrequency(nextFrequency);
        if (Objects.equals(this.frequency, nextFrequency)) {
            return;
        }
        this.frequency = nextFrequency;
        if (world != null && !world.isRemote) {
            replaceStorage(EnderSavedData.getInstance(world).getFrequency(nextFrequency));
        } else {
            markDirty();
            requestUpdatePacket();
        }
    }

    /**
     * Replaces the shared target in the required order: close old target
     * listener, bind target, subscribe, facade RESET, accessor invalidation.
     */
    boolean replaceStorage(@Nullable EnderInventoryHandler replacement) {
        if (storage == replacement) {
            return false;
        }
        itemHandlerFacade.closeTarget();
        storage = replacement;
        if (replacement != null) {
            this.isLocked = replacement.isLocked();
            this.needsUpgradeCache = true;
        }
        itemHandlerFacade.bindTarget(replacement);
        itemHandlerFacade.emitReset();
        invalidateAE2Accessor();
        markDirty();
        requestUpdatePacket();
        requestControllerHandlerRefresh();
        return true;
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

    private final class ForwardingItemHandler implements IBigItemHandler {

        private final StorageChangeDispatcher<BigItemStack, ItemStorageKey> dispatcher = new StorageChangeDispatcher<>();
        private StorageSubscription targetSubscription = StorageSubscription.CLOSED;

        @Override
        public int getStorageCount() {
            EnderInventoryHandler target = storage;
            return target == null ? 1 : target.getStorageCount();
        }

        @Nonnull
        @Override
        public BigItemStack getSnapshot(int slot) {
            EnderInventoryHandler target = storage;
            return target == null ? BigItemStack.empty() : target.getSnapshot(slot);
        }

        @Override
        public long getCapacity(int slot) {
            EnderInventoryHandler target = storage;
            return target == null ? 0L : target.getCapacity(slot);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack, ItemStorageKey> insert(int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
            Objects.requireNonNull(action, "action");
            EnderInventoryHandler target = storage;
            if (target != null) {
                return target.insert(slot, request, action);
            }
            long requested = request.isEmpty() ? 0L : request.getAmount();
            return new TransferResult<>(requested, BigItemStack.empty(), action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack, ItemStorageKey> extract(int slot, long amount, @Nonnull StorageAction action) {
            Objects.requireNonNull(action, "action");
            EnderInventoryHandler target = storage;
            if (target != null) {
                return target.extract(slot, amount, action);
            }
            return new TransferResult<>(Math.max(0L, amount), BigItemStack.empty(), action);
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

        @Override
        public double getMultiplier() {
            EnderInventoryHandler target = storage;
            return target == null ? 1D : target.getMultiplier();
        }

        @Nonnull
        @Override
        public Object getStorageIdentity() {
            EnderInventoryHandler target = storage;
            return target == null ? this : target;
        }

        @Override
        public void onChange(@Nonnull StorageChange<BigItemStack, ItemStorageKey> change) {
            dispatcher.dispatch(change);
        }

        @Nonnull
        @Override
        public StorageSubscription subscribe(@Nonnull Consumer<? super StorageChange<BigItemStack, ItemStorageKey>> listener) {
            return dispatcher.subscribe(listener);
        }

        private void bindTarget(@Nullable EnderInventoryHandler target) {
            closeTarget();
            if (target != null) {
                targetSubscription = target.subscribe(dispatcher::dispatch);
            }
        }

        private void rebindTarget() {
            EnderInventoryHandler target = storage;
            if (target != null && (targetSubscription == null || targetSubscription.isClosed())) {
                targetSubscription = target.subscribe(dispatcher::dispatch);
            }
        }

        private void closeTarget() {
            StorageSubscription current = targetSubscription;
            targetSubscription = StorageSubscription.CLOSED;
            if (current != null) {
                current.close();
            }
        }

        private void emitReset() {
            dispatcher.dispatch(StorageChange.reset());
        }
    }
}
