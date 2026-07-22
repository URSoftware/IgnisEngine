package com.ignis.mcp;

import com.ignis.core.Game;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate de coordenacao multi-agente do {@link IgnisToolRegistry}.
 *
 * <p>O caminho de CONFLITO e testavel headless porque o gate barra a chamada
 * <b>antes</b> de despachar para a FX thread — que e exatamente a garantia que
 * interessa: a escrita conflitante nunca chega a tocar a cena. Os caminhos de
 * liberacao sao verificados direto em {@code coordConflict} (chamar {@code call()}
 * exigiria o toolkit JavaFX).</p>
 */
class McpCoordinationGateTest {

    @TempDir
    File projectFolder;

    private IgnisToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new IgnisToolRegistry(projectFolder);
        Game game = new Game();
        game.setSize(320, 180);
        registry.attachLiveEditor(game, () -> { }, () -> { }, () -> { }, () -> { }, null, null);
    }

    @AfterEach
    void releaseClaims() {
        // McpCoordination e singleton estatico: sem isso um claim vaza para o
        // proximo teste.
        McpCoordination.get().release("Ana", "objeto:Hero");
        McpCoordination.get().release("Ana", "script:PlayerController");
    }

    @Test
    void blocksSceneWriteOnObjectClaimedByAnotherAgent() throws Exception {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        String result = registry.call("set_object_transform",
                new JSONObject().put("name", "Hero").put("x", "50").put("agent", "Bruno"));

        assertTrue(result.startsWith("CONFLITO"), result);
        assertTrue(result.contains("Ana"), "deve dizer quem segura o recurso: " + result);
    }

    @Test
    void claimOwnerIsNotBlocked() {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        assertNull(registry.coordConflict("set_object_transform",
                new JSONObject().put("name", "Hero"), "Ana"));
    }

    @Test
    void otherObjectsStayFreeWhileOneIsClaimed() {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        // Claim e por recurso, nao global: Bruno continua livre no resto da cena.
        assertNull(registry.coordConflict("set_object_transform",
                new JSONObject().put("name", "Vilao"), "Bruno"));
    }

    @Test
    void anonymousCallsAreNotCoordinated() {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        // Retrocompatibilidade: cliente que nao manda 'agent' passa como antes.
        assertNull(registry.coordConflict("set_object_transform",
                new JSONObject().put("name", "Hero"), ""));
    }

    @Test
    void readOnlyToolsAreNotGuarded() {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        assertNull(registry.coordConflict("get_object_info",
                new JSONObject().put("name", "Hero"), "Bruno"));
        assertNull(registry.coordConflict("list_scene_objects", new JSONObject(), "Bruno"));
    }

    @Test
    void wideDestructiveToolIsBlockedByAnyClaimOfAnotherAgent() throws Exception {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        // clear_scene nao mira um alvo: sem a regra 'wide' passaria por cima do
        // trabalho da Ana sem conflito nenhum.
        String result = registry.call("clear_scene", new JSONObject().put("agent", "Bruno"));

        assertTrue(result.startsWith("CONFLITO"), result);
        assertTrue(result.contains("objeto:Hero"), result);
    }

    @Test
    void wideDestructiveToolIsAllowedForTheSoleClaimOwner() {
        McpCoordination.get().claim("Ana", "objeto:Hero");

        assertNull(registry.coordConflict("clear_scene", new JSONObject(), "Ana"));
    }

    @Test
    void scriptClaimStillGuardsWriteScript() {
        McpCoordination.get().claim("Ana", "script:PlayerController");

        String conflict = registry.coordConflict("write_script",
                new JSONObject().put("scriptName", "PlayerController"), "Bruno");

        assertNotNull(conflict);
        assertTrue(conflict.contains("script:PlayerController"), conflict);
    }

    @Test
    void guardedToolsDeclareTheAgentParameterInTheirSchema() {
        JSONObject schema = registry.get("set_object_transform").inputSchema;
        assertNotNull(schema.getJSONObject("properties").optJSONObject("agent"),
                "ferramenta guardada deve anunciar 'agent' no catalogo /mcp/tools");

        // objectSchema() nao traz 'properties'; a injecao precisa cria-lo.
        JSONObject wideSchema = registry.get("stop_camera_follow").inputSchema;
        assertNotNull(wideSchema.getJSONObject("properties").optJSONObject("agent"));
    }

    /** Ultimo numero de sequencia do mural (o mural e um singleton compartilhado). */
    private static long lastSeq(McpCoordination coord) {
        String board = coord.readMessages("Bruno", 0);
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("lastSeq=(\\d+)").matcher(board);
        assertTrue(matcher.find(), board);
        return Long.parseLong(matcher.group(1));
    }

    @Test
    void blankMessagesAreRejectedInsteadOfPostedToTheBoard() {
        // Uma mensagem vazia no mural nao e inofensiva: o outro agente a le como
        // instrucao perdida e fica sem saber se houve falha de transporte.
        McpCoordination coord = McpCoordination.get();
        long before = lastSeq(coord);

        assertTrue(coord.sendMessage("Ana", "Bruno", "   ").startsWith("Erro"));
        assertTrue(coord.sendMessage("Ana", "Bruno", "").startsWith("Erro"));
        assertTrue(coord.sendMessage("Ana", "Bruno", null).startsWith("Erro"));

        // Comparar o seq (e nao procurar texto) isola este teste do estado que os
        // outros deixam no singleton.
        assertEquals(before, lastSeq(coord),
                "mensagem em branco nao pode consumir um seq do mural");
    }

    @Test
    void realMessagesStillGoThrough() {
        McpCoordination coord = McpCoordination.get();

        assertTrue(coord.sendMessage("Ana", "Bruno", "Hero esta comigo")
                .startsWith("Mensagem enviada"));
        assertTrue(coord.readMessages("Bruno", 0).contains("Hero esta comigo"));
    }

    @Test
    void readOnlyToolsDoNotAdvertiseAgent() {
        JSONObject schema = registry.get("list_scene_objects").inputSchema;
        JSONObject props = schema.optJSONObject("properties");
        assertTrue(props == null || props.optJSONObject("agent") == null,
                "ferramenta de leitura nao deve pedir 'agent'");
    }
}
