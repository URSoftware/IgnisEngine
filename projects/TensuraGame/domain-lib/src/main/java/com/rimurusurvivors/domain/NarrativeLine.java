package com.rimurusurvivors.domain;

/** One authored line in a data-driven narrative sequence. */
public record NarrativeLine(String id, String speaker, String portrait, String text) {

    public NarrativeLine {
        id = requireText(id, "Line id");
        speaker = requireText(speaker, "Speaker");
        portrait = portrait == null ? "" : portrait.trim();
        text = requireText(text, "Line text");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return value.trim();
    }
}
