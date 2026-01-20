package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public abstract class GameObject {

    protected String id;
    protected String name;
    protected double x, y;
    protected double rotation = 0.0;
    protected int width, height;
    protected Game game;
    protected String spritePath;
    protected boolean visible = true; // Controls if object is rendered
    
    // Color for the object name in hierarchy (default white)
    protected Color nameColor = Color.WHITE;
    
    // Lista de scripts/componentes anexados a este objeto
    protected List<IgnisScript> scripts = new ArrayList<>();
    
    // Lista de nomes de scripts (para serialização)
    protected List<String> scriptNames = new ArrayList<>();

    public GameObject(String name, Game game, double x, double y, int width, int height) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.game = game;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.spritePath = null;
        this.visible = true;
        this.nameColor = Color.WHITE;
    }

    // Construtor vazio para EntityFactory
    public GameObject() {
        this.id = java.util.UUID.randomUUID().toString();
        this.visible = true;
        this.nameColor = Color.WHITE;
    }

    public abstract void tick();

    public abstract void render(Graphics g);

    // Metodos para serializacao/deserializacao
    public abstract void loadProperties(JSONObject props);

    public abstract JSONObject saveProperties();

    // Getters e Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        // Normalize rotation to 0-360
        this.rotation = rotation % 360;
        if (this.rotation < 0)
            this.rotation += 360;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public String getSpritePath() {
        return spritePath;
    }

    public void setSpritePath(String spritePath) {
        this.spritePath = spritePath;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public Color getNameColor() {
        return nameColor;
    }
    
    public void setNameColor(Color nameColor) {
        this.nameColor = nameColor != null ? nameColor : Color.WHITE;
    }

    // Metodo para obter o tipo da entidade (nome da classe)
    public String getType() {
        return this.getClass().getSimpleName();
    }
    
    // ==================== SISTEMA DE SCRIPTS ====================
    
    /**
     * Adiciona um script a este GameObject
     * @param script O script a ser adicionado
     */
    public void addScript(IgnisScript script) {
        if (script != null && !scripts.contains(script)) {
            scripts.add(script);
            script.init(this, game);
            
            // Armazenar nome para serialização
            String scriptName = script.getScriptName();
            if (!scriptNames.contains(scriptName)) {
                scriptNames.add(scriptName);
            }
        }
    }
    
    /**
     * Remove um script deste GameObject
     * @param script O script a ser removido
     */
    public void removeScript(IgnisScript script) {
        if (script != null) {
            scripts.remove(script);
            scriptNames.remove(script.getScriptName());
        }
    }
    
    /**
     * Remove um script pelo nome
     * @param scriptName Nome do script
     */
    public void removeScriptByName(String scriptName) {
        IgnisScript toRemove = null;
        for (IgnisScript script : scripts) {
            if (script.getScriptName().equals(scriptName)) {
                toRemove = script;
                break;
            }
        }
        if (toRemove != null) {
            removeScript(toRemove);
        }
    }
    
    /**
     * Obtém um script pelo tipo
     * @param scriptClass Classe do script
     * @return O script, ou null se não encontrado
     */
    @SuppressWarnings("unchecked")
    public <T extends IgnisScript> T getScript(Class<T> scriptClass) {
        for (IgnisScript script : scripts) {
            if (scriptClass.isInstance(script)) {
                return (T) script;
            }
        }
        return null;
    }
    
    /**
     * Verifica se o objeto tem um script específico
     * @param scriptName Nome do script
     * @return true se o script está anexado
     */
    public boolean hasScript(String scriptName) {
        for (IgnisScript script : scripts) {
            if (script.getScriptName().equals(scriptName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Retorna todos os scripts anexados
     */
    public List<IgnisScript> getScripts() {
        return scripts;
    }
    
    /**
     * Retorna os nomes dos scripts anexados
     */
    public List<String> getScriptNames() {
        return scriptNames;
    }
    
    /**
     * Define os nomes dos scripts (para deserialização)
     */
    public void setScriptNames(List<String> names) {
        this.scriptNames = new ArrayList<>(names);
    }
    
    /**
     * Atualiza todos os scripts (chamado a cada frame quando o jogo está rodando)
     */
    public void tickScripts() {
        for (IgnisScript script : scripts) {
            script.internalTick();
        }
    }
    
    /**
     * Reseta todos os scripts (quando o jogo é parado)
     */
    public void resetScripts() {
        for (IgnisScript script : scripts) {
            script.reset();
        }
    }
    
    /**
     * Notifica scripts sobre colisão
     */
    public void notifyCollision(GameObject other) {
        for (IgnisScript script : scripts) {
            script.onCollision(other);
        }
    }
}
