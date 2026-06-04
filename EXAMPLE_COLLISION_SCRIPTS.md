# Ready-to-Use Collision Scripts

This file contains copy-paste ready scripts for common collision scenarios.

## 1. Simple Collision Detector

**Use Case**: Test that collision detection is working

```java
public class SimpleCollisionDetector extends IgnisScript {
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
        getGame().alert("Collision detector ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert("Collision with: " + other.getName());
    }
}
```

**How to use**: Attach to any GameObject and move another GameObject into it

---

## 2. Health System with Collision Damage

**Use Case**: Take damage when colliding with hazards

```java
public class HealthSystem extends IgnisScript {
    private int currentHealth = 100;
    private final int maxHealth = 100;
    private final int damagePerHit = 20;
    private boolean canTakeDamage = true;
    private float damageCooldown = 0f;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
        getGame().alert("Health: " + currentHealth + "/" + maxHealth);
    }
    
    @Override
    public void update() {
        // Cooldown for damage
        if (!canTakeDamage) {
            damageCooldown -= 0.016f; // 60fps
            if (damageCooldown <= 0) {
                canTakeDamage = true;
            }
        }
    }
    
    @Override
    public void onCollision(GameObject other) {
        if (canTakeDamage && isHazard(other)) {
            takeDamage(damagePerHit);
            damageCooldown = 0.5f; // 0.5 second cooldown
            canTakeDamage = false;
        }
    }
    
    private void takeDamage(int damage) {
        currentHealth -= damage;
        getGame().alert("Damage taken! HP: " + currentHealth + "/" + maxHealth);
        
        if (currentHealth <= 0) {
            die();
        }
    }
    
    private void die() {
        getGame().alert("DEAD!");
        getGame().removeEntity(this.getGameObject());
    }
    
    private boolean isHazard(GameObject obj) {
        String name = obj.getName().toLowerCase();
        return name.contains("spike") || name.contains("hazard") || name.contains("enemy");
    }
}
```

**How to use**: Attach to player, create enemy/spike objects, collide with them to take damage

---

## 3. Trigger Zone with Entry/Exit

**Use Case**: Detect when player enters/exits a zone

```java
public class TriggerZone extends IgnisScript {
    private boolean playerInsideZone = false;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");
        setCollisionShape("rectangle");
        getGame().alert("Trigger zone ready!");
    }
    
    @Override
    public void onTrigger(GameObject other) {
        if (other.getName().equals("Player")) {
            playerInsideZone = true;
            getGame().alert("Player entered zone!");
            onZoneEntered();
        }
    }
    
    @Override
    public void update() {
        // Check if player left zone (simple distance check)
        if (playerInsideZone) {
            List<GameObject> entities = getGame().getEntities();
            GameObject player = null;
            
            for (GameObject entity : entities) {
                if (entity.getName().equals("Player")) {
                    player = entity;
                    break;
                }
            }
            
            if (player != null) {
                double distance = getDistanceTo(player);
                int zoneRadius = Math.max(
                    this.getGameObject().getWidth(),
                    this.getGameObject().getHeight()
                );
                
                if (distance > zoneRadius) {
                    playerInsideZone = false;
                    getGame().alert("Player left zone!");
                    onZoneExited();
                }
            }
        }
    }
    
    private void onZoneEntered() {
        // Override in subclasses
    }
    
    private void onZoneExited() {
        // Override in subclasses
    }
    
    private double getDistanceTo(GameObject other) {
        double dx = other.getX() - this.getGameObject().getX();
        double dy = other.getY() - this.getGameObject().getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
```

**How to use**: Attach to a large rectangular object, move player through it

---

## 4. Item Collection System

**Use Case**: Collect items and accumulate score

