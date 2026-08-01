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
            "create_object", "delete_object", "rename_object", "set_object_metadata",
            "set_object_transform", "set_object_sprite",
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
            "set_parent", "clear_parent",
            "create_scene", "switch_scene", "copy_object_to_scene");

    // Ferramentas que MUTAM o grafo de cena de forma PERSISTENTE (o que o Stop
    // descarta): as que mudanças em Play seriam perdidas ao restaurar o snapshot.
    // Derivadas do FORWARD_TO_HOST menos as que controlam o proprio Play/save
    // (play_game/stop_game precisam rodar em Play; save_project tem seu proprio
    // preflight). Ganham o contrato uniforme mode/dryRun/diff/allowInPlay no gate
    // central do call() e no schema (via add()), sem tocar cada handler.
    private static final java.util.Set<String> SCENE_MUTATING = buildSceneMutating();

    private static java.util.Set<String> buildSceneMutating() {
        java.util.Set<String> s = new java.util.HashSet<>(FORWARD_TO_HOST);
        s.remove("play_game");
        s.remove("stop_game");
        s.remove("save_project");
        return java.util.Collections.unmodifiableSet(s);
    }

    // ------------------------------------------------------------------
    // Coordenacao multi-agente: escopo de claim por ferramenta
    // ------------------------------------------------------------------

    /**
     * Escopo de coordenacao de uma ferramenta que MUTA algo: qual recurso ela toca,
     * para o gate central checar o claim de {@link McpCoordination} antes de executar.
     */
    private static final class Guard {
        /** Prefixo do recurso ("objeto", "camera", "script", "mundo", "cena"). */
        final String prefix;
        /** Argumento que identifica o alvo; null = recurso fixo (o proprio prefixo). */
        final String arg;
        /** Operacao destrutiva ampla: conflita com QUALQUER claim de outro agente. */
        final boolean wide;

        Guard(String prefix, String arg, boolean wide) {
            this.prefix = prefix;
            this.arg = arg;
            this.wide = wide;
        }
    }

    // Ferramenta -> recurso que ela disputa. Um agente que informa 'agent' e barrado
    // se o recurso estiver reservado (claim) por OUTRO agente. Chamadas sem 'agent'
    // passam direto: a coordenacao e opt-in e nao quebra clientes existentes.
    private static final Map<String, Guard> GUARDS = buildGuards();

    private static Map<String, Guard> buildGuards() {
        Map<String, Guard> m = new LinkedHashMap<>();
        // Objetos de cena identificados pelo argumento 'name'.
        for (String t : List.of(
                "create_object", "delete_object", "rename_object", "set_object_metadata",
                "set_object_transform", "set_object_sprite",
                "set_sprite_region", "set_object_visual", "set_object_visible",
                "set_object_name_color", "reorder_object_z", "set_object_world_collision",
                "clear_parent", "create_text_object", "set_text",
                "create_light_object", "set_light_properties",
                "create_background_layer", "set_parallax_factor",
                "create_particle_emitter", "particle_burst", "set_particle_emitting",
                "create_tilemap", "add_tilemap_layer", "clear_tilemap_layer",
                "set_tile", "paint_tiles")) {
            m.put(t, new Guard("objeto", "name", false));
        }
        // Objetos de cena identificados por 'objectName'.
        for (String t : List.of(
                "attach_script", "remove_script_from_object", "set_object_collider",
                "attach_animation", "play_animation", "stop_animation")) {
            m.put(t, new Guard("objeto", "objectName", false));
        }
        // UI persistente por objeto (P1 fatia 2): ferramentas ui_* com 'objectName'
        // disputam o OBJETO dono do CanvasComponent. Sem 'objectName' (canvas global
        // volatil), o argumento fica vazio e nao gera claim (ver coordConflict).
        for (String t : List.of(
                "ui_create_label", "ui_create_button", "ui_create_progressbar", "ui_create_panel",
                "ui_create_image", "ui_create_textfield", "ui_create_checkbox", "ui_create_slider",
                "ui_set_text", "ui_set_progress_value", "ui_set_nine_slice", "ui_remove_element",
                "ui_clear_all", "ui_set_anchor", "ui_set_style",
                "ui_attach_canvas", "ui_set_canvas_props", "ui_detach_canvas")) {
            m.put(t, new Guard("objeto", "objectName", false));
        }
        // Reparentar: o filho e quem muda de lugar na hierarquia.
        m.put("set_parent", new Guard("objeto", "child", false));

        // Scripts (o claim historico: 'script:PlayerController').
        m.put("write_script", new Guard("script", "scriptName", false));
        m.put("patch_script", new Guard("script", "scriptName", false));
        m.put("create_script", new Guard("script", "scriptName", false));

        // Cameras nomeadas.
        for (String t : List.of("create_camera", "set_camera_transform", "set_active_camera")) {
            m.put(t, new Guard("camera", "name", false));
        }
        // Camera ativa (sem alvo nomeado): recurso fixo 'camera'.
        for (String t : List.of("set_camera_follow", "stop_camera_follow", "camera_shake",
                "set_camera_bounds", "clear_camera_bounds")) {
            m.put(t, new Guard("camera", null, false));
        }
        // Mundo: limites, grade e barreiras sao um recurso so.
        for (String t : List.of("set_world_bounds", "clear_world_bounds", "set_world_grid",
                "block_rect", "unblock_rect", "block_cell", "unblock_cell",
                "clear_barriers", "set_world_property")) {
            m.put(t, new Guard("mundo", null, false));
        }
        // Cena inteira.
        m.put("set_scene_ambient_light", new Guard("cena", null, false));
        m.put("instantiate_prefab", new Guard("cena", null, false));
        // Destrutivas/disruptivas amplas: nao miram um alvo, mas atropelam quem edita.
        m.put("clear_scene", new Guard("cena", null, true));
        m.put("play_game", new Guard("cena", null, true));
        m.put("stop_game", new Guard("cena", null, true));
        // Cria/troca a cena ATIVA do editor inteiro, ou copia um objeto para outra
        // cena: tao amplas quanto clear_scene, mesmo tratamento.
        m.put("create_scene", new Guard("cena", null, true));
        m.put("switch_scene", new Guard("cena", null, true));
        m.put("copy_object_to_scene", new Guard("cena", null, true));
        // Reiniciar o editor derruba a sessao inteira (bridge, Play, cena): tao amplo
        // quanto clear_scene — barra se outro agente segura qualquer recurso.
        m.put("restart_editor", new Guard("cena", null, true));
        // Ferramentas de teste que mexem no runtime/sim inteiro: mesmo escopo amplo
        // de play_game/stop_game (afetam o que todos veem no Play).
        m.put("inject_input", new Guard("cena", null, true));
        m.put("advance_frames", new Guard("cena", null, true));
        m.put("pause_game", new Guard("cena", null, true));
        m.put("resume_game", new Guard("cena", null, true));
        m.put("run_input_tape", new Guard("cena", null, true));
        m.put("click_ui", new Guard("cena", null, true));
        m.put("move_mouse", new Guard("cena", null, true));
        m.put("run_cutscene", new Guard("cena", null, true));

        // Autoria de cutscenes: cada cutscene e um recurso proprio ('cutscene:intro'),
        // como scripts — dois agentes podem editar cutscenes diferentes em paralelo.
        for (String t : List.of("create_cutscene", "delete_cutscene", "add_cutscene_track",
                "add_cutscene_keyframe", "remove_cutscene_keyframe", "set_cutscene_duration")) {
            m.put(t, new Guard("cutscene", "name", false));
        }

        // Autoria de dialogos: cada dialogo e um recurso proprio ('dialogo:intro'),
        // identificado por 'id' — mesmo tratamento das cutscenes.
        for (String t : List.of("create_dialog", "delete_dialog", "set_dialog_node",
                "remove_dialog_node")) {
            m.put(t, new Guard("dialogo", "id", false));
        }

        return java.util.Collections.unmodifiableMap(m);
    }

    // Contexto vivo do editor (opcional): presente quando o bridge roda dentro do
    // editor JavaFX, habilitando ferramentas de cena e de Play. Nulo no modo headless.
    Game liveGame;
    Runnable playHook, stopHook, saveHook;
    Runnable refreshHook;
    // Relanca o processo do editor (ferramenta restart_editor). Injetado pelo
    // IgnisEditorApp; nulo no modo headless (STDIO), onde nao ha editor a reiniciar.
    Runnable restartHook;
    // Ponte para criar/listar/trocar de cena e copiar objetos entre cenas (nulo se o
    // editor nao a injetou ainda — ver SceneHost). Ferramentas de SceneTools checam.
    SceneHost sceneHost;

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
        // Liga a coordenacao multi-agente ao arquivo do projeto: mural, claims e
        // tarefas passam a sobreviver ao fechamento/restart do editor.
        McpCoordination.get().bindProject(projectFolder);
        registerDefaults();
    }

    /**
     * Liga o registry ao editor vivo, registrando as ferramentas de cena e Play.
     * Os hooks (play/stop/refresh/save) invocam os metodos reais do editor e sao
     * executados na thread de UI (o {@link #call} ja envolve tudo em runOnFxThread).
     */
    public void attachLiveEditor(Game game, Runnable play, Runnable stop, Runnable refresh, Runnable save,
            SceneHost sceneHost, Runnable restart) {
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
        this.sceneHost = sceneHost;
        this.restartHook = restart;
        registerEditorTools();
    }

    /**
     * Compatibility overload for integrations created before the restart hook
     * and explicit SceneHost were added. New editor integrations should use the
     * seven-argument overload above.
     */
    public void attachLiveEditor(Game game, Runnable play, Runnable stop, Runnable refresh, Runnable save,
            SceneHost sceneHost) {
        attachLiveEditor(game, play, stop, refresh, save, sceneHost, () -> { });
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
        new SceneTools(this).registerAll();
        new EditorLifecycleTools(this).registerAll();
        new RuntimeInspectionTools(this).registerAll();
        new RuntimeTestingTools(this).registerAll();
        new CutsceneTools(this).registerAll();
        new DialogTools(this).registerAll();
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

        // Identidade do chamador: o bridge HTTP nao tem sessao por conexao, entao o
        // agente se identifica pelo argumento 'agent'. E opcional — sem ele a chamada
        // roda como anonima, sem coordenacao (retrocompatibilidade).
        final String agent = safeArgs.optString("agent", "").trim();
        // Para o LOG, aceita tambem 'from' (as ferramentas de mural usam esse nome),
        // senao send_message/read_messages apareceriam sem dono no Console. So o
        // 'agent' vale para o gate de claims — 'from' nao autoriza escrita.
        String caller = agent.isEmpty() ? safeArgs.optString("from", "").trim() : agent;
        final String who = caller.isEmpty() ? "" : caller + ": ";

        // Gate de coordenacao: barra a escrita num recurso reservado por outro agente
        // ANTES de executar, para o conflito nunca chegar a tocar a cena.
        String conflict = coordConflict(name, safeArgs, agent);
        if (conflict != null) {
            IgnisLogger.warn("[MCP] " + who + name + " -> CONFLITO");
            return conflict;
        }
        if (!agent.isEmpty()) {
            McpCoordination.get().touchAgent(agent);
        }

        // Contrato uniforme das ferramentas que mutam a cena (mode/dryRun/diff): estes
        // gates rodam ANTES do dispatch para a FX thread, para recusar/relatar sem nunca
        // tocar a cena — e serem testaveis headless.
        final boolean sceneMutating = SCENE_MUTATING.contains(name);
        final String mode = (liveGame != null) ? liveGame.getGameState().toString().toLowerCase(java.util.Locale.ROOT)
                : "headless";
        if (sceneMutating && liveGame != null
                && liveGame.getGameState() == Game.GameState.PLAYING
                && !safeArgs.optBoolean("allowInPlay", false)) {
            IgnisLogger.warn("[MCP] " + who + name + " -> RECUSADO (Play)");
            return "RECUSADO ('" + name + "' em modo Play): mudancas feitas durante o Play sao DESCARTADAS no "
                    + "Stop (o editor restaura o snapshot inicial da cena). Pare o Play (stop_game) para editar "
                    + "de forma persistente, ou passe allowInPlay=true para aplicar so no runtime transitorio.";
        }
        if (sceneMutating && safeArgs.optBoolean("dryRun", false)) {
            IgnisLogger.info("[MCP] " + who + name + " -> dryRun");
            return "[dryRun] '" + name + "' NAO aplicado (modo=" + mode + "). Faria com: "
                    + truncate(safeArgs.toString(), 200);
        }

        final boolean wantDiff = sceneMutating && liveGame != null && safeArgs.optBoolean("diff", false);
        long startNanos = System.nanoTime();
        String result = IgnisMcpBridge.runOnFxThread(() -> {
            java.util.Map<String, String> before = wantDiff ? snapshotScene() : null;
            String r;
            try {
                r = def.handler.execute(safeArgs);
            } catch (Exception e) {
                return "Erro ao executar '" + name + "': " + e.getMessage();
            }
            if (before != null && (r == null || !r.startsWith("Erro"))) {
                r = r + "\n" + diffScenes(before, snapshotScene());
            }
            return r;
        });
        // Toda ferramenta mutavel informa o modo em que rodou (contrato do roadmap).
        if (sceneMutating && result != null && !result.startsWith("Erro")) {
            result = result + " [modo=" + mode + "]";
        }
        // Auditoria: cada chamada de agente aparece no Console do editor
        // (FxConsolePanel captura System.out). Args truncados para nao inundar
        // o log com conteudos grandes (ex: write_script).
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        String argsPreview = safeArgs.isEmpty() ? "" : " " + truncate(safeArgs.toString(), 120);
        boolean isError = result != null && result.startsWith("Erro");
        IgnisLogger.info("[MCP] " + who + name + argsPreview + " -> "
                + (isError ? "ERRO" : "ok") + " (" + ms + "ms)");
        return result;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * Fotografa a cena viva por ID estavel -> assinatura curta (nome, transform, z,
     * visibilidade). Base do resumo de diff opcional das ferramentas mutaveis. Roda na
     * FX thread (chamado de dentro do runnable do {@link #call}).
     */
    java.util.Map<String, String> snapshotScene() {
        java.util.Map<String, String> m = new LinkedHashMap<>();
        if (liveGame == null) return m;
        for (GameObject go : liveGame.getEntities()) {
            m.put(go.getId(), go.getName() + "|" + (int) go.getX() + "," + (int) go.getY()
                    + "|" + go.getWidth() + "x" + go.getHeight() + "|z" + go.getZIndex()
                    + "|" + (go.isVisible() ? "v" : "h"));
        }
        return m;
    }

    // Snapshots NOMEADOS de cena (ferramentas snapshot_scene/compare_scene_snapshot):
    // vivem so na memoria da sessao — comparam edicao persistente vs runtime
    // transitorio, nao substituem save. Limitados para nao crescer sem fim.
    private final Map<String, java.util.Map<String, String>> namedSceneSnapshots = new LinkedHashMap<>();
    private static final int MAX_NAMED_SNAPSHOTS = 16;

    void storeSceneSnapshot(String label, java.util.Map<String, String> snap) {
        namedSceneSnapshots.remove(label);
        while (namedSceneSnapshots.size() >= MAX_NAMED_SNAPSHOTS) {
            namedSceneSnapshots.remove(namedSceneSnapshots.keySet().iterator().next());
        }
        namedSceneSnapshots.put(label, snap);
    }

    java.util.Map<String, String> getSceneSnapshot(String label) {
        return namedSceneSnapshots.get(label);
    }

    java.util.Set<String> sceneSnapshotLabels() {
        return new java.util.LinkedHashSet<>(namedSceneSnapshots.keySet());
    }

    /** Resumo textual do que mudou entre dois snapshots de cena (added/removed/changed). */
    static String diffScenes(java.util.Map<String, String> before, java.util.Map<String, String> after) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            String old = before.get(e.getKey());
            if (old == null) added.add(nameOf(e.getValue()));
            else if (!old.equals(e.getValue())) changed.add(nameOf(old) + ": " + old + " -> " + e.getValue());
        }
        for (Map.Entry<String, String> e : before.entrySet()) {
            if (!after.containsKey(e.getKey())) removed.add(nameOf(e.getValue()));
        }
        if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
            return "diff: (nenhuma mudanca na cena)";
        }
        StringBuilder sb = new StringBuilder("diff: +").append(added.size())
                .append(" -").append(removed.size()).append(" ~").append(changed.size());
        if (!added.isEmpty()) sb.append("\n  + ").append(String.join(", ", added));
        if (!removed.isEmpty()) sb.append("\n  - ").append(String.join(", ", removed));
        for (String c : changed) sb.append("\n  ~ ").append(c);
        return sb.toString();
    }

    private static String nameOf(String signature) {
        int bar = signature.indexOf('|');
        return bar >= 0 ? signature.substring(0, bar) : signature;
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
        // Ponto unico de registro: toda ferramenta guardada ganha o parametro 'agent'
        // e a nota de coordenacao no schema, sem que cada classe de dominio precise
        // declara-lo. Assim o catalogo (/mcp/tools) ensina o protocolo por si so.
        if (GUARDS.containsKey(name)) {
            JSONObject properties = schema.optJSONObject("properties");
            if (properties == null) {
                properties = new JSONObject();
                schema.put("properties", properties);
            }
            properties.put("agent", new JSONObject()
                    .put("type", "string")
                    .put("description", "Seu nome de agente (opcional). Identifica voce no Console do "
                            + "editor e faz esta chamada respeitar os claims dos outros agentes."));
            description = description + " Informe 'agent' para se identificar e respeitar claims.";
        }
        // Contrato uniforme das ferramentas que mutam a cena de forma persistente:
        // dryRun (nao aplica, so relata), diff (resumo do que mudou na cena) e
        // allowInPlay (aplicar mesmo em Play, ciente de que o Stop descarta). Injetado
        // aqui para o catalogo /mcp/tools ensinar o protocolo sem cada handler repetir.
        if (SCENE_MUTATING.contains(name)) {
            JSONObject properties = schema.optJSONObject("properties");
            if (properties == null) {
                properties = new JSONObject();
                schema.put("properties", properties);
            }
            properties.put("dryRun", new JSONObject().put("type", "boolean")
                    .put("description", "Se true, NAO aplica: valida e relata o que faria (previa segura)."));
            properties.put("diff", new JSONObject().put("type", "boolean")
                    .put("description", "Se true, anexa um resumo do que mudou na cena (objetos +/-/alterados)."));
            properties.put("allowInPlay", new JSONObject().put("type", "boolean")
                    .put("description", "Em modo Play, edicoes sao descartadas no Stop. Passe true para aplicar "
                            + "mesmo assim (so no runtime transitorio). Padrao: recusa em Play."));
            description = description + " [muta cena: aceita dryRun/diff; recusada em Play sem allowInPlay]";
        }
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

    /**
     * Gate de coordenacao multi-agente, aplicado a TODA ferramenta guardada (ver
     * {@link #GUARDS}) antes da execucao.
     *
     * <p>Retorna a mensagem de conflito quando o recurso alvo esta reservado por
     * OUTRO agente, ou null quando pode prosseguir: chamada anonima (sem 'agent'),
     * ferramenta nao guardada (leitura), recurso livre, ou o dono do claim e voce.</p>
     */
    String coordConflict(String tool, JSONObject args, String agent) {
        if (agent.isEmpty()) return null; // anonimo: sem coordenacao (opt-in)
        Guard guard = GUARDS.get(tool);
        if (guard == null) return null;  // leitura ou ferramenta fora do escopo

        if (guard.wide) {
            // Destrutiva ampla: qualquer claim de terceiro basta para barrar.
            String other = McpCoordination.get().anyHolderExcept(agent);
            if (other != null) {
                return "CONFLITO: '" + tool + "' afeta a cena inteira e ha recurso reservado"
                        + " por outro agente: " + other
                        + ". Combine pelo mural (send_message) antes de prosseguir.";
            }
            return null;
        }

        String resource = guard.prefix;
        if (guard.arg != null) {
            String id = args.optString(guard.arg, "").trim();
            if (id.isEmpty()) return null; // sem alvo identificavel: nada a checar
            resource = guard.prefix + ":" + id;
        }
        String holder = McpCoordination.get().holderOf(resource);
        if (holder != null && !holder.equalsIgnoreCase(agent)) {
            return "CONFLITO: '" + resource + "' esta reservado por " + holder
                    + ". Combine pelo mural (send_message) antes de editar.";
        }
        return null;
    }

    // Guia de autoria retornado por how_to_create_game. Ensina o modelo mental do
    // Editor e a ordem/regras das ferramentas para o trabalho do agente aparecer na
    // Cena, na Hierarchy e no Inspector — e nao se perder no Play ou fora do viewport.
    private static final String HOW_TO_CREATE_GAME =
        "# Como criar jogos no Editor IgnisEngine (via MCP)\n"
        + "\n"
        + "## Modelo mental\n"
        + "- CENA (Scene): o 'mapa'/'mundo' aberto agora. Tem os GameObjects numa HIERARQUIA\n"
        + "  pai-filho e uma camera. Um jogo tem VARIAS cenas (um mundo por cena).\n"
        + "- HIERARQUIA: dentro da cena, objetos podem ter pai (set_parent). O filho segue o\n"
        + "  pai no Play. A Hierarchy do editor mostra essa arvore.\n"
        + "- MUNDO (World): limites/grade/barreiras da cena (set_world_bounds, block_rect...).\n"
        + "  Nao confundir com 'cena': o World e a fisica/espaco; a cena e o conteudo.\n"
        + "- Z-INDEX: ordem de desenho. Menor atras, maior na frente. Fundos ficam atras.\n"
        + "\n"
        + "## Ordem recomendada\n"
        + "1. how_to_create_game (isto) + get_project_context + get_editor_status para ver\n"
        + "   os caminhos reais do workspace/projeto e a cena atual. Nunca adivinhe caminhos.\n"
        + "2. list_scenes; crie um mundo por cena com create_scene / troque com switch_scene.\n"
        + "3. Crie objetos: create_object (formas/player), create_text_object, create_tilemap,\n"
        + "   create_background_layer, create_particle_emitter, create_light_object.\n"
        + "4. De identidade/aparencia/comportamento: rename_object, set_object_metadata,\n"
        + "   set_object_sprite, attach_script, attach_animation, set_object_transform,\n"
        + "   reorder_object_z. Inspecione com get_object_info/get_object_components.\n"
        + "5. Organize a hierarquia com set_parent/clear_parent.\n"
        + "6. Camera: create_camera/set_active_camera/set_camera_follow (o que o jogador ve).\n"
        + "7. validate_scene para achar problemas; corrija.\n"
        + "8. save_project para PERSISTIR (sem isso, nada fica salvo no .ignis).\n"
        + "\n"
        + "## Regras para o trabalho APARECER (Cena/Hierarchy/Inspector/viewport)\n"
        + "- Todo create_* adiciona o objeto a cena ATIVA e atualiza a Hierarchy/Inspector.\n"
        + "  Se nao aparece, confira: cena certa aberta (switch_scene) e nome unico.\n"
        + "- NOME UNICO por cena: nomes duplicados deixam as ferramentas por-nome ambiguas.\n"
        + "- VISIVEL: set_object_visible(true). Scripts de cutscene podem esconder objetos.\n"
        + "- Z-INDEX na frente do fundo: o tilemap tem z=-100 por padrao e pode ficar ATRAS\n"
        + "  de um Background z=0 — use reorder_object_z para trazer a frente.\n"
        + "- DENTRO do viewport/limites: objeto fora dos bounds do World nao aparece na tela\n"
        + "  (validate_scene aponta). Posicione perto de (0,0) ou da camera.\n"
        + "- SPRITE existente: set_object_sprite deve apontar para um asset que existe\n"
        + "  (import_asset_from_path / generate_sprite antes). validate_scene sinaliza ausentes.\n"
        + "\n"
        + "## Play e persistencia (nao perca trabalho)\n"
        + "- EDICAO e onde voce constroi. play_game inicia a simulacao; stop_game RESTAURA o\n"
        + "  snapshot inicial: mudancas feitas DURANTE o Play sao descartadas. Edite em edicao.\n"
        + "- Por isso, ferramentas que mutam a cena sao RECUSADAS em Play por padrao (pare com\n"
        + "  stop_game, ou passe allowInPlay=true para mexer so no runtime). Use dryRun=true para\n"
        + "  simular sem aplicar e diff=true para ver o que mudou. A resposta informa [modo=...].\n"
        + "- Compile scripts antes do Play (compile_project) e recarregue com restart_editor se\n"
        + "  trocar codigo de script fora do editor.\n"
        + "- QA determinista: play_game -> pause_game -> inject_input -> advance_frames ->\n"
        + "  ler (list_runtime_objects/capture_viewport) -> release_all_inputs -> stop_game.\n"
        + "  Sequencias inteiras: run_input_tape (fita de eventos por frame; zera o input no fim).\n"
        + "  inject_input aceita durationFrames para segurar uma tecla N frames e soltar sozinho.\n"
        + "  CLIQUE por coordenada: click_ui(x,y) dispara o onClick de um botao/escolha de dialogo\n"
        + "  (ache as bounds com get_ui_tree); move_mouse(x,y) so posiciona/hover. A fita tambem\n"
        + "  aceita eventos {clickUi:true, x, y} para clicar no meio de uma sequencia.\n"
        + "\n"
        + "## Como CONFERIR (em vez de adivinhar)\n"
        + "- list_runtime_objects: estado real (posicao, z, visivel, pai, scripts, componentes).\n"
        + "- get_ui_tree: arvore da UI in-game (widgets, bounds, texto, interatividade, origem).\n"
        + "- validate_scene: nomes duplicados, assets/scripts ausentes, pai quebrado, fora do mundo.\n"
        + "- snapshot_scene + compare_scene_snapshot: fotografe a cena antes (ex: do Play) e\n"
        + "  compare depois — separa edicao persistente de runtime transitorio.\n"
        + "- capture_viewport / capture_editor_window: ver o que a Scene View e a janela mostram.\n"
        + "- get_runtime_metrics: contagens e memoria (detecta objetos/listeners que vazam).\n"
        + "\n"
        + "## UI in-game: VOLATIL vs PERSISTENTE\n"
        + "- SEM objectName, as ferramentas ui_* montam no canvas GLOBAL de runtime: some no\n"
        + "  stop_game e NAO e salvo no .ignis. Bom para HUD temporario de teste durante o Play.\n"
        + "- COM objectName, montam no CanvasComponent daquele objeto: PERSISTENTE — serializa na\n"
        + "  cena, reabre pronto e o preview aparece ate em edicao. Anexa o componente sozinho.\n"
        + "  Ex: ui_create_progressbar(name=hp, objectName=Player) faz um HUD que fica salvo.\n"
        + "- Nomes sao unicos POR CANVAS (o mesmo 'hp' pode existir no Player e no Boss).\n"
        + "- Ao contrario da cena, edicao de UI persistente feita em Play NAO e descartada no Stop.\n"
        + "- ui_attach_canvas/ui_set_canvas_props/ui_detach_canvas gerem o componente; ui_set_anchor\n"
        + "  e ui_set_style ajustam ancoras/cores/fonte/z; botao guarda actionData (ex 'signal:x')\n"
        + "  que um script le e conecta. Confira tudo com get_ui_tree (mostra a origem de cada widget).\n"
        + "\n"
        + "## Cutscenes (timeline por tracks/keyframes)\n"
        + "- create_cutscene -> add_cutscene_keyframe (ACTOR/CAMERA interpolam x/y com easing;\n"
        + "  DIALOG/AUDIO/SIGNAL/FLAG disparam no frame exato; 60 frames = 1s).\n"
        + "- validate_cutscene confere ator/asset ausente; preview_cutscene faz scrub read-only.\n"
        + "- run_cutscene executa no Play frame a frame (ou skip=true direto ao estado final).\n"
        + "- Fica em cutscenes/<nome>.cutscene.json no projeto (persistente, versionavel).\n"
        + "\n"
        + "## Dialogos (grafo de nos, data-driven)\n"
        + "- create_dialog -> set_dialog_node (fala com speaker/retrato/texto e saida via 'next' OU\n"
        + "  'choices' [{text,next,setFlag?,condition?}]). Um no sem next e sem choices e terminal.\n"
        + "- validate_dialog checa start, referencias quebradas, nos inalcancaveis e ciclos sem saida;\n"
        + "  preview_dialog percorre o grafo seguindo choicesPath (indices) e mostra a transcricao.\n"
        + "- Fica em dialogs/<id>.dialog.json. A engine NAO exibe sozinha: um script le o JSON e desenha\n"
        + "  com a UI persistente (ui_* com objectName). Ponte: track DIALOG de cutscene pode citar\n"
        + "  'dialog:<id>#<no>' no campo data. Nao guarde texto protegido copiado da obra.\n"
        + "\n"
        + "## Multiagente\n"
        + "- Informe 'agent' nas chamadas e use claim/release + send_message para nao pisar no\n"
        + "  trabalho de outro agente. Mural, claims e tarefas sobrevivem ao restart do editor.\n";

    private void registerDefaults() {
        ProjectAuthoringTools projectAuthoring = new ProjectAuthoringTools(this);
        // how_to_create_game — guia de orientacao para o agente construir jogos no
        // Editor pela ordem/regras certas (Cena, hierarquia, mundos/cenas, aparecer
        // no viewport, salvar). Disponivel tambem no STDIO (headless) para o agente
        // ler o protocolo antes de mexer na cena.
        add("how_to_create_game",
            "Guia PASSO A PASSO de como criar jogos no Editor IgnisEngine via MCP: o modelo de Cena e "
            + "hierarquia de objetos, mundos/cenas, a ordem correta de ferramentas e as regras para os objetos "
            + "aparecerem na Cena/Hierarchy/Inspector e o programador poder ve-los. LEIA ISTO ANTES de comecar.",
            objectSchema(),
            args -> HOW_TO_CREATE_GAME);
        projectAuthoring.registerAll();

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
                String validation = projectAuthoring.validateScriptName(name);
                if (validation != null) return validation;
                String content = scriptManager().readScriptContent(name);
                return content != null ? content : "Erro: script nao encontrado: " + name;
            });

        // write_script
        Map<String, String> writeScriptProps = new LinkedHashMap<>();
        writeScriptProps.put("scriptName", "Nome do script (ex: PlayerController)");
        writeScriptProps.put("content", "Conteudo Java completo do script");
        writeScriptProps.put("expectedSha256", "Hash SHA-256 retornado por get_script_info; recusa se o arquivo mudou");
        writeScriptProps.put("dryRun", "Se true, valida e calcula o novo hash sem gravar");
        JSONObject writeScriptSchema = schemaWith(writeScriptProps, List.of("scriptName", "content"));
        writeScriptSchema.getJSONObject("properties").put("dryRun", new JSONObject()
                .put("type", "boolean")
                .put("description", writeScriptProps.get("dryRun")));
        add("write_script",
            "Sobrescreve atomicamente um script existente. Prefira expectedSha256 para nao apagar edicao concorrente; "
            + "para mudanca localizada, prefira patch_script.",
            writeScriptSchema,
            args -> {
                String name = args.optString("scriptName", "").trim();
                String content = args.optString("content", "");
                String validation = projectAuthoring.validateScriptName(name);
                if (validation != null) return validation;
                File script = projectAuthoring.scriptFile(name);
                if (!script.isFile()) return "Erro: script nao encontrado: " + name
                        + ". Use create_script antes de write_script.";
                String currentHash = projectAuthoring.sha256(script);
                String expectedHash = args.optString("expectedSha256", "").trim();
                if (!expectedHash.isEmpty() && !expectedHash.equalsIgnoreCase(currentHash)) {
                    return "CONFLITO: o script mudou desde a leitura. esperado=" + expectedHash
                            + ", atual=" + currentHash + ". Leia novamente antes de editar.";
                }
                String nextHash = projectAuthoring.sha256(content);
                if (args.optBoolean("dryRun", false)) {
                    return "DRY-RUN: write_script validado; nenhuma gravacao.\npath="
                            + projectAuthoring.canonicalPath(script)
                            + "\nsha256Before=" + currentHash + "\nsha256After=" + nextHash;
                }
                boolean ok = scriptManager().saveScriptContent(name, content);
                return ok ? "Script salvo atomicamente: " + name + "\npath="
                        + projectAuthoring.canonicalPath(script)
                        + "\nsha256=" + nextHash : "Erro ao salvar script: " + name;
            });

        // create_script
        Map<String, String> createScriptProps = new LinkedHashMap<>();
        createScriptProps.put("scriptName", "Nome do novo script (ex: EnemyAI)");
        add("create_script",
            "Cria um novo script Java a partir do template padrao do motor.",
            schemaWith(createScriptProps, List.of("scriptName")),
            args -> {
                String name = args.optString("scriptName", "").trim();
                String validation = projectAuthoring.validateScriptName(name);
                if (validation != null) return validation;
                boolean ok = scriptManager().createNewScript(name);
                File script = projectAuthoring.scriptFile(name);
                return ok ? "Script criado atomicamente: " + name + "\npath="
                        + projectAuthoring.canonicalPath(script)
                        + "\nsha256=" + projectAuthoring.sha256(script)
                        : "Erro: script ja existe ou nome invalido: " + name;
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

        Map<String, String> readLogsProps = new LinkedHashMap<>();
        readLogsProps.put("maxLines", "Quantidade de entradas recentes (1-500; padrao 100)");
        readLogsProps.put("level", "Filtro opcional: INFO, WARN, ERROR ou SCRIPT");
        add("read_logs",
            "Le os logs recentes da engine sem precisar inspecionar a tela do editor.",
            schemaWith(readLogsProps, List.of()),
            args -> {
                int maxLines = Math.max(1, Math.min(500, args.optInt("maxLines", 100)));
                String rawLevel = args.optString("level", "").trim();
                com.ignis.core.IgnisLogger.Level level = null;
                if (!rawLevel.isEmpty()) {
                    try {
                        level = com.ignis.core.IgnisLogger.Level.valueOf(rawLevel.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        return "Erro: level deve ser INFO, WARN, ERROR ou SCRIPT.";
                    }
                }
                List<com.ignis.core.IgnisLogger.LogEntry> entries =
                        com.ignis.core.IgnisLogger.recentLogs(maxLines, level);
                if (entries.isEmpty()) return "(sem logs)";
                return entries.stream()
                        .map(entry -> "[" + entry.sequence() + "][" + entry.level() + "] " + entry.message())
                        .collect(java.util.stream.Collectors.joining("\n"));
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
            String digits = h;
            if (digits.startsWith("#")) {
                digits = digits.substring(1);
            } else if (digits.startsWith("0x") || digits.startsWith("0X")) {
                digits = digits.substring(2);
            }

            if (digits.matches("[0-9a-fA-F]{6}")) {
                return new Color(Integer.parseInt(digits, 16));
            }
            if (digits.matches("[0-9a-fA-F]{8}")) {
                // As ferramentas de UI e a serializacao de UIComponent usam
                // #RRGGBBAA. Color.decode ignora os oito bits mais altos e
                // transformava qualquer alpha informado em FF.
                int rgba = (int) Long.parseLong(digits, 16);
                int red = (rgba >>> 24) & 0xFF;
                int green = (rgba >>> 16) & 0xFF;
                int blue = (rgba >>> 8) & 0xFF;
                int alpha = rgba & 0xFF;
                return new Color(red, green, blue, alpha);
            }

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
