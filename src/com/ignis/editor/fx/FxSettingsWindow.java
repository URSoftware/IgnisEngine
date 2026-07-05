package com.ignis.editor.fx;

import com.ignis.core.Game;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import com.ignis.collab.CollabSession;
import com.ignis.mcp.McpService;

import java.io.File;
import java.util.List;

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
    private final File projectFolder;
    private final StackPane contentArea = new StackPane();

    public FxSettingsWindow(Stage owner, Game game) {
        this(owner, game, null);
    }

    public FxSettingsWindow(Stage owner, Game game, File projectFolder) {
        this.game = game;
        this.projectFolder = projectFolder;
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
                navItem(topics, "Viewport", this::buildViewportPane, false),
                navItem(topics, "IA & MCP", this::buildAiMcpPane, false),
                navItem(topics, "Colaboracao", this::buildCollabPane, false)
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

        CheckBox autoComplete = new CheckBox("Auto-complete");
        autoComplete.setSelected(EditorPrefs.isAutoComplete());
        autoComplete.selectedProperty().addListener((o, a, b) -> EditorPrefs.setAutoComplete(b));

        ComboBox<String> syntax = new ComboBox<>();
        syntax.getItems().addAll("Dracula", "Monokai", "One Dark", "Solarized Dark", "Classic Dark", "Classic Light");
        if (EditorPrefs.getCodeEditorCustomThemeJson() != null) syntax.getItems().add("Custom");
        syntax.setValue(EditorPrefs.getCodeEditorTheme());
        syntax.valueProperty().addListener((o, a, b) -> { if (b != null) EditorPrefs.setCodeEditorTheme(b); });

        Spinner<Integer> fontSize = new Spinner<>(8, 40, EditorPrefs.getCodeEditorFontSize(), 1);
        fontSize.setEditable(true);
        fontSize.valueProperty().addListener((o, a, b) -> EditorPrefs.setCodeEditorFontSize(b));

        Button importBtn = new Button("Importar tema…");
        importBtn.setOnAction(e -> importCodeTheme(syntax));
        Button exportBtn = new Button("Exportar tema…");
        exportBtn.setOnAction(e -> exportCodeTheme(syntax.getValue()));
        HBox themeButtons = new HBox(8, importBtn, exportBtn);

        box.getChildren().addAll(
                autoComplete,
                labeledRow("Tema de sintaxe", syntax),
                labeledRow("Tamanho da fonte (px)", fontSize),
                themeButtons,
                hint("Aplicado aos editores de codigo abertos a partir de agora.")
        );
        return box;
    }

    // Importa um tema de sintaxe (JSON) e o define como tema customizado ativo.
    private void importCodeTheme(ComboBox<String> syntax) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Importar tema (JSON)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
        File f = fc.showOpenDialog(this);
        if (f == null) return;
        try {
            String json = java.nio.file.Files.readString(f.toPath());
            FxCodeEditor.importThemeFromJson(json); // valida o JSON
            EditorPrefs.setCodeEditorCustomThemeJson(json);
            EditorPrefs.setCodeEditorTheme("Custom");
            if (!syntax.getItems().contains("Custom")) syntax.getItems().add("Custom");
            syntax.setValue("Custom");
            new Alert(Alert.AlertType.INFORMATION,
                    "Tema importado. Sera aplicado aos editores abertos a partir de agora.").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao importar tema:\n" + ex.getMessage()).showAndWait();
        }
    }

    // Exporta o tema de sintaxe selecionado (nomeado ou customizado) para JSON.
    private void exportCodeTheme(String name) {
        try {
            FxCodeEditor.EditorTheme theme;
            if ("Custom".equals(name) && EditorPrefs.getCodeEditorCustomThemeJson() != null) {
                theme = FxCodeEditor.importThemeFromJson(EditorPrefs.getCodeEditorCustomThemeJson());
            } else {
                theme = FxCodeEditor.themeByName(name);
            }
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar tema (JSON)");
            fc.setInitialFileName((name == null ? "tema" : name.toLowerCase().replace(" ", "_")) + "_theme.json");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
            File f = fc.showSaveDialog(this);
            if (f == null) return;
            java.nio.file.Files.writeString(f.toPath(), FxCodeEditor.exportThemeToJson(theme));
            new Alert(Alert.AlertType.INFORMATION, "Tema exportado: " + f.getName()).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Falha ao exportar tema:\n" + ex.getMessage()).showAndWait();
        }
    }

    private Node buildViewportPane() {
            VBox box = section("Viewport");

            boolean curGrid = game != null && game.isShowGrid();
            boolean curSnap = game != null && game.isSnapToGrid();
            int curSize = game != null ? game.getGridSize() : 32;
            boolean curColliders = game != null && game.isShowColliders();

            CheckBox showGrid = new CheckBox("Mostrar grade");
            showGrid.setSelected(curGrid);
            showGrid.selectedProperty().addListener((o, a, b) -> {
                if (game != null) game.setShowGrid(b);
                EditorPrefs.setGridVisible(b);
            });

            CheckBox snapToGrid = new CheckBox("Snap à grade ao arrastar");
            snapToGrid.setSelected(curSnap);
            snapToGrid.selectedProperty().addListener((o, a, b) -> {
                if (game != null) game.setSnapToGrid(b);
                EditorPrefs.setSnapToGrid(b);
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
                    snapToGrid,
                    labeledRow("Tamanho da grade (px)", gridSize),
                    showColliders,
                    hint("Tambem usado como padrao ao iniciar o editor.")
            );
            return box;
        }

    // ================= IA & MCP =================

    private Node buildAiMcpPane() {
        VBox box = section("IA & MCP");

        // ---- Servidor MCP (bridge HTTP local) ----
        Label mcpTitle = new Label("Servidor MCP (ferramentas para agentes de IA)");
        mcpTitle.getStyleClass().add("field-label");

        boolean projectOpen = projectFolder != null && projectFolder.isDirectory();

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("field-label");

        TextField urlField = new TextField();
        urlField.setEditable(false);
        urlField.setPromptText("Servidor desativado");
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Button copyBtn = new Button("Copiar URL");
        copyBtn.setOnAction(e -> copyToClipboard(urlField.getText()));

        Spinner<Integer> portSpinner = new Spinner<>(1024, 65535, EditorPrefs.getMcpPort(), 1);
        portSpinner.setEditable(true);
        portSpinner.valueProperty().addListener((o, a, b) -> { if (b != null) EditorPrefs.setMcpPort(b); });

        CheckBox exposeNet = new CheckBox("Expor na rede/VPN (0.0.0.0) — permite conexao de outras maquinas");
        exposeNet.setSelected(EditorPrefs.isMcpExposeNetwork());
        exposeNet.selectedProperty().addListener((o, a, b) -> EditorPrefs.setMcpExposeNetwork(b));

        TextField tokenField = new TextField(EditorPrefs.getMcpToken());
        tokenField.setPromptText("(opcional) token Bearer para proteger o acesso");
        tokenField.textProperty().addListener((o, a, b) -> EditorPrefs.setMcpToken(b));

        ToggleButton mcpToggle = new ToggleButton();
        Runnable refreshMcp = () -> {
            boolean running = McpService.isRunning();
            mcpToggle.setSelected(running);
            mcpToggle.setText(running ? "Desativar servidor MCP" : "Ativar servidor MCP");
            statusLabel.setText(running
                    ? "Status: ATIVO — " + McpService.getRegistry().list().size() + " ferramentas expostas"
                    : (projectOpen ? "Status: desativado" : "Status: abra um projeto para ativar"));
            urlField.setText(running && McpService.getUrl() != null ? McpService.getUrl() : "");
            copyBtn.setDisable(!running);
            mcpToggle.setDisable(!projectOpen);
        };
        mcpToggle.setOnAction(e -> {
            try {
                if (McpService.isRunning()) {
                    McpService.stop();
                    EditorPrefs.setMcpEnabled(false);
                } else {
                    McpService.start(projectFolder, EditorPrefs.isMcpExposeNetwork(),
                            EditorPrefs.getMcpPort(), EditorPrefs.getMcpToken());
                    EditorPrefs.setMcpEnabled(true);
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Falha ao alternar o servidor MCP:\n" + ex.getMessage()).showAndWait();
            }
            refreshMcp.run();
        });
        refreshMcp.run();

        box.getChildren().addAll(
                mcpTitle,
                statusLabel,
                mcpToggle,
                labeledRow("Porta", portSpinner),
                exposeNet,
                labeledRow("Token (opcional)", tokenField),
                new HBox(8, urlField, copyBtn),
                hint("Cole esta URL na configuracao do agente. Endpoints: GET /mcp/tools e POST /mcp/call. "
                        + "Uma IA agentica local (futura) e as APIs Gemini/NVIDIA usam exatamente as mesmas ferramentas."),
                new Separator()
        );

        // ---- Agentes de IA (Gemini / NVIDIA) ----
        Label aiTitle = new Label("Agente de IA (APIs gratuitas)");
        aiTitle.getStyleClass().add("field-label");

        ComboBox<String> provider = new ComboBox<>();
        provider.getItems().addAll("Gemini", "NVIDIA");
        provider.setValue(EditorPrefs.getAiProvider());
        provider.valueProperty().addListener((o, a, b) -> { if (b != null) EditorPrefs.setAiProvider(b); });

        PasswordField geminiKey = new PasswordField();
        geminiKey.setText(EditorPrefs.getGeminiApiKey());
        geminiKey.setPromptText("Chave da API Gemini (aistudio.google.com)");
        geminiKey.textProperty().addListener((o, a, b) -> EditorPrefs.setGeminiApiKey(b));

        PasswordField nvidiaKey = new PasswordField();
        nvidiaKey.setText(EditorPrefs.getNvidiaApiKey());
        nvidiaKey.setPromptText("Chave da API NVIDIA (build.nvidia.com)");
        nvidiaKey.textProperty().addListener((o, a, b) -> EditorPrefs.setNvidiaApiKey(b));

        CheckBox aiTools = new CheckBox("Permitir que o agente use as ferramentas do MCP (function-calling)");
        aiTools.setSelected(EditorPrefs.isAiToolsEnabled());
        aiTools.selectedProperty().addListener((o, a, b) -> EditorPrefs.setAiToolsEnabled(b));

        box.getChildren().addAll(
                aiTitle,
                labeledRow("Provedor ativo", provider),
                labeledRow("Chave Gemini", geminiKey),
                labeledRow("Chave NVIDIA", nvidiaKey),
                aiTools,
                hint("Quando o MCP esta ativo e o function-calling ligado, o agente pode ler a arvore do projeto, "
                        + "criar/editar scripts e compilar. Plano completo em doc/AGENTIC_AI_PLAN.md.")
        );
        return box;
    }

    // ================= Colaboracao =================

    private Node buildCollabPane() {
        VBox box = section("Colaboracao em tempo real");

        CollabSession collab = CollabSession.get();

        TextField nameField = new TextField(EditorPrefs.getCollabDisplayName());
        nameField.setPromptText("Seu nome de exibicao");
        nameField.textProperty().addListener((o, a, b) -> {
            EditorPrefs.setCollabDisplayName(b);
            collab.setDisplayName(b);
        });

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("field-label");
        statusLabel.setWrapText(true);

        ListView<String> participantsView = new ListView<>();
        participantsView.setPrefHeight(120);

        // ---- Host ----
        Spinner<Integer> portSpinner = new Spinner<>(1024, 65535, EditorPrefs.getCollabPort(), 1);
        portSpinner.setEditable(true);
        portSpinner.valueProperty().addListener((o, a, b) -> { if (b != null) EditorPrefs.setCollabPort(b); });

        TextField shareField = new TextField();
        shareField.setEditable(false);
        shareField.setPromptText("Endereco para compartilhar aparece aqui ao hospedar");
        HBox.setHgrow(shareField, Priority.ALWAYS);
        Button copyShare = new Button("Copiar");
        copyShare.setOnAction(e -> copyToClipboard(shareField.getText()));

        TextField hostToken = new TextField();
        hostToken.setPromptText("(opcional) senha da sessao");
        Button hostBtn = new Button("Hospedar sessao");
        Button stopBtn = new Button("Encerrar");

        // ---- Guest ----
        TextField hostAddr = new TextField();
        hostAddr.setPromptText("IP do host (ex.: 25.x.x.x da Radmin VPN)");
        Spinner<Integer> joinPort = new Spinner<>(1024, 65535, EditorPrefs.getCollabPort(), 1);
        joinPort.setEditable(true);
        TextField joinToken = new TextField();
        joinToken.setPromptText("(se o host definiu) senha da sessao");
        Button joinBtn = new Button("Entrar na sessao");

        Runnable refresh = () -> {
            boolean active = collab.isActive();
            boolean host = collab.getRole() == CollabSession.Role.HOST;
            hostBtn.setDisable(active);
            joinBtn.setDisable(active);
            stopBtn.setDisable(!active);
            shareField.setText(host && collab.getShareAddress() != null ? collab.getShareAddress() : "");
            copyShare.setDisable(!host);
            statusLabel.setText(active
                    ? (host ? "Hospedando — " + collab.participantCount() + " participante(s)"
                            : "Conectado como convidado")
                    : "Sem sessao ativa");
        };

        // Ouve eventos da sessao para atualizar presenca/status na UI Thread.
        CollabSession.Listener uiListener = new CollabSession.Listener() {
            @Override public void onPresence(List<String> participants) {
                javafx.application.Platform.runLater(() -> {
                    participantsView.getItems().setAll(participants);
                    refresh.run();
                });
            }
            @Override public void onStatus(String message, boolean connected) {
                javafx.application.Platform.runLater(() -> { statusLabel.setText(message); refresh.run(); });
            }
        };
        collab.addListener(uiListener);
        setOnHidden(e -> collab.removeListener(uiListener));

        hostBtn.setOnAction(e -> {
            try {
                collab.setDisplayName(nameField.getText());
                collab.host(portSpinner.getValue(), hostToken.getText().trim());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Falha ao hospedar:\n" + ex.getMessage()).showAndWait();
            }
            refresh.run();
        });
        stopBtn.setOnAction(e -> { collab.stop(); participantsView.getItems().clear(); refresh.run(); });
        joinBtn.setOnAction(e -> {
            try {
                collab.setDisplayName(nameField.getText());
                collab.join(hostAddr.getText().trim(), joinPort.getValue(), joinToken.getText().trim());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Falha ao conectar:\n" + ex.getMessage()).showAndWait();
            }
            refresh.run();
        });
        refresh.run();

        // Cache de projetos sincronizados (copias temporarias recebidas do host).
        Button clearCacheBtn = new Button("Limpar cache de sessoes");
        clearCacheBtn.setOnAction(e -> {
            if (collab.isActive()) {
                new Alert(Alert.AlertType.WARNING,
                        "Encerre a sessao atual antes de limpar o cache.").showAndWait();
                return;
            }
            boolean ok = com.ignis.collab.CollabProjectSync.clearAllCaches();
            new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                    ok ? "Cache de sessoes colaborativas limpo."
                       : "Nao foi possivel remover todo o cache (arquivos em uso?).").showAndWait();
        });

        box.getChildren().addAll(
                labeledRow("Nome de exibicao", nameField),
                statusLabel,
                new Separator(),
                new Label("Hospedar (voce vira o host):"),
                labeledRow("Porta", portSpinner),
                labeledRow("Senha (opcional)", hostToken),
                new HBox(8, hostBtn, stopBtn),
                new HBox(8, shareField, copyShare),
                hint("Funciona por IP direto ou por VPN (Radmin/Hamachi/Tailscale). Compartilhe o endereco (e a senha, se definir)."),
                new Separator(),
                new Label("Entrar em uma sessao existente:"),
                labeledRow("Endereco do host", hostAddr),
                labeledRow("Porta", joinPort),
                labeledRow("Senha", joinToken),
                joinBtn,
                hint("Ao entrar, voce recebe uma copia temporaria do projeto do host (assets, scripts, cenas) "
                        + "em ~/.ignis/collab-cache — nada sobrescreve seus projetos locais. Reentrar na mesma "
                        + "sessao so baixa o que mudou."),
                new Separator(),
                new Label("Participantes:"),
                participantsView,
                labeledRow("Cache local", clearCacheBtn),
                hint("Sincronizacao completa: projeto inicial + alteracoes ao vivo (cena, scripts, assets) + "
                        + "ponteiro dos participantes na Scene View. Detalhes em doc/COLLABORATION_GUIDE.md.")
        );
        return box;
    }

    // Copia texto para a area de transferencia (no-op se vazio).
    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
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
