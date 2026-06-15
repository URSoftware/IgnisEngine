package com.ignis.editor.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tela inicial de selecao de projeto do editor JavaFX. Analoga ao startup do
 * editor Swing (Editor.showStartupDialog), porem com uma LISTA VISUAL dos
 * projetos existentes e dos recentes (★) — melhoria sobre o dialogo de 3 botoes
 * do Swing. Modal e bloqueante (showAndWait); devolve a escolha do usuario.
 */
public class FxProjectStartupDialog {

    public enum Kind { OPEN, NEW, IMPORT, EXIT }

    /** Escolha do usuario. {@code file} so e preenchido quando {@code kind == OPEN}. */
    public static final class Choice {
        public final Kind kind;
        public final File file;
        public Choice(Kind kind, File file) { this.kind = kind; this.file = file; }
    }

    /**
     * Mostra o dialogo modal e retorna a escolha (nunca null; EXIT por padrao).
     *
     * @param owner    janela dona (pode ser null)
     * @param projetos todos os .ignis encontrados em projects/
     * @param recentes .ignis recentes (mostrados no topo, marcados com ★)
     */
    public static Choice show(Window owner, List<File> projetos, List<File> recentes) {
        final Choice[] result = { new Choice(Kind.EXIT, null) };

        Stage dlg = new Stage();
        dlg.setTitle("IgnisEngine — Selecionar Projeto");
        if (owner != null) dlg.initOwner(owner);
        dlg.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e;");
        root.setPadding(new Insets(16));

        Label title = new Label("Bem-vindo ao IgnisEngine");
        title.setStyle("-fx-text-fill: #2e8b57; -fx-font-size: 18px; -fx-font-weight: bold;");
        Label subtitle = new Label("Escolha um projeto para abrir ou crie um novo.");
        subtitle.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 12px;");
        VBox header = new VBox(4, title, subtitle);
        header.setPadding(new Insets(0, 0, 12, 0));
        root.setTop(header);

        // Recentes primeiro, depois o restante dos projetos (sem duplicar).
        LinkedHashSet<File> ordered = new LinkedHashSet<>();
        if (recentes != null) ordered.addAll(recentes);
        if (projetos != null) ordered.addAll(projetos);
        final Set<File> recentSet = new HashSet<>(recentes == null ? List.of() : recentes);

        ListView<File> list = new ListView<>();
        list.getItems().addAll(ordered);
        list.setStyle("-fx-control-inner-background: #232323; -fx-background-color: #232323;");
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String nome = item.getName().replaceFirst("(?i)\\.ignis$", "");
                    String prefixo = recentSet.contains(item) ? "★  " : "    ";
                    setText(prefixo + nome + "      —  " + item.getParent());
                    setStyle("-fx-text-fill: #e0e0e0;");
                }
            }
        });
        if (!list.getItems().isEmpty()) list.getSelectionModel().select(0);
        list.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                result[0] = new Choice(Kind.OPEN, list.getSelectionModel().getSelectedItem());
                dlg.close();
            }
        });

        if (list.getItems().isEmpty()) {
            Label vazio = new Label("Nenhum projeto encontrado em 'projects/'.\nClique em 'Novo projeto…' para comecar.");
            vazio.setStyle("-fx-text-fill: #888; -fx-text-alignment: center;");
            StackPane ph = new StackPane(vazio);
            ph.setStyle("-fx-background-color: #232323;");
            root.setCenter(ph);
        } else {
            root.setCenter(list);
        }

        Button btnOpen = new Button("Abrir selecionado");
        btnOpen.setStyle("-fx-background-color: #2e8b57; -fx-text-fill: white; -fx-font-weight: bold;");
        btnOpen.setDefaultButton(true);
        btnOpen.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        btnOpen.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) { result[0] = new Choice(Kind.OPEN, sel); dlg.close(); }
        });

        Button btnNew = new Button("Novo projeto…");
        btnNew.setStyle("-fx-background-color: #4682b4; -fx-text-fill: white;");
        btnNew.setOnAction(e -> { result[0] = new Choice(Kind.NEW, null); dlg.close(); });

        Button btnImport = new Button("Abrir de arquivo…");
        btnImport.setStyle("-fx-background-color: #5a5a5a; -fx-text-fill: white;");
        btnImport.setOnAction(e -> { result[0] = new Choice(Kind.IMPORT, null); dlg.close(); });

        Button btnExit = new Button("Sair");
        btnExit.setStyle("-fx-background-color: #8b2e2e; -fx-text-fill: white;");
        btnExit.setOnAction(e -> { result[0] = new Choice(Kind.EXIT, null); dlg.close(); });

        Region spacer = new Region();
        HBox buttons = new HBox(8, btnNew, btnImport, spacer, btnOpen, btnExit);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        buttons.setPadding(new Insets(12, 0, 0, 0));
        buttons.setAlignment(Pos.CENTER_LEFT);
        root.setBottom(buttons);

        dlg.setScene(new Scene(root, 580, 440));
        dlg.showAndWait();
        return result[0];
    }
}
