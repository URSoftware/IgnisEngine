package com.rimurusurvivors.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** Estado minimo e imutavel necessario para retomar a campanha narrativa. */
public record CampaignSnapshot(
        int schemaVersion,
        String areaId,
        double playerX,
        double playerY,
        Set<String> completedMilestones) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CampaignSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Campaign snapshot must use the current schema version.");
        }
        if (areaId == null || areaId.isBlank()) {
            throw new IllegalArgumentException("Campaign area id is required.");
        }
        if (!Double.isFinite(playerX) || !Double.isFinite(playerY)) {
            throw new IllegalArgumentException("Campaign player coordinates must be finite.");
        }
        LinkedHashSet<String> milestones = new LinkedHashSet<>();
        if (completedMilestones != null) {
            for (String milestone : completedMilestones) {
                if (milestone == null || milestone.isBlank()) {
                    throw new IllegalArgumentException("Campaign milestone ids must not be blank.");
                }
                milestones.add(milestone);
            }
        }
        completedMilestones = Set.copyOf(milestones);
    }
}
