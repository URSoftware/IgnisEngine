package com.ignis.core.ui;

import java.awt.*;
import org.json.JSONObject;

/**
 * UIPanel - Container para agrupar outros componentes de UI.
 * 
 * O UIPanel serve como container visual e organizacional para outros componentes.
 * Pode ter fundo, bordas e layout automático.
 * 
 * Layouts disponíveis:
 * - NONE: Posicionamento manual
 * - VERTICAL: Empilha filhos verticalmente
 * - HORIZONTAL: Empilha filhos horizontalmente
 * - GRID: Organiza em grade
 * 
 * Exemplo:
 * ```java
 * UIPanel menuPanel = new UIPanel(50, 50, 300, 400);
 * menuPanel.setLayout(UIPanel.Layout.VERTICAL);
 * menuPanel.setSpacing(10);
 * 
 * menuPanel.addChild(new UIButton("Jogar"));
 * menuPanel.addChild(new UIButton("Opções"));
 * menuPanel.addChild(new UIButton("Sair"));
 * 
 * canvas.addChild(menuPanel);
 * ```
 */
public class UIPanel extends UIComponent {

    // ==================== ENUMS ====================
    
    public enum Layout {
        /** Sem layout automático */
        NONE,
        /** Empilha verticalmente */
        VERTICAL,
        /** Empilha horizontalmente */
        HORIZONTAL,
        /** Organiza em grade */
        GRID
    }
    
    // ==================== CAMPOS ====================
    
    private Layout layout = Layout.NONE;
    private int spacing = 5;
    private int gridColumns = 2;
    
    /** Se o painel deve ajustar seu tamanho ao conteúdo */
    private boolean fitContent = false;
    
    /** Se deve clipar filhos que excedem os limites */
    private boolean clipChildren = true;
    
    /** Scroll */
    private boolean scrollable = false;
    private double scrollX = 0;
    private double scrollY = 0;
    private double contentWidth = 0;
    private double contentHeight = 0;
    
    // ==================== CONSTRUTORES ====================
    
    public UIPanel() {
        super("Panel");
        initDefaults();
    }
    
    public UIPanel(double x, double y, double width, double height) {
        super("Panel", x, y, width, height);
        initDefaults();
    }
    
    public UIPanel(String name, double x, double y, double width, double height) {
        super(name, x, y, width, height);
        initDefaults();
    }
    
    private void initDefaults() {
        this.backgroundColor = new Color(40, 40, 40, 200);
        this.borderRadius = 8;
        this.borderWidth = 1;
        this.borderColor = new Color(80, 80, 80);
        this.paddingTop = 10;
        this.paddingRight = 10;
        this.paddingBottom = 10;
        this.paddingLeft = 10;
    }
    
    // ==================== LAYOUT ====================
    
    @Override
    public void addChild(UIComponent child) {
        super.addChild(child);
        updateLayout();
    }
    
    @Override
    public void removeChild(UIComponent child) {
        super.removeChild(child);
        updateLayout();
    }
    
    /**
     * Atualiza o layout dos filhos.
     */
    public void updateLayout() {
        if (layout == Layout.NONE || children.isEmpty()) return;
        
        double offsetX = paddingLeft;
        double offsetY = paddingTop;
        double maxWidth = 0;
        double maxHeight = 0;
        int col = 0;
        
        for (UIComponent child : children) {
            switch (layout) {
                case VERTICAL:
                    child.setX(offsetX);
                    child.setY(offsetY);
                    offsetY += child.getHeight() + spacing;
                    maxWidth = Math.max(maxWidth, child.getWidth());
                    maxHeight = offsetY - spacing + paddingBottom;
                    break;
                    
                case HORIZONTAL:
                    child.setX(offsetX);
                    child.setY(offsetY);
                    offsetX += child.getWidth() + spacing;
                    maxHeight = Math.max(maxHeight, child.getHeight());
                    maxWidth = offsetX - spacing + paddingRight;
                    break;
                    
                case GRID:
                    double cellWidth = (width - paddingLeft - paddingRight - (gridColumns - 1) * spacing) / gridColumns;
                    double cellHeight = child.getHeight();
                    
                    child.setX(offsetX);
                    child.setY(offsetY);
                    child.setWidth(cellWidth);
                    
                    col++;
                    if (col >= gridColumns) {
                        col = 0;
                        offsetX = paddingLeft;
                        offsetY += cellHeight + spacing;
                    } else {
                        offsetX += cellWidth + spacing;
                    }
                    break;
                    
                case NONE:
                default:
                    break;
            }
        }
        
        // Armazenar tamanho do conteúdo para scroll
        contentWidth = maxWidth;
        contentHeight = maxHeight;
        
        // Ajustar tamanho se fitContent
        if (fitContent) {
            if (layout == Layout.VERTICAL) {
                this.height = maxHeight;
                this.width = maxWidth + paddingLeft + paddingRight;
            } else if (layout == Layout.HORIZONTAL) {
                this.width = maxWidth;
                this.height = maxHeight + paddingTop + paddingBottom;
            }
        }
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        // Desenhar fundo
        drawBackground(g);
        
        // Desenhar borda
        drawBorder(g);
    }
    
