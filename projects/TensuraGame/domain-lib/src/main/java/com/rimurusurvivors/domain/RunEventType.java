package com.rimurusurvivors.domain;

/**
 * Eventos pontuais que a simulacao emite para o apresentador.
 *
 * <p>Sao <b>semanticos</b>: dizem o que aconteceu na partida, nunca com que arte ou
 * som isso e mostrado. O mapeamento evento -> clipe/sprite/audio vive no
 * apresentador (o script da Ignis), e por isso este dominio nao depende de imagem,
 * audio nem da engine — ver o contrato de integracao em
 * {@code 02_design/rimuru-survivors-direcao-visual-fatia-slime.md}.</p>
 */
public enum RunEventType {
    /** Ataque primario da forma atual (hidrolamina, katana, corte do vazio...). */
    ATTACK,
    ENEMY_DEFEATED,
    PLAYER_HIT,
    LEVEL_UP,
    UPGRADE_SELECTED,
    FORM_CHANGED,
    RANGA_SUMMONED,
    CIEL_AWAKENED,
    AZATHOTH_AWAKENED,
    BOSS_SPAWNED,
    VICTORY,
    GAME_OVER,

    /**
     * Pulso do Predador (habilidade da forma Slime). Tipado a parte do ATTACK porque
     * o apresentador precisa sincronizar clipe, aura e som especificos — antes ele
     * tinha de adivinhar isso casando a string do detail ("Predador").
     */
    PREDATOR_CAST,

    /** Investida do Ranga num alvo. Tipado pelo mesmo motivo do PREDATOR_CAST. */
    RANGA_ATTACK
}
