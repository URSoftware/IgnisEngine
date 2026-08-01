package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CampaignSaveV2ToV3MigrationTest {

    @Test
    void schemaTwoCampaignReceivesDeterministicTownState() {
        SaveDocument current = new CampaignSaveCodec().encode(
                new CampaignSnapshot(
                        CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                        "goblin_village",
                        96,
                        256,
                        Set.of("ranga_naming_complete")));
        Map<String, String> schemaTwoFields = new LinkedHashMap<>();
        current.fields().forEach((key, value) -> {
            if (!key.startsWith("town.")) {
                schemaTwoFields.put(key, value);
            }
        });

        SaveDocument migrated = CampaignSaveMigrations.currentChain()
                .migrateToCurrent(new SaveDocument(2, schemaTwoFields));
        CampaignState restored = new CampaignSaveCodec().decodeState(migrated);

        assertEquals(3, migrated.schemaVersion());
        assertEquals(TownStage.GOBLIN_VILLAGE, restored.townState().stage());
        assertEquals(TownResourceBundle.EMPTY, restored.townState().resources());
        assertTrue(restored.townState().projects().values().stream()
                .allMatch(project -> project.status() == TownProjectStatus.LOCKED));
    }
}
