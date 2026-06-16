package com.ignis.editor.fx;

import com.ignis.editor.Editor;
import com.ignis.core.ScriptManager;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaFX implementation of the Script Code Editor.
 * Replaces com.ignis.editor.ScriptEditorWindow using RichTextFX.
 */
public class FxCodeEditor extends Stage {

    public static class EditorTheme {
        public final String name;
        public final Color background;
        public final Color foreground;
        public final Color caret;
        public final Color keyword;
        public final Color type;
        public final Color number;
        public final Color string;
        public final Color comment;
        public final Color annotation;

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
        Color.rgb(40, 42, 54),
        Color.rgb(248, 248, 242),
        Color.rgb(248, 248, 242),
        Color.rgb(255, 121, 198),
        Color.rgb(139, 233, 253),
        Color.rgb(189, 147, 249),
        Color.rgb(241, 250, 140),
        Color.rgb(98, 114, 164),
        Color.rgb(80, 250, 123)
    );

    public static final EditorTheme MONOKAI = new EditorTheme(
        "Monokai",
        Color.rgb(39, 40, 34),
        Color.rgb(248, 248, 242),
        Color.rgb(248, 248, 242),
        Color.rgb(249, 38, 114),
        Color.rgb(102, 217, 239),
        Color.rgb(174, 129, 255),
        Color.rgb(230, 219, 116),
        Color.rgb(117, 113, 94),
        Color.rgb(166, 226, 46)
    );

    public static final EditorTheme ONE_DARK = new EditorTheme(
        "One Dark",
        Color.rgb(40, 44, 52),
        Color.rgb(171, 178, 191),
        Color.rgb(82, 139, 255),
        Color.rgb(198, 120, 221),
        Color.rgb(229, 192, 123),
        Color.rgb(209, 154, 102),
        Color.rgb(152, 195, 121),
        Color.rgb(92, 99, 112),
        Color.rgb(97, 175, 239)
    );

    public static final EditorTheme SOLARIZED_DARK = new EditorTheme(
        "Solarized Dark",
        Color.rgb(0, 43, 54),
        Color.rgb(131, 148, 150),
        Color.rgb(131, 148, 150),
        Color.rgb(133, 153, 0),
        Color.rgb(38, 139, 210),
        Color.rgb(211, 54, 130),
        Color.rgb(42, 161, 152),
        Color.rgb(88, 110, 117),
        Color.rgb(203, 75, 22)
    );

    public static final EditorTheme CLASSIC_DARK = new EditorTheme(
        "Classic Dark",
        Color.rgb(30, 30, 30),
        Color.rgb(220, 220, 220),
        Color.WHITE,
        Color.rgb(86, 156, 214),
        Color.rgb(78, 201, 176),
        Color.rgb(181, 206, 168),
        Color.rgb(214, 157, 133),
        Color.rgb(106, 153, 85),
        Color.rgb(220, 220, 120)
    );

    public static final EditorTheme CLASSIC_LIGHT = new EditorTheme(
        "Classic Light",
        Color.WHITE,
        Color.BLACK,
        Color.BLACK,
        Color.BLUE,
        Color.rgb(0, 128, 128),
        Color.rgb(9, 134, 115),
        Color.rgb(163, 21, 21),
        Color.rgb(0, 128, 0),
        Color.rgb(128, 128, 0)
    );

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

    private final Editor editor;
    private final ScriptManager scriptManager;
    private final String scriptName;

    private CodeArea codeArea;
    private Label statusLabel;
    private Label cursorLabel;
    private ToggleButton autocompleteToggle;
    
    private Popup autocompletePopup;
    private ListView<String> autocompleteList;
    private Label ghostLabel;
    private StackPane editorContainer;
    private String ghostText = "";
    
    private EditorTheme activeTheme = CLASSIC_DARK;
    private boolean modified = false;
    private Timeline highlightTimer;
    private Timeline autoSaveTimer;

