package com.rimurusurvivors.domain;

/**
 * Eventos pontuais que a exploracao emite para o apresentador. Semanticos, como em
 * RunEventType: dizem o que aconteceu, nunca com que arte ou som isso e mostrado.
 */
public enum ExplorationEventType {
    INTERACTABLE_FOCUSED,
    INTERACTABLE_UNFOCUSED,
    DIALOGUE_STARTED,
    DIALOGUE_ADVANCED,
    DIALOGUE_ENDED,
    ITEM_COLLECTED,
    AREA_CHANGED
}
