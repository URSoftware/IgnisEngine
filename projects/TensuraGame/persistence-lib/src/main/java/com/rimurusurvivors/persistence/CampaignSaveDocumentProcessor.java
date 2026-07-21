package com.rimurusurvivors.persistence;

import com.rimurusurvivors.domain.CampaignSaveCodec;
import com.rimurusurvivors.domain.SaveDocument;
import com.rimurusurvivors.domain.SaveMigrationChain;

/** Applies domain migrations and semantic validation before a document reaches gameplay. */
public final class CampaignSaveDocumentProcessor implements SaveDocumentProcessor {

    private final SaveMigrationChain migrations;
    private final CampaignSaveCodec campaignCodec;

    public CampaignSaveDocumentProcessor(
            SaveMigrationChain migrations, CampaignSaveCodec campaignCodec) {
        if (migrations == null || campaignCodec == null) {
            throw new IllegalArgumentException("Save migrations and campaign codec are required.");
        }
        this.migrations = migrations;
        this.campaignCodec = campaignCodec;
    }

    @Override
    public SaveDocument process(SaveDocument document) {
        SaveDocument migrated = migrations.migrateToCurrent(document);
        campaignCodec.decode(migrated);
        return migrated;
    }
}
