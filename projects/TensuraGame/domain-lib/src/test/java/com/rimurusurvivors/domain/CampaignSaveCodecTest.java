package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CampaignSaveCodecTest {

    private final CampaignSaveCodec codec = new CampaignSaveCodec();

    @Test
    void currentCampaignRoundTripsThroughANeutralDocument() {
        CampaignSnapshot original = new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "jura_forest_approach", 96, 192,
                Set.of("awakening_complete", "veldora_alliance", "goblin_contact"));

        CampaignSnapshot restored = codec.decode(codec.encode(original));

        assertEquals(original, restored);
    }

    @Test
    void questAwareCampaignStateRoundTripsDeterministically() {
        CampaignState original = CampaignState.fromSnapshot(new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "goblin_village",
                96,
                256,
                Set.of(
                        "goblin_contact_complete",
                        "dire_wolf_duel_complete",
                        "ranga_naming_complete")))
                .apply(new CampaignCommand.ChooseDialogue(
                        CampaignChoice.ASSESS_VILLAGE_NEEDS))
                .apply(new CampaignCommand.ChooseDialogue(
                        CampaignChoice.SEEK_DWARGON_ARTISANS));
        original = original.applyReturnReport(new ReturnReport(
                "forest_return_after_ranga",
                new TownResourceBundle(40, 24, 8, 4, 4, 2),
                Set.of("discovery_jura_timber"),
                Set.of(TownProjects.GOBLIN_BUILDER),
                2));

        SaveDocument encoded = codec.encodeState(original);
        CampaignState restored = codec.decodeState(encoded);

        assertEquals(original, restored);
        assertEquals("3", encoded.fields().get("campaign.quests.count"));
        assertEquals("goblin_village", encoded.fields().get("narrative.chapterId"));
        assertEquals("3", encoded.fields().get("town.projects.count"));
        assertEquals("GOBLIN_VILLAGE", encoded.fields().get("town.stage"));
    }

    @Test
    void townProjectsAndDelegatedExpeditionsRoundTripInProgressAndCompleted() {
        CampaignState campaign = CampaignState.fromSnapshot(new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "goblin_village",
                96,
                256,
                Set.of("ranga_naming_complete")))
                .applyReturnReport(new ReturnReport(
                        "forest_return_after_ranga",
                        new TownResourceBundle(40, 24, 8, 4, 4, 2),
                        Set.of("discovery_jura_timber"),
                        Set.of(TownProjects.GOBLIN_BUILDER),
                        2))
                .prioritizeTownProject(TownProjects.SHELTER)
                .startPrioritizedTownProject()
                .scheduleDelegatedExpedition(DelegatedExpedition.planned(
                        "goblin_timber_route",
                        "ranga",
                        "collect_known_timber",
                        "next_adventure_complete",
                        new TownResourceBundle(6, 0, 1, 0, 0, 0),
                        Set.of("route_north_timber")).begin(false));

        assertEquals(campaign, codec.decodeState(codec.encodeState(campaign)));

        CampaignState completed = campaign.completePrioritizedTownProject();

        assertEquals(completed, codec.decodeState(codec.encodeState(completed)));
        assertEquals(
                TownProjectStatus.COMPLETED,
                completed.townState().projects().get(TownProjects.SHELTER).status());
    }

    @Test
    void encodingMilestonesIsDeterministic() {
        CampaignSnapshot snapshot = new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, "cave_gallery", 176, 432,
                Set.of("zeta", "alpha"));

        SaveDocument document = codec.encode(snapshot);

        assertEquals("alpha", document.fields().get("campaign.milestones.0"));
        assertEquals("zeta", document.fields().get("campaign.milestones.1"));
    }

    @Test
    void campaignRejectsInvalidCoordinatesAndMilestones() {
        assertThrows(IllegalArgumentException.class, () -> new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, "cave", Double.NaN, 0, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, "cave", 0, 0, Set.of(" ")));
    }

    @Test
    void decoderRejectsMissingAndInvalidFields() {
        SaveDocument missingArea = new SaveDocument(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, Map.of(
                "campaign.playerX", "0",
                "campaign.playerY", "0",
                "campaign.milestones.count", "0"));
        SaveDocument invalidCoordinate = new SaveDocument(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, Map.of(
                "campaign.areaId", "cave",
                "campaign.playerX", "Infinity",
                "campaign.playerY", "0",
                "campaign.milestones.count", "0"));

        assertThrows(IllegalArgumentException.class, () -> codec.decode(missingArea));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidCoordinate));
    }

    @Test
    void decoderRequiresMigrationAndRejectsFutureDocuments() {
        assertThrows(IllegalArgumentException.class, () ->
                codec.decode(new SaveDocument(1, Map.of())));
        assertThrows(IllegalArgumentException.class, () ->
                codec.decode(new SaveDocument(
                        CampaignSnapshot.CURRENT_SCHEMA_VERSION + 1, Map.of())));
    }

    @Test
    void decoderRejectsDuplicateMilestones() {
        SaveDocument duplicated = new SaveDocument(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, Map.of(
                "campaign.areaId", "cave",
                "campaign.playerX", "0",
                "campaign.playerY", "0",
                "campaign.milestones.count", "2",
                "campaign.milestones.0", "awakening_complete",
                "campaign.milestones.1", "awakening_complete"));

        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicated));
    }
}
