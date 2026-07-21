package com.rimurusurvivors.domain;

/**
 * Fases de comportamento do lider dos lobos no duelo.
 */
public enum LeaderPhase {
    PROBING("Sondagem", "Ataque inicial leve para testar as defesas."),
    PACK_PRESSURE("Pressao de Matilha", "Convocacao de apoio e variacao de alvos."),
    DECISIVE_STRIKE("Investida Decisiva", "Ataque direto de alto impacto exigindo reacao defensiva."),
    MORAL_BREAKDOWN("Ruptura de Moral", "Lider hesita apos resposta decisiva, permitindo negociacao ou captura.");

    private final String title;
    private final String description;

    LeaderPhase(String title, String description) {
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
