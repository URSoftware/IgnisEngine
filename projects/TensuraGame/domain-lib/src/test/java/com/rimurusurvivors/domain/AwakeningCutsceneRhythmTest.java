package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Ritmo autorado de data/cutscene-awakening.json v2 contra os limites de
 * data/cutscene-polish-acceptance.json, secao cave_awakening. Puro: sem engine, sem
 * path e sem relogio real. A conferencia de que o JSON no disco tem estes mesmos
 * numeros fica em tools/validate_awakening_rhythm.py.
 */
class AwakeningCutsceneRhythmTest {

    private static final double DURATION = 5.26;
    private static final double SKIPPABLE_AFTER = 1.0;
    private static final double FRAME = 1.0 / 60.0;

    private static final double MAX_DISMISS_TO_NEXT_FEEDBACK = 0.05;
    private static final double MAX_FINAL_FEEDBACK_TO_CONTROL = 0.35;

    private static final String GATE = "great_sage_analysis";
    private static final String FORESHADOW = "veldora_foreshadow";
    private static final String HANDOFF = "control_handoff";

    private static final List<CutsceneCue> AUTHORED = List.of(
            new CutsceneCue("darkness_and_drops", 0.0),
            new CutsceneCue("magicule_convergence", 0.85),
            new CutsceneCue("slime_body_formation", 2.1),
            new CutsceneCue("first_perception", 3.55),
            new CutsceneCue(GATE, 4.7),
            new CutsceneCue(FORESHADOW, 4.71),
            new CutsceneCue(HANDOFF, 5.01));

    @Test
    void authoredOrderMatchesTheAcceptanceContract() {
        assertEquals(
                List.of(
                        "darkness_and_drops",
                        "magicule_convergence",
                        "slime_body_formation",
                        "first_perception",
                        GATE,
                        FORESHADOW,
                        HANDOFF),
                AUTHORED.stream().map(CutsceneCue::id).toList());
    }

    @Test
    void theGateBatchAlsoCarriesTheForeshadowSoTheAdapterMustDeferIt() {
        // Com 0.01 s de intervalo, um passo de frame cruza os dois cues na MESMA chamada.
        // E por isso que o adaptador guarda o resto do lote em vez de aplicar tudo.
        CutsceneTimeline timeline = timeline();
        List<String> batch = List.of();
        for (int frame = 0; frame < 2000 && !batch.contains(GATE); frame++) {
            batch = timeline.advance(FRAME);
        }
        assertEquals(List.of(GATE, FORESHADOW), batch);
    }

    @Test
    void theFrozenGateDoesNotLeakTheForeshadowBeforeTheDismissal() {
        AdapterModel adapter = new AdapterModel();
        runUntilApplied(adapter, GATE);

        for (int frame = 0; frame < 120; frame++) {
            assertEquals(List.of(), adapter.tick(false));
        }
    }

    @Test
    void dismissingTheAnalysisReachesVeldoraFeedbackWithinFiftyMilliseconds() {
        AdapterModel adapter = new AdapterModel();
        double atGate = runUntilApplied(adapter, GATE);
        adapter.tick(false);
        adapter.tick(false);

        List<String> applied = adapter.tick(true);
        double elapsedSinceDismissal = adapter.clock - adapter.clockBeforeLastTick;

        assertTrue(applied.contains(FORESHADOW), "o feedback nao saiu na dispensa");
        assertTrue(
                elapsedSinceDismissal <= MAX_DISMISS_TO_NEXT_FEEDBACK,
                "dispensa ate o proximo feedback levou " + elapsedSinceDismissal + "s");
        assertTrue(adapter.clock > atGate);
    }

    @Test
    void theLastFeedbackReachesControlWithinThreeHundredFiftyMilliseconds() {
        CutsceneTimeline timeline = timeline();
        double atForeshadow = advanceUntil(timeline, FORESHADOW);
        double atHandoff = advanceUntil(timeline, HANDOFF);

        assertTrue(
                atHandoff - atForeshadow <= MAX_FINAL_FEEDBACK_TO_CONTROL,
                "feedback final ate o controle levou " + (atHandoff - atForeshadow) + "s");
        assertTrue(
                DURATION - 5.01 <= MAX_FINAL_FEEDBACK_TO_CONTROL,
                "rabo apos o handoff excede o limite");
    }

