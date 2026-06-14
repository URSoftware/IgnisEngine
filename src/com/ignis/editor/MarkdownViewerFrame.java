package com.ignis.editor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ignis.core.ui.VectorIcon;

/**
 * Premium visual Markdown reader and editor for README files.
 * Provides Source Mode (raw edit with line numbers) and Reader Mode (rendered HTML).
 */
public class MarkdownViewerFrame extends JFrame {

    private final File mdFile;
    private final Editor editor;

    private JTextArea sourceArea;
    private JTextArea lineNumbers;
    private JTextPane readerPane;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JLabel statusLabel;
    
    private JToggleButton btnSource;
    private JToggleButton btnReader;
    private boolean modified = false;

    public MarkdownViewerFrame(Editor editor, File mdFile) {
        super("Markdown Viewer - " + mdFile.getName());
        this.editor = editor;
        this.mdFile = mdFile;
        com.ignis.core.AppIconHelper.setWindowIcon(this);

        setSize(850, 600);
        setLocationRelativeTo(editor);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setupUI();
        loadContent();
        switchToReaderMode();
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 30, 30));

        // --- Top Bar ---
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(45, 45, 45));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        // Mode toggles
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        modePanel.setBackground(new Color(45, 45, 45));

        btnReader = new JToggleButton("Reader Mode", true);
        btnSource = new JToggleButton("Source Mode", false);
        
        styleToggle(btnReader);
        styleToggle(btnSource);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(btnReader);
        modeGroup.add(btnSource);

        modePanel.add(btnReader);
        modePanel.add(btnSource);

        // Action buttons (Save, Export)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actionPanel.setBackground(new Color(45, 45, 45));

        JButton btnSave = new JButton("Save", new VectorIcon(VectorIcon.VectorIconType.SAVE, 14, Color.WHITE));
        styleButton(btnSave, new Color(46, 139, 87));
        actionPanel.add(btnSave);

        topBar.add(modePanel, BorderLayout.WEST);
        topBar.add(actionPanel, BorderLayout.EAST);
        mainPanel.add(topBar, BorderLayout.NORTH);

        // --- Card Panel (Center) ---
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(new Color(30, 30, 30));

        // Reader component
        readerPane = new JTextPane();
        readerPane.setContentType("text/html");
        readerPane.setEditable(false);
        readerPane.setBackground(new Color(30, 30, 30));
        readerPane.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        // Link handling
        readerPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    System.err.println("Failed to open link: " + ex.getMessage());
                }
            }
        });

        // Set up Stylesheet for beautiful look
        HTMLDocument doc = (HTMLDocument) readerPane.getDocument();
        StyleSheet stylesheet = doc.getStyleSheet();
        stylesheet.addRule("body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; color: #e2e8f0; background-color: #1e1e1e; line-height: 1.6; }");
        stylesheet.addRule("h1 { font-size: 24px; color: #569cd6; border-bottom: 1px solid #3c3c3c; padding-bottom: 6px; margin-top: 20px; }");
        stylesheet.addRule("h2 { font-size: 20px; color: #4fc1ff; border-bottom: 1px solid #3c3c3c; padding-bottom: 4px; margin-top: 16px; }");
        stylesheet.addRule("h3 { font-size: 16px; color: #9cdcfe; margin-top: 12px; }");
        stylesheet.addRule("p { margin-top: 6px; margin-bottom: 6px; }");
        stylesheet.addRule("ul { margin-left: 20px; list-style-type: square; }");
        stylesheet.addRule("ol { margin-left: 20px; }");
        stylesheet.addRule("li { margin-top: 3px; }");
        stylesheet.addRule("pre { background-color: #2d2d2d; color: #f8f8f2; padding: 10px; border-radius: 5px; font-family: 'Consolas', monospace; border: 1px solid #3c3c3c; }");
        stylesheet.addRule("code { background-color: #2d2d2d; color: #ce9178; padding: 2px 4px; border-radius: 3px; font-family: 'Consolas', monospace; }");
        stylesheet.addRule("table { border-collapse: collapse; width: 100%; margin-top: 10px; }");
        stylesheet.addRule("th, td { border: 1px solid #3c3c3c; padding: 8px; text-align: left; }");
        stylesheet.addRule("th { background-color: #252526; color: #569cd6; }");
        stylesheet.addRule("a { color: #37911e; text-decoration: none; }");

        JScrollPane readerScroll = new JScrollPane(readerPane);
        readerScroll.setBorder(null);
        cardPanel.add(readerScroll, "READER");

        // Source component
        sourceArea = new JTextArea();
        sourceArea.setBackground(new Color(30, 30, 30));
        sourceArea.setForeground(new Color(220, 220, 220));
        sourceArea.setCaretColor(Color.WHITE);
        sourceArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        sourceArea.setTabSize(4);

        lineNumbers = new JTextArea("1");
        lineNumbers.setBackground(new Color(45, 45, 45));
        lineNumbers.setForeground(new Color(120, 120, 120));
        lineNumbers.setFont(new Font("Consolas", Font.PLAIN, 14));
        lineNumbers.setEditable(false);
        lineNumbers.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        sourceArea.getDocument().addDocumentListener(new DocumentListener() {
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
                modified = true;
                updateLineNumbers();
            }
            private void updateLineNumbers() {
                int lines = sourceArea.getLineCount();
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= lines; i++) {
                    sb.append(i).append("\n");
                }
                lineNumbers.setText(sb.toString());
            }
        });

        JScrollPane sourceScroll = new JScrollPane(sourceArea);
        sourceScroll.setRowHeaderView(lineNumbers);
        sourceScroll.setBorder(null);
        cardPanel.add(sourceScroll, "SOURCE");

        mainPanel.add(cardPanel, BorderLayout.CENTER);

        // --- Status Bar (Bottom) ---
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(40, 40, 40));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)));
        statusLabel = new JLabel(" Ready");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusBar.add(statusLabel, BorderLayout.WEST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        add(mainPanel);

        // Intercept Mode Switch
        btnReader.addActionListener(e -> switchToReaderMode());
        btnSource.addActionListener(e -> switchToSourceMode());
        
        btnSave.addActionListener(e -> saveFile());
    }

    private void styleToggle(JToggleButton btn) {
        btn.setBackground(new Color(55, 55, 55));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    }

    private void loadContent() {
        if (mdFile.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(mdFile.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                sourceArea.setText(content);
                modified = false;
            } catch (Exception ex) {
                statusLabel.setText(" Error loading file: " + ex.getMessage());
            }
        }
    }

    private void switchToReaderMode() {
        btnReader.setSelected(true);
        btnSource.setSelected(false);
        
        // Parse source to HTML and display
        String md = sourceArea.getText();
        String html = parseMarkdown(md);
        readerPane.setText(html);
        cardLayout.show(cardPanel, "READER");
        statusLabel.setText(" Reader Mode active");
    }

    private void switchToSourceMode() {
        btnReader.setSelected(false);
        btnSource.setSelected(true);
        cardLayout.show(cardPanel, "SOURCE");
        statusLabel.setText(" Source Mode active");
    }

    private void saveFile() {
        try {
            Files.write(mdFile.toPath(), sourceArea.getText().getBytes(StandardCharsets.UTF_8));
            modified = false;
            statusLabel.setText(" File saved successfully");
            if (btnReader.isSelected()) {
                switchToReaderMode();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- Regex-based Markdown-to-HTML parser ---
    private String parseMarkdown(String md) {
        if (md == null) return "";
        String[] lines = md.split("\n");
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");

        boolean inList = false;
        boolean inOList = false;
        boolean inCodeBlock = false;
        boolean inTable = false;
        StringBuilder codeContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            // Code Blocks
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    html.append("<pre><code>")
                        .append(escapeHtml(codeContent.toString()))
                        .append("</code></pre>");
                    codeContent.setLength(0);
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeContent.append(line).append("\n");
                continue;
            }

            // Headings
            if (trimmed.startsWith("# ")) {
                closeStructures(html, inList, inOList, inTable); inList = false; inOList = false; inTable = false;
                html.append("<h1>").append(parseInline(trimmed.substring(2))).append("</h1>");
                continue;
            } else if (trimmed.startsWith("## ")) {
                closeStructures(html, inList, inOList, inTable); inList = false; inOList = false; inTable = false;
                html.append("<h2>").append(parseInline(trimmed.substring(3))).append("</h2>");
                continue;
            } else if (trimmed.startsWith("### ")) {
                closeStructures(html, inList, inOList, inTable); inList = false; inOList = false; inTable = false;
                html.append("<h3>").append(parseInline(trimmed.substring(4))).append("</h3>");
                continue;
            }

            // Checklists
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("* [ ] ") || trimmed.startsWith("* [x] ")) {
                closeOList(html, inOList); inOList = false;
                closeTable(html, inTable); inTable = false;
                if (!inList) {
                    html.append("<ul style='list-style-type: none; padding-left: 10px;'>");
                    inList = true;
                }
                boolean checked = trimmed.contains("[x]");
                String checkbox = checked ? "&#9745; " : "&#9744; "; // Ballot box check vs blank
                String content = trimmed.substring(6);
                html.append("<li><span style='color: #4fc1ff; font-weight: bold;'>").append(checkbox).append("</span>")
                    .append(parseInline(content)).append("</li>");
                continue;
            }

            // Bullet Lists
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                closeOList(html, inOList); inOList = false;
                closeTable(html, inTable); inTable = false;
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(parseInline(trimmed.substring(2))).append("</li>");
                continue;
            }

            // Numbered Lists
            if (trimmed.matches("^\\d+\\.\\s.*")) {
                closeList(html, inList); inList = false;
                closeTable(html, inTable); inTable = false;
                if (!inOList) {
                    html.append("<ol>");
                    inOList = true;
                }
                String content = trimmed.replaceFirst("^\\d+\\.\\s", "");
                html.append("<li>").append(parseInline(content)).append("</li>");
                continue;
            }

            // Tables
            if (trimmed.startsWith("|")) {
                closeList(html, inList); inList = false;
                closeOList(html, inOList); inOList = false;
                if (trimmed.contains("---")) {
                    // Separator line, ignore
                    continue;
                }
                if (!inTable) {
                    html.append("<table>");
                    inTable = true;
                }
                String[] cells = trimmed.split("\\|");
                html.append("<tr>");
                for (int c = 1; c < cells.length; c++) {
                    String cell = cells[c].trim();
                    // First row is header if inTable was just set
                    if (html.indexOf("<tr>") == html.lastIndexOf("<tr>")) {
                        html.append("<th>").append(parseInline(cell)).append("</th>");
                    } else {
                        html.append("<td>").append(parseInline(cell)).append("</td>");
                    }
                }
                html.append("</tr>");
                continue;
            }

            // Blank line
            if (trimmed.isEmpty()) {
                closeStructures(html, inList, inOList, inTable); inList = false; inOList = false; inTable = false;
                html.append("<br>");
                continue;
            }

            // Paragraph
            closeStructures(html, inList, inOList, inTable); inList = false; inOList = false; inTable = false;
            html.append("<p>").append(parseInline(line)).append("</p>");
        }

        closeStructures(html, inList, inOList, inTable);
        html.append("</body></html>");
        return html.toString();
    }

    private void closeStructures(StringBuilder html, boolean inList, boolean inOList, boolean inTable) {
        closeList(html, inList);
        closeOList(html, inOList);
        closeTable(html, inTable);
    }
    private void closeList(StringBuilder html, boolean inList) {
        if (inList) html.append("</ul>");
    }
    private void closeOList(StringBuilder html, boolean inOList) {
        if (inOList) html.append("</ol>");
    }
    private void closeTable(StringBuilder html, boolean inTable) {
        if (inTable) html.append("</table>");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String parseInline(String text) {
        String res = escapeHtml(text);
        
        // Bold: **text** or __text__
        res = res.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        res = res.replaceAll("__(.*?)__", "<b>$1</b>");
        
        // Italic: *text* or _text_
        res = res.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
        res = res.replaceAll("_(.*?)_", "<i>$1</i>");

        // Inline Code: `code`
        res = res.replaceAll("`(.*?)`", "<code>$1</code>");

        // Links: [text](url)
        res = res.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href='$2'>$1</a>");

        return res;
    }
}
