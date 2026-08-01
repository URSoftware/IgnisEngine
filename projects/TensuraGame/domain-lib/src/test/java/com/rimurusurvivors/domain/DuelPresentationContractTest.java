package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Contrato que o apresentador do duelo consome: um calculo por comando, recusa sem
 * gastar turno, os cinco graus de reacao distintos e os tres modos de timing. Puro:
 * o golden path de DireWolfDuelWalkthroughTest continua sendo a verdade do combate.
 */
class DuelPresentationContractTest {

    private static final long SEED = 20260721L;
    private static final Set<VillagePreparation> PREPARATIONS = Set.of(
            VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK);

    @Test
    void everyCommandIsAcceptedOrRefusedWithoutAmbiguity() {
        for (BattleCommand command : BattleCommand.values()) {
            BattleSimulation simulation = simulation();
            int turnsBefore = simulation.turnCount();
            BattleActionResult result = simulation.executeCommand(command, ReactionTiming.NONE);

            assertNotEquals("", result.message(), command + " precisa de mensagem para o HUD");
            if (!result.valid()) {
                // Comando recusado nao gasta turno: o apresentador volta direto a COMMAND.
                assertEquals(turnsBefore, simulation.turnCount(),
                        command + " recusado nao pode gastar turno");
            }
        }
    }

    @Test
    void theSixAuthoredCommandsAreExactlyTheDomainCommands() {
        assertEquals(6, BattleCommand.values().length);
        for (String authored : new String[] {
                "ANALYZE", "WATER_BLADE", "DEFEND", "GOBLIN_SUPPORT", "PREDATOR", "NEGOTIATE"}) {
            assertNotEquals(null, BattleCommand.valueOf(authored));
        }
    }

