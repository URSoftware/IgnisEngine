package com.ignis.editor.fx;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/**
 * Construcao dos menus do editor (Fase F -- quebra do {@link IgnisEditorApp}): a
 * barra de menus principal e os menus de contexto da hierarquia e do viewport. E
 * uma camada fina: cada item liga um rotulo/atalho a uma acao do editor.
 *
 * <p>Recebe o {@link IgnisEditorApp} e chama suas acoes (abrir/salvar projeto,
 * criar entidade, undo/redo, dialogos...) via {@code app.}. Mesmo pacote, entao usa
 * os metodos de acao package-private sem API publica nova.</p>
 */
final class EditorMenuBuilder {

    private final IgnisEditorApp app;

    EditorMenuBuilder(IgnisEditorApp app) {
        this.app = app;
    }

    MenuBar buildMenuBar(Stage stage) {
        Menu file = new Menu("Arquivo");
        MenuItem novo = new MenuItem("Novo projeto…");
        novo.setOnAction(e -> app.newProject(stage));
        MenuItem open = new MenuItem("Abrir projeto…");
        open.setOnAction(e -> app.openProjectViaChooser(stage));
        app.recentMenu = new Menu("Abrir recente");
        app.rebuildRecentMenu(stage);
        MenuItem selecionar = new MenuItem("Selecionar projeto…");
        selecionar.setOnAction(e -> app.showProjectStartup(stage, false));
        MenuItem salvar = new MenuItem("Salvar");
        salvar.setOnAction(e -> app.saveProject());
        MenuItem salvarComo = new MenuItem("Salvar como…");
        salvarComo.setOnAction(e -> app.saveProjectAs(stage));
        MenuItem fechar = new MenuItem("Fechar projeto");
        fechar.setOnAction(e -> app.closeProject(stage));
        MenuItem prefs = new MenuItem("Configuracoes…");
        prefs.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN));
        prefs.setOnAction(e -> app.openSettings());
        MenuItem exit = new MenuItem("Sair");
        exit.setOnAction(e -> { app.saveLayout(); app.stopGameLoop(); stage.close(); Platform.exit(); System.exit(0); });
        file.getItems().addAll(novo, open, app.recentMenu, selecionar, new SeparatorMenuItem(),
                salvar, salvarComo, prefs, new SeparatorMenuItem(), fechar, exit);

        Menu tools = new Menu("Ferramentas");
        MenuItem miAudio = new MenuItem("Editor de Audio (DAW)");
        miAudio.setOnAction(e -> app.openAudioEditor());
        MenuItem miImage = new MenuItem("Editor de Imagens");
        miImage.setOnAction(e -> app.openImageEditor());
        MenuItem miAnim = new MenuItem("Editor de Animacao");
        miAnim.setOnAction(e -> app.openAnimationEditor());
        MenuItem miNotes = new MenuItem("Sistema de Notas");
        miNotes.setOnAction(e -> app.openNotes());
        MenuItem miCommunity = new MenuItem("Comunidade & Marketplace");
        miCommunity.setOnAction(e -> app.openCommunity());
        MenuItem miCode = new MenuItem("Editor de Codigo (Scripts)");
        miCode.setOnAction(e -> app.openCodeEditor());
        MenuItem miBuild = new MenuItem("Build do Projeto");
        miBuild.setOnAction(e -> app.openBuildDialog());
        tools.getItems().addAll(miAudio, miImage, miAnim, miNotes, miCommunity, miCode, miBuild);

        Menu view = new Menu("Visualizar");
        
        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
        zoomInItem.setOnAction(e -> app.zoomCamera(1.25));
        
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomOutItem.setOnAction(e -> app.zoomCamera(0.8));
        
        MenuItem zoom100Item = new MenuItem("Zoom to 100%");
        zoom100Item.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));
        zoom100Item.setOnAction(e -> {
            com.ignis.core.Camera cam = app.game.getViewCamera();
            if (cam != null) {
                cam.setZoom(1.0);
                app.updateCameraLabels();
            }
        });
        
        MenuItem resetCamItem = new MenuItem("Reset Camera");
        resetCamItem.setAccelerator(new KeyCodeCombination(KeyCode.HOME));
        resetCamItem.setOnAction(e -> app.resetCamera());
        
        MenuItem focusSelectedItem = new MenuItem("Focus on Selected");
        focusSelectedItem.setAccelerator(new KeyCodeCombination(KeyCode.F));
        focusSelectedItem.setOnAction(e -> app.focusCameraOnSelected());

        MenuItem frameAllItem = new MenuItem("Enquadrar Tudo");
        frameAllItem.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHIFT_DOWN));
        frameAllItem.setOnAction(e -> app.frameAllObjects());
        
        CheckMenuItem showCollidersItem = new CheckMenuItem("Show Colliders");
                showCollidersItem.setSelected(app.game.isShowColliders());
                showCollidersItem.setOnAction(e -> app.game.setShowColliders(showCollidersItem.isSelected()));

                CheckMenuItem showCameraBoundsItem = new CheckMenuItem("Mostrar Câmera (campo de visão)");
                showCameraBoundsItem.setSelected(app.game.isShowCameraBounds());
                showCameraBoundsItem.setOnAction(e -> app.game.setShowCameraBounds(showCameraBoundsItem.isSelected()));

                CheckMenuItem showGridItem = new CheckMenuItem("Show Grid");
                showGridItem.setSelected(app.game.isShowGrid());
                showGridItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
                showGridItem.setOnAction(e -> app.game.setShowGrid(showGridItem.isSelected()));

                CheckMenuItem snapToGridItem = new CheckMenuItem("Snap to Grid");
                snapToGridItem.setSelected(app.game.isSnapToGrid());
                snapToGridItem.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
                snapToGridItem.setOnAction(e -> app.game.setSnapToGrid(snapToGridItem.isSelected()));

                Menu gridSizeMenu = new Menu("Grid Size");
                ToggleGroup gridSizeGroup = new ToggleGroup();
                int[] gridSizes = {16, 32, 64, 128};
                for (int size : gridSizes) {
                    RadioMenuItem sizeItem = new RadioMenuItem(size + " px");
                    sizeItem.setToggleGroup(gridSizeGroup);
                    sizeItem.setSelected(app.game.getGridSize() == size);
                    sizeItem.setOnAction(e -> app.game.setGridSize(size));
                    gridSizeMenu.getItems().add(sizeItem);
                }

                // Item de alternancia do Console (dock inferior). Instanciado aqui —
                // antes so era declarado como campo e entrava null no menu, causando
                // NullPointerException ao renderizar a MenuBar no start.
                app.consoleMenuItem = new CheckMenuItem("Mostrar Console");
                app.consoleMenuItem.setSelected(EditorPrefs.isConsoleVisible());
                app.consoleMenuItem.setOnAction(e -> app.setConsoleVisible(app.consoleMenuItem.isSelected()));

                // Espelho do toggle da toolbar: ver a cena pela camera ativa do jogo.
                CheckMenuItem cameraPreviewItem = new CheckMenuItem("Ver pela Câmera do Jogo");
                cameraPreviewItem.setSelected(app.game.isCameraPreview());
                if (app.cameraPreviewToggle != null) {
                    cameraPreviewItem.selectedProperty()
                            .bindBidirectional(app.cameraPreviewToggle.selectedProperty());
                }
                cameraPreviewItem.setOnAction(e -> app.setCameraPreview(cameraPreviewItem.isSelected()));

                view.getItems().addAll(
                    zoomInItem, zoomOutItem, zoom100Item, new SeparatorMenuItem(),
                    resetCamItem, focusSelectedItem, frameAllItem, cameraPreviewItem, new SeparatorMenuItem(),
                    showGridItem, snapToGridItem, gridSizeMenu, new SeparatorMenuItem(),
                    showCollidersItem, showCameraBoundsItem, new SeparatorMenuItem(),
                    app.consoleMenuItem
                );

        Menu help = new Menu("Ajuda");
        MenuItem about = new MenuItem("Sobre");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "IgnisEngine — editor JavaFX (Fase 3 da migracao).").showAndWait());
        help.getItems().add(about);

        return new MenuBar(file, app.buildEditMenu(), app.buildSceneMenu(), tools, view, help);
    }

    ContextMenu buildHierarchyContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
        criarObjeto.setOnAction(e -> app.createEntity("GameObject"));
        MenuItem criarCamera = new MenuItem("Criar Câmera");
        criarCamera.setOnAction(e -> app.createEntity("Camera"));
        menu.getItems().addAll(criarObjeto, criarCamera, new SeparatorMenuItem());
        MenuItem dup = new MenuItem("Duplicar (Ctrl+D)");
        dup.setOnAction(e -> app.duplicateSelected());
        MenuItem ren = new MenuItem("Renomear… (F2)");
        ren.setOnAction(e -> app.renameSelected());
        MenuItem del = new MenuItem("Deletar (Delete)");
        del.setOnAction(e -> app.deleteSelected());
        MenuItem copyItem = new MenuItem("Copiar (Ctrl+C)");
        copyItem.setOnAction(e -> app.copySelected());
        MenuItem pasteItem = new MenuItem("Colar (Ctrl+V)");
        pasteItem.setOnAction(e -> app.pasteSelected());

        Menu ordenar = new Menu("Ordenar");
        MenuItem up = new MenuItem("Mover para cima");
        up.setOnAction(e -> app.moveSelected(-1));
        MenuItem down = new MenuItem("Mover para baixo");
        down.setOnAction(e -> app.moveSelected(1));
        MenuItem top = new MenuItem("Mover para o topo");
        top.setOnAction(e -> app.moveSelectedTo(Integer.MAX_VALUE));
        MenuItem bottom = new MenuItem("Mover para o fundo");
        bottom.setOnAction(e -> app.moveSelectedTo(0));
        ordenar.getItems().addAll(up, down, top, bottom);

        MenuItem savePrefab = new MenuItem("Salvar como Prefab…");
        savePrefab.setOnAction(e -> app.saveSelectedAsPrefab());
        MenuItem instPrefab = new MenuItem("Instanciar Prefab…");
        instPrefab.setOnAction(e -> app.instantiatePrefabDialog());

        menu.getItems().addAll(dup, ren, del,
                new SeparatorMenuItem(), copyItem, pasteItem, new SeparatorMenuItem(), ordenar,
                new SeparatorMenuItem(), savePrefab, instPrefab);
        return menu;
    }

    ContextMenu buildViewportContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
        criarObjeto.setOnAction(e -> app.createEntity("GameObject"));
        MenuItem criarCamera = new MenuItem("Criar Câmera");
        criarCamera.setOnAction(e -> app.createEntity("Camera"));
        menu.getItems().addAll(criarObjeto, criarCamera, new SeparatorMenuItem());

        MenuItem dup = new MenuItem("Duplicar (Ctrl+D)");
        dup.setOnAction(e -> app.duplicateSelected());

        MenuItem ren = new MenuItem("Renomear… (F2)");
        ren.setOnAction(e -> app.renameSelected());

        MenuItem del = new MenuItem("Deletar (Delete)");
        del.setOnAction(e -> app.deleteSelected());

        MenuItem copyItem = new MenuItem("Copiar (Ctrl+C)");
        copyItem.setOnAction(e -> app.copySelected());

        MenuItem pasteItem = new MenuItem("Colar (Ctrl+V)");
        pasteItem.setOnAction(e -> app.pasteSelected());

        MenuItem savePrefab = new MenuItem("Salvar como Prefab…");
        savePrefab.setOnAction(e -> app.saveSelectedAsPrefab());
        MenuItem instPrefab = new MenuItem("Instanciar Prefab…");
        instPrefab.setOnAction(e -> app.instantiatePrefabDialog());

        menu.getItems().addAll(dup, ren, del,
                new SeparatorMenuItem(), copyItem, pasteItem,
                new SeparatorMenuItem(), savePrefab, instPrefab);
        return menu;
    }
}
