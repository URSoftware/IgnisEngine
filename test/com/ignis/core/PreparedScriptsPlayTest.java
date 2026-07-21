package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression coverage for the editor's compile/reload/play lifecycle. */
class PreparedScriptsPlayTest {

    @TempDir
    Path tempDir;

    @Test
    void preparedPlayDoesNotCompileASecondScriptGeneration() {
        CountingScriptManager manager = new CountingScriptManager(tempDir.resolve("prepared").toFile());
        Game game = gameWith(manager);

        game.playWorldWithPreparedScripts();

        assertEquals(0, manager.compileCount);
        game.stopWorld();
        manager.close();
    }

    @Test
    void standalonePlayStillCompilesScriptsOnce() {
        CountingScriptManager manager = new CountingScriptManager(tempDir.resolve("standalone").toFile());
        Game game = gameWith(manager);

        game.playWorld();

        assertEquals(1, manager.compileCount);
        game.stopWorld();
        manager.close();
    }

    private Game gameWith(ScriptManager manager) {
        Game game = new Game();
        game.setSuppressAwtRepaint(true);
        game.setScriptManager(manager);
        return game;
    }

    private static final class CountingScriptManager extends ScriptManager {
        private int compileCount;

        private CountingScriptManager(File projectFolder) {
            super(projectFolder);
        }

        @Override
        public int compileAllScripts() {
            compileCount++;
            return 0;
        }
    }
}
