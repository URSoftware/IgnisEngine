package com.ignis.core.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;
import com.ignis.core.Game;

/**
 * UIComponent - Classe base para todos os componentes de interface do usuário.
 * 
 * O sistema de UI do Ignis Engine é inspirado em sistemas profissionais como
 * Unity UI, Godot Control, e Unreal UMG. Oferece:
 * 
 * - Sistema de ancoragem flexível
 * - Hierarquia de componentes
 * - Eventos de interação (click, hover, etc)
 * - Estilização visual completa
 * - Integração com scripts
 * 
 * Arquitetura:
 * ```
 * UICanvas (raiz)
 *   └── UIPanel
 *         ├── UILabel
 *         ├── UIButton
 *         └── UIImage
 * ```
 * 
 * @see UICanvas
 * @see UIButton
 * @see UILabel
 * @see UIPanel
 */
public abstract class UIComponent {

    // ==================== IDENTIDADE ====================
    
    protected String id;
    protected String name;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected int zOrder = 0;
    
    // ==================== HIERARQUIA ====================
    
    protected UIComponent parent;
    protected List<UIComponent> children = new ArrayList<>();
    
    // ==================== POSIÇÃO E TAMANHO ====================
    
    /** Posição X relativa à âncora */
    protected double x = 0;
    
    /** Posição Y relativa à âncora */
    protected double y = 0;
    
    /** Largura do componente */
    protected double width = 100;
    
    /** Altura do componente */
    protected double height = 40;
    
    /** Rotação em graus */
    protected double rotation = 0;
    
    /** Ponto de ancoragem do pai (0-1) */
    protected double anchorX = 0;
    protected double anchorY = 0;
    
    /** Ponto de pivô do próprio componente (0-1) */
    protected double pivotX = 0;
    protected double pivotY = 0;
    
    // ==================== ESTILO ====================
    
    protected Color backgroundColor = new Color(60, 60, 60, 200);
    protected Color borderColor = new Color(100, 100, 100);
    protected Color textColor = Color.WHITE;
    protected int borderWidth = 0;
    protected int borderRadius = 0;
    protected int paddingTop = 0;
    protected int paddingRight = 0;
    protected int paddingBottom = 0;
    protected int paddingLeft = 0;
    
    // ==================== FONTE ====================
    
    protected String fontFamily = "SansSerif";
    protected int fontSize = 14;
    protected int fontStyle = Font.PLAIN;
    protected transient Font cachedFont;
    
    // ==================== INTERAÇÃO ====================
    
    protected boolean interactive = false;
    protected boolean hovered = false;
    protected boolean pressed = false;
    protected boolean focused = false;
    
    // ==================== CALLBACKS ====================
    
    protected transient Runnable onClick;
    protected transient Runnable onHoverEnter;
    protected transient Runnable onHoverExit;
    protected transient java.util.function.Consumer<String> onValueChange;
    
    // ==================== REFERÊNCIA AO JOGO ====================
    
    protected transient Game game;
    
    // ==================== CONSTRUTORES ====================
    
    public UIComponent() {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = getClass().getSimpleName();
    }
    
