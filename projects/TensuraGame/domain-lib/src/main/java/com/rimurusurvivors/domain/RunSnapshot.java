package com.rimurusurvivors.domain;

import java.util.List;

/** Fotografia imutavel da partida depois de um passo da simulacao. */
public record RunSnapshot(
        double elapsedSeconds,
        double playerX,
        double playerY,
        double health,
        double maxHealth,
        int level,
        int experience,
        int experienceToNextLevel,
        int kills,
        int weaponLevel,
        int passiveLevel,
        int regenerationLevel,
        int pendingUpgrades,
        RimuruForm form,
        boolean rangaSummoned,
        boolean cielAwakened,
        boolean azathothAwakened,
        boolean gameOver,
        boolean victory,
        List<WorldEntitySnapshot> entities,
        List<RunEvent> events) {
}
