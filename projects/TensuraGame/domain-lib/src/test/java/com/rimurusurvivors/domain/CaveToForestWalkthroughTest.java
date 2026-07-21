package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Walkthrough de dominio, sem bypass de scripts: Caverna do Selo ate
 * jura_forest_approach, dirigido por InputTape/ExplorationInputTapeRunner. As
 * areas/dialogos abaixo espelham os valores de data/cave-seal-map.json e
 * data/cave-seal-dialogues.json (domain-lib nao le paths do projeto — ver
 * contrato no handoff canonico) — mudar o schema runtime exige atualizar os
 * dois lados.
 *
 * <p>Contrato: comparar estado SEMANTICO (area, foco, dialogo, tipos/detalhes
 * de evento, posicao com tolerancia de alcance de interacao), nunca pixels
 * exatos, timestamps reais ou ordem nao-deterministica.</p>
 */
class CaveToForestWalkthroughTest {

    @Test
    void walkingFromAwakeningSpawnReachesTheGalleryExitTriggerAfterVeldora() {
        ExplorationSimulation simulation = caveSimulation();

        InputTape tape = new InputTape(List.of(
                // Desvia do bloco central_rock (cols 9-11, rows 4-7) passando por baixo.
                new InputAction.Move(0, 1, 0.7),
                new InputAction.Move(1, 0, 3.65),
                new InputAction.Move(0, -1, 0.65),
                new InputAction.Interact(), // ENTER cave_gallery_exit -> transiciona de area

                new InputAction.Move(0, -1, 2.5),
                new InputAction.Interact(),         // abre dlg_gallery_seal
                new InputAction.AdvanceDialogue(),   // segunda fala
                new InputAction.AdvanceDialogue(),   // DIALOGUE_ENDED dlg_gallery_seal (pede Veldora)

                new InputAction.Move(0, 1, 2.5),
                new InputAction.Move(1, 0, 0.7),
                new InputAction.Interact(),         // abre dlg_cave_exit
                new InputAction.AdvanceDialogue(),   // segunda fala
                new InputAction.AdvanceDialogue()));  // DIALOGUE_ENDED dlg_cave_exit (pede contato goblin)

        ExplorationInputTrace inputTrace = ExplorationInputTapeRunner.runTrace(simulation, tape);
        inputTrace.requireMatches(List.of(
                new ExplorationSemanticCheckpoint(
                        "entrada na galeria", 3, "cave_gallery", false,
                        ExplorationEventType.AREA_CHANGED, "cave_gallery"),
                new ExplorationSemanticCheckpoint(
                        "encontro com Veldora solicitado", 7, "cave_gallery", false,
                        ExplorationEventType.DIALOGUE_ENDED, "dlg_gallery_seal"),
                new ExplorationSemanticCheckpoint(
                        "contato goblin solicitado", 12, "cave_gallery", false,
                        ExplorationEventType.DIALOGUE_ENDED, "dlg_cave_exit")));
        List<ExplorationSnapshot> trace = inputTrace.steps().stream()
                .map(ExplorationInputTrace.Step::snapshot)
                .toList();
        List<ExplorationEvent> events = trace.stream().flatMap(s -> s.events().stream()).toList();

        ExplorationSnapshot last = trace.get(trace.size() - 1);
        assertEquals("cave_gallery", last.areaId());
        assertFalse(last.dialogueActive());

        assertTrue(events.stream().anyMatch(e ->
                e.type() == ExplorationEventType.AREA_CHANGED && "cave_gallery".equals(e.detail())));
        assertTrue(events.stream().anyMatch(e ->
                e.type() == ExplorationEventType.DIALOGUE_ENDED && "dlg_gallery_seal".equals(e.detail())));
        // O gatilho real do contato goblin (ExplorationDirector): DIALOGUE_ENDED cujo
        // detail e o id do dialogo da boca da caverna, e so depois do Veldora — os
        // dois DIALOGUE_ENDED acima, nesta ordem, sao exatamente essa precondicao.
        assertTrue(events.stream().anyMatch(e ->
                e.type() == ExplorationEventType.DIALOGUE_ENDED && "dlg_cave_exit".equals(e.detail())));

        int veldoraTrigger = indexOfDialogueEnded(events, "dlg_gallery_seal");
        int goblinTrigger = indexOfDialogueEnded(events, "dlg_cave_exit");
        assertTrue(veldoraTrigger < goblinTrigger, "selo da galeria deve terminar antes da saida da caverna");
    }

