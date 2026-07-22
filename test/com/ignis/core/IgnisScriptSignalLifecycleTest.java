package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IgnisScriptSignalLifecycleTest {

    @Test
    void detachedScriptStopsReceivingSceneWideSignalsAfterReload() {
        Game game = new Game();
        GameObject owner = new GameObject("Director", game, 0, 0, 1, 1);
        game.addEntity(owner);

        ListeningScript retired = new ListeningScript();
        owner.addComponent(retired);
        retired.internalTick();
        owner.removeComponent(retired);

        ListeningScript current = new ListeningScript();
        owner.addComponent(current);
        current.internalTick();

        game.getSceneDispatcher().enqueue("TEST_SCENE_SIGNAL", null);
        game.getSceneDispatcher().processPendingSignals();

        assertEquals(0, retired.received);
        assertEquals(1, current.received);
    }

    @Test
    void clearingSceneDisconnectsRetiredScriptsFromGlobalSignals() {
        Game game = new Game();
        GameObject owner = new GameObject("Director", game, 0, 0, 1, 1);
        game.addEntity(owner);

        GlobalListeningScript retired = new GlobalListeningScript();
        owner.addComponent(retired);
        retired.internalTick();

        game.clearEntities();
        game.getSignalBus().enqueueGlobal("TEST_GLOBAL_SIGNAL", null);
        game.getSignalBus().processGlobalSignals();

        assertEquals(0, retired.received);
    }

    @Test
    void attachingScriptDuringPlayStartsItOnlyOnce() {
        Game game = new Game();
        GameObject owner = new GameObject("Director", game, 0, 0, 1, 1);
        game.addEntity(owner);
        game.playWorld();

        StartCountingScript script = new StartCountingScript();
        owner.addComponent(script);
        script.internalTick();

        assertEquals(1, script.starts);
    }

    private static final class ListeningScript extends IgnisScript {
        private int received;

        @Override
        public void start() {
            onSceneSignal("TEST_SCENE_SIGNAL", payload -> received++);
        }
    }

    private static final class GlobalListeningScript extends IgnisScript {
        private int received;

        @Override
        public void start() {
            onGlobal("TEST_GLOBAL_SIGNAL", payload -> received++);
        }
    }

    private static final class StartCountingScript extends IgnisScript {
        private int starts;

        @Override
        public void start() {
            starts++;
        }
    }
}
