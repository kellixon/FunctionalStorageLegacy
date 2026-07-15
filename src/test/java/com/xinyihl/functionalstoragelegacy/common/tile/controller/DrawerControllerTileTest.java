package com.xinyihl.functionalstoragelegacy.common.tile.controller;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DrawerControllerTileTest {

    @Test
    public void wrappedConnectionsRoundTrip() {
        DrawerControllerTile source = new DrawerControllerTile();
        BlockPos linked = new BlockPos(4, 5, 6);
        source.getConnectedDrawers().getConnectedDrawers().add(linked.toLong());

        NBTTagCompound serialized = source.saveTileToNBT();
        DrawerControllerTile restored = new DrawerControllerTile();
        restored.loadTileFromNBT(serialized);

        assertEquals(1, restored.getConnectedDrawers().getConnectedDrawers().size());
        assertEquals(linked.toLong(),
                restored.getConnectedDrawers().getConnectedDrawers().get(0).longValue());
    }

    @Test
    public void unwrappedLegacyConnectionsAreIgnoredByBothReadPaths() {
        NBTTagCompound legacy = new NBTTagCompound();
        NBTTagList positions = new NBTTagList();
        positions.appendTag(new NBTTagLong(new BlockPos(7, 8, 9).toLong()));
        legacy.setTag("Positions", positions);

        DrawerControllerTile worldTile = new DrawerControllerTile();
        worldTile.getConnectedDrawers().getConnectedDrawers()
                .add(new BlockPos(1, 2, 3).toLong());
        worldTile.readFromNBT(legacy);
        assertTrue(worldTile.getConnectedDrawers().getConnectedDrawers().isEmpty());

        DrawerControllerTile itemTile = new DrawerControllerTile();
        itemTile.getConnectedDrawers().getConnectedDrawers()
                .add(new BlockPos(3, 2, 1).toLong());
        itemTile.loadTileFromNBT(legacy);
        assertTrue(itemTile.getConnectedDrawers().getConnectedDrawers().isEmpty());
    }
}
