package com.xinyihl.functionalstoragelegacy.common.block;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Internal wood variant data with stable serialized identifiers. */
public enum DrawerWoodType {

    OAK(Blocks.LOG, Blocks.PLANKS, 0),
    SPRUCE(Blocks.LOG, Blocks.PLANKS, 1),
    BIRCH(Blocks.LOG, Blocks.PLANKS, 2),
    JUNGLE(Blocks.LOG, Blocks.PLANKS, 3),
    ACACIA(Blocks.LOG2, Blocks.PLANKS, 4),
    DARK_OAK(Blocks.LOG2, Blocks.PLANKS, 5);

    private static final Map<String, DrawerWoodType> BY_ID = new HashMap<>();

    static {
        for (DrawerWoodType woodType : values()) {
            BY_ID.put(woodType.id, woodType);
        }
    }

    private final Block log;
    private final Block planks;
    private final int meta;
    private final String id;

    DrawerWoodType(Block log, Block planks, int meta) {
        this.log = log;
        this.planks = planks;
        this.meta = meta;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public Block getLog() {
        return log;
    }

    public Block getPlanks() {
        return planks;
    }

    public int getMeta() {
        return meta;
    }

    public String getId() {
        return id;
    }

    public static DrawerWoodType fromId(String id) {
        DrawerWoodType woodType = BY_ID.get(Objects.requireNonNull(id, "id"));
        return woodType == null ? OAK : woodType;
    }
}
