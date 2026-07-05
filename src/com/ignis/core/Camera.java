package com.ignis.core;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import org.json.JSONObject;

/**
 * Camera - The "moving origin" of the visualization in the Ignis Engine.
 * 
 * The Camera uses a Transform component to store its world position, rotation,
 * and zoom level. It generates a View Matrix that transforms world coordinates
 * to screen coordinates.
 * 
 * Key concepts:
 * - Camera at (0,0) shows the world origin at the center of the screen
 * - Moving the camera moves the world in the OPPOSITE direction
 * - The View Matrix handles translation, rotation, and zoom in a single operation
 * - Supports WorldToScreen and ScreenToWorld coordinate conversions
 * 
 * Coordinate System:
 * - World: The game world coordinates where objects live
 * - Screen: Pixel coordinates on the actual display
 */
public class Camera extends GameObject {

    // The camera's transform (position in world, rotation, zoom)
    private Transform cameraTransform;

    // Viewport associated with this camera
    private Viewport viewport;

    // Zoom level (1.0 = normal, 2.0 = zoomed in 2x, 0.5 = zoomed out)
    private double zoom = 1.0;

    // Minimum and maximum zoom levels
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;

    // The cached view matrix and its inverse
    private AffineTransform viewMatrix;
    private AffineTransform inverseViewMatrix;
    private boolean matrixDirty = true;

    // Whether this camera is the active camera for rendering
    private boolean isActive = true;

    // Name for identification
    private String cameraName = "MainCamera";

    // ---- Comportamentos nativos da camera (Fase B do plano do motor grafico) ----
    // Antes so existiam como wrappers protected em IgnisScript; agora vivem na
    // Camera e sao avancados por update(dt), chamado pelo Game a cada tick de Play.
    private transient GameObject followTarget = null;   // alvo a seguir (ou null)
    private double followSmoothing = 0.15;               // 0..1 por tick (1 = instantaneo)
    private double baseX, baseY;                         // posicao "logica" sem o shake
    private boolean hasBounds = false;                   // limites do mundo ativos?
    private double boundMinX, boundMinY, boundMaxX, boundMaxY;
    private double shakeIntensity = 0.0;                 // amplitude do tremor (px)
    private double shakeDuration = 0.0;                  // duracao total (s)
    private double shakeElapsed = Double.MAX_VALUE;      // tempo decorrido (>= duration = parado)
    private final transient java.util.Random shakeRng = new java.util.Random();

    /**
     * Creates a camera at the origin with default viewport.
     */
    public Camera() {
        super();
        this.cameraTransform = new Transform();
        this.viewport = null; // Will be set when attached to Game
        this.viewMatrix = new AffineTransform();
        this.inverseViewMatrix = new AffineTransform();
        this.name = "Camera";
        this.width = 64;
        this.height = 64;
        this.visible = true;
        markDirty();
    }

    /**
     * Creates a camera with the specified Game reference.
     */
    public Camera(String name, Game game, double x, double y) {
        super(name, game, x, y, 64, 64);
        this.cameraTransform = new Transform(x, y);
        this.cameraName = name;
        this.viewport = null;
        this.viewMatrix = new AffineTransform();
        this.inverseViewMatrix = new AffineTransform();
        markDirty();
    }

    /**
     * Sets the viewport for this camera.
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
        markDirty();
    }

    /**
     * Gets the viewport associated with this camera.
     */
    public Viewport getViewport() {
        return viewport;
    }

    // ==================== TRANSFORM ACCESS ====================

    /**
     * Gets the camera's transform component.
     */
    public Transform getCameraTransform() {
        return cameraTransform;
    }

    @Override
    public double getX() {
        return cameraTransform.getX();
    }

    @Override
    public void setX(double x) {
        cameraTransform.setX(x);
        this.x = x;
        markDirty();
    }

    @Override
    public double getY() {
        return cameraTransform.getY();
    }

    @Override
    public void setY(double y) {
        cameraTransform.setY(y);
        this.y = y;
        markDirty();
    }

    /**
     * Sets the camera position in world coordinates.
     */
    public void setPosition(double x, double y) {
        cameraTransform.setPosition(x, y);
        this.x = x;
        this.y = y;
        markDirty();
    }

    /**
     * Moves the camera by the given delta values.
     */
    public void translate(double dx, double dy) {
        cameraTransform.translate(dx, dy);
        this.x = cameraTransform.getX();
        this.y = cameraTransform.getY();
        markDirty();
    }

    @Override
    public double getRotation() {
        return cameraTransform.getRotation();
    }

    @Override
    public void setRotation(double rotation) {
        cameraTransform.setRotation(rotation);
        this.rotation = rotation;
        markDirty();
    }

