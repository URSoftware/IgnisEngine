package com.rimurusurvivors.domain;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Promove o snapshot v1 para o estado completo v2 sem depender de filesystem. */
public final class CampaignSaveV1ToV2Migration implements SaveMigration {

    private static final String AREA_ID = "campaign.areaId";
    private static final String PLAYER_X = "campaign.playerX";
    private static final String PLAYER_Y = "campaign.playerY";
    private static final String MILESTONE_COUNT = "campaign.milestones.count";
    private static final String MILESTONE_PREFIX = "campaign.milestones.";

    @Override
    public int sourceVersion() {
        return 1;
    }

    @Override
    public int targetVersion() {
        return 2;
    }

    @Override
    public SaveDocument migrate(SaveDocument source) {
        if (source == null || source.schemaVersion() != sourceVersion()) {
            throw new IllegalArgumentException("Campaign v1 save document is required.");
        }
        CampaignSnapshot checkpoint = new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                source.requireField(AREA_ID),
                parseFiniteDouble(source, PLAYER_X),
                parseFiniteDouble(source, PLAYER_Y),
                parseMilestones(source));
        SaveDocument currentShape = new CampaignSaveCodec().encode(checkpoint);
        Map<String, String> schemaTwoFields = new LinkedHashMap<>();
        currentShape.fields().forEach((key, value) -> {
            if (!key.startsWith("town.")) {
                schemaTwoFields.put(key, value);
            }
        });
        return new SaveDocument(targetVersion(), schemaTwoFields);
    }

    private static Set<String> parseMilestones(SaveDocument source) {
        int count = parseNonNegativeInt(source, MILESTONE_COUNT);
        Set<String> milestones = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String milestone = source.requireField(MILESTONE_PREFIX + index);
            if (milestone.isBlank() || !milestones.add(milestone)) {
                throw new IllegalArgumentException(
                        "Blank or duplicate campaign milestone in v1 save.");
            }
        }
        return milestones;
    }

    private static double parseFiniteDouble(SaveDocument source, String field) {
        try {
            double value = Double.parseDouble(source.requireField(field));
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("V1 save coordinate must be finite: " + field);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid v1 decimal field: " + field, exception);
        }
    }

    private static int parseNonNegativeInt(SaveDocument source, String field) {
        try {
            int value = Integer.parseInt(source.requireField(field));
            if (value < 0) {
                throw new IllegalArgumentException("V1 save count must be non-negative.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid v1 integer field: " + field, exception);
        }
    }
}
