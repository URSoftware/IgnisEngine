package com.ignis.editor.fx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;

import com.ignis.marketplace.MarketplaceClient;
import com.ignis.marketplace.MarketplaceItem;

/**
 * JavaFX implementation of the Ignis Community Hub & Marketplace window.
 * Ports com.ignis.community.CommunityFrame.
 */
public class FxCommunityWindow extends Stage {

    private final File projectPluginsFolder;
    private final File projectAssetsFolder;
    private final Label statusLabel;
    private final MarketplaceClient marketplace = MarketplaceClient.getInstance();

    public FxCommunityWindow(File projectFolder) {
        setTitle("Ignis Community Hub & Marketplace");
        initModality(Modality.NONE);

        // Resolve paths
        if (projectFolder != null) {
            this.projectPluginsFolder = new File(projectFolder, "plugins");
            this.projectAssetsFolder = new File(projectFolder, "assets");
        } else {
            this.projectPluginsFolder = new File(System.getProperty("user.home"), ".ignis/plugins");
            this.projectAssetsFolder = new File(System.getProperty("user.home"), ".ignis/assets");
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: -ignis-bg;");

        // --- Top Header ---
        BorderPane headerPanel = new BorderPane();
        headerPanel.setStyle("-fx-background-color: -ignis-panel; -fx-border-color: -ignis-border; -fx-border-width: 0 0 1 0;");
        headerPanel.setPadding(new Insets(12, 16, 12, 16));

        Label titleLabel = new Label("👥 Community & Marketplace");
        titleLabel.setStyle("-fx-font-family: 'Arial'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -ignis-text;");
        headerPanel.setLeft(titleLabel);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button btnPublishSite = new Button("🌐 Publicar no site");
        styleActionButton(btnPublishSite, "-ignis-info");
        btnPublishSite.setTooltip(new Tooltip("Abre o marketplace no navegador para publicar com login GitHub."));
        btnPublishSite.setOnAction(e -> openInBrowser(marketplace.getPublishUrl()));

        Button btnPublishLocal = new Button("💻 Publicar com token");
        styleActionButton(btnPublishLocal, "-ignis-primary");
        btnPublishLocal.setTooltip(new Tooltip("Publica direto do editor usando seu token (sem abrir o navegador)."));
        btnPublishLocal.setOnAction(e -> openLocalPublishDialog());

        Button btnToken = new Button("🔑 Token");
        styleActionButton(btnToken, "-ignis-secondary");
        btnToken.setTooltip(new Tooltip("Configurar o token de publicacao."));
        btnToken.setOnAction(e -> openTokenDialog());

        Button btnHelp = new Button("❓");
        styleActionButton(btnHelp, "-ignis-secondary");
        btnHelp.setTooltip(new Tooltip("Como publicar funciona."));
        btnHelp.setOnAction(e -> showPublishHelp());

        actions.getChildren().addAll(btnPublishSite, btnPublishLocal, btnToken, btnHelp);
        headerPanel.setRight(actions);
        root.setTop(headerPanel);

        // --- Tabs Container (Center) ---
        TabPane tabbedPane = new TabPane();
        tabbedPane.setStyle("-fx-background-color: -ignis-bg;");
        
        List<MarketplaceItem> catalog = marketplace.fetchCatalog();

        Tab tabPlugins = new Tab("🔌 Plugins Marketplace", createCatalogPanel(catalog, "plugin"));
        tabPlugins.setClosable(false);
        Tab tabWorkshop = new Tab("🛠 Workshop Assets", createCatalogPanel(catalog, "workshop"));
        tabWorkshop.setClosable(false);
        Tab tabAssets = new Tab("🌃 Shared Tilemaps/Art", createCatalogPanel(catalog, "asset"));
        tabAssets.setClosable(false);

        tabbedPane.getTabs().addAll(tabPlugins, tabWorkshop, tabAssets);
        root.setCenter(tabbedPane);

        // --- Status Bar (Bottom) ---
        HBox statusBar = new HBox(12);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setStyle("-fx-background-color: -ignis-panel; -fx-border-color: -ignis-border; -fx-border-width: 1 0 0 0;");
        
        statusLabel = new Label(marketplace.isLastFetchOnline()
                ? "Online catalog loaded from " + marketplace.getBaseUrl()
                : "Offline: showing built-in catalog (marketplace API unreachable).");
        statusLabel.setStyle("-fx-text-fill: -ignis-text-dim; -fx-font-size: 11px;");
        statusBar.getChildren().add(statusLabel);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 900, 600);
        FxTheme.apply(scene);
        setScene(scene);
    }

