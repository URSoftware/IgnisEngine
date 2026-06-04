package com.ignis.core.ui;

import java.awt.*;
import org.json.JSONObject;

/**
 * UISlider - Controle deslizante para valores numéricos.
 * 
 * Ideal para:
 * - Controles de volume
 * - Configurações numéricas
 * - Ajuste de brilho/contraste
 * 
 * Exemplo:
 * ```java
 * UISlider volumeSlider = new UISlider(100, 100, 200, 30);
 * volumeSlider.setRange(0, 100);
 * volumeSlider.setValue(75);
 * volumeSlider.setOnValueChange(value -> {
 *     setVolume(Float.parseFloat(value) / 100f);
 * });
 * canvas.addChild(volumeSlider);
 * ```
 */
public class UISlider extends UIComponent {

    // ==================== CAMPOS ====================
    
    private float value = 0.5f;
    private float minValue = 0;
    private float maxValue = 1;
    private float step = 0; // 0 = contínuo
    
    /** Visual */
    private Color trackColor = new Color(80, 80, 80);
    private Color fillColor = new Color(0, 120, 215);
    private Color handleColor = Color.WHITE;
    private Color handleBorderColor = new Color(180, 180, 180);
    
    /** Dimensões */
    private int trackHeight = 6;
    private int handleSize = 18;
    
    /** Estado */
    private boolean dragging = false;
    
    /** Mostrar valor */
    private boolean showValue = false;
    private String valueFormat = "%.0f";
    
    // ==================== CONSTRUTORES ====================
    
    public UISlider() {
        super("Slider");
        initDefaults();
    }
    
    public UISlider(double x, double y, double width, double height) {
        super("Slider", x, y, width, height);
        initDefaults();
    }
    
    private void initDefaults() {
        this.interactive = true;
        this.backgroundColor = new Color(0, 0, 0, 0);
        this.height = 30;
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        int trackY = (int) ((height - trackHeight) / 2);
        int handleX = getHandleX();
        int handleY = (int) ((height - handleSize) / 2);
        
        // Track background
        g.setColor(trackColor);
        g.fillRoundRect(handleSize / 2, trackY, (int) width - handleSize, trackHeight, 
                       trackHeight, trackHeight);
        
        // Track fill (até o handle)
        g.setColor(fillColor);
        g.fillRoundRect(handleSize / 2, trackY, handleX - handleSize / 2, trackHeight,
                       trackHeight, trackHeight);
        
        // Handle shadow
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(handleX - handleSize / 2 + 2, handleY + 2, handleSize, handleSize);
        
        // Handle
        g.setColor(handleColor);
        g.fillOval(handleX - handleSize / 2, handleY, handleSize, handleSize);
        
        // Handle border
        g.setColor(hovered || dragging ? fillColor : handleBorderColor);
        g.setStroke(new BasicStroke(2));
        g.drawOval(handleX - handleSize / 2, handleY, handleSize, handleSize);
        
        // Mostrar valor
        if (showValue) {
            String valueText = String.format(valueFormat, getDisplayValue());
            g.setFont(getFont());
            g.setColor(textColor);
            FontMetrics fm = g.getFontMetrics();
            int textX = (int) width + 10;
            int textY = (int) ((height + fm.getAscent() - fm.getDescent()) / 2);
            g.drawString(valueText, textX, textY);
        }
    }
    
    private int getHandleX() {
        float normalized = (value - minValue) / (maxValue - minValue);
        return (int) (handleSize / 2 + normalized * (width - handleSize));
    }
    
    private float getDisplayValue() {
        return minValue + value * (maxValue - minValue);
    }
    
    // ==================== INTERAÇÃO ====================
    
