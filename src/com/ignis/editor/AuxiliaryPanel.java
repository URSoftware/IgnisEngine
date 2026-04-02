package com.ignis.editor;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

import com.ignis.core.Game;

/**
 * Auxiliary panel with AI integration features
 * Provides ASK and AGENT modes for interacting with AI services
 */
public class AuxiliaryPanel extends JPanel {
    private AIIntegration aiIntegration;
    private Game game;
    private JTabbedPane tabbedPane;
    
    // File refresh callback
    private Runnable fileRefreshCallback;
    
    // Settings tab components
    private JPasswordField apiKeyField;
    private JLabel apiStatusLabel;
    
    // ASK tab components
    private JTextArea askInputArea;
    private JTextArea askOutputArea;
    private JButton askSendButton;
    private JLabel askStatusLabel;
    
    // AGENT tab components
    private JTextArea agentTaskArea;
    private JTextArea agentOutputArea;
    private JButton agentExecuteButton;
    private JLabel agentStatusLabel;
    
    // ==================== RATE LIMITING ====================
    // Free tier limits: 5 requests/min, 1500 requests/day
    private long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 12000; // 12 seconds to be safe
    private static final String RATE_LIMIT_WARNING = 
        "⚠️ Rate limited! Please wait before making another request.\n" +
        "Free tier limits: 5 requests/min, 1500/day\n\n" +
        "To increase limits:\n" +
        "1. Enable billing in Google Cloud Console\n" +
        "2. Or wait and try again in a few seconds";
    
    public AuxiliaryPanel(AIIntegration aiIntegration, Game game) {
        this.aiIntegration = aiIntegration;
        this.game = game;
        this.fileRefreshCallback = null; // Can be set via setFileRefreshCallback
        
        setLayout(new BorderLayout());
        setBackground(new Color(45, 45, 45));
        
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(45, 45, 45));
        tabbedPane.setForeground(Color.WHITE);
        
        // Create tabs
        tabbedPane.addTab("⚙️ Settings", createSettingsTab());
        tabbedPane.addTab("❓ Ask", createAskTab());
        tabbedPane.addTab("🤖 Agent", createAgentTab());
        
