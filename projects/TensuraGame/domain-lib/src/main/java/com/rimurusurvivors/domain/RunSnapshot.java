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
        /**
         * Se o jogador se deslocou neste passo. Escolhe a locomocao no apresentador
         * (clipe de andar x repouso) — e estado continuo, por isso vive aqui e nao
         * como evento por tick. Ver o contrato de direcao visual.
         */
        boolean playerMoving,
        boolean rangaSummoned,
        boolean cielAwakened,
        boolean azathothAwakened,
        boolean gameOver,
        boolean victory,
        List<WorldEntitySnapshot> entities,
        List<RunEvent> events) {
}
