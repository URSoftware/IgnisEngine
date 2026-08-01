package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TownStateTest {

    private static final NarrativeFlags VILLAGE_FLAGS = new NarrativeFlags(
            "goblin_village", Set.of("ranga_naming_complete"));

    @Test
    void forestReportIsIdempotentAndUnlocksThreeCouncilPriorities() {
        TownState initial = TownState.initial(VILLAGE_FLAGS);
        ReturnReport report = forestReport();

        TownState reported = initial.applyReturnReport(report, VILLAGE_FLAGS);
        TownState repeated = reported.applyReturnReport(report, VILLAGE_FLAGS);

        assertSame(reported, repeated);
        assertEquals(new TownResourceBundle(40, 24, 8, 4, 4, 2), reported.resources());
        assertEquals(3, reported.projects().values().stream()
                .filter(state -> state.status() == TownProjectStatus.AVAILABLE)
                .count());
        assertTrue(reported.specialists().contains(TownProjects.GOBLIN_BUILDER));
        assertTrue(reported.discoveries().contains("discovery_jura_timber"));
    }

    @Test
    void prioritizedProjectConsumesResourcesOnceAndEmitsVisualAndBenefitSignals() {
        TownState reported = TownState.initial(VILLAGE_FLAGS)
                .applyReturnReport(forestReport(), VILLAGE_FLAGS);
        TownState started = reported
                .prioritize(TownProjects.SHELTER)
                .startPrioritizedProject(VILLAGE_FLAGS);

        TownState completed = started.completePrioritizedProject(VILLAGE_FLAGS);
        TownState repeated = completed.completePrioritizedProject(VILLAGE_FLAGS);

        assertSame(completed, repeated);
        assertEquals(TownProjectStatus.COMPLETED,
                completed.projects().get(TownProjects.SHELTER).status());
        assertEquals(new TownResourceBundle(28, 20, 6, 4, 4, 2), completed.resources());
        assertTrue(completed.emittedSignals().contains("town_visual_houses"));
        assertTrue(completed.emittedSignals().contains("town_visual_storehouse"));
        assertTrue(completed.emittedSignals().contains("town_benefit_recovery"));
    }

    @Test
    void projectCannotStartWithoutResourcesSpecialistAndNarrativeMilestone() {
        TownState withoutReport = TownState.initial(VILLAGE_FLAGS);
        assertThrows(
                IllegalStateException.class,
                () -> withoutReport.prioritize(TownProjects.SHELTER));

        NarrativeFlags earlyFlags = new NarrativeFlags("dire_wolf_conflict", Set.of());
        TownState earlyReport = TownState.initial(earlyFlags)
                .applyReturnReport(forestReport(), earlyFlags);
        assertThrows(
                IllegalStateException.class,
                () -> earlyReport.prioritize(TownProjects.SHELTER));
    }

    @Test
    void onlyOneCouncilPriorityIsSelectedAtATime() {
        TownState reported = TownState.initial(VILLAGE_FLAGS)
                .applyReturnReport(forestReport(), VILLAGE_FLAGS);

        TownState shelter = reported.prioritize(TownProjects.SHELTER);
        TownState palisade = shelter.prioritize(TownProjects.PALISADE);

        assertEquals(TownProjects.SHELTER, shelter.prioritizedProjectId());
        assertEquals(TownProjects.PALISADE, palisade.prioritizedProjectId());
        assertTrue(palisade.emittedSignals().contains(
                "town_priority_" + TownProjects.PALISADE));
        assertTrue(palisade.emittedSignals().stream()
                .noneMatch(signal -> signal.equals(
                        "town_priority_" + TownProjects.SHELTER)));
    }

    private static ReturnReport forestReport() {
        return new ReturnReport(
                "forest_return_after_ranga",
                new TownResourceBundle(40, 24, 8, 4, 4, 2),
                Set.of("discovery_jura_timber"),
                Set.of(TownProjects.GOBLIN_BUILDER),
                2);
    }
}
