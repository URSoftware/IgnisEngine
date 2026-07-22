package com.ignis.editor.fx;

import com.ignis.core.GameObject;
import com.ignis.core.Scene;
import com.ignis.core.World;
import org.json.JSONArray;
import org.json.JSONObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Organizador de cenarios do editor (Fase F, passo 9 — extraido do
 * IgnisEditorApp): seletor de cena da toolbar, CRUD de cenas (criar/renomear/
 * duplicar/deletar/cena inicial), troca de cena do editor e em Play (via
 * SceneLoader de scripts) e o dialogo "Gerenciar Cenarios" com a secao de
 * Mundo. Mesmo padrao dos demais builders: classe auxiliar no mesmo pacote
 * operando sobre os membros package-private do editor.
 */
final class EditorSceneOrganizer {

    private final IgnisEditorApp app;

    // Evita que a repopulacao programatica do seletor dispare o handler de troca.
    private boolean updatingSceneSelector = false;
    private boolean runtimeSceneChanged = false;

    EditorSceneOrganizer(IgnisEditorApp app) {
        this.app = app;
    }


    // (Re)popula o seletor de cena da toolbar a partir do projeto, marcando a cena
    // inicial com ⭐. Programatico: nao dispara a troca de cena (updatingSceneSelector).
    void refreshSceneSelector() {
        if (app.sceneSelector == null) return;
        updatingSceneSelector = true;
        try {
            app.sceneSelector.getItems().clear();
            if (app.currentProject == null) {
                app.sceneSelector.setDisable(true);
                return;
            }
            app.sceneSelector.setDisable(false);
            String start = app.currentProject.getMainScene();
            String currentLabel = null;
            for (Scene s : app.currentProject.getScenes()) {
                String label = sceneLabel(s, start);
                app.sceneSelector.getItems().add(label);
                if (s == app.currentProject.getCurrentScene()) currentLabel = label;
            }
            if (currentLabel != null) app.sceneSelector.setValue(currentLabel);
        } finally {
            updatingSceneSelector = false;
        }
    }

    // Rotulo de cena: nome + prefixo ⭐ quando e a cena inicial (mainScene).
    private String sceneLabel(Scene s, String startSceneName) {
        boolean isStart = s.getSceneName().equals(startSceneName);
        return (isStart ? "⭐ " : "") + s.getSceneName();
    }

    private String sceneNameFromLabel(String label) {
        if (label == null) return null;
        return label.startsWith("⭐ ") ? label.substring(2) : label;
    }

    void onSceneSelectorChanged() {
        if (updatingSceneSelector || app.currentProject == null || app.sceneSelector == null) return;
        String label = app.sceneSelector.getValue();
        if (label == null) return;
        Scene target = app.currentProject.getSceneByName(sceneNameFromLabel(label));
        if (target != null && target != app.currentProject.getCurrentScene()) {
            switchEditorToScene(target);
        }
    }

    // Carrega uma cena no app.game vivo (limpa entidades/cameras e recarrega scripts).
    private void loadSceneIntoGame(Scene target) {
        app.setSelected(null);
        app.clearSecondarySelection();
        app.game.clearEntities();
        app.clearGameCameras();
        if (target != null) {
            for (GameObject e : target.getEntities()) {
                e.setGame(app.game);
                app.addEntityTracked(e);
            }
            app.game.setWorld(target.getWorld());
        }
        try {
            if (app.game.getScriptManager() != null) app.reloadAllScriptInstances();
        } catch (Exception ignore) { /* scripts opcionais */ }
        app.refreshHierarchy();
    }

    void beginPlaySession() {
        runtimeSceneChanged = false;
    }

    boolean restoreEditingSceneAfterPlay() {
        if (!runtimeSceneChanged) return false;
        runtimeSceneChanged = false;
        Scene editingScene = app.currentProject != null ? app.currentProject.getCurrentScene() : null;
        loadSceneIntoGame(editingScene);
        refreshSceneSelector();
        return true;
    }

    // Troca a cena ativa do editor: persiste a cena atual no projeto e carrega a nova.
    private void switchEditorToScene(Scene target) {
        if (app.currentProject == null || target == null) return;
        if (target == app.currentProject.getCurrentScene()) return;
        if (app.playing) {
            app.setStatus("Pare o Play (Stop) antes de trocar de cena.");
            refreshSceneSelector(); // reverte o seletor para a cena atual
            return;
        }
        app.syncEntitiesToScene();                    // app.game -> cena atual
        app.currentProject.setCurrentScene(target);
        loadSceneIntoGame(target);                // cena nova -> app.game
        refreshSceneSelector();
        app.markProjectDirty();
        app.setStatus("Cena ativa: " + target.getSceneName());
    }

