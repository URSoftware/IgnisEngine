package com.rimurusurvivors.domain;

import java.util.Set;

/** Requisitos e resultados de um projeto, sem referencias de apresentacao ou assets. */
public record ProjectDefinition(
        String id,
        TownResourceBundle requiredResources,
        String responsibleSpecialistId,
        String requiredMilestoneId,
        Set<String> visualSignals,
        Set<String> benefitSignals,
        boolean priorityChoice) {

    public ProjectDefinition {
        id = requireId(id, "Project");
        if (requiredResources == null) {
            throw new IllegalArgumentException("Project resources are required.");
        }
        responsibleSpecialistId = requireId(
                responsibleSpecialistId, "Project specialist");
        requiredMilestoneId = requireId(requiredMilestoneId, "Project milestone");
        visualSignals = NarrativeFlags.immutableIds(visualSignals, "Project visual signal");
        benefitSignals = NarrativeFlags.immutableIds(benefitSignals, "Project benefit signal");
        if (visualSignals.isEmpty() || benefitSignals.isEmpty()) {
            throw new IllegalArgumentException(
                    "Project must change both visual and functional state.");
        }
    }

    public boolean unlockRequirementsMet(
            Set<String> specialists, NarrativeFlags narrativeFlags) {
        if (specialists == null || narrativeFlags == null) {
            throw new IllegalArgumentException("Project unlock context is required.");
        }
        return specialists.contains(responsibleSpecialistId)
                && narrativeFlags.contains(requiredMilestoneId);
    }

    public boolean completionRequirementsMet(
            TownResourceBundle resources,
            Set<String> specialists,
            NarrativeFlags narrativeFlags) {
        return unlockRequirementsMet(specialists, narrativeFlags)
                && resources.covers(requiredResources);
    }

    private static String requireId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " id is required.");
        }
        return value;
    }
}