    public UIComponent(String name) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
    }
    
    public UIComponent(String name, double x, double y, double width, double height) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    // ==================== MÉTODOS ABSTRATOS ====================
    
    /**
     * Renderiza o componente.
     * @param g Contexto gráfico
     */
    public abstract void render(Graphics2D g);
    
    /**
     * Retorna o tipo do componente.
     * @return Nome do tipo ("Button", "Label", etc)
     */
    public abstract String getType();
    
    // ==================== CICLO DE VIDA ====================
    
    /**
     * Atualiza o componente a cada frame.
     */
    public void update() {
        if (!enabled) return;
        
        // Atualizar filhos
        for (UIComponent child : children) {
            child.update();
        }
    }
    
    /**
     * Renderiza este componente e seus filhos.
     * @param g Contexto gráfico
     */
    public void renderAll(Graphics2D g) {
        if (!visible) return;
        
        // Salvar transformação
        var oldTransform = g.getTransform();
        
        // Aplicar transformação local
        double[] pos = getAbsolutePosition();
        g.translate(pos[0], pos[1]);
        
        if (rotation != 0) {
            g.rotate(Math.toRadians(rotation), width * pivotX, height * pivotY);
        }
        
        // Renderizar este componente
        render(g);
        
        // Renderizar filhos (ordenados por zOrder)
        children.stream()
            .sorted((a, b) -> Integer.compare(a.zOrder, b.zOrder))
            .forEach(child -> child.renderAll(g));
        
        // Restaurar transformação
        g.setTransform(oldTransform);
    }
    
    // ==================== HIERARQUIA ====================
    
    /**
     * Adiciona um componente filho.
     * @param child Componente a adicionar
     */
    public void addChild(UIComponent child) {
        if (child != null && !children.contains(child)) {
            // Remover do pai anterior
            if (child.parent != null) {
                child.parent.removeChild(child);
            }
            children.add(child);
            child.parent = this;
            child.game = this.game;
        }
    }
    
    /**
     * Remove um componente filho.
     * @param child Componente a remover
     */
    public void removeChild(UIComponent child) {
        if (child != null && children.remove(child)) {
            child.parent = null;
        }
    }
    
    /**
     * Remove todos os filhos.
     */
    public void clearChildren() {
        for (UIComponent child : new ArrayList<>(children)) {
            removeChild(child);
        }
    }
    
    /**
     * Encontra um componente filho pelo nome.
     * @param name Nome do componente
     * @return Componente encontrado ou null
     */
    public UIComponent findChild(String name) {
        for (UIComponent child : children) {
            if (child.name.equals(name)) {
                return child;
            }
            UIComponent found = child.findChild(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    /**
     * Encontra um componente pelo ID (busca recursiva).
     * @param id ID do componente
     * @return Componente encontrado ou null
     */
    public UIComponent findById(String id) {
        if (this.id.equals(id)) {
            return this;
        }
        for (UIComponent child : children) {
            UIComponent found = child.findById(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    /**
     * Encontra um componente pelo nome (busca recursiva).
     * @param name Nome do componente
     * @return Componente encontrado ou null
     */
    public UIComponent findByName(String name) {
        if (this.name.equals(name)) {
            return this;
        }
        for (UIComponent child : children) {
            UIComponent found = child.findByName(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    /**
     * Encontra todos os componentes de um tipo específico (busca recursiva).
     * @param type Tipo do componente ("Button", "Label", etc.)
     * @return Lista de componentes encontrados
     */
    public List<UIComponent> findByType(String type) {
        List<UIComponent> result = new ArrayList<>();
        if (this.getType().equals(type)) {
            result.add(this);
        }
        for (UIComponent child : children) {
            result.addAll(child.findByType(type));
        }
        return result;
    }
    
    /**
     * Obtém todos os filhos de um tipo específico.
     * @param type Classe do tipo desejado
     * @return Lista de componentes do tipo
     */
    @SuppressWarnings("unchecked")
    public <T extends UIComponent> List<T> getChildrenOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (UIComponent child : children) {
            if (type.isInstance(child)) {
                result.add((T) child);
            }
            result.addAll(child.getChildrenOfType(type));
        }
        return result;
    }
    
    // ==================== POSIÇÃO ====================
    
    /**
     * Calcula a posição absoluta na tela.
     * @return Array [x, y] com posição absoluta
     */
    public double[] getAbsolutePosition() {
        double absX = x - (width * pivotX);
        double absY = y - (height * pivotY);
        
        if (parent != null) {
            double[] parentPos = parent.getAbsolutePosition();
            double parentWidth = parent.width;
            double parentHeight = parent.height;
            
            // Aplicar âncora do pai
            absX += parentPos[0] + (parentWidth * anchorX);
            absY += parentPos[1] + (parentHeight * anchorY);
        }
        
        return new double[] { absX, absY };
    }
    
    /**
     * Calcula o retângulo de bounds na tela.
     * @return Rectangle com os limites absolutos
     */
    public Rectangle getAbsoluteBounds() {
        double[] pos = getAbsolutePosition();
        return new Rectangle((int) pos[0], (int) pos[1], (int) width, (int) height);
    }
    
    /**
     * Verifica se um ponto está dentro do componente.
     * @param px Coordenada X
     * @param py Coordenada Y
     * @return true se o ponto está dentro
     */
    public boolean containsPoint(double px, double py) {
        Rectangle bounds = getAbsoluteBounds();
        return bounds.contains(px, py);
    }
    
    // ==================== INTERAÇÃO ====================
    
    /**
     * Processa evento de mouse movendo.
     * @param mx Posição X do mouse
     * @param my Posição Y do mouse
     * @return true se este componente processou o evento
     */
    public boolean handleMouseMove(double mx, double my) {
        if (!visible || !enabled) return false;
        
        // Verificar filhos primeiro (do topo para baixo)
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).handleMouseMove(mx, my)) {
                // Filho processou, garantir que este não está mais em hover
                if (hovered) {
                    hovered = false;
                    if (onHoverExit != null) onHoverExit.run();
                }
                return true;
            }
        }
        
        // Processar este componente
        if (interactive) {
            boolean wasHovered = hovered;
            hovered = containsPoint(mx, my);
            
            if (hovered && !wasHovered && onHoverEnter != null) {
                onHoverEnter.run();
            } else if (!hovered && wasHovered && onHoverExit != null) {
                onHoverExit.run();
            }
            
            return hovered;
        }
        
        return false;
    }
    
    /**
     * Processa evento de clique do mouse.
     * @param mx Posição X do mouse
     * @param my Posição Y do mouse
     * @param pressed Se o botão foi pressionado (true) ou solto (false)
     * @return true se este componente processou o evento
     */
    public boolean handleMouseClick(double mx, double my, boolean pressed) {
        if (!visible || !enabled) return false;
        
        // Verificar filhos primeiro
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).handleMouseClick(mx, my, pressed)) {
                return true;
            }
        }
        
        // Processar este componente
        if (interactive && containsPoint(mx, my)) {
            this.pressed = pressed;
            
            if (!pressed && onClick != null) {
                onClick.run();
            }
            
            return true;
        }
        
        return false;
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getZOrder() { return zOrder; }
    public void setZOrder(int zOrder) { this.zOrder = zOrder; }
    
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    
    public void setSize(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }
    
    public double getAnchorX() { return anchorX; }
    public double getAnchorY() { return anchorY; }
    public void setAnchor(double anchorX, double anchorY) {
        this.anchorX = Math.max(0, Math.min(1, anchorX));
        this.anchorY = Math.max(0, Math.min(1, anchorY));
    }
    
    public double getPivotX() { return pivotX; }
    public double getPivotY() { return pivotY; }
    public void setPivot(double pivotX, double pivotY) {
        this.pivotX = Math.max(0, Math.min(1, pivotX));
        this.pivotY = Math.max(0, Math.min(1, pivotY));
    }
    
    public Color getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(Color color) { this.backgroundColor = color; }
    
    public Color getBorderColor() { return borderColor; }
    public void setBorderColor(Color color) { this.borderColor = color; }
    
    public Color getTextColor() { return textColor; }
    public void setTextColor(Color color) { this.textColor = color; }
    
    public int getBorderWidth() { return borderWidth; }
    public void setBorderWidth(int width) { this.borderWidth = width; }
    
    public int getBorderRadius() { return borderRadius; }
    public void setBorderRadius(int radius) { this.borderRadius = radius; }
    
    public void setPadding(int all) {
        this.paddingTop = this.paddingRight = this.paddingBottom = this.paddingLeft = all;
    }
    
    public void setPadding(int vertical, int horizontal) {
        this.paddingTop = this.paddingBottom = vertical;
        this.paddingLeft = this.paddingRight = horizontal;
    }
    
    public void setPadding(int top, int right, int bottom, int left) {
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        this.paddingLeft = left;
    }
    
    public boolean isInteractive() { return interactive; }
    public void setInteractive(boolean interactive) { this.interactive = interactive; }
    
    public boolean isHovered() { return hovered; }
    public boolean isPressed() { return pressed; }
    public boolean isFocused() { return focused; }
    /** Define o estado de foco de teclado deste componente (gerido pela UICanvas). */
    public void setFocused(boolean focused) { this.focused = focused; }
    
    public UIComponent getParent() { return parent; }
    public List<UIComponent> getChildren() { return new ArrayList<>(children); }
    
    public Game getGame() { return game; }
    public void setGame(Game game) { 
        this.game = game;
        for (UIComponent child : children) {
            child.setGame(game);
        }
    }
    
    // ==================== CALLBACKS ====================
    
    public void setOnClick(Runnable callback) {
        this.onClick = callback;
        this.interactive = true;
    }

    /**
     * Dispara a acao de clique deste componente (usado pela ativacao por teclado —
     * Enter/Espaco no componente focado). No-op se desabilitado ou sem callback.
     */
    public void activate() {
        if (enabled && onClick != null) {
            onClick.run();
        }
    }
    
    public void setOnHoverEnter(Runnable callback) { 
        this.onHoverEnter = callback; 
        this.interactive = true;
    }
    
    public void setOnHoverExit(Runnable callback) { 
        this.onHoverExit = callback; 
        this.interactive = true;
    }
    
    public void setOnValueChange(java.util.function.Consumer<String> callback) {
        this.onValueChange = callback;
    }
    
    // ==================== FONTE ====================
    
    public Font getFont() {
        if (cachedFont == null) {
            cachedFont = new Font(fontFamily, fontStyle, fontSize);
        }
        return cachedFont;
    }
    
    public void setFont(String family, int style, int size) {
        this.fontFamily = family;
        this.fontStyle = style;
        this.fontSize = size;
        this.cachedFont = null;
    }
    
    public void setFontSize(int size) {
        this.fontSize = size;
        this.cachedFont = null;
    }
    
    public void setFontStyle(int style) {
        this.fontStyle = style;
        this.cachedFont = null;
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    /**
     * Serializa o componente para JSON.
     * @return JSONObject com os dados
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        
        // Identidade
        json.put("type", getType());
        json.put("id", id);
        json.put("name", name);
        json.put("visible", visible);
        json.put("enabled", enabled);
        json.put("zOrder", zOrder);
        
        // Transformação
        json.put("x", x);
        json.put("y", y);
        json.put("width", width);
        json.put("height", height);
        json.put("rotation", rotation);
        json.put("anchorX", anchorX);
        json.put("anchorY", anchorY);
        json.put("pivotX", pivotX);
        json.put("pivotY", pivotY);
        
        // Estilo
        json.put("backgroundColor", colorToHex(backgroundColor));
        json.put("borderColor", colorToHex(borderColor));
        json.put("textColor", colorToHex(textColor));
        json.put("borderWidth", borderWidth);
        json.put("borderRadius", borderRadius);
        json.put("paddingTop", paddingTop);
        json.put("paddingRight", paddingRight);
        json.put("paddingBottom", paddingBottom);
        json.put("paddingLeft", paddingLeft);
        
        // Fonte
        json.put("fontFamily", fontFamily);
        json.put("fontSize", fontSize);
        json.put("fontStyle", fontStyle);
        
        // Interação
        json.put("interactive", interactive);
        
        // Filhos
        JSONArray childrenArray = new JSONArray();
        for (UIComponent child : children) {
            childrenArray.put(child.toJSON());
        }
        json.put("children", childrenArray);
        
        return json;
    }
    
    /**
     * Carrega propriedades comuns de JSON.
     * @param json Objeto JSON com os dados
     */
    protected void loadFromJSON(JSONObject json) {
        // Identidade
        id = json.optString("id", java.util.UUID.randomUUID().toString());
        name = json.optString("name", getClass().getSimpleName());
        visible = json.optBoolean("visible", true);
        enabled = json.optBoolean("enabled", true);
        zOrder = json.optInt("zOrder", 0);
        
        // Transformação
        x = json.optDouble("x", 0);
        y = json.optDouble("y", 0);
        width = json.optDouble("width", 100);
        height = json.optDouble("height", 40);
        rotation = json.optDouble("rotation", 0);
        anchorX = json.optDouble("anchorX", 0);
        anchorY = json.optDouble("anchorY", 0);
        pivotX = json.optDouble("pivotX", 0);
        pivotY = json.optDouble("pivotY", 0);
        
        // Estilo
        backgroundColor = hexToColor(json.optString("backgroundColor", "#3C3C3C"));
        borderColor = hexToColor(json.optString("borderColor", "#646464"));
        textColor = hexToColor(json.optString("textColor", "#FFFFFF"));
        borderWidth = json.optInt("borderWidth", 0);
        borderRadius = json.optInt("borderRadius", 0);
        paddingTop = json.optInt("paddingTop", 0);
        paddingRight = json.optInt("paddingRight", 0);
        paddingBottom = json.optInt("paddingBottom", 0);
        paddingLeft = json.optInt("paddingLeft", 0);
        
        // Fonte
        fontFamily = json.optString("fontFamily", "SansSerif");
        fontSize = json.optInt("fontSize", 14);
        fontStyle = json.optInt("fontStyle", Font.PLAIN);
        cachedFont = null;
        
        // Interação
        interactive = json.optBoolean("interactive", false);
    }
    
    // ==================== UTILIDADES ====================
    
    /**
     * Converte Color para string hexadecimal.
     */
    protected static String colorToHex(Color color) {
        if (color == null) return "#FFFFFF";
        return String.format("#%02X%02X%02X%02X", 
            color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
    
    /**
     * Converte string hexadecimal para Color.
     */
    protected static Color hexToColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.WHITE;
        try {
            hex = hex.replace("#", "");
            if (hex.length() == 6) {
                return new Color(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
                );
            } else if (hex.length() == 8) {
                return new Color(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16),
                    Integer.parseInt(hex.substring(6, 8), 16)
                );
            }
        } catch (Exception e) {
            // Ignorar erro de parsing
        }
        return Color.WHITE;
    }
    
    /**
     * Desenha fundo com bordas arredondadas.
     */
    protected void drawBackground(Graphics2D g) {
        if (backgroundColor != null && backgroundColor.getAlpha() > 0) {
            g.setColor(backgroundColor);
            if (borderRadius > 0) {
                g.fillRoundRect(0, 0, (int) width, (int) height, borderRadius, borderRadius);
            } else {
                g.fillRect(0, 0, (int) width, (int) height);
            }
        }
    }
    
    /**
     * Desenha bordas.
     */
    protected void drawBorder(Graphics2D g) {
        if (borderWidth > 0 && borderColor != null) {
            g.setColor(borderColor);
            g.setStroke(new BasicStroke(borderWidth));
            if (borderRadius > 0) {
                g.drawRoundRect(0, 0, (int) width - 1, (int) height - 1, borderRadius, borderRadius);
            } else {
                g.drawRect(0, 0, (int) width - 1, (int) height - 1);
            }
        }
    }
    
    @Override
    public String toString() {
        return getType() + "[" + name + "]";
    }
}