    public FxCodeEditor(Editor editor, ScriptManager scriptManager, String scriptName) {
        this.editor = editor;
        this.scriptManager = scriptManager;
        this.scriptName = scriptName;

        setTitle("Script Editor - " + scriptName);
        setWidth(800);
        setHeight(600);

        try {
            File iconFile = new File("Icons/IconeIgnis.png");
            if (iconFile.exists()) {
                getIcons().add(new javafx.scene.image.Image(iconFile.toURI().toString()));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        setupUI();
        setupAutoSave();
        setupWindowListeners();
    }

    private void setupUI() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("editor-root");

        // --- Toolbar ---
        ToolBar toolbar = new ToolBar();
        toolbar.getStyleClass().add("editor-toolbar");

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("btn-save");
        saveBtn.setOnAction(e -> saveScript());

        Button compileBtn = new Button("Compile");
        compileBtn.getStyleClass().add("btn-compile");
        compileBtn.setOnAction(e -> compileScript());

        Button saveAndCompileBtn = new Button("Save & Compile");
        saveAndCompileBtn.getStyleClass().add("btn-save-compile");
        saveAndCompileBtn.setOnAction(e -> {
            if (saveScript()) {
                compileScript();
            }
        });

        autocompleteToggle = new ToggleButton("Auto Complete");
        autocompleteToggle.setSelected(true);
        autocompleteToggle.getStyleClass().add("btn-autocomplete");
        autocompleteToggle.setOnAction(e -> {
            boolean active = autocompleteToggle.isSelected();
            statusLabel.setText(active ? " Auto-complete active" : " Auto-complete disabled");
            statusLabel.setTextFill(Color.LIGHTGRAY);
            if (!active) {
                hideAutocompletePopup();
            }
        });

        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("Dracula", "Monokai", "One Dark", "Solarized Dark", "Classic Dark", "Classic Light");
        themeBox.setValue("Classic Dark");
        themeBox.getStyleClass().add("theme-box");
        themeBox.setOnAction(evt -> {
            String selected = themeBox.getValue();
            if (selected == null) return;
            switch (selected) {
                case "Dracula" -> { applyTheme(DRACULA); EditorPrefs.setCodeEditorTheme("Dracula"); }
                case "Monokai" -> { applyTheme(MONOKAI); EditorPrefs.setCodeEditorTheme("Monokai"); }
                case "One Dark" -> { applyTheme(ONE_DARK); EditorPrefs.setCodeEditorTheme("One Dark"); }
                case "Solarized Dark" -> { applyTheme(SOLARIZED_DARK); EditorPrefs.setCodeEditorTheme("Solarized Dark"); }
                case "Classic Dark" -> { applyTheme(CLASSIC_DARK); EditorPrefs.setCodeEditorTheme("Classic Dark"); }
                case "Classic Light" -> { applyTheme(CLASSIC_LIGHT); EditorPrefs.setCodeEditorTheme("Classic Light"); }
            }
        });

        Button importThemeBtn = new Button("Import Theme");
        importThemeBtn.getStyleClass().add("btn-theme-action");
        importThemeBtn.setOnAction(evt -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Import Theme JSON");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            File file = fileChooser.showOpenDialog(this);
            if (file != null) {
                try {
                    String json = Files.readString(file.toPath());
                    EditorTheme customTheme = importThemeFromJson(json);
                    applyTheme(customTheme);
                    statusLabel.setText(" ✓ Custom theme '" + customTheme.name + "' loaded");
                    statusLabel.setTextFill(Color.web("#64c864"));
                } catch (Exception ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Error loading theme: " + ex.getMessage());
                    alert.showAndWait();
                }
            }
        });

        Button exportThemeBtn = new Button("Export Theme");
        exportThemeBtn.getStyleClass().add("btn-theme-action");
        exportThemeBtn.setOnAction(evt -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Theme JSON");
            fileChooser.setInitialFileName(activeTheme.name.toLowerCase().replace(" ", "_") + "_theme.json");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
            File file = fileChooser.showSaveDialog(this);
            if (file != null) {
                try {
                    String json = exportThemeToJson(activeTheme);
                    Files.writeString(file.toPath(), json);
                    statusLabel.setText(" ✓ Theme exported to " + file.getName());
                    statusLabel.setTextFill(Color.web("#64c864"));
                } catch (Exception ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Error exporting theme: " + ex.getMessage());
                    alert.showAndWait();
                }
            }
        });

