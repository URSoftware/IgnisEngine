package com.rimurusurvivors.domain;

/** Estado semantico esperado ao final de uma acao da fita de exploracao. */
public record ExplorationSemanticCheckpoint(
        String name,
        int afterActionIndex,
        String areaId,
        Boolean dialogueActive,
        ExplorationEventType eventType,
        String eventDetail) {

    public ExplorationSemanticCheckpoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Checkpoint name is required.");
        }
        if (afterActionIndex < 0) {
            throw new IllegalArgumentException("Checkpoint action index must be non-negative.");
        }
        if (areaId == null && dialogueActive == null && eventType == null) {
            throw new IllegalArgumentException("Checkpoint must declare an expected semantic value.");
        }
        if (eventType == null && eventDetail != null) {
            throw new IllegalArgumentException("Event detail requires an event type.");
        }
    }
}
