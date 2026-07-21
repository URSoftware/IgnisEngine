package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cobre a estrutura autoral de data/cutscene-goblin-first-contact.json rodando por
 * NarrativeSequence (o mesmo motor que GoblinContactDirector adapta). Prova que os
 * dois beats automaticos das pontas (forest_threshold / forest_handoff) avancam por
 * tempo, os tres beats do meio avancam por input, e que fim natural e skip terminam
 * pelo MESMO completionPending consumido uma unica vez.
 */
class GoblinFirstContactSequenceTest {

    /** Reproduz a forma exata do JSON: 2.0s auto, 3 falas, 4 falas, 3 falas, 0.5s auto. */
    private static NarrativeSequence goblinContact() {
        return new NarrativeSequence(List.of(
                new NarrativeBeat("forest_threshold", 2.0, List.of()),
                new NarrativeBeat("scouts_emerge", 0, List.of(
                        new NarrativeLine("gfc_001", "goblin_scout_leader", "", "A presenca mudou."),
                        new NarrativeLine("gfc_002", "rimuru", "skeptical", "Podemos conversar?"),
                        new NarrativeLine("gfc_003", "goblin_scout_leader", "", "Ele fala!"))),
                new NarrativeBeat("cautious_lowering", 0, List.of(
                        new NarrativeLine("gfc_004", "great_sage", "", "Hostilidade reduzida."),
                        new NarrativeLine("gfc_005", "rimuru", "curious", "Pedir ajuda?"),
                        new NarrativeLine("gfc_006", "goblin_scout_leader", "", "Lobos cercam a aldeia."),
                        new NarrativeLine("gfc_007", "rimuru", "determined", "Preciso conhecer o perigo."))),
                new NarrativeBeat("request_and_choice", 0, List.of(
                        new NarrativeLine("gfc_008", "goblin_scout_leader", "", "Venha ver."),
                        new NarrativeLine("gfc_009", "rimuru", "friendly", "Mostrem a aldeia."),
                        new NarrativeLine("gfc_010", "goblin_scout_leader", "", "Guiaremos o caminho."))),
                new NarrativeBeat("forest_handoff", 0.5, List.of())), 1.0);
    }

    @Test
    void autoEdgesAdvanceByTimeAndManualMiddleBeatsAdvanceByInput() {
        NarrativeSequence sequence = goblinContact();
        // Beat automatico de abertura, sem falas: nada de input, so tempo.
        assertEquals("forest_threshold", sequence.currentBeat().id());
        assertNull(sequence.currentLine());
        sequence.update(2.0, false);
        assertEquals("scouts_emerge", sequence.currentBeat().id());
        assertEquals("gfc_001", sequence.currentLine().id());

        // Beats do meio: um E por fala. Input sem passar de tempo nao pode pular falas.
        sequence.update(0.1, true);
        assertEquals("gfc_002", sequence.currentLine().id());
        sequence.update(0.1, true);
        assertEquals("gfc_003", sequence.currentLine().id());
        sequence.update(0.1, true);
        assertEquals("cautious_lowering", sequence.currentBeat().id());
        assertEquals("gfc_004", sequence.currentLine().id());

        for (int i = 0; i < 4; i++) {
            sequence.update(0.1, true);
        }
        assertEquals("request_and_choice", sequence.currentBeat().id());
        assertEquals("gfc_008", sequence.currentLine().id());

        sequence.update(0.1, true);
        sequence.update(0.1, true);
        // Ultima fala do ultimo beat com falas: o proximo E entra no handoff automatico.
        sequence.update(0.1, true);
        assertEquals("forest_handoff", sequence.currentBeat().id());
        assertNull(sequence.currentLine());
        assertFalse(sequence.isFinished());

        // Handoff fecha por tempo (0.5s), nao por input.
        sequence.update(0.5, false);
        assertTrue(sequence.isFinished());
        assertTrue(sequence.consumeCompletion());
        assertFalse(sequence.consumeCompletion());
    }

    @Test
    void skipUsesTheSameSingleCompletionAsFullPlayback() {
        NarrativeSequence sequence = goblinContact();
        // Antes do limite (1.0s) o skip e rejeitado — evita pular a cutscene no frame 0.
        assertFalse(sequence.requestSkip());
        sequence.update(1.0, false);
        assertTrue(sequence.canSkip());
        assertTrue(sequence.requestSkip());
        // Fim natural e skip compartilham o mesmo completionPending: consumido uma vez.
        assertTrue(sequence.consumeCompletion());
        assertFalse(sequence.consumeCompletion());
        assertFalse(sequence.requestSkip());
    }
}