    @Test
    void enteringTheForestAsANewSimulationReachesTheGoblinScoutConversation() {
        // Espelha beginExplorationAt(FOREST_AREA_ID, spawnX, spawnY): a floresta e
        // uma simulacao NOVA (nao um ENTER dentro da mesma), disparada pelo
        // GoblinContactDirector apos a cutscene — nunca pelo dominio diretamente.
        ExplorationSimulation forestSimulation = forestSimulation();

        InputTape tape = new InputTape(List.of(
                new InputAction.Move(1, 0, 2.2),
                new InputAction.Interact(),
                new InputAction.AdvanceDialogue()));

        ExplorationInputTrace inputTrace = ExplorationInputTapeRunner.runTrace(forestSimulation, tape);
        inputTrace.requireMatches(List.of(new ExplorationSemanticCheckpoint(
                "conversa com o batedor concluida", 2, "jura_forest_approach", false,
                ExplorationEventType.DIALOGUE_ENDED, "dlg_goblin_scout_camp")));
        List<ExplorationSnapshot> trace = inputTrace.steps().stream()
                .map(ExplorationInputTrace.Step::snapshot)
                .toList();
        ExplorationSnapshot last = trace.get(trace.size() - 1);

        assertEquals("jura_forest_approach", last.areaId());
        assertFalse(last.dialogueActive());
        assertTrue(last.events().stream().anyMatch(e ->
                e.type() == ExplorationEventType.DIALOGUE_ENDED && "dlg_goblin_scout_camp".equals(e.detail())));
        // Alcance de interacao (raio 24 + 8), nao posicao exata.
        assertTrue(Math.abs(last.playerX() - 400) <= 32, "deve terminar perto do goblin scout leader");
    }

    private static int indexOfDialogueEnded(List<ExplorationEvent> events, String dialogueId) {
        for (int i = 0; i < events.size(); i++) {
            ExplorationEvent event = events.get(i);
            if (event.type() == ExplorationEventType.DIALOGUE_ENDED && dialogueId.equals(event.detail())) {
                return i;
            }
        }
        throw new AssertionError("DIALOGUE_ENDED nao encontrado para " + dialogueId);
    }

    private static ExplorationSimulation caveSimulation() {
        Set<ExplorationMap.Cell> centralRock = new HashSet<>();
        for (int col = 9; col <= 11; col++) {
            for (int row = 4; row <= 7; row++) {
                centralRock.add(new ExplorationMap.Cell(col, row));
            }
        }
        ExplorationMap awakeningMap = new ExplorationMap(20, 12, 32, centralRock);
        Interactable galleryExit = new Interactable(
                "cave_gallery_exit", 592, 208, 20, InteractionVerb.ENTER,
                null, false, "cave_gallery", 176, 432);
        ExplorationArea awakening = new ExplorationArea("cave_awakening", awakeningMap, List.of(galleryExit));

        ExplorationMap galleryMap = new ExplorationMap(10, 16, 32, Set.of());
        Interactable gallerySeal = new Interactable(
                "cave_gallery_seal", 176, 80, 24, InteractionVerb.TALK, "dlg_gallery_seal", false, null, 0, 0);
        Interactable forestExit = new Interactable(
                "cave_forest_exit", 272, 432, 22, InteractionVerb.EXAMINE, "dlg_cave_exit", false, null, 0, 0);
        ExplorationArea gallery = new ExplorationArea(
                "cave_gallery", galleryMap, List.of(gallerySeal, forestExit));

        Map<String, DialogueScript> dialogues = Map.of(
                "dlg_gallery_seal", new DialogueScript("dlg_gallery_seal", List.of(
                        new DialogueLine("Grande Sabio", "Uma barreira imensa esta adiante."),
                        new DialogueLine("Grande Sabio", "Recomendo cautela."))),
                "dlg_cave_exit", new DialogueScript("dlg_cave_exit", List.of(
                        new DialogueLine("Grande Sabio", "Passagem para a superficie detectada."),
                        new DialogueLine("Grande Sabio", "O ar carrega esporos de floresta."))));

        return new ExplorationSimulation(
                Map.of("cave_awakening", awakening, "cave_gallery", gallery), dialogues, "cave_awakening", 80, 208);
    }

    private static ExplorationSimulation forestSimulation() {
        ExplorationMap forestMap = new ExplorationMap(20, 12, 32, Set.of());
        Interactable goblinScoutLeader = new Interactable(
                "goblin_scout_leader", 400, 192, 24, InteractionVerb.TALK,
                "dlg_goblin_scout_camp", false, null, 0, 0);
        ExplorationArea forest = new ExplorationArea("jura_forest_approach", forestMap, List.of(goblinScoutLeader));
        Map<String, DialogueScript> dialogues = Map.of(
                "dlg_goblin_scout_camp", new DialogueScript("dlg_goblin_scout_camp", List.of(
                        new DialogueLine("Goblin", "Grande viajante, este e o caminho para a aldeia."))));
        return new ExplorationSimulation(Map.of("jura_forest_approach", forest), dialogues, "jura_forest_approach", 96, 192);
    }
}
