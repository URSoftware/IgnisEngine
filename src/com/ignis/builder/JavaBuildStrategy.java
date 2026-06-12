package com.ignis.builder;

import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * JVM build strategy — produces self-contained distributions for
 * Windows, Linux and macOS.
 *
 * Output layout (build/[target]/[GameName]/):
 *
 *   engine/ignis-engine.jar   executable jar (Main-Class: GameRuntime)
 *   engine/json.jar           org.json dependency
 *   projects/[Project]/       full copy of the game project
 *   runtime.json              window/project configuration
 *   [GameName].bat|.sh        platform launcher (sets working dir)
 *   README.txt                run instructions
 *
 * The projects/ layout is preserved on purpose: relative asset paths
 * (e.g. MusicPath) resolve against the working directory exactly as
 * they do inside the editor.
 */
public class JavaBuildStrategy implements BuildStrategy {

    private static final String MAIN_CLASS = "com.ignis.runtime.GameRuntime";

    @Override
    public BuildResult build(BuildRequest request, BuildTarget target, BuildLogger log) {
        long start = System.currentTimeMillis();
        try {
            BuildConfig config = request.getConfig();
            String gameName = config.getSafeGameName();

            File targetDir = new File(request.getBuildRoot(), target.id());
            File appDir = new File(targetDir, gameName);
            log.log("Preparing output folder: " + appDir.getAbsolutePath());
            BuildIO.deleteDirectory(appDir);
            java.nio.file.Files.createDirectories(appDir.toPath());

            // 1. Engine runtime (jar from the running engine's code source)
            log.log("Packaging engine runtime...");
            File engineDir = new File(appDir, "engine");
            java.nio.file.Files.createDirectories(engineDir.toPath());

            File engineSource = BuildIO.codeSourceOf(JavaBuildStrategy.class);
            File engineJar = new File(engineDir, "ignis-engine.jar");
            BuildIO.createEngineJar(engineSource, engineJar, MAIN_CLASS, "json.jar");

            File jsonSource = BuildIO.codeSourceOf(JSONObject.class);
            File jsonJar = new File(engineDir, "json.jar");
            if (jsonSource.isFile()) {
                java.nio.file.Files.copy(jsonSource.toPath(), jsonJar.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                // org.json compiled into a classes dir — repackage it
                BuildIO.createEngineJar(jsonSource, jsonJar, "org.json.JSONObject", null);
            }

            // 2. Game project copy, preserving the projects/<Name>/ layout.
            //    The build output folder itself is excluded to avoid recursion.
            log.log("Copying game project...");
            File projectMain = request.getProjectMainFolder();
            File projectsDir = new File(appDir, "projects");
            File projectCopy = new File(projectsDir, projectMain.getName());
            Set<String> exclude = new HashSet<>();
            exclude.add(config.getOutputDirName());
            BuildIO.copyDirectory(projectMain, projectCopy, exclude);

            // 3. Runtime configuration
            log.log("Writing runtime.json...");
            JSONObject runtime = new JSONObject();
            runtime.put("project", "projects/" + projectMain.getName() + "/" + request.getIgnisFile().getName());
            runtime.put("title", config.getGameName());
            runtime.put("width", config.getWidth());
            runtime.put("height", config.getHeight());
            runtime.put("fullscreen", config.isFullscreen());
            BuildIO.writeString(new File(appDir, "runtime.json"), runtime.toString(2));

            // 4. Platform launcher
            log.log("Generating launcher for " + target.getDisplayName() + "...");
            writeLauncher(appDir, gameName, target);

            // 5. Run instructions
            BuildIO.writeString(new File(appDir, "README.txt"), readmeText(gameName, config, target));

            // 6. Distribution zip
            File zipFile = new File(targetDir,
                    gameName + "-" + config.getVersion() + "-" + target.id() + ".zip");
            log.log("Packaging distribution zip: " + zipFile.getName());
            BuildIO.zipDirectory(appDir, zipFile);

            log.log("Done: " + target.getDisplayName());
            return BuildResult.ok(target, appDir, System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error(target.getDisplayName() + ": " + e.getMessage());
            return BuildResult.fail(target, e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void writeLauncher(File appDir, String gameName, BuildTarget target) throws Exception {
        if (target == BuildTarget.WINDOWS) {
            String bat = "@echo off\r\n"
                    + "cd /d \"%~dp0\"\r\n"
                    + "start \"\" javaw -jar \"engine\\ignis-engine.jar\"\r\n";
            BuildIO.writeString(new File(appDir, gameName + ".bat"), bat);
        } else {
            String sh = "#!/bin/sh\n"
                    + "cd \"$(dirname \"$0\")\"\n"
                    + "exec java -jar \"engine/ignis-engine.jar\"\n";
            File launcher = new File(appDir, gameName + ".sh");
            BuildIO.writeString(launcher, sh);
            BuildIO.makeExecutable(launcher);
            if (target == BuildTarget.MACOS) {
                // .command files are double-clickable in Finder
                File command = new File(appDir, gameName + ".command");
                BuildIO.writeString(command, sh);
                BuildIO.makeExecutable(command);
            }
        }
    }

    private String readmeText(String gameName, BuildConfig config, BuildTarget target) {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getGameName()).append(" ").append(config.getVersion())
                .append(" - ").append(target.getDisplayName()).append(" build\n");
        sb.append("Generated by IgnisEngine Builder\n\n");
        sb.append("Requirements: Java 17 or newer (https://adoptium.net)\n\n");
        sb.append("How to run:\n");
        if (target == BuildTarget.WINDOWS) {
            sb.append("  Double-click ").append(gameName).append(".bat\n");
        } else if (target == BuildTarget.MACOS) {
            sb.append("  Double-click ").append(gameName).append(".command\n");
            sb.append("  (first run: chmod +x ").append(gameName).append(".command)\n");
        } else {
            sb.append("  ./").append(gameName).append(".sh\n");
            sb.append("  (first run: chmod +x ").append(gameName).append(".sh)\n");
        }
        sb.append("\nAlternatively: java -jar engine/ignis-engine.jar\n");
        return sb.toString();
    }

    /** Convenience for tests/CLI: builds all JVM targets. */
    public static Set<BuildTarget> supportedTargets() {
        Set<BuildTarget> set = new HashSet<>();
        for (BuildTarget t : BuildTarget.values()) {
            if (t.getStrategy() == BuildTarget.Strategy.JAVA) {
                set.add(t);
            }
        }
        return Collections.unmodifiableSet(set);
    }
}
