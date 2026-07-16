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
 * Ferramentas MCP de animacao: criacao/inspecao de .anim.json (base, funciona headless) e attach/play/stop em objetos da cena viva.
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class SpriteAnimationTools {

    private final IgnisToolRegistry reg;

    SpriteAnimationTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerBaseTools() {
        registerAnimationBaseTools();
    }

    void registerSceneAnimationTools() {
        registerAnimationSceneTools();
    }

    private void registerAnimationBaseTools() {
        // list_animations
        reg.add("list_animations",
            "Lista as animacoes (.anim.json) do projeto com nome, loop, curva e numero de frames.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                List<SpriteAnimation> anims = AnimationIO.loadAll(reg.projectFolder);
                if (anims.isEmpty()) return "(nenhuma animacao)";
                StringBuilder sb = new StringBuilder();
                for (SpriteAnimation a : anims) {
                    sb.append(a.getName()).append(" - loop=").append(a.isLoop())
                      .append(" curve=").append(a.getCurveType())
                      .append(" frames=").append(a.getFrames().size())
                      .append(" duration=").append(a.totalDuration()).append("s\n");
                }
                return sb.toString();
            });

        // create_animation
        Map<String, String> createAnimProps = new LinkedHashMap<>();
        createAnimProps.put("name", "Nome unico da animacao (ex: player_run)");
        createAnimProps.put("loop", "true para tocar em loop (padrao: true)");
        createAnimProps.put("curveType", "LINEAR, EASE_IN, EASE_OUT ou EASE_IN_OUT (padrao: LINEAR)");
        reg.add("create_animation",
            "Cria um novo clipe de animacao vazio no projeto (assets/animations/<name>.anim.json).",
            IgnisToolRegistry.schemaWith(createAnimProps, List.of("name")),
            args -> {
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                SpriteAnimation anim = new SpriteAnimation(name);
                anim.setLoop(args.optBoolean("loop", true));
                try {
                    anim.setCurveType(SpriteAnimation.CurveType.valueOf(args.optString("curveType", "LINEAR").trim().toUpperCase()));
                } catch (IllegalArgumentException iae) {
                    return "Erro: curveType invalido (use LINEAR, EASE_IN, EASE_OUT ou EASE_IN_OUT).";
                }
                try {
                    AnimationIO.save(anim, reg.projectFolder);
                    return "Animacao criada: " + name;
                } catch (Exception e) {
                    return "Erro ao salvar animacao: " + e.getMessage();
                }
            });

        // add_animation_frame
        Map<String, String> addFrameProps = new LinkedHashMap<>();
        addFrameProps.put("animName", "Nome da animacao (sem extensao)");
        addFrameProps.put("spritePath", "Caminho do sprite relativo ao projeto (ex: assets/sprites/run_01.png)");
        addFrameProps.put("duration", "Duracao do frame em segundos (ex: 0.1)");
        reg.add("add_animation_frame",
            "Adiciona um keyframe (sprite + duracao) ao final de uma animacao existente.",
            IgnisToolRegistry.schemaWith(addFrameProps, List.of("animName", "spritePath", "duration")),
            args -> {
                SpriteAnimation anim = reg.loadAnimationOrNull(args.optString("animName", ""));
                if (anim == null) return "Erro: animacao nao encontrada: " + args.optString("animName", "");
                String spritePath = args.optString("spritePath", "");
                if (reg.resolveInProject(spritePath) == null) return "Erro: spritePath invalido (caminho fora do projeto): " + spritePath;
                double duration = args.optDouble("duration", 0.1);
                anim.addFrame(new AnimationFrame(spritePath, duration));
                try {
                    AnimationIO.save(anim, reg.projectFolder);
                    return "Frame adicionado a '" + anim.getName() + "' (" + anim.getFrames().size() + " frames).";
                } catch (Exception e) {
                    return "Erro ao salvar animacao: " + e.getMessage();
                }
            });

        // read_animation
        reg.add("read_animation",
            "Le a definicao completa de uma animacao (frames, duracoes, loop, curva).",
            IgnisToolRegistry.schemaWith(Map.of("animName", "Nome da animacao (sem extensao)"), List.of("animName")),
            args -> {
                SpriteAnimation anim = reg.loadAnimationOrNull(args.optString("animName", ""));
                if (anim == null) return "Erro: animacao nao encontrada: " + args.optString("animName", "");
                return anim.toJSON().toString(2);
            });
    }

    private void registerAnimationSceneTools() {
        // attach_animation
        Map<String, String> attachAnimProps = new LinkedHashMap<>();
        attachAnimProps.put("objectName", "Nome do objeto na cena");
        attachAnimProps.put("animName", "Nome da animacao a anexar (sem extensao)");
        attachAnimProps.put("setAsDefault", "true para tocar automaticamente ao entrar em Play (padrao: false)");
        reg.add("attach_animation",
            "Anexa uma animacao existente (assets/animations/) ao Animator de um GameObject da cena.",
            IgnisToolRegistry.schemaWith(attachAnimProps, List.of("objectName", "animName")),
            args -> {
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                SpriteAnimation anim = reg.loadAnimationOrNull(args.optString("animName", ""));
                if (anim == null) return "Erro: animacao nao encontrada: " + args.optString("animName", "");
                Animator animator = go.getOrCreateAnimator();
                animator.addAnimation(anim);
                if (args.optBoolean("setAsDefault", false)) animator.setDefaultAnimation(anim.getName());
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Animacao '" + anim.getName() + "' anexada a " + go.getName();
            });

        // play_animation
        Map<String, String> playAnimProps = new LinkedHashMap<>();
        playAnimProps.put("objectName", "Nome do objeto na cena");
        playAnimProps.put("animName", "Nome da animacao a tocar (deve ja estar anexada)");
        playAnimProps.put("waitForCurrent", "true para aguardar a animacao atual terminar antes de trocar (padrao: false)");
        reg.add("play_animation",
            "Inicia a reproducao de uma animacao anexada a um GameObject.",
            IgnisToolRegistry.schemaWith(playAnimProps, List.of("objectName", "animName")),
            args -> {
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                Animator animator = go.getAnimator();
                if (animator == null) return "Erro: objeto nao tem animacoes anexadas. Use attach_animation primeiro.";
                String animName = args.optString("animName", "");
                if (animator.getAnimation(animName) == null) return "Erro: animacao nao anexada a este objeto: " + animName;
                animator.play(animName, args.optBoolean("waitForCurrent", false));
                return "Tocando '" + animName + "' em " + go.getName();
            });

        // stop_animation
        reg.add("stop_animation",
            "Para a animacao de um GameObject e restaura o sprite anterior.",
            IgnisToolRegistry.schemaWith(Map.of("objectName", "Nome do objeto na cena"), List.of("objectName")),
            args -> {
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                if (go.getAnimator() != null) go.getAnimator().stop();
                go.resetAnimator();
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Animacao parada em " + go.getName();
            });

        // get_animation_status
        reg.add("get_animation_status",
            "Retorna o estado de animacao de um objeto (animacao atual, se esta tocando, animacoes disponiveis).",
            IgnisToolRegistry.schemaWith(Map.of("objectName", "Nome do objeto na cena"), List.of("objectName")),
            args -> {
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                Animator animator = go.getAnimator();
                if (animator == null) return "(sem animacoes anexadas)";
                return "Atual: " + animator.getCurrentName() + " | tocando: " + animator.isPlaying()
                        + " | disponiveis: " + animator.getAnimations().keySet();
            });
    }
}
