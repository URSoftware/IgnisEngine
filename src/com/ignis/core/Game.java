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

public class Game extends Canvas {

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
        int gridSize = 32; // Grid cell size in world units
        Color gridColor = new Color(255, 255, 255, 30); // Semi-transparent white
        boolean snapToGrid = true; // Snap objects to grid when dragging

    // Loop do jogo (thread propria, tick/render pacing) — Fase F.
    private final GameLoop loop = new GameLoop(this);
    
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

    // ==================== ILUMINACAO 2D (Fase D 3.11) ====================
    // Luz ambiente da cena ativa (alpha = intensidade da escuridao). Null = sem
    // iluminacao (passe de luz nao roda, custo zero). Espelha Scene.ambientLight.
    private Color ambientLight = null;

    public Color getAmbientLight() { return ambientLight; }
    public void setAmbientLight(Color c) { this.ambientLight = c; }

    // Alertas visuais transitorios (overlay no viewport do editor - Fase F, dívida
    // 2.4: antes localizava o editor via reflection sobre um Object generico
    // (setEditor/alert(String) por nome), workaround do antigo editor Swing. O
    // editor JavaFX nunca chamava setEditor(), entao a fila nunca era populada e
    // renderAlerts() sempre no-opava silenciosamente. A fila agora mora aqui: nao
    // ha razao para o alerta depender de uma referencia ao editor.
    private final java.util.List<EditorAlert> activeAlerts = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Alerta transitorio exibido como overlay no viewport (mensagem + timestamp). */
    static final class EditorAlert {
        final String message;
        final long createdTime;

        EditorAlert(String message, long createdTime) {
            this.message = message;
            this.createdTime = createdTime;
        }
    }

