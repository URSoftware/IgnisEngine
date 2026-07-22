package com.ignis.mcp;

import com.ignis.core.Game;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato uniforme mode/dryRun/diff das ferramentas que mutam a cena de forma
 * persistente (roadmap P0 — "operacoes recusam persistencia em Play por padrao" e
 * "toda ferramenta mutavel informa mode/dryRun/diff").
 *
 * <p>Os gates de recusa-em-Play e de dryRun rodam ANTES do dispatch para a FX thread,
 * entao sao testaveis headless (nao chegam a tocar a cena — que e exatamente a
 * garantia). O caminho de aplicacao real (com allowInPlay) e o diff ao vivo exigem o
 * toolkit JavaFX; o resumo de diff e coberto pela funcao pura {@code diffScenes}.</p>
 */
class ScenePlayGuardTest {

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

    @Test
    void sceneMutationIsRefusedDuringPlayByDefault() throws Exception {
        game.playWorld(); // EDITING -> PLAYING

        String result = registry.call("create_object",
                new JSONObject().put("name", "Hero").put("type", "square"));

        assertTrue(result.startsWith("RECUSADO"), result);
        assertTrue(result.contains("stop_game"), "deve orientar a parar o Play: " + result);
        // Nao aplicou: a cena continua vazia.
        assertTrue(game.getEntities().isEmpty(), "nenhum objeto deve ter sido criado em Play");
    }

    @Test
    void dryRunReportsWithoutApplying() throws Exception {
        String result = registry.call("create_object",
                new JSONObject().put("name", "Hero").put("type", "square").put("dryRun", true));

        assertTrue(result.startsWith("[dryRun]"), result);
        assertTrue(result.contains("modo=editing"), "deve informar o modo: " + result);
        assertTrue(game.getEntities().isEmpty(), "dryRun nao pode criar objeto");
    }

    @Test
    void mutatingToolsAdvertiseTheContractInSchema() {
        JSONObject props = registry.get("create_object").inputSchema.optJSONObject("properties");
        assertNotNull(props);
        assertNotNull(props.optJSONObject("dryRun"), "deve anunciar dryRun");
        assertNotNull(props.optJSONObject("diff"), "deve anunciar diff");
        assertNotNull(props.optJSONObject("allowInPlay"), "deve anunciar allowInPlay");
    }

    @Test
    void readOnlyToolsDoNotAdvertiseTheContract() {
        JSONObject props = registry.get("list_scene_objects").inputSchema.optJSONObject("properties");
        assertTrue(props == null || props.optJSONObject("dryRun") == null,
                "ferramenta de leitura nao deve anunciar dryRun");
    }

    @Test
    void playControlToolsAreNotTreatedAsSceneMutations() {
        // play_game/stop_game/save_project controlam o Play e nao devem ganhar o
        // contrato (senao stop_game seria recusado em Play — impossivel parar).
        for (String name : new String[] {"play_game", "stop_game", "save_project"}) {
            JSONObject props = registry.get(name).inputSchema.optJSONObject("properties");
            boolean hasDryRun = props != null && props.optJSONObject("dryRun") != null;
            assertFalse(hasDryRun, name + " nao deve receber o contrato de mutacao de cena");
        }
    }

    @Test
    void diffScenesSummarizesAddedRemovedAndChanged() {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("id-a", "Hero|0,0|64x64|z0|v");
        before.put("id-b", "Slime|10,10|32x32|z0|v");

        Map<String, String> after = new LinkedHashMap<>();
        after.put("id-a", "Hero|50,0|64x64|z0|v");   // mudou (x)
        after.put("id-c", "Coin|5,5|16x16|z1|v");    // novo

        String diff = IgnisToolRegistry.diffScenes(before, after);
        assertTrue(diff.contains("+1"), diff);
        assertTrue(diff.contains("-1"), diff);
        assertTrue(diff.contains("~1"), diff);
        assertTrue(diff.contains("Coin"), diff);   // adicionado
        assertTrue(diff.contains("Slime"), diff);  // removido
        assertTrue(diff.contains("Hero"), diff);   // alterado
    }

    @Test
    void diffScenesReportsNoChange() {
        Map<String, String> snap = new LinkedHashMap<>();
        snap.put("id-a", "Hero|0,0|64x64|z0|v");
        assertTrue(IgnisToolRegistry.diffScenes(snap, snap).contains("nenhuma mudanca"));
    }
}
