package com.ignis.core;

import com.ignis.animation.AnimationFrame;
import com.ignis.animation.SpriteAnimation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Test class to validate the parsing, transition, and update logic of AnimationComponent.
 */
public class AnimationComponentTest {

    /**
     * Entry point to execute the AnimationComponent tests.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        IgnisLogger.info("=== Testing AnimationComponent ===");

        // Setup asset folder structure temporarily
        File projectDir = new File(".");
        AssetResolver.setProjectFolder(projectDir);

        File animDir = new File(projectDir, "assets/animations");
        if (!animDir.exists()) {
            animDir.mkdirs();
        }

        File testControllerFile = new File(animDir, "test.controller.json");
        File testIdleAnimFile = new File(animDir, "test_idle.anim.json");
        File testWalkAnimFile = new File(animDir, "test_walk.anim.json");

        try {
            // 1. Create temporary mock animations
            SpriteAnimation idleAnim = new SpriteAnimation("test_idle");
            idleAnim.setLoop(true);
            idleAnim.addFrame(new AnimationFrame("assets/sprites/test_idle_1.png", 0.5));
            idleAnim.addFrame(new AnimationFrame("assets/sprites/test_idle_2.png", 0.5));
            Files.write(testIdleAnimFile.toPath(), idleAnim.toJSON().toString(2).getBytes(StandardCharsets.UTF_8));

            SpriteAnimation walkAnim = new SpriteAnimation("test_walk");
            walkAnim.setLoop(true);
            walkAnim.addFrame(new AnimationFrame("assets/sprites/test_walk_1.png", 0.3));
            walkAnim.addFrame(new AnimationFrame("assets/sprites/test_walk_2.png", 0.3));
            Files.write(testWalkAnimFile.toPath(), walkAnim.toJSON().toString(2).getBytes(StandardCharsets.UTF_8));

            // 2. Create temporary mock controller
            JSONObject controllerJson = new JSONObject();
            controllerJson.put("name", "TestPlayerController");
            controllerJson.put("defaultState", "idle");

            JSONArray params = new JSONArray();
            params.put(new JSONObject().put("name", "speed").put("type", "float").put("default", 0.0f));
            params.put(new JSONObject().put("name", "jump").put("type", "trigger"));
            controllerJson.put("parameters", params);

            JSONArray states = new JSONArray();
            states.put(new JSONObject().put("name", "idle").put("animation", "assets/animations/test_idle.anim.json").put("speed", 1.0).put("loop", true));
            states.put(new JSONObject().put("name", "walk").put("animation", "assets/animations/test_walk.anim.json").put("speed", 1.0).put("loop", true));
            controllerJson.put("states", states);

            JSONArray transitions = new JSONArray();
            transitions.put(new JSONObject()
                    .put("from", "idle")
                    .put("to", "walk")
                    .put("conditions", new JSONArray().put(new JSONObject().put("parameter", "speed").put("operator", "greaterthan").put("value", 1.0f)))
            );
            transitions.put(new JSONObject()
                    .put("from", "walk")
                    .put("to", "idle")
                    .put("conditions", new JSONArray().put(new JSONObject().put("parameter", "speed").put("operator", "lessthanorequal").put("value", 1.0f)))
            );
            controllerJson.put("transitions", transitions);

            Files.write(testControllerFile.toPath(), controllerJson.toString(2).getBytes(StandardCharsets.UTF_8));

            // 3. Set up simulation entities
            GameObject player = new GameObject("TestPlayer", null, 0, 0, 32, 32);
            SpriteComponent sprite = new SpriteComponent();
            AnimationComponent animator = new AnimationComponent();
            
            player.addComponent(sprite);
            player.addComponent(animator);

            animator.setAnimationController("assets/animations/test.controller.json");
            animator.start(); // loads controller

            // 4. Assert initial idle state
            IgnisLogger.info("Current state (initial): " + animator.getCurrentState());
            assert "idle".equals(animator.getCurrentState()) : "Should start in idle state";

            // Update tick to resolve idle sprite
            animator.update(0.1f);
            IgnisLogger.info("Idle sprite path: " + (sprite.getTexture() != null ? sprite.getTexture().getPath() : "null"));
            assert sprite.getTexture() != null && sprite.getTexture().getPath().contains("test_idle") : "Sprite should be test_idle";

            // 5. Trigger transition to walk state by changing float parameter
            animator.setFloat("speed", 2.0f);
            animator.update(0.1f); // Evaluates transitions
            IgnisLogger.info("Current state (after speed > 1.0): " + animator.getCurrentState());
            assert "walk".equals(animator.getCurrentState()) : "Should transition to walk state";

            // Update tick to resolve walk sprite
            animator.update(0.1f);
            IgnisLogger.info("Walk sprite path: " + (sprite.getTexture() != null ? sprite.getTexture().getPath() : "null"));
            assert sprite.getTexture() != null && sprite.getTexture().getPath().contains("test_walk") : "Sprite should be test_walk";

            // 6. Transition back to idle
            animator.setFloat("speed", 0.5f);
            animator.update(0.1f);
            IgnisLogger.info("Current state (after speed <= 1.0): " + animator.getCurrentState());
            assert "idle".equals(animator.getCurrentState()) : "Should transition back to idle state";

            IgnisLogger.info("=== All tests passed successfully! ===");

        } catch (Exception e) {
            IgnisLogger.error("Test failed with exception:", e);
        } finally {
            // Clean up files
            try {
                if (testControllerFile.exists()) testControllerFile.delete();
                if (testIdleAnimFile.exists()) testIdleAnimFile.delete();
                if (testWalkAnimFile.exists()) testWalkAnimFile.delete();
            } catch (Exception ignore) {}
        }
    }
}
