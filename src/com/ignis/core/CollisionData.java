package com.ignis.core;

/**
 * Encapsulates information about a physical collision impact.
 */
public class CollisionData {
    private final GameObject other;
    private final String collisionPoint;

    /**
     * Creates a new collision data instance.
     * 
     * @param other The other GameObject involved in the impact.
     * @param collisionPoint The point of collision represented as text or a simplified vector.
     */
    public CollisionData(GameObject other, String collisionPoint) {
        this.other = other;
        this.collisionPoint = collisionPoint;
    }

    /**
     * Gets the other GameObject involved in the collision.
     * 
     * @return O GameObject colidido.
     */
    public GameObject getOther() {
        return other;
    }

    /**
     * Gets the vector or coordinate description of the collision point.
     * 
     * @return The collision point coordinates.
     */
    public String getCollisionPoint() {
        return collisionPoint;
    }
}
