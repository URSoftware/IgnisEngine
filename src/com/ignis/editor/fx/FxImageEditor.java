package com.ignis.editor.fx;

import com.ignis.imageeditor.ImageDocument;
import com.ignis.imageeditor.PaintCanvas.ToolType;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JavaFX implementation of the integrated Image Editor.
 * Ports com.ignis.imageeditor.ImageEditorFrame.
 */
public class FxImageEditor extends Stage {

    private final FxPaintCanvas canvas;
    private final ObservableList<ImageDocument.Layer> layerModel = FXCollections.observableArrayList();
    private final ListView<ImageDocument.Layer> layerList = new ListView<>(layerModel);

    private final ObservableList<String> historyModel = FXCollections.observableArrayList();
    private final ListView<String> historyList = new ListView<>(historyModel);
    private boolean isUpdatingHistorySelection = false;

    private final Region colorPreview = new Region();
    private final Label statusLabel = new Label(" Ready");

    private File exportFolder;
    private File currentFile;

    private ComboBox<String> zoomCombo;
    private static final double[] ZOOM_LEVELS = {0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0};

    private final Map<ToolType, ToggleButton> toolButtons = new HashMap<>();

    public FxImageEditor(File exportFolder) {
        setTitle("Ignis Image Editor");
        initModality(Modality.NONE);
        this.exportFolder = exportFolder;

        canvas = new FxPaintCanvas(new ImageDocument(256, 256));
        canvas.setListener(new FxPaintCanvas.CanvasListener() {
            @Override
            public void onDocumentChanged() {
                layerList.refresh();
            }

            @Override
            public void onColorPicked(java.awt.Color picked) {
                Platform.runLater(() -> colorPreview.setStyle("-fx-background-color: " + toHex(picked) + "; -fx-border-color: gray; -fx-border-width: 1px;"));
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

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2b2b2b;");

        // Menu and Toolbar
        VBox topContainer = new VBox(buildMenuBar(), buildToolBar());
        root.setTop(topContainer);

        // Center canvas wrapper
        StackPane canvasWrapper = new StackPane(canvas);
        canvasWrapper.setStyle("-fx-background-color: #232323;");
        canvasWrapper.setPadding(new Insets(10));

        ScrollPane canvasScroll = new ScrollPane(canvasWrapper);
        canvasScroll.setStyle("-fx-background: #232323; -fx-border-color: transparent;");
        canvasScroll.setFitToWidth(true);
        canvasScroll.setFitToHeight(true);

        // Ctrl + Wheel Zoom
        canvasScroll.setOnScroll(e -> {
            if (e.isControlDown()) {
                e.consume();
                if (e.getDeltaY() > 0) {
                    zoomIn();
                } else if (e.getDeltaY() < 0) {
                    zoomOut();
                }
            }
        });

        root.setCenter(canvasScroll);

        // Sidebar (Layers + History)
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #2d2d2d;");
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        TitledPane layersPane = new TitledPane("Layers", buildLayersPanel());
        layersPane.setCollapsible(false);
        VBox.setVgrow(layersPane, Priority.ALWAYS);

        TitledPane historyPane = new TitledPane("Visual History", buildHistoryPanel());
        historyPane.setCollapsible(false);
        VBox.setVgrow(historyPane, Priority.ALWAYS);

        sidebar.getChildren().addAll(layersPane, historyPane);
        root.setRight(sidebar);

        // Status bar
        HBox statusPanel = new HBox(10);
        statusPanel.setPadding(new Insets(6, 12, 6, 12));
        statusPanel.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #3c3c3c; -fx-border-width: 1 0 0 0;");
        statusLabel.setStyle("-fx-text-fill: lightgray;");
        statusPanel.getChildren().add(statusLabel);
        root.setBottom(statusPanel);

        refreshLayers();
        refreshHistory();
        updateStatus(null);

        Scene scene = new Scene(root, 1100, 750);
        setupKeyboardShortcuts(scene);
        setScene(scene);
    }

    private MenuBar buildMenuBar() {
        Menu file = new Menu("File");
        MenuItem mNew = new MenuItem("New...");
        mNew.setOnAction(e -> newDocument());
        MenuItem mOpen = new MenuItem("Open...");
        mOpen.setOnAction(e -> openImage());
        MenuItem mSave = new MenuItem("Save");
        mSave.setOnAction(e -> saveImage(false));
        MenuItem mSaveAs = new MenuItem("Save As...");
        mSaveAs.setOnAction(e -> saveImage(true));
        
        file.getItems().addAll(mNew, mOpen, new SeparatorMenuItem(), mSave, mSaveAs);

        if (exportFolder != null) {
            MenuItem mExport = new MenuItem("Export to Project Sprites...");
            mExport.setOnAction(e -> exportToProject());
            file.getItems().add(mExport);
        }

        MenuItem mClose = new MenuItem("Close");
        mClose.setOnAction(e -> close());
        file.getItems().addAll(new SeparatorMenuItem(), mClose);

        Menu edit = new Menu("Edit");
        MenuItem mUndo = new MenuItem("Undo");
        mUndo.setOnAction(e -> canvas.undo());
        MenuItem mRedo = new MenuItem("Redo");
        mRedo.setOnAction(e -> canvas.redo());
        edit.getItems().addAll(mUndo, mRedo);

        return new MenuBar(file, edit);
    }

    private ToolBar buildToolBar() {
        ToggleGroup group = new ToggleGroup();
        
        addTool(group, "Pencil", ToolType.PENCIL, true);
        addTool(group, "Brush", ToolType.BRUSH, false);
        addTool(group, "Eraser", ToolType.ERASER, false);
        addTool(group, "Line", ToolType.LINE, false);
        addTool(group, "Rect", ToolType.RECTANGLE, false);
        addTool(group, "Ellipse", ToolType.ELLIPSE, false);
        addTool(group, "Fill", ToolType.FILL, false);
        addTool(group, "Selection", ToolType.SELECTION, false);
        addTool(group, "Move", ToolType.MOVE, false);
        addTool(group, "Picker", ToolType.EYEDROPPER, false);

        ToolBar toolbar = new ToolBar();
        toolbar.getItems().addAll(toolButtons.values());
        toolbar.getItems().add(new Separator());

        // Color selector
        colorPreview.setPrefSize(24, 24);
        java.awt.Color awtCol = canvas.getColor();
        colorPreview.setStyle("-fx-background-color: " + toHex(awtCol) + "; -fx-border-color: gray; -fx-border-width: 1px;");

        Button colorButton = new Button("Color");
        colorButton.setOnAction(e -> {
            ColorPicker picker = new ColorPicker(Color.rgb(canvas.getColor().getRed(), canvas.getColor().getGreen(), canvas.getColor().getBlue(), canvas.getColor().getAlpha()/255.0));
            Stage pickerStage = new Stage(javafx.stage.StageStyle.UTILITY);
            pickerStage.initOwner(this);
            pickerStage.initModality(Modality.WINDOW_MODAL);
            pickerStage.setTitle("Choose Color");
            
            VBox box = new VBox(10, picker);
            box.setPadding(new Insets(10));
            box.setAlignment(Pos.CENTER);
            
            picker.setOnAction(evt -> {
                Color c = picker.getValue();
                java.awt.Color newAwt = new java.awt.Color((float)c.getRed(), (float)c.getGreen(), (float)c.getBlue(), (float)c.getOpacity());
                canvas.setColor(newAwt);
                colorPreview.setStyle("-fx-background-color: " + toHex(newAwt) + "; -fx-border-color: gray; -fx-border-width: 1px;");
            });

            pickerStage.setScene(new Scene(box));
            pickerStage.showAndWait();
        });

        toolbar.getItems().addAll(colorButton, colorPreview, new Separator());

        // Brush size
        Label sizeLbl = new Label(" Size: ");
        sizeLbl.setStyle("-fx-text-fill: white;");
        Spinner<Integer> sizeSpinner = new Spinner<>(1, 128, 4);
        sizeSpinner.setPrefWidth(70);
        sizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setBrushSize(newVal);
            updateStatus(null);
        });
        toolbar.getItems().addAll(sizeLbl, sizeSpinner, new Separator());

        // Zoom
        Label zoomLbl = new Label(" Zoom: ");
        zoomLbl.setStyle("-fx-text-fill: white;");
        zoomCombo = new ComboBox<>();
        zoomCombo.getItems().addAll("25%", "50%", "100%", "200%", "400%", "800%", "1600%", "3200%");
        zoomCombo.setValue("100%");
        zoomCombo.setPrefWidth(90);
        zoomCombo.setOnAction(e -> {
            String value = zoomCombo.getValue();
            if (value != null) {
                canvas.setZoom(Integer.parseInt(value.replace("%", "")) / 100.0);
                updateStatus(null);
            }
        });
        toolbar.getItems().addAll(zoomLbl, zoomCombo, new Separator());

        // Grid size
        Label gridLbl = new Label(" Grid: ");
        gridLbl.setStyle("-fx-text-fill: white;");
        ComboBox<String> gridCombo = new ComboBox<>();
        gridCombo.getItems().addAll("None", "Pixel (1x1)", "8x8", "16x16", "32x32");
        gridCombo.setValue("Pixel (1x1)");
        gridCombo.setPrefWidth(100);
        gridCombo.setOnAction(e -> {
            String val = gridCombo.getValue();
            if ("None".equals(val)) canvas.setGridSize(0);
            else if ("Pixel (1x1)".equals(val)) canvas.setGridSize(1);
            else if ("8x8".equals(val)) canvas.setGridSize(8);
            else if ("16x16".equals(val)) canvas.setGridSize(16);
            else if ("32x32".equals(val)) canvas.setGridSize(32);
        });
        toolbar.getItems().addAll(gridLbl, gridCombo, new Separator());

        // Stabilizer
        CheckBox stabilizerBox = new CheckBox("Stabilizer");
        stabilizerBox.setSelected(true);
        stabilizerBox.setStyle("-fx-text-fill: white;");
        stabilizerBox.setOnAction(e -> canvas.setUseStabilizer(stabilizerBox.isSelected()));
        toolbar.getItems().add(stabilizerBox);

        return toolbar;
    }

    private void addTool(ToggleGroup group, String label, ToolType type, boolean selected) {
        ToggleButton button = new ToggleButton(label);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setOnAction(e -> canvas.setTool(type));
        toolButtons.put(type, button);
    }

    private void selectTool(ToolType toolType) {
        canvas.setTool(toolType);
        ToggleButton btn = toolButtons.get(toolType);
        if (btn != null) {
            btn.setSelected(true);
        }
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case B -> { if (!e.isControlDown()) selectTool(ToolType.BRUSH); }
                case P -> selectTool(ToolType.PENCIL);
                case E -> selectTool(ToolType.ERASER);
                case S -> { if (e.isControlDown()) saveImage(false); else selectTool(ToolType.SELECTION); }
                case M -> selectTool(ToolType.MOVE);
                case G -> selectTool(ToolType.FILL);
                case I -> selectTool(ToolType.EYEDROPPER);
                case L -> selectTool(ToolType.LINE);
                case R -> selectTool(ToolType.RECTANGLE);
                case O -> { if (e.isControlDown()) openImage(); else selectTool(ToolType.ELLIPSE); }
                case N -> { if (e.isControlDown()) newDocument(); }
                case Z -> { if (e.isControlDown()) canvas.undo(); }
                case Y -> { if (e.isControlDown()) canvas.redo(); }
                case SPACE -> {
                    // Space panning handle
                    canvas.setStyle("-fx-cursor: hand;");
                }
                default -> {}
            }
        });

        scene.setOnKeyReleased(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                canvas.setStyle("-fx-cursor: default;");
            }
        });
    }

    private VBox buildLayersPanel() {
        VBox vbox = new VBox(6);
        vbox.setStyle("-fx-background-color: #2d2d2d;");
        VBox.setVgrow(vbox, Priority.ALWAYS);

        layerList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ImageDocument.Layer layer, boolean empty) {
                super.updateItem(layer, empty);
                if (empty || layer == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String prefix = (layer.isVisible() ? "[v] " : "[ ] ") + (layer.isLocked() ? "[Locked] " : "");
                    setText(prefix + layer.getName());
                }
            }
        });

        layerList.getSelectionModel().selectedItemProperty().addListener((obs, oldLayer, newLayer) -> {
            if (newLayer != null) {
                int viewIndex = layerList.getSelectionModel().getSelectedIndex();
                canvas.getDocument().setActiveLayerIndex(toModelIndex(viewIndex));
            }
        });

        // Double click rename
        layerList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                ImageDocument.Layer layer = layerList.getSelectionModel().getSelectedItem();
                if (layer != null) {
                    TextInputDialog dialog = new TextInputDialog(layer.getName());
                    dialog.initOwner(this);
                    dialog.setTitle("Rename Layer");
                    dialog.setHeaderText("Rename Layer: " + layer.getName());
                    dialog.setContentText("Enter new name:");
                    dialog.showAndWait().ifPresent(newName -> {
                        if (!newName.trim().isEmpty()) {
                            layer.setName(newName.trim());
                            layerList.refresh();
                        }
                    });
                }
            }
        });

        VBox.setVgrow(layerList, Priority.ALWAYS);

        FlowPane buttons = new FlowPane(4, 4);
        buttons.getChildren().addAll(
            smallButton("+", "Add layer", e -> {
                canvas.getDocument().addLayer("Layer " + (canvas.getDocument().getLayers().size() + 1));
                refreshLayers();
            }),
            smallButton("-", "Remove layer", e -> {
                canvas.getDocument().removeLayer(selectedModelIndex());
                refreshLayers();
            }),
            smallButton("^", "Move layer up", e -> {
                canvas.getDocument().moveLayer(selectedModelIndex(), 1);
                refreshLayers();
            }),
            smallButton("v", "Move layer down", e -> {
                canvas.getDocument().moveLayer(selectedModelIndex(), -1);
                refreshLayers();
            }),
            smallButton("o", "Toggle visibility", e -> {
                int idx = selectedModelIndex();
                if (idx >= 0) {
                    ImageDocument.Layer layer = canvas.getDocument().getLayers().get(idx);
                    layer.setVisible(!layer.isVisible());
                    layerList.refresh();
                    canvas.draw();
                }
            }),
            smallButton("Lock", "Lock Layer", e -> {
                int idx = selectedModelIndex();
                if (idx >= 0) {
                    ImageDocument.Layer layer = canvas.getDocument().getLayers().get(idx);
                    layer.setLocked(!layer.isLocked());
                    layerList.refresh();
                }
            })
        );

        vbox.getChildren().addAll(layerList, buttons);
        return vbox;
    }

    private VBox buildHistoryPanel() {
        VBox vbox = new VBox(6);
        vbox.setStyle("-fx-background-color: #2d2d2d;");
        VBox.setVgrow(vbox, Priority.ALWAYS);

        historyList.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (newIdx.intValue() < 0 || isUpdatingHistorySelection) return;
            
            isUpdatingHistorySelection = true;
            int currentIdx = canvas.getUndoStack().size();
            int selected = newIdx.intValue();
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

        VBox.setVgrow(historyList, Priority.ALWAYS);
        vbox.getChildren().add(historyList);
        return vbox;
    }

    private void refreshHistory() {
        isUpdatingHistorySelection = true;
        historyModel.clear();
        List<FxPaintCanvas.UndoEntry> undoStack = canvas.getUndoStack();
        for (int i = undoStack.size() - 1; i >= 0; i--) {
            historyModel.add(undoStack.get(i).actionName);
        }
        historyModel.add("[Current State]");
        
        List<FxPaintCanvas.UndoEntry> redoStack = canvas.getRedoStack();
        for (FxPaintCanvas.UndoEntry entry : redoStack) {
            historyModel.add("(" + entry.actionName + ")");
        }
        
        historyList.getSelectionModel().select(undoStack.size());
        isUpdatingHistorySelection = false;
    }

    private int toModelIndex(int viewIndex) {
        return canvas.getDocument().getLayers().size() - 1 - viewIndex;
    }

    private int selectedModelIndex() {
        int viewIndex = layerList.getSelectionModel().getSelectedIndex();
        return viewIndex < 0 ? canvas.getDocument().getActiveLayerIndex() : toModelIndex(viewIndex);
    }

    private void refreshLayers() {
        layerModel.clear();
        List<ImageDocument.Layer> layers = canvas.getDocument().getLayers();
        for (int i = layers.size() - 1; i >= 0; i--) {
            layerModel.add(layers.get(i));
        }
        int active = canvas.getDocument().getActiveLayerIndex();
        layerList.getSelectionModel().select(layers.size() - 1 - active);
        canvas.draw();
    }

    private Button smallButton(String text, String tooltip, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnAction(handler);
        btn.setStyle("-fx-font-size: 10px; -fx-padding: 4 8;");
        return btn;
    }

    private void newDocument() {
        Stage sizeStage = new Stage(javafx.stage.StageStyle.UTILITY);
        sizeStage.initOwner(this);
        sizeStage.initModality(Modality.WINDOW_MODAL);
        sizeStage.setTitle("New Image");

        GridPane g = new GridPane();
        g.setPadding(new Insets(12));
        g.setHgap(8);
        g.setVgap(8);

        Label wLbl = new Label("Width:");
        Spinner<Integer> widthSpinner = new Spinner<>(1, 8192, 256, 16);
        g.add(wLbl, 0, 0);
        g.add(widthSpinner, 1, 0);

        Label hLbl = new Label("Height:");
        Spinner<Integer> heightSpinner = new Spinner<>(1, 8192, 256, 16);
        g.add(hLbl, 0, 1);
        g.add(heightSpinner, 1, 1);

        Button btnOk = new Button("OK");
        btnOk.setOnAction(e -> {
            canvas.setDocument(new ImageDocument(widthSpinner.getValue(), heightSpinner.getValue()));
            currentFile = null;
            refreshLayers();
            refreshHistory();
            sizeStage.close();
        });
        g.add(btnOk, 1, 2);

        sizeStage.setScene(new Scene(g));
        sizeStage.showAndWait();
    }

    private void openImage() {
        FileChooser chooser = createImageChooser("Open Image");
        File file = chooser.showOpenDialog(this);
        if (file != null) {
            try {
                BufferedImage image = ImageIO.read(file);
                if (image == null) {
                    throw new java.io.IOException("Unsupported image format");
                }
                canvas.setDocument(ImageDocument.fromImage(image));
                currentFile = file;
                refreshLayers();
                refreshHistory();
            } catch (Exception ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Could not open image: " + ex.getMessage());
                err.showAndWait();
            }
        }
    }

    private void saveImage(boolean forceDialog) {
        File target = currentFile;
        if (target == null || forceDialog) {
            FileChooser chooser = createImageChooser("Save Image");
            File file = chooser.showSaveDialog(this);
            if (file == null) {
                return;
            }
            target = ensurePngExtension(file);
        }
        writePng(target);
        currentFile = target;
    }

    private void exportToProject() {
        TextInputDialog dialog = new TextInputDialog("texture.png");
        dialog.initOwner(this);
        dialog.setTitle("Export");
        dialog.setHeaderText("Texture file name:");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                File target = ensurePngExtension(new File(exportFolder, name.trim()));
                if (target.exists()) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, target.getName() + " already exists. Overwrite?");
                    Optional<ButtonType> opt = confirm.showAndWait();
                    if (opt.isPresent() && opt.get() != ButtonType.OK) {
                        return;
                    }
                }
                writePng(target);
            }
        });
    }

    private void writePng(File target) {
        try {
            target.getParentFile().mkdirs();
            ImageIO.write(canvas.getDocument().composite(), "png", target);
            setTitle("Ignis Image Editor - " + target.getName());
        } catch (Exception ex) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Could not save image: " + ex.getMessage());
            err.showAndWait();
        }
    }

    private FileChooser createImageChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        if (exportFolder != null && exportFolder.exists()) {
            chooser.setInitialDirectory(exportFolder);
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
        for (double level : ZOOM_LEVELS) {
            if (level > currentZoom + 0.001) {
                setZoomLevel(level);
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
            zoomCombo.setValue(match);
        }
        updateStatus(null);
    }

    private void updateStatus(Point imgPos) {
        String coords = (imgPos == null) ? "[- , -]" : "[" + imgPos.x + ", " + imgPos.y + "]";
        int brushSize = canvas.getBrushSize();
        int zoomPercent = (int) Math.round(canvas.getZoom() * 100);
        statusLabel.setText(String.format(" Coords: %s  |  Brush Size: %d px  |  Zoom: %d%%", coords, brushSize, zoomPercent));
    }

    private String toHex(java.awt.Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
