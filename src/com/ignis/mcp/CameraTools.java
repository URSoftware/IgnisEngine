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
 * Ferramentas MCP de camera: follow, shake, bounds, camera ativa, transform e criacao de cameras.
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class CameraTools {

    private final IgnisToolRegistry reg;

    CameraTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerCameraTools();
    }

    private void registerCameraTools() {
        // list_cameras
        reg.add("list_cameras",
            "Lista as cameras da cena ativa (nome, posicao, zoom, se e a ativa).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                List<Camera> cams = reg.liveGame.getCameras();
                if (cams.isEmpty()) return "(nenhuma camera)";
                StringBuilder sb = new StringBuilder();
                for (Camera c : cams) {
                    sb.append(c.getCameraName()).append(" @ (").append((int) c.getX()).append(',').append((int) c.getY())
                      .append(") zoom=").append(c.getZoom())
                      .append(c.isActiveCamera() ? " [ATIVA]" : "").append('\n');
                }
                return sb.toString();
            });

        // create_camera
        Map<String, String> createCamProps = new LinkedHashMap<>();
        createCamProps.put("name", "Nome unico da camera");
        createCamProps.put("x", "Posicao X inicial (padrao 0)");
        createCamProps.put("y", "Posicao Y inicial (padrao 0)");
        createCamProps.put("zoom", "Zoom inicial (padrao 1.0)");
        createCamProps.put("rotation", "Rotacao inicial em graus (padrao 0)");
        createCamProps.put("setActive", "true para ativar imediatamente como camera principal (padrao: false)");
        reg.add("create_camera",
            "Cria uma nova camera e a adiciona a cena ativa.",
            IgnisToolRegistry.schemaWith(createCamProps, List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findCamera(name) != null) return "Erro: ja existe camera com esse nome: " + name;
                Camera cam = new Camera(name, reg.liveGame, args.optDouble("x", 0), args.optDouble("y", 0));
                cam.setCameraName(name);
                if (args.has("zoom")) cam.setZoom(args.optDouble("zoom"));
                if (args.has("rotation")) cam.setRotation(args.optDouble("rotation"));
                if (args.optBoolean("setActive", false)) {
                    reg.liveGame.setMainCamera(cam);
                } else {
                    reg.liveGame.addCamera(cam);
                }
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Camera criada: " + name;
            });

        // set_active_camera
        reg.add("set_active_camera",
            "Define qual camera e a principal/ativa da cena.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome da camera a ativar"), List.of("name")),
            args -> {
                Camera cam = reg.findCamera(args.optString("name", ""));
                if (cam == null) return "Erro: camera nao encontrada: " + args.optString("name", "");
                reg.liveGame.setMainCamera(cam);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Camera ativa: " + cam.getCameraName();
            });

        // set_camera_transform
        Map<String, String> camTransformProps = new LinkedHashMap<>();
        camTransformProps.put("name", "Nome da camera alvo");
        camTransformProps.put("x", "Nova posicao X (opcional)");
        camTransformProps.put("y", "Nova posicao Y (opcional)");
        camTransformProps.put("zoom", "Novo zoom (opcional)");
        camTransformProps.put("rotation", "Nova rotacao em graus (opcional)");
        reg.add("set_camera_transform",
            "Altera posicao/zoom/rotacao de uma camera existente.",
            IgnisToolRegistry.schemaWith(camTransformProps, List.of("name")),
            args -> {
                Camera cam = reg.findCamera(args.optString("name", ""));
                if (cam == null) return "Erro: camera nao encontrada: " + args.optString("name", "");
                if (args.has("x") && args.has("y")) cam.setPosition(args.optDouble("x"), args.optDouble("y"));
                else if (args.has("x")) cam.setX(args.optDouble("x"));
                else if (args.has("y")) cam.setY(args.optDouble("y"));
                if (args.has("zoom")) cam.setZoom(args.optDouble("zoom"));
                if (args.has("rotation")) cam.setRotation(args.optDouble("rotation"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Transform atualizado: " + cam.getCameraName();
            });

        // convert_coordinates
        Map<String, String> convertProps = new LinkedHashMap<>();
        convertProps.put("direction", "'world_to_screen' ou 'screen_to_world'");
        convertProps.put("x", "Coordenada X de entrada");
        convertProps.put("y", "Coordenada Y de entrada");
        reg.add("convert_coordinates",
            "Converte coordenadas entre mundo e tela usando a camera ativa (util para mira/HUD).",
            IgnisToolRegistry.schemaWith(convertProps, List.of("direction", "x", "y")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = reg.liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                double x = args.optDouble("x", 0);
                double y = args.optDouble("y", 0);
                String dir = args.optString("direction", "world_to_screen").trim().toLowerCase();
                java.awt.geom.Point2D.Double p = "screen_to_world".equals(dir)
                        ? cam.screenToWorld(x, y) : cam.worldToScreen(x, y);
                return "(" + p.x + ", " + p.y + ")";
            });

        // set_camera_follow (Fase B): camera ativa segue um objeto
        Map<String, String> followProps = new LinkedHashMap<>();
        followProps.put("targetName", "Nome do objeto a seguir");
        followProps.put("smoothing", "Suavidade 0.0-1.0 por tick (padrao 0.15; 1 = instantaneo)");
        reg.add("set_camera_follow",
            "Faz a camera ativa seguir suavemente o centro de um objeto durante o Play.",
            IgnisToolRegistry.schemaWith(followProps, List.of("targetName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = reg.liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                GameObject target = reg.findObject(args.optString("targetName", ""));
                if (target == null) return "Erro: objeto nao encontrado: " + args.optString("targetName", "");
                cam.follow(target, args.optDouble("smoothing", 0.15));
                return "Camera '" + cam.getCameraName() + "' seguindo " + target.getName();
            });

        // stop_camera_follow
        reg.add("stop_camera_follow",
            "Faz a camera ativa parar de seguir qualquer objeto.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = reg.liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.stopFollow();
                return "Camera '" + cam.getCameraName() + "' parou de seguir.";
            });

        // camera_shake
        Map<String, String> shakeProps = new LinkedHashMap<>();
        shakeProps.put("intensity", "Amplitude do tremor em px (ex: 8)");
        shakeProps.put("duration", "Duracao em segundos (ex: 0.4)");
        reg.add("camera_shake",
            "Dispara um tremor na camera ativa com decaimento linear (efeito de impacto).",
            IgnisToolRegistry.schemaWith(shakeProps, List.of("intensity", "duration")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = reg.liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.shake(args.optDouble("intensity", 8), args.optDouble("duration", 0.4));
                return "Tremor disparado na camera '" + cam.getCameraName() + "'.";
            });

        // set_camera_bounds
        Map<String, String> boundsProps = new LinkedHashMap<>();
        boundsProps.put("minX", "Limite minimo X do centro da camera");
        boundsProps.put("minY", "Limite minimo Y");
        boundsProps.put("maxX", "Limite maximo X");
        boundsProps.put("maxY", "Limite maximo Y");
        reg.add("set_camera_bounds",
            "Limita o centro da camera ativa a um retangulo do mundo (evita mostrar fora do nivel).",
            IgnisToolRegistry.schemaWith(boundsProps, List.of("minX", "minY", "maxX", "maxY")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = reg.liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.setBounds(args.optDouble("minX"), args.optDouble("minY"),
                              args.optDouble("maxX"), args.optDouble("maxY"));
                return "Limites da camera definidos.";
            });

        // clear_camera_bounds
        reg.add("clear_camera_bounds",
            "Remove os limites de movimento da camera ativa.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = reg.liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.clearBounds();
                return "Limites da camera removidos.";
            });
    }
}
