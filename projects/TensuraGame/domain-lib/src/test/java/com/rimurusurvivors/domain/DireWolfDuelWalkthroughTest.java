package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Walkthrough deterministico do duelo contra o Lider da Matilha (P1, entrega 5):
 * exercita as tres preparacoes defensivas da aldeia e alcanca pelo menos dois
 * desfechos validos, com RNG fixo para o resultado ser sempre o mesmo. Cada teste
 * documenta o calculo turno a turno, provando que a sequencia de comandos e
 * suficiente e necessaria para o desfecho — nao um encontro aleatorio que "deu certo".
 */
class DireWolfDuelWalkthroughTest {

    @Test
    void reinforceEntranceAndLightFlank_analisarNaoAtrasaARendicaoPorNegociacao() {
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK),
                RandomSource.fixed(0.5));

        // Analisar nunca consome turno nem altera RNG: pode vir antes de qualquer
        // acao sem mudar o restante da caminhada.
        BattleActionResult analysis = sim.executeCommand(BattleCommand.ANALYZE, ReactionTiming.NONE);
        assertTrue(analysis.valid());
        assertEquals(1, sim.turnCount());

        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Moral: 100 -> 80
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Moral: 80 -> 60
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Moral: 60 -> 40
        sim.executeCommand(BattleCommand.DEFEND, ReactionTiming.PERFECT);   // Moral: 40 -> 5 (LIGHT_FLANK + defesa perfeita)

        assertEquals(LeaderPhase.MORAL_BREAKDOWN, sim.leaderPhase());
        assertTrue(sim.isNegotiationAvailable());

        BattleActionResult result = sim.executeCommand(BattleCommand.NEGOTIATE, ReactionTiming.NONE);

        assertTrue(result.valid());
        assertEquals(BattleOutcome.VICTORY_SURRENDER, sim.outcome());
        assertTrue(sim.rimuruHp() > 0, "defesa perfeita nao deve derrubar Rimuru no caminho da rendicao");
    }

    @Test
    void reinforceEntranceAndControlledBait_predadorSubjugaComOLiderJaEnfraquecido() {
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.CONTROLLED_BAIT),
                RandomSource.fixed(0.5));

        // CONTROLLED_BAIT ja reduz o HP inicial do lider de 200 para 180.
        assertEquals(180, sim.leaderHp());

        // Quatro Hidrolaminas (40 de dano fixo com RNG fixo em 0.5) levam o lider a
        // Ruptura de Moral; REINFORCE_ENTRANCE reduz o contra-ataque de Pressao de
        // Matilha, entao Rimuru sobrevive confortavelmente ate Predador ficar disponivel.
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Lider HP: 180 -> 140 (Sondagem, sem contra-ataque)
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Lider HP: 140 -> 100 (ainda Sondagem nesta rodada)
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Lider HP: 100 -> 60; Pressao de Matilha contra-ataca reduzido
        sim.executeCommand(BattleCommand.WATER_BLADE, ReactionTiming.NONE); // Lider HP: 60 -> 20; Moral: -> 20 (<=30)

        assertTrue(sim.isPredatorAvailable());
        assertTrue(sim.rimuruHp() > 0, "REINFORCE_ENTRANCE deve manter Rimuru vivo ate aqui");

        BattleActionResult result = sim.executeCommand(BattleCommand.PREDATOR, ReactionTiming.NONE);

        assertTrue(result.valid());
        assertEquals(BattleOutcome.VICTORY_SUBDUED, sim.outcome());
        assertEquals(0, sim.leaderHp());
        assertEquals(0, sim.leaderMorale());
    }

    @Test
    void lightFlankAndControlledBait_atacarSemDefenderLevaADerrota() {
        BattleSimulation sim = new BattleSimulation(
                Set.of(VillagePreparation.LIGHT_FLANK, VillagePreparation.CONTROLLED_BAIT),
                RandomSource.fixed(0.5));

        // Sem REINFORCE_ENTRANCE, o contra-ataque de Pressao de Matilha e Investida
        // Decisiva chega em cheio. Apoio aos Goblins causa pouco dano (15) e nunca
        // defende: o lider aguenta mais turnos do que Rimuru consegue sobreviver.
        BattleOutcome outcome = BattleOutcome.IN_PROGRESS;
        int turns = 0;
        while (outcome == BattleOutcome.IN_PROGRESS && turns < 20) {
            BattleActionResult result = sim.executeCommand(BattleCommand.GOBLIN_SUPPORT, ReactionTiming.NONE);
            assertTrue(result.valid(), "Apoio aos Goblins nunca deve ser recusado pelo dominio");
            outcome = sim.outcome();
            turns++;
        }

        assertEquals(BattleOutcome.DEFEAT, outcome, "atacar sem nunca defender deve derrotar Rimuru antes do lider");
        assertEquals(0, sim.rimuruHp());
        assertTrue(sim.leaderHp() > 0, "o lider sobrevive: e Rimuru quem perde essa caminhada");
        assertEquals(6, turns, "a caminhada e deterministica com RNG fixo: sempre o mesmo turno de derrota");
    }
}