    /** Alertas ativos para o overlay do editor, mais recente por ultimo. */
    java.util.List<EditorAlert> getActiveAlerts() {
        return activeAlerts;
    }

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
        WORLD_PAINT, // Pinta/apaga barreiras na grade do World (mundo da cena)
        TILE_PAINT // Pinta/apaga tiles na grade de um TilemapObject (Fase C)
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
    int getScaledGizmoSize() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_SIZE / zoom);
    }

    int getScaledGizmoArrowSize() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_ARROW_SIZE / zoom);
    }

    int getScaledGizmoHitArea() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_GIZMO_HIT_AREA / zoom);
    }

    int getScaledRotateGizmoRadius() {
        Camera cam = getViewCamera();
        double zoom = (cam != null) ? cam.getZoom() : 1.0;
        return (int)(BASE_ROTATE_GIZMO_RADIUS / zoom);
    }

    // Gizmo drag states
    enum GizmoDragMode {
        NONE, AXIS_X, AXIS_Y, CENTER, ROTATE, SCALE_X, SCALE_Y, SCALE_UNIFORM
    }

    GizmoDragMode currentDragMode = GizmoDragMode.NONE;
    GizmoDragMode hoveredGizmoMode = GizmoDragMode.NONE;

    // Overlays de edicao (gizmos, collider, frustum das cameras) — Fase F.
    private final EditorGizmoRenderer gizmos = new EditorGizmoRenderer(this);
    // Desenhadores-folha dos overlays da cena (grid, World, luz, alertas) — Fase F.
    private final SceneOverlayRenderer overlays = new SceneOverlayRenderer(this);
    // Input do editor (mouse -> selecao/arrasto/pintura/panning) -- Fase F.
    private final EditorInputController input = new EditorInputController(this);

    // ---- Gizmo de collider (item 8b) ----
    // Alcas de redimensionamento da hitbox do ColliderComponent do objeto selecionado.
    // Ativo somente em edicao, com 'showColliders' ligado e um ColliderComponent anexado.
    // Indices das 8 alcas: 0=NW 1=N 2=NE 3=E 4=SE 5=S 6=SW 7=W.

    // ---- Pintura de tiles (ferramenta TILE_PAINT, Fase C) ----
    private TilemapObject activeTilemap = null;   // alvo da pintura de tiles
    int activeTileIndex = 0;              // indice do tile a pintar
    int activeTileLayer = 0;              // camada alvo
    boolean tilePaintErase = false;       // stroke atual apaga (Ctrl)
    Runnable tilePaintDirtyHook = null;   // marca o projeto sujo ao pintar

    public void setActiveTilemap(TilemapObject tm) { this.activeTilemap = tm; }
    public TilemapObject getActiveTilemap() { return activeTilemap; }
    public void setActiveTileIndex(int idx) { this.activeTileIndex = idx; }
    public void setActiveTileLayer(int layer) { this.activeTileLayer = Math.max(0, layer); }
    public void setTilePaintErase(boolean erase) { this.tilePaintErase = erase; }
    public void setTilePaintDirtyHook(Runnable hook) { this.tilePaintDirtyHook = hook; }

    // ---- Pintura de barreiras do World (ferramenta WORLD_PAINT) ----
    boolean worldPaintErase = false; // stroke atual apaga (Ctrl) em vez de bloquear
    

    // Interface to notify selection changes
    public interface SelectionListener {
        void onSelectionChanged(GameObject selected);
    }
    
    // Interface to notify transform changes (for undo system)
    public interface TransformListener {
        void onTransformStart(GameObject obj, double x, double y, double rotation, int width, int height);
        void onTransformEnd(GameObject obj);
    }
    
    TransformListener transformListener;

    public void setTransformListener(TransformListener listener) {
        this.transformListener = listener;
    }

    // Notifica inicio/fim de um arraste de redimensionamento de collider (item 8b),
    // para o editor registrar undo/redo do ColliderComponent.
    public interface ColliderEditListener {
        void onColliderEditStart(GameObject owner, ColliderComponent collider);
        void onColliderEditEnd(GameObject owner, ColliderComponent collider);
    }

    ColliderEditListener colliderEditListener;

    public void setColliderEditListener(ColliderEditListener listener) {
        this.colliderEditListener = listener;
    }

    // Notifica inicio/fim de um traco de pintura de barreiras, para o editor registrar
    // undo/redo do conjunto de celulas bloqueadas do World.
    public interface WorldPaintListener {
        void onPaintStrokeStart();
        void onPaintStrokeEnd();
    }

    WorldPaintListener worldPaintListener;

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
        public boolean visible;

        public EntitySnapshot(double x, double y, int width, int height, boolean visible) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.visible = visible;
        }
    }

    public Game() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setMinimumSize(new Dimension(WIDTH, HEIGHT));
        input.install();
        
        // Initialize Input system
        Input.init(this);
        
        // Initialize camera and viewport system
        initializeCameraSystem();
        
        // Initialize collision system
        collisionManager = new IgnisSampleCollisions.CollisionManager();

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

    // Quantos alertas o overlay mantem; SceneOverlayRenderer so desenha os 5 mais
    // recentes, mas a fila cresceria sem limite numa sessao longa sem esse teto.
    private static final int MAX_ACTIVE_ALERTS = 10;

    /**
     * Exibe uma mensagem de alerta como overlay transitorio no viewport do editor
     * (~3s, com fade) e sempre a loga (IgnisLogger.info), inclusive headless/build.
     * @param message A mensagem a ser exibida
     */
    public void alert(String message) {
        activeAlerts.add(new EditorAlert(message, System.currentTimeMillis()));
        while (activeAlerts.size() > MAX_ACTIVE_ALERTS) {
            activeAlerts.remove(0);
        }
        IgnisLogger.info("[ALERT] " + message);
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
            
            // Em PLAYING este metodo e caminho quente (spawners chamam varias vezes
            // por segundo); logar cada clone inunda o console e o stderr. O log fica
            // restrito a instanciacoes feitas em modo de edicao.
            if (gameState != GameState.PLAYING) {
                IgnisLogger.info("Prefab '" + prefabName + "' instanciada como '" + instance.getName() + "' em (" + x + ", " + y + ")");
            }
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

    // Roteia clique/movimento de mouse para a UI: CanvasComponents do topo para o
    // fundo, depois o canvas global de runtime. Fonte unica usada tanto pelo mouse
    // real (EditorInputController) quanto pela injecao por coordenada do MCP.

    // Nucleo do roteamento (sem checar estado): percorre a UI e entrega o clique.
    private boolean dispatchUiClick(MouseEvent e, boolean pressed) {
        java.util.List<CanvasComponent> ccs = getCanvasComponents();
        for (int i = ccs.size() - 1; i >= 0; i--) {
            if (ccs.get(i).processMouseClick(e, pressed)) return true;
        }
        return uiCanvas != null && uiCanvas.isVisible() && uiCanvas.processMouseClick(e, pressed);
    }

    private boolean dispatchUiMove(MouseEvent e) {
        java.util.List<CanvasComponent> ccs = getCanvasComponents();
        for (int i = ccs.size() - 1; i >= 0; i--) {
            if (ccs.get(i).processMouseMove(e)) return true;
        }
        return uiCanvas != null && uiCanvas.isVisible() && uiCanvas.processMouseMove(e);
    }

    /** Roteia um clique de UI real (so no Play). @return true se a UI consumiu. */
    public boolean routeUiMouseClick(MouseEvent e, boolean pressed) {
        return gameState == GameState.PLAYING && dispatchUiClick(e, pressed);
    }

    /** Roteia um movimento de mouse real para a UI (hover), so no Play. */
    public void routeUiMouseMove(MouseEvent e) {
        if (gameState == GameState.PLAYING) dispatchUiMove(e);
    }

    /**
     * Clica na UI em (x,y) de tela como se o jogador tivesse clicado: move o mouse,
     * gera press+release sinteticos e roteia para os CanvasComponents/canvas global.
     * Funciona em Play OU pausado (base do QA determinista de menus/dialogo). O onClick
     * dos widgets dispara no release. @return true se algum widget consumiu o clique.
     */
    public boolean injectUiClickAt(int x, int y, int button) {
        if (gameState == GameState.EDITING) return false;
        Input.injectMouseMove(x, y);
        long t = System.currentTimeMillis();
        dispatchUiMove(new MouseEvent(this, MouseEvent.MOUSE_MOVED, t, 0, x, y, 0, false, MouseEvent.NOBUTTON));
        boolean pressConsumed = dispatchUiClick(
                new MouseEvent(this, MouseEvent.MOUSE_PRESSED, t, 0, x, y, 1, false, button), true);
        boolean releaseConsumed = dispatchUiClick(
                new MouseEvent(this, MouseEvent.MOUSE_RELEASED, t, 0, x, y, 1, false, button), false);
        return pressConsumed || releaseConsumed;
    }

    /** Move o cursor virtual para (x,y) de tela e roteia hover para a UI (Play/pausado). */
    public void moveMouseTo(int x, int y) {
        Input.injectMouseMove(x, y);
        if (gameState != GameState.EDITING) {
            dispatchUiMove(new MouseEvent(this, MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON));
        }
    }

    // ==================== GIZMO DE COLLIDER (item 8b) ====================

    /** Fator mundo-por-pixel da camera de edicao (1.0 sem transform de camera). */
    double editorWorldPerPixel() {
        Camera cam = getViewCamera();
        double zoom = (cam != null && editorCameraMode) ? cam.getZoom() : 1.0;
        return (zoom > 0) ? 1.0 / zoom : 1.0;
    }

    /** Ponto em mundo da alca {@code handle} (0..7) para o bounds {@code [minX,minY,w,h]}. */
    double[] colliderHandlePoint(double[] b, int handle) {
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

    public void cancelDrag() { input.cancelDrag(); }

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
        loop.start();
    }

    public synchronized void stop() {
        loop.stop();
    }

    // ==================== GAME STATE CONTROL ====================

    /**
     * Starts world simulation (Play)
     * Saves initial positions of all objects
     */
    public void playWorld() {
        playWorld(true);
    }

    /**
     * Starts Play after an editor has already compiled scripts and replaced every
     * script instance with the resulting classloader generation.
     *
     * <p>Compiling again here would immediately retire the classloader that owns
     * those live instances. Lazy dependency resolution can then fail during the
     * first tick even though the project jar is valid. Standalone callers should
     * keep using {@link #playWorld()}.</p>
     */
    public void playWorldWithPreparedScripts() {
        playWorld(false);
    }

    private void playWorld(boolean compileScripts) {
        if (gameState == GameState.EDITING) {
            // Save snapshot of all objects
            saveInitialSnapshots();

            // Compile and initialize scripts
            if (scriptManager != null) {
                if (compileScripts) {
                    scriptManager.compileAllScripts();
                }
                initializeScripts();
            }
        } else if (gameState == GameState.PAUSED) {
            // Resuming from pause - resume audio
            IgnisSoundEngine.getInstance().resumeAllAudio();
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
            
            // Pause BGM and every active SFX voice with the world.
            IgnisSoundEngine.getInstance().pauseAllAudio();
        }
    }

    /**
     * Resumes world simulation after pause
     */
    public void resumeWorld() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
            
            // Resume audio
            IgnisSoundEngine.getInstance().resumeAllAudio();
        }
    }

    /**
     * Stops simulation and restores objects to initial positions
     */
    public void stopWorld() {
        gameState = GameState.EDITING;
        
        // Stop all audio
        IgnisSoundEngine.getInstance().stopAllAudio();
        
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
                    entity.getHeight(),
                    entity.isVisible()));
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
                // Restaura a visibilidade original: scripts podem esconder objetos
                // durante o Play (cutscenes/diretores). Sem isto o estado invisivel
                // vazava para o editor e o auto-save persistia a cena inteira
                // invisivel, deixando o viewport em branco ao reabrir o projeto.
                entity.setVisible(snapshot.visible);
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

            // Filhos seguem os pais: recomputa o mundo dos objetos parenteados a
            // partir do pai + offset local, em ordem pai-antes-filho. Feito ANTES
            // da colisao para que os filhos ja estejam na posicao final do frame.
            syncHierarchy();

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

    /**
     * Avanca a simulacao UM passo de forma determinista, para testes por agente
     * (ferramenta MCP {@code advance_frames}). Forca um tick de PLAYING sob o monitor
     * do {@code Game} — portanto mutuamente exclusivo com o loop do jogo e com o
     * render, sem frame "rasgado" — mesmo que o mundo esteja PAUSED, restaurando o
     * estado ao fim para nao "vazar" o Play. Em EDITING nao ha simulacao: o tick so
     * atualiza o Input e a UI.
     *
     * <p>Fluxo determinista recomendado: {@code play_game} &rarr; {@code pause_game}
     * (o loop livre para de avancar) &rarr; {@code inject_input} &rarr;
     * {@code advance_frames} (cada passo forcado aqui) &rarr; ler estado/capturar.</p>
     */
    public synchronized void stepSimulationOnce() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
            try {
                tick();
            } finally {
                gameState = GameState.PAUSED;
            }
        } else {
            tick();
        }
    }

    // ---- Interpolacao de render (Fase A do plano do motor grafico) ----
    // Simulacao fixa em 60 Hz; o editor JavaFX renderiza na taxa do monitor
    // (75/120/144 Hz). Sem interpolacao, o movimento continuo apresenta judder.
    private volatile long lastTickNanos = 0L;
    /** Passos de simulacao por segundo (fixo — nao confundir com o FPS do render). */
    public static final double TICKS_PER_SECOND = 60.0;
    private static final double NS_PER_TICK = 1_000_000_000.0 / TICKS_PER_SECOND;

    // ---- Ritmo do render (Fase E, item 3.13) ----
    // O render e desacoplado da simulacao: roda ate 'fpsCap' quadros por segundo,
    // interpolando entre ticks via getRenderAlpha(). 0 = sem limite (usa a CPU
    // livremente). Default 60 = comportamento historico.
    private volatile int fpsCap = 60;

    /** Limite de quadros por segundo do render (0 = sem limite). */
    public int getFpsCap() {
        return fpsCap;
    }

    /** Define o limite de FPS do render; 0 remove o limite. A simulacao segue a 60 Hz. */
    public void setFpsCap(int fps) {
        this.fpsCap = Math.max(0, fps);
    }
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

    /**
     * Recomputa a posicao/rotacao de mundo de todos os objetos parenteados a partir
     * dos pais (hierarquia pai-filho, Fase C). Processa em ordem crescente de
     * profundidade, garantindo que cada pai ja esteja atualizado antes dos filhos
     * (avo -&gt; pai -&gt; filho). Objetos-raiz (sem pai) sao no-op. Chamado a cada
     * tick durante o Play; pode ser chamado pelo editor apos mover um pai.
     */
    /**
     * Aplica a hierarquia apos um movimento feito NO EDITOR (gizmo ou campos do
     * Inspector), fora do Play. O objeto movido fica onde foi solto: se tem pai,
     * seu offset local e RECAPTURADO a partir da posicao de mundo atual (assim ele
     * nao "salta" de volta); em seguida, os descendentes acompanham via
     * {@link #syncHierarchy()}. No Play o sync ja roda no tick — este metodo cobre
     * o modo de edicao. No-op se o objeto nao participa de nenhuma hierarquia.
     */
    public synchronized void syncHierarchyAfterEditorMove(GameObject moved) {
        if (moved == null) return;
        if (moved.getParent() != null) {
            moved.setParent(moved.getParent()); // recomputa o offset a partir do mundo atual
        }
        syncHierarchy();
    }

    /**
     * Avanca a simulacao dos {@link ParticleEmitter} no MODO DE EDICAO, para que o
     * criador veja o efeito enquanto ajusta os parametros (no Play isso ja roda no
     * tick a 60 Hz). Chamado pelo AnimationTimer do editor com o delta real da frame.
     * No-op fora do EDITING ou sem emissores. So mexe em estado de runtime (pool),
     * nunca no que e serializado.
     */
    public synchronized void previewEditorParticles(double dt) {
        if (gameState != GameState.EDITING) return;
        for (int i = 0; i < entities.size(); i++) {
            GameObject e = entities.get(i);
            if (e instanceof ParticleEmitter) {
                ((ParticleEmitter) e).step(dt);
            }
        }
    }

    /**
     * Preview de animações no editor: avança os {@link AnimationComponent} das
     * entidades no modo de edição para que seus sprites apareçam (e animem) no
     * viewport sem depender do primeiro Play. No-op fora do modo de edição.
     * Espelha {@link #previewEditorParticles(double)}.
     */
    public synchronized void previewEditorAnimations(double dt) {
        if (gameState != GameState.EDITING) return;
        for (int i = 0; i < entities.size(); i++) {
            AnimationComponent ac = entities.get(i).getComponent(AnimationComponent.class);
            if (ac != null) {
                ac.editorPreview(dt);
            }
        }
    }

    public synchronized void syncHierarchy() {
        boolean anyParented = false;
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i).getParent() != null) {
                anyParented = true;
                break;
            }
        }
        if (!anyParented) {
            return; // caminho rapido: nenhuma hierarquia na cena
        }
        java.util.List<GameObject> ordered = new java.util.ArrayList<>(entities);
        ordered.sort(java.util.Comparator.comparingInt(GameObject::hierarchyDepth));
        for (GameObject go : ordered) {
            go.syncToParent();
        }
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
    private boolean isCulled(Camera cam, GameObject e) {
        if (cam == null || !e.isCullable()) return false;
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

    /**
     * Renderiza o jogo no Canvas AWT (BufferStrategy) — pipeline do player
     * standalone/exportado. O DESENHO DA CENA e delegado a {@link #renderWorldTo},
     * fonte unica do pipeline grafico (Fase E, item 3.14). Antes, este metodo
     * duplicava toda a logica de cena (camera, culling, entidades, luz, UI), o que
     * obrigava a manter dois caminhos em sincronia a cada recurso novo — o passe de
     * iluminacao da Fase D, por exemplo, precisou ser inserido nos dois. Aqui ficam
     * apenas as partes especificas deste pipeline: BufferStrategy, ajuste de
     * viewport e os alertas por cima.
     *
     * <p>No editor JavaFX este metodo retorna cedo ({@code isDisplayable()} e false —
     * o Game nao vive numa janela AWT la); quem desenha e o AnimationTimer chamando
     * {@code renderWorldTo} diretamente. Ou seja, este caminho so pinta de fato no
     * jogo exportado, onde {@code gameState} e sempre PLAYING.</p>
     */
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
        try {
            // Mantem o viewport coerente com a janela (resize do player standalone).
            if (viewport != null
                    && (viewport.getWidth() != getWidth() || viewport.getHeight() != getHeight())) {
                viewport.resize(getWidth(), getHeight());
            }

            // Cena completa (fundo, camera, entidades, iluminacao, UI): fonte unica.
            renderWorldTo(g2d, getWidth(), getHeight(), selectedObject);

            // Alertas por cima de tudo (especifico deste pipeline).
            overlays.renderAlerts(g2d);
        } finally {
            g.dispose();
            bs.show();
        }
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
            overlays.drawGrid(g2d);
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
            overlays.drawWorldOverlay(g2d);
            g2d.setTransform(worldTransform);
        }

        // Grade de pintura de barreiras: mostra as celulas do World enquanto a
        // ferramenta WORLD_PAINT esta ativa (mesmo em mundo ainda vazio).
        if (gameState == GameState.EDITING && currentTool == ToolType.WORLD_PAINT
                && world != null && shouldApplyCameraTransform) {
            AffineTransform paintTransform = g2d.getTransform();
            overlays.drawWorldPaintGrid(g2d, width, height);
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
            gizmos.renderCameraBounds(g2d, width, height, selected, originalTransform);
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
                gizmos.renderColliderGizmo(g2d, cc);
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
            gizmos.renderGizmo(g2d);
            g2d.setTransform(gizmoTransform);
        }

        g2d.setTransform(originalTransform);

        // ==================== ILUMINACAO 2D (Fase D 3.11) ====================
        // Antes da UI (que fica imune a luz) e depois de toda a cena/overlays.
        overlays.renderLightingPass(g2d, width, height, activeCamera, shouldApplyCameraTransform);
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
        // Scene changes retire every script at once. Detach them before dropping
        // the entity list so global receivers cannot survive into the next scene.
        for (GameObject entity : new java.util.ArrayList<>(this.entities)) {
            for (Component component : new java.util.ArrayList<>(entity.getComponents())) {
                if (component instanceof IgnisScript script) {
                    script.onDetach();
                }
            }
        }
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

}
