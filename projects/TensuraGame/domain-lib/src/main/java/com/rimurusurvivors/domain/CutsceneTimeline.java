package com.rimurusurvivors.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Relogio deterministico de cutscene. Ele conhece somente tempo, cues e skip; camera,
 * audio, UI e atores continuam responsabilidade do adaptador da engine.
 */
public final class CutsceneTimeline {

    private final double durationSeconds;
    private final double skippableAfterSeconds;
    private final List<CutsceneCue> cues;

    private double elapsedSeconds;
    private int nextCueIndex;
    private boolean finished;
    private boolean completionPending;

    public CutsceneTimeline(double durationSeconds, double skippableAfterSeconds, List<CutsceneCue> cues) {
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be finite and positive.");
        }
        if (!Double.isFinite(skippableAfterSeconds)
                || skippableAfterSeconds < 0
                || skippableAfterSeconds > durationSeconds) {
            throw new IllegalArgumentException("Skip threshold must be inside the timeline.");
        }
        this.durationSeconds = durationSeconds;
        this.skippableAfterSeconds = skippableAfterSeconds;
        this.cues = validateCues(cues, durationSeconds);
    }

    public List<String> advance(double deltaSeconds) {
        if (finished || !Double.isFinite(deltaSeconds) || deltaSeconds <= 0) {
            return List.of();
        }
        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + deltaSeconds);
        List<String> crossed = new ArrayList<>();
        while (nextCueIndex < cues.size() && cues.get(nextCueIndex).atSeconds() <= elapsedSeconds) {
            crossed.add(cues.get(nextCueIndex).id());
            nextCueIndex++;
        }
        if (elapsedSeconds >= durationSeconds) {
            finish();
        }
        return List.copyOf(crossed);
    }

    public boolean requestSkip() {
        if (finished || elapsedSeconds < skippableAfterSeconds) {
            return false;
        }
        elapsedSeconds = durationSeconds;
        nextCueIndex = cues.size();
        finish();
        return true;
    }

    /** Retorna true uma unica vez, tanto no fim natural quanto no skip aceito. */
    public boolean consumeCompletion() {
        if (!completionPending) {
            return false;
        }
        completionPending = false;
        return true;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public double progress() {
        return elapsedSeconds / durationSeconds;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean canSkip() {
        return !finished && elapsedSeconds >= skippableAfterSeconds;
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        completionPending = true;
    }

    private static List<CutsceneCue> validateCues(List<CutsceneCue> source, double durationSeconds) {
        List<CutsceneCue> safe = source == null ? List.of() : List.copyOf(source);
        Set<String> ids = new HashSet<>();
        double previous = -1;
        for (CutsceneCue cue : safe) {
            if (cue == null) {
                throw new IllegalArgumentException("Cue cannot be null.");
            }
            if (!ids.add(cue.id())) {
                throw new IllegalArgumentException("Duplicate cue id: " + cue.id());
            }
            if (cue.atSeconds() < previous) {
                throw new IllegalArgumentException("Cues must be ordered by time.");
            }
            if (cue.atSeconds() > durationSeconds) {
                throw new IllegalArgumentException("Cue is outside the timeline: " + cue.id());
            }
            previous = cue.atSeconds();
        }
        return safe;
    }
}
