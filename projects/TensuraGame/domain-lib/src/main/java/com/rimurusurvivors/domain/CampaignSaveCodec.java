package com.rimurusurvivors.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conversao deterministica entre o estado tipado atual e um documento neutro. */
public final class CampaignSaveCodec {

    private static final String AREA_ID = "campaign.areaId";
    private static final String PLAYER_X = "campaign.playerX";
    private static final String PLAYER_Y = "campaign.playerY";
    private static final String MILESTONE_COUNT = "campaign.milestones.count";
    private static final String MILESTONE_PREFIX = "campaign.milestones.";

    public SaveDocument encode(CampaignSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Campaign snapshot is required.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(AREA_ID, snapshot.areaId());
        fields.put(PLAYER_X, Double.toString(snapshot.playerX()));
        fields.put(PLAYER_Y, Double.toString(snapshot.playerY()));

        List<String> milestones = new ArrayList<>(snapshot.completedMilestones());
        milestones.sort(String::compareTo);
        fields.put(MILESTONE_COUNT, Integer.toString(milestones.size()));
        for (int index = 0; index < milestones.size(); index++) {
            fields.put(MILESTONE_PREFIX + index, milestones.get(index));
        }
        return new SaveDocument(snapshot.schemaVersion(), fields);
    }

    public CampaignSnapshot decode(SaveDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Save document is required.");
        }
        if (document.schemaVersion() > CampaignSnapshot.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Save schema is newer than this game: " + document.schemaVersion());
        }
        if (document.schemaVersion() < CampaignSnapshot.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Save document must be migrated before decoding: " + document.schemaVersion());
        }

        double playerX = parseFiniteDouble(document, PLAYER_X);
        double playerY = parseFiniteDouble(document, PLAYER_Y);
        int milestoneCount = parseNonNegativeInt(document, MILESTONE_COUNT);
        Set<String> milestones = new LinkedHashSet<>();
        for (int index = 0; index < milestoneCount; index++) {
            String milestone = document.requireField(MILESTONE_PREFIX + index);
            if (!milestones.add(milestone)) {
                throw new IllegalArgumentException("Duplicate campaign milestone in save: " + milestone);
            }
        }
        return new CampaignSnapshot(
                document.schemaVersion(), document.requireField(AREA_ID),
                playerX, playerY, milestones);
    }

    private static double parseFiniteDouble(SaveDocument document, String field) {
        try {
            double value = Double.parseDouble(document.requireField(field));
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Save coordinate must be finite: " + field);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal save field: " + field, exception);
        }
    }

    private static int parseNonNegativeInt(SaveDocument document, String field) {
        try {
            int value = Integer.parseInt(document.requireField(field));
            if (value < 0) {
                throw new IllegalArgumentException("Save count must be non-negative: " + field);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer save field: " + field, exception);
        }
    }
}
