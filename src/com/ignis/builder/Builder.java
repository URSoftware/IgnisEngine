package com.ignis.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builder — orchestrates the generation of final game binaries.
 *
 * Decoupled module: depends only on the project files on disk and on the
 * compiled engine classes; it has no reference to the editor UI. The editor
 * (or a CLI) drives it through {@link #build(File, BuildConfig, BuildLogger)}.
 *
 * Strategy dispatch:
 * - {@link BuildTarget.Strategy#JAVA} → {@link JavaBuildStrategy}
 * - {@link BuildTarget.Strategy#CPP}  → {@link CppExportStrategy}
 */
public class Builder {

    private final Map<BuildTarget.Strategy, BuildStrategy> strategies =
            new EnumMap<>(BuildTarget.Strategy.class);

    public Builder() {
        strategies.put(BuildTarget.Strategy.JAVA, new JavaBuildStrategy());
        strategies.put(BuildTarget.Strategy.CPP, new CppExportStrategy());
    }

    /** Allows replacing a strategy (tests, future SDK-aware implementations). */
    public void setStrategy(BuildTarget.Strategy kind, BuildStrategy strategy) {
        strategies.put(kind, strategy);
    }

    /**
     * Builds the project for every target selected in the config.
     *
     * @param ignisFile the project's .ignis file
     * @param config    build configuration (targets, version, window)
     * @param log       progress sink (use BuildLogger.CONSOLE when headless)
     * @return one result per requested target, in order
     */
    public List<BuildResult> build(File ignisFile, BuildConfig config, BuildLogger log) {
        List<BuildResult> results = new ArrayList<>();

        String validationError = validate(ignisFile, config);
        if (validationError != null) {
            log.error(validationError);
            for (BuildTarget target : config.getTargets()) {
                results.add(BuildResult.fail(target, validationError, 0));
            }
            return results;
        }

        BuildStrategy.BuildRequest request = new BuildStrategy.BuildRequest(ignisFile, config);
        log.log("Building '" + config.getGameName() + "' v" + config.getVersion()
                + " (" + config.getTargets().size() + " target(s))");

        for (BuildTarget target : config.getTargets()) {
            if (!target.isEnabled()) {
                log.log("Skipping " + target.getDisplayName() + " (planned, not yet enabled)");
                results.add(BuildResult.fail(target, "Target planned for a future release", 0));
                continue;
            }
            log.log("---- " + target.getDisplayName() + " ----");
            BuildStrategy strategy = strategies.get(target.getStrategy());
            results.add(strategy.build(request, target, log));
        }

        long failures = results.stream().filter(r -> !r.isSuccess()).count();
        log.log("Build finished: " + (results.size() - failures) + " succeeded, " + failures + " failed.");
        return results;
    }

    /** Returns an error description, or null when the request is valid. */
    private String validate(File ignisFile, BuildConfig config) {
        if (ignisFile == null || !ignisFile.exists()) {
            return "Project file not found: " + ignisFile;
        }
        if (!ignisFile.getName().endsWith(".ignis")) {
            return "Not an .ignis project file: " + ignisFile.getName();
        }
        if (config.getTargets().isEmpty()) {
            return "No build target selected";
        }
        File projectFolder = new File(ignisFile.getParentFile(), "project");
        if (!projectFolder.isDirectory()) {
            return "Project content folder missing: " + projectFolder.getAbsolutePath();
        }
        return null;
    }

    /**
     * Headless entry point:
     * java com.ignis.builder.Builder path/to/Game.ignis [WINDOWS LINUX MACOS XBOX PLAYSTATION]
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Builder <project.ignis> [targets...]");
            System.exit(2);
        }
        File ignisFile = new File(args[0]);
        BuildConfig config = BuildConfig.load(ignisFile.getParentFile());

        if (args.length > 1) {
            config.getTargets().clear();
            for (int i = 1; i < args.length; i++) {
                config.getTargets().add(BuildTarget.valueOf(args[i].toUpperCase()));
            }
        } else if (config.getTargets().isEmpty()) {
            config.getTargets().add(BuildTarget.WINDOWS);
        }

        List<BuildResult> results = new Builder().build(ignisFile, config, BuildLogger.CONSOLE);
        boolean allOk = results.stream().allMatch(BuildResult::isSuccess);
        for (BuildResult result : results) {
            System.out.println(result);
        }
        System.exit(allOk ? 0 : 1);
    }
}
