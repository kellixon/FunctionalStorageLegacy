package com.xinyihl.functionalstoragelegacy.client.gui;

import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Client-only pixel positions for server-side drawer layouts.
 */
final class DrawerGuiLayout {

    private static final Map<DrawerLayout, Function<Integer, Pair<Integer, Integer>>> POSITIONS = new EnumMap<>(DrawerLayout.class);

    static {
        POSITIONS.put(DrawerLayout.X_1, index -> Pair.of(16, 16));
        POSITIONS.put(DrawerLayout.X_2, index -> index == 0 ? Pair.of(16, 4) : Pair.of(16, 28));
        POSITIONS.put(DrawerLayout.X_4, index -> {
            switch (index) {
                case 0:
                    return Pair.of(4, 4);
                case 1:
                    return Pair.of(28, 4);
                case 2:
                    return Pair.of(4, 28);
                default:
                    return Pair.of(28, 28);
            }
        });
    }

    private DrawerGuiLayout() {
    }

    static Function<Integer, Pair<Integer, Integer>> slotPositions(DrawerLayout layout) {
        return POSITIONS.get(layout);
    }
}
