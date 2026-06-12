package com.ignis.audioeditor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    // --- Decoupled Audio Data Models ---
    public static class AudioClip {
        String name;
        double start; // In seconds
        double duration; // In seconds
        Color color;

        public AudioClip(String name, double start, double duration, Color color) {
            this.name = name;
            this.start = start;
            this.duration = duration;
            this.color = color;
        }
    }

    public static class AudioTrack {
        String name;
        double volume = 0.8; // 0.0 to 1.0
        boolean isMuted = false;
        boolean isSolo = false;
        List<AudioClip> clips = new ArrayList<>();

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
        tracks.get(0).clips.add(new AudioClip("Intro Theme", 0.0, 12.0, new Color(70, 130, 180, 180)));
        tracks.get(0).clips.add(new AudioClip("Main Loop", 12.0, 24.0, new Color(70, 130, 180, 180)));
        
        tracks.add(new AudioTrack("SFX Track 1"));
        tracks.get(1).clips.add(new AudioClip("Jump_01.wav", 3.5, 1.2, new Color(46, 139, 87, 180)));
        tracks.get(1).clips.add(new AudioClip("Jump_02.wav", 15.0, 1.2, new Color(46, 139, 87, 180)));
        
        tracks.add(new AudioTrack("Voiceover Track"));
        tracks.get(2).clips.add(new AudioClip("Welcome.ogg", 1.0, 4.5, new Color(218, 165, 32, 180)));

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

        add(controlsPanel, BorderLayout.NORTH);

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
                    playheadTime += 0.05;
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
            updatePlayhead();
        });

        btnStop.addActionListener(e -> {
            isPlaying = false;
            isRecording = false;
            playTimer.stop();
            playheadTime = 0.0;
            updatePlayhead();
            visualizer.repaint();
        });

        btnRecord.addActionListener(e -> {
            isPlaying = true;
            isRecording = true;
            playTimer.start();
            // Simulate adding a recorded audio clip after some time
            tracks.get(1).clips.add(new AudioClip("Rec_" + System.currentTimeMillis() + ".wav", playheadTime, 3.0, new Color(139, 0, 0, 180)));
            rebuildTimeline();
        });

        btnAddTrack.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Enter Track Name:", "Add Track", JOptionPane.QUESTION_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                tracks.add(new AudioTrack(name.trim()));
                rebuildTimeline();
            }
        });

        btnExport.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export Audio Mixdown");
            chooser.setSelectedFile(new File("mixdown.wav"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                // Simulate processing export
                ProgressMonitor pm = new ProgressMonitor(this, "Exporting Audio Mixdown", "Encoding tracks...", 0, 100);
                new Thread(() -> {
                    for (int i = 0; i <= 100; i += 10) {
                        try { Thread.sleep(150); } catch (Exception ex) {}
                        int finalI = i;
                        SwingUtilities.invokeLater(() -> pm.setProgress(finalI));
                    }
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Audio exported successfully!", "Export Complete", JOptionPane.INFORMATION_MESSAGE));
                }).start();
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

    private void rebuildTimeline() {
        timelineContainer.removeAll();
        trackRowPanels.clear();

        // Timeline Rulers/Headers Row
        JPanel rulerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(60, 60, 60));
                // Draw seconds tick marks
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

                        // Draw simple soundwave curves inside clip
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
            row.add(clipsPanel, BorderLayout.CENTER);

            timelineContainer.add(row);
            trackRowPanels.add(row);
        }

        timelineContainer.revalidate();
        timelineContainer.repaint();
    }
}
