package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarrativeSequenceTest {

    private static NarrativeSequence sequence() {
        return new NarrativeSequence(List.of(
                new NarrativeBeat("presence", 1.0, List.of()),
                new NarrativeBeat("contact", 0, List.of(
                        new NarrativeLine("one", "veldora", "proud", "First."),
                        new NarrativeLine("two", "rimuru", "curious", "Second."))),
                new NarrativeBeat("aftermath", 0.5, List.of(
                        new NarrativeLine("three", "sage", "", "Complete.")))), 0.75);
    }

    @Test
    void automaticAndManualBeatsAdvanceDeterministically() {
        NarrativeSequence sequence = sequence();
        assertEquals("presence", sequence.currentBeat().id());
        assertNull(sequence.currentLine());

        sequence.update(1.0, false);
        assertEquals("contact", sequence.currentBeat().id());
        assertEquals("one", sequence.currentLine().id());

        sequence.update(0.1, true);
        assertEquals("two", sequence.currentLine().id());
        sequence.update(0.1, true);
        assertEquals("aftermath", sequence.currentBeat().id());
        assertEquals("three", sequence.currentLine().id());

        sequence.update(0.5, false);
        assertTrue(sequence.isFinished());
        assertTrue(sequence.consumeCompletion());
        assertFalse(sequence.consumeCompletion());
    }

    @Test
    void skipIsRejectedEarlyAndCompletesOnceWhenAllowed() {
        NarrativeSequence sequence = sequence();
        sequence.update(0.5, false);
        assertFalse(sequence.requestSkip());
        sequence.update(0.25, false);
        assertTrue(sequence.canSkip());
        assertTrue(sequence.requestSkip());
        assertTrue(sequence.consumeCompletion());
        assertFalse(sequence.requestSkip());
        assertFalse(sequence.consumeCompletion());
    }

    @Test
    void duplicateIdsAndEmptySequencesAreRejected() {
        NarrativeLine duplicate = new NarrativeLine("same", "speaker", "", "Text");
        assertThrows(IllegalArgumentException.class, () -> new NarrativeSequence(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new NarrativeSequence(List.of(
                new NarrativeBeat("a", 0, List.of(duplicate)),
                new NarrativeBeat("b", 0, List.of(duplicate))), 0));
    }
}
