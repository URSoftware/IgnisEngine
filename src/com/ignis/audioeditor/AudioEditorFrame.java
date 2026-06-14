package com.ignis.audioeditor;

import com.ignis.core.ui.VectorIcon;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Integrated Audio Editor Frame (DAW style).
 * Implements Item 6 and 8/9/10 of the roadmap: multi-track DAW timeline, real-time mixing,
 * volume faders, panning, 3-band crossover EQ, actual PCM waveform rendering, temporal selections,
 * clipboard copy/cut/paste, gain amplification, normalization, horizontal zoom, and tone generation.
 */
public class AudioEditorFrame extends JFrame {

    private final List<AudioTrack> tracks = new ArrayList<>();
    private final JPanel timelineContainer;
    private final JSlider masterVolumeSlider;
    private final JLabel statusLabel;
    
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private double playheadTime = 0.0; // In seconds
    private int zoomFactor = 40; // Pixels per second (zoom factor)
    
    private final List<JPanel> trackRowPanels = new ArrayList<>();
    
    // Selection and Clipboard state
    private AudioClip selectedClip = null;
    private double selectionStartTime = -1.0;
    private double selectionEndTime = -1.0;
    private boolean isSelecting = false;
    
    private byte[] clipboardPcm = null;
    private AudioFormat clipboardFormat = null;
    private double clipboardDuration = 0.0;

    private DAWMixerThread mixerThread = null;

    // --- Crossover 3-Band Equalizer (IIR Filter) ---
    public static class TrackEQ {
        private static final double es_lowfreq = 400.0;   // low crossover frequency
        private static final double es_highfreq = 4000.0; // high crossover frequency
        
        public double lg = 1.0; // low gain (0.0 - 2.0)
        public double mg = 1.0; // mid gain (0.0 - 2.0)
        public double hg = 1.0; // high gain (0.0 - 2.0)
        
        // History buffer for left channel
        private double f1p0_L, f1p1_L, f1p2_L, f1p3_L;
        private double f2p0_L, f2p1_L, f2p2_L, f2p3_L;
        
        // History buffer for right channel
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
            
            // Crossover coefficients calculation
            k1 = 2.0 * Math.sin(Math.PI * es_lowfreq / 44100.0);
            k2 = 2.0 * Math.sin(Math.PI * es_highfreq / 44100.0);
        }
        
