package com.rimurusurvivors.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Simulacao deterministica de exploracao: movimento em 8 direcoes, colisao contra o
 * mapa logico, foco de interacao por proximidade, dialogo e transicao de area. Nao
 * conhece a Ignis: entrada, render, som e camera sao responsabilidade do adaptador do
 * projeto — mesmo contrato de RunSimulation.
 */
public final class ExplorationSimulation {

    private static final double MOVE_SPEED = 140.0;
    private static final double PLAYER_RADIUS = 10.0;
    private static final double INTERACT_RANGE = 8.0;

    private final Map<String, ExplorationArea> areas;
    private final Map<String, DialogueScript> dialogues;
    private final List<ExplorationEvent> events = new ArrayList<>();

    private ExplorationArea currentArea;
    private double playerX;
    private double playerY;
    private boolean playerMoving;
    private String focusedInteractableId;

    private DialogueScript activeDialogue;
    private int dialogueLineIndex;
    // Ids ja coletados: evita que um COLLECT emita ITEM_COLLECTED de novo a cada
    // interacao repetida (o alvo continua interativo/examinavel, so o evento de
    // coleta em si e um disparo unico).
    private final Set<String> collectedIds = new HashSet<>();

    public ExplorationSimulation(Map<String, ExplorationArea> areas, Map<String, DialogueScript> dialogues,
            String startAreaId, double startX, double startY) {
        if (areas == null || areas.isEmpty()) {
            throw new IllegalArgumentException("At least one area is required.");
        }
        this.areas = Map.copyOf(areas);
        this.dialogues = dialogues == null ? Map.of() : Map.copyOf(dialogues);
        this.currentArea = this.areas.get(startAreaId);
        if (this.currentArea == null) {
            throw new IllegalArgumentException("Unknown start area: " + startAreaId);
        }
        if (this.currentArea.map().isBlockedAt(startX, startY)) {
            throw new IllegalArgumentException(
                    "Spawn point (" + startX + "," + startY + ") falls on a blocked cell of area " + startAreaId);
        }
        this.playerX = startX;
        this.playerY = startY;
    }

    public ExplorationSnapshot update(double deltaSeconds, RunInput movement, boolean interactPressed) {
        events.clear();
        if (deltaSeconds <= 0) {
            return snapshot();
        }
        RunInput safeMovement = movement == null ? RunInput.NONE : movement;
        double dt = Math.min(deltaSeconds, 0.05);

        if (activeDialogue != null) {
            playerMoving = false;
            if (interactPressed) {
                advanceDialogue();
            }
            return snapshot();
        }

        moveAndCollide(safeMovement.normalized(), dt);
        updateFocus();
        if (interactPressed && focusedInteractableId != null) {
            triggerInteraction(currentArea.findInteractable(focusedInteractableId));
        }
        return snapshot();
    }

    private void moveAndCollide(RunInput direction, double dt) {
        double startX = playerX;
        double startY = playerY;
        double nextX = playerX + direction.horizontal() * MOVE_SPEED * dt;
        double nextY = playerY + direction.vertical() * MOVE_SPEED * dt;

        // Eixos resolvidos em separado para "deslizar" ao longo de uma parede em vez
        // de travar quando so uma das componentes do movimento colide.
        if (!collidesAt(nextX, playerY)) {
            playerX = nextX;
        }
        if (!collidesAt(playerX, nextY)) {
            playerY = nextY;
        }
        // Reflete o deslocamento REAL, nao so a intencao de input: empurrar uma
        // parede nao deve manter a animacao de andar tocando no lugar.
        playerMoving = playerX != startX || playerY != startY;
    }

    private boolean collidesAt(double centerX, double centerY) {
        ExplorationMap map = currentArea.map();
        // Amostra o perimetro do corpo do jogador, nao so o centro — senao ele
        // atravessa metade de uma parede antes do bloqueio ser detectado.
        return map.isBlockedAt(centerX - PLAYER_RADIUS, centerY)
                || map.isBlockedAt(centerX + PLAYER_RADIUS, centerY)
                || map.isBlockedAt(centerX, centerY - PLAYER_RADIUS)
                || map.isBlockedAt(centerX, centerY + PLAYER_RADIUS);
    }

