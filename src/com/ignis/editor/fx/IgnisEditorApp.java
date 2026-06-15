package com.ignis.editor.fx;

import com.ignis.core.Circle;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisProjectIO;
import com.ignis.core.Project;
import com.ignis.core.Scene;
import com.ignis.core.Square;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Editor IgnisEngine em JavaFX — Fase 2 da migracao (ver doc/JAVAFX_MIGRATION_PLAN.md).
 *
 * <p>F1 entregou a casca + ponte de render. F2 adiciona: abrir projeto .ignis real,
 * selecao Hierarchy &lt;-&gt; viewport (contorno) e Inspector editavel (escreve de
 * volta no GameObject; o efeito aparece ao vivo pois a ponte re-renderiza por frame).
 * O editor Swing classico continua intacto.
 */
public class IgnisEditorApp extends Application {

    private final Game game = new Game();
    private GameObject selected;
    private boolean suppressInspectorEvents = false;
    private File projectFolder;
    private File currentIgnisFile;
    private Button playButton;
    private Button stopButton;
    private boolean playing = false;
    private Canvas viewportCanvas;
    // Fonte AWT (nao exibida) usada apenas como 'source' nao-nulo ao fabricar
    // KeyEvent/MouseEvent que roteiam o input do viewport FX para o singleton Input.
    private final java.awt.Component awtEventSource = new java.awt.Canvas();

    private final TreeItem<String> hierarchyRoot = new TreeItem<>("Cena");
    private TreeView<String> hierarchy;
    private Label status;

    // Campos do Inspector
    private TextField nameField, xField, yField, wField, hField, rotField;
    private CheckBox visibleCheck;

