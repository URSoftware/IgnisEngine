package com.ignis.core.ui;

import java.awt.*;
import org.json.JSONObject;

/**
 * UIProgressBar - Barra de progresso para interface do usuário.
 * 
 * Ideal para:
 * - Barras de vida/saúde
 * - Barras de loading
 * - Indicadores de progresso
 * - Barras de mana/energia
 * 
 * Exemplo:
 * ```java
 * // Barra de vida
 * UIProgressBar healthBar = new UIProgressBar(50, 20, 200, 25);
 * healthBar.setValue(0.75f); // 75%
 * healthBar.setFillColor(Color.RED);
 * healthBar.setShowText(true);
 * canvas.addChild(healthBar);
 * 
 * // Atualizar no script
 * healthBar.setValue(player.health / player.maxHealth);
 * ```
 */
public class UIProgressBar extends UIComponent {

    // ==================== ENUMS ====================
    
    public enum Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM
    }
    
    public enum TextMode {
        NONE,
        PERCENTAGE,
        VALUE,
        VALUE_MAX
    }
    
    // ==================== CAMPOS ====================
    
    /** Valor atual (0.0 a 1.0) */
    private float value = 0.5f;
    
    /** Valores para exibição */
    private float currentValue = 50;
    private float maxValue = 100;
    
    /** Cores */
    private Color fillColor = new Color(70, 200, 70);
    private Color emptyColor = new Color(50, 50, 50);
    
    /** Gradiente opcional */
    private boolean useGradient = false;
    private Color gradientEndColor = new Color(200, 70, 70);
    
    /** Direção do preenchimento */
    private Direction direction = Direction.LEFT_TO_RIGHT;
    
    /** Texto */
    private TextMode textMode = TextMode.NONE;
    private String customText = "";
    
    /** Animação suave */
    private boolean smoothAnimation = false;
    private float displayValue = 0.5f;
    private float smoothSpeed = 0.1f;
    
    /** Borda interna */
    private int innerPadding = 2;
    
    // ==================== CONSTRUTORES ====================
    
    public UIProgressBar() {
        super("ProgressBar");
        initDefaults();
    }
    
    public UIProgressBar(double x, double y, double width, double height) {
        super("ProgressBar", x, y, width, height);
        initDefaults();
    }
    
    private void initDefaults() {
        this.backgroundColor = emptyColor;
        this.borderRadius = 4;
        this.borderWidth = 1;
        this.borderColor = new Color(80, 80, 80);
        this.height = 20;
    }
    
    // ==================== ATUALIZAÇÃO ====================
    
    @Override
    public void update() {
        super.update();
        
        // Animação suave
        if (smoothAnimation && displayValue != value) {
            if (Math.abs(displayValue - value) < 0.001f) {
                displayValue = value;
            } else {
                displayValue += (value - displayValue) * smoothSpeed;
            }
        } else {
            displayValue = value;
        }
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        // Desenhar fundo (parte vazia)
        g.setColor(emptyColor);
        if (borderRadius > 0) {
            g.fillRoundRect(0, 0, (int) width, (int) height, borderRadius, borderRadius);
        } else {
            g.fillRect(0, 0, (int) width, (int) height);
        }
        
        // Calcular dimensões do preenchimento
        double fillValue = smoothAnimation ? displayValue : value;
        int px = innerPadding;
        int py = innerPadding;
        int pw = (int) (width - 2 * innerPadding);
        int ph = (int) (height - 2 * innerPadding);
        
        int fillW = pw, fillH = ph, fillX = px, fillY = py;
        
        switch (direction) {
            case LEFT_TO_RIGHT:
                fillW = (int) (pw * fillValue);
                break;
            case RIGHT_TO_LEFT:
                fillW = (int) (pw * fillValue);
                fillX = (int) (width - innerPadding - fillW);
                break;
            case BOTTOM_TO_TOP:
                fillH = (int) (ph * fillValue);
                fillY = (int) (height - innerPadding - fillH);
                break;
            case TOP_TO_BOTTOM:
                fillH = (int) (ph * fillValue);
                break;
        }
        
        // Desenhar preenchimento
        if (fillW > 0 && fillH > 0) {
            if (useGradient) {
                // Cor baseada no valor (verde -> vermelho conforme diminui)
                Color currentColor = lerpColor(gradientEndColor, fillColor, fillValue);
                g.setColor(currentColor);
            } else {
                g.setColor(fillColor);
            }
            
            int rad = Math.max(0, borderRadius - innerPadding);
            if (rad > 0) {
                g.fillRoundRect(fillX, fillY, fillW, fillH, rad, rad);
            } else {
                g.fillRect(fillX, fillY, fillW, fillH);
            }
        }
        
        // Desenhar borda
        drawBorder(g);
        
        // Desenhar texto
        if (textMode != TextMode.NONE || !customText.isEmpty()) {
            String text = getDisplayText();
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(text);
            int textX = (int) ((width - textW) / 2);
            int textY = (int) ((height + fm.getAscent() - fm.getDescent()) / 2);
            
            // Sombra
            g.setColor(Color.BLACK);
            g.drawString(text, textX + 1, textY + 1);
            
            // Texto
            g.setColor(textColor);
            g.drawString(text, textX, textY);
        }
    }
    
    private String getDisplayText() {
        if (!customText.isEmpty()) return customText;
        
        switch (textMode) {
            case PERCENTAGE:
                return String.format("%.0f%%", value * 100);
            case VALUE:
                return String.format("%.0f", currentValue);
            case VALUE_MAX:
                return String.format("%.0f / %.0f", currentValue, maxValue);
            default:
                return "";
        }
    }
    
    private Color lerpColor(Color a, Color b, double t) {
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, bl))
        );
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public float getValue() { return value; }
    public void setValue(float value) { 
        this.value = Math.max(0, Math.min(1, value)); 
    }
    
    /**
     * Define o valor usando valor atual e máximo.
     * @param current Valor atual
     * @param max Valor máximo
     */
    public void setValue(float current, float max) {
        this.currentValue = current;
        this.maxValue = max;
        this.value = max > 0 ? current / max : 0;
    }
    
    public float getCurrentValue() { return currentValue; }
    public float getMaxValue() { return maxValue; }
    
    public Color getFillColor() { return fillColor; }
    public void setFillColor(Color color) { this.fillColor = color; }
    
    public Color getEmptyColor() { return emptyColor; }
    public void setEmptyColor(Color color) { 
        this.emptyColor = color; 
        this.backgroundColor = color;
    }
    
    public boolean isUseGradient() { return useGradient; }
    public void setUseGradient(boolean use) { this.useGradient = use; }
    
    public Color getGradientEndColor() { return gradientEndColor; }
    public void setGradientEndColor(Color color) { this.gradientEndColor = color; }
    
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    
    public TextMode getTextMode() { return textMode; }
    public void setTextMode(TextMode mode) { this.textMode = mode; }
    
    public String getCustomText() { return customText; }
    public void setCustomText(String text) { this.customText = text; }
    
    public boolean isSmoothAnimation() { return smoothAnimation; }
    public void setSmoothAnimation(boolean smooth) { this.smoothAnimation = smooth; }
    
    public float getSmoothSpeed() { return smoothSpeed; }
    public void setSmoothSpeed(float speed) { this.smoothSpeed = speed; }
    
    public int getInnerPadding() { return innerPadding; }
    public void setInnerPadding(int padding) { this.innerPadding = padding; }
    
    /**
     * Define se deve mostrar texto.
     * @param show true para mostrar percentual
     */
    public void setShowText(boolean show) {
        this.textMode = show ? TextMode.PERCENTAGE : TextMode.NONE;
    }
    
    @Override
    public String getType() {
        return "ProgressBar";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("value", value);
        json.put("currentValue", currentValue);
        json.put("maxValue", maxValue);
        json.put("fillColor", colorToHex(fillColor));
        json.put("emptyColor", colorToHex(emptyColor));
        json.put("useGradient", useGradient);
        json.put("gradientEndColor", colorToHex(gradientEndColor));
        json.put("direction", direction.name());
        json.put("textMode", textMode.name());
        json.put("customText", customText);
        json.put("smoothAnimation", smoothAnimation);
        json.put("smoothSpeed", smoothSpeed);
        json.put("innerPadding", innerPadding);
        return json;
    }
    
    public static UIProgressBar fromJSON(JSONObject json) {
        UIProgressBar bar = new UIProgressBar();
        bar.loadFromJSON(json);
        bar.value = (float) json.optDouble("value", 0.5);
        bar.currentValue = (float) json.optDouble("currentValue", 50);
        bar.maxValue = (float) json.optDouble("maxValue", 100);
        bar.fillColor = hexToColor(json.optString("fillColor", "#46C846"));
        bar.emptyColor = hexToColor(json.optString("emptyColor", "#323232"));
        bar.useGradient = json.optBoolean("useGradient", false);
        bar.gradientEndColor = hexToColor(json.optString("gradientEndColor", "#C84646"));
        try {
            bar.direction = Direction.valueOf(json.optString("direction", "LEFT_TO_RIGHT"));
        } catch (Exception e) { bar.direction = Direction.LEFT_TO_RIGHT; }
        try {
            bar.textMode = TextMode.valueOf(json.optString("textMode", "NONE"));
        } catch (Exception e) { bar.textMode = TextMode.NONE; }
        bar.customText = json.optString("customText", "");
        bar.smoothAnimation = json.optBoolean("smoothAnimation", false);
        bar.smoothSpeed = (float) json.optDouble("smoothSpeed", 0.1);
        bar.innerPadding = json.optInt("innerPadding", 2);
        return bar;
    }
}
