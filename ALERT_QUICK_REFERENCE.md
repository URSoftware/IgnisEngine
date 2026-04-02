# Alert System & Collision Testing Reference Card

## Quick Start Guide

### Enable Alerts in Your Script

```java
// Call anywhere to display a message
getGame().alert("Your message here");
```

### Test Collision Detection

```java
public class TestScript extends IgnisScript {
    
    @Override
    public void start() {
        // Make sure collision is enabled
        setCollisionEnabled(true);
        
        // Choose collision mode: "collision" or "trigger"
        setCollisionMode("collision");
        
        // Choose collision shape
        setCollisionShape("circle");
        
        getGame().alert("Collision system ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        // This runs when two objects collide
        getGame().alert("Hit: " + other.getName());
    }
    
    @Override
    public void onTrigger(GameObject other) {
        // This runs when object enters trigger volume
        getGame().alert("Triggered: " + other.getName());
    }
}
```

---

## Alert Display Info

| Property | Value |
|----------|-------|
| **Position** | Top-left corner (x=15, y=35) |
| **Duration** | 3 seconds |
| **Max Visible** | 5 messages |
| **Fade-out** | Last 1 second |
| **Color** | Green background, white text |
| **Font** | Courier New, Bold, 14pt |

---

## Collision Shapes

```
AVAILABLE SHAPES:
┌─────────────────────┬──────────────────┐
│ Shape Name          │ Usage            │
├─────────────────────┼──────────────────┤
│ "circle"            │ Round objects    │
│ "rectangle"         │ Square/rectangular
│ "triangle"          │ 3-sided shapes   │
│ "square"            │ Perfect squares  │
│ "pentagon"          │ 5-sided shapes   │
│ "star"              │ Star shapes      │
└─────────────────────┴──────────────────┘
```

---

## Common Testing Patterns

### Pattern 1: Collision Counter

```java
private int collisionCount = 0;

@Override
public void onCollision(GameObject other) {
    collisionCount++;
    getGame().alert("Collisions: " + collisionCount);
}
```

### Pattern 2: Collision Type Detection

```java
@Override
public void onCollision(GameObject other) {
    String type = "unknown";
    
    if (other.getName().contains("Enemy")) {
        type = "enemy";
    } else if (other.getName().contains("Hazard")) {
        type = "hazard";
    } else if (other.getName().contains("Item")) {
        type = "item";
    }
    
    getGame().alert("Hit " + type);
}
```

### Pattern 3: Distance Calculation

```java
@Override
public void onCollision(GameObject other) {
    double dx = other.getX() - this.getGameObject().getX();
    double dy = other.getY() - this.getGameObject().getY();
    double distance = Math.sqrt(dx * dx + dy * dy);
    
    getGame().alert("Distance: " + (int)distance + " px");
}
```

### Pattern 4: Trigger Entry/Exit

```java
private Set<GameObject> triggeredObjects = new HashSet<>();

@Override
public void onTrigger(GameObject other) {
    if (!triggeredObjects.contains(other)) {
        triggeredObjects.add(other);
        getGame().alert("Entered: " + other.getName());
    }
}

@Override
public void update() {
    // Check if objects left trigger
    List<GameObject> toRemove = new ArrayList<>();
    for (GameObject obj : triggeredObjects) {
        if (obj == null || !isWithinTrigger(obj)) {
            toRemove.add(obj);
            getGame().alert("Exited: " + obj.getName());
        }
    }
    triggeredObjects.removeAll(toRemove);
}
```

---

## Debug Workflow

### Step 1: Verify Script Execution
```java
@Override
public void start() {
    getGame().alert("Script loaded!");
}
```

### Step 2: Verify Collision Setup
```java
@Override
public void start() {
    setCollisionEnabled(true);
    setCollisionShape("circle");
    getGame().alert("Collision enabled");
}
```

### Step 3: Test Collision Detection
```java
@Override
public void onCollision(GameObject other) {
    getGame().alert("Collision detected!");
}
```

### Step 4: Log Object Information
```java
@Override
public void onCollision(GameObject other) {
    getGame().alert(
        "Name: " + other.getName() + 
        " | Pos: (" + (int)other.getX() + "," + (int)other.getY() + ")"
    );
}
```

---

## Performance Alerts

```java
@Override
public void update() {
    long start = System.currentTimeMillis();
    
    // Your code here
    doWork();
    
    long elapsed = System.currentTimeMillis() - start;
    if (elapsed > 5) {
        getGame().alert("Slow frame: " + elapsed + "ms");
    }
}
```

---

## Collision Mode Reference

### Collision Mode "collision"
- Applies physics impulses
- Prevents objects from passing through each other
- Triggers `onCollision()`

```java
setCollisionMode("collision");
```

### Collision Mode "trigger"
- No physics applied
- Objects can pass through
- Triggers `onTrigger()`

```java
setCollisionMode("trigger");
```

---

## For Quick Testing

**Create a simple test GameObject:**

1. Add a new GameObject in the editor (name it "TestBox")
2. Set size to 100x100
3. Attach this script:

```java
public class QuickTestScript extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
        getGame().alert("Test object ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert("Test collision!");
    }
}
```

4. Duplicate the GameObject
5. Move the duplicate to test collision
6. Watch for alerts!

---

## Useful Alert Messages for Testing

```java
// Object Creation
getGame().alert("Created: Player");

// Initialization
getGame().alert("Game ready!");

// State Changes
getGame().alert("State: Walking");

// Collisions
getGame().alert("Collision with: Wall");

// Errors
getGame().alert("ERROR: Missing component!");

// Performance
getGame().alert("FPS: 60");

// User Input
getGame().alert("Key pressed: Space");
```

---

## Alert Message Best Practices

✅ **DO:** Keep messages short and clear
```java
getGame().alert("Hit enemy!");
```

❌ **DON'T:** Use very long messages
```java
getGame().alert("The player has collided with an enemy character object which represents a game level threat");
```

✅ **DO:** Include relevant data
```java
getGame().alert("HP: " + currentHP + "/" + maxHP);
```

✅ **DO:** Use consistent formatting
```java
getGame().alert("ID: 42 | State: Active");
```

---

## Editor Integration

The alert system is integrated directly into the editor:

1. **Automatic Setup**: When Editor starts, it calls `game.setEditor(this)`
2. **Rendering**: Alerts render on top of everything in the game view
3. **Cleanup**: Expired alerts are automatically removed
4. **Queue Management**: Up to 5 alerts can be visible simultaneously

No additional setup needed - just call `getGame().alert(message)` in your scripts!

---

## Troubleshooting Checklist

- [ ] Is `setCollisionEnabled(true)` called?
- [ ] Is `setCollisionShape()` called with a valid shape?
- [ ] Are objects actually overlapping visually?
- [ ] Are you in EDITING mode (not PLAYING)?
- [ ] Did you override `onCollision()` method?
- [ ] Are you using `onTrigger()` for triggers (not `onCollision()`)?
- [ ] Is the Script file saved before testing?
- [ ] Are the alerts being called? (Check console)
