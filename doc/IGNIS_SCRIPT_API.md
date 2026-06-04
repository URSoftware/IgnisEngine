# IgnisScript API Reference

Complete API reference for Ignis Engine scripting system.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Lifecycle Callbacks](#lifecycle-callbacks)
3. [Transform Properties](#transform-properties)
4. [Movement Methods](#movement-methods)
5. [TransformSpace System](#transformspace-system)
6. [Input System](#input-system)
7. [Camera System](#camera-system)
8. [Audio System](#audio-system)
9. [UI System](#ui-system)
10. [Collision System](#collision-system)
11. [Object Management](#object-management)
12. [Script Control](#script-control)
13. [Utility Methods](#utility-methods)
14. [Context References](#context-references)

---

## Getting Started

All scripts must extend `IgnisScript` class.

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import com.ignis.core.GameObject;
import java.awt.event.KeyEvent;

public class MyScript extends IgnisScript {

    @Override
    public void start() {
        // Called once on game start
    }

    @Override
    public void tick() {
        // Called every frame
    }

    @Override
    public void onCollision(GameObject other) {
        // Called on collision with another object
    }
}
```

---

## Lifecycle Callbacks

### `start()`

Called once when the game starts running.

| Property | Value |
|----------|-------|
| Timing | First frame after Play is pressed |
| Calls | Once per game session |
| Use for | Initialization, finding objects, setting up state |

```java
@Override
public void start() {
    log("Game started at position: " + transform.x + ", " + transform.y);
}
```

---

### `tick()`

Called every frame while the game is running (~60 times/second).

| Property | Value |
|----------|-------|
| Timing | Every frame |
| Use for | Movement, input handling, game logic |

```java
@Override
public void tick() {
    move(Input.getHorizontalAxis() * 5, Input.getVerticalAxis() * 5);
}
```

---

### `onCollision(GameObject other)`

Called when this object collides with another.

| Parameter | Type | Description |
|-----------|------|-------------|
| `other` | `GameObject` | The object that was collided with |

```java
@Override
public void onCollision(GameObject other) {
    if (other.getName().equals("Enemy")) {
        log("Hit an enemy!");
    }
}
```

---

## Transform Properties

Access via the `transform` object, or use convenience methods.

### Transform Object Properties

| Property | Type | Description |
|----------|------|-------------|
| `transform.x` | `double` | X position in world space |
| `transform.y` | `double` | Y position in world space |
| `transform.rotation` | `double` | Rotation in degrees |
| `transform.width` | `int` | Object width in pixels |
| `transform.height` | `int` | Object height in pixels |

### Convenience Methods (Recommended)

| Method | Returns | Description |
|--------|---------|-------------|
| `getX()` | `double` | Get X position |
| `getY()` | `double` | Get Y position |
| `setPosition(x, y)` | `void` | Set position |
| `getRotation()` | `double` | Get rotation in degrees |
| `setRotation(degrees)` | `void` | Set rotation |
| `getWidth()` | `int` | Get object width |
| `getHeight()` | `int` | Get object height |
| `getOwner()` | `GameObject` | Get the GameObject this script is attached to |
| `getDeltaTime()` | `double` | Time between frames (~0.0167 for 60 FPS) |

### Utility Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `log(message)` | `void` | Print message with script name prefix |
| `println(message)` | `void` | Print message with script name prefix |
| `print(message)` | `void` | Print message without prefix |

```java
// Using convenience methods (recommended)
double x = getX();
double y = getY();
setPosition(100, 200);

// Direct transform modification (also works)
transform.x += 5;
transform.y = 100;
transform.rotation = 45;

// Delta time for frame-independent movement
double speed = 200; // pixels per second
move(speed * getDeltaTime(), 0);
```

---

## Movement Methods

### `move(double dx, double dy)`

Moves the object by a delta amount.

| Parameter | Type | Description |
|-----------|------|-------------|
| `dx` | `double` | Horizontal movement |
| `dy` | `double` | Vertical movement |

```java
move(5, 0);  // Move right 5 units
```

---

### `moveTowards(double targetX, double targetY, double speed)`

Moves towards a target position at a fixed speed.

| Parameter | Type | Description |
|-----------|------|-------------|
| `targetX` | `double` | Target X position |
| `targetY` | `double` | Target Y position |
| `speed` | `double` | Movement speed per frame |

```java
moveTowards(player.getX(), player.getY(), 3.0);
```

---

### `rotate(double degrees)`

Rotates the object by a delta amount.

| Parameter | Type | Description |
|-----------|------|-------------|
| `degrees` | `double` | Rotation amount in degrees |

```java
rotate(5);  // Rotate 5 degrees clockwise
```

---

### `lookAt(double targetX, double targetY)`

Rotates the object to face a point.

| Parameter | Type | Description |
|-----------|------|-------------|
| `targetX` | `double` | Target X position |
| `targetY` | `double` | Target Y position |

```java
lookAt(Input.getMouseX(), Input.getMouseY());
```

---

## TransformSpace System

The TransformSpace system allows movement relative to the object's orientation rather than world coordinates.

### TransformSpace Enum

| Value | Description |
|-------|-------------|
| `TransformSpace.WORLD` | Global coordinates (X = right, Y = down) |
| `TransformSpace.LOCAL` | Object-relative (X = forward based on rotation, Y = left) |
| `TransformSpace.PARENT` | Relative to parent (for future hierarchies) |

### Space-Aware Movement

| Method | Parameters | Description |
|--------|------------|-------------|
| `move(dx, dy, space)` | `double, double, TransformSpace` | Move using specified coordinate space |
| `moveForward(amount)` | `double` | Move forward (direction object is facing) |
| `moveBackward(amount)` | `double` | Move backward (opposite direction) |
| `strafeRight(amount)` | `double` | Move perpendicular right |
| `strafeLeft(amount)` | `double` | Move perpendicular left |

### Direction Vectors

| Method | Returns | Description |
|--------|---------|-------------|
| `getForwardDirection()` | `double[]` | `[dirX, dirY]` normalized forward vector |
| `getRightDirection()` | `double[]` | `[dirX, dirY]` normalized right vector |

### Example: Tank Controls

```java
public class TankController extends IgnisScript {
    private double speed = 5.0;
    private double rotationSpeed = 3.0;
    
    @Override
    public void tick() {
        // Rotate left/right
        if (Input.isLeftPressed()) {
            rotate(-rotationSpeed);
        }
        if (Input.isRightPressed()) {
            rotate(rotationSpeed);
        }
        
        // Move forward/backward in the direction we're facing
        if (Input.isUpPressed()) {
            moveForward(speed);  // Moves in facing direction
        }
        if (Input.isDownPressed()) {
            moveBackward(speed);  // Moves opposite to facing direction
        }
    }
}
```

### Example: Strafe Movement

```java
public class FPSController extends IgnisScript {
    private double speed = 4.0;
    
    @Override
    public void tick() {
        // WASD movement relative to object rotation
        if (Input.isKeyPressed(KeyEvent.VK_W)) {
            moveForward(speed);
        }
        if (Input.isKeyPressed(KeyEvent.VK_S)) {
            moveBackward(speed);
        }
        if (Input.isKeyPressed(KeyEvent.VK_A)) {
            strafeLeft(speed);
        }
        if (Input.isKeyPressed(KeyEvent.VK_D)) {
            strafeRight(speed);
        }
        
        // Look at mouse
        lookAt(Input.getMouseX(), Input.getMouseY());
    }
}
```

---

## Input System

Input can be accessed directly in scripts using convenience methods, or via the static `Input` class.

### Script Convenience Methods (Recommended)

These methods are available directly in your script without imports:

#### Keyboard

| Method | Returns | Description |
|--------|---------|-------------|
| `isKeyPressed(String keyName)` | `boolean` | True if key is currently held |
| `isKeyPressed(int keyCode)` | `boolean` | True if key is currently held (KeyEvent.VK_*) |
| `isKeyJustPressed(String keyName)` | `boolean` | True only on the frame key was pressed |
| `isKeyJustPressed(int keyCode)` | `boolean` | True only on the frame key was pressed |
| `isKeyJustReleased(String keyName)` | `boolean` | True only on the frame key was released |
| `isKeyJustReleased(int keyCode)` | `boolean` | True only on the frame key was released |

**Supported Key Names:**
- Letters: `"A"` through `"Z"`
- Numbers: `"0"` through `"9"`
- Arrows: `"UP"`, `"DOWN"`, `"LEFT"`, `"RIGHT"`
- Special: `"SPACE"`, `"ENTER"`, `"ESCAPE"`, `"ESC"`, `"SHIFT"`, `"CTRL"`, `"CONTROL"`, `"ALT"`, `"TAB"`, `"BACKSPACE"`, `"DELETE"`
- Function: `"F1"` through `"F12"`

#### Axis (Movement)

| Method | Returns | Description |
|--------|---------|-------------|
| `getHorizontalAxis()` | `int` | -1 (left), 0 (none), 1 (right) - A/D or arrows |
| `getVerticalAxis()` | `int` | -1 (up), 0 (none), 1 (down) - W/S or arrows |

#### Mouse

| Method | Returns | Description |
|--------|---------|-------------|
| `getMouseX()` | `int` | Mouse X position (screen) |
| `getMouseY()` | `int` | Mouse Y position (screen) |
| `isMouseLeftPressed()` | `boolean` | Left button held |
| `isMouseRightPressed()` | `boolean` | Right button held |

```java
// Using string-based key names (simpler, no import needed)
if (isKeyJustPressed("SPACE")) {
    log("Space pressed!");
}

if (isKeyPressed("SHIFT")) {
    speed *= 2;  // Sprint
}

// Using axis for smooth movement
int moveX = getHorizontalAxis();
int moveY = getVerticalAxis();
move(moveX * speed * getDeltaTime(), moveY * speed * getDeltaTime());
```

### Static Input Class Methods

For more advanced usage, you can use the `Input` class directly:

| Method | Returns | Description |
|--------|---------|-------------|
| `Input.isKeyPressed(int keyCode)` | `boolean` | True if key is currently held |
| `Input.isKeyJustPressed(int keyCode)` | `boolean` | True only on the frame key was pressed |
| `Input.isKeyJustReleased(int keyCode)` | `boolean` | True only on the frame key was released |
| `Input.isUpPressed()` | `boolean` | W or UP arrow |
| `Input.isDownPressed()` | `boolean` | S or DOWN arrow |
| `Input.isLeftPressed()` | `boolean` | A or LEFT arrow |
| `Input.isRightPressed()` | `boolean` | D or RIGHT arrow |
| `Input.getHorizontalAxis()` | `int` | -1 (left), 0 (none), 1 (right) |
| `Input.getVerticalAxis()` | `int` | -1 (up), 0 (none), 1 (down) |
| `Input.getMouseX()` | `int` | Mouse X position (screen) |
| `Input.getMouseY()` | `int` | Mouse Y position (screen) |
| `Input.isMouseLeftPressed()` | `boolean` | Left button held |
| `Input.isMouseRightPressed()` | `boolean` | Right button held |
| `Input.isMouseMiddlePressed()` | `boolean` | Middle button held |
| `Input.isMouseLeftJustPressed()` | `boolean` | Left button just pressed |
| `Input.isMouseRightJustPressed()` | `boolean` | Right button just pressed |

```java
import java.awt.event.KeyEvent;
import com.ignis.core.Input;

if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
    log("Space pressed!");
}

double dx = Input.getHorizontalAxis() * speed;
double dy = Input.getVerticalAxis() * speed;
move(dx, dy);
```

---

## Camera System

### Camera Access

| Method | Returns | Description |
|--------|---------|-------------|
| `getCamera()` | `Camera` | Main camera reference |
| `getCameraX()` | `double` | Camera X position |
| `getCameraY()` | `double` | Camera Y position |
| `getCameraZoom()` | `double` | Zoom level (1.0 = normal) |
| `getCameraRotation()` | `double` | Camera rotation in degrees |

### Camera Position

| Method | Parameters | Description |
|--------|------------|-------------|
| `setCameraPosition(x, y)` | `double, double` | Set camera position |
| `moveCamera(dx, dy)` | `double, double` | Move camera by delta |

### Camera Follow

| Method | Parameters | Description |
|--------|------------|-------------|
| `cameraFollowThis()` | - | Camera instantly follows this object |
| `cameraFollowThis(smoothness)` | `double` | Smooth follow (0.0-1.0, lower = smoother) |
| `cameraFollow(target)` | `GameObject` | Camera follows target object |
| `cameraFollow(target, smoothness)` | `GameObject, double` | Smooth follow target |

### Camera Zoom & Rotation

| Method | Parameters | Description |
|--------|------------|-------------|
| `setCameraZoom(zoom)` | `double` | Set zoom (0.1-10.0) |
| `setCameraRotation(degrees)` | `double` | Set rotation |

### Camera Effects

| Method | Parameters | Description |
|--------|------------|-------------|
| `cameraShake(intensity)` | `double` | Apply screen shake |

```java
// Smooth camera follow
@Override
public void tick() {
    cameraFollowThis(0.1);  // Smooth follow
    
    if (takeDamage) {
        cameraShake(5);  // Screen shake on damage
    }
}
```

---

## Audio System

### Sound Effects

| Method | Parameters | Description |
|--------|------------|-------------|
| `playSound(path)` | `String` | Play sound effect |
| `playSound(path, volume)` | `String, float` | Play with volume (0.0-1.0) |
| `playSoundWithCallback(path, callback)` | `String, Runnable` | Play with completion callback |
| `stopAllSounds()` | - | Stop all sound effects |

### Music

| Method | Parameters | Description |
|--------|------------|-------------|
| `playMusic(path)` | `String` | Play music (loops) |
| `playMusic(path, loop)` | `String, boolean` | Play music with loop option |
| `pauseMusic()` | - | Pause music |
| `resumeMusic()` | - | Resume music |
| `stopMusic()` | - | Stop music |
| `isMusicPlaying()` | - | Returns `boolean` |

### Volume Control

| Method | Parameters | Description |
|--------|------------|-------------|
| `setMasterVolume(volume)` | `float` | Set master volume (0.0-1.0) |
| `setMusicVolume(volume)` | `float` | Set music volume (0.0-1.0) |
| `setSfxVolume(volume)` | `float` | Set SFX volume (0.0-1.0) |

```java
@Override
public void start() {
    playMusic("assets/music/theme.wav");
    setMusicVolume(0.5f);
}

@Override
public void tick() {
    if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
        playSound("assets/sfx/jump.wav");
    }
}
```

---

## UI System

The Ignis Engine includes a complete UI system inspired by Unity UI, Godot Control, and Unreal UMG.

### Canvas Management

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `getUICanvas()` | - | `UICanvas` | Get or create main UI canvas |
| `setUICanvas(canvas)` | `UICanvas` | `void` | Set UI canvas |
| `createCanvas()` | - | `UICanvas` | Create new canvas and set as active |
| `createCanvas(name)` | `String` | `UICanvas` | Create named canvas |

### Creating UI Components

#### Buttons

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createButton(text, x, y)` | `String, double, double` | `UIButton` | Create button at position |
| `createButton(text, x, y, width, height)` | `String, double x4` | `UIButton` | Create sized button |
| `createButton(text, x, y, onClick)` | `String, double, double, Runnable` | `UIButton` | Create with click handler |

#### Labels

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createLabel(text, x, y)` | `String, double, double` | `UILabel` | Create text label |
| `createLabel(text, x, y, width, height)` | `String, double x4` | `UILabel` | Create sized label |

#### Panels

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createPanel(x, y, width, height)` | `double x4` | `UIPanel` | Create container panel |
| `createVerticalMenu(x, y, width, labels, onClick)` | `double, double, double, String[], Consumer<Integer>` | `UIPanel` | Create vertical button menu |

#### Progress Bars

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createProgressBar(x, y, width, height)` | `double x4` | `UIProgressBar` | Create progress bar |
| `createStatusBar(x, y, width, label, color)` | `double, double, double, String, Color` | `UIPanel` | Create labeled HP/MP bar |

#### Text Fields

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createTextField(x, y, width, height)` | `double x4` | `UITextField` | Create text input |
| `createTextField(placeholder, x, y, width, height)` | `String, double x4` | `UITextField` | Create with placeholder |

#### Sliders

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createSlider(x, y, width, height)` | `double x4` | `UISlider` | Create slider |
| `createSlider(x, y, width, height, min, max)` | `double x6` | `UISlider` | Create with range |

#### Checkboxes

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createCheckbox(label, x, y)` | `String, double, double` | `UICheckbox` | Create checkbox |
| `createCheckbox(label, x, y, checked)` | `String, double, double, boolean` | `UICheckbox` | Create with initial state |

#### Images

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createImage(path, x, y)` | `String, double, double` | `UIImage` | Create image |
| `createImage(path, x, y, width, height)` | `String, double x4` | `UIImage` | Create sized image |

#### Dialogs

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `createDialog(title, message, x, y, onOk)` | `String, String, double, double, Runnable` | `UIPanel` | Create OK dialog |

### Finding UI Components

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `findUIById(id)` | `String` | `UIComponent` | Find by unique ID |
| `findUIByName(name)` | `String` | `UIComponent` | Find by name |
| `findUIByType(type)` | `String` | `List<UIComponent>` | Find all of type |

### Managing UI Components

| Method | Parameters | Description |
|--------|------------|-------------|
| `addToUI(component)` | `UIComponent` | Add component to canvas |
| `removeUI(component)` | `UIComponent` | Remove component |
| `removeUIById(id)` | `String` | Remove by ID (returns boolean) |
| `clearUI()` | - | Remove all UI components |

### Visibility & State

| Method | Parameters | Description |
|--------|------------|-------------|
| `setUIVisible(component, visible)` | `UIComponent, boolean` | Set visibility |
| `setUIEnabled(component, enabled)` | `UIComponent, boolean` | Enable/disable |
| `showUI(component)` | `UIComponent` | Show component |
| `hideUI(component)` | `UIComponent` | Hide component |
| `isUIBlocking()` | - | Returns true if UI is blocking input |

### Styling

| Method | Parameters | Description |
|--------|------------|-------------|
| `setUIPosition(component, x, y)` | `UIComponent, double, double` | Set position |
| `setUISize(component, width, height)` | `UIComponent, double, double` | Set size |
| `setUIColors(component, bg, text, border)` | `UIComponent, Color, Color, Color` | Set colors |
| `setUIFont(component, family, size, style)` | `UIComponent, String, int, int` | Set font |

### Example: Main Menu

```java
public class MainMenu extends IgnisScript {
    private UIPanel menuPanel;
    
    @Override
    public void start() {
        createCanvas();
        
        // Create menu with buttons
        String[] options = {"Start Game", "Options", "Exit"};
        menuPanel = createVerticalMenu(100, 100, 200, options, index -> {
            switch (index) {
                case 0: startGame(); break;
                case 1: showOptions(); break;
                case 2: exitGame(); break;
            }
        });
    }
    
    private void startGame() {
        hideUI(menuPanel);
        log("Game started!");
    }
    
    private void showOptions() {
        log("Options opened");
    }
    
    private void exitGame() {
        log("Goodbye!");
    }
}
```

### Example: HUD with Health Bar

```java
public class GameHUD extends IgnisScript {
    private UIProgressBar healthBar;
    private UILabel scoreLabel;
    private int score = 0;
    private double health = 100;
    
    @Override
    public void start() {
        createCanvas();
        
        // Health bar
        healthBar = createProgressBar(20, 20, 200, 25);
        healthBar.setFillColor(Color.RED);
        healthBar.setShowText(true);
        healthBar.setTextFormat("HP: {value}%");
        
        // Score label
        scoreLabel = createLabel("Score: 0", 20, 60, 150, 30);
        scoreLabel.setFontSize(18);
    }
    
    @Override
    public void tick() {
        // Update HUD
        healthBar.setValue(health / 100.0);
        scoreLabel.setText("Score: " + score);
    }
    
    public void takeDamage(double amount) {
        health = Math.max(0, health - amount);
        cameraShake(3);
    }
    
    public void addScore(int points) {
        score += points;
    }
}
```

### Example: Options Menu with Slider

```java
public class OptionsMenu extends IgnisScript {
    private UISlider volumeSlider;
    private UICheckbox muteCheckbox;
    
    @Override
    public void start() {
        createCanvas();
        
        createLabel("Options", 100, 50, 200, 40);
        
        // Volume control
        createLabel("Volume:", 100, 100);
        volumeSlider = createSlider(100, 130, 200, 30, 0, 100);
        volumeSlider.setValue(80);
        volumeSlider.setOnValueChange(v -> {
            setMasterVolume((float)(v / 100.0));
        });
        
        // Mute checkbox
        muteCheckbox = createCheckbox("Mute Audio", 100, 180);
        muteCheckbox.setOnClick(() -> {
            if (muteCheckbox.isChecked()) {
                setMasterVolume(0f);
            } else {
                setMasterVolume((float)(volumeSlider.getValue() / 100.0));
            }
        });
        
        // Back button
        createButton("Back", 100, 250, () -> {
            clearUI();
        });
    }
}
```

---

## Collision System

### Collider Configuration

| Method | Parameters | Description |
|--------|------------|-------------|
| `setColliderType(type)` | `String` | Type: `"none"`, `"aabb"`, `"circle"`, `"polygon"` |
| `setCollisionMode(mode)` | `String` | Mode: `"collision"` or `"trigger"` |
| `setColliderEnabled(enabled)` | `boolean` | Enable/disable collider |
| `setUseCCD(use)` | `boolean` | Enable CCD for fast objects |
| `hasCollider()` | - | Returns `boolean` |
| `getCollider()` | - | Returns `Collider` |

### Collision Layers

| Method | Parameters | Description |
|--------|------------|-------------|
| `setCollisionLayer(layer)` | `int` | Set layer (0-31) |
| `setCollisionMask(mask)` | `int` | Set collision mask (-1 = all) |

### Raycasting

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `raycast(startX, startY, dirX, dirY, maxDistance)` | `double` x5 | `RaycastResult` | Cast ray from point |
| `raycastFromHere(dirX, dirY, maxDistance)` | `double` x3 | `RaycastResult` | Cast ray from object center |
| `raycastForward(maxDistance)` | `double` | `RaycastResult` | Cast ray in facing direction |

### Collision Queries

| Method | Returns | Description |
|--------|---------|-------------|
| `getCurrentCollisions()` | `List<CollisionResult>` | All collisions this frame |

```java
@Override
public void start() {
    setColliderType("aabb");
    setCollisionMode("collision");
}

@Override
public void tick() {
    // Line of sight check
    RaycastResult hit = raycastForward(200);
    if (hit.hasHit() && hit.getHitObject().getName().equals("Player")) {
        log("Player spotted!");
    }
}

@Override
public void onCollision(GameObject other) {
    if (other.getName().equals("Coin")) {
        destroy(other);
        playSound("assets/sfx/coin.wav");
    }
}
```

---

## Object Management

### Finding Objects

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `findObject(name)` | `String` | `GameObject` | Find by name (null if not found) |
| `findObjectsByType(type)` | `String` | `List<GameObject>` | Find all by type |

### Destroying Objects

| Method | Parameters | Description |
|--------|------------|-------------|
| `destroy()` | - | Destroy this object |
| `destroy(obj)` | `GameObject` | Destroy another object |

### Distance Calculation

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `distanceTo(obj)` | `GameObject` | `double` | Distance to another object |

### Collision Check

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `isColliding(obj)` | `GameObject` | `boolean` | AABB collision check |

```java
private GameObject target;

@Override
public void start() {
    target = findObject("Player");
}

@Override
public void tick() {
    if (target != null && distanceTo(target) < 50) {
        log("Player is close!");
    }
}
```

---

## Script Control

| Method/Property | Type | Description |
|-----------------|------|-------------|
| `isEnabled()` | `boolean` | Check if script is enabled |
| `setEnabled(enabled)` | `void` | Enable/disable script |
| `getScriptName()` | `String` | Get script class name |
| `getGameObject()` | `GameObject` | Get attached object |
| `getGame()` | `Game` | Get game reference |

```java
// Disable enemy AI
IgnisScript enemyScript = enemy.getScript(EnemyAI.class);
if (enemyScript != null) {
    enemyScript.setEnabled(false);
}
```

---

## Utility Methods

### Debug Logging

| Method | Parameters | Description |
|--------|------------|-------------|
| `log(message)` | `String` | Print debug message with script name prefix |

```java
log("Health: " + health);  // Output: [MyScript] Health: 100
```

---

## Context References

Available automatically in all scripts:

| Reference | Type | Description |
|-----------|------|-------------|
| `gameObject` | `GameObject` | The attached game object |
| `transform` | `Transform` | Transform component |
| `game` | `Game` | Game engine reference |

### GameObject Properties

| Property | Type | Description |
|----------|------|-------------|
| `gameObject.getName()` | `String` | Object name |
| `gameObject.getType()` | `String` | Object type (class name) |
| `gameObject.getId()` | `String` | Unique ID |
| `gameObject.isVisible()` | `boolean` | Visibility state |
| `gameObject.setVisible(boolean)` | `void` | Set visibility |
| `gameObject.getSpritePath()` | `String` | Sprite image path |
| `gameObject.setSpritePath(String)` | `void` | Set sprite image |
| `gameObject.getMusicPath()` | `MusicPath` | Audio component |
| `gameObject.setMusicPath(MusicPath)` | `void` | Set audio component |

### MusicPath Component

The `MusicPath` component can be attached to any GameObject via the Inspector or programmatically.

| Property/Method | Type | Description |
|-----------------|------|-------------|
| `getPath()` | `String` | Audio file path |
| `setPath(String)` | `void` | Set audio file path |
| `getVolume()` | `float` | Volume (0.0-1.0) |
| `setVolume(float)` | `void` | Set volume |
| `isLoop()` | `boolean` | Is looping enabled |
| `setLoop(boolean)` | `void` | Enable/disable loop |
| `isAutoPlay()` | `boolean` | Play on game start |
| `setAutoPlay(boolean)` | `void` | Enable/disable auto-play |
| `isBackgroundMusic()` | `boolean` | Is background music |
| `setBackgroundMusic(boolean)` | `void` | Set as background music |
| `play()` | `void` | Start playback |
| `stop()` | `void` | Stop playback |
| `pause()` | `void` | Pause playback |
| `resume()` | `void` | Resume playback |
| `isPlaying()` | `boolean` | Check if playing |

```java
// Access audio component from script
MusicPath music = gameObject.getMusicPath();
if (music != null) {
    music.setVolume(0.5f);
    music.play();
}
```

---

## Complete Example

```java
import com.ignis.core.*;
import com.ignis.core.ui.*;
import java.awt.Color;
import java.awt.event.KeyEvent;

public class PlayerController extends IgnisScript {

    private double speed = 5.0;
    private int health = 100;
    private int score = 0;
    private GameObject target;
    
    // UI Elements
    private UIProgressBar healthBar;
    private UILabel scoreLabel;

    @Override
    public void start() {
        log("Player spawned at " + transform.x + ", " + transform.y);
        target = findObject("Goal");
        playMusic("assets/music/game.wav");
        setColliderType("aabb");
        
        // Create HUD
        createCanvas();
        
        healthBar = createProgressBar(20, 20, 200, 25);
        healthBar.setFillColor(Color.RED);
        healthBar.setShowText(true);
        
        scoreLabel = createLabel("Score: 0", 20, 55, 150, 25);
        scoreLabel.setFontSize(16);
    }

    @Override
    public void tick() {
        // Movement with TransformSpace (WASD moves relative to facing direction)
        if (Input.isKeyPressed(KeyEvent.VK_W)) {
            moveForward(speed);
        }
        if (Input.isKeyPressed(KeyEvent.VK_S)) {
            moveBackward(speed);
        }
        if (Input.isKeyPressed(KeyEvent.VK_A)) {
            strafeLeft(speed);
        }
        if (Input.isKeyPressed(KeyEvent.VK_D)) {
            strafeRight(speed);
        }
        
        // Look at mouse
        lookAt(Input.getMouseX(), Input.getMouseY());

        // Camera follow
        cameraFollowThis(0.1);

        // Shooting
        if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            playSound("assets/sfx/shoot.wav");
        }

        // Update HUD
        healthBar.setValue(health / 100.0);
        scoreLabel.setText("Score: " + score);

        // Check win condition
        if (target != null && distanceTo(target) < 30) {
            log("You win!");
            showWinDialog();
        }
    }

    @Override
    public void onCollision(GameObject other) {
        if (other.getName().equals("Enemy")) {
            health -= 10;
            cameraShake(3);
            
            if (health <= 0) {
                log("Game Over!");
                showGameOverDialog();
            }
        } else if (other.getName().equals("Coin")) {
            score += 100;
            destroy(other);
            playSound("assets/sfx/coin.wav");
        }
    }
    
    private void showWinDialog() {
        createDialog("Victory!", "You collected all coins!", 200, 150, () -> {
            clearUI();
            // Restart logic here
        });
    }
    
    private void showGameOverDialog() {
        createDialog("Game Over", "You ran out of health!", 200, 150, () -> {
            destroy();
        });
    }
}
```

---

## Quick Reference Card

### Movement
```java
move(dx, dy)
move(dx, dy, TransformSpace.LOCAL)
moveForward(speed)
moveBackward(speed)
strafeLeft(speed)
strafeRight(speed)
moveTowards(x, y, speed)
rotate(degrees)
lookAt(x, y)
```

### Input
```java
Input.isKeyPressed(KeyEvent.VK_SPACE)
Input.isKeyJustPressed(KeyEvent.VK_SPACE)
Input.getHorizontalAxis()
Input.getVerticalAxis()
Input.getMouseX() / getMouseY()
Input.isMouseLeftPressed()
```

### Camera
```java
cameraFollowThis(0.1)
setCameraZoom(1.5)
cameraShake(5)
```

### Audio
```java
playSound("path/sound.wav")
playMusic("path/music.wav")
stopMusic()
```

### UI System
```java
createCanvas()
createButton("Click", 100, 100, () -> doSomething())
createLabel("Text", 10, 10)
createProgressBar(10, 50, 200, 25)
createSlider(10, 100, 200, 30)
createCheckbox("Option", 10, 150)
findUIByName("myButton")
hideUI(component)
clearUI()
```

### Objects
```java
findObject("Name")
destroy()
distanceTo(other)
isColliding(other)
```

### Collision
```java
setColliderType("aabb")
setCollisionMode("trigger")
raycastForward(100)
```

---

*Ignis Engine - Simple. Powerful. Creative.*
