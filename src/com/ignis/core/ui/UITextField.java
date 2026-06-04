package com.ignis.core.ui;

import java.awt.*;
import java.awt.event.*;
import org.json.JSONObject;

/**
 * UITextField - Campo de entrada de texto para interface do usuário.
 * 
 * Características:
 * - Entrada de texto
 * - Placeholder
 * - Validação
 * - Diferentes tipos (text, password, number)
 * - Callback de mudança de valor
 * 
 * Exemplo:
 * ```java
 * UITextField nameField = new UITextField(100, 100, 200, 30);
 * nameField.setPlaceholder("Digite seu nome...");
 * nameField.setOnValueChange(text -> {
 *     System.out.println("Nome: " + text);
 * });
 * canvas.addChild(nameField);
 * ```
 */
public class UITextField extends UIComponent {

    // ==================== ENUMS ====================
    
    public enum InputType {
        TEXT,
        PASSWORD,
        NUMBER,
        EMAIL
    }
    
    // ==================== CAMPOS ====================
    
    private String text = "";
    private String placeholder = "";
    private InputType inputType = InputType.TEXT;
    
    /** Cor do placeholder */
    private Color placeholderColor = new Color(128, 128, 128);
    
    /** Máximo de caracteres (0 = sem limite) */
    private int maxLength = 0;
    
    /** Caractere para senha */
    private char passwordChar = '•';
    
    /** Seleção de texto */
    private int cursorPosition = 0;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    
    /** Estado de edição */
    private boolean editing = false;
    
    /** Cursor piscando */
    private long lastCursorBlink = 0;
    private boolean cursorVisible = true;
    private static final long CURSOR_BLINK_MS = 500;
    
    /** Cor de seleção */
    private Color selectionColor = new Color(0, 120, 215, 128);
    
    /** Scroll horizontal para texto longo */
    private int scrollOffset = 0;
    
    // ==================== CONSTRUTORES ====================
    
    public UITextField() {
        super("TextField");
        initDefaults();
    }
    
    public UITextField(double x, double y, double width, double height) {
        super("TextField", x, y, width, height);
        initDefaults();
    }
    
    public UITextField(String placeholder, double x, double y, double width, double height) {
        super("TextField", x, y, width, height);
        initDefaults();
        this.placeholder = placeholder;
    }
    
    private void initDefaults() {
        this.interactive = true;
        this.backgroundColor = new Color(255, 255, 255);
        this.textColor = Color.BLACK;
        this.borderRadius = 4;
        this.borderWidth = 1;
        this.borderColor = new Color(180, 180, 180);
        this.paddingLeft = 8;
        this.paddingRight = 8;
        this.fontSize = 14;
    }
    
    // ==================== ATUALIZAÇÃO ====================
    
    @Override
    public void update() {
        super.update();
        
        // Atualizar cursor piscando
        if (editing) {
            long now = System.currentTimeMillis();
            if (now - lastCursorBlink > CURSOR_BLINK_MS) {
                cursorVisible = !cursorVisible;
                lastCursorBlink = now;
            }
        }
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        // Cor de fundo baseada no estado
        Color bg = backgroundColor;
        Color border = borderColor;
        
        if (!enabled) {
            bg = new Color(240, 240, 240);
            border = new Color(200, 200, 200);
        } else if (editing) {
            border = new Color(0, 120, 215);
        } else if (hovered) {
            border = new Color(0, 90, 180);
        }
        
        // Desenhar fundo
        g.setColor(bg);
        if (borderRadius > 0) {
            g.fillRoundRect(0, 0, (int) width, (int) height, borderRadius, borderRadius);
        } else {
            g.fillRect(0, 0, (int) width, (int) height);
        }
        
        // Desenhar borda
        if (borderWidth > 0) {
            g.setColor(border);
            g.setStroke(new BasicStroke(editing ? 2 : borderWidth));
            if (borderRadius > 0) {
                g.drawRoundRect(0, 0, (int) width - 1, (int) height - 1, borderRadius, borderRadius);
            } else {
                g.drawRect(0, 0, (int) width - 1, (int) height - 1);
            }
        }
        
        // Configurar área de texto
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        int textY = (int) ((height + fm.getAscent() - fm.getDescent()) / 2);
        int textAreaWidth = (int) (width - paddingLeft - paddingRight);
        
        // Clipar área de texto
        Shape oldClip = g.getClip();
        g.clipRect(paddingLeft, 0, textAreaWidth, (int) height);
        
        // Texto a exibir
        String displayText = getDisplayText();
        
        // Desenhar seleção se houver
        if (editing && hasSelection()) {
            int selStart = Math.min(selectionStart, selectionEnd);
            int selEnd = Math.max(selectionStart, selectionEnd);
            String beforeSel = displayText.substring(0, selStart);
            String selection = displayText.substring(selStart, selEnd);
            int selX = paddingLeft + fm.stringWidth(beforeSel) - scrollOffset;
            int selW = fm.stringWidth(selection);
            
            g.setColor(selectionColor);
            g.fillRect(selX, 2, selW, (int) height - 4);
        }
        
        // Desenhar texto ou placeholder
        int textX = paddingLeft - scrollOffset;
        
        if (text.isEmpty() && !editing) {
            g.setColor(placeholderColor);
            g.drawString(placeholder, textX, textY);
        } else {
            g.setColor(enabled ? textColor : new Color(128, 128, 128));
            g.drawString(displayText, textX, textY);
        }
        
        // Desenhar cursor
        if (editing && cursorVisible) {
            String beforeCursor = displayText.substring(0, cursorPosition);
            int cursorX = paddingLeft + fm.stringWidth(beforeCursor) - scrollOffset;
            g.setColor(textColor);
            g.drawLine(cursorX, 4, cursorX, (int) height - 4);
        }
        
        g.setClip(oldClip);
    }
    
