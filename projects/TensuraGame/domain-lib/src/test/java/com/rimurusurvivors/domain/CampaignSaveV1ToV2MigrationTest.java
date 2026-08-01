package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CampaignSaveV1ToV2MigrationTest {

    @Test
    void realSchemaOneShapeMigratesThroughQuestAndTownSchemas() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("campaign.milestones.3", "veldora_encounter_complete");
        fields.put("campaign.playerX", "304.0");
        fields.put("campaign.milestones.1", "goblin_contact_complete");
        fields.put("campaign.milestones.2", "goblin_village_route_unlocked");
        fields.put("campaign.milestones.count", "4");
        fields.put("campaign.playerY", "58.0");
        fields.put("campaign.milestones.0", "awakening_complete");
        fields.put("campaign.areaId", "jura_forest_approach");

        SaveDocument migrated = CampaignSaveMigrations.currentChain()
                .migrateToCurrent(new SaveDocument(1, fields));
        CampaignState state = new CampaignSaveCodec().decodeState(migrated);

        assertEquals(3, migrated.schemaVersion());
        assertEquals("jura_forest_approach", state.checkpoint().areaId());
        assertEquals("dire_wolf_conflict", state.narrativeFlags().chapterId());
        assertEquals(
                QuestStatus.AVAILABLE,
                state.requireQuest(CampaignQuests.DEFEND_GOBLIN_VILLAGE).status());
        assertEquals("3", migrated.fields().get("campaign.quests.count"));
        assertEquals("CAMP", migrated.fields().get("town.stage"));
    }

    @Test
    void currentDocumentPassesThroughMigrationChainUnchanged() {
        SaveDocument current = new CampaignSaveCodec().encode(new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "cave_gallery",
                176,
                432,
                java.util.Set.of("awakening_complete")));

        assertSame(current, CampaignSaveMigrations.currentChain().migrateToCurrent(current));
    }
}
