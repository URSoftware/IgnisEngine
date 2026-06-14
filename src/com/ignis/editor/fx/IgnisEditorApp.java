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
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
        root.setTop(buildMenuBar(stage));

        // ---- Viewport central ----
        Pane viewportPane = new Pane();
        Canvas canvas = new Canvas();
        canvas.widthProperty().bind(viewportPane.widthProperty());
        canvas.heightProperty().bind(viewportPane.heightProperty());
        viewportPane.getChildren().add(canvas);

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
        stage.setTitle("IgnisEngine — Editor (JavaFX) [migracao]");
        stage.setScene(scene);
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

        Menu view = new Menu("Visualizar");
        view.getItems().add(new MenuItem("Viewport"));

        Menu help = new Menu("Ajuda");
        MenuItem about = new MenuItem("Sobre");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "IgnisEngine — editor JavaFX (Fase 2 da migracao).").showAndWait());
        help.getItems().add(about);

        return new MenuBar(file, view, help);
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
