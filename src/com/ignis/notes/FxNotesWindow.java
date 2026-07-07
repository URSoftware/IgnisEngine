package com.ignis.editor.fx;

import com.ignis.core.IgnisLogger;

import com.ignis.editor.AIIntegration;
import com.ignis.editor.fx.FxTheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.HTMLEditor;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

/**
 * JavaFX implementation of the integrated wiki/notes system.
 * Ports com.ignis.notes.NoteSystemFrame.
 */
public class FxNotesWindow extends Stage {

    private final File notesFolder;
    private final AIIntegration aiIntegration;

    private final TreeView<Object> pageTree;
    private final TreeItem<Object> rootNode;

    private final HTMLEditor editorArea;
    private final TextField titleField;
    private final Label statusLabel;

    private NotePage activePage = null;

    private static class NotePage {
        String title;
        String content;
        File file;

        NotePage(String title, String content, File file) {
            this.title = title;
            this.content = content;
            this.file = file;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public FxNotesWindow(File projectFolder, AIIntegration aiIntegration) {
        setTitle("Ignis Wiki - Note System");
        initModality(Modality.NONE);
        this.aiIntegration = aiIntegration;

        // Determine notes storage folder
        if (projectFolder != null) {
            this.notesFolder = new File(projectFolder, "notes");
        } else {
            this.notesFolder = new File(System.getProperty("user.home"), ".ignis/notes");
        }
        if (!this.notesFolder.exists()) {
            this.notesFolder.mkdirs();
        }

        SplitPane splitPane = new SplitPane();

        // --- Left Sidebar (TreeView for Pages hierarchy) ---
        BorderPane sidebarPanel = new BorderPane();
        sidebarPanel.setStyle("-fx-background-color: -ignis-panel;");

        rootNode = new TreeItem<>("Ignis Project Wiki");
        rootNode.setExpanded(true);
        pageTree = new TreeView<>(rootNode);
        pageTree.setStyle("-fx-background-color: -ignis-panel;");

        // Custom tree cell factory to support clean NotePage title labels
        pageTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        sidebarPanel.setCenter(pageTree);

        HBox sidebarButtons = new HBox(4);
        sidebarButtons.setPadding(new Insets(6));
        sidebarButtons.setStyle("-fx-background-color: -ignis-panel;");
        sidebarButtons.setAlignment(Pos.CENTER);

        Button btnAdd = new Button("📄 New Page");
        Button btnDelete = new Button("🗑 Delete");
        HBox.setHgrow(btnAdd, Priority.ALWAYS);
        HBox.setHgrow(btnDelete, Priority.ALWAYS);
        btnAdd.setMaxWidth(Double.MAX_VALUE);
        btnDelete.setMaxWidth(Double.MAX_VALUE);

        styleBtn(btnAdd, "-ignis-info");
        styleBtn(btnDelete, "-ignis-danger");

        sidebarButtons.getChildren().addAll(btnAdd, btnDelete);
        sidebarPanel.setBottom(sidebarButtons);

        splitPane.getItems().add(sidebarPanel);

        // --- Right Editor Panel ---
        BorderPane editorPanel = new BorderPane();
        editorPanel.setStyle("-fx-background-color: -ignis-bg;");

        // Title and controls toolbar
        BorderPane titlePanel = new BorderPane();
        titlePanel.setPadding(new Insets(8, 12, 8, 12));
        titlePanel.setStyle("-fx-background-color: -ignis-panel;");

        titleField = new TextField("Welcome Page");
        titleField.setStyle("-fx-background-color: -ignis-control; -fx-text-fill: -ignis-text; -fx-font-weight: bold; -fx-font-size: 14px;");
        titlePanel.setCenter(titleField);

        HBox editorButtons = new HBox(6);
        editorButtons.setAlignment(Pos.CENTER_RIGHT);
        BorderPane.setMargin(editorButtons, new Insets(0, 0, 0, 8));

        Button btnSave = new Button("💾 Save");
        Button btnExport = new Button("📥 Export HTML");
        Button btnAskAI = new Button("✨ Ask AI Assistant");

        styleBtn(btnSave, "-ignis-primary");
        styleBtn(btnExport, "-ignis-secondary");
        styleBtn(btnAskAI, "#7b68ee");

        editorButtons.getChildren().addAll(btnSave, btnExport, btnAskAI);
        titlePanel.setRight(editorButtons);

        editorPanel.setTop(titlePanel);

        // Editor Area
        editorArea = new HTMLEditor();
        // Customize styling through inline style if possible, or style rules
        editorArea.setPrefHeight(400);
        editorPanel.setCenter(editorArea);

        // Status bar
        HBox statusBar = new HBox(12);
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setStyle("-fx-background-color: -ignis-panel; -fx-border-color: -ignis-border; -fx-border-width: 1 0 0 0;");
        statusLabel = new Label("Wiki system initialized.");
        statusLabel.setStyle("-fx-text-fill: -ignis-text-dim; -fx-font-size: 11px;");
        statusBar.getChildren().add(statusLabel);
        editorPanel.setBottom(statusBar);

        splitPane.getItems().add(editorPanel);
        splitPane.setDividerPositions(0.25);

        Scene scene = new Scene(splitPane, 850, 550);
        FxTheme.apply(scene);
        setScene(scene);

        // A area de edicao do HTMLEditor e um WebView com pagina branca por padrao.
        // Tematiza via user stylesheet (segue o tema atual) e re-aplica quando o
        // tema troca (FxTheme alterna a styleClass "ignis-light" na raiz).
        setOnShown(e -> Platform.runLater(this::applyEditorDarkStyle));
        splitPane.getStyleClass().addListener(
                (javafx.collections.ListChangeListener<String>) c -> Platform.runLater(this::applyEditorDarkStyle));

        // --- Load Notes ---
        loadNotesFromDisk();

        // --- Listeners & Actions ---
        pageTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() instanceof NotePage) {
                activePage = (NotePage) newVal.getValue();
                titleField.setText(activePage.title);
                editorArea.setHtmlText(ensureHtml(activePage.content));
                statusLabel.setText("Note loaded: " + activePage.title);
            }
        });

