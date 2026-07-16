package com.ignis.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaBuildStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void javaBuildPackagesAllRuntimeDependencies() throws Exception {
        Path projectRoot = tempDir.resolve("SampleGame");
        Files.createDirectories(projectRoot.resolve("project"));
        Path ignisFile = Files.write(projectRoot.resolve("SampleGame.ignis"), new byte[0]);

        BuildConfig config = new BuildConfig();
        config.setGameName("Sample Game");
        config.setOutputDirName("dist");
        config.setTargets(Collections.singletonList(BuildTarget.WINDOWS));

        BuildResult result = new Builder().build(
                ignisFile.toFile(), config, message -> { }).get(0);

        assertTrue(result.isSuccess(), result.getMessage());
        File engineDir = new File(result.getOutputDir(), "engine");
        File engineJar = new File(engineDir, "ignis-engine.jar");
        assertTrue(engineJar.isFile());
        assertTrue(new File(engineDir, "json.jar").isFile());
        assertTrue(new File(engineDir, "fxevents.jar").isFile());

        try (JarFile jar = new JarFile(engineJar)) {
            String classPath = jar.getManifest().getMainAttributes()
                    .getValue(Attributes.Name.CLASS_PATH);
            assertEquals("json.jar fxevents.jar", classPath);
        }
    }
}