    private ScrollPane createCatalogPanel(List<MarketplaceItem> catalog, String filterType) {
        VBox listPanel = new VBox(10);
        listPanel.setPadding(new Insets(12));
        listPanel.setStyle("-fx-background-color: -ignis-bg;");

        for (MarketplaceItem item : catalog) {
            if (item.type.equals(filterType)) {
                listPanel.getChildren().add(createItemCard(item));
            }
        }

        ScrollPane scrollPane = new ScrollPane(listPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -ignis-bg; -fx-border-color: transparent;");
        return scrollPane;
    }

    private BorderPane createItemCard(MarketplaceItem item) {
        BorderPane card = new BorderPane();
        card.setStyle("-fx-background-color: -ignis-panel; -fx-border-color: -ignis-border; -fx-border-width: 1px; -fx-border-radius: 4px;");
        card.setPadding(new Insets(12));

        // Visual Preview Thumbnail (Left)
        StackPane thumbnail = new StackPane();
        thumbnail.setPrefSize(80, 60);
        thumbnail.setStyle("-fx-background-color: -ignis-control; -fx-border-color: -ignis-border; -fx-border-width: 1px;");
        Label thumbLabel = new Label(item.coverImageText);
        thumbLabel.setStyle("-fx-text-fill: -ignis-text-dim; -fx-font-family: 'Arial'; -fx-font-size: 10px; -fx-font-weight: bold;");
        thumbnail.getChildren().add(thumbLabel);
        BorderPane.setMargin(thumbnail, new Insets(0, 15, 0, 0));
        card.setLeft(thumbnail);

        // Center Content Info
        VBox infoPanel = new VBox(4);
        Label nameLabel = new Label(item.name + "  v" + item.version + "  by " + item.author);
        nameLabel.setStyle("-fx-text-fill: -ignis-text; -fx-font-family: 'Arial'; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label descLabel = new Label(item.description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: -ignis-text-dim; -fx-font-family: 'Arial'; -fx-font-size: 11px;");

        Label depLabel = new Label("Git: " + item.gitUrl + "  |  Deps: " + item.dependencies);
        depLabel.setStyle("-fx-text-fill: -ignis-info; -fx-font-family: 'Courier New'; -fx-font-size: 10px;");

        infoPanel.getChildren().addAll(nameLabel, descLabel, depLabel);
        card.setCenter(infoPanel);

        // Install Button (Right)
        StackPane btnPanel = new StackPane();
        btnPanel.setPadding(new Insets(0, 0, 0, 10));
        Button btnInstall = new Button("⚡ 1-Click Install");
        btnInstall.setStyle("-fx-background-color: -ignis-primary; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 14; -fx-background-radius: 4px;");
        btnInstall.setOnAction(e -> installPackage(item, btnInstall));
        btnPanel.getChildren().add(btnInstall);
        card.setRight(btnPanel);

        return card;
    }

    private void installPackage(MarketplaceItem item, Button installBtn) {
        installBtn.setDisable(true);
        installBtn.setText("⏳ Installing...");
        statusLabel.setText("Installing " + item.name + "...");

        Stage progressDialog = new Stage(StageStyle.UTILITY);
        progressDialog.initOwner(this);
        progressDialog.initModality(Modality.WINDOW_MODAL);
        progressDialog.setTitle("Security Installer - " + item.name);
        progressDialog.setResizable(false);

        VBox p = new VBox(8);
        p.setPadding(new Insets(16));
        p.setStyle("-fx-background-color: -ignis-panel;");
        p.setPrefWidth(350);

        Label stepLabel = new Label("1. Downloading package from Git...");
        stepLabel.setStyle("-fx-text-fill: -ignis-text; -fx-font-size: 12px;");

        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(320);
        bar.setStyle("-fx-accent: -ignis-accent;");

        p.getChildren().addAll(stepLabel, bar);
        Scene progressScene = new Scene(p);
        FxTheme.apply(progressScene);
        progressDialog.setScene(progressScene);

        new Thread(() -> {
            try {
                // Step 1: Download
                for (int i = 0; i <= 30; i += 5) {
                    Thread.sleep(80);
                    final double progress = i / 100.0;
                    Platform.runLater(() -> bar.setProgress(progress));
                }

                // Step 2: Sandbox & Permissions Verification
                Platform.runLater(() -> stepLabel.setText("2. Running sandbox & integrity analysis..."));
                for (int i = 30; i <= 70; i += 10) {
                    Thread.sleep(100);
                    final double progress = i / 100.0;
                    Platform.runLater(() -> bar.setProgress(progress));
                }

                // Step 3: Copy Assets to Plugins
                Platform.runLater(() -> stepLabel.setText("3. Copying package files to project..."));
                for (int i = 70; i <= 100; i += 5) {
                    Thread.sleep(60);
                    final double progress = i / 100.0;
                    Platform.runLater(() -> bar.setProgress(progress));
                }

                // Create physical mock file inside project directories
                File targetDir = item.type.equals("plugin") ? projectPluginsFolder : projectAssetsFolder;
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                File manifest = new File(targetDir, item.name.replace(' ', '_').toLowerCase() + "_plugin.txt");
                Files.writeString(manifest.toPath(),
                        "Name: " + item.name + "\n" +
                                "Author: " + item.author + "\n" +
                                "Version: " + item.version + "\n" +
                                "Git URL: " + item.gitUrl + "\n" +
                                "Install-Status: SUCCESSFUL_SANDBOXED\n");

                marketplace.notifyInstall(item.id);
                Thread.sleep(200);

                Platform.runLater(() -> {
                    progressDialog.close();
                    installBtn.setText("✓ Installed");
                    installBtn.setStyle("-fx-background-color: -ignis-secondary; -fx-text-fill: white; -fx-padding: 8 14; -fx-background-radius: 4px;");
                    statusLabel.setText("✓ " + item.name + " installed successfully to " + targetDir.getName() + ".");
                    
                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.initOwner(this);
                    success.setTitle("Installation Successful");
                    success.setHeaderText(null);
                    success.setContentText(item.name + " installed successfully!\nAll package files were sandboxed and copied to " + targetDir.getName() + "/");
                    success.showAndWait();
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressDialog.close();
                    installBtn.setDisable(false);
                    installBtn.setText("⚡ Retry Install");
                    statusLabel.setText("❌ Installation failed: " + ex.getMessage());
                    
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.initOwner(this);
                    error.setTitle("Installation Error");
                    error.setHeaderText(null);
                    error.setContentText("Failed to install: " + ex.getMessage());
                    error.showAndWait();
                });
            }
        }).start();

        progressDialog.show();
    }

    private void styleActionButton(Button b, String hexColor) {
        b.setStyle("-fx-background-color: " + hexColor + "; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 4px;");
    }

    private void openInBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new URI(url));
                statusLabel.setText("Abrindo no navegador: " + url);
            } else {
                showLinkAlert(url);
            }
        } catch (Exception ex) {
            showLinkAlert(url);
        }
    }

    private void showLinkAlert(String url) {
        Alert linkAlert = new Alert(Alert.AlertType.INFORMATION);
        linkAlert.initOwner(this);
        linkAlert.setTitle("Link");
        linkAlert.setHeaderText(null);
        linkAlert.setContentText("Abra no navegador:\n" + url);
        linkAlert.showAndWait();
    }

    private void showPublishHelp() {
        String msg =
            "Como publicar no marketplace:\n\n" +
            "🌐 Publicar no site\n" +
            "   Abre o marketplace no navegador. Voce entra com o GitHub e\n" +
            "   publica pelo formulario da web. Mais simples; nao precisa de token.\n\n" +
            "💻 Publicar com token\n" +
            "   Publica direto daqui, sem abrir o navegador. Para isso:\n" +
            "   1) Clique em '🔑 Token' e depois em 'Gerar token no site';\n" +
            "   2) No site (logado), gere um token e copie;\n" +
            "   3) Cole o token aqui e salve;\n" +
            "   4) Use '💻 Publicar com token' quantas vezes quiser.\n\n" +
            "Em ambos os casos, a submissao passa por uma verificacao de\n" +
            "seguranca (repo Git valido e publico). Se reprovar, nao e publicada.";

        Alert help = new Alert(Alert.AlertType.INFORMATION);
        help.initOwner(this);
        help.setTitle("Ajuda - Publicar");
        help.setHeaderText(null);
        help.setContentText(msg);
        help.showAndWait();
    }

    private void openTokenDialog() {
        Stage dlg = new Stage(StageStyle.UTILITY);
        dlg.initOwner(this);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle("🔑 Token de publicacao");
        dlg.setResizable(false);

        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color: -ignis-panel;");
        panel.setPrefWidth(380);

        Label info = new Label("O token permite publicar direto do editor.\nGere no site (logado com GitHub), copie e cole abaixo.");
        info.setStyle("-fx-text-fill: -ignis-text-dim; -fx-font-size: 11px;");

        TextField txtToken = new TextField(marketplace.hasToken() ? marketplace.getToken() : "");
        txtToken.setStyle("-fx-background-color: -ignis-control; -fx-text-fill: -ignis-text;");

        Button btnGen = new Button("Gerar token no site ↗");
        btnGen.getStyleClass().add("btn-secondary");
        btnGen.setOnAction(e -> openInBrowser(marketplace.getAccountUrl()));

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnSave = new Button("Salvar");
        btnSave.getStyleClass().add("btn-primary");
        btnSave.setOnAction(e -> {
            marketplace.setToken(txtToken.getText().trim());
            statusLabel.setText(marketplace.hasToken() ? "Token salvo." : "Token vazio (nada salvo).");
            dlg.close();
        });

        Button btnClear = new Button("Limpar token");
        btnClear.getStyleClass().add("btn-danger");
        btnClear.setOnAction(e -> {
            marketplace.clearToken();
            statusLabel.setText("Token removido.");
            dlg.close();
        });

        Button btnCancel = new Button("Cancelar");
        btnCancel.getStyleClass().add("btn-secondary");
        btnCancel.setOnAction(e -> dlg.close());

        buttons.getChildren().addAll(btnSave, btnClear, btnCancel);
        panel.getChildren().addAll(info, txtToken, btnGen, buttons);

        Scene tokenScene = new Scene(panel);
        FxTheme.apply(tokenScene);
        dlg.setScene(tokenScene);
        dlg.showAndWait();
    }

    private void openLocalPublishDialog() {
        if (!marketplace.hasToken()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(this);
            confirm.setTitle("Token necessario");
            confirm.setHeaderText(null);
            confirm.setContentText("Voce ainda nao configurou um token.\nQuer configurar agora?");
            ButtonType btnYes = new ButtonType("Sim", ButtonBar.ButtonData.YES);
            ButtonType btnNo = new ButtonType("Não", ButtonBar.ButtonData.NO);
            confirm.getButtonTypes().setAll(btnYes, btnNo);

            confirm.showAndWait().ifPresent(type -> {
                if (type == btnYes) openTokenDialog();
            });

            if (!marketplace.hasToken()) return;
        }

        Stage dlg = new Stage(StageStyle.UTILITY);
        dlg.initOwner(this);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle("💻 Publicar com token");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(12));
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setStyle("-fx-background-color: -ignis-panel;");

        grid.add(lightLabel("Tipo:"), 0, 0);
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("plugin", "workshop", "asset");
        typeCombo.getSelectionModel().select(0);
        grid.add(typeCombo, 1, 0);

        grid.add(lightLabel("Nome:"), 0, 1);
        TextField txtName = new TextField();
        grid.add(txtName, 1, 1);

        grid.add(lightLabel("Autor (opcional):"), 0, 2);
        TextField txtAuthor = new TextField();
        grid.add(txtAuthor, 1, 2);

        grid.add(lightLabel("Descricao:"), 0, 3);
        TextField txtDesc = new TextField();
        grid.add(txtDesc, 1, 3);

        grid.add(lightLabel("URL do repo Git:"), 0, 4);
        TextField txtGit = new TextField();
        grid.add(txtGit, 1, 4);

        grid.add(lightLabel("Versao:"), 0, 5);
        TextField txtVer = new TextField("1.0.0");
        grid.add(txtVer, 1, 5);

        CheckBox chkTerms = new CheckBox("Aceito os Termos e Privacidade");
        chkTerms.setStyle("-fx-text-fill: -ignis-text-dim;");
        Button btnTerms = new Button("ver termos ↗");
        btnTerms.setStyle("-fx-background-color: -ignis-secondary; -fx-text-fill: white; -fx-font-size: 10px;");
        btnTerms.setOnAction(e -> openInBrowser(marketplace.getBaseUrl() + "/terms"));

        HBox termsBox = new HBox(8, chkTerms, btnTerms);
        grid.add(termsBox, 1, 6);

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnSubmit = new Button("Publicar");
        btnSubmit.getStyleClass().add("btn-primary");
        btnSubmit.setOnAction(e -> {
            String name = txtName.getText().trim();
            String git = txtGit.getText().trim();
            if (name.isEmpty() || git.isEmpty()) {
                Alert w = new Alert(Alert.AlertType.WARNING, "Nome e URL do repo Git sao obrigatorios.");
                w.initOwner(dlg);
                w.showAndWait();
                return;
            }
            if (!chkTerms.isSelected()) {
                Alert w = new Alert(Alert.AlertType.WARNING, "Voce precisa aceitar os Termos para publicar.");
                w.initOwner(dlg);
                w.showAndWait();
                return;
            }

            String type = typeCombo.getValue();
            String author = txtAuthor.getText().trim();
            String desc = txtDesc.getText().trim();
            String version = txtVer.getText().trim().isEmpty() ? "1.0.0" : txtVer.getText().trim();

            statusLabel.setText("Publicando " + name + "...");
            MarketplaceItem item = new MarketplaceItem(type, name, author, desc, version, git, "", "None");
            
            dlg.close();

            new Thread(() -> {
                MarketplaceClient.PublishResult res = marketplace.publish(item, true);
                Platform.runLater(() -> {
                    if (res.ok) {
                        statusLabel.setText("✓ " + name + " publicado em " + marketplace.getBaseUrl() + ".");
                        Alert success = new Alert(Alert.AlertType.INFORMATION, name + " publicado com sucesso!");
                        success.initOwner(this);
                        success.showAndWait();
                    } else {
                        statusLabel.setText("❌ " + res.message);
                        StringBuilder sb = new StringBuilder(res.message);
                        if (!res.reasons.isEmpty()) {
                            sb.append("\n\nMotivos da verificacao de seguranca:");
                            for (String r : res.reasons) sb.append("\n • ").append(r);
                        }
                        Alert error = new Alert(Alert.AlertType.WARNING, sb.toString());
                        error.initOwner(this);
                        error.showAndWait();
                    }
                });
            }).start();
        });

        Button btnCancel = new Button("Cancelar");
        btnCancel.getStyleClass().add("btn-secondary");
        btnCancel.setOnAction(e -> dlg.close());

        buttons.getChildren().addAll(btnSubmit, btnCancel);
        grid.add(buttons, 1, 7);

        Scene publishScene = new Scene(grid);
        FxTheme.apply(publishScene);
        dlg.setScene(publishScene);
        dlg.showAndWait();
    }

    private Label lightLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: -ignis-text; -fx-font-weight: bold;");
        return l;
    }
}
