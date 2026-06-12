package com.ignis.builder;

import java.io.File;

/**
 * Outcome of building one target.
 */
public class BuildResult {

    private final BuildTarget target;
    private final boolean success;
    private final String message;
    private final File outputDir;
    private final long durationMs;

    public BuildResult(BuildTarget target, boolean success, String message, File outputDir, long durationMs) {
        this.target = target;
        this.success = success;
        this.message = message;
        this.outputDir = outputDir;
        this.durationMs = durationMs;
    }

    public static BuildResult ok(BuildTarget target, File outputDir, long durationMs) {
        return new BuildResult(target, true, "Build succeeded", outputDir, durationMs);
    }

    public static BuildResult fail(BuildTarget target, String message, long durationMs) {
        return new BuildResult(target, false, message, null, durationMs);
    }

    public BuildTarget getTarget() {
        return target;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String toString() {
        return "[" + target.getDisplayName() + "] " + (success ? "OK" : "FAILED") + " - " + message
                + " (" + durationMs + " ms)";
    }
}