        toolbar.getItems().addAll(
            saveBtn, compileBtn, saveAndCompileBtn,
            new Separator(Orientation.VERTICAL),
            autocompleteToggle,
            new Separator(Orientation.VERTICAL),
            themeBox, importThemeBtn, exportThemeBtn
        );
        root.setTop(toolbar);

        // --- Code Area ---
        codeArea = new CodeArea();
        // Numeros de linha pretos sobre gutter claro, forcado inline (vence o .lineno{#666}
        // default do RichTextFX) para garantir legibilidade em qualquer tema.
        java.util.function.IntFunction<javafx.scene.Node> lineFactory = LineNumberFactory.get(codeArea);
        codeArea.setParagraphGraphicFactory(i -> {
            javafx.scene.Node node = lineFactory.apply(i);
            node.setStyle("-fx-text-fill: black; -fx-fill: black; -fx-background-color: #d8d8d8;");
            return node;
        });

        // Read script content
        String content = scriptManager.readScriptContent(scriptName);
        if (content == null) {
            content = "";
        }
        codeArea.replaceText(content);
        modified = false;

        // Ghost Label overlay
        ghostLabel = new Label();
        ghostLabel.setMouseTransparent(true);

        editorContainer = new StackPane();
        StackPane.setAlignment(ghostLabel, Pos.TOP_LEFT);
        editorContainer.getChildren().addAll(codeArea, ghostLabel);

        root.setCenter(editorContainer);

        // --- Status Bar ---
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("editor-statusbar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label(" Ready");
        statusLabel.setTextFill(Color.LIGHTGRAY);

        cursorLabel = new Label("Line: 1, Col: 1");
        cursorLabel.setTextFill(Color.LIGHTGRAY);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, cursorLabel);
        root.setBottom(statusBar);

        // --- Autocomplete UI Elements ---
        autocompletePopup = new Popup();
        autocompleteList = new ListView<>();
        autocompleteList.setPrefSize(250, 150);
        autocompleteList.setStyle(
            "-fx-background-color: #2d2d2d; " +
            "-fx-control-inner-background: #2d2d2d; " +
            "-fx-text-fill: #dcdcdc; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 13;"
        );
        autocompletePopup.getContent().add(autocompleteList);

        // --- Setup Listeners and Timers ---
        highlightTimer = new Timeline(new KeyFrame(Duration.millis(50), e -> runHighlighting()));
        highlightTimer.setCycleCount(1);

