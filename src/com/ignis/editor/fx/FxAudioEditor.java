package com.ignis.editor.fx;

import com.ignis.audioeditor.WavAudioProcessor;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JavaFX implementation of the integrated DAW style Audio Editor.
 * Ports com.ignis.audioeditor.AudioEditorFrame.
 */
public class FxAudioEditor extends Stage {

    private final List<AudioTrack> tracks = new ArrayList<>();
    private final VBox timelineContainer = new VBox();
    private final Slider masterVolumeSlider = new Slider(0, 100, 80);
    private final Label statusLabel = new Label();
    private final Canvas visualizerCanvas = new Canvas(120, 20);

    private boolean isPlaying = false;
    private boolean isRecording = false; // Placeholder for recording state
    private double playheadTime = 0.0; // In seconds
    private int zoomFactor = 40; // Pixels per second

    private final List<TrackRow> trackRows = new ArrayList<>();
    private Canvas rulerCanvas;

    // Selection and Clipboard state
    private AudioClip selectedClip = null;
    private double selectionStartTime = -1.0;
    private double selectionEndTime = -1.0;
    private boolean isSelecting = false;

    private byte[] clipboardPcm = null;
    private AudioFormat clipboardFormat = null;
    private double clipboardDuration = 0.0;

    private DAWMixerThread mixerThread = null;
    private VisualizerTimer visualizerTimer;

    // --- Crossover 3-Band Equalizer (IIR Filter) ---
    public static class TrackEQ {
        private static final double es_lowfreq = 400.0;
        private static final double es_highfreq = 4000.0;

        public double lg = 1.0;
        public double mg = 1.0;
        public double hg = 1.0;

        private double f1p0_L, f1p1_L, f1p2_L, f1p3_L;
        private double f2p0_L, f2p1_L, f2p2_L, f2p3_L;

        private double f1p0_R, f1p1_R, f1p2_R, f1p3_R;
        private double f2p0_R, f2p1_R, f2p2_R, f2p3_R;

        private double k1, k2;

        public TrackEQ() {
            reset();
        }

        public void setGains(double low, double mid, double high) {
            this.lg = low;
            this.mg = mid;
            this.hg = high;
        }

        public void reset() {
            f1p0_L = f1p1_L = f1p2_L = f1p3_L = 0.0;
            f2p0_L = f2p1_L = f2p2_L = f2p3_L = 0.0;

            f1p0_R = f1p1_R = f1p2_R = f1p3_R = 0.0;
            f2p0_R = f2p1_R = f2p2_R = f2p3_R = 0.0;

            k1 = 2.0 * Math.sin(Math.PI * es_lowfreq / 44100.0);
            k2 = 2.0 * Math.sin(Math.PI * es_highfreq / 44100.0);
        }

        public double[] processSample(double sampleL, double sampleR) {
            f1p0_L += (k1 * (sampleL - f1p0_L));
            f1p1_L += (k1 * (f1p0_L - f1p1_L));
            f1p2_L += (k1 * (f1p1_L - f1p2_L));
            f1p3_L += (k1 * (f1p2_L - f1p3_L));
            double low_L = f1p3_L;

            f2p0_L += (k2 * (sampleL - f2p0_L));
            f2p1_L += (k2 * (f2p0_L - f2p1_L));
            f2p2_L += (k2 * (f2p1_L - f2p2_L));
            f2p3_L += (k2 * (f2p2_L - f2p3_L));
            double high_L = sampleL - f2p3_L;
            double mid_L = f2p3_L - f1p3_L;

            double outL = (low_L * lg) + (mid_L * mg) + (high_L * hg);

            f1p0_R += (k1 * (sampleR - f1p0_R));
            f1p1_R += (k1 * (f1p0_R - f1p1_R));
            f1p2_R += (k1 * (f1p1_R - f1p2_R));
            f1p3_R += (k1 * (f1p2_R - f1p3_R));
            double low_R = f1p3_R;

            f2p0_R += (k2 * (sampleR - f2p0_R));
            f2p1_R += (k2 * (f2p0_R - f2p1_R));
            f2p2_R += (k2 * (f2p1_R - f2p2_R));
            f2p3_R += (k2 * (f2p2_R - f2p3_R));
            double high_R = sampleR - f2p3_R;
            double mid_R = f2p3_R - f1p3_R;

            double outR = (low_R * lg) + (mid_R * mg) + (high_R * hg);

            return new double[] { outL, outR };
        }
    }

    public static class AudioClip {
        public String name;
        public double start;
        public double duration;
        public Color color;

        public File audioFile;
        public byte[] rawPcm;
        public byte[] processedPcm;
        public AudioFormat audioFormat;
        public double fadeIn = 0.0;
        public double fadeOut = 0.0;

        public AudioClip(String name, double start, double duration, Color color) {
            this.name = name;
            this.start = start;
            this.duration = duration;
            this.color = color;
        }
    }

    public static class AudioTrack {
        public String name;
        public double volume = 0.8;
        public double pan = 0.0;
        public boolean isMuted = false;
        public boolean isSolo = false;
        public final TrackEQ eq = new TrackEQ();
        public final List<AudioClip> clips = new ArrayList<>();

