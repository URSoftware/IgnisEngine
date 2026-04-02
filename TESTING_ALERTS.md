# How to Test the Alert System

## Quick Start (5 minutes)

### Step 1: Compile the Project

```bash
cd c:\Users\vinic\OneDrive\Desktop\IginisEngine-main

# Using Maven (if available)
mvn clean compile

# OR using PowerShell script
$jsonJar = "$env:USERPROFILE\.m2\repository\org\json\json\20231013\json-20231013.jar"
$files = (Get-ChildItem -Path src -Filter "*.java" -Recurse | ForEach-Object { $_.FullName })
javac -cp "$jsonJar" -d target/classes $files
```

### Step 2: Start the Editor

```bash
# Using Maven
mvn exec:java

# OR using Java directly
java -cp "target/classes:$env:USERPROFILE\.m2\repository\org\json\json\20231013\json-20231013.jar" com.ignis.editor.Editor
```

### Step 3: Create a Test Script

1. Open Editor
2. Create new project: "TestAlerts"
3. Navigate to: `projects/TestAlerts/project/scripts/`
4. Create file: `AlertTestScript.java`

```java
package com.ignis.core;

public class AlertTestScript extends IgnisScript {
    
    @Override
    public void start() {
        getGame().alert("Alert system is working!");
    }
    
    @Override
    public void update() {
        if (getInput().isKeyPressed("space")) {
            getGame().alert("Space key pressed!");
        }
        
        if (getInput().isKeyPressed("a")) {
            getGame().alert("A pressed - Testing #1");
            getGame().alert("A pressed - Testing #2");
            getGame().alert("A pressed - Testing #3");
        }
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert("Collision: " + other.getName());
    }
}
```

### Step 4: Attach Script to GameObject

1. Create a new GameObject (call it "AlarmBox")
2. Size: 100x100
3. Select it in the Hierarchy
4. In the Inspector, add Script: `AlertTestScript`

### Step 5: Test Alerts

1. Click Play button
2. Look at **top-left corner** of game view
3. You should see: "Alert system is working!"
4. Press **SPACEBAR** → See "Space key pressed!"
5. Press **A** three times → See multiple alerts stacking
6. Wait 3 seconds → See messages fade out

## Testing Collision Alerts

### Create Two GameObjects

**Object 1: AlertTestBox**
```java
public class AlertTestBox extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
        getGame().alert("Box 1 ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert(this.getGameObject().getName() + " hit " + other.getName());
    }
}
```

**Object 2: AlertTestBox2**
```java
public class AlertTestBox2 extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
        getGame().alert("Box 2 ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert(this.getGameObject().getName() + " hit " + other.getName());
    }
}
```

### Test Steps

1. Create GameObject1 (100x100) at position (200, 200)
2. Create GameObject2 (100x100) at position (400, 200)
3. Attach AlertTestBox to GameObject1
4. Attach AlertTestBox2 to GameObject2
5. Click Play
6. Watch for startup alerts
7. Drag GameObject1 to collide with GameObject2
8. Watch alerts report the collision

## Checking Alert Rendering

### Visual Checklist

