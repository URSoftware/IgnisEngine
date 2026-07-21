package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BattleSimulationTest {

    @Test
    void requiresExactlyTwoPreparations() {
        assertThrows(IllegalArgumentException.class, () -> new BattleSimulation(Set.of(), RandomSource.fixed(0.5)));
        assertThrows(IllegalArgumentException.class, () -> new BattleSimulation(Set.of(VillagePreparation.REINFORCE_ENTRANCE), RandomSource.fixed(0.5)));
        assertThrows(IllegalArgumentException.class, () -> new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK, VillagePreparation.CONTROLLED_BAIT),
                RandomSource.fixed(0.5)));
    }

    @Test
    void controlledBaitReducesLeaderInitialHp() {
        BattleSimulation simWithoutBait = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK),
                RandomSource.fixed(0.5));
        BattleSimulation simWithBait = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.CONTROLLED_BAIT),
                RandomSource.fixed(0.5));

        assertEquals(200, simWithoutBait.leaderHp());
        assertEquals(180, simWithBait.leaderHp());
    }

    @Test
    void analyzeRevealsInformationWithoutConsumingTurnOrAlteringRng() {
        RandomSource random = RandomSource.fixed(0.5);
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK),
                random);

        assertFalse(sim.analyzed());
        int initialTurn = sim.turnCount();

        BattleActionResult result = sim.executeCommand(BattleCommand.ANALYZE, ReactionTiming.NONE);

        assertTrue(result.valid());
        assertFalse(result.turnConsumed());
        assertTrue(sim.analyzed());
        assertEquals(initialTurn, sim.turnCount());
        assertTrue(sim.revealedInformation().size() > 0);
    }

    @Test
    void insufficientResourceDoesNotConsumeTurn() {
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK),
                RandomSource.fixed(0.5));

        // Gasta magicules ate ficar com menos de 10
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // 50 -> 40
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // 40 -> 30
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // 30 -> 20
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // 20 -> 10
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // 10 -> 0

        assertEquals(0, sim.rimuruMagicules());
        int turnBeforeFailedCommand = sim.turnCount();

        BattleActionResult failedResult = sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE);

        assertFalse(failedResult.valid());
        assertFalse(failedResult.turnConsumed());
        assertEquals(turnBeforeFailedCommand, sim.turnCount());
    }

    @Test
    void predatorAndNegotiateRequireMoralBreakdown() {
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK),
                RandomSource.fixed(0.5));

        assertFalse(sim.isNegotiationAvailable());
        assertFalse(sim.isPredatorAvailable());

        BattleActionResult negResult = sim.executeCommand(BattleCommand.NEGOTIATE, ReactionTiming.NONE);
        assertFalse(negResult.valid());

        BattleActionResult predResult = sim.executeCommand(BattleCommand.PREDATOR, ReactionTiming.NONE);
        assertFalse(predResult.valid());
    }

    @Test
    void moralBreakdownUnlocksNegotiateAndLeadsToSurrenderVictory() {
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK),
                RandomSource.fixed(0.5));

        // Reduz a moral ate o ponto de ruptura
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Morale: 100 -> 80
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Morale: 80 -> 60
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Morale: 60 -> 40
        sim.executeCommand(BattleCommand.DEFEND, ReactionTiming.PERFECT);   // Morale: 40 -> 5

        assertEquals(LeaderPhase.MORAL_BREAKDOWN, sim.leaderPhase());
        assertTrue(sim.isNegotiationAvailable());

        BattleActionResult result = sim.executeCommand(BattleCommand.NEGOTIATE, ReactionTiming.NONE);

        assertTrue(result.valid());
        assertEquals(BattleOutcome.VICTORY_SURRENDER, sim.outcome());
    }

    @Test
    void sameSeedAndCommandsProduceIdenticalBattleReplay() {
        Set<VillagePreparation> preps = Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK);

        BattleSimulation sim1 = new BattleSimulation(preps, RandomSource.seeded(12345L));
        BattleSimulation sim2 = new BattleSimulation(preps, RandomSource.seeded(12345L));

        sim1.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE);
        sim1.executeCommand(BattleCommand.DEFEND, ReactionTiming.GOOD);

        sim2.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE);
        sim2.executeCommand(BattleCommand.DEFEND, ReactionTiming.GOOD);

        assertEquals(sim1.rimuruHp(), sim2.rimuruHp());
        assertEquals(sim1.leaderHp(), sim2.leaderHp());
        assertEquals(sim1.leaderMorale(), sim2.leaderMorale());
        assertEquals(sim1.turnCount(), sim2.turnCount());
    }
}