    @Override
    public void start(Stage stage) {
        seedSampleScene();

        BorderPane root = new BorderPane();

        // Menu + ToolBar (Fase 3)
        Button btnOpen = new Button("Abrir");
        btnOpen.setOnAction(e -> openProject(stage));
        Button btnBuild = new Button("Build");
        btnBuild.setOnAction(e -> openBuildDialog());
        playButton = new Button("▶ Play");
        playButton.setOnAction(e -> playWorld());
        stopButton = new Button("⏹ Stop");
        stopButton.setOnAction(e -> stopWorld());
        stopButton.setDisable(true);
        ToolBar toolBar = new ToolBar(btnOpen, btnBuild, new Separator(), playButton, stopButton);
        root.setTop(new VBox(buildMenuBar(stage), toolBar));

        // ---- Viewport central ----
        Pane viewportPane = new Pane();
        Canvas canvas = new Canvas();
        canvas.widthProperty().bind(viewportPane.widthProperty());
        canvas.heightProperty().bind(viewportPane.heightProperty());
        viewportPane.getChildren().add(canvas);
        this.viewportCanvas = canvas;
        wireFxInputToEngine(canvas);

        // ---- Hierarchy (esquerda) ----
        hierarchy = buildHierarchy();

        // ---- Inspector (direita) ----
        VBox inspector = buildInspector();

        SplitPane split = new SplitPane();
        split.getItems().addAll(hierarchy, viewportPane, inspector);
        split.setDividerPositions(0.2, 0.78);
        root.setCenter(split);

        status = new Label(" Editor JavaFX (Fase 2) — abra um projeto .ignis (Arquivo > Abrir projeto)");
        status.setStyle("-fx-text-fill: #b0b0b0; -fx-padding: 4 10; -fx-background-color: #2d2d2d;");
        root.setBottom(status);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 1100, 700);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), () -> openProject(stage));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN), this::openBuildDialog);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::playWorld);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), this::stopWorld);
        stage.setTitle("IgnisEngine — Editor (JavaFX) [migracao]");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            stopGameLoop();
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        startRenderBridge(canvas);
    }

    private void seedSampleScene() {
        game.addEntity(new Square("Quadrado", game, 0, 0, 90, 90));
        game.addEntity(new Circle("Circulo", game, 140, 60, 80, 80));
        game.addEntity(new Square("Quadrado2", game, -120, 90, 70, 70));
    }

    // ---------------- Menu ----------------

    private MenuBar buildMenuBar(Stage stage) {
        Menu file = new Menu("Arquivo");
        MenuItem open = new MenuItem("Abrir projeto…");
        open.setOnAction(e -> openProject(stage));
        MenuItem exit = new MenuItem("Sair");
        exit.setOnAction(e -> { stage.close(); Platform.exit(); });
        file.getItems().addAll(open, exit);

        Menu tools = new Menu("Ferramentas");
        MenuItem miAudio = new MenuItem("Editor de Audio (DAW)");
        miAudio.setOnAction(e -> openAudioEditor());
        MenuItem miImage = new MenuItem("Editor de Imagens");
        miImage.setOnAction(e -> openImageEditor());
        MenuItem miAnim = new MenuItem("Editor de Animacao");
        miAnim.setOnAction(e -> openAnimationEditor());
        MenuItem miNotes = new MenuItem("Sistema de Notas");
        miNotes.setOnAction(e -> openNotes());
        MenuItem miCommunity = new MenuItem("Comunidade & Marketplace");
        miCommunity.setOnAction(e -> openCommunity());
        MenuItem miCode = new MenuItem("Editor de Codigo (Scripts)");
        miCode.setOnAction(e -> openCodeEditor());
        MenuItem miBuild = new MenuItem("Build do Projeto");
        miBuild.setOnAction(e -> openBuildDialog());
        tools.getItems().addAll(miAudio, miImage, miAnim, miNotes, miCommunity, miCode, miBuild);

        Menu view = new Menu("Visualizar");
        view.getItems().add(new MenuItem("Viewport"));

        Menu help = new Menu("Ajuda");
        MenuItem about = new MenuItem("Sobre");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "IgnisEngine — editor JavaFX (Fase 3 da migracao).").showAndWait());
        help.getItems().add(about);

        return new MenuBar(file, tools, view, help);
    }

    private void openProject(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Abrir projeto .ignis");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto Ignis (*.ignis)", "*.ignis"));
        try {
            File rootDir = IgnisProjectIO.getProjectsRootFolder();
            if (rootDir != null && rootDir.isDirectory()) fc.setInitialDirectory(rootDir);
        } catch (Exception ignore) { /* sem dir inicial */ }

        File fileChosen = fc.showOpenDialog(stage);
        if (fileChosen == null) return;

        try {
            Project project = IgnisProjectIO.load(fileChosen, game);
            this.currentIgnisFile = fileChosen;
            this.projectFolder = IgnisProjectIO.getProjectFolder(fileChosen);
            try {
                game.setScriptManager(new com.ignis.core.ScriptManager(projectFolder));
            } catch (Exception ignore) { /* scripts opcionais para Play */ }
            game.getEntities().clear();
            setSelected(null);
            Scene scene = project.getCurrentScene();
            if (scene != null) {
                for (GameObject e : scene.getEntities()) {
                    e.setGame(game);
                    game.addEntity(e);
                }
            }
            refreshHierarchy();
            setStatus("Projeto carregado: " + project.getProjectName()
                    + " (" + game.getEntities().size() + " objetos)");
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir projeto:\n" + ex.getMessage()).showAndWait();
        }
    }

    // ---------------- Ferramentas (janelas JavaFX) ----------------

    private boolean requireProject() {
        if (projectFolder == null) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Abra um projeto primeiro (Arquivo > Abrir projeto).").showAndWait();
            return false;
        }
        return true;
    }

    private void openAudioEditor() {
        try {
            FxAudioEditor editor = new FxAudioEditor();
            editor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Audio:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void openImageEditor() {
        try {
            File folder = projectFolder != null ? projectFolder : IgnisProjectIO.getProjectsRootFolder();
            FxImageEditor editor = new FxImageEditor(folder);
            editor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Imagens:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void openAnimationEditor() {
        if (!requireProject()) return;
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Selecione um objeto na Hierarchy para animar.").showAndWait();
            return;
        }
        try {
            final File sprites = new File(projectFolder, "assets/sprites");
            FxAnimationEditor editor = new FxAnimationEditor(projectFolder, sprites, selected);
            editor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Animacao:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void openNotes() {
        if (!requireProject()) return;
        try {
            FxNotesWindow notes = new FxNotesWindow(projectFolder, null);
            notes.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Sistema de Notas:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void openCommunity() {
        if (!requireProject()) return;
        try {
            FxCommunityWindow community = new FxCommunityWindow(projectFolder);
            community.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Comunidade:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Editor de Codigo nativo (FxCodeEditor / RichTextFX). Liga a ultima janela-ferramenta
    // do Gemini ao menu. Sem editor Swing acoplado: passa null (FxCodeEditor ja trata null).
    private void openCodeEditor() {
        if (!requireProject()) return;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) {
                sm = new com.ignis.core.ScriptManager(projectFolder);
                game.setScriptManager(sm);
            }
            final com.ignis.core.ScriptManager scriptManager = sm;

            final String novoLabel = "➕ Novo script…";
            java.util.List<String> options = new java.util.ArrayList<>(scriptManager.listAvailableScripts());
            options.add(novoLabel);

            ChoiceDialog<String> picker = new ChoiceDialog<>(options.get(0), options);
            picker.setTitle("Editor de Codigo");
            picker.setHeaderText(null);
            picker.setContentText("Escolha um script para editar:");
            java.util.Optional<String> choice = picker.showAndWait();
            if (choice.isEmpty()) return;

            String scriptName = choice.get();
            if (novoLabel.equals(scriptName)) {
                TextInputDialog input = new TextInputDialog("NovoScript");
                input.setTitle("Novo script");
                input.setHeaderText(null);
                input.setContentText("Nome da classe do script:");
                java.util.Optional<String> nameOpt = input.showAndWait();
                if (nameOpt.isEmpty() || nameOpt.get().trim().isEmpty()) return;

                java.util.List<String> before = scriptManager.listAvailableScripts();
                if (!scriptManager.createNewScript(nameOpt.get().trim())) {
                    new Alert(Alert.AlertType.ERROR,
                            "Nao foi possivel criar o script (nome invalido ou ja existe).").showAndWait();
                    return;
                }
                // createNewScript sanitiza o nome; descobre o nome real via diff da lista.
                java.util.List<String> after = scriptManager.listAvailableScripts();
                after.removeAll(before);
                if (after.isEmpty()) {
                    new Alert(Alert.AlertType.ERROR,
                            "Script criado, mas nao foi possivel localiza-lo.").showAndWait();
                    return;
                }
                scriptName = after.get(0);
            }

            FxCodeEditor codeEditor = new FxCodeEditor(null, scriptManager, scriptName);
            codeEditor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Codigo:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Build nativo em JavaFX (Fase 3, passo 1).
    private void openBuildDialog() {
        if (!requireProject() || currentIgnisFile == null) return;
        String name = currentIgnisFile.getName().replace(".ignis", "");
        try {
            FxBuildDialog dlg = new FxBuildDialog(currentIgnisFile, name);
            dlg.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Build:\n" + ex.getMessage()).showAndWait();
        }
    }

    // ---------------- Play / Stop ----------------
    // Play inicia o loop da engine (game.start()): o render() do loop sai cedo (Game nao
    // esta em tela) e o tick() avanca a simulacao; a ponte JavaFX desenha cada frame.
    // Limitacao atual: input de teclado/mouse do jogo ainda nao roteado para o viewport FX.

    private void playWorld() {
        if (playing) return;
        try {
            game.playWorld();
            game.start();
            playing = true;
            playButton.setDisable(true);
            stopButton.setDisable(false);
            // Foco no viewport para que as teclas cheguem ao jogo durante o Play.
            if (viewportCanvas != null) viewportCanvas.requestFocus();
            setStatus("Executando (Play)…");
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao iniciar Play:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void stopWorld() {
        if (!playing) return;
        stopGameLoop();
        setStatus("Parado (edicao)");
    }

    private void stopGameLoop() {
        if (!playing) return;
        try {
            game.stopWorld();
            game.stop();
        } catch (Exception ignore) { /* best-effort */ }
        playing = false;
        if (playButton != null) playButton.setDisable(false);
        if (stopButton != null) stopButton.setDisable(true);
    }

    // ---------------- Roteamento de input para o viewport FX (Fase 3) ----------------
    // Liga os eventos de teclado/mouse do Canvas JavaFX ao singleton Input da engine,
    // fabricando java.awt.event.KeyEvent/MouseEvent e chamando os callbacks AWT publicos
    // do Input (mesma superficie que o editor Swing usa via Input.init). Puramente
    // aditivo: nao altera com.ignis.core. O teclado so e encaminhado durante o Play
    // (playing) para nao colidir com os atalhos do editor; o mouse e sempre encaminhado
    // (seu estado so e lido por scripts em Play). Validacao requer teste manual de GUI.

    private void wireFxInputToEngine(Canvas canvas) {
        canvas.setFocusTraversable(true);

        canvas.setOnMousePressed(e -> {
            canvas.requestFocus();
            com.ignis.core.Input.getInstance().mousePressed(
                    buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_PRESSED));
        });
        canvas.setOnMouseReleased(e -> com.ignis.core.Input.getInstance().mouseReleased(
                buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_RELEASED)));
        canvas.setOnMouseMoved(e -> com.ignis.core.Input.getInstance().mouseMoved(
                buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_MOVED)));
        canvas.setOnMouseDragged(e -> com.ignis.core.Input.getInstance().mouseDragged(
                buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_DRAGGED)));

        canvas.setOnKeyPressed(e -> {
            if (!playing) return;
            int vk = toAwtKeyCode(e.getCode());
            if (vk != java.awt.event.KeyEvent.VK_UNDEFINED) {
                com.ignis.core.Input.getInstance().keyPressed(
                        buildAwtKeyEvent(java.awt.event.KeyEvent.KEY_PRESSED, vk));
                e.consume();
            }
        });
        canvas.setOnKeyReleased(e -> {
            if (!playing) return;
            int vk = toAwtKeyCode(e.getCode());
            if (vk != java.awt.event.KeyEvent.VK_UNDEFINED) {
                com.ignis.core.Input.getInstance().keyReleased(
                        buildAwtKeyEvent(java.awt.event.KeyEvent.KEY_RELEASED, vk));
                e.consume();
            }
        });
    }

    private java.awt.event.KeyEvent buildAwtKeyEvent(int id, int vk) {
        return new java.awt.event.KeyEvent(awtEventSource, id, System.currentTimeMillis(), 0,
                vk, java.awt.event.KeyEvent.CHAR_UNDEFINED);
    }

    private java.awt.event.MouseEvent buildAwtMouseEvent(javafx.scene.input.MouseEvent e, int id) {
        int awtButton;
        switch (e.getButton()) {
            case PRIMARY:   awtButton = java.awt.event.MouseEvent.BUTTON1; break;
            case MIDDLE:    awtButton = java.awt.event.MouseEvent.BUTTON2; break;
            case SECONDARY: awtButton = java.awt.event.MouseEvent.BUTTON3; break;
            default:        awtButton = java.awt.event.MouseEvent.NOBUTTON; break;
        }
        boolean pressOrRelease = (id == java.awt.event.MouseEvent.MOUSE_PRESSED
                || id == java.awt.event.MouseEvent.MOUSE_RELEASED);
        return new java.awt.event.MouseEvent(awtEventSource, id, System.currentTimeMillis(), 0,
                (int) e.getX(), (int) e.getY(), pressOrRelease ? 1 : 0, false, awtButton);
    }

    // Traduz o KeyCode do JavaFX para o codigo VK_* do AWT esperado pelo Input.
    // Para letras/digitos, o codigo VK coincide com o ASCII maiusculo (VK_A=='A', VK_0=='0').
    private static int toAwtKeyCode(javafx.scene.input.KeyCode code) {
        switch (code) {
            case UP:         return java.awt.event.KeyEvent.VK_UP;
            case DOWN:       return java.awt.event.KeyEvent.VK_DOWN;
            case LEFT:       return java.awt.event.KeyEvent.VK_LEFT;
            case RIGHT:      return java.awt.event.KeyEvent.VK_RIGHT;
            case SPACE:      return java.awt.event.KeyEvent.VK_SPACE;
            case ENTER:      return java.awt.event.KeyEvent.VK_ENTER;
            case ESCAPE:     return java.awt.event.KeyEvent.VK_ESCAPE;
            case TAB:        return java.awt.event.KeyEvent.VK_TAB;
            case BACK_SPACE: return java.awt.event.KeyEvent.VK_BACK_SPACE;
            case SHIFT:      return java.awt.event.KeyEvent.VK_SHIFT;
            case CONTROL:    return java.awt.event.KeyEvent.VK_CONTROL;
            case ALT:        return java.awt.event.KeyEvent.VK_ALT;
            default:
                String n = code.getName();
                if (n != null && n.length() == 1) {
                    char c = Character.toUpperCase(n.charAt(0));
                    if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                        return c;
                    }
                }
                return java.awt.event.KeyEvent.VK_UNDEFINED;
        }
    }

    // ---------------- Hierarchy ----------------

    private TreeView<String> buildHierarchy() {
        refreshHierarchy();
        hierarchyRoot.setExpanded(true);
        TreeView<String> tree = new TreeView<>(hierarchyRoot);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, item) -> {
            if (item == null || item == hierarchyRoot) { setSelected(null); return; }
            int idx = hierarchyRoot.getChildren().indexOf(item);
            java.util.List<GameObject> ents = game.getEntities();
            setSelected(idx >= 0 && idx < ents.size() ? ents.get(idx) : null);
        });
        return tree;
    }

    private void refreshHierarchy() {
        hierarchyRoot.getChildren().clear();
        for (GameObject go : game.getEntities()) {
            hierarchyRoot.getChildren().add(new TreeItem<>(go.getName()));
        }
        hierarchyRoot.setExpanded(true);
    }

    // ---------------- Inspector ----------------

    private VBox buildInspector() {
        VBox box = new VBox(8);
        box.setStyle("-fx-background-color: #2d2d2d;");
        box.setPadding(new Insets(12));

        Label title = new Label("Inspector");
        title.setStyle("-fx-text-fill: #2e8b57; -fx-font-weight: bold;");

        nameField = new TextField();
        xField = new TextField();
        yField = new TextField();
        wField = new TextField();
        hField = new TextField();
        rotField = new TextField();
        visibleCheck = new CheckBox("Visivel");
        visibleCheck.setStyle("-fx-text-fill: #d8d8d8;");

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        int r = 0;
        addRow(grid, r++, "Nome", nameField);
        addRow(grid, r++, "X", xField);
        addRow(grid, r++, "Y", yField);
        addRow(grid, r++, "Largura", wField);
        addRow(grid, r++, "Altura", hField);
        addRow(grid, r++, "Rotacao", rotField);
        grid.add(visibleCheck, 1, r);

        // Listeners: escrevem de volta no objeto selecionado
        nameField.textProperty().addListener((o, a, b) -> applyIfEditing(() -> {
            selected.setName(b);
            int idx = game.getEntities().indexOf(selected);
            if (idx >= 0 && idx < hierarchyRoot.getChildren().size())
                hierarchyRoot.getChildren().get(idx).setValue(b);
        }));
        xField.textProperty().addListener((o, a, b) -> applyIfEditing(() -> selected.setX(parseD(b, selected.getX()))));
        yField.textProperty().addListener((o, a, b) -> applyIfEditing(() -> selected.setY(parseD(b, selected.getY()))));
        wField.textProperty().addListener((o, a, b) -> applyIfEditing(() -> selected.setWidth(parseI(b, selected.getWidth()))));
        hField.textProperty().addListener((o, a, b) -> applyIfEditing(() -> selected.setHeight(parseI(b, selected.getHeight()))));
        rotField.textProperty().addListener((o, a, b) -> applyIfEditing(() -> selected.setRotation(parseD(b, selected.getRotation()))));
        visibleCheck.selectedProperty().addListener((o, a, b) -> applyIfEditing(() -> selected.setVisible(b)));

        box.getChildren().addAll(title, grid);
        setInspectorEnabled(false);
        return box;
    }

    private void addRow(GridPane grid, int row, String label, TextField field) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #b0b0b0;");
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }

    private void applyIfEditing(Runnable action) {
        if (suppressInspectorEvents || selected == null) return;
        try { action.run(); } catch (Exception ignore) { /* entrada invalida */ }
    }

    private void setSelected(GameObject go) {
        this.selected = go;
        suppressInspectorEvents = true;
        if (go == null) {
            setInspectorEnabled(false);
            nameField.setText(""); xField.setText(""); yField.setText("");
            wField.setText(""); hField.setText(""); rotField.setText("");
            visibleCheck.setSelected(false);
        } else {
            setInspectorEnabled(true);
            nameField.setText(go.getName());
            xField.setText(String.valueOf(go.getX()));
            yField.setText(String.valueOf(go.getY()));
            wField.setText(String.valueOf(go.getWidth()));
            hField.setText(String.valueOf(go.getHeight()));
            rotField.setText(String.valueOf(go.getRotation()));
            visibleCheck.setSelected(go.isVisible());
        }
        suppressInspectorEvents = false;
    }

    private void setInspectorEnabled(boolean enabled) {
        nameField.setDisable(!enabled);
        xField.setDisable(!enabled);
        yField.setDisable(!enabled);
        wField.setDisable(!enabled);
        hField.setDisable(!enabled);
        rotField.setDisable(!enabled);
        visibleCheck.setDisable(!enabled);
    }

    private static double parseD(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static int parseI(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private void setStatus(String text) {
        if (status != null) status.setText(" " + text);
    }

    // ---------------- Ponte de render ----------------

    private void startRenderBridge(Canvas canvas) {
        AnimationTimer timer = new AnimationTimer() {
            private BufferedImage buffer;
            private WritableImage fxImage;

            @Override
            public void handle(long now) {
                int w = (int) Math.floor(canvas.getWidth());
                int h = (int) Math.floor(canvas.getHeight());
                if (w <= 0 || h <= 0) return;

                if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
                    buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    fxImage = new WritableImage(w, h);
                }

                Graphics2D g2d = buffer.createGraphics();
                try {
                    game.renderWorldTo(g2d, w, h, selected);
                } finally {
                    g2d.dispose();
                }

                SwingFXUtils.toFXImage(buffer, fxImage);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, w, h);
                gc.drawImage(fxImage, 0, 0);
            }
        };
        timer.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
