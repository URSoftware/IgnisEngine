package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Adiciona o estado inicial de Tempest aos saves de campanha schema 2. */
public final class CampaignSaveV2ToV3Migration implements SaveMigration {

    private static final String CHAPTER_ID = "narrative.chapterId";
    private static final String FLAG_COUNT = "narrative.flags.count";
    private static final String FLAG_PREFIX = "narrative.flags.";

    @Override
    public int sourceVersion() {
        return 2;
    }

    @Override
    public int targetVersion() {
        return 3;
    }

    @Override
    public SaveDocument migrate(SaveDocument source) {
        if (source == null || source.schemaVersion() != sourceVersion()) {
            throw new IllegalArgumentException("Campaign v2 save document is required.");
        }
        NarrativeFlags narrativeFlags = new NarrativeFlags(
                source.requireField(CHAPTER_ID),
                parseFlags(source));
        Map<String, String> fields = new LinkedHashMap<>(source.fields());
        CampaignSaveCodec.putTown(fields, TownState.initial(narrativeFlags));
        return new SaveDocument(targetVersion(), fields);
    }

    private static Set<String> parseFlags(SaveDocument source) {
        int count = parseNonNegativeInt(source.requireField(FLAG_COUNT));
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String flag = source.requireField(FLAG_PREFIX + index);
            if (flag.isBlank() || !flags.add(flag)) {
                throw new IllegalArgumentException(
                        "Blank or duplicate narrative flag in v2 save.");
            }
        }
        return flags;
    }

    private static int parseNonNegativeInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("V2 save count must be non-negative.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid v2 integer field.", exception);
        }
    }
}
