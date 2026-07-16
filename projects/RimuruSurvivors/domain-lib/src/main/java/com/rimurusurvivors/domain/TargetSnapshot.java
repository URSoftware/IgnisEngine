package com.rimurusurvivors.domain;

/** Porta de TargetSnapshot (RimuruRuntimeRules.cs). */
public record TargetSnapshot(
        String enemyFamilyId,
        Vec2 position,
        Vec2 velocity,
        float healthRatio,
        boolean isThreat,
        boolean isBoss) {
}