    // SceneLoader para scripts (IgnisScript.loadScene). Fora do Play delega para a
    // troca normal do editor; durante o Play carrega uma cópia fresca da cena de
    // destino no app.game vivo (para não corromper a cena salva com o estado de runtime).
    boolean onScriptSceneChange(String sceneName) {
        if (app.currentProject == null || sceneName == null) return false;
        Scene target = app.currentProject.getSceneByName(sceneName);
        if (target == null) return false;
        if (app.playing && !Platform.isFxApplicationThread()) {
            runtimeSceneChanged = true;
            Platform.runLater(() -> {
                if (app.playing) onScriptSceneChange(sceneName);
            });
            return true;
        }
        if (app.playing) {
            Scene copy = Scene.fromJSON(target.toJSON(), app.game);
            app.setSelected(null);
            app.clearSecondarySelection();
            // Scene transitions must also discard the previous scene dispatcher.
            // Clearing only the entity list leaves signal receivers from retired
            // scripts alive, so load/save/cutscene events are handled repeatedly.
            app.game.clearEntities();
            app.clearGameCameras();
            for (GameObject e : copy.getEntities()) {
                e.setGame(app.game);
                app.addEntityTracked(e);
            }
            app.game.setWorld(copy.getWorld());
            app.game.refreshColliders();
            // Reinicia os scripts da nova cena (start()), como numa transição real.
            try {
                if (app.game.getScriptManager() != null) app.reloadAllScriptInstances();
            } catch (Exception ignore) { /* scripts opcionais */ }
            runtimeSceneChanged = true;
            Platform.runLater(app::refreshHierarchy);
        } else {
            Platform.runLater(() -> switchEditorToScene(target));
        }
        return true;
    }

    // Cria uma nova cena vazia e a torna ativa.
    void createNewScene() {
        if (!app.requireProject()) return;
        String name = promptSceneName("Nova cena", "Cena" + (app.currentProject.getScenes().size() + 1), null);
        if (name == null) return;
        Scene s = new Scene(name);
        app.currentProject.addScene(s);
        switchEditorToScene(s);
        app.setStatus("Cena criada: " + name);
    }

    private void renameScene(Scene s) {
        if (s == null || app.currentProject == null) return;
        String name = promptSceneName("Renomear cena", s.getSceneName(), s);
        if (name == null) return;
        boolean wasStart = s.getSceneName().equals(app.currentProject.getMainScene());
        s.setSceneName(name);
        if (wasStart) app.currentProject.setMainScene(name);
        refreshSceneSelector();
        app.markProjectDirty();
    }

    private void duplicateScene(Scene s) {
        if (s == null || app.currentProject == null) return;
        if (s == app.currentProject.getCurrentScene()) app.syncEntitiesToScene();
        Scene copy = Scene.fromJSON(s.toJSON(), app.game);
        copy.setSceneName(uniqueSceneName(s.getSceneName() + " (cópia)"));
        app.currentProject.addScene(copy);
        refreshSceneSelector();
        app.markProjectDirty();
        app.setStatus("Cena duplicada: " + copy.getSceneName());
    }

