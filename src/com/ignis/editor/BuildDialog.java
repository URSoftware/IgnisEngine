package com.ignis.editor;

import com.ignis.builder.BuildConfig;
import com.ignis.builder.BuildLogger;
import com.ignis.builder.BuildResult;
import com.ignis.builder.BuildTarget;
import com.ignis.builder.Builder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Build dialog — editor front-end of the {@link Builder} module.
 *
 * Lets the developer configure game metadata, window settings and the
 * desired platforms, then runs the build on a background thread streaming
 * progress into a log area. Settings persist to build.json in the project.
 */
public class BuildDialog extends JDialog {

    private final File ignisFile;
    private final BuildConfig config;

    private final JTextField nameField;
    private final JTextField versionField;
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private final JCheckBox fullscreenCheck;
    private final Map<BuildTarget, JCheckBox> targetChecks = new EnumMap<>(BuildTarget.class);
    private final JTextArea logArea;
    private final JButton buildButton;
    private final JButton openOutputButton;

    private File lastOutputDir;

    public BuildDialog(JFrame owner, File ignisFile, String defaultGameName) {
        super(owner, "Build Project", false);
        this.ignisFile = ignisFile;
        this.config = BuildConfig.load(ignisFile.getParentFile());
        if (config.getGameName() == null || config.getGameName().equals("IgnisGame")) {
            config.setGameName(defaultGameName);
        }

        setLayout(new BorderLayout(8, 8));

        // ---- Settings panel ----
        JPanel settings = new JPanel(new GridLayout(0, 2, 6, 6));
        settings.setBorder(BorderFactory.createTitledBorder("Game Settings"));

        settings.add(new JLabel("Game name:"));
        nameField = new JTextField(config.getGameName());
        settings.add(nameField);

        settings.add(new JLabel("Version:"));
        versionField = new JTextField(config.getVersion());
        settings.add(versionField);

        settings.add(new JLabel("Window width:"));
        widthSpinner = new JSpinner(new SpinnerNumberModel(config.getWidth(), 320, 7680, 10));
        settings.add(widthSpinner);

        settings.add(new JLabel("Window height:"));
        heightSpinner = new JSpinner(new SpinnerNumberModel(config.getHeight(), 240, 4320, 10));
        settings.add(heightSpinner);

        settings.add(new JLabel("Fullscreen:"));
        fullscreenCheck = new JCheckBox("", config.isFullscreen());
        settings.add(fullscreenCheck);

        // ---- Targets panel ----
        JPanel targets = new JPanel();
        targets.setLayout(new BoxLayout(targets, BoxLayout.Y_AXIS));
        targets.setBorder(BorderFactory.createTitledBorder("Platforms"));

        targets.add(new JLabel("Java (JVM distribution):"));
        for (BuildTarget target : BuildTarget.values()) {
            if (target.getStrategy() == BuildTarget.Strategy.JAVA) {
                targets.add(targetCheckbox(target));
            }
        }
        targets.add(Box.createVerticalStrut(6));
        targets.add(new JLabel("Native C++ export (consoles):"));
        for (BuildTarget target : BuildTarget.values()) {
            if (target.getStrategy() == BuildTarget.Strategy.CPP) {
                targets.add(targetCheckbox(target));
            }
        }

        JPanel north = new JPanel(new GridLayout(1, 2, 8, 8));
        north.add(settings);
        north.add(targets);
        add(north, BorderLayout.NORTH);

        // ---- Log area ----
        logArea = new JTextArea(14, 70);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Build Log"));
        add(scroll, BorderLayout.CENTER);

        // ---- Buttons ----
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        openOutputButton = new JButton("Open Output Folder");
        openOutputButton.setEnabled(false);
        openOutputButton.addActionListener(e -> openOutputFolder());
        buttons.add(openOutputButton);

        buildButton = new JButton("Build");
        buildButton.addActionListener(e -> startBuild());
        buttons.add(buildButton);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JCheckBox targetCheckbox(BuildTarget target) {
        String label = target.getDisplayName() + (target.isEnabled() ? "" : " (planned)");
        JCheckBox check = new JCheckBox(label, config.getTargets().contains(target));
        check.setEnabled(target.isEnabled());
        targetChecks.put(target, check);
        return check;
    }

    private void applyToConfig() {
        config.setGameName(nameField.getText().trim());
        config.setVersion(versionField.getText().trim());
        config.setWidth((Integer) widthSpinner.getValue());
        config.setHeight((Integer) heightSpinner.getValue());
        config.setFullscreen(fullscreenCheck.isSelected());

        List<BuildTarget> selected = new ArrayList<>();
        for (Map.Entry<BuildTarget, JCheckBox> entry : targetChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        config.setTargets(selected);
    }

    private void startBuild() {
        applyToConfig();
        if (config.getTargets().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one platform.",
                    "Build", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            config.save(ignisFile.getParentFile());
        } catch (Exception e) {
            appendLog("[WARN] Could not save build.json: " + e.getMessage());
        }

        buildButton.setEnabled(false);
        openOutputButton.setEnabled(false);
        logArea.setText("");

        BuildLogger logger = message -> SwingUtilities.invokeLater(() -> appendLog(message));

        Thread worker = new Thread(() -> {
            List<BuildResult> results = new Builder().build(ignisFile, config, logger);
            SwingUtilities.invokeLater(() -> onBuildFinished(results));
        }, "IgnisBuilder");
        worker.setDaemon(true);
        worker.start();
    }

    private void onBuildFinished(List<BuildResult> results) {
        buildButton.setEnabled(true);
        appendLog("");
        for (BuildResult result : results) {
            appendLog(result.toString());
            if (result.isSuccess() && result.getOutputDir() != null) {
                lastOutputDir = result.getOutputDir().getParentFile();
            }
        }
        openOutputButton.setEnabled(lastOutputDir != null);
    }

    private void openOutputFolder() {
        try {
            File dir = lastOutputDir != null
                    ? lastOutputDir
                    : new File(ignisFile.getParentFile(), config.getOutputDirName());
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not open folder: " + e.getMessage(),
                    "Build", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
