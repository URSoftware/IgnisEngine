package com.ignis.mcp;

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
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIComponent;
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
 * IgnisToolRegistry - Fonte canonica das ferramentas do IgnisEngine.
 *
 * <p>Descreve, de forma independente do SDK do MCP, o conjunto de ferramentas
 * que a engine expoe para agentes de IA (nome, descricao, schema JSON e o
 * executor). Serve como camada compartilhada por tres consumidores:</p>
 * <ul>
 *   <li>{@link McpServerManager} (transporte STDIO, para clientes MCP tradicionais
 *       como Claude Desktop/Cursor que lancam o processo);</li>
 *   <li>{@link McpHttpBridge} (transporte HTTP/JSON local, para agentes que se
 *       conectam por URL, incluindo IAs usando APIs Gemini/NVIDIA);</li>
 *   <li>uma futura IA agentica embarcada no editor.</li>
 * </ul>
 *
 * <p>Toda execucao passa por {@link IgnisMcpBridge#runOnFxThread}, garantindo que
 * mutacoes no Scene Graph do JavaFX acontecam na thread de UI. O registro delega
 * a logica pesada ao {@link ScriptManager} do projeto ativo, mantendo uma unica
 * fonte de verdade para as operacoes do motor.</p>
 */
public final class IgnisToolRegistry {

    /** Assinatura de um executor de ferramenta: recebe os argumentos e devolve texto. */
    @FunctionalInterface
    public interface ToolHandler {
        String execute(JSONObject arguments) throws Exception;
    }

    /** Descricao imutavel de uma ferramenta exposta ao MCP. */
    public static final class ToolDef {
        public final String name;
        public final String description;
        public final JSONObject inputSchema;
        public final ToolHandler handler;

        ToolDef(String name, String description, JSONObject inputSchema, ToolHandler handler) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.handler = handler;
        }
    }

    private final File projectFolder;
    private final Map<String, ToolDef> tools = new LinkedHashMap<>();

    // Contexto vivo do editor (opcional): presente quando o bridge roda dentro do
    // editor JavaFX, habilitando ferramentas de cena e de Play. Nulo no modo headless.
    private Game liveGame;
    private Runnable playHook, stopHook, refreshHook, saveHook;

    public IgnisToolRegistry(File projectFolder) {
        this.projectFolder = projectFolder;
        registerDefaults();
    }

    /**
     * Liga o registry ao editor vivo, registrando as ferramentas de cena e Play.
     * Os hooks (play/stop/refresh/save) invocam os metodos reais do editor e sao
     * executados na thread de UI (o {@link #call} ja envolve tudo em runOnFxThread).
     */
    public void attachLiveEditor(Game game, Runnable play, Runnable stop, Runnable refresh, Runnable save) {
        this.liveGame = game;
        this.playHook = play;
        this.stopHook = stop;
        this.refreshHook = refresh;
        this.saveHook = save;
        registerSceneTools();
    }

    public boolean hasLiveEditor() {
        return liveGame != null;
    }

    public File getProjectFolder() {
        return projectFolder;
    }

    /** Retorna as ferramentas na ordem de registro. */
    public List<ToolDef> list() {
        return new ArrayList<>(tools.values());
    }

    public ToolDef get(String name) {
        return tools.get(name);
    }

    /**
     * Executa uma ferramenta pelo nome, na thread de UI do JavaFX.
     *
     * @throws IllegalArgumentException se a ferramenta nao existir.
     */
    public String call(String name, JSONObject arguments) throws Exception {
        ToolDef def = tools.get(name);
        if (def == null) throw new IllegalArgumentException("Ferramenta desconhecida: " + name);
        final JSONObject safeArgs = (arguments != null) ? arguments : new JSONObject();
        long startNanos = System.nanoTime();
        String result = IgnisMcpBridge.runOnFxThread(() -> {
            try {
                return def.handler.execute(safeArgs);
            } catch (Exception e) {
                return "Erro ao executar '" + name + "': " + e.getMessage();
            }
        });
        // Auditoria: cada chamada de agente aparece no Console do editor
        // (FxConsolePanel captura System.out). Args truncados para nao inundar
        // o log com conteudos grandes (ex: write_script).
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        String argsPreview = safeArgs.isEmpty() ? "" : " " + truncate(safeArgs.toString(), 120);
        boolean isError = result != null && result.startsWith("Erro");
        System.out.println("[MCP] " + name + argsPreview + " -> "
                + (isError ? "ERRO" : "ok") + " (" + ms + "ms)");
        return result;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** Serializa as definicoes das ferramentas (para o endpoint HTTP GET /mcp/tools). */
    public JSONArray toJsonArray() {
        JSONArray arr = new JSONArray();
        for (ToolDef def : tools.values()) {
            JSONObject t = new JSONObject();
            t.put("name", def.name);
            t.put("description", def.description);
            t.put("inputSchema", def.inputSchema);
            arr.put(t);
        }
        return arr;
    }

    // ----------------------------------------------------------------------
    // Registro das ferramentas padrao
    // ----------------------------------------------------------------------

    private void add(String name, String description, JSONObject schema, ToolHandler handler) {
        tools.put(name, new ToolDef(name, description, schema, handler));
    }

    private static JSONObject objectSchema() {
        return new JSONObject().put("type", "object");
    }

    private static JSONObject schemaWith(Map<String, String> props, List<String> required) {
        JSONObject schema = new JSONObject().put("type", "object");
        JSONObject properties = new JSONObject();
        for (Map.Entry<String, String> e : props.entrySet()) {
            properties.put(e.getKey(), new JSONObject().put("type", "string").put("description", e.getValue()));
        }
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) schema.put("required", new JSONArray(required));
        return schema;
    }

    private ScriptManager scriptManager() {
        return new ScriptManager(projectFolder);
    }

    private void registerDefaults() {
        // get_project_tree
        add("get_project_tree",
            "Retorna a arvore recursiva de diretorios e arquivos do projeto ativo.",
            objectSchema(),
            args -> {
                StringBuilder sb = new StringBuilder();
                buildTree(projectFolder, "", sb);
                return sb.length() == 0 ? "(projeto vazio)" : sb.toString();
            });

        // list_scripts
        add("list_scripts",
            "Lista os nomes dos scripts IgnisScript disponiveis no projeto.",
            objectSchema(),
            args -> {
                List<String> scripts = scriptManager().listAvailableScripts();
                if (scripts.isEmpty()) return "(nenhum script)";
                return String.join("\n", scripts);
            });

        // read_script
        add("read_script",
            "Le o conteudo-fonte de um script pelo nome (sem extensao).",
            schemaWith(Map.of("scriptName", "Nome do script (ex: PlayerController)"), List.of("scriptName")),
            args -> {
                String name = args.optString("scriptName", "").trim();
                if (name.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                String content = scriptManager().readScriptContent(name);
                return content != null ? content : "Erro: script nao encontrado: " + name;
            });

        // write_script
        add("write_script",
            "Sobrescreve o conteudo-fonte de um script existente.",
            schemaWith(new LinkedHashMap<>(Map.of(
                    "scriptName", "Nome do script (ex: PlayerController)",
                    "content", "Conteudo Java completo do script")),
                List.of("scriptName", "content")),
            args -> {
                String name = args.optString("scriptName", "").trim();
                String content = args.optString("content", "");
                if (name.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                boolean ok = scriptManager().saveScriptContent(name, content);
                return ok ? "Script salvo: " + name : "Erro ao salvar script: " + name;
            });

        // create_script
        add("create_script",
            "Cria um novo script Java a partir do template padrao do motor.",
            schemaWith(Map.of("scriptName", "Nome do novo script (ex: EnemyAI)"), List.of("scriptName")),
            args -> {
                String name = args.optString("scriptName", "").trim();
                if (name.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                boolean ok = scriptManager().createNewScript(name);
                return ok ? "Script criado: " + name : "Erro: script ja existe ou nome invalido: " + name;
            });

        // compile_project
        add("compile_project",
            "Compila todos os scripts do projeto e retorna o total compilado.",
            objectSchema(),
            args -> {
                int compiled = scriptManager().compileAllScripts();
                return "Compilacao concluida. Scripts compilados: " + compiled;
            });

        // read_file
        add("read_file",
            "Le um arquivo de texto pelo caminho relativo a raiz do projeto.",
            schemaWith(Map.of("path", "Caminho relativo ao projeto (ex: assets/config.json)"), List.of("path")),
            args -> {
                String rel = args.optString("path", "").trim();
                if (rel.isEmpty()) return "Erro: 'path' obrigatorio.";
                File f = resolveInProject(rel);
                if (f == null) return "Erro: caminho fora do projeto: " + rel;
                if (!f.isFile()) return "Erro: arquivo nao encontrado: " + rel;
                return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            });

        // generate_sprite
        Map<String, String> spriteProps2 = new LinkedHashMap<>();
        spriteProps2.put("name", "Nome do arquivo (sem extensao)");
        spriteProps2.put("shape", "Forma: square, circle, triangle, diamond ou blob (padrao: square)");
        spriteProps2.put("width", "Largura em px (padrao 64)");
        spriteProps2.put("height", "Altura em px (padrao 64)");
        spriteProps2.put("color", "Cor de preenchimento em hex, ex: #4C9EF5 (padrao)");
        spriteProps2.put("outlineColor", "Cor do contorno em hex (padrao #1A2B3C)");
        spriteProps2.put("symbol", "Um ou poucos caracteres desenhados no centro (opcional, ex: H)");
        add("generate_sprite",
            "Gera um sprite 2D simples (forma+cor+simbolo, transparencia) e salva em assets/sprites/<name>.png.",
            schemaWith(spriteProps2, List.of("name")),
            args -> {
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                int w = Math.max(8, Math.min(1024, args.optInt("width", 64)));
                int h = Math.max(8, Math.min(1024, args.optInt("height", 64)));
                try {
                    BufferedImage img = drawSprite(
                            args.optString("shape", "square"),
                            w, h,
                            args.optString("color", "#4C9EF5"),
                            args.optString("outlineColor", "#1A2B3C"),
                            args.optString("symbol", ""));
                    File out = new File(projectFolder, "assets/sprites/" + name + ".png");
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    ImageIO.write(img, "PNG", out);
                    return "Sprite gerado: assets/sprites/" + name + ".png (" + w + "x" + h + ")";
                } catch (Exception e) {
                    return "Erro ao gerar sprite: " + e.getMessage();
                }
            });

        // remove_sprite_background (mesma logica do transporte STDIO, em ImageTools)
        Map<String, String> removeBgProps = new LinkedHashMap<>();
        removeBgProps.put("imagePath", "Caminho relativo da imagem (ex: assets/sprites/hero.png)");
        removeBgProps.put("targetColorHex", "'auto' (detecta cores das bordas), uma cor '#ffffff' ou lista '#fff,#ccc'");
        removeBgProps.put("tolerance", "Tolerancia de cor 0-255 (padrao 20)");
        add("remove_sprite_background",
            "Remove cor solida ou quadriculado (checkerboard) do fundo de uma imagem, deixando-a transparente (sobrescreve como PNG).",
            schemaWith(removeBgProps, List.of("imagePath", "targetColorHex")),
            args -> {
                String imagePath = args.optString("imagePath", "");
                File imgFile = resolveInProject(imagePath);
                if (imgFile == null) return "Erro: imagePath invalido (caminho fora do projeto): " + imagePath;
                int tolerance = Math.max(0, Math.min(255, args.optInt("tolerance", 20)));
                String result = com.ignis.mcp.tools.ImageTools.removeBackground(
                        imgFile, args.optString("targetColorHex", "auto"), tolerance);
                return result.startsWith("Erro") ? result : result + " de " + imagePath;
            });

        registerAudioTools();
        registerAssetNoteTools();
        registerAnimationBaseTools();
    }

    // Desenha um sprite procedural simples (forma + contorno + simbolo opcional) com fundo transparente.
    private static BufferedImage drawSprite(String shape, int w, int h, String colorHex, String outlineHex, String symbol) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = safeColor(colorHex, new Color(0x4C, 0x9E, 0xF5));
        Color outline = safeColor(outlineHex, new Color(0x1A, 0x2B, 0x3C));
        float strokeWidth = Math.max(2f, Math.min(w, h) * 0.06f);
        int margin = (int) Math.ceil(strokeWidth);

        String s = (shape == null ? "square" : shape.trim().toLowerCase());
        switch (s) {
            case "circle":
                g.setColor(fill);
                g.fillOval(margin, margin, w - 2 * margin, h - 2 * margin);
                g.setColor(outline);
                g.setStroke(new BasicStroke(strokeWidth));
                g.drawOval(margin, margin, w - 2 * margin, h - 2 * margin);
                break;
            case "triangle": {
                int[] xs = { w / 2, margin, w - margin };
                int[] ys = { margin, h - margin, h - margin };
                g.setColor(fill);
                g.fillPolygon(xs, ys, 3);
                g.setColor(outline);
                g.setStroke(new BasicStroke(strokeWidth));
                g.drawPolygon(xs, ys, 3);
                break;
            }
            case "diamond": {
                int[] xs = { w / 2, w - margin, w / 2, margin };
                int[] ys = { margin, h / 2, h - margin, h / 2 };
                g.setColor(fill);
                g.fillPolygon(xs, ys, 4);
                g.setColor(outline);
                g.setStroke(new BasicStroke(strokeWidth));
                g.drawPolygon(xs, ys, 4);
                break;
            }
            case "blob": {
                GeneralPath path = new GeneralPath();
                double cx = w / 2.0, cy = h / 2.0;
                double baseR = Math.min(w, h) / 2.0 - margin;
                int steps = 24;
                for (int i = 0; i <= steps; i++) {
                    double angle = 2 * Math.PI * i / steps;
                    double r = baseR * (1.0 + 0.12 * Math.sin(angle * 5.0));
                    double px = cx + r * Math.cos(angle);
                    double py = cy + r * Math.sin(angle);
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.closePath();
                g.setColor(fill);
                g.fill(path);
                g.setColor(outline);
                g.setStroke(new BasicStroke(strokeWidth));
                g.draw(path);
                break;
            }
            case "square":
            default: {
                int arc = Math.max(4, Math.min(w, h) / 6);
                g.setColor(fill);
                g.fillRoundRect(margin, margin, w - 2 * margin, h - 2 * margin, arc, arc);
                g.setColor(outline);
                g.setStroke(new BasicStroke(strokeWidth));
                g.drawRoundRect(margin, margin, w - 2 * margin, h - 2 * margin, arc, arc);
                break;
            }
        }

        if (symbol != null && !symbol.trim().isEmpty()) {
            String text = symbol.trim();
            double luminance = (0.299 * fill.getRed() + 0.587 * fill.getGreen() + 0.114 * fill.getBlue()) / 255.0;
            g.setColor(luminance > 0.6 ? Color.BLACK : Color.WHITE);
            int fontSize = (int) (Math.min(w, h) * 0.45);
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(8, fontSize)));
            FontMetrics fm = g.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, tx, ty);
        }

        g.dispose();
        return img;
    }

    private static Color safeColor(String hex, Color fallback) {
        try {
            String h = hex == null ? "" : hex.trim();
            if (h.isEmpty()) return fallback;
            if (!h.startsWith("#") && !h.startsWith("0x") && !h.startsWith("0X")) h = "#" + h;
            return Color.decode(h);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ----------------------------------------------------------------------
    // Ferramentas de audio (com.ignis.core.IgnisSoundEngine e um singleton
    // estatico; funcionam mesmo sem editor vivo, direto no headless).
    // ----------------------------------------------------------------------

    private void registerAudioTools() {
        // play_sound_preview
        Map<String, String> playSoundProps = new LinkedHashMap<>();
        playSoundProps.put("soundPath", "Caminho relativo do som (ex: assets/sounds/jump.wav)");
        playSoundProps.put("volume", "Volume 0.0-1.0 (opcional; padrao usa o volume SFX global)");
        add("play_sound_preview",
            "Reproduz um efeito sonoro de preview a partir de um caminho relativo do projeto.",
            schemaWith(playSoundProps, List.of("soundPath")),
            args -> {
                File f = resolveInProject(args.optString("soundPath", ""));
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
        add("play_music_preview",
            "Reproduz uma musica de fundo de preview a partir de um caminho relativo do projeto.",
            schemaWith(playMusicProps, List.of("musicPath")),
            args -> {
                File f = resolveInProject(args.optString("musicPath", ""));
                if (f == null || !f.isFile()) return "Erro: arquivo de musica nao encontrado: " + args.optString("musicPath", "");
                boolean loop = args.optBoolean("loop", true);
                IgnisSoundEngine.getInstance().playMusic(f.getAbsolutePath(), loop);
                return "Musica iniciada" + (loop ? " (loop): " : ": ") + args.optString("musicPath", "");
            });

        // stop_all_audio
        add("stop_all_audio",
            "Para todos os efeitos sonoros e a musica de fundo.",
            objectSchema(),
            args -> {
                IgnisSoundEngine.getInstance().stopAllSounds();
                IgnisSoundEngine.getInstance().stopMusic();
                return "Todos os audios foram parados.";
            });

        // pause_resume_music
        add("pause_resume_music",
            "Pausa, retoma ou alterna (toggle) a musica de fundo, preservando a posicao.",
            schemaWith(Map.of("action", "'pause', 'resume' ou 'toggle' (padrao: toggle)"), List.of()),
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
        add("set_audio_volumes",
            "Configura os volumes globais do motor de audio (master, musica, efeitos).",
            schemaWith(volProps, List.of()),
            args -> {
                IgnisSoundEngine eng = IgnisSoundEngine.getInstance();
                StringBuilder res = new StringBuilder();
                if (args.has("masterVolume")) {
                    float v = clamp01((float) args.optDouble("masterVolume", 1));
                    eng.setMasterVolume(v);
                    res.append("Master=").append(v).append(' ');
                }
                if (args.has("musicVolume")) {
                    float v = clamp01((float) args.optDouble("musicVolume", 1));
                    eng.setMusicVolume(v);
                    res.append("Music=").append(v).append(' ');
                }
                if (args.has("sfxVolume")) {
                    float v = clamp01((float) args.optDouble("sfxVolume", 1));
                    eng.setSfxVolume(v);
                    res.append("SFX=").append(v);
                }
                return res.length() > 0 ? "Volumes atualizados: " + res.toString().trim() : "Nenhum volume informado.";
            });

        // list_audio_assets
        add("list_audio_assets",
            "Lista os arquivos de audio do projeto (assets/sounds e assets/music).",
            schemaWith(Map.of("category", "'sounds', 'music' ou 'all' (padrao: all)"), List.of()),
            args -> {
                String cat = args.optString("category", "all").trim().toLowerCase();
                StringBuilder sb = new StringBuilder();
                if ("sounds".equals(cat) || "all".equals(cat)) listAudioDir(sb, "assets/sounds");
                if ("music".equals(cat) || "all".equals(cat)) listAudioDir(sb, "assets/music");
                return sb.length() == 0 ? "(nenhum audio encontrado)" : sb.toString();
            });

        // get_audio_status
        add("get_audio_status",
            "Retorna o estado atual do motor de audio (musica tocando/pausada, volumes).",
            objectSchema(),
            args -> {
                IgnisSoundEngine eng = IgnisSoundEngine.getInstance();
                String musicPath = eng.getCurrentMusicPath();
                String musicState = eng.isMusicPlaying() ? "tocando: " + musicPath
                        : eng.isMusicPaused() ? "pausada: " + musicPath : "parada";
                return "Musica: " + musicState + "\nVolumes -> master=" + eng.getMasterVolume()
                        + " musica=" + eng.getMusicVolume() + " sfx=" + eng.getSfxVolume();
            });
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void listAudioDir(StringBuilder sb, String relDir) {
        File dir = new File(projectFolder, relDir);
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(wav|mp3|aiff|au|ogg)$") && !isSymlink(new File(d, n)));
        if (files == null || files.length == 0) return;
        sb.append("=== ").append(relDir).append(" ===\n");
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File f : files) sb.append("  ").append(relDir).append('/').append(f.getName()).append('\n');
    }

    // ----------------------------------------------------------------------
    // Ferramentas de assets e notas (leitura/escrita em disco; sem editor vivo)
    // ----------------------------------------------------------------------

    private void registerAssetNoteTools() {
        // list_assets
        add("list_assets",
            "Lista os arquivos de assets do projeto (sprites, sounds, music, fonts, tilemaps, ui, animations).",
            schemaWith(Map.of("category", "Subpasta de assets/ a listar (opcional; padrao lista todas)"), List.of()),
            args -> {
                String cat = args.optString("category", "").trim();
                File assetsDir = new File(projectFolder, "assets");
                if (!assetsDir.isDirectory()) return "(pasta assets/ nao encontrada)";
                StringBuilder sb = new StringBuilder();
                File[] dirs;
                if (cat.isEmpty()) {
                    dirs = assetsDir.listFiles(f -> f.isDirectory() && !isSymlink(f));
                } else {
                    File target = resolveInProject("assets/" + cat);
                    if (target == null) return "Erro: categoria invalida (caminho fora do projeto): " + cat;
                    dirs = new File[] { target };
                }
                if (dirs == null) return "(nenhum asset encontrado)";
                java.util.Arrays.sort(dirs, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File d : dirs) {
                    if (!d.isDirectory()) continue;
                    File[] files = d.listFiles(f -> f.isFile() && !isSymlink(f));
                    if (files == null || files.length == 0) continue;
                    sb.append("=== assets/").append(d.getName()).append(" ===\n");
                    java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                    for (File f : files) sb.append("  assets/").append(d.getName()).append('/').append(f.getName())
                            .append(" (").append(f.length()).append(" bytes)\n");
                }
                return sb.length() == 0 ? "(nenhum asset encontrado)" : sb.toString();
            });

        // import_asset_from_path
        Map<String, String> importProps = new LinkedHashMap<>();
        importProps.put("sourcePath", "Caminho absoluto do arquivo de origem, fora do projeto");
        importProps.put("category", "Subpasta de destino em assets/ (ex: sprites, sounds, music)");
        importProps.put("overwrite", "true para sobrescrever se ja existir (padrao: false)");
        add("import_asset_from_path",
            "Copia um arquivo externo para dentro de assets/<category>/ do projeto ativo.",
            schemaWith(importProps, List.of("sourcePath", "category")),
            args -> {
                String srcPath = args.optString("sourcePath", "").trim();
                String category = args.optString("category", "").trim();
                if (srcPath.isEmpty() || category.isEmpty()) return "Erro: 'sourcePath' e 'category' sao obrigatorios.";
                File src = new File(srcPath);
                if (!src.isFile()) return "Erro: arquivo de origem nao encontrado: " + srcPath;
                // 'category' vira parte do destino (dentro do projeto) e precisa ficar contida em assets/;
                // 'sourcePath' fica livre de proposito (o objetivo da ferramenta e importar de FORA do projeto).
                File destDir = resolveInProject("assets/" + category);
                if (destDir == null) return "Erro: categoria invalida (caminho de destino fora do projeto): " + category;
                destDir.mkdirs();
                File dest = new File(destDir, src.getName());
                if (dest.exists() && !args.optBoolean("overwrite", false)) {
                    return "Erro: ja existe um asset com esse nome (use overwrite=true): assets/" + category + "/" + src.getName();
                }
                try {
                    Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return "Asset importado: assets/" + category + "/" + src.getName();
                } catch (Exception e) {
                    return "Erro ao importar asset: " + e.getMessage();
                }
            });

        // list_notes
        add("list_notes",
            "Lista as paginas de notas/wiki do projeto (titulo e nome de arquivo).",
            objectSchema(),
            args -> {
                File[] files = notesFolder().listFiles((d, n) -> n.toLowerCase().endsWith(".json") && !isSymlink(new File(d, n)));
                if (files == null || files.length == 0) return "(nenhuma nota)";
                StringBuilder sb = new StringBuilder();
                for (File f : files) {
                    try {
                        JSONObject json = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                        sb.append(f.getName()).append(" - ").append(json.optString("title", "(sem titulo)")).append('\n');
                    } catch (Exception ignore) { /* pula arquivo corrompido */ }
                }
                return sb.length() == 0 ? "(nenhuma nota)" : sb.toString();
            });

        // create_note
        add("create_note",
            "Cria uma nova pagina de nota/wiki no projeto.",
            schemaWith(Map.of("title", "Titulo da nota"), List.of("title")),
            args -> {
                String title = args.optString("title", "").trim();
                if (title.isEmpty()) return "Erro: 'title' obrigatorio.";
                // Sanitiza o nome derivado do titulo (remove separadores de caminho e afins,
                // evitando que um titulo tipo "../../evil" escreva fora de notes/).
                String safeTitle = title.toLowerCase().replaceAll("[^a-z0-9-_ ]", "").trim();
                if (safeTitle.isEmpty()) return "Erro: titulo invalido (sem caracteres alfanumericos).";
                String fileName = safeTitle.replace(' ', '_') + ".json";
                File file = new File(notesFolder(), fileName);
                if (file.exists()) return "Erro: ja existe uma nota com esse nome: " + fileName;
                JSONObject json = new JSONObject()
                        .put("title", title)
                        .put("content", "<h1>" + title + "</h1><p>Escreva aqui...</p>");
                try {
                    Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                    return "Nota criada: " + fileName;
                } catch (Exception e) {
                    return "Erro ao criar nota: " + e.getMessage();
                }
            });

        // read_note
        add("read_note",
            "Le o titulo e conteudo de uma nota/wiki pelo nome de arquivo.",
            schemaWith(Map.of("fileName", "Nome do arquivo (ex: minha_nota.json)"), List.of("fileName")),
            args -> {
                File file = resolveWithin(notesFolder(), args.optString("fileName", ""));
                if (file == null) return "Erro: nome de arquivo invalido (caminho fora de notes/): " + args.optString("fileName", "");
                if (!file.isFile()) return "Erro: nota nao encontrada: " + args.optString("fileName", "");
                JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                return "Titulo: " + json.optString("title") + "\nConteudo:\n" + json.optString("content");
            });

        // write_note
        Map<String, String> writeNoteProps = new LinkedHashMap<>();
        writeNoteProps.put("fileName", "Nome do arquivo (deve existir)");
        writeNoteProps.put("title", "Novo titulo");
        writeNoteProps.put("content", "Novo conteudo (HTML ou texto)");
        add("write_note",
            "Sobrescreve o titulo e conteudo de uma nota/wiki existente.",
            schemaWith(writeNoteProps, List.of("fileName", "title", "content")),
            args -> {
                File file = resolveWithin(notesFolder(), args.optString("fileName", ""));
                if (file == null) return "Erro: nome de arquivo invalido (caminho fora de notes/): " + args.optString("fileName", "");
                if (!file.isFile()) return "Erro: nota nao existe (crie primeiro): " + args.optString("fileName", "");
                JSONObject json = new JSONObject()
                        .put("title", args.optString("title", ""))
                        .put("content", args.optString("content", ""));
                try {
                    Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                    return "Nota salva: " + args.optString("fileName", "");
                } catch (Exception e) {
                    return "Erro ao salvar nota: " + e.getMessage();
                }
            });
    }

    // Filtro defensivo para listagens: symlinks podem apontar para fora do projeto
    // (exige acesso previo ao filesystem local para plantar, mas e barato de filtrar).
    private static boolean isSymlink(File f) {
        try {
            return Files.isSymbolicLink(f.toPath());
        } catch (Exception e) {
            return false;
        }
    }

    private File notesFolder() {
        File f = new File(projectFolder, "notes");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ----------------------------------------------------------------------
    // Ferramentas de animacao (arquivos .anim.json em assets/animations/;
    // funcionam sem editor vivo — anexar/tocar exige liveGame, ver mais abaixo).
    // ----------------------------------------------------------------------

    private void registerAnimationBaseTools() {
        // list_animations
        add("list_animations",
            "Lista as animacoes (.anim.json) do projeto com nome, loop, curva e numero de frames.",
            objectSchema(),
            args -> {
                List<SpriteAnimation> anims = AnimationIO.loadAll(projectFolder);
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
        add("create_animation",
            "Cria um novo clipe de animacao vazio no projeto (assets/animations/<name>.anim.json).",
            schemaWith(createAnimProps, List.of("name")),
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
                    AnimationIO.save(anim, projectFolder);
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
        add("add_animation_frame",
            "Adiciona um keyframe (sprite + duracao) ao final de uma animacao existente.",
            schemaWith(addFrameProps, List.of("animName", "spritePath", "duration")),
            args -> {
                SpriteAnimation anim = loadAnimationOrNull(args.optString("animName", ""));
                if (anim == null) return "Erro: animacao nao encontrada: " + args.optString("animName", "");
                String spritePath = args.optString("spritePath", "");
                if (resolveInProject(spritePath) == null) return "Erro: spritePath invalido (caminho fora do projeto): " + spritePath;
                double duration = args.optDouble("duration", 0.1);
                anim.addFrame(new AnimationFrame(spritePath, duration));
                try {
                    AnimationIO.save(anim, projectFolder);
                    return "Frame adicionado a '" + anim.getName() + "' (" + anim.getFrames().size() + " frames).";
                } catch (Exception e) {
                    return "Erro ao salvar animacao: " + e.getMessage();
                }
            });

        // read_animation
        add("read_animation",
            "Le a definicao completa de uma animacao (frames, duracoes, loop, curva).",
            schemaWith(Map.of("animName", "Nome da animacao (sem extensao)"), List.of("animName")),
            args -> {
                SpriteAnimation anim = loadAnimationOrNull(args.optString("animName", ""));
                if (anim == null) return "Erro: animacao nao encontrada: " + args.optString("animName", "");
                return anim.toJSON().toString(2);
            });
    }

    // Sanitiza o nome como o AnimationIO faz internamente (privado la), para localizar o arquivo.
    private SpriteAnimation loadAnimationOrNull(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String safe = name.trim().replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
        if (safe.isEmpty()) safe = "animation";
        File file = new File(AnimationIO.getAnimationsFolder(projectFolder), safe + AnimationIO.EXTENSION);
        if (!file.isFile()) return null;
        try {
            return AnimationIO.load(file);
        } catch (Exception e) {
            return null;
        }
    }

    // ----------------------------------------------------------------------
    // Ferramentas de cena e Play (somente com editor vivo)
    // ----------------------------------------------------------------------

    // Instancia a forma concreta pelo tipo (GameObject e abstrato).
    private GameObject newShape(String type, String name, double x, double y, int w, int h) {
        String t = (type == null ? "square" : type.trim().toLowerCase());
        switch (t) {
            case "circle":   return new com.ignis.core.Circle(name, liveGame, x, y, w, h);
            case "triangle": return new com.ignis.core.Triangle(name, liveGame, x, y, w, h);
            case "star":     return new com.ignis.core.Star(name, liveGame, x, y, w, h);
            case "pentagon": return new com.ignis.core.Pentagon(name, liveGame, x, y, w, h);
            case "player":   return new com.ignis.core.Player(name, liveGame, x, y, w, h);
            case "square":
            default:         return new com.ignis.core.Square(name, liveGame, x, y, w, h);
        }
    }

    private GameObject findObject(String name) {
        if (liveGame == null || name == null) return null;
        for (GameObject go : liveGame.getEntities()) {
            if (name.equals(go.getName())) return go;
        }
        return null;
    }

    private void registerSceneTools() {
        // list_scene_objects
        add("list_scene_objects",
            "Lista os GameObjects da cena ativa em ordem de renderizacao (Z-index: menor atras, maior na frente).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                StringBuilder sb = new StringBuilder();
                sb.append("Ordem de renderizacao (Z-order): indices menores sao desenhados primeiro (ficam atras), indices maiores sao desenhados por cima.\n\n");
                java.util.List<GameObject> list = liveGame.getEntities();
                for (int i = 0; i < list.size(); i++) {
                    GameObject go = list.get(i);
                    sb.append("[").append(i).append("] ")
                      .append(go.getName())
                      .append(" @ (").append((int) go.getX()).append(',').append((int) go.getY()).append(')')
                      .append(" ").append(go.getWidth()).append('x').append(go.getHeight())
                      .append(" scripts=").append(go.getScriptNames())
                      .append('\n');
                }
                return sb.length() == 0 ? "(cena vazia)" : sb.toString();
            });

        // create_object
        Map<String, String> createProps = new LinkedHashMap<>();
        createProps.put("name", "Nome do objeto");
        createProps.put("type", "Tipo: square, circle, triangle, star, pentagon, player (padrao: square)");
        createProps.put("x", "Posicao X");
        createProps.put("y", "Posicao Y");
        createProps.put("width", "Largura em px");
        createProps.put("height", "Altura em px");
        add("create_object",
            "Cria um GameObject (forma ou player) e o adiciona a cena ativa.",
            schemaWith(createProps, List.of("name")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                double x = args.optDouble("x", 0);
                double y = args.optDouble("y", 0);
                int w = args.optInt("width", 64);
                int h = args.optInt("height", 64);
                GameObject go = newShape(args.optString("type", "square"), name, x, y, w, h);
                liveGame.addEntity(go);
                if (refreshHook != null) refreshHook.run();
                return "Objeto criado: " + name + " (" + go.getClass().getSimpleName() + ") @ ("
                        + (int) x + "," + (int) y + ") " + w + "x" + h;
            });

        // set_object_transform
        Map<String, String> transformProps = new LinkedHashMap<>();
        transformProps.put("name", "Nome do objeto alvo");
        transformProps.put("x", "Nova posicao X (opcional)");
        transformProps.put("y", "Nova posicao Y (opcional)");
        transformProps.put("width", "Nova largura (opcional)");
        transformProps.put("height", "Nova altura (opcional)");
        transformProps.put("rotation", "Nova rotacao em graus (opcional)");
        add("set_object_transform",
            "Altera posicao/tamanho/rotacao de um GameObject existente.",
            schemaWith(transformProps, List.of("name")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                if (args.has("x")) go.setX(args.optDouble("x"));
                if (args.has("y")) go.setY(args.optDouble("y"));
                if (args.has("width")) go.setWidth(args.optInt("width"));
                if (args.has("height")) go.setHeight(args.optInt("height"));
                if (args.has("rotation")) go.setRotation(args.optDouble("rotation"));
                if (refreshHook != null) refreshHook.run();
                return "Transform atualizado: " + go.getName();
            });

        // set_object_sprite
        Map<String, String> spriteProps = new LinkedHashMap<>();
        spriteProps.put("name", "Nome do objeto");
        spriteProps.put("path", "Caminho do sprite (relativo ao projeto)");
        add("set_object_sprite",
            "Define o sprite (imagem) de um GameObject.",
            schemaWith(spriteProps, List.of("name", "path")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                String path = args.optString("path", "");
                if (resolveInProject(path) == null) return "Erro: path invalido (caminho fora do projeto): " + path;
                go.setSpritePath(path);
                if (refreshHook != null) refreshHook.run();
                return "Sprite definido para " + go.getName() + ": " + path;
            });

        // attach_script
        Map<String, String> attachProps = new LinkedHashMap<>();
        attachProps.put("objectName", "Nome do objeto alvo");
        attachProps.put("scriptName", "Nome do script a anexar");
        add("attach_script",
            "Anexa um IgnisScript a um GameObject da cena.",
            schemaWith(attachProps, List.of("objectName", "scriptName")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                String scriptName = args.optString("scriptName", "").trim();
                if (scriptName.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                ScriptManager sm = liveGame.getScriptManager();
                if (sm == null) { sm = scriptManager(); liveGame.setScriptManager(sm); }
                if (!go.getScriptNames().contains(scriptName)) {
                    go.getScriptNames().add(scriptName);
                    try {
                        com.ignis.core.IgnisScript inst = sm.createScriptInstance(scriptName, go, liveGame);
                        if (inst != null) go.getScripts().add(inst);
                    } catch (Exception ignore) { /* compila no Play se necessario */ }
                }
                if (refreshHook != null) refreshHook.run();
                return "Script '" + scriptName + "' anexado a " + go.getName();
            });

        // delete_object
        add("delete_object",
            "Remove um GameObject da cena ativa pelo nome.",
            schemaWith(Map.of("name", "Nome do objeto a remover"), List.of("name")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                GameObject go = findObject(name);
                if (go == null) return "Erro: objeto nao encontrado: " + name;
                liveGame.removeEntity(go);
                if (refreshHook != null) refreshHook.run();
                return "Objeto removido: " + name;
            });

        // play_game
        add("play_game",
            "Inicia a simulacao (Play) no editor, como apertar o botao Play.",
            objectSchema(),
            args -> {
                if (playHook == null) return "Erro: Play indisponivel.";
                playHook.run();
                return "Play iniciado.";
            });

        // stop_game
        add("stop_game",
            "Para a simulacao e volta ao modo de edicao.",
            objectSchema(),
            args -> {
                if (stopHook == null) return "Erro: Stop indisponivel.";
                stopHook.run();
                return "Simulacao parada (edicao).";
            });

        // save_project
        add("save_project",
            "Salva o projeto atual (sincroniza a cena para o arquivo .ignis).",
            objectSchema(),
            args -> {
                if (saveHook == null) return "Erro: salvar indisponivel.";
                saveHook.run();
                return "Projeto salvo.";
            });

        registerAnimationSceneTools();
        registerPrefabTools();
        registerCollisionTools();
        registerCameraTools();
        registerUiDirectTools();
        registerGameObjectExtraTools();
        registerSceneInfoTools();
        registerWorldTools();
    }

    // ----------------------------------------------------------------------
    // Ferramentas de animacao ligadas a objetos vivos (Animator do GameObject)
    // ----------------------------------------------------------------------

    private void registerAnimationSceneTools() {
        // attach_animation
        Map<String, String> attachAnimProps = new LinkedHashMap<>();
        attachAnimProps.put("objectName", "Nome do objeto na cena");
        attachAnimProps.put("animName", "Nome da animacao a anexar (sem extensao)");
        attachAnimProps.put("setAsDefault", "true para tocar automaticamente ao entrar em Play (padrao: false)");
        add("attach_animation",
            "Anexa uma animacao existente (assets/animations/) ao Animator de um GameObject da cena.",
            schemaWith(attachAnimProps, List.of("objectName", "animName")),
            args -> {
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                SpriteAnimation anim = loadAnimationOrNull(args.optString("animName", ""));
                if (anim == null) return "Erro: animacao nao encontrada: " + args.optString("animName", "");
                Animator animator = go.getOrCreateAnimator();
                animator.addAnimation(anim);
                if (args.optBoolean("setAsDefault", false)) animator.setDefaultAnimation(anim.getName());
                if (refreshHook != null) refreshHook.run();
                return "Animacao '" + anim.getName() + "' anexada a " + go.getName();
            });

        // play_animation
        Map<String, String> playAnimProps = new LinkedHashMap<>();
        playAnimProps.put("objectName", "Nome do objeto na cena");
        playAnimProps.put("animName", "Nome da animacao a tocar (deve ja estar anexada)");
        playAnimProps.put("waitForCurrent", "true para aguardar a animacao atual terminar antes de trocar (padrao: false)");
        add("play_animation",
            "Inicia a reproducao de uma animacao anexada a um GameObject.",
            schemaWith(playAnimProps, List.of("objectName", "animName")),
            args -> {
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                Animator animator = go.getAnimator();
                if (animator == null) return "Erro: objeto nao tem animacoes anexadas. Use attach_animation primeiro.";
                String animName = args.optString("animName", "");
                if (animator.getAnimation(animName) == null) return "Erro: animacao nao anexada a este objeto: " + animName;
                animator.play(animName, args.optBoolean("waitForCurrent", false));
                return "Tocando '" + animName + "' em " + go.getName();
            });

        // stop_animation
        add("stop_animation",
            "Para a animacao de um GameObject e restaura o sprite anterior.",
            schemaWith(Map.of("objectName", "Nome do objeto na cena"), List.of("objectName")),
            args -> {
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                if (go.getAnimator() != null) go.getAnimator().stop();
                go.resetAnimator();
                if (refreshHook != null) refreshHook.run();
                return "Animacao parada em " + go.getName();
            });

        // get_animation_status
        add("get_animation_status",
            "Retorna o estado de animacao de um objeto (animacao atual, se esta tocando, animacoes disponiveis).",
            schemaWith(Map.of("objectName", "Nome do objeto na cena"), List.of("objectName")),
            args -> {
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                Animator animator = go.getAnimator();
                if (animator == null) return "(sem animacoes anexadas)";
                return "Atual: " + animator.getCurrentName() + " | tocando: " + animator.isPlaying()
                        + " | disponiveis: " + animator.getAnimations().keySet();
            });
    }

    // ----------------------------------------------------------------------
    // Ferramentas de Prefabs (com.ignis.core.PrefabManager, via liveGame)
    // ----------------------------------------------------------------------

    private void registerPrefabTools() {
        // list_prefabs
        add("list_prefabs",
            "Lista os prefabs disponiveis no projeto (pasta prefabs/).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                PrefabManager pm = liveGame.getPrefabManager();
                if (pm == null) return "(gerenciador de prefabs indisponivel)";
                List<String> names = pm.listPrefabs();
                return names.isEmpty() ? "(nenhum prefab)" : String.join("\n", names);
            });

        // save_prefab
        Map<String, String> savePrefabProps = new LinkedHashMap<>();
        savePrefabProps.put("objectName", "Nome do objeto da cena a salvar como prefab");
        savePrefabProps.put("prefabName", "Nome para o novo prefab");
        add("save_prefab",
            "Salva um GameObject da cena como um prefab reutilizavel (prefabs/<nome>.prefab.json).",
            schemaWith(savePrefabProps, List.of("objectName", "prefabName")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                PrefabManager pm = liveGame.getPrefabManager();
                if (pm == null) return "Erro: gerenciador de prefabs indisponivel.";
                boolean ok = pm.savePrefab(go, args.optString("prefabName", ""));
                return ok ? "Prefab salvo: " + args.optString("prefabName", "") : "Erro ao salvar prefab.";
            });

        // instantiate_prefab
        Map<String, String> instPrefabProps = new LinkedHashMap<>();
        instPrefabProps.put("prefabName", "Nome do prefab a instanciar");
        instPrefabProps.put("x", "Posicao X (opcional; usa a posicao salva no prefab se omitido)");
        instPrefabProps.put("y", "Posicao Y (opcional; usa a posicao salva no prefab se omitido)");
        add("instantiate_prefab",
            "Instancia um prefab na cena ativa, opcionalmente numa posicao especifica.",
            schemaWith(instPrefabProps, List.of("prefabName")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("prefabName", "").trim();
                if (name.isEmpty()) return "Erro: 'prefabName' obrigatorio.";
                if (args.has("x") != args.has("y")) {
                    return "Erro: informe 'x' e 'y' juntos, ou nenhum dos dois (para usar a posicao salva no prefab).";
                }
                GameObject go;
                if (args.has("x")) {
                    go = liveGame.instantiatePrefab(name, args.optDouble("x"), args.optDouble("y"));
                } else {
                    PrefabManager pm = liveGame.getPrefabManager();
                    go = pm != null ? pm.instantiatePrefab(name) : null;
                    if (go != null) liveGame.addEntity(go);
                }
                if (go == null) return "Erro: nao foi possivel instanciar o prefab: " + name;
                if (refreshHook != null) refreshHook.run();
                return "Prefab instanciado: " + go.getName() + " @ (" + (int) go.getX() + "," + (int) go.getY() + ")";
            });

        // delete_prefab
        add("delete_prefab",
            "Remove um arquivo de prefab do disco.",
            schemaWith(Map.of("prefabName", "Nome do prefab a remover"), List.of("prefabName")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                PrefabManager pm = liveGame.getPrefabManager();
                if (pm == null) return "Erro: gerenciador de prefabs indisponivel.";
                boolean ok = pm.deletePrefab(args.optString("prefabName", ""));
                return ok ? "Prefab removido: " + args.optString("prefabName", "") : "Erro: prefab nao encontrado.";
            });

        // prefab_exists
        add("prefab_exists",
            "Verifica se um prefab existe no disco.",
            schemaWith(Map.of("prefabName", "Nome do prefab a verificar"), List.of("prefabName")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                PrefabManager pm = liveGame.getPrefabManager();
                if (pm == null) return "Erro: gerenciador de prefabs indisponivel.";
                boolean exists = pm.prefabExists(args.optString("prefabName", ""));
                return exists ? "Existe." : "Nao existe.";
            });
    }

    // ----------------------------------------------------------------------
    // Ferramentas de colisao (GameObject.setColliderType/Mode + Collider layer/mask)
    // ----------------------------------------------------------------------

    private void registerCollisionTools() {
        Map<String, String> colliderProps = new LinkedHashMap<>();
        colliderProps.put("objectName", "Nome do objeto na cena");
        colliderProps.put("colliderType", "NONE, AABB, CIRCLE ou POLYGON");
        colliderProps.put("collisionMode", "COLLISION (resposta fisica) ou TRIGGER (so eventos); padrao COLLISION");
        colliderProps.put("layer", "Camada de colisao 0-31 (opcional)");
        colliderProps.put("mask", "Mascara de colisao, bit N = colide com camada N; -1 = todas (opcional)");
        add("set_object_collider",
            "Configura o collider de um GameObject: tipo, modo, camada e mascara de colisao.",
            schemaWith(colliderProps, List.of("objectName", "colliderType")),
            args -> {
                GameObject go = findObject(args.optString("objectName", ""));
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
                if (refreshHook != null) refreshHook.run();
                return "Collider de " + go.getName() + " definido: " + type
                        + (args.has("collisionMode") ? " (" + args.optString("collisionMode") + ")" : "");
            });
    }

    // ----------------------------------------------------------------------
    // Ferramentas de camera (com.ignis.core.Camera + Game.addCamera/setMainCamera)
    // ----------------------------------------------------------------------

    private Camera findCamera(String name) {
        if (liveGame == null || name == null) return null;
        for (Camera c : liveGame.getCameras()) {
            if (name.equals(c.getCameraName()) || name.equals(c.getName())) return c;
        }
        return null;
    }

    private void registerCameraTools() {
        // list_cameras
        add("list_cameras",
            "Lista as cameras da cena ativa (nome, posicao, zoom, se e a ativa).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                List<Camera> cams = liveGame.getCameras();
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
        add("create_camera",
            "Cria uma nova camera e a adiciona a cena ativa.",
            schemaWith(createCamProps, List.of("name")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (findCamera(name) != null) return "Erro: ja existe camera com esse nome: " + name;
                Camera cam = new Camera(name, liveGame, args.optDouble("x", 0), args.optDouble("y", 0));
                cam.setCameraName(name);
                if (args.has("zoom")) cam.setZoom(args.optDouble("zoom"));
                if (args.has("rotation")) cam.setRotation(args.optDouble("rotation"));
                if (args.optBoolean("setActive", false)) {
                    liveGame.setMainCamera(cam);
                } else {
                    liveGame.addCamera(cam);
                }
                if (refreshHook != null) refreshHook.run();
                return "Camera criada: " + name;
            });

        // set_active_camera
        add("set_active_camera",
            "Define qual camera e a principal/ativa da cena.",
            schemaWith(Map.of("name", "Nome da camera a ativar"), List.of("name")),
            args -> {
                Camera cam = findCamera(args.optString("name", ""));
                if (cam == null) return "Erro: camera nao encontrada: " + args.optString("name", "");
                liveGame.setMainCamera(cam);
                if (refreshHook != null) refreshHook.run();
                return "Camera ativa: " + cam.getCameraName();
            });

        // set_camera_transform
        Map<String, String> camTransformProps = new LinkedHashMap<>();
        camTransformProps.put("name", "Nome da camera alvo");
        camTransformProps.put("x", "Nova posicao X (opcional)");
        camTransformProps.put("y", "Nova posicao Y (opcional)");
        camTransformProps.put("zoom", "Novo zoom (opcional)");
        camTransformProps.put("rotation", "Nova rotacao em graus (opcional)");
        add("set_camera_transform",
            "Altera posicao/zoom/rotacao de uma camera existente.",
            schemaWith(camTransformProps, List.of("name")),
            args -> {
                Camera cam = findCamera(args.optString("name", ""));
                if (cam == null) return "Erro: camera nao encontrada: " + args.optString("name", "");
                if (args.has("x") && args.has("y")) cam.setPosition(args.optDouble("x"), args.optDouble("y"));
                else if (args.has("x")) cam.setX(args.optDouble("x"));
                else if (args.has("y")) cam.setY(args.optDouble("y"));
                if (args.has("zoom")) cam.setZoom(args.optDouble("zoom"));
                if (args.has("rotation")) cam.setRotation(args.optDouble("rotation"));
                if (refreshHook != null) refreshHook.run();
                return "Transform atualizado: " + cam.getCameraName();
            });

        // convert_coordinates
        Map<String, String> convertProps = new LinkedHashMap<>();
        convertProps.put("direction", "'world_to_screen' ou 'screen_to_world'");
        convertProps.put("x", "Coordenada X de entrada");
        convertProps.put("y", "Coordenada Y de entrada");
        add("convert_coordinates",
            "Converte coordenadas entre mundo e tela usando a camera ativa (util para mira/HUD).",
            schemaWith(convertProps, List.of("direction", "x", "y")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = liveGame.getActiveCamera();
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
        add("set_camera_follow",
            "Faz a camera ativa seguir suavemente o centro de um objeto durante o Play.",
            schemaWith(followProps, List.of("targetName")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                GameObject target = findObject(args.optString("targetName", ""));
                if (target == null) return "Erro: objeto nao encontrado: " + args.optString("targetName", "");
                cam.follow(target, args.optDouble("smoothing", 0.15));
                return "Camera '" + cam.getCameraName() + "' seguindo " + target.getName();
            });

        // stop_camera_follow
        add("stop_camera_follow",
            "Faz a camera ativa parar de seguir qualquer objeto.",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.stopFollow();
                return "Camera '" + cam.getCameraName() + "' parou de seguir.";
            });

        // camera_shake
        Map<String, String> shakeProps = new LinkedHashMap<>();
        shakeProps.put("intensity", "Amplitude do tremor em px (ex: 8)");
        shakeProps.put("duration", "Duracao em segundos (ex: 0.4)");
        add("camera_shake",
            "Dispara um tremor na camera ativa com decaimento linear (efeito de impacto).",
            schemaWith(shakeProps, List.of("intensity", "duration")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = liveGame.getActiveCamera();
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
        add("set_camera_bounds",
            "Limita o centro da camera ativa a um retangulo do mundo (evita mostrar fora do nivel).",
            schemaWith(boundsProps, List.of("minX", "minY", "maxX", "maxY")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.setBounds(args.optDouble("minX"), args.optDouble("minY"),
                              args.optDouble("maxX"), args.optDouble("maxY"));
                return "Limites da camera definidos.";
            });

        // clear_camera_bounds
        add("clear_camera_bounds",
            "Remove os limites de movimento da camera ativa.",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera cam = liveGame.getActiveCamera();
                if (cam == null) return "Erro: nenhuma camera ativa.";
                cam.clearBounds();
                return "Limites da camera removidos.";
            });
    }

    // ----------------------------------------------------------------------
    // Ferramentas de UI in-game direta (sem precisar escrever um IgnisScript).
    // Usa o mesmo UICanvas do jogo (com.ignis.core.ui) por baixo.
    // ----------------------------------------------------------------------

    private UICanvas ensureUiCanvas() {
        if (liveGame == null) return null;
        UICanvas canvas = liveGame.getUICanvas();
        if (canvas == null) {
            canvas = new UICanvas();
            liveGame.setUICanvas(canvas);
        }
        return canvas;
    }

    private void registerUiDirectTools() {
        // ui_create_label
        Map<String, String> labelProps = new LinkedHashMap<>();
        labelProps.put("name", "Nome unico do elemento (para consultar/alterar depois)");
        labelProps.put("text", "Texto a exibir");
        labelProps.put("x", "Posicao X em pixels de tela (padrao 20)");
        labelProps.put("y", "Posicao Y em pixels de tela (padrao 20)");
        labelProps.put("width", "Largura em px (padrao 240)");
        labelProps.put("height", "Altura em px (padrao 26)");
        labelProps.put("color", "Cor do texto em hex, ex: #FFFFFF (padrao branco)");
        add("ui_create_label",
            "Cria um texto (label) na UI in-game, sem precisar de script. Requer Play para aparecer.",
            schemaWith(labelProps, List.of("name", "text")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                UILabel label = new UILabel(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 240), args.optDouble("height", 26));
                label.setName(args.optString("name", ""));
                if (args.has("color")) label.setTextColor(safeColor(args.optString("color"), Color.WHITE));
                canvas.addChild(label);
                return "Label criado: " + args.optString("name", "");
            });

        // ui_create_button
        Map<String, String> buttonProps = new LinkedHashMap<>();
        buttonProps.put("name", "Nome unico do elemento");
        buttonProps.put("text", "Texto do botao");
        buttonProps.put("x", "Posicao X (padrao 20)");
        buttonProps.put("y", "Posicao Y (padrao 20)");
        buttonProps.put("width", "Largura em px (padrao 150)");
        buttonProps.put("height", "Altura em px (padrao 40)");
        buttonProps.put("removeOnClick", "true para o botao se auto-remover ao ser clicado (padrao false)");
        add("ui_create_button",
            "Cria um botao na UI in-game, sem precisar de script. Requer Play para aparecer/clicar.",
            schemaWith(buttonProps, List.of("name", "text")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                UIButton btn = new UIButton(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 150), args.optDouble("height", 40));
                btn.setName(args.optString("name", ""));
                if (args.optBoolean("removeOnClick", false)) {
                    btn.setOnClick(() -> canvas.removeChild(btn));
                }
                canvas.addChild(btn);
                return "Botao criado: " + args.optString("name", "");
            });

        // ui_create_progressbar
        Map<String, String> pbProps = new LinkedHashMap<>();
        pbProps.put("name", "Nome unico do elemento");
        pbProps.put("x", "Posicao X (padrao 20)");
        pbProps.put("y", "Posicao Y (padrao 20)");
        pbProps.put("width", "Largura em px (padrao 200)");
        pbProps.put("height", "Altura em px (padrao 22)");
        pbProps.put("value", "Valor atual (padrao igual ao maxValue, ou 100)");
        pbProps.put("maxValue", "Valor maximo (padrao 100)");
        pbProps.put("fillColor", "Cor de preenchimento em hex (padrao verde)");
        add("ui_create_progressbar",
            "Cria uma barra de progresso (HP, mana, loading...) na UI in-game, sem precisar de script.",
            schemaWith(pbProps, List.of("name")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                float max = (float) args.optDouble("maxValue", 100);
                float value = (float) args.optDouble("value", max);
                UIProgressBar bar = new UIProgressBar(args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 200), args.optDouble("height", 22));
                bar.setName(args.optString("name", ""));
                bar.setValue(value, max);
                bar.setFillColor(safeColor(args.optString("fillColor", ""), new Color(60, 190, 90)));
                canvas.addChild(bar);
                return "Barra de progresso criada: " + args.optString("name", "");
            });

        // ui_create_panel
        Map<String, String> panelProps = new LinkedHashMap<>();
        panelProps.put("name", "Nome unico do elemento");
        panelProps.put("x", "Posicao X (padrao 20)");
        panelProps.put("y", "Posicao Y (padrao 20)");
        panelProps.put("width", "Largura em px (padrao 300)");
        panelProps.put("height", "Altura em px (padrao 200)");
        panelProps.put("backgroundColor", "Cor de fundo em hex (padrao cinza escuro translucido)");
        panelProps.put("layout", "NONE, VERTICAL, HORIZONTAL ou GRID (padrao NONE)");
        add("ui_create_panel",
            "Cria um painel container na UI in-game, sem precisar de script.",
            schemaWith(panelProps, List.of("name")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                UIPanel panel = new UIPanel(args.optString("name", ""), args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 300), args.optDouble("height", 200));
                if (args.has("backgroundColor")) panel.setBackgroundColor(safeColor(args.optString("backgroundColor"), null));
                try {
                    panel.setLayout(UIPanel.Layout.valueOf(args.optString("layout", "NONE").trim().toUpperCase()));
                } catch (IllegalArgumentException iae) {
                    return "Erro: layout invalido (use NONE, VERTICAL, HORIZONTAL ou GRID).";
                }
                canvas.addChild(panel);
                return "Painel criado: " + args.optString("name", "");
            });

        // ui_set_text
        add("ui_set_text",
            "Altera o texto de um UILabel ou UIButton ja criado.",
            schemaWith(new LinkedHashMap<>(Map.of("name", "Nome do elemento", "text", "Novo texto")),
                    List.of("name", "text")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                if (el instanceof UILabel) { ((UILabel) el).setText(args.optString("text", "")); return "Texto atualizado."; }
                if (el instanceof UIButton) { ((UIButton) el).setText(args.optString("text", "")); return "Texto atualizado."; }
                return "Erro: elemento nao suporta texto (tipo: " + el.getType() + ").";
            });

        // ui_set_progress_value
        Map<String, String> setProgressProps = new LinkedHashMap<>();
        setProgressProps.put("name", "Nome da barra de progresso");
        setProgressProps.put("value", "Novo valor atual");
        setProgressProps.put("maxValue", "Novo valor maximo (opcional; mantem o atual se omitido)");
        add("ui_set_progress_value",
            "Atualiza o valor (e opcionalmente o maximo) de uma barra de progresso existente.",
            schemaWith(setProgressProps, List.of("name", "value")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (!(el instanceof UIProgressBar)) return "Erro: barra de progresso nao encontrada: " + args.optString("name", "");
                UIProgressBar bar = (UIProgressBar) el;
                float value = (float) args.optDouble("value", 0);
                float max = args.has("maxValue") ? (float) args.optDouble("maxValue") : bar.getMaxValue();
                bar.setValue(value, max);
                return "Valor atualizado: " + value + "/" + max;
            });

        // ui_remove_element
        add("ui_remove_element",
            "Remove um elemento de UI (label, botao, barra, painel) pelo nome.",
            schemaWith(Map.of("name", "Nome do elemento a remover"), List.of("name")),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                UIComponent parent = el.getParent();
                if (parent != null) parent.removeChild(el); else canvas.removeChild(el);
                return "Elemento removido: " + args.optString("name", "");
            });

        // ui_clear_all
        add("ui_clear_all",
            "Remove todos os elementos da UI in-game atual.",
            objectSchema(),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                canvas.clearChildren();
                return "UI limpa.";
            });

        // ui_list_elements
        add("ui_list_elements",
            "Lista os elementos atuais da UI in-game (nome, tipo, posicao, tamanho).",
            objectSchema(),
            args -> {
                UICanvas canvas = ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                List<UIComponent> children = canvas.getChildren();
                if (children.isEmpty()) return "(nenhum elemento de UI)";
                StringBuilder sb = new StringBuilder();
                for (UIComponent c : children) {
                    sb.append(c.getName()).append(" [").append(c.getType()).append("] @ (")
                      .append((int) c.getX()).append(',').append((int) c.getY()).append(") ")
                      .append((int) c.getWidth()).append('x').append((int) c.getHeight()).append('\n');
                }
                return sb.toString();
            });
    }

    // ----------------------------------------------------------------------
    // Extras de GameObject (visibilidade, cor, z-order, tipo/busca, scripts, cena)
    // ----------------------------------------------------------------------

    private void registerGameObjectExtraTools() {
        // set_object_visible
        add("set_object_visible",
            "Mostra ou esconde um GameObject (afeta apenas a renderizacao).",
            schemaWith(new LinkedHashMap<>(Map.of("name", "Nome do objeto", "visible", "true para mostrar, false para esconder")),
                    List.of("name", "visible")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.setVisible(args.optBoolean("visible", true));
                if (refreshHook != null) refreshHook.run();
                return (args.optBoolean("visible", true) ? "Visivel: " : "Escondido: ") + go.getName();
            });

        // set_object_name_color
        add("set_object_name_color",
            "Define a cor de exibicao do nome do objeto na hierarquia do editor.",
            schemaWith(Map.of("name", "Nome do objeto", "color", "Cor em hex, ex: #FF8800"), List.of("name", "color")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.setNameColor(safeColor(args.optString("color", ""), Color.WHITE));
                if (refreshHook != null) refreshHook.run();
                return "Cor do nome atualizada: " + go.getName();
            });

        // reorder_object_z
        add("reorder_object_z",
            "Altera o zIndex (profundidade de render) de um objeto: 'top', 'bottom', 'up', 'down' ou um valor numerico. Maior zIndex = na frente; empate mantem a ordem da hierarquia.",
            schemaWith(Map.of("name", "Nome do objeto", "position", "'top', 'bottom', 'up', 'down' ou zIndex numerico"),
                    List.of("name", "position")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                String pos = args.optString("position", "").trim().toLowerCase();
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (GameObject e : liveGame.getEntities()) {
                    minZ = Math.min(minZ, e.getZIndex());
                    maxZ = Math.max(maxZ, e.getZIndex());
                }
                switch (pos) {
                    case "top": go.setZIndex((maxZ == Integer.MIN_VALUE ? 0 : maxZ) + 1); break;
                    case "bottom": go.setZIndex((minZ == Integer.MAX_VALUE ? 0 : minZ) - 1); break;
                    case "up": go.setZIndex(go.getZIndex() + 1); break;
                    case "down": go.setZIndex(go.getZIndex() - 1); break;
                    default:
                        try {
                            go.setZIndex(Integer.parseInt(pos));
                        } catch (NumberFormatException nfe) {
                            return "Erro: 'position' deve ser top/bottom/up/down ou um zIndex numerico.";
                        }
                }
                if (refreshHook != null) refreshHook.run();
                return "zIndex de " + go.getName() + " -> " + go.getZIndex();
            });

        // set_object_visual (Fase B: opacity, flip, escala visual)
        Map<String, String> visualProps = new LinkedHashMap<>();
        visualProps.put("name", "Nome do objeto");
        visualProps.put("opacity", "Opacidade 0.0-1.0 (opcional)");
        visualProps.put("flipX", "Espelhar horizontalmente (true/false, opcional)");
        visualProps.put("flipY", "Espelhar verticalmente (true/false, opcional)");
        visualProps.put("scaleX", "Multiplicador visual de largura (opcional)");
        visualProps.put("scaleY", "Multiplicador visual de altura (opcional)");
        add("set_object_visual",
            "Ajusta as propriedades visuais de um objeto: opacidade, espelhamento (flip) e escala visual.",
            schemaWith(visualProps, List.of("name")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                if (args.has("opacity")) go.setOpacity(args.optDouble("opacity"));
                if (args.has("flipX")) go.setFlipX(args.optBoolean("flipX"));
                if (args.has("flipY")) go.setFlipY(args.optBoolean("flipY"));
                if (args.has("scaleX")) go.setScaleX(args.optDouble("scaleX"));
                if (args.has("scaleY")) go.setScaleY(args.optDouble("scaleY"));
                if (refreshHook != null) refreshHook.run();
                return "Visual atualizado: " + go.getName() + " (opacity=" + go.getOpacity()
                        + " flipX=" + go.isFlipX() + " flipY=" + go.isFlipY()
                        + " scaleX=" + go.getScaleX() + " scaleY=" + go.getScaleY() + ")";
            });

        // get_object_info
        add("get_object_info",
            "Retorna informacoes completas de um GameObject: transform, tipo, visibilidade, sprite, scripts e collider.",
            schemaWith(Map.of("name", "Nome do objeto"), List.of("name")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                StringBuilder sb = new StringBuilder();
                sb.append("nome: ").append(go.getName()).append('\n');
                sb.append("tipo: ").append(go.getType()).append('\n');
                sb.append("posicao: (").append(go.getX()).append(", ").append(go.getY()).append(")\n");
                sb.append("tamanho: ").append(go.getWidth()).append('x').append(go.getHeight()).append('\n');
                sb.append("rotacao: ").append(go.getRotation()).append('\n');
                sb.append("visivel: ").append(go.isVisible()).append('\n');
                sb.append("sprite: ").append(go.getSpritePath()).append('\n');
                sb.append("scripts: ").append(go.getScriptNames()).append('\n');
                sb.append("collider: ").append(go.getColliderType())
                  .append(go.hasCollider() ? " (" + go.getCollisionMode() + ")" : "");
                return sb.toString();
            });

        // find_objects_by_type
        add("find_objects_by_type",
            "Busca objetos da cena por tipo (ex: 'Square', 'Circle', 'Player').",
            schemaWith(Map.of("type", "Nome do tipo/classe a buscar"), List.of("type")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                String type = args.optString("type", "").trim();
                StringBuilder sb = new StringBuilder();
                for (GameObject go : liveGame.getEntities()) {
                    if (go.getType().equalsIgnoreCase(type)) {
                        sb.append(go.getName()).append(" @ (").append((int) go.getX()).append(',').append((int) go.getY()).append(")\n");
                    }
                }
                return sb.length() == 0 ? "(nenhum objeto do tipo " + type + ")" : sb.toString();
            });

        // remove_script_from_object
        add("remove_script_from_object",
            "Remove um script anexado de um GameObject pelo nome.",
            schemaWith(new LinkedHashMap<>(Map.of("objectName", "Nome do objeto", "scriptName", "Nome do script a remover")),
                    List.of("objectName", "scriptName")),
            args -> {
                GameObject go = findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                String scriptName = args.optString("scriptName", "");
                if (!go.getScriptNames().contains(scriptName)) return "Erro: script nao anexado: " + scriptName;
                go.removeScriptByName(scriptName);
                if (refreshHook != null) refreshHook.run();
                return "Script removido: " + scriptName + " de " + go.getName();
            });

        // clear_scene
        add("clear_scene",
            "Remove todos os GameObjects da cena ativa.",
            schemaWith(Map.of("preserveCameras", "true para manter as cameras (padrao true; cameras ja ficam fora da lista de objetos)"), List.of()),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                liveGame.clearEntities();
                if (!args.optBoolean("preserveCameras", true)) {
                    for (Camera c : new ArrayList<>(liveGame.getCameras())) liveGame.removeCamera(c);
                }
                if (refreshHook != null) refreshHook.run();
                return "Cena limpa.";
            });
    }

    // ----------------------------------------------------------------------
    // Informacoes gerais da cena/jogo vivo
    // ----------------------------------------------------------------------

    private void registerSceneInfoTools() {
        add("get_scene_info",
            "Retorna um resumo da cena ativa: total de objetos, cameras e estado do jogo (edicao/play).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                Camera active = liveGame.getActiveCamera();
                return "projeto: " + projectFolder.getName()
                        + "\nestado: " + liveGame.getGameState()
                        + "\nobjetos: " + liveGame.getEntities().size()
                        + "\ncameras: " + liveGame.getCameras().size()
                        + "\ncamera ativa: " + (active != null ? active.getCameraName() : "(nenhuma)");
            });
    }

    // ----------------------------------------------------------------------
    // Sistema de mundos (Fase 1: limites do mapa + barreiras em grade)
    // ----------------------------------------------------------------------

    private void registerWorldTools() {
        // set_world_bounds
        Map<String, String> boundsProps = new LinkedHashMap<>();
        boundsProps.put("minX", "Limite esquerdo do mapa (mundo)");
        boundsProps.put("minY", "Limite superior");
        boundsProps.put("maxX", "Limite direito");
        boundsProps.put("maxY", "Limite inferior");
        add("set_world_bounds",
            "Define os limites do mapa (retangulo). Objetos com world_collision e a camera ficam contidos nele.",
            schemaWith(boundsProps, List.of("minX", "minY", "maxX", "maxY")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                World w = liveGame.getOrCreateWorld();
                w.setBounds(args.optDouble("minX"), args.optDouble("minY"),
                            args.optDouble("maxX"), args.optDouble("maxY"));
                if (refreshHook != null) refreshHook.run();
                return "Limites do mundo: (" + (int) w.getMinX() + "," + (int) w.getMinY() + ") -> ("
                        + (int) w.getMaxX() + "," + (int) w.getMaxY() + ")";
            });

        // clear_world_bounds
        add("clear_world_bounds",
            "Remove os limites do mapa (o mundo passa a ser infinito).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                if (liveGame.getWorld() != null) liveGame.getWorld().clearBounds();
                if (refreshHook != null) refreshHook.run();
                return "Limites do mundo removidos.";
            });

        // set_world_grid
        add("set_world_grid",
            "Define o tamanho (px) das celulas da grade de barreiras.",
            schemaWith(Map.of("cellSize", "Tamanho da celula em px (ex: 64)"), List.of("cellSize")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                World w = liveGame.getOrCreateWorld();
                w.setCellSize(args.optInt("cellSize", 64));
                if (refreshHook != null) refreshHook.run();
                return "Tamanho da celula: " + w.getCellSize() + "px";
            });

        // block_rect
        Map<String, String> rectProps = new LinkedHashMap<>();
        rectProps.put("x", "X do canto do retangulo (mundo)");
        rectProps.put("y", "Y do canto");
        rectProps.put("width", "Largura do retangulo");
        rectProps.put("height", "Altura do retangulo");
        add("block_rect",
            "Marca como barreira (solido) todas as celulas que tocam um retangulo do mundo — 'desenhar' uma parede.",
            schemaWith(rectProps, List.of("x", "y", "width", "height")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                World w = liveGame.getOrCreateWorld();
                int n = w.blockRect(args.optDouble("x"), args.optDouble("y"),
                                    args.optDouble("width"), args.optDouble("height"));
                if (refreshHook != null) refreshHook.run();
                return "Barreira aplicada (" + n + " celulas). Total: " + w.getBlockedCount();
            });

        // unblock_rect
        add("unblock_rect",
            "Remove barreiras de todas as celulas que tocam um retangulo do mundo.",
            schemaWith(rectProps, List.of("x", "y", "width", "height")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                if (liveGame.getWorld() == null) return "Sem mundo definido.";
                int n = liveGame.getWorld().unblockRect(args.optDouble("x"), args.optDouble("y"),
                                    args.optDouble("width"), args.optDouble("height"));
                if (refreshHook != null) refreshHook.run();
                return "Barreiras removidas (" + n + " celulas). Total: " + liveGame.getWorld().getBlockedCount();
            });

        // block_cell
        Map<String, String> cellProps = new LinkedHashMap<>();
        cellProps.put("col", "Coluna da celula (indice inteiro)");
        cellProps.put("row", "Linha da celula (indice inteiro)");
        add("block_cell",
            "Marca uma unica celula da grade como barreira (por indice col,row).",
            schemaWith(cellProps, List.of("col", "row")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                World w = liveGame.getOrCreateWorld();
                w.blockCell(args.optInt("col"), args.optInt("row"));
                if (refreshHook != null) refreshHook.run();
                return "Celula (" + args.optInt("col") + "," + args.optInt("row") + ") bloqueada. Total: " + w.getBlockedCount();
            });

        // unblock_cell
        add("unblock_cell",
            "Remove a barreira de uma unica celula da grade (por indice col,row).",
            schemaWith(cellProps, List.of("col", "row")),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                if (liveGame.getWorld() == null) return "Sem mundo definido.";
                liveGame.getWorld().unblockCell(args.optInt("col"), args.optInt("row"));
                if (refreshHook != null) refreshHook.run();
                return "Celula (" + args.optInt("col") + "," + args.optInt("row") + ") liberada.";
            });

        // clear_barriers
        add("clear_barriers",
            "Remove todas as barreiras do mundo (mantem os limites).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                if (liveGame.getWorld() != null) liveGame.getWorld().clearBarriers();
                if (refreshHook != null) refreshHook.run();
                return "Barreiras limpas.";
            });

        // set_object_world_collision
        Map<String, String> wcProps = new LinkedHashMap<>();
        wcProps.put("name", "Nome do objeto (ex: Hero)");
        wcProps.put("enabled", "true para o objeto colidir com limites/barreiras do mundo");
        add("set_object_world_collision",
            "Liga/desliga a colisao de um objeto com os limites e barreiras do mundo (tipicamente o jogador).",
            schemaWith(wcProps, List.of("name", "enabled")),
            args -> {
                GameObject go = findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.setWorldCollision(args.optBoolean("enabled", true));
                return "world_collision de " + go.getName() + " = " + go.isWorldCollision();
            });

        // set_world_property
        Map<String, String> wpProps = new LinkedHashMap<>();
        wpProps.put("name", "Nome do mundo (opcional)");
        wpProps.put("ambientColor", "Cor ambiente em hex, ex: #204060 (opcional; vazio remove)");
        add("set_world_property",
            "Ajusta propriedades do mundo: nome e cor ambiente.",
            schemaWith(wpProps, List.of()),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                World w = liveGame.getOrCreateWorld();
                if (args.has("name")) w.setName(args.optString("name"));
                if (args.has("ambientColor")) {
                    String hex = args.optString("ambientColor", "").trim();
                    w.setAmbientColor(hex.isEmpty() ? null : safeColor(hex, null));
                }
                return "Mundo '" + w.getName() + "' atualizado.";
            });

        // get_world_info
        add("get_world_info",
            "Retorna o estado do mundo: nome, limites, tamanho da celula e numero de barreiras.",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                World w = liveGame.getWorld();
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

    // Resolve um caminho relativo garantindo que permaneca dentro do projeto (anti path-traversal).
    private File resolveInProject(String relative) {
        return resolveWithin(projectFolder, relative);
    }

    // Resolve um caminho relativo garantindo que o resultado fique DENTRO de 'base'
    // (nao apenas com o mesmo prefixo textual — evita escapar para uma pasta irma,
    // ex: base "Project" nao deve aceitar um alvo resolvido em "ProjectEvil").
    private static File resolveWithin(File base, String relative) {
        if (relative == null || relative.trim().isEmpty()) return null;
        try {
            File baseCanon = base.getCanonicalFile();
            File target = new File(baseCanon, relative).getCanonicalFile();
            String basePath = baseCanon.getPath();
            String targetPath = target.getPath();
            if (targetPath.equals(basePath) || targetPath.startsWith(basePath + File.separator)) return target;
        } catch (Exception ignore) { /* fallthrough */ }
        return null;
    }

    private static void buildTree(File dir, String prefix, StringBuilder sb) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File c : children) {
            if (c.getName().startsWith(".")) continue;
            sb.append(prefix).append(c.isDirectory() ? "[D] " : "    ").append(c.getName()).append('\n');
            if (c.isDirectory()) buildTree(c, prefix + "  ", sb);
        }
    }
}
