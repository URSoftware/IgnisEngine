package com.ignis.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a game scene.
 * Contains a list of entities (GameObjects) that will be rendered,
 * and manages cameras for the scene.
 */
public class Scene {

    private String sceneName;
    private List<GameObject> entities;
    
    // Camera reference for the scene
    private Camera activeCamera;
    private List<Camera> cameras = new ArrayList<>();
    
    // Stores pending script variables to be applied when scripts are instantiated
    // Key: entity ID + ":" + scriptName, Value: variable values JSON
    private Map<String, JSONObject> pendingScriptVariables = new HashMap<>();

    public Scene(String sceneName) {
        this.sceneName = sceneName;
        this.entities = new ArrayList<>();
    }

    /**
     * Adds an entity to the scene.
     * If it's a Camera, also adds it to the camera list.
     */
    public void addEntity(GameObject entity) {
        entities.add(entity);
        
        // Track cameras separately
        if (entity instanceof Camera) {
            Camera cam = (Camera) entity;
            if (!cameras.contains(cam)) {
                cameras.add(cam);
            }
            // Set as active camera if it's the first one
            if (activeCamera == null) {
                activeCamera = cam;
                cam.setActive(true);
            }
        }
    }

    /**
     * Removes an entity from the scene.
     */
    public void removeEntity(GameObject entity) {
        entities.remove(entity);
        
        // Handle camera removal
        if (entity instanceof Camera) {
            Camera cam = (Camera) entity;
            cameras.remove(cam);
            
            // If we removed the active camera, pick a new one
            if (activeCamera == cam) {
                activeCamera = cameras.isEmpty() ? null : cameras.get(0);
                if (activeCamera != null) {
                    activeCamera.setActive(true);
                }
            }
        }
    }

    /**
     * Removes an entity by ID
     */
    public void removeEntityById(String id) {
        entities.removeIf(e -> e.getId().equals(id));
    }

    /**
     * Finds an entity by ID
     */
    public GameObject getEntityById(String id) {
        return entities.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Serializes the scene to JSON (.scene.json)
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("sceneName", sceneName);

        JSONArray entitiesArray = new JSONArray();
        for (GameObject entity : entities) {
            JSONObject entityJson = new JSONObject();
            entityJson.put("id", entity.getId());
            entityJson.put("type", entity.getType());
            entityJson.put("name", entity.getName());

            JSONObject position = new JSONObject();
            position.put("x", entity.getX());
            position.put("y", entity.getY());
            entityJson.put("position", position);

            JSONObject size = new JSONObject();
            size.put("width", entity.getWidth());
            size.put("height", entity.getHeight());
            entityJson.put("size", size);

            if (entity.getSpritePath() != null) {
                entityJson.put("sprite", entity.getSpritePath());
            }
            
            // Save attached audio component
            if (entity.getMusicPath() != null) {
                entityJson.put("musicPath", entity.getMusicPath().toJSON());
            }
            
            // Save attached scripts with their variable values
            if (!entity.getScriptNames().isEmpty()) {
                JSONArray scriptsArray = new JSONArray();
                
                for (int i = 0; i < entity.getScriptNames().size(); i++) {
                    String scriptName = entity.getScriptNames().get(i);
                    JSONObject scriptData = new JSONObject();
                    scriptData.put("name", scriptName);
                    
                    // Save script variables if script instance exists
                    if (i < entity.getScripts().size()) {
                        IgnisScript script = entity.getScripts().get(i);
                        JSONObject variables = saveScriptVariables(script);
                        if (variables.length() > 0) {
                            scriptData.put("variables", variables);
                        }
                    }
                    
                    scriptsArray.put(scriptData);
                }
                entityJson.put("scripts", scriptsArray);
            }

            entityJson.put("properties", entity.saveProperties());

            entitiesArray.put(entityJson);
        }
        json.put("entities", entitiesArray);

        return json;
    }
    
