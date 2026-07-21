package com.rimurusurvivors.domain;

/**
 * Medidas de preparacao defensiva escolhidas para a defesa da aldeia goblin.
 */
public enum VillagePreparation {
    REINFORCE_ENTRANCE("Reforcar Entrada", "Posiciona barreiras de madeira, reduzindo o dano da matilha."),
    LIGHT_FLANK("Iluminar Flanco", "Distribui tochas, antecipando investidas e aumentando a janela de reacao."),
    CONTROLLED_BAIT("Isca Controlada", "Usa rastros para separar lobos de apoio, reduzindo o HP inicial do lider.");

    private final String title;
    private final String description;

    VillagePreparation(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
