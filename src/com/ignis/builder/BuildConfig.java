package com.ignis.builder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-project build configuration, persisted as build.json inside the
 * project main folder (next to the .ignis file).
 *
 * Holds global settings (game name, version, window) plus optional
 * per-platform overrides, keeping each platform independently configurable.
 */
public class BuildConfig {

    public static final String BUILD_CONFIG_FILE = "build.json";

    private String gameName = "IgnisGame";
    private String version = "1.0.0";
    private int width = 1280;
    private int height = 720;
    private boolean fullscreen = false;
    private List<BuildTarget> targets = new ArrayList<>();
    private String outputDirName = "build";

    /** Platform-specific overrides keyed by target id (e.g. "windows"). */
    private final Map<String, JSONObject> platformSettings = new HashMap<>();

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("gameName", gameName);
        json.put("version", version);
        json.put("width", width);
        json.put("height", height);
        json.put("fullscreen", fullscreen);
        json.put("outputDir", outputDirName);

        JSONArray targetArray = new JSONArray();
        for (BuildTarget target : targets) {
            targetArray.put(target.name());
        }
        json.put("targets", targetArray);

        JSONObject platforms = new JSONObject();
        for (Map.Entry<String, JSONObject> entry : platformSettings.entrySet()) {
            platforms.put(entry.getKey(), entry.getValue());
        }
        json.put("platforms", platforms);
        return json;
    }

    public static BuildConfig fromJSON(JSONObject json) {
        BuildConfig config = new BuildConfig();
        config.gameName = json.optString("gameName", config.gameName);
        config.version = json.optString("version", config.version);
        config.width = json.optInt("width", config.width);
        config.height = json.optInt("height", config.height);
        config.fullscreen = json.optBoolean("fullscreen", config.fullscreen);
        config.outputDirName = json.optString("outputDir", config.outputDirName);

        JSONArray targetArray = json.optJSONArray("targets");
        if (targetArray != null) {
            for (int i = 0; i < targetArray.length(); i++) {
                try {
                    config.targets.add(BuildTarget.valueOf(targetArray.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown target in file (e.g. from a newer engine) — skip
                }
            }
        }

        JSONObject platforms = json.optJSONObject("platforms");
        if (platforms != null) {
            for (String key : platforms.keySet()) {
                config.platformSettings.put(key, platforms.getJSONObject(key));
            }
        }
        return config;
    }

    /** Loads build.json from the project main folder, or returns defaults. */
    public static BuildConfig load(File projectMainFolder) {
        File file = new File(projectMainFolder, BUILD_CONFIG_FILE);
        if (file.exists()) {
            try {
                String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                return fromJSON(new JSONObject(raw));
            } catch (Exception e) {
                System.err.println("Could not read " + BUILD_CONFIG_FILE + ": " + e.getMessage());
            }
        }
        return new BuildConfig();
    }

    public void save(File projectMainFolder) throws java.io.IOException {
        File file = new File(projectMainFolder, BUILD_CONFIG_FILE);
        Files.write(file.toPath(), toJSON().toString(2).getBytes(StandardCharsets.UTF_8));
    }

    /** Settings of a specific platform (empty object if none stored). */
    public JSONObject getPlatformSettings(BuildTarget target) {
        return platformSettings.getOrDefault(target.id(), new JSONObject());
    }

    public void setPlatformSettings(BuildTarget target, JSONObject settings) {
        platformSettings.put(target.id(), settings);
    }

    // Getters / setters

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public List<BuildTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<BuildTarget> targets) {
        this.targets = targets;
    }

    public String getOutputDirName() {
        return outputDirName;
    }

    public void setOutputDirName(String outputDirName) {
        this.outputDirName = outputDirName;
    }

    /** Sanitized name safe for files and folders. */
    public String getSafeGameName() {
        String safe = gameName.trim().replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
        return safe.isEmpty() ? "IgnisGame" : safe;
    }
}
