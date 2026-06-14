package com.ignis.audioeditor;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Integrated Audio Editor Frame (DAW style).
 * Implements Item 6 of the IgnisEngine roadmap: multi-track DAW timeline, recording,
 * mixing, sound effects, volume faders, automations, and audio export.
 */
public class AudioEditorFrame extends JFrame {

    private final List<AudioTrack> tracks = new ArrayList<>();
    private final JPanel timelineContainer;
    private final JSlider masterVolumeSlider;
    private final JLabel statusLabel;
    private final Timer playTimer;
    
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private double playheadTime = 0.0; // In seconds
    private final List<JPanel> trackRowPanels = new ArrayList<>();
    
    // Selection and playback state
    private AudioClip selectedClip = null;
    private final List<Clip> activeDAWClips = new ArrayList<>();

    // --- Decoupled Audio Data Models ---
    public static class AudioClip {
        public String name;
        public double start; // In seconds
        public double duration; // In seconds
        public Color color;

        // Real Audio fields
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
        public boolean isMuted = false;
        public boolean isSolo = false;
        public List<AudioClip> clips = new ArrayList<>();

        public AudioTrack(String name) {
            this.name = name;
        }
    }

    public AudioEditorFrame() {
        super("Ignis DAW - Audio Editor");
        com.ignis.core.AppIconHelper.setWindowIcon(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(950, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Initialize with default tracks
        tracks.add(new AudioTrack("BGM Track"));
        tracks.add(new AudioTrack("SFX Track 1"));
        tracks.add(new AudioTrack("Voiceover Track"));

        // --- Controls Panel (Top) ---
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        controlsPanel.setBackground(new Color(45, 45, 45));
        controlsPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        JButton btnPlay = new JButton("▶ Play");
        JButton btnPause = new JButton("⏸ Pause");
        JButton btnStop = new JButton("⏹ Stop");
        JButton btnRecord = new JButton("● Record");
        JButton btnAddTrack = new JButton("➕ Add Track");
        JButton btnExport = new JButton("💾 Export Mixdown...");

        // Dark theme buttons
        styleButton(btnPlay, new Color(46, 139, 87));
        styleButton(btnPause, new Color(180, 130, 30));
        styleButton(btnStop, new Color(178, 34, 34));
        styleButton(btnRecord, new Color(220, 20, 60));
        styleButton(btnAddTrack, new Color(70, 130, 180));
        styleButton(btnExport, new Color(100, 100, 100));

        controlsPanel.add(btnPlay);
        controlsPanel.add(btnPause);
        controlsPanel.add(btnStop);
        controlsPanel.add(btnRecord);
        controlsPanel.add(btnAddTrack);

        controlsPanel.add(new JLabel("  |  Master: "));
        masterVolumeSlider = new JSlider(0, 100, 80);
        masterVolumeSlider.setBackground(new Color(45, 45, 45));
        masterVolumeSlider.setPreferredSize(new Dimension(100, 24));
        controlsPanel.add(masterVolumeSlider);
        controlsPanel.add(btnExport);

        // --- DAW Edit Toolbar (Bottom Controls Panel) ---
        JPanel editToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        editToolbar.setBackground(new Color(40, 40, 40));
        editToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        JButton btnImport = new JButton("📂 Import WAV...");
        JButton btnSplit = new JButton("✂ Split Clip");
        JButton btnFadeIn = new JButton("📈 Fade In...");
        JButton btnFadeOut = new JButton("📉 Fade Out...");
        JButton btnMerge = new JButton("🔗 Merge Track Clips");
        JButton btnDeleteClip = new JButton("🗑 Delete Clip");

        styleButton(btnImport, new Color(120, 80, 160));
        styleButton(btnSplit, new Color(70, 70, 70));
        styleButton(btnFadeIn, new Color(70, 70, 70));
        styleButton(btnFadeOut, new Color(70, 70, 70));
        styleButton(btnMerge, new Color(70, 70, 70));
        styleButton(btnDeleteClip, new Color(150, 50, 50));

        editToolbar.add(btnImport);
        editToolbar.add(btnSplit);
        editToolbar.add(btnFadeIn);
        editToolbar.add(btnFadeOut);
        editToolbar.add(btnMerge);
        editToolbar.add(btnDeleteClip);

        // Wrap controls in a north Panel
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
        
        statusLabel = new JLabel("Status: Stopped  |  Time: 00:00.00  |  Master: 80%");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Fake visualizer spectrum
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

        // --- Actions and Timers ---
        playTimer = new Timer(50, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPlaying) {
                    double lastTime = playheadTime;
                    playheadTime += 0.05;
                    triggerClipsForRange(lastTime, playheadTime);
                    updatePlayhead();
                    visualizer.repaint();
                }
            }
        });

