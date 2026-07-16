package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teto e coalescencia de orbes (estabilidade de sessao longa, 16/07/2026).
 *
 * <p>Orbes so somem quando o jogador encosta neles; os que caem longe do ima ficavam
 * na arena para sempre, e cada um custa um GameObject visual no motor. O teto limita
 * quantos OBJETOS existem — e estes testes fixam a regra que ele nao pode quebrar:
 * <b>a experiencia total no chao e sempre conservada</b>.</p>
 */
class OrbCapTest {

    private static WeaponProgression progression() {
        return new WeaponProgression(List.of(
                new WeaponLevelStats(1, 16, 2.20, 1, 0.90, 0, 0, 0, 0, 0, 1.0, null)));
    }

    private static RunSimulation simulation() {
        return new RunSimulation(progression(), 1);
    }

    @Test
    void nearbyOrbsCoalesceIntoOneWithoutLosingExperience() {
        RunSimulation simulation = simulation();

        // 500 inimigos mortos praticamente no mesmo ponto: sem coalescencia seriam
        // 500 orbes (e 500 visuais no motor) representando a mesma pilha de XP.
        for (int i = 0; i < 500; i++) simulation.spawnOrb(100, 100, 3);

        assertEquals(1, simulation.orbCount(), "orbes no mesmo ponto deviam virar um so");
        assertEquals(1500, simulation.totalOrbValue(), "a experiencia nao pode evaporar");
    }

    @Test
    void distantOrbsStaySeparate() {
        RunSimulation simulation = simulation();

        // Fora da distancia de merge (18): continuam sendo orbes distintos, senao o
        // jogador veria orbes "teleportando" para longe de onde o inimigo morreu.
        simulation.spawnOrb(0, 0, 5);
        simulation.spawnOrb(100, 0, 5);

        assertEquals(2, simulation.orbCount());
        assertEquals(10, simulation.totalOrbValue());
    }

    @Test
    void orbCountStaysUnderTheCapAndKeepsEveryPointOfExperience() {
        RunSimulation simulation = simulation();

        // 1000 orbes espalhados pela arena, todos fora da distancia de merge.
        int granted = 0;
        for (int i = 0; i < 1000; i++) {
            simulation.spawnOrb(i * 53.0, i * 37.0, 2);
            granted += 2;
        }

        assertTrue(simulation.orbCount() <= 120,
                "teto estourado: " + simulation.orbCount());
        assertEquals(granted, simulation.totalOrbValue(),
                "o teto so pode FUNDIR orbes, nunca descartar experiencia");
    }

    @Test
    void capMergesIntoTheNearestNeighbourSoExperienceStaysReachable() {
        RunSimulation simulation = simulation();

        // Enche ate o teto perto do jogador (que comeca na origem)...
        for (int i = 0; i < 120; i++) simulation.spawnOrb(i * 25.0, 0, 1);
        assertEquals(120, simulation.orbCount());

        // ...e entao um orbe MUITO distante: ele e o candidato natural a ser fundido,
        // e seu valor tem de acabar num orbe que ainda existe.
        simulation.spawnOrb(90_000, 90_000, 7);

        assertEquals(120, simulation.orbCount());
        assertEquals(127, simulation.totalOrbValue());
    }
}
