package com.rimurusurvivors.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conversao deterministica entre o estado tipado atual e um documento neutro. */
public final class CampaignSaveCodec {

    private static final String AREA_ID = "campaign.areaId";
    private static final String PLAYER_X = "campaign.playerX";
    private static final String PLAYER_Y = "campaign.playerY";
    private static final String MILESTONE_COUNT = "campaign.milestones.count";
    private static final String MILESTONE_PREFIX = "campaign.milestones.";
    private static final String CHAPTER_ID = "narrative.chapterId";
    private static final String FLAG_COUNT = "narrative.flags.count";
    private static final String FLAG_PREFIX = "narrative.flags.";
    private static final String QUEST_COUNT = "campaign.quests.count";
    private static final String QUEST_PREFIX = "campaign.quests.";
    private static final String TOWN_STAGE = "town.stage";
    private static final String TOWN_TRUST = "town.trust";
    private static final String TOWN_PRIORITY = "town.priority";
    private static final String TOWN_RESOURCES = "town.resources.";
    private static final String TOWN_SPECIALIST_COUNT = "town.specialists.count";
    private static final String TOWN_SPECIALIST_PREFIX = "town.specialists.";
    private static final String TOWN_DISCOVERY_COUNT = "town.discoveries.count";
    private static final String TOWN_DISCOVERY_PREFIX = "town.discoveries.";
    private static final String TOWN_REPORT_COUNT = "town.reports.count";
    private static final String TOWN_REPORT_PREFIX = "town.reports.";
    private static final String TOWN_SIGNAL_COUNT = "town.signals.count";
    private static final String TOWN_SIGNAL_PREFIX = "town.signals.";
    private static final String TOWN_PROJECT_COUNT = "town.projects.count";
    private static final String TOWN_PROJECT_PREFIX = "town.projects.";
    private static final String TOWN_EXPEDITION_COUNT = "town.expeditions.count";
    private static final String TOWN_EXPEDITION_PREFIX = "town.expeditions.";

