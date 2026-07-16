package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.ScriptManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class IgnisToolRegistryTest {

    @TempDir
    File projectFolder;

    @Test
    void reusesProjectScriptManagerAcrossMcpCallsAndLiveEditor() {
        IgnisToolRegistry registry = new IgnisToolRegistry(projectFolder);
        ScriptManager first = registry.scriptManager();

        assertSame(first, registry.scriptManager());

        Game game = new Game();
        game.setSize(320, 180);
        registry.attachLiveEditor(game, () -> { }, () -> { }, () -> { }, () -> { });

        assertSame(first, game.getScriptManager());
        assertNotNull(game.getPrefabManager());
    }
}
