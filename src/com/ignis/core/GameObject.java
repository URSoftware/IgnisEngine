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

    // Transform do inicio do tick anterior, usado pela interpolacao de render
    // (Fase A do plano do motor grafico). Nao serializado. Atualizado por
    // capturePreviousTransform() no comeco de cada tick de simulacao.
    protected transient double prevX, prevY, prevRotation;

    // Propriedades visuais (Fase B do plano). Aplicadas pelo pipeline de render
    // (Game), nao pelo render() de cada forma — valem para todos os GameObjects.
    protected double opacity = 1.0;          // 0 = transparente, 1 = opaco
    protected boolean flipX = false;         // espelha horizontalmente
    protected boolean flipY = false;         // espelha verticalmente
    protected double scaleX = 1.0;           // multiplicador visual de largura
    protected double scaleY = 1.0;           // multiplicador visual de altura
    protected int zIndex = 0;                // ordem de render (maior = na frente)
    protected Game game;
    protected String spritePath;
    protected boolean visible = true; // Controls if object is rendered
    
    // Color for the object name in hierarchy (default white)
    protected Color nameColor = Color.WHITE;
    
    // Lista de scripts/componentes anexados a este objeto
    protected List<IgnisScript> scripts = new ArrayList<>();
    
    // Lista de nomes de scripts (para serialização)
    protected List<String> scriptNames = new ArrayList<>();
    
    // Collision system
    protected IgnisSampleCollisions.Collider collider = null;
    protected IgnisSampleCollisions.ColliderType colliderType = IgnisSampleCollisions.ColliderType.NONE;
    protected IgnisSampleCollisions.CollisionMode collisionMode = IgnisSampleCollisions.CollisionMode.COLLISION;
    
    // Audio component
    protected MusicPath musicPath = null;

    // Animation component (optional). Drives the sprite over time during play.
    protected com.ignis.animation.Animator animator = null;
    // Sprite shown before the animator took over, restored when play stops.
    private transient String spritePathBeforeAnimation = null;

    public GameObject(String name, Game game, double x, double y, int width, int height) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.game = game;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.prevRotation = 0.0;
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

    /**
     * Captura o transform atual como o transform "anterior", chamado pelo
     * {@link Game#tick()} no inicio de cada passo de simulacao. A interpolacao
     * de render desenha entre este valor e o transform atual, suavizando o
     * movimento em monitores com taxa &gt; 60 Hz. Ver Fase A do plano do motor.
     */
    public void capturePreviousTransform() {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevRotation = this.rotation;
    }

    public double getPrevX() { return prevX; }
    public double getPrevY() { return prevY; }
    public double getPrevRotation() { return prevRotation; }

    // ---- Propriedades visuais (Fase B) ----

    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
    }

    public boolean isFlipX() { return flipX; }
    public void setFlipX(boolean flipX) { this.flipX = flipX; }

    public boolean isFlipY() { return flipY; }
    public void setFlipY(boolean flipY) { this.flipY = flipY; }

    public double getScaleX() { return scaleX; }
    public void setScaleX(double scaleX) { this.scaleX = scaleX; }

    public double getScaleY() { return scaleY; }
    public void setScaleY(double scaleY) { this.scaleY = scaleY; }

    public int getZIndex() { return zIndex; }
    public void setZIndex(int zIndex) { this.zIndex = zIndex; }

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
    
    // ==================== SISTEMA DE ÁUDIO ====================
    
    /**
     * Obtém o componente MusicPath deste objeto.
     * @return O MusicPath ou null se não existir
     */
    public MusicPath getMusicPath() {
        return musicPath;
    }
    
    /**
     * Define o componente MusicPath deste objeto.
     * @param musicPath O MusicPath a ser definido
     */
    public void setMusicPath(MusicPath musicPath) {
        this.musicPath = musicPath;
    }

    // ==================== ANIMATION COMPONENT ====================

    public com.ignis.animation.Animator getAnimator() {
        return animator;
    }

    public void setAnimator(com.ignis.animation.Animator animator) {
        this.animator = animator;
    }

    /** Returns the existing animator or creates and attaches a new one. */
    public com.ignis.animation.Animator getOrCreateAnimator() {
        if (animator == null) {
            animator = new com.ignis.animation.Animator();
        }
        return animator;
    }

    /**
     * Advances the animator one fixed step (called every frame while the world
     * is playing) and applies the current frame to this object's sprite. The
     * sprite present before animation started is captured so it can be restored
     * when the world stops, keeping the editor state intact.
     */
    public void tickAnimator(double dt) {
        if (animator == null) {
            return;
        }
        animator.update(dt);
        String frame = animator.getCurrentSpritePath();
        if (frame != null && !frame.isEmpty()) {
            if (spritePathBeforeAnimation == null) {
                spritePathBeforeAnimation = spritePath;
            }
            if (!frame.equals(spritePath)) {
                setSpritePath(frame);
            }
        }
    }

    /** Resets the animator and restores the pre-animation sprite. */
    public void resetAnimator() {
        if (animator == null) {
            return;
        }
        animator.reset();
        if (spritePathBeforeAnimation != null) {
            setSpritePath(spritePathBeforeAnimation);
            spritePathBeforeAnimation = null;
        }
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
        // Remove script instance if it exists
        IgnisScript toRemove = null;
        for (IgnisScript script : scripts) {
            if (script.getScriptName().equals(scriptName)) {
                toRemove = script;
                break;
            }
        }
        if (toRemove != null) {
            scripts.remove(toRemove);
        }
        
        // Always remove from scriptNames list (even if instance doesn't exist)
        // This handles cases where the script file was deleted but the name remains
        scriptNames.remove(scriptName);
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
    
    // ==================== COLLISION SYSTEM ====================
    
    /**
     * Gets the current collider attached to this object
     */
    public IgnisSampleCollisions.Collider getCollider() {
        return collider;
    }
    
    /**
     * Gets the collider type
     */
    public IgnisSampleCollisions.ColliderType getColliderType() {
        return colliderType;
    }
    
    /**
     * Sets the collider type and creates the appropriate collider
     */
    public void setColliderType(IgnisSampleCollisions.ColliderType type) {
        this.colliderType = type;
        
        // Remove old collider from manager if exists
        if (collider != null && game != null && game.getCollisionManager() != null) {
            game.getCollisionManager().removeCollider(collider);
        }
        
        // Create new collider based on type
        switch (type) {
            case AABB:
                collider = new IgnisSampleCollisions.AABBCollider(this);
                break;
            case CIRCLE:
                collider = new IgnisSampleCollisions.CircleCollider(this);
                break;
            case POLYGON:
                collider = new IgnisSampleCollisions.PolygonCollider(this);
                break;
            case NONE:
            default:
                collider = null;
                break;
        }
        
        // Set collision mode
        if (collider != null) {
            collider.setMode(collisionMode);
            
            // Register with collision manager
            if (game != null && game.getCollisionManager() != null) {
                game.getCollisionManager().addCollider(collider);
            }
        }
    }
    
    /**
     * Gets the collision mode (TRIGGER or COLLISION)
     */
    public IgnisSampleCollisions.CollisionMode getCollisionMode() {
        return collisionMode;
    }
    
    /**
     * Sets the collision mode
     */
    public void setCollisionMode(IgnisSampleCollisions.CollisionMode mode) {
        this.collisionMode = mode;
        if (collider != null) {
            collider.setMode(mode);
        }
    }
    
    /**
     * Checks if this object has a collider
     */
    public boolean hasCollider() {
        return collider != null && colliderType != IgnisSampleCollisions.ColliderType.NONE;
    }
    
    /**
     * Enables or disables the collider
     */
    public void setColliderEnabled(boolean enabled) {
        if (collider != null) {
            collider.setEnabled(enabled);
        }
    }
    
    /**
     * Sets whether to use Continuous Collision Detection
     */
    public void setUseCCD(boolean use) {
        if (collider != null) {
            collider.setUseCCD(use);
        }
    }
    
    /**
     * Serializes collider properties to JSON
     */
    public JSONObject saveColliderProperties() {
        JSONObject props = new JSONObject();
        props.put("colliderType", colliderType.name());
        props.put("collisionMode", collisionMode.name());
        
        if (collider != null) {
            props.put("enabled", collider.isEnabled());
            props.put("offsetX", collider.getOffsetX());
            props.put("offsetY", collider.getOffsetY());
            props.put("layer", collider.getLayer());
            props.put("collisionMask", collider.getCollisionMask());
            props.put("useCCD", collider.useCCD());
            
            if (collider instanceof IgnisSampleCollisions.AABBCollider) {
                IgnisSampleCollisions.AABBCollider aabb = (IgnisSampleCollisions.AABBCollider) collider;
                props.put("width", aabb.getWidth());
                props.put("height", aabb.getHeight());
            } else if (collider instanceof IgnisSampleCollisions.CircleCollider) {
                IgnisSampleCollisions.CircleCollider circle = (IgnisSampleCollisions.CircleCollider) collider;
                props.put("radius", circle.getRadius());
            } else if (collider instanceof IgnisSampleCollisions.PolygonCollider) {
                // For polygon, we save vertex count and shape type
                IgnisSampleCollisions.PolygonCollider poly = (IgnisSampleCollisions.PolygonCollider) collider;
                props.put("vertexCount", poly.getVertexCount());
            }
        }
        
        return props;
    }
    
    /**
     * Loads collider properties from JSON
     */
    public void loadColliderProperties(JSONObject props) {
        if (props == null) return;
        
        // Load collider type
        if (props.has("colliderType")) {
            try {
                colliderType = IgnisSampleCollisions.ColliderType.valueOf(props.getString("colliderType"));
            } catch (IllegalArgumentException e) {
                colliderType = IgnisSampleCollisions.ColliderType.NONE;
            }
        }
        
        // Load collision mode
        if (props.has("collisionMode")) {
            try {
                collisionMode = IgnisSampleCollisions.CollisionMode.valueOf(props.getString("collisionMode"));
            } catch (IllegalArgumentException e) {
                collisionMode = IgnisSampleCollisions.CollisionMode.COLLISION;
            }
        }
        
        // Create the collider
        setColliderType(colliderType);
        
        // Apply additional properties
        if (collider != null) {
            if (props.has("enabled")) collider.setEnabled(props.getBoolean("enabled"));
            if (props.has("offsetX") && props.has("offsetY")) {
                collider.setOffset(props.getDouble("offsetX"), props.getDouble("offsetY"));
            }
            if (props.has("layer")) collider.setLayer(props.getInt("layer"));
            if (props.has("collisionMask")) collider.setCollisionMask(props.getInt("collisionMask"));
            if (props.has("useCCD")) collider.setUseCCD(props.getBoolean("useCCD"));
            
            // Type-specific properties
            if (collider instanceof IgnisSampleCollisions.AABBCollider && props.has("width") && props.has("height")) {
                ((IgnisSampleCollisions.AABBCollider) collider).setSize(
                    props.getDouble("width"), props.getDouble("height"));
            } else if (collider instanceof IgnisSampleCollisions.CircleCollider && props.has("radius")) {
                ((IgnisSampleCollisions.CircleCollider) collider).setRadius(props.getDouble("radius"));
            }
        }
    }
}
