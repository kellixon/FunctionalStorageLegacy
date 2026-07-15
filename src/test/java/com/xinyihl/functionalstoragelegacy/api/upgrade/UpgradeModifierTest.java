package com.xinyihl.functionalstoragelegacy.api.upgrade;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UpgradeModifierTest {

    @Test
    public void evaluatesInFixedOperationOrder() {
        List<UpgradeModifier> mixedOrder = Arrays.asList(
                UpgradeModifier.multiply(2.0D),
                UpgradeModifier.addBase(3.0D),
                UpgradeModifier.setBase(5.0D)
        );

        assertEquals(16.0D, UpgradeModifier.calculate(mixedOrder, 100.0D), 0.0D);
    }

    @Test
    public void materializesOneShotIterableBeforeEvaluation() {
        final List<UpgradeModifier> values = Arrays.asList(
                UpgradeModifier.multiply(3.0D),
                UpgradeModifier.addBase(2.0D),
                UpgradeModifier.setBase(4.0D)
        );
        Iterable<UpgradeModifier> oneShot = new Iterable<UpgradeModifier>() {
            private boolean consumed;

            @Override
            public Iterator<UpgradeModifier> iterator() {
                if (consumed) {
                    return Collections.emptyIterator();
                }
                consumed = true;
                return values.iterator();
            }
        };

        assertEquals(18.0D, UpgradeModifier.calculate(oneShot, 1.0D), 0.0D);
    }

    @Test
    public void normalizesInvalidNegativeResults() {
        assertEquals(0.0D, UpgradeModifier.calculate(
                Collections.singletonList(UpgradeModifier.multiply(-1.0D)), 4.0D), 0.0D);
        assertEquals(0.0D, UpgradeModifier.calculate(
                Collections.singletonList(UpgradeModifier.multiply(Double.NaN)), 4.0D), 0.0D);
    }
}
