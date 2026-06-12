package com.ignis.animation;

import org.json.JSONObject;

/**
 * A single keyframe of a {@link SpriteAnimation}: the sprite to display and how
 * long it stays on screen. Sprite paths are stored relative to the project so
 * animations remain portable (resolved at runtime by the engine's AssetResolver).
 */
public class AnimationFrame {

    private String spritePath;
    private double duration; // seconds

    public AnimationFrame() {
        this.spritePath = "";
        this.duration = 0.1;
    }

    public AnimationFrame(String spritePath, double duration) {
        this.spritePath = spritePath;
        this.duration = Math.max(0.0001, duration);
    }

    public String getSpritePath() {
        return spritePath;
    }

    public void setSpritePath(String spritePath) {
        this.spritePath = spritePath;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = Math.max(0.0001, duration);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("sprite", spritePath);
        json.put("duration", duration);
        return json;
    }

    public static AnimationFrame fromJSON(JSONObject json) {
        return new AnimationFrame(
                json.optString("sprite", ""),
                json.optDouble("duration", 0.1));
    }
}
