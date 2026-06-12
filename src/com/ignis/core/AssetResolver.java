package com.ignis.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

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
        // Switching projects: drop cached images so memory stays bounded and no
        // stale asset from a previous project lingers.
        if (folder != projectFolder && (folder == null || !folder.equals(projectFolder))) {
            clearImageCache();
        }
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

    /** Cached, modification-aware image entry. */
    private static final class CachedImage {
        final long lastModified;
        final BufferedImage image;

        CachedImage(long lastModified, BufferedImage image) {
            this.lastModified = lastModified;
            this.image = image;
        }
    }

    private static final Map<String, CachedImage> IMAGE_CACHE = new ConcurrentHashMap<>();

    /**
     * Loads a sprite image for an asset path, sharing one decoded instance
     * across all entities that reference the same file. The cache is keyed by
     * resolved absolute path and validated against the file's last-modified
     * timestamp, so editing/reimporting a sprite externally refreshes it
     * automatically. Returns null (logging once) when the file is missing or
     * cannot be read. The returned image is read-only — never mutate it.
     */
    public static BufferedImage loadImage(String path) {
        File file = resolve(path);
        if (file == null) {
            return null;
        }
        if (!file.exists()) {
            System.err.println("Sprite file not found: " + path);
            IMAGE_CACHE.remove(file.getAbsolutePath());
            return null;
        }

        String key = file.getAbsolutePath();
        long modified = file.lastModified();
        CachedImage cached = IMAGE_CACHE.get(key);
        if (cached != null && cached.lastModified == modified) {
            return cached.image;
        }

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                System.err.println("Unsupported image format: " + path);
                return null;
            }
            IMAGE_CACHE.put(key, new CachedImage(modified, image));
            return image;
        } catch (Exception e) {
            System.err.println("Failed to load sprite: " + e.getMessage());
            return null;
        }
    }

    /** Clears the shared image cache (e.g. when switching projects). */
    public static void clearImageCache() {
        IMAGE_CACHE.clear();
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