```java
public class ItemCollector extends IgnisScript {
    private int score = 0;
    private int itemsCollected = 0;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");
        setCollisionShape("circle");
        getGame().alert("Score: " + score);
    }
    
    @Override
    public void onTrigger(GameObject other) {
        if (isCollectable(other)) {
            collectItem(other);
        }
    }
    
    private void collectItem(GameObject item) {
        int itemValue = getItemValue(item);
        score += itemValue;
        itemsCollected++;
        
        getGame().alert("+" + itemValue + " Score: " + score);
        
        // Remove item from world
        getGame().removeEntity(item);
    }
    
    private int getItemValue(GameObject item) {
        String name = item.getName().toLowerCase();
        
        if (name.contains("coin")) return 10;
        if (name.contains("gem")) return 50;
        if (name.contains("gold")) return 100;
        if (name.contains("star")) return 25;
        
        return 5; // Default value
    }
    
    private boolean isCollectable(GameObject obj) {
        String name = obj.getName().toLowerCase();
        return name.contains("item") || name.contains("coin") || 
               name.contains("gem") || name.contains("gold") || 
               name.contains("star");
    }
}
```

**How to use**: Attach to player, create items with names containing "item", "coin", etc., move over them

---

## 5. Platform Collision Detection

**Use Case**: Detect when on ground for jumping

```java
public class PlatformDetector extends IgnisScript {
    private boolean isOnGround = false;
    private boolean canJump = false;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
        getGame().alert("Platform detector ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        if (isPlatform(other)) {
            // Check if we're on top (crude check)
            if (this.getGameObject().getY() > other.getY()) {
                isOnGround = true;
                canJump = true;
                getGame().alert("On platform: " + other.getName());
            }
        }
    }
    
    @Override
    public void update() {
        // Jump input
        if (canJump && getInput().isKeyPressed("space")) {
            jump();
        }
    }
    
    private void jump() {
        getGame().alert("Jumping!");
        canJump = false;
        // Implement jump physics here
    }
    
    private boolean isPlatform(GameObject obj) {
        String name = obj.getName().toLowerCase();
        return name.contains("platform") || name.contains("ground") || name.contains("floor");
    }
}
```

**How to use**: Attach to player, create platforms below, walk on them and press space to jump

---

## 6. Enemy AI with Collision Response

**Use Case**: Enemy detects player and responds

```java
public class EnemyAI extends IgnisScript {
    private boolean playerDetected = false;
    private boolean playerInRange = false;
    private float alertTimer = 0f;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");
        setCollisionShape("circle");
        getGame().alert("Enemy AI active!");
    }
    
    @Override
    public void onTrigger(GameObject other) {
        if (other.getName().equals("Player")) {
            playerDetected = true;
            playerInRange = true;
            alertTimer = 2f;
            getGame().alert("ALERT! Enemy detected player!");
        }
    }
    
    @Override
    public void update() {
        if (playerInRange) {
            alertTimer -= 0.016f;
            if (alertTimer <= 0) {
                playerInRange = false;
            }
        }
        
        if (playerDetected) {
            // Implement chase AI here
            moveTowardsPlayer();
        }
    }
    
    private void moveTowardsPlayer() {
        List<GameObject> entities = getGame().getEntities();
        GameObject player = null;
        
        for (GameObject entity : entities) {
            if (entity.getName().equals("Player")) {
                player = entity;
                break;
            }
        }
        
        if (player != null) {
            // Simple movement towards player
            double dx = player.getX() - this.getGameObject().getX();
            if (Math.abs(dx) > 5) {
                // Move towards player
            }
        }
    }
}
```

**How to use**: Attach to enemy object, it will detect player when they enter the trigger zone

---

## 7. Bouncing Collision Response

**Use Case**: Object bounces on collision

```java
public class BouncingObject extends IgnisScript {
    private float velocityX = 0;
    private float velocityY = 0;
    private float bounceForce = 200f;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
        velocityX = 150f; // Initial velocity
        getGame().alert("Bouncing object ready!");
    }
    
    @Override
    public void update() {
        // Simple movement
        GameObject obj = this.getGameObject();
        obj.setPosition(
            obj.getX() + velocityX * 0.016f,
            obj.getY() + velocityY * 0.016f
        );
        
        // Gravity
        velocityY += 300f * 0.016f; // Gravity
        
        // Wall boundaries
        if (obj.getX() < 0 || obj.getX() > 1280) {
            velocityX *= -1;
        }
    }
    
    @Override
    public void onCollision(GameObject other) {
        if (isPlatform(other)) {
            // Simple bounce
            velocityY = -bounceForce;
            getGame().alert("Bounce!");
        }
    }
    
    private boolean isPlatform(GameObject obj) {
        String name = obj.getName().toLowerCase();
        return name.contains("platform") || name.contains("ground");
    }
}
```

