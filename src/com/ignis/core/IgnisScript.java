package com.ignis.core;

import com.ignis.core.ui.*;
import java.awt.Color;

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
    
    // ==================== MÉTODOS DE CONVENIÊNCIA ====================
    
    /**
     * Obtém o GameObject dono deste script.
     * @return O GameObject associado a este script
     */
    protected GameObject getOwner() {
        return gameObject;
    }
    
    /**
     * Obtém a posição X do objeto.
     * @return Posição X
     */
    protected double getX() {
        return transform.x;
    }
    
    /**
     * Obtém a posição Y do objeto.
     * @return Posição Y
     */
    protected double getY() {
        return transform.y;
    }
    
    /**
     * Define a posição do objeto.
     * @param x Nova posição X
     * @param y Nova posição Y
     */
    protected void setPosition(double x, double y) {
        transform.x = x;
        transform.y = y;
    }
    
    /**
     * Obtém a rotação do objeto em graus.
     * @return Rotação em graus
     */
    protected double getRotation() {
        return transform.rotation;
    }
    
    /**
     * Define a rotação do objeto.
     * @param degrees Rotação em graus
     */
    protected void setRotation(double degrees) {
        transform.rotation = degrees;
    }
    
    /**
     * Obtém a largura do objeto.
     * @return Largura
     */
    protected int getWidth() {
        return transform.width;
    }
    
    /**
     * Obtém a altura do objeto.
     * @return Altura
     */
    protected int getHeight() {
        return transform.height;
    }
    
    /**
     * Obtém o delta time (tempo entre frames).
     * Para 60 FPS fixo, retorna aproximadamente 0.0167 segundos.
     * @return Delta time em segundos
     */
    protected double getDeltaTime() {
        return 1.0 / 60.0; // 60 FPS fixo
    }
    
    /**
     * Imprime uma mensagem no console.
     * @param message Mensagem a imprimir
     */
    protected void println(String message) {
        com.ignis.core.IgnisLogger.info("[" + scriptName + "] " + message);
    }
    
    /**
     * Imprime uma mensagem no console sem prefixo.
     * @param message Mensagem a imprimir
     */
    protected void print(String message) {
        com.ignis.core.IgnisLogger.info(message);
    }
    
    // ==================== MÉTODOS DE INPUT ====================
    
    /**
     * Verifica se uma tecla está pressionada.
     * @param keyCode Código da tecla (use KeyEvent.VK_*)
     * @return true se a tecla está pressionada
     */
    protected boolean isKeyPressed(int keyCode) {
        return Input.isKeyPressed(keyCode);
    }
    
    /**
     * Verifica se uma tecla está pressionada (por nome).
     * @param keyName Nome da tecla ("W", "A", "S", "D", "SPACE", "SHIFT", "UP", "DOWN", etc.)
     * @return true se a tecla está pressionada
     */
    protected boolean isKeyPressed(String keyName) {
        return Input.isKeyPressed(keyNameToCode(keyName));
    }
    
    /**
     * Verifica se uma tecla acabou de ser pressionada neste frame.
     * @param keyCode Código da tecla (use KeyEvent.VK_*)
     * @return true se a tecla acabou de ser pressionada
     */
    protected boolean isKeyJustPressed(int keyCode) {
        return Input.isKeyJustPressed(keyCode);
    }
    
    /**
     * Verifica se uma tecla acabou de ser pressionada (por nome).
     * @param keyName Nome da tecla
     * @return true se a tecla acabou de ser pressionada
     */
    protected boolean isKeyJustPressed(String keyName) {
        return Input.isKeyJustPressed(keyNameToCode(keyName));
    }
    
    /**
     * Verifica se uma tecla acabou de ser solta neste frame.
     * @param keyCode Código da tecla
     * @return true se a tecla acabou de ser solta
     */
    protected boolean isKeyJustReleased(int keyCode) {
        return Input.isKeyJustReleased(keyCode);
    }
    
    /**
     * Verifica se uma tecla acabou de ser solta (por nome).
     * @param keyName Nome da tecla
     * @return true se a tecla acabou de ser solta
     */
    protected boolean isKeyJustReleased(String keyName) {
        return Input.isKeyJustReleased(keyNameToCode(keyName));
    }
    
    /**
     * Obtém o eixo horizontal (-1, 0, 1).
     * Baseado nas teclas A/D ou setas esquerda/direita.
     * @return -1 = esquerda, 0 = nenhum, 1 = direita
     */
    protected int getHorizontalAxis() {
        return Input.getHorizontalAxis();
    }
    
    /**
     * Obtém o eixo vertical (-1, 0, 1).
     * Baseado nas teclas W/S ou setas cima/baixo.
     * @return -1 = cima, 0 = nenhum, 1 = baixo
     */
    protected int getVerticalAxis() {
        return Input.getVerticalAxis();
    }
    
    /**
     * Obtém a posição X do mouse na tela.
     * @return Posição X do mouse
     */
    protected int getMouseX() {
        return Input.getMouseX();
    }
    
    /**
     * Obtém a posição Y do mouse na tela.
     * @return Posição Y do mouse
     */
    protected int getMouseY() {
        return Input.getMouseY();
    }
    
    /**
     * Verifica se o botão esquerdo do mouse está pressionado.
     * @return true se pressionado
     */
    protected boolean isMouseLeftPressed() {
        return Input.isMouseLeftPressed();
    }
    
    /**
     * Verifica se o botão direito do mouse está pressionado.
     * @return true se pressionado
     */
    protected boolean isMouseRightPressed() {
        return Input.isMouseRightPressed();
    }
    
    /**
     * Converte nome de tecla para código KeyEvent.
     * @param keyName Nome da tecla
     * @return Código da tecla
     */
    private int keyNameToCode(String keyName) {
        if (keyName == null) return 0;
        
        switch (keyName.toUpperCase()) {
            // Letras
            case "A": return java.awt.event.KeyEvent.VK_A;
            case "B": return java.awt.event.KeyEvent.VK_B;
            case "C": return java.awt.event.KeyEvent.VK_C;
            case "D": return java.awt.event.KeyEvent.VK_D;
            case "E": return java.awt.event.KeyEvent.VK_E;
            case "F": return java.awt.event.KeyEvent.VK_F;
            case "G": return java.awt.event.KeyEvent.VK_G;
            case "H": return java.awt.event.KeyEvent.VK_H;
            case "I": return java.awt.event.KeyEvent.VK_I;
            case "J": return java.awt.event.KeyEvent.VK_J;
            case "K": return java.awt.event.KeyEvent.VK_K;
            case "L": return java.awt.event.KeyEvent.VK_L;
            case "M": return java.awt.event.KeyEvent.VK_M;
            case "N": return java.awt.event.KeyEvent.VK_N;
            case "O": return java.awt.event.KeyEvent.VK_O;
            case "P": return java.awt.event.KeyEvent.VK_P;
            case "Q": return java.awt.event.KeyEvent.VK_Q;
            case "R": return java.awt.event.KeyEvent.VK_R;
            case "S": return java.awt.event.KeyEvent.VK_S;
            case "T": return java.awt.event.KeyEvent.VK_T;
            case "U": return java.awt.event.KeyEvent.VK_U;
            case "V": return java.awt.event.KeyEvent.VK_V;
            case "W": return java.awt.event.KeyEvent.VK_W;
            case "X": return java.awt.event.KeyEvent.VK_X;
            case "Y": return java.awt.event.KeyEvent.VK_Y;
            case "Z": return java.awt.event.KeyEvent.VK_Z;
            
            // Números
            case "0": return java.awt.event.KeyEvent.VK_0;
            case "1": return java.awt.event.KeyEvent.VK_1;
            case "2": return java.awt.event.KeyEvent.VK_2;
            case "3": return java.awt.event.KeyEvent.VK_3;
            case "4": return java.awt.event.KeyEvent.VK_4;
            case "5": return java.awt.event.KeyEvent.VK_5;
            case "6": return java.awt.event.KeyEvent.VK_6;
            case "7": return java.awt.event.KeyEvent.VK_7;
            case "8": return java.awt.event.KeyEvent.VK_8;
            case "9": return java.awt.event.KeyEvent.VK_9;
            
            // Setas
            case "UP": return java.awt.event.KeyEvent.VK_UP;
            case "DOWN": return java.awt.event.KeyEvent.VK_DOWN;
            case "LEFT": return java.awt.event.KeyEvent.VK_LEFT;
            case "RIGHT": return java.awt.event.KeyEvent.VK_RIGHT;
            
            // Teclas especiais
            case "SPACE": return java.awt.event.KeyEvent.VK_SPACE;
            case "ENTER": return java.awt.event.KeyEvent.VK_ENTER;
            case "ESCAPE": case "ESC": return java.awt.event.KeyEvent.VK_ESCAPE;
            case "SHIFT": return java.awt.event.KeyEvent.VK_SHIFT;
            case "CONTROL": case "CTRL": return java.awt.event.KeyEvent.VK_CONTROL;
            case "ALT": return java.awt.event.KeyEvent.VK_ALT;
            case "TAB": return java.awt.event.KeyEvent.VK_TAB;
            case "BACKSPACE": return java.awt.event.KeyEvent.VK_BACK_SPACE;
            case "DELETE": return java.awt.event.KeyEvent.VK_DELETE;
            
            // Teclas de função
            case "F1": return java.awt.event.KeyEvent.VK_F1;
            case "F2": return java.awt.event.KeyEvent.VK_F2;
            case "F3": return java.awt.event.KeyEvent.VK_F3;
            case "F4": return java.awt.event.KeyEvent.VK_F4;
            case "F5": return java.awt.event.KeyEvent.VK_F5;
            case "F6": return java.awt.event.KeyEvent.VK_F6;
            case "F7": return java.awt.event.KeyEvent.VK_F7;
            case "F8": return java.awt.event.KeyEvent.VK_F8;
            case "F9": return java.awt.event.KeyEvent.VK_F9;
            case "F10": return java.awt.event.KeyEvent.VK_F10;
            case "F11": return java.awt.event.KeyEvent.VK_F11;
            case "F12": return java.awt.event.KeyEvent.VK_F12;
            
            default: return 0;
        }
    }

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
    
    // ==================== MÉTODOS DE TRANSFORMSPACE ====================
    
    /**
     * Move o objeto usando o espaço de coordenadas especificado.
     * 
     * @param dx Movimento no eixo X
     * @param dy Movimento no eixo Y
     * @param space Espaço de coordenadas (WORLD ou LOCAL)
     * 
     * Em WORLD: dx move para direita, dy move para baixo
     * Em LOCAL: dx move para frente (baseado na rotação), dy move para a esquerda
     */
    protected void move(double dx, double dy, TransformSpace space) {
        if (space == null || space == TransformSpace.WORLD) {
            transform.x += dx;
            transform.y += dy;
        } else {
            // Local space - rotaciona o vetor de movimento pela rotação do objeto
            double rad = Math.toRadians(transform.rotation);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            
            double worldDx = dx * cos - dy * sin;
            double worldDy = dx * sin + dy * cos;
            
            transform.x += worldDx;
            transform.y += worldDy;
        }
    }
    
    /**
     * Move o objeto para frente na direção que está olhando.
     * @param amount Distância a mover
     */
    protected void moveForward(double amount) {
        move(amount, 0, TransformSpace.LOCAL);
    }
    
    /**
     * Move o objeto para trás (oposto à direção que está olhando).
     * @param amount Distância a mover
     */
    protected void moveBackward(double amount) {
        move(-amount, 0, TransformSpace.LOCAL);
    }
    
    /**
     * Move o objeto para a direita (perpendicular à direção que está olhando).
     * @param amount Distância a mover
     */
    protected void strafeRight(double amount) {
        move(0, amount, TransformSpace.LOCAL);
    }
    
    /**
     * Move o objeto para a esquerda (perpendicular à direção que está olhando).
     * @param amount Distância a mover
     */
    protected void strafeLeft(double amount) {
        move(0, -amount, TransformSpace.LOCAL);
    }
    
    /**
     * Obtém a direção para frente baseada na rotação atual.
     * @return Array [dirX, dirY] com direção normalizada
     */
    protected double[] getForwardDirection() {
        double rad = Math.toRadians(transform.rotation);
        return new double[] { Math.cos(rad), Math.sin(rad) };
    }
    
    /**
     * Obtém a direção para a direita (perpendicular à frente).
     * @return Array [dirX, dirY] com direção normalizada
     */
    protected double[] getRightDirection() {
        double rad = Math.toRadians(transform.rotation + 90);
        return new double[] { Math.cos(rad), Math.sin(rad) };
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

    // ==================== MÉTODOS DE CÂMERA ====================

    /**
     * Obtém a câmera principal do jogo.
     * @return A câmera principal, ou null se não existir
     */
    protected Camera getCamera() {
        if (game == null) return null;
        return game.getMainCamera();
    }

    /**
     * Obtém a posição X da câmera.
     * @return Posição X da câmera no mundo
     */
    protected double getCameraX() {
        Camera cam = getCamera();
        return cam != null ? cam.getX() : 0;
    }

    /**
     * Obtém a posição Y da câmera.
     * @return Posição Y da câmera no mundo
     */
    protected double getCameraY() {
        Camera cam = getCamera();
        return cam != null ? cam.getY() : 0;
    }

    /**
     * Define a posição da câmera.
     * @param x Posição X no mundo
     * @param y Posição Y no mundo
     */
    protected void setCameraPosition(double x, double y) {
        Camera cam = getCamera();
        if (cam != null) {
            cam.setPosition(x, y);
        }
    }

    /**
     * Move a câmera por um delta.
     * @param dx Movimento no eixo X
     * @param dy Movimento no eixo Y
     */
    protected void moveCamera(double dx, double dy) {
        Camera cam = getCamera();
        if (cam != null) {
            cam.translate(dx, dy);
        }
    }

    /**
     * Faz a câmera seguir este objeto.
     * Chame este método no tick() para a câmera seguir o objeto suavemente.
     */
    protected void cameraFollowThis() {
        Camera cam = getCamera();
        if (cam != null) {
            double centerX = transform.x + transform.width / 2.0;
            double centerY = transform.y + transform.height / 2.0;
            cam.setPosition(centerX, centerY);
        }
    }

    /**
     * Faz a câmera seguir este objeto com suavização (lerp).
     * @param smoothness Valor de suavização (0.0 a 1.0). Valores menores = mais suave
     */
    protected void cameraFollowThis(double smoothness) {
        Camera cam = getCamera();
        if (cam != null) {
            double centerX = transform.x + transform.width / 2.0;
            double centerY = transform.y + transform.height / 2.0;
            double currentX = cam.getX();
            double currentY = cam.getY();
            double newX = currentX + (centerX - currentX) * smoothness;
            double newY = currentY + (centerY - currentY) * smoothness;
            cam.setPosition(newX, newY);
        }
    }

    /**
     * Faz a câmera seguir um objeto específico.
     * @param target O objeto a ser seguido
     */
    protected void cameraFollow(GameObject target) {
        if (target == null) return;
        Camera cam = getCamera();
        if (cam != null) {
            double centerX = target.getX() + target.getWidth() / 2.0;
            double centerY = target.getY() + target.getHeight() / 2.0;
            cam.setPosition(centerX, centerY);
        }
    }

    /**
     * Faz a câmera seguir um objeto específico com suavização.
     * @param target O objeto a ser seguido
     * @param smoothness Valor de suavização (0.0 a 1.0)
     */
    protected void cameraFollow(GameObject target, double smoothness) {
        if (target == null) return;
        Camera cam = getCamera();
        if (cam != null) {
            double centerX = target.getX() + target.getWidth() / 2.0;
            double centerY = target.getY() + target.getHeight() / 2.0;
            double currentX = cam.getX();
            double currentY = cam.getY();
            double newX = currentX + (centerX - currentX) * smoothness;
            double newY = currentY + (centerY - currentY) * smoothness;
            cam.setPosition(newX, newY);
        }
    }

    /**
     * Obtém o nível de zoom atual da câmera.
     * @return Nível de zoom (1.0 = normal, >1 = ampliado, <1 = afastado)
     */
    protected double getCameraZoom() {
        Camera cam = getCamera();
        return cam != null ? cam.getZoom() : 1.0;
    }

    /**
     * Define o nível de zoom da câmera.
     * @param zoom Nível de zoom (0.1 a 10.0)
     */
    protected void setCameraZoom(double zoom) {
        Camera cam = getCamera();
        if (cam != null) {
            cam.setZoom(zoom);
        }
    }

    /**
     * Obtém a rotação da câmera em graus.
     * @return Rotação da câmera
     */
    protected double getCameraRotation() {
        Camera cam = getCamera();
        return cam != null ? cam.getRotation() : 0;
    }

    /**
     * Define a rotação da câmera.
     * @param rotation Rotação em graus
     */
    protected void setCameraRotation(double rotation) {
        Camera cam = getCamera();
        if (cam != null) {
            cam.setRotation(rotation);
        }
    }

    /**
     * Aplica um efeito de shake (tremor) na câmera.
     * @param intensity Intensidade do tremor em pixels
     */
    protected void cameraShake(double intensity) {
        Camera cam = getCamera();
        if (cam != null) {
            double shakeX = (Math.random() - 0.5) * 2 * intensity;
            double shakeY = (Math.random() - 0.5) * 2 * intensity;
            cam.translate(shakeX, shakeY);
        }
    }
    
    // ==================== MÉTODOS DE COLISÃO ====================
    
    /**
     * Define o tipo de collider para este objeto.
     * @param type Tipo do collider: "none", "aabb", "circle", "polygon"
     */
    protected void setColliderType(String type) {
        if (gameObject == null) return;
        
        IgnisSampleCollisions.ColliderType colliderType;
        switch (type.toLowerCase()) {
            case "aabb":
            case "box":
                colliderType = IgnisSampleCollisions.ColliderType.AABB;
                break;
            case "circle":
                colliderType = IgnisSampleCollisions.ColliderType.CIRCLE;
                break;
            case "polygon":
            case "poly":
                colliderType = IgnisSampleCollisions.ColliderType.POLYGON;
                break;
            default:
                colliderType = IgnisSampleCollisions.ColliderType.NONE;
                break;
        }
        gameObject.setColliderType(colliderType);
    }
    
    /**
     * Define o modo de colisão para este objeto.
     * @param mode Modo de colisão: "collision" (resposta física) ou "trigger" (apenas eventos)
     */
    protected void setCollisionMode(String mode) {
        if (gameObject == null) return;
        
        IgnisSampleCollisions.CollisionMode collisionMode;
        if (mode.equalsIgnoreCase("trigger")) {
            collisionMode = IgnisSampleCollisions.CollisionMode.TRIGGER;
        } else {
            collisionMode = IgnisSampleCollisions.CollisionMode.COLLISION;
        }
        gameObject.setCollisionMode(collisionMode);
    }
    
    /**
     * Habilita ou desabilita o collider deste objeto.
     * @param enabled true para habilitar, false para desabilitar
     */
    protected void setColliderEnabled(boolean enabled) {
        if (gameObject != null) {
            gameObject.setColliderEnabled(enabled);
        }
    }
    
    /**
     * Habilita ou desabilita CCD (Continuous Collision Detection) para objetos rápidos.
     * @param use true para habilitar CCD
     */
    protected void setUseCCD(boolean use) {
        if (gameObject != null) {
            gameObject.setUseCCD(use);
        }
    }
    
    /**
     * Verifica se este objeto possui um collider.
     * @return true se possui collider configurado
     */
    protected boolean hasCollider() {
        return gameObject != null && gameObject.hasCollider();
    }
    
    /**
     * Obtém o collider deste objeto.
     * @return O collider ou null se não existir
     */
    protected IgnisSampleCollisions.Collider getCollider() {
        return gameObject != null ? gameObject.getCollider() : null;
    }
    
    /**
     * Faz um raycast na cena para detectar colisões.
     * Útil para detecção de linha de visão, tiros, etc.
     * 
     * @param startX Posição X inicial do raio
     * @param startY Posição Y inicial do raio
     * @param dirX Direção X do raio
     * @param dirY Direção Y do raio
     * @param maxDistance Distância máxima do raio
     * @return Resultado do raycast contendo informações sobre o que foi atingido
     */
    protected IgnisSampleCollisions.RaycastResult raycast(double startX, double startY, 
                                                           double dirX, double dirY, 
                                                           double maxDistance) {
        if (game == null || game.getCollisionManager() == null) {
            return new IgnisSampleCollisions.RaycastResult();
        }
        
        return IgnisSampleCollisions.raycast(startX, startY, dirX, dirY, maxDistance,
                                              game.getCollisionManager().getColliders());
    }
    
    /**
     * Faz um raycast a partir da posição atual do objeto.
     * @param dirX Direção X do raio
     * @param dirY Direção Y do raio
     * @param maxDistance Distância máxima do raio
     * @return Resultado do raycast
     */
    protected IgnisSampleCollisions.RaycastResult raycastFromHere(double dirX, double dirY, double maxDistance) {
        double startX = transform.x + transform.width / 2.0;
        double startY = transform.y + transform.height / 2.0;
        return raycast(startX, startY, dirX, dirY, maxDistance);
    }
    
    /**
     * Faz um raycast na direção que o objeto está olhando (baseado na rotação).
     * @param maxDistance Distância máxima do raio
     * @return Resultado do raycast
     */
    protected IgnisSampleCollisions.RaycastResult raycastForward(double maxDistance) {
        double rad = Math.toRadians(transform.rotation);
        double dirX = Math.cos(rad);
        double dirY = Math.sin(rad);
        return raycastFromHere(dirX, dirY, maxDistance);
    }
    
    /**
     * Callback chamado quando este objeto entra em contato com outro (trigger ou collision).
     * Sobrescreva este método para implementar lógica de colisão específica.
     * 
     * @param other O outro objeto envolvido na colisão
     * 
     * Exemplo de uso:
     * <pre>
     * @Override
     * public void onCollision(GameObject other) {
     *     if (other.getName().equals("Enemy")) {
     *         log("Colidiu com inimigo!");
     *         // Tomar dano, por exemplo
     *     } else if (other.getName().equals("Coin")) {
     *         log("Coletou moeda!");
     *         destroy(other);
     *     }
     * }
     * </pre>
     */
    // onCollision já está declarado acima, este é apenas documentação adicional
    
    /**
     * Obtém todas as colisões atuais do gerenciador de colisões.
     * @return Lista de resultados de colisão do frame atual
     */
    protected java.util.List<IgnisSampleCollisions.CollisionResult> getCurrentCollisions() {
        if (game == null || game.getCollisionManager() == null) {
            return new java.util.ArrayList<>();
        }
        return game.getCollisionManager().getCurrentCollisions();
    }
    
    /**
     * Configura a camada de colisão deste objeto.
     * Objetos só colidem com objetos em camadas especificadas por sua máscara.
     * 
     * @param layer Camada deste objeto (0-31)
     */
    protected void setCollisionLayer(int layer) {
        if (gameObject != null && gameObject.getCollider() != null) {
            gameObject.getCollider().setLayer(layer);
        }
    }
    
    /**
     * Configura a máscara de colisão deste objeto.
     * Define com quais camadas este objeto pode colidir.
     * 
     * @param mask Máscara de bits indicando camadas válidas (-1 = todas)
     */
    protected void setCollisionMask(int mask) {
        if (gameObject != null && gameObject.getCollider() != null) {
            gameObject.getCollider().setCollisionMask(mask);
        }
    }
    
    // ==================== MÉTODOS DE UI ====================
    
    /**
     * Obtém o canvas de UI principal do jogo.
     * Se não existir, cria um novo.
     * @return O canvas de UI
     */
    protected UICanvas getUICanvas() {
        if (game == null) return null;
        UICanvas canvas = game.getUICanvas();
        if (canvas == null) {
            canvas = new UICanvas();
            game.setUICanvas(canvas);
        }
        return canvas;
    }
    
    /**
     * Define o canvas de UI do jogo.
     * @param canvas O canvas a ser usado
     */
    protected void setUICanvas(UICanvas canvas) {
        if (game != null) {
            game.setUICanvas(canvas);
        }
    }
    
    /**
     * Cria um novo canvas de UI.
     * @return Novo canvas criado e definido no jogo
     */
    protected UICanvas createCanvas() {
        UICanvas canvas = UIFactory.createCanvas();
        setUICanvas(canvas);
        return canvas;
    }
    
    /**
     * Cria um novo canvas de UI com nome.
     * @param name Nome do canvas
     * @return Novo canvas criado e definido no jogo
     */
    protected UICanvas createCanvas(String name) {
        UICanvas canvas = UIFactory.createCanvas(name);
        setUICanvas(canvas);
        return canvas;
    }
    
    // ==================== BOTÕES ====================
    
    /**
     * Cria um botão na UI.
     * @param text Texto do botão
     * @param x Posição X
     * @param y Posição Y
     * @return O botão criado
     */
    protected UIButton createButton(String text, double x, double y) {
        UIButton button = UIFactory.createButton(text, x, y);
        addToUI(button);
        return button;
    }
    
    /**
     * Cria um botão na UI com dimensões específicas.
     * @param text Texto do botão
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O botão criado
     */
    protected UIButton createButton(String text, double x, double y, double width, double height) {
        UIButton button = UIFactory.createButton(text, x, y, width, height);
        addToUI(button);
        return button;
    }
    
    /**
     * Cria um botão com ação de click.
     * @param text Texto do botão
     * @param x Posição X
     * @param y Posição Y
     * @param onClick Ação ao clicar
     * @return O botão criado
     */
    protected UIButton createButton(String text, double x, double y, Runnable onClick) {
        UIButton button = createButton(text, x, y);
        button.setOnClick(onClick);
        return button;
    }
    
    // ==================== LABELS ====================
    
    /**
     * Cria um label de texto na UI.
     * @param text Texto a exibir
     * @param x Posição X
     * @param y Posição Y
     * @return O label criado
     */
    protected UILabel createLabel(String text, double x, double y) {
        UILabel label = UIFactory.createLabel(text, x, y);
        addToUI(label);
        return label;
    }
    
    /**
     * Cria um label de texto com dimensões.
     * @param text Texto a exibir
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O label criado
     */
    protected UILabel createLabel(String text, double x, double y, double width, double height) {
        UILabel label = new UILabel(text, x, y, width, height);
        addToUI(label);
        return label;
    }
    
    // ==================== PAINÉIS ====================
    
    /**
     * Cria um painel container na UI.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O painel criado
     */
    protected UIPanel createPanel(double x, double y, double width, double height) {
        UIPanel panel = UIFactory.createPanel(x, y, width, height);
        addToUI(panel);
        return panel;
    }
    
    /**
     * Cria um menu vertical de botões.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura dos botões
     * @param buttonLabels Labels de cada botão
     * @param onClick Callback com índice do botão clicado
     * @return O painel com os botões
     */
    protected UIPanel createVerticalMenu(double x, double y, double width, 
            String[] buttonLabels, java.util.function.Consumer<Integer> onClick) {
        UIPanel menu = UIFactory.createVerticalMenu(x, y, width, buttonLabels, onClick);
        addToUI(menu);
        return menu;
    }
    
    // ==================== BARRA DE PROGRESSO ====================
    
    /**
     * Cria uma barra de progresso.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return A barra de progresso criada
     */
    protected UIProgressBar createProgressBar(double x, double y, double width, double height) {
        UIProgressBar bar = new UIProgressBar(x, y, width, height);
        addToUI(bar);
        return bar;
    }
    
    /**
     * Cria uma barra de HP/status.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param label Label (ex: "HP")
     * @param fillColor Cor de preenchimento
     * @return O painel com label e barra
     */
    protected UIPanel createStatusBar(double x, double y, double width, 
            String label, Color fillColor) {
        UIPanel panel = UIFactory.createStatusBar(x, y, width, label, fillColor);
        addToUI(panel);
        return panel;
    }
    
    // ==================== CAMPOS DE TEXTO ====================
    
    /**
     * Cria um campo de texto editável.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O campo de texto criado
     */
    protected UITextField createTextField(double x, double y, double width, double height) {
        UITextField field = new UITextField(x, y, width, height);
        addToUI(field);
        return field;
    }
    
    /**
     * Cria um campo de texto com placeholder.
     * @param placeholder Texto de placeholder
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O campo de texto criado
     */
    protected UITextField createTextField(String placeholder, double x, double y, 
            double width, double height) {
        UITextField field = new UITextField(placeholder, x, y, width, height);
        addToUI(field);
        return field;
    }
    
    // ==================== SLIDER ====================
    
    /**
     * Cria um slider.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O slider criado
     */
    protected UISlider createSlider(double x, double y, double width, double height) {
        UISlider slider = new UISlider(x, y, width, height);
        addToUI(slider);
        return slider;
    }
    
    /**
     * Cria um slider com intervalo.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @param min Valor mínimo
     * @param max Valor máximo
     * @return O slider criado
     */
    protected UISlider createSlider(double x, double y, double width, double height, 
            double min, double max) {
        UISlider slider = new UISlider(x, y, width, height);
        slider.setRange(min, max);
        addToUI(slider);
        return slider;
    }
    
    // ==================== CHECKBOX ====================
    
    /**
     * Cria um checkbox.
     * @param label Texto do checkbox
     * @param x Posição X
     * @param y Posição Y
     * @return O checkbox criado
     */
    protected UICheckbox createCheckbox(String label, double x, double y) {
        UICheckbox checkbox = new UICheckbox(label, x, y);
        addToUI(checkbox);
        return checkbox;
    }
    
    /**
     * Cria um checkbox com estado inicial.
     * @param label Texto do checkbox
     * @param x Posição X
     * @param y Posição Y
     * @param checked Estado inicial
     * @return O checkbox criado
     */
    protected UICheckbox createCheckbox(String label, double x, double y, boolean checked) {
        UICheckbox checkbox = new UICheckbox(label, x, y);
        checkbox.setChecked(checked);
        addToUI(checkbox);
        return checkbox;
    }
    
    // ==================== TOGGLE (alias para Checkbox) ====================
    
    /**
     * Cria um toggle (alias para checkbox).
     * @param label Texto do toggle
     * @param x Posição X
     * @param y Posição Y
     * @return O toggle criado
     */
    protected UIToggle createToggle(String label, double x, double y) {
        UIToggle toggle = new UIToggle(label, x, y);
        addToUI(toggle);
        return toggle;
    }
    
    /**
     * Cria um toggle com dimensões.
     * @param label Texto do toggle
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O toggle criado
     */
    protected UIToggle createToggle(String label, double x, double y, double width, double height) {
        UIToggle toggle = new UIToggle(label, x, y, width, height);
        addToUI(toggle);
        return toggle;
    }
    
    /**
     * Cria um toggle com estado inicial.
     * @param label Texto do toggle
     * @param x Posição X
     * @param y Posição Y
     * @param checked Estado inicial
     * @return O toggle criado
     */
    protected UIToggle createToggle(String label, double x, double y, boolean checked) {
        UIToggle toggle = new UIToggle(label, x, y);
        toggle.setChecked(checked);
        addToUI(toggle);
        return toggle;
    }
    
    // ==================== IMAGEM ====================
    
    /**
     * Cria um componente de imagem.
     * @param imagePath Caminho da imagem
     * @param x Posição X
     * @param y Posição Y
     * @return O componente de imagem criado
     */
    protected UIImage createImage(String imagePath, double x, double y) {
        UIImage image = new UIImage(imagePath, x, y);
        addToUI(image);
        return image;
    }
    
    /**
     * Cria um componente de imagem com dimensões.
     * @param imagePath Caminho da imagem
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O componente de imagem criado
     */
    protected UIImage createImage(String imagePath, double x, double y, 
            double width, double height) {
        UIImage image = new UIImage(imagePath, x, y, width, height);
        addToUI(image);
        return image;
    }
    
    // ==================== DIÁLOGOS ====================
    
    /**
     * Cria um diálogo simples com botão OK.
     * @param title Título do diálogo
     * @param message Mensagem
     * @param x Posição X
     * @param y Posição Y
     * @param onOk Ação ao clicar OK
     * @return O painel do diálogo
     */
    protected UIPanel createDialog(String title, String message, double x, double y, Runnable onOk) {
        UIPanel dialog = UIFactory.createDialog(title, message, x, y, onOk);
        addToUI(dialog);
        return dialog;
    }
    
    // ==================== BUSCA DE COMPONENTES ====================
    
    /**
     * Encontra um componente de UI pelo ID.
     * @param id ID do componente
     * @return O componente ou null se não encontrado
     */
    protected UIComponent findUIById(String id) {
        UICanvas canvas = getUICanvas();
        if (canvas == null) return null;
        return canvas.findById(id);
    }
    
    /**
     * Encontra um componente de UI pelo nome.
     * @param name Nome do componente
     * @return O componente ou null se não encontrado
     */
    protected UIComponent findUIByName(String name) {
        UICanvas canvas = getUICanvas();
        if (canvas == null) return null;
        return canvas.findByName(name);
    }
    
    /**
     * Encontra todos os componentes de um tipo específico.
     * @param type Tipo do componente ("Button", "Label", etc)
     * @return Lista de componentes encontrados
     */
    protected java.util.List<UIComponent> findUIByType(String type) {
        UICanvas canvas = getUICanvas();
        if (canvas == null) return new java.util.ArrayList<>();
        return canvas.findByType(type);
    }
    
    // ==================== REMOÇÃO DE COMPONENTES ====================
    
    /**
     * Remove um componente de UI.
     * @param component Componente a remover
     */
    protected void removeUI(UIComponent component) {
        if (component == null) return;
        UIComponent parent = component.getParent();
        if (parent != null) {
            parent.removeChild(component);
        }
    }
    
    /**
     * Remove um componente de UI pelo ID.
     * @param id ID do componente
     * @return true se removido com sucesso
     */
    protected boolean removeUIById(String id) {
        UIComponent comp = findUIById(id);
        if (comp != null) {
            removeUI(comp);
            return true;
        }
        return false;
    }
    
    /**
     * Limpa toda a UI.
     */
    protected void clearUI() {
        UICanvas canvas = getUICanvas();
        if (canvas != null) {
            canvas.clearChildren();
        }
    }
    
    // ==================== VISIBILIDADE E ESTADO ====================
    
    /**
     * Define a visibilidade de um componente de UI.
     * @param component Componente
     * @param visible true para visível
     */
    protected void setUIVisible(UIComponent component, boolean visible) {
        if (component != null) {
            component.setVisible(visible);
        }
    }
    
    /**
     * Define se um componente de UI está habilitado.
     * @param component Componente
     * @param enabled true para habilitado
     */
    protected void setUIEnabled(UIComponent component, boolean enabled) {
        if (component != null) {
            component.setEnabled(enabled);
        }
    }
    
    /**
     * Mostra um componente de UI (torna visível).
     * @param component Componente a mostrar
     */
    protected void showUI(UIComponent component) {
        setUIVisible(component, true);
    }
    
    /**
     * Esconde um componente de UI (torna invisível).
     * @param component Componente a esconder
     */
    protected void hideUI(UIComponent component) {
        setUIVisible(component, false);
    }
    
    // ==================== MÉTODOS UTILITÁRIOS DE UI ====================
    
    /**
     * Adiciona um componente ao canvas de UI.
     * @param component Componente a adicionar
     */
    protected void addToUI(UIComponent component) {
        UICanvas canvas = getUICanvas();
        if (canvas != null && component != null) {
            canvas.addChild(component);
        }
    }
    
    /**
     * Define a posição de um componente de UI.
     * @param component Componente
     * @param x Posição X
     * @param y Posição Y
     */
    protected void setUIPosition(UIComponent component, double x, double y) {
        if (component != null) {
            component.setPosition(x, y);
        }
    }
    
    /**
     * Define o tamanho de um componente de UI.
     * @param component Componente
     * @param width Largura
     * @param height Altura
     */
    protected void setUISize(UIComponent component, double width, double height) {
        if (component != null) {
            component.setSize(width, height);
        }
    }
    
    /**
     * Define as cores de um componente de UI.
     * @param component Componente
     * @param background Cor de fundo
     * @param text Cor do texto
     * @param border Cor da borda
     */
    protected void setUIColors(UIComponent component, Color background, 
            Color text, Color border) {
        if (component != null) {
            if (background != null) component.setBackgroundColor(background);
            if (text != null) component.setTextColor(text);
            if (border != null) component.setBorderColor(border);
        }
    }
    
    /**
     * Define a fonte de um componente de UI.
     * @param component Componente
     * @param fontFamily Família da fonte
     * @param fontSize Tamanho da fonte
     * @param fontStyle Estilo (Font.PLAIN, Font.BOLD, Font.ITALIC)
     */
    protected void setUIFont(UIComponent component, String fontFamily, 
            int fontSize, int fontStyle) {
        if (component != null) {
            component.setFont(fontFamily, fontSize, fontStyle);
        }
    }
    
    /**
     * Verifica se o mouse está sobre a UI (bloqueando input do jogo).
     * @return true se a UI está consumindo input
     */
    protected boolean isUIBlocking() {
        UICanvas canvas = getUICanvas();
        return canvas != null && canvas.isBlockingGameInput();
    }
}
