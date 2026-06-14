package com.ignis.notes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.text.*;
import javax.swing.text.html.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import com.ignis.editor.AIIntegration;

/**
 * Integrated Notion-like Note and Documentation System.
 * Implements Item 7 of the IgnisEngine roadmap: hierarchical pages, wiki,
 * task management, document sharing, and direct Gemini AI copilot integration.
 */
public class NoteSystemFrame extends JFrame {

    private final File notesFolder;
    private final AIIntegration aiIntegration;
    
    private final JTree pageTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    
    private final JTextPane editorArea;
    private final JTextField titleField;
    private final JLabel statusLabel;
    
    private NotePage activePage = null;

    // --- Note Page Data Model ---
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

    public NoteSystemFrame(File projectFolder, AIIntegration aiIntegration) {
        super("Ignis Wiki - Note System");
        com.ignis.core.AppIconHelper.setWindowIcon(this);
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

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(850, 550);
        setLocationRelativeTo(null);

        // Layout splits sidebar (left) and editor (right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(220);

        // --- Left Sidebar (JTree for Pages hierarchy) ---
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setBackground(new Color(45, 45, 45));

        rootNode = new DefaultMutableTreeNode("Ignis Project Wiki");
        treeModel = new DefaultTreeModel(rootNode);
        pageTree = new JTree(treeModel);
        pageTree.setBackground(new Color(45, 45, 45));
        pageTree.setForeground(Color.WHITE);
        
        // Style tree node rendering
        javax.swing.tree.DefaultTreeCellRenderer renderer = new javax.swing.tree.DefaultTreeCellRenderer();
        renderer.setBackgroundNonSelectionColor(new Color(45, 45, 45));
        renderer.setBackgroundSelectionColor(new Color(70, 130, 180));
        renderer.setTextSelectionColor(Color.WHITE);
        renderer.setTextNonSelectionColor(Color.WHITE);
        pageTree.setCellRenderer(renderer);

        JScrollPane treeScroll = new JScrollPane(pageTree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        sidebarPanel.add(treeScroll, BorderLayout.CENTER);

        // Sidebar buttons (Add/Delete page)
        JPanel sidebarButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        sidebarButtons.setBackground(new Color(40, 40, 40));
        sidebarButtons.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JButton btnAdd = new JButton("📄 New Page");
        JButton btnDelete = new JButton("🗑 Delete");
        
        styleBtn(btnAdd, new Color(70, 130, 180));
        styleBtn(btnDelete, new Color(178, 34, 34));

        sidebarButtons.add(btnAdd);
        sidebarButtons.add(btnDelete);
        sidebarPanel.add(sidebarButtons, BorderLayout.SOUTH);

        splitPane.setLeftComponent(sidebarPanel);

        // --- Right Editor Panel ---
        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBackground(new Color(30, 30, 30));

        // Note title bar
        JPanel titlePanel = new JPanel(new BorderLayout(8, 0));
        titlePanel.setBackground(new Color(35, 35, 35));
        titlePanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        titleField = new JTextField("Welcome Page");
        titleField.setBackground(new Color(55, 55, 55));
        titleField.setForeground(Color.WHITE);
        titleField.setCaretColor(Color.WHITE);
        titleField.setFont(new Font("Arial", Font.BOLD, 15));
        titleField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        titlePanel.add(titleField, BorderLayout.CENTER);

        // Save, Export & AI buttons
        JPanel editorButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        editorButtons.setBackground(new Color(35, 35, 35));

        JButton btnSave = new JButton("💾 Save");
        JButton btnExport = new JButton("📥 Export HTML");
        JButton btnAskAI = new JButton("✨ Ask AI Assistant");
        
        styleBtn(btnSave, new Color(46, 139, 87));
        styleBtn(btnExport, new Color(100, 100, 100));
        styleBtn(btnAskAI, new Color(123, 104, 238));

        editorButtons.add(btnSave);
        editorButtons.add(btnExport);
        editorButtons.add(btnAskAI);
        titlePanel.add(editorButtons, BorderLayout.EAST);

        // Put titlePanel and formatToolbar in northPanel
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(titlePanel, BorderLayout.NORTH);

        // Editor text area
        editorArea = new JTextPane();
        editorArea.setContentType("text/html");
        editorArea.setBackground(new Color(30, 30, 30));
        editorArea.setForeground(Color.WHITE);
        editorArea.setCaretColor(Color.WHITE);
        editorArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Configure default styles for HTML in JTextPane
        HTMLDocument doc = (HTMLDocument) editorArea.getDocument();
        StyleSheet stylesheet = doc.getStyleSheet();
        stylesheet.addRule("body { font-family: Arial, sans-serif; font-size: 13px; color: #ffffff; background-color: #1e1e1e; }");
        stylesheet.addRule("p { margin-top: 4px; margin-bottom: 4px; }");
        stylesheet.addRule("ul { list-style-type: disc; margin-left: 25px; margin-top: 4px; margin-bottom: 4px; }");
        stylesheet.addRule("ol { list-style-type: decimal; margin-left: 25px; margin-top: 4px; margin-bottom: 4px; }");
        stylesheet.addRule("li { margin-top: 2px; margin-bottom: 2px; }");

        // Action when pressing Enter: smarter list behavior
        Action defaultEnterAction = editorArea.getActionMap().get(DefaultEditorKit.insertBreakAction);
        editorArea.getActionMap().put(DefaultEditorKit.insertBreakAction, new TextAction(DefaultEditorKit.insertBreakAction) {
            @Override
            public void actionPerformed(ActionEvent ev) {
                JTextPane pane = (JTextPane) getTextComponent(ev);
                if (pane == null) return;
                try {
                    int pos = pane.getCaretPosition();
                    HTMLDocument htmlDoc = (HTMLDocument) pane.getDocument();
                    Element elem = htmlDoc.getCharacterElement(pos);
                    Element li = null;
                    Element temp = elem;
                    while (temp != null) {
                        if (temp.getName().equalsIgnoreCase("li")) {
                            li = temp;
                            break;
                        }
                        temp = temp.getParentElement();
                    }
                    if (li != null) {
                        int start = li.getStartOffset();
                        int end = li.getEndOffset();
                        String content = htmlDoc.getText(start, end - start).replace("\r", "").replace("\n", "").trim();
                        if (content.isEmpty() || content.equalsIgnoreCase("Item")) {
                            // Empty list item: exit list
                            pane.select(start, end);
                            pane.replaceSelection("");
                            Element listParent = li.getParentElement();
                            int listEnd = listParent.getEndOffset();
                            pane.setCaretPosition(Math.min(listEnd, pane.getDocument().getLength()));
                            HTMLEditorKit kit = (HTMLEditorKit) pane.getEditorKit();
                            kit.insertHTML(htmlDoc, pane.getCaretPosition(), "<p>&nbsp;</p>", 0, 0, HTML.Tag.P);
                        } else {
                            // Continue list
                            HTMLEditorKit kit = (HTMLEditorKit) pane.getEditorKit();
                            kit.insertHTML(htmlDoc, pos, "<li></li>", 0, 0, HTML.Tag.LI);
                            pane.setCaretPosition(pos + 1);
                        }
                    } else {
                        defaultEnterAction.actionPerformed(ev);
                    }
                } catch (Exception ex) {
                    defaultEnterAction.actionPerformed(ev);
                }
            }
        });

        // KeyListener for automatic list conversions (typing "- " or "1. ")
        editorArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(java.awt.event.KeyEvent ev) {
                if (ev.getKeyChar() == ' ') {
                    int pos = editorArea.getCaretPosition();
                    try {
                        HTMLDocument htmlDoc = (HTMLDocument) editorArea.getDocument();
                        Element paragraph = htmlDoc.getParagraphElement(pos);
                        int start = paragraph.getStartOffset();
                        String text = htmlDoc.getText(start, pos - start);
                        if (text.equals("-")) {
                            editorArea.select(start, pos);
                            editorArea.replaceSelection("");
                            insertList(false);
                            ev.consume();
                        } else if (text.equals("1.")) {
                            editorArea.select(start, pos);
                            editorArea.replaceSelection("");
                            insertList(true);
                            ev.consume();
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
            }
        });

        btnExport.addActionListener(e -> exportActivePageToHtml());

        // Format Toolbar
        JToolBar formatToolbar = new JToolBar();
        formatToolbar.setBackground(new Color(40, 40, 40));
        formatToolbar.setFloatable(false);
        formatToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        JButton btnBold = new JButton("B");
        btnBold.setFont(new Font("Arial", Font.BOLD, 12));
        styleToolbarButton(btnBold);
        btnBold.addActionListener(e -> new StyledEditorKit.BoldAction().actionPerformed(e));

        JButton btnItalic = new JButton("I");
        btnItalic.setFont(new Font("Arial", Font.ITALIC, 12));
        styleToolbarButton(btnItalic);
        btnItalic.addActionListener(e -> new StyledEditorKit.ItalicAction().actionPerformed(e));

        JButton btnUnderline = new JButton("U");
        btnUnderline.setFont(new Font("Arial", Font.PLAIN, 12));
        styleToolbarButton(btnUnderline);
        btnUnderline.addActionListener(e -> new StyledEditorKit.UnderlineAction().actionPerformed(e));

        JButton btnStrike = new JButton("S");
        btnStrike.setFont(new Font("Arial", Font.PLAIN, 12));
        styleToolbarButton(btnStrike);
        btnStrike.addActionListener(e -> {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            boolean isStrikethrough = false;
            int start = editorArea.getSelectionStart();
            int end = editorArea.getSelectionEnd();
            if (start != end) {
                StyledDocument sdoc = editorArea.getStyledDocument();
                AttributeSet currentAttr = sdoc.getCharacterElement(start).getAttributes();
                isStrikethrough = StyleConstants.isStrikeThrough(currentAttr);
            }
            StyleConstants.setStrikeThrough(attr, !isStrikethrough);
            editorArea.setCharacterAttributes(attr, false);
        });

        String[] families = {"Arial", "Times New Roman", "Courier New", "Comic Sans MS"};
        JComboBox<String> comboFamily = new JComboBox<>(families);
        comboFamily.setBackground(new Color(55, 55, 55));
        comboFamily.setForeground(Color.WHITE);
        comboFamily.setMaximumSize(new Dimension(120, 24));
        comboFamily.addActionListener(e -> {
            String family = (String) comboFamily.getSelectedItem();
            new StyledEditorKit.FontFamilyAction("FontFamily", family).actionPerformed(e);
        });

        String[] sizes = {"12", "14", "16", "18", "24", "32"};
        JComboBox<String> comboSize = new JComboBox<>(sizes);
        comboSize.setBackground(new Color(55, 55, 55));
        comboSize.setForeground(Color.WHITE);
        comboSize.setMaximumSize(new Dimension(60, 24));
        comboSize.addActionListener(e -> {
            int size = Integer.parseInt((String) comboSize.getSelectedItem());
            new StyledEditorKit.FontSizeAction("FontSize", size).actionPerformed(e);
        });

        JButton btnBulletList = new JButton("• List");
        styleToolbarButton(btnBulletList);
        btnBulletList.addActionListener(e -> insertList(false));

        JButton btnNumList = new JButton("1. List");
        styleToolbarButton(btnNumList);
        btnNumList.addActionListener(e -> insertList(true));

        formatToolbar.add(btnBold);
        formatToolbar.add(btnItalic);
        formatToolbar.add(btnUnderline);
        formatToolbar.add(btnStrike);
        formatToolbar.add(Box.createHorizontalStrut(8));
        formatToolbar.add(new JLabel("Font: "));
        formatToolbar.add(comboFamily);
        formatToolbar.add(Box.createHorizontalStrut(5));
        formatToolbar.add(new JLabel("Size: "));
        formatToolbar.add(comboSize);
        formatToolbar.add(Box.createHorizontalStrut(8));
        formatToolbar.add(btnBulletList);
        formatToolbar.add(btnNumList);

        northPanel.add(formatToolbar, BorderLayout.SOUTH);
        editorPanel.add(northPanel, BorderLayout.NORTH);

        JScrollPane editorScroll = new JScrollPane(editorArea);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorPanel.add(editorScroll, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        statusBar.setBackground(new Color(40, 40, 40));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)));
        statusLabel = new JLabel("Wiki system initialized.");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusBar.add(statusLabel);
        editorPanel.add(statusBar, BorderLayout.SOUTH);

        splitPane.setRightComponent(editorPanel);
        add(splitPane, BorderLayout.CENTER);

        // --- Load existing notes ---
        loadNotesFromDisk();

        // --- Listeners & Actions ---
        pageTree.addTreeSelectionListener(e -> {
            TreePath path = pageTree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (selectedNode.getUserObject() instanceof NotePage) {
                    activePage = (NotePage) selectedNode.getUserObject();
                    titleField.setText(activePage.title);
                    editorArea.setText(ensureHtml(activePage.content));
                    statusLabel.setText("Note loaded: " + activePage.title);
                }
            }
        });

