package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fixtures apenas: o save real do usuario nunca e lido nem reescrito por estes testes.
 */
class CampaignCheckpointTest {

    @Test
    void checkpointRejectsRevokingEarnedMilestones() {
        CampaignState advanced = CampaignState.fromSnapshot(snapshot(
                "jura_forest_approach",
                Set.of(
                        "awakening_complete",
                        "veldora_encounter_complete",
                        "goblin_contact_complete")));

        CampaignSnapshot previous = advanced.checkpoint();
        assertThrows(
                IllegalArgumentException.class,
                () -> advanced.withCheckpoint(snapshot(
                        "cave_awakening", Set.of("awakening_complete"))));
        assertEquals(previous, advanced.checkpoint());
    }

    @Test
    void checkpointAcceptsAllPreviousMilestonesAndNewOnes() {
        CampaignState duelDone = CampaignState
                .fromSnapshot(snapshot("jura_forest_approach", Set.of(
                        "awakening_complete", "goblin_contact_complete")))
                .apply(new CampaignCommand.ReachMilestone("dire_wolf_duel_complete"));

        CampaignState moved = duelDone.withCheckpoint(snapshot(
                "goblin_village",
                Set.of(
                        "awakening_complete",
                        "goblin_contact_complete",
                        "dire_wolf_duel_complete",
                        "ranga_naming_complete")));

        assertTrue(moved.checkpoint().completedMilestones()
                .contains("dire_wolf_duel_complete"));
        assertTrue(moved.checkpoint().completedMilestones()
                .contains("ranga_naming_complete"));
        assertEquals("goblin_village", moved.checkpoint().areaId());
        assertEquals(
                QuestStatus.COMPLETED,
                moved.requireQuest(CampaignQuests.DEFEND_GOBLIN_VILLAGE).status());
    }

    @Test
    void newGameStateCarriesNothingFromThePreviousCampaign() {
        CampaignState previous = CampaignState
                .fromSnapshot(snapshot("goblin_village", Set.of(
                        "awakening_complete",
                        "goblin_contact_complete",
                        "dire_wolf_duel_complete",
                        "ranga_naming_complete")))
                .apply(new TownCommand.ApplyReturnReport(new ReturnReport(
                        "forest_return_after_ranga",
                        new TownResourceBundle(40, 24, 8, 4, 4, 2),
                        Set.of("discovery_jura_timber"),
                        Set.of(TownProjects.GOBLIN_BUILDER),
                        2)));

        CampaignState fresh = CampaignState.fromSnapshot(snapshot(
                "cave_awakening", Set.of("awakening_complete")));

        assertEquals(Set.of("awakening_complete"), fresh.checkpoint().completedMilestones());
        assertEquals("sealed_cave", fresh.narrativeFlags().chapterId());
        assertFalse(fresh.narrativeFlags().contains("ranga_naming_complete"));
        assertEquals(TownStage.CAMP, fresh.townState().stage());
        assertEquals(TownResourceBundle.EMPTY, fresh.townState().resources());
        assertTrue(fresh.townState().specialists().isEmpty());
        assertEquals(
                TownProjectStatus.LOCKED,
                fresh.townState().projects().get(TownProjects.SHELTER).status());
        assertEquals(
                QuestStatus.LOCKED,
                fresh.requireQuest(CampaignQuests.DEFEND_GOBLIN_VILLAGE).status());
        assertTrue(previous.townState().specialists().contains(TownProjects.GOBLIN_BUILDER));
    }

    private static CampaignSnapshot snapshot(String areaId, Set<String> milestones) {
        return new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, areaId, 80, 208, milestones);
    }
}
