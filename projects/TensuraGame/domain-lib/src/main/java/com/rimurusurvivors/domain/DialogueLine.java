package com.rimurusurvivors.domain;

/** Uma fala de um dialogo. Speaker vazio identifica o Grande Sabio (sem retrato). */
public record DialogueLine(String speaker, String text) {

    public DialogueLine {
        if (speaker == null) {
            speaker = "";
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Dialogue line text is required.");
        }
    }
}
