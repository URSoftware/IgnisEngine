package com.ignis.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Content;
import com.ignis.animation.AnimationFrame;
import com.ignis.animation.SpriteAnimation;
import com.ignis.animation.SpriteAnimation.CurveType;
import com.ignis.animation.AnimationIO;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * AnimationTools - Ferramentas de gerenciamento do criador de animacoes/cutscenes do IgnisEngine expostas ao MCP.
 * Grava e le configuracoes de SpriteAnimation no diretorio do projeto em formato JSON (.anim.json).
 */
public final class AnimationTools {

    private AnimationTools() {}

    /**
     * Registra as ferramentas de animacao diretamente no servidor MCP.
     *
     * @param server Servidor MCP sincronizado.
     * @param projectFolder Pasta raiz do projeto ativo.
     */
    public static void register(McpSyncServer server, File projectFolder) {

        // --- 1. Tool: list_animations ---
        Tool listAnimations = Tool.builder()
            .name("list_animations")
            .description("Lista todas as definicoes de animacao (.anim.json) existentes no projeto")
            .inputSchema(Map.of("type", (Object) "object"))
            .build();
        server.addTool(new SyncToolSpecification(listAnimations, (exchange, args) -> {
            try {
                List<SpriteAnimation> anims = AnimationIO.loadAll(projectFolder);
                List<Map<String, Object>> animList = new ArrayList<>();
                for (SpriteAnimation anim : anims) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", anim.getName());
                    item.put("loop", anim.isLoop());
                    item.put("curveType", anim.getCurveType().name());
                    item.put("frameCount", anim.getFrames().size());
                    item.put("totalDuration", anim.totalDuration());
                    animList.add(item);
                }
                return new CallToolResult(List.<Content>of(new TextContent("Animacoes encontradas: " + animList.toString())), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao listar animacoes: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 2. Tool: create_animation ---
        Tool createAnimation = Tool.builder()
            .name("create_animation")
            .description("Cria um novo asset de animacao no projeto com as configuracoes iniciais")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "Nome unico da animacao (ex: player_run)"),
                    "loop", Map.of("type", "boolean", "description", "Flag para definir se a animacao deve tocar em loop"),
                    "curveType", Map.of(
                        "type", "string",
                        "enum", List.of("LINEAR", "EASE_IN", "EASE_OUT", "EASE_IN_OUT"),
                        "description", "Curva de easing para a transicao dos frames"
                    )
                ),
                "required", List.of("name")
            ))
            .build();
        server.addTool(new SyncToolSpecification(createAnimation, (exchange, args) -> {
            try {
                String name = (String) args.arguments().get("name");
                boolean loop = args.arguments().containsKey("loop") ? (Boolean) args.arguments().get("loop") : true;
                String curveStr = args.arguments().containsKey("curveType") ? (String) args.arguments().get("curveType") : "LINEAR";

                SpriteAnimation anim = new SpriteAnimation(name);
                anim.setLoop(loop);
                try {
                    anim.setCurveType(CurveType.valueOf(curveStr));
                } catch (Exception e) {
                    anim.setCurveType(CurveType.LINEAR);
                }

                AnimationIO.save(anim, projectFolder);
                return new CallToolResult(List.<Content>of(new TextContent("Animacao '" + name + "' criada com sucesso em assets/animations/.")), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao criar animacao: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 3. Tool: add_animation_frame ---
        Tool addAnimationFrame = Tool.builder()
            .name("add_animation_frame")
            .description("Adiciona um frame (caminho do sprite e duracao) a uma animacao existente")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "animName", Map.of("type", "string", "description", "Nome do arquivo da animacao sem extensao"),
                    "spritePath", Map.of("type", "string", "description", "Caminho do asset de sprite (ex: assets/sprites/hero_run_01.png)"),
                    "duration", Map.of("type", "number", "description", "Duracao da exibicao deste frame em segundos (ex: 0.1)")
                ),
                "required", List.of("animName", "spritePath", "duration")
            ))
            .build();
        server.addTool(new SyncToolSpecification(addAnimationFrame, (exchange, args) -> {
            try {
                String animName = (String) args.arguments().get("animName");
                String spritePath = (String) args.arguments().get("spritePath");
                double duration = ((Number) args.arguments().get("duration")).doubleValue();

                File folder = AnimationIO.getAnimationsFolder(projectFolder);
                String safeName = animName.trim().replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
                if (safeName.isEmpty()) safeName = "animation";
                
                File animFile = new File(folder, safeName + AnimationIO.EXTENSION);
                if (!animFile.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Animacao '" + animName + "' nao encontrada.")), true, null, null);
                }

                SpriteAnimation anim = AnimationIO.load(animFile);
                anim.addFrame(new AnimationFrame(spritePath, duration));
                AnimationIO.save(anim, projectFolder);

                return new CallToolResult(List.<Content>of(new TextContent("Frame adicionado a animacao '" + animName + "'. Novo total de frames: " + anim.getFrames().size())), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao adicionar frame: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 4. Tool: read_animation_json ---
        Tool readAnimationJson = Tool.builder()
            .name("read_animation_json")
            .description("Obtem a representacao estruturada de uma animacao pelo nome")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "animName", Map.of("type", "string", "description", "Nome da animacao a ser lida")
                ),
                "required", List.of("animName")
            ))
            .build();
        server.addTool(new SyncToolSpecification(readAnimationJson, (exchange, args) -> {
            try {
                String animName = (String) args.arguments().get("animName");
                File folder = AnimationIO.getAnimationsFolder(projectFolder);
                String safeName = animName.trim().replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
                if (safeName.isEmpty()) safeName = "animation";

                File animFile = new File(folder, safeName + AnimationIO.EXTENSION);
                if (!animFile.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Animacao nao encontrada.")), true, null, null);
                }

                String raw = new String(Files.readAllBytes(animFile.toPath()), StandardCharsets.UTF_8);
                return new CallToolResult(List.<Content>of(new TextContent(raw)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao ler animacao: " + e.getMessage())), true, null, null);
            }
        }));
    }
}
