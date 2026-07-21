package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReactionWindowTest {

    private final ReactionWindow window = ReactionWindow.standard();

    @Test
    void rejectsInvalidRadii() {
        assertThrows(IllegalArgumentException.class, () -> new ReactionWindow(0.0, 0.1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ReactionWindow(0.1, 0.05, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ReactionWindow(0.05, 0.2, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new ReactionWindow(Double.NaN, 0.1, 0.2));
    }

    @Test
    void centerIsPerfect() {
        assertEquals(ReactionTiming.PERFECT, window.classify(0.0));
    }

    @Test
    void perfectBoundaryBelongsToPerfectOnBothSides() {
        assertEquals(ReactionTiming.PERFECT, window.classify(window.perfectRadius()));
        assertEquals(ReactionTiming.PERFECT, window.classify(-window.perfectRadius()));
    }

    @Test
    void justPastPerfectIsGood() {
        assertEquals(ReactionTiming.GOOD, window.classify(window.perfectRadius() + 0.0001));
        assertEquals(ReactionTiming.GOOD, window.classify(window.goodRadius()));
    }

    @Test
    void betweenGoodAndActiveDependsOnSign() {
        double insideActive = (window.goodRadius() + window.activeRadius()) / 2.0;
        assertEquals(ReactionTiming.EARLY, window.classify(-insideActive));
        assertEquals(ReactionTiming.LATE, window.classify(insideActive));
    }

    @Test
    void activeBoundaryStillReactsButBeyondIsNone() {
        assertEquals(ReactionTiming.LATE, window.classify(window.activeRadius()));
        assertEquals(ReactionTiming.EARLY, window.classify(-window.activeRadius()));
        assertEquals(ReactionTiming.NONE, window.classify(window.activeRadius() + 0.0001));
        assertEquals(ReactionTiming.NONE, window.classify(-window.activeRadius() - 0.0001));
    }

    @Test
    void nonFiniteOffsetIsNone() {
        assertEquals(ReactionTiming.NONE, window.classify(Double.NaN));
        assertEquals(ReactionTiming.NONE, window.classify(Double.POSITIVE_INFINITY));
    }

    @Test
    void noPressIsAlwaysNone() {
        assertEquals(ReactionTiming.NONE, window.classifyPress(false, 0.0));
        assertEquals(ReactionTiming.PERFECT, window.classifyPress(true, 0.0));
    }

    @Test
    void storyModeWidensEveryTierAroundTheSameCenter() {
        ReactionWindow story = ReactionWindow.storyMode();
        double lateInStrategic = window.activeRadius() + 0.05;

        assertEquals(ReactionTiming.NONE, window.classify(lateInStrategic));
        assertEquals(ReactionTiming.LATE, story.classify(lateInStrategic));
        assertEquals(ReactionTiming.PERFECT, story.classify(0.0));
    }

    @Test
    void scaledRejectsNonPositiveFactor() {
        assertThrows(IllegalArgumentException.class, () -> window.scaled(0.0));
        assertThrows(IllegalArgumentException.class, () -> window.scaled(-1.0));
    }
}
