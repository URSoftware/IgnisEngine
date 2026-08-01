package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Estado agregado da campanha: checkpoint, narrativa e diario de missoes. */
public record CampaignState(
        CampaignSnapshot checkpoint,
        NarrativeFlags narrativeFlags,
        Map<String, QuestState> quests,
        TownState townState) {

    /**
     * Marcador transitorio que o fluxo antigo gravava dentro dos marcos concluidos para
     * sinalizar chegada pendente na floresta. Nunca foi um marco: nenhum script ou regra
     * o consulta. Continua sendo removido aqui porque saves legados o carregam e a regra
     * estrita de checkpoint o interpretaria como marco revogado.
     */
    public static final String LEGACY_FOREST_ARRIVAL_PENDING = "forest_arrival_pending";

    public CampaignState {
        if (checkpoint == null || narrativeFlags == null
                || quests == null || townState == null) {
            throw new IllegalArgumentException("Campaign state fields are required.");
        }
        checkpoint = withoutLegacyMarker(checkpoint);
        narrativeFlags = narrativeFlags.without(LEGACY_FOREST_ARRIVAL_PENDING);
        if (checkpoint.schemaVersion() != CampaignSnapshot.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Campaign state must use the current schema.");
        }
        if (!narrativeFlags.values().containsAll(checkpoint.completedMilestones())) {
            throw new IllegalArgumentException(
                    "Narrative flags must include every completed milestone.");
        }
        if (!quests.keySet().equals(CampaignQuests.definitions().keySet())) {
            throw new IllegalArgumentException(
                    "Campaign state must contain every canonical quest exactly once.");
        }

        LinkedHashMap<String, QuestState> validated = new LinkedHashMap<>();
        for (Map.Entry<String, QuestDefinition> entry
                : CampaignQuests.definitions().entrySet()) {
            String questId = entry.getKey();
            QuestDefinition definition = entry.getValue();
            QuestState state = quests.get(questId);
            validateQuestState(definition, state);
            validated.put(questId, state);
        }
        for (Map.Entry<String, QuestDefinition> entry
                : CampaignQuests.definitions().entrySet()) {
            QuestState state = validated.get(entry.getKey());
            if (state.status() != QuestStatus.LOCKED
                    && !entry.getValue().prerequisitesMet(validated, narrativeFlags)) {
                throw new IllegalArgumentException(
                        "Unlocked quest has unmet prerequisites: " + entry.getKey());
            }
        }
        quests = Map.copyOf(validated);
    }

    public CampaignState(
            CampaignSnapshot checkpoint,
            NarrativeFlags narrativeFlags,
            Map<String, QuestState> quests) {
        this(checkpoint, narrativeFlags, quests, TownState.initial(narrativeFlags));
    }

    public static CampaignState fromSnapshot(CampaignSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Campaign snapshot is required.");
        }
        NarrativeFlags flags = new NarrativeFlags(
                chapterFor(snapshot.completedMilestones()),
                snapshot.completedMilestones());
        CampaignState initial = new CampaignState(
                snapshot,
                flags,
                CampaignQuests.initialStates(flags),
                TownState.initial(flags));
        return initial.synchronizeMilestoneProgress().normalize();
    }

    public CampaignState withCheckpoint(CampaignSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Campaign snapshot is required.");
        }
        // Compara os dois lados ja normalizados: o marcador transitorio legado nao conta
        // como marco, mas qualquer marco real ausente continua sendo revogacao.
        CampaignSnapshot candidate = withoutLegacyMarker(snapshot);
        if (!candidate.completedMilestones().containsAll(
                checkpoint.completedMilestones())) {
            throw new IllegalArgumentException(
                    "Campaign checkpoint cannot revoke completed milestones.");
        }
        NarrativeFlags updatedFlags = narrativeFlags.withFlags(
                candidate.completedMilestones());
        updatedFlags = updatedFlags.withChapter(chapterFor(updatedFlags.values()));
        return new CampaignState(
                candidate,
                updatedFlags,
                quests,
                townState.synchronizeNarrative(updatedFlags))
                .synchronizeMilestoneProgress()
                .normalize();
    }

    public CampaignState apply(CampaignCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Campaign command is required.");
        }
        if (command instanceof CampaignCommand.ReachMilestone milestone) {
            return reachMilestone(milestone.milestoneId());
        }
        if (command instanceof CampaignCommand.ChooseDialogue dialogue) {
            return chooseDialogue(dialogue.choice());
        }
        if (command instanceof CampaignCommand.AcceptQuest accept) {
            return acceptQuest(accept.questId()).normalize();
        }
        CampaignCommand.AdvanceObjective advance = (CampaignCommand.AdvanceObjective) command;
        return advanceObjective(
                advance.questId(), advance.objectiveId(), advance.amount(), false).normalize();
    }

    public CampaignState apply(TownCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Town command is required.");
        }
        if (command instanceof TownCommand.ApplyReturnReport report) {
            return applyReturnReport(report.report());
        }
        if (command instanceof TownCommand.PrioritizeProject prioritize) {
            return prioritizeTownProject(prioritize.projectId());
        }
        if (command instanceof TownCommand.StartPrioritizedProject) {
            return startPrioritizedTownProject();
        }
        if (command instanceof TownCommand.CompletePrioritizedProject) {
            return completePrioritizedTownProject();
        }
        if (command instanceof TownCommand.ScheduleExpedition schedule) {
            return scheduleDelegatedExpedition(schedule.expedition());
        }
        if (command instanceof TownCommand.BeginExpedition begin) {
            return beginDelegatedExpedition(begin.expeditionId(), begin.automatic());
        }
        TownCommand.ResolveExpedition resolve = (TownCommand.ResolveExpedition) command;
        return resolveDelegatedExpedition(resolve.expeditionId(), resolve.milestoneId());
    }

    public QuestState requireQuest(String questId) {
        QuestState state = quests.get(questId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown campaign quest: " + questId);
        }
        return state;
    }

    public CampaignState applyReturnReport(ReturnReport report) {
        TownState updated = townState.applyReturnReport(report, narrativeFlags);
        return updated.equals(townState) ? this : withTownState(updated);
    }

    public CampaignState prioritizeTownProject(String projectId) {
        return withTownState(townState.prioritize(projectId));
    }

    public CampaignState startPrioritizedTownProject() {
        return withTownState(townState.startPrioritizedProject(narrativeFlags));
    }

    public CampaignState completePrioritizedTownProject() {
        return withTownState(townState.completePrioritizedProject(narrativeFlags));
    }

    public CampaignState scheduleDelegatedExpedition(DelegatedExpedition expedition) {
        return withTownState(townState.scheduleExpedition(expedition));
    }

    public CampaignState beginDelegatedExpedition(
            String expeditionId, boolean automatic) {
        return withTownState(townState.beginExpedition(expeditionId, automatic));
    }

    public CampaignState resolveDelegatedExpedition(
            String expeditionId, String milestoneId) {
        return withTownState(
                townState.resolveExpedition(expeditionId, milestoneId, narrativeFlags));
    }

    private CampaignState reachMilestone(String milestoneId) {
        if (checkpoint.completedMilestones().contains(milestoneId)) {
            return this;
        }
        LinkedHashSet<String> milestones = new LinkedHashSet<>(
                checkpoint.completedMilestones());
        milestones.add(milestoneId);
        CampaignSnapshot updatedCheckpoint = new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                checkpoint.areaId(),
                checkpoint.playerX(),
                checkpoint.playerY(),
                milestones);
        return withCheckpoint(updatedCheckpoint);
    }

    private CampaignState chooseDialogue(CampaignChoice choice) {
        CampaignState updated = withNarrativeFlags(
                narrativeFlags.withFlag(choice.flagId()));
        return switch (choice) {
            case ASSESS_VILLAGE_NEEDS -> updated
                    .advanceObjective(
                            CampaignQuests.ASSESS_VILLAGE_NEEDS,
                            CampaignQuests.TALK_TO_ELDER,
                            1,
                            true)
                    .normalize();
            case PRIORITIZE_VILLAGE_DEFENSE -> updated
                    .acceptQuest(CampaignQuests.DEFEND_GOBLIN_VILLAGE)
                    .normalize();
            case SEEK_DWARGON_ARTISANS -> updated
                    .acceptQuest(CampaignQuests.SEEK_DWARGON_SUPPORT)
                    .normalize();
        };
    }

    private CampaignState acceptQuest(String questId) {
        QuestState accepted = requireQuest(questId).accept();
        return withQuestState(accepted);
    }

    private CampaignState advanceObjective(
            String questId, String objectiveId, int amount, boolean autoAccept) {
        QuestDefinition definition = CampaignQuests.requireDefinition(questId);
        QuestState state = requireQuest(questId);
        if (autoAccept && state.status() == QuestStatus.AVAILABLE) {
            state = state.accept();
        }
        QuestState updated = state.advance(definition, objectiveId, amount);
        return withQuestState(updated);
    }

    private CampaignState synchronizeMilestoneProgress() {
        CampaignState updated = this;
        Set<String> flags = updated.narrativeFlags.values();
        if (flags.contains("dire_wolf_duel_complete")) {
            updated = updated.advanceObjective(
                    CampaignQuests.DEFEND_GOBLIN_VILLAGE,
                    CampaignQuests.PREPARE_DEFENSE,
                    3,
                    true);
            updated = updated.advanceObjective(
                    CampaignQuests.DEFEND_GOBLIN_VILLAGE,
                    CampaignQuests.COMPLETE_DUEL,
                    1,
                    true);
        }
        if (flags.contains("elder_needs_assessed")) {
            updated = updated.advanceObjective(
                    CampaignQuests.ASSESS_VILLAGE_NEEDS,
                    CampaignQuests.TALK_TO_ELDER,
                    1,
                    true);
        }
        if (flags.contains("dwargon_artisans_secured")) {
            updated = updated.advanceObjective(
                    CampaignQuests.SEEK_DWARGON_SUPPORT,
                    CampaignQuests.SECURE_ARTISANS,
                    1,
                    true);
        }
        if (flags.contains("dwargon_equipment_secured")) {
            updated = updated.advanceObjective(
                    CampaignQuests.SEEK_DWARGON_SUPPORT,
                    CampaignQuests.SECURE_EQUIPMENT,
                    1,
                    true);
        }
        return updated;
    }

    private CampaignState normalize() {
        CampaignState current = this;
        boolean changed;
        do {
            changed = false;
            NarrativeFlags rewardedFlags = current.narrativeFlags;
            for (Map.Entry<String, QuestState> entry : current.quests.entrySet()) {
                if (entry.getValue().status() == QuestStatus.COMPLETED) {
                    rewardedFlags = rewardedFlags.withFlags(
                            CampaignQuests.requireDefinition(entry.getKey()).rewardFlags());
                }
            }
            if (!rewardedFlags.equals(current.narrativeFlags)) {
                current = current.withNarrativeFlags(rewardedFlags);
                changed = true;
            }
            Map<String, QuestState> refreshed = CampaignQuests.refreshAvailability(
                    current.quests, current.narrativeFlags);
            if (!refreshed.equals(current.quests)) {
                current = new CampaignState(
                        current.checkpoint,
                        current.narrativeFlags,
                        refreshed,
                        current.townState);
                changed = true;
            }
        } while (changed);
        return current;
    }

    private CampaignState withNarrativeFlags(NarrativeFlags flags) {
        return flags.equals(narrativeFlags)
                ? this
                : new CampaignState(
                        checkpoint,
                        flags,
                        quests,
                        townState.synchronizeNarrative(flags));
    }

    private CampaignState withQuestState(QuestState state) {
        QuestState previous = requireQuest(state.questId());
        if (previous.equals(state)) {
            return this;
        }
        LinkedHashMap<String, QuestState> updated = new LinkedHashMap<>(quests);
        updated.put(state.questId(), state);
        return new CampaignState(checkpoint, narrativeFlags, updated, townState);
    }

    private CampaignState withTownState(TownState updated) {
        return updated.equals(townState)
                ? this
                : new CampaignState(checkpoint, narrativeFlags, quests, updated);
    }

    private static void validateQuestState(
            QuestDefinition definition, QuestState state) {
        if (state == null || !definition.id().equals(state.questId())) {
            throw new IllegalArgumentException(
                    "Campaign quest state does not match " + definition.id());
        }
        for (Map.Entry<String, Integer> progress : state.progress().objectiveCounts().entrySet()) {
            QuestObjective objective = definition.requireObjective(progress.getKey());
            if (progress.getValue() > objective.requiredCount()) {
                throw new IllegalArgumentException(
                        "Quest progress exceeds objective target: " + progress.getKey());
            }
        }
        if ((state.status() == QuestStatus.LOCKED
                || state.status() == QuestStatus.AVAILABLE)
                && !state.progress().objectiveCounts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Inactive quest cannot contain progress: " + definition.id());
        }
        if (state.status() == QuestStatus.COMPLETED
                && !state.progress().completes(definition)) {
            throw new IllegalArgumentException(
                    "Completed quest is missing objective progress: " + definition.id());
        }
    }

    private static CampaignSnapshot withoutLegacyMarker(CampaignSnapshot snapshot) {
        if (!snapshot.completedMilestones().contains(LEGACY_FOREST_ARRIVAL_PENDING)) {
            return snapshot;
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>(snapshot.completedMilestones());
        cleaned.remove(LEGACY_FOREST_ARRIVAL_PENDING);
        return new CampaignSnapshot(
                snapshot.schemaVersion(),
                snapshot.areaId(),
                snapshot.playerX(),
                snapshot.playerY(),
                cleaned);
    }

    private static String chapterFor(Set<String> milestones) {
        if (milestones.contains("ranga_naming_complete")) {
            return "goblin_village";
        }
        if (milestones.contains("goblin_contact_complete")) {
            return "dire_wolf_conflict";
        }
        return "sealed_cave";
    }
}
