package com.ignis.core;

import org.json.JSONObject;
import java.io.File;

/**
 * Represents an Ignis Engine project.
 * Contains global project information such as name, version and main scene.
 */
public class Project {

    public static final String ENGINE_VERSION = "0.1.0";

    private String projectName;
    private String mainScene;
    private File projectFile;
    private Scene currentScene;

    public Project() {
        this.projectName = "New Project";
        this.mainScene = "main.scene.json";
        this.currentScene = new Scene("MainScene");
    }

    public Project(String projectName) {
        this.projectName = projectName;
        this.mainScene = "main.scene.json";
        this.currentScene = new Scene("MainScene");
    }

    /**
     * Serializes the project to JSON (project.json)
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("engineVersion", ENGINE_VERSION);
        json.put("projectName", projectName);
        json.put("mainScene", mainScene);
        return json;
    }

    /**
     * Loads the project from JSON
     */
    public static Project fromJSON(JSONObject json) {
        Project project = new Project();
        project.projectName = json.getString("projectName");
        project.mainScene = json.getString("mainScene");
        return project;
    }

    // Getters and Setters
    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getMainScene() {
        return mainScene;
    }

    public void setMainScene(String mainScene) {
        this.mainScene = mainScene;
    }

    public File getProjectFile() {
        return projectFile;
    }

    public void setProjectFile(File projectFile) {
        this.projectFile = projectFile;
    }

    public Scene getCurrentScene() {
        return currentScene;
    }

    public void setCurrentScene(Scene currentScene) {
        this.currentScene = currentScene;
    }

    /**
     * Gets the project directory (parent of the project file).
     * @return The project directory, or null if projectFile is not set
     */
    public File getProjectDir() {
        if (projectFile != null) {
            return projectFile.getParentFile();
        }
        return null;
    }

    public static String getEngineVersion() {
        return ENGINE_VERSION;
    }
}
