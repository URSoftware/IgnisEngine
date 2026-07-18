package com.rimurusurvivors.domain;

import java.util.List;

/** A visual/narrative beat that can advance by input or by an authored duration. */
public record NarrativeBeat(String id, double autoDurationSeconds, List<NarrativeLine> lines) {

    public NarrativeBeat {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Beat id cannot be blank.");
        }
        if (!Double.isFinite(autoDurationSeconds) || autoDurationSeconds < 0) {
            throw new IllegalArgumentException("Auto duration must be finite and non-negative.");
        }
        id = id.trim();
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (lines.isEmpty() && autoDurationSeconds <= 0) {
            throw new IllegalArgumentException("A beat needs dialogue or a positive auto duration.");
        }
    }

    public boolean isAutomatic() {
        return autoDurationSeconds > 0;
    }
}
