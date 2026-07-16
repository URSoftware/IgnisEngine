package com.rimurusurvivors.domain;

import java.util.List;

/**
 * Acumula os deltas de weapon.levels (data/rimuru-progression.json) num total resolvido
 * pra um nivel dado. Nivel 1 e absoluto, niveis 2-N somam por cima, e qualquer
 * damageMultiplier presente num nivel multiplica o dano acumulado ate ali (so o nivel 8
 * tem hoje, mas o acumulador aceita mais de um sem mudar de forma).
 *
 * <p>Checagem de fidelidade (ver plano, secao "Riscos"): statsAtLevel(8).damage() deve
 * bater 16 + 4 + 4 + 6 = 30, x1.30 = 39.0.
 */
public final class WeaponProgression {

    public record ResolvedStats(
            double damage,
            double cooldown,
            int amount,
            double area,
            double speed,
            int pierce,
            double slowSeconds,
            double returnDamage,
            double slowCap) {
    }

    private final List<WeaponLevelStats> levels;

    public WeaponProgression(List<WeaponLevelStats> levels) {
        this.levels = levels;
    }

    public int maxLevel() {
        return levels.size();
    }

    public ResolvedStats statsAtLevel(int level) {
        if (level < 1 || level > levels.size()) {
            throw new IllegalArgumentException("Nivel de arma fora do intervalo: " + level);
        }

        double damage = 0;
        double cooldown = 0;
        int amount = 0;
        double area = 0;
        double speed = 0;
        int pierce = 0;
        double slowSeconds = 0;
        double returnDamage = 0;
        double slowCap = 0;
        double multiplier = 1.0;

        for (int i = 0; i < level; i++) {
            WeaponLevelStats entry = levels.get(i);
            damage += entry.damageDelta();
            cooldown += entry.cooldownDelta();
            amount += entry.amountDelta();
            area += entry.areaDelta();
            speed += entry.speedDelta();
            pierce += entry.pierceDelta();
            slowSeconds += entry.slowSecondsDelta();
            returnDamage += entry.returnDamageDelta();
            slowCap += entry.slowCapDelta();
            multiplier *= entry.damageMultiplier();
        }

        return new ResolvedStats(
                damage * multiplier, cooldown, amount, area, speed, pierce, slowSeconds, returnDamage, slowCap);
    }
}