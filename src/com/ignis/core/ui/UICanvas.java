package com.ignis.core.ui;

import java.awt.*;
import java.awt.event.*;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * UICanvas - Container raiz para o sistema de interface do usuário.
 * 
 * O UICanvas é o ponto de entrada para todo o sistema de UI. Ele:
 * - Renderiza sobre o jogo (não é afetado pela câmera)
 * - Gerencia todos os componentes de UI
 * - Processa eventos de entrada
 * - Oferece diferentes modos de escalonamento
 * 
 * Modos de Escalonamento:
 * - SCREEN_SPACE_OVERLAY: UI sempre na frente, coordenadas de tela
 * - SCREEN_SPACE_CAMERA: UI renderizada com a câmera principal
 * - WORLD_SPACE: UI posicionada no mundo do jogo
 * 
 * Exemplo de uso:
 * ```java
 * UICanvas canvas = new UICanvas();
 * 
 * UIButton button = new UIButton("Play", 100, 100, 200, 50);
 * button.setOnClick(() -> startGame());
 * canvas.addChild(button);
 * 
 * game.setUICanvas(canvas);
 * ```
 */
public class UICanvas extends UIComponent {

    // ==================== ENUMS ====================
    
    /**
     * Modo de renderização do Canvas.
     */
    public enum RenderMode {
        /** UI renderizada em coordenadas de tela, sempre na frente */
        SCREEN_SPACE_OVERLAY,
        
        /** UI renderizada em coordenadas de tela, usa câmera */
        SCREEN_SPACE_CAMERA,
        
        /** UI posicionada no mundo do jogo */
        WORLD_SPACE
    }
    
    /**
     * Modo de escalonamento para diferentes resoluções.
     */
    public enum ScaleMode {
        /** Sem escalonamento */
        CONSTANT_PIXEL_SIZE,
        
        /** Escala baseada na largura da tela */
        SCALE_WITH_SCREEN_WIDTH,
        
        /** Escala baseada na altura da tela */
        SCALE_WITH_SCREEN_HEIGHT,
        
        /** Escala para preencher a tela mantendo proporção */
        SCALE_WITH_SCREEN_SIZE
    }
    
    // ==================== CAMPOS ====================
    
    private RenderMode renderMode = RenderMode.SCREEN_SPACE_OVERLAY;
    private ScaleMode scaleMode = ScaleMode.CONSTANT_PIXEL_SIZE;
    
    /** Resolução de referência para escalonamento */
    private double referenceWidth = 1920;
    private double referenceHeight = 1080;
    
    /** Escala calculada */
    private double scaleFactor = 1.0;
    
    /** Dimensões atuais da tela */
    private int screenWidth = 1920;
    private int screenHeight = 1080;
    
    /** Se a UI está bloqueando input do jogo */
    private boolean blockGameInput = false;
    
    // ==================== CONSTRUTORES ====================
    
    public UICanvas() {
        super("Canvas");
        this.interactive = false; // Canvas em si não é interativo
    }
    
    public UICanvas(String name) {
        super(name);
        this.interactive = false;
    }
    
    // ==================== MÉTODOS PRINCIPAIS ====================
    
    /**
     * Atualiza as dimensões da tela e recalcula escala.
     * @param width Largura da tela
     * @param height Altura da tela
     */
    public void updateScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.width = width;
        this.height = height;
        