        add(tabbedPane, BorderLayout.CENTER);
    }
    
    /**
     * Creates the Settings tab for API configuration
     */
    private JPanel createSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(45, 45, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // API Key section
        JLabel apiLabel = new JLabel("Google Gemini API Key");
        apiLabel.setForeground(Color.WHITE);
        apiLabel.setFont(new Font("Arial", Font.BOLD, 13));
        apiLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(apiLabel);
        
        panel.add(Box.createVerticalStrut(8));
        
        // API Key input field
        apiKeyField = new JPasswordField();
        apiKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        apiKeyField.setPreferredSize(new Dimension(250, 32));
        apiKeyField.setBackground(new Color(60, 60, 60));
        apiKeyField.setForeground(Color.WHITE);
        apiKeyField.setCaretColor(Color.WHITE);
        apiKeyField.setFont(new Font("Monospace", Font.PLAIN, 12));
        apiKeyField.setText(aiIntegration.getApiKey());
        apiKeyField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                // Consume Ctrl+S and other editor shortcuts
                if ((e.getModifiers() & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) {
                    e.consume();
                }
            }
        });
        panel.add(apiKeyField);
        
        panel.add(Box.createVerticalStrut(10));
        
        // Save button
        JButton saveButton = new JButton("💾 Save API Key");
        saveButton.setBackground(new Color(70, 130, 180));
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        saveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> {
            String apiKey = new String(apiKeyField.getPassword()).trim();
            aiIntegration.setApiKey(apiKey);
            updateApiStatus();
            JOptionPane.showMessageDialog(this, "API Key saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });
        panel.add(saveButton);
        
        panel.add(Box.createVerticalStrut(15));
        
        // Status
        apiStatusLabel = new JLabel("Status: Not configured");
        apiStatusLabel.setForeground(Color.YELLOW);
        apiStatusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        apiStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(apiStatusLabel);
        updateApiStatus();
        
        panel.add(Box.createVerticalStrut(20));
        
        // Help link
        JLabel helpLabel = new JLabel("📖 For detailed setup instructions, read AI_INTEGRATION_GUIDE.md");
        helpLabel.setForeground(new Color(100, 150, 255));
        helpLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        helpLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(helpLabel);
        
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }
    
    /**
     * Creates the ASK tab for querying the AI
     */
    private JPanel createAskTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Input section
        JPanel inputSection = new JPanel(new BorderLayout());
        inputSection.setBackground(new Color(45, 45, 45));
        
        JLabel inputLabel = new JLabel("Your Question:");
        inputLabel.setForeground(Color.WHITE);
        inputLabel.setFont(new Font("Arial", Font.BOLD, 12));
        inputSection.add(inputLabel, BorderLayout.NORTH);
        
        askInputArea = new JTextArea(4, 40);
        askInputArea.setLineWrap(true);
        askInputArea.setWrapStyleWord(true);
        askInputArea.setBackground(new Color(60, 60, 60));
        askInputArea.setForeground(Color.WHITE);
        askInputArea.setCaretColor(Color.WHITE);
        askInputArea.setFont(new Font("Arial", Font.PLAIN, 12));
        askInputArea.setMargin(new Insets(8, 8, 8, 8));
        // Consume shortcuts to prevent global editor shortcuts
        askInputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    if (e.getKeyCode() != KeyEvent.VK_A && e.getKeyCode() != KeyEvent.VK_C && 
                        e.getKeyCode() != KeyEvent.VK_V && e.getKeyCode() != KeyEvent.VK_X) {
                        e.consume();
                    }
                }
            }
        });
        
        JScrollPane inputScroll = new JScrollPane(askInputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        inputSection.add(inputScroll, BorderLayout.CENTER);
        
        // Send button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(new Color(45, 45, 45));
        
        askSendButton = new JButton("🚀 Send Question");
        askSendButton.setBackground(new Color(34, 139, 34));
        askSendButton.setForeground(Color.WHITE);
        askSendButton.setFocusPainted(false);
        askSendButton.addActionListener(e -> handleAskMode());
        buttonPanel.add(askSendButton);
        
        askStatusLabel = new JLabel("Ready");
        askStatusLabel.setForeground(Color.GREEN);
        askStatusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(askStatusLabel);
        
        inputSection.add(buttonPanel, BorderLayout.SOUTH);
        
        // Output section
        JPanel outputSection = new JPanel(new BorderLayout());
        outputSection.setBackground(new Color(45, 45, 45));
        outputSection.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        JLabel outputLabel = new JLabel("AI Response:");
        outputLabel.setForeground(Color.WHITE);
        outputLabel.setFont(new Font("Arial", Font.BOLD, 12));
        outputSection.add(outputLabel, BorderLayout.NORTH);
        
        askOutputArea = new JTextArea(10, 40);
        askOutputArea.setLineWrap(true);
        askOutputArea.setWrapStyleWord(true);
        askOutputArea.setEditable(false);
        askOutputArea.setBackground(new Color(60, 60, 60));
        askOutputArea.setForeground(Color.LIGHT_GRAY);
        askOutputArea.setFont(new Font("Arial", Font.PLAIN, 11));
        askOutputArea.setMargin(new Insets(8, 8, 8, 8));
        
        JScrollPane outputScroll = new JScrollPane(askOutputArea);
        outputScroll.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        outputSection.add(outputScroll, BorderLayout.CENTER);
        
        // Split input and output
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputSection, outputSection);
        split.setResizeWeight(0.3);
        split.setDividerSize(5);
        panel.add(split, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Creates the AGENT tab for automated modifications
     */
    private JPanel createAgentTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Task description section
        JPanel taskSection = new JPanel(new BorderLayout());
        taskSection.setBackground(new Color(45, 45, 45));
        
        JLabel taskLabel = new JLabel("Task Description:");
        taskLabel.setForeground(Color.WHITE);
        taskLabel.setFont(new Font("Arial", Font.BOLD, 12));
        taskSection.add(taskLabel, BorderLayout.NORTH);
        
        agentTaskArea = new JTextArea(5, 40);
        agentTaskArea.setLineWrap(true);
        agentTaskArea.setWrapStyleWord(true);
        agentTaskArea.setBackground(new Color(60, 60, 60));
        agentTaskArea.setForeground(Color.WHITE);
        agentTaskArea.setCaretColor(Color.WHITE);
        agentTaskArea.setFont(new Font("Arial", Font.PLAIN, 12));
        agentTaskArea.setMargin(new Insets(8, 8, 8, 8));
        agentTaskArea.setText("Example: Create a new Java script 'Player.java' in the scripts folder that handles player movement");
        // Consume shortcuts to prevent global editor shortcuts
        agentTaskArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getModifiers() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    if (e.getKeyCode() != KeyEvent.VK_A && e.getKeyCode() != KeyEvent.VK_C && 
                        e.getKeyCode() != KeyEvent.VK_V && e.getKeyCode() != KeyEvent.VK_X) {
                        e.consume();
                    }
                }
            }
        });
        
        JScrollPane taskScroll = new JScrollPane(agentTaskArea);
        taskScroll.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        taskSection.add(taskScroll, BorderLayout.CENTER);
        
        // Execute button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(new Color(45, 45, 45));
        
        agentExecuteButton = new JButton("⚡ Execute Task");
        agentExecuteButton.setBackground(new Color(220, 20, 60));
        agentExecuteButton.setForeground(Color.WHITE);
        agentExecuteButton.setFocusPainted(false);
        agentExecuteButton.addActionListener(e -> handleAgentMode());
        buttonPanel.add(agentExecuteButton);
        
        agentStatusLabel = new JLabel("Ready");
        agentStatusLabel.setForeground(Color.GREEN);
        agentStatusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(agentStatusLabel);
        
        taskSection.add(buttonPanel, BorderLayout.SOUTH);
        
        // Output section
        JPanel outputSection = new JPanel(new BorderLayout());
        outputSection.setBackground(new Color(45, 45, 45));
        outputSection.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        JLabel outputLabel = new JLabel("Agent Actions & Results:");
        outputLabel.setForeground(Color.WHITE);
        outputLabel.setFont(new Font("Arial", Font.BOLD, 12));
        outputSection.add(outputLabel, BorderLayout.NORTH);
        
        agentOutputArea = new JTextArea(10, 40);
        agentOutputArea.setLineWrap(true);
        agentOutputArea.setWrapStyleWord(true);
        agentOutputArea.setEditable(false);
        agentOutputArea.setBackground(new Color(60, 60, 60));
        agentOutputArea.setForeground(Color.YELLOW);
        agentOutputArea.setFont(new Font("Arial", Font.PLAIN, 11));
        agentOutputArea.setMargin(new Insets(8, 8, 8, 8));
        
        JScrollPane outputScroll = new JScrollPane(agentOutputArea);
        outputScroll.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        outputSection.add(outputScroll, BorderLayout.CENTER);
        
        // Warning
        JPanel warningPanel = new JPanel(new BorderLayout());
        warningPanel.setBackground(new Color(45, 45, 45));
        warningPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        
        JLabel warningLabel = new JLabel("⚠️ Agent mode will modify your project files. Always review changes before applying!");
        warningLabel.setForeground(new Color(255, 165, 0));
        warningLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        warningPanel.add(warningLabel, BorderLayout.CENTER);
        
        outputSection.add(warningPanel, BorderLayout.SOUTH);
        
        // Split task and output
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, taskSection, outputSection);
        split.setResizeWeight(0.3);
        split.setDividerSize(5);
        panel.add(split, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Handles ASK mode query
     */
    private void handleAskMode() {
        if (!aiIntegration.hasApiKey()) {
            askStatusLabel.setText("❌ API Key not configured");
            askStatusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, "Please configure API Key in Settings tab first", "No API Key", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Check rate limit
        if (!checkRateLimit()) {
            askStatusLabel.setText("❌ Rate limited - wait before next request");
            askStatusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, RATE_LIMIT_WARNING, "Rate Limited", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String question = askInputArea.getText().trim();
        if (question.isEmpty()) {
            askStatusLabel.setText("❌ Please enter a question");
            askStatusLabel.setForeground(Color.RED);
            return;
        }
        
        askStatusLabel.setText("⏳ Processing...");
        askStatusLabel.setForeground(Color.YELLOW);
        askSendButton.setEnabled(false);
        
        // Run in background thread
        new Thread(() -> {
            try {
                // Build context
                String projectStructure = aiIntegration.getProjectStructure();
                String documentation = aiIntegration.getDocumentationContext();
                
                String fullPrompt = "You are an AI assistant for the Ignis Game Engine.\n\n" +
                        "PROJECT STRUCTURE:\n" + projectStructure + "\n\n" +
                        "PROJECT DOCUMENTATION:\n" + documentation + "\n\n" +
                        "USER QUESTION:\n" + question;
                
                // Call Google Gemini API
                String response = callGeminiAPI(fullPrompt, false);
                
                SwingUtilities.invokeLater(() -> {
                    askOutputArea.setText(response);
                    askStatusLabel.setText("✓ Response received");
                    askStatusLabel.setForeground(Color.GREEN);
                    askSendButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    askOutputArea.setText("Error: " + e.getMessage());
                    askStatusLabel.setText("❌ Error");
                    askStatusLabel.setForeground(Color.RED);
                    askSendButton.setEnabled(true);
                });
            }
        }).start();
    }
    
    /**
     * Handles AGENT mode task execution
     */
    private void handleAgentMode() {
        if (!aiIntegration.hasApiKey()) {
            agentStatusLabel.setText("❌ API Key not configured");
            agentStatusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, "Please configure API Key in Settings tab first", "No API Key", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Check rate limit
        if (!checkRateLimit()) {
            agentStatusLabel.setText("❌ Rate limited - wait before next request");
            agentStatusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, RATE_LIMIT_WARNING, "Rate Limited", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String task = agentTaskArea.getText().trim();
        if (task.isEmpty()) {
            agentStatusLabel.setText("❌ Please describe a task");
            agentStatusLabel.setForeground(Color.RED);
            return;
        }
        
        // Confirm with user
        int confirm = JOptionPane.showConfirmDialog(this,
                "Agent mode will modify your project files.\n\nTask: " + task + "\n\nContinue?",
                "Confirm Agent Action",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        agentStatusLabel.setText("⏳ Executing agent task...");
        agentStatusLabel.setForeground(Color.YELLOW);
        agentExecuteButton.setEnabled(false);
        
        // Run in background thread
        new Thread(() -> {
            try {
                // Build context with agent instructions
                String projectStructure = aiIntegration.getProjectStructure();
                String documentation = aiIntegration.getDocumentationContext();
                
                String agentPrompt = "You are an AI agent for the Ignis Game Engine project.\n\n" +
                        "=== IGNIS SCRIPT API - READ THIS CAREFULLY ===\n\n" +
                        "ALL scripts must follow this exact structure:\n\n" +
                        "import com.ignis.core.IgnisScript;\n\n" +
                        "public class MyScript extends IgnisScript {\n" +
                        "    @Override\n" +
                        "    public void start() { // Called once when initializing world simulation\n" +
                        "        // Initialization code here\n" +
                        "    }\n\n" +
                        "    @Override\n" +
                        "    public void tick() { // Called once every frame\n" +
                        "        // Your game logic here\n" +
                        "    }\n" +
                        "}\n\n" +
                        "KEY RULES FOR IGNIS SCRIPTS:\n" +
                        "1. ALWAYS extend IgnisScript (NOT Script, NOT anything else)\n" +
                        "2. ALWAYS import from com.ignis.core.* (NOT ignis.* packages)\n" +
                        "3. ALWAYS use tick() method (NOT update, NOT onUpdate)\n" +
                        "4. NEVER declare a package statement for scripts\n" +
                        "5. Use these imports:\n" +
                        "   - import com.ignis.core.IgnisScript;\n" +
                        "   - import com.ignis.core.Input;  (for keyboard/mouse input)\n" +
                        "   - import com.ignis.core.Game;   (for game data)\n" +
                        "   - import com.ignis.core.GameObject;  (for collision detection)\n" +
                        "   - import com.ignis.core.IgnisSampleCollisions;  (for collision types)\n\n" +
                        "WORKING WITH TRANSFORM:\n" +
                        "- Access with: transform.x, transform.y, transform.rotation\n" +
                        "- Modify with: transform.x += value;  OR  transform.y -= value;\n" +
                        "- Do NOT use: gameObject.getTransform().translate() or .scale() or .rotate()\n" +
                        "- transform is a protected field IN IgnisScript, use it directly\n\n" +
                        "WORKING WITH INPUT:\n" +
                        "- Use: Input.getInstance().isKeyPressed(KeyEvent.VK_W)\n" +
                        "- Do NOT use: Input.isKeyDown() directly\n" +
                        "- Import KeyEvent from java.awt.event.KeyEvent\n" +
                        "- Keys available: KeyEvent.VK_W, VK_A, VK_S, VK_D, VK_SPACE, etc.\n\n" +
                        "=== COLLISION SYSTEM (IMPORTANT!) ===\n\n" +
                        "The Ignis Engine has a BUILT-IN collision detection system:\n\n" +
                        "METHOD 1: Override onCollision() - PREFERRED WAY\n" +
                        "@Override\n" +
                        "public void onCollision(GameObject other) {\n" +
                        "    if (other == null) return;\n" +
                        "    System.out.println(\"Collided with: \" + other.getClass().getSimpleName());\n" +
                        "    // Access other object's properties:\n" +
                        "    double otherX = other.getX();\n" +
                        "    double otherY = other.getY();\n" +
                        "    int width = other.getWidth();\n" +
                        "    int height = other.getHeight();\n" +
                        "}\n\n" +
                        "METHOD 2: Configure Collider Type\n" +
                        "@Override\n" +
                        "public void start() {\n" +
                        "    // Set collider type (AABB, CIRCLE, or POLYGON):\n" +
                        "    gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);\n" +
                        "    // Set collision mode (TRIGGER or COLLISION):\n" +
                        "    gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.COLLISION);\n" +
                        "}\n\n" +
                        "COLLIDER TYPES:\n" +
                        "- AABB: Rectangles aligned to axes (fast, no rotation support)\n" +
                        "- CIRCLE: Circular collision (for rounded objects)\n" +
                        "- POLYGON: Complex shapes with SAT algorithm (slower but accurate)\n\n" +
                        "COLLISION MODES:\n" +
                        "- TRIGGER: Only detects collisions, objects pass through (for pickups, areas)\n" +
                        "- COLLISION: Detects and resolves physically (for walls, platforms, solids)\n\n" +
                        "COMPLETE COLLISION EXAMPLE:\n" +
                        "import com.ignis.core.IgnisScript;\n" +
                        "import com.ignis.core.GameObject;\n" +
                        "import com.ignis.core.IgnisSampleCollisions;\n\n" +
                        "public class EnemyScript extends IgnisScript {\n" +
                        "    private java.util.Set<GameObject> collidingWith = new java.util.HashSet<>();\n\n" +
                        "    @Override\n" +
                        "    public void start() {\n" +
                        "        gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);\n" +
                        "    }\n\n" +
                        "    @Override\n" +
                        "    public void onCollision(GameObject other) {\n" +
                        "        if (other == null || collidingWith.contains(other)) return;\n" +
                        "        collidingWith.add(other);\n" +
                        "        System.out.println(\"[COLLISION] Hit \" + other.getClass().getSimpleName());\n" +
                        "    }\n" +
                        "}\n\n" +
                        "IMPORTANT COLLISION RULES:\n" +
                        "- NEVER do manual distance calculations in tick() when you have colliders\n" +
                        "- ALWAYS check for null in onCollision()\n" +
                        "- ALWAYS use getX(), getY(), getWidth(), getHeight() to access other objects\n" +
                        "- NEVER call getTransform() on other GameObject - use the methods above\n" +
                        "- GameObject access via onCollision() is THE proper way\n\n" +
                        "COMMON COLLISION MISTAKES:\n" +
                        "- ❌ Manual distance checks in tick()  →  ✅ Use onCollision()\n" +
                        "- ❌ No collider configured  →  ✅ setColliderType() in start()\n" +
                        "- ❌ other.getTransform().x  →  ✅ other.getX()\n" +
                        "- ❌ Not checking for null  →  ✅ if (other == null) return;\n" +
                        "- ❌ COLLISION mode for pickups  →  ✅ Use TRIGGER mode\n\n" +
                        "COMMON MISTAKES TO AVOID:\n" +
                        "- ❌ extends Script  →  ✅ extends IgnisScript\n" +
                        "- ❌ package scripts;  →  ✅ no package statement\n" +
                        "- ❌ import ignis.*;  →  ✅ import com.ignis.core.*;\n" +
                        "- ❌ public void update()  →  ✅ @Override public void tick()\n" +
                        "- ❌ gameObject.getTransform().translate()  →  ✅ transform.x += value\n" +
                        "- ❌ Input.isKeyDown()  →  ✅ Input.getInstance().isKeyPressed()\n\n" +
                        "=== YOUR TASK ===\n" + task + "\n\n" +
                        "=== PROJECT STRUCTURE ===\n" + projectStructure + "\n\n" +
                        "=== PROJECT DOCUMENTATION ===\n" + documentation + "\n\n" +
                        "=== AVAILABLE ACTIONS ===\n" +
                        "You can perform these actions by formatting your response exactly as shown:\n\n" +
                        "1. TO CREATE A FILE (.java extension for code files):\n" +
                        "CREATE_FILE: path/to/filename.java\n" +
                        "<entire file content here>\n" +
                        "/CREATE_FILE\n\n" +
                        "2. TO EDIT A FILE:\n" +
                        "EDIT_FILE: path/to/filename.java\n" +
                        "<entire new file content here>\n" +
                        "/EDIT_FILE\n\n" +
                        "3. TO DELETE A FILE:\n" +
                        "DELETE_FILE: path/to/filename.java\n\n" +
                        "4. TO DESCRIBE AN ACTION:\n" +
                        "EXECUTE_ACTION: description of what was done\n\n" +
                        "=== CRITICAL REQUIREMENTS ===\n" +
                        "- ALWAYS use .java extension for code files\n" +
                        "- NEVER use .ignis extension\n" +
                        "- Write COMPLETE, compilable Java code\n" +
                        "- Every file you create must follow the IgnisScript template above\n" +
                        "- For collision scripts, ALWAYS override onCollision() method\n" +
                        "- Configure colliders properly with setColliderType()\n" +
                        "- Test your code mentally before writing it\n" +
                        "- Verify imports and package names are correct\n" +
                        "- Use one action marker per file operation\n" +
                        "- Escape special characters properly in JSON strings\n" +
                        "- Analyze the task first, then execute actions step by step\n\n" +
                        "Start your response with your analysis, then your file operations.";
                
                // Log what we're sending
                System.out.println("[AGENT] Sending task to Gemini...");
                System.out.println("[AGENT] Task: " + task);
                
                // Call Google Gemini API
                String response = callGeminiAPI(agentPrompt, true);
                
                System.out.println("[AGENT] Received response (" + response.length() + " chars)");
                
                // Parse and execute actions
                String result = parseAndExecuteAgentActions(response);
                
                SwingUtilities.invokeLater(() -> {
                    agentOutputArea.setText(result);
                    if (result.contains("Failed") || result.contains("Error") || result.contains("❌")) {
                        agentStatusLabel.setText("⚠️ Task completed with issues");
                        agentStatusLabel.setForeground(new Color(255, 165, 0));
                    } else if (result.contains("✅")) {
                        agentStatusLabel.setText("✓ Agent task completed");
                        agentStatusLabel.setForeground(Color.GREEN);
                    } else {
                        agentStatusLabel.setText("⏳ Task executed - review output");
                        agentStatusLabel.setForeground(Color.YELLOW);
                    }
                    agentExecuteButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    String errorMsg = "Error: " + e.getMessage() + "\n\n";
                    errorMsg += "Stack trace:\n";
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    errorMsg += sw.toString();
                    agentOutputArea.setText(errorMsg);
                    agentStatusLabel.setText("❌ Error");
                    agentStatusLabel.setForeground(Color.RED);
                    agentExecuteButton.setEnabled(true);
                });
            }
        }).start();
    }
    
    /**
     * Checks rate limit to prevent hitting API quota
     * @return true if allowed to make request, false if should wait
     */
    private synchronized boolean checkRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            long waitTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest;
            System.out.println("Rate limit: wait " + (waitTime / 1000) + "s before next request");
            return false;
        }
        
        lastRequestTime = currentTime;
        return true;
    }
    
    /**
     * Calls Google Gemini API
     */
    private String callGeminiAPI(String prompt, boolean agentMode) throws Exception {
        String apiKey = aiIntegration.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key not configured");
        }
        
        try {
            // Try to use HttpClient for REST API call
            return callGeminiAPIViaREST(apiKey, prompt);
        } catch (Exception e) {
            // If HTTP call fails, return helpful message
            System.err.println("Failed to call Gemini API: " + e.getMessage());
            return "The API infrastructure is ready, but the call failed.\n" +
                   "Reason: " + e.getMessage() + "\n\n" +
                   "This usually means:\n" +
                   "1. Invalid API key\n" +
                   "2. Network connectivity issue\n" +
                   "3. API rate limit exceeded\n" +
                   "4. Google Generative AI SDK not installed\n\n" +
                   "Please check your API key and try again.";
        }
    }
    
    /**
     * Calls Gemini API using REST (requires Java 11+)
     */
    private String callGeminiAPIViaREST(String apiKey, String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
        
        // Build JSON request body
        String jsonBody = "{\n" +
            "  \"contents\": [{\n" +
            "    \"parts\": [{\n" +
            "      \"text\": \"" + escapeJson(prompt) + "\"\n" +
            "    }]\n" +
            "  }]\n" +
            "}";
        
        try {
            // Try with Java 11+ HttpClient
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseGeminiResponse(response.body());
            } else {
                String errorMsg = "API Error: " + response.statusCode() + "\n";
                errorMsg += response.body();
                System.err.println("API Error Response: " + errorMsg);
                return errorMsg;
            }
        } catch ( NoClassDefFoundError e) {
            // Java 11+ HttpClient not available, try legacy URLConnection
            return callGeminiAPIViaURLConnection(url, jsonBody);
        }
    }
    
    /**
     * Fallback for older Java versions using URLConnection
     */
    private String callGeminiAPIViaURLConnection(String urlString, String jsonBody) throws Exception {
        java.net.URL url = new java.net.URL(urlString);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (java.io.OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        int status = conn.getResponseCode();
        if (status == 200) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return parseGeminiResponse(response.toString());
            }
        } else {
            // Read error response
            StringBuilder errorResponse = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    errorResponse.append(line);
                }
            } catch (Exception e) {
                // Ignore error reading error stream
            }
            String errMsg = "API Error: " + status + "\n" + errorResponse.toString();
            System.err.println(errMsg);
            return errMsg;
        }
    }
    
    /**
     * Parses the JSON response from Gemini API
     * Handles: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
     */
    private String parseGeminiResponse(String jsonResponse) {
        try {
            System.out.println("[API RESPONSE] Received " + jsonResponse.length() + " bytes");
            
            // Check if it's an error response
            if (jsonResponse.contains("\"error\"")) {
                System.err.println("[API ERROR] Error response: " + jsonResponse);
                return "API Error: " + jsonResponse;
            }
            
            // More robust JSON parsing
            // Structure: {"candidates":[{"content":{"parts":[{"text":"actual text here"}]}}]}
            String text = extractJsonField(jsonResponse, "\"text\"");
            if (text != null && !text.isEmpty()) {
                System.out.println("[PARSED] Successfully extracted " + text.length() + " chars");
                return text;
            }
            
            // If parsing fails, return raw response for debugging
            System.err.println("[PARSE ERROR] Could not extract text field from: " + jsonResponse);
            return "Unexpected response format:\n\n" + jsonResponse;
        } catch (Exception e) {
            System.err.println("[PARSE EXCEPTION] " + e.getMessage());
            e.printStackTrace();
            return "Error parsing response: " + e.getMessage();
        }
    }
    
    /**
     * Extracts JSON string value from response
     * Handles escaped quotes and newlines correctly
     */
    private String extractJsonField(String json, String fieldName) {
        // Find the field
        String marker = fieldName + ":";
        int fieldIndex = json.indexOf(marker);
        if (fieldIndex == -1) {
            return null;
        }
        
        // Skip to the opening quote
        int startIdx = json.indexOf('"', fieldIndex + marker.length());
        if (startIdx == -1) {
            return null;
        }
        
        startIdx++; // Skip the opening quote
        
        // Find the closing quote, accounting for escaped quotes
        StringBuilder result = new StringBuilder();
        boolean inString = true;
        for (int i = startIdx; i < json.length() && inString; i++) {
            char c = json.charAt(i);
            
            if (c == '\\' && i + 1 < json.length()) {
                // Handle escape sequence
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n':
                        result.append('\n');
                        i++; // Skip the next char
                        break;
                    case 't':
                        result.append('\t');
                        i++;
                        break;
                    case 'r':
                        result.append('\r');
                        i++;
                        break;
                    case '"':
                        result.append('"');
                        i++;
                        break;
                    case '\\':
                        result.append('\\');
                        i++;
                        break;
                    default:
                        result.append(c);
                }
            } else if (c == '"') {
                // End of string
                inString = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Escapes special characters for JSON
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Parses AI response and executes file operations for AGENT mode
     */
    private String parseAndExecuteAgentActions(String response) {
        StringBuilder result = new StringBuilder();
        result.append("Agent Execution Log:\n");
        result.append("====================\n\n");
        
        // Log the raw response for debugging
        result.append("Raw Response Length: ").append(response.length()).append(" chars\n");
        if (response.contains("Error") || response.contains("error")) {
            result.append("⚠️ API returned an error!\n\n");
            result.append(response).append("\n\n");
            return result.toString();
        }
        
        boolean actionsFound = false;
        
        // Parse CREATE_FILE actions
        System.out.println("[AGENT] Parsing CREATE_FILE actions...");
        String[] createFileActions = response.split("CREATE_FILE:");
        for (int i = 1; i < createFileActions.length; i++) {
            actionsFound = true;
            String action = createFileActions[i];
            int endIndex = action.indexOf("/CREATE_FILE");
            if (endIndex == -1) {
                endIndex = action.length();
            }
            
            String content = action.substring(0, endIndex).trim();
            String[] lines = content.split("\n", 2);
            if (lines.length < 2) {
                result.append("⚠️ Invalid CREATE_FILE action (missing content)\n");
                continue;
            }
            
            String filePath = lines[0].trim();
            String fileContent = lines[1];
            
            System.out.println("[AGENT] Creating file: " + filePath);
            if (aiIntegration.writeFileContent(filePath, fileContent)) {
                result.append("✓ Created file: ").append(filePath)
                      .append(" (").append(fileContent.length()).append(" bytes)\n");
                // Refresh file tree to show new file
                if (fileRefreshCallback != null) {
                    fileRefreshCallback.run();
                }
            } else {
                result.append("✗ Failed to create file: ").append(filePath).append("\n");
            }
        }
        
        // Parse EDIT_FILE actions
        System.out.println("[AGENT] Parsing EDIT_FILE actions...");
        String[] editFileActions = response.split("EDIT_FILE:");
        for (int i = 1; i < editFileActions.length; i++) {
            actionsFound = true;
            String action = editFileActions[i];
            int endIndex = action.indexOf("/EDIT_FILE");
            if (endIndex == -1) {
                endIndex = action.length();
            }
            
            String content = action.substring(0, endIndex).trim();
            String[] lines = content.split("\n", 2);
            if (lines.length < 2) {
                result.append("⚠️ Invalid EDIT_FILE action (missing content)\n");
                continue;
            }
            
            String filePath = lines[0].trim();
            String fileContent = lines[1];
            
            System.out.println("[AGENT] Editing file: " + filePath);
            if (aiIntegration.writeFileContent(filePath, fileContent)) {
                result.append("✓ Edited file: ").append(filePath)
                      .append(" (").append(fileContent.length()).append(" bytes)\n");
                // Refresh file tree to show updated file
                if (fileRefreshCallback != null) {
                    fileRefreshCallback.run();
                }
            } else {
                result.append("✗ Failed to edit file: ").append(filePath).append("\n");
            }
        }
        
        // Parse DELETE_FILE actions
        System.out.println("[AGENT] Parsing DELETE_FILE actions...");
        String[] deleteActions = response.split("DELETE_FILE:");
        for (int i = 1; i < deleteActions.length; i++) {
            actionsFound = true;
            String filePath = deleteActions[i].trim().split("\n")[0].trim();
            result.append("⚠️ Delete requested for: ").append(filePath)
                  .append(" (manual review needed)\n");
        }
        
        // Parse EXECUTE_ACTION descriptions
        System.out.println("[AGENT] Parsing EXECUTE_ACTION...");
        String[] actionLines = response.split("EXECUTE_ACTION:");
        for (int i = 1; i < actionLines.length; i++) {
            actionsFound = true;
            String action = actionLines[i].trim();
            if (!action.isEmpty()) {
                String firstLine = action.split("\n")[0];
                result.append("➜ ").append(firstLine).append("\n");
            }
        }
        
        if (!actionsFound) {
            result.append("\n❌ No structured actions found in response!\n");
            result.append("\nAgent response (for debugging):\n");
            result.append("================================\n");
            result.append(response);
            result.append("\n\nTIP: Make sure the Agent prompt instructs to use these exact markers:\n");
            result.append("  CREATE_FILE: path\n<content>\n/CREATE_FILE\n");
            result.append("  EDIT_FILE: path\n<content>\n/EDIT_FILE\n");
            result.append("  DELETE_FILE: path\n");
        } else {
            result.append("\n✅ Agent task completed successfully!");
        }
        
        return result.toString();
    }
    
    /**
     * Sets a callback to refresh the file tree when files are created/modified
     */
    public void setFileRefreshCallback(Runnable callback) {
        this.fileRefreshCallback = callback;
    }
    
    /**
     * Updates the API status label
     */
    private void updateApiStatus() {
        if (aiIntegration.hasApiKey()) {
            apiStatusLabel.setText("Status: ✓ Configured");
            apiStatusLabel.setForeground(Color.GREEN);
        } else {
            apiStatusLabel.setText("Status: ✗ Not configured");
            apiStatusLabel.setForeground(Color.RED);
        }
    }
}
