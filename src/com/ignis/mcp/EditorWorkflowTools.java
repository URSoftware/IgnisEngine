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
 * Ferramentas MCP de fluxo de trabalho do editor: notas de asset, coordenacao multi-agente e capturas de tela (viewport/janela).
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class EditorWorkflowTools {

    private final IgnisToolRegistry reg;

    EditorWorkflowTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerHeadlessTools() {
        registerAssetNoteTools();
        registerCoordinationTools();
    }

    private void registerAssetNoteTools() {
        // list_assets
        reg.add("list_assets",
            "Lista os arquivos de assets do projeto (sprites, sounds, music, fonts, tilemaps, ui, animations).",
            IgnisToolRegistry.schemaWith(Map.of("category", "Subpasta de assets/ a listar (opcional; padrao lista todas)"), List.of()),
            args -> {
                String cat = args.optString("category", "").trim();
                File assetsDir = new File(reg.projectFolder, "assets");
                if (!assetsDir.isDirectory()) return "(pasta assets/ nao encontrada)";
                StringBuilder sb = new StringBuilder();
                File[] dirs;
                if (cat.isEmpty()) {
                    dirs = assetsDir.listFiles(f -> f.isDirectory() && !IgnisToolRegistry.isSymlink(f));
                } else {
                    File target = reg.resolveInProject("assets/" + cat);
                    if (target == null) return "Erro: categoria invalida (caminho fora do projeto): " + cat;
                    dirs = new File[] { target };
                }
                if (dirs == null) return "(nenhum asset encontrado)";
                java.util.Arrays.sort(dirs, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File d : dirs) {
                    if (!d.isDirectory()) continue;
                    File[] files = d.listFiles(f -> f.isFile() && !IgnisToolRegistry.isSymlink(f));
                    if (files == null || files.length == 0) continue;
                    sb.append("=== assets/").append(d.getName()).append(" ===\n");
                    java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                    for (File f : files) sb.append("  assets/").append(d.getName()).append('/').append(f.getName())
                            .append(" (").append(f.length()).append(" bytes)\n");
                }
                return sb.length() == 0 ? "(nenhum asset encontrado)" : sb.toString();
            });

        // import_asset_from_path
        Map<String, String> importProps = new LinkedHashMap<>();
        importProps.put("sourcePath", "Caminho absoluto do arquivo de origem, fora do projeto");
        importProps.put("category", "Subpasta de destino em assets/ (ex: sprites, sounds, music)");
        importProps.put("overwrite", "true para sobrescrever se ja existir (padrao: false)");
        reg.add("import_asset_from_path",
            "Copia um arquivo externo para dentro de assets/<category>/ do projeto ativo.",
            IgnisToolRegistry.schemaWith(importProps, List.of("sourcePath", "category")),
            args -> {
                String srcPath = args.optString("sourcePath", "").trim();
                String category = args.optString("category", "").trim();
                if (srcPath.isEmpty() || category.isEmpty()) return "Erro: 'sourcePath' e 'category' sao obrigatorios.";
                File src = new File(srcPath);
                if (!src.isFile()) return "Erro: arquivo de origem nao encontrado: " + srcPath;
                // 'category' vira parte do destino (dentro do projeto) e precisa ficar contida em assets/;
                // 'sourcePath' fica livre de proposito (o objetivo da ferramenta e importar de FORA do projeto).
                File destDir = reg.resolveInProject("assets/" + category);
                if (destDir == null) return "Erro: categoria invalida (caminho de destino fora do projeto): " + category;
                destDir.mkdirs();
                File dest = new File(destDir, src.getName());
                if (dest.exists() && !args.optBoolean("overwrite", false)) {
                    return "Erro: ja existe um asset com esse nome (use overwrite=true): assets/" + category + "/" + src.getName();
                }
                try {
                    Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return "Asset importado: assets/" + category + "/" + src.getName();
                } catch (Exception e) {
                    return "Erro ao importar asset: " + e.getMessage();
                }
            });

        // list_notes
        reg.add("list_notes",
            "Lista as paginas de notas/wiki do projeto (titulo e nome de arquivo).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                File[] files = reg.notesFolder().listFiles((d, n) -> n.toLowerCase().endsWith(".json") && !IgnisToolRegistry.isSymlink(new File(d, n)));
                if (files == null || files.length == 0) return "(nenhuma nota)";
                StringBuilder sb = new StringBuilder();
                for (File f : files) {
                    try {
                        JSONObject json = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                        sb.append(f.getName()).append(" - ").append(json.optString("title", "(sem titulo)")).append('\n');
                    } catch (Exception ignore) { /* pula arquivo corrompido */ }
                }
                return sb.length() == 0 ? "(nenhuma nota)" : sb.toString();
            });

        // create_note
        reg.add("create_note",
            "Cria uma nova pagina de nota/wiki no projeto.",
            IgnisToolRegistry.schemaWith(Map.of("title", "Titulo da nota"), List.of("title")),
            args -> {
                String title = args.optString("title", "").trim();
                if (title.isEmpty()) return "Erro: 'title' obrigatorio.";
                // Sanitiza o nome derivado do titulo (remove separadores de caminho e afins,
                // evitando que um titulo tipo "../../evil" escreva fora de notes/).
                String safeTitle = title.toLowerCase().replaceAll("[^a-z0-9-_ ]", "").trim();
                if (safeTitle.isEmpty()) return "Erro: titulo invalido (sem caracteres alfanumericos).";
                String fileName = safeTitle.replace(' ', '_') + ".json";
                File file = new File(reg.notesFolder(), fileName);
                if (file.exists()) return "Erro: ja existe uma nota com esse nome: " + fileName;
                JSONObject json = new JSONObject()
                        .put("title", title)
                        .put("content", "<h1>" + title + "</h1><p>Escreva aqui...</p>");
                try {
                    Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                    return "Nota criada: " + fileName;
                } catch (Exception e) {
                    return "Erro ao criar nota: " + e.getMessage();
                }
            });

        // read_note
        reg.add("read_note",
            "Le o titulo e conteudo de uma nota/wiki pelo nome de arquivo.",
            IgnisToolRegistry.schemaWith(Map.of("fileName", "Nome do arquivo (ex: minha_nota.json)"), List.of("fileName")),
            args -> {
                File file = IgnisToolRegistry.resolveWithin(reg.notesFolder(), args.optString("fileName", ""));
                if (file == null) return "Erro: nome de arquivo invalido (caminho fora de notes/): " + args.optString("fileName", "");
                if (!file.isFile()) return "Erro: nota nao encontrada: " + args.optString("fileName", "");
                JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                return "Titulo: " + json.optString("title") + "\nConteudo:\n" + json.optString("content");
            });

        // write_note
        Map<String, String> writeNoteProps = new LinkedHashMap<>();
        writeNoteProps.put("fileName", "Nome do arquivo (deve existir)");
        writeNoteProps.put("title", "Novo titulo");
        writeNoteProps.put("content", "Novo conteudo (HTML ou texto)");
        reg.add("write_note",
            "Sobrescreve o titulo e conteudo de uma nota/wiki existente.",
            IgnisToolRegistry.schemaWith(writeNoteProps, List.of("fileName", "title", "content")),
            args -> {
                File file = IgnisToolRegistry.resolveWithin(reg.notesFolder(), args.optString("fileName", ""));
                if (file == null) return "Erro: nome de arquivo invalido (caminho fora de notes/): " + args.optString("fileName", "");
                if (!file.isFile()) return "Erro: nota nao existe (crie primeiro): " + args.optString("fileName", "");
                JSONObject json = new JSONObject()
                        .put("title", args.optString("title", ""))
                        .put("content", args.optString("content", ""));
                try {
                    Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                    return "Nota salva: " + args.optString("fileName", "");
                } catch (Exception e) {
                    return "Erro ao salvar nota: " + e.getMessage();
                }
            });
    }

    private void registerCoordinationTools() {
        final McpCoordination coord = McpCoordination.get();

        // agent_join
        Map<String, String> joinProps = new LinkedHashMap<>();
        joinProps.put("agent", "Seu nome de agente (ex: Claude, Gemini)");
        joinProps.put("role", "Papel/especialidade (opcional, ex: 'scripts', 'arte')");
        reg.add("agent_join",
            "Registra sua presenca como agente e lista quem mais esta trabalhando no projeto. Chame ao comecar.",
            IgnisToolRegistry.schemaWith(joinProps, List.of("agent")),
            args -> {
                String agent = args.optString("agent", "").trim();
                if (agent.isEmpty()) return "Erro: 'agent' obrigatorio.";
                return coord.join(agent, args.optString("role", ""));
            });

        // agent_list
        reg.add("agent_list",
            "Lista os agentes registrados e se estao ativos (presenca).",
            IgnisToolRegistry.objectSchema(),
            args -> coord.listAgents());

        // send_message
        Map<String, String> msgProps = new LinkedHashMap<>();
        msgProps.put("from", "Seu nome de agente");
        msgProps.put("text", "Mensagem");
        msgProps.put("to", "Destinatario (opcional; vazio = para todos)");
        reg.add("send_message",
            "Envia uma mensagem no mural compartilhado para coordenar com os outros agentes (e o usuario).",
            IgnisToolRegistry.schemaWith(msgProps, List.of("from", "text")),
            args -> {
                String from = args.optString("from", "").trim();
                if (from.isEmpty()) return "Erro: 'from' obrigatorio.";
                return coord.sendMessage(from, args.optString("to", ""), args.optString("text", ""));
            });

        // read_messages
        Map<String, String> readProps = new LinkedHashMap<>();
        readProps.put("agent", "Seu nome de agente (recebe broadcast + mensagens a voce)");
        readProps.put("since", "Ultimo seq lido (opcional; 0 = tudo). Use o lastSeq retornado.");
        reg.add("read_messages",
            "Le as mensagens novas do mural (broadcast + direcionadas a voce). Faca polling passando o 'since'.",
            IgnisToolRegistry.schemaWith(readProps, List.of("agent")),
            args -> {
                String agent = args.optString("agent", "").trim();
                if (agent.isEmpty()) return "Erro: 'agent' obrigatorio.";
                return coord.readMessages(agent, args.optLong("since", 0));
            });

        // claim
        Map<String, String> claimProps = new LinkedHashMap<>();
        claimProps.put("agent", "Seu nome de agente");
        claimProps.put("resource", "Recurso a reservar (ex: script:PlayerController, objeto:Hero)");
        reg.add("claim",
            "Reserva um recurso (script/objeto/area) para voce trabalhar, evitando que outro agente mexa junto.",
            IgnisToolRegistry.schemaWith(claimProps, List.of("agent", "resource")),
            args -> {
                String agent = args.optString("agent", "").trim();
                String resource = args.optString("resource", "").trim();
                if (agent.isEmpty() || resource.isEmpty()) return "Erro: 'agent' e 'resource' obrigatorios.";
                return coord.claim(agent, resource);
            });

        // release
        reg.add("release",
            "Libera um recurso que voce havia reservado.",
            IgnisToolRegistry.schemaWith(claimProps, List.of("agent", "resource")),
            args -> {
                String agent = args.optString("agent", "").trim();
                String resource = args.optString("resource", "").trim();
                if (agent.isEmpty() || resource.isEmpty()) return "Erro: 'agent' e 'resource' obrigatorios.";
                return coord.release(agent, resource);
            });

        // list_claims
        reg.add("list_claims",
            "Lista os recursos reservados no momento e por quem.",
            IgnisToolRegistry.objectSchema(),
            args -> coord.listClaims());

        // create_task
        Map<String, String> taskProps = new LinkedHashMap<>();
        taskProps.put("title", "Titulo da tarefa");
        taskProps.put("description", "Detalhe (opcional)");
        reg.add("create_task",
            "Cria uma tarefa no quadro compartilhado (o usuario usa para dividir o trabalho entre os agentes).",
            IgnisToolRegistry.schemaWith(taskProps, List.of("title")),
            args -> {
                String title = args.optString("title", "").trim();
                if (title.isEmpty()) return "Erro: 'title' obrigatorio.";
                return coord.createTask(title, args.optString("description", ""));
            });

        // assign_task
        Map<String, String> assignProps = new LinkedHashMap<>();
        assignProps.put("id", "Numero da tarefa");
        assignProps.put("agent", "Agente responsavel");
        reg.add("assign_task",
            "Atribui uma tarefa a um agente (marca como em andamento).",
            IgnisToolRegistry.schemaWith(assignProps, List.of("id", "agent")),
            args -> coord.assignTask(args.optInt("id", -1), args.optString("agent", "").trim()));

        // complete_task
        reg.add("complete_task",
            "Marca uma tarefa como concluida.",
            IgnisToolRegistry.schemaWith(Map.of("id", "Numero da tarefa"), List.of("id")),
            args -> coord.completeTask(args.optInt("id", -1)));

        // list_tasks
        reg.add("list_tasks",
            "Lista as tarefas do quadro compartilhado com status e responsavel.",
            IgnisToolRegistry.objectSchema(),
            args -> coord.listTasks());
    }

    void registerCaptureTools() {
        // select_object — seleciona um objeto na cena (Hierarchy + Inspector + gizmos
        // acompanham via selectionListener). Util para agentes inspecionarem/editarem
        // e para validar a UI do Inspector por captura.
        reg.add("select_object",
            "Seleciona um objeto da cena pelo nome (atualiza Hierarchy, Inspector e gizmos). "
            + "Use name vazio para limpar a selecao.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do objeto (vazio limpa a selecao)"), List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) {
                    reg.liveGame.setSelectedObject(null);
                    return "Selecao limpa.";
                }
                GameObject go = reg.findObject(name);
                if (go == null) return "Erro: objeto nao encontrado: " + name;
                reg.liveGame.setSelectedObject(go);
                return "Selecionado: " + go.getName() + " (" + go.getType() + ")";
            });

        // capture_viewport — renderiza a cena (renderWorldTo) num PNG. Permite a um
        // agente VER o que o motor desenha: validar tilemap/parallax/particulas,
        // conferir orientacao de sprites, comparar antes/depois de uma edicao.
        Map<String, String> vpProps = new LinkedHashMap<>();
        vpProps.put("width", "Largura do frame em px (padrao: viewport atual ou 1280)");
        vpProps.put("height", "Altura do frame em px (padrao: viewport atual ou 720)");
        reg.add("capture_viewport",
            "Captura a Scene View (render do mundo pela camera de visao) num arquivo PNG "
            + "e retorna o caminho absoluto. Use para validar visualmente a cena.",
            IgnisToolRegistry.schemaWith(vpProps, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                int defW = (reg.liveGame.getViewport() != null && reg.liveGame.getViewport().getWidth() > 0)
                        ? reg.liveGame.getViewport().getWidth() : 1280;
                int defH = (reg.liveGame.getViewport() != null && reg.liveGame.getViewport().getHeight() > 0)
                        ? reg.liveGame.getViewport().getHeight() : 720;
                int w = Math.max(16, Math.min(4096, args.optInt("width", defW)));
                int h = Math.max(16, Math.min(4096, args.optInt("height", defH)));
                java.awt.image.BufferedImage img =
                        new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2d = img.createGraphics();
                try {
                    reg.liveGame.renderWorldTo(g2d, w, h, reg.liveGame.getSelectedObject());
                } finally {
                    g2d.dispose();
                }
                String path = IgnisToolRegistry.saveCapture(img, "viewport");
                return "Viewport capturado (" + w + "x" + h + "): " + path;
            });

        // capture_editor_window — snapshot da janela inteira do editor (menus, toolbar,
        // Hierarchy, Inspector). Depende do supplier injetado pelo IgnisEditorApp.
        reg.add("capture_editor_window",
            "Captura a janela INTEIRA do editor (toolbar, Hierarchy, Inspector, viewport) "
            + "num arquivo PNG e retorna o caminho absoluto. Use para validar a UI do editor.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.windowCaptureSupplier == null) {
                    return "Erro: captura de janela indisponivel (editor sem GUI ou supplier nao registrado).";
                }
                java.awt.image.BufferedImage img = reg.windowCaptureSupplier.get();
                if (img == null) return "Erro: snapshot da janela retornou vazio.";
                String path = IgnisToolRegistry.saveCapture(img, "editor");
                return "Janela do editor capturada (" + img.getWidth() + "x" + img.getHeight() + "): " + path;
            });
    }
}
