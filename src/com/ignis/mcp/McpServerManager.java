package com.ignis.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignis.core.IgnisLogger;
import com.ignis.mcp.tools.*;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * McpServerManager - Gerenciador do Servidor MCP para o IgnisEngine.
 * Controla a inicializacao do StdioServerTransportProvider e o registro das ferramentas.
 *
 * <p>Desde a unificacao com o {@link IgnisToolRegistry}, o transporte STDIO serve o
 * MESMO conjunto de ferramentas base do bridge HTTP (paridade garantida por um
 * adapter generico ToolDef -&gt; SDK). As unicas ferramentas registradas fora do
 * registry sao as exclusivas deste transporte: processamento WAV ({@link AudioTools})
 * e edicao de imagem em camadas com estado em memoria ({@link ImageTools}).</p>
 */
public final class McpServerManager {

    private static McpSyncServer server;
    private static File projectFolder;

    private McpServerManager() {}

    /**
     * Inicia o servidor MCP em uma thread de segundo plano usando System.in e o
     * System.out corrente como canal do protocolo. Prefira o overload com o stream
     * explicito quando o chamador redireciona System.out (modo --mcp-server).
     *
     * @param projectDir Diretorio raiz do projeto ativo na engine.
     */
    public static void start(File projectDir) {
        start(projectDir, System.out);
    }

    /**
     * Inicia o servidor MCP em uma thread de segundo plano.
     *
     * @param projectDir  Diretorio raiz do projeto ativo na engine.
     * @param protocolOut Stream onde o JSON-RPC do MCP e escrito (o stdout REAL do
     *                    processo). Deve ser capturado ANTES de qualquer
     *                    {@code System.setOut}; sem isso o transporte era construido
     *                    sobre o stdout ja redirecionado para stderr e o cliente
     *                    nunca recebia as respostas.
     */
    public static void start(File projectDir, java.io.OutputStream protocolOut) {
        projectFolder = projectDir;
        new Thread(() -> {
            try {
                IgnisLogger.info("[IgnisMCP] Inicializando transporte Stdio com Jackson Mapper...");
                JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
                StdioServerTransportProvider transportProvider =
                        new StdioServerTransportProvider(jsonMapper, System.in, protocolOut);

                IgnisLogger.info("[IgnisMCP] Criando construtor do Sync Server...");
                server = McpServer.sync(transportProvider)
                        .serverInfo("IgnisEngine-Server", "1.0.0")
                        .capabilities(ServerCapabilities.builder()
                                .tools(true)
                                .build())
                        .build();

                IgnisLogger.info("[IgnisMCP] Registrando ferramentas do motor...");

                // Fonte canonica: todas as ferramentas base do IgnisToolRegistry
                // (mesmo conjunto do bridge HTTP; sem as de cena, que exigem editor vivo).
                IgnisToolRegistry registry = new IgnisToolRegistry(projectFolder);
                int adapted = registerRegistryTools(server, registry);
                IgnisLogger.info("[IgnisMCP] " + adapted + " ferramentas do registry adaptadas para STDIO.");

                // Exclusivas do STDIO (nao duplicam nada do registry):
                // processamento WAV e edicao de imagem em camadas (estado em memoria).
                AudioTools.register(server, projectFolder);
                ImageTools.register(server, projectFolder);

                IgnisLogger.info("[IgnisMCP] Servidor rodando com sucesso.");
            } catch (Exception e) {
                IgnisLogger.error("[IgnisMCP] Erro fatal na inicializacao do servidor: " + e.getMessage(), e);
            }
        }, "IgnisMCP-Server-Thread").start();
    }

    /**
     * Adapta cada {@link IgnisToolRegistry.ToolDef} para uma tool do SDK MCP e a
     * registra no servidor STDIO. A execucao delega a {@code registry.call(...)},
     * que ja roda o handler na thread de UI do JavaFX e converte excecoes em texto.
     *
     * @return quantidade de ferramentas registradas.
     */
    private static int registerRegistryTools(McpSyncServer server, IgnisToolRegistry registry) {
        int count = 0;
        for (IgnisToolRegistry.ToolDef def : registry.list()) {
            try {
                Tool tool = Tool.builder()
                        .name(def.name)
                        .description(def.description)
                        .inputSchema(def.inputSchema.toMap())
                        .build();
                final String toolName = def.name;
                server.addTool(new SyncToolSpecification(tool, (exchange, args) -> {
                    try {
                        JSONObject jsonArgs = (args != null && args.arguments() != null)
                                ? new JSONObject(args.arguments()) : new JSONObject();
                        String result = registry.call(toolName, jsonArgs);
                        boolean isError = result != null && result.startsWith("Erro");
                        return new CallToolResult(List.<Content>of(new TextContent(
                                result != null ? result : "(sem resultado)")), isError, null, null);
                    } catch (Exception e) {
                        return new CallToolResult(List.<Content>of(new TextContent(
                                "Erro ao executar '" + toolName + "': " + e.getMessage())), true, null, null);
                    }
                }));
                count++;
            } catch (Exception e) {
                IgnisLogger.error("[IgnisMCP] Falha ao adaptar ferramenta '" + def.name + "': " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * Finaliza a execucao do servidor MCP e encerra as conexoes ativas.
     */
    public static void stop() {
        if (server != null) {
            try {
                server.close();
                IgnisLogger.info("[IgnisMCP] Servidor encerrado com sucesso.");
            } catch (Exception e) {
                IgnisLogger.error("[IgnisMCP] Erro ao encerrar o servidor: " + e.getMessage());
            }
            server = null;
        }
    }
}