    @Test
    void repeatingTheSameInputDoesNotProduceASecondCalculationForTheSameTurn() {
        BattleSimulation simulation = simulation();
        int hpBefore = simulation.leaderHp();

        BattleActionResult first = simulation.executeCommand(
                BattleCommand.WATER_BLADE, ReactionTiming.NONE);
        int hpAfterFirst = simulation.leaderHp();

        assertTrue(first.valid());
        assertTrue(hpAfterFirst < hpBefore);

        // O bloqueio de input vive no adaptador; aqui fica provado o que ele protege:
        // uma segunda chamada no mesmo beat mudaria o estado de novo.
        simulation.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE);
        assertNotEquals(hpAfterFirst, simulation.leaderHp());
    }

    @Test
    void analyzeRevealsWithoutSpendingTheTurn() {
        BattleSimulation simulation = simulation();

        BattleActionResult result = simulation.executeCommand(
                BattleCommand.ANALYZE, ReactionTiming.NONE);

        assertTrue(result.valid());
        assertTrue(simulation.analyzed());
        assertFalse(simulation.revealedInformation().isEmpty());
    }

    @Test
    void theFiveReactionGradesAreDistinctAndOrderedByPrecision() {
        ReactionWindow window = ReactionWindow.standard();

        assertEquals(ReactionTiming.PERFECT, window.classify(0.0));
        assertEquals(ReactionTiming.GOOD, window.classify(window.goodRadius()));
        assertEquals(ReactionTiming.EARLY, window.classify(-window.activeRadius()));
        assertEquals(ReactionTiming.LATE, window.classify(window.activeRadius()));
        assertEquals(ReactionTiming.NONE, window.classify(window.activeRadius() * 2));
        assertEquals(5, ReactionTiming.values().length);
    }

    @Test
    void theThreeTimingModesWidenTheWindowWithoutChangingTheGradeSet() {
        ReactionWindow normal = ReactionWindow.standard();
        ReactionWindow wide = ReactionWindow.standard().scaled(1.6);
        ReactionWindow story = ReactionWindow.storyMode();

        assertTrue(wide.perfectRadius() > normal.perfectRadius());
        assertTrue(wide.activeRadius() > normal.activeRadius());
        assertTrue(story.activeRadius() >= wide.activeRadius());

        double offsetJustOutsideNormal = normal.goodRadius() + normal.perfectRadius() / 2;
        assertNotEquals(
                normal.classify(offsetJustOutsideNormal),
                wide.classify(offsetJustOutsideNormal),
                "o modo amplo precisa perdoar um desvio que o normal ja penaliza");
        assertEquals(ReactionTiming.PERFECT, story.classify(0.0));
    }

    @Test
    void defeatKeepsTheSimulationReadableForTheRetryPanel() {
        BattleSimulation simulation = simulation();
        for (int turn = 0; turn < 40 && simulation.outcome() == BattleOutcome.IN_PROGRESS; turn++) {
            simulation.executeCommand(BattleCommand.GOBLIN_SUPPORT, ReactionTiming.NONE);
        }

        assertNotEquals(BattleOutcome.IN_PROGRESS, simulation.outcome());
        assertTrue(simulation.turnCount() > 0);
        assertNotEquals("", simulation.outcome().description());
    }

    @Test
    void aFreshSimulationWithTheSameSeedRepeatsTheSameOpening() {
        BattleSimulation first = simulation();
        BattleSimulation second = simulation();

        assertEquals(first.currentIntention().actionName(), second.currentIntention().actionName());
        assertEquals(first.leaderHp(), second.leaderHp());
        assertSame(first.leaderPhase(), second.leaderPhase());
    }

    @Test
    void eachOfTheSixCommandsMutatesTheDuelExactlyOnceWhenAccepted() {
        for (BattleCommand command : BattleCommand.values()) {
            BattleSimulation simulation = readyForAnyCommand();
            String before = fingerprint(simulation);

            BattleActionResult result = simulation.executeCommand(command, ReactionTiming.PERFECT);

            assertTrue(result.valid(), command + " deveria ser aceito no estado de ruptura");
            if (command == BattleCommand.ANALYZE) {
                // Unico comando que revela sem gastar turno: muda a informacao, nao o placar.
                assertEquals(before, fingerprint(simulation), "ANALYZE nao pode alterar o placar");
                assertTrue(simulation.analyzed());
            } else {
                assertNotEquals(before, fingerprint(simulation),
                        command + " precisa produzir exatamente uma mutacao observavel");
            }
        }
    }

    @Test
    void theThreeTimingModesKeepTheSameFiveGradesAtEveryBoundary() {
        ReactionWindow[] modes = {
                ReactionWindow.standard(),
                ReactionWindow.standard().scaled(1.6),
                ReactionWindow.storyMode()};

        for (ReactionWindow window : modes) {
            assertEquals(ReactionTiming.PERFECT, window.classify(window.perfectRadius()));
            assertEquals(ReactionTiming.GOOD, window.classify(window.goodRadius()));
            assertEquals(ReactionTiming.GOOD, window.classify(-window.goodRadius()));
            assertEquals(ReactionTiming.LATE, window.classify(window.activeRadius()));
            assertEquals(ReactionTiming.EARLY, window.classify(-window.activeRadius()));
            assertEquals(ReactionTiming.NONE, window.classify(window.activeRadius() * 1.01));
            assertEquals(ReactionTiming.NONE, window.classify(-window.activeRadius() * 1.01));
        }
    }

    @Test
    void surrenderAndSubmissionAreDistinctEndingsFromTheSameBrokenMorale() {
        BattleSimulation negotiated = readyForAnyCommand();
        BattleSimulation subdued = readyForAnyCommand();

        negotiated.executeCommand(BattleCommand.NEGOTIATE, ReactionTiming.NONE);
        subdued.executeCommand(BattleCommand.PREDATOR, ReactionTiming.NONE);

        assertEquals(BattleOutcome.VICTORY_SURRENDER, negotiated.outcome());
        assertEquals(BattleOutcome.VICTORY_SUBDUED, subdued.outcome());
        assertNotEquals(negotiated.outcome(), subdued.outcome());
        // O lider rendido continua de pe; o subjugado, nao. A cutscene de resolucao
        // depende dessa diferenca para escolher o clipe certo.
        assertTrue(negotiated.leaderHp() > 0);
        assertEquals(0, subdued.leaderHp());
    }

    @Test
    void aFinishedDuelRefusesEveryFurtherCommand() {
        for (BattleOutcome ending : new BattleOutcome[] {
                BattleOutcome.VICTORY_SURRENDER, BattleOutcome.VICTORY_SUBDUED, BattleOutcome.DEFEAT}) {
            BattleSimulation simulation = finishedWith(ending);
            assertEquals(ending, simulation.outcome());

            for (BattleCommand command : BattleCommand.values()) {
                BattleActionResult result = simulation.executeCommand(command, ReactionTiming.NONE);
                assertFalse(result.valid(),
                        command + " nao pode ser aceito depois de " + ending);
            }
            // O estado sobrevive intacto para o painel de resultado e para o retry.
            assertEquals(ending, simulation.outcome());
        }
    }

    /** Ruptura de moral com o lider ainda vivo: os seis comandos ficam validos aqui. */
    private static BattleSimulation readyForAnyCommand() {
        BattleSimulation simulation = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.CONTROLLED_BAIT),
                RandomSource.fixed(0.5));
        for (int blade = 0; blade < 4; blade++) {
            simulation.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE);
        }
        assertTrue(simulation.isPredatorAvailable());
        assertTrue(simulation.isNegotiationAvailable());
        assertTrue(simulation.leaderHp() > 0);
        assertTrue(simulation.rimuruHp() > 0);
        assertEquals(BattleOutcome.IN_PROGRESS, simulation.outcome());
        return simulation;
    }

    private static BattleSimulation finishedWith(BattleOutcome ending) {
        if (ending == BattleOutcome.DEFEAT) {
            BattleSimulation simulation = new BattleSimulation(
                    Set.of(VillagePreparation.LIGHT_FLANK, VillagePreparation.CONTROLLED_BAIT),
                    RandomSource.fixed(0.5));
            while (simulation.outcome() == BattleOutcome.IN_PROGRESS) {
                simulation.executeCommand(BattleCommand.GOBLIN_SUPPORT, ReactionTiming.NONE);
            }
            return simulation;
        }
        BattleSimulation simulation = readyForAnyCommand();
        simulation.executeCommand(
                ending == BattleOutcome.VICTORY_SURRENDER
                        ? BattleCommand.NEGOTIATE
                        : BattleCommand.PREDATOR,
                ReactionTiming.NONE);
        return simulation;
    }

    /**
     * Tudo que o HUD projeta. Inclui o desfecho de proposito: NEGOTIATE encerra o
     * duelo sem mexer em HP, moral ou turno, e essa continua sendo uma mutacao — quem
     * so olhasse o placar concluiria, errado, que o comando nao fez nada.
     */
    private static String fingerprint(BattleSimulation simulation) {
        return simulation.turnCount() + "/" + simulation.rimuruHp() + "/" + simulation.rimuruMagicules()
                + "/" + simulation.leaderHp() + "/" + simulation.leaderMorale()
                + "/" + simulation.leaderPhase() + "/" + simulation.outcome();
    }

    private static BattleSimulation simulation() {
        return new BattleSimulation(PREPARATIONS, RandomSource.seeded(SEED));
    }
}
