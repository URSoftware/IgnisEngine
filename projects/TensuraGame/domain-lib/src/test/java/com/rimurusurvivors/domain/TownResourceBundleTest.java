package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TownResourceBundleTest {

    @Test
    void resourcesAddAndSubtractWithoutBecomingNegative() {
        TownResourceBundle stock = new TownResourceBundle(20, 10, 4, 2, 2, 1);
        TownResourceBundle delivery = new TownResourceBundle(2, 3, 1, 0, 0, 0);
        TownResourceBundle cost = new TownResourceBundle(12, 4, 2, 0, 0, 0);

        TownResourceBundle updated = stock.add(delivery).subtract(cost);

        assertEquals(new TownResourceBundle(10, 9, 3, 2, 2, 1), updated);
        assertThrows(
                IllegalStateException.class,
                () -> TownResourceBundle.EMPTY.subtract(cost));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TownResourceBundle(-1, 0, 0, 0, 0, 0));
    }
}
