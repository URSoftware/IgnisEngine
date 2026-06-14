package com.ignis.editor;

import com.ignis.core.ScriptManager;
import com.ignis.core.ui.VectorIcon;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class ScriptEditorWindow extends JFrame {

    private final Editor editor;
    private final ScriptManager scriptManager;
    private final String scriptName;
    
    private EditorTextPane textArea;
    private JTextArea lineNumbers;
    private JLabel statusLabel;
    private JToggleButton autocompleteToggle;
    private AutocompleteManager autocompleteManager;
    
    private boolean modified = false;
    private Timer autoSaveTimer;

    public ScriptEditorWindow(Editor editor, ScriptManager scriptManager, String scriptName) {
        super("Script Editor - " + scriptName);
        this.editor = editor;
        this.scriptManager = scriptManager;
        this.scriptName = scriptName;

        setSize(800, 600);
        setLocationRelativeTo(editor);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        setupUI();
        setupAutoSave();
        setupWindowListeners();
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(40, 40, 40));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBackground(new Color(50, 50, 50));

        JButton saveBtn = new JButton("Save", new VectorIcon(VectorIcon.VectorIconType.SAVE, 14));
        saveBtn.setBackground(new Color(60, 120, 60));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorderPainted(false);

        JButton compileBtn = new JButton("Compile", new VectorIcon(VectorIcon.VectorIconType.COMPILE, 14));
        compileBtn.setBackground(new Color(60, 100, 150));
        compileBtn.setForeground(Color.WHITE);
        compileBtn.setFocusPainted(false);
        compileBtn.setBorderPainted(false);

        JButton saveAndCompileBtn = new JButton("Save & Compile", new VectorIcon(VectorIcon.VectorIconType.SAVE, 14));
        saveAndCompileBtn.setBackground(new Color(100, 80, 150));
        saveAndCompileBtn.setForeground(Color.WHITE);
        saveAndCompileBtn.setFocusPainted(false);
        saveAndCompileBtn.setBorderPainted(false);

        // Autocomplete toggle button
        autocompleteToggle = new JToggleButton("Auto Complete", true);
        autocompleteToggle.setFocusPainted(false);
        autocompleteToggle.setBackground(new Color(80, 80, 80));
        autocompleteToggle.setForeground(Color.WHITE);
        autocompleteToggle.setSelected(true);
        autocompleteToggle.addActionListener(e -> {
            boolean active = autocompleteToggle.isSelected();
            autocompleteManager.setEnabled(active);
            statusLabel.setText(active ? " Auto-complete active" : " Auto-complete disabled");
        });

        toolbar.add(saveBtn);
        toolbar.add(compileBtn);
        toolbar.add(saveAndCompileBtn);
        toolbar.add(new JSeparator(JSeparator.VERTICAL));
        toolbar.add(autocompleteToggle);

        toolbar.add(new JSeparator(JSeparator.VERTICAL));
        
        JComboBox<String> themeBox = new JComboBox<>(new String[] {
            "Dracula", "Monokai", "One Dark", "Solarized Dark", "Classic Dark", "Classic Light"
        });
        themeBox.setBackground(new Color(50, 50, 50));
        themeBox.setForeground(Color.WHITE);
        themeBox.setSelectedItem("Classic Dark");
        toolbar.add(themeBox);

        JButton importThemeBtn = new JButton("Import Theme");
        importThemeBtn.setBackground(new Color(70, 70, 70));
        importThemeBtn.setForeground(Color.WHITE);
        importThemeBtn.setFocusPainted(false);
        toolbar.add(importThemeBtn);

        JButton exportThemeBtn = new JButton("Export Theme");
        exportThemeBtn.setBackground(new Color(70, 70, 70));
        exportThemeBtn.setForeground(Color.WHITE);
        exportThemeBtn.setFocusPainted(false);
        toolbar.add(exportThemeBtn);

        // Text editor area
        String content = scriptManager.readScriptContent(scriptName);
        if (content == null) {
            content = "";
        }
        textArea = new EditorTextPane();
        textArea.setText(content);

        // Line numbers
        lineNumbers = new JTextArea("1");
        lineNumbers.setBackground(textArea.getBackground().darker());
        lineNumbers.setForeground(textArea.getForeground().darker());
        lineNumbers.setFont(new Font("Consolas", Font.PLAIN, 14));
        lineNumbers.setEditable(false);
        lineNumbers.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        // Theme Box action listener
        themeBox.addActionListener(evt -> {
            String selected = (String) themeBox.getSelectedItem();
            if (selected == null) return;
            switch (selected) {
                case "Dracula" -> textArea.setTheme(EditorTextPane.DRACULA);
                case "Monokai" -> textArea.setTheme(EditorTextPane.MONOKAI);
                case "One Dark" -> textArea.setTheme(EditorTextPane.ONE_DARK);
                case "Solarized Dark" -> textArea.setTheme(EditorTextPane.SOLARIZED_DARK);
                case "Classic Dark" -> textArea.setTheme(EditorTextPane.CLASSIC_DARK);
                case "Classic Light" -> textArea.setTheme(EditorTextPane.CLASSIC_LIGHT);
            }
            lineNumbers.setBackground(textArea.getBackground().darker());
            lineNumbers.setForeground(textArea.getForeground().darker());
        });

        // Import/Export Theme actions
        importThemeBtn.addActionListener(evt -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(ScriptEditorWindow.this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.nio.file.Path path = fileChooser.getSelectedFile().toPath();
                    String json = java.nio.file.Files.readString(path);
                    EditorTextPane.EditorTheme customTheme = EditorTextPane.importThemeFromJson(json);
                    textArea.setTheme(customTheme);
                    lineNumbers.setBackground(textArea.getBackground().darker());
                    lineNumbers.setForeground(textArea.getForeground().darker());
                    statusLabel.setText(" ✓ Custom theme '" + customTheme.name + "' loaded");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ScriptEditorWindow.this, "Error loading theme: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        exportThemeBtn.addActionListener(evt -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(textArea.getActiveTheme().name.toLowerCase().replace(" ", "_") + "_theme.json"));
            if (fileChooser.showSaveDialog(ScriptEditorWindow.this) == JFileChooser.APPROVE_OPTION) {
                try {
                    String json = EditorTextPane.exportThemeToJson(textArea.getActiveTheme());
                    java.nio.file.Path path = fileChooser.getSelectedFile().toPath();
                    java.nio.file.Files.writeString(path, json);
                    statusLabel.setText(" ✓ Theme exported to " + fileChooser.getSelectedFile().getName());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ScriptEditorWindow.this, "Error exporting theme: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Update line numbers and tracking modifications
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                modified = true;
                updateLineNumbers();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                modified = true;
                updateLineNumbers();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                // Ignore style changes, but track modifications if they are edits
            }

            private void updateLineNumbers() {
                int lines = textArea.getText().split("\n", -1).length;
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= lines; i++) {
                    sb.append(i).append("\n");
                }
                lineNumbers.setText(sb.toString());
            }
        });

        // Initialize line numbers
        int lines = textArea.getText().split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i).append("\n");
        }
        lineNumbers.setText(sb.toString());

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.setBorder(null);

        // Status bar
        statusLabel = new JLabel(" Ready");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(50, 50, 50));
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Button Actions
        saveBtn.addActionListener(e -> saveScript());
        compileBtn.addActionListener(e -> compileScript());
        saveAndCompileBtn.addActionListener(e -> {
            if (saveScript()) {
                compileScript();
            }
        });

        // Shortcuts
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        textArea.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveScript();
            }
        });

        // Autocomplete
        autocompleteManager = new AutocompleteManager(textArea, this);

        mainPanel.add(toolbar, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void setupAutoSave() {
        // 30-second timer to auto-save if modified
        autoSaveTimer = new Timer(30000, e -> {
            if (editor.isAutoSaveScriptsEnabled() && modified) {
                saveScriptSilently();
            }
        });
        autoSaveTimer.start();
    }

    private void setupWindowListeners() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (modified) {
                    if (editor.isAutoSaveScriptsEnabled()) {
                        saveScript();
                        closeEditor();
                    } else {
                        int choice = JOptionPane.showConfirmDialog(
                            ScriptEditorWindow.this,
                            "Script '" + scriptName + "' has unsaved changes. Save now?",
                            "Unsaved Changes",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE
                        );
                        if (choice == JOptionPane.YES_OPTION) {
                            if (saveScript()) {
                                closeEditor();
                            }
                        } else if (choice == JOptionPane.NO_OPTION) {
                            closeEditor();
                        }
                    }
                } else {
                    closeEditor();
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                // Save automatically on focus loss (window deactivated/switched)
                if (editor.isAutoSaveScriptsEnabled() && modified) {
                    saveScriptSilently();
                }
            }
        });
    }

    /**
     * Saves script file with UI status update.
     * @return true if save succeeded
     */
    public boolean saveScript() {
        if (scriptManager.saveScriptContent(scriptName, textArea.getText())) {
            statusLabel.setText(" ✓ Script saved");
            statusLabel.setForeground(new Color(100, 200, 100));
            modified = false;
            return true;
        } else {
            statusLabel.setText(" ✕ Failed to save");
            statusLabel.setForeground(new Color(200, 100, 100));
            return false;
        }
    }

    /**
     * Saves script file silently without altering UI text/colors unless failed.
     */
    private void saveScriptSilently() {
        if (scriptManager.saveScriptContent(scriptName, textArea.getText())) {
            modified = false;
            statusLabel.setText(" ✓ Auto-saved");
            statusLabel.setForeground(new Color(100, 200, 100));
        } else {
            statusLabel.setText(" ✕ Auto-save failed");
            statusLabel.setForeground(new Color(200, 100, 100));
        }
    }

    public void compileScript() {
        File scriptFile = new File(scriptManager.getScriptsFolder(), scriptName + ".java");
        if (scriptManager.compileScript(scriptFile)) {
            statusLabel.setText(" ✓ Compilation successful");
            statusLabel.setForeground(new Color(100, 200, 100));
            editor.reloadAllScriptInstances();
        } else {
            statusLabel.setText(" ✕ Compilation failed - check console");
            statusLabel.setForeground(new Color(200, 100, 100));
        }
    }

    public void closeEditor() {
        autoSaveTimer.stop();
        autocompleteManager.hidePopup();
        editor.onScriptEditorClosed(scriptName);
        dispose();
    }
}
