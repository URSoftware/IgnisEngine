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
import com.fxutilities.fxevents.core.GameSignalBus;
import com.fxutilities.fxevents.core.SceneSignalDispatcher;

public class Game extends Canvas implements Runnable {

    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    
    // ==================== CAMERA SYSTEM ====================
    // Main camera for rendering
    private Camera mainCamera;
    
    // Viewport for the game view
    private Viewport viewport;
    
    // List of all cameras (for multi-camera support)
    private List<Camera> cameras = new java.util.concurrent.CopyOnWriteArrayList<>();

    // Flag to indicate if we're in editor camera mode (free camera)
    private boolean editorCameraMode = true;

    // Camera exclusiva da Scene View do editor: navegar (pan/zoom) no editor mexe
    // NELA, nao na camera do jogo. Nunca entra na lista 'cameras' nem e serializada
    // na cena — e um utensilio do editor, como em Unity/Godot.
    private Camera editorCamera;

    // Preview da camera do jogo: quando true (em EDITING), a Scene View renderiza
    // atraves da camera ativa da cena ("ver o que a camera ve"). Quando false
    // (padrao), usa a editorCamera livre — da para inspecionar os assets sem a
    // visao ficar presa a camera do jogo.
    private boolean cameraPreview = false;
    
    // Grid display settings
        private boolean showGrid = false;
        private int gridSize = 32; // Grid cell size in world units
        private Color gridColor = new Color(255, 255, 255, 30); // Semi-transparent white
        private boolean snapToGrid = true; // Snap objects to grid when dragging

    private Thread thread;
    // volatile: written by the EDT (stop) and read by the game loop thread
    private volatile boolean isRunning = false;
    
    // ScriptManager para gerenciar scripts
    private ScriptManager scriptManager;
    
    // PrefabManager para instanciar prefabs
    private PrefabManager prefabManager;
    
    // Collision system
    private IgnisSampleCollisions.CollisionManager collisionManager;
    private boolean showColliders = false; // Debug view for colliders

    // Visualizador de camera (editor): desenha o retangulo de captura de cada camera
    // no viewport, para o criador ver "para onde a camera aponta". So no modo de edicao.
    private boolean showCameraBounds = true;
    
    // ==================== UI SYSTEM ====================
    // Canvas de interface do usuário
    private UICanvas uiCanvas;
    
    // Reference to the editor for displaying alerts
    private Object editorReference = null;
 
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
    private List<GameObject> runtimeObjects = new java.util.concurrent.CopyOnWriteArrayList<>();

    // ==================== EVENT SYSTEM ====================
    private GameSignalBus gameSignalBus = new GameSignalBus();
    private SceneSignalDispatcher sceneSignalDispatcher = new SceneSignalDispatcher();

    public GameSignalBus getSignalBus() { return gameSignalBus; }
    public SceneSignalDispatcher getSceneDispatcher() { return sceneSignalDispatcher; }

    // ==================== SELECTION SYSTEM ====================
    private GameObject selectedObject = null;
    private List<SelectionListener> selectionListeners = new ArrayList<>();
    // Realces secundarios de multi-selecao do editor (alem do selectedObject primario).
    // Puramente visual e aditivo: o editor FX define a lista; renderWorldTo desenha um
    // contorno tracejado para cada um. Nao afeta gizmos/drag/colisao do core.
    private final List<GameObject> editorHighlights = new ArrayList<>();

    // ==================== TOOL SYSTEM ====================
    public enum ToolType {
        MOVE, // Move objects
        ROTATE, // Rotate objects
        SCALE, // Scale objects
        WORLD_PAINT // Pinta/apaga barreiras na grade do World (mundo da cena)
    }

    private ToolType currentTool = ToolType.MOVE;

    // Base gizmo settings (will be scaled based on zoom)
    private static final int BASE_GIZMO_SIZE = 60;
    private static final int BASE_GIZMO_ARROW_SIZE = 12;
    private static final int BASE_GIZMO_HIT_AREA = 30;
    private static final int BASE_ROTATE_GIZMO_RADIUS = 50;
    
