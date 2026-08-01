package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.SpriteComponent;
import com.ignis.core.Square;
import com.ignis.core.TilemapObject;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regressao das ferramentas MCP de manutencao de objetos sem depender da GUI. */
class SceneObjectMaintenanceToolsTest {

    @TempDir
    File projectFolder;

    private IgnisToolRegistry registry;
    private Game game;

    @BeforeEach
    void setUp() {
        registry = new IgnisToolRegistry(projectFolder);
        game = new Game();
        game.setSize(320, 180);
        registry.attachLiveEditor(game, () -> { }, () -> { }, () -> { }, () -> { }, null, () -> { });
    }

    private String exec(String tool, JSONObject args) throws Exception {
        return registry.get(tool).handler.execute(args);
    }

    private GameObject add(String name) {
        GameObject object = new Square();
        object.setName(name);
        game.addEntity(object);
        return object;
    }

    @Test
    void renamePreservaIdentidadeComponentesEHierarquia() throws Exception {
        GameObject parent = add("Leader");
        GameObject child = add("Shadow");
        child.setParent(parent);
        SpriteComponent sprite = new SpriteComponent();
        parent.addComponent(sprite);
        String id = parent.getId();

        String result = exec("rename_object",
                new JSONObject().put("name", "Leader").put("newName", "DireWolfLeader"));

        assertTrue(result.contains("Leader -> DireWolfLeader"), result);
        assertEquals("DireWolfLeader", parent.getName());
        assertEquals(id, parent.getId());
        assertSame(parent, child.getParent());
        assertSame(sprite, parent.getComponent(SpriteComponent.class));
    }

    @Test
    void renameRecusaVazioAusenteEDuplicado() throws Exception {
        add("Ranga");
        add("DireWolfLeader");

        assertTrue(exec("rename_object", new JSONObject()
                .put("name", "Ranga").put("newName", " ")).startsWith("Erro"));
        assertTrue(exec("rename_object", new JSONObject()
                .put("name", "NaoExiste").put("newName", "Leader")).contains("nao encontrado"));
        assertTrue(exec("rename_object", new JSONObject()
                .put("name", "Ranga").put("newName", "DireWolfLeader")).contains("ja existe"));
        assertNotNull(registry.get("rename_object").inputSchema
                .getJSONObject("properties").optJSONObject("dryRun"));
    }

    @Test
    void metadadosPodemSerEditadosELimpos() throws Exception {
        GameObject object = add("Trigger");

        String result = exec("set_object_metadata", new JSONObject()
                .put("name", "Trigger").put("tag", "Encounter").put("layer", "Gameplay"));
        assertTrue(result.contains("Encounter"), result);
        assertEquals("Encounter", object.getTag());
        assertEquals("Gameplay", object.getLayer());

        exec("set_object_metadata", new JSONObject()
                .put("name", "Trigger").put("tag", "").put("layer", ""));
        assertEquals("", object.getTag());
        assertEquals("Default", object.getLayer());
        assertTrue(exec("set_object_metadata", new JSONObject().put("name", "Trigger"))
                .startsWith("Erro"));
    }

    @Test
    void inspecaoListaComponentesEDestacaDuplicatas() throws Exception {
        TilemapObject tilemap = new TilemapObject();
        tilemap.setName("Map");
        tilemap.addComponent(new SpriteComponent());
        // Simula uma cena contaminada pelo loader antigo.
        tilemap.addComponent(new com.ignis.core.TilemapRendererComponent());
        game.addEntity(tilemap);

        String result = exec("get_object_components", new JSONObject().put("name", "Map"));

        assertTrue(result.contains("TilemapRendererComponent"), result);
        assertTrue(result.contains("SpriteComponent"), result);
        assertTrue(result.contains("[DUPLICADO x2]"), result);
        assertTrue(result.contains("origem=native"), result);
    }

    @Test
    void buscaPorTagIgnoraCaixaEInfoIncluiMetadados() throws Exception {
        GameObject leader = add("DireWolfLeader");
        leader.setTag("Boss");
        leader.setLayer("Actors");

        String found = exec("find_objects_by_tag", new JSONObject().put("tag", "boss"));
        assertTrue(found.contains("DireWolfLeader"), found);
        assertTrue(found.contains("layer=Actors"), found);

        String info = exec("get_object_info", new JSONObject().put("name", "DireWolfLeader"));
        assertTrue(info.contains("id: " + leader.getId()), info);
        assertTrue(info.contains("tag: Boss"), info);
        assertTrue(info.contains("layer: Actors"), info);
        assertTrue(info.contains("componentes:"), info);
    }
}