    private String getDisplayText() {
        if (inputType == InputType.PASSWORD) {
            return String.valueOf(passwordChar).repeat(text.length());
        }
        return text;
    }
    
    // ==================== INTERAÇÃO ====================
    
    @Override
    public boolean handleMouseClick(double mx, double my, boolean pressed) {
        if (!visible || !enabled) return false;
        
        boolean inside = containsPoint(mx, my);
        
        if (pressed && inside) {
            // Iniciar edição
            editing = true;
            focused = true;
            cursorVisible = true;
            lastCursorBlink = System.currentTimeMillis();
            
            // Posicionar cursor no clique
            Rectangle bounds = getAbsoluteBounds();
            int clickX = (int) (mx - bounds.x - paddingLeft + scrollOffset);
            FontMetrics fm = new Canvas().getFontMetrics(getFont());
            
            cursorPosition = text.length();
            for (int i = 0; i < text.length(); i++) {
                int charX = fm.stringWidth(text.substring(0, i + 1));
                if (clickX < charX - fm.charWidth(text.charAt(i)) / 2) {
                    cursorPosition = i;
                    break;
                }
            }
            
            clearSelection();
            return true;
        } else if (pressed && !inside && editing) {
            // Finalizar edição
            finishEditing();
        }
        
        return inside;
    }
    
    /**
     * Processa entrada de tecla.
     * @param e Evento de tecla
     * @return true se processou
     */
    public boolean handleKeyPressed(KeyEvent e) {
        if (!editing || !enabled) return false;
        
        int keyCode = e.getKeyCode();
        boolean ctrl = e.isControlDown();
        boolean shift = e.isShiftDown();
        
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
                finishEditing();
                return true;
                
            case KeyEvent.VK_ENTER:
                finishEditing();
                return true;
                
            case KeyEvent.VK_BACK_SPACE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                    cursorPosition--;
                    onTextChanged();
                }
                return true;
                
