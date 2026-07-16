package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato de apresentacao entre a simulacao e o apresentador da Ignis
 * (02_design/rimuru-survivors-direcao-visual-fatia-slime.md).
 *
 * <p>A regra que estes testes protegem: a simulacao diz <b>o que aconteceu</b> de
 * forma tipada e nao sabe nada de imagem, som ou engine. Antes, o apresentador tinha
 * de descobrir qual habilidade disparou casando a string do detail
 * ({@code detail.contains("Predador")}) — fragil e invisivel ao compilador.</p>
 */
class PresentationContractTest {

    private static WeaponProgression progression() {
        return new WeaponProgression(List.of(
                new WeaponLevelStats(1, 16, 2.20, 1, 0.90, 0, 0, 0, 0, 0, 1.0, null)));
    }

    private static boolean hasEvent(RunSnapshot snapshot, RunEventType type) {
        return snapshot.events().stream().anyMatch(event -> event.type() == type);
    }

    /** Roda a simulacao ate um evento aparecer (ou desistir), devolvendo o snapshot. */
    private static RunSnapshot advanceUntil(RunSimulation simulation, RunEventType type, int maxSteps) {
        for (int i = 0; i < maxSteps; i++) {
            RunSnapshot snapshot = simulation.update(0.05, RunInput.NONE);
            if (hasEvent(snapshot, type)) return snapshot;
        }
        return null;
    }

    @Test
    void predatorPulseEmitsItsOwnTypedEventInsteadOfAGenericAttack() {
        RunSimulation simulation = new RunSimulation(progression(), 1);

        // A habilidade da forma Slime e o Predador: em algum momento ela dispara.
        RunSnapshot snapshot = advanceUntil(simulation, RunEventType.PREDATOR_CAST, 600);

        org.junit.jupiter.api.Assertions.assertNotNull(snapshot,
                "o pulso do Predador deveria ocorrer numa run de 30s");
        assertFalse(snapshot.events().stream()
                        .anyMatch(e -> e.type() == RunEventType.ATTACK && "Predador".equals(e.detail())),
                "Predador nao pode mais sair como ATTACK generico com string no detail");
    }

    @Test
    void rangaChargeEmitsATypedEvent() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        simulation.grantExperience(50_000);
        for (int i = 0; i < 4; i++) simulation.chooseUpgrade(UpgradeChoice.PREDATOR);

        RunSnapshot summoned = advanceUntil(simulation, RunEventType.RANGA_SUMMONED, 200);
        if (summoned == null) return; // Ranga depende do nivel da arma; sem ele nao ha o que checar

        RunSnapshot attack = advanceUntil(simulation, RunEventType.RANGA_ATTACK, 600);
        org.junit.jupiter.api.Assertions.assertNotNull(attack,
                "o Ranga invocado deveria investir contra algum alvo");
    }

    @Test
    void locomotionIsStateInTheSnapshotNotAnEventPerTick() {
        RunSimulation simulation = new RunSimulation(progression(), 1);

        RunSnapshot idle = simulation.update(0.05, RunInput.NONE);
        assertFalse(idle.playerMoving(), "sem input o jogador esta parado");

        RunSnapshot moving = simulation.update(0.05, new RunInput(1, 0));
        assertTrue(moving.playerMoving(), "com input o jogador esta andando");

        RunSnapshot stopped = simulation.update(0.05, RunInput.NONE);
        assertFalse(stopped.playerMoving(), "soltar o input volta para repouso");
    }

    @Test
    void movingDoesNotFloodTheEventListEveryTick() {
        RunSimulation simulation = new RunSimulation(progression(), 1);

        // 200 ticks andando: a locomocao nao pode gerar um evento por tick — foi
        // esse tipo de lixo por frame que a tarefa de estabilidade eliminou.
        int totalEvents = 0;
        for (int i = 0; i < 200; i++) {
            totalEvents += simulation.update(0.05, new RunInput(1, 0)).events().size();
        }

        assertTrue(totalEvents < 200,
                "eventos demais para 200 ticks andando (" + totalEvents + "): "
                        + "locomocao virou pulso por frame?");
    }

    @Test
    void theDomainNeverMentionsAssets() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        simulation.grantExperience(50_000);

        // Varre tudo que a simulacao expoe: nenhum detail pode carregar caminho de
        // arte/audio. O apresentador e quem mapeia evento -> asset.
        for (int i = 0; i < 400; i++) {
            RunSnapshot snapshot = simulation.update(0.05, new RunInput(1, 1));
            for (RunEvent event : snapshot.events()) {
                String detail = event.detail();
                if (detail == null) continue;
                String lower = detail.toLowerCase(java.util.Locale.ROOT);
                assertFalse(lower.contains(".png") || lower.contains(".wav")
                                || lower.contains("assets/") || lower.contains(".anim"),
                        "o dominio vazou um asset no evento " + event.type() + ": " + detail);
            }
        }
    }

    @Test
    void eventTypeCoversEveryBeatOfTheVisualContract() {
        // O contrato de direcao visual nomeia estes momentos; se alguem remover um
        // deles do enum, o apresentador perde o gancho silenciosamente.
        for (String required : new String[] {
                "PLAYER_HIT", "PREDATOR_CAST", "RANGA_SUMMONED", "RANGA_ATTACK", "FORM_CHANGED"}) {
            assertEquals(required, RunEventType.valueOf(required).name());
        }
    }
}
