package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Catalogo canonico do primeiro Conselho de Tempest. */
public final class TownProjects {

    public static final String SHELTER = "town_project_shelter";
    public static final String WORKSHOP = "town_project_workshop";
    public static final String PALISADE = "town_project_palisade";
    public static final String GOBLIN_BUILDER = "specialist_goblin_builder";

    private static final Map<String, ProjectDefinition> DEFINITIONS = createDefinitions();

    private TownProjects() {
    }

    public static Map<String, ProjectDefinition> definitions() {
        return DEFINITIONS;
    }

    public static ProjectDefinition requireDefinition(String projectId) {
        ProjectDefinition definition = DEFINITIONS.get(projectId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown town project: " + projectId);
        }
        return definition;
    }

    public static Map<String, TownProjectState> initialStates() {
        LinkedHashMap<String, TownProjectState> states = new LinkedHashMap<>();
        DEFINITIONS.keySet().forEach(id ->
                states.put(id, new TownProjectState(id, TownProjectStatus.LOCKED)));
        return Map.copyOf(states);
    }

    private static Map<String, ProjectDefinition> createDefinitions() {
        LinkedHashMap<String, ProjectDefinition> definitions = new LinkedHashMap<>();
        definitions.put(SHELTER, new ProjectDefinition(
                SHELTER,
                new TownResourceBundle(12, 4, 2, 0, 0, 0),
                GOBLIN_BUILDER,
                "ranga_naming_complete",
                Set.of("town_visual_houses", "town_visual_storehouse"),
                Set.of("town_benefit_recovery"),
                true));
        definitions.put(WORKSHOP, new ProjectDefinition(
                WORKSHOP,
                new TownResourceBundle(8, 8, 0, 2, 2, 0),
                GOBLIN_BUILDER,
                "ranga_naming_complete",
                Set.of("town_visual_workshop"),
                Set.of("town_benefit_field_repairs"),
                true));
        definitions.put(PALISADE, new ProjectDefinition(
                PALISADE,
                new TownResourceBundle(14, 6, 0, 0, 0, 0),
                GOBLIN_BUILDER,
                "ranga_naming_complete",
                Set.of("town_visual_palisade"),
                Set.of("town_benefit_defense"),
                true));
        return Map.copyOf(definitions);
    }
}
