package com.ignis.dialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diálogo data-driven orientado a grafo de nós (roadmap P1 — "editor de diálogo
 * orientado a dados com speaker, retrato, condição, escolha, flag e validação de ID").
 *
 * <p>Modelo puro, sem UI nem runtime acoplados: a engine NÃO exibe o diálogo sozinha —
 * um script consome este JSON e desenha com a UI persistente da fatia 2a (painel +
 * label + botões de escolha), na mesma filosofia do CutscenePlayer, que reporta em vez
 * de acoplar. O editor valida ESTRUTURA e IDs, nunca o conteúdo (regra do roadmap: não
 * armazenar material protegido copiado da obra).</p>
 *
 * <p>Cada nó tem um speaker, retrato opcional, texto e ou {@code next} (fluxo linear)
 * ou uma lista de {@link Choice} (ramificação). Uma escolha pode setar uma flag e ter
 * uma condição (flag exigida). Persistido em {@code dialogs/<id>.dialog.json}.</p>
 */
public final class Dialog {

    /** Uma escolha do jogador num nó: texto, próximo nó, flag a setar e condição. */
    public static final class Choice {
        public final String text;
        public final String next;
        /** Flag setada ao escolher esta opção (ex.: "aceitou_missao"). Pode ser vazia. */
        public final String setFlag;
        /** Flag exigida para a opção aparecer (ex.: "tem_chave"). Pode ser vazia. */
        public final String condition;

        public Choice(String text, String next, String setFlag, String condition) {
            this.text = text != null ? text : "";
            this.next = next != null ? next : "";
            this.setFlag = setFlag != null ? setFlag : "";
            this.condition = condition != null ? condition : "";
        }

        JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("text", text);
            o.put("next", next);
            if (!setFlag.isEmpty()) o.put("setFlag", setFlag);
            if (!condition.isEmpty()) o.put("condition", condition);
            return o;
        }

        static Choice fromJSON(JSONObject o) {
            return new Choice(o.optString("text", ""), o.optString("next", ""),
                    o.optString("setFlag", ""), o.optString("condition", ""));
        }
    }

    /** Um nó do diálogo: fala de um speaker com retrato, texto e saída (next OU choices). */
    public static final class Node {
        public final String id;
        public final String speaker;
        public final String portrait;
        public final String text;
        /** Próximo nó no fluxo linear (vazio = nó terminal, se não houver choices). */
        public final String next;
        private final List<Choice> choices = new ArrayList<>();

        public Node(String id, String speaker, String portrait, String text, String next) {
            this.id = id;
            this.speaker = speaker != null ? speaker : "";
            this.portrait = portrait != null ? portrait : "";
            this.text = text != null ? text : "";
            this.next = next != null ? next : "";
        }

        public void addChoice(Choice c) { choices.add(c); }

        public List<Choice> getChoices() { return new ArrayList<>(choices); }

        public boolean isTerminal() { return next.isEmpty() && choices.isEmpty(); }

        JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("id", id);
            if (!speaker.isEmpty()) o.put("speaker", speaker);
            if (!portrait.isEmpty()) o.put("portrait", portrait);
            o.put("text", text);
            if (!next.isEmpty()) o.put("next", next);
            if (!choices.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (Choice c : choices) arr.put(c.toJSON());
                o.put("choices", arr);
            }
            return o;
        }

        static Node fromJSON(JSONObject o) {
            Node n = new Node(o.optString("id", ""), o.optString("speaker", ""),
                    o.optString("portrait", ""), o.optString("text", ""), o.optString("next", ""));
            JSONArray arr = o.optJSONArray("choices");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    if (c != null) n.addChoice(Choice.fromJSON(c));
                }
            }
            return n;
        }
    }

    private final String id;
    private String start;
    // LinkedHashMap: preserva a ordem de autoria (get_dialog fica estável/legível).
    private final Map<String, Node> nodes = new LinkedHashMap<>();

    public Dialog(String id, String start) {
        this.id = id;
        this.start = start != null ? start : "";
    }

    public String getId() { return id; }

    public String getStart() { return start; }

    public void setStart(String start) { this.start = start != null ? start : ""; }

    /** Insere ou substitui um nó pelo id. */
    public void putNode(Node node) { nodes.put(node.id, node); }

    public boolean removeNode(String nodeId) { return nodes.remove(nodeId) != null; }

    public Node getNode(String nodeId) { return nodes.get(nodeId); }

    public List<Node> getNodes() { return new ArrayList<>(nodes.values()); }

    // ------------------------------------------------------------------
    // Validação (roadmap P1: "validação de ID"; sem armazenar material da obra)
    // ------------------------------------------------------------------

    /**
     * {@code portraitExists} nulo pula a checagem de arquivo de retrato (sem projeto).
     * Reporta: sem nós, start ausente/inexistente, texto vazio, refs de next/choice
     * quebradas, nós inalcançáveis, escolha sem texto, retrato ausente, condição que
     * nenhuma escolha seta (aviso) e ausência de nó terminal alcançável (loop potencial).
     */
    public List<String> validate(java.util.function.Predicate<String> portraitExists) {
        List<String> issues = new ArrayList<>();
        if (nodes.isEmpty()) {
            issues.add("Diálogo sem nós.");
            return issues;
        }
        if (start.isEmpty()) {
            issues.add("Nó inicial (start) não definido.");
        } else if (!nodes.containsKey(start)) {
            issues.add("Nó inicial ausente: start='" + start + "' não existe.");
        }

        Set<String> setFlags = new HashSet<>();
        Set<String> usedConditions = new HashSet<>();
        for (Node n : nodes.values()) {
            if (n.text.isBlank() && n.getChoices().isEmpty()) {
                issues.add("Nó '" + n.id + "' sem texto nem escolhas.");
            }
            if (!n.next.isEmpty() && !nodes.containsKey(n.next)) {
                issues.add("Referência quebrada: nó '" + n.id + "' aponta next='" + n.next
                        + "' que não existe.");
            }
            if (n.portrait != null && !n.portrait.isEmpty() && portraitExists != null
                    && !portraitExists.test(n.portrait)) {
                issues.add("Retrato ausente: nó '" + n.id + "' usa '" + n.portrait
                        + "' (arquivo não encontrado no projeto).");
            }
            for (Choice c : n.getChoices()) {
                if (c.text.isBlank()) {
                    issues.add("Escolha sem texto no nó '" + n.id + "'.");
                }
                if (!c.next.isEmpty() && !nodes.containsKey(c.next)) {
                    issues.add("Referência quebrada: escolha do nó '" + n.id + "' aponta next='"
                            + c.next + "' que não existe.");
                }
                if (!c.setFlag.isEmpty()) setFlags.add(c.setFlag);
                if (!c.condition.isEmpty()) usedConditions.add(c.condition);
            }
        }

        // Flags exigidas por condição que nenhuma escolha seta: aviso (pode vir de fora).
        for (String cond : usedConditions) {
            if (!setFlags.contains(cond)) {
                issues.add("Aviso: condição '" + cond + "' nunca é setada por nenhuma escolha "
                        + "(pode ser uma flag externa ao diálogo).");
            }
        }

        // Alcançabilidade a partir do start + existência de terminal alcançável.
        if (!start.isEmpty() && nodes.containsKey(start)) {
            Set<String> reachable = new HashSet<>();
            boolean terminalReachable = walk(start, reachable);
            for (String nid : nodes.keySet()) {
                if (!reachable.contains(nid)) {
                    issues.add("Nó inalcançável: '" + nid + "' não é atingível a partir do start.");
                }
            }
            if (!terminalReachable) {
                issues.add("Aviso: nenhum nó terminal é alcançável a partir do start "
                        + "(o diálogo pode nunca fechar — verifique ciclos).");
            }
        }
        return issues;
    }

    // DFS: marca alcançáveis e retorna true se algum terminal é atingido.
    private boolean walk(String nodeId, Set<String> visited) {
        if (!visited.add(nodeId)) return false; // já visitado neste caminho de marcação
        Node n = nodes.get(nodeId);
        if (n == null) return false;
        boolean terminal = n.isTerminal();
        if (!n.next.isEmpty() && nodes.containsKey(n.next)) {
            terminal |= walk(n.next, visited);
        }
        for (Choice c : n.getChoices()) {
            if (!c.next.isEmpty() && nodes.containsKey(c.next)) {
                terminal |= walk(c.next, visited);
            } else if (c.next.isEmpty()) {
                terminal = true; // escolha que encerra o diálogo
            }
        }
        return terminal;
    }

    // ------------------------------------------------------------------
    // Serialização
    // ------------------------------------------------------------------

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("start", start);
        JSONArray arr = new JSONArray();
        for (Node n : nodes.values()) arr.put(n.toJSON());
        o.put("nodes", arr);
        return o;
    }

    public static Dialog fromJSON(JSONObject o) {
        Dialog d = new Dialog(o.optString("id", "dialogo"), o.optString("start", ""));
        JSONArray arr = o.optJSONArray("nodes");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject n = arr.optJSONObject(i);
                if (n != null) d.putNode(Node.fromJSON(n));
            }
        }
        return d;
    }
}
