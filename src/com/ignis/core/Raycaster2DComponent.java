package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Componente nativo do IgnisEngine para sensores por raio 2D (Raycaster).
 * Dispara raios vetoriais invisíveis a partir da entidade para detecção de chão
 * (Grounded Check), linha de visão de inimigos ou mecânicas de tiro instantâneo (hitscan).
 */
public class Raycaster2DComponent extends Component {

    @Serialize
    private float directionX = 0.0f; // Padrão: apontando para baixo (0, 1)

    @Serialize
    private float directionY = 1.0f;

    @Serialize
    private float distance = 100.0f; // Comprimento máximo do raio em pixels

    @Serialize
    private String layerMask = "*"; // "*", "All" ou camada específica (ex: "Default", "Ground")

    @Serialize
    private boolean useGameObjectRotation = true;

    @Serialize
    private boolean debugDraw = true;

    @Serialize
    private boolean enabled = true;

    // Cache transiente do resultado da última checagem de raio
    private transient IgnisSampleCollisions.RaycastResult lastHit = new IgnisSampleCollisions.RaycastResult();

    public Raycaster2DComponent() {
    }

    public Raycaster2DComponent(float directionX, float directionY, float distance) {
        this.directionX = directionX;
        this.directionY = directionY;
        this.distance = distance;
    }

    @Override
    public void awake() {
    }

    @Override
    public void start() {
    }

    @Override
    public void update(float deltaTime) {
        if (enabled) {
            castRay();
        }
    }

    /**
     * Executa o disparar do raio vetorial 2D e armazena o resultado em cache.
     * @return O resultado detalhado do impacto {@link IgnisSampleCollisions.RaycastResult}.
     */
    public IgnisSampleCollisions.RaycastResult castRay() {
        if (gameObject == null || gameObject.getGame() == null) {
            lastHit = new IgnisSampleCollisions.RaycastResult();
            return lastHit;
        }

        double startX = gameObject.getX();
        double startY = gameObject.getY();

        double dirX = directionX;
        double dirY = directionY;

        // Se ativado, ajusta a direção do vetor com base na rotação atual do GameObject
        if (useGameObjectRotation && gameObject.getRotation() != 0) {
            double angleRad = Math.toRadians(gameObject.getRotation());
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            double rx = dirX * cos - dirY * sin;
            double ry = dirX * sin + dirY * cos;
            dirX = rx;
            dirY = ry;
        }

        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.0001) {
            dirX = 0;
            dirY = 1;
        } else {
            dirX /= len;
            dirY /= len;
        }

        Game game = gameObject.getGame();
        List<IgnisSampleCollisions.Collider> candidateColliders = new ArrayList<>();

        for (GameObject entity : game.getEntities()) {
            if (entity == null || entity == gameObject || !entity.isVisible()) {
                continue;
            }

            ColliderComponent comp = entity.getComponent(ColliderComponent.class);
            if (comp == null || !comp.isEnabled()) {
                continue;
            }

            // Filtragem por Layer Mask
            if (!matchesLayerMask(comp.getCollisionLayer(), entity.getLayer())) {
                continue;
            }

            IgnisSampleCollisions.Collider runtimeCollider = comp.getRuntimeCollider();
            if (runtimeCollider != null) {
                candidateColliders.add(runtimeCollider);
            }
        }

