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
import java.awt.image.BufferStrategy;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class Game extends Canvas implements Runnable {

    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;

    private Thread thread;
    private boolean isRunning = false;
    
    // ScriptManager para gerenciar scripts
    private ScriptManager scriptManager;
    
    // PrefabManager para instanciar prefabs
    private PrefabManager prefabManager;
    
    // Flag para indicar se o Input foi inicializado
    private boolean inputInitialized = false;

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

    // Gizmo settings
    private static final int GIZMO_SIZE = 60;
    private static final int GIZMO_ARROW_SIZE = 12;
    private static final int GIZMO_HIT_AREA = 15;
    private static final int ROTATE_GIZMO_RADIUS = 50;

    // Gizmo drag states
    private enum GizmoDragMode {
        NONE, AXIS_X, AXIS_Y, CENTER, ROTATE, SCALE_X, SCALE_Y, SCALE_UNIFORM
    }

    private GizmoDragMode currentDragMode = GizmoDragMode.NONE;
    private int dragStartX, dragStartY;
    private double objectStartX, objectStartY;
    private double objectStartRotation;
    private int objectStartWidth, objectStartHeight;

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
        inputInitialized = true;
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

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePress(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseRelease();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDrag(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(e.getX(), e.getY());
            }
        });
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

        int deltaX = mouseX - dragStartX;
        int deltaY = mouseY - dragStartY;

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
                // Calculate rotation based on angle from center
                int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
                int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
                double startAngle = Math.atan2(dragStartY - centerY, dragStartX - centerX);
                double currentAngle = Math.atan2(mouseY - centerY, mouseX - centerX);
                double deltaAngle = Math.toDegrees(currentAngle - startAngle);
                selectedObject.setRotation(objectStartRotation + deltaAngle);
                break;
            case SCALE_X:
                // Scale from center: adjust position to keep center fixed
                int newWidth = Math.max(10, objectStartWidth + deltaX * 2);
                double oldCenterX = objectStartX + objectStartWidth / 2.0;
                selectedObject.setWidth(newWidth);
                selectedObject.setX(oldCenterX - newWidth / 2.0);
                break;
            case SCALE_Y:
                // Scale from center: adjust position to keep center fixed
                int newHeight = Math.max(10, objectStartHeight - deltaY * 2);
                double oldCenterY = objectStartY + objectStartHeight / 2.0;
                selectedObject.setHeight(newHeight);
                selectedObject.setY(oldCenterY - newHeight / 2.0);
                break;
            case SCALE_UNIFORM:
                // Uniform scale from center
                int scaleAmount = (deltaX - deltaY);
                int newUniformWidth = Math.max(10, objectStartWidth + scaleAmount);
                int newUniformHeight = Math.max(10, objectStartHeight + scaleAmount);
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

    private GizmoDragMode getGizmoHitArea(int mouseX, int mouseY) {
        if (selectedObject == null)
            return GizmoDragMode.NONE;

        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;

        switch (currentTool) {
            case MOVE:
                // Check X axis (arrow to right)
                if (mouseX >= centerX && mouseX <= centerX + GIZMO_SIZE &&
                        mouseY >= centerY - GIZMO_HIT_AREA && mouseY <= centerY + GIZMO_HIT_AREA) {
                    return GizmoDragMode.AXIS_X;
                }
                // Check Y axis (arrow up)
                if (mouseX >= centerX - GIZMO_HIT_AREA && mouseX <= centerX + GIZMO_HIT_AREA &&
                        mouseY >= centerY - GIZMO_SIZE && mouseY <= centerY) {
                    return GizmoDragMode.AXIS_Y;
                }
                // Check center
                if (mouseX >= centerX - GIZMO_HIT_AREA && mouseX <= centerX + GIZMO_HIT_AREA &&
                        mouseY >= centerY - GIZMO_HIT_AREA && mouseY <= centerY + GIZMO_HIT_AREA) {
                    return GizmoDragMode.CENTER;
                }
                break;

            case ROTATE:
                // Check if on rotation circle
                double dist = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
                if (dist >= ROTATE_GIZMO_RADIUS - 10 && dist <= ROTATE_GIZMO_RADIUS + 10) {
                    return GizmoDragMode.ROTATE;
                }
                break;

            case SCALE:
                int squareSize = 8;
                // Check X axis square end (scale X)
                if (mouseX >= centerX + GIZMO_SIZE - squareSize && mouseX <= centerX + GIZMO_SIZE + squareSize &&
                        mouseY >= centerY - squareSize && mouseY <= centerY + squareSize) {
                    return GizmoDragMode.SCALE_X;
                }
                // Check Y axis square end (scale Y)
                if (mouseX >= centerX - squareSize && mouseX <= centerX + squareSize &&
                        mouseY >= centerY - GIZMO_SIZE - squareSize && mouseY <= centerY - GIZMO_SIZE + squareSize) {
                    return GizmoDragMode.SCALE_Y;
                }
                // Check center square (uniform scale)
                if (mouseX >= centerX - GIZMO_HIT_AREA && mouseX <= centerX + GIZMO_HIT_AREA &&
                        mouseY >= centerY - GIZMO_HIT_AREA && mouseY <= centerY + GIZMO_HIT_AREA) {
                    return GizmoDragMode.SCALE_UNIFORM;
                }
                break;
        }

        return GizmoDragMode.NONE;
    }

    /**
     * Returns the object at the specified position (top to bottom in render
     * order)
     */
    public GameObject getObjectAt(int x, int y) {
        // Iterate from back to front (objects rendered last are "on
        // top")
        for (int i = entities.size() - 1; i >= 0; i--) {
            GameObject obj = entities.get(i);
            if (x >= obj.getX() && x <= obj.getX() + obj.getWidth() &&
                    y >= obj.getY() && y <= obj.getY() + obj.getHeight()) {
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
        }
    }

    /**
     * Resumes world simulation after pause
     */
    public void resumeWorld() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
        }
    }

    /**
     * Stops simulation and restores objects to initial positions
     */
    public void stopWorld() {
        gameState = GameState.EDITING;
        
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
        
        // Only update objects if in PLAYING state
        if (gameState == GameState.PLAYING) {
            for (int i = 0; i < entities.size(); i++) {
                GameObject entity = entities.get(i);
                entity.tick();
                
                // Executar scripts anexados ao objeto
                entity.tickScripts();
            }
            
            // Verificar colisões simples (AABB)
            checkCollisions();
        }
    }
    
    /**
     * Checks collisions between all objects
     */
    private void checkCollisions() {
        for (int i = 0; i < entities.size(); i++) {
            GameObject a = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                GameObject b = entities.get(j);
                
                // Simple AABB check
                if (a.getX() < b.getX() + b.getWidth() &&
                    a.getX() + a.getWidth() > b.getX() &&
                    a.getY() < b.getY() + b.getHeight() &&
                    a.getY() + a.getHeight() > b.getY()) {
                    
                    // Notificar ambos os objetos sobre a colisão
                    a.notifyCollision(b);
                    b.notifyCollision(a);
                }
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

        g.setColor(Color.GRAY);
        g.fillRect(0, 0, this.getWidth(), this.getHeight());

        // Enable anti-aliasing for smoother rendering
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 0; i < entities.size(); i++) {
            GameObject entity = entities.get(i);

            // Apply rotation transform if entity has rotation
            if (entity.getRotation() != 0) {
                // Save original transform
                java.awt.geom.AffineTransform originalTransform = g2d.getTransform();

                // Calculate center of entity
                double centerX = entity.getX() + entity.getWidth() / 2.0;
                double centerY = entity.getY() + entity.getHeight() / 2.0;

                // Rotate around center
                g2d.rotate(Math.toRadians(entity.getRotation()), centerX, centerY);

                // Render entity
                entity.render(g);

                // Restore original transform
                g2d.setTransform(originalTransform);
            } else {
                entity.render(g);
            }
        }

        // Draw selection and gizmo only in EDITING mode
        if (gameState == GameState.EDITING && selectedObject != null) {
            renderSelection(g2d);
            renderGizmo(g2d);
        }

        g.dispose();

        bs.show();
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

        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Center square
        g2d.setColor(new Color(255, 255, 100));
        int centerSize = 10;
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // X axis (red) - arrow to right
        Color xColor = (currentDragMode == GizmoDragMode.AXIS_X) ? new Color(255, 100, 100) : new Color(220, 50, 50);
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + GIZMO_SIZE, centerY);
        // X arrow head
        Polygon arrowX = new Polygon();
        arrowX.addPoint(centerX + GIZMO_SIZE + GIZMO_ARROW_SIZE, centerY);
        arrowX.addPoint(centerX + GIZMO_SIZE - 2, centerY - GIZMO_ARROW_SIZE / 2);
        arrowX.addPoint(centerX + GIZMO_SIZE - 2, centerY + GIZMO_ARROW_SIZE / 2);
        g2d.fillPolygon(arrowX);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        g2d.drawString("X", centerX + GIZMO_SIZE + GIZMO_ARROW_SIZE + 2, centerY + 4);

        // Y axis (green) - arrow up
        Color yColor = (currentDragMode == GizmoDragMode.AXIS_Y) ? new Color(100, 255, 100) : new Color(50, 200, 50);
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY - GIZMO_SIZE);
        // Y arrow head
        Polygon arrowY = new Polygon();
        arrowY.addPoint(centerX, centerY - GIZMO_SIZE - GIZMO_ARROW_SIZE);
        arrowY.addPoint(centerX - GIZMO_ARROW_SIZE / 2, centerY - GIZMO_SIZE + 2);
        arrowY.addPoint(centerX + GIZMO_ARROW_SIZE / 2, centerY - GIZMO_SIZE + 2);
        g2d.fillPolygon(arrowY);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Y", centerX - 4, centerY - GIZMO_SIZE - GIZMO_ARROW_SIZE - 2);
    }

    /**
     * Renders the rotate gizmo (circle)
     */
    private void renderRotateGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;

        // Rotation circle
        Color circleColor = (currentDragMode == GizmoDragMode.ROTATE) ? new Color(100, 200, 255)
                : new Color(50, 150, 220);
        g2d.setColor(circleColor);
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - ROTATE_GIZMO_RADIUS, centerY - ROTATE_GIZMO_RADIUS,
                ROTATE_GIZMO_RADIUS * 2, ROTATE_GIZMO_RADIUS * 2);

        // Rotation indicator line (shows current rotation)
        double radians = Math.toRadians(selectedObject.getRotation());
        int indicatorX = centerX + (int) (Math.cos(radians) * ROTATE_GIZMO_RADIUS);
        int indicatorY = centerY + (int) (Math.sin(radians) * ROTATE_GIZMO_RADIUS);
        g2d.setColor(new Color(255, 150, 50));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(centerX, centerY, indicatorX, indicatorY);

        // Center point
        g2d.setColor(new Color(50, 150, 220));
        g2d.fillOval(centerX - 5, centerY - 5, 10, 10);

        // Rotation value label
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        g2d.drawString(String.format("%.1f\u00B0", selectedObject.getRotation()),
                centerX + ROTATE_GIZMO_RADIUS + 5, centerY - 5);
    }

    /**
     * Renders the scale gizmo (arrows with square ends like move gizmo)
     */
    private void renderScaleGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        int objW = selectedObject.getWidth();
        int objH = selectedObject.getHeight();
        int squareSize = 8;

        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Center square (uniform scale)
        Color uColor = (currentDragMode == GizmoDragMode.SCALE_UNIFORM) ? new Color(255, 255, 150)
                : new Color(255, 220, 50);
        g2d.setColor(uColor);
        int centerSize = 12;
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // X axis (red) - arrow to right with square end
        Color xColor = (currentDragMode == GizmoDragMode.SCALE_X) ? new Color(255, 100, 100) : new Color(220, 50, 50);
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + GIZMO_SIZE, centerY);
        // X square end
        g2d.fillRect(centerX + GIZMO_SIZE - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX + GIZMO_SIZE - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        g2d.drawString("X", centerX + GIZMO_SIZE + squareSize, centerY + 4);

        // Y axis (green) - arrow up with square end
        Color yColor = (currentDragMode == GizmoDragMode.SCALE_Y) ? new Color(100, 255, 100) : new Color(50, 200, 50);
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY - GIZMO_SIZE);
        // Y square end
        g2d.fillRect(centerX - squareSize / 2, centerY - GIZMO_SIZE - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX - squareSize / 2, centerY - GIZMO_SIZE - squareSize / 2, squareSize, squareSize);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        g2d.drawString("Y", centerX - 4, centerY - GIZMO_SIZE - squareSize - 2);

        // Size label
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.PLAIN, 11));
        g2d.drawString(objW + " x " + objH, centerX + GIZMO_SIZE + squareSize, centerY + 20);
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
