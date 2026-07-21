package com.rimurusurvivors.domain;

/**
 * Resultado possivel do duelo de combate.
 */
public enum BattleOutcome {
    IN_PROGRESS("Em andamento"),
    VICTORY_SURRENDER("Vitoria por Rendicao e Pacto"),
    VICTORY_SUBDUED("Vitoria por Sobrevivencia Fisica"),
    DEFEAT("Derrota");

    private final String description;

    BattleOutcome(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
