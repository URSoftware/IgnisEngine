package com.rimurusurvivors.domain;

import java.util.Set;

/** Tarefa delegada que progride por marco narrativo, sem relogio real. */
public record DelegatedExpedition(
        String id,
        String leaderId,
        String objectiveId,
        String resolutionMilestoneId,
        DelegatedExpeditionStatus status,
        boolean demonstrated,
        TownResourceBundle resultResources,
        Set<String> resultDiscoveries) {

    public DelegatedExpedition {
        id = requireId(id, "Expedition");
        leaderId = requireId(leaderId, "Expedition leader");
        objectiveId = requireId(objectiveId, "Expedition objective");
        resolutionMilestoneId = requireId(
                resolutionMilestoneId, "Expedition resolution milestone");
        if (status == null || resultResources == null) {
            throw new IllegalArgumentException("Expedition status and result are required.");
        }
        resultDiscoveries = NarrativeFlags.immutableIds(
                resultDiscoveries, "Expedition discovery");
    }

    public static DelegatedExpedition planned(
            String id,
            String leaderId,
            String objectiveId,
            String resolutionMilestoneId,
            TownResourceBundle resultResources,
            Set<String> resultDiscoveries) {
        return new DelegatedExpedition(
                id,
                leaderId,
                objectiveId,
                resolutionMilestoneId,
                DelegatedExpeditionStatus.PLANNED,
                false,
                resultResources,
                resultDiscoveries);
    }

    public DelegatedExpedition begin(boolean automatic) {
        if (automatic && !demonstrated) {
            throw new IllegalStateException(
                    "Expedition must be demonstrated before automatic delegation.");
        }
        if (status == DelegatedExpeditionStatus.IN_PROGRESS) {
            return this;
        }
        if (status == DelegatedExpeditionStatus.COMPLETED) {
            throw new IllegalStateException("Completed expedition must be repeated explicitly.");
        }
        return withStatus(DelegatedExpeditionStatus.IN_PROGRESS, demonstrated);
    }

    public DelegatedExpedition resolveAtMilestone(String milestoneId) {
        if (milestoneId == null || milestoneId.isBlank()) {
            throw new IllegalArgumentException("Expedition milestone is required.");
        }
        if (status == DelegatedExpeditionStatus.COMPLETED
                || !resolutionMilestoneId.equals(milestoneId)) {
            return this;
        }
        if (status != DelegatedExpeditionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Planned expedition cannot resolve.");
        }
        return withStatus(DelegatedExpeditionStatus.COMPLETED, true);
    }

    public DelegatedExpedition repeatAutomatically() {
        if (status != DelegatedExpeditionStatus.COMPLETED || !demonstrated) {
            throw new IllegalStateException(
                    "Only a demonstrated expedition can repeat automatically.");
        }
        return withStatus(DelegatedExpeditionStatus.IN_PROGRESS, true);
    }

    private DelegatedExpedition withStatus(
            DelegatedExpeditionStatus nextStatus, boolean nextDemonstrated) {
        return new DelegatedExpedition(
                id,
                leaderId,
                objectiveId,
                resolutionMilestoneId,
                nextStatus,
                nextDemonstrated,
                resultResources,
                resultDiscoveries);
    }

    private static String requireId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " id is required.");
        }
        return value;
    }
}
