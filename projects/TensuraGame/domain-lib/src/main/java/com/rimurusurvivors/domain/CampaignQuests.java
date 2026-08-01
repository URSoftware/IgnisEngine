package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catalogo canonico das primeiras missoes da campanha. */
public final class CampaignQuests {

    public static final String ASSESS_VILLAGE_NEEDS = "quest.assess_village_needs";
    public static final String DEFEND_GOBLIN_VILLAGE = "quest.defend_goblin_village";
    public static final String SEEK_DWARGON_SUPPORT = "quest.seek_dwargon_support";

    public static final String TALK_TO_ELDER = "objective.talk_to_elder";
    public static final String PREPARE_DEFENSE = "objective.prepare_defense";
    public static final String COMPLETE_DUEL = "objective.complete_dire_wolf_duel";
    public static final String SECURE_ARTISANS = "objective.secure_dwargon_artisans";
    public static final String SECURE_EQUIPMENT = "objective.secure_dwargon_equipment";

    private static final Map<String, QuestDefinition> DEFINITIONS = createDefinitions();

    private CampaignQuests() {
    }

    public static Map<String, QuestDefinition> definitions() {
        return DEFINITIONS;
    }

    public static QuestDefinition requireDefinition(String questId) {
        QuestDefinition definition = DEFINITIONS.get(questId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown campaign quest: " + questId);
        }
        return definition;
    }

    public static Map<String, QuestState> initialStates(NarrativeFlags narrativeFlags) {
        LinkedHashMap<String, QuestState> states = new LinkedHashMap<>();
        for (String questId : DEFINITIONS.keySet()) {
            states.put(questId, QuestState.locked(questId));
        }
        return refreshAvailability(states, narrativeFlags);
    }

    static Map<String, QuestState> refreshAvailability(
            Map<String, QuestState> source, NarrativeFlags narrativeFlags) {
        LinkedHashMap<String, QuestState> states = new LinkedHashMap<>(source);
        boolean changed;
        do {
            changed = false;
            for (QuestDefinition definition : DEFINITIONS.values()) {
                QuestState current = states.get(definition.id());
                if (current == null) {
                    throw new IllegalArgumentException(
                            "Campaign quest state is missing: " + definition.id());
                }
                QuestState refreshed = current.refreshAvailability(
                        definition, states, narrativeFlags);
                if (!refreshed.equals(current)) {
                    states.put(definition.id(), refreshed);
                    changed = true;
                }
            }
        } while (changed);
        return Map.copyOf(states);
    }

    private static Map<String, QuestDefinition> createDefinitions() {
        LinkedHashMap<String, QuestDefinition> definitions = new LinkedHashMap<>();
        definitions.put(ASSESS_VILLAGE_NEEDS, new QuestDefinition(
                ASSESS_VILLAGE_NEEDS,
                Set.of(),
                Set.of("ranga_naming_complete"),
                List.of(new QuestObjective(TALK_TO_ELDER, 1)),
                Set.of("village_needs_assessed")));
        definitions.put(DEFEND_GOBLIN_VILLAGE, new QuestDefinition(
                DEFEND_GOBLIN_VILLAGE,
                Set.of(),
                Set.of("goblin_contact_complete"),
                List.of(
                        new QuestObjective(PREPARE_DEFENSE, 3),
                        new QuestObjective(COMPLETE_DUEL, 1)),
                Set.of("goblin_village_defended")));
        definitions.put(SEEK_DWARGON_SUPPORT, new QuestDefinition(
                SEEK_DWARGON_SUPPORT,
                Set.of(ASSESS_VILLAGE_NEEDS, DEFEND_GOBLIN_VILLAGE),
                Set.of("village_needs_assessed", "goblin_village_defended"),
                List.of(
                        new QuestObjective(SECURE_ARTISANS, 1),
                        new QuestObjective(SECURE_EQUIPMENT, 1)),
                Set.of("dwargon_support_secured")));
        return Map.copyOf(definitions);
    }
}
