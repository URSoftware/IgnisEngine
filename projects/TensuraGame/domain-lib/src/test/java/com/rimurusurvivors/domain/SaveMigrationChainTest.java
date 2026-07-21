package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SaveMigrationChainTest {

    @Test
    void migrationsRunInOrderFromVersionZeroToTwo() {
        SaveMigrationChain chain = new SaveMigrationChain(2, List.of(
                migration(0, fields -> {
                    fields.put("campaign.areaId", fields.remove("zone"));
                    return fields;
                }),
                migration(1, fields -> {
                    fields.put("campaign.playerX", "0");
                    fields.put("campaign.playerY", "0");
                    return fields;
                })));

        SaveDocument migrated = chain.migrateToCurrent(new SaveDocument(0, Map.of("zone", "cave")));

        assertEquals(2, migrated.schemaVersion());
        assertEquals("cave", migrated.fields().get("campaign.areaId"));
        assertEquals("0", migrated.fields().get("campaign.playerX"));
    }

    @Test
    void constructorRejectsGapsAndNonConsecutiveMigrations() {
        assertThrows(IllegalArgumentException.class, () -> new SaveMigrationChain(3, List.of(
                migration(0, fields -> fields),
                migration(2, fields -> fields))));
        assertThrows(IllegalArgumentException.class, () -> new SaveMigrationChain(2, List.of(
                new SaveMigration() {
                    public int sourceVersion() { return 0; }
                    public int targetVersion() { return 2; }
                    public SaveDocument migrate(SaveDocument source) { return source; }
                })));
        assertThrows(IllegalArgumentException.class, () ->
                new SaveMigrationChain(1, Collections.singletonList(null)));
    }

    @Test
    void migrationFailsWhenTheRequiredStepIsMissing() {
        SaveMigrationChain chain = new SaveMigrationChain(2, List.of(migration(1, fields -> fields)));

        assertThrows(IllegalStateException.class, () ->
                chain.migrateToCurrent(new SaveDocument(0, Map.of())));
    }

    @Test
    void migrationRejectsFutureDocumentsAndInvalidResults() {
        SaveMigration invalid = new SaveMigration() {
            public int sourceVersion() { return 0; }
            public int targetVersion() { return 1; }
            public SaveDocument migrate(SaveDocument source) { return new SaveDocument(0, source.fields()); }
        };
        SaveMigrationChain chain = new SaveMigrationChain(1, List.of(invalid));

        assertThrows(IllegalArgumentException.class, () ->
                chain.migrateToCurrent(new SaveDocument(2, Map.of())));
        assertThrows(IllegalStateException.class, () ->
                chain.migrateToCurrent(new SaveDocument(0, Map.of())));
    }

    private static SaveMigration migration(int sourceVersion, FieldMigration migration) {
        return new SaveMigration() {
            public int sourceVersion() {
                return sourceVersion;
            }

            public int targetVersion() {
                return sourceVersion + 1;
            }

            public SaveDocument migrate(SaveDocument source) {
                Map<String, String> fields = new LinkedHashMap<>(source.fields());
                return new SaveDocument(targetVersion(), migration.apply(fields));
            }
        };
    }

    @FunctionalInterface
    private interface FieldMigration {
        Map<String, String> apply(Map<String, String> fields);
    }
}