        public double[] processSample(double sampleL, double sampleR) {
            // Left Channel Filtering
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
            
            // Right Channel Filtering
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

    // --- Decoupled Audio Data Models ---
    public static class AudioClip {
        public String name;
        public double start; // In seconds
        public double duration; // In seconds
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
        public double volume = 0.8; // 0.0 to 1.0
        public double pan = 0.0; // -1.0 to 1.0 (Left/Right balance)
        public boolean isMuted = false;
        public boolean isSolo = false;
        public final TrackEQ eq = new TrackEQ();
        public final List<AudioClip> clips = new ArrayList<>();

        public AudioTrack(String name) {
            this.name = name;
        }
    }

    public AudioEditorFrame() {
        super("Ignis DAW - Audio Editor");
        com.ignis.core.AppIconHelper.setWindowIcon(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Initialize default tracks
        tracks.add(new AudioTrack("BGM Track"));
        tracks.add(new AudioTrack("SFX Track 1"));
        tracks.add(new AudioTrack("Voiceover Track"));

        // --- Controls Panel (Top) ---
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        controlsPanel.setBackground(new Color(45, 45, 45));
        controlsPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        JButton btnPlay = new JButton("Play", new VectorIcon(VectorIcon.VectorIconType.PLAY, 14, Color.WHITE));
        JButton btnPause = new JButton("Pause", new VectorIcon(VectorIcon.VectorIconType.PAUSE, 14, Color.WHITE));
        JButton btnStop = new JButton("Stop", new VectorIcon(VectorIcon.VectorIconType.STOP, 14, Color.WHITE));
        JButton btnAddTrack = new JButton("Add Track", new VectorIcon(VectorIcon.VectorIconType.NEW_PROJECT, 14, Color.WHITE));
        JButton btnExport = new JButton("Export Mixdown", new VectorIcon(VectorIcon.VectorIconType.SAVE, 14, Color.WHITE));

        styleButton(btnPlay, new Color(46, 139, 87));
        styleButton(btnPause, new Color(180, 130, 30));
        styleButton(btnStop, new Color(178, 34, 34));
        styleButton(btnAddTrack, new Color(70, 130, 180));
        styleButton(btnExport, new Color(100, 100, 100));

        controlsPanel.add(btnPlay);
        controlsPanel.add(btnPause);
        controlsPanel.add(btnStop);
        controlsPanel.add(btnAddTrack);

        controlsPanel.add(new JLabel("  |  Master: "));
        masterVolumeSlider = new JSlider(0, 100, 80);
        masterVolumeSlider.setBackground(new Color(45, 45, 45));
        masterVolumeSlider.setPreferredSize(new Dimension(100, 24));
        controlsPanel.add(masterVolumeSlider);
        controlsPanel.add(btnExport);

        // --- DAW Edit Toolbar (Bottom Controls Panel) ---
        JPanel editToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        editToolbar.setBackground(new Color(40, 40, 40));
        editToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        JButton btnImport = new JButton("Import WAV", new VectorIcon(VectorIcon.VectorIconType.OPEN_PROJECT, 14, Color.WHITE));
        JButton btnSplit = new JButton("Split Clip", new VectorIcon(VectorIcon.VectorIconType.TOOLS, 14, Color.WHITE));
        JButton btnFadeIn = new JButton("Fade In", new VectorIcon(VectorIcon.VectorIconType.TIMELINE, 14, Color.WHITE));
        JButton btnFadeOut = new JButton("Fade Out", new VectorIcon(VectorIcon.VectorIconType.TIMELINE, 14, Color.WHITE));
        JButton btnMerge = new JButton("Merge Track", new VectorIcon(VectorIcon.VectorIconType.COMPONENTS, 14, Color.WHITE));
        JButton btnDeleteClip = new JButton("Delete Clip", new VectorIcon(VectorIcon.VectorIconType.CONSOLE, 14, Color.WHITE));
        
        JButton btnCopy = new JButton("Copy", new VectorIcon(VectorIcon.VectorIconType.ASSETS, 12, Color.WHITE));
        JButton btnCut = new JButton("Cut", new VectorIcon(VectorIcon.VectorIconType.TOOLS, 12, Color.WHITE));
        JButton btnPaste = new JButton("Paste", new VectorIcon(VectorIcon.VectorIconType.FILE, 12, Color.WHITE));
        JButton btnAmplify = new JButton("Amplify", new VectorIcon(VectorIcon.VectorIconType.SETTINGS, 12, Color.WHITE));
        JButton btnNormalize = new JButton("Normalize", new VectorIcon(VectorIcon.VectorIconType.REFRESH, 12, Color.WHITE));
        JButton btnGenerateTone = new JButton("Generate Tone", new VectorIcon(VectorIcon.VectorIconType.AUDIO, 12, Color.WHITE));

        styleButton(btnImport, new Color(120, 80, 160));
        styleButton(btnSplit, new Color(70, 70, 70));
        styleButton(btnFadeIn, new Color(70, 70, 70));
        styleButton(btnFadeOut, new Color(70, 70, 70));
        styleButton(btnMerge, new Color(70, 70, 70));
        styleButton(btnDeleteClip, new Color(150, 50, 50));
        styleButton(btnCopy, new Color(50, 80, 80));
        styleButton(btnCut, new Color(80, 50, 80));
        styleButton(btnPaste, new Color(50, 80, 50));
        styleButton(btnAmplify, new Color(80, 80, 50));
        styleButton(btnNormalize, new Color(50, 50, 80));
        styleButton(btnGenerateTone, new Color(120, 100, 60));

        editToolbar.add(btnImport);
        editToolbar.add(btnSplit);
        editToolbar.add(btnFadeIn);
        editToolbar.add(btnFadeOut);
        editToolbar.add(btnMerge);
        editToolbar.add(btnDeleteClip);
        editToolbar.add(new JSeparator(JSeparator.VERTICAL));
        editToolbar.add(btnCopy);
        editToolbar.add(btnCut);
        editToolbar.add(btnPaste);
        editToolbar.add(new JSeparator(JSeparator.VERTICAL));
        editToolbar.add(btnAmplify);
        editToolbar.add(btnNormalize);
        editToolbar.add(btnGenerateTone);

        // Zoom Slider
        editToolbar.add(new JLabel("Zoom: "));
        JSlider zoomSlider = new JSlider(10, 120, zoomFactor);
        zoomSlider.setBackground(new Color(40, 40, 40));
        zoomSlider.setPreferredSize(new Dimension(80, 20));
        zoomSlider.addChangeListener(e -> {
            zoomFactor = zoomSlider.getValue();
            rebuildTimeline();
        });
        editToolbar.add(zoomSlider);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(controlsPanel, BorderLayout.NORTH);
        northPanel.add(editToolbar, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // --- Tracks Timeline Container (Center) ---
        timelineContainer = new JPanel();
        timelineContainer.setLayout(new BoxLayout(timelineContainer, BoxLayout.Y_AXIS));
        timelineContainer.setBackground(new Color(30, 30, 30));

        JScrollPane scrollPane = new JScrollPane(timelineContainer);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(30, 30, 30));
        add(scrollPane, BorderLayout.CENTER);

        // --- Status Bar (Bottom) ---
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(40, 40, 40));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 60, 60)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        
        statusLabel = new JLabel("Status: Stopped  |  Time: 00:00.00  |  Master: 80%  (Shift+Drag timeline to select range)");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Visualizer spectrum
        JPanel visualizer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (isPlaying) {
                    g.setColor(new Color(46, 139, 87));
                    int w = getWidth();
                    int h = getHeight();
                    for (int i = 0; i < w; i += 6) {
                        int barH = (int) (Math.random() * (h - 4)) + 2;
                        g.fillRect(i, h - barH, 4, barH);
                    }
                }
            }
        };
        visualizer.setPreferredSize(new Dimension(120, 20));
        visualizer.setBackground(new Color(40, 40, 40));
        statusBar.add(visualizer, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);

        // --- Playback Actions ---
        btnPlay.addActionListener(e -> {
            if (isPlaying) return;
            isPlaying = true;
            isRecording = false;
            mixerThread = new DAWMixerThread();
            mixerThread.start();
        });

        btnPause.addActionListener(e -> {
            isPlaying = false;
            if (mixerThread != null) {
                try {
                    mixerThread.join(100);
                } catch (InterruptedException ex) {
                    // Ignore
                }
            }
            updatePlayhead();
            visualizer.repaint();
        });

        btnStop.addActionListener(e -> {
            isPlaying = false;
            isRecording = false;
            if (mixerThread != null) {
                try {
                    mixerThread.join(100);
                } catch (InterruptedException ex) {
                    // Ignore
                }
            }
            playheadTime = 0.0;
            updatePlayhead();
            visualizer.repaint();
        });

        btnAddTrack.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter Track Name:", "Add Track", JOptionPane.QUESTION_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                tracks.add(new AudioTrack(name.trim()));
                rebuildTimeline();
            }
        });

        // --- Edit Toolbar Action Listeners ---
        btnImport.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Import WAV Clip");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("WAV Audio (*.wav)", "wav"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                String[] trackNames = new String[tracks.size()];
                for (int i = 0; i < tracks.size(); i++) {
                    trackNames[i] = tracks.get(i).name;
                }
                String trackName = (String) JOptionPane.showInputDialog(this,
                    "Select target track:", "Import WAV",
                    JOptionPane.QUESTION_MESSAGE, null, trackNames, trackNames[0]);
                if (trackName != null) {
                    AudioTrack targetTrack = null;
                    for (AudioTrack t : tracks) {
                        if (t.name.equals(trackName)) {
                            targetTrack = t;
                            break;
                        }
                    }
                    if (targetTrack != null) {
                        try {
                            WavAudioProcessor.WavData data = WavAudioProcessor.readWav(file);
                            AudioClip clip = new AudioClip(file.getName(), playheadTime, data.duration, new Color(70, 130, 180, 180));
                            clip.audioFile = file;
                            clip.rawPcm = data.pcmData;
                            clip.processedPcm = data.pcmData.clone();
                            clip.audioFormat = data.format;
                            targetTrack.clips.add(clip);
                            rebuildTimeline();
                            JOptionPane.showMessageDialog(this, "Imported " + file.getName() + " successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "Failed to load WAV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });

        btnSplit.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            double splitPoint = playheadTime - selectedClip.start;
            if (splitPoint <= 0 || splitPoint >= selectedClip.duration) {
                JOptionPane.showMessageDialog(this, "Move playhead inside the selected clip to split!", "Invalid Split Point", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                AudioTrack targetTrack = null;
                for (AudioTrack t : tracks) {
                    if (t.clips.contains(selectedClip)) {
                        targetTrack = t;
                        break;
                    }
                }
                if (targetTrack != null) {
                    updateClipPcm(selectedClip);
                    
                    byte[] pcm1 = WavAudioProcessor.trimPcm(selectedClip.processedPcm, selectedClip.audioFormat, 0, splitPoint);
                    byte[] pcm2 = WavAudioProcessor.trimPcm(selectedClip.processedPcm, selectedClip.audioFormat, splitPoint, selectedClip.duration);
                    
                    AudioClip clip1 = new AudioClip(selectedClip.name + "_part1", selectedClip.start, splitPoint, selectedClip.color);
                    clip1.audioFile = selectedClip.audioFile;
                    clip1.rawPcm = pcm1;
                    clip1.processedPcm = pcm1.clone();
                    clip1.audioFormat = selectedClip.audioFormat;
                    clip1.fadeIn = selectedClip.fadeIn;
                    
                    AudioClip clip2 = new AudioClip(selectedClip.name + "_part2", playheadTime, selectedClip.duration - splitPoint, selectedClip.color);
                    clip2.audioFile = selectedClip.audioFile;
                    clip2.rawPcm = pcm2;
                    clip2.processedPcm = pcm2.clone();
                    clip2.audioFormat = selectedClip.audioFormat;
                    clip2.fadeOut = selectedClip.fadeOut;
                    
                    targetTrack.clips.remove(selectedClip);
                    targetTrack.clips.add(clip1);
                    targetTrack.clips.add(clip2);
                    
                    selectedClip = null;
                    rebuildTimeline();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Split failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnFadeIn.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String input = JOptionPane.showInputDialog(this, "Enter fade-in duration (seconds):", String.valueOf(selectedClip.fadeIn));
            if (input != null) {
                try {
                    double fade = Double.parseDouble(input);
                    selectedClip.fadeIn = Math.max(0.0, fade);
                    updateClipPcm(selectedClip);
                    timelineContainer.repaint();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid number format!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnFadeOut.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String input = JOptionPane.showInputDialog(this, "Enter fade-out duration (seconds):", String.valueOf(selectedClip.fadeOut));
            if (input != null) {
                try {
                    double fade = Double.parseDouble(input);
                    selectedClip.fadeOut = Math.max(0.0, fade);
                    updateClipPcm(selectedClip);
                    timelineContainer.repaint();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid number format!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnDeleteClip.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            for (AudioTrack t : tracks) {
                if (t.clips.remove(selectedClip)) {
                    selectedClip = null;
                    rebuildTimeline();
                    break;
                }
            }
        });

        btnMerge.addActionListener(e -> {
            String[] trackNames = new String[tracks.size()];
            for (int i = 0; i < tracks.size(); i++) {
                trackNames[i] = tracks.get(i).name;
            }
            String trackName = (String) JOptionPane.showInputDialog(this,
                "Select track to merge all clips:", "Merge Clips",
                JOptionPane.QUESTION_MESSAGE, null, trackNames, trackNames[0]);
            if (trackName != null) {
                AudioTrack targetTrack = null;
                for (AudioTrack t : tracks) {
                    if (t.name.equals(trackName)) {
                        targetTrack = t;
                        break;
                    }
                }
                if (targetTrack != null && !targetTrack.clips.isEmpty()) {
                    try {
                        java.util.List<byte[]> streams = new ArrayList<>();
                        java.util.List<Double> starts = new ArrayList<>();
                        AudioFormat format = targetTrack.clips.get(0).audioFormat;
                        
                        double minStart = Double.MAX_VALUE;
                        for (AudioClip c : targetTrack.clips) {
                            updateClipPcm(c);
                            streams.add(c.processedPcm);
                            starts.add(c.start);
                            if (c.start < minStart) {
                                minStart = c.start;
                            }
                        }
                        
                        java.util.List<Double> relativeStarts = new ArrayList<>();
                        for (double start : starts) {
                            relativeStarts.add(start - minStart);
                        }
                        
                        byte[] mergedPcm = WavAudioProcessor.mixTracks(streams, relativeStarts, format);
                        double duration = (double) mergedPcm.length / (format.getFrameSize() * format.getSampleRate());
                        
                        AudioClip mergedClip = new AudioClip("Merged_" + targetTrack.name, minStart, duration, targetTrack.clips.get(0).color);
                        mergedClip.rawPcm = mergedPcm;
                        mergedClip.processedPcm = mergedPcm.clone();
                        mergedClip.audioFormat = format;
                        
                        targetTrack.clips.clear();
                        targetTrack.clips.add(mergedClip);
                        selectedClip = null;
                        rebuildTimeline();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Merge failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        // --- Timeline Clipboard Actions ---
        btnCopy.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first to copy a range!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (selectionStartTime < 0 || selectionEndTime <= selectionStartTime) {
                JOptionPane.showMessageDialog(this, "Use Shift + Drag on the timeline to select a range first!", "No Time Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            double relStart = Math.max(0.0, selectionStartTime - selectedClip.start);
            double relEnd = Math.min(selectedClip.duration, selectionEndTime - selectedClip.start);
            if (relStart >= relEnd) {
                JOptionPane.showMessageDialog(this, "Selected time range does not overlap with selected clip!", "Range Out of Bound", JOptionPane.WARNING_MESSAGE);
                return;
            }
            updateClipPcm(selectedClip);
            clipboardPcm = WavAudioProcessor.trimPcm(selectedClip.processedPcm, selectedClip.audioFormat, relStart, relEnd);
            clipboardFormat = selectedClip.audioFormat;
            clipboardDuration = relEnd - relStart;
            statusLabel.setText(" ✓ Copied " + String.format("%.2f", clipboardDuration) + "s of audio to clipboard");
        });

        btnCut.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first to cut!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (selectionStartTime < 0 || selectionEndTime <= selectionStartTime) {
                JOptionPane.showMessageDialog(this, "Use Shift + Drag to select a range first!", "No Time Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            double relStart = Math.max(0.0, selectionStartTime - selectedClip.start);
            double relEnd = Math.min(selectedClip.duration, selectionEndTime - selectedClip.start);
            if (relStart >= relEnd) {
                JOptionPane.showMessageDialog(this, "Selected range does not overlap with selected clip!", "Range Out of Bound", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                AudioTrack targetTrack = null;
                for (AudioTrack t : tracks) {
                    if (t.clips.contains(selectedClip)) {
                        targetTrack = t;
                        break;
                    }
                }
                if (targetTrack != null) {
                    updateClipPcm(selectedClip);
                    clipboardPcm = WavAudioProcessor.trimPcm(selectedClip.processedPcm, selectedClip.audioFormat, relStart, relEnd);
                    clipboardFormat = selectedClip.audioFormat;
                    clipboardDuration = relEnd - relStart;

                    byte[] pcmBefore = WavAudioProcessor.trimPcm(selectedClip.processedPcm, selectedClip.audioFormat, 0, relStart);
                    byte[] pcmAfter = WavAudioProcessor.trimPcm(selectedClip.processedPcm, selectedClip.audioFormat, relEnd, selectedClip.duration);

                    AudioClip clipBefore = new AudioClip(selectedClip.name + "_cut1", selectedClip.start, relStart, selectedClip.color);
                    clipBefore.rawPcm = pcmBefore;
                    clipBefore.processedPcm = pcmBefore.clone();
                    clipBefore.audioFormat = selectedClip.audioFormat;

                    AudioClip clipAfter = new AudioClip(selectedClip.name + "_cut2", selectedClip.start + relEnd, selectedClip.duration - relEnd, selectedClip.color);
                    clipAfter.rawPcm = pcmAfter;
                    clipAfter.processedPcm = pcmAfter.clone();
                    clipAfter.audioFormat = selectedClip.audioFormat;

                    targetTrack.clips.remove(selectedClip);
                    if (clipBefore.duration > 0.01) targetTrack.clips.add(clipBefore);
                    if (clipAfter.duration > 0.01) targetTrack.clips.add(clipAfter);

                    selectedClip = null;
                    rebuildTimeline();
                    statusLabel.setText(" ✓ Cut audio range to clipboard");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Cut failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnPaste.addActionListener(e -> {
            if (clipboardPcm == null) {
                JOptionPane.showMessageDialog(this, "Clipboard is empty!", "Clipboard Empty", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String[] trackNames = new String[tracks.size()];
            for (int i = 0; i < tracks.size(); i++) {
                trackNames[i] = tracks.get(i).name;
            }
            String trackName = (String) JOptionPane.showInputDialog(this,
                "Select destination track to paste:", "Paste Audio",
                JOptionPane.QUESTION_MESSAGE, null, trackNames, trackNames[0]);
            if (trackName != null) {
                AudioTrack targetTrack = null;
                for (AudioTrack t : tracks) {
                    if (t.name.equals(trackName)) {
                        targetTrack = t;
                        break;
                    }
                }
                if (targetTrack != null) {
                    AudioClip pastedClip = new AudioClip("Pasted_Clip", playheadTime, clipboardDuration, new Color(46, 139, 87, 180));
                    pastedClip.rawPcm = clipboardPcm.clone();
                    pastedClip.processedPcm = clipboardPcm.clone();
                    pastedClip.audioFormat = clipboardFormat;
                    targetTrack.clips.add(pastedClip);
                    rebuildTimeline();
                    statusLabel.setText(" ✓ Audio pasted successfully at " + String.format("%.2f", playheadTime) + "s");
                }
            }
        });

        // --- Amplification & Normalization ---
        btnAmplify.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String input = JOptionPane.showInputDialog(this, "Enter gain factor multiplier (e.g. 0.5 to 2.5):", "1.5");
            if (input != null) {
                try {
                    double gain = Double.parseDouble(input);
                    ByteBuffer buf = ByteBuffer.wrap(selectedClip.rawPcm).order(ByteOrder.LITTLE_ENDIAN);
                    int samples = selectedClip.rawPcm.length / 2;
                    for (int s = 0; s < samples; s++) {
                        short sample = buf.getShort(s * 2);
                        int amplified = (int) (sample * gain);
                        amplified = Math.max(-32768, Math.min(32767, amplified));
                        buf.putShort(s * 2, (short) amplified);
                    }
                    updateClipPcm(selectedClip);
                    timelineContainer.repaint();
                    statusLabel.setText(" ✓ Clip amplified by " + gain + "x");
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid gain number!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnNormalize.addActionListener(e -> {
            if (selectedClip == null) {
                JOptionPane.showMessageDialog(this, "Select a clip first!", "No Clip Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int peak = 0;
            ByteBuffer buf = ByteBuffer.wrap(selectedClip.rawPcm).order(ByteOrder.LITTLE_ENDIAN);
            int samples = selectedClip.rawPcm.length / 2;
            for (int s = 0; s < samples; s++) {
                short sample = buf.getShort(s * 2);
                peak = Math.max(peak, Math.abs(sample));
            }
            if (peak > 0) {
                double scale = 32767.0 / peak;
                for (int s = 0; s < samples; s++) {
                    short sample = buf.getShort(s * 2);
                    short normalized = (short) (sample * scale);
                    buf.putShort(s * 2, normalized);
                }
                updateClipPcm(selectedClip);
                timelineContainer.repaint();
                statusLabel.setText(" ✓ Normalized peak to maximum (Scale multiplier: " + String.format("%.2f", scale) + "x)");
            }
        });

        // --- Tone Generation ---
        btnGenerateTone.addActionListener(e -> showToneGeneratorDialog());

        btnExport.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export Audio Mixdown");
            chooser.setSelectedFile(new File("mixdown.wav"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File saveFile = chooser.getSelectedFile();
                try {
                    java.util.List<byte[]> streams = new ArrayList<>();
                    java.util.List<Double> starts = new ArrayList<>();
                    AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        44100.0f,
                        16,
                        2,
                        4,
                        44100.0f,
                        false
                    );
                    
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
                            updateClipPcm(clip);
                            byte[] processed = clip.processedPcm.clone();
                            int samples = processed.length / 4;
                            ByteBuffer buf = ByteBuffer.wrap(processed).order(ByteOrder.LITTLE_ENDIAN);
                            
                            // Reset track EQ state for offline processing
                            TrackEQ offlineEq = new TrackEQ();
                            offlineEq.setGains(track.eq.lg, track.eq.mg, track.eq.hg);
                            
                            for (int s = 0; s < samples; s++) {
                                int offset = s * 4;
                                short cL = buf.getShort(offset);
                                short cR = buf.getShort(offset + 2);
                                
                                double[] eqVal = offlineEq.processSample(cL, cR);
                                double outL = eqVal[0] * trackVolume * leftGain;
                                double outR = eqVal[1] * trackVolume * rightGain;
                                
                                short finalL = (short) Math.max(-32768, Math.min(32767, outL));
                                short finalR = (short) Math.max(-32768, Math.min(32767, outR));
                                
                                buf.putShort(offset, finalL);
                                buf.putShort(offset + 2, finalR);
                            }
                            streams.add(processed);
                            starts.add(clip.start);
                        }
                    }
                    
                    if (streams.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "No audio data found to export!", "Export Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
                    byte[] mixed = WavAudioProcessor.mixTracks(streams, starts, targetFormat);
                    WavAudioProcessor.writeWav(mixed, targetFormat, saveFile);
                    JOptionPane.showMessageDialog(this, "Audio exported successfully to: " + saveFile.getName(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        masterVolumeSlider.addChangeListener(e -> updatePlayhead());

        rebuildTimeline();
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    }

    private void updatePlayhead() {
        int minutes = (int) (playheadTime / 60);
        double seconds = playheadTime % 60;
        String status = isRecording ? "Recording" : (isPlaying ? "Playing" : "Stopped");
        statusLabel.setText(String.format("Status: %s  |  Time: %02d:%05.2f  |  Master: %d%%  (Shift+Drag timeline to select range)", 
                status, minutes, seconds, masterVolumeSlider.getValue()));
        timelineContainer.repaint();
    }

    private void updateClipPcm(AudioClip clip) {
        if (clip.rawPcm == null) return;
        byte[] pcm = clip.rawPcm;
        double maxDuration = (double) clip.rawPcm.length / (clip.audioFormat.getFrameSize() * clip.audioFormat.getSampleRate());
        if (clip.duration < maxDuration) {
            pcm = WavAudioProcessor.trimPcm(clip.rawPcm, clip.audioFormat, 0, clip.duration);
        }
        clip.processedPcm = WavAudioProcessor.applyFades(pcm, clip.audioFormat, clip.fadeIn, clip.fadeOut);
    }

    private void rebuildTimeline() {
        timelineContainer.removeAll();
        trackRowPanels.clear();

        // Timeline Ruler
        JPanel rulerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(80, 80, 80));
                int w = getWidth() - 180;
                for (int s = 0; s < 100; s += 2) {
                    int x = 180 + (s * zoomFactor);
                    g.drawLine(x, 10, x, getHeight());
                    if (s % 10 == 0 || zoomFactor >= 30) {
                        g.drawString(s + "s", x + 2, 12);
                    }
                }
            }
        };
        rulerPanel.setPreferredSize(new Dimension(getWidth(), 22));
        rulerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        rulerPanel.setBackground(new Color(38, 38, 38));
        rulerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                playheadTime = (double) (e.getX() - 180) / zoomFactor;
                playheadTime = Math.max(0.0, playheadTime);
                updatePlayhead();
            }
        });
        timelineContainer.add(rulerPanel);

        // Rebuild Track Rows
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack track = tracks.get(i);
            JPanel row = new JPanel(new BorderLayout());
            row.setPreferredSize(new Dimension(getWidth(), 95));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
            row.setBackground(new Color(30, 30, 30));
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 50)));

            // Left controls
            JPanel faderPanel = new JPanel(null);
            faderPanel.setPreferredSize(new Dimension(180, 95));
            faderPanel.setBackground(new Color(40, 40, 40));
            faderPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 60)));

            JLabel lblName = new JLabel(track.name);
            lblName.setForeground(Color.WHITE);
            lblName.setFont(new Font("Arial", Font.BOLD, 11));
            lblName.setBounds(8, 4, 164, 18);
            faderPanel.add(lblName);

            JToggleButton btnMute = new JToggleButton("M", track.isMuted);
            btnMute.setToolTipText("Mute Track");
            btnMute.setBounds(8, 24, 26, 20);
            btnMute.setFont(new Font("Arial", Font.BOLD, 9));
            btnMute.setMargin(new Insets(0, 0, 0, 0));
            btnMute.setBackground(track.isMuted ? Color.RED : Color.DARK_GRAY);
            btnMute.setForeground(Color.WHITE);
            btnMute.addActionListener(e -> {
                track.isMuted = btnMute.isSelected();
                btnMute.setBackground(track.isMuted ? Color.RED : Color.DARK_GRAY);
            });
            faderPanel.add(btnMute);

            JToggleButton btnSolo = new JToggleButton("S", track.isSolo);
            btnSolo.setToolTipText("Solo Track");
            btnSolo.setBounds(38, 24, 26, 20);
            btnSolo.setFont(new Font("Arial", Font.BOLD, 9));
            btnSolo.setMargin(new Insets(0, 0, 0, 0));
            btnSolo.setBackground(track.isSolo ? Color.YELLOW : Color.DARK_GRAY);
            btnSolo.setForeground(track.isSolo ? Color.BLACK : Color.WHITE);
            btnSolo.addActionListener(e -> {
                track.isSolo = btnSolo.isSelected();
                btnSolo.setBackground(track.isSolo ? Color.YELLOW : Color.DARK_GRAY);
                btnSolo.setForeground(track.isSolo ? Color.BLACK : Color.WHITE);
            });
            faderPanel.add(btnSolo);

            JSlider volSlider = new JSlider(0, 100, (int) (track.volume * 100));
            volSlider.setBounds(70, 24, 102, 20);
            volSlider.setBackground(new Color(40, 40, 40));
            volSlider.addChangeListener(e -> track.volume = volSlider.getValue() / 100.0);
            faderPanel.add(volSlider);

            // Pan Slider
            JLabel lblPan = new JLabel("P");
            lblPan.setForeground(Color.LIGHT_GRAY);
            lblPan.setFont(new Font("Arial", Font.BOLD, 10));
            lblPan.setBounds(8, 48, 12, 20);
            faderPanel.add(lblPan);
            
            JSlider panSlider = new JSlider(-100, 100, (int)(track.pan * 100));
            panSlider.setBounds(20, 48, 60, 20);
            panSlider.setBackground(new Color(40, 40, 40));
            panSlider.addChangeListener(e -> track.pan = panSlider.getValue() / 100.0);
            faderPanel.add(panSlider);

            // EQ low/mid/high sliders
            JSlider eqLow = new JSlider(0, 200, (int)(track.eq.lg * 100));
            eqLow.setBounds(90, 46, 26, 38);
            eqLow.setOrientation(JSlider.VERTICAL);
            eqLow.setBackground(new Color(40, 40, 40));
            eqLow.addChangeListener(e -> track.eq.setGains(eqLow.getValue() / 100.0, track.eq.mg, track.eq.hg));
            faderPanel.add(eqLow);

            JSlider eqMid = new JSlider(0, 200, (int)(track.eq.mg * 100));
            eqMid.setBounds(120, 46, 26, 38);
            eqMid.setOrientation(JSlider.VERTICAL);
            eqMid.setBackground(new Color(40, 40, 40));
            eqMid.addChangeListener(e -> track.eq.setGains(track.eq.lg, eqMid.getValue() / 100.0, track.eq.hg));
            faderPanel.add(eqMid);

            JSlider eqHigh = new JSlider(0, 200, (int)(track.eq.hg * 100));
            eqHigh.setBounds(150, 46, 26, 38);
            eqHigh.setOrientation(JSlider.VERTICAL);
            eqHigh.setBackground(new Color(40, 40, 40));
            eqHigh.addChangeListener(e -> track.eq.setGains(track.eq.lg, track.eq.mg, eqHigh.getValue() / 100.0));
            faderPanel.add(eqHigh);

            JLabel lblEQ = new JLabel("L   M   H");
            lblEQ.setForeground(Color.LIGHT_GRAY);
            lblEQ.setFont(new Font("Arial", Font.BOLD, 8));
            lblEQ.setBounds(94, 84, 80, 10);
            faderPanel.add(lblEQ);

            row.add(faderPanel, BorderLayout.WEST);

            // Right workspace
            JPanel clipsPanel = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // Grid background
                    g2d.setColor(new Color(45, 45, 45));
                    for (int s = 0; s < 100; s += 2) {
                        int x = s * zoomFactor;
                        g2d.drawLine(x, 0, x, getHeight());
                    }

                    // Render clips
                    for (AudioClip clip : track.clips) {
                        int cx = (int) (clip.start * zoomFactor);
                        int cw = (int) (clip.duration * zoomFactor);
                        int cy = 10;
                        int ch = getHeight() - 20;

                        g2d.setColor(clip.color);
                        g2d.fillRoundRect(cx, cy, cw, ch, 6, 6);

                        g2d.setColor(Color.WHITE);
                        g2d.drawRoundRect(cx, cy, cw, ch, 6, 6);

                        if (clip == selectedClip) {
                            g2d.setColor(Color.YELLOW);
                            g2d.setStroke(new BasicStroke(2.5f));
                            g2d.drawRoundRect(cx - 1, cy - 1, cw + 2, ch + 2, 6, 6);
                        }

                        // Render actual PCM waveform
                        if (clip.processedPcm != null && clip.processedPcm.length > 0) {
                            g2d.setColor(new Color(255, 255, 255, 140));
                            g2d.setStroke(new BasicStroke(1.0f));
                            int mid = cy + ch / 2;
                            int pcmLengthSamples = clip.processedPcm.length / 4;
                            
                            for (int px = 2; px < cw - 2; px++) {
                                double tStart = (double) (px) / zoomFactor;
                                double tEnd = (double) (px + 1) / zoomFactor;
                                
                                int sampleStart = (int) (tStart * 44100.0);
                                int sampleEnd = (int) (tEnd * 44100.0);
                                
                                sampleStart = Math.max(0, Math.min(pcmLengthSamples - 1, sampleStart));
                                sampleEnd = Math.max(sampleStart, Math.min(pcmLengthSamples, sampleEnd));
                                
                                int maxVal = 0;
                                ByteBuffer buf = ByteBuffer.wrap(clip.processedPcm).order(ByteOrder.LITTLE_ENDIAN);
                                
                                for (int s = sampleStart; s < sampleEnd; s += Math.max(1, (sampleEnd - sampleStart) / 10)) {
                                    int offset = s * 4;
                                    if (offset + 1 < clip.processedPcm.length) {
                                        short valL = buf.getShort(offset);
                                        maxVal = Math.max(maxVal, Math.abs(valL));
                                    }
                                }
                                
                                double norm = (double) maxVal / 32768.0;
                                int hHalf = (int) (norm * (ch / 2.0));
                                
                                int screenX = cx + px;
                                g2d.drawLine(screenX, mid - hHalf, screenX, mid + hHalf);
                            }
                        }

                        g2d.setColor(Color.WHITE);
                        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
                        g2d.drawString(clip.name, cx + 6, cy + 14);
                    }

                    // Draw time range selection
                    if (selectionStartTime >= 0 && selectionEndTime > selectionStartTime) {
                        int sx = (int) (selectionStartTime * zoomFactor);
                        int sw = (int) ((selectionEndTime - selectionStartTime) * zoomFactor);
                        g2d.setColor(new Color(0, 150, 255, 60)); // Translucent Blue
                        g2d.fillRect(sx, 0, sw, getHeight());
                        g2d.setColor(new Color(0, 150, 255, 120));
                        g2d.drawRect(sx, 0, sw, getHeight());
                    }

                    // Vertical playhead line
                    int px = (int) (playheadTime * zoomFactor);
                    g2d.setColor(Color.ORANGE);
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(px, 0, px, getHeight());
                }
            };
            clipsPanel.setBackground(new Color(32, 32, 32));
            
            clipsPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (e.isShiftDown()) {
                            // Start selection range
                            selectionStartTime = (double) e.getX() / zoomFactor;
                            selectionEndTime = selectionStartTime;
                            isSelecting = true;
                            selectedClip = null;
                        } else {
                            // Reset selection
                            selectionStartTime = -1.0;
                            selectionEndTime = -1.0;
                            isSelecting = false;
                            
                            // Select clip
                            selectedClip = null;
                            for (AudioClip clip : track.clips) {
                                int cx = (int) (clip.start * zoomFactor);
                                int cw = (int) (clip.duration * zoomFactor);
                                int cy = 10;
                                int ch = clipsPanel.getHeight() - 20;
                                if (e.getX() >= cx && e.getX() <= cx + cw && e.getY() >= cy && e.getY() <= cy + ch) {
                                    selectedClip = clip;
                                    break;
                                }
                            }
                        }
                    }
                    timelineContainer.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isSelecting) {
                        isSelecting = false;
                    }
                }
            });

            clipsPanel.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isSelecting) {
                        double currentSel = (double) e.getX() / zoomFactor;
                        if (currentSel < selectionStartTime) {
                            selectionEndTime = selectionStartTime;
                            selectionStartTime = Math.max(0.0, currentSel);
                        } else {
                            selectionEndTime = currentSel;
                        }
                        timelineContainer.repaint();
                    }
                }
            });

            row.add(clipsPanel, BorderLayout.CENTER);
            timelineContainer.add(row);
            trackRowPanels.add(row);
        }

        timelineContainer.revalidate();
        timelineContainer.repaint();
    }

    private void showToneGeneratorDialog() {
        JDialog dialog = new JDialog(this, "Generate Tone", true);
        dialog.setLayout(new GridLayout(5, 2, 8, 8));
        dialog.setSize(320, 200);
        dialog.setLocationRelativeTo(this);
        
        dialog.add(new JLabel(" Wave Type:"));
        JComboBox<String> waveTypeBox = new JComboBox<>(new String[] {
            "Sine", "Square", "Triangle", "Sawtooth", "White Noise", "Beep"
        });
        dialog.add(waveTypeBox);
        
        dialog.add(new JLabel(" Frequency (Hz):"));
        JTextField freqField = new JTextField("440");
        dialog.add(freqField);
        
        dialog.add(new JLabel(" Duration (sec):"));
        JTextField durField = new JTextField("2.0");
        dialog.add(durField);
        
        dialog.add(new JLabel(" Destination Track:"));
        String[] trackNames = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            trackNames[i] = tracks.get(i).name;
        }
        JComboBox<String> trackDestBox = new JComboBox<>(trackNames);
        dialog.add(trackDestBox);
        
        JButton btnGenerate = new JButton("Generate");
        btnGenerate.addActionListener(e -> {
            try {
                String type = (String) waveTypeBox.getSelectedItem();
                double frequency = Double.parseDouble(freqField.getText());
                double duration = Double.parseDouble(durField.getText());
                String trackDest = (String) trackDestBox.getSelectedItem();
                
                int sampleCount = (int) (duration * 44100.0);
                byte[] pcm = new byte[sampleCount * 4];
                ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
                
                for (int i = 0; i < sampleCount; i++) {
                    double t = (double) i / 44100.0;
                    double val = 0;
                    switch (type) {
                        case "Sine" -> val = Math.sin(2 * Math.PI * frequency * t);
                        case "Square" -> val = Math.sin(2 * Math.PI * frequency * t) >= 0 ? 1.0 : -1.0;
                        case "Triangle" -> val = 2.0 * Math.abs(2.0 * (t * frequency - Math.floor(t * frequency + 0.5))) - 1.0;
                        case "Sawtooth" -> val = 2.0 * (t * frequency - Math.floor(t * frequency + 0.5));
                        case "White Noise" -> val = Math.random() * 2.0 - 1.0;
                        case "Beep" -> {
                            double fade = Math.max(0.0, 1.0 - (t / 0.2));
                            val = Math.sin(2 * Math.PI * 1000.0 * t) * fade;
                        }
                    }
                    
                    short sampleVal = (short) (val * 28000.0); // Safe scale with headroom
                    buf.putShort(sampleVal); // Left channel
                    buf.putShort(sampleVal); // Right channel
                }
                
                // Find track and add clip
                for (AudioTrack track : tracks) {
                    if (track.name.equals(trackDest)) {
                        AudioClip toneClip = new AudioClip(type + " Tone", playheadTime, duration, new Color(200, 100, 100, 180));
                        toneClip.rawPcm = pcm;
                        toneClip.processedPcm = pcm.clone();
                        toneClip.audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100.0f, 16, 2, 4, 44100.0f, false);
                        track.clips.add(toneClip);
                        break;
                    }
                }
                rebuildTimeline();
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        dialog.add(btnGenerate);
        dialog.setVisible(true);
    }

    // --- Background Mixer and Audio Thread ---
    private class DAWMixerThread extends Thread {
        private SourceDataLine line;
        
        @Override
        public void run() {
            AudioFormat standardFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100.0f,
                16,
                2,
                4,
                44100.0f,
                false
            );
            
            try {
                line = AudioSystem.getSourceDataLine(standardFormat);
                line.open(standardFormat, 8192); // 8KB buffer size
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
            byte[] outBuffer = new byte[2048]; // 512 frames * 4 bytes/frame
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
                    // Constant-power panning
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
                                
                                // Process through TrackEQ
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
                
                SwingUtilities.invokeLater(() -> updatePlayhead());
            }
            
            line.stop();
            line.close();
        }
    }
}
