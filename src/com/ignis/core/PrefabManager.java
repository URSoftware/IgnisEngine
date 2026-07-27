package com.ignis.core;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Cache do JSON ja parseado de cada prefab. Sem isso, CADA instantiatePrefab lia
    // o .prefab.json do disco e reparseava o JSON — num jogo com spawner (orbes,
    // projeteis) isso e I/O + parse centenas de vezes por segundo, e foi um dos
    // fatores da pressao de memoria que derrubou o editor em 15/07/2026.
    // A desserializacao apenas LE o JSON (nenhum loadProperties muta o objeto
    // recebido), entao a mesma instancia pode ser reusada com seguranca.
    private final Map<String, CachedPrefab> prefabCache = new HashMap<>();

    /** JSON de um prefab + o mtime do arquivo de origem que o produziu. */
    private static final class CachedPrefab {
        final JSONObject json;
        final long lastModified;

        CachedPrefab(JSONObject json, long lastModified) {
            this.json = json;
            this.lastModified = lastModified;
        }
    }

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
            // Salvar duas vezes dentro do mesmo tick de mtime (resolucao do FS pode
            // ser de 1s) deixaria o cache servindo a versao antiga: invalida na mao.
            invalidatePrefabCache(prefabName);
            propagateChanges(prefabName);

            com.ignis.core.IgnisLogger.info("Prefab salvo com sucesso: " + prefabFile.getName());
            return true;
            
        } catch (Exception e) {
            com.ignis.core.IgnisLogger.error("Falha ao salvar prefab " + prefabName + ": " + e.getMessage());
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
            IgnisLogger.error("Prefab not found: " + prefabName);
            return null;
        }
        
        try {
            JSONObject prefabJson = prefabJson(prefabFile);

            GameObject obj = deserializeGameObject(prefabJson, x, y);
            if (obj != null) {
                obj.setPrefabLink(new PrefabLink(prefabName));
                if (x >= 0) obj.getPrefabLink().setOverride("x");
                if (y >= 0) obj.getPrefabLink().setOverride("y");
            }
            return obj;

        } catch (Exception e) {
            IgnisLogger.error("Failed to instantiate prefab: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Propaga alterações do prefab para todas as instâncias vivas no jogo, preservando overrides.
     */
    public void propagateChanges(String prefabName) {
        if (game == null || game.getEntities() == null) return;
        File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
        if (!prefabFile.exists()) return;

        try {
            JSONObject prefabJson = prefabJson(prefabFile);
            JSONObject transform = prefabJson.optJSONObject("transform");

            for (GameObject obj : game.getEntities()) {
                if (obj.isPrefabInstance() && prefabName.equals(obj.getPrefabLink().getPrefabName())) {
                    PrefabLink link = obj.getPrefabLink();

                    if (transform != null) {
                        if (!link.isOverridden("x")) obj.x = transform.optDouble("x", obj.getX());
                        if (!link.isOverridden("y")) obj.y = transform.optDouble("y", obj.getY());
                        if (!link.isOverridden("rotation")) obj.setRotation(transform.optDouble("rotation", 0));
                        if (!link.isOverridden("width")) obj.setWidth(transform.optInt("width", obj.getWidth()));
                        if (!link.isOverridden("height")) obj.setHeight(transform.optInt("height", obj.getHeight()));
                    }

                    if (prefabJson.has("spritePath") && !link.isOverridden("spritePath")) {
                        obj.setSpritePath(prefabJson.getString("spritePath"));
                    }

                    if (prefabJson.has("tag") && !link.isOverridden("tag")) {
                        obj.setTag(prefabJson.getString("tag"));
                    }
                    if (prefabJson.has("layer") && !link.isOverridden("layer")) {
                        obj.setLayer(prefabJson.getString("layer"));
                    }
                }
            }
        } catch (Exception e) {
            IgnisLogger.error("Failed to propagate prefab changes: " + e.getMessage());
        }
    }

    /**
     * Propaga as alterações da instância para o arquivo do prefab e limpa os overrides.
     */
    public boolean applyOverridesToPrefab(GameObject instance) {
        if (instance == null || !instance.isPrefabInstance()) return false;
        String prefabName = instance.getPrefabLink().getPrefabName();
        boolean saved = savePrefab(instance, prefabName);
        if (saved) {
            instance.getPrefabLink().clearAllOverrides();
            propagateChanges(prefabName);
        }
        return saved;
    }

    /**
     * Reverte uma instância inteira para o estado base do prefab.
     */
    public boolean revertInstanceToPrefab(GameObject instance) {
        if (instance == null || !instance.isPrefabInstance()) return false;
        String prefabName = instance.getPrefabLink().getPrefabName();
        File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
        if (!prefabFile.exists()) return false;

        try {
            JSONObject json = prefabJson(prefabFile);
            JSONObject transform = json.optJSONObject("transform");

            if (transform != null) {
                instance.setX(transform.optDouble("x", 0));
                instance.setY(transform.optDouble("y", 0));
                instance.setRotation(transform.optDouble("rotation", 0));
                instance.setWidth(transform.optInt("width", 32));
                instance.setHeight(transform.optInt("height", 32));
            }
            if (json.has("spritePath")) {
                instance.setSpritePath(json.getString("spritePath"));
            }
            if (json.has("tag")) instance.setTag(json.getString("tag"));
            if (json.has("layer")) instance.setLayer(json.getString("layer"));

            instance.getPrefabLink().clearAllOverrides();
            return true;
        } catch (Exception e) {
            IgnisLogger.error("Failed to revert prefab instance: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reverte uma única propriedade sobreescrita de uma instância de prefab.
     */
    public boolean revertProperty(GameObject instance, String propertyName) {
        if (instance == null || !instance.isPrefabInstance() || propertyName == null) return false;
        String prefabName = instance.getPrefabLink().getPrefabName();
        File prefabFile = new File(prefabsFolder, prefabName + ".prefab.json");
        if (!prefabFile.exists()) return false;

        try {
            JSONObject json = prefabJson(prefabFile);
            JSONObject transform = json.optJSONObject("transform");

            switch (propertyName) {
                case "x":
                    if (transform != null) instance.setX(transform.optDouble("x", 0));
                    break;
                case "y":
                    if (transform != null) instance.setY(transform.optDouble("y", 0));
                    break;
                case "rotation":
                    if (transform != null) instance.setRotation(transform.optDouble("rotation", 0));
                    break;
                case "spritePath":
                    if (json.has("spritePath")) instance.setSpritePath(json.getString("spritePath"));
                    break;
                case "tag":
                    if (json.has("tag")) instance.setTag(json.getString("tag"));
                    break;
                case "layer":
                    if (json.has("layer")) instance.setLayer(json.getString("layer"));
                    break;
            }
            instance.getPrefabLink().removeOverride(propertyName);
            return true;
        } catch (Exception e) {
            IgnisLogger.error("Failed to revert property: " + e.getMessage());
            return false;
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
                            // Via addComponent: instancia entra em components e e
                            // serializada pela Scene (getScripts().add() perdia o anexo).
                            object.addComponent(instance);
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
            invalidatePrefabCache(prefabName);
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
    /**
     * JSON do prefab, do cache quando o arquivo nao mudou desde a ultima leitura.
     *
     * <p>A chave de validade e o {@code lastModified} do arquivo: editar o prefab no
     * editor (ou fora dele) muda o mtime e a proxima instanciacao reparseia sozinha —
     * o cache nao "congela" o prefab.</p>
     */
    private JSONObject prefabJson(File prefabFile) throws IOException {
        String key = prefabFile.getPath();
        long stamp = prefabFile.lastModified();
        CachedPrefab cached = prefabCache.get(key);
        if (cached != null && cached.lastModified == stamp) {
            return cached.json;
        }
        JSONObject parsed = new JSONObject(readFile(prefabFile));
        prefabCache.put(key, new CachedPrefab(parsed, stamp));
        return parsed;
    }

    /** Invalida o cache de um prefab (salvar/apagar). */
    private void invalidatePrefabCache(String prefabName) {
        prefabCache.remove(new File(prefabsFolder, prefabName + ".prefab.json").getPath());
    }

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
