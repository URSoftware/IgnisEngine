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
        List<ExplorationSnapshot> trace = new ArrayList<>();
        for (InputAction action : tape.actions()) {
            if (action instanceof InputAction.Move move) {
                RunInput direction = new RunInput(move.horizontal(), move.vertical());
                runSteps(simulation, trace, move.durationSeconds(), direction, false);
            } else if (action instanceof InputAction.Wait wait) {
                runSteps(simulation, trace, wait.durationSeconds(), RunInput.NONE, false);
            } else if (action instanceof InputAction.Interact || action instanceof InputAction.AdvanceDialogue) {
                trace.add(simulation.update(STEP_SECONDS, RunInput.NONE, true));
            } else {
                throw new IllegalStateException("Unknown input action: " + action);
            }
        }
        return trace;
    }

    private static void runSteps(
            ExplorationSimulation simulation, List<ExplorationSnapshot> trace,
            double durationSeconds, RunInput direction, boolean interactPressed) {
        int steps = (int) Math.round(durationSeconds / STEP_SECONDS);
        for (int i = 0; i < steps; i++) {
            trace.add(simulation.update(STEP_SECONDS, direction, interactPressed));
        }
    }
}
