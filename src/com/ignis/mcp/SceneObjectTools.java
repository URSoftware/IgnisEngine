package com.ignis.mcp;

import com.ignis.core.IgnisLogger;
import com.ignis.animation.AnimationFrame;
import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.Camera;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisSampleCollisions;
import com.ignis.core.IgnisScript;
import com.ignis.core.IgnisSoundEngine;
import com.ignis.core.PrefabManager;
import com.ignis.core.ScriptManager;
import com.ignis.core.World;
import com.ignis.collab.CollabBridge;
import com.ignis.collab.CollabSession;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIComponent;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.ignis.core.ui.UIProgressBar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Ferramentas MCP de objetos de cena: CRUD/transform/sprite/scripts, hierarquia pai-filho, prefabs e utilitarios extras (z-order, clone, etc).
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class SceneObjectTools {

    private final IgnisToolRegistry reg;

    SceneObjectTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerSceneTools();
        registerHierarchyTools();
        registerPrefabTools();
        registerGameObjectExtraTools();
    }

    private void registerSceneTools() {
        // list_scene_objects
        reg.add("list_scene_objects",
            "Lista os GameObjects da cena ativa em ordem de renderizacao (Z-index: menor atras, maior na frente).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                StringBuilder sb = new StringBuilder();
                sb.append("Ordem de renderizacao (Z-order): indices menores sao desenhados primeiro (ficam atras), indices maiores sao desenhados por cima.\n\n");
                java.util.List<GameObject> list = reg.liveGame.getEntities();
                for (int i = 0; i < list.size(); i++) {
                    GameObject go = list.get(i);
                    sb.append("[").append(i).append("] ")
                      .append(go.getName())
                      .append(" @ (").append((int) go.getX()).append(',').append((int) go.getY()).append(')')
                      .append(" ").append(go.getWidth()).append('x').append(go.getHeight())
                      .append(" scripts=").append(go.getScriptNames())
                      .append('\n');
                }
                return sb.length() == 0 ? "(cena vazia)" : sb.toString();
            });

        // create_object
        Map<String, String> createProps = new LinkedHashMap<>();
        createProps.put("name", "Nome do objeto");
        createProps.put("type", "Tipo: square, circle, triangle, star, pentagon, player (padrao: square)");
        createProps.put("x", "Posicao X");
        createProps.put("y", "Posicao Y");
        createProps.put("width", "Largura em px");
        createProps.put("height", "Altura em px");
        reg.add("create_object",
            "Cria um GameObject (forma ou player) e o adiciona a cena ativa.",
            IgnisToolRegistry.schemaWith(createProps, List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                double x = args.optDouble("x", 0);
                double y = args.optDouble("y", 0);
                int w = args.optInt("width", 64);
                int h = args.optInt("height", 64);
                GameObject go = reg.newShape(args.optString("type", "square"), name, x, y, w, h);
                reg.liveGame.addEntity(go);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Objeto criado: " + name + " (" + go.getClass().getSimpleName() + ") @ ("
                        + (int) x + "," + (int) y + ") " + w + "x" + h;
            });

        // set_object_transform
        Map<String, String> transformProps = new LinkedHashMap<>();
        transformProps.put("name", "Nome do objeto alvo");
        transformProps.put("x", "Nova posicao X (opcional)");
        transformProps.put("y", "Nova posicao Y (opcional)");
        transformProps.put("width", "Nova largura (opcional)");
        transformProps.put("height", "Nova altura (opcional)");
        transformProps.put("rotation", "Nova rotacao em graus (opcional)");
        reg.add("set_object_transform",
            "Altera posicao/tamanho/rotacao de um GameObject existente.",
            IgnisToolRegistry.schemaWith(transformProps, List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                if (args.has("x")) go.setX(args.optDouble("x"));
                if (args.has("y")) go.setY(args.optDouble("y"));
                if (args.has("width")) go.setWidth(args.optInt("width"));
                if (args.has("height")) go.setHeight(args.optInt("height"));
                if (args.has("rotation")) go.setRotation(args.optDouble("rotation"));
                // Hierarquia (Fase C): o objeto fica onde foi posto (recaptura o offset
                // se tiver pai) e os filhos acompanham — como no editor.
                if (reg.liveGame != null) reg.liveGame.syncHierarchyAfterEditorMove(go);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Transform atualizado: " + go.getName();
            });

        // set_object_sprite
        Map<String, String> spriteProps = new LinkedHashMap<>();
        spriteProps.put("name", "Nome do objeto");
        spriteProps.put("path", "Caminho do sprite (relativo ao projeto)");
        reg.add("set_object_sprite",
            "Define o sprite (imagem) de um GameObject.",
            IgnisToolRegistry.schemaWith(spriteProps, List.of("name", "path")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                String path = args.optString("path", "");
                if (reg.resolveInProject(path) == null) return "Erro: path invalido (caminho fora do projeto): " + path;
                go.setSpritePath(path);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Sprite definido para " + go.getName() + ": " + path;
            });

        // set_sprite_region (spritesheet/atlas)
        Map<String, String> regionProps = new LinkedHashMap<>();
        regionProps.put("name", "Nome do objeto");
        regionProps.put("path", "Caminho da spritesheet (relativo ao projeto)");
        regionProps.put("x", "X do recorte em pixels (modo retangulo)");
        regionProps.put("y", "Y do recorte em pixels (modo retangulo)");
        regionProps.put("w", "Largura do recorte em pixels (modo retangulo)");
        regionProps.put("h", "Altura do recorte em pixels (modo retangulo)");
        regionProps.put("col", "Coluna da celula (modo grade; requer 'row', 'tileW', 'tileH')");
        regionProps.put("row", "Linha da celula (modo grade)");
        regionProps.put("tileW", "Largura do tile (modo grade)");
        regionProps.put("tileH", "Altura do tile (modo grade)");
        reg.add("set_sprite_region",
            "Aponta o sprite de um objeto para uma REGIAO de uma spritesheet/atlas, por "
            + "retangulo (x,y,w,h) ou por celula de grade (col,row,tileW,tileH).",
            IgnisToolRegistry.schemaWith(regionProps, List.of("name", "path")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                String path = args.optString("path", "");
                if (reg.resolveInProject(path) == null) return "Erro: path invalido (fora do projeto): " + path;
                String composed;
                if (args.has("col") && args.has("row") && args.has("tileW") && args.has("tileH")) {
                    composed = path + "@" + args.optInt("col") + "," + args.optInt("row")
                            + "," + args.optInt("tileW") + "," + args.optInt("tileH");
                } else if (args.has("x") && args.has("y") && args.has("w") && args.has("h")) {
                    composed = path + "#" + args.optInt("x") + "," + args.optInt("y")
                            + "," + args.optInt("w") + "," + args.optInt("h");
                } else {
                    return "Erro: informe (x,y,w,h) para retangulo OU (col,row,tileW,tileH) para grade.";
                }
                go.setSpritePath(composed);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Regiao de sprite definida para " + go.getName() + ": " + composed;
            });

        // attach_script
        Map<String, String> attachProps = new LinkedHashMap<>();
        attachProps.put("objectName", "Nome do objeto alvo");
        attachProps.put("scriptName", "Nome do script a anexar");
        reg.add("attach_script",
            "Anexa um IgnisScript a um GameObject da cena.",
            IgnisToolRegistry.schemaWith(attachProps, List.of("objectName", "scriptName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                String scriptName = args.optString("scriptName", "").trim();
                if (scriptName.isEmpty()) return "Erro: 'scriptName' obrigatorio.";
                ScriptManager sm = reg.liveGame.getScriptManager();
                if (sm == null) { sm = reg.scriptManager(); reg.liveGame.setScriptManager(sm); }
                if (!go.getScriptNames().contains(scriptName)) {
                    try {
                        if (sm.hasCompiledScript(scriptName)) {
                            com.ignis.core.IgnisScript inst = sm.createScriptInstance(scriptName, go, reg.liveGame);
                            // addComponent mantem components/scripts/scriptNames coerentes
                            // (fora de components o anexo nao e serializado pela Scene).
                            if (inst != null) go.addComponent(inst);
                        }
                    } catch (Exception ignore) { /* compila no Play se necessario */ }
                    if (!go.getScriptNames().contains(scriptName)) {
                        go.getScriptNames().add(scriptName); // preserva o anexo sem instancia
                    }
                }
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Script '" + scriptName + "' anexado a " + go.getName();
            });

        // delete_object
        reg.add("delete_object",
            "Remove um GameObject da cena ativa pelo nome.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto a remover"), List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                GameObject go = reg.findObject(name);
                if (go == null) return "Erro: objeto nao encontrado: " + name;
                reg.liveGame.removeEntity(go);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Objeto removido: " + name;
            });

        // play_game
        reg.add("play_game",
            "Inicia a simulacao (Play) no editor, como apertar o botao Play.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.playHook == null) return "Erro: Play indisponivel.";
                reg.playHook.run();
                return "Play iniciado.";
            });

        // stop_game
        reg.add("stop_game",
            "Para a simulacao e volta ao modo de edicao.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.stopHook == null) return "Erro: Stop indisponivel.";
                reg.stopHook.run();
                return "Simulacao parada (edicao).";
            });

        // save_project
        reg.add("save_project",
            "Salva o projeto atual (sincroniza a cena para o arquivo .ignis).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.saveHook == null) return "Erro: salvar indisponivel.";
                reg.saveHook.run();
                return "Projeto salvo.";
            });
    }

    private void registerHierarchyTools() {
        // set_parent
        Map<String, String> parentProps = new LinkedHashMap<>();
        parentProps.put("child", "Nome do objeto filho");
        parentProps.put("parent", "Nome do objeto pai");
        reg.add("set_parent",
            "Torna um objeto filho de outro: ao mover/rotacionar o pai (no Play), o "
            + "filho acompanha mantendo o deslocamento atual. Rejeita ciclos e auto-parent.",
            IgnisToolRegistry.schemaWith(parentProps, List.of("child", "parent")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                GameObject child = reg.findObject(args.optString("child", ""));
                GameObject parent = reg.findObject(args.optString("parent", ""));
                if (child == null) return "Erro: filho nao encontrado: " + args.optString("child", "");
                if (parent == null) return "Erro: pai nao encontrado: " + args.optString("parent", "");
                if (child == parent) return "Erro: um objeto nao pode ser pai de si mesmo.";
                child.setParent(parent);
                if (child.getParent() != parent) {
                    return "Erro: parentear criaria um ciclo (o pai e descendente do filho).";
                }
                if (reg.refreshHook != null) reg.refreshHook.run();
                return child.getName() + " agora e filho de " + parent.getName();
            });

        // clear_parent
        reg.add("clear_parent",
            "Remove o vinculo de um objeto com o pai (ele permanece onde esta no mundo).",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto"), List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.clearParent();
                if (reg.refreshHook != null) reg.refreshHook.run();
                return go.getName() + " nao tem mais pai.";
            });

        // list_children
        reg.add("list_children",
            "Lista os filhos diretos de um objeto na hierarquia da cena.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto pai"), List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                GameObject parent = reg.findObject(args.optString("name", ""));
                if (parent == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                StringBuilder sb = new StringBuilder();
                for (GameObject go : reg.liveGame.getEntities()) {
                    if (go.getParent() == parent) sb.append(go.getName()).append('\n');
                }
                return sb.length() == 0 ? "(sem filhos)" : sb.toString();
            });
    }

    private void registerPrefabTools() {
        // list_prefabs
        reg.add("list_prefabs",
            "Lista os prefabs disponiveis no projeto (pasta prefabs/).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                PrefabManager pm = reg.liveGame.getPrefabManager();
                if (pm == null) return "(gerenciador de prefabs indisponivel)";
                List<String> names = pm.listPrefabs();
                return names.isEmpty() ? "(nenhum prefab)" : String.join("\n", names);
            });

        // save_prefab
        Map<String, String> savePrefabProps = new LinkedHashMap<>();
        savePrefabProps.put("objectName", "Nome do objeto da cena a salvar como prefab");
        savePrefabProps.put("prefabName", "Nome para o novo prefab");
        reg.add("save_prefab",
            "Salva um GameObject da cena como um prefab reutilizavel (prefabs/<nome>.prefab.json).",
            IgnisToolRegistry.schemaWith(savePrefabProps, List.of("objectName", "prefabName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                PrefabManager pm = reg.liveGame.getPrefabManager();
                if (pm == null) return "Erro: gerenciador de prefabs indisponivel.";
                boolean ok = pm.savePrefab(go, args.optString("prefabName", ""));
                return ok ? "Prefab salvo: " + args.optString("prefabName", "") : "Erro ao salvar prefab.";
            });

        // instantiate_prefab
        Map<String, String> instPrefabProps = new LinkedHashMap<>();
        instPrefabProps.put("prefabName", "Nome do prefab a instanciar");
        instPrefabProps.put("x", "Posicao X (opcional; usa a posicao salva no prefab se omitido)");
        instPrefabProps.put("y", "Posicao Y (opcional; usa a posicao salva no prefab se omitido)");
        reg.add("instantiate_prefab",
            "Instancia um prefab na cena ativa, opcionalmente numa posicao especifica.",
            IgnisToolRegistry.schemaWith(instPrefabProps, List.of("prefabName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("prefabName", "").trim();
                if (name.isEmpty()) return "Erro: 'prefabName' obrigatorio.";
                if (args.has("x") != args.has("y")) {
                    return "Erro: informe 'x' e 'y' juntos, ou nenhum dos dois (para usar a posicao salva no prefab).";
                }
                GameObject go;
                if (args.has("x")) {
                    go = reg.liveGame.instantiatePrefab(name, args.optDouble("x"), args.optDouble("y"));
                } else {
                    PrefabManager pm = reg.liveGame.getPrefabManager();
                    go = pm != null ? pm.instantiatePrefab(name) : null;
                    if (go != null) reg.liveGame.addEntity(go);
                }
                if (go == null) return "Erro: nao foi possivel instanciar o prefab: " + name;
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Prefab instanciado: " + go.getName() + " @ (" + (int) go.getX() + "," + (int) go.getY() + ")";
            });

        // delete_prefab
        reg.add("delete_prefab",
            "Remove um arquivo de prefab do disco.",
            IgnisToolRegistry.schemaWith(Map.of("prefabName", "Nome do prefab a remover"), List.of("prefabName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                PrefabManager pm = reg.liveGame.getPrefabManager();
                if (pm == null) return "Erro: gerenciador de prefabs indisponivel.";
                boolean ok = pm.deletePrefab(args.optString("prefabName", ""));
                return ok ? "Prefab removido: " + args.optString("prefabName", "") : "Erro: prefab nao encontrado.";
            });

        // prefab_exists
        reg.add("prefab_exists",
            "Verifica se um prefab existe no disco.",
            IgnisToolRegistry.schemaWith(Map.of("prefabName", "Nome do prefab a verificar"), List.of("prefabName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                PrefabManager pm = reg.liveGame.getPrefabManager();
                if (pm == null) return "Erro: gerenciador de prefabs indisponivel.";
                boolean exists = pm.prefabExists(args.optString("prefabName", ""));
                return exists ? "Existe." : "Nao existe.";
            });
    }

    private void registerGameObjectExtraTools() {
        // set_object_visible
        reg.add("set_object_visible",
            "Mostra ou esconde um GameObject (afeta apenas a renderizacao).",
            IgnisToolRegistry.schemaWith(new LinkedHashMap<>(Map.of("name", "Nome do objeto", "visible", "true para mostrar, false para esconder")),
                    List.of("name", "visible")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.setVisible(args.optBoolean("visible", true));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return (args.optBoolean("visible", true) ? "Visivel: " : "Escondido: ") + go.getName();
            });

        // set_object_name_color
        reg.add("set_object_name_color",
            "Define a cor de exibicao do nome do objeto na hierarquia do editor.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto", "color", "Cor em hex, ex: #FF8800"), List.of("name", "color")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                go.setNameColor(IgnisToolRegistry.safeColor(args.optString("color", ""), Color.WHITE));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Cor do nome atualizada: " + go.getName();
            });

        // reorder_object_z
        reg.add("reorder_object_z",
            "Altera o zIndex (profundidade de render) de um objeto: 'top', 'bottom', 'up', 'down' ou um valor numerico. Maior zIndex = na frente; empate mantem a ordem da hierarquia.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto", "position", "'top', 'bottom', 'up', 'down' ou zIndex numerico"),
                    List.of("name", "position")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                String pos = args.optString("position", "").trim().toLowerCase();
                int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (GameObject e : reg.liveGame.getEntities()) {
                    minZ = Math.min(minZ, e.getZIndex());
                    maxZ = Math.max(maxZ, e.getZIndex());
                }
                switch (pos) {
                    case "top": go.setZIndex((maxZ == Integer.MIN_VALUE ? 0 : maxZ) + 1); break;
                    case "bottom": go.setZIndex((minZ == Integer.MAX_VALUE ? 0 : minZ) - 1); break;
                    case "up": go.setZIndex(go.getZIndex() + 1); break;
                    case "down": go.setZIndex(go.getZIndex() - 1); break;
                    default:
                        try {
                            go.setZIndex(Integer.parseInt(pos));
                        } catch (NumberFormatException nfe) {
                            return "Erro: 'position' deve ser top/bottom/up/down ou um zIndex numerico.";
                        }
                }
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "zIndex de " + go.getName() + " -> " + go.getZIndex();
            });

        // set_object_visual (Fase B: opacity, flip, escala visual)
        Map<String, String> visualProps = new LinkedHashMap<>();
        visualProps.put("name", "Nome do objeto");
        visualProps.put("opacity", "Opacidade 0.0-1.0 (opcional)");
        visualProps.put("flipX", "Espelhar horizontalmente (true/false, opcional)");
        visualProps.put("flipY", "Espelhar verticalmente (true/false, opcional)");
        visualProps.put("scaleX", "Multiplicador visual de largura (opcional)");
        visualProps.put("scaleY", "Multiplicador visual de altura (opcional)");
        reg.add("set_object_visual",
            "Ajusta as propriedades visuais de um objeto: opacidade, espelhamento (flip) e escala visual.",
            IgnisToolRegistry.schemaWith(visualProps, List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                if (args.has("opacity")) go.setOpacity(args.optDouble("opacity"));
                if (args.has("flipX")) go.setFlipX(args.optBoolean("flipX"));
                if (args.has("flipY")) go.setFlipY(args.optBoolean("flipY"));
                if (args.has("scaleX")) go.setScaleX(args.optDouble("scaleX"));
                if (args.has("scaleY")) go.setScaleY(args.optDouble("scaleY"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Visual atualizado: " + go.getName() + " (opacity=" + go.getOpacity()
                        + " flipX=" + go.isFlipX() + " flipY=" + go.isFlipY()
                        + " scaleX=" + go.getScaleX() + " scaleY=" + go.getScaleY() + ")";
            });

        // get_object_info
        reg.add("get_object_info",
            "Retorna informacoes completas de um GameObject: transform, tipo, visibilidade, sprite, scripts e collider.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto"), List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("name", "");
                StringBuilder sb = new StringBuilder();
                sb.append("nome: ").append(go.getName()).append('\n');
                sb.append("tipo: ").append(go.getType()).append('\n');
                sb.append("posicao: (").append(go.getX()).append(", ").append(go.getY()).append(")\n");
                sb.append("tamanho: ").append(go.getWidth()).append('x').append(go.getHeight()).append('\n');
                sb.append("rotacao: ").append(go.getRotation()).append('\n');
                sb.append("visivel: ").append(go.isVisible()).append('\n');
                sb.append("sprite: ").append(go.getSpritePath()).append('\n');
                sb.append("scripts: ").append(go.getScriptNames()).append('\n');
                sb.append("collider: ").append(go.getColliderType())
                  .append(go.hasCollider() ? " (" + go.getCollisionMode() + ")" : "");
                return sb.toString();
            });

        // find_objects_by_type
        reg.add("find_objects_by_type",
            "Busca objetos da cena por tipo (ex: 'Square', 'Circle', 'Player').",
            IgnisToolRegistry.schemaWith(Map.of("type", "Nome do tipo/classe a buscar"), List.of("type")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String type = args.optString("type", "").trim();
                StringBuilder sb = new StringBuilder();
                for (GameObject go : reg.liveGame.getEntities()) {
                    if (go.getType().equalsIgnoreCase(type)) {
                        sb.append(go.getName()).append(" @ (").append((int) go.getX()).append(',').append((int) go.getY()).append(")\n");
                    }
                }
                return sb.length() == 0 ? "(nenhum objeto do tipo " + type + ")" : sb.toString();
            });

        // remove_script_from_object
        reg.add("remove_script_from_object",
            "Remove um script anexado de um GameObject pelo nome.",
            IgnisToolRegistry.schemaWith(new LinkedHashMap<>(Map.of("objectName", "Nome do objeto", "scriptName", "Nome do script a remover")),
                    List.of("objectName", "scriptName")),
            args -> {
                GameObject go = reg.findObject(args.optString("objectName", ""));
                if (go == null) return "Erro: objeto nao encontrado: " + args.optString("objectName", "");
                String scriptName = args.optString("scriptName", "");
                if (!go.getScriptNames().contains(scriptName)) return "Erro: script nao anexado: " + scriptName;
                go.removeScriptByName(scriptName);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Script removido: " + scriptName + " de " + go.getName();
            });

        // clear_scene
        reg.add("clear_scene",
            "Remove todos os GameObjects da cena ativa.",
            IgnisToolRegistry.schemaWith(Map.of("preserveCameras", "true para manter as cameras (padrao true; cameras ja ficam fora da lista de objetos)"), List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                reg.liveGame.clearEntities();
                if (!args.optBoolean("preserveCameras", true)) {
                    for (Camera c : new ArrayList<>(reg.liveGame.getCameras())) reg.liveGame.removeCamera(c);
                }
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Cena limpa.";
            });
    }
}
