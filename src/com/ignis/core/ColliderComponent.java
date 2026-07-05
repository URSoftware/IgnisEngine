package com.ignis.core;

/**
 * Fonte unica de verdade para a geometria e as propriedades de colisao de um
 * {@link GameObject} no padrao Entidade-Componente.
 *
 * <p>Substitui o par legado {@code GameObject.colliderType/collisionMode}: alem
 * dos parametros fisicos (friccao, elasticidade, trigger, layer) o componente
 * agora carrega a <b>hitbox</b> (tamanho + offset) e constroi/registra por conta
 * propria um {@link IgnisSampleCollisions.Collider} no CollisionManager do jogo,
 * tornando-se funcional em tempo de execucao. O gizmo de collider do editor (item
 * 8b) edita diretamente {@link #getWorldBounds}/{@link #resizeToWorldBounds}.</p>
 *
 * <p><b>Semantica do offset:</b> para {@code Box}/{@code Capsule} o offset desloca
 * o <i>canto superior-esquerdo</i> da hitbox em relacao ao do dono (a caixa ocupa
 * {@code [owner.x + offsetX, owner.y + offsetY, width, height]}). Para
 * {@code Sphere} o offset desloca o <i>centro</i> do circulo em relacao ao centro
 * do dono. Ambos default 0 (hitbox coincide com o dono).</p>
 */
public class ColliderComponent extends Component {

    @Serialize
    private String shape = "Box"; // Box, Sphere, Capsule
    @Serialize
    private double width = 0.0;   // <= 0 => usa a largura do dono
    @Serialize
    private double height = 0.0;  // <= 0 => usa a altura do dono
    @Serialize
    private double radius = 0.0;  // <= 0 => usa metade do menor lado do dono (Sphere)
    @Serialize
    private double offsetX = 0.0;
    @Serialize
    private double offsetY = 0.0;
    @Serialize
    private double friction = 0.5;
    @Serialize
    private double bounciness = 0.0;
    @Serialize
    private boolean isTrigger = false;
    @Serialize
    private boolean enabled = true;
    @Serialize
    private String collisionLayer = "Default";

    // Collider concreto registrado no CollisionManager. Transiente: reconstruido a
    // partir dos campos @Serialize. 'dirty' forca a reconstrucao quando a forma muda.
    private transient IgnisSampleCollisions.Collider runtimeCollider;
    private transient boolean dirty = true;

    /**
     * Default constructor for the collider component.
     */
    public ColliderComponent() {
    }

    @Override
    public void awake() {
        // Ja tenta materializar/registrar o collider — util para o debug view do
        // editor e para objetos criados em runtime. Idempotente (ver ensureRegistered).
        ensureRegistered();
    }

    @Override
    public void start() {
        ensureRegistered();
    }

    @Override
    public void update(float deltaTime) {
        // Mantem o collider registrado e com a geometria/modo em dia a cada tick de Play.
        ensureRegistered();
        syncRuntimeCollider();
    }

    @Override
    public void onDetach() {
        // Remove o collider do manager ao ser removido do GameObject.
        if (runtimeCollider != null && gameObject != null && gameObject.getGame() != null
                && gameObject.getGame().getCollisionManager() != null) {
            gameObject.getGame().getCollisionManager().removeCollider(runtimeCollider);
        }
        runtimeCollider = null;
        dirty = true;
    }

    @Override
    public void loadProperties(org.json.JSONObject props,
                               ScriptSerializationHelper.GameObjectResolver resolver) {
        super.loadProperties(props, resolver);
        // Os campos @Serialize foram setados por reflexao (sem passar pelos setters),
        // entao o collider concreto precisa ser reconstruido/re-registrado com a
        // geometria carregada — senao a hitbox no manager fica com o tamanho default.
        this.dirty = true;
        ensureRegistered();
    }

    /**
     * Simulates receiving an impact notification from the physics engine and triggers the GameObject collision event.
     *
     * @param data The collision impact details payload.
     */
    public void onPhysicsImpact(CollisionData data) {
        if (gameObject != null) {
            gameObject.onCollisionEnter.invoke(data);
        }
    }

