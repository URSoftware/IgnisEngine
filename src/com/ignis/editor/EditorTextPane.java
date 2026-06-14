package com.ignis.editor;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A customized JTextPane for script editing with:
 * - Gray translucent ghost text previewing autocompletions next to caret
 * - Intercepting Tab key to accept the ghost text
 * - Debounced Java syntax highlighting using a custom regex pattern
 * - Editor theme presets with JSON import/export support
 */
public class EditorTextPane extends JTextPane {

    public static class EditorTheme {
        public String name;
        public Color background;
        public Color foreground;
        public Color caret;
        public Color keyword;
        public Color type;
        public Color number;
        public Color string;
        public Color comment;
        public Color annotation;

        public EditorTheme(String name, Color background, Color foreground, Color caret,
                           Color keyword, Color type, Color number, Color string, Color comment, Color annotation) {
            this.name = name;
            this.background = background;
            this.foreground = foreground;
            this.caret = caret;
            this.keyword = keyword;
            this.type = type;
            this.number = number;
            this.string = string;
            this.comment = comment;
            this.annotation = annotation;
        }
    }

    // Preset Themes
    public static final EditorTheme DRACULA = new EditorTheme(
        "Dracula",
        new Color(40, 42, 54),
        new Color(248, 248, 242),
        new Color(248, 248, 242),
        new Color(255, 121, 198), // keyword
        new Color(139, 233, 253), // type
        new Color(189, 147, 249), // number
        new Color(241, 250, 140), // string
        new Color(98, 114, 164),  // comment
        new Color(80, 250, 123)   // annotation
    );

    public static final EditorTheme MONOKAI = new EditorTheme(
        "Monokai",
        new Color(39, 40, 34),
        new Color(248, 248, 242),
        new Color(248, 248, 242),
        new Color(249, 38, 114), // keyword
        new Color(102, 217, 239), // type
        new Color(174, 129, 255), // number
        new Color(230, 219, 116), // string
        new Color(117, 113, 94),  // comment
        new Color(166, 226, 46)   // annotation
    );

    public static final EditorTheme ONE_DARK = new EditorTheme(
        "One Dark",
        new Color(40, 44, 52),
        new Color(171, 178, 191),
        new Color(82, 139, 255),
        new Color(198, 120, 221), // keyword
        new Color(229, 192, 123), // type
        new Color(209, 154, 102), // number
        new Color(152, 195, 121), // string
        new Color(92, 99, 112),   // comment
        new Color(97, 175, 239)   // annotation
    );

    public static final EditorTheme SOLARIZED_DARK = new EditorTheme(
        "Solarized Dark",
        new Color(0, 43, 54),
        new Color(131, 148, 150),
        new Color(131, 148, 150),
        new Color(133, 153, 0),  // keyword
        new Color(38, 139, 210),  // type
        new Color(211, 54, 130),  // number
        new Color(42, 161, 152),  // string
        new Color(88, 110, 117),  // comment
        new Color(203, 75, 22)    // annotation
    );

    public static final EditorTheme CLASSIC_DARK = new EditorTheme(
        "Classic Dark",
        new Color(30, 30, 30),
        new Color(220, 220, 220),
        Color.WHITE,
        new Color(86, 156, 214),  // keyword
        new Color(78, 201, 176),  // type
        new Color(181, 206, 168), // number
        new Color(214, 157, 133), // string
        new Color(106, 153, 85),  // comment
        new Color(220, 220, 120)  // annotation
    );

    public static final EditorTheme CLASSIC_LIGHT = new EditorTheme(
        "Classic Light",
        Color.WHITE,
        Color.BLACK,
        Color.BLACK,
        Color.BLUE,                // keyword
        new Color(0, 128, 128),    // type
        new Color(9, 134, 115),    // number
        new Color(163, 21, 21),    // string
        new Color(0, 128, 0),      // comment
        new Color(128, 128, 0)     // annotation
    );

    private String ghostText = "";
    private EditorTheme activeTheme = CLASSIC_DARK;
    private final Timer highlightTimer;
    private boolean isHighlighting = false;

