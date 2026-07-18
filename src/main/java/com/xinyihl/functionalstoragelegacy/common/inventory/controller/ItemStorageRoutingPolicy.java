package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Default item policy used by a controller when no custom policy is given.
 */
public final class ItemStorageRoutingPolicy implements StorageRoutingPolicy<BigItemStack, ItemStorageKey> {

    @Override
    public ItemStorageKey getExactKey(@Nonnull BigItemStack snapshot) {
        return snapshot.getKey();
    }

    @Nonnull
    @Override
    public Collection<? extends StorageKey> getCompatibleAliases(@Nonnull BigItemStack snapshot) {
        if (!snapshot.hasTemplate()) {
            return Collections.emptyList();
        }
        ItemStack template = snapshot.getTemplate();
        if (template.isEmpty()) {
            return Collections.emptyList();
        }
        int[] ids = OreDictionary.getOreIDs(template);
        if (ids.length == 0) {
            return Collections.emptyList();
        }
        List<StorageKey> aliases = new ArrayList<>(ids.length);
        for (int id : ids) {
            aliases.add(StorageAliasKey.ore(id));
        }
        return aliases;
    }

    @Override
    public boolean isEmptySlotEligible(@Nonnull IStorageHandler<BigItemStack, ItemStorageKey> handler, int index, @Nonnull BigItemStack request) {
        return !handler.isLocked();
    }

    @Override
    public int getCandidatePriority(@Nonnull IStorageHandler<BigItemStack, ItemStorageKey> handler, int index, @Nonnull BigItemStack current, @Nonnull BigItemStack request) {
        if (!current.hasTemplate()) {
            return -1;
        }
        // The controller has already selected exact or alias candidates.  The
        // policy only supplies the lock ordering at this stage.
        return handler.isLocked() ? 0 : 1;
    }
}
