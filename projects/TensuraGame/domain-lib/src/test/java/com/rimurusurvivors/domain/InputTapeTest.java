package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InputTapeTest {

    private static ExplorationSimulation freshOpenSimulation(Interactable interactable) {
        ExplorationMap map = new ExplorationMap(10, 10, 32, Set.of());
        List<Interactable> interactables = interactable == null ? List.of() : List.of(interactable);
        ExplorationArea area = new ExplorationArea("start", map, interactables);
        Map<String, DialogueScript> dialogues = interactable == null || interactable.dialogueId() == null
                ? Map.of()
                : Map.of(interactable.dialogueId(), new DialogueScript(interactable.dialogueId(),
                        List.of(new DialogueLine("Narrador", "Unica fala."))));
        return new ExplorationSimulation(Map.of("start", area), dialogues, "start", 48, 48);
    }

    @Test
    void moveAdvancesByExactlyTheStepCountForTheRequestedDuration() {
        ExplorationSimulation simulation = freshOpenSimulation(null);
        InputTape tape = new InputTape(List.of(new InputAction.Move(1, 0, 0.5)));

        List<ExplorationSnapshot> trace = ExplorationInputTapeRunner.run(simulation, tape);

        // 0.5s / 0.05s de passo = 10 passos; cada um anda 140*0.05=7px em X.
        assertEquals(10, trace.size());
        assertEquals(48 + 70, trace.get(trace.size() - 1).playerX(), 1e-9);
        assertEquals(48, trace.get(trace.size() - 1).playerY(), 1e-9);
    }

    @Test
    void sameTapeOnAFreshSimulationIsFullyDeterministic() {
        InputTape tape = new InputTape(List.of(
                new InputAction.Move(1, 0, 0.3),
                new InputAction.Move(0, 1, 0.2),
                new InputAction.Wait(0.1)));

        List<ExplorationSnapshot> first = ExplorationInputTapeRunner.run(freshOpenSimulation(null), tape);
        List<ExplorationSnapshot> second = ExplorationInputTapeRunner.run(freshOpenSimulation(null), tape);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).playerX(), second.get(i).playerX(), 1e-9, "passo " + i);
            assertEquals(first.get(i).playerY(), second.get(i).playerY(), 1e-9, "passo " + i);
            assertEquals(first.get(i).playerMoving(), second.get(i).playerMoving(), "passo " + i);
        }
    }

    @Test
    void interactOpensDialogueOnAFocusedTarget() {
        Interactable sage = new Interactable(
                "sage", 48, 40, 12, InteractionVerb.TALK, "sage_line", false, null, 0, 0);
        ExplorationSimulation simulation = freshOpenSimulation(sage);
        InputTape tape = new InputTape(List.of(new InputAction.Interact()));

        List<ExplorationSnapshot> trace = ExplorationInputTapeRunner.run(simulation, tape);

        assertEquals(1, trace.size());
        assertTrue(trace.get(0).dialogueActive());
        assertTrue(trace.get(0).events().stream().anyMatch(e -> e.type() == ExplorationEventType.DIALOGUE_STARTED));
    }

    @Test
    void advanceDialogueClosesAnOpenSingleLineDialogue() {
        Interactable sage = new Interactable(
                "sage", 48, 40, 12, InteractionVerb.TALK, "sage_line", false, null, 0, 0);
        ExplorationSimulation simulation = freshOpenSimulation(sage);
        InputTape tape = new InputTape(List.of(
                new InputAction.Interact(),
                new InputAction.AdvanceDialogue()));

        List<ExplorationSnapshot> trace = ExplorationInputTapeRunner.run(simulation, tape);

        assertEquals(2, trace.size());
        assertFalse(trace.get(1).dialogueActive());
        assertTrue(trace.get(1).events().stream().anyMatch(e -> e.type() == ExplorationEventType.DIALOGUE_ENDED));
    }

    @Test
    void waitProducesStepsWithNoMovementAndNoEvents() {
        ExplorationSimulation simulation = freshOpenSimulation(null);
        InputTape tape = new InputTape(List.of(new InputAction.Wait(0.15)));

        List<ExplorationSnapshot> trace = ExplorationInputTapeRunner.run(simulation, tape);

        assertEquals(3, trace.size());
        for (ExplorationSnapshot snapshot : trace) {
            assertEquals(48, snapshot.playerX(), 1e-9);
            assertEquals(48, snapshot.playerY(), 1e-9);
            assertFalse(snapshot.playerMoving());
            assertTrue(snapshot.events().isEmpty());
        }
    }

    @Test
    void moveRejectsNonFiniteAxes() {
        assertThrows(IllegalArgumentException.class, () -> new InputAction.Move(Double.NaN, 0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new InputAction.Move(0, Double.POSITIVE_INFINITY, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new InputAction.Move(Double.NEGATIVE_INFINITY, 0, 0.1));
    }

    @Test
    void positiveSubStepDurationProducesOneStepWhileZeroProducesNone() {
        List<ExplorationSnapshot> tinyMove = ExplorationInputTapeRunner.run(
                freshOpenSimulation(null), new InputTape(List.of(new InputAction.Move(1, 0, 0.001))));
        List<ExplorationSnapshot> zeroMove = ExplorationInputTapeRunner.run(
                freshOpenSimulation(null), new InputTape(List.of(new InputAction.Move(1, 0, 0))));

        assertEquals(1, tinyMove.size());
        assertTrue(zeroMove.isEmpty());
    }

    @Test
    void semanticCheckpointsReportTheFirstDivergenceWithActionAndState() {
        InputTape tape = new InputTape(List.of(
                new InputAction.Move(1, 0, 0.05),
                new InputAction.Wait(0.05)));
        ExplorationInputTrace trace = ExplorationInputTapeRunner.runTrace(freshOpenSimulation(null), tape);
        List<ExplorationSemanticCheckpoint> checkpoints = List.of(
                new ExplorationSemanticCheckpoint("wrong area", 0, "forest", false, null, null),
                new ExplorationSemanticCheckpoint("also wrong", 1, "village", false, null, null));

        ExplorationInputTrace.Divergence divergence = trace.firstDivergence(checkpoints).orElseThrow();
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> trace.requireMatches(checkpoints));

        assertEquals(1, divergence.stepNumber());
        assertEquals(0, divergence.actionIndex());
        assertEquals(divergence.message(), failure.getMessage());
        assertTrue(divergence.message().contains("Move"));
        assertTrue(divergence.message().contains("expected=wrong area {areaId=forest, dialogueActive=false}"));
        assertTrue(divergence.message().contains("obtained=areaId=start"));
    }
}