    // ==================== PONTE DE RUNTIME (CollisionManager) ====================

    /** Mapeia a forma logica para o tipo concreto de collider do motor. */
    public IgnisSampleCollisions.ColliderType resolveColliderType() {
        if ("Sphere".equalsIgnoreCase(shape)) {
            return IgnisSampleCollisions.ColliderType.CIRCLE;
        }
        // Box e Capsule usam AABB (Capsule ainda nao tem forma dedicada no motor).
        return IgnisSampleCollisions.ColliderType.AABB;
    }

    /**
     * Constroi (ou reconstroi, se a forma mudou) e devolve o collider concreto deste
     * componente. Retorna null se ainda nao ha dono. Nao registra no manager — use
     * {@link #ensureRegistered()}.
     */
    public IgnisSampleCollisions.Collider getOrBuildRuntimeCollider() {
        if (gameObject == null) {
            return null;
        }
        IgnisSampleCollisions.ColliderType wanted = resolveColliderType();
        boolean typeMismatch = runtimeCollider == null || runtimeCollider.getType() != wanted
                || runtimeCollider.getOwner() != gameObject;
        if (typeMismatch) {
            // Ao trocar a forma/dono, o collider antigo deve sair do manager para nao
            // ficar uma hitbox fantasma registrada.
            if (runtimeCollider != null && gameObject.getGame() != null
                    && gameObject.getGame().getCollisionManager() != null) {
                gameObject.getGame().getCollisionManager().removeCollider(runtimeCollider);
            }
            switch (wanted) {
                case CIRCLE:
                    runtimeCollider = new IgnisSampleCollisions.CircleCollider(gameObject);
                    break;
                case AABB:
                default:
                    runtimeCollider = new IgnisSampleCollisions.AABBCollider(gameObject);
                    break;
            }
            dirty = true;
        }
        syncRuntimeCollider();
        return runtimeCollider;
    }

    /** Copia tamanho/offset/modo/enabled dos campos deste componente para o collider concreto. */
    private void syncRuntimeCollider() {
        if (runtimeCollider == null || gameObject == null) {
            return;
        }
        runtimeCollider.setEnabled(enabled);
        runtimeCollider.setMode(isTrigger
                ? IgnisSampleCollisions.CollisionMode.TRIGGER
                : IgnisSampleCollisions.CollisionMode.COLLISION);
        runtimeCollider.setOffset(offsetX, offsetY);
        if (runtimeCollider instanceof IgnisSampleCollisions.AABBCollider) {
            ((IgnisSampleCollisions.AABBCollider) runtimeCollider).setSize(effectiveWidth(), effectiveHeight());
        } else if (runtimeCollider instanceof IgnisSampleCollisions.CircleCollider) {
            ((IgnisSampleCollisions.CircleCollider) runtimeCollider).setRadius(effectiveRadius());
        }
        dirty = false;
    }

    /** Garante que o collider concreto exista e esteja registrado no CollisionManager. */
    public void ensureRegistered() {
        if (gameObject == null || gameObject.getGame() == null) {
            return;
        }
        IgnisSampleCollisions.CollisionManager mgr = gameObject.getGame().getCollisionManager();
        if (mgr == null) {
            return;
        }
        IgnisSampleCollisions.Collider c = getOrBuildRuntimeCollider();
        if (c != null) {
            mgr.addCollider(c); // idempotente (contains check no manager)
        }
    }

    // ==================== GEOMETRIA / BOUNDS ====================

    /** Largura efetiva da hitbox (default: largura do dono quando width <= 0). */
    public double effectiveWidth() {
        if (width > 0) return width;
        return gameObject != null ? gameObject.getWidth() : 0.0;
    }

    /** Altura efetiva da hitbox (default: altura do dono quando height <= 0). */
    public double effectiveHeight() {
        if (height > 0) return height;
        return gameObject != null ? gameObject.getHeight() : 0.0;
    }