        public AudioTrack(String name) {
            this.name = name;
        }
    }

    public FxAudioEditor() {
        setTitle("Ignis DAW - Audio Editor");
        initModality(Modality.NONE);

        tracks.add(new AudioTrack("BGM Track"));
        tracks.add(new AudioTrack("SFX Track 1"));
        tracks.add(new AudioTrack("Voiceover Track"));

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #282828;");

        // --- Controls Panel (Top) ---
        VBox topContainer = new VBox();
        
        ToolBar controlsToolbar = new ToolBar();
        controlsToolbar.setStyle("-fx-background-color: #2d2d2d;");
        
        Button btnPlay = new Button("Play");
        styleButton(btnPlay, "#2e8b57");
        Button btnPause = new Button("Pause");
        styleButton(btnPause, "#b4821e");
        Button btnStop = new Button("Stop");
        styleButton(btnStop, "#b22222");
        Button btnAddTrack = new Button("Add Track");
        styleButton(btnAddTrack, "#4682b4");
        Button btnExport = new Button("Export Mixdown");
        styleButton(btnExport, "#646464");

        Label masterLbl = new Label("  Master Volume: ");
        masterLbl.setStyle("-fx-text-fill: white;");
        masterVolumeSlider.setPrefWidth(120);

        controlsToolbar.getItems().addAll(btnPlay, btnPause, btnStop, new Separator(), btnAddTrack, new Separator(), masterLbl, masterVolumeSlider, new Separator(), btnExport);

        // DAW Edit Toolbar
        ToolBar editToolbar = new ToolBar();
        editToolbar.setStyle("-fx-background-color: #282828;");

        Button btnImport = new Button("Import WAV");
        styleButton(btnImport, "#7850a0");
        Button btnSplit = new Button("Split Clip");
        styleButton(btnSplit, "#464646");
        Button btnFadeIn = new Button("Fade In");
        styleButton(btnFadeIn, "#464646");
        Button btnFadeOut = new Button("Fade Out");
        styleButton(btnFadeOut, "#464646");
        Button btnMerge = new Button("Merge Track");
        styleButton(btnMerge, "#464646");
        Button btnDeleteClip = new Button("Delete Clip");
        styleButton(btnDeleteClip, "#963232");

        Button btnCopy = new Button("Copy");
        styleButton(btnCopy, "#325050");
        Button btnCut = new Button("Cut");
        styleButton(btnCut, "#503250");
        Button btnPaste = new Button("Paste");
        styleButton(btnPaste, "#325032");

        Button btnAmplify = new Button("Amplify");
        styleButton(btnAmplify, "#505032");
        Button btnNormalize = new Button("Normalize");
        styleButton(btnNormalize, "#323250");
        Button btnGenerateTone = new Button("Generate Tone");
        styleButton(btnGenerateTone, "#78643c");

        editToolbar.getItems().addAll(
                btnImport, btnSplit, btnFadeIn, btnFadeOut, btnMerge, btnDeleteClip, new Separator(),
                btnCopy, btnCut, btnPaste, new Separator(),
                btnAmplify, btnNormalize, btnGenerateTone
        );

        topContainer.getChildren().addAll(controlsToolbar, editToolbar);
        root.setTop(topContainer);

        // --- Central Timeline Area ---
        timelineContainer.setStyle("-fx-background-color: #1e1e1e;");
        ScrollPane scrollPane = new ScrollPane(timelineContainer);
        scrollPane.setStyle("-fx-background: #1e1e1e; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        root.setCenter(scrollPane);

        // --- Status Bar (Bottom) ---
        BorderPane statusBar = new BorderPane();
        statusBar.setPadding(new Insets(6, 12, 6, 12));
        statusBar.setStyle("-fx-background-color: #282828; -fx-border-color: #3c3c3c; -fx-border-width: 1 0 0 0;");
        
        statusLabel.setText("Status: Stopped  |  Time: 00:00.00  |  Master: 80%  (Shift+Drag timeline to select range)");
        statusLabel.setStyle("-fx-text-fill: lightgray; -fx-font-size: 11px;");
        statusBar.setLeft(statusLabel);

        // Visualizer spectrum
        visualizerCanvas.setHeight(20);
        visualizerCanvas.setWidth(120);
        statusBar.setRight(visualizerCanvas);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1000, 650);
        setScene(scene);

        // Rebuild timeline UI
        rebuildTimeline();

        // --- Playback Actions ---
        btnPlay.setOnAction(e -> {
            if (isPlaying) return;
            isPlaying = true;
            isRecording = false;
            mixerThread = new DAWMixerThread();
            mixerThread.start();
            if (visualizerTimer != null) visualizerTimer.stop();
            visualizerTimer = new VisualizerTimer();
            visualizerTimer.start();
        });

        btnPause.setOnAction(e -> {
            isPlaying = false;
            if (mixerThread != null) {
                try { mixerThread.join(100); } catch (InterruptedException ignore) {}
            }
            updatePlayhead();
            if (visualizerTimer != null) visualizerTimer.stop();
            clearVisualizer();
        });

        btnStop.setOnAction(e -> {
            isPlaying = false;
            isRecording = false;
            if (mixerThread != null) {
                try { mixerThread.join(100); } catch (InterruptedException ignore) {}
            }
            playheadTime = 0.0;
            updatePlayhead();
            if (visualizerTimer != null) visualizerTimer.stop();
            clearVisualizer();
        });

        btnAddTrack.setOnAction(e -> {
            tracks.add(new AudioTrack("Audio Track " + (tracks.size() + 1)));
            rebuildTimeline();
        });

        btnImport.setOnAction(e -> importWavFile());
        btnSplit.setOnAction(e -> splitSelectedClip());
        btnFadeIn.setOnAction(e -> applyFade(true));
        btnFadeOut.setOnAction(e -> applyFade(false));
        btnMerge.setOnAction(e -> mergeActiveTrack());
        btnDeleteClip.setOnAction(e -> deleteSelectedClip());

        btnCopy.setOnAction(e -> copySelectedRange());
        btnCut.setOnAction(e -> cutSelectedRange());
        btnPaste.setOnAction(e -> pasteClipboard());

        btnAmplify.setOnAction(e -> amplifySelectedClip());
        btnNormalize.setOnAction(e -> normalizeSelectedClip());
        btnGenerateTone.setOnAction(e -> openGenerateToneDialog());

        masterVolumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> updateStatusText());
    }

    private void styleButton(Button b, String hexColor) {
        b.setStyle("-fx-background-color: " + hexColor + "; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 4px;");
    }

    private void rebuildTimeline() {
        timelineContainer.getChildren().clear();
        trackRows.clear();

        // 1. Timeline Ruler Row
        HBox rulerRow = new HBox();
        rulerRow.setStyle("-fx-background-color: #262626;");
        Region rulerSpacer = new Region();
        rulerSpacer.setPrefWidth(180);
        rulerSpacer.setMinWidth(180);
        
        rulerCanvas = new Canvas(820, 22);
        rulerRow.getChildren().addAll(rulerSpacer, rulerCanvas);
        timelineContainer.getChildren().add(rulerRow);

        rulerCanvas.setOnMousePressed(e -> {
            playheadTime = (e.getX()) / zoomFactor;
            playheadTime = Math.max(0.0, playheadTime);
            updatePlayhead();
        });

        // 2. Add Audio Tracks Rows
        for (AudioTrack track : tracks) {
            TrackRow row = new TrackRow(track);
            trackRows.add(row);
            timelineContainer.getChildren().add(row);
        }

        drawRuler();
        for (TrackRow row : trackRows) {
            row.draw();
        }
    }

    private void drawRuler() {
        GraphicsContext gc = rulerCanvas.getGraphicsContext2D();
        double w = rulerCanvas.getWidth();
        double h = rulerCanvas.getHeight();
        gc.setFill(Color.web("#262626"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#505050"));
        gc.setLineWidth(1.0);
        for (int s = 0; s < 100; s += 2) {
            double x = s * zoomFactor;
            gc.strokeLine(x, 10, x, h);
            if (s % 10 == 0 || zoomFactor >= 30) {
                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(new Font("Arial", 9));
                gc.fillText(s + "s", x + 2, 12);
            }
        }
    }

    private void updatePlayhead() {
        Platform.runLater(() -> {
            updateStatusText();
            drawRuler();
            // Draw orange vertical playhead on ruler
            GraphicsContext gc = rulerCanvas.getGraphicsContext2D();
            double px = playheadTime * zoomFactor;
            gc.setStroke(Color.ORANGE);
            gc.setLineWidth(1.5);
            gc.strokeLine(px, 0, px, rulerCanvas.getHeight());

            for (TrackRow row : trackRows) {
                row.draw();
            }
        });
    }

    private void updateStatusText() {
        int min = (int) (playheadTime / 60);
        double sec = playheadTime % 60;
        String status = isPlaying ? "Playing" : "Stopped";
        statusLabel.setText(String.format("Status: %s  |  Time: %02d:%05.2f  |  Master: %d%%  (Shift+Drag timeline to select range)",
                status, min, sec, (int) masterVolumeSlider.getValue()));
    }

    private void clearVisualizer() {
        GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#282828"));
        gc.fillRect(0, 0, visualizerCanvas.getWidth(), visualizerCanvas.getHeight());
    }

    private class VisualizerTimer extends AnimationTimer {
        private long lastUpdate = 0;
        @Override
        public void handle(long now) {
            if (now - lastUpdate >= 50_000_000L) { // 50ms
                GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
                double w = visualizerCanvas.getWidth();
                double h = visualizerCanvas.getHeight();
                gc.setFill(Color.web("#282828"));
                gc.fillRect(0, 0, w, h);

                if (isPlaying) {
                    gc.setFill(Color.web("#2e8b57"));
                    for (double i = 0; i < w; i += 6) {
                        double barH = (Math.random() * (h - 4)) + 2;
                        gc.fillRect(i, h - barH, 4, barH);
                    }
                }
                lastUpdate = now;
            }
        }
    }

    // --- Track UI Row Wrapper ---
    private class TrackRow extends HBox {
        private final AudioTrack track;
        private final Canvas clipsCanvas;
        
        public TrackRow(AudioTrack track) {
            this.track = track;
            setStyle("-fx-border-color: #323232; -fx-border-width: 0 0 1 0; -fx-background-color: #1e1e1e;");
            setPrefHeight(95);
            setMinHeight(95);

            // Left Fader Box
            GridPane faderPanel = new GridPane();
            faderPanel.setPadding(new Insets(6));
            faderPanel.setHgap(4);
            faderPanel.setVgap(4);
            faderPanel.setPrefWidth(180);
            faderPanel.setMinWidth(180);
            faderPanel.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #3c3c3c; -fx-border-width: 0 1 0 0;");

            Label nameLbl = new Label(track.name);
            nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
            faderPanel.add(nameLbl, 0, 0, 3, 1);

            Slider volSlider = new Slider(0, 100, track.volume * 100);
            volSlider.setPrefWidth(90);
            volSlider.valueProperty().addListener((obs, oldVal, newVal) -> track.volume = newVal.doubleValue() / 100.0);
            faderPanel.add(new Label("Vol:"), 0, 1);
            faderPanel.add(volSlider, 1, 1, 2, 1);

            Slider panSlider = new Slider(-1, 1, track.pan);
            panSlider.setPrefWidth(90);
            panSlider.valueProperty().addListener((obs, oldVal, newVal) -> track.pan = newVal.doubleValue());
            faderPanel.add(new Label("Pan:"), 0, 2);
            faderPanel.add(panSlider, 1, 2, 2, 1);

            ToggleButton btnMute = new ToggleButton("M");
            btnMute.setSelected(track.isMuted);
            btnMute.setOnAction(e -> track.isMuted = btnMute.isSelected());
            ToggleButton btnSolo = new ToggleButton("S");
            btnSolo.setSelected(track.isSolo);
            btnSolo.setOnAction(e -> track.isSolo = btnSolo.isSelected());
            HBox msBox = new HBox(4, btnMute, btnSolo);
            faderPanel.add(msBox, 0, 3, 3, 1);

            // EQ Knobs/Sliders (Low, Mid, High)
            Slider lowEq = new Slider(0, 2, track.eq.lg);
            Slider midEq = new Slider(0, 2, track.eq.mg);
            Slider highEq = new Slider(0, 2, track.eq.hg);
            lowEq.setPrefWidth(30); midEq.setPrefWidth(30); highEq.setPrefWidth(30);

            lowEq.valueProperty().addListener((obs, oldVal, newVal) -> track.eq.lg = newVal.doubleValue());
            midEq.valueProperty().addListener((obs, oldVal, newVal) -> track.eq.mg = newVal.doubleValue());
            highEq.valueProperty().addListener((obs, oldVal, newVal) -> track.eq.hg = newVal.doubleValue());

            HBox eqBox = new HBox(4, lowEq, midEq, highEq);
            faderPanel.add(new Label("EQ:"), 0, 4);
            faderPanel.add(eqBox, 1, 4, 2, 1);

            getChildren().add(faderPanel);

            // Right timeline canvas
            clipsCanvas = new Canvas(820, 95);
            getChildren().add(clipsCanvas);

            setupInteraction();
        }

        private void setupInteraction() {
            clipsCanvas.setOnMousePressed(e -> {
                double x = e.getX();
                double clickTime = x / zoomFactor;

                if (e.isShiftDown()) {
                    isSelecting = true;
                    selectionStartTime = clickTime;
                    selectionEndTime = clickTime;
                } else {
                    isSelecting = false;
                    selectionStartTime = -1.0;
                    selectionEndTime = -1.0;

                    // Select clip
                    selectedClip = null;
                    for (AudioClip clip : track.clips) {
                        if (clickTime >= clip.start && clickTime <= clip.start + clip.duration) {
                            selectedClip = clip;
                            break;
                        }
                    }
                }
                updatePlayhead();
            });

            clipsCanvas.setOnMouseDragged(e -> {
                if (isSelecting) {
                    double dragTime = e.getX() / zoomFactor;
                    selectionEndTime = Math.max(0.0, dragTime);
                    updatePlayhead();
                }
            });
        }

        public void draw() {
            GraphicsContext gc = clipsCanvas.getGraphicsContext2D();
            double w = clipsCanvas.getWidth();
            double h = clipsCanvas.getHeight();

            gc.setFill(Color.web("#1e1e1e"));
            gc.fillRect(0, 0, w, h);

            // Grid background
            gc.setStroke(Color.web("#2d2d2d"));
            gc.setLineWidth(1.0);
            for (int s = 0; s < 100; s += 2) {
                double x = s * zoomFactor;
                gc.strokeLine(x, 0, x, h);
            }

            // Render clips
            for (AudioClip clip : track.clips) {
                double cx = clip.start * zoomFactor;
                double cw = clip.duration * zoomFactor;
                double cy = 10;
                double ch = h - 20;

                gc.setFill(clip.color);
                gc.fillRoundRect(cx, cy, cw, ch, 6, 6);

                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(cx, cy, cw, ch, 6, 6);

                if (clip == selectedClip) {
                    gc.setStroke(Color.YELLOW);
                    gc.setLineWidth(2.5);
                    gc.strokeRoundRect(cx - 1, cy - 1, cw + 2, ch + 2, 6, 6);
                }

                // Render actual PCM waveform
                if (clip.processedPcm != null && clip.processedPcm.length > 0) {
                    gc.setStroke(Color.rgb(255, 255, 255, 0.55));
                    gc.setLineWidth(1.0);
                    double mid = cy + ch / 2.0;
                    int pcmLengthSamples = clip.processedPcm.length / 4;

                    for (int px = 2; px < cw - 2; px++) {
                        double tStart = px / (double) zoomFactor;
                        double tEnd = (px + 1) / (double) zoomFactor;

                        int sampleStart = (int) (tStart * 44100.0);
                        int sampleEnd = (int) (tEnd * 44100.0);

                        sampleStart = Math.max(0, Math.min(pcmLengthSamples - 1, sampleStart));
                        sampleEnd = Math.max(sampleStart, Math.min(pcmLengthSamples, sampleEnd));

                        int maxVal = 0;
                        ByteBuffer buf = ByteBuffer.wrap(clip.processedPcm).order(ByteOrder.LITTLE_ENDIAN);

                        int step = Math.max(1, (sampleEnd - sampleStart) / 10);
                        for (int s = sampleStart; s < sampleEnd; s += step) {
                            int offset = s * 4;
                            if (offset + 1 < clip.processedPcm.length) {
                                short valL = buf.getShort(offset);
                                maxVal = Math.max(maxVal, Math.abs(valL));
                            }
                        }

                        double norm = maxVal / 32768.0;
                        double hHalf = norm * (ch / 2.0);

                        double screenX = cx + px;
                        gc.strokeLine(screenX, mid - hHalf, screenX, mid + hHalf);
                    }
                }

                gc.setFill(Color.WHITE);
                gc.setFont(new Font("Arial", 10));
                gc.fillText(clip.name, cx + 6, cy + 14);
            }

            // Draw time range selection overlay
            if (selectionStartTime >= 0 && selectionEndTime > selectionStartTime) {
                double sx = selectionStartTime * zoomFactor;
                double sw = (selectionEndTime - selectionStartTime) * zoomFactor;
                gc.setFill(Color.web("#0096ff3c"));
                gc.fillRect(sx, 0, sw, h);
                gc.setStroke(Color.web("#0096ff78"));
                gc.strokeRect(sx, 0, sw, h);
            }

            // Vertical playhead line
            double px = playheadTime * zoomFactor;
            gc.setStroke(Color.ORANGE);
            gc.setLineWidth(1.5);
            gc.strokeLine(px, 0, px, h);
        }
    }

    // --- Audio Operations ---

    private void importWavFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import WAV File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV Audio (*.wav)", "*.wav"));
        
        File file = chooser.showOpenDialog(this);
        if (file != null) {
            try {
                WavAudioProcessor.WavData wav = WavAudioProcessor.readWav(file);
                TextInputDialog posDialog = new TextInputDialog("0.0");
                posDialog.initOwner(this);
                posDialog.setTitle("Import Position");
                posDialog.setHeaderText("Specify insert position in seconds:");
                posDialog.setContentText("Seconds:");
                Optional<String> res = posDialog.showAndWait();
                
                double startPos = 0.0;
                if (res.isPresent()) {
                    try { startPos = Double.parseDouble(res.get()); } catch (Exception ignore) {}
                }

                AudioTrack activeTrack = getSelectedTrack();
                AudioClip clip = new AudioClip(file.getName(), startPos, wav.duration, Color.web("#4682b4"));
                clip.audioFile = file;
                clip.rawPcm = wav.pcmData;
                clip.processedPcm = wav.pcmData.clone();
                clip.audioFormat = wav.format;

                activeTrack.clips.add(clip);
                selectedClip = clip;
                updatePlayhead();
            } catch (Exception ex) {
                Alert error = new Alert(Alert.AlertType.ERROR, "Could not load WAV: " + ex.getMessage());
                error.showAndWait();
            }
        }
    }

    private void splitSelectedClip() {
        if (selectedClip == null) return;
        AudioTrack track = getTrackForClip(selectedClip);
        if (track == null) return;

        double splitPoint = playheadTime;
        if (splitPoint > selectedClip.start && splitPoint < selectedClip.start + selectedClip.duration) {
            double firstDur = splitPoint - selectedClip.start;
            double secondDur = selectedClip.duration - firstDur;

            // Trim PCM
            byte[] firstPcm = WavAudioProcessor.trimPcm(selectedClip.rawPcm, selectedClip.audioFormat, 0, firstDur);
            byte[] secondPcm = WavAudioProcessor.trimPcm(selectedClip.rawPcm, selectedClip.audioFormat, firstDur, selectedClip.duration);

            AudioClip second = new AudioClip(selectedClip.name + " (Split)", splitPoint, secondDur, selectedClip.color);
            second.rawPcm = secondPcm;
            second.processedPcm = secondPcm.clone();
            second.audioFormat = selectedClip.audioFormat;

            // Shrink first
            selectedClip.duration = firstDur;
            selectedClip.rawPcm = firstPcm;
            selectedClip.processedPcm = firstPcm.clone();

            track.clips.add(second);
            selectedClip = second;
            updatePlayhead();
        }
    }

    private void applyFade(boolean isFadeIn) {
        if (selectedClip == null) return;
        TextInputDialog durationDialog = new TextInputDialog("1.0");
        durationDialog.initOwner(this);
        durationDialog.setTitle("Fade Duration");
        durationDialog.setHeaderText("Specify fade duration in seconds:");
        durationDialog.setContentText("Seconds:");
        
        durationDialog.showAndWait().ifPresent(res -> {
            try {
                double dur = Double.parseDouble(res);
                if (isFadeIn) selectedClip.fadeIn = dur;
                else selectedClip.fadeOut = dur;
                processClipsFades(selectedClip);
                updatePlayhead();
            } catch (Exception ignore) {}
        });
    }

    private void processClipsFades(AudioClip clip) {
        clip.processedPcm = WavAudioProcessor.applyFades(clip.rawPcm, clip.audioFormat, clip.fadeIn, clip.fadeOut);
    }

    private void mergeActiveTrack() {
        AudioTrack active = getSelectedTrack();
        if (active.clips.isEmpty()) return;

        List<byte[]> streams = new ArrayList<>();
        List<Double> starts = new ArrayList<>();
        for (AudioClip c : active.clips) {
            streams.add(c.processedPcm);
            starts.add(c.start);
        }

        AudioFormat fmt = active.clips.get(0).audioFormat;
        byte[] mixed = WavAudioProcessor.mixTracks(streams, starts, fmt);
        double totalDur = (double) mixed.length / fmt.getFrameSize() / fmt.getSampleRate();

        active.clips.clear();
        AudioClip merged = new AudioClip("Merged Track", 0.0, totalDur, Color.web("#a05050"));
        merged.rawPcm = mixed;
        merged.processedPcm = mixed.clone();
        merged.audioFormat = fmt;
        active.clips.add(merged);
        selectedClip = merged;
        updatePlayhead();
    }

    private void deleteSelectedClip() {
        if (selectedClip == null) return;
        AudioTrack track = getTrackForClip(selectedClip);
        if (track != null) {
            track.clips.remove(selectedClip);
            selectedClip = null;
            updatePlayhead();
        }
    }

    private void copySelectedRange() {
        if (selectionStartTime < 0 || selectionEndTime <= selectionStartTime) return;
        AudioTrack track = getSelectedTrack();
        if (track == null || track.clips.isEmpty()) return;

        // Clip/Range mixing subset
        List<byte[]> streams = new ArrayList<>();
        List<Double> starts = new ArrayList<>();
        AudioFormat fmt = track.clips.get(0).audioFormat;

        for (AudioClip clip : track.clips) {
            double clipEnd = clip.start + clip.duration;
            double overlapStart = Math.max(selectionStartTime, clip.start);
            double overlapEnd = Math.min(selectionEndTime, clipEnd);

            if (overlapStart < overlapEnd) {
                byte[] trimmed = WavAudioProcessor.trimPcm(clip.processedPcm, fmt, overlapStart - clip.start, overlapEnd - clip.start);
                streams.add(trimmed);
                starts.add(overlapStart - selectionStartTime);
            }
        }

        if (!streams.isEmpty()) {
            clipboardPcm = WavAudioProcessor.mixTracks(streams, starts, fmt);
            clipboardFormat = fmt;
            clipboardDuration = selectionEndTime - selectionStartTime;
            statusLabel.setText("✓ Audio range copied to clipboard.");
        }
    }

    private void cutSelectedRange() {
        copySelectedRange();
        if (clipboardPcm == null) return;

        AudioTrack track = getSelectedTrack();
        List<AudioClip> toRemove = new ArrayList<>();
        List<AudioClip> toAdd = new ArrayList<>();

        AudioFormat fmt = clipboardFormat;

        for (AudioClip clip : track.clips) {
            double clipEnd = clip.start + clip.duration;
            if (clip.start >= selectionStartTime && clipEnd <= selectionEndTime) {
                toRemove.add(clip);
            } else if (clip.start < selectionStartTime && clipEnd > selectionEndTime) {
                // Split in two
                byte[] first = WavAudioProcessor.trimPcm(clip.rawPcm, fmt, 0, selectionStartTime - clip.start);
                byte[] second = WavAudioProcessor.trimPcm(clip.rawPcm, fmt, selectionEndTime - clip.start, clip.duration);

                clip.duration = selectionStartTime - clip.start;
                clip.rawPcm = first;
                clip.processedPcm = first.clone();

                AudioClip right = new AudioClip(clip.name + " (Cut)", selectionEndTime, clipEnd - selectionEndTime, clip.color);
                right.rawPcm = second;
                right.processedPcm = second.clone();
                right.audioFormat = fmt;
                toAdd.add(right);
            } else if (clip.start < selectionStartTime && clipEnd > selectionStartTime) {
                // Trim right side
                byte[] trimmed = WavAudioProcessor.trimPcm(clip.rawPcm, fmt, 0, selectionStartTime - clip.start);
                clip.duration = selectionStartTime - clip.start;
                clip.rawPcm = trimmed;
                clip.processedPcm = trimmed.clone();
            } else if (clip.start >= selectionStartTime && clip.start < selectionEndTime) {
                // Trim left side
                byte[] trimmed = WavAudioProcessor.trimPcm(clip.rawPcm, fmt, selectionEndTime - clip.start, clip.duration);
                clip.duration = clipEnd - selectionEndTime;
                clip.start = selectionEndTime;
                clip.rawPcm = trimmed;
                clip.processedPcm = trimmed.clone();
            }
        }
        track.clips.removeAll(toRemove);
        track.clips.addAll(toAdd);
        selectedClip = null;
        selectionStartTime = -1.0;
        selectionEndTime = -1.0;
        updatePlayhead();
    }

    private void pasteClipboard() {
        if (clipboardPcm == null) return;
        AudioTrack active = getSelectedTrack();
        
        AudioClip pasted = new AudioClip("Pasted Clip", playheadTime, clipboardDuration, Color.web("#2e8b57"));
        pasted.rawPcm = clipboardPcm.clone();
        pasted.processedPcm = clipboardPcm.clone();
        pasted.audioFormat = clipboardFormat;

        active.clips.add(pasted);
        selectedClip = pasted;
        updatePlayhead();
    }

    private void amplifySelectedClip() {
        if (selectedClip == null) return;
        TextInputDialog ampDialog = new TextInputDialog("120");
        ampDialog.initOwner(this);
        ampDialog.setTitle("Amplify");
        ampDialog.setHeaderText("Specify amplification percentage:");
        ampDialog.setContentText("Percentage:");
        
        ampDialog.showAndWait().ifPresent(res -> {
            try {
                double factor = Double.parseDouble(res) / 100.0;
                ByteBuffer buf = ByteBuffer.wrap(selectedClip.rawPcm).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < selectedClip.rawPcm.length; i += 2) {
                    short val = buf.getShort(i);
                    short newVal = (short) Math.max(-32768, Math.min(32767, val * factor));
                    buf.putShort(i, newVal);
                }
                processClipsFades(selectedClip);
                updatePlayhead();
            } catch (Exception ignore) {}
        });
    }

    private void normalizeSelectedClip() {
        if (selectedClip == null) return;
        
        ByteBuffer buf = ByteBuffer.wrap(selectedClip.rawPcm).order(ByteOrder.LITTLE_ENDIAN);
        int maxVal = 0;
        for (int i = 0; i < selectedClip.rawPcm.length; i += 2) {
            short val = buf.getShort(i);
            maxVal = Math.max(maxVal, Math.abs(val));
        }

        if (maxVal > 0) {
            double factor = 32767.0 / maxVal;
            for (int i = 0; i < selectedClip.rawPcm.length; i += 2) {
                short val = buf.getShort(i);
                short newVal = (short) (val * factor);
                buf.putShort(i, newVal);
            }
            processClipsFades(selectedClip);
            updatePlayhead();
        }
    }

    private void openGenerateToneDialog() {
        Stage dlg = new Stage(StageStyle.UTILITY);
        dlg.initOwner(this);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle("Generate Tone");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(12));
        grid.setHgap(8);
        grid.setVgap(8);

        grid.add(new Label("Wave Type:"), 0, 0);
        ComboBox<String> waveCombo = new ComboBox<>();
        waveCombo.getItems().addAll("Sine", "Square", "Triangle", "Sawtooth", "White Noise", "Short Beep");
        waveCombo.setValue("Sine");
        grid.add(waveCombo, 1, 0);

        grid.add(new Label("Freq (Hz):"), 0, 1);
        TextField txtFreq = new TextField("440");
        grid.add(txtFreq, 1, 1);

        grid.add(new Label("Duration (s):"), 0, 2);
        TextField txtDur = new TextField("2.0");
        grid.add(txtDur, 1, 2);

        Button btnGen = new Button("Generate");
        btnGen.setOnAction(e -> {
            try {
                String type = waveCombo.getValue();
                double freq = Double.parseDouble(txtFreq.getText());
                double duration = Double.parseDouble(txtDur.getText());
                int sampleCount = (int) (duration * 44100.0);
                byte[] pcm = new byte[sampleCount * 4];
                ByteBuffer wrap = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);

                java.util.Random rand = new java.util.Random();
                for (int i = 0; i < sampleCount; i++) {
                    double t = (double) i / 44100.0;
                    short sample = 0;

                    switch (type) {
                        case "Sine" -> sample = (short) (16384 * Math.sin(2.0 * Math.PI * freq * t));
                        case "Square" -> sample = (short) (16384 * (Math.sin(2.0 * Math.PI * freq * t) >= 0 ? 1.0 : -1.0));
                        case "Triangle" -> sample = (short) (16384 * (2.0 * Math.abs(2.0 * (t * freq - Math.floor(t * freq + 0.5))) - 1.0));
                        case "Sawtooth" -> sample = (short) (16384 * (2.0 * (t * freq - Math.floor(t * freq + 0.5))));
                        case "White Noise" -> sample = (short) (rand.nextInt(16384) - 8192);
                        case "Short Beep" -> {
                            if (t < 0.15) {
                                sample = (short) (16384 * Math.sin(2.0 * Math.PI * freq * t));
                            }
                        }
                    }
                    wrap.putShort(sample); // Left
                    wrap.putShort(sample); // Right
                }

                AudioFormat standardFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100.0f, 16, 2, 4, 44100.0f, false);
                AudioTrack activeTrack = getSelectedTrack();
                AudioClip clip = new AudioClip(type + " Tone", playheadTime, duration, Color.web("#78643c"));
                clip.rawPcm = pcm;
                clip.processedPcm = pcm.clone();
                clip.audioFormat = standardFormat;

                activeTrack.clips.add(clip);
                selectedClip = clip;
                updatePlayhead();
                dlg.close();
            } catch (Exception ignore) {}
        });

        grid.add(btnGen, 1, 3);
        dlg.setScene(new Scene(grid));
        dlg.showAndWait();
    }

    private AudioTrack getSelectedTrack() {
        // Fallback to first track if none is selected
        return tracks.get(0);
    }

    private AudioTrack getTrackForClip(AudioClip clip) {
        for (AudioTrack t : tracks) {
            if (t.clips.contains(clip)) return t;
        }
        return null;
    }

    // --- DAW Mixing Audio Playback Thread ---
    private class DAWMixerThread extends Thread {
        private SourceDataLine line;
        
        @Override
        public void run() {
            AudioFormat standardFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100.0f, 16, 2, 4, 44100.0f, false);
            
            try {
                line = AudioSystem.getSourceDataLine(standardFormat);
                line.open(standardFormat, 8192);
                line.start();
            } catch (LineUnavailableException ex) {
                System.err.println("Line unavailable: " + ex.getMessage());
                isPlaying = false;
                return;
            }
            
            for (AudioTrack track : tracks) {
                track.eq.reset();
            }
            
            long currentSample = (long) (playheadTime * 44100.0);
            byte[] outBuffer = new byte[2048];
            ByteBuffer outByteBuffer = ByteBuffer.wrap(outBuffer).order(ByteOrder.LITTLE_ENDIAN);
            
            while (isPlaying) {
                outByteBuffer.clear();
                int framesToProcess = 512;
                
                double[] sumL = new double[framesToProcess];
                double[] sumR = new double[framesToProcess];
                
                boolean anySolo = false;
                for (AudioTrack track : tracks) {
                    if (track.isSolo) {
                        anySolo = true;
                        break;
                    }
                }
                
                for (AudioTrack track : tracks) {
                    if (track.isMuted) continue;
                    if (anySolo && !track.isSolo) continue;
                    
                    double pan = track.pan;
                    double leftGain = Math.cos((pan + 1.0) * Math.PI / 4.0);
                    double rightGain = Math.sin((pan + 1.0) * Math.PI / 4.0);
                    double trackVolume = track.volume;
                    
                    for (AudioClip clip : track.clips) {
                        long clipStartSample = (long) (clip.start * 44100.0);
                        long clipDurationSamples = (long) (clip.duration * 44100.0);
                        long clipEndSample = clipStartSample + clipDurationSamples;
                        
                        long overlapStart = Math.max(currentSample, clipStartSample);
                        long overlapEnd = Math.min(currentSample + framesToProcess, clipEndSample);
                        
                        if (overlapStart < overlapEnd) {
                            ByteBuffer clipBuf = ByteBuffer.wrap(clip.processedPcm).order(ByteOrder.LITTLE_ENDIAN);
                            
                            int writeOffset = (int) (overlapStart - currentSample);
                            int readOffsetSamples = (int) (overlapStart - clipStartSample);
                            int count = (int) (overlapEnd - overlapStart);
                            
                            for (int i = 0; i < count; i++) {
                                int clipByteOffset = (readOffsetSamples + i) * 4;
                                if (clipByteOffset + 3 >= clip.processedPcm.length) break;
                                
                                short cL = clipBuf.getShort(clipByteOffset);
                                short cR = clipBuf.getShort(clipByteOffset + 2);
                                
                                double[] eqOut = track.eq.processSample(cL, cR);
                                double lOut = eqOut[0] * trackVolume * leftGain;
                                double rOut = eqOut[1] * trackVolume * rightGain;
                                
                                sumL[writeOffset + i] += lOut;
                                sumR[writeOffset + i] += rOut;
                            }
                        }
                    }
                }
                
                double masterVolume = masterVolumeSlider.getValue() / 100.0;
                for (int i = 0; i < framesToProcess; i++) {
                    double lVal = sumL[i] * masterVolume;
                    double rVal = sumR[i] * masterVolume;
                    
                    short finalL = (short) Math.max(-32768, Math.min(32767, lVal));
                    short finalR = (short) Math.max(-32768, Math.min(32767, rVal));
                    
                    outByteBuffer.putShort(finalL);
                    outByteBuffer.putShort(finalR);
                }
                
                line.write(outBuffer, 0, outBuffer.length);
                currentSample += framesToProcess;
                playheadTime = (double) currentSample / 44100.0;
                
                updatePlayhead();
            }
            
            line.stop();
            line.close();
        }
    }
}
