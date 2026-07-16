package com.ignis.editor.fx;

import com.ignis.core.GameObject;

import java.io.File;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Construcao dos paineis laterais do editor (Fase F -- quebra do {@link IgnisEditorApp}):
 * a arvore da hierarquia da cena e o navegador de assets do projeto. Montam os
 * {@code TreeView}, celulas, filtros e handlers (selecao, duplo-clique, menu de
 * contexto) e os ligam ao estado/acoes do editor.
 *
 * <p>Recebe o {@link IgnisEditorApp} e escreve os campos de painel dele
 * ({@code hierarchy}, {@code assetTree}) alem de ler estado (objeto selecionado,
 * {@code game}) e chamar acoes. Mesmo pacote, membros package-private.</p>
 */
final class EditorPanelBuilder {

    private final IgnisEditorApp app;

    EditorPanelBuilder(IgnisEditorApp app) {
        this.app = app;
    }

        javafx.scene.Node buildHierarchy() {
            app.refreshHierarchy();
            app.hierarchyRoot.setExpanded(true);
            TreeView<String> tree = new TreeView<>(app.hierarchyRoot);
            app.hierarchy = tree;
            // Habilitar multi-selecao nativa (Ctrl+Click / Shift+Click) na TreeView.
            tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
            // Search/filter field for app.hierarchy
            TextField hierarchyFilter = new TextField();
            hierarchyFilter.setPromptText("Filtrar Hierarchy...");
            hierarchyFilter.textProperty().addListener((obs, oldVal, newVal) -> app.applyHierarchyFilter(newVal));

            // Cell factory: selecionar o item sob o cursor no clique DIREITO (SECONDARY).
            // Sem isso, JavaFX TreeView so seleciona no clique esquerdo, e o menu de
            // contexto opera no item previamente selecionado — nao no que esta sob o cursor.
            tree.setCellFactory(tv -> {
                javafx.scene.control.TreeCell<String> cell = new javafx.scene.control.TreeCell<>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            setStyle(null);
                        } else {
                            TreeItem<String> treeItem = getTreeItem();
                            if (treeItem != null && treeItem.getParent() != null && treeItem.getParent() != app.hierarchyRoot) {
                                // É um componente
                                setText("  ↳  " + item);
                                setStyle("-fx-text-fill: #9ab0c5; -fx-font-style: italic; -fx-font-size: 11px;");
                            } else {
                                // É um GameObject ou o nó raiz "Cena"
                                setText(item);
                                if (treeItem == app.hierarchyRoot) {
                                    setStyle("-fx-text-fill: -ignis-primary; -fx-font-weight: bold;");
                                } else {
                                    setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold; -fx-font-size: 12px;");
                                }
                            }
                        }
                    }
                };
                cell.setOnMousePressed(e -> {
                    if (!cell.isEmpty() && e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                        tv.getSelectionModel().select(cell.getTreeItem());
                    }
                });
                cell.setOnDragDetected(e -> {
                    if (!cell.isEmpty() && cell.getItem() != null && !cell.getItem().equals("Cena")) {
                        javafx.scene.input.Dragboard db = cell.startDragAndDrop(javafx.scene.input.TransferMode.ANY);
                        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        content.putString(cell.getItem());
                        db.setContent(content);
                        e.consume();
                    }
                });
                return cell;
            });
            // Listener de selecao: sincroniza o primario + secundarios a partir da
            // multi-selecao nativa do TreeView. O ultimo item na lista de selecao do
            // modelo e o primario (recebe gizmo/Inspector); os demais viram secundarios.
            tree.getSelectionModel().getSelectedItems().addListener(
                    (javafx.collections.ListChangeListener<TreeItem<String>>) change -> {
                if (app.suppressSelectionEvents) return;
                app.suppressSelectionEvents = true;
                try {
                    var selItems = tree.getSelectionModel().getSelectedItems();
                    java.util.List<GameObject> ents = app.game.getEntities();
                    if (selItems.isEmpty() || (selItems.size() == 1 && selItems.get(0) == app.hierarchyRoot)) {
                        app.setSelected(null);
                        app.clearSecondarySelection();
                        return;
                    }
                    // O item com foco (ultimo clicado) vira o primario.
                    TreeItem<String> focusedItem = selItems.get(selItems.size() - 1);
                    if (focusedItem == null || focusedItem == app.hierarchyRoot) focusedItem = selItems.get(0);
                
                    TreeItem<String> goItem = focusedItem;
                    if (focusedItem.getParent() != null && focusedItem.getParent() != app.hierarchyRoot) {
                        goItem = focusedItem.getParent();
                        app.selectedComponentName = focusedItem.getValue();
                    } else {
                        app.selectedComponentName = null;
                    }
                
                    int primaryIdx = app.hierarchyRoot.getChildren().indexOf(goItem);
                    GameObject primary = (primaryIdx >= 0 && primaryIdx < ents.size()) ? ents.get(primaryIdx) : null;
                    app.setSelected(primary);
                
                    // Montar a lista de secundarios (os demais).
                    app.secondarySelection.clear();
                    for (TreeItem<String> ti : selItems) {
                        if (ti == null || ti == app.hierarchyRoot || ti == focusedItem) continue;
                        TreeItem<String> pItem = ti;
                        if (ti.getParent() != null && ti.getParent() != app.hierarchyRoot) {
                            pItem = ti.getParent();
                        }
                        int idx = app.hierarchyRoot.getChildren().indexOf(pItem);
                        if (idx >= 0 && idx < ents.size()) {
                            GameObject go = ents.get(idx);
                            if (go != primary && !app.secondarySelection.contains(go)) {
                                app.secondarySelection.add(go);
                            }
                        }
                    }
                    app.syncHighlights();
                } finally {
                    app.suppressSelectionEvents = false;
                }
            });
            tree.setOnMouseClicked(ev -> {
                if (ev.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    TreeItem<String> sel = tree.getSelectionModel().getSelectedItem();
                    ContextMenu menu;
                    if (sel != null && sel != app.hierarchyRoot) {
                        menu = app.menus.buildHierarchyContextMenu();
                    } else {
                        menu = new ContextMenu();
                        MenuItem criarObjeto = new MenuItem("Criar Objeto de Cena");
                        criarObjeto.setOnAction(e -> app.createEntity("GameObject"));
                        MenuItem criarCamera = new MenuItem("Criar Câmera");
                        criarCamera.setOnAction(e -> app.createEntity("Camera"));
                        menu.getItems().addAll(criarObjeto, criarCamera);
                    }
                    menu.show(tree, ev.getScreenX(), ev.getScreenY());
                }
            });
            // Atalhos so quando a arvore tem foco (evita conflito com os campos do Inspector).
            tree.setOnKeyPressed(ev -> {
                if (ev.getCode() == KeyCode.DELETE) { app.deleteSelected(); ev.consume(); }
                else if (ev.getCode() == KeyCode.F2) { app.renameSelected(); ev.consume(); }
                else if (ev.getCode() == KeyCode.D && ev.isControlDown()) { app.duplicateSelected(); ev.consume(); }
            });
        
            // Painel: campo de filtro acima da arvore (antes o VBox era montado mas
            // o metodo retornava so a 'tree', deixando o filtro da Hierarchy orfao).
            VBox hierarchyBox = new VBox(4, hierarchyFilter, tree);
            VBox.setVgrow(tree, Priority.ALWAYS);
            return hierarchyBox;
        }

        javafx.scene.Node buildAssetBrowser() {
            VBox box = new VBox(4);
            box.getStyleClass().add("ignis-panel");
            Label title = new Label("Assets");
            title.getStyleClass().add("panel-title");

            // Search/filter field for asset browser
            TextField assetFilter = new TextField();
            assetFilter.setPromptText("Filtrar Assets...");
            assetFilter.textProperty().addListener((obs, oldVal, newVal) -> app.applyAssetFilter(newVal));

            app.assetTree = new TreeView<>();
            app.assetTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    File file = newVal.getValue();
                    if (file != null && file.isFile() && file.getName().endsWith(".java")) {
                        app.inspectScriptFile(file);
                    }
                }
            });
            app.assetTree.setShowRoot(true);
            app.assetTree.setCellFactory(tv -> {
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
            app.assetTree.setOnMouseClicked(ev -> {
                TreeItem<File> sel = app.assetTree.getSelectionModel().getSelectedItem();
                File file = (sel != null) ? sel.getValue() : null;
                if (ev.getClickCount() == 2 && ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    if (file != null && file.isFile()) {
                        if (file.getName().endsWith(".prefab.json")) {
                            app.instantiatePrefabByName(app.prefabNameOf(file));
                        } else if (file.getName().endsWith(".java")) {
                            app.openScriptInIgnisEditor(file);
                        } else {
                            app.openAssetFile(file);
                        }
                    }
                } else if (ev.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    ContextMenu menu = app.buildAssetsContextMenu(file);
                    menu.show(app.assetTree, ev.getScreenX(), ev.getScreenY());
                }
            });
            VBox.setVgrow(app.assetTree, Priority.ALWAYS);

            box.getChildren().addAll(title, assetFilter, app.assetTree);
            app.refreshAssetBrowser();
            return box;
        }
}
