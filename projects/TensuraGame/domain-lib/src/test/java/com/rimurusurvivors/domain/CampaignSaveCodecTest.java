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
        SaveDocument missingArea = new SaveDocument(1, Map.of(
                "campaign.playerX", "0",
                "campaign.playerY", "0",
                "campaign.milestones.count", "0"));
        SaveDocument invalidCoordinate = new SaveDocument(1, Map.of(
                "campaign.areaId", "cave",
                "campaign.playerX", "Infinity",
                "campaign.playerY", "0",
                "campaign.milestones.count", "0"));

        assertThrows(IllegalArgumentException.class, () -> codec.decode(missingArea));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidCoordinate));
    }

    @Test
    void decoderRequiresMigrationAndRejectsFutureDocuments() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new SaveDocument(0, Map.of())));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new SaveDocument(2, Map.of())));
    }

    @Test
    void decoderRejectsDuplicateMilestones() {
        SaveDocument duplicated = new SaveDocument(1, Map.of(
                "campaign.areaId", "cave",
                "campaign.playerX", "0",
                "campaign.playerY", "0",
                "campaign.milestones.count", "2",
                "campaign.milestones.0", "awakening_complete",
                "campaign.milestones.1", "awakening_complete"));

        assertThrows(IllegalArgumentException.class, () -> codec.decode(duplicated));
    }
}
