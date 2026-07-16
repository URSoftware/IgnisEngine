package com.rimurusurvivors.domain;

/** Evento pontual usado pelo adaptador para som, particulas e mensagens. */
public record RunEvent(RunEventType type, String detail, double x, double y) {
}
