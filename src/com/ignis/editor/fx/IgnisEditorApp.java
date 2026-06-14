package com.ignis.editor.fx;

import com.ignis.core.Circle;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.Square;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Editor IgnisEngine em JavaFX — Fase 1 da migracao (ver doc/JAVAFX_MIGRATION_PLAN.md).
 *
 * <p>Esta e a CASCA JavaFX (BorderPane + MenuBar + SplitPane) com a <b>ponte de
 * render</b> do viewport: a engine ({@link Game}) desenha o mundo num
 * {@link BufferedImage} offscreen via {@code Game.renderWorldTo(...)} e o frame e
 * convertido com {@link SwingFXUtils} e pintado num {@link Canvas} JavaFX por um
 * {@link AnimationTimer}. Nao depende de BufferStrategy/AWT Canvas em tela.
 *
 * <p>O editor Swing classico ({@code com.ignis.editor.Editor}) permanece intacto;
 * esta versao roda separadamente com {@code mvnw javafx:run}. Hierarchy ja e nativa
 * (TreeView); Inspector e demais paineis serao migrados nas fases seguintes.
 */
public class IgnisEditorApp extends Application {

    private final Game game = new Game();

    @Override
    public void start(Stage stage) {
        seedSampleScene();

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));

        // ---- Viewport central (Canvas JavaFX alimentado pela ponte de render) ----
        Pane viewportPane = new Pane();
        Canvas canvas = new Canvas();
        canvas.widthProperty().bind(viewportPane.widthProperty());
        canvas.heightProperty().bind(viewportPane.heightProperty());
        viewportPane.getChildren().add(canvas);

        // ---- Hierarchy (esquerda) — nativa JavaFX ----
        TreeView<String> hierarchy = buildHierarchy();

        // ---- Inspector (direita) — placeholder; migra na Fase 2 ----
        VBox inspector = new VBox(8);
        inspector.setStyle("-fx-padding: 12; -fx-background-color: #2d2d2d;");
        Label inspTitle = new Label("Inspector");
        inspTitle.setStyle("-fx-text-fill: #2e8b57; -fx-font-weight: bold;");
        Label inspBody = new Label("Em migracao (Fase 2).\nSelecione objetos na Hierarchy.");
        inspBody.setStyle("-fx-text-fill: #b0b0b0;");
        inspector.getChildren().addAll(inspTitle, inspBody);

        SplitPane split = new SplitPane();
        split.getItems().addAll(hierarchy, viewportPane, inspector);
        split.setDividerPositions(0.2, 0.8);
        root.setCenter(split);

        Label status = new Label(" Editor JavaFX (Fase 1) — ponte de render ativa");
        status.setStyle("-fx-text-fill: #b0b0b0; -fx-padding: 4 10; -fx-background-color: #2d2d2d;");
        root.setBottom(status);

        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("IgnisEngine — Editor (JavaFX) [migracao]");
        stage.setScene(scene);
        stage.show();

        startRenderBridge(canvas);
    }

    /** Cena de amostra apenas para validar a ponte de render (Fase 1). */
    private void seedSampleScene() {
        game.addEntity(new Square("Quadrado", game, 0, 0, 90, 90));
        game.addEntity(new Circle("Circulo", game, 140, 60, 80, 80));
        game.addEntity(new Square("Quadrado2", game, -120, 90, 70, 70));
    }

    private MenuBar buildMenuBar(Stage stage) {
        Menu file = new Menu("Arquivo");
        MenuItem exit = new MenuItem("Sair");
        exit.setOnAction(e -> {
            stage.close();
            Platform.exit();
        });
        file.getItems().add(exit);

        Menu view = new Menu("Visualizar");
        view.getItems().add(new MenuItem("Viewport"));

        Menu help = new Menu("Ajuda");
        MenuItem about = new MenuItem("Sobre");
        about.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "IgnisEngine — editor JavaFX (Fase 1 da migracao).").showAndWait());
        help.getItems().add(about);

        return new MenuBar(file, view, help);
    }

    private TreeView<String> buildHierarchy() {
        TreeItem<String> root = new TreeItem<>("Cena");
        for (GameObject go : game.getEntities()) {
            root.getChildren().add(new TreeItem<>(go.getName()));
        }
        root.setExpanded(true);
        return new TreeView<>(root);
    }

    /**
     * Ponte de render: a cada frame, a engine desenha em um BufferedImage offscreen
     * e o resultado e copiado para o Canvas JavaFX (SwingFXUtils).
     */
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
                    game.renderWorldTo(g2d, w, h);
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
