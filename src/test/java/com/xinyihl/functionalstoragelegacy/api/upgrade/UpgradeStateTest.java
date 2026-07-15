package com.xinyihl.functionalstoragelegacy.api.upgrade;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpgradeStateTest {

    @Test
    public void buildCreatesDetachedDeeplyUnmodifiableSnapshot() {
        UpgradeState.Builder builder = UpgradeState.builder()
                .addModifier(UpgradeAttribute.ITEM_CAPACITY, UpgradeModifier.multiply(2.0D))
                .addFeature(StorageFeature.VOID_OVERFLOW);
        UpgradeState first = builder.build();

        builder.addModifier(UpgradeAttribute.ITEM_CAPACITY, UpgradeModifier.addBase(5.0D))
                .addFeature(StorageFeature.CREATIVE);

        assertEquals(1, first.getModifiers(UpgradeAttribute.ITEM_CAPACITY).size());
        assertEquals(16.0D, first.calculate(UpgradeAttribute.ITEM_CAPACITY, 8.0D), 0.0D);
        assertTrue(first.hasFeature(StorageFeature.VOID_OVERFLOW));
        assertFalse(first.hasFeature(StorageFeature.CREATIVE));
        assertUnmodifiable(first.getModifiers(UpgradeAttribute.ITEM_CAPACITY));
    }

    private static void assertUnmodifiable(List<UpgradeModifier> modifiers) {
        try {
            modifiers.add(UpgradeModifier.setBase(1.0D));
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("modifier list accepted a mutation");
    }
}
