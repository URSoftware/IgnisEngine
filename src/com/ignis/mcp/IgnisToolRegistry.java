package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.ScriptManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return IgnisMcpBridge.runOnFxThread(() -> {
            try {
                return def.handler.execute(safeArgs);
            } catch (Exception e) {
                return "Erro ao executar '" + name + "': " + e.getMessage();
            }
        });
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
            "Lista os GameObjects da cena ativa (nome, posicao, tamanho, scripts).",
            objectSchema(),
            args -> {
                if (liveGame == null) return "Erro: editor nao disponivel.";
                StringBuilder sb = new StringBuilder();
                for (GameObject go : liveGame.getEntities()) {
                    sb.append(go.getName())
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
                go.setSpritePath(args.optString("path", ""));
                if (refreshHook != null) refreshHook.run();
                return "Sprite definido para " + go.getName() + ": " + args.optString("path", "");
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
    }

    // Resolve um caminho relativo garantindo que permaneca dentro do projeto (anti path-traversal).
    private File resolveInProject(String relative) {
        try {
            File base = projectFolder.getCanonicalFile();
            File target = new File(base, relative).getCanonicalFile();
            if (target.getPath().startsWith(base.getPath())) return target;
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
