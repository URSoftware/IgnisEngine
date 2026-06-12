package com.ignis.core;

import java.io.File;
import java.nio.file.Path;

/**
 * Resolves asset paths stored in scenes and prefabs.
 *
 * Asset paths are saved relative to the project folder (e.g.
 * "assets/sprites/Grama.jpg") so projects stay portable across machines and
 * survive Builder distribution. Absolute paths written by older scenes are
 * still accepted and used as-is.
 *
 * The base folder is set automatically by {@link IgnisProjectIO} whenever a
 * project is saved or loaded.
 */
public final class AssetResolver {

    private static File projectFolder;

    private AssetResolver() {
    }

    /**
     * Sets the project folder (the "project/" directory of the current
     * project) used as base for relative asset paths.
     */
    public static void setProjectFolder(File folder) {
        projectFolder = folder;
    }

    public static File getProjectFolder() {
        return projectFolder;
    }

    /**
     * Resolves an asset path to a File. Absolute paths (legacy scenes) are
     * returned as-is; relative paths are resolved against the current project
     * folder.
     */
    public static File resolve(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        if (projectFolder != null) {
            return new File(projectFolder, path);
        }
        return file;
    }

    /**
     * Returns the path of the file relative to the current project folder,
     * using forward slashes, or null if the file is outside the project
     * folder (or no project folder is set).
     */
    public static String relativize(File file) {
        if (projectFolder == null || file == null) {
            return null;
        }
        try {
            Path base = projectFolder.getCanonicalFile().toPath();
            Path target = file.getCanonicalFile().toPath();
            if (!target.startsWith(base)) {
                return null;
            }
            return base.relativize(target).toString().replace(File.separatorChar, '/');
        } catch (Exception e) {
            return null;
        }
    }
}
