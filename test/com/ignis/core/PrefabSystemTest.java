package com.ignis.core;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PrefabSystemTest {

    @TempDir
    Path projectFolder;

    @Test
    void setupIdeConfigForProjects() throws Exception {
        java.io.File isolatedProject = projectFolder.resolve("project").toFile();
        assertTrue(isolatedProject.mkdirs());

        IgnisProjectIO.setupIdeConfig(isolatedProject);

        assertTrue(new java.io.File(isolatedProject, "libs/ignis-engine-api.jar").exists());
        assertTrue(new java.io.File(isolatedProject, ".vscode/settings.json").exists());
        assertTrue(new java.io.File(isolatedProject, ".classpath").exists());
        assertTrue(new java.io.File(isolatedProject, ".project").exists());
        assertTrue(new java.io.File(isolatedProject, "pom.xml").exists());

        // Configuracao existente pertence ao usuario e nao pode ser truncada quando a
        // engine abre o projeto novamente.
        Path settings = isolatedProject.toPath().resolve(".vscode/settings.json");
        Files.writeString(settings, "{\"editor.formatOnSave\":true}");
        IgnisProjectIO.setupIdeConfig(isolatedProject);
        assertEquals("{\"editor.formatOnSave\":true}", Files.readString(settings));
    }

    private Path writePrefab(String name, int width, String spritePath) throws Exception {
        Path prefabs = projectFolder.resolve("prefabs");
        Files.createDirectories(prefabs);
        Path file = prefabs.resolve(name + ".prefab.json");
        Files.writeString(file, """
                {
                  "type": "GameObject",
                  "name": "%s",
                  "prefabName": "%s",
                  "spritePath": "%s",
                  "transform": { "x": 10.0, "y": 20.0, "width": %d, "height": 32, "rotation": 0.0 }
                }
                """.formatted(name, name, spritePath, width));
        return file;
    }

    private PrefabManager newManager(Game game) {
        return new PrefabManager(projectFolder.toFile(), game, null);
    }

    @Test
    void instantiatePrefabAttachesLinkAndTracksOverrides() throws Exception {
        writePrefab("Enemy", 32, "textures/orc.png");
        Game game = new Game();
        PrefabManager manager = newManager(game);
        game.setPrefabManager(manager);

        GameObject instance = manager.instantiatePrefab("Enemy");
        assertNotNull(instance);
        assertTrue(instance.isPrefabInstance());
        assertEquals("Enemy", instance.getPrefabLink().getPrefabName());

        // Alterar posicao gera override em 'x'
        instance.setX(100.0);
        assertTrue(instance.getPrefabLink().isOverridden("x"));
        assertFalse(instance.getPrefabLink().isOverridden("y"));
    }

    @Test
    void propagateChangesUpdatesInstancesPreservingOverrides() throws Exception {
        writePrefab("Hero", 32, "textures/hero.png");
        Game game = new Game();
        PrefabManager manager = newManager(game);
        game.setPrefabManager(manager);

        GameObject instance = manager.instantiatePrefab("Hero");
        game.addEntity(instance);

        // Gera override local em X
        instance.setX(99.0);

        // Atualiza o Prefab base (mudando spritePath de textures/hero.png para textures/hero_v2.png)
        writePrefab("Hero", 32, "textures/hero_v2.png");
        manager.propagateChanges("Hero");

        // X deve ser preservado (99.0), e spritePath deve ser atualizado (textures/hero_v2.png)
        assertEquals(99.0, instance.getX());
        assertEquals("textures/hero_v2.png", instance.getSpritePath());
    }

    @Test
    void applyOverridesToPrefabUpdatesBaseAndClearsOverrides() throws Exception {
        writePrefab("Box", 32, "textures/box.png");
        Game game = new Game();
        PrefabManager manager = newManager(game);
        game.setPrefabManager(manager);

        GameObject instance = manager.instantiatePrefab("Box");
        game.addEntity(instance);

        instance.setX(250.0);
        assertTrue(instance.getPrefabLink().isOverridden("x"));

        boolean ok = manager.applyOverridesToPrefab(instance);
        assertTrue(ok);
        assertFalse(instance.getPrefabLink().isOverridden("x"));

        // Instanciar novo prefab verifica que o valor 250.0 foi persistido no arquivo base
        GameObject newInst = manager.instantiatePrefab("Box");
        assertEquals(250.0, newInst.getX());
    }

    @Test
    void revertInstanceRestoresOriginalValues() throws Exception {
        writePrefab("Coin", 16, "textures/coin.png");
        Game game = new Game();
        PrefabManager manager = newManager(game);
        game.setPrefabManager(manager);

        GameObject instance = manager.instantiatePrefab("Coin");
        game.addEntity(instance);

        instance.setX(500.0);
        instance.setSpritePath("textures/gold.png");
        assertTrue(instance.getPrefabLink().isOverridden("x"));

        manager.revertInstanceToPrefab(instance);
        assertEquals(10.0, instance.getX());
        assertEquals("textures/coin.png", instance.getSpritePath());
        assertFalse(instance.getPrefabLink().isOverridden("x"));
    }

    @Test
    void unpackPrefabRemovesLink() throws Exception {
        writePrefab("Tree", 64, "textures/tree.png");
        Game game = new Game();
        PrefabManager manager = newManager(game);

        GameObject instance = manager.instantiatePrefab("Tree");
        assertTrue(instance.isPrefabInstance());

        instance.unpackPrefab();
        assertFalse(instance.isPrefabInstance());
        assertNull(instance.getPrefabLink());
    }

    @Test
    void sceneRoundtripPreservesPrefabLink() throws Exception {
        writePrefab("Rock", 32, "textures/rock.png");
        Game game = new Game();
        PrefabManager manager = newManager(game);

        GameObject instance = manager.instantiatePrefab("Rock");
        instance.setX(123.0);

        Scene scene = new Scene("TestScene");
        scene.addEntity(instance);

        JSONObject sceneJson = scene.toJSON();
        Scene loadedScene = Scene.fromJSON(sceneJson, game);

        GameObject loadedObj = loadedScene.getEntityById(instance.getId());
        assertNotNull(loadedObj);
        assertTrue(loadedObj.isPrefabInstance());
        assertEquals("Rock", loadedObj.getPrefabLink().getPrefabName());
        assertTrue(loadedObj.getPrefabLink().isOverridden("x"));
        assertEquals(123.0, loadedObj.getX());
    }
}
