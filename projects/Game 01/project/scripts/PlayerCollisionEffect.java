import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;

/**
 * PlayerCollisionEffect - Handles collision detection using Ignis Engine collision system
 * 
 * This script demonstrates proper usage of the onCollision() method which is 
 * automatically called by the engine when the player object collides with another object.
 * 
 * IMPORTANT: This script requires that:
 * 1. The GameObject has a Collider attached (AABB, Circle, or Polygon)
 * 2. Other game objects also have Colliders
 * 3. The Collider is set to COLLISION or TRIGGER mode
 */
public class PlayerCollisionEffect extends IgnisScript {

    // Track colliding objects to prevent spam
    private java.util.Set<GameObject> currentlyCollidingWith = new java.util.HashSet<>();

    @Override
    public void start() {
        System.out.println("[DEBUG] PlayerCollisionEffect initialized for: " + getScriptName());
        System.out.println("[DEBUG] Player position: (" + transform.x + ", " + transform.y + ")");
        System.out.println("[DEBUG] Collision system active - waiting for collisions with other objects...");
    }

    @Override
    public void tick() {
        // Collision detection is handled automatically by the engine
        // This method just runs the game logic each frame
    }

    /**
     * Called automatically by the Ignis Engine when this object collides with another.
     * This is the proper way to detect collisions in Ignis.
     * 
     * @param other The GameObject that this object collided with
     */
    @Override
    public void onCollision(GameObject other) {
        if (other == null) {
            System.out.println("[DEBUG ERROR] onCollision received null GameObject!");
            return;
        }

        // Check if this is a new collision (not already tracked)
        if (!currentlyCollidingWith.contains(other)) {
            currentlyCollidingWith.add(other);
            
            // Collision just started
            System.out.println("=================================================");
            System.out.println("[COLLISION] Player collided with: " + other.getClass().getSimpleName());
            getGame().alert("Colisão detectada");
            System.out.println("[DEBUG] Player position: (" + transform.x + ", " + transform.y + ")");
            System.out.println("[DEBUG] Other object position: (" + other.getX() + ", " + other.getY() + ")");
            System.out.println("[DEBUG] Other object size: " + other.getWidth() + "x" + other.getHeight());
            System.out.println("=================================================");
            
            // Example: Change visual properties when collision occurs
            // (Future API: gameObject.setColor(new Color(255, 0, 0)); // Red)
            handleCollisionEffect(other);
        }
    }

    /**
     * Helper method to handle collision effects
     * @param other The colliding object
     */
    private void handleCollisionEffect(GameObject other) {
        System.out.println("[DEBUG] Applying collision effect...");
        // Add collision response logic here
        // Examples:
        // - Play collision sound
        // - Apply knockback force
        // - Trigger animation
        // - Reduce health
        // - etc.
    }
}