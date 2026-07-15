package com.xinyihl.functionalstoragelegacy.common.block;

import net.minecraft.util.IStringSerializable;

import javax.annotation.Nonnull;

/** Physical surface to which a drawer block is attached. */
public enum DrawerAttachment implements IStringSerializable {
    WALL("wall", 0),
    FLOOR("floor", 1),
    CEILING("ceiling", 2);

    private final String id;
    private final int metadataIndex;

    DrawerAttachment(String id, int metadataIndex) {
        this.id = id;
        this.metadataIndex = metadataIndex;
    }

    public static DrawerAttachment byIndex(int index) {
        for (DrawerAttachment attachment : values()) {
            if (attachment.metadataIndex == index) {
                return attachment;
            }
        }
        return WALL;
    }

    public int getIndex() {
        return metadataIndex;
    }

    @Nonnull
    @Override
    public String getName() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }
}
