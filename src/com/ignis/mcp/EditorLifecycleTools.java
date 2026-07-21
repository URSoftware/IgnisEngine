package com.ignis.mcp;

import com.ignis.core.GameObject;

import java.util.List;

/**
 * Ferramentas MCP de ciclo de vida do editor: reiniciar o processo e consultar o
 * estado atual. Nascem do roadmap de producao agentica (lacuna "coordenacao MCP e
 * volatil" + necessidade de um agente recuperar/religar o editor sem depender do
 * usuario mexer na janela).
 *
 * <p>{@code restart_editor} relanca uma JVM nova do editor que reabre o ultimo
 * projeto e re-sobe o bridge HTTP na MESMA porta/token (via {@code EditorPrefs}).
 * Combinado com a persistencia de {@link McpCoordination} em
 * {@code .ignis/coordination.json}, o mural, os claims e as tarefas sobrevivem ao
 * reinicio — o agente so precisa aguardar alguns segundos e reconectar na mesma URL.</p>
 */
final class EditorLifecycleTools {

    private final IgnisToolRegistry reg;

    EditorLifecycleTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        // restart_editor — salva o projeto e relanca o editor num processo novo.
        reg.add("restart_editor",
            "Reinicia o editor: salva o projeto e relanca o processo, que reabre o ultimo projeto e "
            + "re-sobe o bridge MCP na MESMA porta/token. Use para recuperar de um estado travado ou "
            + "carregar codigo/scripts recompilados. O bridge fica alguns segundos fora do ar durante o "
            + "relançamento — aguarde ~5-10s e reconecte na mesma URL. Mural, claims e tarefas sobrevivem.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.restartHook == null) {
                    return "Erro: reinicio indisponivel (editor sem GUI ou contexto vivo nao registrado).";
                }
                // Dispara o reinicio com um pequeno atraso, numa thread propria, para
                // que ESTA resposta (HTTP/STDIO) chegue ao agente antes de a JVM cair.
                Runnable hook = reg.restartHook;
                Thread t = new Thread(() -> {
                    try { Thread.sleep(1200); } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    hook.run();
                }, "IgnisEditor-Restart");
                t.setDaemon(true);
                t.start();
                return "Reiniciando o editor em ~1s. O bridge MCP volta na mesma URL em alguns segundos; "
                        + "aguarde e refaca a conexao (o mural, os claims e as tarefas foram preservados).";
            });

        // get_editor_status — panorama do editor vivo para o agente se orientar
        // (sobretudo apos um restart_editor, para confirmar que o editor voltou).
        reg.add("get_editor_status",
            "Retorna o estado atual do editor: projeto, cenas, contagem de objetos e scripts, estado do "
            + "bridge MCP (URL/porta/ferramentas) e agentes ativos. Use para confirmar que o editor voltou "
            + "apos um restart_editor.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Projeto: ")
                  .append(reg.projectFolder != null ? reg.projectFolder.getAbsolutePath() : "(nenhum)")
                  .append('\n');
                sb.append("Editor vivo: ").append(reg.liveGame != null ? "sim" : "nao (headless)").append('\n');

                if (reg.sceneHost != null) {
                    List<String> scenes = reg.sceneHost.listScenes();
                    sb.append("Cenas (").append(scenes.size()).append("): ")
                      .append(scenes.isEmpty() ? "(nenhuma)" : String.join(", ", scenes)).append('\n');
                }
                if (reg.liveGame != null) {
                    List<GameObject> objs = reg.liveGame.getEntities();
                    sb.append("Objetos na cena ativa: ").append(objs != null ? objs.size() : 0).append('\n');
                }
                try {
                    sb.append("Scripts do projeto: ")
                      .append(reg.scriptManager().listAvailableScripts().size()).append('\n');
                } catch (Exception ignore) { /* projeto sem scripts ainda */ }

                McpHttpBridge bridge = McpHttpBridge.current();
                if (bridge != null) {
                    sb.append("Bridge HTTP: ativo em ").append(bridge.getUrl())
                      .append(" (porta ").append(bridge.getPort()).append(")\n");
                } else {
                    sb.append("Bridge HTTP: inativo\n");
                }
                sb.append("Ferramentas expostas: ").append(reg.list().size()).append('\n');
                sb.append("--- Agentes ---\n").append(McpCoordination.get().listAgents());
                return sb.toString();
            });
    }
}