    @Test
    void theOldTimingWouldStillFailTheSameAssertions() {
        // Regressao declarada no contrato: 4.7 -> 6.2 retomava 1.5 s depois da dispensa.
        CutsceneTimeline old = new CutsceneTimeline(8.2, SKIPPABLE_AFTER, List.of(
                new CutsceneCue(GATE, 4.7),
                new CutsceneCue(FORESHADOW, 6.2),
                new CutsceneCue(HANDOFF, 7.8)));
        double atGate = advanceUntil(old, GATE);
        double atForeshadow = advanceUntil(old, FORESHADOW);

        assertFalse(atForeshadow - atGate <= MAX_DISMISS_TO_NEXT_FEEDBACK);
    }

    @Test
    void skipBeforeTheThresholdIsRejectedAndKeepsThePlaybackAlive() {
        CutsceneTimeline timeline = timeline();
        timeline.advance(0.5);

        assertFalse(timeline.requestSkip());
        assertFalse(timeline.isFinished());
        assertFalse(timeline.consumeCompletion());
    }

    @Test
    void acceptedSkipAndNaturalPlaybackDeliverCompletionExactlyOnce() {
        CutsceneTimeline skipped = timeline();
        skipped.advance(SKIPPABLE_AFTER + FRAME);
        assertTrue(skipped.requestSkip());
        assertTrue(skipped.consumeCompletion());
        assertFalse(skipped.consumeCompletion());

        CutsceneTimeline natural = timeline();
        advanceUntil(natural, HANDOFF);
        while (!natural.isFinished()) {
            natural.advance(FRAME);
        }
        assertTrue(natural.consumeCompletion());
        assertFalse(natural.consumeCompletion());

        assertEquals(skipped.isFinished(), natural.isFinished());
        assertEquals(skipped.elapsedSeconds(), natural.elapsedSeconds());
    }

    @Test
    void skipAfterTheThresholdDoesNotReopenTheManualGate() {
        CutsceneTimeline timeline = timeline();
        advanceUntil(timeline, GATE);

        assertTrue(timeline.requestSkip());
        assertTrue(timeline.isFinished());
        assertEquals(List.of(), timeline.advance(FRAME));
    }

    private static CutsceneTimeline timeline() {
        return new CutsceneTimeline(DURATION, SKIPPABLE_AFTER, AUTHORED);
    }

    /**
     * Espelha a regra do adaptador em CutsceneDirector.AwakeningCutscene: o lote do
     * portao manual e consumido ate o portao, o resto fica pendente e sai no frame da
     * dispensa. O relogio continua correndo enquanto o texto esta na tela.
     */
    private static final class AdapterModel {

        private final CutsceneTimeline timeline = timeline();
        private final java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        private boolean gated;
        private double clock;
        private double clockBeforeLastTick;

        List<String> tick(boolean dismissPressed) {
            clockBeforeLastTick = clock;
            clock += FRAME;
            if (gated) {
                if (!dismissPressed) {
                    return List.of();
                }
                gated = false;
            } else {
                pending.addAll(timeline.advance(FRAME));
            }
            List<String> applied = new java.util.ArrayList<>();
            while (!pending.isEmpty()) {
                String cue = pending.poll();
                applied.add(cue);
                if (cue.equals(GATE)) {
                    gated = true;
                    break;
                }
            }
            return List.copyOf(applied);
        }
    }

    private static double runUntilApplied(AdapterModel adapter, String cueId) {
        for (int frame = 0; frame < 2000; frame++) {
            if (adapter.tick(false).contains(cueId)) {
                return adapter.clock;
            }
        }
        throw new AssertionError("Cue nunca foi aplicado: " + cueId);
    }

    /** Avanca em passos de um frame ate o cue sair e devolve o instante em que saiu. */
    private static double advanceUntil(CutsceneTimeline timeline, String cueId) {
        for (int frame = 0; frame < 2000; frame++) {
            if (timeline.advance(FRAME).contains(cueId)) {
                return timeline.elapsedSeconds();
            }
        }
        throw new AssertionError("Cue nunca disparou: " + cueId);
    }
}
