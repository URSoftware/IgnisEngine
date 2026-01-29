package com.ignis.core.ui;

/**
 * UIToggle - Alias para UICheckbox.
 * 
 * Toggle e Checkbox são funcionalmente idênticos nesta engine.
 * Esta classe existe para compatibilidade e clareza semântica.
 * 
 * Exemplo:
 * ```java
 * UIToggle sound = new UIToggle("Sound Effects", 100, 100);
 * sound.setChecked(true);
 * sound.setOnValueChange(value -> {
 *     setSoundEnabled(Boolean.parseBoolean(value));
 * });
 * canvas.addChild(sound);
 * ```
 */
public class UIToggle extends UICheckbox {
    
    public UIToggle() {
        super();
    }
    
    public UIToggle(String label) {
        super(label);
    }
    
    public UIToggle(String label, double x, double y) {
        super(label, x, y);
    }
    
    public UIToggle(String label, double x, double y, double width, double height) {
        super(label, x, y);
        setSize(width, height);
    }
    
    @Override
    public String getType() {
        return "Toggle";
    }
}
