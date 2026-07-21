package com.rimurusurvivors.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executa migracoes consecutivas e rejeita lacunas ou versoes futuras. */
public final class SaveMigrationChain {

    private final int currentVersion;
    private final Map<Integer, SaveMigration> migrationsBySource;

    public SaveMigrationChain(int currentVersion, List<SaveMigration> migrations) {
        if (currentVersion < 0) {
            throw new IllegalArgumentException("Current save version must be non-negative.");
        }
        this.currentVersion = currentVersion;
        this.migrationsBySource = validateMigrations(currentVersion, migrations);
    }

    public SaveDocument migrateToCurrent(SaveDocument source) {
        if (source == null) {
            throw new IllegalArgumentException("Save document is required.");
        }
        if (source.schemaVersion() > currentVersion) {
            throw new IllegalArgumentException(
                    "Save schema is newer than the current version: " + source.schemaVersion());
        }

        SaveDocument current = source;
        while (current.schemaVersion() < currentVersion) {
            SaveMigration migration = migrationsBySource.get(current.schemaVersion());
            if (migration == null) {
                throw new IllegalStateException(
                        "Missing save migration from version " + current.schemaVersion());
            }
            SaveDocument migrated = migration.migrate(current);
            if (migrated == null || migrated.schemaVersion() != migration.targetVersion()) {
                throw new IllegalStateException(
                        "Save migration " + migration.sourceVersion() + " -> "
                                + migration.targetVersion() + " returned an invalid version.");
            }
            current = migrated;
        }
        return current;
    }

    private static Map<Integer, SaveMigration> validateMigrations(
            int currentVersion, List<SaveMigration> migrations) {
        List<SaveMigration> candidates = migrations == null ? List.of() : migrations;
        if (candidates.stream().anyMatch(migration -> migration == null)) {
            throw new IllegalArgumentException("Save migrations must not contain null.");
        }
        List<SaveMigration> ordered = candidates.stream()
                .sorted(Comparator.comparingInt(SaveMigration::sourceVersion))
                .toList();
        Map<Integer, SaveMigration> result = new HashMap<>();
        Integer previousTarget = null;
        for (SaveMigration migration : ordered) {
            if (migration.targetVersion() != migration.sourceVersion() + 1) {
                throw new IllegalArgumentException("Save migrations must advance exactly one version.");
            }
            if (migration.targetVersion() > currentVersion) {
                throw new IllegalArgumentException("Save migration targets a future version.");
            }
            if (previousTarget != null && migration.sourceVersion() != previousTarget) {
                throw new IllegalArgumentException("Save migration chain contains a version gap.");
            }
            if (result.put(migration.sourceVersion(), migration) != null) {
                throw new IllegalArgumentException(
                        "Duplicate save migration from version " + migration.sourceVersion());
            }
            previousTarget = migration.targetVersion();
        }
        return Map.copyOf(result);
    }
}
