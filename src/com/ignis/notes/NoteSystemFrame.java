package com.ignis.notes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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
    
    private final JTextArea editorArea;
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

        // Save & AI buttons
        JPanel editorButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        editorButtons.setBackground(new Color(35, 35, 35));

        JButton btnSave = new JButton("💾 Save");
        JButton btnAskAI = new JButton("✨ Ask AI Assistant");
        
        styleBtn(btnSave, new Color(46, 139, 87));
        styleBtn(btnAskAI, new Color(123, 104, 238));

        editorButtons.add(btnSave);
        editorButtons.add(btnAskAI);
        titlePanel.add(editorButtons, BorderLayout.EAST);

        editorPanel.add(titlePanel, BorderLayout.NORTH);

        // Editor text area
        editorArea = new JTextArea();
        editorArea.setBackground(new Color(30, 30, 30));
        editorArea.setForeground(Color.WHITE);
        editorArea.setCaretColor(Color.WHITE);
        editorArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        editorArea.setLineWrap(true);
        editorArea.setWrapStyleWord(true);
        editorArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

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
                    editorArea.setText(activePage.content);
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
                            editorArea.append("\n\n---\n### AI Assistant Response (" + option + "):\n" + response);
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
}