    private void updateFocus() {
        Interactable nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        // Sem alocar Vec2 por interagivel por tick (ver historico de OOM da sessao
        // longa): distancia ao quadrado em doubles crus e comparacao direta.
        for (Interactable candidate : currentArea.interactables()) {
            if (!candidate.isNear(playerX, playerY, INTERACT_RANGE)) {
                continue;
            }
            double dx = candidate.x() - playerX;
            double dy = candidate.y() - playerY;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = candidate;
            }
        }
        String nextFocusId = nearest == null ? null : nearest.id();
        if (Objects.equals(nextFocusId, focusedInteractableId)) {
            return;
        }
        if (focusedInteractableId != null) {
            events.add(new ExplorationEvent(
                    ExplorationEventType.INTERACTABLE_UNFOCUSED, focusedInteractableId, playerX, playerY));
        }
        focusedInteractableId = nextFocusId;
        if (focusedInteractableId != null) {
            events.add(new ExplorationEvent(
                    ExplorationEventType.INTERACTABLE_FOCUSED, focusedInteractableId, playerX, playerY));
        }
    }

    private void triggerInteraction(Interactable interactable) {
        if (interactable == null) {
            return;
        }
        if (interactable.verb() == InteractionVerb.ENTER) {
            changeArea(interactable);
            return;
        }
        // Coleta e um disparo unico por id: o alvo continua interativo (pode ser
        // reexaminado), mas ITEM_COLLECTED so acontece na primeira vez — sem isto,
        // segurar a tecla de interacao gerava coleta infinita.
        if (interactable.collectible() && collectedIds.add(interactable.id())) {
            events.add(new ExplorationEvent(
                    ExplorationEventType.ITEM_COLLECTED, interactable.id(), interactable.x(), interactable.y()));
        }
        if (interactable.dialogueId() != null) {
            startDialogue(interactable.dialogueId());
        }
    }

    private void changeArea(Interactable exit) {
        ExplorationArea target = areas.get(exit.targetAreaId());
        if (target == null) {
            throw new IllegalStateException("Unknown target area: " + exit.targetAreaId());
        }
        if (target.map().isBlockedAt(exit.targetSpawnX(), exit.targetSpawnY())) {
            throw new IllegalStateException("Target spawn (" + exit.targetSpawnX() + "," + exit.targetSpawnY()
                    + ") of exit '" + exit.id() + "' falls on a blocked cell of area " + target.id());
        }
        currentArea = target;
        playerX = exit.targetSpawnX();
        playerY = exit.targetSpawnY();
        focusedInteractableId = null;
        events.add(new ExplorationEvent(ExplorationEventType.AREA_CHANGED, target.id(), playerX, playerY));
    }

    private void startDialogue(String dialogueId) {
        DialogueScript script = dialogues.get(dialogueId);
        if (script == null) {
            return;
        }
        activeDialogue = script;
        dialogueLineIndex = 0;
        events.add(new ExplorationEvent(ExplorationEventType.DIALOGUE_STARTED, dialogueId, playerX, playerY));
    }

    private void advanceDialogue() {
        dialogueLineIndex++;
        if (dialogueLineIndex >= activeDialogue.lineCount()) {
            events.add(new ExplorationEvent(ExplorationEventType.DIALOGUE_ENDED, activeDialogue.id(), playerX, playerY));
            activeDialogue = null;
            dialogueLineIndex = 0;
            return;
        }
        events.add(new ExplorationEvent(ExplorationEventType.DIALOGUE_ADVANCED, activeDialogue.id(), playerX, playerY));
    }

    public ExplorationSnapshot snapshot() {
        DialogueLine currentLine = activeDialogue != null ? activeDialogue.line(dialogueLineIndex) : null;
        return new ExplorationSnapshot(
                currentArea.id(),
                playerX,
                playerY,
                playerMoving,
                focusedInteractableId,
                activeDialogue != null,
                currentLine != null ? currentLine.speaker() : null,
                currentLine != null ? currentLine.text() : null,
                activeDialogue != null && dialogueLineIndex < activeDialogue.lineCount() - 1,
                List.copyOf(events));
    }
}
