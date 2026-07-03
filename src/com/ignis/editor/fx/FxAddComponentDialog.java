package com.ignis.editor.fx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;

/**
 * Janela dedicada e pesquisável para adicionar novos componentes a um GameObject.
 */
public class FxAddComponentDialog extends Stage {

    private final ListView<String> listView;
    private final TextField searchField;
    private final Button addButton;
    private String result = null;

    /**
     * Cria e exibe a janela de seleção de componentes.
     * 
     * @param owner O Stage proprietário da janela modal.
     * @param available A lista de nomes de componentes disponíveis.
     */
    public FxAddComponentDialog(Stage owner, List<String> available) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Adicionar Componente");

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("ignis-panel");

        Label label = new Label("Selecione um componente para adicionar:");
        label.getStyleClass().add("field-label");

        // Campo de busca
        searchField = new TextField();
        searchField.setPromptText("Pesquisar componentes...");

        // Lista de componentes
        ObservableList<String> items = FXCollections.observableArrayList(available);
        FilteredList<String> filteredItems = new FilteredList<>(items, p -> true);
        
        listView = new ListView<>(filteredItems);
        listView.setPrefHeight(200);

        // Filtro em tempo real
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredItems.setPredicate(name -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lower = newValue.toLowerCase();
                return name.toLowerCase().contains(lower);
            });
        });

        // Ação de confirmar com Enter no searchField
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                confirmSelection();
            }
        });

        // Ação de duplo clique na lista
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                confirmSelection();
            }
        });

        // Atalhos de teclado na lista (Enter)
        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                confirmSelection();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
            }
        });

        // Botões de ação
        addButton = new Button("Adicionar");
        addButton.getStyleClass().add("btn-primary");
        addButton.setOnAction(e -> confirmSelection());

        Button cancelButton = new Button("Cancelar");
        cancelButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, addButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(label, searchField, listView, buttons);

        Scene scene = new Scene(root, 320, 350);
        setScene(scene);
        FxTheme.apply(scene);

        // Foco inicial no campo de busca para facilitar digitação rápida
        setOnShown(e -> searchField.requestFocus());
    }

    private void confirmSelection() {
        String selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            result = selected;
            close();
        }
    }

    /**
     * Retorna o componente selecionado ou null caso a operação tenha sido cancelada.
     */
    public String showAndGetResult() {
        showAndWait();
        return result;
    }
}
