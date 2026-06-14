package com.ignis.imageeditor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrated image editor window (roadmap item 3).
 *
 * Decoupled module: depends only on ImageDocument/PaintCanvas. The engine
 * editor opens it through Tools - Image Editor, passing the project's
 * assets/sprites folder so exported textures land directly in the project.
 */
public class ImageEditorFrame extends JFrame {

    private final PaintCanvas canvas;
    private final DefaultListModel<ImageDocument.Layer> layerModel = new DefaultListModel<>();
    private final JList<ImageDocument.Layer> layerList = new JList<>(layerModel);
    
    // Visual History UI elements
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private boolean isUpdatingHistorySelection = false;

    private final JLabel colorPreview = new JLabel("   ");
    private final JLabel statusLabel = new JLabel(" Ready");

    /** Project assets/sprites folder, or null when opened standalone. */
    private File exportFolder;
    private File currentFile;

    private JComboBox<String> zoomCombo;
    private static final double[] ZOOM_LEVELS = {0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0};

    // Tool buttons reference for shortcuts selection
    private final Map<PaintCanvas.ToolType, JToggleButton> toolButtons = new HashMap<>();

    public ImageEditorFrame(File exportFolder) {
        super("Ignis Image Editor");
        com.ignis.core.AppIconHelper.setWindowIcon(this);
        this.exportFolder = exportFolder;

        canvas = new PaintCanvas(new ImageDocument(256, 256));
        canvas.setListener(new PaintCanvas.CanvasListener() {
            @Override
            public void onDocumentChanged() {
                layerList.repaint();
            }

            @Override
            public void onColorPicked(Color picked) {
                colorPreview.setBackground(picked);
            }

            @Override
            public void onMouseMoved(Point imagePos) {
                updateStatus(imagePos);
            }

            @Override
            public void onHistoryUpdated() {
                refreshHistory();
            }
        });

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);

        JPanel canvasWrapper = new JPanel(new java.awt.GridBagLayout());
        canvasWrapper.setBackground(new Color(35, 35, 35));
        canvasWrapper.add(canvas);

        JScrollPane canvasScroll = new JScrollPane(canvasWrapper);
        canvasScroll.getViewport().setBackground(new Color(35, 35, 35));
        