    private void deleteScene(Scene s) {
        if (s == null || app.currentProject == null) return;
        if (app.currentProject.getScenes().size() <= 1) {
            app.setStatus("Não é possível remover a única cena do projeto.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remover a cena \"" + s.getSceneName() + "\"? Esta ação não pode ser desfeita.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        boolean isCurrent = (s == app.currentProject.getCurrentScene());
        boolean isStart = s.getSceneName().equals(app.currentProject.getMainScene());
        app.currentProject.removeScene(s); // troca currentScene se necessario
        if (isStart && app.currentProject.getCurrentScene() != null) {
            app.currentProject.setMainScene(app.currentProject.getCurrentScene().getSceneName());
        }
        if (isCurrent) {
            loadSceneIntoGame(app.currentProject.getCurrentScene());
        }
        refreshSceneSelector();
        app.markProjectDirty();
        app.setStatus("Cena removida: " + s.getSceneName());
    }

    private void setStartScene(Scene s) {
        if (s == null || app.currentProject == null) return;
        app.currentProject.setMainScene(s.getSceneName());
        refreshSceneSelector();
        app.markProjectDirty();
        app.setStatus("Cena inicial definida: " + s.getSceneName());
    }

    // Cria/garante um World para a cena com limites default (1920x1080 centrado na origem).
    void createWorldForScene(Scene s) {
        if (s == null) return;
        World w = s.getWorld();
        if (w == null) {
            w = new World(s.getSceneName() + " World");
            s.setWorld(w);
        }
        if (!w.hasBounds()) {
            w.setBounds(-960, -540, 960, 540);
        }
        if (s == app.currentProject.getCurrentScene()) app.game.setWorld(w);
        app.markProjectDirty();
        app.setStatus("Mundo criado para a cena: " + s.getSceneName());
    }

    // Dialogo de nome de cena com validacao de unicidade. 'editing' e a cena sendo
    // renomeada (para permitir manter o proprio nome); null ao criar.
    private String promptSceneName(String title, String suggested, Scene editing) {
        while (true) {
            TextInputDialog dlg = new TextInputDialog(suggested);
            dlg.setTitle(title);
            dlg.setHeaderText(null);
            dlg.setContentText("Nome da cena:");
            java.util.Optional<String> opt = dlg.showAndWait();
            if (opt.isEmpty()) return null;
            String name = opt.get().trim();
            if (name.isEmpty()) return null;
            Scene existing = app.currentProject.getSceneByName(name);
            if (existing != null && existing != editing) {
                new Alert(Alert.AlertType.WARNING, "Já existe uma cena com esse nome.").showAndWait();
                suggested = name;
                continue;
            }
            return name;
        }
    }

    // Gera um nome unico a partir de uma base (base, base 2, base 3, ...).
    private String uniqueSceneName(String base) {
        if (app.currentProject.getSceneByName(base) == null) return base;
        int i = 2;
        while (app.currentProject.getSceneByName(base + " " + i) != null) i++;
        return base + " " + i;
    }

    // ------------------------------------------------------------------
    // Ponte para o MCP (com.ignis.mcp.SceneHost, injetada pelo IgnisEditorApp).
    // Reaproveita switchEditorToScene/createNewScene ja testados pelo menu
    // "Cenarios..." — nenhuma logica nova de troca/criacao de cena vive aqui.
    // ------------------------------------------------------------------

    /** Nomes das cenas do projeto, com marcadores [inicial]/[ativa]. Vazio se nao houver projeto. */
    java.util.List<String> listSceneNamesForMcp() {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (app.currentProject == null) return result;
        String start = app.currentProject.getMainScene();
        Scene current = app.currentProject.getCurrentScene();
        for (Scene s : app.currentProject.getScenes()) {
            StringBuilder sb = new StringBuilder(s.getSceneName());
            if (s.getSceneName().equals(start)) sb.append(" [inicial]");
            if (s == current) sb.append(" [ativa]");
            result.add(sb.toString());
        }
        return result;
    }

    /** Cria uma cena vazia com o nome dado e a torna ativa. Retorna null em sucesso, ou o erro. */
    String createSceneForMcp(String sceneName) {
        if (app.currentProject == null) return "Nenhum projeto aberto.";
        if (sceneName == null || sceneName.isBlank()) return "Nome de cena obrigatorio.";
        if (app.currentProject.getSceneByName(sceneName) != null) {
            return "Ja existe uma cena com esse nome: " + sceneName;
        }
        Scene s = new Scene(sceneName);
        app.currentProject.addScene(s);
        switchEditorToScene(s);
        app.setStatus("Cena criada (MCP): " + sceneName);
        return null;
    }

    /** Troca a cena ativa do editor pelo nome. Retorna null em sucesso, ou o erro. */
    String switchSceneForMcp(String sceneName) {
        if (app.currentProject == null) return "Nenhum projeto aberto.";
        Scene target = app.currentProject.getSceneByName(sceneName);
        if (target == null) return "Cena nao encontrada: " + sceneName;
        if (target == app.currentProject.getCurrentScene()) return null; // ja e a atual
        if (app.playing) return "Pare o Play (Stop) antes de trocar de cena.";
        switchEditorToScene(target);
        return null;
    }

    /**
     * Copia um objeto nomeado da cena ATIVA (game vivo) para outra cena do projeto.
     * Por serializacao/desserializacao via {@link Scene#toJSON()}/{@link Scene#fromJSON}
     * (as MESMAS rotinas ja usadas por duplicateScene) — nenhuma logica nova de
     * clonagem de GameObject. O objeto original permanece intacto na cena de origem.
     */
    String copyObjectToSceneForMcp(String objectName, String targetSceneName, String newName) {
        if (app.currentProject == null) return "Nenhum projeto aberto.";
        if (objectName == null || objectName.isBlank()) return "Nome do objeto obrigatorio.";
        Scene target = app.currentProject.getSceneByName(targetSceneName);
        if (target == null) return "Cena de destino nao encontrada: " + targetSceneName;
        if (target == app.currentProject.getCurrentScene()) {
            return "A cena de destino e a propria cena ativa.";
        }
        GameObject source = null;
        for (GameObject e : app.game.getEntities()) {
            if (objectName.equals(e.getName())) { source = e; break; }
        }
        if (source == null) return "Objeto nao encontrado na cena ativa: " + objectName;

        // Serializa SO este objeto usando uma cena descartavel como envelope (reaproveita
        // Scene.toJSON inalterado em vez de duplicar a logica de serializacao por campo).
        Scene sourceWrapper = new Scene("temp-source");
        sourceWrapper.addEntity(source);
        JSONObject entityJson = sourceWrapper.toJSON().getJSONArray("entities").getJSONObject(0);

        JSONObject copyWrapperJson = new JSONObject()
                .put("sceneName", "temp-copy")
                .put("entities", new JSONArray().put(entityJson));
        Scene decoded = Scene.fromJSON(copyWrapperJson, app.game);
        GameObject copy = decoded.getEntities().get(0);
        copy.setId(java.util.UUID.randomUUID().toString()); // id novo: independente do original
        if (newName != null && !newName.isBlank()) copy.setName(newName);
        target.addEntity(copy);
        app.markProjectDirty();
        app.setStatus("Objeto '" + source.getName() + "' copiado para a cena: " + targetSceneName);
        return null;
    }

    // Abre o gerenciador de cenários (organizar cenas + cena inicial + mundos).
    void openSceneManager() {
        if (!app.requireProject()) return;
        Stage dialog = new Stage();
        dialog.initOwner(app.primaryStage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("Gerenciar Cenários");

        ListView<String> list = new ListView<>();
        Runnable reload = () -> {
            String prev = list.getSelectionModel().getSelectedItem();
            list.getItems().clear();
            for (Scene s : app.currentProject.getScenes()) {
                list.getItems().add(sceneLabel(s, app.currentProject.getMainScene()));
            }
            if (prev != null && list.getItems().contains(prev)) {
                list.getSelectionModel().select(prev);
            } else if (!list.getItems().isEmpty()) {
                list.getSelectionModel().select(0);
            }
        };
        reload.run();
        list.setPrefHeight(220);

        java.util.function.Supplier<Scene> selected = () -> {
            String label = list.getSelectionModel().getSelectedItem();
            return label == null ? null : app.currentProject.getSceneByName(sceneNameFromLabel(label));
        };

        Button btnNova = new Button("Nova…");
        btnNova.setOnAction(e -> { createNewScene(); reload.run(); });
        Button btnAtivar = new Button("Ativar");
        btnAtivar.setOnAction(e -> { Scene s = selected.get(); if (s != null) { switchEditorToScene(s); reload.run(); } });
        Button btnRenomear = new Button("Renomear…");
        btnRenomear.setOnAction(e -> { Scene s = selected.get(); if (s != null) { renameScene(s); reload.run(); } });
        Button btnDuplicar = new Button("Duplicar");
        btnDuplicar.setOnAction(e -> { Scene s = selected.get(); if (s != null) { duplicateScene(s); reload.run(); } });
        Button btnInicial = new Button("Definir como inicial ⭐");
        btnInicial.setOnAction(e -> { Scene s = selected.get(); if (s != null) { setStartScene(s); reload.run(); } });
        Button btnDeletar = new Button("Deletar");
        btnDeletar.setOnAction(e -> { Scene s = selected.get(); if (s != null) { deleteScene(s); reload.run(); } });

        VBox sceneButtons = new VBox(6, btnNova, btnAtivar, btnRenomear, btnDuplicar, btnInicial, btnDeletar);
        sceneButtons.setMinWidth(180);

        // --- Seção de Mundo da cena selecionada ---
        Label worldTitle = new Label("Mundo da cena");
        worldTitle.getStyleClass().add("panel-title");
        Label worldStatus = new Label();
        worldStatus.setWrapText(true);
        TextField minXf = new TextField();
        TextField minYf = new TextField();
        TextField maxXf = new TextField();
        TextField maxYf = new TextField();
        for (TextField tf : new TextField[]{minXf, minYf, maxXf, maxYf}) tf.setPrefColumnCount(6);
        GridPane worldGrid = new GridPane();
        worldGrid.setHgap(6); worldGrid.setVgap(4);
        worldGrid.addRow(0, new Label("Min X"), minXf, new Label("Min Y"), minYf);
        worldGrid.addRow(1, new Label("Max X"), maxXf, new Label("Max Y"), maxYf);

        Button btnCriarMundo = new Button("Criar Mundo");
        Button btnAplicarMundo = new Button("Aplicar limites");
        Button btnRemoverMundo = new Button("Remover Mundo");

        Runnable refreshWorld = () -> {
            Scene s = selected.get();
            World w = (s != null) ? s.getWorld() : null;
            boolean hasWorld = (w != null);
            boolean hasBounds = hasWorld && w.hasBounds();
            worldStatus.setText(s == null ? "Selecione uma cena."
                    : hasWorld ? ("Mundo: " + w.getName() + (hasBounds ? "" : " (sem limites)"))
                    : "Esta cena não tem mundo.");
            btnCriarMundo.setDisable(s == null || hasWorld);
            btnAplicarMundo.setDisable(!hasBounds);
            btnRemoverMundo.setDisable(!hasWorld);
            minXf.setDisable(!hasBounds); minYf.setDisable(!hasBounds);
            maxXf.setDisable(!hasBounds); maxYf.setDisable(!hasBounds);
            if (hasBounds) {
                minXf.setText(app.fmt(w.getMinX())); minYf.setText(app.fmt(w.getMinY()));
                maxXf.setText(app.fmt(w.getMaxX())); maxYf.setText(app.fmt(w.getMaxY()));
            } else {
                minXf.clear(); minYf.clear(); maxXf.clear(); maxYf.clear();
            }
        };
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshWorld.run());

        btnCriarMundo.setOnAction(e -> { Scene s = selected.get(); if (s != null) { createWorldForScene(s); refreshWorld.run(); } });
        btnAplicarMundo.setOnAction(e -> {
            Scene s = selected.get();
            World w = (s != null) ? s.getWorld() : null;
            if (w == null) return;
            try {
                final World world = w;
                final double[] antes = { world.getMinX(), world.getMinY(), world.getMaxX(), world.getMaxY() };
                final double[] depois = { Double.parseDouble(minXf.getText()), Double.parseDouble(minYf.getText()),
                        Double.parseDouble(maxXf.getText()), Double.parseDouble(maxYf.getText()) };
                world.setBounds(depois[0], depois[1], depois[2], depois[3]);
                if (s == app.currentProject.getCurrentScene()) app.game.setWorld(world);
                app.undoManager.push("Limites do mundo",
                        () -> { world.setBounds(antes[0], antes[1], antes[2], antes[3]); app.game.repaint(); },
                        () -> { world.setBounds(depois[0], depois[1], depois[2], depois[3]); app.game.repaint(); });
                app.markProjectDirty();
                app.setStatus("Limites do mundo atualizados.");
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.WARNING, "Valores de limite inválidos.").showAndWait();
            }
        });
        btnRemoverMundo.setOnAction(e -> {
            Scene s = selected.get();
            if (s == null) return;
            s.setWorld(null);
            if (s == app.currentProject.getCurrentScene()) app.game.setWorld(null);
            app.markProjectDirty();
            refreshWorld.run();
            app.setStatus("Mundo removido da cena.");
        });
        refreshWorld.run();

        VBox worldBox = new VBox(6, worldTitle, worldStatus, worldGrid,
                new HBox(6, btnCriarMundo, btnAplicarMundo, btnRemoverMundo));
        worldBox.setMinWidth(320);

        Button btnFechar = new Button("Fechar");
        btnFechar.setOnAction(e -> dialog.close());

        HBox body = new HBox(12, list, sceneButtons, new Separator(javafx.geometry.Orientation.VERTICAL), worldBox);
        VBox rootBox = new VBox(10, body, btnFechar);
        rootBox.setPadding(new Insets(12));
        javafx.scene.Scene dlgScene = new javafx.scene.Scene(rootBox);
        FxTheme.apply(dlgScene);
        dialog.setScene(dlgScene);
        dialog.showAndWait();
        refreshSceneSelector();
    }
}
