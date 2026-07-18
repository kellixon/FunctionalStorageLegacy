package com.xinyihl.functionalstoragelegacy.common.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side drawer layout data with stable serialized identifiers.
 *
 * <p>This type deliberately contains no rendering or GUI coordinates. The base capacity is the
 * capacity multiplier for each slot or tank before upgrades are applied.</p>
 */
public enum DrawerLayout {
    X_1("1x1", 1, 32),
    X_2("1x2", 2, 16),
    X_4("2x2", 4, 8);

    private static final Map<String, DrawerLayout> BY_ID = new HashMap<>();

    static {
        for (DrawerLayout layout : values()) {
            BY_ID.put(layout.id, layout);
        }
    }

    private final String id;
    private final int slotCount;
    private final int baseCapacity;

    DrawerLayout(String id, int slotCount, int baseCapacity) {
        this.id = id;
        this.slotCount = slotCount;
        this.baseCapacity = baseCapacity;
    }

    /**
     * Resolves a stable identifier.
     *
     * @param id serialized layout identifier
     * @return the matching layout, or {@link #X_1} when the identifier is unknown
     */
    public static DrawerLayout fromId(String id) {
        DrawerLayout layout = BY_ID.get(Objects.requireNonNull(id, "id"));
        return layout == null ? X_1 : layout;
    }

    /**
     * Returns the stable string used in NBT and other persistent data.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the number of independent slots or tanks.
     */
    public int getSlotCount() {
        return slotCount;
    }

    /**
     * Returns the unmodified capacity multiplier of each slot or tank.
     */
    public int getBaseCapacity() {
        return baseCapacity;
    }
}