    // ==================== ZOOM ====================

    /**
     * Gets the current zoom level.
     * 1.0 = normal, > 1 = zoomed in, < 1 = zoomed out
     */
    public double getZoom() {
        return zoom;
    }

    /**
     * Sets the zoom level, clamped to valid range.
     */
    public void setZoom(double zoom) {
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        markDirty();
    }

    /**
     * Zooms in by the given factor (multiplies current zoom).
     */
    public void zoomIn(double factor) {
        setZoom(zoom * factor);
    }

    /**
     * Zooms out by the given factor (divides current zoom).
     */
    public void zoomOut(double factor) {
        setZoom(zoom / factor);
    }

    /**
     * Zooms towards a specific world point (keeps that point stationary on screen).
     */
    public void zoomToPoint(double worldX, double worldY, double newZoom) {
        // Calculate the screen position of the target point before zoom
        Point2D.Double before = worldToScreen(worldX, worldY);

        // Apply new zoom
        setZoom(newZoom);

        // Calculate the screen position of the target point after zoom
        Point2D.Double after = worldToScreen(worldX, worldY);

        // Adjust camera position to keep the point stationary
        double dx = (after.x - before.x) / zoom;
        double dy = (after.y - before.y) / zoom;
        translate(dx, dy);
    }

    // ==================== VIEW MATRIX ====================

    /**
     * Marks the view matrix as needing recalculation.
     */
    private void markDirty() {
        matrixDirty = true;
    }

    /**
     * Rebuilds the view matrix if dirty.
     * 
     * The View Matrix transforms world coordinates to screen coordinates:
     * 1. Translate the world opposite to camera position (center camera at origin)
     * 2. Apply camera rotation (rotate world around camera)
     * 3. Apply zoom (scale from center)
     * 4. Flip Y axis (positive Y points up)
     * 5. Translate to screen center (camera position = screen center)
     * 
     * Coordinate System: Y-axis is inverted so positive Y goes UP (like standard math coordinates)
     */
    private void updateViewMatrix() {
        if (!matrixDirty || viewport == null) return;

        viewMatrix.setToIdentity();

        // Get screen center
        double screenCenterX = viewport.getScreenCenterX();
        double screenCenterY = viewport.getScreenCenterY();

        // 1. Translate to screen center
        viewMatrix.translate(screenCenterX, screenCenterY);

        // 2. Flip Y axis (positive Y points up instead of down)
        viewMatrix.scale(1, -1);

        // 3. Apply zoom (scale from center)
        viewMatrix.scale(zoom, zoom);

        // 4. Apply camera rotation (rotate world around center)
        if (cameraTransform.getRotation() != 0) {
            viewMatrix.rotate(-cameraTransform.getRotationRadians());
        }

        // 5. Translate world opposite to camera position
        viewMatrix.translate(-cameraTransform.getX(), -cameraTransform.getY());

        // Compute inverse matrix for screen-to-world conversion
        try {
            inverseViewMatrix = viewMatrix.createInverse();
        } catch (NoninvertibleTransformException e) {
            // Fallback to identity if matrix is non-invertible
            inverseViewMatrix.setToIdentity();
        }

        matrixDirty = false;
    }

    /**
     * Gets the current view matrix.
     * This matrix transforms world coordinates to screen coordinates.
     */
    public AffineTransform getViewMatrix() {
        updateViewMatrix();
        return viewMatrix;
    }

    /**
     * Gets the inverse view matrix.
     * This matrix transforms screen coordinates to world coordinates.
     */
    public AffineTransform getInverseViewMatrix() {
        updateViewMatrix();
        return inverseViewMatrix;
    }

    // ==================== COORDINATE CONVERSION ====================

    /**
     * Converts a world position to screen pixel coordinates.
     * Essential for rendering objects at the correct screen position.
     * 
     * @param worldX X coordinate in world space
     * @param worldY Y coordinate in world space
     * @return Screen coordinates as Point2D.Double
     */
    public Point2D.Double worldToScreen(double worldX, double worldY) {
        updateViewMatrix();
        Point2D.Double src = new Point2D.Double(worldX, worldY);
        Point2D.Double dst = new Point2D.Double();
        viewMatrix.transform(src, dst);
        return dst;
    }

    /**
     * Converts screen pixel coordinates to world position.
     * Essential for mouse picking and clicking on the map.
     * 
     * @param screenX X coordinate in screen pixels
     * @param screenY Y coordinate in screen pixels
     * @return World coordinates as Point2D.Double
     */
    public Point2D.Double screenToWorld(double screenX, double screenY) {
        updateViewMatrix();
        Point2D.Double src = new Point2D.Double(screenX, screenY);
        Point2D.Double dst = new Point2D.Double();
        inverseViewMatrix.transform(src, dst);
        return dst;
    }

