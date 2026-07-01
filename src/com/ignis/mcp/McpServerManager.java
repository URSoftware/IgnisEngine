package com.ignis.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignis.mcp.tools.*;

import java.io.File;

/**
 * McpServerManager - Gerenciador do Servidor MCP para o IgnisEngine.
 * Controla a inicializacao do StdioServerTransportProvider e o registro das ferramentas.
 */
public final class McpServerManager {

    private static McpSyncServer server;
    private static File projectFolder;

    private McpServerManager() {}

    /**
     * Inicia o servidor MCP em uma thread de segundo plano.
     *
     * @param projectDir Diretorio raiz do projeto ativo na engine.
     */
    public static void start(File projectDir) {
        projectFolder = projectDir;
        new Thread(() -> {
            try {
                System.err.println("[IgnisMCP] Inicializando transporte Stdio com Jackson Mapper...");
                JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
                StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

                System.err.println("[IgnisMCP] Criando construtor do Sync Server...");
                server = McpServer.sync(transportProvider)
                        .serverInfo("IgnisEngine-Server", "1.0.0")
                        .capabilities(ServerCapabilities.builder()
                                .tools(true)
                                .build())
                        .build();

                System.err.println("[IgnisMCP] Registrando ferramentas do motor...");
                
                // Registra ferramentas agrupadas por categorias diretamente no servidor construido
                CoreTools.register(server, projectFolder);
                AudioTools.register(server, projectFolder);
                ImageTools.register(server, projectFolder);
                NoteTools.register(server, projectFolder);
                AnimationTools.register(server, projectFolder);

                System.err.println("[IgnisMCP] Servidor rodando com sucesso.");
            } catch (Exception e) {
                System.err.println("[IgnisMCP] Erro fatal na inicializacao do servidor: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }, "IgnisMCP-Server-Thread").start();
    }

    /**
     * Finaliza a execucao do servidor MCP e encerra as conexoes ativas.
     */
    public static void stop() {
        if (server != null) {
            try {
                server.close();
                System.err.println("[IgnisMCP] Servidor encerrado com sucesso.");
            } catch (Exception e) {
                System.err.println("[IgnisMCP] Erro ao encerrar o servidor: " + e.getMessage());
            }
            server = null;
        }
    }
}