    @Override
    public boolean handleMouseClick(double mx, double my, boolean pressed) {
        if (!visible || !enabled) return false;
        
        Rectangle bounds = getAbsoluteBounds();
        boolean inside = bounds.contains(mx, my);
        
        if (pressed && inside) {
            dragging = true;
            updateValueFromMouse(mx);
            return true;
        } else if (!pressed && dragging) {
            dragging = false;
        }
        
        return inside;
    }
    
    @Override
    public boolean handleMouseMove(double mx, double my) {
        boolean result = super.handleMouseMove(mx, my);
        
        if (dragging) {
            updateValueFromMouse(mx);
            return true;
        }
        
        return result;
    }
    
    private void updateValueFromMouse(double mx) {
        Rectangle bounds = getAbsoluteBounds();
        double relativeX = mx - bounds.x - handleSize / 2;
        double trackWidth = width - handleSize;
        
        float newValue = (float) Math.max(0, Math.min(1, relativeX / trackWidth));
        
        // Aplicar step
        if (step > 0) {
            float range = maxValue - minValue;
            float steps = range / step;
            newValue = Math.round(newValue * steps) / steps;
        }
        
        if (newValue != value) {
            value = newValue;
            if (onValueChange != null) {
                onValueChange.accept(String.valueOf(getDisplayValue()));
            }
        }
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public float getValue() { return value; }
    public void setValue(float value) {
        this.value = Math.max(0, Math.min(1, (value - minValue) / (maxValue - minValue)));
    }
    
    public float getMinValue() { return minValue; }
    public float getMaxValue() { return maxValue; }
    public void setRange(double min, double max) {
        this.minValue = (float) min;
        this.maxValue = (float) max;
    }
    
    public float getStep() { return step; }
    public void setStep(float step) { this.step = step; }
    
    public Color getTrackColor() { return trackColor; }
    public void setTrackColor(Color color) { this.trackColor = color; }
    
    public Color getFillColor() { return fillColor; }
    public void setFillColor(Color color) { this.fillColor = color; }
    
    public Color getHandleColor() { return handleColor; }
    public void setHandleColor(Color color) { this.handleColor = color; }
    
    public int getTrackHeight() { return trackHeight; }
    public void setTrackHeight(int height) { this.trackHeight = height; }
    
    public int getHandleSize() { return handleSize; }
    public void setHandleSize(int size) { this.handleSize = size; }
    
    public boolean isShowValue() { return showValue; }
    public void setShowValue(boolean show) { this.showValue = show; }
    
    public String getValueFormat() { return valueFormat; }
    public void setValueFormat(String format) { this.valueFormat = format; }
    
    @Override
    public String getType() {
        return "Slider";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("value", value);
        json.put("minValue", minValue);
        json.put("maxValue", maxValue);
        json.put("step", step);
        json.put("trackColor", colorToHex(trackColor));
        json.put("fillColor", colorToHex(fillColor));
        json.put("handleColor", colorToHex(handleColor));
        json.put("trackHeight", trackHeight);
        json.put("handleSize", handleSize);
        json.put("showValue", showValue);
        json.put("valueFormat", valueFormat);
        return json;
    }
    
    public static UISlider fromJSON(JSONObject json) {
        UISlider slider = new UISlider();
        slider.loadFromJSON(json);
        slider.value = (float) json.optDouble("value", 0.5);
        slider.minValue = (float) json.optDouble("minValue", 0);
        slider.maxValue = (float) json.optDouble("maxValue", 1);
        slider.step = (float) json.optDouble("step", 0);
        slider.trackColor = hexToColor(json.optString("trackColor", "#505050"));
        slider.fillColor = hexToColor(json.optString("fillColor", "#0078D7"));
        slider.handleColor = hexToColor(json.optString("handleColor", "#FFFFFF"));
        slider.trackHeight = json.optInt("trackHeight", 6);
        slider.handleSize = json.optInt("handleSize", 18);
        slider.showValue = json.optBoolean("showValue", false);
        slider.valueFormat = json.optString("valueFormat", "%.0f");
        return slider;
    }
}