    /**
     * Gets the current gizmo size scaled by camera zoom.
     * At lower zoom levels, gizmos appear larger in world space to remain usable.
     */
    private int getScaledGizmoSize() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_SIZE / zoom);
    }

    private int getScaledGizmoArrowSize() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_ARROW_SIZE / zoom);
    }

    private int getScaledGizmoHitArea() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_HIT_AREA / zoom);
    }

    private int getScaledRotateGizmoRadius() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_ROTATE_GIZMO_RADIUS / zoom);
    }

    // Gizmo drag states
    private enum GizmoDragMode {
        NONE, AXIS_X, AXIS_Y, CENTER, ROTATE, SCALE_X, SCALE_Y, SCALE_UNIFORM
    }

    private GizmoDragMode currentDragMode = GizmoDragMode.NONE;
    private GizmoDragMode hoveredGizmoMode = GizmoDragMode.NONE;
    private int dragStartX, dragStartY;
    private double objectStartX, objectStartY;
    private double objectStartRotation;
    private int objectStartWidth, objectStartHeight;

    // ---- Gizmo de collider (item 8b) ----
    // Alcas de redimensionamento da hitbox do ColliderComponent do objeto selecionado.
    // Ativo somente em edicao, com 'showColliders' ligado e um ColliderComponent anexado.
    // Indices das 8 alcas: 0=NW 1=N 2=NE 3=E 4=SE 5=S 6=SW 7=W.
    private int colliderHandle = -1;         // alca sob o cursor durante o arraste (-1 = nenhuma)
    private int hoveredColliderHandle = -1;  // alca sob o cursor no hover (feedback de cursor)
    private boolean draggingCollider = false;
    private double colStartMinX, colStartMinY, colStartW, colStartH; // bounds no inicio do arraste

    // ---- Pintura de barreiras do World (ferramenta WORLD_PAINT) ----
    private boolean paintingWorld = false;   // arraste de pincel em andamento
    private boolean worldPaintErase = false; // stroke atual apaga (Ctrl) em vez de bloquear
    
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

    // Notifica inicio/fim de um arraste de redimensionamento de collider (item 8b),
    // para o editor registrar undo/redo do ColliderComponent.
    public interface ColliderEditListener {
        void onColliderEditStart(GameObject owner, ColliderComponent collider);
        void onColliderEditEnd(GameObject owner, ColliderComponent collider);
    }

    private ColliderEditListener colliderEditListener;

    public void setColliderEditListener(ColliderEditListener listener) {
        this.colliderEditListener = listener;
    }

    // Notifica inicio/fim de um traco de pintura de barreiras, para o editor registrar
    // undo/redo do conjunto de celulas bloqueadas do World.
    public interface WorldPaintListener {
        void onPaintStrokeStart();
        void onPaintStrokeEnd();
    }

    private WorldPaintListener worldPaintListener;

    public void setWorldPaintListener(WorldPaintListener listener) {
        this.worldPaintListener = listener;
    }

    /** Define se o proximo traco de WORLD_PAINT apaga (true) ou bloqueia (false). */
    public void setWorldPaintErase(boolean erase) {
        this.worldPaintErase = erase;
    }

    // Quando true, chamadas a repaint() sao ignoradas. Usado pelo editor JavaFX,
    // cujo AnimationTimer renderiza via renderWorldTo — o pipeline AWT seria desperdicio.
    private boolean suppressAwtRepaint = false;

    public void setSuppressAwtRepaint(boolean suppress) {
        this.suppressAwtRepaint = suppress;
    }

    @Override
    public void repaint() {
        if (suppressAwtRepaint) return;
        super.repaint();
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
            IgnisLogger.error("Nao foi possivel criar Robot para infinite drag: " + e.getMessage());
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
                    if (editorCamera != null) {
                        editorCamera.setViewport(viewport);
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

        // Camera livre da Scene View — fora da lista 'cameras' de proposito:
        // nao aparece na Hierarchy, nao e salva na cena e nao vira camera ativa.
        editorCamera = new Camera("EditorCamera", this, 0, 0);
        editorCamera.setViewport(viewport);
        editorCamera.setGame(this);
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
     * Define a referência do editor para exibir alertas
     */
    public void setEditor(Object editor) {
        this.editorReference = editor;
    }
    
    /**
     * Exibe uma mensagem de alerta no editor (se disponível)
     * @param message A mensagem a ser exibida
     */
    public void alert(String message) {
        if (editorReference != null) {
            try {
                // Use reflection para chamar o método alert do editor
                Class<?> editorClass = editorReference.getClass();
                java.lang.reflect.Method alertMethod = editorClass.getMethod("alert", String.class);
                alertMethod.invoke(editorReference, message);
            } catch (Exception e) {
                // Silenciosamente ignorar se o editor não tiver o método alert
                IgnisLogger.warn("Aviso: Nao foi possivel chamar alert() no editor: " + e.getMessage());
            }
        } else {
            // Se não há editor, exibir no console
            IgnisLogger.info("[ALERT] " + message);
        }
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

    /** Liga/desliga o visualizador de campo de visao das cameras (editor). */
    public void setShowCameraBounds(boolean show) {
        this.showCameraBounds = show;
    }

    /** Se o visualizador de campo de visao das cameras esta ligado. */
    public boolean isShowCameraBounds() {
        return showCameraBounds;
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
            IgnisLogger.error("PrefabManager nao esta disponivel. Certifique-se de que um projeto esta aberto.");
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
            
            // Se estiver em modo de jogo, inicializar scripts. addComponent mantem
            // components/scripts coerentes e ja chama start() quando PLAYING.
            if (gameState == GameState.PLAYING && scriptManager != null) {
                for (String scriptName : new java.util.ArrayList<>(instance.getScriptNames())) {
                    IgnisScript script = scriptManager.createScriptInstance(scriptName, instance, this);
                    if (script != null) {
                        instance.addComponent(script);
                    }
                }
            }
            
            IgnisLogger.info("Prefab '" + prefabName + "' instanciada como '" + instance.getName() + "' em (" + x + ", " + y + ")");
        } else {
            IgnisLogger.error("Falha ao instanciar prefab '" + prefabName + "'. Verifique se a prefab existe.");
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

    /**
     * CanvasComponents visiveis de todas as entidades, ordenados por
     * sortingOrder (menor desenha primeiro; maior fica na frente e recebe
     * input antes). UI por-objeto persistida na cena — ver CanvasComponent.
     */
    public java.util.List<CanvasComponent> getCanvasComponents() {
        java.util.List<CanvasComponent> list = new java.util.ArrayList<>();
        for (GameObject go : entities) {
            if (!go.isVisible()) continue;
            CanvasComponent cc = go.getComponent(CanvasComponent.class);
            if (cc != null) list.add(cc);
        }
        list.sort(java.util.Comparator.comparingInt(CanvasComponent::getSortingOrder));
        return list;
    }

    // Roteia clique de mouse para a UI durante o Play: CanvasComponents do topo
    // para o fundo, depois o canvas global de runtime. true = UI consumiu.
    private boolean routeMouseClickToUi(MouseEvent e, boolean pressed) {
        if (gameState != GameState.PLAYING) return false;
        java.util.List<CanvasComponent> ccs = getCanvasComponents();
        for (int i = ccs.size() - 1; i >= 0; i--) {
            if (ccs.get(i).processMouseClick(e, pressed)) return true;
        }
        return uiCanvas != null && uiCanvas.isVisible() && uiCanvas.processMouseClick(e, pressed);
    }

    // Idem para movimento do mouse (hover de botoes etc.).
    private void routeMouseMoveToUi(MouseEvent e) {
        if (gameState != GameState.PLAYING) return;
        java.util.List<CanvasComponent> ccs = getCanvasComponents();
        for (int i = ccs.size() - 1; i >= 0; i--) {
            if (ccs.get(i).processMouseMove(e)) return;
        }
        if (uiCanvas != null && uiCanvas.isVisible()) uiCanvas.processMouseMove(e);
    }

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // UI (CanvasComponents + canvas global) tem prioridade no Play
                if (routeMouseClickToUi(e, true)) {
                    e.consume();
                    return;
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
                // UI (CanvasComponents + canvas global) tem prioridade no Play
                if (routeMouseClickToUi(e, false)) {
                    e.consume();
                    return;
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
                // UI (CanvasComponents + canvas global) recebe hover no Play
                routeMouseMoveToUi(e);

                // Handle panning first
                if (isPanning) {
                    handlePanning(e.getX(), e.getY());
                    return;
                }
                handleMouseDrag(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // UI (CanvasComponents + canvas global) recebe hover no Play
                routeMouseMoveToUi(e);

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
        Camera cam = getViewCamera();
        if (cam != null) {
            camStartX = cam.getX();
            camStartY = cam.getY();
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }
    
    private void handlePanning(int x, int y) {
        if (!isPanning || gameState != GameState.EDITING) return;

        Camera cam = getViewCamera();
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

        // Ferramenta de pintura de barreiras: um clique/arraste bloqueia (ou apaga com
        // Ctrl) celulas da grade do World. Nao seleciona nem move objetos.
        if (currentTool == ToolType.WORLD_PAINT) {
            if (world != null) {
                paintingWorld = true;
                if (worldPaintListener != null) worldPaintListener.onPaintStrokeStart();
                paintCellAt(mouseX, mouseY);
            }
            return;
        }

        // Gizmo de collider (item 8b): alcas da hitbox tem precedencia sobre o gizmo
        // de transform quando o modo de edicao de collider esta ativo.
        int ch = getColliderHandleAt(mouseX, mouseY);
        if (ch != -1) {
            ColliderComponent cc = editableCollider();
            double[] b = cc != null ? cc.getWorldBounds() : null;
            if (b != null) {
                colliderHandle = ch;
                draggingCollider = true;
                colStartMinX = b[0];
                colStartMinY = b[1];
                colStartW = b[2];
                colStartH = b[3];
                dragStartX = mouseX;
                dragStartY = mouseY;
                if (colliderEditListener != null) {
                    colliderEditListener.onColliderEditStart(selectedObject, cc);
                }
                return;
            }
        }

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

        // Check if clicked on any object (cicla entre objetos sobrepostos a cada clique)
        GameObject clicked = getObjectAt(mouseX, mouseY, selectedObject);
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
        // Fim do traco de pintura de barreiras (WORLD_PAINT).
        if (paintingWorld) {
            paintingWorld = false;
            if (worldPaintListener != null) worldPaintListener.onPaintStrokeEnd();
            return;
        }
        // Fim do arraste de collider (item 8b): marca o projeto sujo via o mesmo
        // listener (o transform do objeto nao mudou, entao nenhum comando de undo e
        // gerado; apenas dispara markProjectDirty no editor).
        if (draggingCollider) {
            draggingCollider = false;
            colliderHandle = -1;
            if (selectedObject != null && colliderEditListener != null) {
                colliderEditListener.onColliderEditEnd(selectedObject,
                        selectedObject.getComponent(ColliderComponent.class));
            } else if (selectedObject != null && transformListener != null) {
                transformListener.onTransformEnd(selectedObject);
            }
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        // Notificar fim de transformacao (para undo/auto-save)
        if (currentDragMode != GizmoDragMode.NONE && selectedObject != null && transformListener != null) {
            transformListener.onTransformEnd(selectedObject);
        }
        
        currentDragMode = GizmoDragMode.NONE;
        setCursor(Cursor.getDefaultCursor());
        // Notificacao de selecao NAO e necessaria aqui: o Inspector do editor FX ja
        // sincroniza via AnimationTimer a 60fps (updateInspectorFields). Notificar
        // redundantemente enfileirava lambdas extras em Platform.runLater, contribuindo
        // para o loop de selecao infinita.
    }

    private void handleMouseDrag(int mouseX, int mouseY) {
        // Arraste do pincel de barreiras (WORLD_PAINT).
        if (paintingWorld) {
            paintCellAt(mouseX, mouseY);
            return;
        }
        // Arraste de alca de collider (item 8b) — independente do gizmo de transform.
        if (draggingCollider) {
            handleColliderDrag(mouseX, mouseY);
            return;
        }
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
        if (robot != null && isShowing()) {
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
                        if (snapToGrid) selectedObject.setX(snapToGrid(selectedObject.getX()));
                        break;
                    case AXIS_Y:
                        selectedObject.setY(objectStartY + deltaY);
                        if (snapToGrid) selectedObject.setY(snapToGrid(selectedObject.getY()));
                        break;
                    case CENTER:
                        selectedObject.setX(objectStartX + deltaX);
                        selectedObject.setY(objectStartY + deltaY);
                        if (snapToGrid) {
                            selectedObject.setX(snapToGrid(selectedObject.getX()));
                            selectedObject.setY(snapToGrid(selectedObject.getY()));
                        }
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
                        if (snapToGrid) selectedObject.setX(snapToGrid(selectedObject.getX()));
                        break;
                    case SCALE_Y:
                        // Scale from center: adjust position to keep center fixed
                        // Dragging up (positive Y) increases height
                        int newHeight = Math.max(1, objectStartHeight + (int)(deltaY * 2));
                        double oldCenterY = objectStartY + objectStartHeight / 2.0;
                        selectedObject.setHeight(newHeight);
                        selectedObject.setY(oldCenterY - newHeight / 2.0);
                        if (snapToGrid) selectedObject.setY(snapToGrid(selectedObject.getY()));
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
                        if (snapToGrid) {
                            selectedObject.setX(snapToGrid(selectedObject.getX()));
                            selectedObject.setY(snapToGrid(selectedObject.getY()));
                        }
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

        // Hover nas alcas do gizmo de collider (item 8b): cursor de redimensionamento
        // direcional, com precedencia sobre o gizmo de transform.
        int colHandle = getColliderHandleAt(mouseX, mouseY);
        if (colHandle != hoveredColliderHandle) {
            hoveredColliderHandle = colHandle;
            repaint();
        }
        if (colHandle != -1) {
            setCursor(Cursor.getPredefinedCursor(colliderHandleCursor(colHandle)));
            return;
        }

        if (selectedObject != null) {
            GizmoDragMode mode = getGizmoHitArea(mouseX, mouseY);
            if (mode != hoveredGizmoMode) {
                hoveredGizmoMode = mode;
                repaint();
            }
            switch (mode) {
                case AXIS_X:
                case SCALE_X:
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    return;
                case AXIS_Y:
                case SCALE_Y:
                    setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                    return;
                case CENTER:
                case SCALE_UNIFORM:
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                case ROTATE:
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                default:
                    break;
            }
        } else {
            if (hoveredGizmoMode != GizmoDragMode.NONE) {
                hoveredGizmoMode = GizmoDragMode.NONE;
                repaint();
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
        int scaledHitTolerance = (int)(25 / (getViewCamera() != null ? getViewCamera().getZoom() : 1.0));

        switch (currentTool) {
            case MOVE:
                // Check center first (precedence)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return GizmoDragMode.CENTER;
                }
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
                break;

            case ROTATE:
                // Check if on rotation circle
                double dist = Math.sqrt(Math.pow(mouseX - centerX, 2) + Math.pow(mouseY - centerY, 2));
                if (dist >= rotateRadius - scaledHitTolerance && dist <= rotateRadius + scaledHitTolerance) {
                    return GizmoDragMode.ROTATE;
                }
                break;

            case SCALE:
                int squareSize = (int)(20 / (getViewCamera() != null ? getViewCamera().getZoom() : 1.0));
                // Check center square first (uniform scale precedence)
                if (mouseX >= centerX - hitArea && mouseX <= centerX + hitArea &&
                        mouseY >= centerY - hitArea && mouseY <= centerY + hitArea) {
                    return GizmoDragMode.SCALE_UNIFORM;
                }
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
                break;
        }

        return GizmoDragMode.NONE;
    }

    // ==================== GIZMO DE COLLIDER (item 8b) ====================

    /**
     * ColliderComponent do objeto selecionado elegivel para edicao por gizmo, ou
     * {@code null}. Requer modo de edicao, {@code showColliders} ligado e um
     * ColliderComponent anexado.
     */
    private ColliderComponent editableCollider() {
        if (gameState != GameState.EDITING || !showColliders || selectedObject == null
                || selectedObject instanceof Camera) {
            return null;
        }
        return selectedObject.getComponent(ColliderComponent.class);
    }

    /** Bloqueia (ou apaga, se worldPaintErase) a celula do World sob o ponto de tela. */
    private void paintCellAt(int screenX, int screenY) {
        if (world == null) return;
        Point2D.Double wp = screenToWorld(screenX, screenY);
        int col = world.cellCol(wp.x);
        int row = world.cellRow(wp.y);
        if (worldPaintErase) {
            world.unblockCell(col, row);
        } else {
            world.blockCell(col, row);
        }
        repaint();
    }

    /** Fator mundo-por-pixel da camera de edicao (1.0 sem transform de camera). */
    private double editorWorldPerPixel() {
        Camera cam = getViewCamera();
        double zoom = (cam != null && editorCameraMode) ? cam.getZoom() : 1.0;
        return (zoom > 0) ? 1.0 / zoom : 1.0;
    }

    /** Ponto em mundo da alca {@code handle} (0..7) para o bounds {@code [minX,minY,w,h]}. */
    private double[] colliderHandlePoint(double[] b, int handle) {
        double minX = b[0], minY = b[1], w = b[2], h = b[3];
        double midX = minX + w / 2.0, midY = minY + h / 2.0, maxX = minX + w, maxY = minY + h;
        switch (handle) {
            case 0: return new double[] { minX, minY }; // NW
            case 1: return new double[] { midX, minY }; // N
            case 2: return new double[] { maxX, minY }; // NE
            case 3: return new double[] { maxX, midY }; // E
            case 4: return new double[] { maxX, maxY }; // SE
            case 5: return new double[] { midX, maxY }; // S
            case 6: return new double[] { minX, maxY }; // SW
            case 7: return new double[] { minX, midY }; // W
            default: return new double[] { midX, midY };
        }
    }

    /** Cursor de redimensionamento AWT correspondente a alca de collider (0..7). */
    private int colliderHandleCursor(int handle) {
        switch (handle) {
            case 0: return Cursor.NW_RESIZE_CURSOR;
            case 1: return Cursor.N_RESIZE_CURSOR;
            case 2: return Cursor.NE_RESIZE_CURSOR;
            case 3: return Cursor.E_RESIZE_CURSOR;
            case 4: return Cursor.SE_RESIZE_CURSOR;
            case 5: return Cursor.S_RESIZE_CURSOR;
            case 6: return Cursor.SW_RESIZE_CURSOR;
            case 7: return Cursor.W_RESIZE_CURSOR;
            default: return Cursor.DEFAULT_CURSOR;
        }
    }

    /**
     * Indice (0..7) da alca de collider sob o ponto de tela, ou -1. So retorna algo
     * quando {@link #editableCollider()} nao e nulo.
     */
    private int getColliderHandleAt(int screenX, int screenY) {
        ColliderComponent cc = editableCollider();
        if (cc == null) return -1;
        double[] b = cc.getWorldBounds();
        if (b == null) return -1;
        Point2D.Double world = screenToWorld(screenX, screenY);
        double tol = 7.0 * editorWorldPerPixel();
        for (int i = 0; i < 8; i++) {
            double[] p = colliderHandlePoint(b, i);
            if (Math.abs(world.x - p[0]) <= tol && Math.abs(world.y - p[1]) <= tol) {
                return i;
            }
        }
        return -1;
    }

    /** Aplica o arraste de uma alca de collider, redimensionando a hitbox em mundo. */
    private void handleColliderDrag(int mouseX, int mouseY) {
        ColliderComponent cc = editableCollider();
        if (cc == null) {
            draggingCollider = false;
            colliderHandle = -1;
            return;
        }
        Point2D.Double startW = screenToWorld(dragStartX, dragStartY);
        Point2D.Double curW = screenToWorld(mouseX, mouseY);
        double dx = curW.x - startW.x;
        double dy = curW.y - startW.y;

        double minX = colStartMinX, minY = colStartMinY;
        double maxX = colStartMinX + colStartW, maxY = colStartMinY + colStartH;

        boolean top = (colliderHandle == 0 || colliderHandle == 1 || colliderHandle == 2);
        boolean bottom = (colliderHandle == 4 || colliderHandle == 5 || colliderHandle == 6);
        boolean left = (colliderHandle == 0 || colliderHandle == 6 || colliderHandle == 7);
        boolean right = (colliderHandle == 2 || colliderHandle == 3 || colliderHandle == 4);

        if (top) minY += dy;
        if (bottom) maxY += dy;
        if (left) minX += dx;
        if (right) maxX += dx;

        // Normaliza (permite arrastar uma borda para alem da oposta sem inverter).
        double newMinX = Math.min(minX, maxX);
        double newMinY = Math.min(minY, maxY);
        double newW = Math.abs(maxX - minX);
        double newH = Math.abs(maxY - minY);
        cc.resizeToWorldBounds(newMinX, newMinY, newW, newH);
    }

    /**
     * Desenha o contorno da hitbox e as 8 alcas de redimensionamento do collider do
     * objeto selecionado (item 8b). Chamado em espaco de mundo (transform de camera
     * aplicada) pelo pipeline de render do editor.
     */
    private void renderColliderGizmo(Graphics2D g2d, ColliderComponent cc) {
        double[] b = cc.getWorldBounds();
        if (b == null) return;
        double minX = b[0], minY = b[1], w = b[2], h = b[3];
        double wpp = editorWorldPerPixel();

        Color c = cc.isTrigger() ? new Color(80, 220, 120) : new Color(0, 200, 255);
        g2d.setColor(c);
        float dash = (float) (5.0 * wpp);
        g2d.setStroke(new BasicStroke((float) Math.max(1.0, 1.5 * wpp),
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                new float[] { dash, dash }, 0f));
        if ("Sphere".equalsIgnoreCase(cc.getShape())) {
            g2d.drawOval((int) minX, (int) minY, (int) w, (int) h);
        } else {
            g2d.drawRect((int) minX, (int) minY, (int) w, (int) h);
        }

        double hs = 5.0 * wpp; // meia-aresta das alcas (tamanho ~constante em tela)
        g2d.setStroke(new BasicStroke((float) Math.max(1.0, wpp)));
        for (int i = 0; i < 8; i++) {
            double[] p = colliderHandlePoint(b, i);
            g2d.setColor(Color.WHITE);
            g2d.fillRect((int) (p[0] - hs), (int) (p[1] - hs), (int) (hs * 2), (int) (hs * 2));
            g2d.setColor(c);
            g2d.drawRect((int) (p[0] - hs), (int) (p[1] - hs), (int) (hs * 2), (int) (hs * 2));
        }
    }

    /**
     * Desenha o retangulo de captura (frustum 2D) de cada camera da cena em espaco de
     * mundo, com uma cruz no centro (posicao da camera) e o nome. A camera ativa recebe
     * destaque (amarelo preenchido); as demais ficam tracejadas em cinza. {@code designW/H}
     * e a resolucao de referencia usada para o tamanho da captura em zoom 1.
     */
    private void renderCameraBounds(Graphics2D g2d, int designW, int designH, GameObject selected,
                                    AffineTransform screenTransform) {
        if (cameras == null || cameras.isEmpty()) return;
        double wpp = editorWorldPerPixel();
        Camera active = getActiveCamera();
        java.awt.Font baseFont = g2d.getFont();
        AffineTransform worldTransform = g2d.getTransform();
        for (Camera cam : cameras) {
            if (cam == null || !cam.isVisible()) continue;
            double[] r = cam.getFrustumWorldRect(designW, designH);
            boolean isActive = (cam == active) || cam.isActiveCamera();
            boolean isSel = (cam == selected);
            Color col = isActive ? new Color(255, 210, 40) : new Color(165, 165, 175);

            float sw = (float) Math.max(1.0, (isActive ? 2.0 : 1.5) * wpp);
            if (isSel) {
                g2d.setStroke(new BasicStroke(sw));
            } else {
                float d = (float) (6.0 * wpp);
                g2d.setStroke(new BasicStroke(sw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[] { d, d }, 0f));
            }
            g2d.setColor(col);
            g2d.drawRect((int) r[0], (int) r[1], (int) r[2], (int) r[3]);
            if (isActive) {
                g2d.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 22));
                g2d.fillRect((int) r[0], (int) r[1], (int) r[2], (int) r[3]);
            }

            // Cruz no centro = posicao da camera.
            double cx = cam.getX(), cy = cam.getY();
            double cs = 8.0 * wpp;
            g2d.setColor(col);
            g2d.setStroke(new BasicStroke((float) Math.max(1.0, wpp)));
            g2d.drawLine((int) (cx - cs), (int) cy, (int) (cx + cs), (int) cy);
            g2d.drawLine((int) cx, (int) (cy - cs), (int) cx, (int) (cy + cs));

            // Rotulo em ESPACO DE TELA: a transform da camera inverte o eixo Y, o que
            // espelharia o texto se desenhado em espaco de mundo. O canto superior-
            // esquerdo do frustum na tela corresponde ao mundo (minX, maxY) por causa da
            // inversao de Y.
            String label = (cam.getName() != null ? cam.getName() : "Camera") + (isActive ? " (ativa)" : "");
            Point2D.Double topLeft = worldToScreen(r[0], r[1] + r[3]);
            g2d.setTransform(screenTransform != null ? screenTransform : new AffineTransform());
            g2d.setFont(baseFont.deriveFont(11f));
            g2d.setColor(col);
            g2d.drawString(label, (float) (topLeft.x + 4.0), (float) (topLeft.y + 13.0));
            g2d.setTransform(worldTransform);
        }
        g2d.setFont(baseFont);
    }

    /**
     * Returns the object at the specified screen position (top to bottom in render order).
     * Converts screen coordinates to world coordinates for proper picking.
     */
    public GameObject getObjectAt(int screenX, int screenY) {
        return getObjectAt(screenX, screenY, null);
    }

    /**
     * Picking ciente de z-order, rotacao e sobreposicao. Coleta todos os objetos
     * sob o ponto, do topo para o fundo (ordem inversa de render). Se {@code afterCurrent}
     * estiver entre eles, retorna o PROXIMO (cicla entre objetos empilhados a cada clique
     * no mesmo ponto); caso contrario, retorna o do topo.
     */
    public GameObject getObjectAt(int screenX, int screenY, GameObject afterCurrent) {
        Point2D.Double worldPos = screenToWorld(screenX, screenY);
        double wx = worldPos.x;
        double wy = worldPos.y;

        java.util.List<GameObject> hits = new java.util.ArrayList<>();
        // Iterate from front to back (objects rendered last are "on top")
        for (int i = entities.size() - 1; i >= 0; i--) {
            GameObject obj = entities.get(i);
            if (obj instanceof Camera) continue; // cameras nao sao selecionaveis por clique
            if (hitTest(obj, wx, wy)) hits.add(obj);
        }
        if (hits.isEmpty()) return null;
        if (afterCurrent != null) {
            int idx = hits.indexOf(afterCurrent);
            if (idx >= 0) return hits.get((idx + 1) % hits.size());
        }
        return hits.get(0);
    }

    /**
     * Testa se um ponto em coordenadas de mundo cai sobre o objeto, respeitando a
     * rotacao (mesma convencao do render: {@code g2d.rotate(+rotation)} em torno do
     * centro). Para objetos sem rotacao recai num teste de AABB simples.
     */
    private boolean hitTest(GameObject obj, double wx, double wy) {
        double w = obj.getWidth();
        double h = obj.getHeight();
        double lx = wx;
        double ly = wy;
        double rot = obj.getRotation();
        if (rot != 0) {
            double cx = obj.getX() + w / 2.0;
            double cy = obj.getY() + h / 2.0;
            double rad = Math.toRadians(-rot); // desfaz a rotacao aplicada no render
            double dx = wx - cx;
            double dy = wy - cy;
            lx = cx + dx * Math.cos(rad) - dy * Math.sin(rad);
            ly = cy + dx * Math.sin(rad) + dy * Math.cos(rad);
        }
        return lx >= obj.getX() && lx <= obj.getX() + w
                && ly >= obj.getY() && ly <= obj.getY() + h;
    }

    // ==================== SELECTION ====================

    public void setSelectedObject(GameObject obj) {
        this.selectedObject = obj;
        notifySelectionListeners();
        repaint();
    }

    public void cancelDrag() {
        if (currentDragMode != GizmoDragMode.NONE && selectedObject != null && transformListener != null) {
            transformListener.onTransformEnd(selectedObject);
        }
        currentDragMode = GizmoDragMode.NONE;
        draggingCollider = false;
        colliderHandle = -1;
        isPanning = false;
        setCursor(Cursor.getDefaultCursor());
    }

    public GameObject getSelectedObject() {
        return selectedObject;
    }

    /**
     * Define os realces secundarios da multi-selecao do editor (alem do objeto
     * primario). Apenas visual: cada um recebe um contorno tracejado em
     * {@link #renderWorldTo}. Passar null/vazio limpa os realces. Aditivo — nao
     * altera gizmos, drag, colisao nem o objeto selecionado primario.
     */
    public synchronized void setEditorHighlights(List<GameObject> objs) {
        editorHighlights.clear();
        if (objs != null) editorHighlights.addAll(objs);
        repaint();
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
        // Joining from the loop thread itself would deadlock (self-join)
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        IgnisLogger.info("Objetos de runtime removidos.");
        
        // Restore initial positions
        restoreInitialSnapshots();
        
        // Resetar scripts e animadores (restaura sprite anterior à animação)
        for (GameObject entity : entities) {
            entity.resetScripts();
            entity.resetAnimator();
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

    private java.util.List<GameObject> entities = new java.util.concurrent.CopyOnWriteArrayList<>();

    // synchronized no mesmo monitor de render()/renderWorldTo(): a simulacao roda
    // na thread do loop do jogo (Game.run) enquanto o editor JavaFX renderiza na
    // thread do AnimationTimer. Sem exclusao mutua, o render podia ler um objeto
    // com x ja atualizado e y ainda antigo (frame "rasgado"). Ver Fase A do plano.
    public synchronized void tick() {
        // Update Input system
        Input.update();

        // Update UI Canvas (even in editing mode for preview)
        if (uiCanvas != null) {
            uiCanvas.update();
        }

        // Only update objects if in PLAYING state
        if (gameState == GameState.PLAYING) {
            // Passo 1: fixa o transform anterior de TODAS as entidades (inclusive as
            // paradas) antes de qualquer atualizacao, base da interpolacao de render.
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).capturePreviousTransform();
            }
            // Passo 2: avanca a simulacao.
            for (int i = 0; i < entities.size(); i++) {
                GameObject entity = entities.get(i);
                entity.tick();

                // Advance sprite animation (fixed 60 ticks/sec step)
                entity.tickAnimator(1.0 / 60.0);

                // Executar scripts anexados ao objeto
                entity.tickScripts();

                // Componentes NAO-script (Collider/Health/futuros) tambem avancam —
                // antes deste hook o update(dt) deles nunca era chamado pelo loop.
                entity.tickComponents(1.0f / 60.0f);
            }

            // Resolve limites/barreiras do World para objetos com worldCollision.
            // Usa o transform anterior (prevX/prevY) como origem do movimento.
            if (world != null && world.isActive()) {
                for (int i = 0; i < entities.size(); i++) {
                    GameObject e = entities.get(i);
                    if (!e.isWorldCollision() || e instanceof Camera) continue;
                    double[] r = world.resolveMovement(e.getPrevX(), e.getPrevY(),
                            e.getX(), e.getY(), e.getWidth(), e.getHeight());
                    e.setX(r[0]);
                    e.setY(r[1]);
                }
            }

            // Run collision detection using the advanced collision system
            if (collisionManager != null) {
                collisionManager.update();
            }

            // Avanca os comportamentos nativos da camera ativa (follow/shake/bounds).
            Camera activeCam = getActiveCamera();
            if (activeCam != null) {
                activeCam.update(1.0 / 60.0);
                // Limites do mundo mandam na camera: ela nao mostra alem do mapa.
                // Clampe DIRETO na posicao final (funciona com follow, shake ou
                // posicionamento manual por script — independe de cam.update()).
                if (world != null && world.hasBounds()) {
                    clampCameraToWorld(activeCam);
                }
            }

            // Marca o instante do tick para o calculo do alpha de interpolacao.
            lastTickNanos = System.nanoTime();
            
            // Processa os sinais pendentes no dispatcher da cena e no bus global
            if (sceneSignalDispatcher != null) sceneSignalDispatcher.processPendingSignals();
            if (gameSignalBus != null) gameSignalBus.processGlobalSignals();
        }
    }

    // ---- Interpolacao de render (Fase A do plano do motor grafico) ----
    // Simulacao fixa em 60 Hz; o editor JavaFX renderiza na taxa do monitor
    // (75/120/144 Hz). Sem interpolacao, o movimento continuo apresenta judder.
    private volatile long lastTickNanos = 0L;
    private static final double NS_PER_TICK = 1_000_000_000.0 / 60.0;
    // Distancia^2 (em px) acima da qual NAO interpolamos — trata teleporte
    // (setPosition com salto grande) como corte seco em vez de deslize na tela.
    private static final double INTERP_SNAP_SQ = 256.0 * 256.0;

    /**
     * Fracao [0,1] do tick atual ja decorrida, usada para interpolar posicoes no
     * render. Retorna 1.0 fora do modo PLAYING (desenha o transform atual, sem
     * interpolacao) — logo o editor em edicao nao e afetado.
     */
    public double getRenderAlpha() {
        if (gameState != GameState.PLAYING || lastTickNanos == 0L) return 1.0;
        double alpha = (System.nanoTime() - lastTickNanos) / NS_PER_TICK;
        if (alpha < 0.0) return 0.0;
        if (alpha > 1.0) return 1.0;
        return alpha;
    }

    /**
     * Deslocamento X interpolado do objeto para o frame atual (prevX -&gt; x),
     * com corte seco em teleportes. Igual a {@code entity.getX()} fora do Play.
     */
    private double interpX(GameObject e, double alpha) {
        if (alpha >= 1.0) return e.getX();
        double dx = e.getX() - e.getPrevX();
        double dy = e.getY() - e.getPrevY();
        if (dx * dx + dy * dy > INTERP_SNAP_SQ) return e.getX();
        return e.getPrevX() + dx * alpha;
    }

    private double interpY(GameObject e, double alpha) {
        if (alpha >= 1.0) return e.getY();
        double dx = e.getX() - e.getPrevX();
        double dy = e.getY() - e.getPrevY();
        if (dx * dx + dy * dy > INTERP_SNAP_SQ) return e.getY();
        return e.getPrevY() + dy * alpha;
    }

    // ---- Ordem de render por zIndex (Fase B) ----
    // Buffer reutilizado (evita alocacao por frame). Chamado apenas dentro dos
    // metodos de render, que sao synchronized(this) — sem concorrencia sobre ele.
    private final java.util.ArrayList<GameObject> renderOrder = new java.util.ArrayList<>();

    private java.util.List<GameObject> entitiesInRenderOrder() {
        renderOrder.clear();
        renderOrder.addAll(entities);
        // Sort estavel (TimSort): empates de zIndex mantem a ordem da hierarquia.
        renderOrder.sort(java.util.Comparator.comparingInt(GameObject::getZIndex));
        return renderOrder;
    }

    // Aplica interpolacao de posicao + flip/escala visual (em torno do centro) ao
    // Graphics2D antes de desenhar a entidade. A opacidade e tratada no loop via
    // AlphaComposite (precisa ser restaurada separadamente do transform).
    private void applyEntityVisual(Graphics2D g2d, GameObject e, double alpha) {
        g2d.translate(interpX(e, alpha) - e.getX(), interpY(e, alpha) - e.getY());
        double vsx = (e.isFlipX() ? -1.0 : 1.0) * e.getScaleX();
        double vsy = (e.isFlipY() ? -1.0 : 1.0) * e.getScaleY();
        if (vsx != 1.0 || vsy != 1.0) {
            double cx = e.getX() + e.getWidth() / 2.0;
            double cy = e.getY() + e.getHeight() / 2.0;
            g2d.translate(cx, cy);
            g2d.scale(vsx, vsy);
            g2d.translate(-cx, -cy);
        }
    }

    // Culling em espaco de mundo: true se o AABB da entidade nao intersecta o
    // retangulo visivel da camera. Conservador — usa a diagonal como meio-lado
    // (cobre qualquer rotacao do objeto) e uma folga fixa, entao nunca corta algo
    // parcialmente visivel. So chamado quando a transform de camera esta aplicada.
    // ---- Sistema de mundos (Fase 1: limites + barreiras) ----
    private World world = null;

    public World getWorld() { return world; }
    public void setWorld(World world) { this.world = world; }

    /** Retorna o World ativo, criando um vazio se ainda nao houver. */
    public World getOrCreateWorld() {
        if (world == null) world = new World();
        return world;
    }

    // Clampa a posicao da camera para que o retangulo visivel nao ultrapasse os
    // limites do mundo (inset por metade da area visivel). Se o mundo e menor que
    // a tela naquele eixo, centraliza. Chamado por tick() quando o World tem bounds.
    private void clampCameraToWorld(Camera cam) {
        double halfVisW = (getWidth() / 2.0) / Math.max(0.0001, cam.getZoom());
        double halfVisH = (getHeight() / 2.0) / Math.max(0.0001, cam.getZoom());
        double x = cam.getX(), y = cam.getY();
        if (world.getMaxX() - world.getMinX() <= 2 * halfVisW) {
            x = (world.getMinX() + world.getMaxX()) / 2.0;
        } else {
            x = Math.max(world.getMinX() + halfVisW, Math.min(world.getMaxX() - halfVisW, x));
        }
        if (world.getMaxY() - world.getMinY() <= 2 * halfVisH) {
            y = (world.getMinY() + world.getMaxY()) / 2.0;
        } else {
            y = Math.max(world.getMinY() + halfVisH, Math.min(world.getMaxY() - halfVisH, y));
        }
        if (x != cam.getX() || y != cam.getY()) cam.setPosition(x, y);
    }

    // Desenha, no espaco do mundo, os limites do mapa (contorno) e as celulas de
    // barreira (preenchimento vermelho translucido) — feedback visual no editor.
    /**
     * Desenha a grade de celulas do World sobre a area visivel, para orientar a
     * pintura de barreiras (ferramenta WORLD_PAINT). Linhas tenues; as celulas ja
     * bloqueadas continuam sendo desenhadas por {@link #drawWorldOverlay}.
     */
    private void drawWorldPaintGrid(Graphics2D g2d, int width, int height) {
        if (world == null) return;
        int cs = world.getCellSize();
        if (cs <= 0) return;
        Camera cam = getViewCamera();
        double[] vis = (cam != null) ? cam.getVisibleWorldBounds() : new double[] { 0, 0, width, height };
        int c0 = world.cellCol(vis[0]) - 1, c1 = world.cellCol(vis[2]) + 1;
        int r0 = world.cellRow(vis[1]) - 1, r1 = world.cellRow(vis[3]) + 1;
        // Limite de seguranca para nao desenhar milhares de linhas em zoom-out extremo.
        if ((long) (c1 - c0) * (r1 - r0) > 20000) return;
        g2d.setColor(new Color(255, 255, 255, 40));
        g2d.setStroke(new BasicStroke((float) Math.max(0.5, editorWorldPerPixel())));
        for (int c = c0; c <= c1; c++) {
            int x = c * cs;
            g2d.drawLine(x, r0 * cs, x, r1 * cs);
        }
        for (int r = r0; r <= r1; r++) {
            int y = r * cs;
            g2d.drawLine(c0 * cs, y, c1 * cs, y);
        }
    }

    private void drawWorldOverlay(Graphics2D g2d) {
        if (world == null) return;
        // Barreiras: so as celulas dentro do retangulo visivel (culling barato).
        if (world.getBlockedCount() > 0) {
            double[] vis = null;
            Camera cam = getViewCamera();
            if (cam != null) vis = cam.getVisibleWorldBounds();
            int cs = world.getCellSize();
            int c0, c1, r0, r1;
            if (vis != null) {
                c0 = world.cellCol(vis[0]); c1 = world.cellCol(vis[2]);
                r0 = world.cellRow(vis[1]); r1 = world.cellRow(vis[3]);
            } else {
                c0 = r0 = -200; c1 = r1 = 200; // fallback limitado
            }
            g2d.setColor(new Color(220, 60, 60, 90));
            java.awt.Color border = new Color(220, 60, 60, 160);
            for (int c = c0; c <= c1; c++) {
                for (int r = r0; r <= r1; r++) {
                    if (!world.isCellBlocked(c, r)) continue;
                    int cx = c * cs, cy = r * cs;
                    g2d.setColor(new Color(220, 60, 60, 90));
                    g2d.fillRect(cx, cy, cs, cs);
                    g2d.setColor(border);
                    g2d.drawRect(cx, cy, cs, cs);
                }
            }
        }
        // Limites do mapa: contorno azul-claro.
        if (world.hasBounds()) {
            g2d.setColor(new Color(80, 170, 255, 220));
            g2d.setStroke(new java.awt.BasicStroke(2f));
            int bx = (int) world.getMinX(), by = (int) world.getMinY();
            int bw = (int) (world.getMaxX() - world.getMinX());
            int bh = (int) (world.getMaxY() - world.getMinY());
            g2d.drawRect(bx, by, bw, bh);
        }
    }

    private boolean isCulled(Camera cam, GameObject e) {
        if (cam == null) return false;
        double[] b = cam.getVisibleWorldBounds(); // [minX, minY, maxX, maxY]
        if (b == null || b.length < 4) return false;
        double cx = e.getX() + e.getWidth() / 2.0;
        double cy = e.getY() + e.getHeight() / 2.0;
        double half = 0.5 * Math.sqrt((double) e.getWidth() * e.getWidth()
                + (double) e.getHeight() * e.getHeight());
        double margin = 32.0; // folga extra de seguranca
        double objMinX = cx - half - margin, objMaxX = cx + half + margin;
        double objMinY = cy - half - margin, objMaxY = cy + half + margin;
        return objMaxX < b[0] || objMinX > b[2] || objMaxY < b[1] || objMinY > b[3];
    }
    
    // ==================== TRANSICAO DE CENAS (runtime) ====================

    /**
     * Estrategia de carregamento de cena por nome. Registrada pelo host (editor ou
     * runtime standalone), que sabe onde estao as cenas do projeto. Permite que
     * scripts troquem de cena via {@link IgnisScript#loadScene(String)} sem o core
     * conhecer o {@code Project}.
     */
    public interface SceneLoader {
        /** Carrega a cena de nome dado no game vivo. Retorna false se nao existir. */
        boolean loadScene(String sceneName);
    }

    private SceneLoader sceneLoader;

    /** Define a estrategia de troca de cena por nome (host: editor/runtime). */
    public void setSceneLoader(SceneLoader loader) {
        this.sceneLoader = loader;
    }

    /**
     * Solicita a troca para a cena de nome dado (chamado por scripts). Delega ao
     * {@link SceneLoader} registrado pelo host.
     *
     * @return true se a cena foi carregada; false se nao ha loader ou a cena nao existe.
     */
    public boolean loadScene(String sceneName) {
        if (sceneLoader == null || sceneName == null) {
            IgnisLogger.warn("loadScene(\"" + sceneName + "\") ignorado: nenhum SceneLoader registrado.");
            return false;
        }
        return sceneLoader.loadScene(sceneName);
    }

    /** Primeiro objeto da cena com a tag dada (ignora caixa), ou null. */
    public GameObject findByTag(String tag) {
        if (tag == null) return null;
        for (GameObject e : entities) {
            if (e.hasTag(tag)) return e;
        }
        return null;
    }

    /** Todos os objetos da cena com a tag dada (ignora caixa). */
    public java.util.List<GameObject> findAllByTag(String tag) {
        java.util.List<GameObject> out = new java.util.ArrayList<>();
        if (tag != null) {
            for (GameObject e : entities) {
                if (e.hasTag(tag)) out.add(e);
            }
        }
        return out;
    }

    /**
     * Registers all entity colliders with the collision manager
     * Call this after loading a scene or adding entities
     */
    public void refreshColliders() {
        if (collisionManager == null) return;
        
        collisionManager.clear();
        for (GameObject entity : entities) {
            // Fonte moderna (item 8c): a hitbox vem do ColliderComponent.
            ColliderComponent cc = entity.getComponent(ColliderComponent.class);
            if (cc != null) {
                cc.ensureRegistered();
            } else if (entity.hasCollider()) {
                // Compat: objetos ainda no par legado colliderType/collisionMode.
                collisionManager.addCollider(entity.getCollider());
            }
        }
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void paint(Graphics g) {
        render();
    }

    public synchronized void render() {
        if (!this.isDisplayable()) {
            return;
        }

        BufferStrategy bs = this.getBufferStrategy();
        if (bs == null) {
            try {
                this.createBufferStrategy(3);
            } catch (IllegalStateException e) {
                // Wait for the component to be fully ready
            }
            return;
        }

        Graphics g = null;
        try {
            g = bs.getDrawGraphics();
        } catch (Exception e) {
            // Buffer strategy was not fully validated/created
            return;
        }

        if (g == null) {
            return;
        }

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
        // 1. Select the view camera (editor livre em EDITING; camera do jogo no Play/preview)
        Camera activeCamera = getViewCamera();

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

        // 4. Render all entities (ordenadas por zIndex; empate = ordem da lista)
        Camera cullCamera = shouldApplyCameraTransform ? activeCamera : null;
        double renderAlpha = getRenderAlpha();
        for (GameObject entity : entitiesInRenderOrder()) {
            // Skip invisible entities
            if (!entity.isVisible()) continue;

            // Skip camera entities from regular rendering (cameras are special)
            if (entity instanceof Camera) continue;

            // Culling: pula entidades totalmente fora do retangulo visivel.
            if (isCulled(cullCamera, entity)) continue;

            // Save entity transform + composite
            AffineTransform entityTransform = g2d.getTransform();
            java.awt.Composite oldComposite = g2d.getComposite();

            // Interpolacao de posicao + flip/escala visual (NAO rotaciona aqui:
            // cada forma ja aplica a propria rotacao dentro de render()).
            applyEntityVisual(g2d, entity, renderAlpha);
            if (entity.getOpacity() < 1.0) {
                g2d.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, (float) entity.getOpacity()));
            }

            // Render entity
            entity.render(g);

            // Restore entity transform + composite
            g2d.setTransform(entityTransform);
            g2d.setComposite(oldComposite);
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

        // CanvasComponents das entidades (UI persistente por objeto), por cima
        // do canvas global, ordenados por sortingOrder.
        for (CanvasComponent cc : getCanvasComponents()) {
            g2d.setTransform(originalTransform);
            cc.render(g2d, getWidth(), getHeight());
        }
        g2d.setTransform(originalTransform);
        
        // ==================== ALERTS RENDERING ====================
        // Render alerts on top of everything
        g2d.setTransform(originalTransform);
        renderAlerts(g2d);

        g.dispose();
        bs.show();
    }

    /**
     * Renderiza o "mundo" (fundo + camera + grid + entidades) em um Graphics2D
     * arbitrario de tamanho width x height. Base da PONTE DE RENDER do editor
     * JavaFX (offscreen BufferedImage -> FxImageBridge -> Canvas JavaFX), sem
     * depender de BufferStrategy/AWT Canvas. Aditivo: NAO altera render() (usado
     * pelo editor Swing). Ver doc/JAVAFX_MIGRATION_PLAN.md (ponte de render).
     */
    public synchronized void renderWorldTo(Graphics2D g2d, int width, int height) {
        renderWorldTo(g2d, width, height, null);
    }

    /**
     * Como {@link #renderWorldTo(Graphics2D, int, int)}, mas tambem desenha um
     * contorno de selecao em torno de {@code selected} (usado pelo editor JavaFX,
     * Fase 2). Aditivo.
     */
    public synchronized void renderWorldTo(Graphics2D g2d, int width, int height, GameObject selected) {
        if (g2d == null || width <= 0 || height <= 0) return;

        g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // Fundo
        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, 0, width, height);

        Camera activeCamera = getViewCamera();
        AffineTransform originalTransform = g2d.getTransform();
        boolean shouldApplyCameraTransform = activeCamera != null
                && (editorCameraMode || gameState == GameState.PLAYING);
        if (shouldApplyCameraTransform) {
            activeCamera.applyTransform(g2d);
        }

        if (gameState == GameState.EDITING && showGrid && shouldApplyCameraTransform) {
            drawGrid(g2d);
        }

        // Entidades (ordenadas por zIndex; empate = ordem da lista/hierarquia)
        Camera cullCamera = shouldApplyCameraTransform ? activeCamera : null;
        double renderAlpha = getRenderAlpha();
        for (GameObject entity : entitiesInRenderOrder()) {
            if (!entity.isVisible()) continue;
            if (entity instanceof Camera) continue;
            if (isCulled(cullCamera, entity)) continue;

            AffineTransform entityTransform = g2d.getTransform();
            java.awt.Composite oldComposite = g2d.getComposite();
            // Interpolacao de posicao + flip/escala visual (suaviza movimento em
            // monitores > 60 Hz; identidade fora do Play). NAO rotacionar aqui.
            applyEntityVisual(g2d, entity, renderAlpha);
            if (entity.getOpacity() < 1.0) {
                g2d.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, (float) entity.getOpacity()));
            }
            entity.render(g2d);
            g2d.setTransform(entityTransform);
            g2d.setComposite(oldComposite);
        }

        // Overlay do World (limites do mapa + barreiras) — so no editor, para o dev
        // ver e ajustar. No espaco do mundo (transform de camera aplicada).
        if (gameState == GameState.EDITING && world != null && world.isActive() && shouldApplyCameraTransform) {
            AffineTransform worldTransform = g2d.getTransform();
            drawWorldOverlay(g2d);
            g2d.setTransform(worldTransform);
        }

        // Grade de pintura de barreiras: mostra as celulas do World enquanto a
        // ferramenta WORLD_PAINT esta ativa (mesmo em mundo ainda vazio).
        if (gameState == GameState.EDITING && currentTool == ToolType.WORLD_PAINT
                && world != null && shouldApplyCameraTransform) {
            AffineTransform paintTransform = g2d.getTransform();
            drawWorldPaintGrid(g2d, width, height);
            g2d.setTransform(paintTransform);
        }

        // Debug de colliders (espaco do mundo) — espelha o render() do editor Swing.
        // Independe de selecao; respeita 'showColliders'.
        if (gameState == GameState.EDITING && showColliders && collisionManager != null) {
            AffineTransform colliderTransform = g2d.getTransform();
            collisionManager.debugRender(g2d);
            g2d.setTransform(colliderTransform);
        }

        // Visualizador de camera (espaco do mundo): retangulo de captura de cada camera,
        // para o criador ver "para onde a camera aponta". So no editor, respeita a flag.
        if (gameState == GameState.EDITING && showCameraBounds) {
            AffineTransform camBoundsTransform = g2d.getTransform();
            renderCameraBounds(g2d, width, height, selected, originalTransform);
            g2d.setTransform(camBoundsTransform);
        }

        // Realces de multi-selecao (contorno tracejado laranja, sem alcas) para os
        // objetos secundarios definidos pelo editor. O primario continua com o
        // contorno+alcas+gizmo abaixo. Aditivo e puramente visual.
        if (gameState == GameState.EDITING && !editorHighlights.isEmpty()) {
            for (GameObject hl : editorHighlights) {
                if (hl == null || hl == selected || !hl.isVisible() || hl instanceof Camera) continue;
                AffineTransform hlTransform = g2d.getTransform();
                if (hl.getRotation() != 0) {
                    double cx = hl.getX() + hl.getWidth() / 2.0;
                    double cy = hl.getY() + hl.getHeight() / 2.0;
                    g2d.rotate(Math.toRadians(hl.getRotation()), cx, cy);
                }
                int hx = (int) hl.getX();
                int hy = (int) hl.getY();
                g2d.setColor(new Color(255, 160, 40));
                g2d.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                        java.awt.BasicStroke.JOIN_ROUND, 1.0f, new float[] { 4.0f, 4.0f }, 0.0f));
                g2d.drawRect(hx - 2, hy - 2, hl.getWidth() + 4, hl.getHeight() + 4);
                g2d.setTransform(hlTransform);
            }
        }

        // Contorno de selecao (espaco do mundo, com a transform da camera aplicada)
        if (selected != null && selected.isVisible() && !(selected instanceof Camera)) {
            AffineTransform selTransform = g2d.getTransform();
            if (selected.getRotation() != 0) {
                double cx = selected.getX() + selected.getWidth() / 2.0;
                double cy = selected.getY() + selected.getHeight() / 2.0;
                g2d.rotate(Math.toRadians(selected.getRotation()), cx, cy);
            }
            int sx = (int) selected.getX();
            int sy = (int) selected.getY();
            int sw = selected.getWidth();
            int sh = selected.getHeight();
            Color selColor = selected.getNameColor() != null
                    ? selected.getNameColor() : new Color(0, 150, 255);
            // Borda tracejada (espelha renderSelection do editor Swing)
            g2d.setColor(selColor);
            g2d.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND, 1.0f, new float[] { 5.0f, 5.0f }, 0.0f));
            g2d.drawRect(sx - 2, sy - 2, sw + 4, sh + 4);
            // Alcas de canto (preenchidas brancas com borda da cor de selecao)
            int hs = 6;
            g2d.setStroke(new java.awt.BasicStroke(1));
            g2d.setColor(Color.WHITE);
            g2d.fillRect(sx - hs / 2 - 2, sy - hs / 2 - 2, hs, hs);
            g2d.fillRect(sx + sw - hs / 2 + 2, sy - hs / 2 - 2, hs, hs);
            g2d.fillRect(sx - hs / 2 - 2, sy + sh - hs / 2 + 2, hs, hs);
            g2d.fillRect(sx + sw - hs / 2 + 2, sy + sh - hs / 2 + 2, hs, hs);
            g2d.setColor(selColor);
            g2d.drawRect(sx - hs / 2 - 2, sy - hs / 2 - 2, hs, hs);
            g2d.drawRect(sx + sw - hs / 2 + 2, sy - hs / 2 - 2, hs, hs);
            g2d.drawRect(sx - hs / 2 - 2, sy + sh - hs / 2 + 2, hs, hs);
            g2d.drawRect(sx + sw - hs / 2 + 2, sy + sh - hs / 2 + 2, hs, hs);
            g2d.setTransform(selTransform);
        }

        // Gizmo de collider (item 8b): contorno da hitbox + alcas de redimensionamento
        // para o objeto selecionado com um ColliderComponent, quando showColliders esta
        // ligado. Espaco de mundo. Desenhado antes do gizmo de transform para que as
        // setas de mover/escalar fiquem por cima no centro.
        if (selected != null && showColliders && gameState == GameState.EDITING
                && !(selected instanceof Camera)) {
            ColliderComponent cc = selected.getComponent(ColliderComponent.class);
            if (cc != null) {
                AffineTransform colGizmoTransform = g2d.getTransform();
                renderColliderGizmo(g2d, cc);
                g2d.setTransform(colGizmoTransform);
            }
        }

        // Gizmos da ferramenta atual (Mover/Rotacionar/Escalar) para o objeto
        // selecionado — espaco do mundo. renderGizmo ja faz o feedback de hover/drag
        // via hoveredGizmoMode/currentDragMode (atualizados pelos eventos de mouse
        // roteados ao engine pelo viewport FX). So no modo de edicao.
        if (gameState == GameState.EDITING && selectedObject != null
                && !(selectedObject instanceof Camera)) {
            AffineTransform gizmoTransform = g2d.getTransform();
            renderGizmo(g2d);
            g2d.setTransform(gizmoTransform);
        }

        g2d.setTransform(originalTransform);

        // ==================== UI RENDERING (espaco de tela) ====================
        // Canvas global de runtime + CanvasComponents das entidades — paridade
        // com o render() Swing (a UI nao aparecia no viewport JavaFX) e preview
        // de design das interfaces tambem no modo de edicao.
        if (uiCanvas != null && uiCanvas.isVisible()) {
            uiCanvas.updateScreenSize(width, height);
            uiCanvas.renderAll(g2d);
            g2d.setTransform(originalTransform);
        }
        for (CanvasComponent cc : getCanvasComponents()) {
            cc.render(g2d, width, height);
            g2d.setTransform(originalTransform);
        }
    }

    /**
     * Renderiza mensagens de alerta na tela do editor
     */
    private void renderAlerts(Graphics2D g2d) {
        if (editorReference == null) return;
        
        try {
            // Use reflection para obter os alertas do editor
            Class<?> editorClass = editorReference.getClass();
            java.lang.reflect.Method getAlertsMethod = editorClass.getMethod("getActiveAlerts");
            @SuppressWarnings("unchecked")
            java.util.List<Object> alerts = (java.util.List<Object>) getAlertsMethod.invoke(editorReference);
            
            if (alerts == null || alerts.isEmpty()) return;
            
            // Configurar font e cores
            Font alertFont = new Font("Courier New", Font.BOLD, 14);
            g2d.setFont(alertFont);
            
            int x = 15;
            int y = 35;
            int lineHeight = 22;
            
            // Renderizar cada alerta
            for (int i = 0; i < alerts.size() && i < 5; i++) {
                Object alertObj = alerts.get(i);
                
                // Obter a mensagem do alerta via reflection
                Class<?> alertClass = alertObj.getClass();
                java.lang.reflect.Field messageField = alertClass.getDeclaredField("message");
                messageField.setAccessible(true);
                String message = (String) messageField.get(alertObj);
                
                // Calcular opacidade baseado na idade do alerta
                java.lang.reflect.Field createdTimeField = alertClass.getDeclaredField("createdTime");
                createdTimeField.setAccessible(true);
                long createdTime = createdTimeField.getLong(alertObj);
                long age = System.currentTimeMillis() - createdTime;
                
                // Fade out no último segundo
                float opacity = 1.0f;
                if (age > 2000) { // Último 1 segundo de 3 segundos totais
                    opacity = 1.0f - ((age - 2000) / 1000.0f);
                }
                
                // Definir cor com transparência
                int alpha = (int)(255 * opacity);
                g2d.setColor(new java.awt.Color(0, 200, 100, alpha));
                
                // Desenhar caixa de fundo
                java.awt.FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(message);
                int textHeight = fm.getHeight();
                
                g2d.fillRect(x - 5, y - textHeight + 5, textWidth + 10, textHeight + 4);
                
                // Desenhar texto
                g2d.setColor(new java.awt.Color(255, 255, 255, alpha));
                g2d.drawString(message, x, y);
                
                y += lineHeight;
            }
        } catch (Exception e) {
            // Silenciosamente ignorar erros ao renderizar alertas
        }
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
        if (!showGrid) {
            return;
        }
        int crossSize = 20;
        
        // Save current stroke
        java.awt.Stroke oldStroke = g2d.getStroke();
        
        // Draw main crosshair at world origin (gray, dashed/semi-transparent)
        g2d.setColor(new Color(100, 100, 100, 100));
        float[] dashPattern = {4.0f, 4.0f};
        BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dashPattern, 0.0f);
        g2d.setStroke(dashedStroke);
        
        g2d.drawLine(-crossSize, 0, crossSize, 0);
        g2d.drawLine(0, -crossSize, 0, crossSize);
        
        // Restore stroke
        g2d.setStroke(oldStroke);
        
        // Origin label
        g2d.setColor(new Color(100, 100, 100, 150));
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        drawWorldText(g2d, "(0,0)", 6, 6);
    }
    
    /**
     * Draws the editor grid in world space.
     * The grid adapts to the camera zoom level for better visibility.
     */
    private void drawGrid(Graphics2D g2d) {
        Camera cam = getViewCamera();
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
        g2d.setColor(selectedObject.getNameColor() != null ? selectedObject.getNameColor() : new Color(0, 150, 255));
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
    /**
     * Renders the move gizmo (X and Y arrows)
     */
    private void renderMoveGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        
        // Get scaled gizmo dimensions
        int gizmoSize = getScaledGizmoSize();
        int arrowSize = getScaledGizmoArrowSize();

        double zoom = (getViewCamera() != null) ? getViewCamera().getZoom() : 1.0;
        int centerSize = (int)(10 / zoom);
        centerSize = Math.max(4, centerSize); // Minimum visible size

        // Determine colors based on drag or hover
        boolean xActive = (currentDragMode == GizmoDragMode.AXIS_X || hoveredGizmoMode == GizmoDragMode.AXIS_X);
        boolean yActive = (currentDragMode == GizmoDragMode.AXIS_Y || hoveredGizmoMode == GizmoDragMode.AXIS_Y);
        boolean cActive = (currentDragMode == GizmoDragMode.CENTER || hoveredGizmoMode == GizmoDragMode.CENTER);

        Color xColor = xActive ? new Color(255, 80, 80) : new Color(220, 40, 40);
        Color yColor = yActive ? new Color(80, 255, 80) : new Color(40, 200, 40);
        Color cColor = cActive ? new Color(255, 255, 150) : new Color(220, 220, 50);

        // --- 1. Draw Black Outline Background ---
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(Color.BLACK);
        
        // X outline
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        Polygon arrowXOutline = new Polygon();
        arrowXOutline.addPoint(centerX + gizmoSize + arrowSize + 2, centerY);
        arrowXOutline.addPoint(centerX + gizmoSize - 3, centerY - arrowSize / 2 - 2);
        arrowXOutline.addPoint(centerX + gizmoSize - 3, centerY + arrowSize / 2 + 2);
        g2d.fillPolygon(arrowXOutline);

        // Y outline
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        Polygon arrowYOutline = new Polygon();
        arrowYOutline.addPoint(centerX, centerY + gizmoSize + arrowSize + 2);
        arrowYOutline.addPoint(centerX - arrowSize / 2 - 2, centerY + gizmoSize - 3);
        arrowYOutline.addPoint(centerX + arrowSize / 2 + 2, centerY + gizmoSize - 3);
        g2d.fillPolygon(arrowYOutline);

        // Center square outline
        g2d.fillRect(centerX - centerSize / 2 - 2, centerY - centerSize / 2 - 2, centerSize + 4, centerSize + 4);

        // --- 2. Draw Colored Foreground ---
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // X Foreground
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        Polygon arrowX = new Polygon();
        arrowX.addPoint(centerX + gizmoSize + arrowSize, centerY);
        arrowX.addPoint(centerX + gizmoSize - 2, centerY - arrowSize / 2);
        arrowX.addPoint(centerX + gizmoSize - 2, centerY + arrowSize / 2);
        g2d.fillPolygon(arrowX);

        // Y Foreground
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        Polygon arrowY = new Polygon();
        arrowY.addPoint(centerX, centerY + gizmoSize + arrowSize);
        arrowY.addPoint(centerX - arrowSize / 2, centerY + gizmoSize - 2);
        arrowY.addPoint(centerX + arrowSize / 2, centerY + gizmoSize - 2);
        g2d.fillPolygon(arrowY);

        // Center Foreground
        g2d.setColor(cColor);
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // Labels
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "X", centerX + gizmoSize + arrowSize + 4, centerY - 4);
        drawWorldText(g2d, "Y", centerX - 4, centerY + gizmoSize + arrowSize + 4);
    }

    /**
     * Renders the rotate gizmo (circle)
     */
    private void renderRotateGizmo(Graphics2D g2d) {
        int centerX = (int) selectedObject.getX() + selectedObject.getWidth() / 2;
        int centerY = (int) selectedObject.getY() + selectedObject.getHeight() / 2;
        
        // Get scaled radius
        int rotateRadius = getScaledRotateGizmoRadius();

        boolean rActive = (currentDragMode == GizmoDragMode.ROTATE || hoveredGizmoMode == GizmoDragMode.ROTATE);
        Color circleColor = rActive ? new Color(100, 200, 255) : new Color(50, 150, 220);

        // 1. Black outline circle
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - rotateRadius, centerY - rotateRadius, rotateRadius * 2, rotateRadius * 2);

        // 2. Colored circle
        g2d.setColor(circleColor);
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - rotateRadius, centerY - rotateRadius, rotateRadius * 2, rotateRadius * 2);

        // Rotation indicator line (shows current rotation)
        double radians = Math.toRadians(selectedObject.getRotation());
        int indicatorX = centerX + (int) (Math.cos(radians) * rotateRadius);
        int indicatorY = centerY + (int) (Math.sin(radians) * rotateRadius);
        
        // Indicator outline
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(4));
        g2d.drawLine(centerX, centerY, indicatorX, indicatorY);
        // Indicator line
        g2d.setColor(new Color(255, 150, 50));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(centerX, centerY, indicatorX, indicatorY);

        // Center point
        int centerPointSize = (int)(5 / (getViewCamera() != null ? getViewCamera().getZoom() : 1.0));
        centerPointSize = Math.max(3, centerPointSize);
        g2d.setColor(Color.BLACK);
        g2d.fillOval(centerX - centerPointSize - 1, centerY - centerPointSize - 1, (centerPointSize + 1) * 2, (centerPointSize + 1) * 2);
        g2d.setColor(circleColor);
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
        double zoom = (getViewCamera() != null) ? getViewCamera().getZoom() : 1.0;
        int squareSize = (int)(20 / zoom);
        squareSize = Math.max(4, squareSize);
        int centerSize = (int)(12 / zoom);
        centerSize = Math.max(6, centerSize);

        boolean xActive = (currentDragMode == GizmoDragMode.SCALE_X || hoveredGizmoMode == GizmoDragMode.SCALE_X);
        boolean yActive = (currentDragMode == GizmoDragMode.SCALE_Y || hoveredGizmoMode == GizmoDragMode.SCALE_Y);
        boolean uActive = (currentDragMode == GizmoDragMode.SCALE_UNIFORM || hoveredGizmoMode == GizmoDragMode.SCALE_UNIFORM);

        Color xColor = xActive ? new Color(255, 80, 80) : new Color(220, 40, 40);
        Color yColor = yActive ? new Color(80, 255, 80) : new Color(40, 200, 40);
        Color uColor = uActive ? new Color(255, 255, 150) : new Color(255, 220, 50);

        // --- 1. Draw Black Outline Background ---
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(Color.BLACK);
        
        // X outline
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        g2d.fillRect(centerX + gizmoSize - squareSize / 2 - 2, centerY - squareSize / 2 - 2, squareSize + 4, squareSize + 4);
        
        // Y outline
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        g2d.fillRect(centerX - squareSize / 2 - 2, centerY + gizmoSize - squareSize / 2 - 2, squareSize + 4, squareSize + 4);

        // Center outline
        g2d.fillRect(centerX - centerSize / 2 - 2, centerY - centerSize / 2 - 2, centerSize + 4, centerSize + 4);

        // --- 2. Draw Colored Foreground ---
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // X Foreground
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        g2d.fillRect(centerX + gizmoSize - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX + gizmoSize - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);

        // Y Foreground
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        g2d.fillRect(centerX - squareSize / 2, centerY + gizmoSize - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX - squareSize / 2, centerY + gizmoSize - squareSize / 2, squareSize, squareSize);

        // Center Foreground
        g2d.setColor(uColor);
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // Labels
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "X", centerX + gizmoSize + squareSize + 2, centerY - 4);
        drawWorldText(g2d, "Y", centerX - 4, centerY + gizmoSize + squareSize + 4);

        // Size label
        g2d.setFont(new Font("Dialog", Font.PLAIN, 11));
        drawWorldText(g2d, objW + " x " + objH, centerX + gizmoSize + squareSize + 2, centerY - 20);
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
        
        // Expurgar conexões de eventos da entidade na cena atual
        if (this.sceneSignalDispatcher != null) {
            this.sceneSignalDispatcher.purgeEntityConnections(entity.getId());
        }
        // Expurgar conexões globais dos scripts atachados
        if (this.gameSignalBus != null) {
            for (Component comp : entity.getComponents()) {
                if (comp instanceof IgnisScript) {
                    this.gameSignalBus.disconnectAllForInstance(comp);
                }
            }
        }
    }

    public void clearEntities() {
        this.entities.clear();
        this.runtimeObjects.clear();
        this.sceneSignalDispatcher = new SceneSignalDispatcher(); // Reset scene connections
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
        
        // Nenhum ajuste de indice: newIndex indica a posicao FINAL desejada na lista
        // resultante. O clamp abaixo garante que o valor fique no intervalo valido
        // apos a remocao. O ajuste anterior (newIndex--) estava errado e fazia
        // moveEntityUp ser um no-op.
        
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
     * Camera pela qual a VIEW atual e renderizada. Durante o Play (ou pausa) e a
     * camera ativa do jogo; no modo de edicao e a camera livre do editor — a menos
     * que o preview da camera esteja ligado ({@link #setCameraPreview}), quando a
     * Scene View passa a mostrar exatamente o que a camera ativa do jogo ve.
     * Todo codigo de render/pick/navegacao do viewport deve usar esta camera;
     * {@link #getActiveCamera()} continua sendo a semantica de GAMEPLAY.
     */
    public Camera getViewCamera() {
        if (gameState != GameState.EDITING || cameraPreview || editorCamera == null) {
            return getActiveCamera();
        }
        return editorCamera;
    }

    /** Camera livre da Scene View do editor (nunca serializada na cena). */
    public Camera getEditorCamera() {
        return editorCamera;
    }

    /**
     * Liga/desliga o preview da camera do jogo na Scene View. Ligado, a view fica
     * presa ao enquadramento da camera ativa da cena; desligado (padrao), a view
     * volta para a camera livre do editor, sem mexer na camera do jogo.
     */
    public void setCameraPreview(boolean enabled) {
        this.cameraPreview = enabled;
    }

    /** Se a Scene View esta mostrando a visao da camera ativa do jogo. */
    public boolean isCameraPreview() {
        return cameraPreview;
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
         * Sets whether objects should snap to grid when dragging.
         */
        public void setSnapToGrid(boolean snap) {
            this.snapToGrid = snap;
        }

        /**
         * Returns whether objects snap to grid when dragging.
         */
        public boolean isSnapToGrid() {
            return snapToGrid;
                    }

                /**
                 * Snaps a coordinate to the nearest grid line.
                 * @param coord Coordinate in world units
                 * @return Snapped coordinate
                 */
                public double snapToGrid(double coord) {
                    if (!snapToGrid || gridSize <= 0) return coord;
                    return Math.round(coord / gridSize) * gridSize;
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
        Camera cam = getViewCamera();
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
        Camera cam = getViewCamera();
        if (cam != null && editorCameraMode) {
            return cam.screenToWorld(screenX, screenY);
        }
        // No camera transform - return as-is
        return new Point2D.Double(screenX, screenY);
    }

    @Override
    public void setCursor(java.awt.Cursor cursor) {
        if (isDisplayable()) {
            super.setCursor(cursor);
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double amountOfTicks = 60.0;
        double ns = 1000000000 / amountOfTicks;
        double delta = 0;

        while (isRunning) {
            try {
                long now = System.nanoTime();
                delta += (now - lastTime) / ns;
                lastTime = now;

                if (delta >= 1) {
                    tick();
                    render();
                    delta--;
                } else {
                    // Yield between frames instead of busy-spinning a full core
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Throwable t) {
                IgnisLogger.error("Erro na thread do loop do jogo: " + t.getMessage());
                // Avoid fast spinning if there's a persistent error
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
