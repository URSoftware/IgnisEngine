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

    final File projectFolder;
    private final Map<String, ToolDef> tools = new LinkedHashMap<>();
    private ScriptManager projectScriptManager;

    // Ferramentas que MUTAM a cena/mundo/camera: quando o editor e convidado numa
    // sessao de colaboracao, sao encaminhadas ao host (host-autoritativo). As
    // demais (list_/get_/read_, audio, coordenacao, scripts) rodam localmente.
    private static final java.util.Set<String> FORWARD_TO_HOST = java.util.Set.of(
            "create_object", "delete_object", "set_object_transform", "set_object_sprite",
            "set_sprite_region",
            "set_object_visual", "set_object_visible", "set_object_name_color", "reorder_object_z",
            "attach_script", "remove_script_from_object", "set_object_collider",
            "set_object_world_collision", "clear_scene", "instantiate_prefab",
            "play_game", "stop_game", "save_project",
            "attach_animation", "play_animation", "stop_animation",
            "set_camera_follow", "stop_camera_follow", "camera_shake", "set_camera_bounds",
            "clear_camera_bounds", "set_active_camera", "create_camera", "set_camera_transform",
            "set_world_bounds", "clear_world_bounds", "set_world_grid", "block_rect", "unblock_rect",
            "block_cell", "unblock_cell", "clear_barriers", "set_world_property",
            "create_background_layer", "set_parallax_factor",
            "create_particle_emitter", "particle_burst", "set_particle_emitting",
            "create_tilemap", "add_tilemap_layer", "set_tile", "paint_tiles", "clear_tilemap_layer",
            "create_text_object", "set_text",
            "create_light_object", "set_light_properties", "set_scene_ambient_light",
            "set_parent", "clear_parent");

    // Contexto vivo do editor (opcional): presente quando o bridge roda dentro do
    // editor JavaFX, habilitando ferramentas de cena e de Play. Nulo no modo headless.
    Game liveGame;
    Runnable playHook, stopHook, saveHook;
    Runnable refreshHook;

    // Captura da janela inteira do editor, injetada pelo IgnisEditorApp (inversao de
    // dependencia: o registry nao conhece JavaFX — o snapshot FX vive no editor).
    // Executada na FX thread (call() ja roda todo handler la). Nula no headless.
    java.util.function.Supplier<java.awt.image.BufferedImage> windowCaptureSupplier;

    /** Injeta o capturador da janela do editor (snapshot JavaFX -> BufferedImage). */
    public void setWindowCaptureSupplier(java.util.function.Supplier<java.awt.image.BufferedImage> supplier) {
        this.windowCaptureSupplier = supplier;
    }

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
        ScriptManager manager = scriptManager();
        game.setScriptManager(manager);
        if (game.getPrefabManager() == null) {
            game.setPrefabManager(new PrefabManager(projectFolder, game, manager));
        }
        this.playHook = play;
        this.stopHook = stop;
        this.refreshHook = refresh;
        this.saveHook = save;
        registerEditorTools();
    }

    // Ferramentas que exigem o editor vivo (liveGame): registradas em grupos por
    // dominio — cada classe *Tools cobre um assunto (Fase F, passo 10).
    private void registerEditorTools() {
        new SceneObjectTools(this).registerAll();
        new SpriteAnimationTools(this).registerSceneAnimationTools();
        new CameraTools(this).registerAll();
        new UiTools(this).registerAll();
        new WorldTools(this).registerAll();
        new ContentTools(this).registerAll();
        new EditorWorkflowTools(this).registerCaptureTools();
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

        // Colaboracao em tempo real (host-autoritativo): se este editor esta como
        // CONVIDADO, ferramentas que mutam a cena sao encaminhadas ao host, que
        // aplica e rebroadcasta. O convidado ve o resultado pelo snapshot.
        if (FORWARD_TO_HOST.contains(name) && CollabSession.get().getRole() == CollabSession.Role.GUEST) {
            CollabBridge.sendCommand(name, safeArgs);
            IgnisLogger.info("[Collab] '" + name + "' encaminhado ao host.");
            return "[colaboracao] '" + name + "' encaminhado ao host (aplicado na cena autoritativa).";
        }

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
        IgnisLogger.info("[MCP] " + name + argsPreview + " -> "
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

    void add(String name, String description, JSONObject schema, ToolHandler handler) {
        tools.put(name, new ToolDef(name, description, schema, handler));
    }

    static JSONObject objectSchema() {
        return new JSONObject().put("type", "object");
    }

    static JSONObject schemaWith(Map<String, String> props, List<String> required) {
        JSONObject schema = new JSONObject().put("type", "object");
        JSONObject properties = new JSONObject();
        for (Map.Entry<String, String> e : props.entrySet()) {
            properties.put(e.getKey(), new JSONObject().put("type", "string").put("description", e.getValue()));
        }
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) schema.put("required", new JSONArray(required));
        return schema;
    }

    ScriptManager scriptManager() {
        if (projectScriptManager == null) {
            projectScriptManager = new ScriptManager(projectFolder);
        }
        return projectScriptManager;
    }

    // Coordenacao multi-agente: se 'agent' foi informado e o recurso esta reservado
    // por OUTRO agente, retorna a mensagem de conflito (a ferramenta deve abortar).
    // Retorna null quando pode prosseguir (sem agente, recurso livre, ou dono e voce).
    private String coordConflict(String resource, String agent) {
        if (agent == null || agent.trim().isEmpty()) return null;
        String holder = McpCoordination.get().holderOf(resource);
        if (holder != null && !holder.equalsIgnoreCase(agent.trim())) {
            return "CONFLITO: '" + resource + "' esta reservado por " + holder
                    + ". Combine pelo mural (send_message) antes de editar.";
        }
        return null;
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
        Map<String, String> writeScriptProps = new LinkedHashMap<>();
        writeScriptProps.put("scriptName", "Nome do script (ex: PlayerController)");
        writeScriptProps.put("content", "Conteudo Java completo do script");
        writeScriptProps.put("agent", "Seu nome de agente (opcional; respeita claim de outro agente)");
        add("write_script",
            "Sobrescreve o conteudo-fonte de um script existente. Respeita claim (coordenacao multi-agente) se 'agent' for informado.",
            schemaWith(writeScriptProps, List.of("scriptName", "content")),
            args -> {
                String name = args.optString("scriptName", "").trim();
                String content = args.optString("content", "");
                if (name.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                String conflict = coordConflict("script:" + name, args.optString("agent", ""));
                if (conflict != null) return conflict;
                boolean ok = scriptManager().saveScriptContent(name, content);
                return ok ? "Script salvo: " + name : "Erro ao salvar script: " + name;
            });

        // create_script
        Map<String, String> createScriptProps = new LinkedHashMap<>();
        createScriptProps.put("scriptName", "Nome do novo script (ex: EnemyAI)");
        createScriptProps.put("agent", "Seu nome de agente (opcional; respeita claim de outro agente)");
        add("create_script",
            "Cria um novo script Java a partir do template padrao do motor.",
            schemaWith(createScriptProps, List.of("scriptName")),
            args -> {
                String name = args.optString("scriptName", "").trim();
                if (name.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                String conflict = coordConflict("script:" + name, args.optString("agent", ""));
                if (conflict != null) return conflict;
                boolean ok = scriptManager().createNewScript(name);
                return ok ? "Script criado: " + name : "Erro: script ja existe ou nome invalido: " + name;
            });

        // compile_project
        add("compile_project",
            "Compila todos os scripts do projeto e retorna o total compilado.",
            objectSchema(),
            args -> {
                ScriptManager manager = scriptManager();
                int compiled = manager.compileAllScripts();
                if (liveGame != null) {
                    liveGame.setScriptManager(manager);
                    if (liveGame.getPrefabManager() == null) {
                        liveGame.setPrefabManager(new PrefabManager(projectFolder, liveGame, manager));
                    }
                }
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

        new SoundTools(this).registerAll();
        new EditorWorkflowTools(this).registerHeadlessTools();
        new SpriteAnimationTools(this).registerBaseTools();
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

    static Color safeColor(String hex, Color fallback) {
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


    static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    void listAudioDir(StringBuilder sb, String relDir) {
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


    // Filtro defensivo para listagens: symlinks podem apontar para fora do projeto
    // (exige acesso previo ao filesystem local para plantar, mas e barato de filtrar).
    static boolean isSymlink(File f) {
        try {
            return Files.isSymbolicLink(f.toPath());
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // Coordenacao multi-agente (varias IAs trabalhando juntas pelo MCP).
    // Estado compartilhado em McpCoordination. Ferramentas base (sempre ativas).
    // ----------------------------------------------------------------------


    File notesFolder() {
        File f = new File(projectFolder, "notes");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ----------------------------------------------------------------------
    // Ferramentas de animacao (arquivos .anim.json em assets/animations/;
    // funcionam sem editor vivo — anexar/tocar exige liveGame, ver mais abaixo).
    // ----------------------------------------------------------------------


    // Sanitiza o nome como o AnimationIO faz internamente (privado la), para localizar o arquivo.
    SpriteAnimation loadAnimationOrNull(String name) {
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
    GameObject newShape(String type, String name, double x, double y, int w, int h) {
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

    GameObject findObject(String name) {
        if (liveGame == null || name == null) return null;
        for (GameObject go : liveGame.getEntities()) {
            if (name.equals(go.getName())) return go;
        }
        return null;
    }


    // ----------------------------------------------------------------------
    // Ferramentas de captura visual (validacao de GUI por agentes)
    // ----------------------------------------------------------------------

    /** Salva a imagem como PNG em ignis-captures/ (temp do sistema) e retorna o caminho. */
    static String saveCapture(java.awt.image.BufferedImage img, String label) throws Exception {
        File outDir = new File(System.getProperty("java.io.tmpdir"), "ignis-captures");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("Nao foi possivel criar " + outDir);
        }
        File out = new File(outDir, label + "-" + System.currentTimeMillis() + ".png");
        javax.imageio.ImageIO.write(img, "png", out);
        return out.getAbsolutePath();
    }


    // ----------------------------------------------------------------------
    // Ferramentas de hierarquia pai-filho (GameObject.parent)
    // ----------------------------------------------------------------------


    // ----------------------------------------------------------------------
    // Ferramentas de tilemap (com.ignis.core.TilemapObject)
    // ----------------------------------------------------------------------

    // ----------------------------------------------------------------------
    // Ferramentas de texto no mundo (com.ignis.core.TextObject) — Fase D 3.9
    // ----------------------------------------------------------------------

    // ----------------------------------------------------------------------
    // Ferramentas de iluminacao 2D (com.ignis.core.LightObject) — Fase D 3.11
    // ----------------------------------------------------------------------

    // ----------------------------------------------------------------------
    // Ferramentas de particulas (com.ignis.core.ParticleEmitter)
    // ----------------------------------------------------------------------

    // ----------------------------------------------------------------------
    // Ferramentas de fundo com parallax (com.ignis.core.BackgroundLayer)
    // ----------------------------------------------------------------------

    /** Limita um valor ao intervalo [0,1]. */
    static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Interpreta uma cor de texto: {@code #RRGGBB}, {@code #AARRGGBB}, {@code 0x...}
     * ou um inteiro decimal. Retorna null se vazio/invalido.
     */
    static java.awt.Color parseColor(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            String hex = s;
            if (hex.startsWith("#")) hex = hex.substring(1);
            else if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
            if (hex.matches("[0-9a-fA-F]+")) {
                long v = Long.parseLong(hex, 16);
                boolean hasAlpha = hex.length() > 6;
                return new java.awt.Color((int) v, hasAlpha);
            }
            return new java.awt.Color(Integer.parseInt(s));
        } catch (Exception e) {
            return null;
        }
    }

    // ----------------------------------------------------------------------
    // Ferramentas de animacao ligadas a objetos vivos (Animator do GameObject)
    // ----------------------------------------------------------------------


    // ----------------------------------------------------------------------
    // Ferramentas de Prefabs (com.ignis.core.PrefabManager, via liveGame)
    // ----------------------------------------------------------------------


    // ----------------------------------------------------------------------
    // Ferramentas de colisao (GameObject.setColliderType/Mode + Collider layer/mask)
    // ----------------------------------------------------------------------


    // ----------------------------------------------------------------------
    // Ferramentas de camera (com.ignis.core.Camera + Game.addCamera/setMainCamera)
    // ----------------------------------------------------------------------

    Camera findCamera(String name) {
        if (liveGame == null || name == null) return null;
        for (Camera c : liveGame.getCameras()) {
            if (name.equals(c.getCameraName()) || name.equals(c.getName())) return c;
        }
        return null;
    }


    // ----------------------------------------------------------------------
    // Ferramentas de UI in-game direta (sem precisar escrever um IgnisScript).
    // Usa o mesmo UICanvas do jogo (com.ignis.core.ui) por baixo.
    // ----------------------------------------------------------------------

    UICanvas ensureUiCanvas() {
        if (liveGame == null) return null;
        UICanvas canvas = liveGame.getUICanvas();
        if (canvas == null) {
            canvas = new UICanvas();
            liveGame.setUICanvas(canvas);
        }
        return canvas;
    }


    // ----------------------------------------------------------------------
    // Extras de GameObject (visibilidade, cor, z-order, tipo/busca, scripts, cena)
    // ----------------------------------------------------------------------


    // ----------------------------------------------------------------------
    // Informacoes gerais da cena/jogo vivo
    // ----------------------------------------------------------------------


    // ----------------------------------------------------------------------
    // Sistema de mundos (Fase 1: limites do mapa + barreiras em grade)
    // ----------------------------------------------------------------------


    // Resolve um caminho relativo garantindo que permaneca dentro do projeto (anti path-traversal).
    File resolveInProject(String relative) {
        return resolveWithin(projectFolder, relative);
    }

    // Resolve um caminho relativo garantindo que o resultado fique DENTRO de 'base'
    // (nao apenas com o mesmo prefixo textual — evita escapar para uma pasta irma,
    // ex: base "Project" nao deve aceitar um alvo resolvido em "ProjectEvil").
    static File resolveWithin(File base, String relative) {
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
