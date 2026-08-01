package com.rimurusurvivors.domain;

/** Objetivo semantico contado pelo dominio, sem referencia a cena ou asset. */
public record QuestObjective(String id, int requiredCount) {

    public QuestObjective {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest objective id is required.");
        }
        if (requiredCount < 1) {
            throw new IllegalArgumentException("Quest objective count must be positive.");
        }
    }
}
