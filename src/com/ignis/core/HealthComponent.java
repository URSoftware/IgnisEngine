package com.ignis.core;

import com.fxutilities.fxevents.core.SignalReceiver;

/**
 * Gameplay component that manages entity health and reacts to collision events.
 */
public class HealthComponent extends Component {

    @Serialize
    private int health = 100;

    /**
     * Default constructor for the health component.
     */
    public HealthComponent() {
    }

    private SignalReceiver collisionReceiver = payload -> {
        if (payload instanceof CollisionData) {
            handleCollision((CollisionData) payload);
        }
    };

    @Override
    public void awake() {
        if (gameObject != null && gameObject.getGame() != null && gameObject.getGame().getSceneDispatcher() != null) {
            gameObject.getGame().getSceneDispatcher().connect("onCollisionEnter_" + gameObject.getId(), collisionReceiver);
        }
    }

    @Override
    public void onDetach() {
        if (gameObject != null && gameObject.getGame() != null && gameObject.getGame().getSceneDispatcher() != null) {
            gameObject.getGame().getSceneDispatcher().disconnect("onCollisionEnter_" + gameObject.getId(), collisionReceiver);
        }
    }

    /**
     * Handles the collision impact and decreases the owner GameObject's health.
     * 
     * @param data The collision impact details payload.
     */
    private void handleCollision(CollisionData data) {
        this.health -= 10;
        IgnisLogger.info("[HealthComponent] " + gameObject.getName() + " collided with " 
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
