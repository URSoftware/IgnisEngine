package com.ignis.core.ui;

import com.ignis.core.IgnisLogger;

import java.awt.*;
import org.json.JSONObject;

/**
 * UIButton - Botão clicável para interface do usuário.
 * 
 * Características:
 * - Texto centralizado
 * - Estados visuais (normal, hover, pressed, disabled)
 * - Callback de clique
 * - Estilização completa
 * 
 * Exemplo:
 * ```java
 * UIButton button = new UIButton("Jogar", 100, 100, 200, 50);
 * button.setBackgroundColor(new Color(70, 130, 180));
 * button.setOnClick(() -> startGame());
 * canvas.addChild(button);
 * ```
 */
public class UIButton extends UIComponent {

    // ==================== CAMPOS ====================
    
    private String text = "Button";
    
    /** Cores para diferentes estados */
    private Color normalColor = new Color(70, 70, 70);
    private Color hoverColor = new Color(90, 90, 90);
    private Color pressedColor = new Color(50, 50, 50);
    private Color disabledColor = new Color(50, 50, 50);
    private Color disabledTextColor = new Color(128, 128, 128);
    
    /** Ícone opcional (caminho ou nome) */
    private String iconPath;
    private transient Image iconImage;
    private int iconSize = 20;
    private int iconPadding = 8;
    
    // ==================== CONSTRUTORES ====================
    
    public UIButton() {
        super("Button");
        initDefaults();
    }
    
    public UIButton(String text) {
        super("Button");
        this.text = text;
        initDefaults();
    }
    
    public UIButton(String text, double x, double y, double width, double height) {
        super("Button", x, y, width, height);
        this.text = text;
        initDefaults();
    }
    