**How to use**: Attach to a ball-like object, watch it bounce on platforms

---

## 8. Collision Counter with Persistence

**Use Case**: Count total collisions across frames

```java
public class CollisionCounter extends IgnisScript {
    private int totalCollisions = 0;
    private int collisionsThisFrame = 0;
    private int maxCollisionsPerFrame = 0;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
        getGame().alert("Collision counter started!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        collisionsThisFrame++;
        totalCollisions++;
        
        if (collisionsThisFrame <= 3) { // Limit spam
            getGame().alert("Collision #" + totalCollisions + ": " + other.getName());
        }
    }
    
    @Override
    public void update() {
        if (collisionsThisFrame > maxCollisionsPerFrame) {
            maxCollisionsPerFrame = collisionsThisFrame;
        }
        
        // Reset frame counter
        collisionsThisFrame = 0;
        
        // Periodic report
        if ((int)(System.currentTimeMillis() / 1000) % 3 == 0) {
            getGame().alert("Total collisions: " + totalCollisions);
        }
    }
}
```

**How to use**: Attach to any object and collide it with multiple objects to see counters

---

## 9. Distance-Based Collision Alert

**Use Case**: Alert when objects get close without touching

```java
public class ProximityAlert extends IgnisScript {
    private float alertDistance = 100f;
    private float lastAlertTime = 0f;
    private final float alertCooldown = 1f;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");
        setCollisionShape("circle");
        getGame().alert("Proximity alert ready!");
    }
    
    @Override
    public void update() {
        List<GameObject> entities = getGame().getEntities();
        long currentTime = System.currentTimeMillis();
        
        for (GameObject entity : entities) {
            if (entity == this.getGameObject()) continue;
            if (isMonitored(entity)) {
                double distance = getDistanceTo(entity);
                
                if (distance < alertDistance && 
                    (currentTime - lastAlertTime * 1000) > (alertCooldown * 1000)) {
                    getGame().alert("NEAR: " + entity.getName() + " (" + (int)distance + "px)");
                    lastAlertTime = currentTime / 1000f;
                }
            }
        }
    }
    
    private double getDistanceTo(GameObject other) {
        double dx = other.getX() - this.getGameObject().getX();
        double dy = other.getY() - this.getGameObject().getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    private boolean isMonitored(GameObject obj) {
        String name = obj.getName().toLowerCase();
        return name.contains("enemy") || name.contains("hazard");
    }
}
```

**How to use**: Attach to player, enemies nearby will trigger proximity alerts

---

## 10. Multi-Object Collision Tracker

**Use Case**: Track simultaneous collisions with multiple objects

```java
public class MultiCollisionTracker extends IgnisScript {
    private Map<String, Integer> collisionMap = new HashMap<>();
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
        getGame().alert("Collision tracker ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        String objectName = other.getName();
        collisionMap.put(objectName, collisionMap.getOrDefault(objectName, 0) + 1);
        
        getGame().alert(objectName + " x" + collisionMap.get(objectName));
    }
    
    public int getCollisionCount(String objectName) {
        return collisionMap.getOrDefault(objectName, 0);
    }
    
    public void printCollisionReport() {
        StringBuilder report = new StringBuilder("Collision Report:\n");
        for (String name : collisionMap.keySet()) {
            report.append(name).append(": ").append(collisionMap.get(name)).append("\n");
        }
        getGame().alert(report.toString());
    }
}
```

**How to use**: Attach to an object, collide with multiple different objects to build a map

---

## Tips for Using These Scripts

1. **Copy the entire `public class` block** including all methods
2. **Adjust shape** with different collision shapes (circle, rectangle, etc.)
3. **Modify collision mode** between "collision" and "trigger" as needed
4. **Customize alert messages** to fit your game
5. **Debug by looking at alerts** displayed in the top-left of editor window
6. **Combine features** from multiple scripts to create your own

---

## Testing Workflow

For each script:
1. Create a test GameObject
2. Attach the script
3. Add another GameObject to test collision with
4. Watch the alerts at the top-left
5. Verify behavior matches expectations
6. Adjust parameters as needed

Happy testing!
