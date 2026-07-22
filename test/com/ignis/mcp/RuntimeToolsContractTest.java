package com.ignis.mcp;

import com.ignis.core.Game;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato de registro das ferramentas P0/ciclo-de-vida no {@link IgnisToolRegistry}:
 * garante que as ferramentas novas existem no catalogo (o que o {@code GET /mcp/tools}
 * serve) com schema valido e que as guardadas anunciam 'agent'. Nao sobe HTTP — cobre
 * a camada que o bridge apenas serializa; o smoke HTTP ao vivo fica para o editor.
 */
class RuntimeToolsContractTest {

    @TempDir
    File projectFolder;

    private IgnisToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new IgnisToolRegistry(projectFolder);
        Game game = new Game();
        game.setSize(320, 180);
        registry.attachLiveEditor(game, () -> { }, () -> { }, () -> { }, () -> { }, null, () -> { });
    }

    @Test
    void novasFerramentasEstaoRegistradasComSchema() {
        String[] tools = {
                // Orientacao (headless tambem).
                "how_to_create_game",
                // Ciclo de vida.
                "restart_editor", "get_editor_status",
                // Observabilidade e validacao.
                "list_runtime_objects", "get_runtime_metrics", "validate_scene",
                // Teste de runtime.
                "inject_input", "release_all_inputs", "advance_frames", "pause_game", "resume_game",
                "run_input_tape", "click_ui", "move_mouse",
                // Snapshots e UI (fechamento do P0).
                "snapshot_scene", "compare_scene_snapshot", "get_ui_tree",
                // Cutscenes (P1, passo 5).
                "create_cutscene", "list_cutscenes", "get_cutscene", "set_cutscene_duration",
                "add_cutscene_track", "add_cutscene_keyframe", "remove_cutscene_keyframe",
                "delete_cutscene", "validate_cutscene", "preview_cutscene", "run_cutscene",
                // UI persistente (P1, fatia 2a).
                "ui_attach_canvas", "ui_set_canvas_props", "ui_detach_canvas",
                "ui_create_textfield", "ui_create_checkbox", "ui_create_slider",
                "ui_set_anchor", "ui_set_style",
                // Dialogo data-driven (P1, fatia 2b).
                "create_dialog", "list_dialogs", "get_dialog", "set_dialog_node",
                "remove_dialog_node", "delete_dialog", "validate_dialog", "preview_dialog",
        };
        for (String name : tools) {
            IgnisToolRegistry.ToolDef def = registry.get(name);
            assertNotNull(def, "ferramenta ausente no catalogo: " + name);
            assertNotNull(def.description, "sem descricao: " + name);
            assertTrue(def.description.length() > 10, "descricao curta demais: " + name);
            assertEquals("object", def.inputSchema.optString("type"), name);
        }
    }

    @Test
    void ferramentasDeSimSaoGuardadasEAnunciamAgent() {
        // inject_input/advance_frames/pause/resume mexem no runtime inteiro: guardadas
        // como amplas (mesmo escopo de play_game) e portanto anunciam 'agent'.
        for (String name : new String[] {"inject_input", "advance_frames", "pause_game", "resume_game",
                "restart_editor", "run_input_tape", "run_cutscene", "add_cutscene_keyframe",
                "set_dialog_node", "create_dialog", "click_ui", "move_mouse"}) {
            JSONObject schema = registry.get(name).inputSchema;
            JSONObject props = schema.optJSONObject("properties");
            assertNotNull(props, "schema sem 'properties' apos injecao do agent: " + name);
            assertNotNull(props.optJSONObject("agent"), "ferramenta guardada deve anunciar 'agent': " + name);
        }
    }

    @Test
    void ferramentasReadOnlyNaoPedemAgent() {
        for (String name : new String[] {"list_runtime_objects", "get_runtime_metrics", "validate_scene",
                "get_editor_status", "how_to_create_game", "get_ui_tree", "snapshot_scene",
                "compare_scene_snapshot", "list_cutscenes", "preview_cutscene", "validate_cutscene",
                "list_dialogs", "get_dialog", "validate_dialog", "preview_dialog"}) {
            JSONObject props = registry.get(name).inputSchema.optJSONObject("properties");
            assertTrue(props == null || props.optJSONObject("agent") == null,
                    "ferramenta de leitura nao deve pedir 'agent': " + name);
        }
    }
}