    /**
     * Saves script variable values to JSON
     */
    private JSONObject saveScriptVariables(IgnisScript script) {
        JSONObject variables = new JSONObject();
        
        try {
            Class<?> clazz = script.getClass();
            Field[] fields = clazz.getDeclaredFields();
            
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                
                field.setAccessible(true);
                Class<?> type = field.getType();
                Object value = field.get(script);
                
                if (value == null) continue;
                
                // Handle different types
                if (type == int.class || type == Integer.class ||
                    type == double.class || type == Double.class ||
                    type == float.class || type == Float.class ||
                    type == long.class || type == Long.class ||
                    type == boolean.class || type == Boolean.class ||
                    type == String.class) {
                    variables.put(field.getName(), value);
                } else if (GameObject.class.isAssignableFrom(type)) {
                    // Save GameObject references by name
                    GameObject ref = (GameObject) value;
                    JSONObject refData = new JSONObject();
                    refData.put("_type", "GameObjectRef");
                    refData.put("name", ref.getName());
                    variables.put(field.getName(), refData);
                }
            }
        } catch (Exception e) {
            System.err.println("Error saving script variables: " + e.getMessage());
        }
        
        return variables;
    }

    /**
     * Loads the scene from JSON
     */
    public static Scene fromJSON(JSONObject json, Game game) {
        String sceneName = json.getString("sceneName");
        Scene scene = new Scene(sceneName);

        JSONArray entitiesArray = json.getJSONArray("entities");
        for (int i = 0; i < entitiesArray.length(); i++) {
            JSONObject entityJson = entitiesArray.getJSONObject(i);

            String type = entityJson.getString("type");
            GameObject entity = EntityFactory.create(type);

            entity.setId(entityJson.getString("id"));
            entity.setName(entityJson.optString("name", type));
            entity.setGame(game);

            JSONObject position = entityJson.getJSONObject("position");
            entity.setX(position.getDouble("x"));
            entity.setY(position.getDouble("y"));

            if (entityJson.has("size")) {
                JSONObject size = entityJson.getJSONObject("size");
                entity.setWidth(size.getInt("width"));
                entity.setHeight(size.getInt("height"));
            }

            if (entityJson.has("sprite")) {
                entity.setSpritePath(entityJson.getString("sprite"));
            }
            
            // Load attached audio component
            if (entityJson.has("musicPath")) {
                MusicPath musicPath = MusicPath.fromJSON(entityJson.getJSONObject("musicPath"));
                entity.setMusicPath(musicPath);
            }
            
            // Load attached scripts (supports both old string format and new object format)
            if (entityJson.has("scripts")) {
                JSONArray scriptsArray = entityJson.getJSONArray("scripts");
                java.util.List<String> scriptNames = new java.util.ArrayList<>();
                
                for (int j = 0; j < scriptsArray.length(); j++) {
                    Object scriptEntry = scriptsArray.get(j);
                    if (scriptEntry instanceof String) {
                        // Old format: just string name
                        scriptNames.add((String) scriptEntry);
                    } else if (scriptEntry instanceof JSONObject) {
                        // New format: object with name and variables
                        JSONObject scriptData = (JSONObject) scriptEntry;
                        String scriptName = scriptData.getString("name");
                        scriptNames.add(scriptName);
                        if (scriptData.has("variables")) {
                            // Store for later resolution when scripts are instantiated
                            String key = entity.getId() + ":" + scriptName;
                            scene.pendingScriptVariables.put(key, scriptData.getJSONObject("variables"));
                        }
                    }
                }
                entity.setScriptNames(scriptNames);
            }

            if (entityJson.has("properties")) {
                entity.loadProperties(entityJson.getJSONObject("properties"));
            }

            scene.addEntity(entity);
            
            // If it's a camera, also register it with the game
            if (entity instanceof Camera && game != null) {
                Camera cam = (Camera) entity;
                cam.setViewport(game.getViewport());
                game.addCamera(cam);
            }
        }

        return scene;
    }
    
    /**
     * Applies pending script variables to a script instance.
     * This should be called after scripts are instantiated.
     */
    public void applyPendingScriptVariables(GameObject entity, IgnisScript script) {
        String key = entity.getId() + ":" + script.getClass().getSimpleName();
        JSONObject variables = pendingScriptVariables.get(key);
        if (variables != null) {
            loadScriptVariables(script, variables, this);
        }
    }
    
    /**
     * Checks if there are pending variables for a given entity and script
     */
    public boolean hasPendingVariables(GameObject entity, String scriptName) {
        return pendingScriptVariables.containsKey(entity.getId() + ":" + scriptName);
    }
    
    /**
     * Gets the pending variables map (used for persistence)
     */
    public Map<String, JSONObject> getPendingScriptVariables() {
        return pendingScriptVariables;
    }
    
    /**
     * Loads script variable values from JSON, resolving GameObject references
     */
    private void loadScriptVariables(IgnisScript script, JSONObject variables, Scene scene) {
        try {
            Class<?> clazz = script.getClass();
            
            for (String fieldName : variables.keySet()) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Class<?> type = field.getType();
                    Object value = variables.get(fieldName);
                    
                    if (value instanceof JSONObject) {
                        JSONObject refData = (JSONObject) value;
                        if (refData.has("_type") && "GameObjectRef".equals(refData.getString("_type"))) {
                            // Resolve GameObject reference by name
                            String refName = refData.getString("name");
                            GameObject referenced = scene.findEntityByName(refName);
                            if (referenced != null) {
                                field.set(script, referenced);
                            }
                        }
                    } else if (type == int.class || type == Integer.class) {
                        field.set(script, variables.getInt(fieldName));
                    } else if (type == double.class || type == Double.class) {
                        field.set(script, variables.getDouble(fieldName));
                    } else if (type == float.class || type == Float.class) {
                        field.set(script, (float) variables.getDouble(fieldName));
                    } else if (type == long.class || type == Long.class) {
                        field.set(script, variables.getLong(fieldName));
                    } else if (type == boolean.class || type == Boolean.class) {
                        field.set(script, variables.getBoolean(fieldName));
                    } else if (type == String.class) {
                        field.set(script, variables.getString(fieldName));
                    }
                } catch (NoSuchFieldException e) {
                    // Field no longer exists in script, skip
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading script variables: " + e.getMessage());
        }
    }
    
    /**
     * Finds an entity in this scene by name
     */
    public GameObject findEntityByName(String name) {
        if (name == null) return null;
        for (GameObject entity : entities) {
            if (name.equals(entity.getName())) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Clears all entities from the scene
     */
    public void clear() {
        entities.clear();
        cameras.clear();
        activeCamera = null;
    }

    // ==================== CAMERA MANAGEMENT ====================
    
    /**
     * Gets the active camera for this scene.
     */
    public Camera getActiveCamera() {
        return activeCamera;
    }
    
    /**
     * Sets the active camera for this scene.
     */
    public void setActiveCamera(Camera camera) {
        if (cameras.contains(camera)) {
            // Deactivate old camera
            if (activeCamera != null) {
                activeCamera.setActive(false);
            }
            // Activate new camera
            activeCamera = camera;
            camera.setActive(true);
        }
    }
    
    /**
     * Gets all cameras in this scene.
     */
    public List<Camera> getCameras() {
        return cameras;
    }

    // Getters and Setters
    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName;
    }

    public List<GameObject> getEntities() {
        return entities;
    }

    public void setEntities(List<GameObject> entities) {
        this.entities = entities;
        
        // Rebuild camera list
        cameras.clear();
        activeCamera = null;
        for (GameObject entity : entities) {
            if (entity instanceof Camera) {
                Camera cam = (Camera) entity;
                cameras.add(cam);
                if (activeCamera == null) {
                    activeCamera = cam;
                    cam.setActive(true);
                }
            }
        }
    }
}
