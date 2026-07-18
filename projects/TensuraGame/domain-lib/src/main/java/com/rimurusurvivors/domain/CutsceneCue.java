package com.rimurusurvivors.domain;

/** Marco semantico disparado quando a timeline cruza {@code atSeconds}. */
public record CutsceneCue(String id, double atSeconds) {

    public CutsceneCue {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cue id is required.");
        }
        if (!Double.isFinite(atSeconds) || atSeconds < 0) {
            throw new IllegalArgumentException("Cue time must be finite and non-negative.");
        }
    }
}
