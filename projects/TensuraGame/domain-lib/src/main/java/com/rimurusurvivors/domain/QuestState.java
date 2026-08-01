package com.rimurusurvivors.domain;

import java.util.Map;

/** Estado imutavel e idempotente de uma missao. */
public record QuestState(String questId, QuestStatus status, QuestProgress progress) {

    public QuestState {
        if (questId == null || questId.isBlank()) {
            throw new IllegalArgumentException("Quest state id is required.");
        }
        if (status == null || progress == null) {
            throw new IllegalArgumentException("Quest status and progress are required.");
        }
    }

    public static QuestState locked(String questId) {
        return new QuestState(questId, QuestStatus.LOCKED, QuestProgress.empty());
    }

    public QuestState refreshAvailability(
            QuestDefinition definition,
            Map<String, QuestState> allStates,
            NarrativeFlags narrativeFlags) {
        requireDefinition(definition);
        if (status != QuestStatus.LOCKED
                || !definition.prerequisitesMet(allStates, narrativeFlags)) {
            return this;
        }
        return new QuestState(questId, QuestStatus.AVAILABLE, progress);
    }

    public QuestState accept() {
        if (status == QuestStatus.LOCKED) {
            throw new IllegalStateException("Locked quest cannot be accepted: " + questId);
        }
        if (status != QuestStatus.AVAILABLE) {
            return this;
        }
        return new QuestState(questId, QuestStatus.ACTIVE, progress);
    }

    public QuestState advance(QuestDefinition definition, String objectiveId, int amount) {
        requireDefinition(definition);
        if (status == QuestStatus.COMPLETED) {
            return this;
        }
        if (status != QuestStatus.ACTIVE) {
            throw new IllegalStateException("Quest must be active before progress: " + questId);
        }
        QuestProgress updatedProgress = progress.advance(
                definition.requireObjective(objectiveId), amount);
        QuestStatus updatedStatus = updatedProgress.completes(definition)
                ? QuestStatus.COMPLETED : QuestStatus.ACTIVE;
        return new QuestState(questId, updatedStatus, updatedProgress);
    }

    private void requireDefinition(QuestDefinition definition) {
        if (definition == null || !questId.equals(definition.id())) {
            throw new IllegalArgumentException("Quest definition does not match state " + questId);
        }
    }
}
