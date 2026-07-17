package com.rimurusurvivors.domain;

/** Evento pontual usado pelo adaptador para som, UI e mensagens. */
public record ExplorationEvent(ExplorationEventType type, String detail, double x, double y) {
}
