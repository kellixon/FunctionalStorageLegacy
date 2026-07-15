package com.xinyihl.functionalstoragelegacy.util;

import com.xinyihl.functionalstoragelegacy.common.tile.WoodDrawerTile;
import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ConnectedDrawersTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void unloadedPositionsSurviveAndReloadRefreshesLiveHandlerIdentity() {
        ConnectedDrawers drawers = new ConnectedDrawers();
        BlockPos position = new BlockPos(4, 5, 6);
        drawers.getConnectedDrawers().add(position.toLong());
        MutableResolver resolver = new MutableResolver();

        resolver.loaded = false;
        assertFalse(drawers.rebuildWithResolver(resolver));
        assertTrue(drawers.getConnectedDrawers().contains(position.toLong()));
        assertTrue(drawers.getItemHandlers().isEmpty());

        WoodDrawerTile first = new WoodDrawerTile();
        resolver.loaded = true;
        resolver.tile = first;
        assertFalse(drawers.rebuildWithResolver(resolver));
        assertSame(first.getItemHandler(), drawers.getItemHandlers().get(0));

        WoodDrawerTile replacement = new WoodDrawerTile();
        resolver.tile = replacement;
        assertFalse(drawers.rebuildWithResolver(resolver));
        assertSame(replacement.getItemHandler(), drawers.getItemHandlers().get(0));

        resolver.tile = null;
        assertTrue(drawers.rebuildWithResolver(resolver));
        assertTrue(drawers.getConnectedDrawers().isEmpty());
        assertTrue(drawers.getItemHandlers().isEmpty());
    }

    private static final class MutableResolver
            implements ConnectedDrawers.LoadedTileResolver {
        private boolean loaded;
        private TileEntity tile;

        @Override
        public boolean isLoaded(BlockPos pos) {
            return loaded;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return tile;
        }
    }
}
