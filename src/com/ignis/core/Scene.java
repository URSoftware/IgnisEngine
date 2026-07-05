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

    // Mundo da cena (limites do mapa + barreiras). Pode ser null (sem mundo).
    private World world = null;

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }
    
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

            entityJson.put("rotation", entity.getRotation());
            entityJson.put("zIndex", entity.getZIndex());
            entityJson.put("visible", entity.isVisible());

            // Serializar todos os componentes anexados
            JSONArray componentsArray = new JSONArray();
            java.util.Set<String> serializedScripts = new java.util.HashSet<>();
            for (Component comp : entity.getComponents()) {
                JSONObject compJson = new JSONObject();
                String compType = comp.getClass().getSimpleName();
                compJson.put("type", compType);
                // Serializacao GENERICA (plano item 5): todo componente persiste
                // via saveProperties() — @Serialize por padrao; componentes com
                // estado complexo (CanvasComponent) sobrescrevem o metodo.
                if (comp instanceof IgnisScript) serializedScripts.add(compType);
                compJson.put("properties", comp.saveProperties());
                componentsArray.put(compJson);
            }
            // Anexos de script SEM instancia viva (compilacao falhou ou ainda nao
            // rodou): persiste o vinculo pelo nome, com as variaveis pendentes se
            // houver — sem isto o anexo sumia do .ignis ao salvar.
            for (String scriptName : entity.getScriptNames()) {
                if (serializedScripts.contains(scriptName) || "SpriteComponent".equals(scriptName)) {
                    continue;
                }
                JSONObject compJson = new JSONObject();
                compJson.put("type", scriptName);
                JSONObject pending = pendingScriptVariables.get(entity.getId() + ":" + scriptName);
                compJson.put("properties", pending != null ? pending : new JSONObject());
                componentsArray.put(compJson);
            }
            entityJson.put("components", componentsArray);

            entitiesArray.put(entityJson);
        }
        json.put("entities", entitiesArray);

        // Mundo da cena (limites + barreiras), se houver.
        if (world != null) {
            json.put("world", world.toJSON());
        }

        return json;
    }
    
    /**
     * Saves script variable values to JSON
     */
    private JSONObject saveScriptVariables(IgnisScript script) {
        return ScriptSerializationHelper.saveScriptVariables(script);
    }

    /**
     * Instancia um componente NATIVO do motor pelo nome do tipo (reflexao em
     * com.ignis.core). Retorna null para tipos desconhecidos — tratados como
     * scripts de usuario. Novos componentes nativos passam a ser serializados
     * sem alterar a Scene (plano item 5).
     */
    private static Component instantiateNativeComponent(String typeName) {
        if (typeName == null || typeName.isEmpty() || !typeName.matches("[A-Za-z0-9_]+")) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName("com.ignis.core." + typeName);
            if (!Component.class.isAssignableFrom(clazz)) return null;
            // Scripts de usuario nao vivem em com.ignis.core; IgnisScript nativos
            // (SpriteComponent) tambem entram por aqui.
            return (Component) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
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

            entity.setRotation(entityJson.optDouble("rotation", 0.0));
            entity.setZIndex(entityJson.optInt("zIndex", 0));
            entity.setVisible(entityJson.optBoolean("visible", entityJson.optBoolean("visibleCheck", true)));

            // CARREGAMENTO DE COMPONENTES E SUPORTE A MIGRACAO RETROATIVA
            if (entityJson.has("components")) {
                JSONArray compsArray = entityJson.getJSONArray("components");
                java.util.List<String> scriptNames = new java.util.ArrayList<>();
                for (int j = 0; j < compsArray.length(); j++) {
                    JSONObject compJson = compsArray.getJSONObject(j);
                    String compType = compJson.getString("type");
                    
                    // Carga GENERICA (plano item 5): componentes nativos do motor
                    // (com.ignis.core.*) sao instanciados por reflexao e restaurados
                    // via loadProperties(); qualquer outro tipo e um script de
                    // usuario (instanciado depois, pelo ScriptManager).
                    Component nativeComp = instantiateNativeComponent(compType);
                    if (nativeComp != null) {
                        entity.addComponent(nativeComp);
                        if (compJson.has("properties")) {
                            nativeComp.loadProperties(compJson.getJSONObject("properties"),
                                    scene::getEntityById);
                        }
                    } else {
                        // É um script customizado
                        scriptNames.add(compType);
                        if (compJson.has("properties")) {
                            String key = entity.getId() + ":" + compType;
                            scene.pendingScriptVariables.put(key, compJson.getJSONObject("properties"));
                        }
                    }
                }
                entity.setScriptNames(scriptNames);
            } else {
                // Cenário Legado: Migração automática
                boolean needsSprite = entityJson.has("sprite") || 
                    type.equals("Square") || type.equals("Circle") || 
                    type.equals("Triangle") || type.equals("Star") || 
                    type.equals("Pentagon") || type.equals("MergedShape") ||
                    type.equals("Player");
                
                if (needsSprite) {
                    SpriteComponent spriteComp = new SpriteComponent();
                    if (entityJson.has("sprite")) {
                        spriteComp.setTexture(new Texture2D(entityJson.getString("sprite")));
                    } else if (type.equals("Square") || type.equals("Circle") || 
                               type.equals("Triangle") || type.equals("Star") || 
                               type.equals("Pentagon")) {
                        spriteComp.setShapeType(type);
                    }
                    
                    if (entityJson.has("properties")) {
                        JSONObject props = entityJson.getJSONObject("properties");
                        if (props.has("color")) {
                            spriteComp.setTint(new java.awt.Color(props.getInt("color")));
                        }
                    }
                    entity.addComponent(spriteComp);
                }
                
                if (entityJson.has("scripts")) {
                    JSONArray scriptsArray = entityJson.getJSONArray("scripts");
                    java.util.List<String> scriptNames = new java.util.ArrayList<>();
                    for (int j = 0; j < scriptsArray.length(); j++) {
                        Object scriptEntry = scriptsArray.get(j);
                        if (scriptEntry instanceof String) {
                            scriptNames.add((String) scriptEntry);
                        } else if (scriptEntry instanceof JSONObject) {
                            JSONObject scriptData = (JSONObject) scriptEntry;
                            String scriptName = scriptData.getString("name");
                            scriptNames.add(scriptName);
                            if (scriptData.has("variables")) {
                                String key = entity.getId() + ":" + scriptName;
                                scene.pendingScriptVariables.put(key, scriptData.getJSONObject("variables"));
                            }
                        }
                    }
                    entity.setScriptNames(scriptNames);
                }
            }

            if (entityJson.has("properties")) {
                entity.loadProperties(entityJson.getJSONObject("properties"));
            }

            scene.addEntity(entity);
            
            // Se for Camera, registra no jogo
            if (entity instanceof Camera && game != null) {
                Camera cam = (Camera) entity;
                cam.setViewport(game.getViewport());
                game.addCamera(cam);
            }
        }

        // Mundo da cena (limites + barreiras), se serializado.
        if (json.has("world")) {
            scene.setWorld(World.fromJSON(json.getJSONObject("world")));
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
        ScriptSerializationHelper.loadScriptVariables(script, variables, scene::findEntityByName);
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
