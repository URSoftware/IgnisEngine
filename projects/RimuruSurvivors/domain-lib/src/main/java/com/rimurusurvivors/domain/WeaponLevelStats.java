package com.rimurusurvivors.domain;

/**
 * Um item de weapon.levels em data/rimuru-progression.json. O nivel 1 traz os valores
 * absolutos de base; os niveis 2-8 sao deltas somados sobre o nivel anterior (exceto
 * damageMultiplier, que e um multiplicador final aplicado sobre o dano acumulado —
 * ver WeaponProgression.statsAtLevel).
 */
public record WeaponLevelStats(
        int level,
        double damageDelta,
        double cooldownDelta,
        int amountDelta,
        double areaDelta,
        double speedDelta,
        int pierceDelta,
        double slowSecondsDelta,
        double returnDamageDelta,
        double slowCapDelta,
        double damageMultiplier,
        String summonId) {
}