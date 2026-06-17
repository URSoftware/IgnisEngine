package com.ignis.editor.fx;

import com.ignis.animation.AnimationFrame;
import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JavaFX implementation of the Ignis Animation Editor.
 * Ports com.ignis.editor.AnimationEditorFrame.
 */
public class FxAnimationEditor extends Stage {

    private final File projectFolder;
    private final File spritesFolder;
    private final GameObject targetObject;

    private final ObservableList<AnimationFrame> frameModel = FXCollections.observableArrayList();
    private final TextField nameField = new TextField("new_animation");
    private final CheckBox loopCheck = new CheckBox("Loop");
    private final Spinner<Integer> fpsSpinner = new Spinner<>(1, 60, 10);
    private ComboBox<SpriteAnimation.CurveType> curveCombo;

    private final Canvas previewCanvas = new Canvas(600, 350);
    private final Canvas timelineCanvas = new Canvas(800, 100);
    private final ScrollPane timelineScroll;

    private AnimationTimer previewTimer;
    private double previewElapsed = 0.0;
    private boolean previewPlaying = false;
    private int selectedIndex = -1;
    private SpriteAnimation.CurveType easingCurve = SpriteAnimation.CurveType.LINEAR;

    private final double pixelsPerSecond = 200.0;
    private int dragFrameIndex = -1;
    private boolean draggingPlayhead = false;

    public FxAnimationEditor(File projectFolder, File spritesFolder, GameObject targetObject) {
        setTitle("Ignis Animation Editor");
        initModality(Modality.NONE);
        this.projectFolder = projectFolder;
        this.spritesFolder = spritesFolder;
        this.targetObject = targetObject;

        loopCheck.setSelected(true);
        loopCheck.setStyle("-fx-text-fill: white;");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #282828;");

        root.setTop(buildToolBar());

        // Center preview
        StackPane previewContainer = new StackPane(previewCanvas);
        previewContainer.setStyle("-fx-background-color: #1e1e1e;");
        root.setCenter(previewContainer);

        // South timeline and controls
        VBox southPanel = new VBox();
        timelineScroll = new ScrollPane(timelineCanvas);
        timelineScroll.setPrefViewportHeight(120);
        timelineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        timelineScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        timelineScroll.setStyle("-fx-background: #1e1e1e; -fx-border-color: transparent;");

        southPanel.getChildren().addAll(timelineScroll, buildBottomBar());
        root.setBottom(southPanel);

        // ~60fps preview timer
        previewTimer = new AnimationTimer() {
            private long lastTime = 0;

            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double delta = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                previewElapsed += delta;
                double total = totalDuration();
                if (total > 0) {
                    if (loopCheck.isSelected()) {
                        previewElapsed %= total;
                    } else if (previewElapsed >= total) {
                        previewElapsed = total;
                        stopPreview();
                    }
                } else {
                    stopPreview();
                }
                drawTimeline();
                drawPreview();
            }

            @Override
            public void start() {
                lastTime = 0;
                super.start();
            }
        };

        setupTimelineListeners();

        Scene scene = new Scene(root, 900, 650);
        FxTheme.apply(scene);

