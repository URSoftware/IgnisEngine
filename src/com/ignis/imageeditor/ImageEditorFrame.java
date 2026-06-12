package com.ignis.imageeditor;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;

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
    private final JLabel colorPreview = new JLabel("   ");

    /** Project assets/sprites folder, or null when opened standalone. */
    private File exportFolder;
    private File currentFile;

    public ImageEditorFrame(File exportFolder) {
        super("Ignis Image Editor");
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
        });

        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);

        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.getViewport().setBackground(new Color(35, 35, 35));
        add(canvasScroll, BorderLayout.CENTER);

        add(buildLayersPanel(), BorderLayout.EAST);

        refreshLayers();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationByPlatform(true);
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
        addTool(toolbar, group, "Eraser", PaintCanvas.ToolType.ERASER, false);
        addTool(toolbar, group, "Line", PaintCanvas.ToolType.LINE, false);
        addTool(toolbar, group, "Rect", PaintCanvas.ToolType.RECTANGLE, false);
        addTool(toolbar, group, "Ellipse", PaintCanvas.ToolType.ELLIPSE, false);
        addTool(toolbar, group, "Fill", PaintCanvas.ToolType.FILL, false);
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
        sizeSpinner.addChangeListener(e -> canvas.setBrushSize((Integer) sizeSpinner.getValue()));
        toolbar.add(sizeSpinner);

        toolbar.addSeparator();

        // Zoom
        toolbar.add(new JLabel(" Zoom: "));
        JComboBox<String> zoomCombo = new JComboBox<>(
                new String[]{"25%", "50%", "100%", "200%", "400%", "800%"});
        zoomCombo.setSelectedItem("100%");
        zoomCombo.setMaximumSize(new Dimension(90, 26));
        zoomCombo.addActionListener(e -> {
            String value = (String) zoomCombo.getSelectedItem();
            if (value != null) {
                canvas.setZoom(Integer.parseInt(value.replace("%", "")) / 100.0);
            }
        });
        toolbar.add(zoomCombo);

        toolbar.add(Box.createHorizontalGlue());
        return toolbar;
    }

    private void addTool(JToolBar toolbar, ButtonGroup group, String label,
                         PaintCanvas.ToolType type, boolean selected) {
        JToggleButton button = new JToggleButton(label, selected);
        button.addActionListener(e -> canvas.setTool(type));
        group.add(button);
        toolbar.add(button);
    }

    // ==================== LAYERS PANEL ====================

    private JComponent buildLayersPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Layers"));
        panel.setPreferredSize(new Dimension(190, 0));

        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                ImageDocument.Layer layer = (ImageDocument.Layer) value;
                setText((layer.isVisible() ? "[v] " : "[ ] ") + layer.getName());
                return this;
            }
        });
        layerList.addListSelectionListener(e -> {
            int viewIndex = layerList.getSelectedIndex();
            if (viewIndex >= 0) {
                canvas.getDocument().setActiveLayerIndex(toModelIndex(viewIndex));
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
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
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

    /** Standalone launcher (no project integration). */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> new ImageEditorFrame(null).setVisible(true));
    }
}
