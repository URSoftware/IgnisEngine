package com.ignis.editor.fx;

import com.ignis.core.Circle;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisProjectIO;
import com.ignis.core.Project;
import com.ignis.core.Scene;
import com.ignis.core.Square;
import com.ignis.core.SpriteComponent;
import com.ignis.core.Texture2D;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.geometry.Orientation;
import javafx.scene.layout.Priority;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToggleButton;
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
    private boolean suppressSelectionEvents = false;
    private File projectFolder;
    private File currentIgnisFile;
    private Project currentProject;
    // Prefabs: gerenciador lazy, recriado quando a pasta do projeto muda.
    private com.ignis.core.PrefabManager prefabManager;
    private File prefabManagerFolder;
    private Stage primaryStage;
    private Menu recentMenu;
    private boolean projectDirty = false;
    private javafx.animation.Timeline projectAutoSaveTimer;
    private Button playButton;
    private Button stopButton;
    private boolean playing = false;
    private Canvas viewportCanvas;
    private SplitPane mainSplit;
    private SplitPane leftSplit;
    private SplitPane centerSplit;
    private FxConsolePanel console;
    private CheckMenuItem consoleMenuItem;
    private FxSettingsWindow settingsWindow;

    // Desfazer/Refazer (padrao Command). Cobre criar/deletar/duplicar/colar/
    // renomear/reordenar e transformacoes por gizmo (via TransformListener).
    private final UndoManager undoManager = new UndoManager();
    private MenuItem undoItem;
    private MenuItem redoItem;
    // Estado capturado no inicio de um arraste de gizmo (para o comando de transformacao).
    private GameObject transformObj;
    private double txStartX, txStartY, txStartRot;
    private int txStartW, txStartH;
    
    private static final class TransformState {
        final GameObject obj;
        final double x, y, rotation;
        final int w, h;
        TransformState(GameObject obj, double x, double y, double rotation, int w, int h) {
            this.obj = obj; this.x = x; this.y = y; this.rotation = rotation; this.w = w; this.h = h;
        }
    }
    private final java.util.List<TransformState> secondaryStartStates = new java.util.ArrayList<>();
    // Fonte AWT (nao exibida) usada apenas como 'source' nao-nulo ao fabricar
    // KeyEvent/MouseEvent que roteiam o input do viewport FX para o singleton Input.
    private final java.awt.Component awtEventSource = new java.awt.Canvas();

    private final TreeItem<String> hierarchyRoot = new TreeItem<>("Cena");
    private TreeView<String> hierarchy;
    private TreeView<File> assetTree;
    private Label status;

    // Campos do Inspector
    private TextField nameField, xField, yField, wField, hField, rotField;
    private CheckBox visibleCheck;
    // Snapshot para desfazer edicoes digitadas no Inspector (valor capturado no foco).
    private GameObject inspectorEditObj;
    private Object inspectorEditOld;
    // Seções do Inspector dependentes do tipo do objeto (cor/sprite/collider/camera/scripts),
    // reconstruídas a cada seleção (ver rebuildInspectorExtras).
    private VBox inspectorExtras;

    private Label cameraPosLabel;
    private Label cameraZoomLabel;
    private GameObject clipboardObject;
    // Multi-selecao: lista de objetos selecionados secundariamente (alem do primario 'selected').
    // O primario recebe gizmo/drag; os secundarios recebem contorno tracejado (editorHighlights).
    private final java.util.List<GameObject> secondarySelection = new java.util.ArrayList<>();
    private ToggleButton btnMove;
    private ToggleButton btnRotate;
    private ToggleButton btnScale;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        seedSampleScene();
        // O editor JavaFX renderiza via AnimationTimer (renderWorldTo). O pipeline
        // AWT (repaint/BufferStrategy) e desnecessario e gera trabalho inutil.
        game.setSuppressAwtRepaint(true);

        // Defaults de viewport persistidos nas Configuracoes (1a execucao mantem o
        // default atual do Game, pois passa o valor corrente como fallback).
        game.setShowGrid(EditorPrefs.getGridVisible(game.isShowGrid()));
        game.setGridSize(EditorPrefs.getGridSize(game.getGridSize()));
        game.setShowColliders(EditorPrefs.getShowColliders(game.isShowColliders()));

        BorderPane root = new BorderPane();

        // Menu + ToolBar (Fase 3)
        Button btnOpen = new Button("Abrir");
        btnOpen.setOnAction(e -> openProjectViaChooser(stage));
        Button btnBuild = new Button("Build");
        btnBuild.setOnAction(e -> openBuildDialog());
        playButton = new Button("▶ Play");
        playButton.setOnAction(e -> playWorld());
        stopButton = new Button("⏹ Stop");
        stopButton.setOnAction(e -> stopWorld());
        stopButton.setDisable(true);

        btnMove = new ToggleButton("Mover (W)");
        btnRotate = new ToggleButton("Rotacionar (E)");
        btnScale = new ToggleButton("Redimensionar (R)");
        ToggleGroup toolGroup = new ToggleGroup();
        btnMove.setToggleGroup(toolGroup);
        btnRotate.setToggleGroup(toolGroup);
        btnScale.setToggleGroup(toolGroup);
        btnMove.setSelected(true);
        
        btnMove.setOnAction(e -> game.setCurrentTool(com.ignis.core.Game.ToolType.MOVE));
        btnRotate.setOnAction(e -> game.setCurrentTool(com.ignis.core.Game.ToolType.ROTATE));
        btnScale.setOnAction(e -> game.setCurrentTool(com.ignis.core.Game.ToolType.SCALE));

        Button btnZoomIn = new Button("Zoom In");
        btnZoomIn.setOnAction(e -> zoomCamera(1.25));
        Button btnZoomOut = new Button("Zoom Out");
        btnZoomOut.setOnAction(e -> zoomCamera(0.8));
        Button btnResetCam = new Button("Reset Cam");
        btnResetCam.setOnAction(e -> resetCamera());
        Button btnFocusSelected = new Button("Focus Selected");
        btnFocusSelected.setOnAction(e -> focusCameraOnSelected());

        cameraPosLabel = new Label("Cam Pos: (0.0, 0.0)");
        cameraPosLabel.getStyleClass().add("toolbar-label");
        cameraZoomLabel = new Label("Zoom: 100%");
        cameraZoomLabel.getStyleClass().add("toolbar-label");

        ToolBar toolBar = new ToolBar(
            btnOpen, btnBuild, new Separator(),
            playButton, stopButton, new Separator(),
            btnMove, btnRotate, btnScale, new Separator(),
            btnZoomIn, btnZoomOut, btnResetCam, btnFocusSelected, new Separator(),
            cameraPosLabel, cameraZoomLabel
        );
        root.setTop(new VBox(buildMenuBar(stage), toolBar));

        // Configure selection listener from Game
        game.addSelectionListener(go -> {
            Platform.runLater(() -> {
                if (suppressSelectionEvents) return;
                // Rejeitar notificacoes obsoletas: so agir se o game AINDA aponta
                // para 'go' no momento em que a lambda roda. Sem isso, lambdas
                // enfileiradas por Platform.runLater podem operar com selecoes que
                // ja mudaram, causando ping-pong infinito entre objetos sobrepostos.
                if (game.getSelectedObject() == go && selected != go) {
                    selectEntity(go);
                }
            });
        });

        setupProjectAutoSave();

        // ---- Viewport central ----
        Pane viewportPane = new Pane();
        Canvas canvas = new Canvas();
        canvas.widthProperty().bind(viewportPane.widthProperty());
        canvas.heightProperty().bind(viewportPane.heightProperty());
        viewportPane.getChildren().add(canvas);
        this.viewportCanvas = canvas;
        wireFxInputToEngine(canvas);

        // ---- Hierarchy + Asset Browser (esquerda) ----
        hierarchy = buildHierarchy();
        javafx.scene.Node assetBrowser = buildAssetBrowser();
        leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.getItems().addAll(hierarchy, assetBrowser);
        leftSplit.setDividerPositions(0.6);

        // ---- Inspector (direita) ----
        javafx.scene.Node inspector = buildInspector();

        mainSplit = new SplitPane();
        mainSplit.getItems().addAll(leftSplit, viewportPane, inspector);
        mainSplit.setDividerPositions(0.2, 0.78);

        // ---- Console dockavel (abaixo do viewport/paineis) ----
        console = new FxConsolePanel();
        console.startCapture();
        centerSplit = new SplitPane();
        centerSplit.setOrientation(Orientation.VERTICAL);
        centerSplit.getItems().add(mainSplit);
        if (EditorPrefs.isConsoleVisible()) {
            centerSplit.getItems().add(console);
            centerSplit.setDividerPositions(0.76);
        }
        root.setCenter(centerSplit);

        status = new Label(" Editor JavaFX (Fase 2) — abra um projeto .ignis (Arquivo > Abrir projeto)");
        status.getStyleClass().add("status-bar");
        root.setBottom(status);

        // F4-B: restaura tamanho da janela salvo (default 1100x700). Respeita a
        // preferencia "Lembrar layout" (Configuracoes > Geral).
        double[] savedBounds = EditorPrefs.isRememberLayout() ? EditorPrefs.getWindowBounds() : null;
        double initW = (savedBounds != null && !Double.isNaN(savedBounds[2])) ? savedBounds[2] : 1100;
        double initH = (savedBounds != null && !Double.isNaN(savedBounds[3])) ? savedBounds[3] : 700;
        javafx.scene.Scene scene = new javafx.scene.Scene(root, initW, initH);
        FxTheme.apply(scene);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), () -> openProjectViaChooser(stage));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN), this::openBuildDialog);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::saveProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN), () -> saveProjectAs(stage));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::playWorld);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), this::stopWorld);
        // Refazer tambem por Ctrl+Shift+Z (alem de Ctrl+Y no menu Editar).
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::doRedo);

        // Scene key event filter for tools, selection controls, camera resets
        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            // Durante o Play, os atalhos de ferramenta (W/E/R/F/etc.) NAO devem
            // capturar teclas — elas pertencem ao jogo (ex: W = mover no script).
            // O input do viewport ja roteia teclado ao Input da engine em Play.
            if (playing) return;
            if (scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
                return;
            }

            if (ev.getCode() == KeyCode.W && !ev.isControlDown() && !ev.isAltDown() && !ev.isShiftDown()) {
                btnMove.setSelected(true);
                game.setCurrentTool(com.ignis.core.Game.ToolType.MOVE);
                ev.consume();
            } else if (ev.getCode() == KeyCode.E && !ev.isControlDown() && !ev.isAltDown() && !ev.isShiftDown()) {
                btnRotate.setSelected(true);
                game.setCurrentTool(com.ignis.core.Game.ToolType.ROTATE);
                ev.consume();
            } else if (ev.getCode() == KeyCode.R && !ev.isControlDown() && !ev.isAltDown() && !ev.isShiftDown()) {
                btnScale.setSelected(true);
                game.setCurrentTool(com.ignis.core.Game.ToolType.SCALE);
                ev.consume();
            } else if (ev.getCode() == KeyCode.F && !ev.isControlDown() && !ev.isAltDown() && !ev.isShiftDown()) {
                focusCameraOnSelected();
                ev.consume();
            } else if (ev.getCode() == KeyCode.DELETE) {
                deleteSelected();
                ev.consume();
            } else if (ev.getCode() == KeyCode.F2) {
                renameSelected();
                ev.consume();
            } else if (ev.getCode() == KeyCode.HOME) {
                resetCamera();
                ev.consume();
            }

            if (ev.isControlDown()) {
                if (ev.getCode() == KeyCode.D) {
                    duplicateSelected();
                    ev.consume();
                } else if (ev.getCode() == KeyCode.C) {
                    copySelected();
                    ev.consume();
                } else if (ev.getCode() == KeyCode.V) {
                    pasteSelected();
                    ev.consume();
                } else if (ev.getCode() == KeyCode.EQUALS || ev.getCode() == KeyCode.ADD) {
                    zoomCamera(1.25);
                    ev.consume();
                } else if (ev.getCode() == KeyCode.MINUS || ev.getCode() == KeyCode.SUBTRACT) {
                    zoomCamera(0.8);
                    ev.consume();
                } else if (ev.getCode() == KeyCode.DIGIT0 || ev.getCode() == KeyCode.NUMPAD0) {
                    com.ignis.core.Camera cam = game.getMainCamera();
                    if (cam != null) {
                        cam.setZoom(1.0);
                        updateCameraLabels();
                    }
                    ev.consume();
                } else if (ev.getCode() == KeyCode.G) {
                    game.setShowGrid(!game.isShowGrid());
                    ev.consume();
                }
            }
        });
        stage.setTitle("IgnisEngine — Editor (JavaFX) [migracao]");
        try {
            File iconFile = new File("Icons/IconeIgnis.png");
            if (iconFile.exists()) {
                stage.getIcons().add(new javafx.scene.image.Image(iconFile.toURI().toString()));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        stage.setScene(scene);

        // F4-B: restaura posicao e estado de maximizacao salvos (com guarda de tela).
        if (savedBounds != null && !Double.isNaN(savedBounds[0]) && !Double.isNaN(savedBounds[1])
                && isOnScreen(savedBounds[0], savedBounds[1])) {
            stage.setX(savedBounds[0]);
            stage.setY(savedBounds[1]);
        }
        if (EditorPrefs.isRememberLayout() && EditorPrefs.isWindowMaximized()) stage.setMaximized(true);

        stage.setOnCloseRequest(e -> {
            saveLayout();
            if (console != null) console.stopCapture();
            stopGameLoop();
            try { com.ignis.mcp.McpService.stop(); } catch (Exception ignore) {}
            try { com.ignis.collab.CollabSession.get().stop(); } catch (Exception ignore) {}
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        // Divisores so assumem a posicao salva apos o primeiro layout da cena.
        Platform.runLater(this::restoreDividers);

        startRenderBridge(canvas);

        // Tela inicial de selecao de projeto (analoga ao startup do editor Swing).
        Platform.runLater(() -> {
            String last = EditorPrefs.getLastProject();
            if (last != null) {
                File lastFile = new File(last);
                if (lastFile.exists() && lastFile.isFile()) {
                    if (openProjectFile(lastFile)) {
                        return; // Carregado com sucesso!
                    }
                }
            }
            showProjectStartup(stage, true);
        });
    }

    private void seedSampleScene() {
        game.addEntity(new Square("Quadrado", game, 0, 0, 90, 90));
        game.addEntity(new Circle("Circulo", game, 140, 60, 80, 80));
        game.addEntity(new Square("Quadrado2", game, -120, 90, 70, 70));
    }

    // ---------------- Menu ----------------

    private MenuBar buildMenuBar(Stage stage) {
        Menu file = new Menu("Arquivo");
        MenuItem novo = new MenuItem("Novo projeto…");
        novo.setOnAction(e -> newProject(stage));
        MenuItem open = new MenuItem("Abrir projeto…");
        open.setOnAction(e -> openProjectViaChooser(stage));
        recentMenu = new Menu("Abrir recente");
        rebuildRecentMenu(stage);
        MenuItem selecionar = new MenuItem("Selecionar projeto…");
        selecionar.setOnAction(e -> showProjectStartup(stage, false));
        MenuItem salvar = new MenuItem("Salvar");
        salvar.setOnAction(e -> saveProject());
        MenuItem salvarComo = new MenuItem("Salvar como…");
        salvarComo.setOnAction(e -> saveProjectAs(stage));
        MenuItem fechar = new MenuItem("Fechar projeto");
        fechar.setOnAction(e -> closeProject(stage));
        MenuItem prefs = new MenuItem("Configuracoes…");
        prefs.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN));
        prefs.setOnAction(e -> openSettings());
        MenuItem exit = new MenuItem("Sair");
        exit.setOnAction(e -> { saveLayout(); stopGameLoop(); stage.close(); Platform.exit(); System.exit(0); });
        file.getItems().addAll(novo, open, recentMenu, selecionar, new SeparatorMenuItem(),
                salvar, salvarComo, prefs, new SeparatorMenuItem(), fechar, exit);

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
        
        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
        zoomInItem.setOnAction(e -> zoomCamera(1.25));
        
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOutItem.setOnAction(e -> zoomCamera(0.8));
        
        MenuItem zoom100Item = new MenuItem("Zoom to 100%");
        zoom100Item.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        zoom100Item.setOnAction(e -> {
            com.ignis.core.Camera cam = game.getMainCamera();
            if (cam != null) {
                cam.setZoom(1.0);
                updateCameraLabels();
            }
        });
        
        MenuItem resetCamItem = new MenuItem("Reset Camera");
        resetCamItem.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
        resetCamItem.setOnAction(e -> resetCamera());
        
        MenuItem focusSelectedItem = new MenuItem("Focus on Selected");
        focusSelectedItem.setAccelerator(new KeyCodeCombination(KeyCode.F));
        focusSelectedItem.setOnAction(e -> focusCameraOnSelected());
        
        CheckMenuItem showGridItem = new CheckMenuItem("Show Grid");
        showGridItem.setSelected(game.isShowGrid());
        showGridItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
        showGridItem.setOnAction(e -> game.setShowGrid(showGridItem.isSelected()));
        
        Menu gridSizeMenu = new Menu("Grid Size");
        ToggleGroup gridSizeGroup = new ToggleGroup();
        int[] gridSizes = {16, 32, 64, 128};
        for (int size : gridSizes) {
            RadioMenuItem sizeItem = new RadioMenuItem(size + " px");
            sizeItem.setToggleGroup(gridSizeGroup);
            sizeItem.setSelected(game.getGridSize() == size);
            sizeItem.setOnAction(e -> game.setGridSize(size));
            gridSizeMenu.getItems().add(sizeItem);
        }
        
        CheckMenuItem showCollidersItem = new CheckMenuItem("Show Colliders");
        showCollidersItem.setSelected(game.isShowColliders());
        showCollidersItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        showCollidersItem.setOnAction(e -> game.setShowColliders(showCollidersItem.isSelected()));

        consoleMenuItem = new CheckMenuItem("Mostrar Console");
        consoleMenuItem.setSelected(EditorPrefs.isConsoleVisible());
        consoleMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.J, KeyCombination.CONTROL_DOWN));
        consoleMenuItem.setOnAction(e -> setConsoleVisible(consoleMenuItem.isSelected()));

        view.getItems().addAll(
            zoomInItem, zoomOutItem, zoom100Item, new SeparatorMenuItem(),
            resetCamItem, focusSelectedItem, new SeparatorMenuItem(),
            showGridItem, gridSizeMenu, new SeparatorMenuItem(),
            showCollidersItem, new SeparatorMenuItem(),
            consoleMenuItem
        );

        Menu help = new Menu("Ajuda");
        MenuItem about = new MenuItem("Sobre");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "IgnisEngine — editor JavaFX (Fase 3 da migracao).").showAndWait());
        help.getItems().add(about);

        return new MenuBar(file, buildEditMenu(), buildSceneMenu(), tools, view, help);
    }

    // Menu "Editar": desfazer/refazer. Os rotulos/estado sao mantidos por
    // updateUndoRedoUi (via UndoManager.onChange).
    private Menu buildEditMenu() {
        Menu edit = new Menu("Editar");
        undoItem = new MenuItem("Desfazer");
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.setOnAction(e -> doUndo());
        redoItem = new MenuItem("Refazer");
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoItem.setOnAction(e -> doRedo());
        edit.getItems().addAll(undoItem, redoItem);
        undoManager.setOnChange(this::updateUndoRedoUi);
        updateUndoRedoUi();
        return edit;
    }

    private void doUndo() {
        if (!undoManager.canUndo()) { setStatus("Nada para desfazer."); return; }
        String name = undoManager.peekUndoName();
        undoManager.undo();
        setStatus("Desfeito: " + name);
    }

    private void doRedo() {
        if (!undoManager.canRedo()) { setStatus("Nada para refazer."); return; }
        String name = undoManager.peekRedoName();
        undoManager.redo();
        setStatus("Refeito: " + name);
    }

    private void updateUndoRedoUi() {
        if (undoItem != null) {
            undoItem.setDisable(!undoManager.canUndo());
            undoItem.setText(undoManager.canUndo() ? "Desfazer: " + undoManager.peekUndoName() : "Desfazer");
        }
        if (redoItem != null) {
            redoItem.setDisable(!undoManager.canRedo());
            redoItem.setText(undoManager.canRedo() ? "Refazer: " + undoManager.peekRedoName() : "Refazer");
        }
    }

    // Adiciona/remove uma entidade tratando o caso especial de Camera (lista propria).
    private void addEntityTracked(GameObject o) {
        game.addEntity(o);
        if (o instanceof com.ignis.core.Camera) game.addCamera((com.ignis.core.Camera) o);
    }

    private void removeEntityTracked(GameObject o) {
        game.removeEntity(o);
        if (o instanceof com.ignis.core.Camera) {
            try { game.getCameras().remove(o); } catch (Exception ignore) { /* best-effort */ }
        }
    }

    // Menu "Cena": criar/duplicar/renomear/deletar/reordenar entidades.
    private Menu buildSceneMenu() {
        Menu scene = new Menu("Cena");
        MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
        criarObjeto.setOnAction(e -> createEntity("GameObject"));
        MenuItem criarCamera = new MenuItem("Criar Câmera");
        criarCamera.setOnAction(e -> createEntity("Camera"));
        
        MenuItem dup = new MenuItem("Duplicar selecionado");
        dup.setOnAction(e -> duplicateSelected());
        MenuItem ren = new MenuItem("Renomear selecionado…");
        ren.setOnAction(e -> renameSelected());
        MenuItem del = new MenuItem("Deletar selecionado");
        del.setOnAction(e -> deleteSelected());
        MenuItem up = new MenuItem("Mover para cima");
        up.setOnAction(e -> moveSelected(-1));
        MenuItem down = new MenuItem("Mover para baixo");
        down.setOnAction(e -> moveSelected(1));
        MenuItem top = new MenuItem("Mover para o topo");
        top.setOnAction(e -> moveSelectedTo(Integer.MAX_VALUE));
        MenuItem bottom = new MenuItem("Mover para o fundo");
        bottom.setOnAction(e -> moveSelectedTo(0));
        MenuItem savePrefab = new MenuItem("Salvar selecionado como Prefab…");
        savePrefab.setOnAction(e -> saveSelectedAsPrefab());
        MenuItem instPrefab = new MenuItem("Instanciar Prefab…");
        instPrefab.setOnAction(e -> instantiatePrefabDialog());
        scene.getItems().addAll(criarObjeto, criarCamera, new SeparatorMenuItem(), dup, ren, del,
                new SeparatorMenuItem(), up, down, top, bottom,
                new SeparatorMenuItem(), savePrefab, instPrefab);
        return scene;
    }

    private ContextMenu buildHierarchyContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
        criarObjeto.setOnAction(e -> createEntity("GameObject"));
        MenuItem criarCamera = new MenuItem("Criar Câmera");
        criarCamera.setOnAction(e -> createEntity("Camera"));
        menu.getItems().addAll(criarObjeto, criarCamera, new SeparatorMenuItem());
        MenuItem dup = new MenuItem("Duplicar (Ctrl+D)");
        dup.setOnAction(e -> duplicateSelected());
        MenuItem ren = new MenuItem("Renomear… (F2)");
        ren.setOnAction(e -> renameSelected());
        MenuItem del = new MenuItem("Deletar (Delete)");
        del.setOnAction(e -> deleteSelected());
        MenuItem copyItem = new MenuItem("Copiar (Ctrl+C)");
        copyItem.setOnAction(e -> copySelected());
        MenuItem pasteItem = new MenuItem("Colar (Ctrl+V)");
        pasteItem.setOnAction(e -> pasteSelected());

        Menu ordenar = new Menu("Ordenar");
        MenuItem up = new MenuItem("Mover para cima");
        up.setOnAction(e -> moveSelected(-1));
        MenuItem down = new MenuItem("Mover para baixo");
        down.setOnAction(e -> moveSelected(1));
        MenuItem top = new MenuItem("Mover para o topo");
        top.setOnAction(e -> moveSelectedTo(Integer.MAX_VALUE));
        MenuItem bottom = new MenuItem("Mover para o fundo");
        bottom.setOnAction(e -> moveSelectedTo(0));
        ordenar.getItems().addAll(up, down, top, bottom);

        MenuItem savePrefab = new MenuItem("Salvar como Prefab…");
        savePrefab.setOnAction(e -> saveSelectedAsPrefab());
        MenuItem instPrefab = new MenuItem("Instanciar Prefab…");
        instPrefab.setOnAction(e -> instantiatePrefabDialog());

        menu.getItems().addAll(dup, ren, del,
                new SeparatorMenuItem(), copyItem, pasteItem, new SeparatorMenuItem(), ordenar,
                new SeparatorMenuItem(), savePrefab, instPrefab);
        return menu;
    }

    // ---------------- Ciclo de vida do projeto ----------------
    // Tela de selecao inicial + abrir/criar/salvar/fechar/trocar projeto, com
    // persistencia de ultimo/recentes (EditorPrefs). Reproduz o fluxo do editor
    // Swing (showStartupDialog/showNewProjectDialog/showOpenProjectDialog/doSaveProject),
    // mas 100% JavaFX. Tudo aditivo; nada em com.ignis.core muda.

    // Mostra a tela de selecao em laco ate carregar um projeto ou o usuario sair.
    // exitOnCancel=true (startup/fechar): cancelar sem projeto encerra o app, como o Swing.
    // exitOnCancel=false (trocar): cancelar apenas mantem o estado atual.
    private void showProjectStartup(Stage stage, boolean exitOnCancel) {
        while (true) {
            FxProjectStartupDialog.Choice c =
                    FxProjectStartupDialog.show(stage, listProjectIgnisFiles(), recentProjectFiles());
            switch (c.kind) {
                case OPEN:
                    if (c.file != null && openProjectFile(c.file)) return;
                    break;
                case NEW:
                    if (newProject(stage)) return;
                    break;
                case IMPORT:
                    if (openProjectViaChooser(stage)) return;
                    break;
                case EXIT:
                default:
                    if (exitOnCancel && currentProject == null) {
                        saveLayout();
                        stopGameLoop();
                        Platform.exit();
                        System.exit(0);
                    }
                    return;
            }
        }
    }

    // Abre via FileChooser (.ignis). Retorna true se carregou.
    private boolean openProjectViaChooser(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Abrir projeto .ignis");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto Ignis (*.ignis)", "*.ignis"));
        try {
            File rootDir = IgnisProjectIO.getProjectsRootFolder();
            if (rootDir != null && rootDir.isDirectory()) fc.setInitialDirectory(rootDir);
        } catch (Exception ignore) { /* sem dir inicial */ }

        File fileChosen = fc.showOpenDialog(stage);
        if (fileChosen == null) return false;
        return openProjectFile(fileChosen);
    }

    // Carga efetiva de um .ignis (sem dialogos). Reaproveitada por dialog/recentes/chooser.
    private boolean openProjectFile(File ignisFile) {
        if (ignisFile == null || !ignisFile.isFile()) {
            new Alert(Alert.AlertType.ERROR, "Arquivo de projeto inexistente:\n" + ignisFile).showAndWait();
            return false;
        }
        try {
            clearGameCameras();
            Project project = IgnisProjectIO.load(ignisFile, game);
            this.currentProject = project;
            this.currentIgnisFile = ignisFile;
            this.projectFolder = IgnisProjectIO.getProjectFolder(ignisFile);
            try {
                game.setScriptManager(new com.ignis.core.ScriptManager(projectFolder));
            } catch (Exception ignore) { /* scripts opcionais para Play */ }
            maybeAutoStartMcp();
            game.getEntities().clear();
            setSelected(null);
            Scene scene = project.getCurrentScene();
            if (scene != null) {
                for (GameObject e : scene.getEntities()) {
                    e.setGame(game);
                    game.addEntity(e);
                }
                // Carrega o mundo (limites/barreiras) da cena para o game vivo.
                game.setWorld(scene.getWorld());
            }
            // Compila e recarrega os scripts para instanciá-los e carregar suas variáveis no editor
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm != null) {
                sm.compileAllScripts();
                reloadAllScriptInstances();
            }
            refreshHierarchy();
            refreshAssetBrowser();
            undoManager.clear();
            EditorPrefs.addRecent(ignisFile);
            rebuildRecentMenu(primaryStage);
            stage().setTitle("IgnisEngine — " + project.getProjectName() + " (JavaFX)");
            setStatus("Projeto carregado: " + project.getProjectName()
                    + " (" + game.getEntities().size() + " objetos)");
            return true;
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir projeto:\n" + ex.getMessage()).showAndWait();
            return false;
        }
    }

    // Cria um novo projeto no disco (estrutura + .ignis + Square central), como o Swing.
    private boolean newProject(Stage stage) {
        TextInputDialog dlg = new TextInputDialog("MyGame");
        dlg.setTitle("Novo projeto");
        dlg.setHeaderText(null);
        dlg.setContentText("Nome do projeto:");
        java.util.Optional<String> opt = dlg.showAndWait();
        if (opt.isEmpty() || opt.get().trim().isEmpty()) return false;
        String name = opt.get().trim();
        try {
            File projectsRoot = IgnisProjectIO.getProjectsRootFolder();
            File projectMainFolder = new File(projectsRoot, name);
            if (projectMainFolder.exists()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Ja existe um projeto com esse nome. Sobrescrever?", ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText(null);
                if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return false;
            }

            Project project = IgnisProjectIO.createNew(name);
            game.getEntities().clear();
            setSelected(null);
            Scene scene = project.getCurrentScene();
            int sz = 100;
            Square sq = new Square("Square", game, -sz / 2, -sz / 2, sz, sz);
            if (scene != null) scene.addEntity(sq);
            game.addEntity(sq);

            projectMainFolder.mkdirs();
            IgnisProjectIO.ensureProjectFolderStructure(
                    new File(projectMainFolder, IgnisProjectIO.PROJECT_FOLDER_NAME));
            File ignisFile = new File(projectMainFolder, name + ".ignis");
            IgnisProjectIO.save(project, ignisFile);

            this.currentProject = project;
            this.currentIgnisFile = project.getProjectFile() != null ? project.getProjectFile() : ignisFile;
            this.projectFolder = IgnisProjectIO.getProjectFolder(this.currentIgnisFile);
            try {
                game.setScriptManager(new com.ignis.core.ScriptManager(projectFolder));
            } catch (Exception ignore) { /* scripts opcionais */ }
            maybeAutoStartMcp();
            refreshHierarchy();
            refreshAssetBrowser();
            undoManager.clear();
            EditorPrefs.addRecent(this.currentIgnisFile);
            rebuildRecentMenu(stage);
            stage.setTitle("IgnisEngine — " + name + " (JavaFX)");
            setStatus("Projeto criado: " + name);
            return true;
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao criar projeto:\n" + ex.getMessage()).showAndWait();
            return false;
        }
    }

    // Salva o projeto atual (sincroniza game -> Scene, depois IgnisProjectIO.save).
    private void saveProject() {
        if (currentProject == null || currentIgnisFile == null) {
            setStatus("Nenhum projeto aberto para salvar.");
            return;
        }
        try {
            syncEntitiesToScene();
            IgnisProjectIO.save(currentProject, currentIgnisFile);
            if (currentProject.getProjectFile() != null) currentIgnisFile = currentProject.getProjectFile();
            EditorPrefs.addRecent(currentIgnisFile);
            projectDirty = false;
            setStatus("Projeto salvo: " + currentProject.getProjectName());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao salvar:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void saveProjectAs(Stage stage) {
        if (currentProject == null) {
            setStatus("Nenhum projeto aberto.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Salvar projeto como");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto Ignis (*.ignis)", "*.ignis"));
        fc.setInitialFileName(currentProject.getProjectName() + ".ignis");
        File dest = fc.showSaveDialog(stage);
        if (dest == null) return;
        try {
            // IgnisProjectIO.save deriva a pasta e a identidade do NOME do projeto; atualizar
            // o nome para o destino escolhido evita pasta e project.json divergentes.
            String novoNome = dest.getName().replaceFirst("(?i)\\.ignis$", "");
            if (!novoNome.isEmpty()) currentProject.setProjectName(novoNome);
            syncEntitiesToScene();
            IgnisProjectIO.save(currentProject, dest);
            currentIgnisFile = currentProject.getProjectFile() != null ? currentProject.getProjectFile() : dest;
            projectFolder = IgnisProjectIO.getProjectFolder(currentIgnisFile);
            EditorPrefs.addRecent(currentIgnisFile);
            projectDirty = false;
            rebuildRecentMenu(stage);
            stage.setTitle("IgnisEngine — " + currentProject.getProjectName() + " (JavaFX)");
            // O core sempre grava em projects/<nome>/; informar o destino real, nao o escolhido.
            setStatus("Projeto salvo em: " + currentIgnisFile.getAbsolutePath());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao salvar como:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Remove as cameras residuais do Game ao trocar/fechar projeto. Scene.fromJSON
    // registra cada camera carregada via game.addCamera; sem isto elas acumulam entre
    // projetos e getActiveCamera() poderia retornar uma camera de outro projeto.
    private void clearGameCameras() {
        try {
            game.getCameras().clear();
            // Recoloca a mainCamera na lista: sem isso ela vira "fantasma"
            // (getActiveCamera() ainda a retorna, mas list_cameras/getCameras()
            // fica vazio, confundindo o MCP e o editor).
            com.ignis.core.Camera main = game.getMainCamera();
            if (main != null) game.addCamera(main);
        } catch (Exception ignore) { /* best-effort */ }
    }

    // Espelha doSaveProject() do Swing: reescreve as entidades da cena com as do game.
    // Usa scene.clear() (limpa entities + cameras + activeCamera) para nao deixar
    // cameras orfas, e reconstroi via addEntity (que re-registra cameras).
    private void syncEntitiesToScene() {
        if (currentProject == null) return;
        Scene scene = currentProject.getCurrentScene();
        if (scene == null) return;
        scene.clear();
        for (GameObject e : game.getEntities()) scene.addEntity(e);
        // Sincroniza o mundo vivo (limites/barreiras) para persistir no .ignis.
        scene.setWorld(game.getWorld());
    }

    // Fecha o projeto atual e volta para a tela de selecao (espelha o ramo sem-projeto
    // de updateProjectRoot() do Swing: libera ScriptManager e limpa o estado).
    private void closeProject(Stage stage) {
        this.currentProject = null;
        this.currentIgnisFile = null;
        this.projectFolder = null;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm != null) sm.close();
        } catch (Exception ignore) { /* best-effort */ }
        game.setScriptManager(null);
        game.getEntities().clear();
        clearGameCameras();
        setSelected(null);
        refreshHierarchy();
        refreshAssetBrowser();
        undoManager.clear();
        stage.setTitle("IgnisEngine — Editor (JavaFX)");
        setStatus("Projeto fechado.");
        // Editor ja aberto: cancelar a selecao mantem o editor vazio (nao encerra).
        showProjectStartup(stage, false);
    }

    // (Re)constroi o submenu "Abrir recente" a partir do EditorPrefs (limpa inexistentes).
    private void rebuildRecentMenu(Stage stage) {
        if (recentMenu == null) return;
        recentMenu.getItems().clear();
        java.util.List<File> recents = recentProjectFiles();
        if (recents.isEmpty()) {
            MenuItem vazio = new MenuItem("(nenhum)");
            vazio.setDisable(true);
            recentMenu.getItems().add(vazio);
            return;
        }
        for (File f : recents) {
            MenuItem mi = new MenuItem(f.getName().replaceFirst("(?i)\\.ignis$", "") + "   —  " + f.getParent());
            mi.setOnAction(e -> openProjectFile(f));
            recentMenu.getItems().add(mi);
        }
    }

    // Enumera os .ignis em projects/ (nenhuma API do core lista projetos).
    private static java.util.List<File> listProjectIgnisFiles() {
        java.util.List<File> out = new java.util.ArrayList<>();
        try {
            File root = IgnisProjectIO.getProjectsRootFolder();
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) {
                java.util.Arrays.sort(dirs,
                        java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File d : dirs) {
                    File preferred = new File(d, d.getName() + ".ignis");
                    if (preferred.isFile()) { out.add(preferred); continue; }
                    File[] ignis = d.listFiles((dir, n) -> n.toLowerCase().endsWith(".ignis"));
                    if (ignis != null && ignis.length > 0) out.add(ignis[0]);
                }
            }
        } catch (Exception ignore) { /* lista vazia em caso de erro */ }
        return out;
    }

    // Recentes validos (existentes) do EditorPrefs, como File.
    private static java.util.List<File> recentProjectFiles() {
        java.util.List<File> out = new java.util.ArrayList<>();
        for (String p : EditorPrefs.clearMissing()) out.add(new File(p));
        return out;
    }

    private Stage stage() {
        return primaryStage;
    }

    // ---------------- Persistencia de layout (Fase F4-B) ----------------

    // Salva tamanho/posicao da janela e posicoes dos divisores (best-effort).
    // Quando maximizada, nao sobrescreve os bounds restaurados (passa NaN) para
    // preservar o tamanho "janela" anterior; apenas grava o flag de maximizacao.
    private void saveLayout() {
        try {
            if (!EditorPrefs.isRememberLayout()) return;
            Stage s = primaryStage;
            if (s == null) return;
            boolean max = s.isMaximized();
            if (max) {
                EditorPrefs.saveWindowState(Double.NaN, Double.NaN, Double.NaN, Double.NaN, true);
            } else {
                EditorPrefs.saveWindowState(s.getX(), s.getY(), s.getWidth(), s.getHeight(), false);
            }
            if (mainSplit != null) EditorPrefs.saveDividers("main", mainSplit.getDividerPositions());
            if (leftSplit != null) EditorPrefs.saveDividers("left", leftSplit.getDividerPositions());
            if (centerSplit != null && centerSplit.getDividers().size() == 1) {
                EditorPrefs.saveDividers("center", centerSplit.getDividerPositions());
            }
        } catch (Exception ignore) { /* best-effort */ }
    }

    // Aplica as posicoes salvas dos divisores (apos o primeiro layout da cena).
    private void restoreDividers() {
        try {
            if (!EditorPrefs.isRememberLayout()) return;
            double[] main = EditorPrefs.getDividers("main");
            if (main != null && main.length > 0 && mainSplit != null
                    && mainSplit.getDividers().size() == main.length) {
                mainSplit.setDividerPositions(main);
            }
            double[] left = EditorPrefs.getDividers("left");
            if (left != null && left.length > 0 && leftSplit != null
                    && leftSplit.getDividers().size() == left.length) {
                leftSplit.setDividerPositions(left);
            }
            double[] center = EditorPrefs.getDividers("center");
            if (center != null && center.length == 1 && centerSplit != null
                    && centerSplit.getDividers().size() == 1) {
                centerSplit.setDividerPositions(center);
            }
        } catch (Exception ignore) { /* best-effort */ }
    }

    // A posicao (x,y) cai dentro de algum monitor conectado? Evita restaurar a
    // janela fora da area visivel (ex: monitor secundario removido).
    private static boolean isOnScreen(double x, double y) {
        try {
            for (javafx.stage.Screen sc : javafx.stage.Screen.getScreens()) {
                javafx.geometry.Rectangle2D b = sc.getVisualBounds();
                if (x >= b.getMinX() - 50 && x <= b.getMaxX() - 50
                        && y >= b.getMinY() && y <= b.getMaxY() - 50) {
                    return true;
                }
            }
        } catch (Exception ignore) { /* assume visivel em caso de erro */ return true; }
        return false;
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

            FxCodeEditor codeEditor = new FxCodeEditor(this, scriptManager, scriptName);
            codeEditor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Codigo:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Janela de Configuracoes centralizada (tema, Auto Save, editor de codigo, viewport).
    // Reusa a instancia se ja aberta (apenas traz ao foco).
    private void openSettings() {
        try {
            if (settingsWindow != null && settingsWindow.isShowing()) {
                settingsWindow.toFront();
                settingsWindow.requestFocus();
                return;
            }
            settingsWindow = new FxSettingsWindow(primaryStage, game, projectFolder);
            settingsWindow.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Configuracoes:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Sobe o bridge HTTP do MCP ao abrir um projeto, se o usuario deixou habilitado
    // nas Configuracoes (IA & MCP). Best-effort: nunca quebra o carregamento do projeto.
    private void maybeAutoStartMcp() {
        try {
            // Registra o editor vivo para o MCP expor ferramentas de cena e Play,
            // independentemente de o bridge estar ligado agora (o toggle das
            // Configuracoes tambem se beneficia).
            com.ignis.mcp.McpService.setEditorContext(game,
                    this::playWorld, this::stopWorld, this::refreshHierarchy, this::saveProjectSilently);
            if (EditorPrefs.isMcpEnabled() && projectFolder != null && projectFolder.isDirectory()) {
                com.ignis.mcp.McpService.start(projectFolder, EditorPrefs.isMcpExposeNetwork(),
                        EditorPrefs.getMcpPort(), EditorPrefs.getMcpToken());
                System.out.println("[IgnisMCP] Bridge HTTP iniciado: " + com.ignis.mcp.McpService.getUrl());
            }
        } catch (Exception ex) {
            System.err.println("[IgnisMCP] Falha ao auto-iniciar o bridge: " + ex.getMessage());
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
            clearSecondarySelection();
            // Recompilar e recarregar todos os scripts antes de iniciar o Play
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm != null) {
                sm.compileAllScripts();
                reloadAllScriptInstances();
            }
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
        // Recarregar os scripts para restaurar o estado do editor e suas variáveis
        reloadAllScriptInstances();
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

        // O botao direito NAO e encaminhado ao engine (game.dispatchEvent) para nao acionar
        // selecao/drag do editor; ele e tratado so em setOnContextMenuRequested. O Input
        // (estado para scripts em Play) continua recebendo todos os botoes.
        canvas.setOnMousePressed(e -> {
            canvas.requestFocus();
            java.awt.event.MouseEvent awtEvent = buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_PRESSED);
            com.ignis.core.Input.getInstance().mousePressed(awtEvent);
            // Multi-selecao via Ctrl+Click esquerdo no viewport (so em edicao, nao em Play).
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.isControlDown() && !playing) {
                GameObject clicked = game.getObjectAt((int) e.getX(), (int) e.getY());
                if (clicked != null) {
                    if (clicked == selected) {
                        // Ctrl+Click no primario: promove o 1o secundario a primario (se houver)
                        // e move o primario atual para a lista secundaria.
                        if (!secondarySelection.isEmpty()) {
                            GameObject newPrimary = secondarySelection.remove(0);
                            secondarySelection.add(selected);
                            selectEntity(newPrimary);
                            syncHighlights();
                        }
                        // Se nao ha secundarios, Ctrl+Click no unico selecionado nao faz nada.
                    } else {
                        toggleSecondarySelection(clicked);
                    }
                }
                // NAO despacha ao engine; impede que ele substitua a selecao primaria.
                return;
            }
            // Clique normal (sem Ctrl): limpa a selecao secundaria.
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && !e.isControlDown()) {
                clearSecondarySelection();
            }
            if (e.getButton() != javafx.scene.input.MouseButton.SECONDARY) game.dispatchEvent(awtEvent);
        });
        canvas.setOnMouseReleased(e -> {
            java.awt.event.MouseEvent awtEvent = buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_RELEASED);
            com.ignis.core.Input.getInstance().mouseReleased(awtEvent);
            if (e.getButton() != javafx.scene.input.MouseButton.SECONDARY) game.dispatchEvent(awtEvent);
        });
        canvas.setOnMouseMoved(e -> {
            java.awt.event.MouseEvent awtEvent = buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_MOVED);
            com.ignis.core.Input.getInstance().mouseMoved(awtEvent);
            game.dispatchEvent(awtEvent);
        });
        canvas.setOnMouseDragged(e -> {
            java.awt.event.MouseEvent awtEvent = buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_DRAGGED);
            com.ignis.core.Input.getInstance().mouseDragged(awtEvent);
            if (e.getButton() != javafx.scene.input.MouseButton.SECONDARY) game.dispatchEvent(awtEvent);
        });

        canvas.setOnScroll(e -> {
            double deltaY = e.getDeltaY();
            if (deltaY != 0) {
                double factor = deltaY > 0 ? 1.15 : 0.85;
                zoomCamera(factor);
            }
        });

        ContextMenu viewportMenu = buildViewportContextMenu();
        canvas.setOnContextMenuRequested(e -> {
            game.cancelDrag();
            GameObject clicked = game.getObjectAt((int) e.getX(), (int) e.getY());
            if (clicked != null) {
                selectEntity(clicked);
            } else {
                selectEntity(null);
            }
            viewportMenu.show(canvas, e.getScreenX(), e.getScreenY());
        });
        // Ao dispensar o menu de contexto (ex: clique esquerdo fora), garantir que
        // qualquer estado de arraste residual e limpo. Sem isso, o drag do AWT pode
        // ficar preso em GizmoDragMode.CENTER e causar saltos de coordenadas.
        viewportMenu.setOnHidden(e -> game.cancelDrag());
        // Selecao por clique esquerdo NAO e tratada aqui: o engine (handleMousePress, via
        // dispatchEvent) ja seleciona e notifica o selectionListener -> selectEntity. Tratar
        // tambem aqui causaria selecao dupla/concorrente.

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
        // Habilitar multi-selecao nativa (Ctrl+Click / Shift+Click) na TreeView.
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // Cell factory: selecionar o item sob o cursor no clique DIREITO (SECONDARY).
        // Sem isso, JavaFX TreeView so seleciona no clique esquerdo, e o menu de
        // contexto opera no item previamente selecionado — nao no que esta sob o cursor.
        tree.setCellFactory(tv -> {
            javafx.scene.control.TreeCell<String> cell = new javafx.scene.control.TreeCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.setOnMousePressed(e -> {
                if (!cell.isEmpty() && e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    tv.getSelectionModel().select(cell.getTreeItem());
                }
            });
            cell.setOnDragDetected(e -> {
                if (!cell.isEmpty() && cell.getItem() != null && !cell.getItem().equals("Cena")) {
                    javafx.scene.input.Dragboard db = cell.startDragAndDrop(javafx.scene.input.TransferMode.ANY);
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(cell.getItem());
                    db.setContent(content);
                    e.consume();
                }
            });
            return cell;
        });
        // Listener de selecao: sincroniza o primario + secundarios a partir da
        // multi-selecao nativa do TreeView. O ultimo item na lista de selecao do
        // modelo e o primario (recebe gizmo/Inspector); os demais viram secundarios.
        tree.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<TreeItem<String>>) change -> {
            if (suppressSelectionEvents) return;
            suppressSelectionEvents = true;
            try {
                var selItems = tree.getSelectionModel().getSelectedItems();
                java.util.List<GameObject> ents = game.getEntities();
                if (selItems.isEmpty() || (selItems.size() == 1 && selItems.get(0) == hierarchyRoot)) {
                    setSelected(null);
                    clearSecondarySelection();
                    return;
                }
                // O item com foco (ultimo clicado) vira o primario.
                TreeItem<String> focusedItem = selItems.get(selItems.size() - 1);
                if (focusedItem == null || focusedItem == hierarchyRoot) focusedItem = selItems.get(0);
                
                TreeItem<String> goItem = focusedItem;
                if (focusedItem.getParent() != null && focusedItem.getParent() != hierarchyRoot) {
                    goItem = focusedItem.getParent();
                }
                
                int primaryIdx = hierarchyRoot.getChildren().indexOf(goItem);
                GameObject primary = (primaryIdx >= 0 && primaryIdx < ents.size()) ? ents.get(primaryIdx) : null;
                setSelected(primary);
                
                // Montar a lista de secundarios (os demais).
                secondarySelection.clear();
                for (TreeItem<String> ti : selItems) {
                    if (ti == null || ti == hierarchyRoot || ti == focusedItem) continue;
                    TreeItem<String> pItem = ti;
                    if (ti.getParent() != null && ti.getParent() != hierarchyRoot) {
                        pItem = ti.getParent();
                    }
                    int idx = hierarchyRoot.getChildren().indexOf(pItem);
                    if (idx >= 0 && idx < ents.size()) {
                        GameObject go = ents.get(idx);
                        if (go != primary && !secondarySelection.contains(go)) {
                            secondarySelection.add(go);
                        }
                    }
                }
                syncHighlights();
            } finally {
                suppressSelectionEvents = false;
            }
        });
        tree.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                TreeItem<String> sel = tree.getSelectionModel().getSelectedItem();
                ContextMenu menu;
                if (sel != null && sel != hierarchyRoot) {
                    menu = buildHierarchyContextMenu();
                } else {
                    menu = new ContextMenu();
                    MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
                    criarObjeto.setOnAction(e -> createEntity("GameObject"));
                    MenuItem criarCamera = new MenuItem("Criar Câmera");
                    criarCamera.setOnAction(e -> createEntity("Camera"));
                    menu.getItems().addAll(criarObjeto, criarCamera);
                }
                menu.show(tree, ev.getScreenX(), ev.getScreenY());
            }
        });
        // Atalhos so quando a arvore tem foco (evita conflito com os campos do Inspector).
        tree.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.DELETE) { deleteSelected(); ev.consume(); }
            else if (ev.getCode() == KeyCode.F2) { renameSelected(); ev.consume(); }
            else if (ev.getCode() == KeyCode.D && ev.isControlDown()) { duplicateSelected(); ev.consume(); }
        });
        return tree;
    }

    // ---------------- Mecanicas de edicao da cena ----------------

    private void createEntity(String type) {
        if (!requireProject()) return;
        try {
            GameObject obj = com.ignis.core.EntityFactory.create(type);
            obj.setName(uniqueNameExcept(type, null));
            obj.setGame(game);
            obj.setX(-25);
            obj.setY(-25);
            obj.setWidth(50);
            obj.setHeight(50);
            addEntityTracked(obj);
            refreshHierarchy();
            selectEntity(obj);
            markProjectDirty();
            setStatus("Objeto criado: " + obj.getName());
            undoManager.push("Criar " + obj.getName(),
                    () -> { removeEntityTracked(obj); refreshHierarchy(); selectEntity(null); markProjectDirty(); },
                    () -> { addEntityTracked(obj); refreshHierarchy(); selectEntity(obj); markProjectDirty(); });
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao criar objeto:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void duplicateSelected() {
        java.util.List<GameObject> targets = allSelected();
        if (targets.isEmpty()) { setStatus("Nada selecionado para duplicar."); return; }
        try {
            java.util.List<GameObject> copies = new java.util.ArrayList<>();
            for (GameObject original : targets) {
                GameObject copy = com.ignis.core.EntityFactory.create(original.getType());
                copy.setGame(game);
                copy.setX(original.getX());
                copy.setY(original.getY());
                copy.setWidth(original.getWidth());
                copy.setHeight(original.getHeight());
                copy.setSpritePath(original.getSpritePath());
                try { copy.loadProperties(original.saveProperties()); } catch (Exception ignore) { /* props opcionais */ }
                copy.setName(uniqueNameExcept(original.getName() + " (Copy)", null));
                copies.add(copy);
            }
            for (GameObject copy : copies) {
                addEntityTracked(copy);
            }
            refreshHierarchy();
            
            // Seleciona as copias: primeira como primaria, as outras como secundarias
            GameObject firstCopy = copies.get(0);
            selectEntity(firstCopy);
            for (int i = 1; i < copies.size(); i++) {
                secondarySelection.add(copies.get(i));
            }
            syncHighlights();
            
            markProjectDirty();
            if (copies.size() == 1) {
                setStatus("Duplicado: " + firstCopy.getName());
            } else {
                setStatus("Duplicados " + copies.size() + " objetos.");
            }
            
            undoManager.push("Duplicar em bloco",
                () -> {
                    for (GameObject copy : copies) {
                        removeEntityTracked(copy);
                    }
                    setSelected(null);
                    clearSecondarySelection();
                    refreshHierarchy();
                    markProjectDirty();
                },
                () -> {
                    for (GameObject copy : copies) {
                        addEntityTracked(copy);
                    }
                    refreshHierarchy();
                    selectEntity(firstCopy);
                    for (int i = 1; i < copies.size(); i++) {
                        secondarySelection.add(copies.get(i));
                    }
                    syncHighlights();
                    markProjectDirty();
                }
            );
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao duplicar:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void deleteSelected() {
        java.util.List<GameObject> targets = allSelected();
        if (targets.isEmpty()) { setStatus("Nada selecionado para deletar."); return; }
        
        String confirmMsg = (targets.size() == 1) ? 
                "Deletar '" + targets.get(0).getName() + "'?" : 
                "Deletar " + targets.size() + " objetos selecionados?";
                
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, confirmMsg, ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        
        class DeletedEntry {
            final GameObject obj;
            final int index;
            DeletedEntry(GameObject obj, int index) { this.obj = obj; this.index = index; }
        }
        java.util.List<DeletedEntry> deleted = new java.util.ArrayList<>();
        for (GameObject obj : targets) {
            int idx = game.getEntities().indexOf(obj);
            deleted.add(new DeletedEntry(obj, idx));
        }
        
        for (GameObject obj : targets) {
            removeEntityTracked(obj);
        }
        setSelected(null);
        clearSecondarySelection();
        refreshHierarchy();
        markProjectDirty();
        
        if (targets.size() == 1) {
            setStatus("Deletado: " + targets.get(0).getName());
        } else {
            setStatus("Deletados " + targets.size() + " objetos.");
        }
        
        undoManager.push("Deletar em bloco",
            () -> {
                java.util.List<DeletedEntry> sorted = new java.util.ArrayList<>(deleted);
                sorted.sort(java.util.Comparator.comparingInt(d -> d.index));
                for (DeletedEntry d : sorted) {
                    addEntityTracked(d.obj);
                    if (d.index >= 0 && d.index < game.getEntities().size()) {
                        game.moveEntityToIndex(d.obj, d.index);
                    }
                }
                refreshHierarchy();
                if (!sorted.isEmpty()) {
                    GameObject first = sorted.get(0).obj;
                    selectEntity(first);
                    for (int i = 1; i < sorted.size(); i++) {
                        secondarySelection.add(sorted.get(i).obj);
                    }
                    syncHighlights();
                }
                markProjectDirty();
            },
            () -> {
                for (GameObject obj : targets) {
                    removeEntityTracked(obj);
                }
                setSelected(null);
                clearSecondarySelection();
                refreshHierarchy();
                markProjectDirty();
            }
        );
    }

    private void renameSelected() {
        if (selected == null) { setStatus("Nada selecionado para renomear."); return; }
        GameObject go = selected;
        TextInputDialog dlg = new TextInputDialog(go.getName());
        dlg.setTitle("Renomear objeto");
        dlg.setHeaderText(null);
        dlg.setContentText("Novo nome:");
        java.util.Optional<String> opt = dlg.showAndWait();
        if (opt.isEmpty() || opt.get().trim().isEmpty()) return;
        String oldName = go.getName();
        String newName = uniqueNameExcept(opt.get().trim(), go);
        if (newName.equals(oldName)) return;
        go.setName(newName);
        refreshHierarchy();
        selectEntity(go);
        markProjectDirty();
        setStatus("Renomeado para: " + go.getName());
        undoManager.push("Renomear",
                () -> { go.setName(oldName); refreshHierarchy(); selectEntity(go); markProjectDirty(); },
                () -> { go.setName(newName); refreshHierarchy(); selectEntity(go); markProjectDirty(); });
    }

    private void moveSelected(int delta) {
        if (selected == null) return;
        GameObject toReselect = selected; // refreshHierarchy() zera 'selected' via listener
        int oldIdx = game.getEntities().indexOf(toReselect);
        if (delta < 0) game.moveEntityUp(toReselect);
        else game.moveEntityDown(toReselect);
        int newIdx = game.getEntities().indexOf(toReselect);
        refreshHierarchy();
        selectEntity(toReselect);
        if (newIdx != oldIdx) { markProjectDirty(); pushReorder(toReselect, oldIdx, newIdx); }
    }

    private void moveSelectedTo(int index) {
        if (selected == null) return;
        GameObject toReselect = selected; // refreshHierarchy() zera 'selected' via listener
        int oldIdx = game.getEntities().indexOf(toReselect);
        int target = (index == Integer.MAX_VALUE) ? game.getEntities().size() - 1 : index;
        if (target < 0) target = 0;
        game.moveEntityToIndex(toReselect, target);
        int newIdx = game.getEntities().indexOf(toReselect);
        refreshHierarchy();
        selectEntity(toReselect);
        if (newIdx != oldIdx) { markProjectDirty(); pushReorder(toReselect, oldIdx, newIdx); }
    }

    // Aplica um estado de transformacao a um objeto (usado por desfazer/refazer de gizmo).
    private void applyTransform(GameObject o, double x, double y, double rot, int w, int h) {
        o.setX(x); o.setY(y); o.setRotation(rot); o.setWidth(w); o.setHeight(h);
        refreshHierarchy();
        selectEntity(o);
        markProjectDirty();
    }

    private void pushReorder(GameObject o, int oldIdx, int newIdx) {
        undoManager.push("Reordenar " + o.getName(),
                () -> { game.moveEntityToIndex(o, oldIdx); refreshHierarchy(); selectEntity(o); markProjectDirty(); },
                () -> { game.moveEntityToIndex(o, newIdx); refreshHierarchy(); selectEntity(o); markProjectDirty(); });
    }

    // ---------------- Prefabs ----------------
    // Templates reutilizaveis de objeto (com transform/sprite/scripts/variaveis),
    // gravados em <projeto>/prefabs/*.prefab.json pelo core PrefabManager. Aqui so
    // fiamos a UI: salvar selecao como prefab e instanciar na cena (com desfazer).

    // PrefabManager lazy; recriado quando muda a pasta do projeto.
    private com.ignis.core.PrefabManager getPrefabManager() {
        if (projectFolder == null) return null;
        if (prefabManager == null || !projectFolder.equals(prefabManagerFolder)) {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) { sm = new com.ignis.core.ScriptManager(projectFolder); game.setScriptManager(sm); }
            prefabManager = new com.ignis.core.PrefabManager(projectFolder, game, sm);
            prefabManagerFolder = projectFolder;
        }
        return prefabManager;
    }

    // Salva o objeto selecionado como prefab reutilizavel.
    private void saveSelectedAsPrefab() {
        if (selected == null) { setStatus("Nada selecionado para salvar como prefab."); return; }
        if (!requireProject()) return;
        com.ignis.core.PrefabManager pm = getPrefabManager();
        if (pm == null) return;
        TextInputDialog dlg = new TextInputDialog(selected.getName());
        dlg.setTitle("Salvar como Prefab");
        dlg.setHeaderText(null);
        dlg.setContentText("Nome do prefab:");
        java.util.Optional<String> opt = dlg.showAndWait();
        if (opt.isEmpty() || opt.get().trim().isEmpty()) return;
        String name = opt.get().trim();
        if (pm.prefabExists(name)) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Prefab '" + name + "' ja existe. Sobrescrever?", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        }
        if (pm.savePrefab(selected, name)) {
            setStatus("Prefab salvo: " + name);
            refreshAssetBrowser();
        } else {
            new Alert(Alert.AlertType.ERROR, "Falha ao salvar prefab '" + name + "'.").showAndWait();
        }
    }

    // Nome do prefab a partir do arquivo <nome>.prefab.json.
    private String prefabNameOf(File f) {
        return f == null ? null : f.getName().replaceFirst("(?i)\\.prefab\\.json$", "");
    }

    // Escolhe um prefab salvo e o instancia na cena atual.
    private void instantiatePrefabDialog() {
        if (!requireProject()) return;
        com.ignis.core.PrefabManager pm = getPrefabManager();
        if (pm == null) return;
        java.util.List<String> prefabs = pm.listPrefabs();
        if (prefabs.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Nenhum prefab salvo ainda.\nSelecione um objeto e use 'Salvar como Prefab'.").showAndWait();
            return;
        }
        java.util.Collections.sort(prefabs);
        ChoiceDialog<String> dlg = new ChoiceDialog<>(prefabs.get(0), prefabs);
        dlg.setTitle("Instanciar Prefab");
        dlg.setHeaderText(null);
        dlg.setContentText("Prefab:");
        java.util.Optional<String> opt = dlg.showAndWait();
        opt.ifPresent(this::instantiatePrefabByName);
    }

    // Instancia o prefab nomeado, adiciona a cena e registra desfazer/refazer.
    private void instantiatePrefabByName(String name) {
        if (name == null || !requireProject()) return;
        com.ignis.core.PrefabManager pm = getPrefabManager();
        if (pm == null) return;
        GameObject obj;
        try {
            obj = pm.instantiatePrefab(name);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao instanciar prefab:\n" + ex.getMessage()).showAndWait();
            return;
        }
        if (obj == null) {
            new Alert(Alert.AlertType.ERROR, "Prefab nao encontrado ou invalido: " + name).showAndWait();
            return;
        }
        addEntityTracked(obj);
        refreshHierarchy();
        selectEntity(obj);
        markProjectDirty();
        setStatus("Prefab instanciado: " + obj.getName());
        undoManager.push("Instanciar " + obj.getName(),
                () -> { removeEntityTracked(obj); refreshHierarchy(); selectEntity(null); markProjectDirty(); },
                () -> { addEntityTracked(obj); refreshHierarchy(); selectEntity(obj); markProjectDirty(); });
    }

    // Nome unico entre as entidades (ignora 'except', util ao renomear o proprio objeto).
    private String uniqueNameExcept(String base, GameObject except) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (GameObject g : game.getEntities()) if (g != except) existing.add(g.getName());
        if (!existing.contains(base)) return base;
        int c = 1;
        String n;
        do { n = base + " (" + c++ + ")"; } while (existing.contains(n));
        return n;
    }

    // Fonte UNICA de selecao: atualiza a Hierarchy (efeito visual) E o Inspector (setSelected),
    // venha o clique do viewport, da arvore, ou de criar/duplicar/colar. Antes o Inspector nao
    // atualizava ao clicar no viewport porque setSelected so era chamado pelo listener da arvore,
    // que aborta quando suppressSelectionEvents ja esta true.
    private void selectEntity(GameObject go) {
        if (suppressSelectionEvents) return;
        suppressSelectionEvents = true;
        try {
            if (go == null) {
                if (hierarchy != null) hierarchy.getSelectionModel().clearSelection();
            } else {
                int idx = game.getEntities().indexOf(go);
                if (hierarchy != null && idx >= 0 && idx < hierarchyRoot.getChildren().size()) {
                    // Em MULTIPLE mode, select() ACUMULA; para selecao programatica (criar,
                    // duplicar, undo, etc.) precisamos clearSelection antes para que o tree
                    // reflita apenas o primario. A multi-selecao nativa (Ctrl/Shift) e tratada
                    // pelo listener do getSelectedItems().
                    hierarchy.getSelectionModel().clearSelection();
                    hierarchy.getSelectionModel().select(hierarchyRoot.getChildren().get(idx));
                }
            }
            setSelected(go); // SEMPRE — atualiza o Inspector (fonte unica de verdade)
        } finally {
            suppressSelectionEvents = false;
        }
    }

    // ---------------- Multi-selecao ----------------

    // Empurra a lista de selecao secundaria para o Game (contornos tracejados no viewport).
    private void syncHighlights() {
        game.setEditorHighlights(secondarySelection.isEmpty() ? null : secondarySelection);
    }

    // Limpa a selecao secundaria e atualiza o viewport.
    private void clearSecondarySelection() {
        if (!secondarySelection.isEmpty()) {
            secondarySelection.clear();
            syncHighlights();
        }
    }

    // Toggle de um objeto na selecao secundaria (Ctrl+Click). Se o objeto ja
    // esta na lista, remove; senao, adiciona. O objeto primario ('selected') nunca
    // entra na lista secundaria — ele mantem o gizmo e o Inspector.
    private void toggleSecondarySelection(GameObject go) {
        if (go == null || go == selected) return;
        if (secondarySelection.contains(go)) {
            secondarySelection.remove(go);
        } else {
            secondarySelection.add(go);
        }
        syncHighlights();
    }

    // Retorna todos os selecionados (primario + secundarios), na ordem da cena.
    private java.util.List<GameObject> allSelected() {
        java.util.List<GameObject> all = new java.util.ArrayList<>();
        java.util.List<GameObject> entities = game.getEntities();
        for (GameObject e : entities) {
            if (e == selected || secondarySelection.contains(e)) all.add(e);
        }
        return all;
    }

    // Quantidade total de objetos selecionados (primario + secundarios).
    private int selectionCount() {
        return (selected != null ? 1 : 0) + secondarySelection.size();
    }

    // ---------------- Auto Save do projeto ----------------

    private void setupProjectAutoSave() {
        // Marca o projeto como "sujo" ao fim de um arraste (mover/rotacionar/escalar) e
        // registra um comando de Desfazer com o estado antes/depois da transformacao.
        game.setTransformListener(new com.ignis.core.Game.TransformListener() {
            @Override public void onTransformStart(GameObject o, double x, double y, double rotation, int w, int h) {
                transformObj = o;
                txStartX = x; txStartY = y; txStartRot = rotation; txStartW = w; txStartH = h;
                secondaryStartStates.clear();
                for (GameObject sec : secondarySelection) {
                    if (sec != o) {
                        secondaryStartStates.add(new TransformState(sec, sec.getX(), sec.getY(), sec.getRotation(), sec.getWidth(), sec.getHeight()));
                    }
                }
            }
            @Override public void onTransformEnd(GameObject o) {
                markProjectDirty();
                if (o == null || o != transformObj) { transformObj = null; secondaryStartStates.clear(); return; }
                double sx = txStartX, sy = txStartY, srot = txStartRot; int sw = txStartW, sh = txStartH;
                double ex = o.getX(), ey = o.getY(), erot = o.getRotation(); int ew = o.getWidth(), eh = o.getHeight();
                transformObj = null;
                // Ignora se nada mudou (ex: clique sem arraste).
                if (sx == ex && sy == ey && srot == erot && sw == ew && sh == eh) {
                    secondaryStartStates.clear();
                    return;
                }
                
                // Calcula deltas do primario
                double dx = ex - sx;
                double dy = ey - sy;
                double drot = erot - srot;
                double dw = ew - sw;
                double dh = eh - sh;
                
                // Grava os estados de antes e depois de todos (primario + secundarios)
                java.util.List<TransformState> beforeStates = new java.util.ArrayList<>();
                java.util.List<TransformState> afterStates = new java.util.ArrayList<>();
                
                // Adiciona primario
                beforeStates.add(new TransformState(o, sx, sy, srot, sw, sh));
                afterStates.add(new TransformState(o, ex, ey, erot, ew, eh));
                
                // Aplica e grava os secundarios
                for (TransformState start : secondaryStartStates) {
                    GameObject sec = start.obj;
                    double secEx = start.x + dx;
                    double secEy = start.y + dy;
                    double secErot = start.rotation + drot;
                    int secEw = (int)(start.w + dw);
                    int secEh = (int)(start.h + dh);
                    
                    sec.setX(secEx);
                    sec.setY(secEy);
                    sec.setRotation(secErot);
                    sec.setWidth(secEw);
                    sec.setHeight(secEh);
                    
                    beforeStates.add(start);
                    afterStates.add(new TransformState(sec, secEx, secEy, secErot, secEw, secEh));
                }
                secondaryStartStates.clear();
                
                refreshHierarchy();
                // Forca atualizacao do Inspector para o primario
                setSelected(o);
                game.repaint();
                
                undoManager.push("Transformar em bloco",
                        () -> applyMultipleTransforms(beforeStates),
                        () -> applyMultipleTransforms(afterStates));
            }
        });
        projectAutoSaveTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(EditorPrefs.getAutoSaveIntervalSeconds()), e -> {
            if (EditorPrefs.isAutoSave() && currentProject != null && currentIgnisFile != null
                    && projectDirty && !playing) {
                saveProjectSilently();
            }
        }));
        projectAutoSaveTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        projectAutoSaveTimer.play();
    }

    private void applyMultipleTransforms(java.util.List<TransformState> states) {
        for (TransformState s : states) {
            s.obj.setX(s.x);
            s.obj.setY(s.y);
            s.obj.setRotation(s.rotation);
            s.obj.setWidth(s.w);
            s.obj.setHeight(s.h);
        }
        refreshHierarchy();
        if (selected != null) {
            setSelected(selected);
        }
        game.repaint();
        markProjectDirty();
    }

    // Salvamento usado pelo Auto Save: nunca abre Alert modal (uma falha persistente de
    // gravacao geraria um dialogo a cada intervalo). Erros vao so para a barra de status.
    private void saveProjectSilently() {
        if (currentProject == null || currentIgnisFile == null) return;
        try {
            syncEntitiesToScene();
            IgnisProjectIO.save(currentProject, currentIgnisFile);
            if (currentProject.getProjectFile() != null) currentIgnisFile = currentProject.getProjectFile();
            projectDirty = false;
            setStatus("Auto-saved: " + currentProject.getProjectName());
        } catch (Exception ex) {
            setStatus("Auto-save falhou: " + ex.getMessage());
        }
    }

    // Marca alteracoes nao salvas no projeto (consumido pelo Auto Save).
    private void markProjectDirty() {
        projectDirty = true;
    }

    // Mostra/oculta o painel de Console (dock inferior) e persiste a preferencia.
    private void setConsoleVisible(boolean visible) {
        if (centerSplit == null || console == null) return;
        boolean present = centerSplit.getItems().contains(console);
        if (visible && !present) {
            centerSplit.getItems().add(console);
            centerSplit.setDividerPositions(0.76);
        } else if (!visible && present) {
            centerSplit.getItems().remove(console);
        }
        if (consoleMenuItem != null) consoleMenuItem.setSelected(visible);
        EditorPrefs.setConsoleVisible(visible);
    }

    // ---------------- Asset Browser (arvore de arquivos do projeto) ----------------

    private javafx.scene.Node buildAssetBrowser() {
        VBox box = new VBox(4);
        box.getStyleClass().add("ignis-panel");
        Label title = new Label("Assets");
        title.getStyleClass().add("panel-title");

        assetTree = new TreeView<>();
        assetTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                File file = newVal.getValue();
                if (file != null && file.isFile() && file.getName().endsWith(".java")) {
                    inspectScriptFile(file);
                }
            }
        });
        assetTree.setShowRoot(true);
        assetTree.setCellFactory(tv -> {
            TreeCell<File> cell = new TreeCell<>() {
                @Override protected void updateItem(File item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            };
            // Selecionar o item sob o cursor no clique DIREITO (SECONDARY), para o menu de
            // contexto operar no item certo. Sem isso o TreeView so seleciona no clique
            // esquerdo e o menu agia sobre a selecao anterior (ou nenhuma).
            cell.setOnMousePressed(e -> {
                if (!cell.isEmpty() && e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    tv.getSelectionModel().select(cell.getTreeItem());
                }
            });
            return cell;
        });
        assetTree.setOnMouseClicked(ev -> {
            TreeItem<File> sel = assetTree.getSelectionModel().getSelectedItem();
            File file = (sel != null) ? sel.getValue() : null;
            if (ev.getClickCount() == 2 && ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                if (file != null && file.isFile()) {
                    if (file.getName().endsWith(".prefab.json")) {
                        instantiatePrefabByName(prefabNameOf(file));
                    } else if (file.getName().endsWith(".java")) {
                        openScriptInIgnisEditor(file);
                    } else {
                        openAssetFile(file);
                    }
                }
            } else if (ev.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                ContextMenu menu = buildAssetsContextMenu(file);
                menu.show(assetTree, ev.getScreenX(), ev.getScreenY());
            }
        });
        VBox.setVgrow(assetTree, Priority.ALWAYS);

        box.getChildren().addAll(title, assetTree);
        refreshAssetBrowser();
        return box;
    }

    // Reconstroi a arvore a partir da pasta do projeto (ou vazia se nenhum projeto).
    private void refreshAssetBrowser() {
        if (assetTree == null) return;
        if (projectFolder != null && projectFolder.isDirectory()) {
            TreeItem<File> root = buildFileTree(projectFolder);
            root.setExpanded(true);
            assetTree.setRoot(root);
        } else {
            assetTree.setRoot(null);
        }
    }

    private TreeItem<File> buildFileTree(File f) {
        return buildFileTree(f, 0, new java.util.HashSet<>());
    }

    // Recursao com guarda: pula symlinks, limita profundidade e detecta ciclos (canonical
    // path ja visitado) — evita StackOverflowError (que e Error, nao Exception) em FS POSIX.
    private TreeItem<File> buildFileTree(File f, int depth, java.util.Set<String> visited) {
        TreeItem<File> item = new TreeItem<>(f);
        boolean symlink;
        try { symlink = java.nio.file.Files.isSymbolicLink(f.toPath()); } catch (Exception e) { symlink = false; }
        if (f.isDirectory() && depth < 32 && !symlink) {
            String canonical;
            try { canonical = f.getCanonicalPath(); } catch (Exception e) { canonical = f.getAbsolutePath(); }
            if (visited.add(canonical)) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    java.util.Arrays.sort(kids, java.util.Comparator
                            .comparing((File k) -> !k.isDirectory())
                            .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                    for (File k : kids) item.getChildren().add(buildFileTree(k, depth + 1, visited));
                }
            }
        }
        return item;
    }

    // Abre o arquivo com o aplicativo padrao do sistema (best-effort).
    private void openAssetFile(File f) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(f);
                setStatus("Abrindo: " + f.getName());
            } else {
                setStatus("Abertura de arquivos nao suportada neste sistema.");
            }
        } catch (Exception ex) {
            setStatus("Nao foi possivel abrir: " + f.getName());
        }
    }

    private void openScriptInIgnisEditor(File file) {
        if (!requireProject()) return;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) {
                sm = new com.ignis.core.ScriptManager(projectFolder);
                game.setScriptManager(sm);
            }
            String scriptName = file.getName();
            if (scriptName.endsWith(".java")) {
                scriptName = scriptName.substring(0, scriptName.length() - 5);
            }
            FxCodeEditor codeEditor = new FxCodeEditor(this, sm, scriptName);
            codeEditor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Codigo:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void refreshHierarchy() {
        hierarchyRoot.setValue("Cena  (z menor = atras)");
        hierarchyRoot.getChildren().clear();
        for (GameObject go : game.getEntities()) {
            TreeItem<String> goItem = new TreeItem<>(go.getName() + "   z:" + go.getZIndex());
            
            // Sub-item de Transform
            goItem.getChildren().add(new TreeItem<>("Transform"));
            
            // Sub-item de SpriteComponent se anexado
            if (go.getComponent(SpriteComponent.class) != null) {
                goItem.getChildren().add(new TreeItem<>("SpriteComponent"));
            }
            
            // Outros componentes / scripts
            for (com.ignis.core.IgnisScript script : go.getScripts()) {
                if (script instanceof SpriteComponent) continue;
                goItem.getChildren().add(new TreeItem<>(script.getScriptName()));
            }
            
            goItem.setExpanded(true);
            hierarchyRoot.getChildren().add(goItem);
        }
        hierarchyRoot.setExpanded(true);
    }

    // ---------------- Inspector ----------------

    private javafx.scene.Node buildInspector() {
        VBox box = new VBox(8);
        box.getStyleClass().add("ignis-panel");
        box.setPadding(new Insets(12));

        Label title = new Label("Inspector");
        title.getStyleClass().add("panel-title");

        nameField = new TextField();
        xField = new TextField();
        yField = new TextField();
        wField = new TextField();
        hField = new TextField();
        rotField = new TextField();
        visibleCheck = new CheckBox("Visivel");

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setMaxWidth(Double.MAX_VALUE);
        // Coluna 0 (rotulos) com largura fixa; coluna 1 (campos) cresce para ocupar
        // todo o espaco disponivel quando o painel do Inspector e alargado.
        javafx.scene.layout.ColumnConstraints labelCol = new javafx.scene.layout.ColumnConstraints();
        labelCol.setMinWidth(64);
        labelCol.setHalignment(javafx.geometry.HPos.LEFT);
        javafx.scene.layout.ColumnConstraints fieldCol = new javafx.scene.layout.ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);
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

        // Desfazer/Refazer da edicao por digitacao: captura o valor no foco e registra
        // um comando no commit (Enter ou foco perdido) quando o valor mudou. Os listeners
        // acima ja aplicaram a mudanca ao vivo; aqui so registramos o antes->depois.
        wireUndoableField(nameField, GameObject::getName, (g, v) -> g.setName((String) v), "Nome");
        wireUndoableField(xField,    GameObject::getX,      (g, v) -> g.setX((Double) v),    "X");
        wireUndoableField(yField,    GameObject::getY,      (g, v) -> g.setY((Double) v),    "Y");
        wireUndoableField(wField,    GameObject::getWidth,  (g, v) -> g.setWidth((Integer) v),  "Largura");
        wireUndoableField(hField,    GameObject::getHeight, (g, v) -> g.setHeight((Integer) v), "Altura");
        wireUndoableField(rotField,  GameObject::getRotation, (g, v) -> g.setRotation((Double) v), "Rotacao");

        // Checkbox de visibilidade: undo imediato (mudanca discreta).
        visibleCheck.selectedProperty().addListener((o, a, b) -> {
            if (suppressInspectorEvents || selected == null) return;
            GameObject o2 = selected;
            boolean oldV = a, newV = b;
            o2.setVisible(newV);
            markProjectDirty();
            undoManager.push("Editar Visivel",
                    () -> applyInspectorUndo(o2, g -> g.setVisible(oldV)),
                    () -> applyInspectorUndo(o2, g -> g.setVisible(newV)));
        });

        inspectorExtras = new VBox(8);
        box.getChildren().addAll(title, grid, inspectorExtras);
        setInspectorEnabled(false);

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("ignis-panel");
        return scroll;
    }

    private void addRow(GridPane grid, int row, String label, TextField field) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }

    private void applyIfEditing(Runnable action) {
        if (suppressInspectorEvents || selected == null) return;
        try { action.run(); markProjectDirty(); } catch (Exception ignore) { /* entrada invalida */ }
    }

    // Liga undo/redo por digitacao a um campo: snapshot no foco, commit ao perder o
    // foco ou no Enter. O valor ja foi aplicado ao vivo pelo listener de texto; aqui
    // so registramos o comando antes->depois se o valor realmente mudou.
    private void wireUndoableField(TextField field,
            java.util.function.Function<GameObject, Object> getter,
            java.util.function.BiConsumer<GameObject, Object> setter,
            String label) {
        field.focusedProperty().addListener((o, was, now) -> {
            if (now) beginInspectorEdit(getter);
            else commitInspectorEdit(getter, setter, label);
        });
        field.setOnAction(e -> { // Enter
            commitInspectorEdit(getter, setter, label);
            beginInspectorEdit(getter); // nova linha de base para edicoes seguintes
        });
    }

    private void beginInspectorEdit(java.util.function.Function<GameObject, Object> getter) {
        if (selected == null) { inspectorEditObj = null; return; }
        inspectorEditObj = selected;
        inspectorEditOld = getter.apply(selected);
    }

    private void commitInspectorEdit(java.util.function.Function<GameObject, Object> getter,
            java.util.function.BiConsumer<GameObject, Object> setter, String label) {
        GameObject o = inspectorEditObj;
        inspectorEditObj = null;
        if (o == null) return;
        Object oldV = inspectorEditOld;
        Object newV = getter.apply(o);
        if (java.util.Objects.equals(oldV, newV)) return;
        undoManager.push("Editar " + label,
                () -> applyInspectorUndo(o, g -> setter.accept(g, oldV)),
                () -> applyInspectorUndo(o, g -> setter.accept(g, newV)));
    }

    // Aplica uma mutacao de propriedade (undo/redo do Inspector) e reflete na UI.
    private void applyInspectorUndo(GameObject o, java.util.function.Consumer<GameObject> mutation) {
        mutation.accept(o);
        refreshHierarchy();
        selectEntity(o);
        markProjectDirty();
    }

    private void setSelected(GameObject go) {
        // So cancela um drag em andamento se a selecao NAO veio do proprio engine. Quando
        // game.getSelectedObject()==go, o handleMousePress ja selecionou e ARMOU o drag deste
        // objeto no mesmo gesto; cancelar aqui (via selectionListener->runLater) abortaria um
        // clicar-e-arrastar de objeto nao-selecionado.
        if (this.selected != go && game.getSelectedObject() != go) {
            game.cancelDrag();
        }
        this.selected = go;
        suppressInspectorEvents = true;
        if (go == null) {
            setInspectorEnabled(false);
            nameField.setText(""); xField.setText(""); yField.setText("");
            wField.setText(""); hField.setText(""); rotField.setText("");
            visibleCheck.setSelected(false);
            clearSecondarySelection();
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

        if (game.getSelectedObject() != go) {
            game.setSelectedObject(go);
        }

        rebuildInspectorExtras(go);

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

    // ---------------- Inspector: secoes por tipo (Cor/Sprite/Collider/Camera/Scripts) ----------------
    // Reconstruidas a cada selecao para refletir o tipo do objeto. Os handlers sao
    // ligados APOS definir o valor inicial, para que o setup nao dispare escritas.

    private void rebuildInspectorExtras(GameObject go) {
        if (inspectorExtras == null) return;
        inspectorExtras.getChildren().clear();
        if (go == null) return;
        
        SpriteComponent spriteComp = go.getComponent(SpriteComponent.class);
        if (spriteComp != null) {
            inspectorExtras.getChildren().add(buildSpriteComponentSection(go, spriteComp));
        }
        
        inspectorExtras.getChildren().add(buildColliderSection(go));
        if (go instanceof com.ignis.core.Camera) {
            inspectorExtras.getChildren().add(buildCameraSection((com.ignis.core.Camera) go));
        }
        inspectorExtras.getChildren().add(buildScriptsSection(go));
    }

    private void inspectScriptFile(File file) {
        // Deseleciona GameObject ativo
        setSelected(null);
        
        if (inspectorExtras == null) return;
        inspectorExtras.getChildren().clear();

        String scriptClassName = file.getName();
        if (scriptClassName.endsWith(".java")) {
            scriptClassName = scriptClassName.substring(0, scriptClassName.length() - 5);
        }

        VBox panel = new VBox(6);
        panel.setPadding(new Insets(8));
        panel.setStyle("-fx-border-color: #555; -fx-border-width: 0 0 0 2; -fx-background-color: rgba(255, 255, 255, 0.02);");

        Label title = new Label("Script: " + scriptClassName);
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: -ignis-primary; -fx-font-size: 13px;");
        panel.getChildren().add(title);

        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) {
                sm = new com.ignis.core.ScriptManager(projectFolder);
                game.setScriptManager(sm);
            }
            
            Class<? extends com.ignis.core.IgnisScript> scriptClass = sm.loadScriptClass(scriptClassName);
            if (scriptClass != null) {
                com.ignis.core.IgnisScript tempInstance = scriptClass.getDeclaredConstructor().newInstance();
                java.util.List<java.lang.reflect.Field> fields = com.ignis.core.ScriptSerializationHelper.getSerializedFields(scriptClass);
                
                if (fields.isEmpty()) {
                    Label noVars = new Label("Nenhuma variável serializada (@Serialize) encontrada.");
                    noVars.setStyle("-fx-text-fill: #888; -fx-font-style: italic; -fx-wrap-text: true;");
                    panel.getChildren().add(noVars);
                } else {
                    GridPane grid = new GridPane();
                    grid.setHgap(8);
                    grid.setVgap(4);
                    int rowIdx = 0;
                    for (java.lang.reflect.Field field : fields) {
                        Class<?> type = field.getType();
                        if (com.ignis.core.ScriptSerializationHelper.isSupportedType(type)) {
                            field.setAccessible(true);
                            Object val = field.get(tempInstance);
                            
                            Label nameLbl = new Label(field.getName());
                            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #ccc;");
                            
                            Label typeLbl = new Label("(" + type.getSimpleName() + ")");
                            typeLbl.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
                            
                            Label valLbl = new Label(val != null ? val.toString() : "null");
                            valLbl.setStyle("-fx-text-fill: #aaa;");
                            
                            grid.add(nameLbl, 0, rowIdx);
                            grid.add(typeLbl, 1, rowIdx);
                            grid.add(valLbl, 2, rowIdx);
                            rowIdx++;
                        }
                    }
                    panel.getChildren().add(grid);
                }
            } else {
                Label errLabel = new Label("Não foi possível carregar o script.\nAbra o script e compile-o para corrigir.");
                errLabel.setStyle("-fx-text-fill: #c86464; -fx-wrap-text: true;");
                panel.getChildren().add(errLabel);
            }
        } catch (Exception ex) {
            Label errLabel = new Label("Erro ao ler variáveis: " + ex.getMessage());
            errLabel.setStyle("-fx-text-fill: #c86464; -fx-wrap-text: true;");
            panel.getChildren().add(errLabel);
        }

        inspectorExtras.getChildren().add(panel);
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-title");
        VBox.setMargin(l, new Insets(8, 0, 2, 0));
        return l;
    }

    // Cor (so para objetos que expoem getColor/setColor via reflexao) + Sprite.
    private javafx.scene.Node buildSpriteComponentSection(GameObject go, SpriteComponent spriteComp) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("SpriteComponent"));

        // 1. Textura / Sprite
        Label spriteVal = new Label(spriteLabel(spriteComp.getTexture() != null ? spriteComp.getTexture().getPath() : null));
        spriteVal.getStyleClass().add("field-label");
        spriteVal.setWrapText(true);
        Button pick = new Button("Escolher…");
        pick.setOnAction(e -> {
            File f = chooseSpriteFile();
            if (f != null) {
                String path = importSpriteToProject(f);
                spriteComp.setTexture(new Texture2D(path));
                spriteVal.setText(spriteLabel(path));
                markProjectDirty();
                // update hierarchy when texture changes
                refreshHierarchy();
            }
        });
        Button clear = new Button("Limpar");
        clear.setOnAction(e -> {
            spriteComp.setTexture(null);
            spriteVal.setText(spriteLabel(null));
            markProjectDirty();
            refreshHierarchy();
        });
        Label spriteLbl = new Label("Sprite");
        spriteLbl.getStyleClass().add("field-label");
        sec.getChildren().add(new VBox(4, spriteLbl, spriteVal, new HBox(6, pick, clear)));

        // 2. Tipo de Forma (shapeType)
        ComboBox<String> shapeBox = new ComboBox<>();
        shapeBox.getItems().addAll("None", "Square", "Circle", "Triangle", "Star", "Pentagon");
        shapeBox.setValue(spriteComp.getShapeType());
        shapeBox.setOnAction(e -> {
            spriteComp.setShapeType(shapeBox.getValue());
            markProjectDirty();
        });
        sec.getChildren().add(labeledInspectorRow("Forma", shapeBox));

        // 3. Cor / Tint
        ColorPicker tintPicker = new ColorPicker();
        if (spriteComp.getTint() != null) {
            tintPicker.setValue(awtToFx(spriteComp.getTint()));
        }
        tintPicker.setOnAction(e -> {
            spriteComp.setTint(fxToAwt(tintPicker.getValue()));
            markProjectDirty();
        });
        sec.getChildren().add(labeledInspectorRow("Cor / Tint", tintPicker));

        // 4. FlipX e FlipY
        CheckBox fxCheck = new CheckBox("Flip X");
        fxCheck.setSelected(spriteComp.isFlipX());
        fxCheck.setOnAction(e -> {
            spriteComp.setFlipX(fxCheck.isSelected());
            markProjectDirty();
        });

        CheckBox fyCheck = new CheckBox("Flip Y");
        fyCheck.setSelected(spriteComp.isFlipY());
        fyCheck.setOnAction(e -> {
            spriteComp.setFlipY(fyCheck.isSelected());
            markProjectDirty();
        });

        sec.getChildren().add(new HBox(12, fxCheck, fyCheck));

        return sec;
    }

    private void openAddComponentDialog(GameObject go) {
        if (!requireProject()) return;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) {
                sm = new com.ignis.core.ScriptManager(projectFolder);
                game.setScriptManager(sm);
            }
            
            java.util.List<String> available = new java.util.ArrayList<>();
            
            // 1. SpriteComponent se nao anexado
            if (go.getComponent(SpriteComponent.class) == null) {
                available.add("SpriteComponent");
            }
            
            // 2. Scripts disponiveis
            for (String scriptName : sm.listAvailableScripts()) {
                if (!go.getScriptNames().contains(scriptName)) {
                    available.add(scriptName);
                }
            }

            if (available.isEmpty()) {
                setStatus("Nenhum componente disponível para adicionar.");
                return;
            }

            FxAddComponentDialog dialog = new FxAddComponentDialog(primaryStage, available);
            String selected = dialog.showAndGetResult();
            
            if (selected != null) {
                if (selected.equals("SpriteComponent")) {
                    SpriteComponent sprite = new SpriteComponent();
                    go.addComponent(sprite);
                    setStatus("SpriteComponent adicionado.");
                } else {
                    go.getScriptNames().add(selected);
                    try {
                        com.ignis.core.IgnisScript inst = sm.createScriptInstance(selected, go, game);
                        if (inst != null) {
                            go.addComponent(inst);
                        }
                    } catch (Exception ignore) {}
                    setStatus("Script anexado: " + selected);
                }
                markProjectDirty();
                refreshHierarchy();
                rebuildInspectorExtras(go);
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao adicionar componente:\n" + ex.getMessage()).showAndWait();
        }
    }

    private javafx.scene.Node buildColliderSection(GameObject go) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Collider"));

        ComboBox<com.ignis.core.IgnisSampleCollisions.ColliderType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(com.ignis.core.IgnisSampleCollisions.ColliderType.values());
        typeBox.setValue(go.getColliderType());

        ComboBox<com.ignis.core.IgnisSampleCollisions.CollisionMode> modeBox = new ComboBox<>();
        modeBox.getItems().addAll(com.ignis.core.IgnisSampleCollisions.CollisionMode.values());
        modeBox.setValue(go.getCollisionMode());

        CheckBox enabled = new CheckBox("Habilitado");
        enabled.setSelected(go.hasCollider());

        boolean none = go.getColliderType() == com.ignis.core.IgnisSampleCollisions.ColliderType.NONE;
        modeBox.setDisable(none);
        enabled.setDisable(none);

        typeBox.setOnAction(e -> {
            go.setColliderType(typeBox.getValue());
            boolean isNone = typeBox.getValue() == com.ignis.core.IgnisSampleCollisions.ColliderType.NONE;
            modeBox.setDisable(isNone);
            enabled.setDisable(isNone);
            if (!isNone) {
                go.setCollisionMode(modeBox.getValue());
                go.setColliderEnabled(enabled.isSelected());
            }
            markProjectDirty();
        });
        modeBox.setOnAction(e -> { go.setCollisionMode(modeBox.getValue()); markProjectDirty(); });
        enabled.selectedProperty().addListener((o, a, b) -> { go.setColliderEnabled(b); markProjectDirty(); });

        sec.getChildren().addAll(labeledInspectorRow("Tipo", typeBox),
                labeledInspectorRow("Modo", modeBox), enabled);
        return sec;
    }

    private javafx.scene.Node buildCameraSection(com.ignis.core.Camera cam) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Camera"));

        TextField zoom = new TextField(String.valueOf(cam.getZoom()));
        Runnable applyZoom = () -> { cam.setZoom(parseD(zoom.getText(), cam.getZoom())); markProjectDirty(); };
        zoom.setOnAction(e -> applyZoom.run());
        zoom.focusedProperty().addListener((o, a, focused) -> { if (!focused) applyZoom.run(); });

        CheckBox active = new CheckBox("Camera ativa");
        active.setSelected(cam.isActiveCamera());
        active.selectedProperty().addListener((o, a, b) -> { cam.setActive(b); markProjectDirty(); });

        sec.getChildren().addAll(labeledInspectorRow("Zoom", zoom), active);
        return sec;
    }

    private javafx.scene.Node buildScriptsSection(GameObject go) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Componentes / Scripts"));

        ListView<String> list = new ListView<>();
        java.util.List<String> listItems = new java.util.ArrayList<>();
        if (go.getComponent(SpriteComponent.class) != null) {
            listItems.add("SpriteComponent");
        }
        listItems.addAll(go.getScriptNames());
        list.getItems().setAll(listItems);
        list.setPrefHeight(90);

        Button attach = new Button("Adicionar Componente…");
        attach.setOnAction(e -> { openAddComponentDialog(go); });
        
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                if (sel.equals("SpriteComponent")) {
                    SpriteComponent sprite = go.getComponent(SpriteComponent.class);
                    if (sprite != null) go.removeComponent(sprite);
                } else {
                    go.removeScriptByName(sel);
                }
                rebuildInspectorExtras(go);
                refreshHierarchy();
                markProjectDirty();
            }
        });
        
        Button open = new Button("Abrir");
        open.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.equals("SpriteComponent")) openScriptByName(sel);
        });

        // Habilita/Desabilita o botao Abrir dependendo do item selecionado
        list.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            open.setDisable(newV == null || newV.equals("SpriteComponent"));
        });
        open.setDisable(list.getSelectionModel().getSelectedItem() == null || "SpriteComponent".equals(list.getSelectionModel().getSelectedItem()));

        sec.getChildren().addAll(list, new HBox(6, attach, remove, open));

        // Renderizar painel de variaveis para cada script instanciado (pulando SpriteComponent)
        for (com.ignis.core.IgnisScript script : go.getScripts()) {
            if (script instanceof SpriteComponent) continue;
            javafx.scene.Node varsNode = createScriptVariablesNode(script);
            if (varsNode != null) {
                sec.getChildren().add(varsNode);
            }
        }

        return sec;
    }

    private javafx.scene.Node createScriptVariablesNode(com.ignis.core.IgnisScript script) {
        VBox panel = new VBox(4);
        panel.setPadding(new Insets(4, 4, 4, 10));
        panel.setStyle("-fx-border-color: #555; -fx-border-width: 0 0 0 2; -fx-background-color: rgba(255, 255, 255, 0.02);");

        Label title = new Label("[" + script.getScriptName() + " Variables]");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: -ignis-primary; -fx-font-size: 10px;");
        panel.getChildren().add(title);

        java.util.List<java.lang.reflect.Field> fields = com.ignis.core.ScriptSerializationHelper.getSerializedFields(script.getClass());
        boolean hasFields = false;

        for (java.lang.reflect.Field field : fields) {
            Class<?> type = field.getType();
            if (com.ignis.core.ScriptSerializationHelper.isSupportedType(type)) {
                hasFields = true;
                panel.getChildren().add(createFieldEditorNode(script, field));
            }
        }

        return hasFields ? panel : null;
    }

    private javafx.scene.Node createFieldEditorNode(com.ignis.core.IgnisScript script, java.lang.reflect.Field field) {
        String displayName = formatFieldName(field.getName());
        Label label = new Label(displayName);
        label.getStyleClass().add("field-label");
        label.setMinWidth(90);
        label.setWrapText(true);

        javafx.scene.Node editor = createFieldEditorFx(script, field);
        HBox row = new HBox(6, label, editor);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(editor, Priority.ALWAYS);
        return row;
    }

    private String formatFieldName(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString();
    }

    private javafx.scene.Node createFieldEditorFx(com.ignis.core.IgnisScript script, java.lang.reflect.Field field) {
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) {
            CheckBox cb = new CheckBox();
            try {
                field.setAccessible(true);
                cb.setSelected(field.getBoolean(script));
            } catch (Exception ignore) {}
            cb.selectedProperty().addListener((obs, oldV, newV) -> {
                try {
                    field.setAccessible(true);
                    field.setBoolean(script, newV);
                    saveScriptVariablesToPending(script);
                } catch (Exception ex) {
                    System.err.println("Erro ao salvar variavel booleana: " + ex.getMessage());
                }
            });
            return cb;
        } else if (GameObject.class.isAssignableFrom(type)) {
            return createGameObjectEditorFx(script, field);
        } else if (type == com.ignis.core.Texture2D.class) {
            HBox panel = new HBox(4);
            panel.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(panel, Priority.ALWAYS);
            
            Label pathLabel = new Label();
            pathLabel.getStyleClass().add("field-label");
            pathLabel.setMaxWidth(160);
            pathLabel.setWrapText(false);
            
            try {
                field.setAccessible(true);
                com.ignis.core.Texture2D tex = (com.ignis.core.Texture2D) field.get(script);
                pathLabel.setText(tex != null ? tex.getPath() : "(nenhuma)");
            } catch (Exception ignore) {}
            
            Button pick = new Button("...");
            pick.setStyle("-fx-min-width: 24px;");
            pick.setOnAction(e -> {
                File f = chooseSpriteFile();
                if (f != null) {
                    String relative = importSpriteToProject(f);
                    try {
                        field.setAccessible(true);
                        field.set(script, new com.ignis.core.Texture2D(relative));
                        pathLabel.setText(relative);
                        saveScriptVariablesToPending(script);
                        markProjectDirty();
                    } catch (Exception ex) {
                        System.err.println("Erro ao definir Texture2D: " + ex.getMessage());
                    }
                }
            });
            
            Button clear = new Button("X");
            clear.setStyle("-fx-min-width: 24px;");
            clear.setOnAction(e -> {
                try {
                    field.setAccessible(true);
                    field.set(script, null);
                    pathLabel.setText("(nenhuma)");
                    saveScriptVariablesToPending(script);
                    markProjectDirty();
                } catch (Exception ex) {
                    System.err.println("Erro ao limpar Texture2D: " + ex.getMessage());
                }
            });
            
            panel.getChildren().addAll(pathLabel, pick, clear);
            return panel;
        } else {
            TextField tf = new TextField();
            try {
                field.setAccessible(true);
                Object val = field.get(script);
                tf.setText(val != null ? val.toString() : "");
            } catch (Exception ignore) {}
            
            Runnable apply = () -> {
                try {
                    field.setAccessible(true);
                    String text = tf.getText();
                    if (type == int.class || type == Integer.class) {
                        field.set(script, Integer.parseInt(text.trim()));
                    } else if (type == double.class || type == Double.class) {
                        field.set(script, Double.parseDouble(text.trim()));
                    } else if (type == float.class || type == Float.class) {
                        field.set(script, Float.parseFloat(text.trim()));
                    } else if (type == long.class || type == Long.class) {
                        field.set(script, Long.parseLong(text.trim()));
                    } else if (type == String.class) {
                        field.set(script, text);
                    }
                    saveScriptVariablesToPending(script);
                } catch (NumberFormatException ignore) {
                    try {
                        Object val = field.get(script);
                        tf.setText(val != null ? val.toString() : "");
                    } catch (Exception ignored) {}
                } catch (Exception ex) {
                    System.err.println("Erro ao definir variavel: " + ex.getMessage());
                }
            };
            
            tf.setOnAction(e -> apply.run());
            tf.focusedProperty().addListener((obs, oldV, focused) -> {
                if (!focused) apply.run();
            });
            return tf;
        }
    }

    private javafx.scene.Node createGameObjectEditorFx(com.ignis.core.IgnisScript script, java.lang.reflect.Field field) {
        HBox panel = new HBox(4);
        panel.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(panel, Priority.ALWAYS);

        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(comboBox, Priority.ALWAYS);

        comboBox.getItems().add("None");
        for (GameObject entity : game.getEntities()) {
            if (entity != script.getGameObject()) {
                comboBox.getItems().add(entity.getName());
            }
        }

        try {
            field.setAccessible(true);
            Object currentValue = field.get(script);
            if (currentValue instanceof GameObject) {
                comboBox.setValue(((GameObject) currentValue).getName());
            } else {
                comboBox.setValue("None");
            }
        } catch (Exception e) {
            comboBox.setValue("None");
        }

        comboBox.setOnAction(e -> {
            String selectedName = comboBox.getValue();
            try {
                field.setAccessible(true);
                if ("None".equals(selectedName) || selectedName == null) {
                    field.set(script, null);
                } else {
                    GameObject target = null;
                    for (GameObject entity : game.getEntities()) {
                        if (entity.getName().equals(selectedName)) {
                            target = entity;
                            break;
                        }
                    }
                    field.set(script, target);
                }
                saveScriptVariablesToPending(script);
            } catch (Exception ex) {
                System.err.println("Erro ao definir referencia: " + ex.getMessage());
            }
        });

        Button pickButton = new Button("◎");
        pickButton.setTooltip(new Tooltip("Selecionar objeto clicando no viewport"));
        pickButton.setOnAction(e -> {
            javafx.scene.Cursor originalCursor = viewportCanvas.getCursor();
            viewportCanvas.setCursor(javafx.scene.Cursor.CROSSHAIR);
            
            javafx.event.EventHandler<javafx.scene.input.MouseEvent> pickHandler = new javafx.event.EventHandler<>() {
                @Override
                public void handle(javafx.scene.input.MouseEvent me) {
                    GameObject clicked = game.getObjectAt((int) me.getX(), (int) me.getY());
                    if (clicked != null && clicked != script.getGameObject()) {
                        try {
                            field.setAccessible(true);
                            field.set(script, clicked);
                            comboBox.setValue(clicked.getName());
                            saveScriptVariablesToPending(script);
                        } catch (Exception ex) {
                            System.err.println("Erro ao associar objeto clicado: " + ex.getMessage());
                        }
                    }
                    viewportCanvas.setCursor(originalCursor);
                    viewportCanvas.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, this);
                }
            };
            viewportCanvas.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, pickHandler);
            
            javafx.event.EventHandler<javafx.scene.input.KeyEvent> escHandler = new javafx.event.EventHandler<>() {
                @Override
                public void handle(javafx.scene.input.KeyEvent ke) {
                    if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        viewportCanvas.setCursor(originalCursor);
                        viewportCanvas.removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, pickHandler);
                        viewportCanvas.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this);
                    }
                }
            };
            viewportCanvas.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, escHandler);
        });

        // Configura Drag & Drop Target
        comboBox.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                comboBox.setStyle("-fx-border-color: -ignis-primary; -fx-border-width: 1px;");
                e.acceptTransferModes(javafx.scene.input.TransferMode.ANY);
            }
            e.consume();
        });

        comboBox.setOnDragExited(e -> {
            comboBox.setStyle("");
            e.consume();
        });

        comboBox.setOnDragDropped(e -> {
            javafx.scene.input.Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String goName = db.getString();
                GameObject target = null;
                for (GameObject entity : game.getEntities()) {
                    if (entity.getName().equals(goName)) {
                        if (entity != script.getGameObject()) {
                            target = entity;
                            break;
                        }
                    }
                }
                if (target != null) {
                    try {
                        field.setAccessible(true);
                        field.set(script, target);
                        comboBox.setValue(target.getName());
                        saveScriptVariablesToPending(script);
                        success = true;
                    } catch (Exception ex) {
                        System.err.println("Erro ao associar drag-drop: " + ex.getMessage());
                    }
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });

        panel.getChildren().addAll(comboBox, pickButton);
        return panel;
    }

    private void saveScriptVariablesToPending(com.ignis.core.IgnisScript script) {
        if (currentProject != null && currentProject.getCurrentScene() != null) {
            org.json.JSONObject variables = com.ignis.core.ScriptSerializationHelper.saveScriptVariables(script);
            String key = script.getGameObject().getId() + ":" + script.getClass().getSimpleName();
            currentProject.getCurrentScene().getPendingScriptVariables().put(key, variables);
            markProjectDirty();
        }
    }

    public void reloadAllScriptInstances() {
        com.ignis.core.ScriptManager sm = game.getScriptManager();
        if (sm == null) return;
        
        Scene currentScene = (currentProject != null) ? currentProject.getCurrentScene() : null;
        
        for (GameObject obj : game.getEntities()) {
            java.util.List<String> scriptNames = new java.util.ArrayList<>(obj.getScriptNames());
            obj.getScripts().clear();
            
            for (String scriptName : scriptNames) {
                com.ignis.core.IgnisScript newInstance = sm.createScriptInstance(scriptName, obj, game);
                if (newInstance != null) {
                    obj.getScripts().add(newInstance);
                    if (currentScene != null) {
                        currentScene.applyPendingScriptVariables(obj, newInstance);
                    }
                }
            }
        }
        
        Platform.runLater(() -> {
            if (selected != null) {
                rebuildInspectorExtras(selected);
            }
        });
    }

    // Anexa um script (da pasta do projeto) ao objeto — espelha o editor Swing:
    // registra o nome em getScriptNames() e (se compilavel) adiciona a instancia.
    private void attachScriptTo(GameObject go) {
        if (!requireProject()) return;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) { sm = new com.ignis.core.ScriptManager(projectFolder); game.setScriptManager(sm); }
            java.util.List<String> available = new java.util.ArrayList<>(sm.listAvailableScripts());
            available.removeAll(go.getScriptNames());
            if (available.isEmpty()) { setStatus("Nenhum script disponivel para anexar."); return; }

            ChoiceDialog<String> dlg = new ChoiceDialog<>(available.get(0), available);
            dlg.setTitle("Anexar script");
            dlg.setHeaderText(null);
            dlg.setContentText("Script:");
            java.util.Optional<String> choice = dlg.showAndWait();
            if (choice.isEmpty()) return;

            String name = choice.get();
            if (!go.getScriptNames().contains(name)) {
                go.getScriptNames().add(name);
                try {
                    com.ignis.core.IgnisScript inst = sm.createScriptInstance(name, go, game);
                    if (inst != null) go.getScripts().add(inst);
                } catch (Exception ignore) { /* compila no Play se falhar agora */ }
                markProjectDirty();
                setStatus("Script anexado: " + name);
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao anexar script:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void openScriptByName(String scriptName) {
        if (!requireProject()) return;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) { sm = new com.ignis.core.ScriptManager(projectFolder); game.setScriptManager(sm); }
            new FxCodeEditor(this, sm, scriptName).show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir script:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Importa a imagem para assets/sprites do projeto (se ainda estiver fora dele)
    // e retorna o caminho RELATIVO ao projeto — sprites passam a viver no projeto e
    // ficam portateis. Se ja estiver dentro, apenas relativiza. Fallback para
    // caminho absoluto se nao houver projeto ou a copia falhar.
    private String importSpriteToProject(File src) {
        try {
            String existingRel = com.ignis.core.AssetResolver.relativize(src);
            if (existingRel != null) return existingRel; // ja dentro do projeto
            if (projectFolder == null) return src.getAbsolutePath();
            File spritesDir = new File(projectFolder, "assets/sprites");
            if (!spritesDir.exists()) spritesDir.mkdirs();
            File dest = uniqueDestFile(spritesDir, src.getName());
            java.nio.file.Files.copy(src.toPath(), dest.toPath());
            refreshAssetBrowser();
            String rel = com.ignis.core.AssetResolver.relativize(dest);
            setStatus("Sprite importado: " + (rel != null ? rel : dest.getName()));
            return rel != null ? rel : dest.getAbsolutePath();
        } catch (Exception ex) {
            setStatus("Falha ao importar sprite: " + ex.getMessage());
            return src.getAbsolutePath();
        }
    }

    // Gera um destino que nao sobrescreve arquivos existentes (nome, nome_1, nome_2…).
    private static File uniqueDestFile(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 1;
        do { dest = new File(dir, base + "_" + i++ + ext); } while (dest.exists());
        return dest;
    }

    private File chooseSpriteFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Escolher sprite");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        if (projectFolder != null && projectFolder.isDirectory()) {
            File assets = new File(projectFolder, "assets");
            fc.setInitialDirectory(assets.isDirectory() ? assets : projectFolder);
        }
        return fc.showOpenDialog(primaryStage);
    }

    private HBox labeledInspectorRow(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        l.setMinWidth(70);
        if (control instanceof javafx.scene.layout.Region) {
            ((javafx.scene.layout.Region) control).setMaxWidth(Double.MAX_VALUE);
        }
        HBox.setHgrow(control, Priority.ALWAYS);
        HBox row = new HBox(8, l, control);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private static String spriteLabel(String path) {
        return (path == null || path.isEmpty()) ? "(nenhum)" : path;
    }

    private static java.lang.reflect.Method methodOrNull(Object o, String name) {
        try { return o.getClass().getMethod(name); } catch (Exception e) { return null; }
    }

    private static java.lang.reflect.Method setColorMethodOrNull(Object o) {
        try { return o.getClass().getMethod("setColor", java.awt.Color.class); } catch (Exception e) { return null; }
    }

    private static javafx.scene.paint.Color awtToFx(java.awt.Color c) {
        return new javafx.scene.paint.Color(
                c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0, c.getAlpha() / 255.0);
    }

    private static java.awt.Color fxToAwt(javafx.scene.paint.Color c) {
        return new java.awt.Color(
                (float) c.getRed(), (float) c.getGreen(), (float) c.getBlue(), (float) c.getOpacity());
    }

    private static double parseD(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static int parseI(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private void setStatus(String text) {
        if (status != null) status.setText(" " + text);
        com.ignis.core.IgnisLogger.info(text);
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
                    game.setSize(w, h);
                    if (game.getViewport() != null) {
                        game.getViewport().resize(w, h);
                    }
                }

                Graphics2D g2d = buffer.createGraphics();
                try {
                    game.renderWorldTo(g2d, w, h, selected);
                } finally {
                    g2d.dispose();
                }

                fxImage = FxImageBridge.toFXImage(buffer, fxImage);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, w, h);
                gc.drawImage(fxImage, 0, 0);

                updateCameraLabels();
                updateInspectorFields();

                // Colaboracao em tempo real: o host transmite o snapshot da cena
                // (throttle interno ~12 Hz); o convidado aplica via listener.
                com.ignis.collab.CollabBridge bridge = com.ignis.collab.CollabBridge.get();
                if (bridge != null) bridge.onEditorFrame();
            }
        };
        com.ignis.collab.CollabBridge.init(game);
        // Executor de comandos do host: aplica comandos de convidados reusando o
        // registry do MCP (host-autoritativo). Sem registry ativo, ignora.
        com.ignis.collab.CollabBridge.setCommandExecutor((tool, args) -> {
            try {
                com.ignis.mcp.IgnisToolRegistry reg = com.ignis.mcp.McpService.getRegistry();
                return reg != null ? reg.call(tool, args) : "(host sem registry MCP ativo)";
            } catch (Exception e) {
                return "erro ao aplicar comando: " + e.getMessage();
            }
        });
        timer.start();
    }

    public static void main(String[] args) {
        boolean mcpMode = false;
        String projectPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("--mcp-server".equals(args[i])) {
                mcpMode = true;
                if (i + 1 < args.length) {
                    projectPath = args[i + 1];
                }
            }
        }

        if (mcpMode) {
            // Silencia o standard output redirecionando logs para o System.err
            System.setOut(System.err);
            
            if (projectPath == null) {
                System.err.println("Erro: Caminho do projeto nao especificado para o modo MCP.");
                System.exit(1);
            }
            File folder = new File(projectPath);
            if (!folder.exists() || !folder.isDirectory()) {
                System.err.println("Erro: Diretorio do projeto invalido: " + projectPath);
                System.exit(1);
            }

            // Inicia o JavaFX Platform em modo Headless
            try {
                Platform.startup(() -> {
                    System.err.println("[IgnisMCP] Runtime JavaFX inicializado em modo headless.");
                });
            } catch (IllegalStateException e) {
                // JavaFX runtime ja iniciado
            }

            // Inicia o servidor MCP
            com.ignis.mcp.McpServerManager.start(folder);
        } else {
            launch(args);
        }
    }

    private void zoomCamera(double factor) {
        com.ignis.core.Camera cam = game.getMainCamera();
        if (cam != null) {
            cam.setZoom(cam.getZoom() * factor);
            updateCameraLabels();
        }
    }

    private void resetCamera() {
        com.ignis.core.Camera cam = game.getMainCamera();
        if (cam != null) {
            cam.setPosition(0, 0);
            cam.setZoom(1.0);
            cam.setRotation(0);
            updateCameraLabels();
        }
    }

    private void focusCameraOnSelected() {
        GameObject sel = this.selected;
        com.ignis.core.Camera cam = game.getMainCamera();
        if (sel != null && cam != null) {
            double centerX = sel.getX() + sel.getWidth() / 2.0;
            double centerY = sel.getY() + sel.getHeight() / 2.0;
            cam.setPosition(centerX, centerY);
            updateCameraLabels();
        }
    }

    private void updateCameraLabels() {
        if (cameraPosLabel == null || cameraZoomLabel == null) return;
        com.ignis.core.Camera cam = game.getMainCamera();
        if (cam != null) {
            Platform.runLater(() -> {
                cameraPosLabel.setText(String.format("Cam Pos: (%.1f, %.1f)", cam.getX(), cam.getY()));
                cameraZoomLabel.setText(String.format("Zoom: %.0f%%", cam.getZoom() * 100));
            });
        }
    }

    private void copySelected() {
        if (selected != null) {
            clipboardObject = selected;
            setStatus("Copiado: " + selected.getName());
        }
    }

    private void pasteSelected() {
        if (clipboardObject != null) {
            try {
                GameObject original = clipboardObject;
                GameObject copy = com.ignis.core.EntityFactory.create(original.getType());
                copy.setGame(game);
                copy.setX(original.getX() + 20);
                copy.setY(original.getY() + 20);
                copy.setWidth(original.getWidth());
                copy.setHeight(original.getHeight());
                copy.setSpritePath(original.getSpritePath());
                try { copy.loadProperties(original.saveProperties()); } catch (Exception ignore) {}
                copy.setName(uniqueNameExcept(original.getName() + " (Copy)", null));
                addEntityTracked(copy);

                refreshHierarchy();
                selectEntity(copy);
                markProjectDirty();
                setStatus("Colado: " + copy.getName());
                undoManager.push("Colar " + copy.getName(),
                        () -> { removeEntityTracked(copy); refreshHierarchy(); selectEntity(null); markProjectDirty(); },
                        () -> { addEntityTracked(copy); refreshHierarchy(); selectEntity(copy); markProjectDirty(); });
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Erro ao colar objeto:\n" + ex.getMessage()).showAndWait();
            }
        }
    }

    private ContextMenu buildViewportContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
        criarObjeto.setOnAction(e -> createEntity("GameObject"));
        MenuItem criarCamera = new MenuItem("Criar Câmera");
        criarCamera.setOnAction(e -> createEntity("Camera"));
        menu.getItems().addAll(criarObjeto, criarCamera, new SeparatorMenuItem());

        MenuItem dup = new MenuItem("Duplicar (Ctrl+D)");
        dup.setOnAction(e -> duplicateSelected());

        MenuItem ren = new MenuItem("Renomear… (F2)");
        ren.setOnAction(e -> renameSelected());

        MenuItem del = new MenuItem("Deletar (Delete)");
        del.setOnAction(e -> deleteSelected());

        MenuItem copyItem = new MenuItem("Copiar (Ctrl+C)");
        copyItem.setOnAction(e -> copySelected());

        MenuItem pasteItem = new MenuItem("Colar (Ctrl+V)");
        pasteItem.setOnAction(e -> pasteSelected());

        MenuItem savePrefab = new MenuItem("Salvar como Prefab…");
        savePrefab.setOnAction(e -> saveSelectedAsPrefab());
        MenuItem instPrefab = new MenuItem("Instanciar Prefab…");
        instPrefab.setOnAction(e -> instantiatePrefabDialog());

        menu.getItems().addAll(dup, ren, del,
                new SeparatorMenuItem(), copyItem, pasteItem,
                new SeparatorMenuItem(), savePrefab, instPrefab);
        return menu;
    }

    private void updateInspectorFields() {
        if (selected == null || suppressInspectorEvents) return;
        suppressInspectorEvents = true;
        try {
            if (!nameField.isFocused()) {
                String val = selected.getName();
                if (!java.util.Objects.equals(nameField.getText(), val)) {
                    nameField.setText(val);
                }
            }
            if (!xField.isFocused()) {
                String val = String.valueOf(selected.getX());
                if (!java.util.Objects.equals(xField.getText(), val)) {
                    xField.setText(val);
                }
            }
            if (!yField.isFocused()) {
                String val = String.valueOf(selected.getY());
                if (!java.util.Objects.equals(yField.getText(), val)) {
                    yField.setText(val);
                }
            }
            if (!wField.isFocused()) {
                String val = String.valueOf(selected.getWidth());
                if (!java.util.Objects.equals(wField.getText(), val)) {
                    wField.setText(val);
                }
            }
            if (!hField.isFocused()) {
                String val = String.valueOf(selected.getHeight());
                if (!java.util.Objects.equals(hField.getText(), val)) {
                    hField.setText(val);
                }
            }
            if (!rotField.isFocused()) {
                String val = String.valueOf(selected.getRotation());
                if (!java.util.Objects.equals(rotField.getText(), val)) {
                    rotField.setText(val);
                }
            }
            if (!visibleCheck.isFocused()) {
                boolean val = selected.isVisible();
                if (visibleCheck.isSelected() != val) {
                    visibleCheck.setSelected(val);
                }
            }
        } finally {
            suppressInspectorEvents = false;
        }
    }

    private ContextMenu buildAssetsContextMenu(File file) {
        ContextMenu menu = new ContextMenu();
        if (file == null) {
            MenuItem refreshItem = new MenuItem("Atualizar Assets");
            refreshItem.setOnAction(e -> refreshAssetBrowser());
            menu.getItems().add(refreshItem);
            return menu;
        }

        MenuItem openItem = new MenuItem("Abrir / Editar (Sistema)");
        openItem.setOnAction(e -> openAssetFile(file));

        if (file.isFile() && file.getName().endsWith(".java")) {
            MenuItem openInIgnisItem = new MenuItem("Abrir no Editor do Ignis");
            openInIgnisItem.setOnAction(e -> openScriptInIgnisEditor(file));
            menu.getItems().add(openInIgnisItem);
        }

        if (file.isFile() && file.getName().endsWith(".prefab.json")) {
            MenuItem instItem = new MenuItem("Instanciar Prefab");
            instItem.setOnAction(e -> instantiatePrefabByName(prefabNameOf(file)));
            menu.getItems().add(instItem);
        }

        menu.getItems().add(openItem);

        MenuItem renameItem = new MenuItem("Renomear");
        renameItem.setOnAction(e -> renameAssetFile(file));

        MenuItem deleteItem = new MenuItem("Deletar");
        deleteItem.setOnAction(e -> deleteAssetFile(file));

        MenuItem copyPathItem = new MenuItem("Copiar Caminho");
        copyPathItem.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(file.getAbsolutePath());
            clipboard.setContent(content);
            setStatus("Caminho copiado: " + file.getName());
        });

        menu.getItems().addAll(renameItem, deleteItem, new SeparatorMenuItem(), copyPathItem);

        if (file.isDirectory()) {
            MenuItem newScriptItem = new MenuItem("Criar Novo Script...");
            newScriptItem.setOnAction(e -> createNewScriptInFolder(file));
            menu.getItems().add(0, newScriptItem);
        }

        return menu;
    }

    private void renameAssetFile(File file) {
        TextInputDialog dialog = new TextInputDialog(file.getName());
        dialog.setTitle("Renomear Arquivo");
        dialog.setHeaderText("Renomear '" + file.getName() + "'");
        dialog.setContentText("Novo Nome:");
        dialog.showAndWait().ifPresent(newName -> {
            if (newName.trim().isEmpty() || newName.equals(file.getName())) return;
            File dest = new File(file.getParentFile(), newName.trim());
            if (dest.exists()) {
                new Alert(Alert.AlertType.ERROR, "Destino já existe!").showAndWait();
                return;
            }
            if (file.renameTo(dest)) {
                refreshAssetBrowser();
                setStatus("Renomeado para: " + dest.getName());
            } else {
                new Alert(Alert.AlertType.ERROR, "Falha ao renomear.").showAndWait();
            }
        });
    }

    private void deleteAssetFile(File file) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Tem certeza que deseja deletar '" + file.getName() + "'?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            if (deleteRecursively(file)) {
                refreshAssetBrowser();
                setStatus("Deletado: " + file.getName());
            } else {
                new Alert(Alert.AlertType.ERROR, "Falha ao deletar arquivo/diretório.").showAndWait();
            }
        }
    }

    private boolean deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File sub : files) {
                    if (!deleteRecursively(sub)) return false;
                }
            }
        }
        return f.delete();
    }

    private void createNewScriptInFolder(File folder) {
        if (!requireProject()) return;
        try {
            com.ignis.core.ScriptManager sm = game.getScriptManager();
            if (sm == null) {
                sm = new com.ignis.core.ScriptManager(projectFolder);
                game.setScriptManager(sm);
            }
            TextInputDialog input = new TextInputDialog("NovoScript");
            input.setTitle("Novo script");
            input.setHeaderText(null);
            input.setContentText("Nome da classe do script:");
            java.util.Optional<String> nameOpt = input.showAndWait();
            if (nameOpt.isEmpty() || nameOpt.get().trim().isEmpty()) return;

            if (!sm.createNewScript(nameOpt.get().trim())) {
                new Alert(Alert.AlertType.ERROR,
                        "Não foi possível criar o script (nome inválido ou já existe).").showAndWait();
                return;
            }
            refreshAssetBrowser();
            setStatus("Script criado com sucesso.");
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao criar script:\n" + ex.getMessage()).showAndWait();
        }
    }
}