        // Intercept keys (Filters execute before handlers)
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressedFilter);

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            modified = true;
            highlightTimer.playFromStart();
            Platform.runLater(this::checkAutocomplete);
        });

        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            int line = codeArea.getCurrentParagraph() + 1;
            int col = codeArea.getCaretColumn() + 1;
            cursorLabel.setText("Line: " + line + ", Col: " + col);
            Platform.runLater(this::updateGhostTextPosition);
        });

        // Reposition ghost text on scrolling
        codeArea.addEventFilter(ScrollEvent.ANY, e -> Platform.runLater(this::updateGhostTextPosition));

        // Click suggestions in ListView
        autocompleteList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                insertSelectedSuggestion();
            }
        });

        // Setup Scene and Theme
        Scene scene = new Scene(root, 800, 600);
        setScene(scene);
        
        String savedTheme = EditorPrefs.getCodeEditorTheme();
        themeBox.setValue(savedTheme);
        switch (savedTheme) {
            case "Dracula" -> applyTheme(DRACULA);
            case "Monokai" -> applyTheme(MONOKAI);
            case "One Dark" -> applyTheme(ONE_DARK);
            case "Solarized Dark" -> applyTheme(SOLARIZED_DARK);
            case "Classic Light" -> applyTheme(CLASSIC_LIGHT);
            default -> applyTheme(CLASSIC_DARK);
        }
    }

    private void handleKeyPressedFilter(KeyEvent e) {
        if (autocompletePopup.isShowing()) {
            KeyCode code = e.getCode();
            if (code == KeyCode.UP) {
                int idx = autocompleteList.getSelectionModel().getSelectedIndex();
                if (idx > 0) {
                    autocompleteList.getSelectionModel().select(idx - 1);
                    autocompleteList.scrollTo(idx - 1);
                }
                e.consume();
            } else if (code == KeyCode.DOWN) {
                int idx = autocompleteList.getSelectionModel().getSelectedIndex();
                if (idx < autocompleteList.getItems().size() - 1) {
                    autocompleteList.getSelectionModel().select(idx + 1);
                    autocompleteList.scrollTo(idx + 1);
                }
                e.consume();
            } else if (code == KeyCode.ENTER || code == KeyCode.TAB) {
                insertSelectedSuggestion();
                e.consume();
            } else if (code == KeyCode.ESCAPE) {
                hideAutocompletePopup();
                e.consume();
            }
        } else {
            // Check for ghost text or general tab insertion
            if (e.getCode() == KeyCode.TAB) {
                if (ghostText != null && !ghostText.isEmpty()) {
                    codeArea.insertText(codeArea.getCaretPosition(), ghostText);
                    setGhostText("");
                    e.consume();
                } else {
                    codeArea.insertText(codeArea.getCaretPosition(), "    ");
                    e.consume();
                }
            }
        }
    }

    private void runHighlighting() {
        String text = codeArea.getText();
        if (text.isEmpty()) return;
        StyleSpans<Collection<String>> spans = computeHighlighting(text);
        if (spans != null) {
            codeArea.setStyleSpans(0, spans);
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = JAVA_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("TYPE") != null ? "type" :
                    matcher.group("NUMBER") != null ? "number" :
                    matcher.group("STRING") != null ? "string" :
                    matcher.group("MULTICOMMENT") != null ? "comment" :
                    matcher.group("SINGLECOMMENT") != null ? "comment" :
                    matcher.group("ANNOTATION") != null ? "annotation" :
                    null;
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private void setGhostText(String ghostText) {
        this.ghostText = ghostText;
        ghostLabel.setText(ghostText);
        Platform.runLater(this::updateGhostTextPosition);
    }

    private void updateGhostTextPosition() {
        if (ghostText == null || ghostText.isEmpty()) {
            ghostLabel.setVisible(false);
            return;
        }
        Optional<Bounds> boundsOpt = codeArea.getCaretBounds();
        if (boundsOpt.isPresent()) {
            Bounds bounds = boundsOpt.get();
            Point2D local = editorContainer.screenToLocal(bounds.getMaxX(), bounds.getMinY());
            if (local != null) {
                ghostLabel.setLayoutX(local.getX());
                ghostLabel.setLayoutY(local.getY());
                ghostLabel.setVisible(true);
            } else {
                ghostLabel.setVisible(false);
            }
        } else {
            ghostLabel.setVisible(false);
        }
    }

    private void checkAutocomplete() {
        if (!autocompleteToggle.isSelected()) {
            hideAutocompletePopup();
            return;
        }

        int pos = codeArea.getCaretPosition();
        if (pos <= 0) {
            hideAutocompletePopup();
            return;
        }

        String text = codeArea.getText();
        if (pos > text.length()) {
            hideAutocompletePopup();
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
            hideAutocompletePopup();
        } else {
            showAutocompletePopup(suggestions);
            String topSuggestion = suggestions.get(0);
            if (topSuggestion.toLowerCase().startsWith(prefix.toLowerCase())) {
                String ghost = topSuggestion.substring(prefix.length());
                setGhostText(ghost);
            } else {
                setGhostText("");
            }
        }
    }

    private void showAutocompletePopup(List<String> suggestions) {
        autocompleteList.getItems().setAll(suggestions);
        autocompleteList.getSelectionModel().select(0);

        Optional<Bounds> boundsOpt = codeArea.getCaretBounds();
        if (boundsOpt.isPresent()) {
            Bounds bounds = boundsOpt.get();
            autocompletePopup.show(codeArea.getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 2);
        } else {
            autocompletePopup.hide();
        }
    }

    public void hideAutocompletePopup() {
        if (autocompletePopup.isShowing()) {
            autocompletePopup.hide();
        }
        setGhostText("");
    }

    private void insertSelectedSuggestion() {
        String selected = autocompleteList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        int pos = codeArea.getCaretPosition();
        String text = codeArea.getText();

        int start = pos - 1;
        while (start >= 0 && isWordChar(text.charAt(start))) {
            start--;
        }

        String prefix = text.substring(start + 1, pos);

        String insertText = selected;
        int parenIndex = selected.indexOf('(');
        boolean hasParams = false;
        if (parenIndex != -1) {
            insertText = selected.substring(0, parenIndex + 1);
            if (selected.charAt(parenIndex + 1) != ')') {
                hasParams = true;
            } else {
                insertText += ")";
            }
        }

        codeArea.replaceText(start + 1, pos, insertText);

        if (hasParams) {
            codeArea.moveTo(start + 1 + parenIndex + 1);
        }
        hideAutocompletePopup();
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private List<String> getLocalVariablesAndFields() {
        List<String> locals = new ArrayList<>();
        String text = codeArea.getText();

        // Match fields
        Pattern fieldPattern = Pattern.compile("\\b(?:private|public|protected|static|final)?\\s+[a-zA-Z0-9_<>]+\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|;)");
        Matcher matcher = fieldPattern.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!locals.contains(name) && !KEYWORDS.contains(name)) {
                locals.add(name);
            }
        }

        // Match local variables
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
        String text = codeArea.getText();

        Pattern methodPattern = Pattern.compile("\\b(?:public|private|protected|static|void|int|float|double|boolean|String)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)");
        Matcher matcher = methodPattern.matcher(text);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            String params = matcher.group(2).trim();
            String entry = methodName + "(" + (params.isEmpty() ? "" : params) + ")";
            if (!methods.contains(entry) && !methodName.equals("if") && !methodName.equals("for") && !methodName.equals("while") && !methodName.equals("switch")) {
                methods.add(entry);
            }
        }

        return methods;
    }

    // Auto Save ligado por EditorPrefs (casca FX passa editor=null, entao a flag global
    // de EditorPrefs e a fonte de verdade; mantem compat com o editor Swing legado).
    private boolean autoSaveOn() {
        return EditorPrefs.isAutoSave() || (editor != null && editor.isAutoSaveScriptsEnabled());
    }

    private void setupAutoSave() {
        autoSaveTimer = new Timeline(new KeyFrame(
                Duration.seconds(EditorPrefs.getAutoSaveIntervalSeconds()), e -> {
            if (autoSaveOn() && modified) {
                saveScriptSilently(); // salva so quando ha mudanca; mostra indicador no status
            }
        }));
        autoSaveTimer.setCycleCount(Animation.INDEFINITE);
        autoSaveTimer.play();
    }

    private void setupWindowListeners() {
        setOnCloseRequest(e -> {
            if (modified) {
                if (autoSaveOn()) {
                    saveScript();
                    closeEditor();
                } else {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Unsaved Changes");
                    alert.setHeaderText("Script '" + scriptName + "' has unsaved changes.");
                    alert.setContentText("Save changes now?");
                    
                    ButtonType yesButton = new ButtonType("Yes", ButtonBar.ButtonData.YES);
                    ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.NO);
                    ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    
                    alert.getButtonTypes().setAll(yesButton, noButton, cancelButton);
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent()) {
                        if (result.get() == yesButton) {
                            if (saveScript()) {
                                closeEditor();
                            } else {
                                e.consume();
                            }
                        } else if (result.get() == noButton) {
                            closeEditor();
                        } else {
                            e.consume();
                        }
                    } else {
                        e.consume();
                    }
                }
            } else {
                closeEditor();
            }
        });

        focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                if (autoSaveOn() && modified) {
                    saveScriptSilently();
                }
            }
        });
    }

    public boolean saveScript() {
        if (scriptManager.saveScriptContent(scriptName, codeArea.getText())) {
            statusLabel.setText(" ✓ Script saved");
            statusLabel.setTextFill(Color.web("#64c864"));
            modified = false;
            return true;
        } else {
            statusLabel.setText(" ✕ Failed to save");
            statusLabel.setTextFill(Color.web("#c86464"));
            return false;
        }
    }

    private void saveScriptSilently() {
        if (scriptManager.saveScriptContent(scriptName, codeArea.getText())) {
            modified = false;
            statusLabel.setText(" ✓ Auto-saved");
            statusLabel.setTextFill(Color.web("#64c864"));
        } else {
            statusLabel.setText(" ✕ Auto-save failed");
            statusLabel.setTextFill(Color.web("#c86464"));
        }
    }

    public void compileScript() {
        File scriptFile = new File(scriptManager.getScriptsFolder(), scriptName + ".java");
        if (scriptManager.compileScript(scriptFile)) {
            statusLabel.setText(" ✓ Compilation successful");
            statusLabel.setTextFill(Color.web("#64c864"));
            if (editor != null) {
                try {
                    java.lang.reflect.Method method = editor.getClass().getDeclaredMethod("reloadAllScriptInstances");
                    method.setAccessible(true);
                    method.invoke(editor);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } else {
            statusLabel.setText(" ✕ Compilation failed - check console");
            statusLabel.setTextFill(Color.web("#c86464"));
        }
    }

    public void closeEditor() {
        if (autoSaveTimer != null) {
            autoSaveTimer.stop();
        }
        hideAutocompletePopup();
        if (editor != null) {
            editor.onScriptEditorClosed(scriptName);
        }
        close();
    }

    public void applyTheme(EditorTheme theme) {
        this.activeTheme = theme;
        
        String bgHex = toCssColor(theme.background);
        String fgHex = toCssColor(theme.foreground);
        String caretHex = toCssColor(theme.caret);
        String keywordHex = toCssColor(theme.keyword);
        String typeHex = toCssColor(theme.type);
        String numberHex = toCssColor(theme.number);
        String stringHex = toCssColor(theme.string);
        String commentHex = toCssColor(theme.comment);
        String annotationHex = toCssColor(theme.annotation);
        
        // Numeros de linha SEMPRE pretos sobre um gutter claro fixo — legiveis em qualquer
        // tema (antes usavam o foreground do tema com 50% de alpha e sumiam/perdiam contraste).
        Color linenoFg = Color.BLACK;
        Color linenoBg = Color.rgb(216, 216, 216);
        String linenoFgHex = toCssColor(linenoFg);
        String linenoBgHex = toCssColor(linenoBg);

        Color barBg = theme.background.darker();
        Color btnBg = theme.background.brighter();
        
        if (theme == CLASSIC_LIGHT) {
            barBg = Color.rgb(230, 230, 230);
            btnBg = Color.rgb(240, 240, 240);
        } else if (theme.background.getRed() < 0.1 && theme.background.getGreen() < 0.1 && theme.background.getBlue() < 0.1) {
            barBg = Color.rgb(20, 20, 20);
            btnBg = Color.rgb(55, 55, 55);
        }
        
        String barBgHex = toCssColor(barBg);
        String btnBgHex = toCssColor(btnBg);
        String btnHoverBgHex = toCssColor(btnBg.brighter());

        String css =
            ".styled-text-area, .code-area {\n" +
            "    -fx-background-color: " + bgHex + ";\n" +
            "}\n" +
            ".styled-text-area .text, .code-area .text {\n" +
            "    -fx-fill: " + fgHex + ";\n" +
            "    -fx-font-family: 'Consolas';\n" +
            "    -fx-font-size: 14px;\n" +
            "}\n" +
            ".code-area .caret {\n" +
            "    -fx-stroke: " + caretHex + ";\n" +
            "}\n" +
            ".code-area .keyword {\n" +
            "    -fx-fill: " + keywordHex + ";\n" +
            "    -fx-font-weight: bold;\n" +
            "}\n" +
            ".code-area .type {\n" +
            "    -fx-fill: " + typeHex + ";\n" +
            "}\n" +
            ".code-area .number {\n" +
            "    -fx-fill: " + numberHex + ";\n" +
            "}\n" +
            ".code-area .string {\n" +
            "    -fx-fill: " + stringHex + ";\n" +
            "}\n" +
            ".code-area .comment {\n" +
            "    -fx-fill: " + commentHex + ";\n" +
            "    -fx-font-style: italic;\n" +
            "}\n" +
            ".code-area .annotation {\n" +
            "    -fx-fill: " + annotationHex + ";\n" +
            "}\n" +
            ".code-area .paragraph-graphic {\n" +
            "    -fx-background-color: " + linenoBgHex + " !important;\n" +
            "    -fx-padding: 0 5 0 5;\n" +
            "}\n" +
            ".code-area .lineno, .code-area .line-number {\n" +
            "    -fx-text-fill: " + linenoFgHex + " !important;\n" +
            "    -fx-fill: " + linenoFgHex + " !important;\n" +
            "    -fx-opacity: 1;\n" +
            "    -fx-font-family: 'Consolas';\n" +
            "    -fx-font-size: 14px;\n" +
            "}\n" +
            ".editor-root {\n" +
            "    -fx-background-color: " + bgHex + ";\n" +
            "}\n" +
            ".editor-toolbar {\n" +
            "    -fx-background-color: " + barBgHex + ";\n" +
            "    -fx-padding: 5;\n" +
            "    -fx-spacing: 5;\n" +
            "}\n" +
            ".editor-statusbar {\n" +
            "    -fx-background-color: " + barBgHex + ";\n" +
            "    -fx-padding: 5 10;\n" +
            "}\n" +
            ".btn-save {\n" +
            "    -fx-background-color: #3c783c;\n" +
            "    -fx-text-fill: white;\n" +
            "    -fx-font-weight: bold;\n" +
            "    -fx-background-radius: 4;\n" +
            "}\n" +
            ".btn-save:hover {\n" +
            "    -fx-background-color: #4ca84c;\n" +
            "}\n" +
            ".btn-compile {\n" +
            "    -fx-background-color: #3c6496;\n" +
            "    -fx-text-fill: white;\n" +
            "    -fx-font-weight: bold;\n" +
            "    -fx-background-radius: 4;\n" +
            "}\n" +
            ".btn-compile:hover {\n" +
            "    -fx-background-color: #4c84c6;\n" +
            "}\n" +
            ".btn-save-compile {\n" +
            "    -fx-background-color: #645096;\n" +
            "    -fx-text-fill: white;\n" +
            "    -fx-font-weight: bold;\n" +
            "    -fx-background-radius: 4;\n" +
            "}\n" +
            ".btn-save-compile:hover {\n" +
            "    -fx-background-color: #8470c6;\n" +
            "}\n" +
            ".btn-autocomplete {\n" +
            "    -fx-background-color: " + btnBgHex + ";\n" +
            "    -fx-text-fill: " + fgHex + ";\n" +
            "    -fx-background-radius: 4;\n" +
            "}\n" +
            ".btn-autocomplete:selected {\n" +
            "    -fx-background-color: " + keywordHex + ";\n" +
            "    -fx-text-fill: " + bgHex + ";\n" +
            "    -fx-font-weight: bold;\n" +
            "}\n" +
            ".btn-theme-action {\n" +
            "    -fx-background-color: " + btnBgHex + ";\n" +
            "    -fx-text-fill: " + fgHex + ";\n" +
            "    -fx-background-radius: 4;\n" +
            "}\n" +
            ".btn-theme-action:hover, .btn-autocomplete:hover {\n" +
            "    -fx-background-color: " + btnHoverBgHex + ";\n" +
            "}\n" +
            ".theme-box {\n" +
            "    -fx-background-color: " + btnBgHex + ";\n" +
            "    -fx-text-fill: " + fgHex + ";\n" +
            "    -fx-background-radius: 4;\n" +
            "    -fx-mark-color: " + fgHex + ";\n" +
            "}\n" +
            ".theme-box .label {\n" +
            "    -fx-text-fill: " + fgHex + ";\n" +
            "}\n" +
            ".theme-box .list-cell {\n" +
            "    -fx-background-color: " + btnBgHex + ";\n" +
            "    -fx-text-fill: " + fgHex + ";\n" +
            "}\n" +
            ".theme-box .list-cell:hover {\n" +
            "    -fx-background-color: " + keywordHex + ";\n" +
            "    -fx-text-fill: " + bgHex + ";\n" +
            "}\n";

        Scene scene = getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            try {
                String encoded = java.net.URLEncoder.encode(css, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                scene.getStylesheets().add("data:text/css," + encoded);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        ghostLabel.setTextFill(Color.color(theme.foreground.getRed(), theme.foreground.getGreen(), theme.foreground.getBlue(), 0.55));
        ghostLabel.setFont(Font.font("Consolas", 14));

        highlightTimer.playFromStart();
    }

    private static String toCssColor(Color color) {
        return String.format("rgba(%d, %d, %d, %.2f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity());
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private static Color hexToColor(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return Color.WHITE;
        }
        return Color.web(hex);
    }

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

    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
