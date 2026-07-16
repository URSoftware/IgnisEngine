package com.rimurusurvivors.domain;

/** Porta de RevivalAnalysisResult (RimuruRuntimeRules.cs). */
public record RevivalAnalysisResult(
        String enemyFamilyId,
        boolean isNewEnemyAnalysis,
        boolean immunityGranted,
        boolean cielAwakened,
        boolean abilityCopied) {
}