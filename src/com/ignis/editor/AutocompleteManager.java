package com.ignis.editor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutocompleteManager {

    private final JTextPane textArea;
    private final JWindow popup;
    private final JList<String> list;
    private final DefaultListModel<String> listModel;
    private boolean enabled = true;

    // Database of suggestion words
    private static final List<String> KEYWORDS = Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", 
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", 
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", 
        "interface", "long", "native", "new", "package", "private", "protected", "public", 
        "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", 
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
    );

    private static final List<String> ENGINE_CLASSES = Arrays.asList(
        "GameObject", "Input", "IgnisScript", "Animator", "Camera", "Color", "Vector2", "Game", "Scene", "Sound", "Sprite"
    );

    private static final List<String> IGNIS_SCRIPT_METHODS = Arrays.asList(
        "getX()", "getY()", "setPosition(x, y)", "getRotation()", "setRotation(degrees)", 
        "getWidth()", "getHeight()", "getDeltaTime()", "println(message)", "print(message)",
        "isKeyPressed(keyCode)", "isKeyPressed(keyName)", "isKeyJustPressed(keyCode)", 
        "isKeyJustPressed(keyName)", "isKeyJustReleased(keyCode)", "isKeyJustReleased(keyName)",
        "getHorizontalAxis()", "getVerticalAxis()", "getMouseX()", "getMouseY()", 
        "isMouseLeftPressed()", "isMouseRightPressed()", "move(dx, dy)", 
        "moveTowards(targetX, targetY, speed)", "rotate(degrees)", "lookAt(targetX, targetY)", 
        "move(dx, dy, space)", "moveForward(amount)", "moveBackward(amount)", "strafeRight(amount)", 
        "strafeLeft(amount)", "getForwardDirection()", "getRightDirection()", "distanceTo(other)", 
        "isColliding(other)", "findObject(name)", "findObjectsByType(type)", "destroy()", 
        "destroy(obj)", "log(message)", "playSound(filePath)", "playSound(filePath, volume)", 
        "playSoundWithCallback(filePath, onComplete)", "stopAllSounds()", "playMusic(filePath)", 
        "playMusic(filePath, loop)", "pauseMusic()", "resumeMusic()", "stopMusic()", 
        "isMusicPlaying()", "setMasterVolume(volume)", "setMusicVolume(volume)", "setSfxVolume(volume)",
        "getCamera()", "getCameraX()", "getCameraY()", "setCameraPosition(x, y)", "moveCamera(dx, dy)", 
        "cameraFollowThis()", "cameraFollowThis(smoothness)", "cameraFollow(target)", 
        "cameraFollow(target, smoothness)"
    );

    private static final List<String> GAMEOBJECT_METHODS = Arrays.asList(
        "getX()", "getY()", "setX(x)", "setY(y)", "getWidth()", "getHeight()", "setWidth(w)", 
        "setHeight(h)", "getName()", "setName(name)", "getScaleX()", "getScaleY()", 
        "setScaleX(sx)", "setScaleY(sy)", "getRotation()", "setRotation(r)", "getComponent(type)",
        "addComponent(c)", "removeComponent(c)", "getScript(name)", "addScript(name)", 
        "removeScriptByName(name)"
    );

    private static final List<String> INPUT_METHODS = Arrays.asList(
        "isKeyPressed(keyCode)", "isKeyJustPressed(keyCode)", "isKeyJustReleased(keyCode)", 
        "getMouseX()", "getMouseY()", "isMouseLeftPressed()", "isMouseRightPressed()",
        "getHorizontalAxis()", "getVerticalAxis()"
    );

    public AutocompleteManager(JTextPane textArea, JFrame ownerFrame) {
        this.textArea = textArea;
        this.popup = new JWindow(ownerFrame);
        this.listModel = new DefaultListModel<>();
        this.list = new JList<>(listModel);

        setupPopupUI();
        setupListeners();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            hidePopup();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void setupPopupUI() {
        list.setBackground(new Color(45, 45, 45));
        list.setForeground(new Color(220, 220, 220));
        list.setSelectionBackground(new Color(0, 120, 215));
        list.setSelectionForeground(Color.WHITE);
        list.setFont(new Font("Consolas", Font.PLAIN, 13));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        popup.add(scrollPane);
        popup.setSize(250, 150);
        popup.setFocusable(false);
    }

    private void setupListeners() {
        // Hide popup when text area loses focus or parent window moves/resizes
        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Delay hiding slightly to allow mouse clicks on the JList (if any)
                SwingUtilities.invokeLater(() -> hidePopup());
            }
        });

        Component parent = textArea.getTopLevelAncestor();
        if (parent instanceof Window) {
            ((Window) parent).addComponentListener(new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent e) {
                    hidePopup();
                }

                @Override
                public void componentResized(ComponentEvent e) {
                    hidePopup();
                }
            });
        }

        // Mouse click on text area hides the popup
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                hidePopup();
            }
        });

        // Key interception in JTextPane
        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!enabled || !popup.isVisible()) return;

                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_UP) {
                    int index = list.getSelectedIndex();
                    if (index > 0) {
                        list.setSelectedIndex(index - 1);
                        list.ensureIndexIsVisible(index - 1);
                    }
                    e.consume();
                } else if (keyCode == KeyEvent.VK_DOWN) {
                    int index = list.getSelectedIndex();
                    if (index < listModel.getSize() - 1) {
                        list.setSelectedIndex(index + 1);
                        list.ensureIndexIsVisible(index + 1);
                    }
                    e.consume();
                } else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_TAB) {
                    insertSelectedSuggestion();
                    e.consume();
                } else if (keyCode == KeyEvent.VK_ESCAPE ||
                           keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
                           keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
                           keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN) {
                    hidePopup();
                    if (keyCode == KeyEvent.VK_ESCAPE) {
                        e.consume();
                    }
                }
            }
        });

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (enabled) {
                    SwingUtilities.invokeLater(() -> checkAutocomplete());
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (enabled) {
                    SwingUtilities.invokeLater(() -> checkAutocomplete());
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });

        // Mouse click on JList selection
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    insertSelectedSuggestion();
                }
            }
        });
    }

    private void checkAutocomplete() {
        if (!enabled) return;

        String text = textArea.getText();
        int pos = textArea.getCaretPosition();
        if (pos > text.length()) {
            pos = text.length();
        }
        if (pos <= 0) {
            hidePopup();
            return;
        }
        
        // Find prefix being typed
        int start = pos - 1;
        while (start >= 0 && isWordChar(text.charAt(start))) {
            start--;
        }
        
        String prefix = text.substring(start + 1, pos);
        
        // Check if there's a dot operator before the prefix
        boolean hasDot = false;
        String objectName = "";
        if (start >= 0 && text.charAt(start) == '.') {
            hasDot = true;
            // Trace the word before the dot
            int objStart = start - 1;
            while (objStart >= 0 && isWordChar(text.charAt(objStart))) {
                objStart--;
            }
            objectName = text.substring(objStart + 1, start);
        }

        List<String> suggestions = new ArrayList<>();

        if (hasDot) {
            // Context sensitive autocomplete
            if (objectName.equalsIgnoreCase("gameobject")) {
                for (String m : GAMEOBJECT_METHODS) {
                    if (m.toLowerCase().startsWith(prefix.toLowerCase())) {
                        suggestions.add(m);
                    }
                }
            } else if (objectName.equalsIgnoreCase("input")) {
                for (String m : INPUT_METHODS) {
                    if (m.toLowerCase().startsWith(prefix.toLowerCase())) {
                        suggestions.add(m);
                    }
                }
            } else {
                // If it's this. or some other object, combine all methods
                List<String> combined = new ArrayList<>();
                combined.addAll(IGNIS_SCRIPT_METHODS);
                combined.addAll(GAMEOBJECT_METHODS);
                combined.addAll(getLocalMethods());
                for (String m : combined) {
                    if (m.toLowerCase().startsWith(prefix.toLowerCase()) && !suggestions.contains(m)) {
                        suggestions.add(m);
                    }
                }
            }
        } else {
            // General autocomplete
            if (prefix.length() >= 1) {
                // Keywords
                for (String kw : KEYWORDS) {
                    if (kw.startsWith(prefix)) {
                        suggestions.add(kw);
                    }
                }
                // Classes
                for (String ec : ENGINE_CLASSES) {
                    if (ec.toLowerCase().startsWith(prefix.toLowerCase())) {
                        suggestions.add(ec);
                    }
                }
                // Script methods
                for (String sm : IGNIS_SCRIPT_METHODS) {
                    if (sm.toLowerCase().startsWith(prefix.toLowerCase())) {
                        suggestions.add(sm);
                    }
                }
                // Local variables and methods
                for (String lv : getLocalVariablesAndFields()) {
                    if (lv.toLowerCase().startsWith(prefix.toLowerCase()) && !suggestions.contains(lv)) {
                        suggestions.add(lv);
                    }
                }
                for (String lm : getLocalMethods()) {
                    if (lm.toLowerCase().startsWith(prefix.toLowerCase()) && !suggestions.contains(lm)) {
                        suggestions.add(lm);
                    }
                }
            }
        }

        if (suggestions.isEmpty()) {
            hidePopup();
        } else {
            showPopup(suggestions, prefix);
            if (textArea instanceof EditorTextPane) {
                String topSuggestion = suggestions.get(0);
                // Calculate inline ghost text
                if (topSuggestion.toLowerCase().startsWith(prefix.toLowerCase())) {
                    String ghost = topSuggestion.substring(prefix.length());
                    ((EditorTextPane) textArea).setGhostText(ghost);
                } else {
                    ((EditorTextPane) textArea).setGhostText("");
                }
            }
        }
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void showPopup(List<String> suggestions, String prefix) {
        listModel.clear();
        for (String s : suggestions) {
            listModel.addElement(s);
        }
        list.setSelectedIndex(0);

        try {
            int pos = textArea.getCaretPosition();
            Rectangle rect = null;
            try {
                rect = textArea.modelToView(pos);
            } catch (Exception ex) {
                try {
                    rect = textArea.modelToView2D(pos).getBounds();
                } catch (Exception ex2) {}
            }

            if (rect != null && textArea.isShowing()) {
                Point p = textArea.getLocationOnScreen();
                // Position popup below the cursor
                popup.setLocation(p.x + rect.x, p.y + rect.y + rect.height + 2);
                popup.setVisible(true);
            }
        } catch (Exception e) {
            hidePopup();
        }
    }

    public void hidePopup() {
        if (popup.isVisible()) {
            popup.setVisible(false);
        }
        if (textArea instanceof EditorTextPane) {
            ((EditorTextPane) textArea).setGhostText("");
        }
    }

    private void insertSelectedSuggestion() {
        String selected = list.getSelectedValue();
        if (selected == null) return;

        String text = textArea.getText();
        int pos = textArea.getCaretPosition();
        if (pos > text.length()) {
            pos = text.length();
        }
        
        // Find how much prefix is typed
        int start = pos - 1;
        while (start >= 0 && start < text.length() && isWordChar(text.charAt(start))) {
            start--;
        }
        
        String prefix = text.substring(start + 1, pos);
        
        // Clean suggestion parameters (e.g. "setX(x)" -> "setX()")
        String insertText = selected;
        int parenIndex = selected.indexOf('(');
        boolean hasParams = false;
        if (parenIndex != -1) {
            insertText = selected.substring(0, parenIndex + 1) + ")";
            if (selected.charAt(parenIndex + 1) != ')') {
                hasParams = true;
            }
        }

        try {
            javax.swing.text.Document doc = textArea.getDocument();
            doc.remove(start + 1, pos - (start + 1));
            doc.insertString(start + 1, insertText, null);
            
            // Position caret inside parenthesis if there are parameters
            if (hasParams) {
                textArea.setCaretPosition(start + 1 + parenIndex + 1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        hidePopup();
    }

    // --- Parser methods to extract fields/variables/methods of the current class ---

    private List<String> getLocalVariablesAndFields() {
        List<String> locals = new ArrayList<>();
        String text = textArea.getText();

        // 1. Match fields (class level variables)
        Pattern fieldPattern = Pattern.compile("\\b(?:private|public|protected|static|final)?\\s+[a-zA-Z0-9_<>]+\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|;)");
        Matcher matcher = fieldPattern.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!locals.contains(name) && !KEYWORDS.contains(name)) {
                locals.add(name);
            }
        }

        // 2. Match local variables in methods
        Pattern localVarPattern = Pattern.compile("\\b(?:int|float|double|boolean|String|char|var|GameObject|Vector2)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|;)");
        matcher = localVarPattern.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!locals.contains(name) && !KEYWORDS.contains(name)) {
                locals.add(name);
            }
        }

        return locals;
    }

    private List<String> getLocalMethods() {
        List<String> methods = new ArrayList<>();
        String text = textArea.getText();

        // Match method declarations, e.g. public void update()
        Pattern methodPattern = Pattern.compile("\\b(?:public|private|protected|static|void|int|float|double|boolean|String)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)");
        Matcher matcher = methodPattern.matcher(text);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            String params = matcher.group(2).trim();
            // Format as update() or update(dt)
            String entry = methodName + "(" + (params.isEmpty() ? "" : params) + ")";
            if (!methods.contains(entry) && !methodName.equals("if") && !methodName.equals("for") && !methodName.equals("while") && !methodName.equals("switch")) {
                methods.add(entry);
            }
        }

        return methods;
    }
}
