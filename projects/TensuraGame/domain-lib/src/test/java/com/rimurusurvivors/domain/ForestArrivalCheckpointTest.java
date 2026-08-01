package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Regressao da cadeia caverna -> floresta -> contato goblin. Fixtures apenas: nenhum
 * save real e lido ou reescrito.
 */
class ForestArrivalCheckpointTest {

    private static final String CAVE_AREA = "cave_awakening";
    private static final String FOREST_AREA = "jura_forest_approach";

    @Test
    void caveToForestToGoblinContactPersistsEveryCheckpoint() {
        CampaignState awakened = CampaignState.fromSnapshot(
                snapshot(CAVE_AREA, Set.of("awakening_complete")));

        CampaignState veldora = awakened.withCheckpoint(snapshot(
                CAVE_AREA, Set.of("awakening_complete", "veldora_encounter_complete")));
        CampaignState forestArrival = veldora.withCheckpoint(snapshot(
                FOREST_AREA, Set.of("awakening_complete", "veldora_encounter_complete")));
        CampaignState goblinContact = forestArrival.withCheckpoint(snapshot(
                FOREST_AREA,
                Set.of(
                        "awakening_complete",
                        "veldora_encounter_complete",
                        "goblin_contact_complete",
                        "goblin_village_route_unlocked")));

        assertEquals(FOREST_AREA, goblinContact.checkpoint().areaId());
        assertTrue(goblinContact.checkpoint().completedMilestones()
                .contains("goblin_contact_complete"));
        assertTrue(goblinContact.checkpoint().completedMilestones()
                .contains("veldora_encounter_complete"));
    }

    @Test
    void legacySaveCarryingTheTransientMarkerLoadsAndKeepsAdvancing() {
        SaveDocument legacy = legacyDocumentWithTransientMarker();
        CampaignSaveCodec codec = new CampaignSaveCodec();

        CampaignState loaded = codec.decodeState(legacy);

        assertFalse(loaded.checkpoint().completedMilestones()
                .contains(CampaignState.LEGACY_FOREST_ARRIVAL_PENDING));
        assertFalse(loaded.narrativeFlags()
                .contains(CampaignState.LEGACY_FOREST_ARRIVAL_PENDING));

        CampaignState goblinContact = loaded.withCheckpoint(snapshot(
                FOREST_AREA,
                Set.of(
                        "awakening_complete",
                        "veldora_encounter_complete",
                        "goblin_contact_complete",
                        "goblin_village_route_unlocked")));

        assertTrue(goblinContact.checkpoint().completedMilestones()
                .contains("goblin_contact_complete"));
        assertFalse(codec.encodeState(goblinContact).fields().containsValue(
                CampaignState.LEGACY_FOREST_ARRIVAL_PENDING));
    }

    @Test
    void aRealMilestoneStillCannotBeRevoked() {
        CampaignState advanced = CampaignState.fromSnapshot(snapshot(
                FOREST_AREA,
                Set.of(
                        "awakening_complete",
                        "veldora_encounter_complete",
                        "goblin_contact_complete")));
        CampaignSnapshot previous = advanced.checkpoint();

        assertThrows(
                IllegalArgumentException.class,
                () -> advanced.withCheckpoint(snapshot(
                        FOREST_AREA,
                        Set.of("awakening_complete", "veldora_encounter_complete"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> advanced.withCheckpoint(snapshot(
                        FOREST_AREA,
                        Set.of(
                                "awakening_complete",
                                "veldora_encounter_complete",
                                CampaignState.LEGACY_FOREST_ARRIVAL_PENDING))));
        assertEquals(previous, advanced.checkpoint());
    }

    @Test
    void anUnknownMarkerIsKeptAndStillProtectedByTheStrictRule() {
        CampaignState tagged = CampaignState.fromSnapshot(snapshot(
                FOREST_AREA,
                Set.of("awakening_complete", "some_future_milestone")));

        assertTrue(tagged.checkpoint().completedMilestones()
                .contains("some_future_milestone"));
        assertThrows(
                IllegalArgumentException.class,
                () -> tagged.withCheckpoint(snapshot(
                        FOREST_AREA, Set.of("awakening_complete"))));
    }

    private static SaveDocument legacyDocumentWithTransientMarker() {
        CampaignState clean = CampaignState.fromSnapshot(snapshot(
                FOREST_AREA,
                Set.of("awakening_complete", "veldora_encounter_complete")));
        Map<String, String> fields = new LinkedHashMap<>(
                new CampaignSaveCodec().encodeState(clean).fields());
        fields.put("campaign.milestones.count", "3");
        fields.put("campaign.milestones.2", CampaignState.LEGACY_FOREST_ARRIVAL_PENDING);
        fields.put("narrative.flags.count", "3");
        fields.put("narrative.flags.2", CampaignState.LEGACY_FOREST_ARRIVAL_PENDING);
        return new SaveDocument(CampaignSnapshot.CURRENT_SCHEMA_VERSION, fields);
    }

    private static CampaignSnapshot snapshot(String areaId, Set<String> milestones) {
        return new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, areaId, 304.0, 58.0, milestones);
    }
}
