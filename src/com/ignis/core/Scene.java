package com.ignis.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a game scene.
 * Contains a list of entities (GameObjects) that will be rendered.
 */
public class Scene {

    private String sceneName;
    private List<GameObject> entities;

    public Scene(String sceneName) {
        this.sceneName = sceneName;
        this.entities = new ArrayList<>();
    }

    /**
     * Adds an entity to the scene
     */
    public void addEntity(GameObject entity) {
        entities.add(entity);
    }

    /**
     * Removes an entity from the scene
     */
    public void removeEntity(GameObject entity) {
        entities.remove(entity);
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
            
            // Save attached scripts
            if (!entity.getScriptNames().isEmpty()) {
                JSONArray scriptsArray = new JSONArray();
                for (String scriptName : entity.getScriptNames()) {
                    scriptsArray.put(scriptName);
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
            
            // Load attached scripts
            if (entityJson.has("scripts")) {
                JSONArray scriptsArray = entityJson.getJSONArray("scripts");
                java.util.List<String> scriptNames = new java.util.ArrayList<>();
                for (int j = 0; j < scriptsArray.length(); j++) {
                    scriptNames.add(scriptsArray.getString(j));
                }
                entity.setScriptNames(scriptNames);
            }

            if (entityJson.has("properties")) {
                entity.loadProperties(entityJson.getJSONObject("properties"));
            }

            scene.addEntity(entity);
        }

        return scene;
    }

    /**
     * Clears all entities from the scene
     */
    public void clear() {
        entities.clear();
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
    }
}
