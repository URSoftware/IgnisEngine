package com.ignis.mcp;

import com.ignis.core.IgnisLogger;

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
        server.createContext("/", this::handleRoot);
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
        IgnisLogger.info("[IgnisMCP-Http] Bridge ativo em " + instance.getUrl());
        return instance;
    }

    /** Encerra o bridge caso esteja ativo. */
    public static synchronized void stop() {
        if (instance != null) {
            instance.server.stop(0);
            IgnisLogger.info("[IgnisMCP-Http] Bridge encerrado.");
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
            if (IgnisToolRegistry.isFailureResult(result)) {
                respond(ex, 200, new JSONObject().put("ok", false).put("name", name).put("error", result));
            } else {
                respond(ex, 200, new JSONObject().put("ok", true).put("name", name).put("result", result));
            }
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

    private void handleRoot(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
            return;
        }
        respondHtml(ex, 200, getDashboardHtml());
    }

    private void respondHtml(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getDashboardHtml() {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <title>Console MCP - Ignis Engine</title>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
                <style>
                    :root {
                        --bg-color: #0c0f1d;
                        --panel-bg: #13182b;
                        --border-color: #1e2640;
                        --accent-primary: #8a5cf5;
                        --accent-primary-hover: #9f75ff;
                        --accent-secondary: #00f0ff;
                        --text-main: #f3f4f6;
                        --text-muted: #9ca3af;
                        --success: #10b981;
                        --error: #ef4444;
                        --warning: #f59e0b;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: 'Inter', sans-serif;
                        background-color: var(--bg-color);
                        color: var(--text-main);
                        height: 100vh;
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                    }
                    header {
                        background-color: var(--panel-bg);
                        border-bottom: 1px solid var(--border-color);
                        padding: 16px 24px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .header-title {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }
                    .header-title h1 {
                        font-size: 1.25rem;
                        font-weight: 700;
                        letter-spacing: -0.025em;
                        background: linear-gradient(135deg, var(--accent-secondary), var(--accent-primary));
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                    }
                    .status-badge {
                        background-color: rgba(16, 185, 129, 0.1);
                        color: var(--success);
                        padding: 4px 10px;
                        border-radius: 9999px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        display: flex;
                        align-items: center;
                        gap: 6px;
                        border: 1px solid rgba(16, 185, 129, 0.2);
                    }
                    .status-dot {
                        width: 8px;
                        height: 8px;
                        background-color: var(--success);
                        border-radius: 50%;
                        box-shadow: 0 0 8px var(--success);
                    }
                    .main-container {
                        display: flex;
                        flex: 1;
                        overflow: hidden;
                    }
                    .sidebar {
                        width: 320px;
                        background-color: var(--panel-bg);
                        border-right: 1px solid var(--border-color);
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                    }
                    .search-container {
                        padding: 16px;
                        border-bottom: 1px solid var(--border-color);
                    }
                    .search-input {
                        width: 100%;
                        background-color: rgba(255,255,255,0.05);
                        border: 1px solid var(--border-color);
                        border-radius: 6px;
                        padding: 10px 14px;
                        color: var(--text-main);
                        font-size: 0.875rem;
                        outline: none;
                        transition: border-color 0.2s;
                    }
                    .search-input:focus {
                        border-color: var(--accent-primary);
                    }
                    .tools-list {
                        flex: 1;
                        overflow-y: auto;
                        padding: 8px;
                    }
                    .tool-item {
                        padding: 12px;
                        border-radius: 6px;
                        cursor: pointer;
                        transition: all 0.2s;
                        display: flex;
                        flex-direction: column;
                        gap: 4px;
                        margin-bottom: 4px;
                    }
                    .tool-item:hover {
                        background-color: rgba(255, 255, 255, 0.03);
                    }
                    .tool-item.active {
                        background-color: rgba(138, 92, 245, 0.15);
                        border-left: 3px solid var(--accent-primary);
                    }
                    .tool-name {
                        font-size: 0.875rem;
                        font-weight: 600;
                        font-family: 'JetBrains Mono', monospace;
                        color: var(--text-main);
                    }
                    .tool-desc-short {
                        font-size: 0.75rem;
                        color: var(--text-muted);
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .content-area {
                        flex: 1;
                        display: flex;
                        flex-direction: column;
                        background-color: var(--bg-color);
                        overflow-y: auto;
                        padding: 24px;
                        gap: 24px;
                    }
                    .quick-actions {
                        background-color: var(--panel-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        padding: 16px;
                        display: flex;
                        flex-wrap: wrap;
                        gap: 12px;
                    }
                    .quick-action-btn {
                        background-color: rgba(255, 255, 255, 0.05);
                        border: 1px solid var(--border-color);
                        color: var(--text-main);
                        padding: 8px 16px;
                        border-radius: 6px;
                        font-size: 0.875rem;
                        font-weight: 500;
                        cursor: pointer;
                        transition: all 0.2s;
                        display: flex;
                        align-items: center;
                        gap: 8px;
                    }
                    .quick-action-btn:hover {
                        background-color: var(--accent-primary);
                        border-color: var(--accent-primary);
                    }
                    .workspace-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 24px;
                        flex: 1;
                    }
                    .card {
                        background-color: var(--panel-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        display: flex;
                        flex-direction: column;
                        overflow: hidden;
                    }
                    .card-header {
                        padding: 16px 20px;
                        border-bottom: 1px solid var(--border-color);
                        font-weight: 600;
                        font-size: 0.95rem;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .card-body {
                        padding: 20px;
                        flex: 1;
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                        overflow-y: auto;
                    }
                    .tool-meta {
                        display: flex;
                        flex-direction: column;
                        gap: 8px;
                    }
                    .tool-full-name {
                        font-family: 'JetBrains Mono', monospace;
                        font-size: 1.1rem;
                        color: var(--accent-secondary);
                    }
                    .tool-full-desc {
                        font-size: 0.875rem;
                        color: var(--text-muted);
                        line-height: 1.5;
                    }
                    .params-form {
                        display: flex;
                        flex-direction: column;
                        gap: 12px;
                    }
                    .form-group {
                        display: flex;
                        flex-direction: column;
                        gap: 6px;
                    }
                    .form-group label {
                        font-size: 0.75rem;
                        font-weight: 600;
                        color: var(--text-muted);
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                    }
                    .form-group label span {
                        color: var(--error);
                    }
                    .form-control {
                        background-color: rgba(0,0,0,0.2);
                        border: 1px solid var(--border-color);
                        border-radius: 6px;
                        padding: 10px 12px;
                        color: var(--text-main);
                        font-size: 0.875rem;
                        outline: none;
                        transition: all 0.2s;
                    }
                    .form-control:focus {
                        border-color: var(--accent-primary);
                        box-shadow: 0 0 0 2px rgba(138, 92, 245, 0.2);
                    }
                    .execute-btn {
                        background-color: var(--accent-primary);
                        color: white;
                        border: none;
                        border-radius: 6px;
                        padding: 12px;
                        font-weight: 600;
                        font-size: 0.875rem;
                        cursor: pointer;
                        transition: background-color 0.2s;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        margin-top: 12px;
                    }
                    .execute-btn:hover {
                        background-color: var(--accent-primary-hover);
                    }
                    .terminal {
                        background-color: #05070c;
                        border: 1px solid #111625;
                        border-radius: 6px;
                        padding: 16px;
                        font-family: 'JetBrains Mono', monospace;
                        font-size: 0.875rem;
                        color: #a9b2c3;
                        overflow: auto;
                        flex: 1;
                        white-space: pre-wrap;
                        box-shadow: inset 0 0 10px rgba(0,0,0,0.5);
                    }
                    .terminal-placeholder {
                        color: #4b5563;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        height: 100%;
                    }
                    footer {
                        background-color: var(--panel-bg);
                        border-top: 1px solid var(--border-color);
                        padding: 10px 24px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        font-size: 0.75rem;
                        color: var(--text-muted);
                    }
                    footer a {
                        color: var(--accent-secondary);
                        text-decoration: none;
                    }
                    footer a:hover {
                        text-decoration: underline;
                    }
                    .btn-green { background-color: var(--success); }
                    .btn-green:hover { background-color: #059669; }
                    .btn-red { background-color: var(--error); }
                    .btn-red:hover { background-color: #dc2626; }
                </style>
            </head>
            <body>
                <header>
                    <div class="header-title">
                        <h1>Ignis Engine MCP</h1>
                        <div class="status-badge">
                            <span class="status-dot"></span>
                            Bridge HTTP Ativo
                        </div>
                    </div>
                    <div style="font-size: 0.85rem; color: var(--text-muted)">
                        Servidor: <code style="color: var(--accent-secondary)">IgnisEngine-Http-MCP</code>
                    </div>
                </header>
                
                <div class="main-container">
                    <div class="sidebar">
                        <div class="search-container">
                            <input type="text" class="search-input" id="search" placeholder="Buscar ferramentas (66)...">
                        </div>
                        <div class="tools-list" id="toolsList">
                            <div style="text-align: center; padding: 20px; color: var(--text-muted)">Carregando...</div>
                        </div>
                    </div>
                    
                    <div class="content-area">
                        <div class="quick-actions">
                            <button class="quick-action-btn" onclick="quickCall('get_scene_info')">
                                Status da Cena
                            </button>
                            <button class="quick-action-btn" onclick="quickCall('list_scene_objects')">
                                Listar Objetos
                            </button>
                            <button class="quick-action-btn btn-green" onclick="quickCall('play_game')">
                                Play Game
                            </button>
                            <button class="quick-action-btn btn-red" onclick="quickCall('stop_game')">
                                Stop Game
                            </button>
                            <button class="quick-action-btn" onclick="quickCall('save_project')">
                                Salvar Projeto
                            </button>
                        </div>
                        
                        <div class="workspace-grid">
                            <div class="card">
                                <div class="card-header">Configuracao do Comando</div>
                                <div class="card-body" id="commandBody">
                                    <div class="terminal-placeholder">Selecione uma ferramenta na barra lateral para comecar</div>
                                </div>
                            </div>
                            
                            <div class="card">
                                <div class="card-header">
                                    Saida do Servidor
                                    <button class="quick-action-btn" style="padding: 2px 8px; font-size: 0.75rem;" onclick="clearTerminal()">Limpar</button>
                                </div>
                                <div class="card-body">
                                    <div class="terminal" id="terminal"><span class="terminal-placeholder">Aguardando execucao...</span></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                
                <footer>
                    <div>Autor: <strong>ThyagoToledo</strong></div>
                    <div>Ignis Engine 2D &copy; 2026. Todos os direitos reservados.</div>
                </footer>
            
                <script>
                    let allTools = [];
                    let activeTool = null;
            
                    // Carregar ferramentas ao iniciar
                    fetch('/mcp/tools')
                        .then(res => res.json())
                        .then(data => {
                            allTools = data.tools || [];
                            document.getElementById('search').placeholder = `Buscar ferramentas (${allTools.length})...`;
                            renderToolsList(allTools);
                        })
                        .catch(err => {
                            document.getElementById('toolsList').innerHTML = `<div style="color:var(--error); padding:20px;">Erro ao carregar: ${err.message}</div>`;
                        });
            
                    function renderToolsList(tools) {
                        const list = document.getElementById('toolsList');
                        list.innerHTML = '';
                        tools.forEach(t => {
                            const div = document.createElement('div');
                            div.className = `tool-item ${activeTool && activeTool.name === t.name ? 'active' : ''}`;
                            div.onclick = () => selectTool(t);
                            div.innerHTML = `
                                <div class="tool-name">${t.name}</div>
                                <div class="tool-desc-short">${t.description}</div>
                            `;
                            list.appendChild(div);
                        });
                    }
            
                    // Pesquisa
                    document.getElementById('search').oninput = (e) => {
                        const q = e.target.value.toLowerCase();
                        const filtered = allTools.filter(t => t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q));
                        renderToolsList(filtered);
                    };
            
                    function selectTool(tool) {
                        activeTool = tool;
                        
                        // Re-render lista para atualizar estado visual do item ativo
                        const q = document.getElementById('search').value.toLowerCase();
                        const filtered = allTools.filter(t => t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q));
                        renderToolsList(filtered);
            
                        const body = document.getElementById('commandBody');
                        body.innerHTML = `
                            <div class="tool-meta">
                                <div class="tool-full-name">${tool.name}</div>
                                <div class="tool-full-desc">${tool.description}</div>
                            </div>
                            <hr style="border: 0; border-top: 1px solid var(--border-color); margin: 8px 0;">
                            <form class="params-form" id="paramsForm" onsubmit="executeActiveTool(event)">
                                <div id="formFields" class="params-form"></div>
                                <button type="submit" class="execute-btn">
                                    Executar Comando
                                </button>
                            </form>
                        `;
            
                        const fieldsDiv = document.getElementById('formFields');
                        const schema = tool.inputSchema || {};
                        const props = schema.properties || {};
                        const required = schema.required || [];
            
                        if (Object.keys(props).length === 0) {
                            fieldsDiv.innerHTML = '<div style="color:var(--text-muted); font-size:0.875rem;">Sem parametros necessarios.</div>';
                            return;
                        }
            
                        for (const [pName, pConfig] of Object.entries(props)) {
                            const group = document.createElement('div');
                            group.className = 'form-group';
                            const isReq = required.includes(pName);
                            
                            group.innerHTML = `
                                <label>${pName}${isReq ? ' <span>*</span>' : ''}</label>
                                <input type="text" class="form-control" name="${pName}" placeholder="${pConfig.description || ''}" ${isReq ? 'required' : ''}>
                            `;
                            fieldsDiv.appendChild(group);
                        }
                    }
            
                    function executeActiveTool(e) {
                        e.preventDefault();
                        if (!activeTool) return;
            
                        const form = document.getElementById('paramsForm');
                        const formData = new FormData(form);
                        const args = {};
                        for (const [k, v] of formData.entries()) {
                            if (v.trim() !== '') args[k] = v.trim();
                        }
            
                        const term = document.getElementById('terminal');
                        term.innerHTML = '<span style="color:var(--accent-secondary)">Executando no servidor...</span>';
            
                        fetch('/mcp/call', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ name: activeTool.name, arguments: args })
                        })
                        .then(res => res.json())
                        .then(data => {
                            if (data.ok) {
                                term.innerHTML = `<span style="color:var(--success)">[SUCESSO] ${activeTool.name}</span>\n\n${escapeHtml(data.result)}`;
                            } else {
                                term.innerHTML = `<span style="color:var(--error)">[ERRO] ${data.error || 'Erro desconhecido'}</span>`;
                            }
                        })
                        .catch(err => {
                            term.innerHTML = `<span style="color:var(--error)">[FALHA DE REDE] ${err.message}</span>`;
                        });
                    }
            
                    function quickCall(toolName) {
                        const term = document.getElementById('terminal');
                        term.innerHTML = `<span style="color:var(--accent-secondary)">Chamando ${toolName}...</span>`;
                        
                        fetch('/mcp/call', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ name: toolName, arguments: {} })
                        })
                        .then(res => res.json())
                        .then(data => {
                            if (data.ok) {
                                term.innerHTML = `<span style="color:var(--success)">[SUCESSO] ${toolName}</span>\n\n${escapeHtml(data.result)}`;
                            } else {
                                term.innerHTML = `<span style="color:var(--error)">[ERRO] ${data.error || 'Erro desconhecido'}</span>`;
                            }
                        })
                        .catch(err => {
                            term.innerHTML = `<span style="color:var(--error)">[FALHA DE REDE] ${err.message}</span>`;
                        });
                    }
            
                    function clearTerminal() {
                        document.getElementById('terminal').innerHTML = '<span class="terminal-placeholder">Aguardando execucao...</span>';
                    }
            
                    function escapeHtml(text) {
                        return text
                            .replace(/&/g, "&amp;")
                            .replace(/</g, "&lt;")
                            .replace(/>/g, "&gt;")
                            .replace(/"/g, "&quot;")
                            .replace(/'/g, "&#039;");
                    }
                </script>
            </body>
            </html>
            """;
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