        btnPlay.addActionListener(e -> {
            isPlaying = true;
            isRecording = false;
            playTimer.start();
        });

        btnPause.addActionListener(e -> {
            isPlaying = false;
            playTimer.stop();
            stopAllDAWClips();
            updatePlayhead();
        });

        btnStop.addActionListener(e -> {
            isPlaying = false;
            isRecording = false;
            playTimer.stop();
            stopAllDAWClips();
            playheadTime = 0.0;
            updatePlayhead();
            visualizer.repaint();
        });

        btnRecord.addActionListener(e -> {
            isPlaying = true;
            isRecording = true;
            playTimer.start();
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

        btnExport.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export Audio Mixdown");
            chooser.setSelectedFile(new File("mixdown.wav"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File saveFile = chooser.getSelectedFile();
                try {
                    java.util.List<byte[]> streams = new ArrayList<>();
                    java.util.List<Double> starts = new ArrayList<>();
                    AudioFormat targetFormat = null;
                    
                    for (AudioTrack track : tracks) {
                        if (track.isMuted) continue;
                        for (AudioClip clip : track.clips) {
                            targetFormat = clip.audioFormat;
                            break;
                        }
                        if (targetFormat != null) break;
                    }
                    
                    if (targetFormat == null) {
                        JOptionPane.showMessageDialog(this, "No clips found to export!", "Export Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    
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
                        
                        for (AudioClip clip : track.clips) {
                            updateClipPcm(clip);
                            byte[] volAdjusted = clip.processedPcm.clone();
                            float clipVolume = (float) track.volume;
                            int samples = volAdjusted.length / 2;
                            ByteBuffer buf = ByteBuffer.wrap(volAdjusted);
                            buf.order(clip.audioFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                            for (int s = 0; s < samples; s++) {
                                short sample = buf.getShort(s * 2);
                                buf.putShort(s * 2, (short)(sample * clipVolume));
                            }
                            streams.add(volAdjusted);
                            starts.add(clip.start);
                        }
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
        statusLabel.setText(String.format("Status: %s  |  Time: %02d:%05.2f  |  Master: %d%%", 
                status, minutes, seconds, masterVolumeSlider.getValue()));
        timelineContainer.repaint();
    }

    private void stopAllDAWClips() {
        synchronized (activeDAWClips) {
            for (Clip c : new ArrayList<>(activeDAWClips)) {
                if (c.isOpen()) {
                    c.stop();
                    c.close();
                }
            }
            activeDAWClips.clear();
        }
    }

    private void playPcmData(byte[] pcm, AudioFormat format, float volume) {
        if (pcm == null || pcm.length == 0) return;
        try {
            AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm),
                format,
                pcm.length / format.getFrameSize()
            );
            Clip javaClip = AudioSystem.getClip();
            javaClip.open(ais);
            
            // Set volume
            FloatControl gainControl = (FloatControl) javaClip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log10(Math.max(volume, 0.0001)) * 20.0);
            dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
            gainControl.setValue(dB);
            
            activeDAWClips.add(javaClip);
            javaClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    activeDAWClips.remove(javaClip);
                    javaClip.close();
                }
            });
            javaClip.start();
        } catch (Exception ex) {
            System.err.println("Error playing PCM clip: " + ex.getMessage());
        }
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

    private void triggerClipsForRange(double fromTime, double toTime) {
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
            
            for (AudioClip clip : track.clips) {
                if (clip.start >= fromTime && clip.start < toTime) {
                    updateClipPcm(clip);
                    float clipVolume = (float) (track.volume * (masterVolumeSlider.getValue() / 100.0));
                    playPcmData(clip.processedPcm, clip.audioFormat, clipVolume);
                }
            }
        }
    }

    private void rebuildTimeline() {
        timelineContainer.removeAll();
        trackRowPanels.clear();

        // Timeline Rulers/Headers Row
        JPanel rulerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(60, 60, 60));
                int w = getWidth() - 150;
                for (int s = 0; s < 100; s += 2) {
                    int x = 150 + (s * 15);
                    g.drawLine(x, 10, x, getHeight());
                    if (s % 10 == 0) {
                        g.drawString(s + "s", x + 2, 12);
                    }
                }
            }
        };
        rulerPanel.setPreferredSize(new Dimension(getWidth(), 22));
        rulerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        rulerPanel.setBackground(new Color(38, 38, 38));
        timelineContainer.add(rulerPanel);

        // Rebuild each track visual representation
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack track = tracks.get(i);
            JPanel row = new JPanel(new BorderLayout());
            row.setPreferredSize(new Dimension(getWidth(), 70));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            row.setBackground(new Color(30, 30, 30));
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 50)));

            // Left Fader Control Panel
            JPanel faderPanel = new JPanel(null);
            faderPanel.setPreferredSize(new Dimension(150, 70));
            faderPanel.setBackground(new Color(40, 40, 40));
            faderPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(60, 60, 60)));

            JLabel lblName = new JLabel(track.name);
            lblName.setForeground(Color.WHITE);
            lblName.setFont(new Font("Arial", Font.BOLD, 11));
            lblName.setBounds(8, 4, 134, 18);
            faderPanel.add(lblName);

            JToggleButton btnMute = new JToggleButton("M", track.isMuted);
            btnMute.setToolTipText("Mute");
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
            btnSolo.setToolTipText("Solo");
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
            volSlider.setBounds(70, 24, 75, 20);
            volSlider.setBackground(new Color(40, 40, 40));
            volSlider.addChangeListener(e -> track.volume = volSlider.getValue() / 100.0);
            faderPanel.add(volSlider);

            row.add(faderPanel, BorderLayout.WEST);

            // Right Clips Workspace Panel
            JPanel clipsPanel = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    // Grid background
                    g2d.setColor(new Color(45, 45, 45));
                    for (int s = 0; s < 100; s += 2) {
                        int x = s * 15;
                        g2d.drawLine(x, 0, x, getHeight());
                    }

                    // Render clips
                    for (AudioClip clip : track.clips) {
                        int cx = (int) (clip.start * 15);
                        int cw = (int) (clip.duration * 15);
                        int cy = 10;
                        int ch = getHeight() - 20;

                        g2d.setColor(clip.color);
                        g2d.fillRoundRect(cx, cy, cw, ch, 6, 6);

                        g2d.setColor(Color.WHITE);
                        g2d.drawRoundRect(cx, cy, cw, ch, 6, 6);

                        if (clip == selectedClip) {
                            g2d.setColor(Color.YELLOW);
                            g2d.setStroke(new BasicStroke(3));
                            g2d.drawRoundRect(cx - 1, cy - 1, cw + 2, ch + 2, 6, 6);
                        }

                        // Draw soundwave curve
                        g2d.setColor(new Color(255, 255, 255, 120));
                        for (int px = cx + 4; px < cx + cw - 4; px += 4) {
                            int mid = cy + ch / 2;
                            int waveH = (int) (Math.sin(px * 0.1) * (ch / 3.0) + Math.cos(px * 0.05) * (ch / 4.0));
                            g2d.drawLine(px, mid - waveH, px, mid + waveH);
                        }

                        g2d.setColor(Color.WHITE);
                        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
                        g2d.drawString(clip.name, cx + 6, cy + 14);
                    }

                    // Draw vertical playhead line
                    int px = (int) (playheadTime * 15);
                    g2d.setColor(Color.ORANGE);
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(px, 0, px, getHeight());
                }
            };
            clipsPanel.setBackground(new Color(32, 32, 32));
            
            clipsPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    selectedClip = null;
                    for (AudioClip clip : track.clips) {
                        int cx = (int) (clip.start * 15);
                        int cw = (int) (clip.duration * 15);
                        int cy = 10;
                        int ch = clipsPanel.getHeight() - 20;
                        if (e.getX() >= cx && e.getX() <= cx + cw && e.getY() >= cy && e.getY() <= cy + ch) {
                            selectedClip = clip;
                            break;
                        }
                    }
                    timelineContainer.repaint();
                }
            });

            row.add(clipsPanel, BorderLayout.CENTER);

            timelineContainer.add(row);
            trackRowPanels.add(row);
        }

        timelineContainer.revalidate();
        timelineContainer.repaint();
    }
}
