package com.rimurusurvivors.domain;

/**
 * Verbo do prompt contextual (icone + palavra) mostrado quando o jogador se
 * aproxima de um alvo interativo. O dominio so escolhe o verbo; qual icone/traducao
 * usar e responsabilidade da UI.
 */
public enum InteractionVerb {
    EXAMINE,
    TALK,
    COLLECT,
    ENTER
}
