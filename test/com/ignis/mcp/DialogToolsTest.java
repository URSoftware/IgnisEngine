package com.ignis.mcp;

import com.ignis.core.Game;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxo de diálogo data-driven via ferramentas MCP (P1 fatia 2b), headless: autoria
 * em arquivo, validação de referências, preview percorrendo escolhas. Executa os
 * handlers direto ({@code def.handler}).
 */
class DialogToolsTest {

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

    private String exec(String tool, JSONObject args) throws Exception {
        return registry.get(tool).handler.execute(args);
    }

    @Test
    void autoriaPersisteEmArquivo() throws Exception {
        assertTrue(exec("create_dialog", new JSONObject().put("id", "intro")).contains("criado"));
        assertTrue(new File(projectFolder, "dialogs/intro.dialog.json").isFile());

        exec("set_dialog_node", new JSONObject().put("id", "intro").put("nodeId", "start")
                .put("speaker", "Rimuru").put("text", "Ola!").put("next", "fim").put("makeStart", true));
        exec("set_dialog_node", new JSONObject().put("id", "intro").put("nodeId", "fim")
                .put("text", "Tchau."));

        assertTrue(exec("list_dialogs", new JSONObject()).contains("intro"));
        assertTrue(exec("get_dialog", new JSONObject().put("id", "intro")).contains("Rimuru"));
    }

    @Test
    void idInvalidoRecusadoAntesDeTocarDisco() throws Exception {
        assertTrue(exec("create_dialog", new JSONObject().put("id", "../fora")).startsWith("Erro"));
        assertTrue(exec("create_dialog", new JSONObject().put("id", "com espaco")).startsWith("Erro"));
    }

    @Test
    void validacaoApontaReferenciaQuebrada() throws Exception {
        exec("create_dialog", new JSONObject().put("id", "d").put("start", "a"));
        exec("set_dialog_node", new JSONObject().put("id", "d").put("nodeId", "a")
                .put("text", "Oi").put("next", "fantasma"));

        String r = exec("validate_dialog", new JSONObject().put("id", "d"));
        assertTrue(r.contains("Referência quebrada"), r);
    }

    @Test
    void escolhasComoArrayJsonEValidacaoOk() throws Exception {
        exec("create_dialog", new JSONObject().put("id", "q").put("start", "start"));
        JSONArray choices = new JSONArray()
                .put(new JSONObject().put("text", "Sim").put("next", "fim").put("setFlag", "aceitou"))
                .put(new JSONObject().put("text", "Nao").put("next", "fim"));
        exec("set_dialog_node", new JSONObject().put("id", "q").put("nodeId", "start")
                .put("text", "Aceita?").put("choices", choices));
        exec("set_dialog_node", new JSONObject().put("id", "q").put("nodeId", "fim").put("text", "Ok."));

        assertTrue(exec("validate_dialog", new JSONObject().put("id", "q")).startsWith("OK"));
    }

    @Test
    void previewSegueChoicesPathEListaOpcoesSemIndice() throws Exception {
        exec("create_dialog", new JSONObject().put("id", "q").put("start", "start"));
        JSONArray choices = new JSONArray()
                .put(new JSONObject().put("text", "Ir para A").put("next", "a"))
                .put(new JSONObject().put("text", "Ir para B").put("next", "b"));
        exec("set_dialog_node", new JSONObject().put("id", "q").put("nodeId", "start")
                .put("text", "Para onde?").put("choices", choices));
        exec("set_dialog_node", new JSONObject().put("id", "q").put("nodeId", "a").put("text", "Chegou em A."));
        exec("set_dialog_node", new JSONObject().put("id", "q").put("nodeId", "b").put("text", "Chegou em B."));

        // Sem choicesPath: lista as opcoes e para no start.
        String listing = exec("preview_dialog", new JSONObject().put("id", "q"));
        assertTrue(listing.contains("escolhas disponíveis"), listing);
        assertTrue(listing.contains("[0] Ir para A"), listing);

        // Com choicesPath=[1]: segue para B.
        String followed = exec("preview_dialog", new JSONObject().put("id", "q")
                .put("choicesPath", new JSONArray().put(1)));
        assertTrue(followed.contains("Chegou em B."), followed);
        assertTrue(!followed.contains("Chegou em A."), followed);
    }

    @Test
    void removeNoEDeleteDialogo() throws Exception {
        exec("create_dialog", new JSONObject().put("id", "d"));
        exec("set_dialog_node", new JSONObject().put("id", "d").put("nodeId", "x").put("text", "X"));
        assertTrue(exec("remove_dialog_node", new JSONObject().put("id", "d").put("nodeId", "x")).contains("removido"));
        assertTrue(exec("remove_dialog_node", new JSONObject().put("id", "d").put("nodeId", "x")).startsWith("Erro"));
        assertTrue(exec("delete_dialog", new JSONObject().put("id", "d")).contains("apagado"));
        assertTrue(exec("list_dialogs", new JSONObject()).contains("nenhum"));
    }
}
