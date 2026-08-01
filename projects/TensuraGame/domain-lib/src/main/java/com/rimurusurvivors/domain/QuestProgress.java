package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Contadores imutaveis dos objetivos de uma missao. */
public record QuestProgress(Map<String, Integer> objectiveCounts) {

    public QuestProgress {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
        if (objectiveCounts != null) {
            objectiveCounts.forEach((objectiveId, count) -> {
                if (objectiveId == null || objectiveId.isBlank()) {
                    throw new IllegalArgumentException("Quest progress objective id is required.");
                }
                if (count == null || count < 0) {
                    throw new IllegalArgumentException(
                            "Quest objective progress must be non-negative.");
                }
                copy.put(objectiveId, count);
            });
        }
        objectiveCounts = Map.copyOf(copy);
    }

    public static QuestProgress empty() {
        return new QuestProgress(Map.of());
    }

    public int count(String objectiveId) {
        return objectiveCounts.getOrDefault(objectiveId, 0);
    }

    public QuestProgress advance(QuestObjective objective, int amount) {
        if (objective == null) {
            throw new IllegalArgumentException("Quest objective is required.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("Quest progress amount must be positive.");
        }
        int current = count(objective.id());
        int updatedCount = Math.min(objective.requiredCount(), current + amount);
        if (updatedCount == current) {
            return this;
        }
        LinkedHashMap<String, Integer> updated = new LinkedHashMap<>(objectiveCounts);
        updated.put(objective.id(), updatedCount);
        return new QuestProgress(updated);
    }

    public boolean completes(QuestDefinition definition) {
        for (QuestObjective objective : definition.objectives()) {
            if (count(objective.id()) < objective.requiredCount()) {
                return false;
            }
        }
        return true;
    }
}
