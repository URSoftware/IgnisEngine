package com.rimurusurvivors.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplica uma {@link InputTape} sobre uma {@link ExplorationSimulation} em
 * passos fixos, no mesmo tamanho de passo que a simulacao ja usa internamente
 * ({@code ExplorationSimulation} clampa dt em 0.05s). Deterministico: mesma
 * fita sobre a mesma simulacao recem-construida sempre produz a mesma
 * sequencia de snapshots — sem relogio real, sem {@code Math.random()}.
 */
public final class ExplorationInputTapeRunner {

    private static final double STEP_SECONDS = 0.05;

    private ExplorationInputTapeRunner() {
    }

    /** Roda a fita inteira e retorna um snapshot por passo simulado, na ordem. */
    public static List<ExplorationSnapshot> run(ExplorationSimulation simulation, InputTape tape) {
        return runTrace(simulation, tape).steps().stream()
                .map(ExplorationInputTrace.Step::snapshot)
                .toList();
    }

    /** Roda a fita preservando a acao de origem de cada passo para diagnostico. */
    public static ExplorationInputTrace runTrace(ExplorationSimulation simulation, InputTape tape) {
        List<ExplorationInputTrace.Step> trace = new ArrayList<>();
        for (int actionIndex = 0; actionIndex < tape.actions().size(); actionIndex++) {
            InputAction action = tape.actions().get(actionIndex);
            if (action instanceof InputAction.Move move) {
                RunInput direction = new RunInput(move.horizontal(), move.vertical());
                runSteps(simulation, trace, actionIndex, action, move.durationSeconds(), direction, false);
            } else if (action instanceof InputAction.Wait wait) {
                runSteps(simulation, trace, actionIndex, action, wait.durationSeconds(), RunInput.NONE, false);
            } else if (action instanceof InputAction.Interact || action instanceof InputAction.AdvanceDialogue) {
                addStep(simulation, trace, actionIndex, action, RunInput.NONE, true);
            } else {
                throw new IllegalStateException("Unknown input action: " + action);
            }
        }
        return new ExplorationInputTrace(trace);
    }

    private static void runSteps(
            ExplorationSimulation simulation, List<ExplorationInputTrace.Step> trace,
            int actionIndex, InputAction action, double durationSeconds,
            RunInput direction, boolean interactPressed) {
        int steps = durationSeconds == 0
                ? 0
                : Math.max(1, (int) Math.round(durationSeconds / STEP_SECONDS));
        for (int i = 0; i < steps; i++) {
            addStep(simulation, trace, actionIndex, action, direction, interactPressed);
        }
    }

    private static void addStep(
            ExplorationSimulation simulation, List<ExplorationInputTrace.Step> trace,
            int actionIndex, InputAction action, RunInput direction, boolean interactPressed) {
        ExplorationSnapshot snapshot = simulation.update(STEP_SECONDS, direction, interactPressed);
        trace.add(new ExplorationInputTrace.Step(trace.size() + 1, actionIndex, action, snapshot));
    }
}
