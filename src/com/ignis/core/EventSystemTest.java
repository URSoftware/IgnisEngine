package com.ignis.core;

/**
 * Test class to validate the decoupled collision event system execution.
 */
public class EventSystemTest {
    public static void main(String[] args) {
        System.out.println("=== Testing Collision Event System ===");

        // 1. Create GameObject
        GameObject obj = new GameObject();
        obj.setName("PlayerEntity");

        // 2. Create and attach components
        ColliderComponent collider = new ColliderComponent();
        HealthComponent health = new HealthComponent();

        obj.addComponent(collider);
        obj.addComponent(health);

        // Components are initialized (awake) automatically during addComponent.

        // 3. Simulate physical impact
        GameObject enemy = new GameObject();
        enemy.setName("SpikeTrap");

        CollisionData collision = new CollisionData(enemy, "x:10.0, y:20.0");

        System.out.println("Triggering simulated collision...");
        collider.onPhysicsImpact(collision);

        System.out.println("Final health after collision: " + health.getHealth());
    }
}
