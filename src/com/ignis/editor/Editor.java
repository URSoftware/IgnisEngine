package com.ignis.editor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import org.json.JSONObject;
import com.ignis.core.*;
import com.ignis.core.ui.*;

public class Editor extends JFrame {
    private JSplitPane mainSplit;
    private JSplitPane rightSplit;
    private JSplitPane leftSplit; // Split between hierarchy and file browser
    private static final String SETTINGS_FILE = "editor_layout.json";
    private boolean isLoading = false;
    private DefaultListModel<GameObject> hierarchyModel;
    private JList<GameObject> hierarchyList;
    private JPopupMenu hierarchyContextMenu;

    // File browser
    private JTree fileTree;
    private DefaultTreeModel fileTreeModel;
    private File projectRoot;

    // Tool panel buttons
    private JToggleButton moveToolButton;
    private JToggleButton rotateToolButton;
    private JToggleButton scaleToolButton;
    private ButtonGroup toolButtonGroup;

    // Inspector fields
    private JTextField inspectorNameField;
    private JTextField inspectorPosXField;
    private JTextField inspectorPosYField;
    private JTextField inspectorRotationField;
    private JTextField inspectorScaleXField;
    private JTextField inspectorScaleYField;
    private JPanel inspectorPanel;
    private boolean isUpdatingInspector = false;
    private boolean isUserEditingInspector = false; // Flag to prevent updates while user is editing

    // Flag to avoid selection loops
    private boolean isUpdatingSelection = false;

    // Current project
    private Project currentProject;
    private Game game;

    // World control buttons
    private JButton playButton;
    private JButton pauseButton;
    private JButton stopButton;

    // Clipboard for copy/paste objects
    private GameObject clipboardObject = null;

    // Flag indicating if a project was loaded
    private boolean projectLoaded = false;
    
    // ScriptManager to manage scripts
    private ScriptManager scriptManager;
    
    // Timer for updating inspector during gameplay
    private javax.swing.Timer inspectorUpdateTimer;
    
    // Prefab manager
    private PrefabManager prefabManager;
    
    // AI Integration
    private AIIntegration aiIntegration;
    private AuxiliaryPanel auxiliaryPanel;
    
    // ==================== ALERT SYSTEM ====================
    // Fila de mensagens de alerta com timestamps
    private java.util.Queue<AlertMessage> alertQueue = new java.util.LinkedList<>();
    private static final int ALERT_DISPLAY_TIME = 3000; // 3 segundos
    private static final int MAX_ALERTS_ON_SCREEN = 5; // Máximo de alertas simultâneos
    
    private static class AlertMessage {
        String message;
        long createdTime;
        
        AlertMessage(String message) {
            this.message = message;
            this.createdTime = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - createdTime > ALERT_DISPLAY_TIME;
        }
    }
    
    // ==================== UNDO SYSTEM ====================
    // Stack to store undo actions
    private java.util.Deque<UndoAction> undoStack = new java.util.ArrayDeque<>();
    private static final int MAX_UNDO_HISTORY = 50;
    
    /**
     * Represents an undoable action in the editor
     */
    private abstract class UndoAction {
        protected String description;
        public UndoAction(String description) {
            this.description = description;
        }
        public abstract void undo();
        public String getDescription() { return description; }
    }
    
    /**
     * Undo action for object transformation changes (position, size, rotation)
     */
    private class TransformUndoAction extends UndoAction {
        private GameObject object;
        private double oldX, oldY, oldRotation;
        private int oldWidth, oldHeight;
        
        public TransformUndoAction(GameObject obj, double x, double y, double rotation, int width, int height) {
            super("Transform " + obj.getName());
            this.object = obj;
            this.oldX = x;
            this.oldY = y;
            this.oldRotation = rotation;
            this.oldWidth = width;
            this.oldHeight = height;
        }
        
        @Override
        public void undo() {
            object.setX(oldX);
            object.setY(oldY);
            object.setRotation(oldRotation);
            object.setWidth(oldWidth);
            object.setHeight(oldHeight);
            updateInspector(object);
            game.repaint();
        }
    }
    
    /**
     * Undo action for object creation (will delete the object)
     */
    private class CreateObjectUndoAction extends UndoAction {
        private GameObject object;
        
        public CreateObjectUndoAction(GameObject obj) {
            super("Create " + obj.getName());
            this.object = obj;
        }
        
        @Override
        public void undo() {
            game.removeEntity(object);
            updateHierarchy();
            if (game.getSelectedObject() == object) {
                game.setSelectedObject(null);
                updateInspector(null);
            }
            game.repaint();
        }
    }
    
    /**
     * Undo action for object deletion (will restore the object)
     */
    private class DeleteObjectUndoAction extends UndoAction {
        private GameObject object;
        private int index;
        
        public DeleteObjectUndoAction(GameObject obj, int index) {
            super("Delete " + obj.getName());
            this.object = obj;
            this.index = index;
        }
        
        @Override
        public void undo() {
            if (index >= 0 && index <= game.getEntities().size()) {
                game.getEntities().add(index, object);
            } else {
                game.addEntity(object);
            }
            updateHierarchy();
            hierarchyList.setSelectedValue(object, true);
            game.setSelectedObject(object);
            updateInspector(object);
            game.repaint();
        }
    }
    
    /**
     * Undo action for object rename
     */
    private class RenameUndoAction extends UndoAction {
        private GameObject object;
        private String oldName;
        
        public RenameUndoAction(GameObject obj, String oldName) {
            super("Rename " + oldName);
            this.object = obj;
            this.oldName = oldName;
        }
        
        @Override
        public void undo() {
            object.setName(oldName);
            updateHierarchy();
            hierarchyList.setSelectedValue(object, true);
            updateInspector(object);
        }
    }
    
    /**
     * Pushes an undo action to the stack
     */
    private void pushUndoAction(UndoAction action) {
        undoStack.push(action);
        // Limit history size
        while (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.removeLast();
        }
    }
    
    /**
     * Performs undo operation
     */
    private void performUndo() {
        if (undoStack.isEmpty()) {
            System.out.println("Nada para desfazer.");
            return;
        }
        
        UndoAction action = undoStack.pop();
        action.undo();
        System.out.println("Desfeito: " + action.getDescription());
    }
    
    /**
     * Saves current transform state for undo (call before making changes)
     */
    private void saveTransformForUndo(GameObject obj) {
        if (obj != null) {
            pushUndoAction(new TransformUndoAction(
                obj, obj.getX(), obj.getY(), obj.getRotation(), 
                obj.getWidth(), obj.getHeight()
            ));
        }
    }
    
    // Estado salvo antes de edição no Inspector
    private double savedX, savedY, savedRotation;
    private int savedWidth, savedHeight;
    private String savedName;
    private GameObject savedObject;
    private boolean hasUnsavedInspectorChanges = false;
    
    /**
     * Salva o estado atual antes de começar a editar no Inspector
     */
    private void saveInspectorStateForUndo() {
        GameObject obj = game.getSelectedObject();
        if (obj != null && !hasUnsavedInspectorChanges) {
            savedObject = obj;
            savedX = obj.getX();
            savedY = obj.getY();
            savedRotation = obj.getRotation();
            savedWidth = obj.getWidth();
            savedHeight = obj.getHeight();
            savedName = obj.getName();
            hasUnsavedInspectorChanges = true;
        }
    }
    
    /**
     * Confirma as mudanças do Inspector e salva para undo
     */
    private void commitInspectorChanges() {
        if (hasUnsavedInspectorChanges && savedObject != null) {
            // Verificar se houve mudança real
            if (savedX != savedObject.getX() || 
                savedY != savedObject.getY() ||
                savedRotation != savedObject.getRotation() ||
                savedWidth != savedObject.getWidth() ||
                savedHeight != savedObject.getHeight() ||
                !savedName.equals(savedObject.getName())) {
                
                // Salvar undo com valores antigos
                pushUndoAction(new TransformUndoAction(
                    savedObject, savedX, savedY, savedRotation, savedWidth, savedHeight
                ));
            }
            hasUnsavedInspectorChanges = false;
        }
    }

    public Editor(Game game) {
        this.game = game;
        this.currentProject = null; // No project initially

        setTitle("IgnisEngine - Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 720);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        // Configurar o icone do app desktop com suporte a multiplas resolucoes
        com.ignis.core.AppIconHelper.setWindowIcon(this);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.DARK_GRAY);

        // ==================== WORLD CONTROL TOOLBAR ====================
        JPanel toolbar = createWorldControlToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // ==================== HIERARCHY PANEL ====================
        JPanel hierarchy = createHierarchyPanel();

        // ==================== FILE BROWSER PANEL ====================
        JPanel fileBrowser = createFileBrowserPanel();

        // Split left panel: Hierarchy on top, File Browser on bottom
        leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, hierarchy, fileBrowser);
        leftSplit.setResizeWeight(0.5);
        leftSplit.setDividerSize(5);
        leftSplit.setContinuousLayout(true);

        // Configure selection listener from Game
        game.addSelectionListener(selected -> {
            // Não atualizar o inspector se estivermos aplicando mudanças
            if (isUpdatingInspector) {
                return;
            }
            
            if (!isUpdatingSelection) {
                isUpdatingSelection = true;
                if (selected != null) {
                    hierarchyList.setSelectedValue(selected, true);
                } else {
                    hierarchyList.clearSelection();
                }
                // Update inspector with selected object (only if not editing)
                if (!isUserEditingInspector) {
                    updateInspector(selected);
                }
                isUpdatingSelection = false;
            }
        });
        
        // Configure transform listener for undo system
        game.setTransformListener(new Game.TransformListener() {
            private double startX, startY, startRotation;
            private int startWidth, startHeight;
            private GameObject transformingObject;
            
            @Override
            public void onTransformStart(GameObject obj, double x, double y, double rotation, int width, int height) {
                transformingObject = obj;
                startX = x;
                startY = y;
                startRotation = rotation;
                startWidth = width;
                startHeight = height;
            }
            
            @Override
            public void onTransformEnd(GameObject obj) {
                if (transformingObject != null && transformingObject == obj) {
                    // Verificar se houve mudança real
                    if (startX != obj.getX() || startY != obj.getY() ||
                        startRotation != obj.getRotation() ||
                        startWidth != obj.getWidth() || startHeight != obj.getHeight()) {
                        
                        // Salvar estado anterior para undo
                        pushUndoAction(new TransformUndoAction(
                            obj, startX, startY, startRotation, startWidth, startHeight
                        ));
                    }
                }
                transformingObject = null;
            }
        });

        // ==================== VIEWPORT WITH TOOL PANEL ====================
        JPanel viewport = new JPanel(new BorderLayout()) {
            @Override
            public void paint(Graphics g) {
                // Fill with game background color before painting children
                g.setColor(Color.GRAY);
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paint(g);
            }
        };
        viewport.setBackground(Color.GRAY);
        viewport.setOpaque(true);
        viewport.setDoubleBuffered(true);

        // Tool panel (floating on left side of viewport)
        JPanel toolPanel = createToolPanel();

