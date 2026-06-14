package com.ignis.editor;

import com.ignis.animation.AnimationFrame;
import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;

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

/**
 * Animation editor (roadmap item 4): builds and edits 2D sprite animations on a
 * timeline of frames, previews playback, persists them to
 * project/assets/animations and assigns an {@link Animator} to a selected entity.
 *
 * Decoupled from the engine core beyond GameObject/AssetResolver; the animation
 * model itself ({@link SpriteAnimation}) carries no UI.
 */
public class AnimationEditorFrame extends JFrame {

    private final File projectFolder;   // the "project/" folder, or null
    private final File spritesFolder;   // assets/sprites, or null
    private final GameObject targetObject; // selected entity to assign to, or null

    private final DefaultListModel<AnimationFrame> frameModel = new DefaultListModel<>();
    private final JTextField nameField = new JTextField("new_animation", 14);
    private final JCheckBox loopCheck = new JCheckBox("Loop", true);
    private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 60, 1));
    private JComboBox<SpriteAnimation.CurveType> curveCombo;
    private final PreviewPanel preview = new PreviewPanel();
    private final TimelinePanel timelinePanel = new TimelinePanel();
    private final Timer previewTimer;

    private double previewElapsed;
    private boolean previewPlaying;
    private int selectedIndex = -1;
    private SpriteAnimation.CurveType easingCurve = SpriteAnimation.CurveType.LINEAR;

    public AnimationEditorFrame(File projectFolder, File spritesFolder, GameObject targetObject) {
        super("Ignis Animation Editor");
        com.ignis.core.AppIconHelper.setWindowIcon(this);
        this.projectFolder = projectFolder;
        this.spritesFolder = spritesFolder;
        this.targetObject = targetObject;

        add(buildToolBar(), BorderLayout.NORTH);
        add(preview, BorderLayout.CENTER);
        
        // South section with timeline and controls
        JPanel southPanel = new JPanel(new BorderLayout());
        JScrollPane timelineScroll = new JScrollPane(timelinePanel);
        timelineScroll.setPreferredSize(new Dimension(800, 120));
        timelineScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        southPanel.add(timelineScroll, BorderLayout.CENTER);
        southPanel.add(buildBottomBar(), BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);

        // ~60fps preview clock
        previewTimer = new Timer(16, e -> {
            previewElapsed += 0.016;
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
            timelinePanel.repaint();
            preview.repaint();
        });

        loadExistingIfAny();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
    }

    // ==================== UI BUILDERS ====================

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new JLabel(" Name: "));
        bar.add(nameField);
        bar.addSeparator();
        bar.add(loopCheck);
        bar.addSeparator();
        bar.add(new JLabel(" FPS: "));
        fpsSpinner.setMaximumSize(new Dimension(60, 26));
        bar.add(fpsSpinner);
        JButton applyFps = new JButton("Apply FPS to all");
        applyFps.addActionListener(e -> applyFpsToAll());
        bar.add(applyFps);

        bar.addSeparator();
        bar.add(new JLabel(" Easing Curve: "));
        curveCombo = new JComboBox<>(SpriteAnimation.CurveType.values());
        curveCombo.setSelectedItem(SpriteAnimation.CurveType.LINEAR);
        curveCombo.setMaximumSize(new Dimension(130, 26));
        curveCombo.addActionListener(e -> {
            easingCurve = (SpriteAnimation.CurveType) curveCombo.getSelectedItem();
            preview.repaint();
        });
        bar.add(curveCombo);

        return bar;
    }

    private JPanel buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(button("Play", e -> startPreview()));
        panel.add(button("Stop", e -> stopPreview()));
        panel.add(new JLabel("   |   "));
        panel.add(button("Add Frame...", e -> addFrames()));
        panel.add(button("Remove Frame", e -> removeFrame()));
        panel.add(button("Duplicate (Ctrl+D)", e -> duplicateFrame()));
        panel.add(new JLabel("   |   "));
        panel.add(button("Save", e -> save()));
        panel.add(button("Load...", e -> loadFromDialog()));
        if (targetObject != null) {
            panel.add(button("Assign to '" + targetObject.getName() + "'", e -> assignToTarget()));
        }
        return panel;
    }

    private JButton button(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        return button;
    }

    // ==================== FRAME OPS ====================

    private void addFrames() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Add Frames");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (*.png, *.jpg, *.jpeg, *.gif, *.bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (spritesFolder != null && spritesFolder.exists()) {
            chooser.setCurrentDirectory(spritesFolder);
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        double duration = 1.0 / (Integer) fpsSpinner.getValue();
        for (File file : chooser.getSelectedFiles()) {
            frameModel.addElement(new AnimationFrame(toProjectPath(file), duration));
        }
        if (selectedIndex == -1 && !frameModel.isEmpty()) {
            selectedIndex = 0;
        }
        timelinePanel.updatePreferredSize();
        timelinePanel.repaint();
    }

    private void removeFrame() {
        if (selectedIndex >= 0 && selectedIndex < frameModel.size()) {
            frameModel.remove(selectedIndex);
            if (frameModel.isEmpty()) {
                selectedIndex = -1;
            } else {
                selectedIndex = Math.min(selectedIndex, frameModel.size() - 1);
            }
            timelinePanel.updatePreferredSize();
            timelinePanel.repaint();
            preview.repaint();
        }
    }

    private void duplicateFrame() {
        if (selectedIndex >= 0 && selectedIndex < frameModel.size()) {
            AnimationFrame current = frameModel.get(selectedIndex);
            frameModel.add(selectedIndex + 1, new AnimationFrame(current.getSpritePath(), current.getDuration()));
            selectedIndex = selectedIndex + 1;
            timelinePanel.updatePreferredSize();
            timelinePanel.repaint();
            preview.repaint();
        }
    }

    private void applyFpsToAll() {
        double duration = 1.0 / (Integer) fpsSpinner.getValue();
        for (int i = 0; i < frameModel.size(); i++) {
            frameModel.get(i).setDuration(duration);
        }
        timelinePanel.repaint();
    }

    private double totalDuration() {
        double total = 0;
        for (int i = 0; i < frameModel.size(); i++) {
            total += frameModel.get(i).getDuration();
        }
        return total;
    }

    // ==================== PREVIEW ====================

    private void startPreview() {
        previewElapsed = 0;
        previewPlaying = true;
        previewTimer.start();
    }

    private void stopPreview() {
        previewPlaying = false;
        previewTimer.stop();
        preview.repaint();
        timelinePanel.repaint();
    }

    private class PreviewPanel extends JPanel {
        PreviewPanel() {
            setBackground(new Color(40, 40, 40));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            SpriteAnimation animation = buildAnimation();
            String path = previewPlaying
                    ? animation.spritePathAt(previewElapsed)
                    : (frameModel.isEmpty() ? null : frameModel.get(Math.max(0, selectedIndex)).getSpritePath());

            if (path == null) {
                g.setColor(Color.GRAY);
                g.drawString("Add frames and press Play", 20, 30);
                return;
            }
            BufferedImage image = AssetResolver.loadImage(path);
            if (image == null) {
                g.setColor(Color.RED);
                g.drawString("Missing: " + path, 20, 30);
                return;
            }
            // Fit-scale centered
            double scale = Math.min(
                    (getWidth() - 40.0) / image.getWidth(),
                    (getHeight() - 40.0) / image.getHeight());
            scale = Math.max(0.05, Math.min(scale, 8));
            int w = (int) (image.getWidth() * scale);
            int h = (int) (image.getHeight() * scale);
            g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
        }
    }

    // ==================== TIMELINE PANEL ====================

    private class TimelinePanel extends JPanel {
        private final double pixelsPerSecond = 200.0;
        private int dragFrameIndex = -1;
        private boolean draggingPlayhead = false;

        TimelinePanel() {
            setBackground(new Color(30, 30, 30));
            updatePreferredSize();

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Point pt = e.getPoint();
                    
                    // Check if clicked near any diamond keyframe
                    int index = getFrameAtX(pt.x);
                    if (index >= 0) {
                        dragFrameIndex = index;
                        selectedIndex = Math.min(index, frameModel.size() - 1);
                        repaint();
                        preview.repaint();
                    } else if (pt.y < 25) {
                        // Clicked on ruler: scrub playhead
                        draggingPlayhead = true;
                        scrub(pt.x);
                    } else {
                        // Check if clicked a block
                        int clickedBlock = getBlockAtX(pt.x);
                        if (clickedBlock >= 0) {
                            selectedIndex = clickedBlock;
                            repaint();
                            preview.repaint();
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragFrameIndex > 0) {
                        double newTime = e.getX() / pixelsPerSecond;
                        double prevStartTime = getFrameStartTime(dragFrameIndex - 1);
                        double newDuration = newTime - prevStartTime;
                        if (newDuration >= 0.05) { // minimum 50ms
                            frameModel.get(dragFrameIndex - 1).setDuration(newDuration);
                            updatePreferredSize();
                            revalidate();
                            repaint();
                            preview.repaint();
                        }
                    } else if (draggingPlayhead) {
                        scrub(e.getX());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragFrameIndex = -1;
                    draggingPlayhead = false;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);

            // Duplicate shortcut
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
            getActionMap().put("duplicate", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    duplicateFrame();
                }
            });
        }

        private void scrub(int x) {
            double time = x / pixelsPerSecond;
            previewElapsed = Math.max(0, Math.min(time, totalDuration()));
            repaint();
            preview.repaint();
        }

        private double getFrameStartTime(int index) {
            double t = 0;
            for (int i = 0; i < index; i++) {
                t += frameModel.get(i).getDuration();
            }
            return t;
        }

        private int getFrameAtX(int x) {
            for (int i = 0; i <= frameModel.size(); i++) {
                double t = getFrameStartTime(i);
                int kx = (int) (t * pixelsPerSecond);
                if (Math.abs(kx - x) <= 6) {
                    return i;
                }
            }
            return -1;
        }

        private int getBlockAtX(int x) {
            for (int i = 0; i < frameModel.size(); i++) {
                double start = getFrameStartTime(i);
                double dur = frameModel.get(i).getDuration();
                int x1 = (int) (start * pixelsPerSecond);
                int x2 = (int) ((start + dur) * pixelsPerSecond);
                if (x >= x1 && x < x2) {
                    return i;
                }
            }
            return -1;
        }

        void updatePreferredSize() {
            double total = totalDuration();
            int w = (int) Math.max(800.0, (total + 1.0) * pixelsPerSecond);
            setPreferredSize(new Dimension(w, 100));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Draw ticks on ruler
            g.setColor(new Color(60, 60, 60));
            g.fillRect(0, 0, w, 25);
            g.setColor(new Color(80, 80, 80));
            g.drawLine(0, 25, w, 25);

            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font("Dialog", Font.PLAIN, 10));
            double total = totalDuration();
            double maxTime = Math.max(total + 1.0, w / pixelsPerSecond);

            for (double t = 0.0; t <= maxTime; t += 0.1) {
                int x = (int) (t * pixelsPerSecond);
                boolean major = Math.abs(t * 10 % 5) < 0.1;
                if (major) {
                    g.drawLine(x, 10, x, 25);
                    g.drawString(String.format("%.1fs", t), x + 2, 20);
                } else {
                    g.drawLine(x, 18, x, 25);
                }
            }

            // Draw track background for frames
            g.setColor(new Color(45, 45, 45));
            g.fillRect(0, 26, w, h - 26);

            // Draw frames as blocks
            for (int i = 0; i < frameModel.size(); i++) {
                double start = getFrameStartTime(i);
                double dur = frameModel.get(i).getDuration();
                int x1 = (int) (start * pixelsPerSecond);
                int x2 = (int) ((start + dur) * pixelsPerSecond);

                g.setColor(i % 2 == 0 ? new Color(55, 55, 65) : new Color(70, 70, 80));
                if (i == selectedIndex) {
                    g.setColor(new Color(0, 100, 180));
                }
                g.fillRect(x1, 35, x2 - x1 - 1, 30);

                // Label
                g.setColor(Color.WHITE);
                g.drawString(shortName(frameModel.get(i).getSpritePath()), x1 + 5, 53);
            }

            // Draw diamond keyframes (diamonds at boundaries)
            for (int i = 0; i <= frameModel.size(); i++) {
                double t = getFrameStartTime(i);
                int x = (int) (t * pixelsPerSecond);

                g.setColor(i == selectedIndex || (i > 0 && i - 1 == selectedIndex) ? Color.CYAN : Color.LIGHT_GRAY);
                int[] dx = {x, x + 6, x, x - 6};
                int[] dy = {30, 35, 40, 35};
                g.fillPolygon(dx, dy, 4);
                g.setColor(Color.DARK_GRAY);
                g.drawPolygon(dx, dy, 4);
            }

            // Draw playhead (vertical red line)
            int px = (int) (previewElapsed * pixelsPerSecond);
            g.setColor(Color.RED);
            g.drawLine(px, 0, px, h);
            // Draw head handle
            int[] hx = {px - 5, px + 5, px + 5, px, px - 5};
            int[] hy = {0, 0, 8, 13, 8};
            g.fillPolygon(hx, hy, 5);
        }
    }

    // ==================== PERSISTENCE / ASSIGN ====================

    private SpriteAnimation buildAnimation() {
        SpriteAnimation animation = new SpriteAnimation(sanitizeName());
        animation.setLoop(loopCheck.isSelected());
        animation.setCurveType(easingCurve);
        for (int i = 0; i < frameModel.size(); i++) {
            AnimationFrame f = frameModel.get(i);
            animation.addFrame(new AnimationFrame(f.getSpritePath(), f.getDuration()));
        }
        return animation;
    }

    private void save() {
        if (projectFolder == null) {
            JOptionPane.showMessageDialog(this, "Open a project before saving animations.",
                    "Animation Editor", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (frameModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one frame.",
                    "Animation Editor", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            AnimationIO.save(buildAnimation(), projectFolder);
            JOptionPane.showMessageDialog(this, "Saved to assets/animations/" + sanitizeName() + ".anim.json",
                    "Animation Editor", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage(),
                    "Animation Editor", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadExistingIfAny() {
        if (projectFolder == null) {
            return;
        }
        java.util.List<SpriteAnimation> all = AnimationIO.loadAll(projectFolder);
        if (!all.isEmpty()) {
            applyAnimation(all.get(0));
        }
    }

    private void loadFromDialog() {
        if (projectFolder == null) {
            return;
        }
        java.util.List<SpriteAnimation> all = AnimationIO.loadAll(projectFolder);
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No animations found in this project.",
                    "Animation Editor", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] names = all.stream().map(SpriteAnimation::getName).toArray(String[]::new);
        JComboBox<String> combo = new JComboBox<>(names);
        if (JOptionPane.showConfirmDialog(this, combo, "Load Animation",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            applyAnimation(all.get(combo.getSelectedIndex()));
        }
    }

    private void applyAnimation(SpriteAnimation animation) {
        nameField.setText(animation.getName());
        loopCheck.setSelected(animation.isLoop());
        easingCurve = animation.getCurveType();
        if (curveCombo != null) {
            curveCombo.setSelectedItem(easingCurve);
        }
        frameModel.clear();
        for (AnimationFrame frame : animation.getFrames()) {
            frameModel.addElement(new AnimationFrame(frame.getSpritePath(), frame.getDuration()));
        }
        selectedIndex = frameModel.isEmpty() ? -1 : 0;
        timelinePanel.updatePreferredSize();
        timelinePanel.repaint();
        preview.repaint();
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
        JOptionPane.showMessageDialog(this,
                "Animator assigned to '" + targetObject.getName() + "'. Press Play in the editor to see it.",
                "Animation Editor", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== HELPERS ====================

    private String sanitizeName() {
        String name = nameField.getText().trim();
        return name.isEmpty() ? "animation" : name;
    }

    /** Stores frame paths relative to the project so animations stay portable. */
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
