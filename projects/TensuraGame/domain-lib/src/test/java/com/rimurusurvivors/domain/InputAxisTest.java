package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conversao dos eixos de entrada (regressao do bug de W/S invertidos, 16/07/2026).
 *
 * <p>Origem do bug: {@code Input.getVerticalAxis()} da Ignis usa convencao de TELA
 * (cima = -1, baixo = +1), mas a simulacao — como o mundo da Ignis, cujo flip a
 * camera aplica no render — usa Y-para-cima. O adaptador passava o eixo de tela
 * direto para {@code new RunInput(...)}, entao W empurrava o Rimuru para baixo.</p>
 *
 * <p>Estes testes amarram a tecla ao resultado na tela: sao eles que impedem a
 * inversao de voltar sem ninguem perceber.</p>
 */
class InputAxisTest {

    private static WeaponProgression progression() {
        return new WeaponProgression(List.of(
                new WeaponLevelStats(1, 16, 2.20, 1, 0.90, 0, 0, 0, 0, 0, 1.0, null)));
    }

    @Test
    void screenUpBecomesWorldUp() {
        // W / Seta-cima na Ignis = -1 (tela). No mundo tem de virar +1 (cima).
        assertEquals(1.0, RunInput.fromScreenAxes(0, -1).vertical());
    }

    @Test
    void screenDownBecomesWorldDown() {
        assertEquals(-1.0, RunInput.fromScreenAxes(0, 1).vertical());
    }

    @Test
    void horizontalIsNotTouched() {
        // A/D e as setas laterais tem a mesma direcao nos dois espacos.
        assertEquals(-1.0, RunInput.fromScreenAxes(-1, 0).horizontal());
        assertEquals(1.0, RunInput.fromScreenAxes(1, 0).horizontal());
    }

    @Test
    void pressingUpMovesRimuruUpInTheWorld() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        double startY = simulation.snapshot().playerY();

        // Exatamente o que o adaptador faz quando o jogador segura W.
        RunSnapshot snapshot = simulation.update(0.1, RunInput.fromScreenAxes(0, -1));

        assertTrue(snapshot.playerY() > startY,
                "W tem de subir: Y do mundo cresce para cima (a camera inverte no render)");
    }

    @Test
    void pressingDownMovesRimuruDownInTheWorld() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        double startY = simulation.snapshot().playerY();

        RunSnapshot snapshot = simulation.update(0.1, RunInput.fromScreenAxes(0, 1));

        assertTrue(snapshot.playerY() < startY, "S tem de descer");
    }

    @Test
    void pressingRightMovesRimuruRight() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        double startX = simulation.snapshot().playerX();

        RunSnapshot snapshot = simulation.update(0.1, RunInput.fromScreenAxes(1, 0));

        assertTrue(snapshot.playerX() > startX, "D nao pode ter sido afetado pela correcao");
    }

    @Test
    void diagonalUpRightGoesUpAndRight() {
        RunSimulation simulation = new RunSimulation(progression(), 1);
        RunSnapshot start = simulation.snapshot();

        RunSnapshot snapshot = simulation.update(0.1, RunInput.fromScreenAxes(1, -1));

        assertTrue(snapshot.playerX() > start.playerX(), "W+D: direita");
        assertTrue(snapshot.playerY() > start.playerY(), "W+D: cima");
    }
}
