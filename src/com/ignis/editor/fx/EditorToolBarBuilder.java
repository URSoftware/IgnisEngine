package com.ignis.editor.fx;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

/**
 * Monta a barra de ferramentas principal do editor (Fase F, passo 9 — extraida
 * do IgnisEditorApp). Mesmo padrao do EditorPanelBuilder: classe auxiliar no
 * mesmo pacote que escreve os campos package-private do editor (playButton,
 * btnMove, sceneSelector, ...) e devolve o no pronto para o layout. Nenhuma
 * API publica nova — o estado continua morando no IgnisEditorApp.
 */
final class EditorToolBarBuilder {

    private final IgnisEditorApp app;

    EditorToolBarBuilder(IgnisEditorApp app) {
        this.app = app;
    }

    /**
     * Cria a ToolBar completa e inicializa os campos de controle do editor
     * (botoes de play/stop, ferramentas de gizmo, camera, seletor de cena).
     */
    ToolBar build(Stage stage) {
        Button btnOpen = new Button("Abrir");
        btnOpen.setOnAction(e -> app.openProjectViaChooser(stage));
        Button btnBuild = new Button("Build");
        btnBuild.setOnAction(e -> app.openBuildDialog());
        app.playButton = new Button("▶ Play");
        app.playButton.setOnAction(e -> app.playWorld());
        app.stopButton = new Button("⏹ Stop");
        app.stopButton.setOnAction(e -> app.stopWorld());
        app.stopButton.setDisable(true);

        app.btnMove = new ToggleButton("Mover (W)");
        app.btnRotate = new ToggleButton("Rotacionar (E)");
        app.btnScale = new ToggleButton("Redimensionar (R)");
        app.btnWorldPaint = new ToggleButton("Pintar Mundo");
        app.btnWorldPaint.setTooltip(new Tooltip("Pinta barreiras na grade do mundo. Ctrl+arraste apaga."));
        ToggleGroup toolGroup = new ToggleGroup();
        app.btnMove.setToggleGroup(toolGroup);
        app.btnRotate.setToggleGroup(toolGroup);
        app.btnScale.setToggleGroup(toolGroup);
        app.btnWorldPaint.setToggleGroup(toolGroup);
        app.btnMove.setSelected(true);

        app.btnMove.setOnAction(e -> app.game.setCurrentTool(com.ignis.core.Game.ToolType.MOVE));
        app.btnRotate.setOnAction(e -> app.game.setCurrentTool(com.ignis.core.Game.ToolType.ROTATE));
        app.btnScale.setOnAction(e -> app.game.setCurrentTool(com.ignis.core.Game.ToolType.SCALE));
        app.btnWorldPaint.setOnAction(e -> {
            // Requer um World na cena; se nao houver, cria um (limites default) para pintar.
            if (app.currentProject != null && app.currentProject.getCurrentScene() != null
                    && app.currentProject.getCurrentScene().getWorld() == null) {
                app.scenes.createWorldForScene(app.currentProject.getCurrentScene());
            }
            app.game.setCurrentTool(com.ignis.core.Game.ToolType.WORLD_PAINT);
            app.setStatus("Pincel de mundo: clique/arraste para bloquear; Ctrl para apagar.");
        });

        Button btnZoomIn = new Button("Zoom In");
        btnZoomIn.setOnAction(e -> app.zoomCamera(1.25));
        Button btnZoomOut = new Button("Zoom Out");
        btnZoomOut.setOnAction(e -> app.zoomCamera(0.8));
        Button btnResetCam = new Button("Reset Cam");
        btnResetCam.setOnAction(e -> app.resetCamera());
        Button btnFocusSelected = new Button("Focus Selected");
        btnFocusSelected.setOnAction(e -> app.focusCameraOnSelected());
        Button btnFrameAll = new Button("Enquadrar Tudo");
        btnFrameAll.setTooltip(new Tooltip("Centraliza e ajusta o zoom para mostrar todos os objetos da cena (Shift+F)"));
        btnFrameAll.setOnAction(e -> app.frameAllObjects());

        // Preview da camera do jogo: ligado, a Scene View mostra exatamente o que a
        // camera ativa da cena ve; desligado (padrao), navega-se livre pela cena com
        // a camera do editor, sem mexer na camera do jogo.
        app.cameraPreviewToggle = new ToggleButton("Ver Câmera do Jogo");
        app.cameraPreviewToggle.setTooltip(new Tooltip(
                "Alterna entre a visão livre do editor e a visão da câmera ativa do jogo"));
        app.cameraPreviewToggle.setOnAction(e -> app.setCameraPreview(app.cameraPreviewToggle.isSelected()));

        app.cameraPosLabel = new Label("Cam Pos: (0.0, 0.0)");
        app.cameraPosLabel.getStyleClass().add("toolbar-label");
        app.cameraZoomLabel = new Label("Zoom: 100%");
        app.cameraZoomLabel.getStyleClass().add("toolbar-label");

        // Organizador de cenários: seletor de cena ativa + gerenciador.
        app.sceneSelector = new ComboBox<>();
        app.sceneSelector.setTooltip(new Tooltip("Cena ativa"));
        app.sceneSelector.setPrefWidth(160);
        app.sceneSelector.setOnAction(e -> app.scenes.onSceneSelectorChanged());
        Button btnScenes = new Button("Cenários…");
        btnScenes.setTooltip(new Tooltip("Criar, organizar e definir a cena inicial; criar mundos"));
        btnScenes.setOnAction(e -> app.scenes.openSceneManager());

        return new ToolBar(
            btnOpen, btnBuild, new Separator(),
            app.playButton, app.stopButton, new Separator(),
            app.btnMove, app.btnRotate, app.btnScale, app.btnWorldPaint, new Separator(),
            btnZoomIn, btnZoomOut, btnResetCam, btnFocusSelected, btnFrameAll,
            app.cameraPreviewToggle, new Separator(),
            new Label("Cena:"), app.sceneSelector, btnScenes, new Separator(),
            app.cameraPosLabel, app.cameraZoomLabel
        );
    }
}
