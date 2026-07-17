package com.ignis.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ferramentas MCP de Cenas/Cenarios: listar, criar, trocar a cena ativa do editor e
 * copiar um objeto de uma cena para outra.
 *
 * <p>Toda a logica real vive em {@code EditorSceneOrganizer} (a mesma classe por
 * tras do menu "Cenarios..." da toolbar); esta classe so traduz argumentos MCP para
 * chamadas em {@link IgnisToolRegistry#sceneHost}, que e nulo se o editor ainda nao
 * injetou o contexto vivo (projeto nao aberto).</p>
 */
final class SceneTools {

    private final IgnisToolRegistry reg;

    SceneTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        reg.add("list_scenes",
            "Lista as cenas do projeto (a cena inicial marcada com [inicial], a cena ativa com [ativa]). "
                + "Cada 'mundo' do jogo deve ser sua propria cena.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.sceneHost == null) return "Erro: nenhum projeto de editor vivo (SceneHost nao disponivel).";
                List<String> names = reg.sceneHost.listScenes();
                return names.isEmpty() ? "(nenhuma cena)" : String.join("\n", names);
            });

        Map<String, String> createProps = new LinkedHashMap<>();
        createProps.put("sceneName", "Nome da nova cena (ex: CaveGallery)");
        reg.add("create_scene",
            "Cria uma cena vazia com o nome dado e a torna ativa no editor.",
            IgnisToolRegistry.schemaWith(createProps, List.of("sceneName")),
            args -> {
                if (reg.sceneHost == null) return "Erro: nenhum projeto de editor vivo (SceneHost nao disponivel).";
                String name = args.optString("sceneName", "").trim();
                if (name.isEmpty()) return "Erro: 'sceneName' obrigatorio.";
                String error = reg.sceneHost.createScene(name);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return error != null ? "Erro: " + error : "Cena criada e ativa: " + name;
            });

        Map<String, String> switchProps = new LinkedHashMap<>();
        switchProps.put("sceneName", "Nome da cena a ativar (ver list_scenes)");
        reg.add("switch_scene",
            "Troca a cena ativa do editor pelo nome (fora do Play; pare o Play antes de trocar).",
            IgnisToolRegistry.schemaWith(switchProps, List.of("sceneName")),
            args -> {
                if (reg.sceneHost == null) return "Erro: nenhum projeto de editor vivo (SceneHost nao disponivel).";
                String name = args.optString("sceneName", "").trim();
                if (name.isEmpty()) return "Erro: 'sceneName' obrigatorio.";
                String error = reg.sceneHost.switchScene(name);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return error != null ? "Erro: " + error : "Cena ativa: " + name;
            });

        Map<String, String> copyProps = new LinkedHashMap<>();
        copyProps.put("objectName", "Nome do objeto na cena ATIVA a copiar");
        copyProps.put("targetSceneName", "Nome da cena de destino (ver list_scenes)");
        copyProps.put("newName", "Novo nome do objeto copiado (opcional; mantem o nome original se omitido)");
        reg.add("copy_object_to_scene",
            "Copia um objeto da cena ativa para outra cena do projeto (o original permanece intacto). "
                + "Use para reaproveitar um objeto ja existente numa cena em outro 'mundo'.",
            IgnisToolRegistry.schemaWith(copyProps, List.of("objectName", "targetSceneName")),
            args -> {
                if (reg.sceneHost == null) return "Erro: nenhum projeto de editor vivo (SceneHost nao disponivel).";
                String objectName = args.optString("objectName", "").trim();
                String targetSceneName = args.optString("targetSceneName", "").trim();
                if (objectName.isEmpty()) return "Erro: 'objectName' obrigatorio.";
                if (targetSceneName.isEmpty()) return "Erro: 'targetSceneName' obrigatorio.";
                String newName = args.optString("newName", "").trim();
                String error = reg.sceneHost.copyObjectToScene(objectName, targetSceneName,
                        newName.isEmpty() ? null : newName);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return error != null ? "Erro: " + error
                        : "Objeto '" + objectName + "' copiado para a cena: " + targetSceneName;
            });
    }
}
