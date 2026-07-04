package com.ignis.core;

import com.ignis.animation.AnimationIO;
import com.ignis.animation.SpriteAnimation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The AnimationComponent class implements a 2D Animation State Machine for game entities.
 * It automates switching textures of the entity's SpriteComponent based on states,
 * transitions, and parameters configured in a state machine controller JSON file.
 */
public class AnimationComponent extends IgnisScript {

    @Serialize
    private String animationController = "";

    @Serialize
    private float speed = 1.0f;

    // Runtime state maps for transition conditions
    private final transient Map<String, Boolean> booleans = new HashMap<>();
    private final transient Map<String, Float> floats = new HashMap<>();
    private final transient Map<String, Boolean> triggers = new HashMap<>();

    // Runtime engine state
    private transient String currentState = "";
    private transient double elapsedStateTime = 0.0;
    private transient String loadedControllerPath = null;
    private transient ControllerData controllerData = null;
    private final transient Map<String, SpriteAnimation> animationCache = new HashMap<>();

    /**
     * Default constructor for AnimationComponent.
     * Initializes a new instance with default speed.
     */
    public AnimationComponent() {
        super();
    }

    /**
     * Resets runtime parameters and clears the internal state and caches.
     * Called when the component is initialized or awoken.
     */
    @Override
    public void awake() {
        currentState = "";
        elapsedStateTime = 0.0;
        loadedControllerPath = null;
        controllerData = null;
        animationCache.clear();
        booleans.clear();
        floats.clear();
        triggers.clear();
    }

    /**
     * Pre-simulation hook. Triggers the loading of the controller if configured.
     */
    @Override
    public void start() {
        checkLoadController();
    }

    /**
     * Called every simulation tick.
     * Advances current state time, evaluates state transitions, and updates the SpriteComponent's texture.
     *
     * @param deltaTime Elapsed time in seconds since the last frame.
     */
    @Override
    public void update(float deltaTime) {
        if (gameObject == null) {
            return;
        }

        checkLoadController();

        if (controllerData == null) {
            return;
        }

        elapsedStateTime += deltaTime * speed;

        evaluateTransitions();

        updateSprite();
    }

    /**
     * Evaluates state transitions and updates the current state if a transition's conditions are met.
     */
    private void evaluateTransitions() {
        if (controllerData == null || currentState == null || currentState.isEmpty()) {
            return;
        }

        for (TransitionData trans : controllerData.transitions) {
            // Check if transition source matches current state or is "any"
            if (!trans.from.equalsIgnoreCase("any") && !trans.from.equals(currentState)) {
                continue;
            }

            // Check if target state exists in controller
            if (!controllerData.states.containsKey(trans.to)) {
                continue;
            }

            // Evaluate all conditions in the transition
            boolean allMet = true;
            for (ConditionData cond : trans.conditions) {
                if (!checkCondition(cond)) {
                    allMet = false;
                    break;
                }
            }

            if (allMet) {
                // Consume any triggers associated with the transition conditions
                for (ConditionData cond : trans.conditions) {
                    if (triggers.containsKey(cond.parameter)) {
                        triggers.put(cond.parameter, false);
                    }
                }

                // Change state
                currentState = trans.to;
                elapsedStateTime = 0.0;
                break; // Stop evaluating transitions this frame to prevent chain transitions
            }
        }
    }

    /**
     * Checks if a single condition is satisfied.
     *
     * @param cond The transition condition to check.
     * @return True if the condition is satisfied, false otherwise.
     */
    private boolean checkCondition(ConditionData cond) {
        String param = cond.parameter;
        String op = cond.operator.toLowerCase();

        // Evaluate boolean conditions
        if (booleans.containsKey(param)) {
            boolean val = booleans.get(param);
            if (op.equals("true") || op.equals("equal") || op.equals("==")) {
                return val == cond.boolValue;
            } else if (op.equals("false") || op.equals("notequal") || op.equals("!=")) {
                return val != cond.boolValue;
            }
        }

        // Evaluate float conditions
        if (floats.containsKey(param)) {
            float val = floats.get(param);
            switch (op) {
                case "greater":
                case "greaterthan":
                case ">":
                    return val > cond.floatValue;
                case "less":
                case "lessthan":
                case "<":
                    return val < cond.floatValue;
                case "equal":
                case "==":
                    return val == cond.floatValue;
                case "notequal":
                case "!=":
                    return val != cond.floatValue;
                case "greaterthanorequal":
                case ">=":
                    return val >= cond.floatValue;
                case "lessthanorequal":
                case "<=":
                    return val <= cond.floatValue;
            }
        }

        // Evaluate trigger conditions
        if (triggers.containsKey(param)) {
            boolean val = triggers.get(param);
            if (op.equals("triggered") || op.equals("true") || op.equals("equal") || op.equals("==")) {
                return val;
            }
        }

        return false;
    }

