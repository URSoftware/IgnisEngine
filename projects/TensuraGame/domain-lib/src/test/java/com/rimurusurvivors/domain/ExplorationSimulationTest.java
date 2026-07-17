package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationSimulationTest {

    private static final double CELL = 32.0;

    /** Mapa 6x6 com uma parede vertical inteira na coluna 3 (x entre 96 e 128). */
    private static ExplorationMap wallMap() {
        Set<ExplorationMap.Cell> blocked = new java.util.HashSet<>();
        for (int row = 0; row < 6; row++) {
            blocked.add(new ExplorationMap.Cell(3, row));
        }
        return new ExplorationMap(6, 6, CELL, blocked);
    }

    private static ExplorationMap openMap() {
        return new ExplorationMap(6, 6, CELL, Set.of());
    }

    @Test
    void movesDiagonallyWithoutSpeedBoost() {
        ExplorationArea area = new ExplorationArea("start", openMap(), List.of());
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of(), "start", 48, 48);

        ExplorationSnapshot snapshot = simulation.update(0.05, new RunInput(1, 1), false);

        // Partida em (48,48) com deslocamento diagonal igual em x e y: os dois devem
        // permanecer iguais entre si (sem bonus de velocidade na diagonal).
        assertEquals(snapshot.playerX(), snapshot.playerY(), 1e-9);
        assertTrue(snapshot.playerMoving());
        assertTrue(snapshot.playerX() - 48 < 145 * 0.05);
    }

    @Test
    void wallBlocksMovementPastTheBarrierColumn() {
        ExplorationArea area = new ExplorationArea("start", wallMap(), List.of());
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of(), "start", 48, 48);

        ExplorationSnapshot snapshot = null;
        for (int i = 0; i < 200; i++) {
            snapshot = simulation.update(0.05, new RunInput(1, 0), false);
        }

        // Parede comeca em x=96; o corpo do jogador (raio 10) nao deve ultrapassar isso.
        assertTrue(snapshot.playerX() < 96);
    }

    @Test
    void focusEventsFireWhenEnteringAndLeavingRange() {
        Interactable crystal = new Interactable(
                "cave_crystal_cyan", 70, 48, 12, InteractionVerb.EXAMINE, "crystal_line", false, null, 0, 0);
        ExplorationArea area = new ExplorationArea("start", openMap(), List.of(crystal));
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of("crystal_line", oneLineDialogue("crystal_line")), "start", 48, 48);

        ExplorationSnapshot near = simulation.update(0.05, new RunInput(1, 0), false);
        assertEquals("cave_crystal_cyan", near.focusedInteractableId());
        assertTrue(near.events().stream().anyMatch(e -> e.type() == ExplorationEventType.INTERACTABLE_FOCUSED));

        // Eventos so existem no snapshot do tick em que ocorreram (mesmo contrato de
        // RunEvent): o UNFOCUSED acontece no meio da caminhada de volta, nao no ultimo
        // tick, entao acumulamos os eventos de cada passo para verificar que ele saiu.
        ExplorationSnapshot far = null;
        boolean sawUnfocused = false;
        for (int i = 0; i < 40; i++) {
            far = simulation.update(0.05, new RunInput(-1, 0), false);
            sawUnfocused |= far.events().stream().anyMatch(e -> e.type() == ExplorationEventType.INTERACTABLE_UNFOCUSED);
        }
        assertNull(far.focusedInteractableId());
        assertTrue(sawUnfocused);
    }

    @Test
    void talkingStartsAndAdvancesDialogueAndBlocksMovement() {
        Interactable sage = new Interactable(
                "great_sage", 48, 40, 12, InteractionVerb.TALK, "sage_intro", false, null, 0, 0);
        ExplorationArea area = new ExplorationArea("start", openMap(), List.of(sage));
        DialogueScript dialogue = new DialogueScript("sage_intro", List.of(
                new DialogueLine("Grande Sabio", "Analise iniciada."),
                new DialogueLine("Grande Sabio", "Percepcao e movimento confirmados.")));
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of("sage_intro", dialogue), "start", 48, 48);

        ExplorationSnapshot started = simulation.update(0.05, RunInput.NONE, true);
        assertTrue(started.dialogueActive());
        assertEquals("Analise iniciada.", started.dialogueText());
        assertTrue(started.dialogueHasMore());

        // Movimento e ignorado com dialogo ativo.
        double xDuringDialogue = simulation.update(0.05, new RunInput(1, 0), false).playerX();
        assertEquals(48.0, xDuringDialogue, 1e-9);

        ExplorationSnapshot secondLine = simulation.update(0.05, RunInput.NONE, true);
        assertEquals("Percepcao e movimento confirmados.", secondLine.dialogueText());
        assertFalse(secondLine.dialogueHasMore());

        ExplorationSnapshot ended = simulation.update(0.05, RunInput.NONE, true);
        assertFalse(ended.dialogueActive());
        assertTrue(ended.events().stream().anyMatch(e -> e.type() == ExplorationEventType.DIALOGUE_ENDED));
    }

    @Test
    void enteringAnExitTransitionsToTheTargetArea() {
        // 150,150 (nao 200,200): a area "gallery" tambem e um mapa 6x6*32=192x192,
        // um alvo fora da grade seria rejeitado pela validacao de bounds do spawn.
        Interactable exit = new Interactable(
                "cave_gallery_exit", 48, 40, 12, InteractionVerb.ENTER, null, false, "gallery", 150, 150);
        ExplorationArea start = new ExplorationArea("start", openMap(), List.of(exit));
        ExplorationArea gallery = new ExplorationArea("gallery", openMap(), List.of());
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", start, "gallery", gallery), Map.of(), "start", 48, 48);

        ExplorationSnapshot snapshot = simulation.update(0.05, RunInput.NONE, true);

        assertEquals("gallery", snapshot.areaId());
        assertEquals(150, snapshot.playerX(), 1e-9);
        assertEquals(150, snapshot.playerY(), 1e-9);
        assertTrue(snapshot.events().stream().anyMatch(e -> e.type() == ExplorationEventType.AREA_CHANGED));
    }

    @Test
    void enteringAnExitWithABlockedTargetSpawnFailsFast() {
        // A "porta dos fundos" do bug relatado pelo Codex: um exit mal configurado
        // apontando para dentro de uma parede da area de destino nao pode deixar o
        // jogador preso silenciosamente — tem que falhar alto na hora da transicao.
        Set<ExplorationMap.Cell> galleryWalls = Set.of(new ExplorationMap.Cell(4, 4));
        ExplorationMap galleryMap = new ExplorationMap(6, 6, CELL, galleryWalls);
        Interactable exit = new Interactable(
                "broken_exit", 48, 40, 12, InteractionVerb.ENTER, null, false, "gallery", 150, 150);
        ExplorationArea start = new ExplorationArea("start", openMap(), List.of(exit));
        ExplorationArea gallery = new ExplorationArea("gallery", galleryMap, List.of());
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", start, "gallery", gallery), Map.of(), "start", 48, 48);

        assertThrows(IllegalStateException.class, () -> simulation.update(0.05, RunInput.NONE, true));
    }

    @Test
    void constructorRejectsASpawnPointInsideAWall() {
        ExplorationArea area = new ExplorationArea("start", wallMap(), List.of());
        // x=112 cai na coluna 3 (bloqueada por wallMap entre x=96 e x=128).
        assertThrows(IllegalArgumentException.class,
                () -> new ExplorationSimulation(Map.of("start", area), Map.of(), "start", 112, 48));
    }

    @Test
    void collectingAnItemEmitsItemCollectedBeforeDialogue() {
        Interactable vein = new Interactable(
                "mineral_vein", 48, 40, 12, InteractionVerb.COLLECT, "vein_line", true, null, 0, 0);
        ExplorationArea area = new ExplorationArea("start", openMap(), List.of(vein));
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of("vein_line", oneLineDialogue("vein_line")), "start", 48, 48);

        ExplorationSnapshot snapshot = simulation.update(0.05, RunInput.NONE, true);

        List<ExplorationEventType> types = snapshot.events().stream().map(ExplorationEvent::type).toList();
        assertTrue(types.contains(ExplorationEventType.ITEM_COLLECTED));
        assertTrue(types.contains(ExplorationEventType.DIALOGUE_STARTED));
        assertTrue(types.indexOf(ExplorationEventType.ITEM_COLLECTED) < types.indexOf(ExplorationEventType.DIALOGUE_STARTED));
    }

    @Test
    void collectingTheSameItemTwiceEmitsItemCollectedOnlyOnce() {
        // Bug relatado pelo Codex: segurar a tecla de interacao sobre um coletavel
        // gerava ITEM_COLLECTED indefinidamente porque a coleta nao era idempotente.
        Interactable vein = new Interactable(
                "mineral_vein", 48, 40, 12, InteractionVerb.COLLECT, "vein_line", true, null, 0, 0);
        ExplorationArea area = new ExplorationArea("start", openMap(), List.of(vein));
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of("vein_line", oneLineDialogue("vein_line")), "start", 48, 48);

        long firstPass = simulation.update(0.05, RunInput.NONE, true).events().stream()
                .filter(e -> e.type() == ExplorationEventType.ITEM_COLLECTED).count();
        // Fecha o dialogo aberto pela primeira coleta antes de interagir de novo.
        simulation.update(0.05, RunInput.NONE, true);
        long secondPass = simulation.update(0.05, RunInput.NONE, true).events().stream()
                .filter(e -> e.type() == ExplorationEventType.ITEM_COLLECTED).count();

        assertEquals(1, firstPass);
        assertEquals(0, secondPass);
    }

    @Test
    void nullMovementIsTreatedAsNoMovement() {
        ExplorationArea area = new ExplorationArea("start", openMap(), List.of());
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of(), "start", 48, 48);

        ExplorationSnapshot snapshot = simulation.update(0.05, null, false);

        assertEquals(48, snapshot.playerX(), 1e-9);
        assertEquals(48, snapshot.playerY(), 1e-9);
        assertFalse(snapshot.playerMoving());
    }

    @Test
    void pushingIntoAWallStopsThePlayerMovingFlag() {
        // playerMoving reflete deslocamento REAL, nao so a intencao de input — sem
        // isto a animacao de andar continuava tocando com o jogador parado na parede.
        ExplorationArea area = new ExplorationArea("start", wallMap(), List.of());
        ExplorationSimulation simulation = new ExplorationSimulation(
                Map.of("start", area), Map.of(), "start", 48, 48);

        ExplorationSnapshot snapshot = null;
        for (int i = 0; i < 200; i++) {
            snapshot = simulation.update(0.05, new RunInput(1, 0), false);
        }

        assertFalse(snapshot.playerMoving());
    }

    private static DialogueScript oneLineDialogue(String id) {
        return new DialogueScript(id, List.of(new DialogueLine("Grande Sabio", "Registrado.")));
    }
}
