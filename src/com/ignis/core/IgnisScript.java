package com.ignis.core;

/**
 * Classe base para scripts do motor Ignis.
 * Estenda esta classe para criar comportamentos personalizados.
 */
public abstract class IgnisScript {

    // Referências do contexto
    protected GameObject gameObject;
    protected Transform transform;
    protected Game game;
    
    // Estado interno
    private boolean started = false;
    private boolean enabled = true;
    private String scriptName;

    /**
     * Classe interna para acesso à transformação do objeto.
     */
    public class Transform {
        public double x, y;
        public double rotation;
        public int width, height;

        private void sync() {
            if (gameObject != null) {
                x = gameObject.getX();
                y = gameObject.getY();
                rotation = gameObject.getRotation();
                width = gameObject.getWidth();
                height = gameObject.getHeight();
            }
        }

        private void apply() {
            if (gameObject != null) {
                gameObject.setX(x);
                gameObject.setY(y);
                gameObject.setRotation(rotation);
                gameObject.setWidth(width);
                gameObject.setHeight(height);
            }
        }
    }

    public IgnisScript() {
        this.transform = new Transform();
        this.scriptName = getClass().getSimpleName();
    }

    /**
     * Inicializa o script com o GameObject pai.
     */
    public void init(GameObject gameObject, Game game) {
        this.gameObject = gameObject;
        this.game = game;
        this.transform.sync();
    }

    // ==================== MÉTODOS PRINCIPAIS ====================

    /**
     * Chamado uma vez quando o jogo inicia.
     */
    public void start() {}

    /**
     * Chamado a cada frame enquanto o jogo roda.
     */
    public void tick() {}

    /**
     * Chamado quando colide com outro objeto.
     */
    public void onCollision(GameObject other) {}

    // ==================== MÉTODOS INTERNOS ====================

    public final void internalTick() {
        if (!enabled) return;
        
        transform.sync();
        
        if (!started) {
            start();
            started = true;
        }
        
        tick();
        
        transform.apply();
    }

    public final void reset() {
        started = false;
    }

    // ==================== GETTERS/SETTERS ====================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getScriptName() { return scriptName; }
    public GameObject getGameObject() { return gameObject; }
    public Game getGame() { return game; }

    // ==================== MÉTODOS UTILITÁRIOS ====================

    /**
     * Move o objeto na direção especificada.
     */
    protected void move(double dx, double dy) {
        transform.x += dx;
        transform.y += dy;
    }

