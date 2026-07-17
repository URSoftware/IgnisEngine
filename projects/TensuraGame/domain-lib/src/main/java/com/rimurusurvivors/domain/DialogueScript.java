package com.rimurusurvivors.domain;

import java.util.List;

/** Sequencia de falas identificada por id, carregada de dados (ver contrato do vault). */
public record DialogueScript(String id, List<DialogueLine> lines) {

    public DialogueScript {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dialogue id is required.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Dialogue needs at least one line.");
        }
        lines = List.copyOf(lines);
    }

    public DialogueLine line(int index) {
        return lines.get(index);
    }

    public int lineCount() {
        return lines.size();
    }
}
