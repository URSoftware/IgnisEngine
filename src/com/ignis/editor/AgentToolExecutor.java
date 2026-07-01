package com.ignis.editor;

import com.ignis.mcp.IgnisToolRegistry;
import com.ignis.mcp.McpService;
import org.json.JSONObject;

/**
 * AgentToolExecutor - Cola entre um agente de IA (Gemini/NVIDIA ou, no futuro,
 * uma IA embarcada) e as ferramentas do IgnisEngine expostas pelo
 * {@link IgnisToolRegistry}.
 *
 * <p>Providers de IA baseados em texto (como Gemini e NVIDIA neste projeto) nao
 * executam ferramentas nativamente. Esta classe fornece as duas metades
 * necessarias para um laco agentico simples via prompt:</p>
 * <ol>
 *   <li>{@link #toolManifest()} descreve as ferramentas disponiveis para injetar
 *       no system prompt;</li>
 *   <li>{@link #tryHandleToolCall(String)} detecta um pedido de ferramenta emitido
 *       pelo modelo (JSON no formato {@code {"tool":"nome","arguments":{...}}}) e o
 *       executa contra o registry, devolvendo o resultado para ser realimentado ao
 *       modelo.</li>
 * </ol>
 *
 * <p>O registry usado e o mesmo publicado pelo {@link McpService}, garantindo
 * paridade total entre o que agentes externos (via URL) e a IA do editor enxergam.
 * O plano completo do laco agentico esta documentado em
 * {@code doc/AGENTIC_AI_PLAN.md}.</p>
 */
public final class AgentToolExecutor {

    private AgentToolExecutor() {}

    /** Ha um registry de ferramentas disponivel (MCP ativo com projeto)? */
    public static boolean hasTools() {
        return McpService.getRegistry() != null;
    }

    /**
     * Descricao textual das ferramentas para o system prompt do agente. Segue um
     * formato estavel que instrui o modelo a emitir chamadas em JSON.
     */
    public static String toolManifest() {
        IgnisToolRegistry registry = McpService.getRegistry();
        if (registry == null) return "(nenhuma ferramenta disponivel: ative o MCP com um projeto aberto)";

        StringBuilder sb = new StringBuilder();
        sb.append("Voce pode usar ferramentas do IgnisEngine. Para chamar uma ferramenta, ")
          .append("responda APENAS com um JSON na forma:\n")
          .append("{\"tool\":\"<nome>\",\"arguments\":{...}}\n")
          .append("Ferramentas disponiveis:\n");
        for (IgnisToolRegistry.ToolDef def : registry.list()) {
            sb.append("- ").append(def.name).append(": ").append(def.description).append('\n');
            sb.append("    schema: ").append(def.inputSchema.toString()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Tenta interpretar a resposta do modelo como uma chamada de ferramenta e
     * executa-la. Retorna o texto de resultado da ferramenta, ou {@code null} se a
     * resposta nao for uma chamada de ferramenta (texto normal).
     */
    public static String tryHandleToolCall(String modelResponse) {
        if (modelResponse == null) return null;
        String trimmed = modelResponse.trim();
        // Remove cercas de codigo comuns emitidas por LLMs.
        if (trimmed.startsWith("```")) {
            int nl = trimmed.indexOf('\n');
            if (nl >= 0) trimmed = trimmed.substring(nl + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        if (!trimmed.startsWith("{") || !trimmed.contains("\"tool\"")) return null;

        try {
            JSONObject obj = new JSONObject(trimmed);
            String tool = obj.optString("tool", "").trim();
            if (tool.isEmpty()) return null;
            JSONObject args = obj.optJSONObject("arguments");
            IgnisToolRegistry registry = McpService.getRegistry();
            if (registry == null) return "Erro: nenhum registry de ferramentas ativo.";
            return registry.call(tool, args);
        } catch (Exception e) {
            // Nao era uma chamada de ferramenta valida; deixa a UI tratar como texto.
            return null;
        }
    }
}