        // Ctrl + Wheel Zoom
        canvasScroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                e.consume();
                if (e.getWheelRotation() < 0) {
                    zoomIn();
                } else if (e.getWheelRotation() > 0) {
                    zoomOut();
                }
            }
        });

        add(canvasScroll, BorderLayout.CENTER);

        // Sidebar containing Layers and Visual History
        JPanel sidebar = new JPanel(new GridLayout(2, 1, 5, 5));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.add(buildLayersPanel());
        sidebar.add(buildHistoryPanel());
        add(sidebar, BorderLayout.EAST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.setBackground(new Color(45, 45, 45));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        refreshLayers();
        refreshHistory();
        updateStatus(null);
        setupKeyboardShortcuts();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
    }

    // ==================== MENU ====================

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("New...", KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
                e -> newDocument()));
        file.add(menuItem("Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
                e -> openImage()));
        file.addSeparator();
        file.add(menuItem("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
                e -> saveImage(false)));
        file.add(menuItem("Save As...", null, e -> saveImage(true)));
        if (exportFolder != null) {
            file.add(menuItem("Export to Project Sprites...", null, e -> exportToProject()));
        }
        file.addSeparator();
        file.add(menuItem("Close", null, e -> dispose()));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.add(menuItem("Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
                e -> canvas.undo()));
        edit.add(menuItem("Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
                e -> canvas.redo()));
        bar.add(edit);

        return bar;
    }

    private JMenuItem menuItem(String text, KeyStroke accelerator, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.addActionListener(action);
        return item;
    }

    // ==================== TOOLBAR ====================

    private JToolBar buildToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        ButtonGroup group = new ButtonGroup();
        addTool(toolbar, group, "Pencil", PaintCanvas.ToolType.PENCIL, true);
        addTool(toolbar, group, "Brush", PaintCanvas.ToolType.BRUSH, false);
        addTool(toolbar, group, "Eraser", PaintCanvas.ToolType.ERASER, false);
        addTool(toolbar, group, "Line", PaintCanvas.ToolType.LINE, false);
        addTool(toolbar, group, "Rect", PaintCanvas.ToolType.RECTANGLE, false);
        addTool(toolbar, group, "Ellipse", PaintCanvas.ToolType.ELLIPSE, false);
        addTool(toolbar, group, "Fill", PaintCanvas.ToolType.FILL, false);
        addTool(toolbar, group, "Selection", PaintCanvas.ToolType.SELECTION, false);
        addTool(toolbar, group, "Move", PaintCanvas.ToolType.MOVE, false);
        addTool(toolbar, group, "Picker", PaintCanvas.ToolType.EYEDROPPER, false);

        toolbar.addSeparator();

        // Color selector
        colorPreview.setOpaque(true);
        colorPreview.setBackground(canvas.getColor());
        colorPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        colorPreview.setPreferredSize(new Dimension(24, 24));
        colorPreview.setMaximumSize(new Dimension(24, 24));
        JButton colorButton = new JButton("Color");
        colorButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choose Color", canvas.getColor());
            if (chosen != null) {
                canvas.setColor(chosen);
                colorPreview.setBackground(chosen);
            }
        });
        toolbar.add(colorButton);
        toolbar.add(colorPreview);

        toolbar.addSeparator();

        // Brush size
        toolbar.add(new JLabel(" Size: "));
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 128, 1));
        sizeSpinner.setMaximumSize(new Dimension(60, 26));
        sizeSpinner.addChangeListener(e -> {
            canvas.setBrushSize((Integer) sizeSpinner.getValue());
            updateStatus(null);
        });
        toolbar.add(sizeSpinner);

        toolbar.addSeparator();

        // Zoom
        toolbar.add(new JLabel(" Zoom: "));
        zoomCombo = new JComboBox<>(
                new String[]{"25%", "50%", "100%", "200%", "400%", "800%", "1600%", "3200%"});
        zoomCombo.setSelectedItem("100%");
        zoomCombo.setMaximumSize(new Dimension(90, 26));
        zoomCombo.addActionListener(e -> {
            String value = (String) zoomCombo.getSelectedItem();
            if (value != null) {
                canvas.setZoom(Integer.parseInt(value.replace("%", "")) / 100.0);
                updateStatus(null);
            }
        });
        toolbar.add(zoomCombo);

        toolbar.addSeparator();

        // Grid Size Combobox
        toolbar.add(new JLabel(" Grid: "));
        JComboBox<String> gridCombo = new JComboBox<>(new String[]{"None", "Pixel (1x1)", "8x8", "16x16", "32x32"});
        gridCombo.setSelectedItem("Pixel (1x1)");
        gridCombo.setMaximumSize(new Dimension(100, 26));
        gridCombo.addActionListener(e -> {
            String val = (String) gridCombo.getSelectedItem();
            if ("None".equals(val)) canvas.setGridSize(0);
            else if ("Pixel (1x1)".equals(val)) canvas.setGridSize(1);
            else if ("8x8".equals(val)) canvas.setGridSize(8);
            else if ("16x16".equals(val)) canvas.setGridSize(16);
            else if ("32x32".equals(val)) canvas.setGridSize(32);
        });
        toolbar.add(gridCombo);

        toolbar.addSeparator();

        // Stabilizer Checkbox
        JCheckBox stabilizerBox = new JCheckBox("Stabilizer", true);
        stabilizerBox.addActionListener(e -> canvas.setUseStabilizer(stabilizerBox.isSelected()));
        toolbar.add(stabilizerBox);

        toolbar.add(Box.createHorizontalGlue());
        return toolbar;
    }

    private void addTool(JToolBar toolbar, ButtonGroup group, String label,
                         PaintCanvas.ToolType type, boolean selected) {
        JToggleButton button = new JToggleButton(label, selected);
        button.addActionListener(e -> canvas.setTool(type));
        group.add(button);
        toolbar.add(button);
        toolButtons.put(type, button);
    }

    private void selectTool(PaintCanvas.ToolType toolType) {
        canvas.setTool(toolType);
        JToggleButton btn = toolButtons.get(toolType);
        if (btn != null) {
            btn.setSelected(true);
        }
    }

    private void setupKeyboardShortcuts() {
        JPanel content = (JPanel) getContentPane();
        
        // Register shortcuts for tools
        registerShortcut(content, KeyEvent.VK_B, 0, "selectBrush", e -> selectTool(PaintCanvas.ToolType.BRUSH));
        registerShortcut(content, KeyEvent.VK_P, 0, "selectPencil", e -> selectTool(PaintCanvas.ToolType.PENCIL));
        registerShortcut(content, KeyEvent.VK_E, 0, "selectEraser", e -> selectTool(PaintCanvas.ToolType.ERASER));
        registerShortcut(content, KeyEvent.VK_S, 0, "selectSelection", e -> selectTool(PaintCanvas.ToolType.SELECTION));
        registerShortcut(content, KeyEvent.VK_M, 0, "selectMove", e -> selectTool(PaintCanvas.ToolType.MOVE));
        registerShortcut(content, KeyEvent.VK_G, 0, "selectFill", e -> selectTool(PaintCanvas.ToolType.FILL));
        registerShortcut(content, KeyEvent.VK_I, 0, "selectEyedropper", e -> selectTool(PaintCanvas.ToolType.EYEDROPPER));
        registerShortcut(content, KeyEvent.VK_L, 0, "selectLine", e -> selectTool(PaintCanvas.ToolType.LINE));
        registerShortcut(content, KeyEvent.VK_R, 0, "selectRect", e -> selectTool(PaintCanvas.ToolType.RECTANGLE));
        registerShortcut(content, KeyEvent.VK_O, 0, "selectEllipse", e -> selectTool(PaintCanvas.ToolType.ELLIPSE));

        // Ctrl + Z / Ctrl + Y
        registerShortcut(content, KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, "undoAction", e -> canvas.undo());
        registerShortcut(content, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, "redoAction", e -> canvas.redo());
    }

    private void registerShortcut(JComponent comp, int keyCode, int modifiers, String name, java.awt.event.ActionListener action) {
        comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        comp.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.actionPerformed(e);
            }
        });
    }

    // ==================== LAYERS PANEL ====================

    private JComponent buildLayersPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Layers (Double click to rename)"));

        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ImageDocument.Layer layer = (ImageDocument.Layer) value;
                String prefix = (layer.isVisible() ? "[v] " : "[ ] ") + (layer.isLocked() ? "[Locked] " : "");
                setText(prefix + layer.getName());
                return this;
            }
        });
        
        layerList.addListSelectionListener(e -> {
            int viewIndex = layerList.getSelectedIndex();
            if (viewIndex >= 0) {
                canvas.getDocument().setActiveLayerIndex(toModelIndex(viewIndex));
            }
        });

        // Double click to rename layer
        layerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = layerList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        ImageDocument.Layer layer = layerModel.getElementAt(index);
                        String newName = JOptionPane.showInputDialog(ImageEditorFrame.this, "Rename Layer:", layer.getName());
                        if (newName != null && !newName.trim().isEmpty()) {
                            layer.setName(newName.trim());
                            layerList.repaint();
                        }
                    }
                }
            }
        });

        panel.add(new JScrollPane(layerList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        buttons.add(smallButton("+", "Add layer", e -> {
            canvas.getDocument().addLayer("Layer " + (canvas.getDocument().getLayers().size() + 1));
            refreshLayers();
        }));
        buttons.add(smallButton("-", "Remove layer", e -> {
            canvas.getDocument().removeLayer(selectedModelIndex());
            refreshLayers();
        }));
        buttons.add(smallButton("^", "Move layer up", e -> {
            canvas.getDocument().moveLayer(selectedModelIndex(), +1);
            refreshLayers();
        }));
        buttons.add(smallButton("v", "Move layer down", e -> {
            canvas.getDocument().moveLayer(selectedModelIndex(), -1);
            refreshLayers();
        }));
        buttons.add(smallButton("o", "Toggle visibility", e -> {
            int index = selectedModelIndex();
            if (index >= 0) {
                ImageDocument.Layer layer = canvas.getDocument().getLayers().get(index);
                layer.setVisible(!layer.isVisible());
                layerList.repaint();
                canvas.repaint();
            }
        }));
        buttons.add(smallButton("Lock", "Lock Layer", e -> {
            int index = selectedModelIndex();
            if (index >= 0) {
                ImageDocument.Layer layer = canvas.getDocument().getLayers().get(index);
                layer.setLocked(!layer.isLocked());
                layerList.repaint();
            }
        }));

        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    // ==================== VISUAL HISTORY PANEL ====================

    private JComponent buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Visual History"));

        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || isUpdatingHistorySelection) return;
            int selected = historyList.getSelectedIndex();
            if (selected < 0) return;

            isUpdatingHistorySelection = true;
            int currentIdx = canvas.getUndoStack().size();
            if (selected < currentIdx) {
                int steps = currentIdx - selected;
                canvas.revertToHistoryStep(steps);
            } else if (selected > currentIdx) {
                int steps = selected - currentIdx;
                for (int i = 0; i < steps; i++) {
                    canvas.redo();
                }
            }
            isUpdatingHistorySelection = false;
        });

        panel.add(new JScrollPane(historyList), BorderLayout.CENTER);
        return panel;
    }

    private void refreshHistory() {
        isUpdatingHistorySelection = true;
        historyModel.clear();
        List<PaintCanvas.UndoEntry> undoStack = canvas.getUndoStack();
        
        // Show in chronological order: oldest on top, newest at bottom, followed by current state, followed by redo states
        for (int i = undoStack.size() - 1; i >= 0; i--) {
            historyModel.addElement(undoStack.get(i).actionName);
        }
        historyModel.addElement("[Current State]");
        
        List<PaintCanvas.UndoEntry> redoStack = canvas.getRedoStack();
        for (int i = 0; i < redoStack.size(); i++) {
            historyModel.addElement("(" + redoStack.get(i).actionName + ")");
        }
        
        historyList.setSelectedIndex(undoStack.size());
        isUpdatingHistorySelection = false;
    }

    private JButton smallButton(String text, String tooltip, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setMargin(new java.awt.Insets(2, 6, 2, 6));
        button.addActionListener(action);
        return button;
    }

    /** The list shows layers top-first; the model stores them bottom-first. */
    private int toModelIndex(int viewIndex) {
        return canvas.getDocument().getLayers().size() - 1 - viewIndex;
    }

    private int selectedModelIndex() {
        int viewIndex = layerList.getSelectedIndex();
        return viewIndex < 0 ? canvas.getDocument().getActiveLayerIndex() : toModelIndex(viewIndex);
    }

    private void refreshLayers() {
        layerModel.clear();
        java.util.List<ImageDocument.Layer> layers = canvas.getDocument().getLayers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            layerModel.addElement(layers.get(i));
        }
        int active = canvas.getDocument().getActiveLayerIndex();
        layerList.setSelectedIndex(layers.size() - 1 - active);
        canvas.repaint();
    }

    // ==================== FILE OPERATIONS ====================

    private void newDocument() {
        JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(256, 1, 8192, 16));
        JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(256, 1, 8192, 16));
        JPanel form = new JPanel(new FlowLayout());
        form.add(new JLabel("Width:"));
        form.add(widthSpinner);
        form.add(new JLabel("Height:"));
        form.add(heightSpinner);
        int result = JOptionPane.showConfirmDialog(this, form, "New Image",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            canvas.setDocument(new ImageDocument(
                    (Integer) widthSpinner.getValue(), (Integer) heightSpinner.getValue()));
            currentFile = null;
            refreshLayers();
            refreshHistory();
        }
    }

    private void openImage() {
        JFileChooser chooser = createImageChooser("Open Image");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage image = ImageIO.read(chooser.getSelectedFile());
                if (image == null) {
                    throw new java.io.IOException("Unsupported image format");
                }
                canvas.setDocument(ImageDocument.fromImage(image));
                currentFile = chooser.getSelectedFile();
                refreshLayers();
                refreshHistory();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open image: " + ex.getMessage(),
                        "Open Image", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveImage(boolean forceDialog) {
        File target = currentFile;
        if (target == null || forceDialog) {
            JFileChooser chooser = createImageChooser("Save Image");
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            target = ensurePngExtension(chooser.getSelectedFile());
        }
        writePng(target);
        currentFile = target;
    }

    private void exportToProject() {
        String name = JOptionPane.showInputDialog(this, "Texture file name:", "texture.png");
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        File target = ensurePngExtension(new File(exportFolder, name.trim()));
        if (target.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    target.getName() + " already exists. Overwrite?",
                    "Export", JOptionPane.YES_NO_OPTION);
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        writePng(target);
    }

    private void writePng(File target) {
        try {
            target.getParentFile().mkdirs();
            ImageIO.write(canvas.getDocument().composite(), "png", target);
            setTitle("Ignis Image Editor - " + target.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save image: " + ex.getMessage(),
                    "Save Image", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JFileChooser createImageChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
                "png", "jpg", "jpeg", "gif", "bmp"));
        if (exportFolder != null && exportFolder.exists()) {
            chooser.setCurrentDirectory(exportFolder);
        }
        return chooser;
    }

    private File ensurePngExtension(File file) {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")
                && !name.endsWith(".gif") && !name.endsWith(".bmp")) {
            return new File(file.getParentFile(), file.getName() + ".png");
        }
        return file;
    }

    private void zoomIn() {
        double currentZoom = canvas.getZoom();
        for (int i = 0; i < ZOOM_LEVELS.length; i++) {
            if (ZOOM_LEVELS[i] > currentZoom + 0.001) {
                setZoomLevel(ZOOM_LEVELS[i]);
                return;
            }
        }
    }

    private void zoomOut() {
        double currentZoom = canvas.getZoom();
        for (int i = ZOOM_LEVELS.length - 1; i >= 0; i--) {
            if (ZOOM_LEVELS[i] < currentZoom - 0.001) {
                setZoomLevel(ZOOM_LEVELS[i]);
                return;
            }
        }
    }

    private void setZoomLevel(double zoom) {
        canvas.setZoom(zoom);
        String match = (int) Math.round(zoom * 100) + "%";
        if (zoomCombo != null) {
            for (int i = 0; i < zoomCombo.getItemCount(); i++) {
                if (zoomCombo.getItemAt(i).equals(match)) {
                    zoomCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        updateStatus(null);
    }

    private void updateStatus(Point imgPos) {
        String coords = (imgPos == null) ? "[- , -]" : "[" + imgPos.x + ", " + imgPos.y + "]";
        int brushSize = canvas.getBrushSize();
        int zoomPercent = (int) Math.round(canvas.getZoom() * 100);
        statusLabel.setText(String.format(" Coords: %s  |  Brush Size: %d px  |  Zoom: %d%%", coords, brushSize, zoomPercent));
    }

    /** Standalone launcher (no project integration). */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> new ImageEditorFrame(null).setVisible(true));
    }
}
