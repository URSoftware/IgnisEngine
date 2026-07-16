package com.ignis.mcp;

import com.ignis.core.IgnisLogger;
import com.ignis.animation.AnimationFrame;
import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.Camera;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisSampleCollisions;
import com.ignis.core.IgnisScript;
import com.ignis.core.IgnisSoundEngine;
import com.ignis.core.PrefabManager;
import com.ignis.core.ScriptManager;
import com.ignis.core.World;
import com.ignis.collab.CollabBridge;
import com.ignis.collab.CollabSession;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIComponent;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.ignis.core.ui.UIProgressBar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Ferramentas MCP de mundo e fisica: colliders de exemplo, info da cena, limites/grade do mundo e barreiras.
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class WorldTools {

    private final IgnisToolRegistry reg;

    WorldTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerCollisionTools();
        registerSceneInfoTools();
        registerWorldTools();
    }

    private void registerCollisionTools() {
        Map<String, String> colliderProps = new LinkedHashMap<>();
        colliderProps.put("objectName", "Nome do objeto na cena");
        colliderProps.put("colliderType", "NONE, AABB, CIRCLE ou POLYGON");
        colliderProps.put("collisionMode", "COLLISION (resposta fisica) ou TRIGGER (so eventos); padrao COLLISION");
        colliderProps.put("layer", "Camada de colisao 0-31 (opcional)");
        colliderProps.put("mask", "Mascara de colisao, bit N = colide com camada N; -1 = todas (opcional)");
        reg.add("set_object_collider",
            "Configura o collider de um GameObject: tipo, modo, camada e mascara de colisao.",
            IgnisToolRegistry.schemaWith(colliderProps, List.of("objectName", "colliderType")),
            args -> {
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                IgnisSampleCollisions.ColliderType type;
                try {
                    type = IgnisSampleCollisions.ColliderType.valueOf(args.optString("colliderType", "NONE").trim().toUpperCase());
                } catch (IllegalArgumentException iae) {
                    return "Erro: colliderType invalido (use NONE, AABB, CIRCLE ou POLYGON).";
                }
                go.setColliderType(type);
                if (args.has("collisionMode")) {
                    try {
                        go.setCollisionMode(IgnisSampleCollisions.CollisionMode.valueOf(
                                args.optString("collisionMode", "COLLISION").trim().toUpperCase()));
                    } catch (IllegalArgumentException iae) {
                        return "Erro: collisionMode invalido (use COLLISION ou TRIGGER).";
                    }
                }
                if (go.getCollider() != null) {
                    if (args.has("layer")) go.getCollider().setLayer(args.optInt("layer"));
                    if (args.has("mask")) go.getCollider().setCollisionMask(args.optInt("mask"));
                }
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Collider de " + go.getName() + " definido: " + type
                        + (args.has("collisionMode") ? " (" + args.optString("collisionMode") + ")" : "");
            });
    }

    private void registerSceneInfoTools() {
        reg.add("get_scene_info",
            "Retorna um resumo da cena ativa: total de objetos, cameras e estado do jogo (edicao/play).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera active = reg.liveGame.getActiveCamera();
                return "projeto: " + reg.projectFolder.getName()
                        + "\nestado: " + reg.liveGame.getGameState()
                        + "\nobjetos: " + reg.liveGame.getEntities().size()
                        + "\ncameras: " + reg.liveGame.getCameras().size()
                        + "\ncamera ativa: " + (active != null ? active.getCameraName() : "(nenhuma)");
            });
    }

    private void registerWorldTools() {
        // set_world_bounds
        Map<String, String> boundsProps = new LinkedHashMap<>();
        boundsProps.put("minX", "Limite esquerdo do mapa (mundo)");
        boundsProps.put("minY", "Limite superior");
        boundsProps.put("maxX", "Limite direito");
        boundsProps.put("maxY", "Limite inferior");
        reg.add("set_world_bounds",
            "Define os limites do mapa (retangulo). Objetos com world_collision e a camera ficam contidos nele.",
            IgnisToolRegistry.schemaWith(boundsProps, List.of("minX", "minY", "maxX", "maxY")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                World w = reg.liveGame.getOrCreateWorld();
                w.setBounds(args.optDouble("minX"), args.optDouble("minY"),
                            args.optDouble("maxX"), args.optDouble("maxY"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Limites do mundo: (" + (int) w.getMinX() + "," + (int) w.getMinY() + ") -> ("
                        + (int) w.getMaxX() + "," + (int) w.getMaxY() + ")";
            });

        // clear_world_bounds
        reg.add("clear_world_bounds",
            "Remove os limites do mapa (o mundo passa a ser infinito).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getWorld() != null) reg.liveGame.getWorld().clearBounds();
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Limites do mundo removidos.";
            });

        // set_world_grid
        reg.add("set_world_grid",
            "Define o tamanho (px) das celulas da grade de barreiras.",
            IgnisToolRegistry.schemaWith(Map.of("cellSize", "Tamanho da celula em px (ex: 64)"), List.of("cellSize")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                World w = reg.liveGame.getOrCreateWorld();
                w.setCellSize(args.optInt("cellSize", 64));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Tamanho da celula: " + w.getCellSize() + "px";
            });

        // block_rect
        Map<String, String> rectProps = new LinkedHashMap<>();
        rectProps.put("x", "X do canto do retangulo (mundo)");
        rectProps.put("y", "Y do canto");
        rectProps.put("width", "Largura do retangulo");
        rectProps.put("height", "Altura do retangulo");
        reg.add("block_rect",
            "Marca como barreira (solido) todas as celulas que tocam um retangulo do mundo — 'desenhar' uma parede.",
            IgnisToolRegistry.schemaWith(rectProps, List.of("x", "y", "width", "height")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                World w = reg.liveGame.getOrCreateWorld();
                int n = w.blockRect(args.optDouble("x"), args.optDouble("y"),
                                    args.optDouble("width"), args.optDouble("height"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Barreira aplicada (" + n + " celulas). Total: " + w.getBlockedCount();
            });

        // unblock_rect
        reg.add("unblock_rect",
            "Remove barreiras de todas as celulas que tocam um retangulo do mundo.",
            IgnisToolRegistry.schemaWith(rectProps, List.of("x", "y", "width", "height")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getWorld() == null) return "Sem mundo definido.";
                int n = reg.liveGame.getWorld().unblockRect(args.optDouble("x"), args.optDouble("y"),
                                    args.optDouble("width"), args.optDouble("height"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Barreiras removidas (" + n + " celulas). Total: " + reg.liveGame.getWorld().getBlockedCount();
            });

        // block_cell
        Map<String, String> cellProps = new LinkedHashMap<>();
        cellProps.put("col", "Coluna da celula (indice inteiro)");
        cellProps.put("row", "Linha da celula (indice inteiro)");
        reg.add("block_cell",
            "Marca uma unica celula da grade como barreira (por indice col,row).",
            IgnisToolRegistry.schemaWith(cellProps, List.of("col", "row")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                World w = reg.liveGame.getOrCreateWorld();
                w.blockCell(args.optInt("col"), args.optInt("row"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Celula (" + args.optInt("col") + "," + args.optInt("row") + ") bloqueada. Total: " + w.getBlockedCount();
            });

        // unblock_cell
        reg.add("unblock_cell",
            "Remove a barreira de uma unica celula da grade (por indice col,row).",
            IgnisToolRegistry.schemaWith(cellProps, List.of("col", "row")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getWorld() == null) return "Sem mundo definido.";
                reg.liveGame.getWorld().unblockCell(args.optInt("col"), args.optInt("row"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Celula (" + args.optInt("col") + "," + args.optInt("row") + ") liberada.";
            });

        // clear_barriers
        reg.add("clear_barriers",
            "Remove todas as barreiras do mundo (mantem os limites).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getWorld() != null) reg.liveGame.getWorld().clearBarriers();
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Barreiras limpas.";
            });

        // set_object_world_collision
        Map<String, String> wcProps = new LinkedHashMap<>();
        wcProps.put("name", "Nome do objeto (ex: Hero)");
        wcProps.put("enabled", "true para o objeto colidir com limites/barreiras do mundo");
        reg.add("set_object_world_collision",
            "Liga/desliga a colisao de um objeto com os limites e barreiras do mundo (tipicamente o jogador).",
            IgnisToolRegistry.schemaWith(wcProps, List.of("name", "enabled")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.setWorldCollision(args.optBoolean("enabled", true));
                return "world_collision de " + go.getName() + " = " + go.isWorldCollision();
            });

        // set_world_property
        Map<String, String> wpProps = new LinkedHashMap<>();
        wpProps.put("name", "Nome do mundo (opcional)");
        wpProps.put("ambientColor", "Cor ambiente em hex, ex: #204060 (opcional; vazio remove)");
        reg.add("set_world_property",
            "Ajusta propriedades do mundo: nome e cor ambiente.",
            IgnisToolRegistry.schemaWith(wpProps, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                World w = reg.liveGame.getOrCreateWorld();
                if (args.has("name")) w.setName(args.optString("name"));
                if (args.has("ambientColor")) {
                    String hex = args.optString("ambientColor", "").trim();
                    w.setAmbientColor(hex.isEmpty() ? null : IgnisToolRegistry.safeColor(hex, null));
                }
                return "Mundo '" + w.getName() + "' atualizado.";
            });

        // get_world_info
        reg.add("get_world_info",
            "Retorna o estado do mundo: nome, limites, tamanho da celula e numero de barreiras.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                World w = reg.liveGame.getWorld();
                if (w == null) return "(nenhum mundo definido)";
                StringBuilder sb = new StringBuilder();
                sb.append("nome: ").append(w.getName()).append('\n');
                sb.append("limites: ").append(w.hasBounds()
                        ? "(" + (int) w.getMinX() + "," + (int) w.getMinY() + ") -> ("
                          + (int) w.getMaxX() + "," + (int) w.getMaxY() + ")"
                        : "(sem limites)").append('\n');
                sb.append("cellSize: ").append(w.getCellSize()).append("px\n");
                sb.append("barreiras: ").append(w.getBlockedCount()).append(" celulas");
                return sb.toString();
            });
    }
}
