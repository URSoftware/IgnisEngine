package com.ignis.mcp;

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

    public IgnisToolRegistry(File projectFolder) {
        this.projectFolder = projectFolder;
        registerDefaults();
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
