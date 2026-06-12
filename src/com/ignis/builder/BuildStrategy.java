package com.ignis.builder;

import java.io.File;

/**
 * A compilation strategy for one family of platforms.
 *
 * Implementations:
 * - {@link JavaBuildStrategy}: JVM distributions (Windows, Linux, macOS)
 * - {@link CppExportStrategy}: native C++ project export (consoles)
 */
public interface BuildStrategy {

    /**
     * Builds the project for a single target.
     *
     * @param request immutable description of what to build
     * @param target  the platform to build for
     * @param log     progress sink
     * @return the result of the build (never null)
     */
    BuildResult build(BuildRequest request, BuildTarget target, BuildLogger log);

    /** Immutable input shared by all strategies. */
    class BuildRequest {
        private final File ignisFile;
        private final File projectMainFolder;
        private final BuildConfig config;

        public BuildRequest(File ignisFile, BuildConfig config) {
            this.ignisFile = ignisFile;
            this.projectMainFolder = ignisFile.getParentFile();
            this.config = config;
        }

        public File getIgnisFile() {
            return ignisFile;
        }

        /** projects/[ProjectName]/ — contains the .ignis file and project/ folder. */
        public File getProjectMainFolder() {
            return projectMainFolder;
        }

        public BuildConfig getConfig() {
            return config;
        }

        /** projects/[ProjectName]/build/ (or configured output dir). */
        public File getBuildRoot() {
            return new File(projectMainFolder, config.getOutputDirName());
        }
    }
}
