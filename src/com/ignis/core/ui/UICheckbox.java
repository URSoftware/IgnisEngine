package com.ignis.core.ui;

import java.awt.*;
import org.json.JSONObject;

/**
 * UICheckbox - Caixa de seleção para opções booleanas.
 * 
 * Exemplo:
 * ```java
 * UICheckbox fullscreen = new UICheckbox("Tela Cheia", 100, 100);
 * fullscreen.setChecked(false);
 * fullscreen.setOnValueChange(value -> {
 *     setFullscreen(Boolean.parseBoolean(value));
 * });
 * canvas.addChild(fullscreen);
 * ```
 */
public class UICheckbox extends UIComponent {

    // ==================== CAMPOS ====================
    
    private String label = "Checkbox";
    private boolean checked = false;
    
    /** Visual */
    private Color checkColor = new Color(0, 120, 215);
    private Color boxColor = Color.WHITE;
    private Color boxBorderColor = new Color(180, 180, 180);
    
    /** Dimensões */
    private int boxSize = 20;
    private int labelSpacing = 10;
    
    // ==================== CONSTRUTORES ====================
    
    public UICheckbox() {
        super("Checkbox");
        initDefaults();
    }
    
    public UICheckbox(String label) {
        super("Checkbox");
        this.label = label;
        initDefaults();
    }
    
    public UICheckbox(String label, double x, double y) {
        super("Checkbox", x, y, 200, 24);
        this.label = label;
        initDefaults();
    }
    
    private void initDefaults() {
        this.interactive = true;
        this.backgroundColor = new Color(0, 0, 0, 0);
        this.textColor = Color.WHITE;
        this.height = 24;
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        int boxY = (int) ((height - boxSize) / 2);
        
        // Caixa de fundo
        g.setColor(boxColor);
        g.fillRoundRect(0, boxY, boxSize, boxSize, 4, 4);
        
        // Borda da caixa
        g.setColor(hovered ? checkColor : boxBorderColor);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(0, boxY, boxSize, boxSize, 4, 4);
        
        // Marca de verificação
        if (checked) {
            g.setColor(checkColor);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            
            int cx = boxSize / 2;
            int cy = boxY + boxSize / 2;
            
            // Desenhar checkmark
            int[] xPoints = { cx - 5, cx - 1, cx + 6 };
            int[] yPoints = { cy, cy + 5, cy - 5 };
            g.drawPolyline(xPoints, yPoints, 3);
        }
        
        // Label
        if (label != null && !label.isEmpty()) {
            g.setFont(getFont());
            g.setColor(enabled ? textColor : new Color(128, 128, 128));
            FontMetrics fm = g.getFontMetrics();
            int labelX = boxSize + labelSpacing;
            int labelY = (int) ((height + fm.getAscent() - fm.getDescent()) / 2);
            g.drawString(label, labelX, labelY);
        }
    }
    
    // ==================== INTERAÇÃO ====================
    
    @Override
    public boolean handleMouseClick(double mx, double my, boolean pressed) {
        if (!visible || !enabled) return false;
        
        boolean inside = containsPoint(mx, my);
        
        if (pressed && inside) {
            this.pressed = true;
            return true;
        } else if (!pressed && this.pressed && inside) {
            // Toggle ao soltar
            checked = !checked;
            if (onValueChange != null) {
                onValueChange.accept(String.valueOf(checked));
            }
            this.pressed = false;
            return true;
        }
        
        this.pressed = false;
        return inside;
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    
    public void toggle() { this.checked = !this.checked; }
    
    public Color getCheckColor() { return checkColor; }
    public void setCheckColor(Color color) { this.checkColor = color; }
    
    public Color getBoxColor() { return boxColor; }
    public void setBoxColor(Color color) { this.boxColor = color; }
    
    public int getBoxSize() { return boxSize; }
    public void setBoxSize(int size) { this.boxSize = size; }
    
    public int getLabelSpacing() { return labelSpacing; }
    public void setLabelSpacing(int spacing) { this.labelSpacing = spacing; }
    
    @Override
    public String getType() {
        return "Checkbox";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("label", label);
        json.put("checked", checked);
        json.put("checkColor", colorToHex(checkColor));
        json.put("boxColor", colorToHex(boxColor));
        json.put("boxSize", boxSize);
        json.put("labelSpacing", labelSpacing);
        return json;
    }
    
    public static UICheckbox fromJSON(JSONObject json) {
        UICheckbox checkbox = new UICheckbox();
        checkbox.loadFromJSON(json);
        checkbox.label = json.optString("label", "Checkbox");
        checkbox.checked = json.optBoolean("checked", false);
        checkbox.checkColor = hexToColor(json.optString("checkColor", "#0078D7"));
        checkbox.boxColor = hexToColor(json.optString("boxColor", "#FFFFFF"));
        checkbox.boxSize = json.optInt("boxSize", 20);
        checkbox.labelSpacing = json.optInt("labelSpacing", 10);
        return checkbox;
    }
}
