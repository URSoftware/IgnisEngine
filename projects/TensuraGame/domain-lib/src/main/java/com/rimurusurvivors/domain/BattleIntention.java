package com.rimurusurvivors.domain;

/**
 * Intencao de acao do lider dos lobos para o turno atual.
 */
public record BattleIntention(
        LeaderPhase phase,
        String actionName,
        int baseDamage,
        boolean requiresReaction,
        String telegraphHint) {

    public BattleIntention {
        if (phase == null || actionName == null || actionName.isBlank() || telegraphHint == null) {
            throw new IllegalArgumentException("BattleIntention arguments must be valid.");
        }
        if (baseDamage < 0) {
            throw new IllegalArgumentException("baseDamage cannot be negative.");
        }
    }
}
