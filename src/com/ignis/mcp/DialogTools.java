package com.ignis.mcp;

import com.ignis.dialog.Dialog;
import com.ignis.dialog.DialogIO;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ferramentas MCP de autoria e preview de diálogos data-driven (roadmap P1, passo 6 —
 * "editor de diálogo orientado a dados"). API-first, mesmo padrão das cutscenes: o
 * agente autora um grafo de nós/escolhas num JSON do projeto
 * ({@code dialogs/<id>.dialog.json}), valida IDs/estrutura e faz preview percorrendo
 * escolhas — tudo sem tocar a cena nem exigir Play.
 *
 * <p>A engine NÃO exibe o diálogo sozinha: um script consome o JSON e desenha com a UI
 * persistente (fatia 2a). Ponte com cutscene: uma track DIALOG pode referenciar
 * {@code dialog:<id>#<node>} no campo {@code data}.</p>
 */
final class DialogTools {

    private final IgnisToolRegistry reg;

    DialogTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerAuthoring();
        registerInspection();
    }

    // ------------------------------------------------------------------
    // Autoria (arquivos do projeto)
    // ------------------------------------------------------------------

    private void registerAuthoring() {
        Map<String, String> createProps = new LinkedHashMap<>();
        createProps.put("id", "Id do diálogo (letras/dígitos/_/-; vira dialogs/<id>.dialog.json).");
        createProps.put("start", "Id do nó inicial (padrão 'start').");
        reg.add("create_dialog",
            "Cria um diálogo vazio no projeto (grafo de nós data-driven). Depois use set_dialog_node para "
            + "adicionar falas/escolhas; validate_dialog confere IDs e referências; preview_dialog percorre o "
            + "grafo sem Play.",
            IgnisToolRegistry.schemaWith(createProps, List.of("id")),
            args -> {
                String id = args.optString("id", "").trim();
                if (!DialogIO.isValidId(id)) {
                    return "Erro: id inválido (use letras, dígitos, '_' ou '-').";
                }
                if (DialogIO.exists(reg.projectFolder, id)) {
                    return "Erro: já existe diálogo '" + id + "'.";
                }
                Dialog d = new Dialog(id, args.optString("start", "start").trim());
                DialogIO.save(reg.projectFolder, d);
                return "Diálogo '" + id + "' criado (nó inicial: '" + d.getStart() + "').";
            });

        Map<String, String> nodeProps = new LinkedHashMap<>();
        nodeProps.put("id", "Id do diálogo.");
        nodeProps.put("nodeId", "Id do nó (cria ou substitui).");
        nodeProps.put("speaker", "Nome de quem fala (opcional).");
        nodeProps.put("portrait", "Caminho do retrato no projeto (opcional; validado).");
        nodeProps.put("text", "Texto da fala.");
        nodeProps.put("next", "Id do próximo nó no fluxo linear (vazio = terminal, se não houver choices).");
        nodeProps.put("choices", "Array JSON de escolhas: [{\"text\":.., \"next\":.., \"setFlag\"?:.., "
                + "\"condition\"?:..}]. Substitui as escolhas do nó.");
        nodeProps.put("makeStart", "true para tornar este nó o inicial do diálogo.");
        reg.add("set_dialog_node",
            "Adiciona ou substitui um nó do diálogo (fala de um speaker com texto e saída via 'next' OU "
            + "'choices'). Uma escolha pode setar flag e ter condição. Cria o nó se não existir.",
            IgnisToolRegistry.schemaWith(nodeProps, List.of("id", "nodeId")),
            args -> {
                Dialog d = loadOrNull(args);
                if (d == null) return notFound(args);
                String nodeId = args.optString("nodeId", "").trim();
                if (nodeId.isEmpty()) return "Erro: 'nodeId' obrigatório.";
                Dialog.Node node = new Dialog.Node(nodeId,
                        args.optString("speaker", ""), args.optString("portrait", ""),
                        args.optString("text", ""), args.optString("next", ""));

                JSONArray choices = args.optJSONArray("choices");
                if (choices == null && args.has("choices")) {
                    try {
                        choices = new JSONArray(args.optString("choices", ""));
                    } catch (Exception e) {
                        return "Erro: 'choices' deve ser um array JSON de {text, next, setFlag?, condition?}.";
                    }
                }
                if (choices != null) {
                    for (int i = 0; i < choices.length(); i++) {
                        JSONObject c = choices.optJSONObject(i);
                        if (c == null) return "Erro: escolha " + i + " não é um objeto JSON.";
                        node.addChoice(new Dialog.Choice(c.optString("text", ""), c.optString("next", ""),
                                c.optString("setFlag", ""), c.optString("condition", "")));
                    }
                }
                d.putNode(node);
                if (args.optBoolean("makeStart", false)) d.setStart(nodeId);
                DialogIO.save(reg.projectFolder, d);
                return "Nó '" + nodeId + "' gravado em '" + d.getId() + "' ("
                        + node.getChoices().size() + " escolha(s)"
                        + (args.optBoolean("makeStart", false) ? ", agora é o start" : "") + ").";
            });

        Map<String, String> rmProps = new LinkedHashMap<>();
        rmProps.put("id", "Id do diálogo.");
        rmProps.put("nodeId", "Id do nó a remover.");
        reg.add("remove_dialog_node",
            "Remove um nó do diálogo. (validate_dialog aponta referências que ficarem quebradas.)",
            IgnisToolRegistry.schemaWith(rmProps, List.of("id", "nodeId")),
            args -> {
                Dialog d = loadOrNull(args);
                if (d == null) return notFound(args);
                String nodeId = args.optString("nodeId", "").trim();
                if (!d.removeNode(nodeId)) return "Erro: nó '" + nodeId + "' não encontrado.";
                DialogIO.save(reg.projectFolder, d);
                return "Nó '" + nodeId + "' removido de '" + d.getId() + "'.";
            });

        reg.add("delete_dialog",
            "Apaga um diálogo do projeto (remove dialogs/<id>.dialog.json).",
            IgnisToolRegistry.schemaWith(Map.of("id", "Id do diálogo a apagar"), List.of("id")),
            args -> {
                String id = args.optString("id", "").trim();
                if (!DialogIO.isValidId(id)) return "Erro: id inválido.";
                if (!DialogIO.delete(reg.projectFolder, id)) {
                    return "Erro: diálogo '" + id + "' não encontrado.";
                }
                return "Diálogo '" + id + "' apagado.";
            });
    }

    // ------------------------------------------------------------------
    // Inspeção, validação e preview (read-only)
    // ------------------------------------------------------------------

    private void registerInspection() {
        reg.add("list_dialogs",
            "Lista os diálogos do projeto (pasta dialogs/).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                List<String> ids = DialogIO.listIds(reg.projectFolder);
                if (ids.isEmpty()) return "(nenhum diálogo; crie com create_dialog)";
                StringBuilder sb = new StringBuilder(ids.size() + " diálogo(s):\n");
                for (String id : ids) {
                    Dialog d = DialogIO.load(reg.projectFolder, id);
                    sb.append("- ").append(id);
                    if (d != null) sb.append(" (").append(d.getNodes().size()).append(" nós, start '")
                            .append(d.getStart()).append("')");
                    else sb.append(" (CORROMPIDO)");
                    sb.append('\n');
                }
                return sb.toString();
            });

        reg.add("get_dialog",
            "Retorna o grafo completo de um diálogo (nós e escolhas em JSON).",
            IgnisToolRegistry.schemaWith(Map.of("id", "Id do diálogo"), List.of("id")),
            args -> {
                Dialog d = loadOrNull(args);
                if (d == null) return notFound(args);
                return d.toJSON().toString(2);
            });

        reg.add("validate_dialog",
            "Valida um diálogo: nó inicial existe, referências de next/escolha apontam para nós existentes, nós "
            + "inalcançáveis, texto/escolha vazios, retrato ausente, condição nunca setada e ausência de nó "
            + "terminal alcançável (ciclo). Retorna 'OK' se nada for encontrado.",
            IgnisToolRegistry.schemaWith(Map.of("id", "Id do diálogo"), List.of("id")),
            args -> {
                Dialog d = loadOrNull(args);
                if (d == null) return notFound(args);
                List<String> issues = d.validate(rel -> {
                    java.io.File f = reg.resolveInProject(rel);
                    return f != null && f.isFile();
                });
                if (issues.isEmpty()) {
                    return "OK: diálogo '" + d.getId() + "' válido (" + d.getNodes().size() + " nós).";
                }
                StringBuilder sb = new StringBuilder(issues.size() + " achado(s):\n");
                for (String i : issues) sb.append("- ").append(i).append('\n');
                return sb.toString();
            });

        Map<String, String> prevProps = new LinkedHashMap<>();
        prevProps.put("id", "Id do diálogo.");
        prevProps.put("fromNode", "Nó de início do percurso (padrão: o start do diálogo).");
        prevProps.put("choicesPath", "Array JSON de índices (0-based) da escolha a seguir em cada nó com "
                + "ramificação, na ordem em que aparecem. Ex: [0,1].");
        reg.add("preview_dialog",
            "Percorre o diálogo simulando escolhas e devolve a transcrição (speaker: texto) SEM tocar a cena "
            + "nem exigir Play. Em nós com escolhas, segue o índice de choicesPath; sem índice (ou fora do "
            + "alcance), para e lista as opções disponíveis.",
            IgnisToolRegistry.schemaWith(prevProps, List.of("id")),
            args -> {
                Dialog d = loadOrNull(args);
                if (d == null) return notFound(args);
                String from = args.optString("fromNode", "").trim();
                if (from.isEmpty()) from = d.getStart();
                if (d.getNode(from) == null) return "Erro: nó '" + from + "' não existe neste diálogo.";

                int[] path = parsePath(args);
                StringBuilder sb = new StringBuilder("Percurso de '" + d.getId() + "' desde '" + from + "':\n");
                java.util.Set<String> guard = new java.util.HashSet<>();
                String cursor = from;
                int choiceIdx = 0;
                while (cursor != null && !cursor.isEmpty()) {
                    Dialog.Node n = d.getNode(cursor);
                    if (n == null) { sb.append("  [!] nó '").append(cursor).append("' não existe.\n"); break; }
                    if (!guard.add(cursor)) { sb.append("  [ciclo] '").append(cursor).append("' já visitado — paro.\n"); break; }
                    sb.append("  ").append(n.speaker.isEmpty() ? "(narrador)" : n.speaker)
                      .append(": ").append(n.text).append('\n');

                    List<Dialog.Choice> choices = n.getChoices();
                    if (!choices.isEmpty()) {
                        if (path != null && choiceIdx < path.length && path[choiceIdx] >= 0
                                && path[choiceIdx] < choices.size()) {
                            Dialog.Choice chosen = choices.get(path[choiceIdx]);
                            sb.append("    -> escolha [").append(path[choiceIdx]).append("]: ")
                              .append(chosen.text);
                            if (!chosen.setFlag.isEmpty()) sb.append(" (seta ").append(chosen.setFlag).append(')');
                            sb.append('\n');
                            choiceIdx++;
                            cursor = chosen.next;
                        } else {
                            sb.append("    (escolhas disponíveis — informe choicesPath para seguir):\n");
                            for (int i = 0; i < choices.size(); i++) {
                                sb.append("      [").append(i).append("] ").append(choices.get(i).text).append('\n');
                            }
                            break;
                        }
                    } else {
                        cursor = n.next;
                    }
                }
                return sb.toString();
            });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private int[] parsePath(JSONObject args) {
        JSONArray arr = args.optJSONArray("choicesPath");
        if (arr == null && args.has("choicesPath")) {
            try { arr = new JSONArray(args.optString("choicesPath", "")); }
            catch (Exception e) { return null; }
        }
        if (arr == null) return null;
        int[] path = new int[arr.length()];
        for (int i = 0; i < arr.length(); i++) path[i] = arr.optInt(i, -1);
        return path;
    }

    private Dialog loadOrNull(JSONObject args) {
        String id = args.optString("id", "").trim();
        if (!DialogIO.isValidId(id)) return null;
        return DialogIO.load(reg.projectFolder, id);
    }

    private String notFound(JSONObject args) {
        String id = args.optString("id", "").trim();
        if (!DialogIO.isValidId(id)) return "Erro: id inválido (use letras, dígitos, '_' ou '-').";
        return "Erro: diálogo '" + id + "' não encontrado. Existentes: "
                + DialogIO.listIds(reg.projectFolder) + ".";
    }
}
