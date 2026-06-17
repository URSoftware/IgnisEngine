package com.ignis.editor.fx;

import com.ignis.core.Game;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;

/**
 * Janela de Configuracoes centralizada do editor JavaFX.
 *
 * <p>Centraliza preferencias antes espalhadas pelas janelas/menus (tema da UI,
 * Auto Save, tema e fonte do editor de codigo, grade/colliders do viewport) num
 * unico lugar, navegavel por topicos. Layout: painel esquerdo (~1/3) com a lista
 * de topicos rolavel; painel direito (~2/3) com os controles do topico ativo.
 *
 * <p>Cada controle persiste de imediato em {@link EditorPrefs} (estilo VSCode);
 * mudancas de tema da UI sao aplicadas ao vivo via {@link FxTheme#refreshAllWindows()}.
 * Tudo aditivo; nada em {@code com.ignis.core} e alterado (apenas estado vivo do Game).
 */
public class FxSettingsWindow extends Stage {

    private final Game game;
    private final StackPane contentArea = new StackPane();

    public FxSettingsWindow(Stage owner, Game game) {
        this.game = game;
        setTitle("Configuracoes");
        initModality(Modality.NONE);
        if (owner != null) initOwner(owner);
        setWidth(820);
        setHeight(560);
        loadIcon();
        buildUI();
    }

    private void loadIcon() {
        try {
            File iconFile = new File("Icons/IconeIgnis.png");
            if (iconFile.exists()) {
                getIcons().add(new javafx.scene.image.Image(iconFile.toURI().toString()));
            }
        } catch (Exception ignore) { /* icone opcional */ }
    }

