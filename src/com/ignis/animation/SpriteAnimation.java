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

    public enum CurveType {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT
    }

    private String name;
    private boolean loop = true;
    private CurveType curveType = CurveType.LINEAR;
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

    public CurveType getCurveType() {
        return curveType;
    }

    public void setCurveType(CurveType curveType) {
        this.curveType = curveType;
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

        // Apply easing curve warping to progress t
        double progress = t / total;
        double warpedProgress = applyEasing(progress, curveType);
        double warpedTime = warpedProgress * total;

        double acc = 0;
        for (AnimationFrame frame : frames) {
            acc += frame.getDuration();
            if (warpedTime < acc) {
                return frame.getSpritePath();
            }
        }
        return frames.get(frames.size() - 1).getSpritePath();
    }

    private double applyEasing(double p, CurveType type) {
        if (type == null) return p;
        return switch (type) {
            case LINEAR -> p;
            case EASE_IN -> p * p;
            case EASE_OUT -> p * (2.0 - p);
            case EASE_IN_OUT -> p < 0.5 ? 2.0 * p * p : 1.0 - 2.0 * (1.0 - p) * (1.0 - p);
        };
    }

    /** True when a non-looping animation has reached its end. */
    public boolean isFinished(double time) {
        return !loop && time >= totalDuration();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("loop", loop);
        json.put("curveType", curveType.name());
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
        
        String curveStr = json.optString("curveType", CurveType.LINEAR.name());
        try {
            animation.curveType = CurveType.valueOf(curveStr);
        } catch (Exception e) {
            animation.curveType = CurveType.LINEAR;
        }

        JSONArray frameArray = json.optJSONArray("frames");
        if (frameArray != null) {
            for (int i = 0; i < frameArray.length(); i++) {
                animation.frames.add(AnimationFrame.fromJSON(frameArray.getJSONObject(i)));
            }
        }
        return animation;
    }
}
