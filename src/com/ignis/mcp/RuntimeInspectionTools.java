package com.ignis.mcp;

import com.ignis.core.Component;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.SceneValidator;
import com.ignis.core.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ferramentas MCP de observabilidade de runtime e validacao de cena (roadmap de
 * producao agentica, P0 "observabilidade de runtime" e "validacao e seguranca de
 * edicao"). Sao read-only: dao ao agente uma visao direta do que existe na cena e
 * apontam problemas ANTES de salvar/publicar, em vez de inferir por captura de tela.
 */
final class RuntimeInspectionTools {

    private final IgnisToolRegistry reg;

    RuntimeInspectionTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerListRuntimeObjects();
        registerRuntimeMetrics();
        registerValidateScene();
        registerSceneSnapshots();
        registerGetUiTree();
    }

    private void registerListRuntimeObjects() {
        reg.add("list_runtime_objects",
            "Lista os objetos da cena com ID, tipo, posicao, tamanho, z-index, visibilidade, pai, scripts e "
            + "componentes anexados. Visao direta do estado (inclui o que scripts esconderam no Play) para o "
            + "agente diagnosticar sem depender de captura de tela.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                List<GameObject> objs = reg.liveGame.getEntities();
                if (objs == null || objs.isEmpty()) return "(cena vazia)";
                StringBuilder sb = new StringBuilder();
                sb.append("Estado: ").append(reg.liveGame.getGameState())
                  .append(" | objetos: ").append(objs.size()).append('\n');
                for (GameObject go : objs) {
                    sb.append("- ").append(go.getName())
                      .append(" [").append(go.getType()).append("] id=").append(go.getId())
                      .append(" @(").append((int) go.getX()).append(',').append((int) go.getY()).append(')')
                      .append(' ').append(go.getWidth()).append('x').append(go.getHeight())
                      .append(" z=").append(go.getZIndex())
                      .append(go.isVisible() ? "" : " [oculto]");
                    GameObject parent = go.getParent();
                    if (parent != null) sb.append(" pai=").append(parent.getName());
                    List<String> scripts = go.getScriptNames();
                    if (scripts != null && !scripts.isEmpty()) sb.append(" scripts=").append(scripts);
                    List<Component> comps = go.getComponents();
                    if (comps != null && !comps.isEmpty()) {
                        List<String> cs = new ArrayList<>();
                        for (Component c : comps) cs.add(c.getClass().getSimpleName());
                        sb.append(" comps=").append(cs);
                    }
                    sb.append('\n');
                }
                return sb.toString();
            });
    }

    private void registerRuntimeMetrics() {
        reg.add("get_runtime_metrics",
            "Metricas do runtime: contagem de objetos, visiveis/ocultos, scripts e componentes anexados, "
            + "estado do mundo, memoria JVM e taxa fixa de simulacao. Util para detectar vazamento (objetos/"
            + "listeners que nao somem) e conferir escala da cena.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                List<GameObject> objs = reg.liveGame.getEntities();
                int total = objs != null ? objs.size() : 0;
                int hidden = 0, scriptCount = 0, compCount = 0, parented = 0;
                if (objs != null) {
                    for (GameObject go : objs) {
                        if (!go.isVisible()) hidden++;
                        if (go.getScripts() != null) scriptCount += go.getScripts().size();
                        if (go.getComponents() != null) compCount += go.getComponents().size();
                        if (go.getParent() != null) parented++;
                    }
                }
                Runtime rt = Runtime.getRuntime();
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long maxMb = rt.maxMemory() / (1024 * 1024);
                World world = reg.liveGame.getWorld();
                StringBuilder sb = new StringBuilder();
                sb.append("Estado do mundo: ").append(reg.liveGame.getGameState()).append('\n');
                sb.append("Objetos: ").append(total)
                  .append(" (visiveis ").append(total - hidden).append(", ocultos ").append(hidden).append(")\n");
                sb.append("Objetos com pai (hierarquia): ").append(parented).append('\n');
                sb.append("Scripts anexados: ").append(scriptCount).append('\n');
                sb.append("Componentes anexados: ").append(compCount).append('\n');
                sb.append("Mundo com limites: ").append(world != null && world.hasBounds() ? "sim" : "nao").append('\n');
                sb.append("Simulacao: ").append((int) Game.TICKS_PER_SECOND).append(" passos/s (fixo)\n");
                sb.append("Memoria JVM: ").append(usedMb).append(" MB usados / ").append(maxMb).append(" MB max");
                return sb.toString();
            });
    }

    private void registerValidateScene() {
        reg.add("validate_scene",
            "Valida a cena ativa e lista problemas ANTES de salvar/publicar: nomes duplicados, sprites de asset "
            + "ausentes, scripts nao encontrados/nao compilados, referencias de pai quebradas e objetos fora dos "
            + "limites do mundo. Retorna 'OK' se nada for encontrado.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                List<GameObject> objs = reg.liveGame.getEntities();
                if (objs == null || objs.isEmpty()) return "OK: cena vazia (nenhum problema).";

                List<String> availableScripts = null;
                try {
                    availableScripts = reg.scriptManager().listAvailableScripts();
                } catch (Exception ignore) { /* projeto sem scripts */ }

                // Regra compartilhada com o menu "Validar Cena..." do editor (SceneValidator).
                List<String> issues = SceneValidator.validate(objs, reg.liveGame.getWorld(),
                        reg.projectFolder, availableScripts);

                if (issues.isEmpty()) return "OK: nenhum problema encontrado (" + objs.size() + " objetos).";
                StringBuilder sb = new StringBuilder();
                sb.append(issues.size()).append(" problema(s) na cena:\n");
                for (String i : issues) sb.append("- ").append(i).append('\n');
                return sb.toString();
            });
    }

    // ------------------------------------------------------------------
    // Snapshots nomeados de cena (roadmap P0: compare_scene_snapshot — distinguir
    // edicao persistente de runtime transitorio, ex: antes/depois de um Play).
    // ------------------------------------------------------------------

    private void registerSceneSnapshots() {
        Map<String, String> snapProps = new HashMap<>();
        snapProps.put("label", "Nome do snapshot (para comparar depois). Padrao: 'default'.");
        reg.add("snapshot_scene",
            "Fotografa a cena atual (ID, nome, posicao, tamanho, z, visibilidade de cada objeto) sob um rotulo. "
            + "Compare depois com compare_scene_snapshot para distinguir edicao persistente de runtime "
            + "transitorio (ex: snapshot antes do Play, comparar depois do Stop).",
            IgnisToolRegistry.schemaWith(snapProps, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String label = args.optString("label", "default").trim();
                if (label.isEmpty()) label = "default";
                if (label.equals("current")) return "Erro: 'current' e reservado (significa a cena viva).";
                Map<String, String> snap = reg.snapshotScene();
                reg.storeSceneSnapshot(label, snap);
                return "Snapshot '" + label + "' salvo com " + snap.size() + " objeto(s). "
                        + "Compare com compare_scene_snapshot(before='" + label + "', after='current').";
            });

        Map<String, String> cmpProps = new HashMap<>();
        cmpProps.put("before", "Rotulo do snapshot base (ou 'current' para a cena viva).");
        cmpProps.put("after", "Rotulo do snapshot a comparar (padrao: 'current' = cena viva).");
        reg.add("compare_scene_snapshot",
            "Compara dois snapshots de cena (feitos com snapshot_scene) e resume o que mudou: objetos "
            + "adicionados, removidos e alterados (posicao/tamanho/z/visibilidade). Use 'current' para "
            + "comparar com a cena viva agora.",
            IgnisToolRegistry.schemaWith(cmpProps, List.of("before")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Map<String, String> before = resolveSnapshot(args.optString("before", "").trim());
                if (before == null) return "Erro: snapshot 'before' nao encontrado: " + args.optString("before", "")
                        + ". Rotulos salvos: " + reg.sceneSnapshotLabels() + " (ou use 'current').";
                String afterLabel = args.optString("after", "current").trim();
                Map<String, String> after = resolveSnapshot(afterLabel);
                if (after == null) return "Erro: snapshot 'after' nao encontrado: " + afterLabel
                        + ". Rotulos salvos: " + reg.sceneSnapshotLabels() + " (ou use 'current').";
                return IgnisToolRegistry.diffScenes(before, after);
            });
    }

    private Map<String, String> resolveSnapshot(String label) {
        if (label.isEmpty() || label.equals("current")) return reg.snapshotScene();
        return reg.getSceneSnapshot(label);
    }

    // ------------------------------------------------------------------
    // Arvore de UI (roadmap P0: get_ui_tree — bounds, ancoras, texto, interatividade
    // e origem de cada widget, sem depender de captura de tela).
    // ------------------------------------------------------------------

    private void registerGetUiTree() {
        Map<String, String> props = new HashMap<>();
        props.put("objectName", "So a UI do CanvasComponent deste objeto (padrao: canvas global de runtime + "
                + "todos os CanvasComponents da cena).");
        reg.add("get_ui_tree",
            "Arvore da UI in-game: cada widget com tipo, nome, bounds absolutos, ancora/pivo, z, texto, "
            + "visibilidade e interatividade — e de onde ele veio (canvas global volatil de runtime ou "
            + "CanvasComponent persistente de um objeto). Diagnostico direto de HUD/dialogo sem screenshot.",
            IgnisToolRegistry.schemaWith(props, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                StringBuilder sb = new StringBuilder();
                String only = args.optString("objectName", "").trim();
                if (!only.isEmpty()) {
                    GameObject go = reg.findObject(only);
                    if (go == null) return "Erro: objeto nao encontrado: " + only;
                    com.ignis.core.CanvasComponent cc = go.getComponent(com.ignis.core.CanvasComponent.class);
                    if (cc == null) return "Objeto '" + only + "' nao tem CanvasComponent.";
                    sb.append("CanvasComponent de '").append(only).append("' (persistente na cena)")
                      .append(cc.isCanvasVisible() ? "" : " [oculto]").append(":\n");
                    appendUiNode(sb, cc.getCanvas(), 1);
                    return sb.toString();
                }

                com.ignis.core.ui.UICanvas global = reg.liveGame.getUICanvas();
                boolean any = false;
                if (global != null && !global.getChildren().isEmpty()) {
                    sb.append("Canvas global de runtime (volatil — limpo no Stop):\n");
                    appendUiNode(sb, global, 1);
                    any = true;
                }
                List<GameObject> objs = reg.liveGame.getEntities();
                if (objs != null) {
                    for (GameObject go : objs) {
                        com.ignis.core.CanvasComponent cc =
                                go.getComponent(com.ignis.core.CanvasComponent.class);
                        if (cc == null) continue;
                        sb.append("CanvasComponent de '").append(go.getName())
                          .append("' (persistente na cena, ordem ").append(cc.getSortingOrder()).append(')')
                          .append(cc.isCanvasVisible() ? "" : " [oculto]").append(":\n");
                        appendUiNode(sb, cc.getCanvas(), 1);
                        any = true;
                    }
                }
                return any ? sb.toString() : "(nenhuma UI in-game: canvas global vazio e nenhum CanvasComponent)";
            });
    }

    // Imprime um no da arvore de UI e desce recursivamente nos filhos.
    private void appendUiNode(StringBuilder sb, com.ignis.core.ui.UIComponent c, int depth) {
        sb.append("  ".repeat(depth)).append("- ").append(c.getName())
          .append(" [").append(c.getType()).append(']');
        java.awt.Rectangle b = c.getAbsoluteBounds();
        sb.append(" @(").append(b.x).append(',').append(b.y).append(") ")
          .append(b.width).append('x').append(b.height);
        if (c.getAnchorX() != 0 || c.getAnchorY() != 0) {
            sb.append(" ancora=(").append(c.getAnchorX()).append(',').append(c.getAnchorY()).append(')');
        }
        if (c.getPivotX() != 0 || c.getPivotY() != 0) {
            sb.append(" pivo=(").append(c.getPivotX()).append(',').append(c.getPivotY()).append(')');
        }
        if (c.getZOrder() != 0) sb.append(" z=").append(c.getZOrder());
        String text = widgetText(c);
        if (text != null && !text.isEmpty()) {
            sb.append(" texto=\"").append(text.length() > 60 ? text.substring(0, 60) + "..." : text).append('"');
        }
        if (!c.isVisible()) sb.append(" [oculto]");
        if (!c.isEnabled()) sb.append(" [desabilitado]");
        if (c.isInteractive()) sb.append(" [interativo]");
        if (c.isFocused()) sb.append(" [focado]");
        sb.append('\n');
        for (com.ignis.core.ui.UIComponent child : c.getChildren()) {
            appendUiNode(sb, child, depth + 1);
        }
    }

    // Widgets de texto variam (UILabel, UIButton, UITextField...); getText por
    // reflexao cobre todos sem acoplar a cada classe concreta.
    private static String widgetText(com.ignis.core.ui.UIComponent c) {
        try {
            Object v = c.getClass().getMethod("getText").invoke(c);
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