    private void initDefaults() {
        this.interactive = true;
        this.backgroundColor = normalColor;
        this.borderRadius = 5;
        this.borderWidth = 1;
        this.borderColor = new Color(100, 100, 100);
        this.textColor = Color.WHITE;
        this.fontSize = 14;
        this.fontStyle = Font.BOLD;
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        // Determinar cor baseada no estado
        Color bgColor;
        Color txtColor = textColor;
        
        if (!enabled) {
            bgColor = disabledColor;
            txtColor = disabledTextColor;
        } else if (pressed) {
            bgColor = pressedColor;
        } else if (hovered) {
            bgColor = hoverColor;
        } else {
            bgColor = normalColor;
        }
        
        // Desenhar fundo
        g.setColor(bgColor);
        if (borderRadius > 0) {
            g.fillRoundRect(0, 0, (int) width, (int) height, borderRadius, borderRadius);
        } else {
            g.fillRect(0, 0, (int) width, (int) height);
        }
        
        // Desenhar borda
        if (borderWidth > 0) {
            g.setColor(borderColor);
            g.setStroke(new BasicStroke(borderWidth));
            if (borderRadius > 0) {
                g.drawRoundRect(0, 0, (int) width - 1, (int) height - 1, borderRadius, borderRadius);
            } else {
                g.drawRect(0, 0, (int) width - 1, (int) height - 1);
            }
        }
        
        // Calcular posição do texto e ícone
        g.setColor(txtColor);
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        
        int textWidth = fm.stringWidth(text);
        int totalWidth = textWidth;
        int iconX = 0;
        
        // Se há ícone
        if (iconImage != null) {
            totalWidth += iconSize + iconPadding;
        }
        
        int startX = (int) ((width - totalWidth) / 2);
        
        // Desenhar ícone
        if (iconImage != null) {
            int iconY = (int) ((height - iconSize) / 2);
            g.drawImage(iconImage, startX, iconY, iconSize, iconSize, null);
            startX += iconSize + iconPadding;
        }
        
        // Desenhar texto
        int textX = startX;
        int textY = (int) ((height + fm.getAscent() - fm.getDescent()) / 2);
        
        // Efeito de press (mover texto levemente para baixo)
        if (pressed) {
            textY += 1;
        }
        
        g.drawString(text, textX, textY);
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public Color getNormalColor() { return normalColor; }
    public void setNormalColor(Color color) { this.normalColor = color; }
    
    public Color getHoverColor() { return hoverColor; }
    public void setHoverColor(Color color) { this.hoverColor = color; }
    
    public Color getPressedColor() { return pressedColor; }
    public void setPressedColor(Color color) { this.pressedColor = color; }
    
    public Color getDisabledColor() { return disabledColor; }
    public void setDisabledColor(Color color) { this.disabledColor = color; }
    
    public String getIconPath() { return iconPath; }
    public void setIconPath(String path) {
        this.iconPath = path;
        loadIcon();
    }
    
    /**
     * Define o esquema de cores do botão.
     * @param normal Cor normal
     * @param hover Cor ao passar o mouse
     * @param pressed Cor ao clicar
     */
    public void setColorScheme(Color normal, Color hover, Color pressed) {
        this.normalColor = normal;
        this.hoverColor = hover;
        this.pressedColor = pressed;
    }
    
    /**
     * Aplica um estilo predefinido.
     * @param style Nome do estilo ("primary", "secondary", "success", "danger", "warning")
     */
    public void applyStyle(String style) {
        switch (style.toLowerCase()) {
            case "primary":
                setColorScheme(
                    new Color(0, 120, 215),
                    new Color(30, 140, 235),
                    new Color(0, 90, 180)
                );
                break;
            case "secondary":
                setColorScheme(
                    new Color(108, 117, 125),
                    new Color(128, 137, 145),
                    new Color(88, 97, 105)
                );
                break;
            case "success":
                setColorScheme(
                    new Color(40, 167, 69),
                    new Color(60, 187, 89),
                    new Color(30, 147, 59)
                );
                break;
            case "danger":
                setColorScheme(
                    new Color(220, 53, 69),
                    new Color(240, 73, 89),
                    new Color(200, 43, 59)
                );
                break;
            case "warning":
                setColorScheme(
                    new Color(255, 193, 7),
                    new Color(255, 213, 57),
                    new Color(225, 163, 0)
                );
                textColor = Color.BLACK;
                break;
            case "dark":
                setColorScheme(
                    new Color(52, 58, 64),
                    new Color(72, 78, 84),
                    new Color(42, 48, 54)
                );
                break;
        }
    }
    
    private void loadIcon() {
        if (iconPath == null || iconPath.isEmpty()) {
            iconImage = null;
            return;
        }
        try {
            iconImage = javax.imageio.ImageIO.read(new java.io.File(iconPath));
        } catch (Exception e) {
            IgnisLogger.error("[UIButton] Erro ao carregar ícone: " + e.getMessage());
            iconImage = null;
        }
    }
    
    @Override
    public String getType() {
        return "Button";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("text", text);
        json.put("normalColor", colorToHex(normalColor));
        json.put("hoverColor", colorToHex(hoverColor));
        json.put("pressedColor", colorToHex(pressedColor));
        json.put("disabledColor", colorToHex(disabledColor));
        if (iconPath != null) json.put("iconPath", iconPath);
        json.put("iconSize", iconSize);
        return json;
    }
    
    /**
     * Cria um UIButton a partir de JSON.
     */
    public static UIButton fromJSON(JSONObject json) {
        UIButton button = new UIButton();
        button.loadFromJSON(json);
        button.text = json.optString("text", "Button");
        button.normalColor = hexToColor(json.optString("normalColor", "#464646"));
        button.hoverColor = hexToColor(json.optString("hoverColor", "#5A5A5A"));
        button.pressedColor = hexToColor(json.optString("pressedColor", "#323232"));
        button.disabledColor = hexToColor(json.optString("disabledColor", "#323232"));
        button.iconPath = json.optString("iconPath", null);
        button.iconSize = json.optInt("iconSize", 20);
        if (button.iconPath != null) button.loadIcon();
        return button;
    }
}
