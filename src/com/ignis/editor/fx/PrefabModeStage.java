package com.ignis.editor.fx;

import com.ignis.core.GameObject;
import com.ignis.core.PrefabManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;

/**
 * Isolated editor window (Prefab Mode) for editing Prefab assets.
 */
public class PrefabModeStage extends Stage {

    private final String prefabName;
    private final PrefabManager prefabManager;
    private final Runnable onSavedCallback;

    private TextField nameField;
    private TextField spritePathField;
    private Spinner<Double> xSpinner;
    private Spinner<Double> ySpinner;
    private Spinner<Double> rotSpinner;
    private Spinner<Integer> widthSpinner;
    private Spinner<Integer> heightSpinner;

    public PrefabModeStage(String prefabName, PrefabManager prefabManager, Runnable onSavedCallback) {
        this.prefabName = prefabName;
        this.prefabManager = prefabManager;
        this.onSavedCallback = onSavedCallback;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Prefab Mode - " + prefabName);

        initUI();
        loadPrefabData();
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        VBox form = new VBox(10);
        form.setPadding(new Insets(10));

        Label header = new Label("Edição Isolada de Prefab: " + prefabName);
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        nameField = new TextField();
        spritePathField = new TextField();
        xSpinner = new Spinner<>(-10000.0, 10000.0, 0.0, 1.0);
        ySpinner = new Spinner<>(-10000.0, 10000.0, 0.0, 1.0);
        rotSpinner = new Spinner<>(0.0, 360.0, 0.0, 5.0);
        widthSpinner = new Spinner<>(1, 4096, 32, 1);
        heightSpinner = new Spinner<>(1, 4096, 32, 1);

        xSpinner.setEditable(true);
        ySpinner.setEditable(true);
        rotSpinner.setEditable(true);

        form.getChildren().addAll(
                header,
                new Separator(),
                createFormRow("Nome Base:", nameField),
                createFormRow("Caminho do Sprite:", spritePathField),
                createFormRow("Posição X Padrao:", xSpinner),
                createFormRow("Posição Y Padrao:", ySpinner),
                createFormRow("Rotação Padrao:", rotSpinner),
                createFormRow("Largura:", widthSpinner),
                createFormRow("Altura:", heightSpinner)
        );

        root.setCenter(form);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        Button saveBtn = new Button("Salvar e Propagar");
        saveBtn.setStyle("-fx-base: #2e7d32; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> handleSave());

        Button cancelBtn = new Button("Fechar");
        cancelBtn.setOnAction(e -> close());

        buttons.getChildren().addAll(saveBtn, cancelBtn);
        root.setBottom(buttons);

        Scene scene = new Scene(root, 450, 420);
        setScene(scene);
    }

    private HBox createFormRow(String labelText, Control field) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(labelText);
        label.setPrefWidth(140);
        field.setPrefWidth(240);
        row.getChildren().addAll(label, field);
        return row;
    }

    private void loadPrefabData() {
        if (prefabManager == null) return;
        GameObject template = prefabManager.instantiatePrefab(prefabName);
        if (template != null) {
            nameField.setText(template.getName());
            if (template.getSpritePath() != null) {
                spritePathField.setText(template.getSpritePath());
            }
            xSpinner.getValueFactory().setValue(template.getX());
            ySpinner.getValueFactory().setValue(template.getY());
            rotSpinner.getValueFactory().setValue(template.getRotation());
            widthSpinner.getValueFactory().setValue(template.getWidth());
            heightSpinner.getValueFactory().setValue(template.getHeight());
        }
    }

    private void handleSave() {
        if (prefabManager == null) return;
        GameObject dummy = new GameObject(nameField.getText().trim(), null,
                xSpinner.getValue(), ySpinner.getValue(),
                widthSpinner.getValue(), heightSpinner.getValue());
        dummy.setRotation(rotSpinner.getValue());
        if (!spritePathField.getText().trim().isEmpty()) {
            dummy.setSpritePath(spritePathField.getText().trim());
        }

        boolean ok = prefabManager.savePrefab(dummy, prefabName);
        if (ok) {
            prefabManager.propagateChanges(prefabName);
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
            close();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Erro ao salvar prefab em Prefab Mode.");
            alert.showAndWait();
        }
    }
}
