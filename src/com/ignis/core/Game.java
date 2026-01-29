package com.ignis.core;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.Polygon;
import java.awt.Cursor;
import java.awt.Robot;
import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.image.BufferStrategy;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.ignis.core.ui.UICanvas;

public class Game extends Canvas implements Runnable {

    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    
    // ==================== CAMERA SYSTEM ====================
    // Main camera for rendering
    private Camera mainCamera;
    
    // Viewport for the game view
    private Viewport viewport;
    
    // List of all cameras (for multi-camera support)
    private List<Camera> cameras = new ArrayList<>();
    
    // Flag to indicate if we're in editor camera mode (free camera)
    private boolean editorCameraMode = true;
    
    // Grid display settings
    private boolean showGrid = false;
    private int gridSize = 32; // Grid cell size in world units
    private Color gridColor = new Color(255, 255, 255, 30); // Semi-transparent white

    private Thread thread;
    private boolean isRunning = false;
    
    // ScriptManager para gerenciar scripts
    private ScriptManager scriptManager;
    
    // PrefabManager para instanciar prefabs
    private PrefabManager prefabManager;
    
    // Collision system
    private IgnisSampleCollisions.CollisionManager collisionManager;
    private boolean showColliders = false; // Debug view for colliders
    
    // ==================== UI SYSTEM ====================
    // Canvas de interface do usuário
    private UICanvas uiCanvas;

    // Game states: EDITING, PLAYING, PAUSED
    public enum GameState {
        EDITING, // Editor mode - no simulation
        PLAYING, // World running
        PAUSED // World paused
    }

    private GameState gameState = GameState.EDITING;

    // Snapshot of initial object positions
    private Map<String, EntitySnapshot> initialSnapshots = new HashMap<>();
    
    // Lista de objetos criados em runtime (durante o jogo)
    // Estes objetos serão removidos quando o jogo parar
    private List<GameObject> runtimeObjects = new ArrayList<>();

    // ==================== SELECTION SYSTEM ====================
    private GameObject selectedObject = null;
    private List<SelectionListener> selectionListeners = new ArrayList<>();

    // ==================== TOOL SYSTEM ====================
    public enum ToolType {
        MOVE, // Move objects
        ROTATE, // Rotate objects
        SCALE // Scale objects
    }

    private ToolType currentTool = ToolType.MOVE;

    // Base gizmo settings (will be scaled based on zoom)
    private static final int BASE_GIZMO_SIZE = 60;
    private static final int BASE_GIZMO_ARROW_SIZE = 12;
    private static final int BASE_GIZMO_HIT_AREA = 15;
    private static final int BASE_ROTATE_GIZMO_RADIUS = 50;
    
