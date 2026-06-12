package com.ignis.animation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A named 2D sprite animation: an ordered timeline of {@link AnimationFrame}
 * keyframes with an optional loop flag. Pure model (no Swing, no engine core
 * dependency) so it can be reused by the editor, the runtime and future tools.
 */
public class SpriteAnimation {

    private String name;
    private boolean loop = true;
    private final List<AnimationFrame> frames = new ArrayList<>();

    public SpriteAnimation() {
        this.name = "animation";
    }

    public SpriteAnimation(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public List<AnimationFrame> getFrames() {
        return frames;
    }

    public void addFrame(AnimationFrame frame) {
        frames.add(frame);
    }

    /** Sum of all frame durations in seconds. */
    public double totalDuration() {
        double total = 0;
        for (AnimationFrame frame : frames) {
            total += frame.getDuration();
        }
        return total;
    }

    /**
     * Resolves which sprite path should be shown at the given elapsed time.
     * Loops or clamps to the last frame depending on {@link #loop}. Returns
     * null when the animation has no frames.
     */
    public String spritePathAt(double time) {
        if (frames.isEmpty()) {
            return null;
        }
        double total = totalDuration();
        if (total <= 0) {
            return frames.get(0).getSpritePath();
        }

        double t;
        if (loop) {
            t = time % total;
            if (t < 0) {
                t += total;
            }
        } else {
            t = Math.min(time, total);
        }

        double acc = 0;
        for (AnimationFrame frame : frames) {
            acc += frame.getDuration();
            if (t < acc) {
                return frame.getSpritePath();
            }
        }
        return frames.get(frames.size() - 1).getSpritePath();
    }

    /** True when a non-looping animation has reached its end. */
    public boolean isFinished(double time) {
        return !loop && time >= totalDuration();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("loop", loop);
        JSONArray frameArray = new JSONArray();
        for (AnimationFrame frame : frames) {
            frameArray.put(frame.toJSON());
        }
        json.put("frames", frameArray);
        return json;
    }

    public static SpriteAnimation fromJSON(JSONObject json) {
        SpriteAnimation animation = new SpriteAnimation(json.optString("name", "animation"));
        animation.loop = json.optBoolean("loop", true);
        JSONArray frameArray = json.optJSONArray("frames");
        if (frameArray != null) {
            for (int i = 0; i < frameArray.length(); i++) {
                animation.frames.add(AnimationFrame.fromJSON(frameArray.getJSONObject(i)));
            }
        }
        return animation;
    }
}
