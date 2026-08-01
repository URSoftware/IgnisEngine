package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CampaignStateTest {

    @Test
    void defenseQuestUsesSemanticMilestonesAndCompletesOnlyOnce() {
        CampaignState state = CampaignState.fromSnapshot(snapshot(Set.of(
                "awakening_complete",
                "goblin_contact_complete")));

        assertEquals(
                QuestStatus.AVAILABLE,
                state.requireQuest(CampaignQuests.DEFEND_GOBLIN_VILLAGE).status());

        CampaignState active = state.apply(new CampaignCommand.AcceptQuest(
                CampaignQuests.DEFEND_GOBLIN_VILLAGE));
        CampaignState prepared = active.apply(new CampaignCommand.AdvanceObjective(
                CampaignQuests.DEFEND_GOBLIN_VILLAGE,
                CampaignQuests.PREPARE_DEFENSE,
                3));
        CampaignState completed = prepared.apply(new CampaignCommand.ReachMilestone(
                "dire_wolf_duel_complete"));
        CampaignState repeated = completed.apply(new CampaignCommand.ReachMilestone(
                "dire_wolf_duel_complete"));

        assertEquals(
                QuestStatus.COMPLETED,
                completed.requireQuest(CampaignQuests.DEFEND_GOBLIN_VILLAGE).status());
        assertTrue(completed.narrativeFlags().contains("goblin_village_defended"));
        assertSame(completed, repeated);
    }

    @Test
    void typedDialogueChoicesUnlockDwargonWithoutPresenterFlags() {
        CampaignState state = CampaignState.fromSnapshot(snapshot(Set.of(
                "awakening_complete",
                "goblin_contact_complete",
                "dire_wolf_duel_complete",
                "ranga_naming_complete")));

        CampaignState assessed = state.apply(new CampaignCommand.ChooseDialogue(
                CampaignChoice.ASSESS_VILLAGE_NEEDS));
        CampaignState headingToDwargon = assessed.apply(new CampaignCommand.ChooseDialogue(
                CampaignChoice.SEEK_DWARGON_ARTISANS));

        assertEquals(
                QuestStatus.COMPLETED,
                assessed.requireQuest(CampaignQuests.ASSESS_VILLAGE_NEEDS).status());
        assertEquals(
                QuestStatus.AVAILABLE,
                assessed.requireQuest(CampaignQuests.SEEK_DWARGON_SUPPORT).status());
        assertEquals(
                QuestStatus.ACTIVE,
                headingToDwargon.requireQuest(CampaignQuests.SEEK_DWARGON_SUPPORT).status());
        assertTrue(headingToDwargon.narrativeFlags().contains(
                CampaignChoice.SEEK_DWARGON_ARTISANS.flagId()));
    }

    @Test
    void canonicalQuestDefinitionsContainNoFilesystemPaths() {
        CampaignQuests.definitions().values().forEach(definition -> {
            assertFalse(definition.id().contains("/"));
            assertFalse(definition.id().contains("\\"));
            definition.objectives().forEach(objective -> {
                assertFalse(objective.id().contains("/"));
                assertFalse(objective.id().contains("\\"));
            });
        });
    }

    @Test
    void stateRejectsAnActiveQuestWithUnmetPrerequisites() {
        CampaignState baseline = CampaignState.fromSnapshot(snapshot(Set.of()));
        LinkedHashMap<String, QuestState> corrupted = new LinkedHashMap<>(
                baseline.quests());
        corrupted.put(
                CampaignQuests.SEEK_DWARGON_SUPPORT,
                new QuestState(
                        CampaignQuests.SEEK_DWARGON_SUPPORT,
                        QuestStatus.ACTIVE,
                        QuestProgress.empty()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new CampaignState(
                        baseline.checkpoint(),
                        baseline.narrativeFlags(),
                        corrupted));
    }

    private static CampaignSnapshot snapshot(Set<String> milestones) {
        return new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "goblin_village",
                96,
                256,
                milestones);
    }
}
