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
 * Ferramentas MCP de audio: musica de fundo e efeitos sonoros via IgnisSoundEngine.
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class SoundTools {

    private final IgnisToolRegistry reg;

    SoundTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerAudioTools();
    }

    private void registerAudioTools() {
        // play_sound_preview
        Map<String, String> playSoundProps = new LinkedHashMap<>();
        playSoundProps.put("soundPath", "Caminho relativo do som (ex: assets/sounds/jump.wav)");
        playSoundProps.put("volume", "Volume 0.0-1.0 (opcional; padrao usa o volume SFX global)");
        reg.add("play_sound_preview",
            "Reproduz um efeito sonoro de preview a partir de um caminho relativo do projeto.",
            IgnisToolRegistry.schemaWith(playSoundProps, List.of("soundPath")),
            args -> {
                File f = reg.resolveInProject(args.optString("soundPath", ""));
                if (f == null || !f.isFile()) return "Erro: arquivo de som nao encontrado: " + args.optString("soundPath", "");
                double vol = args.optDouble("volume", -1);
                if (vol >= 0 && vol <= 1) {
                    IgnisSoundEngine.getInstance().playSound(f.getAbsolutePath(), (float) vol);
                } else {
                    IgnisSoundEngine.getInstance().playSound(f.getAbsolutePath());
                }
                return "Som reproduzido: " + args.optString("soundPath", "");
            });

        // play_music_preview
        Map<String, String> playMusicProps = new LinkedHashMap<>();
        playMusicProps.put("musicPath", "Caminho relativo da musica (ex: assets/music/theme.wav)");
        playMusicProps.put("loop", "true para repetir em loop (padrao: true)");
        reg.add("play_music_preview",
            "Reproduz uma musica de fundo de preview a partir de um caminho relativo do projeto.",
            IgnisToolRegistry.schemaWith(playMusicProps, List.of("musicPath")),
            args -> {
                File f = reg.resolveInProject(args.optString("musicPath", ""));
                if (f == null || !f.isFile()) return "Erro: arquivo de musica nao encontrado: " + args.optString("musicPath", "");
                boolean loop = args.optBoolean("loop", true);
                IgnisSoundEngine.getInstance().playMusic(f.getAbsolutePath(), loop);
                return "Musica iniciada" + (loop ? " (loop): " : ": ") + args.optString("musicPath", "");
            });

        // stop_all_audio
        reg.add("stop_all_audio",
            "Para todos os efeitos sonoros e a musica de fundo.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                IgnisSoundEngine.getInstance().stopAllSounds();
                IgnisSoundEngine.getInstance().stopMusic();
                return "Todos os audios foram parados.";
            });

        // pause_resume_music
        reg.add("pause_resume_music",
            "Pausa, retoma ou alterna (toggle) a musica de fundo, preservando a posicao.",
            IgnisToolRegistry.schemaWith(Map.of("action", "'pause', 'resume' ou 'toggle' (padrao: toggle)"), List.of()),
            args -> {
                String act = args.optString("action", "toggle").trim().toLowerCase();
                IgnisSoundEngine eng = IgnisSoundEngine.getInstance();
                if ("pause".equals(act)) { eng.pauseMusic(); return "Musica pausada."; }
                if ("resume".equals(act)) { eng.resumeMusic(); return "Musica retomada."; }
                if (eng.isMusicPaused()) { eng.resumeMusic(); return "Musica retomada (toggle)."; }
                if (eng.isMusicPlaying()) { eng.pauseMusic(); return "Musica pausada (toggle)."; }
                return "Nenhuma musica em reproducao.";
            });

        // set_audio_volumes
        Map<String, String> volProps = new LinkedHashMap<>();
        volProps.put("masterVolume", "Volume master 0.0-1.0 (opcional)");
        volProps.put("musicVolume", "Volume da musica 0.0-1.0 (opcional)");
        volProps.put("sfxVolume", "Volume dos efeitos sonoros 0.0-1.0 (opcional)");
        reg.add("set_audio_volumes",
            "Configura os volumes globais do motor de audio (master, musica, efeitos).",
            IgnisToolRegistry.schemaWith(volProps, List.of()),
            args -> {
                IgnisSoundEngine eng = IgnisSoundEngine.getInstance();
                StringBuilder res = new StringBuilder();
                if (args.has("masterVolume")) {
                    float v = IgnisToolRegistry.clamp01((float) args.optDouble("masterVolume", 1));
                    eng.setMasterVolume(v);
                    res.append("Master=").append(v).append(' ');
                }
                if (args.has("musicVolume")) {
                    float v = IgnisToolRegistry.clamp01((float) args.optDouble("musicVolume", 1));
                    eng.setMusicVolume(v);
                    res.append("Music=").append(v).append(' ');
                }
                if (args.has("sfxVolume")) {
                    float v = IgnisToolRegistry.clamp01((float) args.optDouble("sfxVolume", 1));
                    eng.setSfxVolume(v);
                    res.append("SFX=").append(v);
                }
                return res.length() > 0 ? "Volumes atualizados: " + res.toString().trim() : "Nenhum volume informado.";
            });

        // list_audio_assets
        reg.add("list_audio_assets",
            "Lista os arquivos de audio do projeto (assets/sounds e assets/music).",
            IgnisToolRegistry.schemaWith(Map.of("category", "'sounds', 'music' ou 'all' (padrao: all)"), List.of()),
            args -> {
                String cat = args.optString("category", "all").trim().toLowerCase();
                StringBuilder sb = new StringBuilder();
                if ("sounds".equals(cat) || "all".equals(cat)) reg.listAudioDir(sb, "assets/sounds");
                if ("music".equals(cat) || "all".equals(cat)) reg.listAudioDir(sb, "assets/music");
                return sb.length() == 0 ? "(nenhum audio encontrado)" : sb.toString();
            });

        // get_audio_status
        reg.add("get_audio_status",
            "Retorna o estado atual do motor de audio (musica tocando/pausada, volumes).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                IgnisSoundEngine eng = IgnisSoundEngine.getInstance();
                String musicPath = eng.getCurrentMusicPath();
                String musicState = eng.isMusicPlaying() ? "tocando: " + musicPath
                        : eng.isMusicPaused() ? "pausada: " + musicPath : "parada";
                return "Musica: " + musicState + "\nVolumes -> master=" + eng.getMasterVolume()
                        + " musica=" + eng.getMusicVolume() + " sfx=" + eng.getSfxVolume();
            });
    }
}