    // ==================== RENDERING ====================

    /**
     * Applies the camera transform to a Graphics2D context.
     * Call this before rendering world objects.
     * 
     * @param g2d The graphics context to transform
     */
    public void applyTransform(Graphics2D g2d) {
        updateViewMatrix();
        g2d.transform(viewMatrix);
    }

    /**
     * Checks if a world position is visible within the current viewport.
     * Useful for culling objects outside the view.
     * 
     * @param worldX X coordinate in world space
     * @param worldY Y coordinate in world space
     * @param objectWidth Width of the object
     * @param objectHeight Height of the object
     * @return true if any part of the object is visible
     */
    public boolean isVisible(double worldX, double worldY, int objectWidth, int objectHeight) {
        if (viewport == null) return true;

        Point2D.Double screenPos = worldToScreen(worldX, worldY);
        double scaledWidth = objectWidth * zoom;
        double scaledHeight = objectHeight * zoom;

        return screenPos.x + scaledWidth > viewport.getX() &&
               screenPos.x < viewport.getX() + viewport.getWidth() &&
               screenPos.y + scaledHeight > viewport.getY() &&
               screenPos.y < viewport.getY() + viewport.getHeight();
    }

    /**
     * Gets the visible world bounds based on camera position and viewport.
     * Returns [minX, minY, maxX, maxY] in world coordinates.
     */
    public double[] getVisibleWorldBounds() {
        if (viewport == null) {
            return new double[]{0, 0, 1920, 1080};
        }

        // Get the four corners of the viewport in world space
        Point2D.Double topLeft = screenToWorld(viewport.getX(), viewport.getY());
        Point2D.Double topRight = screenToWorld(viewport.getX() + viewport.getWidth(), viewport.getY());
        Point2D.Double bottomLeft = screenToWorld(viewport.getX(), viewport.getY() + viewport.getHeight());
        Point2D.Double bottomRight = screenToWorld(viewport.getX() + viewport.getWidth(), viewport.getY() + viewport.getHeight());

        // Find min/max considering rotation might swap corners
        double minX = Math.min(Math.min(topLeft.x, topRight.x), Math.min(bottomLeft.x, bottomRight.x));
        double minY = Math.min(Math.min(topLeft.y, topRight.y), Math.min(bottomLeft.y, bottomRight.y));
        double maxX = Math.max(Math.max(topLeft.x, topRight.x), Math.max(bottomLeft.x, bottomRight.x));
        double maxY = Math.max(Math.max(topLeft.y, topRight.y), Math.max(bottomLeft.y, bottomRight.y));

        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * Retangulo do mundo que esta camera captura para uma resolucao de projeto
     * (design) dada, independente de haver um {@link Viewport} vivo — usado pelo
     * visualizador de camera do editor. A posicao da camera e o centro da view; a
     * area capturada encolhe com o zoom (zoom &gt; 1 aproxima).
     *
     * @param designWidth  largura de referencia do jogo (px)
     * @param designHeight altura de referencia do jogo (px)
     * @return {@code [minX, minY, width, height]} em coordenadas de mundo
     */
    public double[] getFrustumWorldRect(double designWidth, double designHeight) {
        double z = (zoom > 0) ? zoom : 1.0;
        double w = designWidth / z;
        double h = designHeight / z;
        return new double[] { getX() - w / 2.0, getY() - h / 2.0, w, h };
    }

    // ==================== GAME OBJECT OVERRIDES ====================

    @Override
    public void tick() {
        // Camera typically doesn't have automatic behavior
        // Movement is controlled by scripts or the editor
    }

    // ---- Follow / shake / bounds (Fase B) ----

    /** Faz a camera seguir suavemente o centro de um objeto. smoothing 0..1 por tick. */
    public void follow(GameObject target, double smoothing) {
        this.followTarget = target;
        this.followSmoothing = Math.max(0.0, Math.min(1.0, smoothing));
        this.baseX = getX();
        this.baseY = getY();
    }

    /** Para de seguir; a camera fica onde estava. */
    public void stopFollow() {
        this.followTarget = null;
    }

    public GameObject getFollowTarget() { return followTarget; }

    /** Limita o centro da camera a um retangulo do mundo. */
    public void setBounds(double minX, double minY, double maxX, double maxY) {
        this.boundMinX = Math.min(minX, maxX);
        this.boundMinY = Math.min(minY, maxY);
        this.boundMaxX = Math.max(minX, maxX);
        this.boundMaxY = Math.max(minY, maxY);
        this.hasBounds = true;
    }

    public void clearBounds() { this.hasBounds = false; }

    /** Dispara um tremor de camera com decaimento linear ao longo de duration (s). */
    public void shake(double intensity, double duration) {
        // Captura a base atual apenas se ainda nao ha shake em andamento nem follow
        // (senao a base ja e gerenciada e capturar leria a posicao ja tremida).
        if (followTarget == null && shakeElapsed >= shakeDuration) {
            this.baseX = getX();
            this.baseY = getY();
        }
        this.shakeIntensity = Math.max(0.0, intensity);
        this.shakeDuration = Math.max(0.0001, duration);
        this.shakeElapsed = 0.0;
    }

    public boolean isShaking() { return shakeElapsed < shakeDuration; }

    /**
     * Avanca follow, bounds e shake em um passo de tempo. Chamado pelo
     * {@link Game#tick()} para a camera ativa durante o Play. Nao faz nada se nao
     * ha follow nem shake ativos (a posicao continua controlada externamente).
     */
    public void update(double dt) {
        boolean shaking = shakeElapsed < shakeDuration;
        if (followTarget == null && !shaking) return;

        // 1. Posicao base: segue o centro do alvo, se houver.
        if (followTarget != null) {
            double tcx = followTarget.getX() + followTarget.getWidth() / 2.0;
            double tcy = followTarget.getY() + followTarget.getHeight() / 2.0;
            baseX += (tcx - baseX) * followSmoothing;
            baseY += (tcy - baseY) * followSmoothing;
        }

        // 2. Limites do mundo.
        if (hasBounds) {
            baseX = Math.max(boundMinX, Math.min(boundMaxX, baseX));
            baseY = Math.max(boundMinY, Math.min(boundMaxY, baseY));
        }

        // 3. Deslocamento do tremor (decaimento linear), aplicado por cima da base.
        double ox = 0, oy = 0;
        if (shaking) {
            shakeElapsed += dt;
            double k = Math.max(0.0, 1.0 - shakeElapsed / shakeDuration);
            double mag = shakeIntensity * k;
            ox = (shakeRng.nextDouble() * 2.0 - 1.0) * mag;
            oy = (shakeRng.nextDouble() * 2.0 - 1.0) * mag;
        }

        // 4. Aplica base + shake sem contaminar a base (setPosition nao mexe em baseX/baseY).
        setPosition(baseX + ox, baseY + oy);
    }

    @Override
    public void render(java.awt.Graphics g) {
        // Camera itself is usually not rendered
        // But in editor mode, we draw a simple circle indicator at camera center
        if (!visible) return;

        java.awt.Graphics2D g2d = (java.awt.Graphics2D) g;
        
        // In editor, draw a simple circle indicator at camera center position
        if (game != null && game.getGameState() == Game.GameState.EDITING) {
            int cx = (int) x;
            int cy = (int) y;
            
            // Calculate radius based on zoom to keep consistent screen size
            int radius = (int)(16 / zoom);
            radius = Math.max(8, radius); // Minimum visible size
            
            // Draw a simple semi-transparent circle (50% opacity)
            g2d.setColor(new java.awt.Color(100, 180, 255, 128)); // 128 = 50% opacity
            g2d.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            
            // Subtle border
            g2d.setColor(new java.awt.Color(80, 150, 220, 180));
            g2d.setStroke(new java.awt.BasicStroke(1.5f));
            g2d.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props.has("zoom")) {
            this.zoom = props.getDouble("zoom");
        }
        if (props.has("cameraName")) {
            this.cameraName = props.getString("cameraName");
        }
        if (props.has("isActive")) {
            this.isActive = props.getBoolean("isActive");
        }
        if (props.has("transform")) {
            this.cameraTransform = Transform.fromJSON(props.getJSONObject("transform"));
            this.x = cameraTransform.getX();
            this.y = cameraTransform.getY();
            this.rotation = cameraTransform.getRotation();
        }
        markDirty();
    }

    @Override
    public JSONObject saveProperties() {
        JSONObject props = new JSONObject();
        props.put("zoom", zoom);
        props.put("cameraName", cameraName);
        props.put("isActive", isActive);
        props.put("transform", cameraTransform.toJSON());
        return props;
    }

    @Override
    public String getType() {
        return "Camera";
    }

    // ==================== GETTERS & SETTERS ====================

    public boolean isActiveCamera() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public String getCameraName() {
        return cameraName;
    }

    public void setCameraName(String cameraName) {
        this.cameraName = cameraName;
        this.name = cameraName;
    }

    @Override
    public String toString() {
        return String.format("Camera('%s' at (%.1f, %.1f), zoom=%.2f, rot=%.1f°)",
                cameraName, cameraTransform.getX(), cameraTransform.getY(),
                zoom, cameraTransform.getRotation());
    }
}
