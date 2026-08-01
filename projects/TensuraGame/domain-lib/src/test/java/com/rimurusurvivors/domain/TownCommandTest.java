package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TownCommandTest {

    @Test
    void eachCommandVariantDrivesTempestWithoutTouchingTheTownStateDirectly() {
        CampaignState reported = village().apply(
                new TownCommand.ApplyReturnReport(forestReport()));
        CampaignState prioritized = reported.apply(
                new TownCommand.PrioritizeProject(TownProjects.SHELTER));
        CampaignState started = prioritized.apply(
                new TownCommand.StartPrioritizedProject());
        CampaignState completed = started.apply(
                new TownCommand.CompletePrioritizedProject());

        CampaignState scheduled = completed.apply(
                new TownCommand.ScheduleExpedition(expedition()));
        CampaignState begun = scheduled.apply(
                new TownCommand.BeginExpedition("goblin_timber_route", false));
        CampaignState resolved = begun.apply(new TownCommand.ResolveExpedition(
                "goblin_timber_route", "next_adventure_complete"));

        assertEquals(TownProjects.SHELTER, prioritized.townState().prioritizedProjectId());
        assertEquals(
                TownProjectStatus.IN_PROGRESS,
                started.townState().projects().get(TownProjects.SHELTER).status());
        assertEquals(
                TownProjectStatus.COMPLETED,
                completed.townState().projects().get(TownProjects.SHELTER).status());
        assertTrue(completed.townState().emittedSignals().contains("town_visual_houses"));
        assertEquals(
                DelegatedExpeditionStatus.COMPLETED,
                resolved.townState().requireExpedition("goblin_timber_route").status());
        assertTrue(resolved.townState().discoveries().contains("route_north_timber"));
    }

    @Test
    void reappliedReportDoesNotDuplicateResources() {
        CampaignState reported = village().apply(
                new TownCommand.ApplyReturnReport(forestReport()));
        CampaignState repeated = reported.apply(
                new TownCommand.ApplyReturnReport(forestReport()));

        assertSame(reported, repeated);
        assertEquals(
                new TownResourceBundle(40, 24, 8, 4, 4, 2),
                repeated.townState().resources());
    }

    @Test
    void repeatedCompletionDoesNotConsumeResourcesTwice() {
        CampaignState completed = village()
                .apply(new TownCommand.ApplyReturnReport(forestReport()))
                .apply(new TownCommand.PrioritizeProject(TownProjects.SHELTER))
                .apply(new TownCommand.StartPrioritizedProject())
                .apply(new TownCommand.CompletePrioritizedProject());
        CampaignState repeated = completed.apply(
                new TownCommand.CompletePrioritizedProject());

        assertSame(completed, repeated);
        assertEquals(
                new TownResourceBundle(28, 20, 6, 4, 4, 2),
                repeated.townState().resources());
    }

    @Test
    void unmetRequirementKeepsThePreviousStateUntouched() {
        CampaignState reported = village().apply(
                new TownCommand.ApplyReturnReport(forestReport()));

        assertThrows(
                IllegalStateException.class,
                () -> reported.apply(new TownCommand.CompletePrioritizedProject()));
        assertThrows(
                IllegalArgumentException.class,
                () -> reported.apply(new TownCommand.PrioritizeProject("town_project_unknown")));
        assertThrows(
                IllegalArgumentException.class,
                () -> reported.apply(new TownCommand.BeginExpedition("missing_route", false)));

        assertNull(reported.townState().prioritizedProjectId());
        assertEquals(
                TownProjectStatus.AVAILABLE,
                reported.townState().projects().get(TownProjects.SHELTER).status());
        assertEquals(
                new TownResourceBundle(40, 24, 8, 4, 4, 2),
                reported.townState().resources());
    }

    @Test
    void priorityProgressAndExpeditionSurviveASaveRoundTrip() {
        CampaignState inProgress = village()
                .apply(new TownCommand.ApplyReturnReport(forestReport()))
                .apply(new TownCommand.PrioritizeProject(TownProjects.WORKSHOP))
                .apply(new TownCommand.StartPrioritizedProject())
                .apply(new TownCommand.ScheduleExpedition(expedition()))
                .apply(new TownCommand.BeginExpedition("goblin_timber_route", false));

        CampaignSaveCodec codec = new CampaignSaveCodec();
        CampaignState reloaded = codec.decodeState(codec.encodeState(inProgress));
        CampaignState completedAfterReload = reloaded.apply(
                new TownCommand.CompletePrioritizedProject());

        assertEquals(inProgress, reloaded);
        assertEquals(TownProjects.WORKSHOP, reloaded.townState().prioritizedProjectId());
        assertEquals(
                TownProjectStatus.IN_PROGRESS,
                reloaded.townState().projects().get(TownProjects.WORKSHOP).status());
        assertEquals(
                DelegatedExpeditionStatus.IN_PROGRESS,
                reloaded.townState().requireExpedition("goblin_timber_route").status());
        assertEquals(
                TownProjectStatus.COMPLETED,
                completedAfterReload.townState().projects()
                        .get(TownProjects.WORKSHOP).status());
        assertEquals(
                completedAfterReload,
                codec.decodeState(codec.encodeState(completedAfterReload)));
    }

    private static CampaignState village() {
        return CampaignState.fromSnapshot(new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "goblin_village",
                304,
                340,
                Set.of(
                        "awakening_complete",
                        "goblin_contact_complete",
                        "dire_wolf_duel_complete",
                        "ranga_naming_complete")));
    }

    private static ReturnReport forestReport() {
        return new ReturnReport(
                "forest_return_after_ranga",
                new TownResourceBundle(40, 24, 8, 4, 4, 2),
                Set.of("discovery_jura_timber"),
                Set.of(TownProjects.GOBLIN_BUILDER),
                2);
    }

    private static DelegatedExpedition expedition() {
        return DelegatedExpedition.planned(
                "goblin_timber_route",
                "ranga",
                "collect_known_timber",
                "next_adventure_complete",
                new TownResourceBundle(6, 0, 1, 0, 0, 0),
                Set.of("route_north_timber"));
    }
}
