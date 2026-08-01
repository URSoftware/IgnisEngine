package com.ignis.mcp;

import com.ignis.core.Game;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHttpBridgeTest {

    @TempDir
    File projectFolder;

    @AfterEach
    void stopBridge() {
        McpHttpBridge.stop();
    }

    @Test
    void exposesToolLevelFailureAsFailedHttpPayload() throws Exception {
        int port = freePort();
        IgnisToolRegistry registry = new IgnisToolRegistry(projectFolder);
        Game game = attachHeadlessEditor(registry);
        game.playWorld();
        McpHttpBridge.start(registry, "127.0.0.1", port, null);

        JSONObject response = call(port, "set_object_visible",
                new JSONObject().put("name", "missing").put("visible", true));

        assertFalse(response.getBoolean("ok"), response.toString());
        assertEquals("set_object_visible", response.getString("name"));
        assertTrue(response.getString("error").startsWith("RECUSADO"), response.toString());
        assertFalse(response.has("result"), "falha nao pode aparecer no campo de sucesso");
    }

    @Test
    void keepsSuccessfulToolResultInSuccessPayload() throws Exception {
        int port = freePort();
        IgnisToolRegistry registry = new IgnisToolRegistry(projectFolder);
        attachHeadlessEditor(registry);
        McpHttpBridge.start(registry, "127.0.0.1", port, null);

        JSONObject response = call(port, "set_object_visible",
                new JSONObject().put("name", "missing").put("visible", true).put("dryRun", true));

        assertTrue(response.getBoolean("ok"), response.toString());
        assertTrue(response.has("result"), response.toString());
        assertFalse(response.has("error"), response.toString());
    }

    @Test
    void classifiesCoordinationAndPlayGuardsAsFailures() {
        assertTrue(IgnisToolRegistry.isFailureResult("Erro: objeto ausente"));
        assertTrue(IgnisToolRegistry.isFailureResult("  RECUSADO: editor em PLAYING"));
        assertTrue(IgnisToolRegistry.isFailureResult("CONFLITO: recurso ocupado"));
        assertFalse(IgnisToolRegistry.isFailureResult("DryRun: nenhuma mudanca"));
        assertFalse(IgnisToolRegistry.isFailureResult("OK"));
        assertFalse(IgnisToolRegistry.isFailureResult(null));
    }

    private static JSONObject call(int port, String name, JSONObject arguments) throws Exception {
        JSONObject body = new JSONObject().put("name", name).put("arguments", arguments);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp/call"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return new JSONObject(response.body());
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Game attachHeadlessEditor(IgnisToolRegistry registry) {
        Game game = new Game();
        game.setSize(320, 180);
        registry.attachLiveEditor(game, () -> { }, () -> { }, () -> { }, () -> { }, null, () -> { });
        return game;
    }
}
