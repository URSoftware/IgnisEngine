package com.ignis.animation;

import com.ignis.core.IgnisLogger;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves {@link SpriteAnimation}s as project assets under
 * project/assets/animations/*.anim.json. Keeps animation definitions versionable
 * and shareable, independent of any single scene or entity.
 */
public final class AnimationIO {

    public static final String ANIMATIONS_DIR = "assets/animations";
    public static final String EXTENSION = ".anim.json";

    private AnimationIO() {
    }

    /** Returns the animations folder for a project "project/" folder, creating it. */
    public static File getAnimationsFolder(File projectFolder) {
        File folder = new File(projectFolder, ANIMATIONS_DIR);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static void save(SpriteAnimation animation, File projectFolder) throws IOException {
        File folder = getAnimationsFolder(projectFolder);
        File file = new File(folder, sanitize(animation.getName()) + EXTENSION);
        Files.write(file.toPath(), animation.toJSON().toString(2).getBytes(StandardCharsets.UTF_8));
    }

    public static SpriteAnimation load(File animFile) throws IOException {
        String raw = new String(Files.readAllBytes(animFile.toPath()), StandardCharsets.UTF_8);
        return SpriteAnimation.fromJSON(new JSONObject(raw));
    }

    /** Loads every animation asset in the project. */
    public static List<SpriteAnimation> loadAll(File projectFolder) {
        List<SpriteAnimation> result = new ArrayList<>();
        File folder = new File(projectFolder, ANIMATIONS_DIR);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (files != null) {
            for (File file : files) {
                try {
                    result.add(load(file));
                } catch (Exception e) {
                    IgnisLogger.error("[AnimationIO] Failed to load " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        return result;
    }

    private static String sanitize(String name) {
        String safe = name.trim().replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
        return safe.isEmpty() ? "animation" : safe;
    }
}
