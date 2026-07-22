package com.ignis.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Linter de cena reutilizável: detecta problemas ANTES de salvar/publicar (nomes
 * duplicados, sprites/scripts ausentes, referência de pai quebrada, objeto fora dos
 * limites do mundo).
 *
 * <p>Extraído do handler {@code validate_scene} do MCP para que o MESMO diagnóstico
 * fique disponível ao usuário humano do editor (menu "Validar Cena…"), sem depender de
 * o bridge MCP estar rodando. Ferramenta e GUI compartilham a mesma regra — princípio
 * do roadmap ("editor e agentes usam o mesmo comando de domínio, com validação
 * equivalente").</p>
 */
public final class SceneValidator {

    private SceneValidator() { }

    /**
     * @param entities        objetos da cena viva ({@code game.getEntities()}).
     * @param world           mundo da cena (ou null): usado para checar objetos fora dos limites.
     * @param projectFolder   raiz do projeto: valida caminhos de sprite (anti path-traversal).
     * @param availableScripts scripts existentes no projeto (ou null para pular a checagem).
     * @return lista de problemas (vazia = cena OK).
     */
    public static List<String> validate(List<GameObject> entities, World world, File projectFolder,
            List<String> availableScripts) {
        List<String> issues = new ArrayList<>();
        if (entities == null || entities.isEmpty()) return issues;

        // Nomes duplicados (o findObject por nome fica ambiguo).
        Map<String, Integer> nameCount = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (GameObject go : entities) {
            nameCount.merge(go.getName(), 1, Integer::sum);
            ids.add(go.getId());
        }
        for (Map.Entry<String, Integer> e : nameCount.entrySet()) {
            if (e.getValue() > 1) {
                issues.add("Nome duplicado: '" + e.getKey() + "' (" + e.getValue() + " objetos) — "
                        + "ferramentas que miram por nome ficam ambiguas.");
            }
        }

        boolean bounded = world != null && world.hasBounds();

        for (GameObject go : entities) {
            // Sprite de asset ausente ou com caminho invalido.
            String sprite = go.getSpritePath();
            if (sprite != null && !sprite.isBlank()) {
                File f = resolveWithin(projectFolder, sprite);
                if (f == null) {
                    issues.add("Caminho de sprite invalido: '" + go.getName() + "' aponta para "
                            + sprite + " (fora do projeto ou path-traversal).");
                } else if (!f.isFile()) {
                    issues.add("Sprite ausente: '" + go.getName() + "' aponta para " + sprite
                            + " (arquivo nao existe).");
                }
            }
            // Scripts nao encontrados no projeto.
            List<String> scripts = go.getScriptNames();
            if (scripts != null && availableScripts != null) {
                for (String s : scripts) {
                    if (!availableScripts.contains(s)) {
                        issues.add("Script ausente: '" + go.getName() + "' usa '" + s
                                + "' que nao esta no projeto (crie/compile antes do Play).");
                    }
                }
            }
            // Referencia de pai quebrada.
            String parentId = go.getParentId();
            if (parentId != null && !parentId.isBlank() && !ids.contains(parentId)) {
                issues.add("Pai quebrado: '" + go.getName() + "' referencia parentId=" + parentId
                        + " que nao existe na cena.");
            }
            // Objeto fora dos limites do mundo.
            if (bounded) {
                double cx = go.getX() + go.getWidth() / 2.0;
                double cy = go.getY() + go.getHeight() / 2.0;
                if (cx < world.getMinX() || cx > world.getMaxX()
                        || cy < world.getMinY() || cy > world.getMaxY()) {
                    issues.add("Fora do mundo: '" + go.getName() + "' @(" + (int) go.getX() + ","
                            + (int) go.getY() + ") esta alem dos limites do World.");
                }
            }
        }
        return issues;
    }

    // Resolve 'relative' garantindo que fique DENTRO de 'base' (anti path-traversal),
    // mesma regra do IgnisToolRegistry.resolveWithin — replicada aqui para o core nao
    // depender do pacote mcp.
    private static File resolveWithin(File base, String relative) {
        if (base == null || relative == null || relative.trim().isEmpty()) return null;
        try {
            File baseCanon = base.getCanonicalFile();
            File target = new File(baseCanon, relative).getCanonicalFile();
            String basePath = baseCanon.getPath();
            String targetPath = target.getPath();
            if (targetPath.equals(basePath) || targetPath.startsWith(basePath + File.separator)) return target;
        } catch (Exception ignore) { /* fallthrough */ }
        return null;
    }
}
