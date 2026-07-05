package com.ignis.core;

/**
 * Component responsible for managing and exposing geometric and physical properties for physics collision detection.
 */
public class ColliderComponent extends Component {

    @Serialize
    private String shape = "Box"; // Box, Sphere, Capsule
    @Serialize
    private double friction = 0.5;
    @Serialize
    private double bounciness = 0.0;
    @Serialize
    private boolean isTrigger = false;
    @Serialize
    private String collisionLayer = "Default";

    /**
     * Default constructor for the collider component.
     */
    public ColliderComponent() {
    }

    @Override
    public void awake() {
        // Registers this shape to the PhysicsSystem or similar simulation list.
    }

    @Override
    public void start() {
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
