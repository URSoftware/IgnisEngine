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
import javafx.scene.control.ContextMenu;
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
    private FxSettingsWindow settingsWindow;
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

    private Label cameraPosLabel;
    private Label cameraZoomLabel;
    private GameObject clipboardObject;
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
        VBox inspector = buildInspector();

        mainSplit = new SplitPane();
        mainSplit.getItems().addAll(leftSplit, viewportPane, inspector);
        mainSplit.setDividerPositions(0.2, 0.78);
        root.setCenter(mainSplit);

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

        // Scene key event filter for tools, selection controls, camera resets
        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
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
            stopGameLoop();
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        // Divisores so assumem a posicao salva apos o primeiro layout da cena.
        Platform.runLater(this::restoreDividers);

        startRenderBridge(canvas);

        // Tela inicial de selecao de projeto (analoga ao startup do editor Swing).
        Platform.runLater(() -> showProjectStartup(stage, true));
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
        
        view.getItems().addAll(
            zoomInItem, zoomOutItem, zoom100Item, new SeparatorMenuItem(),
            resetCamItem, focusSelectedItem, new SeparatorMenuItem(),
            showGridItem, gridSizeMenu, new SeparatorMenuItem(),
            showCollidersItem
        );

        Menu help = new Menu("Ajuda");
        MenuItem about = new MenuItem("Sobre");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "IgnisEngine — editor JavaFX (Fase 3 da migracao).").showAndWait());
        help.getItems().add(about);

        return new MenuBar(file, buildSceneMenu(), tools, view, help);
    }

    // Menu "Cena": criar/duplicar/renomear/deletar/reordenar entidades.
    private Menu buildSceneMenu() {
        Menu scene = new Menu("Cena");
        Menu criar = new Menu("Criar objeto");
        for (String t : com.ignis.core.EntityFactory.getSupportedTypes()) {
            MenuItem mi = new MenuItem(t);
            mi.setOnAction(e -> createEntity(t));
            criar.getItems().add(mi);
        }
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
        scene.getItems().addAll(criar, new SeparatorMenuItem(), dup, ren, del,
                new SeparatorMenuItem(), up, down, top, bottom);
        return scene;
    }

    private ContextMenu buildHierarchyContextMenu() {
        ContextMenu menu = new ContextMenu();
        Menu criar = new Menu("Criar objeto");
        for (String t : com.ignis.core.EntityFactory.getSupportedTypes()) {
            MenuItem mi = new MenuItem(t);
            mi.setOnAction(e -> createEntity(t));
            criar.getItems().add(mi);
        }
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

        menu.getItems().addAll(criar, new SeparatorMenuItem(), dup, ren, del,
                new SeparatorMenuItem(), copyItem, pasteItem, new SeparatorMenuItem(), ordenar);
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
            refreshAssetBrowser();
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
            refreshHierarchy();
            refreshAssetBrowser();
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

            FxCodeEditor codeEditor = new FxCodeEditor(null, scriptManager, scriptName);
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
            settingsWindow = new FxSettingsWindow(primaryStage, game);
            settingsWindow.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Configuracoes:\n" + ex.getMessage()).showAndWait();
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

        // O botao direito NAO e encaminhado ao engine (game.dispatchEvent) para nao acionar
        // selecao/drag do editor; ele e tratado so em setOnContextMenuRequested. O Input
        // (estado para scripts em Play) continua recebendo todos os botoes.
        canvas.setOnMousePressed(e -> {
            canvas.requestFocus();
            java.awt.event.MouseEvent awtEvent = buildAwtMouseEvent(e, java.awt.event.MouseEvent.MOUSE_PRESSED);
            com.ignis.core.Input.getInstance().mousePressed(awtEvent);
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
            return cell;
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, item) -> {
            if (suppressSelectionEvents) return;
            suppressSelectionEvents = true;
            try {
                if (item == null || item == hierarchyRoot) { setSelected(null); return; }
                int idx = hierarchyRoot.getChildren().indexOf(item);
                java.util.List<GameObject> ents = game.getEntities();
                setSelected(idx >= 0 && idx < ents.size() ? ents.get(idx) : null);
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
                    Menu createMenu = new Menu("Criar objeto");
                    for (String type : com.ignis.core.EntityFactory.getSupportedTypes()) {
                        MenuItem item = new MenuItem(type);
                        item.setOnAction(e -> createEntity(type));
                        createMenu.getItems().add(item);
                    }
                    menu.getItems().add(createMenu);
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
            game.addEntity(obj);
            // Camera precisa ser registrada na lista de cameras do Game, alem de virar entidade.
            if (obj instanceof com.ignis.core.Camera) game.addCamera((com.ignis.core.Camera) obj);
            refreshHierarchy();
            selectEntity(obj);
            markProjectDirty();
            setStatus("Objeto criado: " + obj.getName());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao criar objeto:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void duplicateSelected() {
        if (selected == null) { setStatus("Nada selecionado para duplicar."); return; }
        try {
            GameObject original = selected;
            GameObject copy = com.ignis.core.EntityFactory.create(original.getType());
            copy.setGame(game);
            copy.setX(original.getX());
            copy.setY(original.getY());
            copy.setWidth(original.getWidth());
            copy.setHeight(original.getHeight());
            copy.setSpritePath(original.getSpritePath());
            try { copy.loadProperties(original.saveProperties()); } catch (Exception ignore) { /* props opcionais */ }
            copy.setName(uniqueNameExcept(original.getName() + " (Copy)", null));
            game.addEntity(copy);
            refreshHierarchy();
            selectEntity(copy);
            markProjectDirty();
            setStatus("Duplicado: " + copy.getName());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao duplicar:\n" + ex.getMessage()).showAndWait();
        }
    }

    private void deleteSelected() {
        if (selected == null) { setStatus("Nada selecionado para deletar."); return; }
        GameObject toDelete = selected;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deletar '" + toDelete.getName() + "'?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        game.removeEntity(toDelete);
        setSelected(null);
        refreshHierarchy();
        markProjectDirty();
        setStatus("Deletado: " + toDelete.getName());
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
        go.setName(uniqueNameExcept(opt.get().trim(), go));
        refreshHierarchy();
        selectEntity(go);
        markProjectDirty();
        setStatus("Renomeado para: " + go.getName());
    }

    private void moveSelected(int delta) {
        if (selected == null) return;
        GameObject toReselect = selected; // refreshHierarchy() zera 'selected' via listener
        if (delta < 0) game.moveEntityUp(toReselect);
        else game.moveEntityDown(toReselect);
        refreshHierarchy();
        selectEntity(toReselect);
        markProjectDirty();
    }

    private void moveSelectedTo(int index) {
        if (selected == null) return;
        GameObject toReselect = selected; // refreshHierarchy() zera 'selected' via listener
        int target = (index == Integer.MAX_VALUE) ? game.getEntities().size() - 1 : index;
        if (target < 0) target = 0;
        game.moveEntityToIndex(toReselect, target);
        refreshHierarchy();
        selectEntity(toReselect);
        markProjectDirty();
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
                    hierarchy.getSelectionModel().select(hierarchyRoot.getChildren().get(idx));
                }
            }
            setSelected(go); // SEMPRE — atualiza o Inspector (fonte unica de verdade)
        } finally {
            suppressSelectionEvents = false;
        }
    }

    // ---------------- Auto Save do projeto ----------------

    private void setupProjectAutoSave() {
        // Marca o projeto como "sujo" ao fim de um arraste (mover/rotacionar/escalar).
        game.setTransformListener(new com.ignis.core.Game.TransformListener() {
            @Override public void onTransformStart(GameObject o, double x, double y, double rotation, int w, int h) { }
            @Override public void onTransformEnd(GameObject o) { markProjectDirty(); }
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

    // ---------------- Asset Browser (arvore de arquivos do projeto) ----------------

    private javafx.scene.Node buildAssetBrowser() {
        VBox box = new VBox(4);
        box.getStyleClass().add("ignis-panel");
        Label title = new Label("Assets");
        title.getStyleClass().add("panel-title");

        assetTree = new TreeView<>();
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
                    if (file.getName().endsWith(".java")) {
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
            FxCodeEditor codeEditor = new FxCodeEditor(null, sm, scriptName);
            codeEditor.show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Editor de Codigo:\n" + ex.getMessage()).showAndWait();
        }
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
        l.getStyleClass().add("field-label");
        grid.add(l, 0, row);
        grid.add(field, 1, row);
    }

    private void applyIfEditing(Runnable action) {
        if (suppressInspectorEvents || selected == null) return;
        try { action.run(); markProjectDirty(); } catch (Exception ignore) { /* entrada invalida */ }
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

                SwingFXUtils.toFXImage(buffer, fxImage);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, w, h);
                gc.drawImage(fxImage, 0, 0);

                updateCameraLabels();
                updateInspectorFields();
            }
        };
        timer.start();
    }

    public static void main(String[] args) {
        launch(args);
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
                game.addEntity(copy);
                if (copy instanceof com.ignis.core.Camera) game.addCamera((com.ignis.core.Camera) copy);

                refreshHierarchy();
                selectEntity(copy);
                setStatus("Colado: " + copy.getName());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Erro ao colar objeto:\n" + ex.getMessage()).showAndWait();
            }
        }
    }

    private ContextMenu buildViewportContextMenu() {
        ContextMenu menu = new ContextMenu();

        Menu criar = new Menu("Criar objeto");
        for (String t : com.ignis.core.EntityFactory.getSupportedTypes()) {
            MenuItem mi = new MenuItem(t);
            mi.setOnAction(e -> createEntity(t));
            criar.getItems().add(mi);
        }

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

        menu.getItems().addAll(criar, new SeparatorMenuItem(), dup, ren, del, new SeparatorMenuItem(), copyItem, pasteItem);
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
