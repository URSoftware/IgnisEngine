package com.ignis.editor.fx;

import com.ignis.builder.BuildConfig;
import com.ignis.builder.BuildLogger;
import com.ignis.builder.BuildResult;
import com.ignis.builder.BuildTarget;
import com.ignis.builder.Builder;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Build do projeto em JavaFX — Fase 3 (passo 1) da migracao.
 * Reescreve {@code com.ignis.editor.BuildDialog} (Swing) em JavaFX, reusando a mesma
 * API do {@link Builder}/{@link BuildConfig}/{@link BuildTarget}. A logica de build
 * nao muda; so a camada visual.
 */
public class FxBuildDialog extends Stage {

    private final File ignisFile;
    private final BuildConfig config;

    private final TextField nameField;
    private final TextField versionField;
    private final Spinner<Integer> widthSpinner;
    private final Spinner<Integer> heightSpinner;
    private final CheckBox fullscreenCheck;
    private final Map<BuildTarget, CheckBox> targetChecks = new EnumMap<>(BuildTarget.class);
    private final TextArea logArea = new TextArea();
    private final Button buildButton = new Button("Build");
    private final Button openOutputButton = new Button("Abrir pasta de saida");

    private File lastOutputDir;

    public FxBuildDialog(File ignisFile, String defaultGameName) {
        this.ignisFile = ignisFile;
        this.config = BuildConfig.load(ignisFile.getParentFile());
        if (config.getGameName() == null || config.getGameName().equals("IgnisGame")) {
            config.setGameName(defaultGameName);
        }
        setTitle("Build do Projeto");

        // ---- Configuracoes ----
        GridPane settings = new GridPane();
        settings.setHgap(6);
        settings.setVgap(6);
        settings.setPadding(new Insets(8));
        nameField = new TextField(config.getGameName());
        versionField = new TextField(config.getVersion());
        widthSpinner = new Spinner<>(320, 7680, config.getWidth(), 10);
        heightSpinner = new Spinner<>(240, 4320, config.getHeight(), 10);
        widthSpinner.setEditable(true);
        heightSpinner.setEditable(true);
        fullscreenCheck = new CheckBox();
        fullscreenCheck.setSelected(config.isFullscreen());
        int r = 0;
        settings.addRow(r++, new Label("Nome do jogo:"), nameField);
        settings.addRow(r++, new Label("Versao:"), versionField);
        settings.addRow(r++, new Label("Largura:"), widthSpinner);
        settings.addRow(r++, new Label("Altura:"), heightSpinner);
        settings.addRow(r++, new Label("Tela cheia:"), fullscreenCheck);
        TitledPane settingsPane = new TitledPane("Configuracoes do jogo", settings);
        settingsPane.setCollapsible(false);

        // ---- Plataformas ----
        VBox targets = new VBox(4);
        targets.setPadding(new Insets(8));
        targets.getChildren().add(new Label("Java (distribuicao JVM):"));
        for (BuildTarget t : BuildTarget.values()) {
            if (t.getStrategy() == BuildTarget.Strategy.JAVA) targets.getChildren().add(targetCheck(t));
        }
        targets.getChildren().add(new Separator());
        targets.getChildren().add(new Label("C++ nativo (consoles):"));
        for (BuildTarget t : BuildTarget.values()) {
            if (t.getStrategy() == BuildTarget.Strategy.CPP) targets.getChildren().add(targetCheck(t));
        }
        TitledPane targetsPane = new TitledPane("Plataformas", targets);
        targetsPane.setCollapsible(false);

        HBox north = new HBox(8, settingsPane, targetsPane);
        north.setPadding(new Insets(8));

        // ---- Log ----
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace;");
        logArea.setPrefRowCount(14);
        TitledPane logPane = new TitledPane("Log de build", logArea);
        logPane.setCollapsible(false);

        // ---- Botoes ----
        openOutputButton.setDisable(true);
        openOutputButton.setOnAction(e -> openOutputFolder());
        buildButton.setOnAction(e -> startBuild());
        HBox buttons = new HBox(8, openOutputButton, buildButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setTop(north);
        root.setCenter(logPane);
        root.setBottom(buttons);

        setScene(new Scene(root, 780, 580));
    }

    private CheckBox targetCheck(BuildTarget target) {
        String label = target.getDisplayName() + (target.isEnabled() ? "" : " (planejado)");
        CheckBox cb = new CheckBox(label);
        cb.setSelected(config.getTargets().contains(target));
        cb.setDisable(!target.isEnabled());
        targetChecks.put(target, cb);
        return cb;
    }

    private void applyToConfig() {
        config.setGameName(nameField.getText().trim());
        config.setVersion(versionField.getText().trim());
        config.setWidth(widthSpinner.getValue());
        config.setHeight(heightSpinner.getValue());
        config.setFullscreen(fullscreenCheck.isSelected());
        List<BuildTarget> selected = new ArrayList<>();
        for (Map.Entry<BuildTarget, CheckBox> e : targetChecks.entrySet()) {
            if (e.getValue().isSelected()) selected.add(e.getKey());
        }
        config.setTargets(selected);
    }

    private void startBuild() {
        applyToConfig();
        if (config.getTargets().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Selecione ao menos uma plataforma.").showAndWait();
            return;
        }
        try {
            config.save(ignisFile.getParentFile());
        } catch (Exception e) {
            appendLog("[WARN] Nao salvou build.json: " + e.getMessage());
        }

        buildButton.setDisable(true);
        openOutputButton.setDisable(true);
        logArea.clear();

        BuildLogger logger = message -> Platform.runLater(() -> appendLog(message));
        Thread worker = new Thread(() -> {
            List<BuildResult> results = new Builder().build(ignisFile, config, logger);
            Platform.runLater(() -> onBuildFinished(results));
        }, "IgnisBuilder");
        worker.setDaemon(true);
        worker.start();
    }

    private void onBuildFinished(List<BuildResult> results) {
        buildButton.setDisable(false);
        appendLog("");
        for (BuildResult result : results) {
            appendLog(result.toString());
            if (result.isSuccess() && result.getOutputDir() != null) {
                lastOutputDir = result.getOutputDir().getParentFile();
            }
        }
        openOutputButton.setDisable(lastOutputDir == null);
    }

    private void openOutputFolder() {
        try {
            File dir = lastOutputDir != null
                    ? lastOutputDir
                    : new File(ignisFile.getParentFile(), config.getOutputDirName());
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Nao foi possivel abrir a pasta: " + e.getMessage()).showAndWait();
        }
    }

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
    }
}
