package com.ignis.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * McpCoordination - Camada de coordenacao multi-agente do MCP do IgnisEngine.
 *
 * <p>Permite que varias IAs (ex.: Claude e Gemini) trabalhem no mesmo projeto ao
 * mesmo tempo, conversando e dividindo o trabalho **atraves do proprio MCP**, sem
 * pisar uma no trabalho da outra ("desavencas"). Concentra quatro coisas:</p>
 * <ul>
 *   <li><b>Presenca:</b> quem esta conectado (agentes registrados + ultima atividade);</li>
 *   <li><b>Mural de mensagens:</b> chat compartilhado (broadcast ou direcionado),
 *       com numero de sequencia para polling incremental;</li>
 *   <li><b>Claims (locks):</b> reserva de um recurso (script, objeto...) por um
 *       agente, com expiracao — para dois agentes nao editarem a mesma coisa.
 *       Os claims sao <b>aplicados</b> pelo gate central do
 *       {@link IgnisToolRegistry#call}: toda ferramenta que muta cena, objeto,
 *       camera, mundo ou script e barrada se o alvo pertencer a outro agente;</li>
 *   <li><b>Tarefas:</b> quadro simples para o usuario dividir o trabalho e os
 *       agentes reivindicarem/concluirem.</li>
 * </ul>
 *
 * <p>Singleton estatico: o estado sobrevive a recriacao do {@link IgnisToolRegistry}
 * (troca de projeto), mas e em memoria (perde no fechamento do editor). Todo acesso
 * vem das ferramentas MCP, executadas na thread de UI do JavaFX
 * (via {@link IgnisMcpBridge#runOnFxThread}) — portanto serializado; ainda assim,
 * os metodos sao {@code synchronized} por seguranca.</p>
 */
public final class McpCoordination {

    /** Janela de presenca: um agente e "ativo" se foi visto nos ultimos 10 min. */
    private static final long PRESENCE_WINDOW_MS = 10 * 60 * 1000L;
    /** Um claim expira (auto-libera) apos 10 min sem renovacao. */
    private static final long CLAIM_TTL_MS = 10 * 60 * 1000L;

    private static final McpCoordination INSTANCE = new McpCoordination();

    public static McpCoordination get() { return INSTANCE; }

    private McpCoordination() {}

    // ------------------------------------------------------------------
    // Estruturas
    // ------------------------------------------------------------------

    private static final class Message {
        final long seq;
        final long millis;
        final String from;
        final String to;   // null = broadcast
        final String text;
        Message(long seq, long millis, String from, String to, String text) {
            this.seq = seq; this.millis = millis; this.from = from; this.to = to; this.text = text;
        }
    }

    private static final class Claim {
        final String agent;
        long millis;
        Claim(String agent, long millis) { this.agent = agent; this.millis = millis; }
    }

    private static final class Task {
        final int id;
        final String title;
        final String description;
        String owner;   // null = sem dono
        String status;  // "aberta" | "em_andamento" | "concluida"
        Task(int id, String title, String description) {
            this.id = id; this.title = title; this.description = description;
            this.status = "aberta";
        }
    }

    private final Map<String, Long> agents = new LinkedHashMap<>();   // nome -> ultima atividade
    private final Map<String, String> agentRoles = new LinkedHashMap<>();
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Claim> claims = new LinkedHashMap<>();  // recurso -> claim
    private final List<Task> tasks = new ArrayList<>();
    private long seqCounter = 0;
    private int taskCounter = 0;

    // ------------------------------------------------------------------
    // Presenca
    // ------------------------------------------------------------------

    /**
     * Marca presenca sem entrar no mural. Chamado pelo {@link IgnisToolRegistry} a
     * cada ferramenta executada com 'agent' — assim um agente que trabalha sem
     * chamar agent_join ainda aparece em agent_list e nao "some" da presenca
     * enquanto trabalha.
     */
    public synchronized void touchAgent(String agent) {
        touch(agent);
    }

    /** Registra/atualiza a presenca de um agente. Retorna a lista de ativos. */
    public synchronized String join(String agent, String role) {
        touch(agent);
        if (role != null && !role.isBlank()) agentRoles.put(agent, role.trim());
        String others = activeAgents(agent);
        system(agent + " entrou" + (role != null && !role.isBlank() ? " (" + role.trim() + ")" : "") + ".");
        return "Bem-vindo, " + agent + ".\n" + (others.isEmpty()
                ? "Voce e o unico agente ativo no momento."
                : "Outros agentes ativos: " + others);
    }

    private void touch(String agent) {
        if (agent != null && !agent.isBlank()) agents.put(agent, System.currentTimeMillis());
    }

    public synchronized String listAgents() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : agents.entrySet()) {
            long ago = (now - e.getValue()) / 1000;
            boolean active = (now - e.getValue()) <= PRESENCE_WINDOW_MS;
            sb.append(active ? "[ativo] " : "[inativo] ").append(e.getKey());
            String r = agentRoles.get(e.getKey());
            if (r != null) sb.append(" — ").append(r);
            sb.append("  (visto ha ").append(ago).append("s)\n");
        }
        return sb.length() == 0 ? "(nenhum agente registrado)" : sb.toString();
    }

    private String activeAgents(String exclude) {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : agents.entrySet()) {
            if (e.getKey().equalsIgnoreCase(exclude)) continue;
            if ((now - e.getValue()) <= PRESENCE_WINDOW_MS) out.add(e.getKey());
        }
        return String.join(", ", out);
    }

    // ------------------------------------------------------------------
    // Mensagens
    // ------------------------------------------------------------------

    /** Posta uma mensagem no mural (to null/vazio = broadcast). */
    public synchronized String sendMessage(String from, String to, String text) {
        touch(from);
        String target = (to == null || to.isBlank()) ? null : to.trim();
        messages.add(new Message(++seqCounter, System.currentTimeMillis(), from, target, text));
        // Limita o historico para nao crescer sem limite.
        while (messages.size() > 500) messages.remove(0);
        return "Mensagem enviada" + (target != null ? " para " + target : " (broadcast)") + ". seq=" + seqCounter;
    }

    private void system(String text) {
        messages.add(new Message(++seqCounter, System.currentTimeMillis(), "sistema", null, text));
        while (messages.size() > 500) messages.remove(0);
    }

    /**
     * Le as mensagens relevantes para {@code agent} (broadcast + direcionadas a ele
     * + enviadas por ele) com seq maior que {@code since}. Retorna tambem o maior
     * seq atual, para o agente continuar o polling.
     */
    public synchronized String readMessages(String agent, long since) {
        touch(agent);
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            if (m.seq <= since) continue;
            boolean relevant = m.to == null
                    || (agent != null && (m.to.equalsIgnoreCase(agent) || (m.from != null && m.from.equalsIgnoreCase(agent))));
            if (!relevant) continue;
            sb.append('[').append(m.seq).append("] ").append(m.from);
            if (m.to != null) sb.append(" -> ").append(m.to);
            sb.append(": ").append(m.text).append('\n');
        }
        String body = sb.length() == 0 ? "(sem novas mensagens)" : sb.toString();
        return body + "\n---\nlastSeq=" + seqCounter + " (use como 'since' na proxima leitura)";
    }

    // ------------------------------------------------------------------
    // Claims (locks de recurso)
    // ------------------------------------------------------------------

    /** Reserva um recurso para um agente. Falha se outro agente o segura (nao expirado). */
    public synchronized String claim(String agent, String resource) {
        touch(agent);
        long now = System.currentTimeMillis();
        Claim existing = claims.get(resource);
        if (existing != null && !existing.agent.equalsIgnoreCase(agent) && (now - existing.millis) <= CLAIM_TTL_MS) {
            return "NEGADO: '" + resource + "' esta reservado por " + existing.agent
                    + ". Combine pelo mural (send_message) ou espere liberar.";
        }
        claims.put(resource, new Claim(agent, now));
        return "OK: '" + resource + "' reservado para " + agent + ".";
    }

    /** Libera um recurso (apenas o dono, ou um claim ja expirado). */
    public synchronized String release(String agent, String resource) {
        Claim existing = claims.get(resource);
        if (existing == null) return "'" + resource + "' nao estava reservado.";
        long now = System.currentTimeMillis();
        boolean expired = (now - existing.millis) > CLAIM_TTL_MS;
        if (!existing.agent.equalsIgnoreCase(agent) && !expired) {
            return "NEGADO: '" + resource + "' pertence a " + existing.agent + ".";
        }
        claims.remove(resource);
        return "OK: '" + resource + "' liberado.";
    }

    public synchronized String listClaims() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Claim> e : claims.entrySet()) {
            Claim c = e.getValue();
            if ((now - c.millis) > CLAIM_TTL_MS) { stale.add(e.getKey()); continue; }
            sb.append(e.getKey()).append(" -> ").append(c.agent)
              .append("  (ha ").append((now - c.millis) / 1000).append("s)\n");
        }
        for (String s : stale) claims.remove(s); // limpa expirados
        return sb.length() == 0 ? "(nenhum recurso reservado)" : sb.toString();
    }

    /**
     * Quem segura um recurso, ou null se livre/expirado. Usado pelas ferramentas de
     * escrita para respeitar o claim de OUTRO agente.
     */
    public synchronized String holderOf(String resource) {
        Claim c = claims.get(resource);
        if (c == null) return null;
        if ((System.currentTimeMillis() - c.millis) > CLAIM_TTL_MS) { claims.remove(resource); return null; }
        return c.agent;
    }

    /**
     * Primeiro recurso reservado por um agente DIFERENTE de {@code agent}, no formato
     * {@code "recurso (dono)"}; null se ninguem mais segura nada.
     *
     * <p>Serve as operacoes destrutivas de escopo amplo (limpar a cena, iniciar o
     * Play): elas nao miram um recurso especifico, entao a checagem por chave nao
     * as pegaria — mas atropelam o trabalho de quem quer que esteja editando.</p>
     */
    public synchronized String anyHolderExcept(String agent) {
        long now = System.currentTimeMillis();
        List<String> stale = new ArrayList<>();
        String found = null;
        for (Map.Entry<String, Claim> e : claims.entrySet()) {
            Claim c = e.getValue();
            if ((now - c.millis) > CLAIM_TTL_MS) { stale.add(e.getKey()); continue; }
            if (agent != null && c.agent.equalsIgnoreCase(agent)) continue;
            if (found == null) found = e.getKey() + " (" + c.agent + ")";
        }
        for (String s : stale) claims.remove(s);
        return found;
    }

    // ------------------------------------------------------------------
    // Tarefas
    // ------------------------------------------------------------------

    public synchronized String createTask(String title, String description) {
        Task t = new Task(++taskCounter, title, description == null ? "" : description);
        tasks.add(t);
        return "Tarefa #" + t.id + " criada: " + t.title;
    }

    public synchronized String assignTask(int id, String agent) {
        for (Task t : tasks) {
            if (t.id == id) {
                t.owner = agent;
                t.status = "em_andamento";
                touch(agent);
                return "Tarefa #" + id + " atribuida a " + agent + ".";
            }
        }
        return "Tarefa #" + id + " nao encontrada.";
    }

    public synchronized String completeTask(int id) {
        for (Task t : tasks) {
            if (t.id == id) {
                t.status = "concluida";
                return "Tarefa #" + id + " concluida.";
            }
        }
        return "Tarefa #" + id + " nao encontrada.";
    }

    public synchronized String listTasks() {
        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            sb.append('#').append(t.id).append(" [").append(t.status).append("] ").append(t.title);
            if (t.owner != null) sb.append(" (").append(t.owner).append(')');
            if (!t.description.isBlank()) sb.append(" — ").append(t.description);
            sb.append('\n');
        }
        return sb.length() == 0 ? "(nenhuma tarefa)" : sb.toString();
    }
}
