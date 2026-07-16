package com.rimurusurvivors.domain;

/** Estado visual minimo de uma entidade da simulacao. */
public record WorldEntitySnapshot(
        long id,
        WorldEntityKind kind,
        double x,
        double y,
        double rotation,
        double healthRatio,
        boolean boss) {
}