        // Ctrl+D duplicate shortcut
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN), this::duplicateFrame);
        
        setScene(scene);

        loadExistingIfAny();
        drawTimeline();
        drawPreview();
    }

    private ToolBar buildToolBar() {
        Label nameLbl = new Label(" Name: ");
        nameLbl.setStyle("-fx-text-fill: white;");
        nameField.setPrefWidth(120);

        Label fpsLbl = new Label(" FPS: ");
        fpsLbl.setStyle("-fx-text-fill: white;");
        fpsSpinner.setPrefWidth(70);

        Button applyFps = new Button("Apply FPS to all");
        applyFps.setOnAction(e -> applyFpsToAll());

        Label curveLbl = new Label(" Easing Curve: ");
        curveLbl.setStyle("-fx-text-fill: white;");

        curveCombo = new ComboBox<>();
        curveCombo.getItems().addAll(SpriteAnimation.CurveType.values());
        curveCombo.setValue(SpriteAnimation.CurveType.LINEAR);
        curveCombo.setOnAction(e -> {
            easingCurve = curveCombo.getValue();
            drawPreview();
        });

        return new ToolBar(
                nameLbl, nameField,
                new Separator(),
                loopCheck,
                new Separator(),
                fpsLbl, fpsSpinner, applyFps,
                new Separator(),
                curveLbl, curveCombo
        );
    }

    private HBox buildBottomBar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(8));
        bar.setStyle("-fx-background-color: #2d2d2d;");
        bar.setAlignment(Pos.CENTER_LEFT);

        Button btnPlay = new Button("Play");
        btnPlay.setOnAction(e -> startPreview());
        Button btnStop = new Button("Stop");
        btnStop.setOnAction(e -> stopPreview());

        Label sep1 = new Label("  |  ");
        sep1.setStyle("-fx-text-fill: #888;");

        Button btnAdd = new Button("Add Frame...");
        btnAdd.setOnAction(e -> addFrames());
        Button btnRemove = new Button("Remove Frame");
        btnRemove.setOnAction(e -> removeFrame());
        Button btnDuplicate = new Button("Duplicate (Ctrl+D)");
        btnDuplicate.setOnAction(e -> duplicateFrame());

        Label sep2 = new Label("  |  ");
        sep2.setStyle("-fx-text-fill: #888;");

        Button btnSave = new Button("Save");
        btnSave.setOnAction(e -> save());
        Button btnLoad = new Button("Load...");
        btnLoad.setOnAction(e -> loadFromDialog());

        bar.getChildren().addAll(btnPlay, btnStop, sep1, btnAdd, btnRemove, btnDuplicate, sep2, btnSave, btnLoad);

        if (targetObject != null) {
            Button btnAssign = new Button("Assign to '" + targetObject.getName() + "'");
            btnAssign.setOnAction(e -> assignToTarget());
            bar.getChildren().addAll(new Separator(), btnAssign);
        }

        return bar;
    }

    private void setupTimelineListeners() {
        timelineCanvas.setOnMousePressed(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            double x = e.getX();
            double y = e.getY();

            int index = getFrameAtX(x);
            if (index >= 0) {
                dragFrameIndex = index;
                selectedIndex = Math.min(index, frameModel.size() - 1);
                drawTimeline();
                drawPreview();
            } else if (y < 25) {
                draggingPlayhead = true;
                scrub(x);
            } else {
                int clickedBlock = getBlockAtX(x);
                if (clickedBlock >= 0) {
                    selectedIndex = clickedBlock;
                    drawTimeline();
                    drawPreview();
                }
            }
        });

        timelineCanvas.setOnMouseDragged(e -> {
            double x = e.getX();
            if (dragFrameIndex > 0) {
                double newTime = x / pixelsPerSecond;
                double prevStartTime = getFrameStartTime(dragFrameIndex - 1);
                double newDuration = newTime - prevStartTime;
                if (newDuration >= 0.05) { // minimum 50ms
                    frameModel.get(dragFrameIndex - 1).setDuration(newDuration);
                    updateTimelineSize();
                    drawTimeline();
                    drawPreview();
                }
            } else if (draggingPlayhead) {
                scrub(x);
            }
        });

        timelineCanvas.setOnMouseReleased(e -> {
            dragFrameIndex = -1;
            draggingPlayhead = false;
        });
    }

    private void scrub(double x) {
        double time = x / pixelsPerSecond;
        previewElapsed = Math.max(0, Math.min(time, totalDuration()));
        drawTimeline();
        drawPreview();
    }

    private double getFrameStartTime(int index) {
        double t = 0;
        for (int i = 0; i < index; i++) {
            t += frameModel.get(i).getDuration();
        }
        return t;
    }

    private int getFrameAtX(double x) {
        for (int i = 0; i <= frameModel.size(); i++) {
            double t = getFrameStartTime(i);
            double kx = t * pixelsPerSecond;
            if (Math.abs(kx - x) <= 6) {
                return i;
            }
        }
        return -1;
    }

    private int getBlockAtX(double x) {
        for (int i = 0; i < frameModel.size(); i++) {
            double start = getFrameStartTime(i);
            double dur = frameModel.get(i).getDuration();
            double x1 = start * pixelsPerSecond;
            double x2 = (start + dur) * pixelsPerSecond;
            if (x >= x1 && x < x2) {
                return i;
            }
        }
        return -1;
    }

    private void updateTimelineSize() {
        double total = totalDuration();
        double w = Math.max(800.0, (total + 1.0) * pixelsPerSecond);
        timelineCanvas.setWidth(w);
    }

    private double totalDuration() {
        double total = 0;
        for (AnimationFrame f : frameModel) {
            total += f.getDuration();
        }
        return total;
    }

    private void startPreview() {
        previewElapsed = 0;
        previewPlaying = true;
        previewTimer.start();
    }

    private void stopPreview() {
        previewPlaying = false;
        previewTimer.stop();
        drawPreview();
        drawTimeline();
    }

    private void drawPreview() {
        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#282828"));
        gc.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());

        SpriteAnimation animation = buildAnimation();
        String path = previewPlaying
                ? animation.spritePathAt(previewElapsed)
                : (frameModel.isEmpty() ? null : frameModel.get(Math.max(0, selectedIndex)).getSpritePath());

        if (path == null) {
            gc.setFill(Color.GRAY);
            gc.setFont(new Font("Arial", 12));
            gc.fillText("Add frames and press Play", 20, 30);
            return;
        }

        BufferedImage bufferedImage = AssetResolver.loadImage(path);
        if (bufferedImage == null) {
            gc.setFill(Color.RED);
            gc.setFont(new Font("Arial", 12));
            gc.fillText("Missing: " + path, 20, 30);
            return;
        }

        Image image = SwingFXUtils.toFXImage(bufferedImage, null);

        double canvasW = previewCanvas.getWidth();
        double canvasH = previewCanvas.getHeight();

        double scale = Math.min((canvasW - 40.0) / image.getWidth(), (canvasH - 40.0) / image.getHeight());
        scale = Math.max(0.05, Math.min(scale, 8));

        double w = image.getWidth() * scale;
        double h = image.getHeight() * scale;

        gc.setImageSmoothing(false);
        gc.drawImage(image, (canvasW - w) / 2, (canvasH - h) / 2, w, h);
    }

    private void drawTimeline() {
        GraphicsContext gc = timelineCanvas.getGraphicsContext2D();
        double w = timelineCanvas.getWidth();
        double h = timelineCanvas.getHeight();

        // Background
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        // Ruler ticks
        gc.setFill(Color.web("#3c3c3c"));
        gc.fillRect(0, 0, w, 25);
        gc.setStroke(Color.web("#505050"));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, 25, w, 25);

        gc.setFill(Color.LIGHTGRAY);
        gc.setFont(new Font("Arial", 9));
        double maxTime = Math.max(totalDuration() + 1.0, w / pixelsPerSecond);

        for (double t = 0.0; t <= maxTime; t += 0.1) {
            double x = t * pixelsPerSecond;
            boolean major = Math.abs(t * 10 % 5) < 0.1;
            if (major) {
                gc.strokeLine(x, 10, x, 25);
                gc.fillText(String.format("%.1fs", t), x + 2, 20);
            } else {
                gc.strokeLine(x, 18, x, 25);
            }
        }

        // Draw track background for frames
        gc.setFill(Color.web("#2d2d2d"));
        gc.fillRect(0, 26, w, h - 26);

        // Draw frames as blocks
        for (int i = 0; i < frameModel.size(); i++) {
            double start = getFrameStartTime(i);
            double dur = frameModel.get(i).getDuration();
            double x1 = start * pixelsPerSecond;
            double x2 = (start + dur) * pixelsPerSecond;

            gc.setFill(i % 2 == 0 ? Color.web("#373741") : Color.web("#464650"));
            if (i == selectedIndex) {
                gc.setFill(Color.web("#0064b4"));
            }
            gc.fillRect(x1, 35, x2 - x1 - 1, 30);

            // Label
            gc.setFill(Color.WHITE);
            gc.fillText(shortName(frameModel.get(i).getSpritePath()), x1 + 5, 53);
        }

        // Draw diamond keyframes (diamonds at boundaries)
        for (int i = 0; i <= frameModel.size(); i++) {
            double t = getFrameStartTime(i);
            double x = t * pixelsPerSecond;

            gc.setFill(i == selectedIndex || (i > 0 && i - 1 == selectedIndex) ? Color.CYAN : Color.LIGHTGRAY);
            double[] dx = {x, x + 6, x, x - 6};
            double[] dy = {30, 35, 40, 35};
            gc.fillPolygon(dx, dy, 4);
            gc.setStroke(Color.DARKGRAY);
            gc.strokePolygon(dx, dy, 4);
        }

        // Draw playhead (vertical red line)
        double px = previewElapsed * pixelsPerSecond;
        gc.setStroke(Color.RED);
        gc.setLineWidth(1.0);
        gc.strokeLine(px, 0, px, h);

        // Draw head handle
        gc.setFill(Color.RED);
        double[] hx = {px - 5, px + 5, px + 5, px, px - 5};
        double[] hy = {0, 0, 8, 13, 8};
        gc.fillPolygon(hx, hy, 5);
    }

    private void addFrames() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Frames");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        if (spritesFolder != null && spritesFolder.exists()) {
            chooser.setInitialDirectory(spritesFolder);
        }
        List<File> files = chooser.showOpenMultipleDialog(this);
        if (files == null || files.isEmpty()) {
            return;
        }
        double duration = 1.0 / fpsSpinner.getValue();
        for (File file : files) {
            frameModel.add(new AnimationFrame(toProjectPath(file), duration));
        }
        if (selectedIndex == -1 && !frameModel.isEmpty()) {
            selectedIndex = 0;
        }
        updateTimelineSize();
        drawTimeline();
        drawPreview();
    }

    private void removeFrame() {
        if (selectedIndex >= 0 && selectedIndex < frameModel.size()) {
            frameModel.remove(selectedIndex);
            if (frameModel.isEmpty()) {
                selectedIndex = -1;
            } else {
                selectedIndex = Math.min(selectedIndex, frameModel.size() - 1);
            }
            updateTimelineSize();
            drawTimeline();
            drawPreview();
        }
    }

    private void duplicateFrame() {
        if (selectedIndex >= 0 && selectedIndex < frameModel.size()) {
            AnimationFrame current = frameModel.get(selectedIndex);
            frameModel.add(selectedIndex + 1, new AnimationFrame(current.getSpritePath(), current.getDuration()));
            selectedIndex = selectedIndex + 1;
            updateTimelineSize();
            drawTimeline();
            drawPreview();
        }
    }

    private void applyFpsToAll() {
        double duration = 1.0 / fpsSpinner.getValue();
        for (AnimationFrame f : frameModel) {
            f.setDuration(duration);
        }
        drawTimeline();
    }

    private SpriteAnimation buildAnimation() {
        SpriteAnimation animation = new SpriteAnimation(sanitizeName());
        animation.setLoop(loopCheck.isSelected());
        animation.setCurveType(easingCurve);
        for (AnimationFrame f : frameModel) {
            animation.addFrame(new AnimationFrame(f.getSpritePath(), f.getDuration()));
        }
        return animation;
    }

    private void save() {
        if (projectFolder == null) {
            Alert w = new Alert(Alert.AlertType.WARNING, "Open a project before saving animations.");
            w.showAndWait();
            return;
        }
        if (frameModel.isEmpty()) {
            Alert w = new Alert(Alert.AlertType.WARNING, "Add at least one frame.");
            w.showAndWait();
            return;
        }
        try {
            AnimationIO.save(buildAnimation(), projectFolder);
            Alert info = new Alert(Alert.AlertType.INFORMATION, "Saved to assets/animations/" + sanitizeName() + ".anim.json");
            info.showAndWait();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Could not save: " + e.getMessage());
            err.showAndWait();
        }
    }

    private void loadExistingIfAny() {
        if (projectFolder == null) {
            return;
        }
        List<SpriteAnimation> all = AnimationIO.loadAll(projectFolder);
        if (!all.isEmpty()) {
            applyAnimation(all.get(0));
        }
    }

    private void loadFromDialog() {
        if (projectFolder == null) {
            return;
        }
        List<SpriteAnimation> all = AnimationIO.loadAll(projectFolder);
        if (all.isEmpty()) {
            Alert info = new Alert(Alert.AlertType.INFORMATION, "No animations found in this project.");
            info.showAndWait();
            return;
        }

        List<String> names = new ArrayList<>();
        for (SpriteAnimation s : all) {
            names.add(s.getName());
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Load Animation");
        dialog.setHeaderText("Choose animation to load:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            int idx = names.indexOf(name);
            if (idx >= 0) {
                applyAnimation(all.get(idx));
            }
        });
    }

    private void applyAnimation(SpriteAnimation animation) {
        nameField.setText(animation.getName());
        loopCheck.setSelected(animation.isLoop());
        easingCurve = animation.getCurveType();
        if (curveCombo != null) {
            curveCombo.setValue(easingCurve);
        }
        frameModel.clear();
        for (AnimationFrame frame : animation.getFrames()) {
            frameModel.add(new AnimationFrame(frame.getSpritePath(), frame.getDuration()));
        }
        selectedIndex = frameModel.isEmpty() ? -1 : 0;
        updateTimelineSize();
        drawTimeline();
        drawPreview();
    }

    private void assignToTarget() {
        if (targetObject == null || frameModel.isEmpty()) {
            return;
        }
        Animator animator = new Animator();
        animator.addAnimation(buildAnimation());
        animator.setAutoPlay(true);
        animator.reset();
        targetObject.setAnimator(animator);

        Alert info = new Alert(Alert.AlertType.INFORMATION,
                "Animator assigned to '" + targetObject.getName() + "'. Press Play in the editor to see it.");
        info.showAndWait();
    }

    private String sanitizeName() {
        String name = nameField.getText().trim();
        return name.isEmpty() ? "animation" : name;
    }

    private String toProjectPath(File file) {
        String relative = AssetResolver.relativize(file);
        if (relative != null) {
            return relative;
        }
        System.err.println("[AnimationEditor] Frame outside project, stored as absolute: " + file);
        return file.getAbsolutePath();
    }

    private static String shortName(String path) {
        if (path == null) {
            return "(none)";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