    /** Raio efetivo (default: metade do menor lado do dono quando radius <= 0). */
    public double effectiveRadius() {
        if (radius > 0) return radius;
        if (gameObject == null) return 0.0;
        return Math.min(gameObject.getWidth(), gameObject.getHeight()) / 2.0;
    }

    /**
     * Bounding box da hitbox em coordenadas de mundo: {@code [minX, minY, width, height]}.
     * Base do desenho e do hit-test do gizmo de collider. Retorna {@code null} se
     * nao houver dono.
     */
    public double[] getWorldBounds() {
        if (gameObject == null) {
            return null;
        }
        if ("Sphere".equalsIgnoreCase(shape)) {
            double r = effectiveRadius();
            double cx = gameObject.getX() + gameObject.getWidth() / 2.0 + offsetX;
            double cy = gameObject.getY() + gameObject.getHeight() / 2.0 + offsetY;
            return new double[] { cx - r, cy - r, r * 2.0, r * 2.0 };
        }
        // Box / Capsule: offset desloca o canto superior-esquerdo.
        double w = effectiveWidth();
        double h = effectiveHeight();
        return new double[] { gameObject.getX() + offsetX, gameObject.getY() + offsetY, w, h };
    }

    /**
     * Aplica novos limites de mundo a hitbox (inverso de {@link #getWorldBounds}).
     * Usado pelo arraste das alcas do gizmo. Para {@code Sphere} os limites viram um
     * raio (metade do menor lado) centrado no meio do retangulo.
     *
     * @param minX borda esquerda em mundo
     * @param minY borda superior em mundo
     * @param w    largura desejada (sera clampada a um minimo de 1)
     * @param h    altura desejada (sera clampada a um minimo de 1)
     */
    public void resizeToWorldBounds(double minX, double minY, double w, double h) {
        if (gameObject == null) {
            return;
        }
        w = Math.max(1.0, w);
        h = Math.max(1.0, h);
        if ("Sphere".equalsIgnoreCase(shape)) {
            double r = Math.max(1.0, Math.min(w, h) / 2.0);
            double cx = minX + w / 2.0;
            double cy = minY + h / 2.0;
            this.radius = r;
            this.offsetX = cx - (gameObject.getX() + gameObject.getWidth() / 2.0);
            this.offsetY = cy - (gameObject.getY() + gameObject.getHeight() / 2.0);
        } else {
            this.offsetX = minX - gameObject.getX();
            this.offsetY = minY - gameObject.getY();
            this.width = w;
            this.height = h;
        }
        dirty = true;
        syncRuntimeCollider();
    }

    // ==================== GETTERS / SETTERS ====================

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        if (shape != null && !shape.equals(this.shape)) {
            this.shape = shape;
            this.dirty = true;
            // A troca de forma exige reconstruir o collider concreto no proximo sync.
            getOrBuildRuntimeCollider();
        }
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
        this.dirty = true;
        syncRuntimeCollider();
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
        this.dirty = true;
        syncRuntimeCollider();
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
        this.dirty = true;
        syncRuntimeCollider();
    }

    public double getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
        this.dirty = true;
        syncRuntimeCollider();
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
        this.dirty = true;
        syncRuntimeCollider();
    }

    public double getFriction() {
        return friction;
    }

    public void setFriction(double friction) {
        this.friction = friction;
    }

    public double getBounciness() {
        return bounciness;
    }

    public void setBounciness(double bounciness) {
        this.bounciness = bounciness;
    }

    public boolean isTrigger() {
        return isTrigger;
    }

    public void setTrigger(boolean trigger) {
        this.isTrigger = trigger;
        syncRuntimeCollider();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        syncRuntimeCollider();
    }

    public String getCollisionLayer() {
        return collisionLayer;
    }

    public void setCollisionLayer(String collisionLayer) {
        this.collisionLayer = collisionLayer;
    }

    /** Collider concreto atual (pode ser null antes de {@link #ensureRegistered}). */
    public IgnisSampleCollisions.Collider getRuntimeCollider() {
        return runtimeCollider;
    }
}
