package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fidelidade da progressao de niveis da katana Predador contra
 * mods/vampire-survivors-rimuru/data/rimuru-progression.json (weapon.levels).
 * Nivel 1 e absoluto; 2-8 sao deltas somados; nivel 8 tem damageMultiplier final.
 */
class WeaponProgressionTest {

    private static WeaponProgression realProgression() {
        List<WeaponLevelStats> levels = List.of(
                new WeaponLevelStats(1, 16, 2.20, 1, 0.90, 0, 0, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(2, 4, 0, 0, 0, 0, 1, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(3, 0, -0.10, 0, 0, 0.20, 0, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 1.0, "ranga"),
                new WeaponLevelStats(5, 0, 0, 0, 0.25, 0, 0, 0.25, 0, 0, 1.0, null),
                new WeaponLevelStats(6, 6, 0, 0, 0, 0, 1, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(7, 0, -0.35, 0, 0, 0, 0, 0, 0.50, 0, 1.0, null),
                new WeaponLevelStats(8, 0, 0, 1, 0, 0, 0, 0, 0, 0.35, 1.30, null));
        return new WeaponProgression(levels);
    }

    @Test
    void level1IsAbsoluteBase() {
        var stats = realProgression().statsAtLevel(1);
        assertEquals(16.0, stats.damage(), 1e-9);
        assertEquals(2.20, stats.cooldown(), 1e-9);
        assertEquals(1, stats.amount());
        assertEquals(0.90, stats.area(), 1e-9);
    }

    @Test
    void level8DamageMatchesKnownTotal() {
        // 16 (nivel 1) + 4 (nivel 2) + 4 (nivel 4) + 6 (nivel 6) = 30, x1.30 = 39.0
        var stats = realProgression().statsAtLevel(8);
        assertEquals(39.0, stats.damage(), 1e-9);
    }

    @Test
    void level8AccumulatesAllOtherDeltas() {
        var stats = realProgression().statsAtLevel(8);
        assertEquals(2.20 - 0.10 - 0.35, stats.cooldown(), 1e-9); // 1.75
        assertEquals(2, stats.amount()); // 1 (nivel1) + 1 (nivel8)
        assertEquals(0.90 + 0.25, stats.area(), 1e-9); // 1.15
        assertEquals(0.20, stats.speed(), 1e-9);
        assertEquals(2, stats.pierce()); // nivel2 + nivel6
        assertEquals(0.25, stats.slowSeconds(), 1e-9);
        assertEquals(0.50, stats.returnDamage(), 1e-9);
        assertEquals(0.35, stats.slowCap(), 1e-9);
    }

    @Test
    void intermediateLevelDoesNotIncludeFutureDeltas() {
        // Nivel 3 nao deve ainda contar o dano do nivel 4/6/8
        var stats = realProgression().statsAtLevel(3);
        assertEquals(20.0, stats.damage(), 1e-9); // 16 + 4 (nivel 2), sem multiplicador ainda
        assertEquals(2.10, stats.cooldown(), 1e-9); // 2.20 - 0.10
    }

    @Test
    void levelOutOfRangeThrows() {
        WeaponProgression progression = realProgression();
        assertThrows(IllegalArgumentException.class, () -> progression.statsAtLevel(0));
        assertThrows(IllegalArgumentException.class, () -> progression.statsAtLevel(9));
    }
}
