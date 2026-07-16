package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptManagerTest {

    @TempDir
    Path projectFolder;

    @Test
    void retainsPreviousClassLoadersUntilManagerCloses() throws Exception {
        Path script = projectFolder.resolve("scripts/TestScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "public class TestScript extends com.ignis.core.IgnisScript {}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        assertTrue(manager.compileScript(script.toFile()));
        assertTrue(manager.compileScript(script.toFile()));
        assertEquals(1, manager.retainedClassLoaderCount());

        manager.close();
        assertEquals(0, manager.retainedClassLoaderCount());
    }

    @Test
    void boundsRetiredClassLoadersAcrossManyRecompiles() throws Exception {
        Path script = projectFolder.resolve("scripts/TestScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "public class TestScript extends com.ignis.core.IgnisScript {}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        for (int i = 0; i < 6; i++) {
            assertTrue(manager.compileScript(script.toFile()));
        }
        // Sem o teto, seriam 5 loaders aposentados retidos — memoria crescendo a
        // cada Play. Com o teto, apenas as gerações mais recentes ficam abertas.
        assertTrue(manager.retainedClassLoaderCount() <= 2,
                "retidos: " + manager.retainedClassLoaderCount());

        manager.close();
        assertEquals(0, manager.retainedClassLoaderCount());
    }

    @Test
    void compileAllReloadsClassLoaderOncePerBatch() throws Exception {
        for (String name : new String[] {"AlphaScript", "BetaScript", "GammaScript"}) {
            Path script = projectFolder.resolve("scripts/" + name + ".java");
            Files.createDirectories(script.getParent());
            Files.writeString(script,
                    "public class " + name + " extends com.ignis.core.IgnisScript {}\n");
        }

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        assertEquals(3, manager.compileAllScripts());
        // Primeiro lote: nenhum loader anterior para aposentar.
        assertEquals(0, manager.retainedClassLoaderCount());

        assertEquals(3, manager.compileAllScripts());
        // Segundo lote: UMA recarga -> UM aposentado (e nao um por script).
        assertEquals(1, manager.retainedClassLoaderCount());

        manager.close();
    }
}