- [ ] Alerts appear in **top-left corner**
- [ ] Background is **green** (#00C864)
- [ ] Text is **white**
- [ ] Font looks like **monospace** (Courier)
- [ ] Multiple alerts **stack vertically**
- [ ] Maximum 5 alerts visible at once
- [ ] Messages disappear after ~3 seconds
- [ ] Text **fades out** in last second

### Color Reference

| Component | Color |
|-----------|-------|
| Background | Green: RGB(0, 200, 100) |
| Text | White: RGB(255, 255, 255) |
| Border | None |

## Troubleshooting

### Alerts Not Showing

**Check 1: Is the game script calling alert()?**
```java
// Add this to verify script is running
@Override
public void start() {
    System.out.println("Script started!"); // Should see in console
    getGame().alert("Test message");       // Should see on screen
}
```

✅ If console prints but alert doesn't show → Editor reference issue

**Check 2: Is Editor properly initialized?**
```java
// In Editor.java, verify this runs:
game.setEditor(this);
```

**Check 3: Are you in EDITING mode?**
- Alerts only show in **EDITING** mode
- Not visible in **PLAYING** mode
- Check the mode selector in editor

### Alerts Showing But Wrong Position

The alerts should appear:
- **X**: 15 pixels from left edge
- **Y**: 35 pixels from top edge
- **Stacked**: Each new alert is 22 pixels below previous

If they're appearing elsewhere, check `renderAlerts()` method.

### Performance Issues

If frame rate drops when displaying alerts:
1. Check number of active alerts (should be ≤5)
2. Monitor Font.getMetrics() calls (can be expensive)
3. Consider caching font metrics

### Script Not Being Called

**Verify script execution:**
```java
@Override
public void update() {
    // This prints to console every frame
    System.out.println("Update called!");
}
```

If nothing prints to console:
- Script file not saved
- Script not attached to GameObject
- Script name doesn't match file name
- Compilation errors (check target/classes)

## Manual Testing Checklist

### Basic Functionality
- [ ] Alerts appear on screen
- [ ] Alerts display for 3 seconds
- [ ] Alerts fade out in last second
- [ ] Multiple alerts stack correctly
- [ ] Max 5 alerts visible at once

### Integration with Collision
- [ ] Collision callback fires
- [ ] onCollision() receives correct GameObject
- [ ] Alert displays collision object name
- [ ] Both objects report collision

### Integration with Input
- [ ] Keyboard input detected
- [ ] Alert responds immediately
- [ ] Multiple keys work

### Integration with Scripts
- [ ] start() callback fires
- [ ] update() callback fires multiple times per second
- [ ] Collision callbacks fire on actual collision
- [ ] getGame() returns valid Game reference

## Performance Testing

### Method: FPS Counter

Add to a test script:
```java
private int frameCount = 0;
private long lastSecond = 0;

@Override
public void update() {
    frameCount++;
    long now = System.currentTimeMillis();
    
    if (now - lastSecond >= 1000) {
        getGame().alert("FPS: " + frameCount);
        frameCount = 0;
        lastSecond = now;
    }
}
```

Expected: 55-60 FPS without visible slowdown from alerts

### Memory Usage

Alerts store:
- String message (~100 bytes each)
- Long timestamp (8 bytes)
- Total per alert: ~120 bytes
- Max alerts: 5
- Total memory: ~600 bytes (negligible)

## Stress Testing

### Test 1: Rapid Alerts

```java
@Override
public void update() {
    for (int i = 0; i < 10; i++) {
        getGame().alert("Test message #" + i);
    }
}
```

Expected: Last 5 added to queue, others discarded

### Test 2: Long Messages

```java
@Override
public void update() {
    String longMsg = "This is a very long alert message that contains lots of information about the game state and what is currently happening";
    getGame().alert(longMsg);
}
```

Expected: Text may wrap, box expands to fit

### Test 3: Special Characters

```java
@Override
public void update() {
    getGame().alert("❌ Error: xyz");  // May or may not display
    getGame().alert("Value: 123.45°");
    getGame().alert("Math: 5 × 10");
}
```

## Integration Testing

### With Collision System

```java
public class IntegrationTestScript extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
        getGame().alert("Integration test ready!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert("Collision: " + other.getName());
        getGame().alert("My pos: (" + (int)this.getGameObject().getX() + "," + (int)this.getGameObject().getY() + ")");
        getGame().alert("Their pos: (" + (int)other.getX() + "," + (int)other.getY() + ")");
    }
}
```

### With Input System

```java
public class InputTestScript extends IgnisScript {
    @Override
    public void update() {
        if (getInput().isKeyPressed("w")) getGame().alert("W");
        if (getInput().isKeyPressed("a")) getGame().alert("A");
        if (getInput().isKeyPressed("s")) getGame().alert("S");
        if (getInput().isKeyPressed("d")) getGame().alert("D");
    }
}
```

## Expected Results

### Successful Test Run Output
```
Console:
Script started!
Update called!
Update called!
...

Screen Alerts (Top-Left):
┌─────────────────────────────┐
│ Alert system is working!    │
│ Box 1 ready!                │
│ Box 2 ready!                │
│ Collision: AlertTestBox2    │
│ Space key pressed!          │
└─────────────────────────────┘
```

### Alert Behavior Timeline

```
t=0.0s: "Alert system working!" appears (100% opacity)
t=1.0s: "Alert system working!" at full opacity
t=1.5s: "Box 1 ready!" appears
t=2.0s: "Alert system working!" starts fading
t=2.5s: Messages still visible but fading
t=3.0s: "Alert system working!" disappears completely
t=3.5s: "Box 1 ready!" starts fading
...
```

## Conclusion

When testing is complete and all checks pass:

✅ Alert system is fully functional
✅ Collision integration works
✅ Script integration works
✅ Performance is acceptable
✅ Ready for production use

If you encounter issues, refer to the **COLLISION_AND_ALERTS_GUIDE.md** for more detailed debugging information.

Happy testing! 🎮