    /**
     * Resolves the animation frame for the current state and updates the parent SpriteComponent texture.
     */
    private void updateSprite() {
        if (controllerData == null || currentState == null || currentState.isEmpty()) {
            return;
        }

        StateData state = controllerData.states.get(currentState);
        if (state == null || state.animationPath == null || state.animationPath.isEmpty()) {
            return;
        }

        // Resolve SpriteAnimation from cache or load it
        SpriteAnimation anim = animationCache.get(state.animationPath);
        if (anim == null) {
            File animFile = AssetResolver.resolve(state.animationPath);
            if (animFile != null && animFile.exists()) {
                try {
                    anim = AnimationIO.load(animFile);
                    animationCache.put(state.animationPath, anim);
                } catch (Exception e) {
                    System.err.println("[AnimationComponent] Failed to load animation: " + state.animationPath);
                }
            }
        }

        if (anim == null) {
            return;
        }

        // Apply state-specific loop config
        anim.setLoop(state.loop);

        // Calculate time scaled by both state speed and overall component speed
        double stateTimeScaled = elapsedStateTime * state.speed;
        String spritePath = anim.spritePathAt(stateTimeScaled);

        if (spritePath == null || spritePath.isEmpty()) {
            return;
        }

        // Update SpriteComponent texture
        SpriteComponent spriteComp = gameObject.getComponent(SpriteComponent.class);
        if (spriteComp != null) {
            String currentPath = (spriteComp.getTexture() != null) ? spriteComp.getTexture().getPath() : null;
            if (!spritePath.equals(currentPath)) {
                spriteComp.setTexture(new Texture2D(spritePath));
            }
        }
    }

    /**
     * Checks if the animation controller configuration has changed, and triggers reload.
     */
    private void checkLoadController() {
        if (animationController == null || animationController.isEmpty()) {
            controllerData = null;
            loadedControllerPath = null;
            currentState = "";
            return;
        }

        if (!animationController.equals(loadedControllerPath)) {
            loadController(animationController);
        }
    }