            case KeyEvent.VK_DELETE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < text.length()) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                    onTextChanged();
                }
                return true;
                
            case KeyEvent.VK_LEFT:
                if (shift) {
                    extendSelection(-1);
                } else {
                    if (hasSelection()) {
                        cursorPosition = Math.min(selectionStart, selectionEnd);
                        clearSelection();
                    } else if (cursorPosition > 0) {
                        cursorPosition--;
                    }
                }
                resetCursorBlink();
                return true;
                
            case KeyEvent.VK_RIGHT:
                if (shift) {
                    extendSelection(1);
                } else {
                    if (hasSelection()) {
                        cursorPosition = Math.max(selectionStart, selectionEnd);
                        clearSelection();
                    } else if (cursorPosition < text.length()) {
                        cursorPosition++;
                    }
                }
                resetCursorBlink();
                return true;
                
            case KeyEvent.VK_HOME:
                if (shift) {
                    if (selectionStart < 0) selectionStart = cursorPosition;
                    selectionEnd = 0;
                    cursorPosition = 0;
                } else {
                    cursorPosition = 0;
                    clearSelection();
                }
                resetCursorBlink();
                return true;
                
            case KeyEvent.VK_END:
                if (shift) {
                    if (selectionStart < 0) selectionStart = cursorPosition;
                    selectionEnd = text.length();
                    cursorPosition = text.length();
                } else {
                    cursorPosition = text.length();
                    clearSelection();
                }
                resetCursorBlink();
                return true;
                
            case KeyEvent.VK_A:
                if (ctrl) {
                    selectAll();
                    return true;
                }
                break;
                
            case KeyEvent.VK_C:
                if (ctrl && hasSelection()) {
                    copySelection();
                    return true;
                }
                break;
                
            case KeyEvent.VK_V:
                if (ctrl) {
                    paste();
                    return true;
                }
                break;
                
            case KeyEvent.VK_X:
                if (ctrl && hasSelection()) {
                    copySelection();
                    deleteSelection();
                    return true;
                }
                break;
        }
        
        return false;
    }
    
    /**
     * Processa caractere digitado.
     * @param e Evento de tecla
     * @return true se processou
     */
    public boolean handleKeyTyped(KeyEvent e) {
        if (!editing || !enabled) return false;
        
        char c = e.getKeyChar();
        
        // Ignorar caracteres de controle
        if (Character.isISOControl(c)) return false;
        
        // Validar para número
        if (inputType == InputType.NUMBER) {
            if (!Character.isDigit(c) && c != '.' && c != '-') {
                return true;
            }
        }
        
        // Verificar limite
        if (maxLength > 0 && text.length() >= maxLength && !hasSelection()) {
            return true;
        }
        
        // Deletar seleção
        if (hasSelection()) {
            deleteSelection();
        }
        
        // Inserir caractere
        text = text.substring(0, cursorPosition) + c + text.substring(cursorPosition);
        cursorPosition++;
        onTextChanged();
        resetCursorBlink();
        
        return true;
    }
    
    // ==================== SELEÇÃO ====================
    
    private boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
    }
    
    private void clearSelection() {
        selectionStart = -1;
        selectionEnd = -1;
    }
    
    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        text = text.substring(0, start) + text.substring(end);
        cursorPosition = start;
        clearSelection();
        onTextChanged();
    }
    
    private void extendSelection(int direction) {
        if (selectionStart < 0) {
            selectionStart = cursorPosition;
        }
        cursorPosition = Math.max(0, Math.min(text.length(), cursorPosition + direction));
        selectionEnd = cursorPosition;
    }
    
    public void selectAll() {
        selectionStart = 0;
        selectionEnd = text.length();
        cursorPosition = text.length();
    }
    
    private void copySelection() {
        if (!hasSelection()) return;
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        String selected = text.substring(start, end);
        
        try {
            java.awt.datatransfer.Clipboard clipboard = 
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new java.awt.datatransfer.StringSelection(selected), null);
        } catch (Exception e) {
            // Ignorar erro de clipboard
        }
    }
    
    private void paste() {
        try {
            java.awt.datatransfer.Clipboard clipboard = 
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            String data = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            
            if (data != null && !data.isEmpty()) {
                if (hasSelection()) {
                    deleteSelection();
                }
                
                // Validar para número
                if (inputType == InputType.NUMBER) {
                    data = data.replaceAll("[^0-9.-]", "");
                }
                
                // Respeitar limite
                if (maxLength > 0) {
                    int available = maxLength - text.length();
                    if (data.length() > available) {
                        data = data.substring(0, available);
                    }
                }
                
                text = text.substring(0, cursorPosition) + data + text.substring(cursorPosition);
                cursorPosition += data.length();
                onTextChanged();
            }
        } catch (Exception e) {
            // Ignorar erro de clipboard
        }
    }
    
    // ==================== AUXILIARES ====================
    
    private void resetCursorBlink() {
        cursorVisible = true;
        lastCursorBlink = System.currentTimeMillis();
    }
    
    private void finishEditing() {
        editing = false;
        focused = false;
        clearSelection();
    }
    
    private void onTextChanged() {
        if (onValueChange != null) {
            onValueChange.accept(text);
        }
        updateScrollOffset();
    }
    
    private void updateScrollOffset() {
        FontMetrics fm = new Canvas().getFontMetrics(getFont());
        int textWidth = fm.stringWidth(text);
        int textAreaWidth = (int) (width - paddingLeft - paddingRight);
        
        if (textWidth <= textAreaWidth) {
            scrollOffset = 0;
        } else {
            String beforeCursor = text.substring(0, cursorPosition);
            int cursorX = fm.stringWidth(beforeCursor);
            
            if (cursorX - scrollOffset < 0) {
                scrollOffset = cursorX;
            } else if (cursorX - scrollOffset > textAreaWidth - 10) {
                scrollOffset = cursorX - textAreaWidth + 10;
            }
        }
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public String getText() { return text; }
    public void setText(String text) { 
        this.text = text != null ? text : ""; 
        this.cursorPosition = this.text.length();
        clearSelection();
    }
    
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    
    public InputType getInputType() { return inputType; }
    public void setInputType(InputType type) { this.inputType = type; }
    
    public Color getPlaceholderColor() { return placeholderColor; }
    public void setPlaceholderColor(Color color) { this.placeholderColor = color; }
    
    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int max) { this.maxLength = max; }
    
    public char getPasswordChar() { return passwordChar; }
    public void setPasswordChar(char c) { this.passwordChar = c; }
    
    public boolean isEditing() { return editing; }
    
    @Override
    public String getType() {
        return "TextField";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("text", text);
        json.put("placeholder", placeholder);
        json.put("inputType", inputType.name());
        json.put("placeholderColor", colorToHex(placeholderColor));
        json.put("maxLength", maxLength);
        return json;
    }
    
    public static UITextField fromJSON(JSONObject json) {
        UITextField field = new UITextField();
        field.loadFromJSON(json);
        field.text = json.optString("text", "");
        field.placeholder = json.optString("placeholder", "");
        try {
            field.inputType = InputType.valueOf(json.optString("inputType", "TEXT"));
        } catch (Exception e) { field.inputType = InputType.TEXT; }
        field.placeholderColor = hexToColor(json.optString("placeholderColor", "#808080"));
        field.maxLength = json.optInt("maxLength", 0);
        return field;
    }
}
