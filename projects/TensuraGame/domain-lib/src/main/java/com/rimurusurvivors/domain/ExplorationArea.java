package com.rimurusurvivors.domain;

import java.util.List;

/** Uma area jogavel: mapa de colisao mais os alvos interativos que ela contem. */
public record ExplorationArea(String id, ExplorationMap map, List<Interactable> interactables) {

    public ExplorationArea {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Area id is required.");
        }
        if (map == null) {
            throw new IllegalArgumentException("Area map is required.");
        }
        interactables = interactables == null ? List.of() : List.copyOf(interactables);
    }

    public Interactable findInteractable(String interactableId) {
        for (Interactable interactable : interactables) {
            if (interactable.id().equals(interactableId)) {
                return interactable;
            }
        }
        return null;
    }
}
