package com.rimurusurvivors.domain;

/**
 * Precisao do tempo de reacao defensiva do jogador.
 */
public enum ReactionTiming {
    NONE(1.0, 0.0),
    LATE(0.8, 0.1),
    EARLY(0.6, 0.2),
    GOOD(0.3, 0.5),
    PERFECT(0.0, 1.0);

    private final double damageMultiplier;
    private final double moraleImpactMultiplier;

    ReactionTiming(double damageMultiplier, double moraleImpactMultiplier) {
        this.damageMultiplier = damageMultiplier;
        this.moraleImpactMultiplier = moraleImpactMultiplier;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    public double moraleImpactMultiplier() {
        return moraleImpactMultiplier;
    }
}
