package com.ignis.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an Ignis Engine project.
 * Contains global project information such as name, version and scenes.
 */
public class Project {

    public static final String ENGINE_VERSION = "0.1.0";

    private String projectName;
    private String mainScene;
    private File projectFile;
    private Scene currentScene;
    private List<Scene> scenes = new ArrayList<>();

    public Project() {
        this.projectName = "New Project";
        this.mainScene = "main.scene.json";
        this.currentScene = new Scene("MainScene");
        this.scenes.add(currentScene);
    }

    public Project(String projectName) {
        this.projectName = projectName;
        this.mainScene = "main.scene.json";
        this.currentScene = new Scene("MainScene");
        this.scenes.add(currentScene);
    }

    /**
     * Serializes the project to JSON (project.json)
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("engineVersion", ENGINE_VERSION);
        json.put("projectName", projectName);
        json.put("mainScene", mainScene);
        
        // Serialize all scenes
        JSONArray scenesArray = new JSONArray();
        for (Scene scene : scenes) {
            scenesArray.put(scene.toJSON());
        }
        json.put("scenes", scenesArray);
        
        return json;
    }

    /**
     * Loads the project from JSON
     */
    public static Project fromJSON(JSONObject json) {
        Project project = new Project();
        project.projectName = json.getString("projectName");
        project.mainScene = json.getString("mainScene");
        
        // Load scenes
        if (json.has("scenes")) {
            JSONArray scenesArray = json.getJSONArray("scenes");
            project.scenes.clear();
            for (int i = 0; i < scenesArray.length(); i++) {
                JSONObject sceneJson = scenesArray.getJSONObject(i);
                Scene scene = Scene.fromJSON(sceneJson, null); // game will be set later
                project.scenes.add(scene);
            }
            
            // Set current scene to the main scene or first scene
            for (Scene scene : project.scenes) {
                if (scene.getSceneName().equals(project.mainScene)) {
                    project.currentScene = scene;
                    break;
                }
            }
            if (project.currentScene == null && !project.scenes.isEmpty()) {
                project.currentScene = project.scenes.get(0);
            }
        }
        
        return project;
    }

    // Scene management methods
    
    /** Adds a new scene to the project */
    public void addScene(Scene scene) {
        if (scene != null && !scenes.contains(scene)) {
            scenes.add(scene);
        }
    }
    
    /** Removes a scene from the project */
    public boolean removeScene(Scene scene) {
        if (scene == currentScene && scenes.size() == 1) {
            return false; // Cannot remove the only scene
        }
        boolean removed = scenes.remove(scene);
        if (removed && scene == currentScene && !scenes.isEmpty()) {
            currentScene = scenes.get(0); // Switch to first scene
        }
        return removed;
    }
    
    /** Gets all scenes in the project */
    public List<Scene> getScenes() {
        return new ArrayList<>(scenes); // Return copy to prevent external modification
    }
    
    /** Gets a scene by name */
    public Scene getSceneByName(String name) {
        for (Scene scene : scenes) {
            if (scene.getSceneName().equals(name)) {
                return scene;
            }
        }
        return null;
    }
    
    /** Sets the current active scene */
    public void setCurrentScene(Scene scene) {
        if (scene != null && scenes.contains(scene)) {
            currentScene = scene;
        }
    }
    
    /** Switches to a scene by name */
    public boolean switchToScene(String sceneName) {
        Scene scene = getSceneByName(sceneName);
        if (scene != null) {
            currentScene = scene;
            return true;
        }
        return false;
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