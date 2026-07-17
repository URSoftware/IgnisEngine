package com.ignis.mcp;

import java.util.List;

/**
 * Ponte minima entre o MCP e o gerenciamento de Cenas do editor.
 *
 * <p>{@link IgnisToolRegistry} vive no pacote {@code com.ignis.mcp} e nao pode
 * enxergar {@code EditorSceneOrganizer} (package-private a {@code com.ignis.editor.fx}).
 * O editor injeta uma implementacao desta interface via {@link McpService#setEditorContext}
 * para expor criar/listar/trocar de cena e copiar um objeto entre cenas, reaproveitando
 * a MESMA logica ja testada do menu "Cenarios..." — nenhuma regra nova de cena vive aqui.</p>
 */
public interface SceneHost {

    /** Nomes das cenas do projeto, com marcadores [inicial]/[ativa]. Vazio se nao houver projeto. */
    List<String> listScenes();

    /** Cria uma cena vazia com o nome dado e a torna ativa. Retorna null em sucesso, ou o motivo do erro. */
    String createScene(String sceneName);

    /** Troca a cena ativa do editor pelo nome. Retorna null em sucesso, ou o motivo do erro. */
    String switchScene(String sceneName);

    /**
     * Copia um objeto da cena ATIVA para outra cena do projeto (o original permanece
     * intacto). {@code newName} e opcional (mantem o nome original se vazio/null).
     * Retorna null em sucesso, ou o motivo do erro.
     */
    String copyObjectToScene(String objectName, String targetSceneName, String newName);
}
