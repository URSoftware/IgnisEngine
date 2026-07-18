package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManualAdvanceGateTest {

    @Test
    void requiresReleaseMinimumReadingTimeAndFreshPress() {
        ManualAdvanceGate gate = new ManualAdvanceGate(1.0);

        assertFalse(gate.update(0.4, true, true));
        assertFalse(gate.isArmed());
        assertFalse(gate.update(0.4, true, false));
        assertFalse(gate.update(0.1, false, false));
        assertTrue(gate.isArmed());
        assertFalse(gate.update(0.0, true, true));
        assertTrue(gate.update(0.2, true, true));
        assertFalse(gate.update(1.0, true, true));
    }

    @Test
    void rejectsInvalidMinimumTime() {
        boolean rejected = false;
        try {
            new ManualAdvanceGate(Double.NaN);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }
}
