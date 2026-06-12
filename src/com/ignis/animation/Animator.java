package com.ignis.animation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime animation component attached to a game entity. Holds one or more
 * named {@link SpriteAnimation}s and advances the active one over time,
 * exposing the sprite path that should currently be displayed.
 *
 * Fully decoupled from the engine core: it never imports GameObject. The owner
 * advances it each frame ({@link #update(double)}) and applies
 * {@link #getCurrentSpritePath()} to its own sprite, which keeps the animation
 * module reusable and dependency-free.
 *
 * Blending: {@link #play(String, boolean)} can wait for the current
 * (non-looping) animation to finish before switching, giving smooth transitions
 * between states without abrupt frame jumps.
 */
public class Animator {

    private final Map<String, SpriteAnimation> animations = new LinkedHashMap<>();
    private String defaultAnimation;
    private boolean autoPlay = true;

    // Runtime state (not serialized)
    private transient String currentName;
    private transient double elapsed;
    private transient boolean playing;
    private transient String queuedAnimation;

    public void addAnimation(SpriteAnimation animation) {
        animations.put(animation.getName(), animation);
        if (defaultAnimation == null) {
            defaultAnimation = animation.getName();
        }
    }

    public Map<String, SpriteAnimation> getAnimations() {
        return animations;
    }

    public SpriteAnimation getAnimation(String name) {
        return animations.get(name);
    }

    public String getDefaultAnimation() {
        return defaultAnimation;
    }

    public void setDefaultAnimation(String defaultAnimation) {
        this.defaultAnimation = defaultAnimation;
    }

    public boolean isAutoPlay() {
        return autoPlay;
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public String getCurrentName() {
        return currentName;
    }

    public boolean isPlaying() {
        return playing;
    }

    // ==================== PLAYBACK ====================

    /** Plays an animation immediately from its first frame. */
    public void play(String name) {
        play(name, false);
    }

    /**
     * Plays an animation.
     *
     * @param name           animation to play
     * @param waitForCurrent when true, if the current animation is non-looping
     *                       and still running, the new animation is queued and
     *                       starts once the current one finishes (smooth blend)
     */
    public void play(String name, boolean waitForCurrent) {
        if (!animations.containsKey(name)) {
            return;
        }
        if (waitForCurrent && playing && currentName != null) {
            SpriteAnimation current = animations.get(currentName);
            if (current != null && !current.isLoop() && !current.isFinished(elapsed)) {
                queuedAnimation = name;
                return;
            }
        }
        currentName = name;
        elapsed = 0;
        playing = true;
        queuedAnimation = null;
    }

    public void stop() {
        playing = false;
        elapsed = 0;
    }

    public void pause() {
        playing = false;
    }

    public void resume() {
        if (currentName != null) {
            playing = true;
        }
    }

    /** Resets to the initial state (called when the world simulation stops). */
    public void reset() {
        elapsed = 0;
        queuedAnimation = null;
        currentName = defaultAnimation;
        playing = autoPlay && currentName != null;
    }

    /** Advances the active animation by dt seconds. */
    public void update(double dt) {
        if (!playing || currentName == null) {
            return;
        }
        SpriteAnimation current = animations.get(currentName);
        if (current == null) {
            return;
        }
        elapsed += dt;

        if (!current.isLoop() && current.isFinished(elapsed)) {
            if (queuedAnimation != null) {
                play(queuedAnimation, false);
            } else {
                // Hold on the last frame, stop advancing
                playing = false;
            }
        }
    }

    /**
     * Sprite path to display this frame, or null when nothing should change.
     * If the animator was never started, falls back to the default animation's
     * first frame so entities show a sensible pose in the editor preview.
     */
    public String getCurrentSpritePath() {
        String name = currentName != null ? currentName : defaultAnimation;
        if (name == null) {
            return null;
        }
        SpriteAnimation animation = animations.get(name);
        return animation != null ? animation.spritePathAt(elapsed) : null;
    }

    // ==================== SERIALIZATION ====================

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("autoPlay", autoPlay);
        if (defaultAnimation != null) {
            json.put("default", defaultAnimation);
        }
        JSONArray animArray = new JSONArray();
        for (SpriteAnimation animation : animations.values()) {
            animArray.put(animation.toJSON());
        }
        json.put("animations", animArray);
        return json;
    }

    public static Animator fromJSON(JSONObject json) {
        Animator animator = new Animator();
        animator.autoPlay = json.optBoolean("autoPlay", true);
        JSONArray animArray = json.optJSONArray("animations");
        if (animArray != null) {
            for (int i = 0; i < animArray.length(); i++) {
                animator.addAnimation(SpriteAnimation.fromJSON(animArray.getJSONObject(i)));
            }
        }
        if (json.has("default")) {
            animator.defaultAnimation = json.getString("default");
        }
        // Prime runtime state so a freshly loaded animator is ready to play
        animator.reset();
        return animator;
    }
}
