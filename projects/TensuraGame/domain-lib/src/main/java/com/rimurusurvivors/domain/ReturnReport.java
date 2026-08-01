package com.rimurusurvivors.domain;

import java.util.Set;

/** Resultado agregado de uma aventura apresentado pelo Grande Sabio. */
public record ReturnReport(
        String id,
        TownResourceBundle resources,
        Set<String> discoveries,
        Set<String> people,
        int trustGained) {

    public ReturnReport {
        if (id == null || id.isBlank() || resources == null) {
            throw new IllegalArgumentException("Return report id and resources are required.");
        }
        if (trustGained < 0) {
            throw new IllegalArgumentException("Return report trust must not be negative.");
        }
        discoveries = NarrativeFlags.immutableIds(discoveries, "Return report discovery");
        people = NarrativeFlags.immutableIds(people, "Return report person");
    }
}
