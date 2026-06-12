package com.ignis.core;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages prefabs - reusable object templates that can be instantiated in the game.
 * 
 * Prefabs are saved as JSON files in the project/prefabs/ folder.
 * When instantiated, they create a copy of the object with all its properties,
 * scripts, and settings preserved.
 */
public class PrefabManager {
    
    private File prefabsFolder;
    private Game game;
    private ScriptManager scriptManager;
    
    public PrefabManager(File projectFolder, Game game, ScriptManager scriptManager) {
        this.prefabsFolder = new File(projectFolder, "prefabs");
        this.game = game;
        this.scriptManager = scriptManager;
        
        if (!prefabsFolder.exists()) {
            prefabsFolder.mkdirs();
        }
    }
    
    /**
     * Saves a GameObject as a prefab
     * @param object The object to save as prefab
     * @param prefabName The name for the prefab
     * @return true if saved successfully
     */
    public boolean savePrefab(GameObject object, String prefabName) {
        if (object == null || prefabName == null || prefabName.trim().isEmpty()) {
            return false;
        }
        
        // Sanitize name
        prefabName = prefabName.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        
        try {
            JSONObject prefabJson = serializeGameObject(object);
            prefabJson.put("prefabName", prefabName);
            prefabJson.put("prefabVersion", "1.0");
            
            File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
            
            try (FileWriter writer = new FileWriter(prefabFile)) {
                writer.write(prefabJson.toString(2));
            }
            
            System.out.println("Prefab saved: " + prefabFile.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            System.err.println("Failed to save prefab: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Serializes a GameObject to JSON
     */
    private JSONObject serializeGameObject(GameObject object) {
        JSONObject json = new JSONObject();
        
        // Basic properties
        json.put("type", object.getType());
        json.put("name", object.getName());
        
        // Transform
        JSONObject transform = new JSONObject();
        transform.put("x", object.getX());
        transform.put("y", object.getY());
        transform.put("rotation", object.getRotation());
        transform.put("width", object.getWidth());
        transform.put("height", object.getHeight());
        json.put("transform", transform);
        
        // Sprite
        if (object.getSpritePath() != null && !object.getSpritePath().isEmpty()) {
            json.put("spritePath", object.getSpritePath());
        }
        
        // Scripts
        if (!object.getScriptNames().isEmpty()) {
            JSONArray scripts = new JSONArray();
            for (int i = 0; i < object.getScriptNames().size(); i++) {
                String scriptName = object.getScriptNames().get(i);
                JSONObject scriptData = new JSONObject();
                scriptData.put("name", scriptName);
                
                if (i < object.getScripts().size()) {
                    IgnisScript script = object.getScripts().get(i);
                    JSONObject variables = ScriptSerializationHelper.saveScriptVariables(script);
                    if (variables.length() > 0) {
                        scriptData.put("variables", variables);
                    }
                }
                scripts.put(scriptData);
            }
            json.put("scripts", scripts);
        }
        
        // Custom properties
        json.put("properties", object.saveProperties());
        
        return json;
    }
    
    /**
     * Instantiates a prefab by name
     * @param prefabName Name of the prefab to instantiate
     * @return The instantiated GameObject, or null if failed
     */
    public GameObject instantiatePrefab(String prefabName) {
        return instantiatePrefab(prefabName, -1, -1);
    }
    
    /**
     * Instantiates a prefab at a specific position
     * @param prefabName Name of the prefab
     * @param x X position (use -1 to use prefab's saved position)
     * @param y Y position (use -1 to use prefab's saved position)
     * @return The instantiated GameObject
     */
    public GameObject instantiatePrefab(String prefabName, double x, double y) {
        File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
        
        if (!prefabFile.exists()) {
            System.err.println("Prefab not found: " + prefabName);
            return null;
        }
        
        try {
            String content = readFile(prefabFile);
            JSONObject prefabJson = new JSONObject(content);
            
            return deserializeGameObject(prefabJson, x, y);
            
        } catch (Exception e) {
            System.err.println("Failed to instantiate prefab: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Deserializes a GameObject from JSON
     */
    private GameObject deserializeGameObject(JSONObject json, double overrideX, double overrideY) {
        String type = json.getString("type");
        
        // Create object using factory
        GameObject object = EntityFactory.create(type);
        object.setGame(game);
        
        // Generate new unique ID
        object.setId(java.util.UUID.randomUUID().toString());
        
        // Set name (append copy indicator)
        String baseName = json.getString("name");
        object.setName(generateUniqueName(baseName));
        
        // Transform
        JSONObject transform = json.getJSONObject("transform");
        
        if (overrideX >= 0) {
            object.setX(overrideX);
        } else {
            object.setX(transform.getDouble("x"));
        }
        
        if (overrideY >= 0) {
            object.setY(overrideY);
        } else {
            object.setY(transform.getDouble("y"));
        }
        
        object.setRotation(transform.optDouble("rotation", 0));
        object.setWidth(transform.getInt("width"));
        object.setHeight(transform.getInt("height"));
        
        // Sprite
        if (json.has("spritePath")) {
            object.setSpritePath(json.getString("spritePath"));
        }
        
        // Scripts
        if (json.has("scripts")) {
            JSONArray scriptsArray = json.getJSONArray("scripts");
            List<String> scriptNames = new ArrayList<>();
            
            for (int i = 0; i < scriptsArray.length(); i++) {
                Object scriptEntry = scriptsArray.get(i);
                String scriptName = null;
                JSONObject variables = null;
                
                if (scriptEntry instanceof String) {
                    scriptName = (String) scriptEntry;
                } else if (scriptEntry instanceof JSONObject) {
                    JSONObject scriptData = (JSONObject) scriptEntry;
                    scriptName = scriptData.getString("name");
                    if (scriptData.has("variables")) {
                        variables = scriptData.getJSONObject("variables");
                    }
                }
                
                if (scriptName != null) {
                    scriptNames.add(scriptName);
                    
                    // Create script instance if ScriptManager is available
                    if (scriptManager != null) {
                        IgnisScript instance = scriptManager.createScriptInstance(scriptName, object, game);
                        if (instance != null) {
                            object.getScripts().add(instance);
                            if (variables != null) {
                                final Game activeGame = game;
                                ScriptSerializationHelper.loadScriptVariables(instance, variables, name -> {
                                    if (activeGame == null) return null;
                                    for (GameObject entity : activeGame.getEntities()) {
                                        if (name.equals(entity.getName())) {
                                            return entity;
                                        }
                                    }
                                    return null;
                                });
                            }
                        }
                    }
                }
            }
            
            object.setScriptNames(scriptNames);
        }
        
        // Custom properties
        if (json.has("properties")) {
            object.loadProperties(json.getJSONObject("properties"));
        }
        
        return object;
    }
    
    /**
     * Generates a unique name for an instantiated prefab
     */
    private String generateUniqueName(String baseName) {
        // Remove any existing (Clone) or (N) suffix
        baseName = baseName.replaceAll("\\s*\\(Clone\\)$", "");
        baseName = baseName.replaceAll("\\s*\\(\\d+\\)$", "");
        
        // Check existing names
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (GameObject obj : game.getEntities()) {
            existingNames.add(obj.getName());
        }
        
        // Try without suffix first
        String name = baseName + " (Clone)";
        if (!existingNames.contains(name)) {
            return name;
        }
        
        // Add number suffix
        int counter = 2;
        while (existingNames.contains(baseName + " (Clone " + counter + ")")) {
            counter++;
        }
        
        return baseName + " (Clone " + counter + ")";
    }
    
    /**
     * Lists all available prefabs
     * @return List of prefab names (without extension)
     */
    public List<String> listPrefabs() {
        List<String> prefabs = new ArrayList<>();
        
        if (prefabsFolder.exists() && prefabsFolder.isDirectory()) {
            File[] files = prefabsFolder.listFiles((dir, name) -> name.endsWith(".prefab.json"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().replace(".prefab.json", "");
                    prefabs.add(name);
                }
            }
        }
        
        return prefabs;
    }
    
    /**
     * Deletes a prefab
     * @param prefabName Name of the prefab to delete
     * @return true if deleted successfully
     */
    public boolean deletePrefab(String prefabName) {
        File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
        if (prefabFile.exists()) {
            return prefabFile.delete();
        }
        return false;
    }
    
    /**
     * Checks if a prefab exists
     */
    public boolean prefabExists(String prefabName) {
        File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
        return prefabFile.exists();
    }
    
    /**
     * Gets the prefabs folder
     */
    public File getPrefabsFolder() {
        return prefabsFolder;
    }
    
    /**
     * Reads a file to string
     */
    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}