        // Recalcular escala
        switch (scaleMode) {
            case SCALE_WITH_SCREEN_WIDTH:
                scaleFactor = width / referenceWidth;
                break;
            case SCALE_WITH_SCREEN_HEIGHT:
                scaleFactor = height / referenceHeight;
                break;
            case SCALE_WITH_SCREEN_SIZE:
                double scaleW = width / referenceWidth;
                double scaleH = height / referenceHeight;
                scaleFactor = Math.min(scaleW, scaleH);
                break;
            case CONSTANT_PIXEL_SIZE:
            default:
                scaleFactor = 1.0;
                break;
        }
    }
    
    @Override
    public void render(Graphics2D g) {
        // Canvas não renderiza nada próprio, apenas organiza filhos
        // O fundo é transparente por padrão
    }
    
    /**
     * Atualiza o estado de todos os componentes de UI.
     * Chamado a cada frame pelo Game.
     */
    public void update() {
        if (!visible || !enabled) return;
        
        // Atualizar todos os filhos recursivamente
        for (UIComponent child : children) {
            child.update();
        }
    }
    
    /**
     * Renderiza toda a hierarquia de UI.
     * Este método deve ser chamado pelo Game para renderizar a UI.
     * @param g Contexto gráfico
     */
    @Override
    public void renderAll(Graphics2D g) {
        if (!visible) return;
        
        // Salvar estado
        var oldTransform = g.getTransform();
        var oldClip = g.getClip();
        var oldHints = g.getRenderingHints();
        
        // Configurar qualidade de renderização
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Aplicar escala se necessário
        if (scaleFactor != 1.0) {
            g.scale(scaleFactor, scaleFactor);
        }
        
        // Renderizar filhos ordenados por zOrder
        children.stream()
            .filter(c -> c.isVisible())
            .sorted((a, b) -> Integer.compare(a.getZOrder(), b.getZOrder()))
            .forEach(child -> child.renderAll(g));
        
        // Restaurar estado
        g.setTransform(oldTransform);
        g.setClip(oldClip);
        g.setRenderingHints(oldHints);
    }
    
    // ==================== PROCESSAMENTO DE EVENTOS ====================
    
    /**
     * Processa movimento do mouse.
     * @param e Evento do mouse
     * @return true se a UI processou o evento
     */
    public boolean processMouseMove(MouseEvent e) {
        if (!visible || !enabled) return false;
        
        double mx = e.getX() / scaleFactor;
        double my = e.getY() / scaleFactor;
        
        // Processar do topo para baixo
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).handleMouseMove(mx, my)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Processa clique do mouse.
     * @param e Evento do mouse
     * @param pressed Se o botão foi pressionado
     * @return true se a UI processou o evento
     */
    public boolean processMouseClick(MouseEvent e, boolean pressed) {
        if (!visible || !enabled) return false;
        
        double mx = e.getX() / scaleFactor;
        double my = e.getY() / scaleFactor;
        
        // Processar do topo para baixo
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).handleMouseClick(mx, my, pressed)) {
                blockGameInput = true;
                return true;
            }
        }
        
        blockGameInput = false;
        return false;
    }
    
    /**
     * Processa tecla pressionada.
     * @param e Evento de teclado
     * @return true se a UI processou o evento
     */
    public boolean processKeyPressed(KeyEvent e) {
        if (!visible || !enabled) return false;
        
        // TODO: Implementar foco e navegação por teclado
        return false;
    }
    
    /**
     * Processa tecla solta.
     * @param e Evento de teclado
     * @return true se a UI processou o evento
     */
    public boolean processKeyReleased(KeyEvent e) {
        if (!visible || !enabled) return false;
        
        // TODO: Implementar foco e navegação por teclado
        return false;
    }
    
    // ==================== MÉTODOS DE CONVENIÊNCIA ====================
    
    /**
     * Adiciona um botão ao canvas.
     * @param text Texto do botão
     * @param x Posição X
     * @param y Posição Y
     * @param onClick Callback de clique
     * @return O botão criado
     */
    public UIButton addButton(String text, double x, double y, Runnable onClick) {
        UIButton button = new UIButton(text, x, y, 150, 40);
        button.setOnClick(onClick);
        addChild(button);
        return button;
    }
    
    /**
     * Adiciona um label ao canvas.
     * @param text Texto do label
     * @param x Posição X
     * @param y Posição Y
     * @return O label criado
     */
    public UILabel addLabel(String text, double x, double y) {
        UILabel label = new UILabel(text, x, y);
        addChild(label);
        return label;
    }
    
    /**
     * Adiciona um painel ao canvas.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param height Altura
     * @return O painel criado
     */
    public UIPanel addPanel(double x, double y, double width, double height) {
        UIPanel panel = new UIPanel(x, y, width, height);
        addChild(panel);
        return panel;
    }
    
    /**
     * Adiciona uma imagem ao canvas.
     * @param imagePath Caminho da imagem
     * @param x Posição X
     * @param y Posição Y
     * @return A imagem criada
     */
    public UIImage addImage(String imagePath, double x, double y) {
        UIImage image = new UIImage(imagePath, x, y);
        addChild(image);
        return image;
    }
    
    /**
     * Adiciona uma barra de progresso ao canvas.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @return A barra de progresso criada
     */
    public UIProgressBar addProgressBar(double x, double y, double width) {
        UIProgressBar bar = new UIProgressBar(x, y, width, 20);
        addChild(bar);
        return bar;
    }
    
    /**
     * Adiciona um campo de texto ao canvas.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @return O campo de texto criado
     */
    public UITextField addTextField(double x, double y, double width) {
        UITextField field = new UITextField(x, y, width, 30);
        addChild(field);
        return field;
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public RenderMode getRenderMode() { return renderMode; }
    public void setRenderMode(RenderMode mode) { this.renderMode = mode; }
    
    public ScaleMode getScaleMode() { return scaleMode; }
    public void setScaleMode(ScaleMode mode) { 
        this.scaleMode = mode;
        updateScreenSize(screenWidth, screenHeight);
    }
    
    public double getReferenceWidth() { return referenceWidth; }
    public double getReferenceHeight() { return referenceHeight; }
    public void setReferenceResolution(double width, double height) {
        this.referenceWidth = width;
        this.referenceHeight = height;
        updateScreenSize(screenWidth, screenHeight);
    }
    
    public double getScaleFactor() { return scaleFactor; }
    
    public boolean isBlockingGameInput() { return blockGameInput; }
    
    @Override
    public String getType() {
        return "Canvas";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("renderMode", renderMode.name());
        json.put("scaleMode", scaleMode.name());
        json.put("referenceWidth", referenceWidth);
        json.put("referenceHeight", referenceHeight);
        return json;
    }
    
    /**
     * Cria um UICanvas a partir de JSON.
     * @param json Objeto JSON
     * @return Novo UICanvas
     */
    public static UICanvas fromJSON(JSONObject json) {
        UICanvas canvas = new UICanvas();
        canvas.loadFromJSON(json);
        
        try {
            canvas.renderMode = RenderMode.valueOf(json.optString("renderMode", "SCREEN_SPACE_OVERLAY"));
        } catch (Exception e) {
            canvas.renderMode = RenderMode.SCREEN_SPACE_OVERLAY;
        }
        
        try {
            canvas.scaleMode = ScaleMode.valueOf(json.optString("scaleMode", "CONSTANT_PIXEL_SIZE"));
        } catch (Exception e) {
            canvas.scaleMode = ScaleMode.CONSTANT_PIXEL_SIZE;
        }
        
        canvas.referenceWidth = json.optDouble("referenceWidth", 1920);
        canvas.referenceHeight = json.optDouble("referenceHeight", 1080);
        
        // Carregar filhos
        JSONArray childrenArray = json.optJSONArray("children");
        if (childrenArray != null) {
            for (int i = 0; i < childrenArray.length(); i++) {
                JSONObject childJson = childrenArray.getJSONObject(i);
                UIComponent child = UIFactory.createFromJSON(childJson);
                if (child != null) {
                    canvas.addChild(child);
                }
            }
        }
        
        return canvas;
    }
}