        btnAdd.addActionListener(e -> {
            String title = JOptionPane.showInputDialog(this, "Enter Page Title:", "New Wiki Page", JOptionPane.QUESTION_MESSAGE);
            if (title != null && !title.trim().isEmpty()) {
                title = title.trim();
                File pageFile = new File(notesFolder, title.replace(' ', '_').toLowerCase() + ".json");
                NotePage page = new NotePage(title, "# " + title + "\n\nWrite your wiki page here...", pageFile);
                
                // Add to tree
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(page);
                treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
                pageTree.scrollPathToVisible(new TreePath(node.getPath()));
                
                // Select newly created page
                pageTree.setSelectionPath(new TreePath(node.getPath()));
                
                // Auto-save to disk
                saveActivePage();
            }
        });

        btnDelete.addActionListener(e -> {
            TreePath path = pageTree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (selectedNode.getUserObject() instanceof NotePage) {
                    NotePage page = (NotePage) selectedNode.getUserObject();
                    int confirm = JOptionPane.showConfirmDialog(this,
                            "Are you sure you want to delete note '" + page.title + "'?",
                            "Delete Note", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm == JOptionPane.YES_OPTION) {
                        if (page.file.exists()) {
                            page.file.delete();
                        }
                        treeModel.removeNodeFromParent(selectedNode);
                        titleField.setText("");
                        editorArea.setText("");
                        activePage = null;
                        statusLabel.setText("Note deleted.");
                    }
                }
            }
        });

        btnSave.addActionListener(e -> saveActivePage());

        btnAskAI.addActionListener(e -> {
            if (activePage == null) {
                JOptionPane.showMessageDialog(this, "Please select or create a page first.", "No Page", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (aiIntegration == null || !aiIntegration.hasApiKey()) {
                JOptionPane.showMessageDialog(this, "AI integration is not configured. Set your API Key in Settings first.", "AI Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String option = (String) JOptionPane.showInputDialog(this,
                    "What would you like the AI Assistant to do with this page?",
                    "Ask Wiki Assistant",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Summarize Page", "Fix Spelling & Grammar", "Generate Code from Note", "Suggest Optimizations"},
                    "Summarize Page");

            if (option != null) {
                statusLabel.setText("⏳ Calling AI Assistant (" + aiIntegration.getActiveProviderName() + ")...");
                new Thread(() -> {
                    try {
                        String prompt = "You are an AI assistant for the Ignis Game Engine.\n\n" +
                                "TASK: " + option + "\n\n" +
                                "NOTE TEXT:\n" + editorArea.getText() + "\n\n" +
                                "Please provide a clear and structured response directly answering the task.";

                        String response = aiIntegration.callActiveAI(prompt);

                        SwingUtilities.invokeLater(() -> {
                            // Insert AI response at bottom of the page or replace it
                            try {
                                HTMLDocument htmlDoc = (HTMLDocument) editorArea.getDocument();
                                HTMLEditorKit kit = (HTMLEditorKit) editorArea.getEditorKit();
                                String responseHtml = "<hr><p><b>AI Assistant Response (" + option + "):</b></p><p>" +
                                        response.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>";
                                kit.insertHTML(htmlDoc, htmlDoc.getLength(), responseHtml, 0, 0, null);
                            } catch (Exception ex) {
                                // Fallback to plain text modification if HTML insert fails
                                editorArea.setText(editorArea.getText() + "\n\n---\nAI Assistant Response (" + option + "):\n" + response);
                            }
                            statusLabel.setText("✓ AI Assistant completed task.");
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("❌ AI Error: " + ex.getMessage());
                            JOptionPane.showMessageDialog(this, "AI Assistant error: " + ex.getMessage(), "AI Error", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            }
        });
    }

    private void styleBtn(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    }

    private void saveActivePage() {
        if (activePage == null) return;
        
        try {
            activePage.title = titleField.getText().trim();
            activePage.content = editorArea.getText();
            
            JSONObject json = new JSONObject();
            json.put("title", activePage.title);
            json.put("content", activePage.content);
            
            Files.write(activePage.file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("✓ Page saved successfully.");
            
            // Refresh tree to show name changes
            treeModel.nodeChanged((DefaultMutableTreeNode) pageTree.getLastSelectedPathComponent());
        } catch (Exception ex) {
            statusLabel.setText("❌ Save failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to save page: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(page);
                    rootNode.add(node);
                } catch (IOException e) {
                    System.err.println("Failed to read note file: " + file.getName());
                }
            }
            treeModel.reload();
            // Expand tree
            for (int i = 0; i < pageTree.getRowCount(); i++) {
                pageTree.expandRow(i);
            }
        }
    }

    private void styleToolbarButton(JButton btn) {
        btn.setBackground(new Color(55, 55, 55));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    }

    private void insertList(boolean ordered) {
        String tag = ordered ? "<ol><li>Item</li></ol>" : "<ul><li>Item</li></ul>";
        try {
            HTMLDocument doc = (HTMLDocument) editorArea.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) editorArea.getEditorKit();
            kit.insertHTML(doc, editorArea.getCaretPosition(), tag, 0, 0, ordered ? HTML.Tag.OL : HTML.Tag.UL);
        } catch (Exception ex) {
            System.err.println("Error inserting list: " + ex.getMessage());
        }
    }

    private String ensureHtml(String content) {
        if (content == null) return "";
        if (content.toLowerCase().contains("<html>") || content.toLowerCase().contains("<body>")) {
            return content;
        }
        // Simple plain text conversion to HTML for backward compatibility
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial, sans-serif; font-size: 13px; color: #ffffff; background-color: #1e1e1e;'>");
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
            JOptionPane.showMessageDialog(this, "No page active to export.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Note to HTML");
        chooser.setSelectedFile(new File(activePage.title.replace(' ', '_').toLowerCase() + ".html"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HTML Files (*.html)", "html"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try {
                String rawContent = editorArea.getText();
                String htmlText;
                if (rawContent.toLowerCase().contains("<html>")) {
                    // Inject styling inside head if missing
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
                JOptionPane.showMessageDialog(this, "Exported successfully to " + dest.getName(), "Export Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
