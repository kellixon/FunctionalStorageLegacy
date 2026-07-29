package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.TestCapabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ArmoryCabinetTileTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(28000);

    @BeforeClass
    public static void bootstrapCapabilities() {
        Bootstrap.register();
        TestCapabilities.itemHandler();
    }

    @Test
    public void tileUsesTopLevelStorageV2AndExposesTheSameCapabilityInstance() {
        Item item = registeredItem("armory_tile").setMaxStackSize(1);
        ArmoryCabinetTile source = new ArmoryCabinetTile();
        source.getStorage().insertItem(0, new ItemStack(item), false);

        NBTTagCompound serialized = source.saveTileToNBT();
        assertTrue(serialized.hasKey("StorageV2"));
        assertFalse(serialized.hasKey("Inventory"));
        assertSame(source.getStorage(), source.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));

        ArmoryCabinetTile restored = new ArmoryCabinetTile();
        restored.loadTileFromNBT(serialized);
        assertTrue(restored.getStorage().getStackInSlot(0).isEmpty());
        assertFalse(restored.getStorage().getStackInSlot(1).isEmpty());
    }

    @Test
    public void tileDoesNotMigrateLegacyInventoryWrapper() {
        Item item = registeredItem("armory_legacy").setMaxStackSize(1);
        NBTTagCompound legacyHandler = new NBTTagCompound();
        legacyHandler.setInteger("Size", 1);
        legacyHandler.setTag("Slot_0", new ItemStack(item).writeToNBT(new NBTTagCompound()));
        NBTTagCompound legacyTile = new NBTTagCompound();
        legacyTile.setTag("Inventory", legacyHandler);

        ArmoryCabinetTile restored = new ArmoryCabinetTile();
        restored.loadTileFromNBT(legacyTile);

        assertTrue(restored.getStorage().getStackInSlot(0).isEmpty());
    }

    private static Item registeredItem(String path) {
        int id = NEXT_ITEM_ID.getAndIncrement();
        Item item = new Item().setRegistryName(new ResourceLocation(
                "functionalstoragelegacy_test", path + id));
        @SuppressWarnings("unchecked")
        ForgeRegistry<Item> registry = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
        boolean wasFrozen = registry.isLocked();
        if (wasFrozen) {
            registry.unfreeze();
        }
        try {
            registry.register(item);
            return item;
        } finally {
            if (wasFrozen) {
                registry.freeze();
            }
        }
    }
}