    private void buildUI() {
        BorderPane root = new BorderPane();

        // ---- Painel esquerdo (~1/3): navegacao por topicos, rolavel ----
        VBox nav = new VBox(2);
        nav.setPadding(new Insets(8));
        nav.getStyleClass().add("ignis-panel");

        Label navTitle = new Label("Configuracoes");
        navTitle.getStyleClass().add("panel-title");
        nav.getChildren().add(navTitle);

        ToggleGroup topics = new ToggleGroup();
        nav.getChildren().addAll(
                navItem(topics, "Geral", this::buildGeneralPane, true),
                navItem(topics, "Aparencia", this::buildAppearancePane, false),
                navItem(topics, "Editor de Scripts", this::buildScriptsPane, false),
                navItem(topics, "Viewport", this::buildViewportPane, false)
        );

        ScrollPane navScroll = new ScrollPane(nav);
        navScroll.setFitToWidth(true);
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navScroll.getStyleClass().add("ignis-panel");

        // ---- Painel direito (~2/3): conteudo do topico ativo ----
        contentArea.setPadding(new Insets(4));
        ScrollPane contentScroll = new ScrollPane(contentArea);
        contentScroll.setFitToWidth(true);

        // 1/3 x 2/3 via SplitPane (consistente com o resto do editor).
        javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(navScroll, contentScroll);
        split.setDividerPositions(0.33);
        root.setCenter(split);

        // ---- Rodape: botao Fechar ----
        Button close = new Button("Fechar");
        close.setOnAction(e -> close());
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8));
        footer.getStyleClass().add("ignis-panel");
        root.setBottom(footer);

        Scene scene = new Scene(root);
        FxTheme.apply(scene);
        setScene(scene);

        // Abre no primeiro topico.
        showContent(buildGeneralPane());
    }

    // Cria um item de navegacao (ToggleButton de largura total) que troca o conteudo.
    private ToggleButton navItem(ToggleGroup group, String label,
                                 java.util.function.Supplier<Node> contentSupplier, boolean selected) {
        ToggleButton tb = new ToggleButton(label);
        tb.setToggleGroup(group);
        tb.setMaxWidth(Double.MAX_VALUE);
        tb.setAlignment(Pos.CENTER_LEFT);
        tb.getStyleClass().add("nav-item");
        tb.setSelected(selected);
        tb.setOnAction(e -> {
            // Impede desmarcar o item ativo (sempre ha um topico selecionado).
            if (!tb.isSelected()) { tb.setSelected(true); return; }
            showContent(contentSupplier.get());
        });
        return tb;
    }

    private void showContent(Node node) {
        contentArea.getChildren().setAll(node);
    }

    // ================= Topicos =================

    private Node buildGeneralPane() {
        VBox box = section("Geral");

        CheckBox autoSave = new CheckBox("Auto Save (projeto e scripts)");
        autoSave.setSelected(EditorPrefs.isAutoSave());
        autoSave.selectedProperty().addListener((o, a, b) -> EditorPrefs.setAutoSave(b));

        Spinner<Integer> interval = new Spinner<>(5, 600, EditorPrefs.getAutoSaveIntervalSeconds(), 5);
        interval.setEditable(true);
        interval.valueProperty().addListener((o, a, b) -> EditorPrefs.setAutoSaveIntervalSeconds(b));

        CheckBox rememberLayout = new CheckBox("Lembrar tamanho/posicao da janela e divisores");
        rememberLayout.setSelected(EditorPrefs.isRememberLayout());
        rememberLayout.selectedProperty().addListener((o, a, b) -> EditorPrefs.setRememberLayout(b));

        box.getChildren().addAll(
                autoSave,
                labeledRow("Intervalo do Auto Save (s)", interval),
                rememberLayout,
                hint("O intervalo do Auto Save passa a valer na proxima vez que o Auto Save for acionado.")
        );
        return box;
    }

    private Node buildAppearancePane() {
        VBox box = section("Aparencia");

        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().addAll("Escuro (Ignis)", "Padrao (JavaFX)");
        theme.setValue(FxTheme.isDark() ? "Escuro (Ignis)" : "Padrao (JavaFX)");
        theme.valueProperty().addListener((o, a, b) -> {
            EditorPrefs.setEditorTheme("Escuro (Ignis)".equals(b) ? "dark" : "default");
            FxTheme.refreshAllWindows();
        });

        box.getChildren().addAll(
                labeledRow("Tema do editor", theme),
                hint("Aplicado imediatamente as janelas abertas. O editor de codigo mantem seu proprio tema de sintaxe (veja 'Editor de Scripts').")
        );
        return box;
    }

    private Node buildScriptsPane() {
        VBox box = section("Editor de Scripts");

        ComboBox<String> syntax = new ComboBox<>();
        syntax.getItems().addAll("Dracula", "Monokai", "One Dark", "Solarized Dark", "Classic Dark", "Classic Light");
        syntax.setValue(EditorPrefs.getCodeEditorTheme());
        syntax.valueProperty().addListener((o, a, b) -> { if (b != null) EditorPrefs.setCodeEditorTheme(b); });

        Spinner<Integer> fontSize = new Spinner<>(8, 40, EditorPrefs.getCodeEditorFontSize(), 1);
        fontSize.setEditable(true);
        fontSize.valueProperty().addListener((o, a, b) -> EditorPrefs.setCodeEditorFontSize(b));

        box.getChildren().addAll(
                labeledRow("Tema de sintaxe", syntax),
                labeledRow("Tamanho da fonte (px)", fontSize),
                hint("Aplicado aos editores de codigo abertos a partir de agora.")
        );
        return box;
    }

    private Node buildViewportPane() {
        VBox box = section("Viewport");

        boolean curGrid = game != null && game.isShowGrid();
        int curSize = game != null ? game.getGridSize() : 32;
        boolean curColliders = game != null && game.isShowColliders();

        CheckBox showGrid = new CheckBox("Mostrar grade");
        showGrid.setSelected(curGrid);
        showGrid.selectedProperty().addListener((o, a, b) -> {
            if (game != null) game.setShowGrid(b);
            EditorPrefs.setGridVisible(b);
        });

        ComboBox<Integer> gridSize = new ComboBox<>();
        gridSize.getItems().addAll(16, 32, 64, 128);
        gridSize.setValue(curSize);
        gridSize.valueProperty().addListener((o, a, b) -> {
            if (b == null) return;
            if (game != null) game.setGridSize(b);
            EditorPrefs.setGridSize(b);
        });

        CheckBox showColliders = new CheckBox("Mostrar colliders");
        showColliders.setSelected(curColliders);
        showColliders.selectedProperty().addListener((o, a, b) -> {
            if (game != null) game.setShowColliders(b);
            EditorPrefs.setShowColliders(b);
        });

        box.getChildren().addAll(
                showGrid,
                labeledRow("Tamanho da grade (px)", gridSize),
                showColliders,
                hint("Tambem usado como padrao ao iniciar o editor.")
        );
        return box;
    }

    // ================= Helpers de UI =================

    private VBox section(String title) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("ignis-panel");
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        box.getChildren().add(t);
        return box;
    }

    // Linha "rotulo: controle" alinhada horizontalmente.
    private HBox labeledRow(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        l.setMinWidth(190);
        HBox row = new HBox(10, l, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(l, new Insets(6, 0, 0, 0));
        return l;
    }
}