    /**
     * Gets the current gizmo size scaled by camera zoom.
     * At lower zoom levels, gizmos appear larger in world space to remain usable.
     */
    private int getScaledGizmoSize() {
        Camera cam = getActiveCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_SIZE / zoom);
    }
    
    private int getScaledGizmoArrowSize() {
        Camera cam = getActiveCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_ARROW_SIZE / zoom);
    }
    
    private int getScaledGizmoHitArea() {
        Camera cam = getActiveCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_HIT_AREA / zoom);
    }
    
    private int getScaledRotateGizmoRadius() {
        Camera cam = getActiveCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_ROTATE_GIZMO_RADIUS / zoom);
    }

    // Gizmo drag states
    private enum GizmoDragMode {
        NONE, AXIS_X, AXIS_Y, CENTER, ROTATE, SCALE_X, SCALE_Y, SCALE_UNIFORM
    }

    private GizmoDragMode currentDragMode = GizmoDragMode.NONE;
    private int dragStartX, dragStartY;
    private double objectStartX, objectStartY;
    private double objectStartRotation;
    private int objectStartWidth, objectStartHeight;
    
    // Robot for infinite drag (mouse warping)
    private Robot robot;
    private int accumulatedDragX, accumulatedDragY; // Accumulated drag distance
    private int lastMouseX, lastMouseY; // Last mouse position for delta calculation
    private boolean isWarping = false; // Flag to ignore warp events
    private static final int WARP_MARGIN = 10; // Pixels from edge to trigger warp

    // Interface to notify selection changes
    public interface SelectionListener {
        void onSelectionChanged(GameObject selected);
    }
    
    // Interface to notify transform changes (for undo system)
    public interface TransformListener {
        void onTransformStart(GameObject obj, double x, double y, double rotation, int width, int height);
        void onTransformEnd(GameObject obj);
    }
    
    private TransformListener transformListener;
    
    public void setTransformListener(TransformListener listener) {
        this.transformListener = listener;
    }

    // Internal class to store initial state of an object
    public static class EntitySnapshot {
        public double x, y;
        public int width, height;

        public EntitySnapshot(double x, double y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public Game() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setupMouseListeners();
        
        // Initialize Input system
        Input.init(this);
        
        // Initialize camera and viewport system
        initializeCameraSystem();
        
        // Initialize collision system
        collisionManager = new IgnisSampleCollisions.CollisionManager();
        
        // Initialize Robot for infinite drag
        try {
            robot = new Robot();
        } catch (AWTException e) {
            System.err.println("Could not create Robot for infinite drag: " + e.getMessage());
            robot = null;
        }
        
        // Add resize listener to update viewport
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (viewport != null) {
                    viewport.resize(getWidth(), getHeight());
                    if (mainCamera != null) {
                        mainCamera.setViewport(viewport);
                    }
                }
            }
        });
    }
    
    /**
     * Initializes the camera and viewport system.
     */
    private void initializeCameraSystem() {
        // Create default viewport
        viewport = new Viewport(WIDTH, HEIGHT);
        
        // Create default main camera
        mainCamera = new Camera("MainCamera", this, 0, 0);
        mainCamera.setViewport(viewport);
        mainCamera.setGame(this);
        
        // Add to cameras list
        cameras.add(mainCamera);
    }
    
    /**
     * Define o ScriptManager para carregar e gerenciar scripts
     */
    public void setScriptManager(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
    }
    
    /**
     * Retorna o ScriptManager atual
     */
    public ScriptManager getScriptManager() {
        return scriptManager;
    }
    
    /**
     * Define o PrefabManager para instanciar prefabs
     */
    public void setPrefabManager(PrefabManager prefabManager) {
        this.prefabManager = prefabManager;
    }
    
    /**
     * Retorna o PrefabManager atual
     */
    public PrefabManager getPrefabManager() {
        return prefabManager;
    }
    
    /**
     * Define o canvas de UI do jogo.
     * @param canvas O canvas de UI a ser usado
     */
    public void setUICanvas(UICanvas canvas) {
        this.uiCanvas = canvas;
        if (canvas != null) {
            canvas.setGame(this);
            canvas.updateScreenSize(getWidth(), getHeight());
        }
    }
    
    /**
     * Obtém o canvas de UI atual.
     * @return O canvas de UI, ou null se não existir
     */
    public UICanvas getUICanvas() {
        return uiCanvas;
    }
    
    /**
     * Gets the collision manager
     */
    public IgnisSampleCollisions.CollisionManager getCollisionManager() {
        return collisionManager;
    }
    
    /**
     * Sets whether to show collider debug visualization
     */
    public void setShowColliders(boolean show) {
        this.showColliders = show;
        if (collisionManager != null) {
            collisionManager.setDebugDraw(show);
        }
    }
    
    /**
     * Returns whether collider debug visualization is enabled
     */
    public boolean isShowColliders() {
        return showColliders;
    }
    
    /**
     * Instancia uma prefab no mundo do jogo.
     * Este método pode ser chamado por scripts para criar objetos dinamicamente.
     * 
     * @param prefabName Nome da prefab a ser instanciada
     * @param x Posição X onde o objeto será criado
     * @param y Posição Y onde o objeto será criado
     * @return O GameObject instanciado, ou null se falhar
     * 
     * Exemplo de uso em um script:
     * <pre>
     * // Criar um projétil na posição atual do player
     * GameObject projectile = game.instantiatePrefab("Projectile", transform.x + 50, transform.y);
     * if (projectile != null) {
     *     System.out.println("Projétil criado: " + projectile.getName());
     * }
     * </pre>
     */
    public GameObject instantiatePrefab(String prefabName, double x, double y) {
        if (prefabManager == null) {
            System.err.println("PrefabManager não está disponível. Certifique-se de que um projeto está aberto.");
            return null;
        }
        
        GameObject instance = prefabManager.instantiatePrefab(prefabName, x, y);
        
        if (instance != null) {
            // Adicionar o objeto ao jogo
            addEntity(instance);
            
            // Marcar como objeto criado em runtime (será removido quando o jogo parar)
            if (gameState == GameState.PLAYING) {
                runtimeObjects.add(instance);
            }
            
            // Se estiver em modo de jogo, inicializar scripts
            if (gameState == GameState.PLAYING && scriptManager != null) {
                for (String scriptName : instance.getScriptNames()) {
                    IgnisScript script = scriptManager.createScriptInstance(scriptName, instance, this);
                    if (script != null) {
                        instance.getScripts().add(script);
                        script.start();
                    }
                }
            }
            
            System.out.println("Prefab '" + prefabName + "' instanciada como '" + instance.getName() + "' em (" + x + ", " + y + ")");
        } else {
            System.err.println("Falha ao instanciar prefab '" + prefabName + "'. Verifique se a prefab existe.");
        }
        
        return instance;
    }
    
    /**
     * Inicializa os scripts de todos os objetos do jogo
     */
    public void initializeScripts() {
        if (scriptManager == null) return;
        
        for (GameObject entity : entities) {
            for (String scriptName : entity.getScriptNames()) {
                // Verificar se o script já está instanciado
                if (!entity.hasScript(scriptName)) {
                    IgnisScript script = scriptManager.createScriptInstance(scriptName, entity, this);
                    if (script != null) {
                        entity.addScript(script);
                    }
                }
            }
        }
    }

    // ==================== MOUSE LISTENERS ====================
    
    // Panning state
    private boolean isPanning = false;
    private int panStartX, panStartY;
    private double camStartX, camStartY;
    private Runnable onPanUpdate;

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // UI Canvas gets priority when playing
                if (gameState == GameState.PLAYING && uiCanvas != null && uiCanvas.isVisible()) {
                    if (uiCanvas.processMouseClick(e, true)) {
                        e.consume();
                        return;
                    }
                }
                
                // Middle mouse button for panning - handle first to avoid selection
                if (e.getButton() == MouseEvent.BUTTON2) {
                    startPanning(e.getX(), e.getY());
                    e.consume();
                    return;
                }
                // Left click for selection/manipulation
                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleMousePress(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // UI Canvas gets priority when playing
                if (gameState == GameState.PLAYING && uiCanvas != null && uiCanvas.isVisible()) {
                    if (uiCanvas.processMouseClick(e, false)) {
                        e.consume();
                        return;
                    }
                }
                
                if (e.getButton() == MouseEvent.BUTTON2) {
                    stopPanning();
                    e.consume();
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleMouseRelease();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // UI Canvas gets priority when playing
                if (gameState == GameState.PLAYING && uiCanvas != null && uiCanvas.isVisible()) {
                    uiCanvas.processMouseMove(e);
                }
                
                // Handle panning first
                if (isPanning) {
                    handlePanning(e.getX(), e.getY());
                    return;
                }
                handleMouseDrag(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // UI Canvas gets priority when playing
                if (gameState == GameState.PLAYING && uiCanvas != null && uiCanvas.isVisible()) {
                    uiCanvas.processMouseMove(e);
                }
                
                updateCursor(e.getX(), e.getY());
            }
        });
    }
    
    /**
     * Sets up editor panning with a callback for UI updates.
     */
    public void setupEditorPanning(Runnable onUpdate) {
        this.onPanUpdate = onUpdate;
    }
    
    private void startPanning(int x, int y) {
        if (gameState != GameState.EDITING) return;
        
        isPanning = true;
        panStartX = x;
        panStartY = y;
        Camera cam = getMainCamera();
        if (cam != null) {
            camStartX = cam.getX();
            camStartY = cam.getY();
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }
    
    private void handlePanning(int x, int y) {
        if (!isPanning || gameState != GameState.EDITING) return;
        
        Camera cam = getMainCamera();
        if (cam != null) {
            double zoom = cam.getZoom();
            double dx = (x - panStartX) / zoom;
            // Invert dy because Y-axis is flipped (positive Y goes up)
            double dy = -(y - panStartY) / zoom;
            cam.setPosition(camStartX - dx, camStartY - dy);
            repaint();
        }
    }
    
    private void stopPanning() {
        isPanning = false;
        setCursor(Cursor.getDefaultCursor());
        if (onPanUpdate != null) {
            onPanUpdate.run();
        }
    }

    private void handleMousePress(int mouseX, int mouseY) {
        // Only allow selection and manipulation in EDITING mode
        if (gameState != GameState.EDITING)
            return;

        // Check if clicked on gizmo first
        if (selectedObject != null) {
            GizmoDragMode mode = getGizmoHitArea(mouseX, mouseY);
            if (mode != GizmoDragMode.NONE) {
                currentDragMode = mode;
                dragStartX = mouseX;
                dragStartY = mouseY;
                objectStartX = selectedObject.getX();
                objectStartY = selectedObject.getY();
                objectStartRotation = selectedObject.getRotation();
                objectStartWidth = selectedObject.getWidth();
                objectStartHeight = selectedObject.getHeight();
                
                // Initialize infinite drag tracking
                accumulatedDragX = 0;
                accumulatedDragY = 0;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                
                // Notificar início de transformação (para undo)
                if (transformListener != null) {
                    transformListener.onTransformStart(selectedObject, 
                        objectStartX, objectStartY, objectStartRotation, 
                        objectStartWidth, objectStartHeight);
                }
                return;
            }
        }

        // Check if clicked on any object
        GameObject clicked = getObjectAt(mouseX, mouseY);
        if (clicked != null) {
            setSelectedObject(clicked);
            // Start drag from center (move mode)
            if (currentTool == ToolType.MOVE) {
                currentDragMode = GizmoDragMode.CENTER;
                dragStartX = mouseX;
                dragStartY = mouseY;
                objectStartX = clicked.getX();
                objectStartY = clicked.getY();
                
                // Initialize infinite drag tracking
                accumulatedDragX = 0;
                accumulatedDragY = 0;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                
                // Notificar início de transformação (para undo)
                if (transformListener != null) {
                    transformListener.onTransformStart(clicked, 
                        objectStartX, objectStartY, clicked.getRotation(), 
                        clicked.getWidth(), clicked.getHeight());
                }
            }
        } else {
            // Clicked on empty area - deselect
            setSelectedObject(null);
        }
    }

    private void handleMouseRelease() {
        // Notificar fim de transformação (para undo)
        if (currentDragMode != GizmoDragMode.NONE && selectedObject != null && transformListener != null) {
            transformListener.onTransformEnd(selectedObject);
        }
        
        currentDragMode = GizmoDragMode.NONE;
        setCursor(Cursor.getDefaultCursor());

        // Notify listeners now that drag is complete
        // This ensures Inspector gets updated with final values after user finishes
        // dragging
        if (selectedObject != null) {
            notifySelectionListeners();
        }
    }

    private void handleMouseDrag(int mouseX, int mouseY) {
        if (currentDragMode == GizmoDragMode.NONE || selectedObject == null)
            return;
        if (gameState != GameState.EDITING)
            return;
        
        // Skip if this is a warp event (mouse was just teleported)
        if (isWarping) {
            isWarping = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return;
        }
        
        // Calculate mouse delta since last position
        int mouseDeltaX = mouseX - lastMouseX;
        int mouseDeltaY = mouseY - lastMouseY;
        
        // Accumulate the drag
        accumulatedDragX += mouseDeltaX;
        accumulatedDragY += mouseDeltaY;
        
        // Update last position
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        
        // Check for edge wrapping (infinite drag)
        if (robot != null) {
            int w = getWidth();
            int h = getHeight();
            boolean needsWarp = false;
            int newX = mouseX;
            int newY = mouseY;
            
            if (mouseX <= WARP_MARGIN) {
                newX = w - WARP_MARGIN - 1;
                needsWarp = true;
            } else if (mouseX >= w - WARP_MARGIN) {
                newX = WARP_MARGIN + 1;
                needsWarp = true;
            }
            
            if (mouseY <= WARP_MARGIN) {
                newY = h - WARP_MARGIN - 1;
                needsWarp = true;
            } else if (mouseY >= h - WARP_MARGIN) {
                newY = WARP_MARGIN + 1;
                needsWarp = true;
            }
            
            if (needsWarp) {
                isWarping = true;
                lastMouseX = newX;
                lastMouseY = newY;
                // Convert component coordinates to screen coordinates
                Point screenLoc = getLocationOnScreen();
                robot.mouseMove(screenLoc.x + newX, screenLoc.y + newY);
            }
        }

        // Use accumulated drag for calculations
        Point2D.Double startWorld = screenToWorld(dragStartX, dragStartY);
        Point2D.Double accumulatedWorld = screenToWorld(dragStartX + accumulatedDragX, dragStartY + accumulatedDragY);
        
        double deltaX = accumulatedWorld.x - startWorld.x;
        double deltaY = accumulatedWorld.y - startWorld.y;

        switch (currentDragMode) {
            case AXIS_X:
                selectedObject.setX(objectStartX + deltaX);
                break;
            case AXIS_Y:
                selectedObject.setY(objectStartY + deltaY);
                break;
            case CENTER:
                selectedObject.setX(objectStartX + deltaX);
                selectedObject.setY(objectStartY + deltaY);
                break;
            case ROTATE:
                // Calculate rotation based on accumulated angle change
                double centerX = objectStartX + objectStartWidth / 2.0;
                double centerY = objectStartY + objectStartHeight / 2.0;
                double startAngle = Math.atan2(startWorld.y - centerY, startWorld.x - centerX);
                double currentAngle = Math.atan2(accumulatedWorld.y - centerY, accumulatedWorld.x - centerX);
                double deltaAngle = Math.toDegrees(currentAngle - startAngle);
                selectedObject.setRotation(objectStartRotation + deltaAngle);
                break;
            case SCALE_X:
                // Scale from center: adjust position to keep center fixed
                int newWidth = Math.max(1, objectStartWidth + (int)(deltaX * 2));
                double oldCenterX = objectStartX + objectStartWidth / 2.0;
                selectedObject.setWidth(newWidth);
                selectedObject.setX(oldCenterX - newWidth / 2.0);
                break;
            case SCALE_Y:
                // Scale from center: adjust position to keep center fixed
                // Dragging up (positive Y) increases height
                int newHeight = Math.max(1, objectStartHeight + (int)(deltaY * 2));
                double oldCenterY = objectStartY + objectStartHeight / 2.0;
                selectedObject.setHeight(newHeight);
                selectedObject.setY(oldCenterY - newHeight / 2.0);
                break;
            case SCALE_UNIFORM:
                // Uniform scale from center (use world delta)
                // Dragging right/up increases size
                double scaleAmount = deltaX + deltaY;
                int newUniformWidth = Math.max(1, objectStartWidth + (int)scaleAmount);
                int newUniformHeight = Math.max(1, objectStartHeight + (int)scaleAmount);
                double origCenterX = objectStartX + objectStartWidth / 2.0;
                double origCenterY = objectStartY + objectStartHeight / 2.0;
                selectedObject.setWidth(newUniformWidth);
                selectedObject.setHeight(newUniformHeight);
                selectedObject.setX(origCenterX - newUniformWidth / 2.0);
                selectedObject.setY(origCenterY - newUniformHeight / 2.0);
                break;
            default:
                break;
        }

        // Don't notify listeners during drag - prevents Inspector from updating
        // constantly
        // Visual changes are already visible, listeners will be notified on mouse
        // release
    }

    private void updateCursor(int mouseX, int mouseY) {
        if (gameState != GameState.EDITING) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }

        if (selectedObject != null) {
            GizmoDragMode mode = getGizmoHitArea(mouseX, mouseY);
            switch (mode) {
                case AXIS_X:
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    return;
                case AXIS_Y:
                    setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                    return;
                case CENTER:
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                default:
                    break;
            }
        }

        // Verificar se está sobre algum objeto
        if (getObjectAt(mouseX, mouseY) != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private GizmoDragMode getGizmoHitArea(int screenX, int screenY) {
        if (selectedObject == null)
            return GizmoDragMode.NONE;

        // Convert screen mouse position to world coordinates
        Point2D.Double worldPos = screenToWorld(screenX, screenY);
        double mouseX = worldPos.x;
        double mouseY = worldPos.y;

        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        
        // Get scaled gizmo dimensions
        int gizmoSize = getScaledGizmoSize();
        int hitArea = getScaledGizmoHitArea();
        int rotateRadius = getScaledRotateGizmoRadius();
        int scaledHitTolerance = (int)(10 / (getActiveCamera() != null ? getActiveCamera().getZoom() : 1.0));

        switch (currentTool) {
            case MOVE:
                // Check X axis (arrow to right)
                if (mouseX >= centerX && mouseX <= centerX + gizmoSize &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return GizmoDragMode.AXIS_X;
                }
                // Check Y axis (arrow up = positive Y direction)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY && mouseY <= centerY + gizmoSize) {
                    return GizmoDragMode.AXIS_Y;
                }
                // Check center
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return GizmoDragMode.CENTER;
                }
                break;

            case ROTATE:
                // Check if on rotation circle
                double dist = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
                if (dist >= rotateRadius - scaledHitTolerance && dist <= rotateRadius + scaledHitTolerance) {
                    return GizmoDragMode.ROTATE;
                }
                break;

            case SCALE:
                int squareSize = (int)(8 / (getActiveCamera() != null ? getActiveCamera().getZoom() : 1.0));
                // Check X axis square end (scale X)
                if (mouseX >= centerX + gizmoSize - squareSize && mouseX <= centerX + gizmoSize + squareSize &&
                        mouseY >= centerY - squareSize && mouseY <= centerY + squareSize) {
                    return GizmoDragMode.SCALE_X;
                }
                // Check Y axis square end (scale Y - positive Y direction)
                if (mouseX >= centerX - squareSize && mouseX <= centerX + squareSize &&
                        mouseY >= centerY + gizmoSize - squareSize && mouseY <= centerY + gizmoSize + squareSize) {
                    return GizmoDragMode.SCALE_Y;
                }
                // Check center square (uniform scale)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return GizmoDragMode.SCALE_UNIFORM;
                }
                break;
        }

        return GizmoDragMode.NONE;
    }

    /**
     * Returns the object at the specified screen position (top to bottom in render order).
     * Converts screen coordinates to world coordinates for proper picking.
     */
    public GameObject getObjectAt(int screenX, int screenY) {
        // Convert screen coordinates to world coordinates
        Point2D.Double worldPos = screenToWorld(screenX, screenY);
        double wx = worldPos.x;
        double wy = worldPos.y;
        
        // Iterate from back to front (objects rendered last are "on top")
        for (int i = entities.size() - 1; i >= 0; i--) {
            GameObject obj = entities.get(i);
            // Skip camera objects from selection
            if (obj instanceof Camera) continue;
            
            if (wx >= obj.getX() && wx <= obj.getX() + obj.getWidth() &&
                    wy >= obj.getY() && wy <= obj.getY() + obj.getHeight()) {
                return obj;
            }
        }
        return null;
    }

    // ==================== SELECTION ====================

    public void setSelectedObject(GameObject obj) {
        this.selectedObject = obj;
        notifySelectionListeners();
    }

    public GameObject getSelectedObject() {
        return selectedObject;
    }

    public void addSelectionListener(SelectionListener listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionListener(SelectionListener listener) {
        selectionListeners.remove(listener);
    }

    private void notifySelectionListeners() {
        for (SelectionListener listener : selectionListeners) {
            listener.onSelectionChanged(selectedObject);
        }
    }

    public synchronized void start() {
        isRunning = true;
        thread = new Thread(this);
        thread.start();
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // ==================== GAME STATE CONTROL ====================

    /**
     * Starts world simulation (Play)
     * Saves initial positions of all objects
     */
    public void playWorld() {
        if (gameState == GameState.EDITING) {
            // Save snapshot of all objects
            saveInitialSnapshots();
            
            // Compile and initialize scripts
            if (scriptManager != null) {
                scriptManager.compileAllScripts();
                initializeScripts();
            }
        } else if (gameState == GameState.PAUSED) {
            // Resuming from pause - resume audio
            IgnisSoundEngine.getInstance().resumeMusic();
        }
        gameState = GameState.PLAYING;
    }

    /**
     * Pauses world simulation
     * Keeps objects at current positions
     */
    public void pauseWorld() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
            
            // Pause audio
            IgnisSoundEngine.getInstance().pauseMusic();
        }
    }

    /**
     * Resumes world simulation after pause
     */
    public void resumeWorld() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
            
            // Resume audio
            IgnisSoundEngine.getInstance().resumeMusic();
        }
    }

    /**
     * Stops simulation and restores objects to initial positions
     */
    public void stopWorld() {
        gameState = GameState.EDITING;
        
        // Stop all audio
        IgnisSoundEngine.getInstance().stopMusic();
        IgnisSoundEngine.getInstance().stopAllSounds();
        
        // Limpar a interface de usuário criada pelos scripts
        if (uiCanvas != null) {
            uiCanvas.clearChildren();
        }
        
        // Remover todos os objetos criados em runtime (projéteis, inimigos spawados, etc.)
        for (GameObject runtimeObj : runtimeObjects) {
            entities.remove(runtimeObj);
        }
        runtimeObjects.clear();
        System.out.println("Objetos de runtime removidos.");
        
        // Restore initial positions
        restoreInitialSnapshots();
        
        // Resetar scripts
        for (GameObject entity : entities) {
            entity.resetScripts();
        }
    }

    /**
     * Saves initial state of all objects
     */
    private void saveInitialSnapshots() {
        initialSnapshots.clear();
        for (GameObject entity : entities) {
            initialSnapshots.put(entity.getId(), new EntitySnapshot(
                    entity.getX(),
                    entity.getY(),
                    entity.getWidth(),
                    entity.getHeight()));
        }
    }

    /**
     * Restores all objects to initial positions
     */
    private void restoreInitialSnapshots() {
        for (GameObject entity : entities) {
            EntitySnapshot snapshot = initialSnapshots.get(entity.getId());
            if (snapshot != null) {
                entity.setX(snapshot.x);
                entity.setY(snapshot.y);
                entity.setWidth(snapshot.width);
                entity.setHeight(snapshot.height);
            }
        }
    }

    /**
     * Returns the current game state
     */
    public GameState getGameState() {
        return gameState;
    }

    /**
     * Checks if the world is being simulated (playing or paused)
     */
    public boolean isWorldRunning() {
        return gameState == GameState.PLAYING || gameState == GameState.PAUSED;
    }

    private java.util.List<GameObject> entities = new java.util.ArrayList<>();

    public void tick() {
        // Update Input system
        Input.update();
        
        // Update UI Canvas (even in editing mode for preview)
        if (uiCanvas != null) {
            uiCanvas.update();
        }
        
        // Only update objects if in PLAYING state
        if (gameState == GameState.PLAYING) {
            for (int i = 0; i < entities.size(); i++) {
                GameObject entity = entities.get(i);
                entity.tick();
                
                // Executar scripts anexados ao objeto
                entity.tickScripts();
            }
            
            // Run collision detection using the advanced collision system
            if (collisionManager != null) {
                collisionManager.update();
            }
        }
    }
    
    /**
     * Registers all entity colliders with the collision manager
     * Call this after loading a scene or adding entities
     */
    public void refreshColliders() {
        if (collisionManager == null) return;
        
        collisionManager.clear();
        for (GameObject entity : entities) {
            if (entity.hasCollider()) {
                collisionManager.addCollider(entity.getCollider());
            }
        }
    }

    public void render() {
        BufferStrategy bs = this.getBufferStrategy();
        if (bs == null) {
            this.createBufferStrategy(3);
            return;
        }

        Graphics g = bs.getDrawGraphics();
        Graphics2D g2d = (Graphics2D) g;

        // Clear background
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, this.getWidth(), this.getHeight());

        // Enable anti-aliasing for smoother rendering
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Update viewport size if changed
        if (viewport != null && (viewport.getWidth() != getWidth() || viewport.getHeight() != getHeight())) {
            viewport.resize(getWidth(), getHeight());
        }

        // ==================== CAMERA-DEPENDENT RENDERING ====================
        // 1. Select the active camera
        Camera activeCamera = getActiveCamera();
        
        // 2. Save the original transform
        AffineTransform originalTransform = g2d.getTransform();
        
        // 3. Apply camera transform when in editor camera mode OR when playing
        boolean shouldApplyCameraTransform = activeCamera != null && 
            (editorCameraMode || gameState == GameState.PLAYING);
        
        if (shouldApplyCameraTransform) {
            activeCamera.applyTransform(g2d);
        }
        
        // 3.5 Draw grid in editor mode (before entities, so it appears behind)
        if (gameState == GameState.EDITING && showGrid && shouldApplyCameraTransform) {
            drawGrid(g2d);
        }

        // 4. Render all entities (respecting Z-Index by list order)
        for (int i = 0; i < entities.size(); i++) {
            GameObject entity = entities.get(i);
            
            // Skip invisible entities
            if (!entity.isVisible()) continue;
            
            // Skip camera entities from regular rendering (cameras are special)
            if (entity instanceof Camera) continue;

            // Save entity transform
            AffineTransform entityTransform = g2d.getTransform();

            // Apply entity rotation around its center
            if (entity.getRotation() != 0) {
                double centerX = entity.getX() + entity.getWidth() / 2.0;
                double centerY = entity.getY() + entity.getHeight() / 2.0;
                g2d.rotate(Math.toRadians(entity.getRotation()), centerX, centerY);
            }

            // Render entity
            entity.render(g);

            // Restore entity transform
            g2d.setTransform(entityTransform);
        }

        // 5. Restore original transform for UI and editor overlays
        g2d.setTransform(originalTransform);

        // Draw camera gizmos in editor mode (without camera transform applied)
        if (gameState == GameState.EDITING) {
            // If editor camera mode, apply it for gizmos
            if (editorCameraMode && activeCamera != null) {
                AffineTransform editorTransform = g2d.getTransform();
                activeCamera.applyTransform(g2d);
                
                // Render camera indicators
                for (Camera cam : cameras) {
                    if (cam.isVisible()) {
                        cam.render(g);
                    }
                }
                
                g2d.setTransform(editorTransform);
            }
        }

        // Draw selection and gizmo only in EDITING mode
        if (gameState == GameState.EDITING && selectedObject != null) {
            // Apply camera transform for selection/gizmo if in editor camera mode
            if (shouldApplyCameraTransform) {
                activeCamera.applyTransform(g2d);
            }
            
            renderSelection(g2d);
            renderGizmo(g2d);
            
            // Restore transform
            g2d.setTransform(originalTransform);
        }

        // Draw world origin indicator in editing mode
        if (gameState == GameState.EDITING && shouldApplyCameraTransform) {
            AffineTransform temp = g2d.getTransform();
            activeCamera.applyTransform(g2d);
            drawWorldOrigin(g2d);
            
            // Draw collider debug visualization if enabled
            if (showColliders && collisionManager != null) {
                collisionManager.debugRender(g2d);
            }
            
            g2d.setTransform(temp);
        }
        
        // ==================== UI RENDERING ====================
        // Render UI Canvas on top of everything (in SCREEN_SPACE_OVERLAY mode)
        if (uiCanvas != null && uiCanvas.isVisible()) {
            // Ensure canvas has correct screen dimensions
            uiCanvas.updateScreenSize(getWidth(), getHeight());
            
            // Reset transform for screen-space rendering
            g2d.setTransform(originalTransform);
            
            // Render UI hierarchy
            uiCanvas.renderAll(g2d);
        }

        g.dispose();
        bs.show();
    }

    /**
     * Draws text that appears correctly despite the inverted Y-axis.
     * Flips the text vertically before drawing so it appears right-side up.
     */
    private void drawWorldText(Graphics2D g2d, String text, double worldX, double worldY) {
        AffineTransform oldTransform = g2d.getTransform();
        // Move to text position, flip Y to make text appear correctly
        g2d.translate(worldX, worldY);
        g2d.scale(1, -1);
        g2d.drawString(text, 0, 0);
        g2d.setTransform(oldTransform);
    }

    /**
     * Draws the world origin (0,0) indicator
     */
    private void drawWorldOrigin(Graphics2D g2d) {
        int crossSize = 20;
        int axisLength = 35;
        
        // Draw main crosshair at world origin (white, semi-transparent)
        g2d.setColor(new Color(255, 255, 255, 120));
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(-crossSize, 0, crossSize, 0);
        g2d.drawLine(0, -crossSize, 0, crossSize);
        
        // Draw X axis (red, pointing right)
        g2d.setColor(new Color(255, 80, 80, 180));
        g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(0, 0, axisLength, 0);
        // X axis arrow
        g2d.drawLine(axisLength, 0, axisLength - 6, -4);
        g2d.drawLine(axisLength, 0, axisLength - 6, 4);
        
        // Draw Y axis (green, pointing up - positive Y is up in world coordinates)
        g2d.setColor(new Color(80, 255, 80, 180));
        g2d.drawLine(0, 0, 0, axisLength);
        // Y axis arrow (pointing up in world space = positive Y)
        g2d.drawLine(0, axisLength, -4, axisLength - 6);
        g2d.drawLine(0, axisLength, 4, axisLength - 6);
        
        // Origin label
        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        drawWorldText(g2d, "(0,0)", 6, 6);
    }
    
    /**
     * Draws the editor grid in world space.
     * The grid adapts to the camera zoom level for better visibility.
     */
    private void drawGrid(Graphics2D g2d) {
        Camera cam = getActiveCamera();
        if (cam == null) return;
        
        double zoom = cam.getZoom();
        
        // Adapt grid size based on zoom level for better visibility
        int effectiveGridSize = gridSize;
        if (zoom < 0.25) {
            effectiveGridSize = gridSize * 8;
        } else if (zoom < 0.5) {
            effectiveGridSize = gridSize * 4;
        } else if (zoom < 1.0) {
            effectiveGridSize = gridSize * 2;
        }
        
        // Get visible world bounds
        double[] bounds = cam.getVisibleWorldBounds();
        double minX = bounds[0];
        double minY = bounds[1];
        double maxX = bounds[2];
        double maxY = bounds[3];
        
        // Extend bounds slightly to avoid edge artifacts
        minX -= effectiveGridSize;
        minY -= effectiveGridSize;
        maxX += effectiveGridSize;
        maxY += effectiveGridSize;
        
        // Snap to grid
        int startX = (int)(Math.floor(minX / effectiveGridSize) * effectiveGridSize);
        int startY = (int)(Math.floor(minY / effectiveGridSize) * effectiveGridSize);
        int endX = (int)(Math.ceil(maxX / effectiveGridSize) * effectiveGridSize);
        int endY = (int)(Math.ceil(maxY / effectiveGridSize) * effectiveGridSize);
        
        // Set grid appearance
        g2d.setColor(gridColor);
        g2d.setStroke(new BasicStroke(1.0f / (float)zoom)); // Thin lines that stay consistent
        
        // Draw vertical lines
        for (int x = startX; x <= endX; x += effectiveGridSize) {
            g2d.drawLine(x, startY, x, endY);
        }
        
        // Draw horizontal lines
        for (int y = startY; y <= endY; y += effectiveGridSize) {
            g2d.drawLine(startX, y, endX, y);
        }
        
        // Draw major grid lines (every 4 cells) with slightly more opacity
        g2d.setColor(new Color(gridColor.getRed(), gridColor.getGreen(), gridColor.getBlue(), 
                              Math.min(255, gridColor.getAlpha() * 2)));
        g2d.setStroke(new BasicStroke(1.5f / (float)zoom));
        
        int majorGridSize = effectiveGridSize * 4;
        int majorStartX = (int)(Math.floor(minX / majorGridSize) * majorGridSize);
        int majorStartY = (int)(Math.floor(minY / majorGridSize) * majorGridSize);
        
        for (int x = majorStartX; x <= endX; x += majorGridSize) {
            g2d.drawLine(x, startY, x, endY);
        }
        
        for (int y = majorStartY; y <= endY; y += majorGridSize) {
            g2d.drawLine(startX, y, endX, y);
        }
    }

    /**
     * Renders the selection border around the selected object
     */
    private void renderSelection(Graphics2D g2d) {
        if (selectedObject == null)
            return;

        int x = (int) selectedObject.getX();
        int y = (int) selectedObject.getY();
        int w = selectedObject.getWidth();
        int h = selectedObject.getHeight();
        double rotation = selectedObject.getRotation();

        // Save original transform
        java.awt.geom.AffineTransform originalTransform = g2d.getTransform();

        // Apply rotation if necessary
        if (rotation != 0) {
            double centerX = x + w / 2.0;
            double centerY = y + h / 2.0;
            g2d.rotate(Math.toRadians(rotation), centerX, centerY);
        }

        // Selection border
        g2d.setColor(new Color(0, 150, 255));
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] { 5.0f, 5.0f }, 0.0f));
        g2d.drawRect(x - 2, y - 2, w + 4, h + 4);

        // Corner handles
        g2d.setStroke(new BasicStroke(1));
        g2d.setColor(Color.WHITE);
        int handleSize = 6;
        g2d.fillRect(x - handleSize / 2 - 2, y - handleSize / 2 - 2, handleSize, handleSize);
        g2d.fillRect(x + w - handleSize / 2 + 2, y - handleSize / 2 - 2, handleSize, handleSize);
        g2d.fillRect(x - handleSize / 2 - 2, y + h - handleSize / 2 + 2, handleSize, handleSize);
        g2d.fillRect(x + w - handleSize / 2 + 2, y + h - handleSize / 2 + 2, handleSize, handleSize);

        g2d.setColor(new Color(0, 150, 255));
        g2d.drawRect(x - handleSize / 2 - 2, y - handleSize / 2 - 2, handleSize, handleSize);
        g2d.drawRect(x + w - handleSize / 2 + 2, y - handleSize / 2 - 2, handleSize, handleSize);
        g2d.drawRect(x - handleSize / 2 - 2, y + h - handleSize / 2 + 2, handleSize, handleSize);
        g2d.drawRect(x + w - handleSize / 2 + 2, y + h - handleSize / 2 + 2, handleSize, handleSize);

        // Restaurar transformação original
        g2d.setTransform(originalTransform);
    }

    /**
     * Renders the appropriate gizmo based on current tool
     */
    private void renderGizmo(Graphics2D g2d) {
        if (selectedObject == null)
            return;

        switch (currentTool) {
            case MOVE:
                renderMoveGizmo(g2d);
                break;
            case ROTATE:
                renderRotateGizmo(g2d);
                break;
            case SCALE:
                renderScaleGizmo(g2d);
                break;
        }
    }

    /**
     * Renders the move gizmo (X and Y arrows)
     */
    private void renderMoveGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        
        // Get scaled gizmo dimensions
        int gizmoSize = getScaledGizmoSize();
        int arrowSize = getScaledGizmoArrowSize();

        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Center square
        g2d.setColor(new Color(255, 255, 100));
        int centerSize = (int)(10 / (getActiveCamera() != null ? getActiveCamera().getZoom() : 1.0));
        centerSize = Math.max(4, centerSize); // Minimum visible size
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // X axis (red) - arrow to right
        Color xColor = (currentDragMode == GizmoDragMode.AXIS_X) ? new Color(255, 100, 100) : new Color(220, 50, 50);
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        // X arrow head
        Polygon arrowX = new Polygon();
        arrowX.addPoint(centerX + gizmoSize + arrowSize, centerY);
        arrowX.addPoint(centerX + gizmoSize - 2, centerY - arrowSize / 2);
        arrowX.addPoint(centerX + gizmoSize - 2, centerY + arrowSize / 2);
        g2d.fillPolygon(arrowX);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "X", centerX + gizmoSize + arrowSize + 2, centerY - 4);

        // Y axis (green) - arrow pointing up (positive Y direction in world space)
        Color yColor = (currentDragMode == GizmoDragMode.AXIS_Y) ? new Color(100, 255, 100) : new Color(50, 200, 50);
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        // Y arrow head (pointing up = positive Y)
        Polygon arrowY = new Polygon();
        arrowY.addPoint(centerX, centerY + gizmoSize + arrowSize);
        arrowY.addPoint(centerX - arrowSize / 2, centerY + gizmoSize - 2);
        arrowY.addPoint(centerX + arrowSize / 2, centerY + gizmoSize - 2);
        g2d.fillPolygon(arrowY);
        g2d.setColor(Color.WHITE);
        drawWorldText(g2d, "Y", centerX - 4, centerY + gizmoSize + arrowSize + 2);
    }

    /**
     * Renders the rotate gizmo (circle)
     */
    private void renderRotateGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        
        // Get scaled radius
        int rotateRadius = getScaledRotateGizmoRadius();

        // Rotation circle
        Color circleColor = (currentDragMode == GizmoDragMode.ROTATE) ? new Color(100, 200, 255)
                : new Color(50, 150, 220);
        g2d.setColor(circleColor);
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - rotateRadius, centerY - rotateRadius,
                rotateRadius * 2, rotateRadius * 2);

        // Rotation indicator line (shows current rotation)
        double radians = Math.toRadians(selectedObject.getRotation());
        int indicatorX = centerX + (int) (Math.cos(radians) * rotateRadius);
        int indicatorY = centerY + (int) (Math.sin(radians) * rotateRadius);
        g2d.setColor(new Color(255, 150, 50));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(centerX, centerY, indicatorX, indicatorY);

        // Center point
        int centerPointSize = (int)(5 / (getActiveCamera() != null ? getActiveCamera().getZoom() : 1.0));
        centerPointSize = Math.max(3, centerPointSize);
        g2d.setColor(new Color(50, 150, 220));
        g2d.fillOval(centerX - centerPointSize, centerY - centerPointSize, centerPointSize * 2, centerPointSize * 2);

        // Rotation value label
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, String.format("%.1f\u00B0", selectedObject.getRotation()),
                centerX + rotateRadius + 5, centerY + 5);
    }

    /**
     * Renders the scale gizmo (arrows with square ends like move gizmo)
     */
    private void renderScaleGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        int objW = selectedObject.getWidth();
        int objH = selectedObject.getHeight();
        
        // Get scaled gizmo dimensions
        int gizmoSize = getScaledGizmoSize();
        double zoom = (getActiveCamera() != null) ? getActiveCamera().getZoom() : 1.0;
        int squareSize = (int)(8 / zoom);
        squareSize = Math.max(4, squareSize);

        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Center square (uniform scale)
        Color uColor = (currentDragMode == GizmoDragMode.SCALE_UNIFORM) ? new Color(255, 255, 150)
                : new Color(255, 220, 50);
        g2d.setColor(uColor);
        int centerSize = (int)(12 / zoom);
        centerSize = Math.max(6, centerSize);
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // X axis (red) - arrow to right with square end
        Color xColor = (currentDragMode == GizmoDragMode.SCALE_X) ? new Color(255, 100, 100) : new Color(220, 50, 50);
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        // X square end
        g2d.fillRect(centerX + gizmoSize - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX + gizmoSize - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "X", centerX + gizmoSize + squareSize, centerY - 4);

        // Y axis (green) - arrow pointing up (positive Y direction in world space)
        Color yColor = (currentDragMode == GizmoDragMode.SCALE_Y) ? new Color(100, 255, 100) : new Color(50, 200, 50);
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        // Y square end (pointing up = positive Y)
        g2d.fillRect(centerX - squareSize / 2, centerY + gizmoSize - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX - squareSize / 2, centerY + gizmoSize - squareSize / 2, squareSize, squareSize);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "Y", centerX - 4, centerY + gizmoSize + squareSize + 2);

        // Size label
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.PLAIN, 11));
        drawWorldText(g2d, objW + " x " + objH, centerX + gizmoSize + squareSize, centerY - 20);
    }

    // ==================== TOOL MANAGEMENT ====================

    public void setCurrentTool(ToolType tool) {
        this.currentTool = tool;
        currentDragMode = GizmoDragMode.NONE;
    }

    public ToolType getCurrentTool() {
        return currentTool;
    }

    public void addEntity(GameObject entity) {
        this.entities.add(entity);
    }

    public void removeEntity(GameObject entity) {
        this.entities.remove(entity);
        // Também remover da lista de objetos de runtime (se existir)
        this.runtimeObjects.remove(entity);
    }

    public void clearEntities() {
        this.entities.clear();
        this.runtimeObjects.clear();
    }

    public java.util.List<GameObject> getEntities() {
        return this.entities;
    }
    
    /**
     * Moves an entity to a new index in the entities list.
     * This affects the render order - entities later in the list are rendered on top.
     * @param entity The entity to move
     * @param newIndex The new index position
     */
    public void moveEntityToIndex(GameObject entity, int newIndex) {
        if (entity == null || !entities.contains(entity)) return;
        
        int currentIndex = entities.indexOf(entity);
        if (currentIndex == newIndex) return;
        
        entities.remove(entity);
        
        // Adjust index if removing shifted positions
        if (newIndex > currentIndex) {
            newIndex--;
        }
        
        // Clamp to valid range
        newIndex = Math.max(0, Math.min(newIndex, entities.size()));
        
        entities.add(newIndex, entity);
    }
    
    /**
     * Moves an entity up in the render order (rendered later = on top)
     */
    public void moveEntityUp(GameObject entity) {
        int index = entities.indexOf(entity);
        if (index < entities.size() - 1) {
            moveEntityToIndex(entity, index + 1);
        }
    }
    
    /**
     * Moves an entity down in the render order (rendered earlier = behind)
     */
    public void moveEntityDown(GameObject entity) {
        int index = entities.indexOf(entity);
        if (index > 0) {
            moveEntityToIndex(entity, index - 1);
        }
    }

    // ==================== CAMERA SYSTEM METHODS ====================

    /**
     * Gets the main/active camera.
     * Returns the first active camera found, or mainCamera as fallback.
     */
    public Camera getActiveCamera() {
        for (Camera cam : cameras) {
            if (cam.isActiveCamera()) {
                return cam;
            }
        }
        return mainCamera;
    }

    /**
     * Gets the main camera (the default camera).
     */
    public Camera getMainCamera() {
        return mainCamera;
    }

    /**
     * Sets the main camera.
     */
    public void setMainCamera(Camera camera) {
        if (camera != null) {
            // Deactivate old main camera
            if (mainCamera != null) {
                mainCamera.setActive(false);
            }
            // Set and activate new camera
            mainCamera = camera;
            mainCamera.setActive(true);
            mainCamera.setViewport(viewport);
            mainCamera.setGame(this);
            
            // Add to list if not present
            if (!cameras.contains(camera)) {
                cameras.add(camera);
            }
        }
    }

    /**
     * Adds a camera to the camera list.
     */
    public void addCamera(Camera camera) {
        if (camera != null && !cameras.contains(camera)) {
            camera.setViewport(viewport);
            camera.setGame(this);
            cameras.add(camera);
        }
    }

    /**
     * Removes a camera from the camera list.
     */
    public void removeCamera(Camera camera) {
        cameras.remove(camera);
        // Don't allow removing the main camera
        if (camera == mainCamera && !cameras.isEmpty()) {
            mainCamera = cameras.get(0);
            mainCamera.setActive(true);
        }
    }

    /**
     * Gets all cameras in the scene.
     */
    public List<Camera> getCameras() {
        return cameras;
    }

    /**
     * Gets the viewport.
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * Sets editor camera mode (free camera for editing).
     * When true, the camera transform is applied in the editor.
     */
    public void setEditorCameraMode(boolean enabled) {
        this.editorCameraMode = enabled;
    }

    /**
     * Returns whether editor camera mode is active.
     */
    public boolean isEditorCameraMode() {
        return editorCameraMode;
    }
    
    // ==================== GRID SYSTEM ====================
    
    /**
     * Sets whether the grid should be displayed in editor mode.
     */
    public void setShowGrid(boolean show) {
        this.showGrid = show;
    }
    
    /**
     * Returns whether the grid is being displayed.
     */
    public boolean isShowGrid() {
        return showGrid;
    }
    
    /**
     * Sets the grid cell size in world units.
     */
    public void setGridSize(int size) {
        this.gridSize = Math.max(8, size); // Minimum 8 units
    }
    
    /**
     * Returns the current grid cell size.
     */
    public int getGridSize() {
        return gridSize;
    }
    
    /**
     * Sets the grid color.
     */
    public void setGridColor(Color color) {
        this.gridColor = color;
    }

    // ==================== COORDINATE CONVERSION ====================

    /**
     * Converts world coordinates to screen coordinates using the active camera.
     * 
     * @param worldX X position in world space
     * @param worldY Y position in world space
     * @return Screen position as Point2D.Double
     */
    public Point2D.Double worldToScreen(double worldX, double worldY) {
        Camera cam = getActiveCamera();
        if (cam != null && editorCameraMode) {
            return cam.worldToScreen(worldX, worldY);
        }
        // No camera transform - return as-is
        return new Point2D.Double(worldX, worldY);
    }

    /**
     * Converts screen coordinates to world coordinates using the active camera.
     * 
     * @param screenX X position in screen pixels
     * @param screenY Y position in screen pixels
     * @return World position as Point2D.Double
     */
    public Point2D.Double screenToWorld(double screenX, double screenY) {
        Camera cam = getActiveCamera();
        if (cam != null && editorCameraMode) {
            return cam.screenToWorld(screenX, screenY);
        }
        // No camera transform - return as-is
        return new Point2D.Double(screenX, screenY);
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double amountOfTicks = 60.0;
        double ns = 1000000000 / amountOfTicks;
        double delta = 0;

        while (isRunning) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;

            if (delta >= 1) {
                tick();
                render();
                delta--;
            }
        }

        stop();
    }
}