    // Master Regex for Java highlighting (sequential priority to avoid overlap)
    private static final Pattern JAVA_PATTERN = Pattern.compile(
        "(?<MULTICOMMENT>/\\*.*?\\*/)" +
        "|(?<SINGLECOMMENT>//.*)" +
        "|(?<STRING>\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")" +
        "|(?<KEYWORD>\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null)\\b)" +
        "|(?<TYPE>\\b(String|Integer|Double|Float|Long|Short|Byte|Char|Boolean|GameObject|Input|IgnisScript|Animator|Camera|Color|Vector2|Game|Scene|Sound|Sprite)\\b)" +
        "|(?<NUMBER>\\b\\d+(\\.\\d+)?(f|F|d|D|L)?\\b)" +
        "|(?<ANNOTATION>@[a-zA-Z_][a-zA-Z0-9_]*)"
        , Pattern.DOTALL
    );

    public EditorTextPane() {
        super();
        setFont(new Font("Consolas", Font.PLAIN, 14));

        // Debounced syntax highlighting (50ms). Precisa existir ANTES de setTheme(),
        // pois setTheme() chama highlightTimer.restart() (senao, NPE no construtor e
        // a janela do editor de script nao abre).
        highlightTimer = new Timer(50, e -> runHighlighting());
        highlightTimer.setRepeats(false);

        setTheme(CLASSIC_DARK);

        // Setup Tab interception
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "accept-ghost");
        am.put("accept-ghost", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (ghostText != null && !ghostText.isEmpty()) {
                    try {
                        getDocument().insertString(getCaretPosition(), ghostText, null);
                        setGhostText("");
                    } catch (BadLocationException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    replaceSelection("    "); // 4-space tab indent
                }
            }
        });

        // Listen for modifications
        getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                if (!isHighlighting) {
                    highlightTimer.restart();
                }
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                if (!isHighlighting) {
                    highlightTimer.restart();
                }
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                // Ignore style attribute modifications
            }
        });
    }

    public void setGhostText(String ghostText) {
        this.ghostText = ghostText;
        repaint();
    }

    public String getGhostText() {
        return ghostText;
    }

    public void setTheme(EditorTheme theme) {
        this.activeTheme = theme;
        setBackground(theme.background);
        setForeground(theme.foreground);
        setCaretColor(theme.caret);
        highlightTimer.restart();
    }

    public EditorTheme getActiveTheme() {
        return activeTheme;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (ghostText != null && !ghostText.isEmpty()) {
            int pos = getCaretPosition();
            try {
                Rectangle rect = null;
                try {
                    rect = modelToView(pos);
                } catch (Exception ex) {
                    try {
                        rect = modelToView2D(pos).getBounds();
                    } catch (Exception ex2) {}
                }
                if (rect != null) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(150, 150, 150, 140)); // gray translucent
                    g2d.setFont(getFont());
                    FontMetrics fm = g2d.getFontMetrics();
                    int x = rect.x;
                    int y = rect.y + fm.getAscent();
                    g2d.drawString(ghostText, x, y);
                    g2d.dispose();
                }
            } catch (Exception e) {
                // ignore painting failures
            }
        }
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        Component parent = getParent();
        if (parent instanceof JViewport) {
            return getUI().getPreferredSize(this).width <= parent.getWidth();
        }
        return super.getScrollableTracksViewportWidth();
    }

    private void runHighlighting() {
        if (isHighlighting) return;
        isHighlighting = true;
        try {
            applyHighlighting();
        } finally {
            isHighlighting = false;
        }
    }

    private void applyHighlighting() {
        StyledDocument doc = getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return;
        }

        if (text.isEmpty()) {
            return;
        }

        // 1. Reset all attributes to base colors
        SimpleAttributeSet baseAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(baseAttr, activeTheme.foreground);
        doc.setCharacterAttributes(0, text.length(), baseAttr, true);

        // 2. Setup theme style attributes
        SimpleAttributeSet keywordAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(keywordAttr, activeTheme.keyword);

        SimpleAttributeSet typeAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(typeAttr, activeTheme.type);

        SimpleAttributeSet numberAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(numberAttr, activeTheme.number);

        SimpleAttributeSet stringAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(stringAttr, activeTheme.string);

        SimpleAttributeSet commentAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(commentAttr, activeTheme.comment);

        SimpleAttributeSet annotationAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(annotationAttr, activeTheme.annotation);

        // 3. Match and highlight tokens sequentially
        Matcher matcher = JAVA_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group("MULTICOMMENT") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), commentAttr, false);
            } else if (matcher.group("SINGLECOMMENT") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), commentAttr, false);
            } else if (matcher.group("STRING") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), stringAttr, false);
            } else if (matcher.group("KEYWORD") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), keywordAttr, false);
            } else if (matcher.group("TYPE") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), typeAttr, false);
            } else if (matcher.group("NUMBER") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), numberAttr, false);
            } else if (matcher.group("ANNOTATION") != null) {
                doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), annotationAttr, false);
            }
        }
    }

    // Helper functions for theme import/export in JSON without external libraries
    public static String exportThemeToJson(EditorTheme theme) {
        return "{\n" +
            "  \"name\": \"" + theme.name + "\",\n" +
            "  \"background\": \"" + colorToHex(theme.background) + "\",\n" +
            "  \"foreground\": \"" + colorToHex(theme.foreground) + "\",\n" +
            "  \"caret\": \"" + colorToHex(theme.caret) + "\",\n" +
            "  \"keyword\": \"" + colorToHex(theme.keyword) + "\",\n" +
            "  \"type\": \"" + colorToHex(theme.type) + "\",\n" +
            "  \"number\": \"" + colorToHex(theme.number) + "\",\n" +
            "  \"string\": \"" + colorToHex(theme.string) + "\",\n" +
            "  \"comment\": \"" + colorToHex(theme.comment) + "\",\n" +
            "  \"annotation\": \"" + colorToHex(theme.annotation) + "\"\n" +
            "}";
    }

    public static EditorTheme importThemeFromJson(String json) throws Exception {
        String name = extractJsonValue(json, "name");
        Color bg = hexToColor(extractJsonValue(json, "background"));
        Color fg = hexToColor(extractJsonValue(json, "foreground"));
        Color caret = hexToColor(extractJsonValue(json, "caret"));
        Color keyword = hexToColor(extractJsonValue(json, "keyword"));
        Color type = hexToColor(extractJsonValue(json, "type"));
        Color number = hexToColor(extractJsonValue(json, "number"));
        Color string = hexToColor(extractJsonValue(json, "string"));
        Color comment = hexToColor(extractJsonValue(json, "comment"));
        Color annotation = hexToColor(extractJsonValue(json, "annotation"));

        return new EditorTheme(name, bg, fg, caret, keyword, type, number, string, comment, annotation);
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return Color.WHITE;
        }
        return Color.decode(hex);
    }

    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