    /**
     * Loads and parses the animation controller JSON state machine file.
     *
     * @param path Project-relative path of the state machine controller file.
     */
    private void loadController(String path) {
        loadedControllerPath = path;
        controllerData = null;
        currentState = "";
        animationCache.clear();

        File file = AssetResolver.resolve(path);
        if (file == null || !file.exists()) {
            System.err.println("[AnimationComponent] Controller file not found: " + path);
            return;
        }

        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(raw);

            ControllerData data = new ControllerData();
            data.name = json.optString("name", "Controller");

            // Parse parameters and seed default values
            if (json.has("parameters")) {
                JSONArray params = json.getJSONArray("parameters");
                for (int i = 0; i < params.length(); i++) {
                    JSONObject param = params.getJSONObject(i);
                    String pName = param.getString("name");
                    String pType = param.getString("type").toLowerCase();
                    if (pType.equals("bool") || pType.equals("boolean")) {
                        booleans.putIfAbsent(pName, param.optBoolean("default", false));
                    } else if (pType.equals("float") || pType.equals("double") || pType.equals("number")) {
                        floats.putIfAbsent(pName, (float) param.optDouble("default", 0.0));
                    } else if (pType.equals("trigger")) {
                        triggers.putIfAbsent(pName, false);
                    }
                }
            }

            // Parse states
            if (json.has("states")) {
                JSONArray states = json.getJSONArray("states");
                for (int i = 0; i < states.length(); i++) {
                    JSONObject stateJson = states.getJSONObject(i);
                    StateData state = new StateData();
                    state.name = stateJson.getString("name");
                    state.animationPath = stateJson.getString("animation");
                    state.speed = (float) stateJson.optDouble("speed", 1.0);
                    state.loop = stateJson.optBoolean("loop", true);

                    data.states.put(state.name, state);
                    if (data.defaultState == null) {
                        data.defaultState = state.name;
                    }
                }
            }

            if (json.has("defaultState")) {
                data.defaultState = json.getString("defaultState");
            }

            // Parse transitions
            if (json.has("transitions")) {
                JSONArray transitions = json.getJSONArray("transitions");
                for (int i = 0; i < transitions.length(); i++) {
                    JSONObject transJson = transitions.getJSONObject(i);
                    TransitionData trans = new TransitionData();
                    trans.from = transJson.optString("from", "any");
                    trans.to = transJson.getString("to");

                    if (transJson.has("conditions")) {
                        JSONArray conds = transJson.getJSONArray("conditions");
                        for (int j = 0; j < conds.length(); j++) {
                            JSONObject condJson = conds.getJSONObject(j);
                            ConditionData cond = new ConditionData();
                            cond.parameter = condJson.getString("parameter");
                            cond.operator = condJson.getString("operator");

                            if (condJson.has("value")) {
                                Object valObj = condJson.get("value");
                                if (valObj instanceof Boolean) {
                                    cond.boolValue = (Boolean) valObj;
                                } else if (valObj instanceof Number) {
                                    cond.floatValue = ((Number) valObj).floatValue();
                                } else {
                                    String valStr = valObj.toString();
                                    if (valStr.equalsIgnoreCase("true")) {
                                        cond.boolValue = true;
                                    } else if (valStr.equalsIgnoreCase("false")) {
                                        cond.boolValue = false;
                                    } else {
                                        try {
                                            cond.floatValue = Float.parseFloat(valStr);
                                        } catch (Exception ignore) {
                                        }
                                    }
                                }
                            }
                            trans.conditions.add(cond);
                        }
                    }
                    data.transitions.add(trans);
                }
            }

            controllerData = data;
            if (data.defaultState != null) {
                currentState = data.defaultState;
                elapsedStateTime = 0.0;
            }

        } catch (Exception e) {
            System.err.println("[AnimationComponent] Error parsing controller JSON " + path + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the path to the current animation controller.
     *
     * @return Path to the controller.
     */
    public String getAnimationController() {
        return animationController;
    }

    /**
     * Sets the path to the animation controller.
     *
     * @param animationController New controller path.
     */
    public void setAnimationController(String animationController) {
        this.animationController = animationController;
    }

    /**
     * Gets the current playback speed multiplier.
     *
     * @return Playback speed.
     */
    public float getSpeed() {
        return speed;
    }

    /**
     * Sets the playback speed multiplier.
     *
     * @param speed Playback speed factor.
     */
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    /**
     * Sets a boolean parameter.
     *
     * @param name  Parameter name.
     * @param value New boolean value.
     */
    public void setBool(String name, boolean value) {
        booleans.put(name, value);
    }

    /**
     * Gets a boolean parameter value.
     *
     * @param name Parameter name.
     * @return Value, or false if not found.
     */
    public boolean getBool(String name) {
        return booleans.getOrDefault(name, false);
    }

    /**
     * Sets a float parameter.
     *
     * @param name  Parameter name.
     * @param value New float value.
     */
    public void setFloat(String name, float value) {
        floats.put(name, value);
    }

    /**
     * Gets a float parameter value.
     *
     * @param name Parameter name.
     * @return Value, or 0.0f if not found.
     */
    public float getFloat(String name) {
        return floats.getOrDefault(name, 0.0f);
    }

    /**
     * Sets a trigger parameter to true.
     * Triggers are consumed (set back to false) when they cause a transition.
     *
     * @param name Trigger name.
     */
    public void trigger(String name) {
        triggers.put(name, true);
    }

    /**
     * Checks if a trigger is currently active.
     *
     * @param name Trigger name.
     * @return True if active, false otherwise.
     */
    public boolean getTrigger(String name) {
        return triggers.getOrDefault(name, false);
    }

    /**
     * Manually resets a trigger.
     *
     * @param name Trigger name.
     */
    public void resetTrigger(String name) {
        triggers.put(name, false);
    }

    /**
     * Gets the name of the current active state in the state machine.
     *
     * @return Current state name, or empty string if not loaded.
     */
    public String getCurrentState() {
        return currentState;
    }

    /**
     * Helper structure containing loaded animation controller configuration.
     */
    private static class ControllerData {
        String name;
        Map<String, StateData> states = new HashMap<>();
        List<TransitionData> transitions = new ArrayList<>();
        String defaultState;
    }

    /**
     * Helper structure containing state configuration in the animation controller.
     */
    private static class StateData {
        String name;
        String animationPath;
        float speed = 1.0f;
        boolean loop = true;
    }

    /**
     * Helper structure containing transition rules in the animation controller.
     */
    private static class TransitionData {
        String from;
        String to;
        List<ConditionData> conditions = new ArrayList<>();
    }

    /**
     * Helper structure containing a single condition within a transition.
     */
    private static class ConditionData {
        String parameter;
        String operator;
        float floatValue = 0.0f;
        boolean boolValue = false;
    }
}
