package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Contrato estavel de uma missao, composto apenas por dados de dominio. */
public record QuestDefinition(
        String id,
        Set<String> prerequisiteQuestIds,
        Set<String> requiredFlags,
        List<QuestObjective> objectives,
        Set<String> rewardFlags) {

    public QuestDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quest id is required.");
        }
        prerequisiteQuestIds = NarrativeFlags.immutableIds(
                prerequisiteQuestIds, "Prerequisite quest");
        requiredFlags = NarrativeFlags.immutableIds(requiredFlags, "Required flag");
        rewardFlags = NarrativeFlags.immutableIds(rewardFlags, "Reward flag");
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("Quest must contain at least one objective.");
        }
        Map<String, QuestObjective> uniqueObjectives = new LinkedHashMap<>();
        for (QuestObjective objective : objectives) {
            if (objective == null) {
                throw new IllegalArgumentException("Quest objectives must not contain null.");
            }
            if (uniqueObjectives.put(objective.id(), objective) != null) {
                throw new IllegalArgumentException(
                        "Duplicate quest objective id: " + objective.id());
            }
        }
    }

    public QuestObjective requireObjective(String objectiveId) {
        return objectives.stream()
                .filter(objective -> objective.id().equals(objectiveId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown objective " + objectiveId + " for quest " + id));
    }

    public boolean prerequisitesMet(
            Map<String, QuestState> questStates, NarrativeFlags narrativeFlags) {
        if (!narrativeFlags.values().containsAll(requiredFlags)) {
            return false;
        }
        for (String prerequisiteId : prerequisiteQuestIds) {
            QuestState prerequisite = questStates.get(prerequisiteId);
            if (prerequisite == null || prerequisite.status() != QuestStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }
}
