package com.ignis.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Content;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * NoteTools - Ferramentas de gerenciamento do Wiki e Notas do IgnisEngine expostas ao MCP.
 * Grava e le notas do diretorio project/notes/ no formato JSON nativo.
 */
public final class NoteTools {

    private NoteTools() {}

    /**
     * Registra as ferramentas de notas diretamente no servidor MCP.
     *
     * @param server Servidor MCP sincronizado.
     * @param projectFolder Pasta raiz do projeto ativo.
     */
    public static void register(McpSyncServer server, File projectFolder) {
        final File notesFolder = new File(projectFolder, "notes");

        if (!notesFolder.exists()) {
            notesFolder.mkdirs();
        }

        // --- 1. Tool: list_wiki_notes ---
        Tool listWikiNotes = Tool.builder()
            .name("list_wiki_notes")
            .description("Lista todas as paginas de anotacoes e wiki existentes no projeto")
            .inputSchema(Map.of("type", (Object) "object"))
            .build();
        server.addTool(new SyncToolSpecification(listWikiNotes, (exchange, args) -> {
            try {
                File[] files = notesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                List<Map<String, String>> notesList = new ArrayList<>();
                if (files != null) {
                    for (File file : files) {
                        try {
                            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                            JSONObject json = new JSONObject(raw);
                            Map<String, String> item = new HashMap<>();
                            item.put("fileName", file.getName());
                            item.put("title", json.optString("title", "Sem titulo"));
                            notesList.add(item);
                        } catch (Exception ignored) {}
                    }
                }
                return new CallToolResult(List.<Content>of(new TextContent("Notas wiki encontradas: " + notesList.toString())), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao listar notas: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 2. Tool: create_wiki_note ---
        Tool createWikiNote = Tool.builder()
            .name("create_wiki_note")
            .description("Cria uma nova pagina de nota de wiki em formato JSON no projeto")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "title", Map.of("type", "string", "description", "Titulo amigavel da pagina")
                ),
                "required", List.of("title")
            ))
            .build();
        server.addTool(new SyncToolSpecification(createWikiNote, (exchange, args) -> {
            try {
                String title = (String) args.arguments().get("title");
                if (title == null || title.trim().isEmpty()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Titulo invalido.")), true, null, null);
                }
                
                String cleanTitle = title.trim();
                String fileName = cleanTitle.replace(' ', '_').toLowerCase() + ".json";
                File file = new File(notesFolder, fileName);
                
                if (file.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("A nota '" + fileName + "' ja existe.")), true, null, null);
                }

                JSONObject json = new JSONObject();
                json.put("title", cleanTitle);
                json.put("content", "<h1>" + cleanTitle + "</h1><p>Insira suas notas de IA aqui...</p>");

                Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                return new CallToolResult(List.<Content>of(new TextContent("Nota wiki criada com sucesso: " + fileName)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao criar nota wiki: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 3. Tool: read_wiki_note ---
        Tool readWikiNote = Tool.builder()
            .name("read_wiki_note")
            .description("Le o conteudo de uma pagina de wiki especifica pelo seu nome de arquivo")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "fileName", Map.of("type", "string", "description", "Nome do arquivo JSON (ex: notas_gerais.json)")
                ),
                "required", List.of("fileName")
            ))
            .build();
        server.addTool(new SyncToolSpecification(readWikiNote, (exchange, args) -> {
            try {
                String name = (String) args.arguments().get("fileName");
                File file = new File(notesFolder, name);
                if (!file.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Nota '" + name + "' nao encontrada.")), true, null, null);
                }

                String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(raw);
                
                Map<String, String> res = new HashMap<>();
                res.put("title", json.optString("title"));
                res.put("content", json.optString("content"));

                return new CallToolResult(List.<Content>of(new TextContent("Conteudo da nota (" + name + "): " + res.toString())), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao ler nota wiki: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 4. Tool: write_wiki_note ---
        Tool writeWikiNote = Tool.builder()
            .name("write_wiki_note")
            .description("Salva e sobrescreve o titulo e conteudo de uma pagina de wiki existente")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "fileName", Map.of("type", "string", "description", "Nome do arquivo JSON da nota"),
                    "title", Map.of("type", "string", "description", "Novo titulo da pagina"),
                    "content", Map.of("type", "string", "description", "Novo conteudo HTML/Texto da nota")
                ),
                "required", List.of("fileName", "title", "content")
            ))
            .build();
        server.addTool(new SyncToolSpecification(writeWikiNote, (exchange, args) -> {
            try {
                String name = (String) args.arguments().get("fileName");
                String title = (String) args.arguments().get("title");
                String content = (String) args.arguments().get("content");

                File file = new File(notesFolder, name);
                if (!file.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Nota nao existe para ser atualizada. Crie-a primeiro.")), true, null, null);
                }

                JSONObject json = new JSONObject();
                json.put("title", title);
                json.put("content", content);

                Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                return new CallToolResult(List.<Content>of(new TextContent("Nota wiki '" + name + "' salva com sucesso.")), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao salvar nota wiki: " + e.getMessage())), true, null, null);
            }
        }));
    }
}