        btnAdd.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.initOwner(this);
            dialog.setTitle("New Wiki Page");
            dialog.setHeaderText("Create a new wiki page");
            dialog.setContentText("Enter Page Title:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(title -> {
                if (!title.trim().isEmpty()) {
                    String cleanTitle = title.trim();
                    File pageFile = new File(notesFolder, cleanTitle.replace(' ', '_').toLowerCase() + ".json");
                    NotePage page = new NotePage(cleanTitle, "<h1>" + cleanTitle + "</h1><p>Write your wiki page here...</p>", pageFile);

                    TreeItem<Object> node = new TreeItem<>(page);
                    rootNode.getChildren().add(node);
                    pageTree.getSelectionModel().select(node);
                    saveActivePage();
                }
            });
        });

        btnDelete.setOnAction(e -> {
            TreeItem<Object> selectedItem = pageTree.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getValue() instanceof NotePage) {
                NotePage page = (NotePage) selectedItem.getValue();

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.initOwner(this);
                confirm.setTitle("Delete Note");
                confirm.setHeaderText(null);
                confirm.setContentText("Are you sure you want to delete note '" + page.title + "'?");

                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isPresent() && opt.get() == ButtonType.OK) {
                    if (page.file.exists()) {
                        page.file.delete();
                    }
                    rootNode.getChildren().remove(selectedItem);
                    titleField.setText("");
                    editorArea.setHtmlText("");
                    activePage = null;
                    statusLabel.setText("Note deleted.");
                }
            }
        });

        btnSave.setOnAction(e -> saveActivePage());
        btnExport.setOnAction(e -> exportActivePageToHtml());

        btnAskAI.setOnAction(e -> {
            if (activePage == null) {
                Alert w = new Alert(Alert.AlertType.WARNING, "Please select or create a page first.");
                w.initOwner(this);
                w.showAndWait();
                return;
            }
            if (aiIntegration == null || !aiIntegration.hasApiKey()) {
                Alert err = new Alert(Alert.AlertType.ERROR, "AI integration is not configured. Set your API Key in Settings first.");
                err.initOwner(this);
                err.showAndWait();
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>("Summarize Page",
                    "Summarize Page", "Fix Spelling & Grammar", "Generate Code from Note", "Suggest Optimizations");
            dialog.initOwner(this);
            dialog.setTitle("Ask Wiki Assistant");
            dialog.setHeaderText("What would you like the AI Assistant to do with this page?");
            dialog.setContentText("Choose action:");

            Optional<String> option = dialog.showAndWait();
            option.ifPresent(opt -> {
                statusLabel.setText("⏳ Calling AI Assistant (" + aiIntegration.getActiveProviderName() + ")...");
                new Thread(() -> {
                    try {
                        String prompt = "You are an AI assistant for the Ignis Game Engine.\n\n" +
                                "TASK: " + opt + "\n\n" +
                                "NOTE TEXT:\n" + editorArea.getHtmlText() + "\n\n" +
                                "Please provide a clear and structured response directly answering the task.";

                        String response = aiIntegration.callActiveAI(prompt);

                        Platform.runLater(() -> {
                            String responseHtml = "<hr><p><b>AI Assistant Response (" + opt + "):</b></p><p>" +
                                    response.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>";
                            editorArea.setHtmlText(editorArea.getHtmlText() + responseHtml);
                            statusLabel.setText("✓ AI Assistant completed task.");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            statusLabel.setText("❌ AI Error: " + ex.getMessage());
                            Alert err = new Alert(Alert.AlertType.ERROR, "AI Assistant error: " + ex.getMessage());
                            err.initOwner(this);
                            err.showAndWait();
                        });
                    }
                }).start();
            });
        });
    }

    private void styleBtn(Button btn, String hexColor) {
        btn.setStyle("-fx-background-color: " + hexColor + "; -fx-text-fill: white; -fx-padding: 6 10; -fx-background-radius: 4px;");
    }

    private void saveActivePage() {
        if (activePage == null) return;

        try {
            activePage.title = titleField.getText().trim();
            activePage.content = editorArea.getHtmlText();

            JSONObject json = new JSONObject();
            json.put("title", activePage.title);
            json.put("content", activePage.content);

            Files.write(activePage.file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("✓ Page saved successfully.");

            // Refresh tree node label
            TreeItem<Object> selected = pageTree.getSelectionModel().getSelectedItem();
            if (selected != null) {
                // Trigger refresh by temporary clear/re-set
                Object val = selected.getValue();
                selected.setValue(null);
                selected.setValue(val);
            }
        } catch (Exception ex) {
            statusLabel.setText("❌ Save failed: " + ex.getMessage());
            Alert error = new Alert(Alert.AlertType.ERROR, "Failed to save page: " + ex.getMessage());
            error.initOwner(this);
            error.showAndWait();
        }
    }

    private void loadNotesFromDisk() {
        File[] files = notesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(content);
                    String title = json.getString("title");
                    String pageContent = json.getString("content");

                    NotePage page = new NotePage(title, pageContent, file);
                    TreeItem<Object> node = new TreeItem<>(page);
                    rootNode.getChildren().add(node);
                } catch (Exception e) {
                    IgnisLogger.error("Failed to read note file: " + file.getName());
                }
            }
        }
    }

    // Aplica um user stylesheet ao WebView interno do HTMLEditor para que a area
    // de edicao siga o tema (fundo escuro/claro + texto contrastante), sem alterar
    // o HTML salvo das notas. Re-chamavel (no show e ao trocar de tema).
    private void applyEditorDarkStyle() {
        javafx.scene.Node n = editorArea.lookup(".web-view");
        if (!(n instanceof javafx.scene.web.WebView)) return;
        javafx.scene.web.WebView wv = (javafx.scene.web.WebView) n;
        boolean dark = FxTheme.isDark();
        String bg = dark ? "#1e1e1e" : "#ffffff";
        String fg = dark ? "#e0e0e0" : "#1e1e1e";
        String link = dark ? "#5aa0ff" : "#1a5fb4";
        String css = "html,body{background:" + bg + " !important;color:" + fg + " !important;}"
                + "a{color:" + link + ";}";
        String dataUri = "data:text/css;base64,"
                + java.util.Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
        wv.getEngine().setUserStyleSheetLocation(dataUri);
    }

    private String ensureHtml(String content) {
        if (content == null) return "";
        if (content.toLowerCase().contains("<html>") || content.toLowerCase().contains("<body>")) {
            return content;
        }
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>body { font-family: Arial, sans-serif; font-size: 13px; color: #ffffff; background-color: #1e1e1e; }</style></head><body>");
        String[] paragraphs = content.split("\n");
        for (String p : paragraphs) {
            if (!p.trim().isEmpty()) {
                html.append("<p>").append(p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append("</p>");
            } else {
                html.append("<br>");
            }
        }
        html.append("</body></html>");
        return html.toString();
    }

    private void exportActivePageToHtml() {
        if (activePage == null) {
            Alert w = new Alert(Alert.AlertType.WARNING, "No page active to export.");
            w.initOwner(this);
            w.showAndWait();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Note to HTML");
        chooser.setInitialFileName(activePage.title.replace(' ', '_').toLowerCase() + ".html");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files (*.html)", "*.html"));

        File dest = chooser.showSaveDialog(this);
        if (dest != null) {
            try {
                String rawContent = editorArea.getHtmlText();
                String htmlText;
                if (rawContent.toLowerCase().contains("<html>")) {
                    if (!rawContent.toLowerCase().contains("<style>")) {
                        htmlText = rawContent.replace("</head>", "<style>" +
                                "body { font-family: Arial, sans-serif; font-size: 14px; color: #ffffff; background-color: #1e1e1e; padding: 20px; }" +
                                "ul { list-style-type: disc; margin-left: 25px; }" +
                                "ol { list-style-type: decimal; margin-left: 25px; }" +
                                "li { margin-top: 4px; }" +
                                "p { margin: 8px 0; }" +
                                "</style></head>");
                    } else {
                        htmlText = rawContent;
                    }
                } else {
                    htmlText = "<html><head><style>" +
                            "body { font-family: Arial, sans-serif; font-size: 14px; color: #ffffff; background-color: #1e1e1e; padding: 20px; }" +
                            "ul { list-style-type: disc; margin-left: 25px; }" +
                            "ol { list-style-type: decimal; margin-left: 25px; }" +
                            "li { margin-top: 4px; }" +
                            "p { margin: 8px 0; }" +
                            "</style></head><body>" + rawContent + "</body></html>";
                }
                Files.write(dest.toPath(), htmlText.getBytes(StandardCharsets.UTF_8));
                Alert info = new Alert(Alert.AlertType.INFORMATION, "Exported successfully to " + dest.getName());
                info.initOwner(this);
                info.showAndWait();
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage());
                err.initOwner(this);
                err.showAndWait();
            }
        }
    }
}
