package com.rimurusurvivors.domain;

import java.util.List;

/** Fotografia imutavel da exploracao depois de um passo da simulacao. */
public record ExplorationSnapshot(
        String areaId,
        double playerX,
        double playerY,
        boolean playerMoving,
        String focusedInteractableId,
        boolean dialogueActive,
        String dialogueSpeaker,
        String dialogueText,
        boolean dialogueHasMore,
        List<ExplorationEvent> events) {
}
