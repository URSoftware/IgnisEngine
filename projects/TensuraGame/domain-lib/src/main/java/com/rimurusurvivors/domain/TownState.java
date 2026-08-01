package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Estado persistente de Tempest e regras do primeiro Conselho. */
public record TownState(
        TownStage stage,
        TownResourceBundle resources,
        Set<String> specialists,
        Map<String, TownProjectState> projects,
        int trust,
        String prioritizedProjectId,
        Set<String> discoveries,
        Set<String> processedReportIds,
        Set<String> emittedSignals,
        Map<String, DelegatedExpedition> expeditions) {

    public TownState {
        if (stage == null || resources == null || projects == null || expeditions == null) {
            throw new IllegalArgumentException("Town state fields are required.");
        }
        if (trust < 0) {
            throw new IllegalArgumentException("Town trust must not be negative.");
        }
        specialists = NarrativeFlags.immutableIds(specialists, "Town specialist");
        discoveries = NarrativeFlags.immutableIds(discoveries, "Town discovery");
        processedReportIds = NarrativeFlags.immutableIds(
                processedReportIds, "Town processed report");
        emittedSignals = NarrativeFlags.immutableIds(emittedSignals, "Town signal");
        projects = validateProjects(projects);
        expeditions = validateExpeditions(expeditions);
        if (prioritizedProjectId != null) {
            ProjectDefinition definition =
                    TownProjects.requireDefinition(prioritizedProjectId);
            if (!definition.priorityChoice()) {
                throw new IllegalArgumentException(
                        "Town priority must reference a priority project.");
            }
        }
    }

    public static TownState initial(NarrativeFlags narrativeFlags) {
        if (narrativeFlags == null) {
            throw new IllegalArgumentException("Narrative flags are required.");
        }
        TownStage stage = narrativeFlags.contains("ranga_naming_complete")
                ? TownStage.GOBLIN_VILLAGE
                : TownStage.CAMP;
        return new TownState(
                stage,
                TownResourceBundle.EMPTY,
                Set.of(),
                TownProjects.initialStates(),
                0,
                null,
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of());
    }

    public TownState applyReturnReport(
            ReturnReport report, NarrativeFlags narrativeFlags) {
        if (report == null || narrativeFlags == null) {
            throw new IllegalArgumentException("Return report context is required.");
        }
        if (processedReportIds.contains(report.id())) {
            return this;
        }
        LinkedHashSet<String> updatedReports = new LinkedHashSet<>(processedReportIds);
        updatedReports.add(report.id());
        LinkedHashSet<String> updatedSpecialists = new LinkedHashSet<>(specialists);
        updatedSpecialists.addAll(report.people());
        LinkedHashSet<String> updatedDiscoveries = new LinkedHashSet<>(discoveries);
        updatedDiscoveries.addAll(report.discoveries());
        TownState reported = new TownState(
                stage,
                resources.add(report.resources()),
                updatedSpecialists,
                projects,
                Math.addExact(trust, report.trustGained()),
                prioritizedProjectId,
                updatedDiscoveries,
                updatedReports,
                emittedSignals,
                expeditions);
        return reported.refreshAvailability(narrativeFlags);
    }

    public TownState refreshAvailability(NarrativeFlags narrativeFlags) {
        if (narrativeFlags == null) {
            throw new IllegalArgumentException("Narrative flags are required.");
        }
        LinkedHashMap<String, TownProjectState> updated = new LinkedHashMap<>(projects);
        boolean changed = false;
        for (ProjectDefinition definition : TownProjects.definitions().values()) {
            TownProjectState state = updated.get(definition.id());
            if (state.status() == TownProjectStatus.LOCKED
                    && definition.unlockRequirementsMet(specialists, narrativeFlags)) {
                updated.put(definition.id(), state.makeAvailable());
                changed = true;
            }
        }
        return changed ? withProjects(updated) : this;
    }

    public TownState synchronizeNarrative(NarrativeFlags narrativeFlags) {
        if (narrativeFlags == null) {
            throw new IllegalArgumentException("Narrative flags are required.");
        }
        TownStage narrativeStage = narrativeFlags.contains("ranga_naming_complete")
                ? TownStage.GOBLIN_VILLAGE
                : TownStage.CAMP;
        TownState staged = narrativeStage.ordinal() > stage.ordinal()
                ? new TownState(
                        narrativeStage,
                        resources,
                        specialists,
                        projects,
                        trust,
                        prioritizedProjectId,
                        discoveries,
                        processedReportIds,
                        emittedSignals,
                        expeditions)
                : this;
        return staged.refreshAvailability(narrativeFlags);
    }

    public TownState prioritize(String projectId) {
        ProjectDefinition definition = TownProjects.requireDefinition(projectId);
        TownProjectState state = projects.get(projectId);
        if (!definition.priorityChoice()
                || (state.status() != TownProjectStatus.AVAILABLE
                && state.status() != TownProjectStatus.IN_PROGRESS)) {
            throw new IllegalStateException("Town project cannot be prioritized: " + projectId);
        }
        if (projectId.equals(prioritizedProjectId)) {
            return this;
        }
        LinkedHashSet<String> signals = new LinkedHashSet<>(emittedSignals);
        signals.removeIf(signal -> signal.startsWith("town_priority_"));
        signals.add("town_priority_" + projectId);
        return new TownState(
                stage,
                resources,
                specialists,
                projects,
                trust,
                projectId,
                discoveries,
                processedReportIds,
                signals,
                expeditions);
    }

    public TownState startPrioritizedProject(NarrativeFlags narrativeFlags) {
        ProjectDefinition definition = requirePrioritizedDefinition();
        TownProjectState state = projects.get(definition.id());
        if (state.status() == TownProjectStatus.IN_PROGRESS
                || state.status() == TownProjectStatus.COMPLETED) {
            return this;
        }
        requireCompletionRequirements(definition, narrativeFlags);
        return withProject(state.start());
    }

    public TownState completePrioritizedProject(NarrativeFlags narrativeFlags) {
        ProjectDefinition definition = requirePrioritizedDefinition();
        TownProjectState state = projects.get(definition.id());
        if (state.status() == TownProjectStatus.COMPLETED) {
            return this;
        }
        if (state.status() != TownProjectStatus.IN_PROGRESS) {
            throw new IllegalStateException("Town project must start before completion.");
        }
        requireCompletionRequirements(definition, narrativeFlags);
        LinkedHashSet<String> signals = new LinkedHashSet<>(emittedSignals);
        signals.addAll(definition.visualSignals());
        signals.addAll(definition.benefitSignals());
        LinkedHashMap<String, TownProjectState> updatedProjects =
                new LinkedHashMap<>(projects);
        updatedProjects.put(definition.id(), state.complete());
        return new TownState(
                stage,
                resources.subtract(definition.requiredResources()),
                specialists,
                updatedProjects,
                Math.addExact(trust, 1),
                prioritizedProjectId,
                discoveries,
                processedReportIds,
                signals,
                expeditions);
    }

    public TownState scheduleExpedition(DelegatedExpedition expedition) {
        if (expedition == null) {
            throw new IllegalArgumentException("Delegated expedition is required.");
        }
        DelegatedExpedition existing = expeditions.get(expedition.id());
        if (existing != null) {
            return existing.equals(expedition) ? this : rejectDuplicateExpedition(expedition.id());
        }
        LinkedHashMap<String, DelegatedExpedition> updated =
                new LinkedHashMap<>(expeditions);
        updated.put(expedition.id(), expedition);
        return withExpeditions(updated);
    }

    public TownState beginExpedition(String expeditionId, boolean automatic) {
        DelegatedExpedition expedition = requireExpedition(expeditionId);
        DelegatedExpedition started = expedition.begin(automatic);
        return started.equals(expedition) ? this : withExpedition(started);
    }

    public TownState resolveExpedition(
            String expeditionId,
            String milestoneId,
            NarrativeFlags narrativeFlags) {
        DelegatedExpedition expedition = requireExpedition(expeditionId);
        DelegatedExpedition resolved = expedition.resolveAtMilestone(milestoneId);
        if (resolved.equals(expedition)) {
            return this;
        }
        TownState updated = withExpedition(resolved);
        ReturnReport report = new ReturnReport(
                "expedition:" + expeditionId,
                resolved.resultResources(),
                resolved.resultDiscoveries(),
                Set.of(),
                0);
        return updated.applyReturnReport(report, narrativeFlags);
    }

    public DelegatedExpedition requireExpedition(String expeditionId) {
        DelegatedExpedition expedition = expeditions.get(expeditionId);
        if (expedition == null) {
            throw new IllegalArgumentException("Unknown delegated expedition: " + expeditionId);
        }
        return expedition;
    }

    private void requireCompletionRequirements(
            ProjectDefinition definition, NarrativeFlags narrativeFlags) {
        if (!definition.completionRequirementsMet(
                resources, specialists, narrativeFlags)) {
            throw new IllegalStateException(
                    "Town project requirements are not met: " + definition.id());
        }
    }

    private ProjectDefinition requirePrioritizedDefinition() {
        if (prioritizedProjectId == null) {
            throw new IllegalStateException("Town has no prioritized project.");
        }
        return TownProjects.requireDefinition(prioritizedProjectId);
    }

    private TownState withProject(TownProjectState state) {
        LinkedHashMap<String, TownProjectState> updated = new LinkedHashMap<>(projects);
        updated.put(state.projectId(), state);
        return withProjects(updated);
    }

    private TownState withProjects(Map<String, TownProjectState> updatedProjects) {
        return new TownState(
                stage,
                resources,
                specialists,
                updatedProjects,
                trust,
                prioritizedProjectId,
                discoveries,
                processedReportIds,
                emittedSignals,
                expeditions);
    }

    private TownState withExpedition(DelegatedExpedition expedition) {
        LinkedHashMap<String, DelegatedExpedition> updated =
                new LinkedHashMap<>(expeditions);
        updated.put(expedition.id(), expedition);
        return withExpeditions(updated);
    }

    private TownState withExpeditions(
            Map<String, DelegatedExpedition> updatedExpeditions) {
        return new TownState(
                stage,
                resources,
                specialists,
                projects,
                trust,
                prioritizedProjectId,
                discoveries,
                processedReportIds,
                emittedSignals,
                updatedExpeditions);
    }

    private TownState rejectDuplicateExpedition(String expeditionId) {
        throw new IllegalArgumentException(
                "Delegated expedition id already exists: " + expeditionId);
    }

    private static Map<String, TownProjectState> validateProjects(
            Map<String, TownProjectState> source) {
        if (!source.keySet().equals(TownProjects.definitions().keySet())) {
            throw new IllegalArgumentException(
                    "Town state must contain every canonical project exactly once.");
        }
        LinkedHashMap<String, TownProjectState> validated = new LinkedHashMap<>();
        TownProjects.definitions().keySet().forEach(projectId -> {
            TownProjectState state = source.get(projectId);
            if (state == null || !projectId.equals(state.projectId())) {
                throw new IllegalArgumentException(
                        "Town project state does not match " + projectId);
            }
            validated.put(projectId, state);
        });
        return Map.copyOf(validated);
    }

    private static Map<String, DelegatedExpedition> validateExpeditions(
            Map<String, DelegatedExpedition> source) {
        LinkedHashMap<String, DelegatedExpedition> validated = new LinkedHashMap<>();
        source.forEach((id, expedition) -> {
            if (id == null || id.isBlank() || expedition == null
                    || !id.equals(expedition.id())) {
                throw new IllegalArgumentException("Invalid delegated expedition entry.");
            }
            validated.put(id, expedition);
        });
        return Map.copyOf(validated);
    }
}
