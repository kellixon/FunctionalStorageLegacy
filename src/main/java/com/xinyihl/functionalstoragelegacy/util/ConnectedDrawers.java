package com.xinyihl.functionalstoragelegacy.util;

import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.ControllerExtensionTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Manages the set of drawers connected to a controller.
 * Uses BFS to find all connected drawers within range.
 */
public class ConnectedDrawers {

    private final List<Long> connectedDrawerPositions;
    private final List<Long> linkedExtensionPositions;
    private final List<IBigItemHandler> itemHandlers;
    private final List<IBigFluidHandler> fluidHandlers;
    private BlockPos controllerPos;
    private World world;

    public ConnectedDrawers() {
        this.connectedDrawerPositions = new ArrayList<>();
        this.linkedExtensionPositions = new ArrayList<>();
        this.itemHandlers = new ArrayList<>();
        this.fluidHandlers = new ArrayList<>();
    }

    public ConnectedDrawers(World world, TileEntity controller) {
        this();
        this.world = world;
        if (controller != null) {
            this.controllerPos = controller.getPos();
        }
    }

    public void setController(World world, BlockPos controllerPos) {
        this.world = world;
        this.controllerPos = controllerPos;
    }

    public void setLevel(World world) {
        this.world = world;
    }

    /**
     * Get the list of connected drawer positions as longs.
     */
    public List<Long> getConnectedDrawers() {
        return connectedDrawerPositions;
    }

    /**
     * Get connected positions as BlockPos list.
     */
    public List<BlockPos> getConnectedPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (Long l : connectedDrawerPositions) {
            positions.add(BlockPos.fromLong(l));
        }
        return positions;
    }

    public List<Long> getLinkedExtensionPositions() {
        return linkedExtensionPositions;
    }

    /**
     * Rebuild the connected drawers list using BFS from the controller position.
     */
    public boolean rebuild() {
        itemHandlers.clear();
        fluidHandlers.clear();

        if (world == null || controllerPos == null) return false;

        return rebuildWithResolver(new LoadedTileResolver() {
            @Override
            public boolean isLoaded(BlockPos pos) {
                return world.isBlockLoaded(pos);
            }

            @Override
            public TileEntity getTileEntity(BlockPos pos) {
                return world.getTileEntity(pos);
            }
        });
    }

    boolean rebuildWithResolver(LoadedTileResolver resolver) {
        itemHandlers.clear();
        fluidHandlers.clear();
        boolean topologyChanged = false;

        Iterator<Long> iterator = connectedDrawerPositions.iterator();
        while (iterator.hasNext()) {
            Long posLong = iterator.next();
            BlockPos pos = BlockPos.fromLong(posLong);
            if (!resolver.isLoaded(pos)) {
                continue;
            }
            TileEntity te = resolver.getTileEntity(pos);
            if (!isConnectableDrawer(te)) {
                iterator.remove();
                topologyChanged = true;
                continue;
            }
            IBigItemHandler itemHandler = ((ControllableDrawerTile) te).getItemHandler();
            if (itemHandler != null) {
                itemHandlers.add(itemHandler);
            }
            IBigFluidHandler fluidHandler = ((ControllableDrawerTile) te).getFluidHandler();
            if (fluidHandler != null) {
                fluidHandlers.add(fluidHandler);
            }
        }
        return topologyChanged;
    }

    interface LoadedTileResolver {
        boolean isLoaded(BlockPos pos);

        TileEntity getTileEntity(BlockPos pos);
    }

    /**
     * Scan from controller using BFS. Replaces any stored positions.
     */
    public void scan() {
        connectedDrawerPositions.clear();
        itemHandlers.clear();
        fluidHandlers.clear();

        if (world == null || controllerPos == null) return;

        int range = Configurations.GENERAL.drawerControllerLinkingRange;
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(controllerPos);
        visited.add(controllerPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos neighbor = current.offset(facing);

                if (visited.contains(neighbor)) continue;
                if (neighbor.distanceSq(controllerPos) > range * range) continue;

                visited.add(neighbor);

                TileEntity te = world.getTileEntity(neighbor);
                if (isConnectableDrawer(te)) {
                    connectedDrawerPositions.add(neighbor.toLong());

                    IBigItemHandler itemHandler = ((ControllableDrawerTile) te).getItemHandler();
                    if (itemHandler != null) {
                        itemHandlers.add(itemHandler);
                    }
                    IBigFluidHandler fluidHandler = ((ControllableDrawerTile) te).getFluidHandler();
                    if (fluidHandler != null) {
                        fluidHandlers.add(fluidHandler);
                    }

                    queue.add(neighbor);
                }
            }
        }
    }

    private boolean isConnectableDrawer(TileEntity te) {
        return te instanceof ControllableDrawerTile
                && !(te instanceof DrawerControllerTile)
                && !(te instanceof ControllerExtensionTile);
    }

    /**
     * Add a single drawer position.
     */
    public void addDrawer(BlockPos pos) {
        long posLong = pos.toLong();
        if (!connectedDrawerPositions.contains(posLong)) {
            connectedDrawerPositions.add(posLong);
        }
        rebuild();
    }

    /**
     * Remove a single drawer position.
     */
    public void removeDrawer(BlockPos pos) {
        connectedDrawerPositions.removeIf(l -> l == pos.toLong());
        rebuild();
    }

    public void addLinkedExtension(BlockPos pos) {
        long posLong = pos.toLong();
        if (!linkedExtensionPositions.contains(posLong)) {
            linkedExtensionPositions.add(posLong);
        }
    }

    public void removeLinkedExtension(BlockPos pos) {
        linkedExtensionPositions.removeIf(l -> l == pos.toLong());
    }

    public List<IBigItemHandler> getItemHandlers() {
        return itemHandlers;
    }

    public List<IBigFluidHandler> getFluidHandlers() {
        return fluidHandlers;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Long posLong : connectedDrawerPositions) {
            list.appendTag(new NBTTagLong(posLong));
        }
        nbt.setTag("Positions", list);
        NBTTagList linkedExtensions = new NBTTagList();
        for (Long posLong : linkedExtensionPositions) {
            linkedExtensions.appendTag(new NBTTagLong(posLong));
        }
        nbt.setTag("LinkedExtensions", linkedExtensions);
        if (controllerPos != null) {
            nbt.setLong("ControllerPos", controllerPos.toLong());
        }
        return nbt;
    }

    public void deserializeNBT(NBTTagCompound nbt) {
        connectedDrawerPositions.clear();
        linkedExtensionPositions.clear();
        if (nbt.hasKey("Positions")) {
            NBTTagList list = nbt.getTagList("Positions", Constants.NBT.TAG_LONG);
            for (int i = 0; i < list.tagCount(); i++) {
                connectedDrawerPositions.add(((NBTTagLong) list.get(i)).getLong());
            }
        }
        if (nbt.hasKey("LinkedExtensions")) {
            NBTTagList list = nbt.getTagList("LinkedExtensions", Constants.NBT.TAG_LONG);
            for (int i = 0; i < list.tagCount(); i++) {
                linkedExtensionPositions.add(((NBTTagLong) list.get(i)).getLong());
            }
        }
        if (nbt.hasKey("ControllerPos")) {
            controllerPos = BlockPos.fromLong(nbt.getLong("ControllerPos"));
        }
    }
}
