package com.ignis.core.ui;

import java.awt.*;
import org.json.JSONObject;

/**
 * UILabel - Componente de texto para interface do usuário.
 * 
 * Características:
 * - Texto simples ou multilinha
 * - Alinhamento configurável
 * - Sombra de texto opcional
 * - Suporte a texto rico (futuro)
 * 
 * Exemplo:
 * ```java
 * UILabel title = new UILabel("Meu Jogo", 100, 50);
 * title.setFontSize(32);
 * title.setTextColor(Color.YELLOW);
 * title.setAlignment(UILabel.Alignment.CENTER);
 * canvas.addChild(title);
 * ```
 */
public class UILabel extends UIComponent {

    // ==================== ENUMS ====================
    
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }
    
    public enum VerticalAlignment {
        TOP, MIDDLE, BOTTOM
    }
    
    // ==================== CAMPOS ====================
    
    private String text = "Label";
    private Alignment alignment = Alignment.LEFT;
    private VerticalAlignment verticalAlignment = VerticalAlignment.MIDDLE;
    
    /** Sombra de texto */
    private boolean shadowEnabled = false;
    private Color shadowColor = new Color(0, 0, 0, 128);
    private int shadowOffsetX = 2;
    private int shadowOffsetY = 2;
    
    /** Outline de texto */
    private boolean outlineEnabled = false;
    private Color outlineColor = Color.BLACK;
    private int outlineWidth = 1;
    
    /** Auto-size baseado no texto */
    private boolean autoSize = false;
    
    /** Texto multilinha */
    private boolean multiline = false;
    private int lineSpacing = 4;
    
    // ==================== CONSTRUTORES ====================
    
    public UILabel() {
        super("Label");
        initDefaults();
    }
    
    public UILabel(String text) {
        super("Label");
        this.text = text;
        initDefaults();
    }
    
    public UILabel(String text, double x, double y) {
        super("Label", x, y, 200, 30);
        this.text = text;
        initDefaults();
    }
    
    public UILabel(String text, double x, double y, double width, double height) {
        super("Label", x, y, width, height);
        this.text = text;
        initDefaults();
    }
    
    private void initDefaults() {
        this.backgroundColor = new Color(0, 0, 0, 0); // Transparente
        this.textColor = Color.WHITE;
        this.fontSize = 14;
        this.interactive = false;
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        // Desenhar fundo se tiver
        drawBackground(g);
        
        // Configurar fonte
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        
        if (multiline) {
            renderMultiline(g, fm);
        } else {
            renderSingleLine(g, fm);
        }
        
        // Desenhar borda
        drawBorder(g);
    }
    
    private void renderSingleLine(Graphics2D g, FontMetrics fm) {
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();
        
        // Auto-size
        if (autoSize) {
            this.width = textWidth + paddingLeft + paddingRight;
            this.height = fm.getHeight() + paddingTop + paddingBottom;
        }
        
        // Calcular posição X baseado no alinhamento
        int textX;
        switch (alignment) {
            case CENTER:
                textX = (int) ((width - textWidth) / 2);
                break;
            case RIGHT:
                textX = (int) (width - textWidth - paddingRight);
                break;
            case LEFT:
            default:
                textX = paddingLeft;
                break;
        }
        
        // Calcular posição Y baseado no alinhamento vertical
        int textY;
        switch (verticalAlignment) {
            case TOP:
                textY = paddingTop + fm.getAscent();
                break;
            case BOTTOM:
                textY = (int) (height - paddingBottom - fm.getDescent());
                break;
            case MIDDLE:
            default:
                textY = (int) ((height + fm.getAscent() - fm.getDescent()) / 2);
                break;
        }
        
        // Desenhar sombra
        if (shadowEnabled) {
            g.setColor(shadowColor);
            g.drawString(text, textX + shadowOffsetX, textY + shadowOffsetY);
        }
        
        // Desenhar outline
        if (outlineEnabled) {
            g.setColor(outlineColor);
            for (int dx = -outlineWidth; dx <= outlineWidth; dx++) {
                for (int dy = -outlineWidth; dy <= outlineWidth; dy++) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(text, textX + dx, textY + dy);
                    }
                }
            }
        }
        
        // Desenhar texto
        g.setColor(textColor);
        g.drawString(text, textX, textY);
    }
    
    private void renderMultiline(Graphics2D g, FontMetrics fm) {
        String[] lines = text.split("\n");
        int lineHeight = fm.getHeight() + lineSpacing;
        int totalHeight = lines.length * lineHeight;
        
        int startY;
        switch (verticalAlignment) {
            case TOP:
                startY = paddingTop + fm.getAscent();
                break;
            case BOTTOM:
                startY = (int) (height - totalHeight + fm.getAscent() - paddingBottom);
                break;
            case MIDDLE:
            default:
                startY = (int) ((height - totalHeight + lineHeight) / 2);
                break;
        }
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int textWidth = fm.stringWidth(line);
            
            int textX;
            switch (alignment) {
                case CENTER:
                    textX = (int) ((width - textWidth) / 2);
                    break;
                case RIGHT:
                    textX = (int) (width - textWidth - paddingRight);
                    break;
                case LEFT:
                default:
                    textX = paddingLeft;
                    break;
            }
            
            int textY = startY + (i * lineHeight);
            
            // Desenhar sombra
            if (shadowEnabled) {
                g.setColor(shadowColor);
                g.drawString(line, textX + shadowOffsetX, textY + shadowOffsetY);
            }
            
            // Desenhar texto
            g.setColor(textColor);
            g.drawString(line, textX, textY);
        }
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public Alignment getAlignment() { return alignment; }
    public void setAlignment(Alignment alignment) { this.alignment = alignment; }
    
    public VerticalAlignment getVerticalAlignment() { return verticalAlignment; }
    public void setVerticalAlignment(VerticalAlignment alignment) { this.verticalAlignment = alignment; }
    
    public boolean isShadowEnabled() { return shadowEnabled; }
    public void setShadowEnabled(boolean enabled) { this.shadowEnabled = enabled; }
    
    public Color getShadowColor() { return shadowColor; }
    public void setShadowColor(Color color) { this.shadowColor = color; }
    
    public void setShadow(boolean enabled, Color color, int offsetX, int offsetY) {
        this.shadowEnabled = enabled;
        this.shadowColor = color;
        this.shadowOffsetX = offsetX;
        this.shadowOffsetY = offsetY;
    }
    
    public boolean isOutlineEnabled() { return outlineEnabled; }
    public void setOutlineEnabled(boolean enabled) { this.outlineEnabled = enabled; }
    
    public void setOutline(boolean enabled, Color color, int width) {
        this.outlineEnabled = enabled;
        this.outlineColor = color;
        this.outlineWidth = width;
    }
    
    public boolean isAutoSize() { return autoSize; }
    public void setAutoSize(boolean autoSize) { this.autoSize = autoSize; }
    
    public boolean isMultiline() { return multiline; }
    public void setMultiline(boolean multiline) { this.multiline = multiline; }
    
    public int getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(int spacing) { this.lineSpacing = spacing; }
    
    /**
     * Alias para setAlignment usando TextAlignment (compatibilidade).
     */
    public void setTextAlignment(TextAlignment alignment) {
        switch (alignment) {
            case LEFT: this.alignment = Alignment.LEFT; break;
            case CENTER: this.alignment = Alignment.CENTER; break;
            case RIGHT: this.alignment = Alignment.RIGHT; break;
        }
    }
    
    /**
     * Enum alternativo para compatibilidade.
     */
    public enum TextAlignment {
        LEFT, CENTER, RIGHT
    }
    
    /**
     * Define se o texto está em negrito.
     * @param bold true para negrito
     */
    public void setBold(boolean bold) {
        if (bold) {
            this.fontStyle = Font.BOLD;
        } else {
            this.fontStyle = Font.PLAIN;
        }
    }
    
    /**
     * Verifica se o texto está em negrito.
     * @return true se em negrito
     */
    public boolean isBold() {
        return (fontStyle & Font.BOLD) != 0;
    }
    
    /**
     * Define se o texto está em itálico.
     * @param italic true para itálico
     */
    public void setItalic(boolean italic) {
        if (italic) {
            this.fontStyle |= Font.ITALIC;
        } else {
            this.fontStyle &= ~Font.ITALIC;
        }
    }
    
    /**
     * Verifica se o texto está em itálico.
     * @return true se em itálico
     */
    public boolean isItalic() {
        return (fontStyle & Font.ITALIC) != 0;
    }
    
    @Override
    public String getType() {
        return "Label";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("text", text);
        json.put("alignment", alignment.name());
        json.put("verticalAlignment", verticalAlignment.name());
        json.put("shadowEnabled", shadowEnabled);
        json.put("shadowColor", colorToHex(shadowColor));
        json.put("shadowOffsetX", shadowOffsetX);
        json.put("shadowOffsetY", shadowOffsetY);
        json.put("outlineEnabled", outlineEnabled);
        json.put("outlineColor", colorToHex(outlineColor));
        json.put("outlineWidth", outlineWidth);
        json.put("autoSize", autoSize);
        json.put("multiline", multiline);
        json.put("lineSpacing", lineSpacing);
        return json;
    }
    
    public static UILabel fromJSON(JSONObject json) {
        UILabel label = new UILabel();
        label.loadFromJSON(json);
        label.text = json.optString("text", "Label");
        try {
            label.alignment = Alignment.valueOf(json.optString("alignment", "LEFT"));
        } catch (Exception e) { label.alignment = Alignment.LEFT; }
        try {
            label.verticalAlignment = VerticalAlignment.valueOf(json.optString("verticalAlignment", "MIDDLE"));
        } catch (Exception e) { label.verticalAlignment = VerticalAlignment.MIDDLE; }
        label.shadowEnabled = json.optBoolean("shadowEnabled", false);
        label.shadowColor = hexToColor(json.optString("shadowColor", "#00000080"));
        label.shadowOffsetX = json.optInt("shadowOffsetX", 2);
        label.shadowOffsetY = json.optInt("shadowOffsetY", 2);
        label.outlineEnabled = json.optBoolean("outlineEnabled", false);
        label.outlineColor = hexToColor(json.optString("outlineColor", "#000000"));
        label.outlineWidth = json.optInt("outlineWidth", 1);
        label.autoSize = json.optBoolean("autoSize", false);
        label.multiline = json.optBoolean("multiline", false);
        label.lineSpacing = json.optInt("lineSpacing", 4);
        return label;
    }
}
