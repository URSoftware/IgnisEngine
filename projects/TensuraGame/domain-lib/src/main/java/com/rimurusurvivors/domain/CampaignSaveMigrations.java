package com.rimurusurvivors.domain;

import java.util.List;

/** Ponto unico de composicao da cadeia de migracoes da campanha. */
public final class CampaignSaveMigrations {

    private CampaignSaveMigrations() {
    }

    public static SaveMigrationChain currentChain() {
        return new SaveMigrationChain(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                List.of(
                        new CampaignSaveV1ToV2Migration(),
                        new CampaignSaveV2ToV3Migration()));
    }
}
