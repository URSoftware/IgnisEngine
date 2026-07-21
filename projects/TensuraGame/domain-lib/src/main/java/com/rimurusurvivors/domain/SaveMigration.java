package com.rimurusurvivors.domain;

/** Uma transformacao pura entre duas versoes consecutivas do save. */
public interface SaveMigration {

    int sourceVersion();

    int targetVersion();

    SaveDocument migrate(SaveDocument source);
}
