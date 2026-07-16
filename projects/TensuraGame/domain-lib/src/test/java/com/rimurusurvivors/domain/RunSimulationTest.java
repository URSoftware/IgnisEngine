package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSimulationTest {

    private static WeaponProgression progression() {
        return new WeaponProgression(List.of(
                new WeaponLevelStats(1, 16, 2.20, 1, 0.90, 0, 0, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(2, 4, 0, 0, 0, 0, 1, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(3, 0, -0.10, 0, 0, 0.20, 0, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 1.0, "ranga"),
                new WeaponLevelStats(5, 0, 0, 0, 0.25, 0, 0, 0.25, 0, 0, 1.0, null),
                new WeaponLevelStats(6, 6, 0, 0, 0, 0, 1, 0, 0, 0, 1.0, null),
                new WeaponLevelStats(7, 0, -0.35, 0, 0, 0, 0, 0, 0.50, 0, 1.0, null),
                new WeaponLevelStats(8, 0, 0, 1, 0, 0, 0, 0, 0, 0.35, 1.30, null)));
    }

    @Test
    void movesDiagonallyWithoutSpeedBoost() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        simulation.update(0.05, new RunInput(1, 1));
        RunSnapshot snapshot = simulation.snapshot();

        assertEquals(snapshot.playerX(), snapshot.playerY(), 1e-9);
        assertTrue(snapshot.playerX() < 145 * 0.05);
    }

    @Test
    void progressionUnlocksAllCanonicalFormsAndRanga() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        simulation.grantExperience(50_000);
        for (int i = 0; i < 7; i++) assertTrue(simulation.chooseUpgrade(UpgradeChoice.PREDATOR));
        for (int i = 0; i < 4; i++) assertTrue(simulation.chooseUpgrade(UpgradeChoice.GREAT_SAGE));
        for (int i = 0; i < 7; i++) assertTrue(simulation.chooseUpgrade(UpgradeChoice.REGENERATION));
        RunSnapshot snapshot = simulation.snapshot();

        assertTrue(snapshot.level() >= 60);
        assertEquals(RimuruForm.DEMON_LORD, snapshot.form());
        assertTrue(snapshot.rangaSummoned());
        assertTrue(snapshot.cielAwakened());
        assertTrue(snapshot.azathothAwakened());
        assertEquals(0, snapshot.pendingUpgrades());
    }

    @Test
    void levelUpWaitsForARealUpgradeChoice() {
        RunSimulation simulation = new RunSimulation(progression(), 3);
        simulation.grantExperience(20);
        RunSnapshot before = simulation.snapshot();

        assertTrue(before.pendingUpgrades() > 0);
        assertTrue(simulation.chooseUpgrade(UpgradeChoice.REGENERATION));
        RunSnapshot after = simulation.snapshot();

        assertEquals(2, after.regenerationLevel());
        assertEquals(before.pendingUpgrades() - 1, after.pendingUpgrades());
        assertTrue(after.maxHealth() > before.maxHealth());
    }

    @Test
    void spawnsEnemiesAndAutomaticallyAttacks() {
        RunSimulation simulation = new RunSimulation(progression(), 2);
        for (int i = 0; i < 300; i++) simulation.update(1.0 / 60.0, RunInput.NONE);
        RunSnapshot snapshot = simulation.snapshot();

        assertFalse(snapshot.entities().isEmpty());
        assertTrue(snapshot.entities().stream().anyMatch(entity ->
                entity.kind() == WorldEntityKind.GOBLIN
                        || entity.kind() == WorldEntityKind.DIRE_WOLF));
    }

    @Test
    void azathothCanCompleteTheReaperEncounter() {
        RunSimulation simulation = new RunSimulation(progression(), 7);
        simulation.grantExperience(6_000);
        while (simulation.chooseUpgrade(UpgradeChoice.PREDATOR)) { }
        while (simulation.chooseUpgrade(UpgradeChoice.GREAT_SAGE)) { }
        while (simulation.chooseUpgrade(UpgradeChoice.REGENERATION)) { }

        for (int i = 0; i < 7_200 && !simulation.snapshot().victory(); i++) {
            simulation.update(0.05, RunInput.NONE);
        }

        assertTrue(simulation.snapshot().victory());
    }
}
