package com.rimurusurvivors.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Rastro deterministico com diagnostico da primeira divergencia semantica. */
public record ExplorationInputTrace(List<Step> steps) {

    public ExplorationInputTrace {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public Optional<Divergence> firstDivergence(List<ExplorationSemanticCheckpoint> checkpoints) {
        for (ExplorationSemanticCheckpoint checkpoint : checkpoints) {
            Step step = lastStepForAction(checkpoint.afterActionIndex());
            Divergence divergence = compare(checkpoint, step);
            if (divergence != null) {
                return Optional.of(divergence);
            }
        }
        return Optional.empty();
    }

    public void requireMatches(List<ExplorationSemanticCheckpoint> checkpoints) {
        firstDivergence(checkpoints).ifPresent(divergence -> {
            throw new IllegalStateException(divergence.message());
        });
    }

    private Step lastStepForAction(int actionIndex) {
        Step match = null;
        for (Step step : steps) {
            if (step.actionIndex() == actionIndex) {
                match = step;
            }
        }
        return match;
    }

    private static Divergence compare(ExplorationSemanticCheckpoint expected, Step step) {
        if (step == null) {
            return new Divergence(0, expected.afterActionIndex(), null,
                    describeExpected(expected), "no simulated step");
        }
        ExplorationSnapshot actual = step.snapshot();
        if (expected.areaId() != null && !Objects.equals(expected.areaId(), actual.areaId())) {
            return divergent(step, expected, "areaId=" + actual.areaId());
        }
        if (expected.dialogueActive() != null && expected.dialogueActive() != actual.dialogueActive()) {
            return divergent(step, expected, "dialogueActive=" + actual.dialogueActive());
        }
        if (expected.eventType() != null && actual.events().stream().noneMatch(event ->
                event.type() == expected.eventType()
                        && (expected.eventDetail() == null || Objects.equals(expected.eventDetail(), event.detail())))) {
            return divergent(step, expected, "events=" + actual.events());
        }
        return null;
    }

    private static Divergence divergent(
            Step step, ExplorationSemanticCheckpoint expected, String actual) {
        return new Divergence(step.stepNumber(), step.actionIndex(), step.action(),
                describeExpected(expected), actual);
    }

    private static String describeExpected(ExplorationSemanticCheckpoint checkpoint) {
        StringBuilder description = new StringBuilder(checkpoint.name()).append(" {");
        if (checkpoint.areaId() != null) {
            description.append("areaId=").append(checkpoint.areaId()).append(", ");
        }
        if (checkpoint.dialogueActive() != null) {
            description.append("dialogueActive=").append(checkpoint.dialogueActive()).append(", ");
        }
        if (checkpoint.eventType() != null) {
            description.append("event=").append(checkpoint.eventType());
            if (checkpoint.eventDetail() != null) {
                description.append(':').append(checkpoint.eventDetail());
            }
            description.append(", ");
        }
        description.setLength(description.length() - 2);
        return description.append('}').toString();
    }

    public record Step(int stepNumber, int actionIndex, InputAction action, ExplorationSnapshot snapshot) {
    }

    public record Divergence(
            int stepNumber, int actionIndex, InputAction action, String expected, String actual) {

        public String message() {
            return "InputTape divergence at step=" + stepNumber
                    + " action=" + actionIndex + " " + action
                    + ": expected=" + expected + " obtained=" + actual;
        }
    }
}