    public SaveDocument encode(CampaignSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Campaign snapshot is required.");
        }
        return encodeState(CampaignState.fromSnapshot(snapshot));
    }

    public SaveDocument encodeState(CampaignState state) {
        if (state == null) {
            throw new IllegalArgumentException("Campaign state is required.");
        }
        CampaignSnapshot snapshot = state.checkpoint();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(AREA_ID, snapshot.areaId());
        fields.put(PLAYER_X, Double.toString(snapshot.playerX()));
        fields.put(PLAYER_Y, Double.toString(snapshot.playerY()));
        putSortedIds(fields, MILESTONE_COUNT, MILESTONE_PREFIX,
                snapshot.completedMilestones());
        fields.put(CHAPTER_ID, state.narrativeFlags().chapterId());
        putSortedIds(fields, FLAG_COUNT, FLAG_PREFIX, state.narrativeFlags().values());
        putQuests(fields, state.quests());
        putTown(fields, state.townState());
        return new SaveDocument(snapshot.schemaVersion(), fields);
    }

    public CampaignSnapshot decode(SaveDocument document) {
        return decodeState(document).checkpoint();
    }

    public CampaignState decodeState(SaveDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Save document is required.");
        }
        if (document.schemaVersion() > CampaignSnapshot.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Save schema is newer than this game: " + document.schemaVersion());
        }
        if (document.schemaVersion() < CampaignSnapshot.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Save document must be migrated before decoding: " + document.schemaVersion());
        }

        double playerX = parseFiniteDouble(document, PLAYER_X);
        double playerY = parseFiniteDouble(document, PLAYER_Y);
        Set<String> milestones = parseIds(document, MILESTONE_COUNT, MILESTONE_PREFIX);
        CampaignSnapshot snapshot = new CampaignSnapshot(
                document.schemaVersion(), document.requireField(AREA_ID),
                playerX, playerY, milestones);
        NarrativeFlags flags = new NarrativeFlags(
                document.requireField(CHAPTER_ID),
                parseIds(document, FLAG_COUNT, FLAG_PREFIX));
        return new CampaignState(snapshot, flags, parseQuests(document), parseTown(document));
    }

    static void putTown(Map<String, String> fields, TownState town) {
        if (fields == null || town == null) {
            throw new IllegalArgumentException("Town save fields are required.");
        }
        fields.put(TOWN_STAGE, town.stage().name());
        fields.put(TOWN_TRUST, Integer.toString(town.trust()));
        fields.put(TOWN_PRIORITY, town.prioritizedProjectId() == null
                ? ""
                : town.prioritizedProjectId());
        putResourceBundle(fields, TOWN_RESOURCES, town.resources());
        putSortedIds(
                fields,
                TOWN_SPECIALIST_COUNT,
                TOWN_SPECIALIST_PREFIX,
                town.specialists());
        putSortedIds(
                fields,
                TOWN_DISCOVERY_COUNT,
                TOWN_DISCOVERY_PREFIX,
                town.discoveries());
        putSortedIds(
                fields,
                TOWN_REPORT_COUNT,
                TOWN_REPORT_PREFIX,
                town.processedReportIds());
        putSortedIds(
                fields,
                TOWN_SIGNAL_COUNT,
                TOWN_SIGNAL_PREFIX,
                town.emittedSignals());
        putTownProjects(fields, town.projects());
        putExpeditions(fields, town.expeditions());
    }

    private static void putSortedIds(
            Map<String, String> fields,
            String countField,
            String valuePrefix,
            Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        fields.put(countField, Integer.toString(sorted.size()));
        for (int index = 0; index < sorted.size(); index++) {
            fields.put(valuePrefix + index, sorted.get(index));
        }
    }

    private static Set<String> parseIds(
            SaveDocument document, String countField, String valuePrefix) {
        int count = parseNonNegativeInt(document, countField);
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String value = document.requireField(valuePrefix + index);
            if (value.isBlank() || !values.add(value)) {
                throw new IllegalArgumentException(
                        "Blank or duplicate save value under " + valuePrefix + index);
            }
        }
        return values;
    }

    private static void putQuests(
            Map<String, String> fields, Map<String, QuestState> quests) {
        List<String> questIds = new ArrayList<>(quests.keySet());
        questIds.sort(String::compareTo);
        fields.put(QUEST_COUNT, Integer.toString(questIds.size()));
        for (int questIndex = 0; questIndex < questIds.size(); questIndex++) {
            QuestState state = quests.get(questIds.get(questIndex));
            String prefix = QUEST_PREFIX + questIndex + ".";
            fields.put(prefix + "id", state.questId());
            fields.put(prefix + "status", state.status().name());

            List<String> objectiveIds = new ArrayList<>(
                    state.progress().objectiveCounts().keySet());
            objectiveIds.sort(String::compareTo);
            fields.put(prefix + "progress.count", Integer.toString(objectiveIds.size()));
            for (int progressIndex = 0; progressIndex < objectiveIds.size(); progressIndex++) {
                String objectiveId = objectiveIds.get(progressIndex);
                String progressPrefix = prefix + "progress." + progressIndex + ".";
                fields.put(progressPrefix + "objectiveId", objectiveId);
                fields.put(
                        progressPrefix + "value",
                        Integer.toString(state.progress().count(objectiveId)));
            }
        }
    }

    private static Map<String, QuestState> parseQuests(SaveDocument document) {
        int questCount = parseNonNegativeInt(document, QUEST_COUNT);
        Map<String, QuestState> quests = new LinkedHashMap<>();
        for (int questIndex = 0; questIndex < questCount; questIndex++) {
            String prefix = QUEST_PREFIX + questIndex + ".";
            String questId = document.requireField(prefix + "id");
            QuestStatus status;
            try {
                status = QuestStatus.valueOf(document.requireField(prefix + "status"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid quest status in save at index " + questIndex, exception);
            }

            int progressCount = parseNonNegativeInt(
                    document, prefix + "progress.count");
            Map<String, Integer> progress = new LinkedHashMap<>();
            for (int progressIndex = 0; progressIndex < progressCount; progressIndex++) {
                String progressPrefix = prefix + "progress." + progressIndex + ".";
                String objectiveId = document.requireField(progressPrefix + "objectiveId");
                int value = parseNonNegativeInt(document, progressPrefix + "value");
                if (progress.put(objectiveId, value) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate quest objective progress in save: " + objectiveId);
                }
            }
            if (quests.put(
                    questId,
                    new QuestState(questId, status, new QuestProgress(progress))) != null) {
                throw new IllegalArgumentException("Duplicate quest in save: " + questId);
            }
        }
        return quests;
    }

    private static TownState parseTown(SaveDocument document) {
        TownStage stage;
        try {
            stage = TownStage.valueOf(document.requireField(TOWN_STAGE));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid town stage in save.", exception);
        }
        String priority = document.requireField(TOWN_PRIORITY);
        return new TownState(
                stage,
                parseResourceBundle(document, TOWN_RESOURCES),
                parseIds(document, TOWN_SPECIALIST_COUNT, TOWN_SPECIALIST_PREFIX),
                parseTownProjects(document),
                parseNonNegativeInt(document, TOWN_TRUST),
                priority.isBlank() ? null : priority,
                parseIds(document, TOWN_DISCOVERY_COUNT, TOWN_DISCOVERY_PREFIX),
                parseIds(document, TOWN_REPORT_COUNT, TOWN_REPORT_PREFIX),
                parseIds(document, TOWN_SIGNAL_COUNT, TOWN_SIGNAL_PREFIX),
                parseExpeditions(document));
    }

    private static void putTownProjects(
            Map<String, String> fields,
            Map<String, TownProjectState> projects) {
        List<String> projectIds = new ArrayList<>(projects.keySet());
        projectIds.sort(String::compareTo);
        fields.put(TOWN_PROJECT_COUNT, Integer.toString(projectIds.size()));
        for (int index = 0; index < projectIds.size(); index++) {
            TownProjectState state = projects.get(projectIds.get(index));
            String prefix = TOWN_PROJECT_PREFIX + index + ".";
            fields.put(prefix + "id", state.projectId());
            fields.put(prefix + "status", state.status().name());
        }
    }

    private static Map<String, TownProjectState> parseTownProjects(
            SaveDocument document) {
        int count = parseNonNegativeInt(document, TOWN_PROJECT_COUNT);
        LinkedHashMap<String, TownProjectState> projects = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = TOWN_PROJECT_PREFIX + index + ".";
            String projectId = document.requireField(prefix + "id");
            TownProjectStatus status;
            try {
                status = TownProjectStatus.valueOf(
                        document.requireField(prefix + "status"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid town project status at index " + index, exception);
            }
            if (projects.put(
                    projectId,
                    new TownProjectState(projectId, status)) != null) {
                throw new IllegalArgumentException(
                        "Duplicate town project in save: " + projectId);
            }
        }
        return projects;
    }

    private static void putExpeditions(
            Map<String, String> fields,
            Map<String, DelegatedExpedition> expeditions) {
        List<String> expeditionIds = new ArrayList<>(expeditions.keySet());
        expeditionIds.sort(String::compareTo);
        fields.put(TOWN_EXPEDITION_COUNT, Integer.toString(expeditionIds.size()));
        for (int index = 0; index < expeditionIds.size(); index++) {
            DelegatedExpedition expedition = expeditions.get(expeditionIds.get(index));
            String prefix = TOWN_EXPEDITION_PREFIX + index + ".";
            fields.put(prefix + "id", expedition.id());
            fields.put(prefix + "leaderId", expedition.leaderId());
            fields.put(prefix + "objectiveId", expedition.objectiveId());
            fields.put(prefix + "milestoneId", expedition.resolutionMilestoneId());
            fields.put(prefix + "status", expedition.status().name());
            fields.put(prefix + "demonstrated", Boolean.toString(expedition.demonstrated()));
            putResourceBundle(fields, prefix + "result.resources.", expedition.resultResources());
            putSortedIds(
                    fields,
                    prefix + "result.discoveries.count",
                    prefix + "result.discoveries.",
                    expedition.resultDiscoveries());
        }
    }

    private static Map<String, DelegatedExpedition> parseExpeditions(
            SaveDocument document) {
        int count = parseNonNegativeInt(document, TOWN_EXPEDITION_COUNT);
        LinkedHashMap<String, DelegatedExpedition> expeditions = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String prefix = TOWN_EXPEDITION_PREFIX + index + ".";
            String expeditionId = document.requireField(prefix + "id");
            DelegatedExpeditionStatus status;
            try {
                status = DelegatedExpeditionStatus.valueOf(
                        document.requireField(prefix + "status"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid expedition status at index " + index, exception);
            }
            DelegatedExpedition expedition = new DelegatedExpedition(
                    expeditionId,
                    document.requireField(prefix + "leaderId"),
                    document.requireField(prefix + "objectiveId"),
                    document.requireField(prefix + "milestoneId"),
                    status,
                    parseBoolean(document, prefix + "demonstrated"),
                    parseResourceBundle(document, prefix + "result.resources."),
                    parseIds(
                            document,
                            prefix + "result.discoveries.count",
                            prefix + "result.discoveries."));
            if (expeditions.put(expeditionId, expedition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate delegated expedition in save: " + expeditionId);
            }
        }
        return expeditions;
    }

    private static void putResourceBundle(
            Map<String, String> fields,
            String prefix,
            TownResourceBundle bundle) {
        fields.put(prefix + "wood", Integer.toString(bundle.wood()));
        fields.put(prefix + "stone", Integer.toString(bundle.stone()));
        fields.put(prefix + "food", Integer.toString(bundle.food()));
        fields.put(prefix + "cloth", Integer.toString(bundle.cloth()));
        fields.put(prefix + "metal", Integer.toString(bundle.metal()));
        fields.put(prefix + "magicules", Integer.toString(bundle.magicules()));
    }

    private static TownResourceBundle parseResourceBundle(
            SaveDocument document, String prefix) {
        return new TownResourceBundle(
                parseNonNegativeInt(document, prefix + "wood"),
                parseNonNegativeInt(document, prefix + "stone"),
                parseNonNegativeInt(document, prefix + "food"),
                parseNonNegativeInt(document, prefix + "cloth"),
                parseNonNegativeInt(document, prefix + "metal"),
                parseNonNegativeInt(document, prefix + "magicules"));
    }

    private static boolean parseBoolean(SaveDocument document, String field) {
        String value = document.requireField(field);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Invalid boolean save field: " + field);
        }
        return Boolean.parseBoolean(value);
    }

    private static double parseFiniteDouble(SaveDocument document, String field) {
        try {
            double value = Double.parseDouble(document.requireField(field));
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Save coordinate must be finite: " + field);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal save field: " + field, exception);
        }
    }

    private static int parseNonNegativeInt(SaveDocument document, String field) {
        try {
            int value = Integer.parseInt(document.requireField(field));
            if (value < 0) {
                throw new IllegalArgumentException("Save count must be non-negative: " + field);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer save field: " + field, exception);
        }
    }
}