        lastHit = IgnisSampleCollisions.raycast(startX, startY, dirX, dirY, distance, candidateColliders);
        return lastHit;
    }

    /**
     * Retorna verdadeiro se o raio atingir um colisor válido dentro da distância máxima.
     * Útil para validação rápida de chão (Grounded Check).
     */
    public boolean isGrounded() {
        if (lastHit == null) {
            castRay();
        }
        return lastHit != null && lastHit.hit && lastHit.distance <= distance;
    }

    /**
     * Retorna se o raio colidiu com alguma superfície no último disparo.
     */
    public boolean isHit() {
        return lastHit != null && lastHit.hit;
    }

    /**
     * Retorna a distância exata até o ponto de impacto (ou a distância máxima se não houver impacto).
     */
    public float getHitDistance() {
        return (lastHit != null && lastHit.hit) ? (float) lastHit.distance : distance;
    }

    /**
     * Retorna o GameObject atingido pelo raio ou null caso nenhum tenha sido colidido.
     */
    public GameObject getHitObject() {
        return (lastHit != null && lastHit.hit) ? lastHit.gameObject : null;
    }

    /**
     * Retorna o resultado completo da última colisão do raio.
     */
    public IgnisSampleCollisions.RaycastResult getLastHit() {
        return lastHit;
    }

    /**
     * Valida se a camada do colisor bate com a Layer Mask configurada.
     */
    private boolean matchesLayerMask(String colliderLayer, String entityLayer) {
        if (layerMask == null || layerMask.trim().isEmpty() || "*".equals(layerMask.trim()) || "All".equalsIgnoreCase(layerMask.trim())) {
            return true;
        }

        String target = layerMask.trim().toLowerCase();
        if (colliderLayer != null && colliderLayer.trim().toLowerCase().equals(target)) {
            return true;
        }
        if (entityLayer != null && entityLayer.trim().toLowerCase().equals(target)) {
            return true;
        }

        return false;
    }

    /**
     * Desenha o gizmo do raio no editor e no modo de depuração.
     */
    public void render(Graphics g) {
        if (!debugDraw || gameObject == null) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double startX = gameObject.getX();
        double startY = gameObject.getY();

        double dirX = directionX;
        double dirY = directionY;

        if (useGameObjectRotation && gameObject.getRotation() != 0) {
            double angleRad = Math.toRadians(gameObject.getRotation());
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            double rx = dirX * cos - dirY * sin;
            double ry = dirX * sin + dirY * cos;
            dirX = rx;
            dirY = ry;
        }

        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len >= 0.0001) {
            dirX /= len;
            dirY /= len;
        } else {
            dirX = 0;
            dirY = 1;
        }

        double currentDist = (lastHit != null && lastHit.hit) ? lastHit.distance : distance;
        double endX = startX + dirX * currentDist;
        double endY = startY + dirY * currentDist;

        if (lastHit != null && lastHit.hit) {
            g2d.setColor(new Color(0, 230, 118, 220)); // Verde vibrante quando colide
        } else {
            g2d.setColor(new Color(255, 82, 82, 180)); // Vermelho translucido quando livre
        }

        g2d.setStroke(new java.awt.BasicStroke(1.5f));
        g2d.drawLine((int) startX, (int) startY, (int) endX, (int) endY);

        // Se houve impacto, desenha o ponto e vetor normal
        if (lastHit != null && lastHit.hit) {
            g2d.setColor(Color.YELLOW);
            g2d.fillOval((int) lastHit.hitX - 3, (int) lastHit.hitY - 3, 6, 6);
            g2d.setColor(Color.CYAN);
            g2d.drawLine((int) lastHit.hitX, (int) lastHit.hitY,
                    (int) (lastHit.hitX + lastHit.normalX * 12),
                    (int) (lastHit.hitY + lastHit.normalY * 12));
        }
    }

    // Getters e Setters

    public float getDirectionX() { return directionX; }
    public void setDirectionX(float directionX) { this.directionX = directionX; }

    public float getDirectionY() { return directionY; }
    public void setDirectionY(float directionY) { this.directionY = directionY; }

    public float getDistance() { return distance; }
    public void setDistance(float distance) { this.distance = Math.max(0.0f, distance); }

    public String getLayerMask() { return layerMask; }
    public void setLayerMask(String layerMask) { this.layerMask = layerMask; }

    public boolean isUseGameObjectRotation() { return useGameObjectRotation; }
    public void setUseGameObjectRotation(boolean useGameObjectRotation) { this.useGameObjectRotation = useGameObjectRotation; }

    public boolean isDebugDraw() { return debugDraw; }
    public void setDebugDraw(boolean debugDraw) { this.debugDraw = debugDraw; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