        // Layer the tool panel over the game canvas
        JLayeredPane layeredPane = new JLayeredPane() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }

            @Override
            protected void paintComponent(Graphics g) {
                // Don't paint background - let the game canvas handle it
            }
        };
        layeredPane.setLayout(null);
        layeredPane.setOpaque(false); // Transparent - don't paint over game

        // Add game canvas
        game.setBounds(0, 0, 800, 600);
        game.setIgnoreRepaint(false); // Ensure game repaints properly
        layeredPane.add(game, JLayeredPane.DEFAULT_LAYER);

        // Add tool panel
        toolPanel.setBounds(10, 10, 40, 130);
        layeredPane.add(toolPanel, JLayeredPane.PALETTE_LAYER);

        // Handle resize - update bounds immediately
        viewport.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = viewport.getWidth();
                int h = viewport.getHeight();
                game.setBounds(0, 0, w, h);
                layeredPane.setSize(w, h);
                toolPanel.setBounds(10, 10, 40, 130);
                // Force immediate repaint of game
                game.repaint();
            }
        });
        
        // ==================== CAMERA CONTROLS ====================
        // Mouse wheel for zoom
        game.addMouseWheelListener(e -> {
            if (game.getGameState() == Game.GameState.EDITING) {
                Camera cam = game.getMainCamera();
                if (cam != null) {
                    double factor = e.getWheelRotation() < 0 ? 1.15 : 0.85;
                    cam.setZoom(cam.getZoom() * factor);
                    updateCameraLabels();
                    game.repaint();
                }
            }
        });
        
        // Middle mouse button for panning - use game's built-in panning system
        game.setupEditorPanning(this::updateCameraLabels);

        viewport.add(layeredPane, BorderLayout.CENTER);

        // ==================== RIGHT PANEL WITH TABS (INSPECTOR + AUXILIARY) ====================
        JTabbedPane rightTabbedPane = new JTabbedPane();
        rightTabbedPane.setBackground(new Color(45, 45, 45));
        rightTabbedPane.setForeground(Color.WHITE);
        
        // Create Inspector Panel
        inspectorPanel = createInspectorPanel();
        rightTabbedPane.addTab("📋 Inspector", inspectorPanel);
        
        // Initialize AI Integration (after projectRoot is set in current context)
        // Will be properly initialized when project is loaded
        
        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, viewport, rightTabbedPane);
        rightSplit.setResizeWeight(0.75);
        rightSplit.setDividerSize(5);
        rightSplit.setContinuousLayout(false); // Disable to prevent flickering during resize

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightSplit);
        mainSplit.setResizeWeight(0.2);
        mainSplit.setDividerSize(5);
        mainSplit.setContinuousLayout(false); // Disable to prevent flickering during resize

        mainPanel.add(mainSplit, BorderLayout.CENTER);

        setupMenuBar();

        add(mainPanel);
        setVisible(true);
        
        // Passar referência do editor ao game para poder exibir alertas
        game.setEditor(this);

        // Load saved layout after window is visible
        SwingUtilities.invokeLater(() -> {
            loadLayout();
            addDividerListeners();
            
            // Show startup dialog to create or open a project
            showStartupDialog();
        });
    }

    /**
     * Shows the startup dialog to create or open a project
     */
    private void showStartupDialog() {
        System.out.println("[DEBUG] showStartupDialog() called");
        while (!projectLoaded) {
            System.out.println("[DEBUG] Showing option dialog...");
            String[] options = {"New Project", "Open Project", "Exit"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "Welcome to IgnisEngine!\n\nPlease create a new project or open an existing one to continue.",
                    "IgnisEngine - Project Setup",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            System.out.println("[DEBUG] User selected option: " + choice);

            switch (choice) {
                case 0: // New Project
                    System.out.println("[DEBUG] Creating new project...");
                    if (showNewProjectDialog()) {
                        projectLoaded = true;
                        System.out.println("[DEBUG] Project created successfully!");
                    } else {
                        System.out.println("[DEBUG] Project creation cancelled/failed");
                    }
                    break;
                case 1: // Open Project
                    System.out.println("[DEBUG] Opening existing project...");
                    if (showOpenProjectDialog()) {
                        projectLoaded = true;
                        System.out.println("[DEBUG] Project loaded successfully!");
                    } else {
                        System.out.println("[DEBUG] Project loading cancelled/failed");
                    }
                    break;
                case 2: // Exit
                case JOptionPane.CLOSED_OPTION:
                    System.out.println("[DEBUG] Exiting...");
                    dispose();
                    System.exit(0);
                    return;
            }
        }

        // Update hierarchy after project is loaded
        updateHierarchy();
    }

    /**
     * Shows the new project dialog and creates the project
     * @return true if project was created successfully
     */
    private boolean showNewProjectDialog() {
        System.out.println("[DEBUG] showNewProjectDialog() called");
        String projectName = (String) JOptionPane.showInputDialog(
                this,
                "Enter the project name:",
                "New Project",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "MyGame"
        );
        System.out.println("[DEBUG] Project name entered: " + projectName);

        if (projectName != null && !projectName.trim().isEmpty()) {
            projectName = projectName.trim();
            System.out.println("[DEBUG] Project name (trimmed): " + projectName);
            
            // Check if project already exists
            File projectsRoot = IgnisProjectIO.getProjectsRootFolder();
            System.out.println("[DEBUG] Projects root folder: " + projectsRoot);
            File projectFolder = new File(projectsRoot, projectName);
            
            if (projectFolder.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(
                        this,
                        "A project with this name already exists.\nDo you want to overwrite it?",
                        "Project Exists",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (overwrite != JOptionPane.YES_OPTION) {
                    return false;
                }
            }

            // Create new project
            currentProject = IgnisProjectIO.createNew(projectName);
            
            // Clear game entities
            game.clearEntities();
            
            // Create a basic scene with a centered square at world origin
            Scene scene = currentProject.getCurrentScene();
            int squareSize = 100;
            // Center the square at world origin (0,0)
            int centerX = -squareSize / 2;
            int centerY = -squareSize / 2;
            
            Square basicSquare = new Square("Square", game, centerX, centerY, squareSize, squareSize);
            scene.addEntity(basicSquare);
            game.addEntity(basicSquare);
            
            // Create project folder structure and save
            try {
                File projectMainFolder = new File(projectsRoot, projectName);
                if (!projectMainFolder.exists()) {
                    projectMainFolder.mkdirs();
                }
                
                // Create project folder structure
                File projectResourceFolder = new File(projectMainFolder, IgnisProjectIO.PROJECT_FOLDER_NAME);
                IgnisProjectIO.ensureProjectFolderStructure(projectResourceFolder);
                
                // Save project .ignis file
                File ignisFile = new File(projectMainFolder, projectName + ".ignis");
                IgnisProjectIO.save(currentProject, ignisFile);
                
                // Update file browser to project directory
                updateProjectRoot();
                
                // Update title
                setTitle("IgnisEngine - Editor - " + currentProject.getProjectName());
                
                JOptionPane.showMessageDialog(this,
                        "Project '" + projectName + "' created successfully!\n\n" +
                        "Location: " + projectMainFolder.getAbsolutePath(),
                        "Project Created",
                        JOptionPane.INFORMATION_MESSAGE);
                
                return true;
                
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error creating project: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        
        return false;
    }

    /**
     * Shows the open project dialog and loads the project
     * @return true if project was loaded successfully
     */
    private boolean showOpenProjectDialog() {
        // Start in projects folder
        JFileChooser fileChooser = new JFileChooser(IgnisProjectIO.getProjectsRootFolder());
        fileChooser.setDialogTitle("Open Project");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Ignis Project (*.ignis)", "ignis"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            try {
                // Get the project name from file
                String projectName = selectedFile.getName().replace(".ignis", "");
                File projectsRoot = IgnisProjectIO.getProjectsRootFolder();
                
                // Check if file is inside projects folder
                File projectMainFolder = new File(projectsRoot, projectName);
                File projectResourceFolder = new File(projectMainFolder, IgnisProjectIO.PROJECT_FOLDER_NAME);
                
                // If importing from outside, create folder structure in projects
                if (!selectedFile.getParentFile().equals(projectMainFolder)) {
                    // Copy .ignis to projects folder and create structure
                    if (!projectMainFolder.exists()) {
                        projectMainFolder.mkdirs();
                    }
                    
                    // Create folder structure
                    IgnisProjectIO.ensureProjectFolderStructure(projectResourceFolder);
                    
                    // Copy .ignis file to project folder
                    File destIgnisFile = new File(projectMainFolder, projectName + ".ignis");
                    if (!destIgnisFile.equals(selectedFile)) {
                        java.nio.file.Files.copy(
                                selectedFile.toPath(),
                                destIgnisFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        selectedFile = destIgnisFile;
                    }
                } else {
                    // Ensure folder structure exists
                    IgnisProjectIO.ensureProjectFolderStructure(projectResourceFolder);
                }
                
                // Load project
                currentProject = IgnisProjectIO.load(selectedFile, game);

                // Clear and reload entities in Game
                game.clearEntities();
                Scene scene = currentProject.getCurrentScene();
                if (scene != null) {
                    for (GameObject entity : scene.getEntities()) {
                        game.addEntity(entity);
                    }
                }

                // Update file browser to project directory
                updateProjectRoot();

                // Update title
                setTitle("IgnisEngine - Editor - " + currentProject.getProjectName());

                JOptionPane.showMessageDialog(this,
                        "Project '" + currentProject.getProjectName() + "' loaded successfully!",
                        "Project Loaded",
                        JOptionPane.INFORMATION_MESSAGE);
                
                return true;

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error opening project: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        
        return false;
    }

    // ==================== HIERARCHY PANEL ====================

    /**
     * Creates the complete hierarchy panel
     */
    private JPanel createHierarchyPanel() {
        JPanel hierarchy = new JPanel(new BorderLayout());
        hierarchy.setBackground(new Color(45, 45, 45));
        hierarchy.setBorder(BorderFactory.createTitledBorder(null, "Hierarchy", 0, 0, null, Color.WHITE));

        // Model and list
        hierarchyModel = new DefaultListModel<>();
        hierarchyList = new JList<>(hierarchyModel);
        hierarchyList.setBackground(new Color(60, 60, 60));
        hierarchyList.setForeground(Color.WHITE);
        hierarchyList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); // Allow multiple selection with Shift/Ctrl
        hierarchyList.setSelectionBackground(new Color(0, 120, 215));
        hierarchyList.setSelectionForeground(Color.WHITE);

        // Custom renderer for better visualization
        hierarchyList.setCellRenderer(new HierarchyListCellRenderer());
        
        // Enable drag and drop for reordering
        hierarchyList.setDragEnabled(true);
        hierarchyList.setDropMode(DropMode.INSERT);
        hierarchyList.setTransferHandler(new HierarchyTransferHandler());

        // Selection listener - sync with Game
        hierarchyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdatingSelection) {
                isUpdatingSelection = true;
                GameObject selected = hierarchyList.getSelectedValue();
                game.setSelectedObject(selected);
                // Atualizar o inspector com o objeto selecionado
                updateInspector(selected);
                isUpdatingSelection = false;
            }
        });

        // Double click to rename
        hierarchyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = hierarchyList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        renameObject(hierarchyModel.get(index));
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleHierarchyContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleHierarchyContextMenu(e);
            }
        });

        // Keyboard shortcuts
        hierarchyList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleHierarchyKeyPress(e);
            }
        });

        JScrollPane hierarchyScroll = new JScrollPane(hierarchyList);
        hierarchyScroll.setBorder(null);
        hierarchyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        hierarchyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Customizar scrollbar
        hierarchyScroll.getVerticalScrollBar().setBackground(new Color(50, 50, 50));
        hierarchyScroll.getHorizontalScrollBar().setBackground(new Color(50, 50, 50));

        hierarchy.add(hierarchyScroll, BorderLayout.CENTER);

        // Create context menu
        createHierarchyContextMenu();

        return hierarchy;
    }

    // ==================== TOOL PANEL ====================

    /**
     * Creates the floating tool panel with Move, Rotate, Scale buttons
     */
    private JPanel createToolPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(60, 60, 60, 220));
        panel.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40), 1));
        panel.setOpaque(true);

        toolButtonGroup = new ButtonGroup();

        // Move tool button
        moveToolButton = createToolButton("move", "Move Tool (W)", true);
        moveToolButton.addActionListener(e -> {
            game.setCurrentTool(Game.ToolType.MOVE);
        });

        // Rotate tool button
        rotateToolButton = createToolButton("rotate", "Rotate Tool (E)", false);
        rotateToolButton.addActionListener(e -> {
            game.setCurrentTool(Game.ToolType.ROTATE);
        });

        // Scale tool button
        scaleToolButton = createToolButton("scale", "Scale Tool (R)", false);
        scaleToolButton.addActionListener(e -> {
            game.setCurrentTool(Game.ToolType.SCALE);
        });

        toolButtonGroup.add(moveToolButton);
        toolButtonGroup.add(rotateToolButton);
        toolButtonGroup.add(scaleToolButton);

        panel.add(moveToolButton);
        panel.add(Box.createVerticalStrut(2));
        panel.add(rotateToolButton);
        panel.add(Box.createVerticalStrut(2));
        panel.add(scaleToolButton);

        // Keyboard shortcuts (only when not playing)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            // Desabilitar atalhos quando o jogo estiver rodando
            if (game.isWorldRunning()) {
                return false;
            }
            
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                // CTRL+Z - Undo (funciona sempre, não só na hierarquia)
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z) {
                    performUndo();
                    return true;
                }
                
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W:
                        moveToolButton.doClick();
                        return true;
                    case KeyEvent.VK_E:
                        rotateToolButton.doClick();
                        return true;
                    case KeyEvent.VK_R:
                        scaleToolButton.doClick();
                        return true;
                }
            }
            return false;
        });

        return panel;
    }

    /**
     * Creates a styled tool button with custom painted icon
     */
    private JToggleButton createToolButton(String iconType, String tooltip, boolean selected) {
        JToggleButton button = new JToggleButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getForeground());
                g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int size = 10;

                switch (iconType) {
                    case "move":
                        // Desenha setas verticais e horizontais (cruz de movimento)
                        // Seta para cima
                        g2d.drawLine(cx, cy - size, cx, cy + size);
                        g2d.drawLine(cx - 4, cy - size + 4, cx, cy - size);
                        g2d.drawLine(cx + 4, cy - size + 4, cx, cy - size);
                        // Seta para baixo
                        g2d.drawLine(cx - 4, cy + size - 4, cx, cy + size);
                        g2d.drawLine(cx + 4, cy + size - 4, cx, cy + size);
                        // Seta para esquerda
                        g2d.drawLine(cx - size, cy, cx + size, cy);
                        g2d.drawLine(cx - size + 4, cy - 4, cx - size, cy);
                        g2d.drawLine(cx - size + 4, cy + 4, cx - size, cy);
                        // Seta para direita
                        g2d.drawLine(cx + size - 4, cy - 4, cx + size, cy);
                        g2d.drawLine(cx + size - 4, cy + 4, cx + size, cy);
                        break;
                    case "rotate":
                        // Desenha arco com seta (rotação)
                        g2d.drawArc(cx - size, cy - size, size * 2, size * 2, 45, 270);
                        // Seta no final do arco
                        int ax = cx + (int) (size * Math.cos(Math.toRadians(45)));
                        int ay = cy - (int) (size * Math.sin(Math.toRadians(45)));
                        g2d.drawLine(ax, ay, ax + 4, ay);
                        g2d.drawLine(ax, ay, ax, ay + 4);
                        break;
                    case "scale":
                        // Desenha setas diagonais (escala)
                        g2d.drawLine(cx - size, cy - size, cx + size, cy + size);
                        g2d.drawLine(cx - size, cy - size, cx - size + 5, cy - size);
                        g2d.drawLine(cx - size, cy - size, cx - size, cy - size + 5);
                        g2d.drawLine(cx + size, cy + size, cx + size - 5, cy + size);
                        g2d.drawLine(cx + size, cy + size, cx + size, cy + size - 5);
                        break;
                }
                g2d.dispose();
            }
        };
        button.setPreferredSize(new Dimension(36, 36));
        button.setMaximumSize(new Dimension(36, 36));
        button.setMinimumSize(new Dimension(36, 36));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setBackground(selected ? new Color(0, 120, 215) : new Color(70, 70, 70));
        button.setForeground(Color.WHITE);
        button.setSelected(selected);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addChangeListener(e -> {
            if (button.isSelected()) {
                button.setBackground(new Color(0, 120, 215));
            } else {
                button.setBackground(new Color(70, 70, 70));
            }
        });

        return button;
    }
    
    /**
     * Creates a styled remove button with custom painted X icon
     */
    private JButton createRemoveScriptButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int size = 5;

                // Desenha um X
                g2d.drawLine(cx - size, cy - size, cx + size, cy + size);
                g2d.drawLine(cx + size, cy - size, cx - size, cy + size);
                
                g2d.dispose();
            }
        };
        button.setPreferredSize(new Dimension(20, 20));
        button.setMaximumSize(new Dimension(20, 20));
        button.setMinimumSize(new Dimension(20, 20));
        button.setToolTipText("Remove Script");
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setBackground(new Color(180, 60, 60));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(220, 80, 80));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(180, 60, 60));
            }
        });
        
        return button;
    }

    /**
     * Functional interface for custom icon painting
     */
    @FunctionalInterface
    interface IconPainter {
        void paint(Graphics2D g, int width, int height);
    }

    /**
     * Creates a button with a custom-drawn icon
     * @param size Button dimensions
     * @param tooltip Tooltip text
     * @param bgColor Background color
     * @param hoverColor Hover color
     * @param painter The icon painter lambda
     * @return Styled button with custom icon
     */
    private JButton createIconButton(Dimension size, String tooltip, Color bgColor, Color hoverColor, IconPainter painter) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw background
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                
                // Draw custom icon
                painter.paint(g2d, getWidth(), getHeight());
                
                g2d.dispose();
            }
        };
        
        button.setPreferredSize(size);
        button.setMaximumSize(size);
        button.setMinimumSize(size);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setToolTipText(tooltip);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverColor);
                button.repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
                button.repaint();
            }
        });
        
        return button;
    }

    // ==================== INSPECTOR PANEL ====================

    /**
     * Creates the inspector panel for viewing/editing selected object properties
     */
    private JPanel createInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));
        panel.setBorder(BorderFactory.createTitledBorder(null, "Inspector", 0, 0, null, Color.WHITE));
        panel.setMinimumSize(new Dimension(150, 0));
        panel.setPreferredSize(new Dimension(200, 400));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(45, 45, 45));
        content.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        
        // FocusAdapter reutilizável que salva estado para undo
        FocusAdapter inspectorFocusAdapter = new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                isUserEditingInspector = true;
                saveInspectorStateForUndo();
            }

            @Override
            public void focusLost(FocusEvent e) {
                applyInspectorChanges();
                commitInspectorChanges();
                isUserEditingInspector = false;
            }
        };

        // Name field
        content.add(createInspectorLabel("Name"));
        inspectorNameField = createInspectorTextField();
        inspectorNameField.addActionListener(e -> {
            applyInspectorChanges();
            commitInspectorChanges();
            isUserEditingInspector = false;
        });
        inspectorNameField.addFocusListener(inspectorFocusAdapter);
        content.add(wrapFieldFullWidth(inspectorNameField));
        content.add(Box.createVerticalStrut(10));
        
        // Visibility toggle
        JPanel visibilityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        visibilityPanel.setBackground(new Color(45, 45, 45));
        visibilityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        visibilityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        
        JCheckBox visibilityCheckbox = new JCheckBox("Visible");
        visibilityCheckbox.setName("visibilityCheckbox");
        visibilityCheckbox.setBackground(new Color(45, 45, 45));
        visibilityCheckbox.setForeground(Color.WHITE);
        visibilityCheckbox.setSelected(true);
        visibilityCheckbox.addActionListener(e -> {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null) {
                selected.setVisible(visibilityCheckbox.isSelected());
                game.repaint();
            }
        });
        visibilityPanel.add(visibilityCheckbox);
        content.add(visibilityPanel);
        content.add(Box.createVerticalStrut(15));
        
        // ==================== APPEARANCE SECTION ====================
        content.add(createInspectorSectionHeader("Appearance"));
        content.add(Box.createVerticalStrut(8));
        
        // Shape Color (only for primitives - hidden by default)
        JPanel shapeColorSection = new JPanel();
        shapeColorSection.setLayout(new BoxLayout(shapeColorSection, BoxLayout.Y_AXIS));
        shapeColorSection.setBackground(new Color(45, 45, 45));
        shapeColorSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        shapeColorSection.setName("shapeColorSection");
        shapeColorSection.setVisible(false);
        
        content.add(createInspectorLabel("Shape Color"));
        JPanel shapeColorPanel = createColorPickerPanel("shapeColorButton", color -> {
            GameObject selected = game.getSelectedObject();
            if (selected != null) {
                setShapeColor(selected, color);
                game.repaint();
            }
        });
        shapeColorSection.add(shapeColorPanel);
        content.add(shapeColorSection);
        content.add(Box.createVerticalStrut(8));
        
        // Name Color (for hierarchy display)
        content.add(createInspectorLabel("Name Color"));
        JPanel nameColorPanel = createColorPickerPanel("nameColorButton", color -> {
            GameObject selected = game.getSelectedObject();
            if (selected != null) {
                selected.setNameColor(color);
                updateHierarchy();
            }
        });
        content.add(nameColorPanel);
        content.add(Box.createVerticalStrut(15));

        // Transform section header
        content.add(createInspectorSectionHeader("Transform"));
        content.add(Box.createVerticalStrut(8));

        // Position X
        content.add(createInspectorLabel("Position X"));
        inspectorPosXField = createInspectorTextField();
        inspectorPosXField.addActionListener(e -> {
            applyInspectorChanges();
            commitInspectorChanges();
            isUserEditingInspector = false;
        });
        inspectorPosXField.addFocusListener(inspectorFocusAdapter);
        content.add(wrapFieldFullWidth(inspectorPosXField));
        content.add(Box.createVerticalStrut(5));

        // Position Y
        content.add(createInspectorLabel("Position Y"));
        inspectorPosYField = createInspectorTextField();
        inspectorPosYField.addActionListener(e -> {
            applyInspectorChanges();
            commitInspectorChanges();
            isUserEditingInspector = false;
        });
        inspectorPosYField.addFocusListener(inspectorFocusAdapter);
        content.add(wrapFieldFullWidth(inspectorPosYField));
        content.add(Box.createVerticalStrut(10));

        // Rotation
        content.add(createInspectorLabel("Rotation"));
        inspectorRotationField = createInspectorTextField();
        inspectorRotationField.addActionListener(e -> {
            applyInspectorChanges();
            commitInspectorChanges();
            isUserEditingInspector = false;
        });
        inspectorRotationField.addFocusListener(inspectorFocusAdapter);
        content.add(wrapFieldFullWidth(inspectorRotationField));
        content.add(Box.createVerticalStrut(15));

        // Scale section header
        content.add(createInspectorSectionHeader("Scale"));
        content.add(Box.createVerticalStrut(8));

        // Width
        content.add(createInspectorLabel("Width"));
        inspectorScaleXField = createInspectorTextField();
        inspectorScaleXField.addActionListener(e -> {
            applyInspectorChanges();
            commitInspectorChanges();
            isUserEditingInspector = false;
        });
        inspectorScaleXField.addFocusListener(inspectorFocusAdapter);
        content.add(wrapFieldFullWidth(inspectorScaleXField));
        content.add(Box.createVerticalStrut(5));

        // Height
        content.add(createInspectorLabel("Height"));
        inspectorScaleYField = createInspectorTextField();
        inspectorScaleYField.addActionListener(e -> {
            applyInspectorChanges();
            commitInspectorChanges();
            isUserEditingInspector = false;
        });
        inspectorScaleYField.addFocusListener(inspectorFocusAdapter);
        content.add(wrapFieldFullWidth(inspectorScaleYField));
        
        // ==================== CAMERA SECTION (shown only for Camera objects) ====================
        content.add(Box.createVerticalStrut(15));
        
        JPanel cameraSection = new JPanel();
        cameraSection.setLayout(new BoxLayout(cameraSection, BoxLayout.Y_AXIS));
        cameraSection.setBackground(new Color(45, 45, 45));
        cameraSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        cameraSection.setName("cameraSection");
        cameraSection.setVisible(false); // Hidden by default
        
        cameraSection.add(createInspectorSectionHeader("Camera"));
        cameraSection.add(Box.createVerticalStrut(8));
        
        // Zoom field
        JLabel zoomLabel = createInspectorLabel("Zoom");
        cameraSection.add(zoomLabel);
        JTextField cameraZoomField = createInspectorTextField();
        cameraZoomField.setName("cameraZoomField");
        cameraZoomField.addActionListener(e -> {
            applyCameraInspectorChanges();
            isUserEditingInspector = false;
        });
        cameraZoomField.addFocusListener(inspectorFocusAdapter);
        cameraSection.add(wrapFieldFullWidth(cameraZoomField));
        cameraSection.add(Box.createVerticalStrut(5));
        
        // Active camera checkbox
        JPanel activePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        activePanel.setBackground(new Color(45, 45, 45));
        activePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        activePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        
        JCheckBox activeCameraCheckbox = new JCheckBox("Active Camera");
        activeCameraCheckbox.setName("activeCameraCheckbox");
        activeCameraCheckbox.setBackground(new Color(45, 45, 45));
        activeCameraCheckbox.setForeground(Color.WHITE);
        activeCameraCheckbox.addActionListener(e -> {
            GameObject selected = game.getSelectedObject();
            if (selected instanceof Camera) {
                Camera cam = (Camera) selected;
                cam.setActive(activeCameraCheckbox.isSelected());
                if (cam.isActiveCamera()) {
                    game.setMainCamera(cam);
                }
                game.repaint();
            }
        });
        activePanel.add(activeCameraCheckbox);
        cameraSection.add(activePanel);
        cameraSection.add(Box.createVerticalStrut(5));
        
        // Set as Main Camera button
        JButton setMainCameraBtn = new JButton("Set as Main Camera");
        setMainCameraBtn.setBackground(new Color(80, 120, 180));
        setMainCameraBtn.setForeground(Color.WHITE);
        setMainCameraBtn.setFocusPainted(false);
        setMainCameraBtn.setBorderPainted(false);
        setMainCameraBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        setMainCameraBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        setMainCameraBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        setMainCameraBtn.addActionListener(e -> {
            GameObject selected = game.getSelectedObject();
            if (selected instanceof Camera) {
                game.setMainCamera((Camera) selected);
                updateInspector(selected);
                game.repaint();
            }
        });
        
        JPanel setMainCameraWrapper = new JPanel(new BorderLayout());
        setMainCameraWrapper.setBackground(new Color(45, 45, 45));
        setMainCameraWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        setMainCameraWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        setMainCameraWrapper.add(setMainCameraBtn, BorderLayout.CENTER);
        cameraSection.add(setMainCameraWrapper);
        
        content.add(cameraSection);
        
        // ==================== COLLIDER SECTION ====================
        content.add(Box.createVerticalStrut(15));
        content.add(createInspectorSectionHeader("Collider"));
        content.add(Box.createVerticalStrut(8));
        
        // Collider panel container
        JPanel colliderSection = new JPanel();
        colliderSection.setLayout(new BoxLayout(colliderSection, BoxLayout.Y_AXIS));
        colliderSection.setBackground(new Color(45, 45, 45));
        colliderSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        colliderSection.setName("colliderSection");
        
        // Collider type dropdown
        content.add(createInspectorLabel("Collider Type"));
        String[] colliderTypes = {"None", "AABB (Box)", "Circle", "Polygon"};
        JComboBox<String> colliderTypeCombo = new JComboBox<>(colliderTypes);
        colliderTypeCombo.setName("colliderTypeCombo");
        colliderTypeCombo.setBackground(new Color(60, 60, 60));
        colliderTypeCombo.setForeground(Color.WHITE);
        colliderTypeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        colliderTypeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        colliderTypeCombo.addActionListener(e -> {
            GameObject selected = game.getSelectedObject();
            if (selected != null && !isUpdatingInspector) {
                int index = colliderTypeCombo.getSelectedIndex();
                IgnisSampleCollisions.ColliderType type;
                switch (index) {
                    case 1: type = IgnisSampleCollisions.ColliderType.AABB; break;
                    case 2: type = IgnisSampleCollisions.ColliderType.CIRCLE; break;
                    case 3: type = IgnisSampleCollisions.ColliderType.POLYGON; break;
                    default: type = IgnisSampleCollisions.ColliderType.NONE; break;
                }
                selected.setColliderType(type);
                updateInspectorCollider(selected);
                game.repaint();
            }
        });
        JPanel colliderTypeWrapper = new JPanel(new BorderLayout());
        colliderTypeWrapper.setBackground(new Color(45, 45, 45));
        colliderTypeWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        colliderTypeWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        colliderTypeWrapper.add(colliderTypeCombo, BorderLayout.CENTER);
        content.add(colliderTypeWrapper);
        content.add(Box.createVerticalStrut(5));
        
        // Collision mode dropdown
        content.add(createInspectorLabel("Collision Mode"));
        String[] collisionModes = {"Collision (Physical)", "Trigger (Event Only)"};
        JComboBox<String> collisionModeCombo = new JComboBox<>(collisionModes);
        collisionModeCombo.setName("collisionModeCombo");
        collisionModeCombo.setBackground(new Color(60, 60, 60));
        collisionModeCombo.setForeground(Color.WHITE);
        collisionModeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        collisionModeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        collisionModeCombo.addActionListener(e -> {
            GameObject selected = game.getSelectedObject();
            if (selected != null && !isUpdatingInspector) {
                IgnisSampleCollisions.CollisionMode mode = 
                    collisionModeCombo.getSelectedIndex() == 0 ? 
                    IgnisSampleCollisions.CollisionMode.COLLISION : 
                    IgnisSampleCollisions.CollisionMode.TRIGGER;
                selected.setCollisionMode(mode);
                game.repaint();
            }
        });
        JPanel collisionModeWrapper = new JPanel(new BorderLayout());
        collisionModeWrapper.setBackground(new Color(45, 45, 45));
        collisionModeWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        collisionModeWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        collisionModeWrapper.add(collisionModeCombo, BorderLayout.CENTER);
        content.add(collisionModeWrapper);
        content.add(Box.createVerticalStrut(5));
        
        // CCD checkbox
        JCheckBox ccdCheckbox = new JCheckBox("Use Continuous Collision (CCD)");
        ccdCheckbox.setName("ccdCheckbox");
        ccdCheckbox.setBackground(new Color(45, 45, 45));
        ccdCheckbox.setForeground(Color.WHITE);
        ccdCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        ccdCheckbox.setToolTipText("Enable for fast-moving objects like projectiles");
        ccdCheckbox.addActionListener(e -> {
            GameObject selected = game.getSelectedObject();
            if (selected != null && !isUpdatingInspector) {
                selected.setUseCCD(ccdCheckbox.isSelected());
            }
        });
        content.add(ccdCheckbox);
        content.add(Box.createVerticalStrut(5));
        
        // Collider properties panel (updates dynamically based on type)
        JPanel colliderPropsPanel = new JPanel();
        colliderPropsPanel.setLayout(new BoxLayout(colliderPropsPanel, BoxLayout.Y_AXIS));
        colliderPropsPanel.setBackground(new Color(45, 45, 45));
        colliderPropsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        colliderPropsPanel.setName("colliderPropsPanel");
        content.add(colliderPropsPanel);
        
        content.add(colliderSection);
        
        // Scripts section
        content.add(Box.createVerticalStrut(15));
        content.add(createInspectorSectionHeader("Scripts"));
        content.add(Box.createVerticalStrut(8));
        
        // Scripts list panel (will be updated dynamically)
        JPanel scriptsListPanel = new JPanel();
        scriptsListPanel.setLayout(new BoxLayout(scriptsListPanel, BoxLayout.Y_AXIS));
        scriptsListPanel.setBackground(new Color(45, 45, 45));
        scriptsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        scriptsListPanel.setName("scriptsListPanel");
        content.add(scriptsListPanel);
        
        // Button to add script
        JButton addScriptButton = new JButton("+ Add Script");
        addScriptButton.setBackground(new Color(70, 130, 70));
        addScriptButton.setForeground(Color.WHITE);
        addScriptButton.setFocusPainted(false);
        addScriptButton.setBorderPainted(false);
        addScriptButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        addScriptButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        addScriptButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        addScriptButton.addActionListener(e -> showAddScriptDialog());
        
        JPanel addScriptWrapper = new JPanel(new BorderLayout());
        addScriptWrapper.setBackground(new Color(45, 45, 45));
        addScriptWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        addScriptWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        addScriptWrapper.add(addScriptButton, BorderLayout.CENTER);
        content.add(Box.createVerticalStrut(5));
        content.add(addScriptWrapper);
        
        // Sprite section
        content.add(Box.createVerticalStrut(15));
        content.add(createInspectorSectionHeader("Sprite"));
        content.add(Box.createVerticalStrut(8));
        
        // Sprite panel (will be updated dynamically)
        JPanel spritePanel = new JPanel();
        spritePanel.setLayout(new BoxLayout(spritePanel, BoxLayout.Y_AXIS));
        spritePanel.setBackground(new Color(45, 45, 45));
        spritePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        spritePanel.setName("spritePanel");
        content.add(spritePanel);
        
        // Button to import image
        JButton importImageButton = new JButton("🖼 Import Image");
        importImageButton.setBackground(new Color(100, 100, 150));
        importImageButton.setForeground(Color.WHITE);
        importImageButton.setFocusPainted(false);
        importImageButton.setBorderPainted(false);
        importImageButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        importImageButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        importImageButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        importImageButton.addActionListener(e -> importImageForSelectedObject());
        
        JPanel importImageWrapper = new JPanel(new BorderLayout());
        importImageWrapper.setBackground(new Color(45, 45, 45));
        importImageWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        importImageWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        importImageWrapper.add(importImageButton, BorderLayout.CENTER);
        content.add(Box.createVerticalStrut(5));
        content.add(importImageWrapper);
        
        // Add glue to push everything to top
        content.add(Box.createVerticalGlue());

        // Scroll pane for content
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(new Color(45, 45, 45));

        panel.add(scrollPane, BorderLayout.CENTER);

        // Initially disable fields
        setInspectorEnabled(false);

        return panel;
    }

    /**
     * Creates a section header label for inspector
     */
    private JLabel createInspectorSectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(150, 200, 255));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Creates a field label for inspector
     */
    private JLabel createInspectorLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.LIGHT_GRAY);
        label.setFont(label.getFont().deriveFont(10f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Wraps a text field in a panel that takes full width
     */
    private JPanel wrapFieldFullWidth(JTextField field) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(new Color(45, 45, 45));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(field, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Creates a styled text field for inspector
     */
    private JTextField createInspectorTextField() {
        JTextField field = new JTextField();
        field.setBackground(new Color(60, 60, 60));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        field.setPreferredSize(new Dimension(0, 26));
        return field;
    }

    /**
     * Enables or disables inspector fields
     */
    private void setInspectorEnabled(boolean enabled) {
        inspectorNameField.setEnabled(enabled);
        inspectorPosXField.setEnabled(enabled);
        inspectorPosYField.setEnabled(enabled);
        inspectorRotationField.setEnabled(enabled);
        inspectorScaleXField.setEnabled(enabled);
        inspectorScaleYField.setEnabled(enabled);

        Color bgColor = enabled ? new Color(60, 60, 60) : new Color(50, 50, 50);
        inspectorNameField.setBackground(bgColor);
        inspectorPosXField.setBackground(bgColor);
        inspectorPosYField.setBackground(bgColor);
        inspectorRotationField.setBackground(bgColor);
        inspectorScaleXField.setBackground(bgColor);
        inspectorScaleYField.setBackground(bgColor);
    }

    /**
     * Updates the inspector with the selected object's properties
     */
    private void updateInspector(GameObject obj) {
        // Don't update if user is currently editing a field
        if (isUserEditingInspector) {
            return;
        }

        isUpdatingInspector = true;

        // Update visibility checkbox
        JCheckBox visibilityCheckbox = (JCheckBox) findComponentByName(inspectorPanel, "visibilityCheckbox");
        
        if (obj != null) {
            setInspectorEnabled(true);
            inspectorNameField.setText(obj.getName());
            inspectorPosXField.setText(String.format("%.1f", obj.getX()));
            inspectorPosYField.setText(String.format("%.1f", obj.getY()));
            inspectorRotationField.setText(String.format("%.1f", obj.getRotation()));
            inspectorScaleXField.setText(String.valueOf(obj.getWidth()));
            inspectorScaleYField.setText(String.valueOf(obj.getHeight()));
            
            // Update visibility checkbox
            if (visibilityCheckbox != null) {
                visibilityCheckbox.setSelected(obj.isVisible());
            }
            
            // Update appearance (colors)
            updateInspectorAppearance(obj);
            
            // Atualizar seção de collider
            updateInspectorCollider(obj);
            
            // Atualizar lista de scripts
            updateInspectorScripts(obj);
            
            // Atualizar seção de sprite
            updateInspectorSprite(obj);

            // Update camera section visibility and values
            updateInspectorCameraSection(obj);
        } else {
            setInspectorEnabled(false);
            inspectorNameField.setText("");
            inspectorPosXField.setText("");
            inspectorPosYField.setText("");
            inspectorRotationField.setText("");
            inspectorScaleXField.setText("");
            inspectorScaleYField.setText("");
            
            // Reset visibility checkbox
            if (visibilityCheckbox != null) {
                visibilityCheckbox.setSelected(true);
            }
            
            // Reset appearance
            updateInspectorAppearance(null);
            
            // Reset collider section
            updateInspectorCollider(null);
            
            // Limpar lista de scripts
            updateInspectorScripts(null);
            
            // Limpar seção de sprite
            updateInspectorSprite(null);

            // Hide camera section
            updateInspectorCameraSection(null);
        }

        isUpdatingInspector = false;
    }

    /**
     * Applies changes from inspector fields to the selected object
     */
    private void applyInspectorChanges() {
        // Skip if we're in the middle of updating inspector from selection
        if (isUpdatingInspector) {
            return;
        }

        GameObject obj = game.getSelectedObject();
        if (obj == null) {
            return;
        }

        try {
            // Apply name change
            String name = inspectorNameField.getText().trim();
            if (!name.isEmpty() && !name.equals(obj.getName())) {
                obj.setName(name);
            }

            // Parse numeric values
            String posXText = inspectorPosXField.getText().trim();
            String posYText = inspectorPosYField.getText().trim();
            String rotText = inspectorRotationField.getText().trim();
            String widthText = inspectorScaleXField.getText().trim();
            String heightText = inspectorScaleYField.getText().trim();

            // Apply position
            if (!posXText.isEmpty()) {
                double x = Double.parseDouble(posXText);
                obj.setX(x);
            }
            if (!posYText.isEmpty()) {
                double y = Double.parseDouble(posYText);
                obj.setY(y);
            }

            // Apply rotation
            if (!rotText.isEmpty()) {
                double rotation = Double.parseDouble(rotText);
                obj.setRotation(rotation);
            }

            // Apply scale (width/height)
            if (!widthText.isEmpty()) {
                int width = Integer.parseInt(widthText);
                obj.setWidth(Math.max(1, width));
            }
            if (!heightText.isEmpty()) {
                int height = Integer.parseInt(heightText);
                obj.setHeight(Math.max(1, height));
            }

            // Update hierarchy list in case name changed
            updateHierarchy();

            // Repaint game to show changes immediately
            game.repaint();

        } catch (NumberFormatException ex) {
            // Invalid input, revert to current values
            updateInspector(obj);
        }
    }
    
    /**
     * Creates a color picker panel with a color preview button and preset colors palette
     */
    private JPanel createColorPickerPanel(String buttonName, java.util.function.Consumer<Color> onColorChange) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBackground(new Color(45, 45, 45));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Color preview button
        JButton colorButton = new JButton();
        colorButton.setName(buttonName);
        colorButton.setBackground(Color.WHITE);
        colorButton.setPreferredSize(new Dimension(40, 22));
        colorButton.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        colorButton.setFocusPainted(false);
        colorButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        colorButton.setToolTipText("Click to pick custom color");
        
        colorButton.addActionListener(e -> {
            Color currentColor = colorButton.getBackground();
            Color newColor = JColorChooser.showDialog(this, "Choose Color", currentColor);
            if (newColor != null) {
                colorButton.setBackground(newColor);
                onColorChange.accept(newColor);
            }
        });
        
        // Preset colors palette
        JPanel palettePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        palettePanel.setBackground(new Color(45, 45, 45));
        
        Color[] presetColors = {
            Color.WHITE, Color.BLACK, Color.GRAY, Color.LIGHT_GRAY,
            new Color(255, 80, 80), new Color(255, 150, 80), new Color(255, 255, 80),
            new Color(80, 255, 80), new Color(80, 255, 255), new Color(80, 80, 255),
            new Color(255, 80, 255), new Color(139, 69, 19), new Color(255, 192, 203),
            new Color(100, 150, 255), new Color(150, 255, 100), new Color(255, 150, 100)
        };
        
        for (Color preset : presetColors) {
            JButton presetBtn = new JButton();
            presetBtn.setBackground(preset);
            presetBtn.setPreferredSize(new Dimension(14, 14));
            presetBtn.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
            presetBtn.setFocusPainted(false);
            presetBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            presetBtn.addActionListener(e -> {
                colorButton.setBackground(preset);
                onColorChange.accept(preset);
            });
            palettePanel.add(presetBtn);
        }
        
        panel.add(colorButton, BorderLayout.WEST);
        panel.add(palettePanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Sets the color of a primitive shape object
     */
    private void setShapeColor(GameObject obj, Color color) {
        try {
            java.lang.reflect.Method setColor = obj.getClass().getMethod("setColor", Color.class);
            setColor.invoke(obj, color);
        } catch (Exception e) {
            // Object doesn't support setColor - ignore
        }
    }
    
    /**
     * Gets the color of a primitive shape object
     */
    private Color getShapeColor(GameObject obj) {
        try {
            java.lang.reflect.Method getColor = obj.getClass().getMethod("getColor");
            return (Color) getColor.invoke(obj);
        } catch (Exception e) {
            return null; // Object doesn't support getColor
        }
    }
    
    /**
     * Checks if object is a primitive shape that supports color
     */
    private boolean isPrimitiveShape(GameObject obj) {
        if (obj == null) return false;
        String type = obj.getType();
        return type.equals("Square") || type.equals("Circle") || type.equals("Triangle") || 
               type.equals("Star") || type.equals("Pentagon");
    }
    
    /**
     * Updates the appearance section (colors) in the inspector
     */
    private void updateInspectorAppearance(GameObject obj) {
        JPanel shapeColorSection = findComponentByName(inspectorPanel, "shapeColorSection");
        JButton shapeColorButton = findComponentByName(inspectorPanel, "shapeColorButton");
        JButton nameColorButton = findComponentByName(inspectorPanel, "nameColorButton");
        
        if (obj != null) {
            // Update shape color section visibility and value
            if (shapeColorSection != null && shapeColorButton != null) {
                if (isPrimitiveShape(obj)) {
                    shapeColorSection.setVisible(true);
                    Color shapeColor = getShapeColor(obj);
                    if (shapeColor != null) {
                        shapeColorButton.setBackground(shapeColor);
                    }
                } else {
                    shapeColorSection.setVisible(false);
                }
            }
            
            // Update name color button
            if (nameColorButton != null) {
                nameColorButton.setBackground(obj.getNameColor());
            }
        } else {
            if (shapeColorSection != null) {
                shapeColorSection.setVisible(false);
            }
            if (nameColorButton != null) {
                nameColorButton.setBackground(Color.WHITE);
            }
        }
    }
    
    /**
     * Updates the collider section in the Inspector
     */
    @SuppressWarnings("unchecked")
    private void updateInspectorCollider(GameObject obj) {
        JComboBox<String> colliderTypeCombo = findComponentByName(inspectorPanel, "colliderTypeCombo");
        JComboBox<String> collisionModeCombo = findComponentByName(inspectorPanel, "collisionModeCombo");
        JCheckBox ccdCheckbox = findComponentByName(inspectorPanel, "ccdCheckbox");
        JPanel colliderPropsPanel = findComponentByName(inspectorPanel, "colliderPropsPanel");
        
        if (colliderTypeCombo == null || collisionModeCombo == null) return;
        
        if (obj != null) {
            // Update collider type dropdown
            switch (obj.getColliderType()) {
                case AABB: colliderTypeCombo.setSelectedIndex(1); break;
                case CIRCLE: colliderTypeCombo.setSelectedIndex(2); break;
                case POLYGON: colliderTypeCombo.setSelectedIndex(3); break;
                default: colliderTypeCombo.setSelectedIndex(0); break;
            }
            
            // Update collision mode dropdown
            if (obj.getCollisionMode() == IgnisSampleCollisions.CollisionMode.TRIGGER) {
                collisionModeCombo.setSelectedIndex(1);
            } else {
                collisionModeCombo.setSelectedIndex(0);
            }
            
            // Update CCD checkbox
            if (ccdCheckbox != null) {
                IgnisSampleCollisions.Collider collider = obj.getCollider();
                ccdCheckbox.setSelected(collider != null && collider.useCCD());
            }
            
            // Update collider properties panel based on type
            if (colliderPropsPanel != null) {
                colliderPropsPanel.removeAll();
                
                IgnisSampleCollisions.Collider collider = obj.getCollider();
                if (collider != null) {
                    if (collider instanceof IgnisSampleCollisions.AABBCollider) {
                        IgnisSampleCollisions.AABBCollider aabb = (IgnisSampleCollisions.AABBCollider) collider;
                        // Width and Height fields for AABB
                        colliderPropsPanel.add(createColliderPropertyField("Width", 
                            String.valueOf((int)aabb.getWidth()), 
                            value -> aabb.setSize(Double.parseDouble(value), aabb.getHeight())));
                        colliderPropsPanel.add(Box.createVerticalStrut(3));
                        colliderPropsPanel.add(createColliderPropertyField("Height", 
                            String.valueOf((int)aabb.getHeight()), 
                            value -> aabb.setSize(aabb.getWidth(), Double.parseDouble(value))));
                    } else if (collider instanceof IgnisSampleCollisions.CircleCollider) {
                        IgnisSampleCollisions.CircleCollider circle = (IgnisSampleCollisions.CircleCollider) collider;
                        // Radius field for Circle
                        colliderPropsPanel.add(createColliderPropertyField("Radius", 
                            String.valueOf((int)circle.getRadius()), 
                            value -> circle.setRadius(Double.parseDouble(value))));
                    } else if (collider instanceof IgnisSampleCollisions.PolygonCollider) {
                        IgnisSampleCollisions.PolygonCollider poly = (IgnisSampleCollisions.PolygonCollider) collider;
                        // Info label for polygon
                        JLabel polyInfo = new JLabel("Vertices: " + poly.getVertexCount());
                        polyInfo.setForeground(Color.LIGHT_GRAY);
                        polyInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
                        colliderPropsPanel.add(polyInfo);
                    }
                }
                
                colliderPropsPanel.revalidate();
                colliderPropsPanel.repaint();
            }
            
            // Enable controls
            colliderTypeCombo.setEnabled(true);
            collisionModeCombo.setEnabled(obj.getColliderType() != IgnisSampleCollisions.ColliderType.NONE);
            if (ccdCheckbox != null) {
                ccdCheckbox.setEnabled(obj.getColliderType() != IgnisSampleCollisions.ColliderType.NONE);
            }
        } else {
            // Reset to defaults
            colliderTypeCombo.setSelectedIndex(0);
            collisionModeCombo.setSelectedIndex(0);
            if (ccdCheckbox != null) {
                ccdCheckbox.setSelected(false);
            }
            if (colliderPropsPanel != null) {
                colliderPropsPanel.removeAll();
                colliderPropsPanel.revalidate();
                colliderPropsPanel.repaint();
            }
            
            // Disable controls
            colliderTypeCombo.setEnabled(false);
            collisionModeCombo.setEnabled(false);
            if (ccdCheckbox != null) {
                ccdCheckbox.setEnabled(false);
            }
        }
    }
    
    /**
     * Creates a property field for collider settings
     */
    private JPanel createColliderPropertyField(String label, String value, java.util.function.Consumer<String> onChange) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBackground(new Color(45, 45, 45));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel lbl = new JLabel(label + ":");
        lbl.setForeground(Color.LIGHT_GRAY);
        lbl.setPreferredSize(new Dimension(50, 20));
        
        JTextField field = new JTextField(value);
        field.setBackground(new Color(60, 60, 60));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)),
            BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        
        field.addActionListener(e -> {
            try {
                onChange.accept(field.getText().trim());
                game.repaint();
            } catch (NumberFormatException ex) {
                // Ignore invalid input
            }
        });
        
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    onChange.accept(field.getText().trim());
                    game.repaint();
                } catch (NumberFormatException ex) {
                    // Ignore invalid input
                }
            }
        });
        
        panel.add(lbl, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Updates the camera section in the Inspector.
     */
    private void updateInspectorCameraSection(GameObject obj) {
        JPanel cameraSection = findComponentByName(inspectorPanel, "cameraSection");
        if (cameraSection == null) return;
        
        if (obj instanceof Camera) {
            Camera cam = (Camera) obj;
            cameraSection.setVisible(true);
            
            // Update zoom field
            JTextField zoomField = findComponentByName(inspectorPanel, "cameraZoomField");
            if (zoomField != null) {
                zoomField.setText(String.format("%.2f", cam.getZoom()));
            }
            
            // Update active checkbox
            JCheckBox activeCheckbox = findComponentByName(inspectorPanel, "activeCameraCheckbox");
            if (activeCheckbox != null) {
                activeCheckbox.setSelected(cam.isActiveCamera());
            }
        } else {
            cameraSection.setVisible(false);
        }
        
        inspectorPanel.revalidate();
        inspectorPanel.repaint();
    }
    
    /**
     * Applies camera-specific inspector changes.
     */
    private void applyCameraInspectorChanges() {
        if (isUpdatingInspector) return;
        
        GameObject obj = game.getSelectedObject();
        if (!(obj instanceof Camera)) return;
        
        Camera cam = (Camera) obj;
        
        try {
            JTextField zoomField = findComponentByName(inspectorPanel, "cameraZoomField");
            if (zoomField != null) {
                String zoomText = zoomField.getText().trim();
                if (!zoomText.isEmpty()) {
                    double zoom = Double.parseDouble(zoomText);
                    cam.setZoom(Math.max(0.1, Math.min(10.0, zoom)));
                }
            }
            
            updateCameraLabels();
            game.repaint();
        } catch (NumberFormatException ex) {
            updateInspectorCameraSection(obj);
        }
    }
    
    /**
     * Updates the scripts list in the Inspector for the selected object
     */
    private void updateInspectorScripts(GameObject obj) {
        // Find the scripts panel
        JPanel scriptsListPanel = findScriptsListPanel();
        if (scriptsListPanel == null) return;
        
        scriptsListPanel.removeAll();
        
        if (obj != null && !obj.getScriptNames().isEmpty()) {
            for (String scriptName : obj.getScriptNames()) {
                JPanel scriptItem = createScriptItemPanel(scriptName, obj);
                scriptsListPanel.add(scriptItem);
                scriptsListPanel.add(Box.createVerticalStrut(3));
            }
        }
        
        scriptsListPanel.revalidate();
        scriptsListPanel.repaint();
    }
    
    /**
     * Creates a script item for the inspector with editable public variables
     */
    private JPanel createScriptItemPanel(String scriptName, GameObject obj) {
        // Main container with vertical layout
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(new Color(55, 55, 55));
        container.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        // Header panel with script name and remove button
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.setBackground(new Color(55, 55, 55));
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel nameLabel = new JLabel("📜 " + scriptName);
        nameLabel.setForeground(new Color(100, 200, 255));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        nameLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openScriptEditor(scriptName);
                }
            }
        });
        
        JButton removeBtn = createRemoveScriptButton();
        removeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove script '" + scriptName + "' from this object?",
                "Confirm Remove",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                obj.removeScriptByName(scriptName);
                updateInspectorScripts(obj);
            }
        });
        
        headerPanel.add(nameLabel, BorderLayout.CENTER);
        headerPanel.add(removeBtn, BorderLayout.EAST);
        container.add(headerPanel);
        
        // Find the script instance and add public variable fields
        IgnisScript script = findScriptByName(obj, scriptName);
        if (script != null) {
            JPanel variablesPanel = createScriptVariablesPanel(script);
            if (variablesPanel != null) {
                container.add(Box.createVerticalStrut(5));
                container.add(variablesPanel);
            }
        }
        
        return container;
    }
    
    /**
     * Finds a script by name in a GameObject
     */
    private IgnisScript findScriptByName(GameObject obj, String scriptName) {
        for (IgnisScript script : obj.getScripts()) {
            if (script.getScriptName().equals(scriptName)) {
                return script;
            }
        }
        return null;
    }
    
    /**
     * Creates a panel with editable fields for all public variables of a script
     */
    private JPanel createScriptVariablesPanel(IgnisScript script) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(50, 50, 50));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 2));
        
        Class<?> clazz = script.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        boolean hasPublicFields = false;
        
        for (Field field : fields) {
            // Only show non-static fields that are not from parent class
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            
            // Check if field is accessible (public or private that we can expose)
            // For now, we'll expose all declared fields of the script class
            field.setAccessible(true);
            
            Class<?> fieldType = field.getType();
            
            // Only support basic types
            if (isSupportedType(fieldType)) {
                hasPublicFields = true;
                JPanel fieldPanel = createFieldEditorPanel(script, field);
                panel.add(fieldPanel);
                panel.add(Box.createVerticalStrut(2));
            }
        }
        
        if (!hasPublicFields) {
            return null;
        }
        
        return panel;
    }
    
    /**
     * Checks if a field type is supported for editing
     */
    private boolean isSupportedType(Class<?> type) {
        return type == int.class || type == Integer.class ||
               type == double.class || type == Double.class ||
               type == float.class || type == Float.class ||
               type == long.class || type == Long.class ||
               type == boolean.class || type == Boolean.class ||
               type == String.class ||
               GameObject.class.isAssignableFrom(type);
    }
    
    /**
     * Creates an editor panel for a single field
     */
    private JPanel createFieldEditorPanel(IgnisScript script, Field field) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBackground(new Color(50, 50, 50));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Field name label
        String displayName = formatFieldName(field.getName());
        JLabel label = new JLabel(displayName);
        label.setForeground(new Color(180, 180, 180));
        label.setPreferredSize(new Dimension(80, 20));
        label.setFont(label.getFont().deriveFont(11f));
        
        // Create appropriate editor based on field type
        JComponent editor = createFieldEditor(script, field);
        
        panel.add(label, BorderLayout.WEST);
        panel.add(editor, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Formats a field name for display (camelCase to Title Case)
     */
    private String formatFieldName(String name) {
        if (name == null || name.isEmpty()) return name;
        
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(name.charAt(0)));
        
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }
        
        return result.toString();
    }
    
    /**
     * Creates the appropriate editor component for a field
     */
    private JComponent createFieldEditor(IgnisScript script, Field field) {
        Class<?> type = field.getType();
        
        if (type == boolean.class || type == Boolean.class) {
            return createBooleanEditor(script, field);
        } else if (GameObject.class.isAssignableFrom(type)) {
            return createGameObjectEditor(script, field);
        } else {
            return createTextEditor(script, field);
        }
    }
    
    /**
     * Creates a checkbox editor for boolean fields
     */
    private JCheckBox createBooleanEditor(IgnisScript script, Field field) {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setBackground(new Color(50, 50, 50));
        checkBox.setForeground(Color.WHITE);
        
        try {
            boolean value = field.getBoolean(script);
            checkBox.setSelected(value);
        } catch (Exception e) {
            // Ignore
        }
        
        checkBox.addActionListener(e -> {
            try {
                field.setBoolean(script, checkBox.isSelected());
            } catch (Exception ex) {
                System.err.println("Error setting field " + field.getName() + ": " + ex.getMessage());
            }
        });
        
        return checkBox;
    }
    
    /**
     * Creates a dropdown editor for GameObject reference fields.
     * Allows selecting an object from the scene to reference in the script.
     */
    private JPanel createGameObjectEditor(IgnisScript script, Field field) {
        JPanel panel = new JPanel(new BorderLayout(2, 0));
        panel.setBackground(new Color(50, 50, 50));
        panel.setOpaque(true);
        
        // Create the combo box with scene objects
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setBackground(new Color(60, 60, 60));
        comboBox.setForeground(Color.WHITE);
        comboBox.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        ((JComponent) comboBox.getRenderer()).setOpaque(true);
        
        // Custom renderer for dark theme
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (isSelected) {
                    setBackground(new Color(70, 130, 180));
                    setForeground(Color.WHITE);
                } else {
                    setBackground(new Color(60, 60, 60));
                    setForeground(Color.WHITE);
                }
                
                // Highlight "None" option
                if ("None".equals(value)) {
                    setForeground(new Color(150, 150, 150));
                    setFont(getFont().deriveFont(Font.ITALIC));
                }
                
                return this;
            }
        });
        
        // Populate the combo box with scene objects
        comboBox.addItem("None");
        for (GameObject entity : game.getEntities()) {
            // Don't allow self-reference (skip the object that owns this script)
            if (entity != script.getGameObject()) {
                comboBox.addItem(entity.getName());
            }
        }
        
        // Set current value if field has a reference
        try {
            Object currentValue = field.get(script);
            if (currentValue != null && currentValue instanceof GameObject) {
                GameObject currentRef = (GameObject) currentValue;
                comboBox.setSelectedItem(currentRef.getName());
            } else {
                comboBox.setSelectedItem("None");
            }
        } catch (Exception e) {
            comboBox.setSelectedItem("None");
        }
        
        // Handle selection changes
        comboBox.addActionListener(e -> {
            String selectedName = (String) comboBox.getSelectedItem();
            try {
                if ("None".equals(selectedName) || selectedName == null) {
                    field.set(script, null);
                } else {
                    // Find the object by name
                    for (GameObject entity : game.getEntities()) {
                        if (entity.getName().equals(selectedName)) {
                            field.set(script, entity);
                            break;
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Error setting GameObject reference " + field.getName() + ": " + ex.getMessage());
            }
        });
        
        // Button to pick from hierarchy or scene
        JButton pickButton = new JButton("◎");
        pickButton.setToolTipText("Pick from scene (click an object in the viewport)");
        pickButton.setPreferredSize(new Dimension(24, 20));
        pickButton.setMargin(new Insets(0, 0, 0, 0));
        pickButton.setFocusPainted(false);
        pickButton.setBackground(new Color(70, 70, 70));
        pickButton.setForeground(Color.WHITE);
        pickButton.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)));
        
        pickButton.addActionListener(e -> {
            // Enable pick mode in the game canvas
            enableGameObjectPickMode(script, field, comboBox);
        });
        
        panel.add(comboBox, BorderLayout.CENTER);
        panel.add(pickButton, BorderLayout.EAST);
        
        return panel;
    }
    
    /**
     * Enables pick mode where clicking on an object in the scene will assign it to the field
     */
    private void enableGameObjectPickMode(IgnisScript script, Field field, JComboBox<String> comboBox) {
        // Show instruction to user
        JOptionPane.showMessageDialog(this,
            "Click on an object in the scene or hierarchy to select it.\n" +
            "Press ESC to cancel.",
            "Pick GameObject",
            JOptionPane.INFORMATION_MESSAGE);
        
        // Set a flag to indicate we're in pick mode
        final boolean[] picking = {true};
        
        // Add temporary mouse listener to the game canvas
        java.awt.event.MouseAdapter pickListener = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (!picking[0]) return;
                
                // Get the object at the click position
                GameObject clicked = game.getObjectAt(e.getX(), e.getY());
                if (clicked != null && clicked != script.getGameObject()) {
                    try {
                        field.set(script, clicked);
                        comboBox.setSelectedItem(clicked.getName());
                    } catch (Exception ex) {
                        System.err.println("Error setting reference: " + ex.getMessage());
                    }
                }
                
                // Exit pick mode
                picking[0] = false;
                game.removeMouseListener(this);
                game.setCursor(Cursor.getDefaultCursor());
            }
        };
        
        game.addMouseListener(pickListener);
        game.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        
        // Also allow picking from hierarchy list
        ListSelectionListener hierarchyPickListener = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!picking[0] || e.getValueIsAdjusting()) return;
                
                GameObject selected = hierarchyList.getSelectedValue();
                if (selected != null && selected != script.getGameObject()) {
                    try {
                        field.set(script, selected);
                        comboBox.setSelectedItem(selected.getName());
                    } catch (Exception ex) {
                        System.err.println("Error setting reference: " + ex.getMessage());
                    }
                    
                    // Exit pick mode
                    picking[0] = false;
                    game.removeMouseListener(pickListener);
                    game.setCursor(Cursor.getDefaultCursor());
                    hierarchyList.removeListSelectionListener(this);
                }
            }
        };
        
        hierarchyList.addListSelectionListener(hierarchyPickListener);
        
        // Add key listener for ESC to cancel
        KeyAdapter escListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    picking[0] = false;
                    game.removeMouseListener(pickListener);
                    game.setCursor(Cursor.getDefaultCursor());
                    hierarchyList.removeListSelectionListener(hierarchyPickListener);
                    Editor.this.removeKeyListener(this);
                }
            }
        };
        
        this.addKeyListener(escListener);
        this.requestFocus();
    }
    
    /**
     * Creates a text field editor for numeric and string fields
     */
    private JTextField createTextEditor(IgnisScript script, Field field) {
        JTextField textField = new JTextField();
        textField.setBackground(new Color(60, 60, 60));
        textField.setForeground(Color.WHITE);
        textField.setCaretColor(Color.WHITE);
        textField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)),
            BorderFactory.createEmptyBorder(1, 4, 1, 4)
        ));
        textField.setPreferredSize(new Dimension(0, 20));
        
        // Set initial value
        try {
            Object value = field.get(script);
            textField.setText(value != null ? value.toString() : "");
        } catch (Exception e) {
            // Ignore
        }
        
        // Apply changes on Enter or focus lost
        ActionListener applyAction = e -> applyFieldValue(script, field, textField.getText());
        textField.addActionListener(applyAction);
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyFieldValue(script, field, textField.getText());
            }
        });
        
        return textField;
    }
    
    /**
     * Applies a text value to a field, converting to the appropriate type
     */
    private void applyFieldValue(IgnisScript script, Field field, String text) {
        Class<?> type = field.getType();
        
        try {
            if (type == int.class || type == Integer.class) {
                field.set(script, Integer.parseInt(text.trim()));
            } else if (type == double.class || type == Double.class) {
                field.set(script, Double.parseDouble(text.trim()));
            } else if (type == float.class || type == Float.class) {
                field.set(script, Float.parseFloat(text.trim()));
            } else if (type == long.class || type == Long.class) {
                field.set(script, Long.parseLong(text.trim()));
            } else if (type == String.class) {
                field.set(script, text);
            }
        } catch (NumberFormatException ex) {
            // Invalid number format, ignore
        } catch (IllegalAccessException ex) {
            System.err.println("Error setting field " + field.getName() + ": " + ex.getMessage());
        }
    }
    
    /**
     * Finds the scripts panel in the inspector
     */
    private JPanel findScriptsListPanel() {
        return findComponentByName(inspectorPanel, "scriptsListPanel");
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Component> T findComponentByName(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (name.equals(c.getName())) {
                return (T) c;
            }
            if (c instanceof Container) {
                T found = findComponentByName((Container) c, name);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * Updates the sprite section in the inspector
     */
    private void updateInspectorSprite(GameObject obj) {
        JPanel spritePanel = findComponentByName(inspectorPanel, "spritePanel");
        if (spritePanel == null) return;
        
        spritePanel.removeAll();
        
        if (obj != null && obj.getSpritePath() != null && !obj.getSpritePath().isEmpty()) {
            // Show the sprite path with a remove button
            JPanel spriteItem = new JPanel();
            spriteItem.setLayout(new BorderLayout(5, 0));
            spriteItem.setBackground(new Color(55, 55, 55));
            spriteItem.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 40, 40)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
            spriteItem.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            spriteItem.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            // Get just the filename from the path
            String spritePath = obj.getSpritePath();
            String fileName = new File(spritePath).getName();
            
            // Left side: info panel
            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setBackground(new Color(55, 55, 55));
            
            JLabel iconLabel = new JLabel("🖼 " + fileName);
            iconLabel.setForeground(new Color(100, 200, 150));
            iconLabel.setFont(iconLabel.getFont().deriveFont(Font.BOLD, 11f));
            iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JLabel pathLabel = new JLabel(truncatePath(spritePath, 25));
            pathLabel.setForeground(new Color(120, 120, 120));
            pathLabel.setFont(pathLabel.getFont().deriveFont(9f));
            pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            pathLabel.setToolTipText(spritePath);
            
            infoPanel.add(iconLabel);
            infoPanel.add(pathLabel);
            
            // Remove button (same style as script remove button)
            JButton removeBtn = createRemoveButton();
            removeBtn.setToolTipText("Remove Image");
            removeBtn.addActionListener(e -> removeImageFromObject(obj));
            
            spriteItem.add(infoPanel, BorderLayout.CENTER);
            spriteItem.add(removeBtn, BorderLayout.EAST);
            
            spritePanel.add(spriteItem);
        } else {
            // Show "No image" label
            JLabel noImageLabel = new JLabel("No image assigned");
            noImageLabel.setForeground(new Color(100, 100, 100));
            noImageLabel.setFont(noImageLabel.getFont().deriveFont(Font.ITALIC, 10f));
            noImageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            spritePanel.add(noImageLabel);
        }
        
        spritePanel.revalidate();
        spritePanel.repaint();
    }
    
    /**
     * Creates a styled remove button with custom painted X icon (same as script remove)
     */
    private JButton createRemoveButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int size = 5;

                // Desenha um X
                g2d.drawLine(cx - size, cy - size, cx + size, cy + size);
                g2d.drawLine(cx + size, cy - size, cx - size, cy + size);
                
                g2d.dispose();
            }
        };
        button.setPreferredSize(new Dimension(20, 20));
        button.setMaximumSize(new Dimension(20, 20));
        button.setMinimumSize(new Dimension(20, 20));
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setBackground(new Color(180, 60, 60));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(220, 80, 80));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(180, 60, 60));
            }
        });
        
        return button;
    }
    
    /**
     * Truncates a file path to a maximum length, showing ellipsis
     */
    private String truncatePath(String path, int maxLength) {
        if (path.length() <= maxLength) {
            return path;
        }
        return "..." + path.substring(path.length() - maxLength + 3);
    }
    
    /**
     * Shows dialog to add a script to the selected object
     */
    private void showAddScriptDialog() {
        GameObject obj = game.getSelectedObject();
        if (obj == null) {
            JOptionPane.showMessageDialog(this, 
                "Select an object first!", 
                "No Selection", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (scriptManager == null) {
            JOptionPane.showMessageDialog(this, 
                "No project loaded!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // List available scripts
        java.util.List<String> scripts = scriptManager.listAvailableScripts();
        
        if (scripts.isEmpty()) {
            int create = JOptionPane.showConfirmDialog(this,
                "No scripts found. Would you like to create a new script?",
                "No Scripts",
                JOptionPane.YES_NO_OPTION);
            
            if (create == JOptionPane.YES_OPTION) {
                createNewScript();
            }
            return;
        }
        
        // Add option to create new
        String[] options = new String[scripts.size() + 1];
        options[0] = "➕ Create New Script...";
        for (int i = 0; i < scripts.size(); i++) {
            options[i + 1] = scripts.get(i);
        }
        
        String selected = (String) JOptionPane.showInputDialog(this,
            "Select a script to add:",
            "Add Script",
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]);
        
        if (selected == null) return;
        
        if (selected.equals(options[0])) {
            createNewScript();
        } else {
            // Add script to object
            if (!obj.getScriptNames().contains(selected)) {
                obj.getScriptNames().add(selected);
                updateInspectorScripts(obj);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Script already attached to this object!",
                    "Duplicate Script",
                    JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    /**
     * Creates a new script
     */
    private void createNewScript() {
        if (scriptManager == null) {
            JOptionPane.showMessageDialog(this,
                "No project loaded!",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String scriptName = JOptionPane.showInputDialog(this,
            "Enter script name (e.g., PlayerMovement):",
            "New Script",
            JOptionPane.PLAIN_MESSAGE);
        
        if (scriptName == null || scriptName.trim().isEmpty()) return;
        
        scriptName = scriptName.trim();
        
        // Ensure it starts with uppercase
        scriptName = Character.toUpperCase(scriptName.charAt(0)) + scriptName.substring(1);
        
        if (scriptManager.createNewScript(scriptName)) {
            refreshFileTree();
            
            // Ask if want to edit
            int edit = JOptionPane.showConfirmDialog(this,
                "Script '" + scriptName + "' created!\n\nWould you like to edit it now?",
                "Script Created",
                JOptionPane.YES_NO_OPTION);
            
            if (edit == JOptionPane.YES_OPTION) {
                openScriptEditor(scriptName);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to create script. It may already exist.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Opens the script editor for a specific script
     */
    private void openScriptEditor(String scriptName) {
        if (scriptManager == null) return;
        
        String content = scriptManager.readScriptContent(scriptName);
        if (content == null) {
            JOptionPane.showMessageDialog(this,
                "Could not read script: " + scriptName,
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Create script editor window
        JFrame editorFrame = new JFrame("Script Editor - " + scriptName);
        editorFrame.setSize(800, 600);
        editorFrame.setLocationRelativeTo(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(40, 40, 40));
        
        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBackground(new Color(50, 50, 50));
        
        JButton saveBtn = new JButton("💾 Save");
        saveBtn.setBackground(new Color(60, 120, 60));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorderPainted(false);
        
        JButton compileBtn = new JButton("⚙ Compile");
        compileBtn.setBackground(new Color(60, 100, 150));
        compileBtn.setForeground(Color.WHITE);
        compileBtn.setFocusPainted(false);
        compileBtn.setBorderPainted(false);
        
        JButton saveAndCompileBtn = new JButton("💾⚙ Save & Compile");
        saveAndCompileBtn.setBackground(new Color(100, 80, 150));
        saveAndCompileBtn.setForeground(Color.WHITE);
        saveAndCompileBtn.setFocusPainted(false);
        saveAndCompileBtn.setBorderPainted(false);
        
        toolbar.add(saveBtn);
        toolbar.add(compileBtn);
        toolbar.add(saveAndCompileBtn);
        
        // Text editor
        JTextArea textArea = new JTextArea(content);
        textArea.setBackground(new Color(30, 30, 30));
        textArea.setForeground(new Color(220, 220, 220));
        textArea.setCaretColor(Color.WHITE);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        textArea.setTabSize(4);
        
        // Line numbers
        JTextArea lineNumbers = new JTextArea("1");
        lineNumbers.setBackground(new Color(45, 45, 45));
        lineNumbers.setForeground(new Color(120, 120, 120));
        lineNumbers.setFont(new Font("Consolas", Font.PLAIN, 14));
        lineNumbers.setEditable(false);
        lineNumbers.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        
        // Update line numbers
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateLineNumbers(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateLineNumbers(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateLineNumbers(); }
            
            private void updateLineNumbers() {
                int lines = textArea.getLineCount();
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= lines; i++) {
                    sb.append(i).append("\n");
                }
                lineNumbers.setText(sb.toString());
            }
        });
        
        // Inicializar números de linha
        int lines = textArea.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            sb.append(i).append("\n");
        }
        lineNumbers.setText(sb.toString());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.setBorder(null);
        
        // Status bar
        JLabel statusLabel = new JLabel(" Ready");
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(50, 50, 50));
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        // Actions
        saveBtn.addActionListener(e -> {
            if (scriptManager.saveScriptContent(scriptName, textArea.getText())) {
                statusLabel.setText(" ✓ Script saved");
                statusLabel.setForeground(new Color(100, 200, 100));
            } else {
                statusLabel.setText(" ✕ Failed to save");
                statusLabel.setForeground(new Color(200, 100, 100));
            }
        });
        
        compileBtn.addActionListener(e -> {
            File scriptFile = new File(scriptManager.getScriptsFolder(), scriptName + ".java");
            if (scriptManager.compileScript(scriptFile)) {
                statusLabel.setText(" ✓ Compilation successful");
                statusLabel.setForeground(new Color(100, 200, 100));
            } else {
                statusLabel.setText(" ✕ Compilation failed - check console");
                statusLabel.setForeground(new Color(200, 100, 100));
            }
        });
        
        saveAndCompileBtn.addActionListener(e -> {
            if (scriptManager.saveScriptContent(scriptName, textArea.getText())) {
                File scriptFile = new File(scriptManager.getScriptsFolder(), scriptName + ".java");
                if (scriptManager.compileScript(scriptFile)) {
                    statusLabel.setText(" ✓ Saved and compiled successfully");
                    statusLabel.setForeground(new Color(100, 200, 100));
                } else {
                    statusLabel.setText(" ✕ Saved but compilation failed - check console");
                    statusLabel.setForeground(new Color(200, 150, 100));
                }
            } else {
                statusLabel.setText(" ✕ Failed to save");
                statusLabel.setForeground(new Color(200, 100, 100));
            }
        });
        
        // Atalhos de teclado
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        textArea.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveBtn.doClick();
            }
        });
        
        mainPanel.add(toolbar, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
        
        editorFrame.add(mainPanel);
        editorFrame.setVisible(true);
    }
    
    /**
     * Opens a file dialog to import an image for the selected object
     */
    private void importImageForSelectedObject() {
        GameObject selected = hierarchyList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an object first.",
                "No Object Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Image for " + selected.getName());
        
        // Set file filter for images
        FileNameExtensionFilter imageFilter = new FileNameExtensionFilter(
            "Image Files (*.png, *.jpg, *.jpeg, *.gif, *.bmp)",
            "png", "jpg", "jpeg", "gif", "bmp"
        );
        fileChooser.setFileFilter(imageFilter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        
        // Start in project assets folder if available
        if (currentProject != null && currentProject.getProjectFile() != null) {
            File projectDir = currentProject.getProjectFile().getParentFile();
            File assetsFolder = new File(projectDir, "project/assets/sprites");
            if (assetsFolder.exists()) {
                fileChooser.setCurrentDirectory(assetsFolder);
            } else {
                // Try just the project directory
                fileChooser.setCurrentDirectory(projectDir);
            }
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String imagePath = importSpriteIntoProject(selectedFile);

            // Set the sprite path on the object
            selected.setSpritePath(imagePath);

            // Update inspector to show the new sprite
            updateInspector(selected);

            System.out.println("Imported image for " + selected.getName() + ": " + imagePath);
        }
    }

    /**
     * Copies an external image into project/assets/sprites and returns its
     * path relative to the project folder (e.g. "assets/sprites/Grama.jpg"),
     * keeping the project portable. Images already inside the project folder
     * are referenced in place. Falls back to the absolute path when no
     * project is loaded.
     */
    private String importSpriteIntoProject(File imageFile) {
        File projectFolder = null;
        if (currentProject != null && currentProject.getProjectFile() != null) {
            projectFolder = IgnisProjectIO.getProjectFolder(currentProject.getProjectFile());
        }
        if (projectFolder == null) {
            System.err.println("No project loaded; keeping absolute sprite path: "
                    + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
        }

        AssetResolver.setProjectFolder(projectFolder);

        // Already inside the project folder: just store the relative path
        String relative = AssetResolver.relativize(imageFile);
        if (relative != null) {
            return relative;
        }

        File spritesFolder = new File(projectFolder, "assets/sprites");
        spritesFolder.mkdirs();

        // Avoid clobbering a different image that has the same file name
        String name = imageFile.getName();
        String baseName = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            baseName = name.substring(0, dot);
            extension = name.substring(dot);
        }
        File dest = new File(spritesFolder, name);
        int suffix = 1;
        while (dest.exists() && !sameFileContent(imageFile, dest)) {
            dest = new File(spritesFolder, baseName + "_" + suffix + extension);
            suffix++;
        }

        try {
            if (!dest.exists()) {
                Files.copy(imageFile.toPath(), dest.toPath());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not copy image into the project assets:\n" + ex.getMessage() +
                    "\n\nThe absolute path will be used instead (not portable).",
                    "Import Image",
                    JOptionPane.WARNING_MESSAGE);
            return imageFile.getAbsolutePath();
        }

        String destRelative = AssetResolver.relativize(dest);
        return destRelative != null ? destRelative : dest.getAbsolutePath();
    }

    private static boolean sameFileContent(File a, File b) {
        try {
            if (a.length() != b.length()) {
                return false;
            }
            return Arrays.equals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath()));
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Removes the sprite/image from the selected object
     */
    private void removeImageFromObject(GameObject obj) {
        if (obj == null) return;
        
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove the image from '" + obj.getName() + "'?",
            "Remove Image",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            obj.setSpritePath(null);
            updateInspector(obj);
            System.out.println("Removed image from " + obj.getName());
        }
    }

    // ==================== FILE BROWSER PANEL ====================

    /**
     * Creates the file browser panel for project directory management
     */
    private JPanel createFileBrowserPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));
        panel.setBorder(BorderFactory.createTitledBorder(null, "Project Files", 0, 0, null, Color.WHITE));

        // Initialize project root to projects folder (will update to specific project
        // folder when a project is loaded/saved)
        projectRoot = IgnisProjectIO.getProjectsRootFolder();

        // Create tree model
        DefaultMutableTreeNode rootNode = createFileTreeNode(projectRoot);
        fileTreeModel = new DefaultTreeModel(rootNode);

        fileTree = new JTree(fileTreeModel);
        fileTree.setBackground(new Color(60, 60, 60));
        fileTree.setForeground(Color.WHITE);
        fileTree.setCellRenderer(new FileTreeCellRenderer());
        fileTree.setRootVisible(true);
        fileTree.setShowsRootHandles(true);

        // Context menu for file operations
        JPopupMenu fileContextMenu = createFileContextMenu();

        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showFileContextMenu(e, fileContextMenu);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showFileContextMenu(e, fileContextMenu);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        File file = (File) node.getUserObject();
                        if (file.isFile()) {
                            openFile(file);
                        }
                    }
                }
            }
        });

        // Enable drag and drop from external file explorers
        setupFileTreeDragAndDrop();

        JScrollPane scrollPane = new JScrollPane(fileTree);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setBackground(new Color(50, 50, 50));

        // Toolbar for file operations
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        toolbar.setBackground(new Color(50, 50, 50));

        JButton refreshBtn = createFileBrowserButton("refresh", "Refresh");
        refreshBtn.addActionListener(e -> refreshFileTree());

        JButton newFolderBtn = createFileBrowserButton("newfolder", "New Folder");
        newFolderBtn.addActionListener(e -> createNewFolder());

        toolbar.add(refreshBtn);
        toolbar.add(newFolderBtn);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates a custom button with drawn icon for file browser
     */
    private JButton createFileBrowserButton(String iconType, String tooltip) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int w = getWidth();
                int h = getHeight();
                int cx = w / 2;
                int cy = h / 2;
                int size = 6;

                g2d.setColor(Color.WHITE);

                switch (iconType) {
                    case "refresh":
                        // Draw circular arrow
                        g2d.drawArc(cx - size, cy - size, size * 2, size * 2, 45, 270);
                        // Arrow head
                        int ax = cx + (int) (size * Math.cos(Math.toRadians(45)));
                        int ay = cy - (int) (size * Math.sin(Math.toRadians(45)));
                        g2d.drawLine(ax, ay, ax + 3, ay);
                        g2d.drawLine(ax, ay, ax, ay + 3);
                        break;

                    case "newfolder":
                        // Draw folder shape
                        int fx = cx - size;
                        int fy = cy - size + 2;
                        int fw = size * 2;
                        int fh = size + 4;
                        // Folder body
                        g2d.drawRect(fx, fy, fw, fh);
                        // Folder tab
                        g2d.drawLine(fx, fy, fx + 4, fy);
                        g2d.drawLine(fx + 4, fy, fx + 6, fy - 2);
                        g2d.drawLine(fx + 6, fy - 2, fx + 8, fy - 2);
                        g2d.drawLine(fx + 8, fy - 2, fx + 8, fy);
                        // Plus sign
                        g2d.setColor(new Color(100, 255, 100));
                        g2d.drawLine(cx, cy - 2, cx, cy + 4);
                        g2d.drawLine(cx - 3, cy + 1, cx + 3, cy + 1);
                        break;

                    case "newfile":
                        // Draw file shape
                        g2d.drawRect(cx - size + 2, cy - size, size + 4, size * 2);
                        // Corner fold
                        g2d.drawLine(cx + 2, cy - size, cx + 6, cy - size + 4);
                        g2d.drawLine(cx + 6, cy - size + 4, cx + 6, cy - size);
                        break;

                    case "delete":
                        // Draw X
                        g2d.setColor(new Color(255, 100, 100));
                        g2d.drawLine(cx - size + 2, cy - size + 2, cx + size - 2, cy + size - 2);
                        g2d.drawLine(cx + size - 2, cy - size + 2, cx - size + 2, cy + size - 2);
                        break;
                }

                g2d.dispose();
            }
        };

        button.setPreferredSize(new Dimension(24, 24));
        button.setMinimumSize(new Dimension(24, 24));
        button.setMaximumSize(new Dimension(24, 24));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setBackground(new Color(60, 60, 60));
        button.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(80, 80, 80));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(60, 60, 60));
            }
        });

        return button;
    }

    /**
     * Creates a tree node from a file/directory
     */
    private DefaultMutableTreeNode createFileTreeNode(File file) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                // Sort: directories first, then files
                java.util.Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory())
                        return -1;
                    if (!a.isDirectory() && b.isDirectory())
                        return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                for (File child : children) {
                    // Skip hidden files and common non-project folders
                    if (child.isHidden() || child.getName().equals("target") ||
                            child.getName().equals(".git") || child.getName().equals("node_modules")) {
                        continue;
                    }
                    node.add(createFileTreeNode(child));
                }
            }
        }

        return node;
    }

    /**
     * Custom renderer for file tree with drawn icons
     */
    private class FileTreeCellRenderer extends DefaultTreeCellRenderer {
        
        // Larger icon size for better drag and drop targeting
        private static final int ICON_SIZE = 20;
        
        // Cached icons loaded from files
        private ImageIcon audioFileIcon;
        private ImageIcon prefabFileIcon;
        
        public FileTreeCellRenderer() {
            // Load icons from core_assets/icons
            try {
                java.net.URL audioIconUrl = getClass().getResource("/com/ignis/core_assets/icons/sound_icon.ico");
                java.net.URL prefabIconUrl = getClass().getResource("/com/ignis/core_assets/icons/util_gear.ico");
                
                if (audioIconUrl != null) {
                    audioFileIcon = new ImageIcon(new ImageIcon(audioIconUrl).getImage()
                        .getScaledInstance(ICON_SIZE, ICON_SIZE, java.awt.Image.SCALE_SMOOTH));
                }
                if (prefabIconUrl != null) {
                    prefabFileIcon = new ImageIcon(new ImageIcon(prefabIconUrl).getImage()
                        .getScaledInstance(ICON_SIZE, ICON_SIZE, java.awt.Image.SCALE_SMOOTH));
                }
            } catch (Exception e) {
                System.err.println("[Editor] Failed to load file icons: " + e.getMessage());
            }
        }
        
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            File file = (File) node.getUserObject();

            String displayName = file.getName().isEmpty() ? file.getPath() : file.getName();
            setText(displayName);

            // Set icon based on file type
            if (file.isDirectory()) {
                setIcon(expanded ? createFolderOpenIcon() : createFolderIcon());
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".prefab.json")) {
                    setIcon(prefabFileIcon != null ? prefabFileIcon : createPrefabFileIcon());
                } else if (name.endsWith(".java")) {
                    setIcon(createJavaFileIcon());
                } else if (name.endsWith(".ignis")) {
                    setIcon(createIgnisFileIcon());
                } else if (name.endsWith(".json")) {
                    setIcon(createJsonFileIcon());
                } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif")) {
                    setIcon(createImageFileIcon());
                } else if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aiff") || 
                           name.endsWith(".au") || name.endsWith(".ogg") || name.endsWith(".flac")) {
                    setIcon(audioFileIcon != null ? audioFileIcon : createAudioFileIcon());
                } else {
                    setIcon(createFileIcon());
                }
            }

            setBackgroundNonSelectionColor(new Color(60, 60, 60));
            setBackgroundSelectionColor(new Color(0, 120, 215));
            setTextNonSelectionColor(Color.WHITE);
            setTextSelectionColor(Color.WHITE);

            return this;
        }

        private Icon createFolderIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(255, 200, 80));
                    // Folder body (scaled for 20px)
                    g2d.fillRect(x, y + 5, 18, 12);
                    // Folder tab
                    g2d.fillRect(x, y + 3, 8, 4);
                    g2d.setColor(new Color(200, 150, 50));
                    g2d.drawRect(x, y + 5, 18, 12);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }

        private Icon createFolderOpenIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(255, 220, 100));
                    // Folder body (open, scaled for 20px)
                    int[] xPoints = { x, x + 17, x + 20, x + 3 };
                    int[] yPoints = { y + 7, y + 7, y + 17, y + 17 };
                    g2d.fillPolygon(xPoints, yPoints, 4);
                    // Folder tab
                    g2d.setColor(new Color(255, 200, 80));
                    g2d.fillRect(x, y + 3, 8, 6);
                    g2d.setColor(new Color(200, 150, 50));
                    g2d.drawPolygon(xPoints, yPoints, 4);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }

        private Icon createFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(200, 200, 200));
                    g2d.fillRect(x + 3, y, 13, 18);
                    g2d.setColor(new Color(150, 150, 150));
                    g2d.drawRect(x + 3, y, 13, 18);
                    // Corner fold
                    g2d.drawLine(x + 10, y, x + 16, y + 5);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }

        private Icon createJavaFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(200, 200, 200));
                    g2d.fillRect(x + 3, y, 13, 18);
                    g2d.setColor(new Color(255, 120, 50));
                    g2d.setFont(new Font("Dialog", Font.BOLD, 11));
                    g2d.drawString("J", x + 6, y + 14);
                    g2d.setColor(new Color(150, 150, 150));
                    g2d.drawRect(x + 3, y, 13, 18);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }

        private Icon createIgnisFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Fire-colored background
                    g2d.setColor(new Color(255, 100, 50));
                    g2d.fillRect(x + 3, y, 13, 18);
                    // Flame
                    g2d.setColor(new Color(255, 200, 50));
                    int[] fx = { x + 9, x + 5, x + 9, x + 13 };
                    int[] fy = { y + 3, y + 13, y + 9, y + 13 };
                    g2d.fillPolygon(fx, fy, 4);
                    g2d.setColor(new Color(200, 50, 0));
                    g2d.drawRect(x + 3, y, 13, 18);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }

        private Icon createJsonFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(200, 200, 200));
                    g2d.fillRect(x + 3, y, 13, 18);
                    g2d.setColor(new Color(100, 150, 200));
                    g2d.setFont(new Font("Dialog", Font.BOLD, 10));
                    g2d.drawString("{}", x + 4, y + 13);
                    g2d.setColor(new Color(150, 150, 150));
                    g2d.drawRect(x + 3, y, 13, 18);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }
        
        private Icon createPrefabFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // Box background
                    g2d.setColor(new Color(120, 80, 200));
                    g2d.fillRoundRect(x + 1, y + 1, 17, 17, 4, 4);
                    
                    // 3D box effect - top
                    g2d.setColor(new Color(150, 110, 230));
                    int[] topX = {x + 1, x + 9, x + 18, x + 9};
                    int[] topY = {y + 5, y + 1, y + 5, y + 9};
                    g2d.fillPolygon(topX, topY, 4);
                    
                    // Border
                    g2d.setColor(new Color(80, 50, 150));
                    g2d.drawRoundRect(x + 1, y + 1, 17, 17, 4, 4);
                    
                    // "P" letter
                    g2d.setColor(Color.WHITE);
                    g2d.setFont(new Font("Dialog", Font.BOLD, 11));
                    g2d.drawString("P", x + 6, y + 14);
                    
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }

        private Icon createImageFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(new Color(200, 200, 200));
                    g2d.fillRect(x + 3, y, 13, 18);
                    // Mountain/image symbol
                    g2d.setColor(new Color(100, 180, 100));
                    int[] mx = { x + 4, x + 9, x + 15 };
                    int[] my = { y + 14, y + 6, y + 14 };
                    g2d.fillPolygon(mx, my, 3);
                    // Sun
                    g2d.setColor(new Color(255, 200, 50));
                    g2d.fillOval(x + 10, y + 3, 4, 4);
                    g2d.setColor(new Color(150, 150, 150));
                    g2d.drawRect(x + 3, y, 13, 18);
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }
        
        private Icon createAudioFileIcon() {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // Background
                    g2d.setColor(new Color(70, 70, 70));
                    g2d.fillRoundRect(x, y, ICON_SIZE, ICON_SIZE, 4, 4);
                    
                    // Draw two eighth notes (colcheias) connected by a beam
                    g2d.setColor(new Color(255, 150, 50)); // Orange/gold color for music
                    g2d.setStroke(new BasicStroke(2f));
                    
                    // Left note stem
                    g2d.drawLine(x + 5, y + 5, x + 5, y + 14);
                    // Right note stem
                    g2d.drawLine(x + 14, y + 5, x + 14, y + 12);
                    
                    // Beam connecting the two stems (at top)
                    g2d.setStroke(new BasicStroke(3f));
                    g2d.drawLine(x + 5, y + 5, x + 14, y + 5);
                    
                    // Left note head (filled ellipse)
                    g2d.fillOval(x + 2, y + 12, 5, 4);
                    // Right note head (filled ellipse)
                    g2d.fillOval(x + 11, y + 10, 5, 4);
                    
                    g2d.dispose();
                }

                @Override
                public int getIconWidth() {
                    return ICON_SIZE;
                }

                @Override
                public int getIconHeight() {
                    return ICON_SIZE;
                }
            };
        }
    }

    /**
     * Creates context menu for file operations
     */
    private JPopupMenu createFileContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem newFolder = new JMenuItem("📁 New Folder");
        newFolder.addActionListener(e -> createNewFolder());
        
        JMenuItem newScript = new JMenuItem("📜 New Script");
        newScript.addActionListener(e -> createNewScript());

        JMenuItem rename = new JMenuItem("✏ Rename");
        rename.addActionListener(e -> renameSelectedFile());

        JMenuItem delete = new JMenuItem("🗑 Delete");
        delete.addActionListener(e -> deleteSelectedFile());

        JMenuItem refresh = new JMenuItem("🔄 Refresh");
        refresh.addActionListener(e -> refreshFileTree());
        
        // Option to edit script (only appears for .java files)
        JMenuItem editScript = new JMenuItem("📝 Edit Script");
        editScript.addActionListener(e -> {
            TreePath path = fileTree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                File file = (File) node.getUserObject();
                if (file.getName().endsWith(".java")) {
                    String scriptName = file.getName().replace(".java", "");
                    openScriptEditor(scriptName);
                }
            }
        });
        
        // Option to compile script
        JMenuItem compileScript = new JMenuItem("⚙ Compile Script");
        compileScript.addActionListener(e -> {
            TreePath path = fileTree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                File file = (File) node.getUserObject();
                if (file.getName().endsWith(".java") && scriptManager != null) {
                    if (scriptManager.compileScript(file)) {
                        JOptionPane.showMessageDialog(this,
                            "Script compiled successfully!",
                            "Compile",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                            "Compilation failed. Check console for errors.",
                            "Compile Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        menu.add(newFolder);
        menu.add(newScript);
        menu.addSeparator();
        menu.add(editScript);
        menu.add(compileScript);
        menu.addSeparator();
        menu.add(rename);
        menu.add(delete);
        menu.addSeparator();
        menu.add(refresh);

        return menu;
    }

    private void showFileContextMenu(MouseEvent e, JPopupMenu menu) {
        TreePath path = fileTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            fileTree.setSelectionPath(path);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Updates the project root to the "project" folder associated with the .ignis
     * file.
     * This folder contains the game assets, scripts, scenes, and other resources.
     * Structure: projects/[projectName]/project/
     * - assets/
     *   - sprites/ (sprite images and spritesheets)
     *   - sounds/ (sound effects)
     *   - music/ (background music)
     *   - fonts/ (custom fonts)
     *   - tilemaps/ (tile maps)
     * - scripts/ (user scripts)
     * - scenes/ (game scenes)
     * - prefabs/ (prefabricated objects)
     */
    private void updateProjectRoot() {
        if (currentProject != null && currentProject.getProjectFile() != null) {
            // Use the project folder where game resources are stored
            File projectFolder = IgnisProjectIO.getProjectFolder(currentProject.getProjectFile());
            if (projectFolder != null) {
                // Ensure the folder structure exists
                IgnisProjectIO.ensureProjectFolderStructure(projectFolder);
                projectRoot = projectFolder;
                refreshFileTree();
                
                // Initialize ScriptManager
                scriptManager = new ScriptManager(projectFolder);
                game.setScriptManager(scriptManager);
                
                // Initialize PrefabManager
                prefabManager = new PrefabManager(projectFolder, game, scriptManager);
                game.setPrefabManager(prefabManager);
                
                // Initialize AI Integration
                initializeAIIntegration(projectFolder);
            }
        } else {
            // No project loaded, show the projects root folder
            projectRoot = IgnisProjectIO.getProjectsRootFolder();
            refreshFileTree();
            
            // Clear ScriptManager
            if (scriptManager != null) {
                scriptManager.close();
                scriptManager = null;
                game.setScriptManager(null);
            }
            
            // Clear PrefabManager
            prefabManager = null;
            game.setPrefabManager(null);
            
            // Clear AI Integration
            aiIntegration = null;
            removeAuxiliaryTab();
        }
    }
    
    /**
     * Initializes AI integration for the current project
     */
    private void initializeAIIntegration(File projectFolder) {
        // Initialize AI Integration
        aiIntegration = new AIIntegration(projectFolder);
        
        // Add Auxiliary tab if not already added
        JTabbedPane rightTabbedPane = null;
        Component[] components = rightSplit.getComponents();
        for (Component comp : components) {
            if (comp instanceof JTabbedPane) {
                rightTabbedPane = (JTabbedPane) comp;
                break;
            }
        }
        
        if (rightTabbedPane != null) {
            // Remove existing auxiliary tab if it exists
            removeAuxiliaryTab();
            
            // Create and add new auxiliary panel
            auxiliaryPanel = new AuxiliaryPanel(aiIntegration, game);
            
            // Set callback to refresh file tree when files are created/modified
            auxiliaryPanel.setFileRefreshCallback(() -> refreshFileTree());
            
            rightTabbedPane.addTab("🤖 Auxiliary", auxiliaryPanel);
        }
    }
    
    /**
     * Removes the Auxiliary tab from the right panel
     */
    private void removeAuxiliaryTab() {
        JTabbedPane rightTabbedPane = null;
        Component[] components = rightSplit.getComponents();
        for (Component comp : components) {
            if (comp instanceof JTabbedPane) {
                rightTabbedPane = (JTabbedPane) comp;
                break;
            }
        }
        
        if (rightTabbedPane != null) {
            // Find and remove auxiliary tab
            for (int i = 0; i < rightTabbedPane.getTabCount(); i++) {
                if (rightTabbedPane.getTitleAt(i).contains("Auxiliary")) {
                    rightTabbedPane.removeTabAt(i);
                    break;
                }
            }
        }
    }

    private void refreshFileTree() {
        DefaultMutableTreeNode rootNode = createFileTreeNode(projectRoot);
        fileTreeModel.setRoot(rootNode);
        fileTreeModel.reload();
    }

    /**
     * Sets up drag and drop support for the file tree.
     * Allows importing files from external file explorers (Windows Explorer, etc.)
     */
    private void setupFileTreeDragAndDrop() {
        new DropTarget(fileTree, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetListener() {
            
            private TreePath lastHighlightedPath = null;
            
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (isDropAcceptable(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                if (isDropAcceptable(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    
                    // Highlight the folder under the cursor
                    Point pt = dtde.getLocation();
                    TreePath path = fileTree.getPathForLocation(pt.x, pt.y);
                    
                    if (path != lastHighlightedPath) {
                        lastHighlightedPath = path;
                        fileTree.setSelectionPath(path);
                    }
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
                if (isDropAcceptable(dtde)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                lastHighlightedPath = null;
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!isDropAcceptable(dtde)) {
                        dtde.rejectDrop();
                        return;
                    }
                    
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    
                    // Get the target folder
                    Point pt = dtde.getLocation();
                    TreePath path = fileTree.getPathForLocation(pt.x, pt.y);
                    File targetFolder = projectRoot;
                    
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        File file = (File) node.getUserObject();
                        targetFolder = file.isDirectory() ? file : file.getParentFile();
                    }
                    
                    // Get the dropped files
                    Transferable transferable = dtde.getTransferable();
                    
                    @SuppressWarnings("unchecked")
                    java.util.List<File> droppedFiles = (java.util.List<File>) 
                        transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    
                    // Import the files
                    int importedCount = importFiles(droppedFiles, targetFolder);
                    
                    dtde.dropComplete(true);
                    
                    // Refresh the tree and show result
                    refreshFileTree();
                    
                    if (importedCount > 0) {
                        showImportNotification(importedCount, targetFolder);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error during drop: " + e.getMessage());
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
                
                lastHighlightedPath = null;
            }
            
            private boolean isDropAcceptable(DropTargetDragEvent dtde) {
                return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            
            private boolean isDropAcceptable(DropTargetDropEvent dtde) {
                return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
        }, true);
    }

    /**
     * Imports files to the target folder.
     * @param files List of files to import
     * @param targetFolder Destination folder
     * @return Number of files successfully imported
     */
    private int importFiles(java.util.List<File> files, File targetFolder) {
        int imported = 0;
        java.util.List<String> errors = new ArrayList<>();
        
        for (File sourceFile : files) {
            try {
                if (sourceFile.isDirectory()) {
                    // Copy entire directory
                    imported += copyDirectory(sourceFile, new File(targetFolder, sourceFile.getName()));
                } else {
                    // Copy single file
                    File destFile = new File(targetFolder, sourceFile.getName());
                    
                    // Check if file already exists
                    if (destFile.exists()) {
                        int option = JOptionPane.showConfirmDialog(this,
                            "File '" + sourceFile.getName() + "' already exists.\nDo you want to replace it?",
                            "File Exists",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                        
                        if (option != JOptionPane.YES_OPTION) {
                            continue;
                        }
                    }
                    
                    Files.copy(sourceFile.toPath(), destFile.toPath(), 
                        StandardCopyOption.REPLACE_EXISTING);
                    imported++;
                    
                    System.out.println("[Import] File imported: " + destFile.getAbsolutePath());
                }
            } catch (Exception e) {
                errors.add(sourceFile.getName() + ": " + e.getMessage());
                System.err.println("[Import] Error importing " + sourceFile.getName() + ": " + e.getMessage());
            }
        }
        
        // Show errors if any
        if (!errors.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Some files could not be imported:\n\n");
            for (String error : errors) {
                errorMsg.append("• ").append(error).append("\n");
            }
            JOptionPane.showMessageDialog(this, errorMsg.toString(), "Import Errors", JOptionPane.WARNING_MESSAGE);
        }
        
        return imported;
    }

    /**
     * Recursively copies a directory and its contents.
     * @param source Source directory
     * @param dest Destination directory
     * @return Number of files copied
     */
    private int copyDirectory(File source, File dest) throws IOException {
        int count = 0;
        
        if (!dest.exists()) {
            dest.mkdirs();
        }
        
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File destFile = new File(dest, file.getName());
                
                if (file.isDirectory()) {
                    count += copyDirectory(file, destFile);
                } else {
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }
        
        return count;
    }

    /**
     * Shows a notification after importing files.
     * @param count Number of files imported
     * @param targetFolder The folder where files were imported
     */
    private void showImportNotification(int count, File targetFolder) {
        String folderName = targetFolder.getName();
        String message = count == 1 
            ? "1 file imported to '" + folderName + "'"
            : count + " files imported to '" + folderName + "'";
        
        // Create a temporary notification label
        JLabel notification = new JLabel(message);
        notification.setOpaque(true);
        notification.setBackground(new Color(60, 140, 60));
        notification.setForeground(Color.WHITE);
        notification.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 180, 80)),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        notification.setFont(notification.getFont().deriveFont(Font.BOLD, 12f));
        
        // Create popup
        JWindow popup = new JWindow(this);
        popup.getContentPane().add(notification);
        popup.pack();
        
        // Position at bottom-right of the editor
        Point location = getLocationOnScreen();
        int x = location.x + getWidth() - popup.getWidth() - 20;
        int y = location.y + getHeight() - popup.getHeight() - 50;
        popup.setLocation(x, y);
        
        popup.setVisible(true);
        
        // Auto-hide after 3 seconds
        javax.swing.Timer hideTimer = new javax.swing.Timer(3000, e -> popup.dispose());
        hideTimer.setRepeats(false);
        hideTimer.start();
        
        System.out.println("[Import] " + message);
    }

    private void createNewFolder() {
        TreePath path = fileTree.getSelectionPath();
        File parentDir = projectRoot;

        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            File file = (File) node.getUserObject();
            parentDir = file.isDirectory() ? file : file.getParentFile();
        }

        String folderName = JOptionPane.showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (folderName != null && !folderName.trim().isEmpty()) {
            File newFolder = new File(parentDir, folderName.trim());
            if (newFolder.mkdir()) {
                refreshFileTree();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to create folder.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void renameSelectedFile() {
        TreePath path = fileTree.getSelectionPath();
        if (path == null)
            return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        File file = (File) node.getUserObject();

        String newName = JOptionPane.showInputDialog(this, "New name:", file.getName());
        if (newName != null && !newName.trim().isEmpty()) {
            File newFile = new File(file.getParentFile(), newName.trim());
            if (file.renameTo(newFile)) {
                refreshFileTree();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to rename.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedFile() {
        TreePath path = fileTree.getSelectionPath();
        if (path == null)
            return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        File file = (File) node.getUserObject();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + file.getName() + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            if (deleteRecursively(file)) {
                refreshFileTree();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    private void openFile(File file) {
        // Check if it's a prefab file - offer to instantiate
        if (file.getName().endsWith(".prefab.json")) {
            String prefabName = file.getName().replace(".prefab.json", "");
            int choice = JOptionPane.showOptionDialog(
                this,
                "Do you want to instantiate the prefab '" + prefabName + "' in the scene?",
                "Instantiate Prefab",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Instantiate", "Open File"},
                "Instantiate"
            );
            
            if (choice == 0) { // Instantiate
                instantiatePrefab(prefabName);
                return;
            }
            // else fall through to open file normally
        }
        
        // Open file in default application
        try {
            java.awt.Desktop.getDesktop().open(file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot open file: " + e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Custom renderer for hierarchy items
     */
    private class HierarchyListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            if (value instanceof GameObject) {
                GameObject obj = (GameObject) value;
                String icon = getIconForType(obj.getType());
                label.setText(icon + " " + obj.getName());
                label.setToolTipText("Type: " + obj.getType() + " | Pos: (" +
                        (int) obj.getX() + ", " + (int) obj.getY() + ")");
                
                // Apply custom name color when not selected
                if (!isSelected) {
                    label.setForeground(obj.getNameColor());
                }
            }

            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

            if (isSelected) {
                label.setBackground(new Color(0, 120, 215));
                label.setForeground(Color.WHITE); // White text when selected for visibility
            } else {
                label.setBackground(new Color(60, 60, 60));
            }

            return label;
        }

        private String getIconForType(String type) {
            switch (type) {
                case "Player":
                    return "🎮";
                case "Enemy":
                    return "👾";
                case "Item":
                    return "📦";
                case "Square":
                    return "⬛";
                case "Circle":
                    return "⚪";
                case "Triangle":
                    return "🔺";
                default:
                    return "🔷";
            }
        }
    }
    
    /**
     * Transfer handler for drag-and-drop reordering in hierarchy
     */
    private class HierarchyTransferHandler extends TransferHandler {
        private int draggedIndex = -1;
        
        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.MOVE;
        }
        
        @Override
        protected Transferable createTransferable(JComponent c) {
            @SuppressWarnings("unchecked")
            JList<GameObject> list = (JList<GameObject>) c;
            draggedIndex = list.getSelectedIndex();
            GameObject selected = list.getSelectedValue();
            
            if (selected == null) return null;
            
            // Use a simple string transferable with the object name
            return new StringSelection(selected.getName());
        }
        
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }
        
        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            
            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            int dropIndex = dl.getIndex();
            
            if (draggedIndex < 0 || draggedIndex == dropIndex) return false;
            
            // Get the object being dragged
            GameObject draggedObj = hierarchyModel.get(draggedIndex);
            
            // Move in game's entity list
            game.moveEntityToIndex(draggedObj, dropIndex);
            
            // Update hierarchy to reflect new order
            updateHierarchy();
            
            // Re-select the dragged object
            hierarchyList.setSelectedValue(draggedObj, true);
            
            System.out.println("Moved '" + draggedObj.getName() + "' to index " + dropIndex);
            
            return true;
        }
        
        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            draggedIndex = -1;
        }
    }

    /**
     * Creates the hierarchy context menu
     */
    private void createHierarchyContextMenu() {
        hierarchyContextMenu = new JPopupMenu();
        
        // ========== CREATE OBJECT SUBMENU ==========
        JMenu createMenu = new JMenu("➕ Create Object");
        
        JMenuItem createSquare = new JMenuItem("⬜ Square");
        createSquare.addActionListener(e -> createPrimitiveObject("Square"));
        
        JMenuItem createCircle = new JMenuItem("⚪ Circle");
        createCircle.addActionListener(e -> createPrimitiveObject("Circle"));
        
        JMenuItem createTriangle = new JMenuItem("🔺 Triangle");
        createTriangle.addActionListener(e -> createPrimitiveObject("Triangle"));
        
        JMenuItem createStar = new JMenuItem("⭐ Star");
        createStar.addActionListener(e -> createPrimitiveObject("Star"));
        
        JMenuItem createPentagon = new JMenuItem("⬠ Pentagon");
        createPentagon.addActionListener(e -> createPrimitiveObject("Pentagon"));
        
        createMenu.add(createSquare);
        createMenu.add(createCircle);
        createMenu.add(createTriangle);
        createMenu.add(createStar);
        createMenu.add(createPentagon);

        JMenuItem renameItem = new JMenuItem("✏ Rename (F2)");
        renameItem.addActionListener(e -> {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null)
                renameObject(selected);
        });

        JMenuItem duplicateItem = new JMenuItem("📋 Duplicate (Ctrl+D)");
        duplicateItem.addActionListener(e -> duplicateSelectedObject());

        JMenuItem copyItem = new JMenuItem("📄 Copy (Ctrl+C)");
        copyItem.addActionListener(e -> copySelectedObject());

        JMenuItem pasteItem = new JMenuItem("📋 Paste (Ctrl+V)");
        pasteItem.addActionListener(e -> pasteObject());

        JMenuItem deleteItem = new JMenuItem("🗑 Delete (Delete)");
        deleteItem.addActionListener(e -> deleteSelectedObject());
        
        JMenuItem addScriptItem = new JMenuItem("📜 Add Script");
        addScriptItem.addActionListener(e -> showAddScriptDialog());
        
        JMenuItem newScriptItem = new JMenuItem("📝 Create New Script");
        newScriptItem.addActionListener(e -> createNewScript());
        
        JMenuItem importImageItem = new JMenuItem("🖼 Import Image");
        importImageItem.addActionListener(e -> importImageForSelectedObject());
        
        // ========== MERGE OBJECTS ==========
        JMenuItem mergeObjectsItem = new JMenuItem("🔗 Merge Selected Objects");
        mergeObjectsItem.addActionListener(e -> mergeSelectedObjects());
        
        // ========== PREFAB OPTIONS ==========
        JMenuItem saveAsPrefabItem = new JMenuItem("📦 Save as Prefab");
        saveAsPrefabItem.addActionListener(e -> saveSelectedAsPrefab());
        
        JMenu instantiatePrefabMenu = new JMenu("📦 Instantiate Prefab");
        instantiatePrefabMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                updateInstantiatePrefabMenu(instantiatePrefabMenu);
            }
            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        
        // ========== ORDER SUBMENU ==========
        JMenu orderMenu = new JMenu("📊 Order");
        
        JMenuItem moveUpItem = new JMenuItem("⬆ Move Up (render on top)");
        moveUpItem.addActionListener(e -> {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null) {
                game.moveEntityUp(selected);
                updateHierarchy();
                hierarchyList.setSelectedValue(selected, true);
            }
        });
        
        JMenuItem moveDownItem = new JMenuItem("⬇ Move Down (render behind)");
        moveDownItem.addActionListener(e -> {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null) {
                game.moveEntityDown(selected);
                updateHierarchy();
                hierarchyList.setSelectedValue(selected, true);
            }
        });
        
        JMenuItem moveToTopItem = new JMenuItem("⏫ Move to Top (front)");
        moveToTopItem.addActionListener(e -> {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null) {
                game.moveEntityToIndex(selected, game.getEntities().size());
                updateHierarchy();
                hierarchyList.setSelectedValue(selected, true);
            }
        });
        
        JMenuItem moveToBottomItem = new JMenuItem("⏬ Move to Bottom (back)");
        moveToBottomItem.addActionListener(e -> {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null) {
                game.moveEntityToIndex(selected, 0);
                updateHierarchy();
                hierarchyList.setSelectedValue(selected, true);
            }
        });
        
        orderMenu.add(moveUpItem);
        orderMenu.add(moveDownItem);
        orderMenu.addSeparator();
        orderMenu.add(moveToTopItem);
        orderMenu.add(moveToBottomItem);

        hierarchyContextMenu.add(createMenu);
        hierarchyContextMenu.add(instantiatePrefabMenu);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(renameItem);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(orderMenu);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(duplicateItem);
        hierarchyContextMenu.add(copyItem);
        hierarchyContextMenu.add(pasteItem);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(mergeObjectsItem);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(saveAsPrefabItem);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(importImageItem);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(addScriptItem);
        hierarchyContextMenu.add(newScriptItem);
        hierarchyContextMenu.addSeparator();
        hierarchyContextMenu.add(deleteItem);
    }
    
    /**
     * Merges multiple selected objects into a single MergedShape object
     */
    private void mergeSelectedObjects() {
        java.util.List<GameObject> selectedObjects = hierarchyList.getSelectedValuesList();
        
        if (selectedObjects.size() < 2) {
            JOptionPane.showMessageDialog(this,
                "Please select at least 2 objects to merge.\n" +
                "Use Shift+Click or Ctrl+Click to select multiple objects.",
                "Merge Objects",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Ask for the merged object name
        String defaultName = "MergedShape";
        int counter = 1;
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (GameObject existing : game.getEntities()) {
            existingNames.add(existing.getName());
        }
        while (existingNames.contains(defaultName)) {
            defaultName = "MergedShape" + counter++;
        }
        
        String name = (String) JOptionPane.showInputDialog(this,
            "Enter name for the merged object:",
            "Merge " + selectedObjects.size() + " Objects",
            JOptionPane.PLAIN_MESSAGE,
            null, null, defaultName);
        
        if (name == null || name.trim().isEmpty()) {
            return; // User cancelled
        }
        
        // Create the merged shape
        MergedShape merged = MergedShape.createFromObjects(selectedObjects, game);
        merged.setName(name.trim());
        
        // Remove original objects from the scene
        for (GameObject obj : selectedObjects) {
            game.removeEntity(obj);
        }
        
        // Add the merged object
        game.addEntity(merged);
        updateHierarchy();
        hierarchyList.setSelectedValue(merged, true);
        
        System.out.println("[Editor] Merged " + selectedObjects.size() + " objects into '" + name + "'");
    }
    
    /**
     * Creates a primitive object of the specified type
     */
    private void createPrimitiveObject(String type) {
        if (currentProject == null) {
            JOptionPane.showMessageDialog(this,
                "Please open or create a project first.",
                "No Project",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Create the object using EntityFactory
        GameObject obj = EntityFactory.create(type);
        
        // Generate unique name
        String baseName = type;
        int counter = 1;
        String name = baseName;
        
        // Check for existing names
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (GameObject existing : game.getEntities()) {
            existingNames.add(existing.getName());
        }
        
        while (existingNames.contains(name)) {
            name = baseName + " (" + counter + ")";
            counter++;
        }
        
        // Set properties
        obj.setName(name);
        obj.setGame(game);
        
        // Position at world origin (0,0), centered on the origin
        int objSize = 50;
        obj.setX(-objSize / 2);  // Center on origin
        obj.setY(-objSize / 2);  // Center on origin
        obj.setWidth(objSize);
        obj.setHeight(objSize);
        
        // Add to game
        game.addEntity(obj);
        
        // Salvar ação para undo
        pushUndoAction(new CreateObjectUndoAction(obj));
        
        // Update hierarchy and select the new object
        updateHierarchy();
        hierarchyList.setSelectedValue(obj, true);
        game.setSelectedObject(obj);
        updateInspector(obj);
        
        System.out.println("Created new " + type + ": " + name);
    }
    
    /**
     * Saves the currently selected object as a prefab
     */
    private void saveSelectedAsPrefab() {
        GameObject selected = hierarchyList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an object to save as prefab.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (prefabManager == null) {
            JOptionPane.showMessageDialog(this,
                "Please open or create a project first.",
                "No Project",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Ask for prefab name (default to object name)
        String defaultName = selected.getName().replaceAll("\\s*\\(\\d+\\)$", "").trim();
        String prefabName = (String) JOptionPane.showInputDialog(
            this,
            "Enter a name for the prefab:",
            "Save as Prefab",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            defaultName
        );
        
        if (prefabName == null || prefabName.trim().isEmpty()) {
            return; // User cancelled
        }
        
        // Check if prefab already exists
        if (prefabManager.prefabExists(prefabName)) {
            int overwrite = JOptionPane.showConfirmDialog(
                this,
                "A prefab named '" + prefabName + "' already exists.\nDo you want to overwrite it?",
                "Prefab Exists",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        // Save the prefab
        boolean success = prefabManager.savePrefab(selected, prefabName);
        
        if (success) {
            JOptionPane.showMessageDialog(this,
                "Prefab '" + prefabName + "' saved successfully!",
                "Prefab Saved",
                JOptionPane.INFORMATION_MESSAGE);
            
            // Refresh file tree to show new prefab
            refreshFileTree();
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to save prefab.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Updates the "Instantiate Prefab" submenu with available prefabs
     */
    private void updateInstantiatePrefabMenu(JMenu menu) {
        menu.removeAll();
        
        if (prefabManager == null) {
            JMenuItem noPrefabs = new JMenuItem("(No project loaded)");
            noPrefabs.setEnabled(false);
            menu.add(noPrefabs);
            return;
        }
        
        java.util.List<String> prefabs = prefabManager.listPrefabs();
        
        if (prefabs.isEmpty()) {
            JMenuItem noPrefabs = new JMenuItem("(No prefabs available)");
            noPrefabs.setEnabled(false);
            menu.add(noPrefabs);
            return;
        }
        
        for (String prefabName : prefabs) {
            JMenuItem prefabItem = new JMenuItem("📦 " + prefabName);
            prefabItem.addActionListener(e -> instantiatePrefab(prefabName));
            menu.add(prefabItem);
        }
        
        menu.addSeparator();
        
        // Add option to delete prefabs
        JMenu deletePrefabMenu = new JMenu("🗑 Delete Prefab");
        for (String prefabName : prefabs) {
            JMenuItem deleteItem = new JMenuItem("❌ " + prefabName);
            deleteItem.addActionListener(e -> deletePrefab(prefabName));
            deletePrefabMenu.add(deleteItem);
        }
        menu.add(deletePrefabMenu);
    }
    
    /**
     * Instantiates a prefab in the game world
     */
    private void instantiatePrefab(String prefabName) {
        if (prefabManager == null) {
            JOptionPane.showMessageDialog(this,
                "Please open or create a project first.",
                "No Project",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Instantiate at center of viewport
        GameObject instance = prefabManager.instantiatePrefab(prefabName, 400, 300);
        
        if (instance != null) {
            game.addEntity(instance);
            updateHierarchy();
            hierarchyList.setSelectedValue(instance, true);
            game.setSelectedObject(instance);
            updateInspector(instance);
            
            System.out.println("Instantiated prefab: " + prefabName + " as " + instance.getName());
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to instantiate prefab '" + prefabName + "'.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Deletes a prefab
     */
    private void deletePrefab(String prefabName) {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete the prefab '" + prefabName + "'?\nThis action cannot be undone.",
            "Delete Prefab",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            if (prefabManager.deletePrefab(prefabName)) {
                JOptionPane.showMessageDialog(this,
                    "Prefab '" + prefabName + "' deleted.",
                    "Prefab Deleted",
                    JOptionPane.INFORMATION_MESSAGE);
                refreshFileTree();
            } else {
                JOptionPane.showMessageDialog(this,
                    "Failed to delete prefab.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Manipula o menu de contexto
     */
    private void handleHierarchyContextMenu(MouseEvent e) {
        if (e.isPopupTrigger()) {
            int index = hierarchyList.locationToIndex(e.getPoint());
            if (index >= 0) {
                // Only change selection if clicked item is not already selected
                // This preserves multi-selection when right-clicking
                int[] selectedIndices = hierarchyList.getSelectedIndices();
                boolean isAlreadySelected = false;
                for (int selectedIndex : selectedIndices) {
                    if (selectedIndex == index) {
                        isAlreadySelected = true;
                        break;
                    }
                }
                if (!isAlreadySelected) {
                    hierarchyList.setSelectedIndex(index);
                }
            }
            hierarchyContextMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /**
     * Manipula atalhos de teclado na hierarquia
     */
    private void handleHierarchyKeyPress(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F2) {
            GameObject selected = hierarchyList.getSelectedValue();
            if (selected != null)
                renameObject(selected);
        } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
            deleteSelectedObject();
        } else if (e.isControlDown()) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_C:
                    copySelectedObject();
                    break;
                case KeyEvent.VK_V:
                    pasteObject();
                    break;
                case KeyEvent.VK_D:
                    duplicateSelectedObject();
                    break;
                case KeyEvent.VK_Z:
                    performUndo();
                    break;
            }
        }
    }

    /**
     * Renames an object
     */
    private void renameObject(GameObject obj) {
        String oldName = obj.getName();
        String newName = JOptionPane.showInputDialog(
                this,
                "New name for '" + obj.getName() + "':",
                "Rename Object",
                JOptionPane.PLAIN_MESSAGE);

        if (newName != null && !newName.trim().isEmpty()) {
            // Salvar para undo
            pushUndoAction(new RenameUndoAction(obj, oldName));
            
            obj.setName(newName.trim());
            updateHierarchy();
            // Re-select the object
            hierarchyList.setSelectedValue(obj, true);
        }
    }

    /**
     * Copies the selected object to clipboard
     */
    private void copySelectedObject() {
        GameObject selected = hierarchyList.getSelectedValue();
        if (selected != null) {
            clipboardObject = selected;
        }
    }

    /**
     * Pastes the object from clipboard
     */
    private void pasteObject() {
        if (clipboardObject != null) {
            try {
                // Create copy of object
                GameObject copy = createCopyOfObject(clipboardObject);
                if (copy != null) {
                    // Offset to avoid overlap
                    copy.setX(clipboardObject.getX() + 20);
                    copy.setY(clipboardObject.getY() + 20);
                    game.addEntity(copy);
                    updateHierarchy();
                    game.setSelectedObject(copy);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error pasting object: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Duplicates the selected object
     */
    private void duplicateSelectedObject() {
        GameObject selected = hierarchyList.getSelectedValue();
        if (selected != null) {
            clipboardObject = selected;
            pasteObject();
        }
    }

    /**
     * Deletes the selected object
     */
    private void deleteSelectedObject() {
        GameObject selected = hierarchyList.getSelectedValue();
        if (selected != null) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete '" + selected.getName() + "'?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                // Salvar ação para undo (guarda o índice para restaurar na mesma posição)
                int index = game.getEntities().indexOf(selected);
                pushUndoAction(new DeleteObjectUndoAction(selected, index));
                
                game.removeEntity(selected);
                game.setSelectedObject(null);
                updateHierarchy();
            }
        }
    }

    /**
     * Creates a copy of a GameObject
     */
    private GameObject createCopyOfObject(GameObject original) {
        try {
            // Use EntityFactory to create copy
            GameObject copy = EntityFactory.create(original.getType());
            if (copy != null) {
                copy.setName(original.getName() + " (Copy)");
                copy.setX(original.getX());
                copy.setY(original.getY());
                copy.setWidth(original.getWidth());
                copy.setHeight(original.getHeight());
                copy.setGame(game);
                copy.setSpritePath(original.getSpritePath());

                // Copy specific properties
                JSONObject props = original.saveProperties();
                copy.loadProperties(props);

                return copy;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ==================== WORLD CONTROL TOOLBAR ====================

    /**
     * Creates the world control toolbar (Play, Pause, Stop)
     */
    private JPanel createWorldControlToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        toolbar.setBackground(new Color(50, 50, 50));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 30, 30)));

        // Button style
        Dimension buttonSize = new Dimension(80, 30);
        Font buttonFont = new Font("Dialog", Font.BOLD, 14);

        // Play button
        playButton = new JButton("▶");
        playButton.setPreferredSize(buttonSize);
        playButton.setFont(buttonFont);
        playButton.setToolTipText("Start world simulation");
        playButton.setBackground(new Color(40, 167, 69));
        playButton.setForeground(Color.WHITE);
        playButton.setFocusPainted(false);
        playButton.setFocusable(false);
        playButton.setBorderPainted(false);
        playButton.setContentAreaFilled(true);
        playButton.setModel(new javax.swing.DefaultButtonModel() {
            @Override
            protected void fireStateChanged() {
                // Skip focus painting
            }
        });
        playButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        playButton.addActionListener(e -> onPlayWorld());

        // Pause button
        pauseButton = new JButton("⏸");
        pauseButton.setPreferredSize(buttonSize);
        pauseButton.setFont(buttonFont);
        pauseButton.setToolTipText("Pause world simulation");
        pauseButton.setBackground(new Color(255, 193, 7));
        pauseButton.setForeground(Color.BLACK);
        pauseButton.setFocusPainted(false);
        pauseButton.setFocusable(false);
        pauseButton.setBorderPainted(false);
        pauseButton.setContentAreaFilled(true);
        pauseButton.setModel(new javax.swing.DefaultButtonModel() {
            @Override
            protected void fireStateChanged() {
                // Skip focus painting
            }
        });
        pauseButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> onPauseWorld());

        // Stop button
        stopButton = new JButton("⏹");
        stopButton.setPreferredSize(buttonSize);
        stopButton.setFont(buttonFont);
        stopButton.setToolTipText("Stop and reset world simulation");
        stopButton.setBackground(new Color(220, 53, 69));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.setFocusable(false);
        stopButton.setBorderPainted(false);
        stopButton.setContentAreaFilled(true);
        stopButton.setModel(new javax.swing.DefaultButtonModel() {
            @Override
            protected void fireStateChanged() {
                // Skip focus painting
            }
        });
        stopButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> onStopWorld());

        toolbar.add(playButton);
        toolbar.add(pauseButton);
        toolbar.add(stopButton);
        
        // ==================== CAMERA CONTROLS ====================
        toolbar.add(Box.createHorizontalStrut(30));
        
        JLabel cameraLabel = new JLabel("Camera:");
        cameraLabel.setForeground(Color.WHITE);
        toolbar.add(cameraLabel);
        
        // Zoom out button with custom icon
        JButton zoomOutBtn = createIconButton(new Dimension(30, 30), "Zoom Out (Ctrl+-)", 
            new Color(80, 80, 80), new Color(100, 100, 100), (g, w, h) -> {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2));
                // Draw minus sign
                g.drawLine(w/2 - 6, h/2, w/2 + 6, h/2);
            });
        zoomOutBtn.addActionListener(e -> zoomCamera(0.8));
        toolbar.add(zoomOutBtn);
        
        // Zoom level label
        JLabel zoomLabel = new JLabel("100%");
        zoomLabel.setForeground(Color.WHITE);
        zoomLabel.setPreferredSize(new Dimension(50, 30));
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);
        zoomLabel.setName("zoomLabel");
        toolbar.add(zoomLabel);
        
        // Zoom in button with custom icon
        JButton zoomInBtn = createIconButton(new Dimension(30, 30), "Zoom In (Ctrl++)", 
            new Color(80, 80, 80), new Color(100, 100, 100), (g, w, h) -> {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2));
                // Draw plus sign
                g.drawLine(w/2 - 6, h/2, w/2 + 6, h/2);
                g.drawLine(w/2, h/2 - 6, w/2, h/2 + 6);
            });
        zoomInBtn.addActionListener(e -> zoomCamera(1.25));
        toolbar.add(zoomInBtn);
        
        // Reset camera button with custom icon (camera icon)
        JButton resetCamBtn = createIconButton(new Dimension(30, 30), "Reset Camera to Origin (Home)", 
            new Color(80, 80, 80), new Color(100, 100, 100), (g, w, h) -> {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.5f));
                // Draw camera body
                g.drawRoundRect(w/2 - 8, h/2 - 4, 12, 8, 2, 2);
                // Draw lens
                g.fillOval(w/2 + 4, h/2 - 2, 5, 5);
                // Draw viewfinder
                g.drawRect(w/2 - 5, h/2 - 6, 4, 3);
            });
        resetCamBtn.addActionListener(e -> resetCamera());
        toolbar.add(resetCamBtn);
        
        // Focus on selected button with custom icon
        JButton focusBtn = createIconButton(new Dimension(30, 30), "Focus on Selected (F)", 
            new Color(80, 80, 80), new Color(100, 100, 100), (g, w, h) -> {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.5f));
                // Draw crosshair/focus icon
                int cx = w/2, cy = h/2;
                g.drawOval(cx - 6, cy - 6, 12, 12);
                g.drawLine(cx, cy - 9, cx, cy - 3);
                g.drawLine(cx, cy + 3, cx, cy + 9);
                g.drawLine(cx - 9, cy, cx - 3, cy);
                g.drawLine(cx + 3, cy, cx + 9, cy);
            });
        focusBtn.addActionListener(e -> focusCameraOnSelected());
        toolbar.add(focusBtn);
        
        // Camera position display
        JLabel camPosLabel = new JLabel("(0, 0)");
        camPosLabel.setForeground(new Color(150, 200, 255));
        camPosLabel.setPreferredSize(new Dimension(100, 30));
        camPosLabel.setHorizontalAlignment(SwingConstants.CENTER);
        camPosLabel.setName("camPosLabel");
        toolbar.add(camPosLabel);

        return toolbar;
    }
    
    /**
     * Zooms the camera by the given factor.
     */
    private void zoomCamera(double factor) {
        Camera cam = game.getMainCamera();
        if (cam != null) {
            cam.setZoom(cam.getZoom() * factor);
            updateCameraLabels();
            game.repaint();
        }
    }
    
    /**
     * Resets the camera to origin with default zoom.
     */
    private void resetCamera() {
        Camera cam = game.getMainCamera();
        if (cam != null) {
            cam.setPosition(0, 0);
            cam.setZoom(1.0);
            cam.setRotation(0);
            updateCameraLabels();
            game.repaint();
        }
    }
    
    /**
     * Updates the camera zoom and position labels in the toolbar.
     */
    private void updateCameraLabels() {
        Camera cam = game.getMainCamera();
        if (cam == null) return;
        
        // Find labels by name
        Component[] components = ((JPanel) getContentPane().getComponent(0)).getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                updateCameraLabelsInPanel((JPanel) comp, cam);
            }
        }
    }
    
    private void updateCameraLabelsInPanel(JPanel panel, Camera cam) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                if ("zoomLabel".equals(label.getName())) {
                    label.setText(String.format("%.0f%%", cam.getZoom() * 100));
                } else if ("camPosLabel".equals(label.getName())) {
                    label.setText(String.format("(%.0f, %.0f)", cam.getX(), cam.getY()));
                }
            } else if (comp instanceof JPanel) {
                updateCameraLabelsInPanel((JPanel) comp, cam);
            }
        }
    }

    /**
     * Play button action
     */
    private void onPlayWorld() {
        Game.GameState currentState = game.getGameState();

        if (currentState == Game.GameState.PAUSED) {
            // Resume from pause
            game.resumeWorld();
        } else {
            // Recompilar todos os scripts antes de iniciar
            if (scriptManager != null) {
                System.out.println("Recompiling all scripts...");
                int compiled = scriptManager.compileAllScripts();
                System.out.println("Compiled " + compiled + " scripts.");
                
                // Recriar instâncias dos scripts para pegar as novas classes
                reloadAllScriptInstances();
            }
            
            // Start from editing mode
            game.playWorld();
        }
        
        // Start inspector update timer while game is running
        startInspectorUpdateTimer();

        updateControlButtons();
    }
    
    /**
     * Reloads all script instances in all game objects to use newly compiled classes
     */
    private void reloadAllScriptInstances() {
        if (scriptManager == null) return;
        
        Scene currentScene = (currentProject != null) ? currentProject.getCurrentScene() : null;
        
        for (GameObject obj : game.getEntities()) {
            List<String> scriptNames = new ArrayList<>(obj.getScriptNames());
            
            // Remove all current script instances
            obj.getScripts().clear();
            
            // Recreate script instances with new classes
            for (String scriptName : scriptNames) {
                IgnisScript newInstance = scriptManager.createScriptInstance(scriptName, obj, game);
                if (newInstance != null) {
                    obj.getScripts().add(newInstance);
                    
                    // Apply any pending script variables from scene load
                    if (currentScene != null) {
                        currentScene.applyPendingScriptVariables(obj, newInstance);
                    }
                }
            }
        }
        
        // Update inspector to show new script instances
        GameObject selected = game.getSelectedObject();
        if (selected != null) {
            updateInspectorScripts(selected);
        }
    }

    /**
     * Pause button action
     */
    private void onPauseWorld() {
        game.pauseWorld();
        updateControlButtons();
    }

    /**
     * Stop button action
     */
    private void onStopWorld() {
        game.stopWorld();
        stopInspectorUpdateTimer();
        updateControlButtons();
        updateHierarchy(); // Update hierarchy to reflect restored positions
        
        // Update inspector with restored positions
        GameObject selected = game.getSelectedObject();
        if (selected != null) {
            updateInspector(selected);
        }
    }

    /**
     * Starts the timer that updates the inspector during gameplay
     */
    private void startInspectorUpdateTimer() {
        if (inspectorUpdateTimer == null) {
            inspectorUpdateTimer = new javax.swing.Timer(50, e -> {
                // Only update if game is running and not editing
                if (game.isWorldRunning() && !isUserEditingInspector) {
                    GameObject selected = game.getSelectedObject();
                    if (selected != null) {
                        // Update position fields without triggering full refresh
                        isUpdatingInspector = true;
                        inspectorPosXField.setText(String.format("%.1f", selected.getX()));
                        inspectorPosYField.setText(String.format("%.1f", selected.getY()));
                        inspectorRotationField.setText(String.format("%.1f", selected.getRotation()));
                        isUpdatingInspector = false;
                    }
                }
            });
        }
        inspectorUpdateTimer.start();
    }
    
    /**
     * Stops the inspector update timer
     */
    private void stopInspectorUpdateTimer() {
        if (inspectorUpdateTimer != null) {
            inspectorUpdateTimer.stop();
        }
    }
    
    /**
     * Updates the visual state of control buttons
     */
    private void updateControlButtons() {
        Game.GameState state = game.getGameState();

        switch (state) {
            case EDITING:
                playButton.setBackground(new Color(40, 167, 69));
                playButton.setEnabled(true);
                pauseButton.setEnabled(false);
                stopButton.setEnabled(false);
                break;

            case PLAYING:
                playButton.setBackground(new Color(30, 130, 55));
                playButton.setEnabled(false);
                pauseButton.setEnabled(true);
                stopButton.setEnabled(true);
                break;

            case PAUSED:
                playButton.setBackground(new Color(40, 167, 69));
                playButton.setEnabled(true);
                pauseButton.setEnabled(false);
                pauseButton.setBackground(new Color(200, 150, 7));
                stopButton.setEnabled(true);
                break;
        }

        // Reset pause button color when not paused
        if (state != Game.GameState.PAUSED) {
            pauseButton.setBackground(new Color(255, 193, 7));
        }
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        // New Project
        JMenuItem newProjectItem = new JMenuItem("New Project");
        newProjectItem.addActionListener(e -> newProject());
        fileMenu.add(newProjectItem);

        // Open Project
        JMenuItem openProjectItem = new JMenuItem("Open Project");
        openProjectItem.addActionListener(e -> openProject());
        fileMenu.add(openProjectItem);

        // Save Project
        JMenuItem saveProjectItem = new JMenuItem("Save Project");
        saveProjectItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveProjectItem.addActionListener(e -> saveProject());
        fileMenu.add(saveProjectItem);

        // Save Project As
        JMenuItem saveAsItem = new JMenuItem("Save Project As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveProjectAs());
        fileMenu.add(saveAsItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            dispose();
            System.exit(0);
        });
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // ==================== BUILD MENU ====================
        JMenu buildMenu = new JMenu("Build");

        JMenuItem buildProjectItem = new JMenuItem("Build Project...");
        buildProjectItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        buildProjectItem.addActionListener(e -> openBuildDialog());
        buildMenu.add(buildProjectItem);

        menuBar.add(buildMenu);

        // ==================== TOOLS MENU ====================
        JMenu toolsMenu = new JMenu("Tools");

        JMenuItem imageEditorItem = new JMenuItem("Image Editor");
        imageEditorItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        imageEditorItem.addActionListener(e -> openImageEditor());
        toolsMenu.add(imageEditorItem);

        JMenuItem animationEditorItem = new JMenuItem("Animation Editor");
        animationEditorItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        animationEditorItem.addActionListener(e -> openAnimationEditor());
        toolsMenu.add(animationEditorItem);

        menuBar.add(toolsMenu);

        // ==================== VIEW MENU (CAMERA CONTROLS) ====================
        JMenu viewMenu = new JMenu("View");
        
        // Zoom In
        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        zoomInItem.addActionListener(e -> zoomCamera(1.25));
        viewMenu.add(zoomInItem);
        
        // Zoom Out
        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        zoomOutItem.addActionListener(e -> zoomCamera(0.8));
        viewMenu.add(zoomOutItem);
        
        // Zoom to 100%
        JMenuItem zoom100Item = new JMenuItem("Zoom to 100%");
        zoom100Item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        zoom100Item.addActionListener(e -> {
            Camera cam = game.getMainCamera();
            if (cam != null) {
                cam.setZoom(1.0);
                updateCameraLabels();
                game.repaint();
            }
        });
        viewMenu.add(zoom100Item);
        
        viewMenu.addSeparator();
        
        // Reset Camera
        JMenuItem resetCamItem = new JMenuItem("Reset Camera");
        resetCamItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
        resetCamItem.addActionListener(e -> resetCamera());
        viewMenu.add(resetCamItem);
        
        // Focus on Selected Object
        JMenuItem focusSelectedItem = new JMenuItem("Focus on Selected");
        focusSelectedItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0));
        focusSelectedItem.addActionListener(e -> focusCameraOnSelected());
        viewMenu.add(focusSelectedItem);
        
        viewMenu.addSeparator();
        
        // Toggle Grid
        JCheckBoxMenuItem showGridItem = new JCheckBoxMenuItem("Show Grid");
        showGridItem.setSelected(game.isShowGrid());
        showGridItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK));
        showGridItem.addActionListener(e -> {
            game.setShowGrid(showGridItem.isSelected());
            game.repaint();
        });
        viewMenu.add(showGridItem);
        
        // Grid Size submenu
        JMenu gridSizeMenu = new JMenu("Grid Size");
        int[] gridSizes = {16, 32, 64, 128};
        ButtonGroup gridSizeGroup = new ButtonGroup();
        for (int size : gridSizes) {
            JRadioButtonMenuItem sizeItem = new JRadioButtonMenuItem(size + " px");
            sizeItem.setSelected(game.getGridSize() == size);
            sizeItem.addActionListener(e -> {
                game.setGridSize(size);
                game.repaint();
            });
            gridSizeGroup.add(sizeItem);
            gridSizeMenu.add(sizeItem);
        }
        viewMenu.add(gridSizeMenu);
        
        viewMenu.addSeparator();
        
        // Show World Origin
        JCheckBoxMenuItem showOriginItem = new JCheckBoxMenuItem("Show Origin");
        showOriginItem.setSelected(true);
        showOriginItem.addActionListener(e -> {
            // Origin is always shown in editor mode currently
            game.repaint();
        });
        viewMenu.add(showOriginItem);
        
        // Show Colliders (Debug)
        JCheckBoxMenuItem showCollidersItem = new JCheckBoxMenuItem("Show Colliders");
        showCollidersItem.setSelected(game.isShowColliders());
        showCollidersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        showCollidersItem.addActionListener(e -> {
            game.setShowColliders(showCollidersItem.isSelected());
            game.repaint();
        });
        viewMenu.add(showCollidersItem);
        
        menuBar.add(viewMenu);
        
        setJMenuBar(menuBar);
    }

    /**
     * Opens the integrated image editor. When a project is loaded, textures
     * exported from it land directly in project/assets/sprites.
     */
    private void openImageEditor() {
        File spritesFolder = null;
        if (currentProject != null && currentProject.getProjectFile() != null) {
            File projectFolder = IgnisProjectIO.getProjectFolder(currentProject.getProjectFile());
            spritesFolder = new File(projectFolder, "assets/sprites");
        }
        com.ignis.imageeditor.ImageEditorFrame imageEditor =
                new com.ignis.imageeditor.ImageEditorFrame(spritesFolder);
        imageEditor.setVisible(true);
    }

    /**
     * Opens the animation editor. Frames are picked from the project's sprites,
     * saved to project/assets/animations, and an Animator can be assigned to the
     * currently selected object.
     */
    private void openAnimationEditor() {
        File projectFolder = null;
        File spritesFolder = null;
        if (currentProject != null && currentProject.getProjectFile() != null) {
            projectFolder = IgnisProjectIO.getProjectFolder(currentProject.getProjectFile());
            spritesFolder = new File(projectFolder, "assets/sprites");
        }
        AnimationEditorFrame animationEditor =
                new AnimationEditorFrame(projectFolder, spritesFolder, game.getSelectedObject());
        animationEditor.setVisible(true);
    }

    /**
     * Opens the Build dialog for the current project.
     * Requires a saved project (.ignis file on disk).
     */
    private void openBuildDialog() {
        if (currentProject == null || currentProject.getProjectFile() == null) {
            JOptionPane.showMessageDialog(this,
                    "Open or save a project before building.",
                    "Build Project",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        BuildDialog dialog = new BuildDialog(this,
                currentProject.getProjectFile(),
                currentProject.getProjectName());
        dialog.setVisible(true);
    }

    /**
     * Focuses the camera on the currently selected object.
     */
    private void focusCameraOnSelected() {
        GameObject selected = game.getSelectedObject();
        Camera cam = game.getMainCamera();
        if (selected != null && cam != null) {
            double centerX = selected.getX() + selected.getWidth() / 2.0;
            double centerY = selected.getY() + selected.getHeight() / 2.0;
            cam.setPosition(centerX, centerY);
            updateCameraLabels();
            game.repaint();
        }
    }

    // ==================== ALERT SYSTEM METHODS ====================
    
    /**
     * Exibe uma mensagem de alerta na tela do editor
     * A mensagem aparecerá no canto superior esquerdo por 3 segundos
     * @param message A mensagem a ser exibida
     */
    public void alert(String message) {
        alertQueue.offer(new AlertMessage(message));
        
        // Remove alertas expirados
        while (!alertQueue.isEmpty() && alertQueue.peek().isExpired()) {
            alertQueue.poll();
        }
        
        // Limita o número de alertas na tela
        while (alertQueue.size() > MAX_ALERTS_ON_SCREEN) {
            alertQueue.poll();
        }
    }
    
    /**
     * Obtém a lista de alertas ativos para renderização
     * Remove alertas expirados automaticamente
     */
    protected java.util.List<AlertMessage> getActiveAlerts() {
        java.util.List<AlertMessage> activeAlerts = new java.util.ArrayList<>();
        
        // Remove alertas expirados e coleta os ativos
        Iterator<AlertMessage> it = alertQueue.iterator();
        while (it.hasNext()) {
            AlertMessage alert = it.next();
            if (alert.isExpired()) {
                it.remove();
            } else {
                activeAlerts.add(alert);
            }
        }
        
        return activeAlerts;
    }
    
    // ==================== PROJECT MANAGEMENT ====================

    /**
     * Creates a new project
     */
    private void newProject() {
        if (showNewProjectDialog()) {
            projectLoaded = true;
            updateHierarchy();
        }
    }

    /**
     * Opens an existing project (.ignis)
     */
    private void openProject() {
        if (showOpenProjectDialog()) {
            projectLoaded = true;
            updateHierarchy();
        }
    }

    /**
     * Saves the current project
     */
    private void saveProject() {
        if (currentProject == null || currentProject.getProjectFile() == null) {
            saveProjectAs();
        } else {
            doSaveProject(currentProject.getProjectFile());
        }
    }

    /**
     * Saves the project to a new file
     */
    private void saveProjectAs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Project");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Ignis Project (*.ignis)", "ignis"));
        fileChooser.setSelectedFile(new File(currentProject.getProjectName() + ".ignis"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            // Ensure .ignis extension
            if (!selectedFile.getName().endsWith(".ignis")) {
                selectedFile = new File(selectedFile.getAbsolutePath() + ".ignis");
            }

            doSaveProject(selectedFile);
        }
    }

    /**
     * Executes the project save
     */
    private void doSaveProject(File file) {
        try {
            // Sync Game entities with the Scene
            Scene scene = currentProject.getCurrentScene();
            scene.getEntities().clear();
            for (GameObject entity : game.getEntities()) {
                scene.addEntity(entity);
            }

            // Save
            IgnisProjectIO.save(currentProject, file);

            // Update file browser to project directory
            updateProjectRoot();

            JOptionPane.showMessageDialog(this,
                    "Project saved successfully!",
                    "Save Project",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error saving project: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Updates the Hierarchy panel with current entities
     */
    private void updateHierarchy() {
        // Preserve current selection
        GameObject currentSelection = hierarchyList.getSelectedValue();
        
        hierarchyModel.clear();
        for (GameObject entity : game.getEntities()) {
            hierarchyModel.addElement(entity);
        }
        hierarchyList.setModel(hierarchyModel);
        
        // Restore selection if object still exists
        if (currentSelection != null && hierarchyModel.contains(currentSelection)) {
            hierarchyList.setSelectedValue(currentSelection, true);
        }
        
        // Force repaint to update display (e.g., name colors)
        hierarchyList.repaint();
    }

    private void loadLayout() {
        isLoading = true;
        try {
            File file = new File(SETTINGS_FILE);
            if (!file.exists()) {
                // Default values
                mainSplit.setDividerLocation(250);
                rightSplit.setDividerLocation(0.75);
                return;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();

            JSONObject layout = new JSONObject(json.toString());
            mainSplit.setDividerLocation(layout.getInt("hierarchyWidth"));
            rightSplit.setDividerLocation(layout.getInt("inspectorWidth"));
        } catch (Exception e) {
            // On error, use default values
            mainSplit.setDividerLocation(250);
            rightSplit.setDividerLocation(0.75);
        } finally {
            isLoading = false;
        }
    }

    private void addDividerListeners() {
        mainSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            if (!isLoading)
                saveLayout();
        });
        rightSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            if (!isLoading)
                saveLayout();
        });
    }

    private void saveLayout() {
        try {
            JSONObject layout = new JSONObject();
            layout.put("hierarchyWidth", mainSplit.getDividerLocation());
            layout.put("inspectorWidth", rightSplit.getDividerLocation());

            FileWriter writer = new FileWriter(SETTINGS_FILE);
            writer.write(layout.toString(2));
            writer.close();
        } catch (Exception e) {
            // Silent failure on save
        }
    }

    public static void main(String[] args) {
        Game game = new Game();
        // Project will be created/loaded by the initialization dialog
        new Editor(game);
        game.start();
    }
}
