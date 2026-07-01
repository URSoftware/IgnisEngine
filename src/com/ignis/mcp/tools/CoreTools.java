package com.ignis.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Content;
import com.ignis.core.ScriptManager;

import java.io.File;
import java.util.*;

/**
 * CoreTools - Ferramentas estruturais do IgnisEngine expostas ao MCP.
 * Permite listar arquivos, criar scripts e rodar compilacoes.
 */
public final class CoreTools {

    private CoreTools() {}

    /**
     * Registra as ferramentas core diretamente no servidor MCP.
     *
     * @param server Servidor MCP sincronizado.
     * @param projectFolder Pasta raiz do projeto ativo.
     */
    public static void register(McpSyncServer server, File projectFolder) {
        final ScriptManager scriptManager = new ScriptManager(projectFolder);

        // --- 1. Tool: get_project_tree ---
        Tool getProjectTree = Tool.builder()
            .name("get_project_tree")
            .description("Retorna a arvore recursiva de diretorios e arquivos do projeto atual")
            .inputSchema(Map.of("type", (Object) "object"))
            .build();
        server.addTool(new SyncToolSpecification(getProjectTree, (exchange, args) -> {
            try {
                StringBuilder sb = new StringBuilder();
                buildTreeString(projectFolder, "", sb);
                return new CallToolResult(List.<Content>of(new TextContent(sb.toString())), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao listar arvore do projeto: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 2. Tool: compile_project ---
        Tool compileProject = Tool.builder()
            .name("compile_project")
            .description("Compila todos os scripts java do projeto e retorna possiveis erros")
            .inputSchema(Map.of("type", (Object) "object"))
            .build();
        server.addTool(new SyncToolSpecification(compileProject, (exchange, args) -> {
            try {
                int compiled = scriptManager.compileAllScripts();
                return new CallToolResult(List.<Content>of(new TextContent("Compilacao concluida. Total de scripts compilados com sucesso: " + compiled)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro na compilacao: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 3. Tool: create_script ---
        Tool createScript = Tool.builder()
            .name("create_script")
            .description("Cria um novo script java utilizando o template padrao do motor")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "scriptName", Map.of("type", "string", "description", "Nome do script (ex: PlayerController)")
                ),
                "required", List.of("scriptName")
            ))
            .build();
        server.addTool(new SyncToolSpecification(createScript, (exchange, args) -> {
            try {
                String name = (String) args.arguments().get("scriptName");
                if (name == null || name.trim().isEmpty()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Nome do script invalido.")), true, null, null);
                }
                boolean success = scriptManager.createNewScript(name);
                if (success) {
                    return new CallToolResult(List.<Content>of(new TextContent("Script criado com sucesso em scripts/" + name + ".java")), false, null, null);
                } else {
                    return new CallToolResult(List.<Content>of(new TextContent("Falha ao criar o script (ele ja existe ou nome e invalido).")), true, null, null);
                }
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao criar script: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 4. Tool: write_script_content ---
        Tool writeScriptContent = Tool.builder()
            .name("write_script_content")
            .description("Sobrescreve o conteudo de codigo de um script java existente")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "scriptName", Map.of("type", "string", "description", "Nome do script (ex: PlayerController)"),
                    "code", Map.of("type", "string", "description", "Codigo fonte java completo")
                ),
                "required", List.of("scriptName", "code")
            ))
            .build();
        server.addTool(new SyncToolSpecification(writeScriptContent, (exchange, args) -> {
            try {
                String name = (String) args.arguments().get("scriptName");
                String code = (String) args.arguments().get("code");
                if (name == null || code == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Parametros insuficientes.")), true, null, null);
                }
                boolean success = scriptManager.saveScriptContent(name, code);
                if (success) {
                    return new CallToolResult(List.<Content>of(new TextContent("Script " + name + " salvo com sucesso.")), false, null, null);
                } else {
                    return new CallToolResult(List.<Content>of(new TextContent("Falha ao salvar script.")), true, null, null);
                }
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao salvar conteudo: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 5. Tool: read_script_content ---
        Tool readScriptContent = Tool.builder()
            .name("read_script_content")
            .description("Le o conteudo de codigo de um script java existente")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "scriptName", Map.of("type", "string", "description", "Nome do script (ex: PlayerController)")
                ),
                "required", List.of("scriptName")
            ))
            .build();
        server.addTool(new SyncToolSpecification(readScriptContent, (exchange, args) -> {
            try {
                String name = (String) args.arguments().get("scriptName");
                String content = scriptManager.readScriptContent(name);
                if (content != null) {
                    return new CallToolResult(List.<Content>of(new TextContent(content)), false, null, null);
                } else {
                    return new CallToolResult(List.<Content>of(new TextContent("Script nao encontrado.")), true, null, null);
                }
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao ler script: " + e.getMessage())), true, null, null);
            }
        }));
    }

    private static void buildTreeString(File file, String indent, StringBuilder sb) {
        sb.append(indent).append(file.getName());
        if (file.isDirectory()) {
            sb.append("/\n");
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareTo(b.getName());
                });
                for (File child : children) {
                    if (child.getName().equals("compiled") || child.getName().equals("target") || child.getName().equals(".git")) {
                        continue;
                    }
                    buildTreeString(child, indent + "  ", sb);
                }
            }
        } else {
            sb.append(" (").append(file.length()).append(" bytes)\n");
        }
    }
}
