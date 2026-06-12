package com.ignis.editor;

import com.ignis.animation.AnimationFrame;
import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private final JList<AnimationFrame> frameList = new JList<>(frameModel);
    private final JTextField nameField = new JTextField("new_animation", 14);
    private final JCheckBox loopCheck = new JCheckBox("Loop", true);
    private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 60, 1));
    private final PreviewPanel preview = new PreviewPanel();
    private final Timer previewTimer;

    private double previewElapsed;
    private boolean previewPlaying;

    public AnimationEditorFrame(File projectFolder, File spritesFolder, GameObject targetObject) {
        super("Ignis Animation Editor");
        com.ignis.core.AppIconHelper.setWindowIcon(this);
        this.projectFolder = projectFolder;
        this.spritesFolder = spritesFolder;
        this.targetObject = targetObject;

        frameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        frameList.setCellRenderer((list, value, index, sel, focus) -> {
            JLabel label = new JLabel(String.format("%2d.  %s  (%.0f ms)",
                    index + 1, shortName(value.getSpritePath()), value.getDuration() * 1000));
            label.setOpaque(true);
            if (sel) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });

        add(buildToolBar(), BorderLayout.NORTH);
        add(buildFramePanel(), BorderLayout.WEST);
        add(preview, BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        // ~60fps preview clock
        previewTimer = new Timer(16, e -> {
            previewElapsed += 0.016;
            preview.repaint();
        });

        loadExistingIfAny();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(820, 560);
        setLocationByPlatform(true);
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
        return bar;
    }

    private JPanel buildFramePanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Frames (timeline)"));
        panel.setPreferredSize(new Dimension(300, 0));
        panel.add(new JScrollPane(frameList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        buttons.add(button("Add...", e -> addFrames()));
        buttons.add(button("Remove", e -> removeFrame()));
        buttons.add(button("Up", e -> moveFrame(-1)));
        buttons.add(button("Down", e -> moveFrame(1)));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBottomBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(button("Play", e -> startPreview()));
        panel.add(button("Stop", e -> stopPreview()));
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
    }

    private void removeFrame() {
        int index = frameList.getSelectedIndex();
        if (index >= 0) {
            frameModel.remove(index);
        }
    }

    private void moveFrame(int direction) {
        int index = frameList.getSelectedIndex();
        int target = index + direction;
        if (index < 0 || target < 0 || target >= frameModel.size()) {
            return;
        }
        AnimationFrame frame = frameModel.remove(index);
        frameModel.add(target, frame);
        frameList.setSelectedIndex(target);
    }

    private void applyFpsToAll() {
        double duration = 1.0 / (Integer) fpsSpinner.getValue();
        for (int i = 0; i < frameModel.size(); i++) {
            frameModel.get(i).setDuration(duration);
        }
        frameList.repaint();
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
                    : (frameModel.isEmpty() ? null : frameModel.get(Math.max(0, frameList.getSelectedIndex())).getSpritePath());

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

    // ==================== PERSISTENCE / ASSIGN ====================

    private SpriteAnimation buildAnimation() {
        SpriteAnimation animation = new SpriteAnimation(sanitizeName());
        animation.setLoop(loopCheck.isSelected());
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
        frameModel.clear();
        for (AnimationFrame frame : animation.getFrames()) {
            frameModel.addElement(new AnimationFrame(frame.getSpritePath(), frame.getDuration()));
        }
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
        // Outside the project folder: copy would be ideal, but keep absolute as
        // a fallback (mirrors legacy sprite behavior) and warn the user.
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
