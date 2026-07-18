package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutsceneTimelineTest {

    private static CutsceneTimeline awakening() {
        return new CutsceneTimeline(8.2, 1.0, List.of(
                new CutsceneCue("darkness", 0.0),
                new CutsceneCue("magicules", 0.85),
                new CutsceneCue("slime", 2.1),
                new CutsceneCue("analysis", 4.7),
                new CutsceneCue("foreshadow", 6.2),
                new CutsceneCue("handoff", 7.8)));
    }

    @Test
    void emitsEachCueOnceWhenCrossingItsTime() {
        CutsceneTimeline timeline = awakening();

        assertEquals(List.of("darkness"), timeline.advance(0.1));
        assertEquals(List.of("magicules"), timeline.advance(0.8));
        assertEquals(List.of(), timeline.advance(0.1));
        assertEquals(List.of("slime", "analysis"), timeline.advance(4.0));
        assertEquals(List.of(), timeline.advance(0));
    }

    @Test
    void refusesEarlySkipAndAcceptsItAfterThreshold() {
        CutsceneTimeline timeline = awakening();

        timeline.advance(0.9);
        assertFalse(timeline.requestSkip());
        timeline.advance(0.1);
        assertTrue(timeline.canSkip());
        assertTrue(timeline.requestSkip());
        assertTrue(timeline.isFinished());
    }

    @Test
    void completionCanBeConsumedExactlyOnceForNaturalEndAndSkip() {
        CutsceneTimeline natural = awakening();
        natural.advance(20);
        assertTrue(natural.consumeCompletion());
        assertFalse(natural.consumeCompletion());

        CutsceneTimeline skipped = awakening();
        skipped.advance(1.0);
        skipped.requestSkip();
        assertTrue(skipped.consumeCompletion());
        assertFalse(skipped.consumeCompletion());
    }

    @Test
    void rejectsDuplicateOrUnorderedCues() {
        assertThrows(IllegalArgumentException.class, () -> new CutsceneTimeline(2, 1, List.of(
                new CutsceneCue("same", 0.2), new CutsceneCue("same", 0.4))));
        assertThrows(IllegalArgumentException.class, () -> new CutsceneTimeline(2, 1, List.of(
                new CutsceneCue("later", 1.2), new CutsceneCue("earlier", 0.4))));
    }
}
