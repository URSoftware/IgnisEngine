package com.ignis.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameAlertTest {

    @Test
    void alertQueuesMessageWithoutRequiringAnEditor() {
        Game game = new Game();
        game.alert("Mundo sem limites definido");

        List<Game.EditorAlert> alerts = game.getActiveAlerts();
        assertEquals(1, alerts.size());
        assertEquals("Mundo sem limites definido", alerts.get(0).message);
    }

    @Test
    void alertQueueIsCappedSoALongSessionCannotLeakMemory() {
        Game game = new Game();
        for (int i = 0; i < 25; i++) {
            game.alert("alerta " + i);
        }

        List<Game.EditorAlert> alerts = game.getActiveAlerts();
        assertTrue(alerts.size() <= 10, "fila cresceu sem teto: " + alerts.size());
        // Os mais antigos saem primeiro (FIFO); o ultimo continua na fila.
        assertEquals("alerta 24", alerts.get(alerts.size() - 1).message);
    }
}
