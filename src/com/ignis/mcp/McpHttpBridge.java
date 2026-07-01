package com.ignis.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * McpHttpBridge - Servidor HTTP local que expoe as ferramentas do IgnisEngine
 * por uma URL copiavel, permitindo que agentes de IA se conectem sem precisar
 * lancar o processo por STDIO.
 *
 * <p>Usa o {@code com.sun.net.httpserver.HttpServer} embutido no JDK (sem novas
 * dependencias). Endpoints (JSON):</p>
 * <ul>
 *   <li>{@code GET  /health} &rarr; {@code {"status":"ok", ...}}</li>
 *   <li>{@code GET  /mcp/tools} &rarr; {@code {"tools":[...]}} (definicoes + schema)</li>
 *   <li>{@code POST /mcp/call} corpo {@code {"name":"...","arguments":{...}}}
 *       &rarr; {@code {"ok":true,"result":"..."}}</li>
 * </ul>
 *
 * <p>Seguranca: por padrao vincula-se a {@code 127.0.0.1} (somente a maquina
 * local). Para compartilhar via VPN (ex.: Radmin VPN) pode-se vincular a
 * {@code 0.0.0.0}. Um token opcional (Bearer) protege os endpoints de execucao.
 * As ferramentas rodam na thread de UI do JavaFX via {@link IgnisToolRegistry}.</p>
 */
public final class McpHttpBridge {

    private static McpHttpBridge instance;

    private final HttpServer server;
    private final IgnisToolRegistry registry;
    private final int port;
    private final String host;
    private final String token; // pode ser null/empty (sem autenticacao)

    private McpHttpBridge(IgnisToolRegistry registry, String host, int port, String token) throws IOException {
        this.registry = registry;
        this.host = host;
        this.port = port;
        this.token = (token == null || token.isEmpty()) ? null : token;

        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/mcp/tools", this::handleTools);
        server.createContext("/mcp/call", this::handleCall);
        server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "IgnisMCP-Http");
            t.setDaemon(true);
            return t;
        }));
    }

    // ------------------------------------------------------------------
    // Ciclo de vida (singleton controlado pela UI)
    // ------------------------------------------------------------------

    /** Sobe o bridge (idempotente: reinicia se ja havia um ativo). */
    public static synchronized McpHttpBridge start(IgnisToolRegistry registry, String host, int port, String token)
            throws IOException {
        stop();
        instance = new McpHttpBridge(registry, host, port, token);
        instance.server.start();
        System.err.println("[IgnisMCP-Http] Bridge ativo em " + instance.getUrl());
        return instance;
    }

    /** Encerra o bridge caso esteja ativo. */
    public static synchronized void stop() {
        if (instance != null) {
            instance.server.stop(0);
            System.err.println("[IgnisMCP-Http] Bridge encerrado.");
            instance = null;
        }
    }

    public static synchronized boolean isRunning() {
        return instance != null;
    }

    public static synchronized McpHttpBridge current() {
        return instance;
    }

    /** URL base copiavel para colar na configuracao do agente. */
    public String getUrl() {
        String shownHost = "0.0.0.0".equals(host) ? localLanAddress() : host;
        return "http://" + shownHost + ":" + port;
    }

    public int getPort() { return port; }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private void handleHealth(HttpExchange ex) throws IOException {
        JSONObject body = new JSONObject()
                .put("status", "ok")
                .put("server", "IgnisEngine-Http-MCP")
                .put("version", "1.0.0")
                .put("tools", registry.list().size())
                .put("authRequired", token != null);
        respond(ex, 200, body);
    }

    private void handleTools(HttpExchange ex) throws IOException {
        if (!authorized(ex)) { unauthorized(ex); return; }
        JSONObject body = new JSONObject().put("tools", registry.toJsonArray());
        respond(ex, 200, body);
    }

    private void handleCall(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respond(ex, 405, new JSONObject().put("ok", false).put("error", "Use POST"));
            return;
        }
        if (!authorized(ex)) { unauthorized(ex); return; }
        try {
            String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject req = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
            String name = req.optString("name", "").trim();
            JSONObject arguments = req.optJSONObject("arguments");
            if (name.isEmpty()) {
                respond(ex, 400, new JSONObject().put("ok", false).put("error", "campo 'name' obrigatorio"));
                return;
            }
            String result = registry.call(name, arguments);
            respond(ex, 200, new JSONObject().put("ok", true).put("name", name).put("result", result));
        } catch (IllegalArgumentException iae) {
            respond(ex, 404, new JSONObject().put("ok", false).put("error", iae.getMessage()));
        } catch (Exception e) {
            respond(ex, 500, new JSONObject().put("ok", false).put("error", String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean authorized(HttpExchange ex) {
        if (token == null) return true;
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        return auth != null && auth.equals("Bearer " + token);
    }

    private void unauthorized(HttpExchange ex) throws IOException {
        respond(ex, 401, new JSONObject().put("ok", false).put("error", "token invalido ou ausente"));
    }

    private void respond(HttpExchange ex, int status, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Melhor esforco para descobrir o IP LAN/VPN da maquina (para exibir a URL de rede). */
    public static String localLanAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignore) { /* fallback */ }
        return "127.0.0.1";
    }
}
