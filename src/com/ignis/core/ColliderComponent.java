package com.ignis.core;

/**
 * Componente responsável por gerenciar e expor propriedades geométricas e físicas de colisão.
 */
public class ColliderComponent extends Component {

    private String shape = "Box"; // Box, Sphere, Capsule
    private double friction = 0.5;
    private double bounciness = 0.0;
    private boolean isTrigger = false;
    private String collisionLayer = "Default";

    /**
     * Construtor padrão do colisor.
     */
    public ColliderComponent() {
    }

    @Override
    public void awake() {
        // Notifica o sistema de simulação de física ao ser acoplado se aplicável
    }

    @Override
    public void start() {
    }

    /**
     * Simula o recebimento de um impacto vindo do motor de física,
     * propagando-o para o GameObject.
     * 
     * @param data Informações sobre a colisão.
     */
    public void onPhysicsImpact(CollisionData data) {
        if (gameObject != null) {
            gameObject.onCollisionEnter.invoke(data);
        }
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
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
    }

    public String getCollisionLayer() {
        return collisionLayer;
    }

    public void setCollisionLayer(String collisionLayer) {
        this.collisionLayer = collisionLayer;
    }
}
