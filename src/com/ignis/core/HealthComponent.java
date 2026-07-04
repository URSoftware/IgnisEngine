package com.ignis.core;

/**
 * Gameplay component that manages entity health and reacts to collision events.
 */
public class HealthComponent extends Component {

    private int health = 100;

    /**
     * Default constructor for the health component.
     */
    public HealthComponent() {
    }

    @Override
    public void awake() {
        if (gameObject != null) {
            // Subscribes to the collision enter event of the owner GameObject
            gameObject.onCollisionEnter.subscribe(this::handleCollision);
        }
    }

    /**
     * Handles the collision impact and decreases the owner GameObject's health.
     * 
     * @param data The collision impact details payload.
     */
    private void handleCollision(CollisionData data) {
        this.health -= 10;
        System.out.println("[HealthComponent] " + gameObject.getName() + " collided with " 
                + (data.getOther() != null ? data.getOther().getName() : "unknown")
                + " at point " + data.getCollisionPoint() 
                + ". Remaining health: " + health);
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }
}