    @Override
    public void renderAll(Graphics2D g) {
        if (!visible) return;
        
        // Salvar transformação
        var oldTransform = g.getTransform();
        var oldClip = g.getClip();
        
        // Aplicar transformação local
        double[] pos = getAbsolutePosition();
        g.translate(pos[0], pos[1]);
        
        if (rotation != 0) {
            g.rotate(Math.toRadians(rotation), width * pivotX, height * pivotY);
        }
        
        // Renderizar este componente
        render(g);
        
        // Aplicar clip se necessário
        if (clipChildren) {
            g.clipRect(0, 0, (int) width, (int) height);
        }
        
        // Aplicar scroll
        if (scrollable) {
            g.translate(-scrollX, -scrollY);
        }
        
        // Renderizar filhos
        children.stream()
            .sorted((a, b) -> Integer.compare(a.getZOrder(), b.getZOrder()))
            .forEach(child -> child.renderAll(g));
        
        // Restaurar
        g.setTransform(oldTransform);
        g.setClip(oldClip);
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public Layout getLayout() { return layout; }
    public void setLayout(Layout layout) { 
        this.layout = layout; 
        updateLayout();
    }
    
    public int getSpacing() { return spacing; }
    public void setSpacing(int spacing) { 
        this.spacing = spacing; 
        updateLayout();
    }
    
    public int getGridColumns() { return gridColumns; }
    public void setGridColumns(int columns) { 
        this.gridColumns = Math.max(1, columns); 
        updateLayout();
    }
    
    public boolean isFitContent() { return fitContent; }
    public void setFitContent(boolean fit) { 
        this.fitContent = fit; 
        updateLayout();
    }
    
    public boolean isClipChildren() { return clipChildren; }
    public void setClipChildren(boolean clip) { this.clipChildren = clip; }
    
    public boolean isScrollable() { return scrollable; }
    public void setScrollable(boolean scrollable) { this.scrollable = scrollable; }
    
    public double getScrollX() { return scrollX; }
    public void setScrollX(double scrollX) { this.scrollX = scrollX; }
    
    public double getScrollY() { return scrollY; }
    public void setScrollY(double scrollY) { this.scrollY = scrollY; }
    
    public void scroll(double dx, double dy) {
        this.scrollX = Math.max(0, Math.min(scrollX + dx, Math.max(0, contentWidth - width)));
        this.scrollY = Math.max(0, Math.min(scrollY + dy, Math.max(0, contentHeight - height)));
    }
    
    public double getContentWidth() { return contentWidth; }
    public double getContentHeight() { return contentHeight; }
    
    @Override
    public String getType() {
        return "Panel";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("layout", layout.name());
        json.put("spacing", spacing);
        json.put("gridColumns", gridColumns);
        json.put("fitContent", fitContent);
        json.put("clipChildren", clipChildren);
        json.put("scrollable", scrollable);
        return json;
    }
    
    public static UIPanel fromJSON(JSONObject json) {
        UIPanel panel = new UIPanel();
        panel.loadFromJSON(json);
        try {
            panel.layout = Layout.valueOf(json.optString("layout", "NONE"));
        } catch (Exception e) { panel.layout = Layout.NONE; }
        panel.spacing = json.optInt("spacing", 5);
        panel.gridColumns = json.optInt("gridColumns", 2);
        panel.fitContent = json.optBoolean("fitContent", false);
        panel.clipChildren = json.optBoolean("clipChildren", true);
        panel.scrollable = json.optBoolean("scrollable", false);
        
        // Carregar filhos
        var childrenArray = json.optJSONArray("children");
        if (childrenArray != null) {
            for (int i = 0; i < childrenArray.length(); i++) {
                var childJson = childrenArray.getJSONObject(i);
                UIComponent child = UIFactory.createFromJSON(childJson);
                if (child != null) {
                    panel.addChild(child);
                }
            }
        }
        
        return panel;
    }
}
