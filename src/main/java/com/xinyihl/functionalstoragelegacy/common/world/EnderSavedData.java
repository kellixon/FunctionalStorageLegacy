package com.xinyihl.functionalstoragelegacy.common.world;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;
import com.xinyihl.functionalstoragelegacy.common.inventory.EnderInventoryHandler;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * World-saved data for ender drawer frequencies.
 * Holds a map of frequency UUID strings to EnderInventoryHandler instances.
 * All ender drawers sharing a frequency share the same inventory.
 */
public class EnderSavedData extends WorldSavedData {

    private final Map<String, EnderInventoryHandler> frequencyMap = new HashMap<>();
    private final Map<String, StorageSubscription> dirtySubscriptions = new HashMap<>();

    public EnderSavedData(String name) {
        super(name);
    }

    public static EnderSavedData getInstance(World world) {
        EnderSavedData data = null;
        if (world.getMapStorage() != null) {
            data = (EnderSavedData) world.getMapStorage().getOrLoadData(EnderSavedData.class, Tags.MOD_ID + "_ender");
        }
        if (data == null) {
            data = new EnderSavedData(Tags.MOD_ID + "_ender");
            if (world.getMapStorage() != null) {
                world.getMapStorage().setData(Tags.MOD_ID + "_ender", data);
            }
        }
        return data;
    }

    public EnderInventoryHandler getFrequency(String frequency) {
        final String key = frequency == null ? "" : frequency;
        return frequencyMap.computeIfAbsent(key, f -> {
            EnderInventoryHandler handler = new EnderInventoryHandler() {
            };
            // Initial construction is deliberately silent to external listeners.
            handler.setFrequency(f);
            dirtySubscriptions.put(f, handler.subscribe(change -> markDirty()));
            return handler;
        });
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound nbt) {
        for (StorageSubscription subscription : dirtySubscriptions.values()) {
            if (subscription != null) {
                subscription.close();
            }
        }
        dirtySubscriptions.clear();
        frequencyMap.clear();
        int count = nbt.getInteger("FrequencyCount");
        for (int i = 0; i < count; i++) {
            String key = nbt.getString("Freq_" + i);
            NBTTagCompound data = nbt.getCompoundTag("FreqData_" + i);
            EnderInventoryHandler handler = new EnderInventoryHandler() {
            };
            handler.deserializeNBTFull(data);
            handler.setFrequency(key);
            dirtySubscriptions.put(key, handler.subscribe(change -> markDirty()));
            frequencyMap.put(key, handler);
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbt) {
        int i = 0;
        for (Map.Entry<String, EnderInventoryHandler> entry : frequencyMap.entrySet()) {
            nbt.setString("Freq_" + i, entry.getKey());
            nbt.setTag("FreqData_" + i, entry.getValue().serializeNBTFull());
            i++;
        }
        nbt.setInteger("FrequencyCount", i);
        return nbt;
    }
}
