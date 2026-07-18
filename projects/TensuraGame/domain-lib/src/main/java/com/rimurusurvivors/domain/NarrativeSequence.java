package com.rimurusurvivors.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic state machine for dialogue/cutscene beats. Rendering, audio and
 * input mapping stay in the engine adapter; this class only owns progression.
 */
public final class NarrativeSequence {

    private final List<NarrativeBeat> beats;
    private final double skippableAfterSeconds;

    private int beatIndex;
    private int lineIndex;
    private double elapsedSeconds;
    private double beatElapsedSeconds;
    private boolean finished;
    private boolean completionPending;

    public NarrativeSequence(List<NarrativeBeat> beats, double skippableAfterSeconds) {
        this.beats = validateBeats(beats);
        if (!Double.isFinite(skippableAfterSeconds) || skippableAfterSeconds < 0) {
            throw new IllegalArgumentException("Skip threshold must be finite and non-negative.");
        }
        this.skippableAfterSeconds = skippableAfterSeconds;
        this.lineIndex = this.beats.get(0).lines().isEmpty() ? -1 : 0;
    }

    public void update(double deltaSeconds, boolean advancePressed) {
        if (finished) return;
        if (Double.isFinite(deltaSeconds) && deltaSeconds > 0) {
            elapsedSeconds += deltaSeconds;
            beatElapsedSeconds += deltaSeconds;
        }
        NarrativeBeat beat = currentBeat();
        if (beat.isAutomatic()) {
            if (beatElapsedSeconds >= beat.autoDurationSeconds()) {
                advanceStep();
            }
        } else if (advancePressed) {
            advanceStep();
        }
    }

    public boolean requestSkip() {
        if (finished || elapsedSeconds < skippableAfterSeconds) return false;
        finish();
        return true;
    }

    public NarrativeBeat currentBeat() {
        return beats.get(Math.min(beatIndex, beats.size() - 1));
    }

    public NarrativeLine currentLine() {
        if (finished || lineIndex < 0) return null;
        return currentBeat().lines().get(lineIndex);
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean canSkip() {
        return !finished && elapsedSeconds >= skippableAfterSeconds;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Returns true exactly once after natural completion or an accepted skip. */
    public boolean consumeCompletion() {
        if (!completionPending) return false;
        completionPending = false;
        return true;
    }

    private void advanceStep() {
        NarrativeBeat beat = currentBeat();
        if (lineIndex >= 0 && lineIndex + 1 < beat.lines().size()) {
            lineIndex++;
            beatElapsedSeconds = 0;
            return;
        }
        if (beatIndex + 1 >= beats.size()) {
            finish();
            return;
        }
        beatIndex++;
        NarrativeBeat next = currentBeat();
        lineIndex = next.lines().isEmpty() ? -1 : 0;
        beatElapsedSeconds = 0;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        completionPending = true;
    }

    private static List<NarrativeBeat> validateBeats(List<NarrativeBeat> source) {
        List<NarrativeBeat> safe = source == null ? List.of() : List.copyOf(source);
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("A narrative sequence needs at least one beat.");
        }
        Set<String> beatIds = new HashSet<>();
        Set<String> lineIds = new HashSet<>();
        for (NarrativeBeat beat : safe) {
            if (beat == null) throw new IllegalArgumentException("Beat cannot be null.");
            if (!beatIds.add(beat.id())) {
                throw new IllegalArgumentException("Duplicate beat id: " + beat.id());
            }
            for (NarrativeLine line : beat.lines()) {
                if (line == null) throw new IllegalArgumentException("Line cannot be null.");
                if (!lineIds.add(line.id())) {
                    throw new IllegalArgumentException("Duplicate line id: " + line.id());
                }
            }
        }
        return safe;
    }
}