    /**
     * Move o objeto em direção a um ponto.
     */
    protected void moveTowards(double targetX, double targetY, double speed) {
        double dx = targetX - transform.x;
        double dy = targetY - transform.y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance > speed) {
            transform.x += (dx / distance) * speed;
            transform.y += (dy / distance) * speed;
        } else {
            transform.x = targetX;
            transform.y = targetY;
        }
    }

    /**
     * Rotaciona o objeto.
     */
    protected void rotate(double degrees) {
        transform.rotation += degrees;
    }

    /**
     * Faz o objeto olhar para um ponto.
     */
    protected void lookAt(double targetX, double targetY) {
        double dx = targetX - transform.x;
        double dy = targetY - transform.y;
        transform.rotation = Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Calcula a distância até outro objeto.
     */
    protected double distanceTo(GameObject other) {
        double dx = (other.getX() + other.getWidth() / 2.0) - (transform.x + transform.width / 2.0);
        double dy = (other.getY() + other.getHeight() / 2.0) - (transform.y + transform.height / 2.0);
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Verifica colisão com outro objeto (AABB).
     */
    protected boolean isColliding(GameObject other) {
        return transform.x < other.getX() + other.getWidth() &&
               transform.x + transform.width > other.getX() &&
               transform.y < other.getY() + other.getHeight() &&
               transform.y + transform.height > other.getY();
    }

    /**
     * Encontra um objeto pelo nome.
     */
    protected GameObject findObject(String name) {
        if (game == null) return null;
        for (GameObject obj : game.getEntities()) {
            if (obj.getName().equals(name)) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Encontra objetos por tipo.
     */
    protected java.util.List<GameObject> findObjectsByType(String type) {
        java.util.List<GameObject> result = new java.util.ArrayList<>();
        if (game == null) return result;
        for (GameObject obj : game.getEntities()) {
            if (obj.getType().equals(type)) {
                result.add(obj);
            }
        }
        return result;
    }

    /**
     * Destroi o GameObject atual.
     */
    protected void destroy() {
        if (game != null && gameObject != null) {
            game.removeEntity(gameObject);
        }
    }

    /**
     * Destroi outro GameObject.
     */
    protected void destroy(GameObject obj) {
        if (game != null && obj != null) {
            game.removeEntity(obj);
        }
    }

    /**
     * Log de debug.
     */
    protected void log(String message) {
        System.out.println("[" + scriptName + "] " + message);
    }

    // ==================== MÉTODOS DE ÁUDIO ====================

    /**
     * Reproduz um efeito sonoro.
     * @param filePath Caminho do arquivo de áudio (WAV, AIFF, AU)
     */
    protected void playSound(String filePath) {
        IgnisSoundEngine.getInstance().playSound(filePath);
    }

    /**
     * Reproduz um efeito sonoro com volume personalizado.
     * @param filePath Caminho do arquivo de áudio
     * @param volume Volume (0.0 a 1.0)
     */
    protected void playSound(String filePath, float volume) {
        IgnisSoundEngine.getInstance().playSound(filePath, volume);
    }

    /**
     * Reproduz um efeito sonoro com callback ao finalizar.
     * @param filePath Caminho do arquivo de áudio
     * @param onComplete Ação a executar quando o som terminar
     */
    protected void playSoundWithCallback(String filePath, Runnable onComplete) {
        IgnisSoundEngine.getInstance().playSoundWithCallback(filePath, onComplete);
    }

    /**
     * Para todos os efeitos sonoros.
     */
    protected void stopAllSounds() {
        IgnisSoundEngine.getInstance().stopAllSounds();
    }

    /**
     * Reproduz música de fundo em loop.
     * @param filePath Caminho do arquivo de música
     */
    protected void playMusic(String filePath) {
        IgnisSoundEngine.getInstance().playMusic(filePath);
    }

    /**
     * Reproduz música de fundo.
     * @param filePath Caminho do arquivo de música
     * @param loop Se deve repetir em loop
     */
    protected void playMusic(String filePath, boolean loop) {
        IgnisSoundEngine.getInstance().playMusic(filePath, loop);
    }

    /**
     * Pausa a música de fundo.
     */
    protected void pauseMusic() {
        IgnisSoundEngine.getInstance().pauseMusic();
    }

    /**
     * Retoma a música de fundo.
     */
    protected void resumeMusic() {
        IgnisSoundEngine.getInstance().resumeMusic();
    }

    /**
     * Para a música de fundo.
     */
    protected void stopMusic() {
        IgnisSoundEngine.getInstance().stopMusic();
    }

    /**
     * Verifica se há música tocando.
     * @return true se música está tocando
     */
    protected boolean isMusicPlaying() {
        return IgnisSoundEngine.getInstance().isMusicPlaying();
    }

    /**
     * Define o volume master (afeta tudo).
     * @param volume Volume (0.0 a 1.0)
     */
    protected void setMasterVolume(float volume) {
        IgnisSoundEngine.getInstance().setMasterVolume(volume);
    }

    /**
     * Define o volume da música.
     * @param volume Volume (0.0 a 1.0)
     */
    protected void setMusicVolume(float volume) {
        IgnisSoundEngine.getInstance().setMusicVolume(volume);
    }

    /**
     * Define o volume dos efeitos sonoros.
     * @param volume Volume (0.0 a 1.0)
     */
    protected void setSfxVolume(float volume) {
        IgnisSoundEngine.getInstance().setSfxVolume(volume);
    }
}
