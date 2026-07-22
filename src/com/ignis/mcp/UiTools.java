package com.ignis.mcp;

import com.ignis.core.CanvasComponent;
import com.ignis.core.GameObject;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UICheckbox;
import com.ignis.core.ui.UIComponent;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.ignis.core.ui.UIProgressBar;
import com.ignis.core.ui.UISlider;
import com.ignis.core.ui.UITextField;
import org.json.JSONObject;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ferramentas MCP de UI in-game (paineis, labels, botoes, barras, imagens, campos,
 * sliders, checkboxes). Extraido do {@link IgnisToolRegistry} (Fase F, passo 10).
 *
 * <p><b>Dois mundos de UI</b> (P1 fatia 2 — autoria de UI persistente):</p>
 * <ul>
 *   <li><b>Sem {@code objectName}</b>: canvas GLOBAL de runtime ({@code Game.getUICanvas()}),
 *       VOLATIL — o Stop o limpa e ele nao vai para o {@code .ignis}. Bom para HUD montado
 *       por script/QA durante o Play.</li>
 *   <li><b>Com {@code objectName}</b>: a arvore do {@link CanvasComponent} daquele objeto,
 *       PERSISTENTE — serializa na cena e reabre pronta. Anexa o componente
 *       automaticamente se o objeto ainda nao tiver (a resposta avisa).</li>
 * </ul>
 *
 * <p>Nomes de elementos sao unicos POR CANVAS (o mesmo 'hp' pode existir no HUD do
 * Player e no do Boss). {@code get_ui_tree} desambigua mostrando a origem.</p>
 */
final class UiTools {

    private final IgnisToolRegistry reg;

    UiTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    // Alvo resolvido de uma operacao de UI: o canvas concreto + metadados para a
    // resposta (persistente vs volatil, aviso de auto-attach) OU um erro.
    private static final class Target {
        final UICanvas canvas;      // null quando error != null
        final String error;         // null quando ok
        final boolean persistent;
        final String label;         // descreve o alvo na resposta
        final String attachNote;    // "" ou aviso de CanvasComponent anexado

        private Target(UICanvas canvas, String error, boolean persistent, String label, String attachNote) {
            this.canvas = canvas;
            this.error = error;
            this.persistent = persistent;
            this.label = label;
            this.attachNote = attachNote;
        }

        static Target error(String msg) { return new Target(null, msg, false, "", ""); }
        static Target ok(UICanvas c, boolean persistent, String label, String note) {
            return new Target(c, null, persistent, label, note);
        }
    }

    /**
     * Resolve o canvas alvo. {@code allowAttach} decide o comportamento quando o objeto
     * existe mas nao tem {@link CanvasComponent}: {@code true} (ferramentas de criacao)
     * anexa um; {@code false} (set/remove/list) devolve erro orientando.
     */
    private Target resolveTarget(JSONObject args, boolean allowAttach) {
        String objectName = args.optString("objectName", "").trim();
        if (objectName.isEmpty()) {
            UICanvas c = reg.ensureUiCanvas();
            if (c == null) return Target.error("Erro: editor nao disponivel.");
            return Target.ok(c, false, "canvas global (volatil, limpo no Stop)", "");
        }
        GameObject go = reg.findObject(objectName);
        if (go == null) return Target.error("Erro: objeto nao encontrado: " + objectName);
        CanvasComponent cc = go.getComponent(CanvasComponent.class);
        String attachNote = "";
        if (cc == null) {
            if (!allowAttach) {
                return Target.error("Erro: '" + objectName + "' nao tem CanvasComponent. Crie um widget com "
                        + "objectName (anexa automatico) ou use ui_attach_canvas primeiro.");
            }
            cc = new CanvasComponent();
            go.addComponent(cc);
            attachNote = " CanvasComponent anexado a '" + objectName + "'.";
        }
        return Target.ok(cc.getCanvas(), true,
                "CanvasComponent de '" + objectName + "' (persiste apos o Stop)", attachNote);
    }

    // Sufixo padrao das respostas: aviso de attach + alvo + (quando em Play e persistente)
    // lembrete de que a edicao de UI NAO e descartada no Stop (ao contrario da cena).
    private String suffix(Target t) {
        String s = t.attachNote + " (" + t.label + ")";
        if (t.persistent && reg.liveGame != null
                && reg.liveGame.getGameState() == com.ignis.core.Game.GameState.PLAYING) {
            s += " [modo=playing: edicao de UI persiste apos o Stop]";
        }
        return s;
    }

    // dryRun barato para operacoes destrutivas de UI (o gate central so cobre
    // SCENE_MUTATING; UI persistente tem semantica de Play oposta e fica de fora).
    private static boolean isDryRun(JSONObject args) {
        return args.optBoolean("dryRun", false);
    }

    void registerAll() {
        registerUiDirectTools();
        registerCanvasTools();
        registerExtraWidgets();
        registerStyleTools();
    }

    // Descricao comum sobre onde a UI aparece, anexada a cada ferramenta.
    private static String withTargetDoc(String base) {
        return base + " Informe 'objectName' para montar no CanvasComponent daquele objeto "
                + "(PERSISTENTE, anexa se preciso); sem ele, vai no canvas global de runtime (VOLATIL).";
    }

    private void registerUiDirectTools() {
        // ui_create_label
        Map<String, String> labelProps = new LinkedHashMap<>();
        labelProps.put("name", "Nome unico do elemento NO CANVAS alvo (para consultar/alterar depois)");
        labelProps.put("text", "Texto a exibir");
        labelProps.put("x", "Posicao X em pixels de tela (padrao 20)");
        labelProps.put("y", "Posicao Y em pixels de tela (padrao 20)");
        labelProps.put("width", "Largura em px (padrao 240)");
        labelProps.put("height", "Altura em px (padrao 26)");
        labelProps.put("color", "Cor do texto em hex, ex: #FFFFFF (padrao branco)");
        labelProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_label",
            withTargetDoc("Cria um texto (label) na UI in-game, sem precisar de script. Requer Play para aparecer."),
            IgnisToolRegistry.schemaWith(labelProps, List.of("name", "text")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                UILabel label = new UILabel(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 240), args.optDouble("height", 26));
                label.setName(name);
                if (args.has("color")) label.setTextColor(IgnisToolRegistry.safeColor(args.optString("color"), Color.WHITE));
                t.canvas.addChild(label);
                return "Label criado: " + name + "." + suffix(t);
            });

        // ui_create_button
        Map<String, String> buttonProps = new LinkedHashMap<>();
        buttonProps.put("name", "Nome unico do elemento no canvas alvo");
        buttonProps.put("text", "Texto do botao");
        buttonProps.put("x", "Posicao X (padrao 20)");
        buttonProps.put("y", "Posicao Y (padrao 20)");
        buttonProps.put("width", "Largura em px (padrao 150)");
        buttonProps.put("height", "Altura em px (padrao 40)");
        buttonProps.put("removeOnClick", "true para o botao se auto-remover ao ser clicado (padrao false)");
        buttonProps.put("actionData", "Acao declarativa persistida (ex: 'signal:abrir_menu', 'scene:Floresta'). "
                + "Um script de runtime le getActionData() e conecta; o motor nao interpreta sozinho.");
        buttonProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_button",
            withTargetDoc("Cria um botao na UI in-game, sem precisar de script. Requer Play para aparecer/clicar."),
            IgnisToolRegistry.schemaWith(buttonProps, List.of("name", "text")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                UIButton btn = new UIButton(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 150), args.optDouble("height", 40));
                btn.setName(name);
                if (args.has("actionData")) btn.setActionData(args.optString("actionData", ""));
                if (args.optBoolean("removeOnClick", false)) {
                    btn.setOnClick(() -> t.canvas.removeChild(btn));
                }
                t.canvas.addChild(btn);
                return "Botao criado: " + name + "." + suffix(t);
            });

        // ui_create_progressbar
        Map<String, String> pbProps = new LinkedHashMap<>();
        pbProps.put("name", "Nome unico do elemento no canvas alvo");
        pbProps.put("x", "Posicao X (padrao 20)");
        pbProps.put("y", "Posicao Y (padrao 20)");
        pbProps.put("width", "Largura em px (padrao 200)");
        pbProps.put("height", "Altura em px (padrao 22)");
        pbProps.put("value", "Valor atual (padrao igual ao maxValue, ou 100)");
        pbProps.put("maxValue", "Valor maximo (padrao 100)");
        pbProps.put("fillColor", "Cor de preenchimento em hex (padrao verde)");
        pbProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_progressbar",
            withTargetDoc("Cria uma barra de progresso (HP, mana, loading...) na UI in-game, sem precisar de script."),
            IgnisToolRegistry.schemaWith(pbProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                float max = (float) args.optDouble("maxValue", 100);
                float value = (float) args.optDouble("value", max);
                UIProgressBar bar = new UIProgressBar(args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 200), args.optDouble("height", 22));
                bar.setName(name);
                bar.setValue(value, max);
                bar.setFillColor(IgnisToolRegistry.safeColor(args.optString("fillColor", ""), new Color(60, 190, 90)));
                t.canvas.addChild(bar);
                return "Barra de progresso criada: " + name + "." + suffix(t);
            });

        // ui_create_panel
        Map<String, String> panelProps = new LinkedHashMap<>();
        panelProps.put("name", "Nome unico do elemento no canvas alvo");
        panelProps.put("x", "Posicao X (padrao 20)");
        panelProps.put("y", "Posicao Y (padrao 20)");
        panelProps.put("width", "Largura em px (padrao 300)");
        panelProps.put("height", "Altura em px (padrao 200)");
        panelProps.put("backgroundColor", "Cor de fundo em hex (padrao cinza escuro translucido)");
        panelProps.put("layout", "NONE, VERTICAL, HORIZONTAL ou GRID (padrao NONE)");
        panelProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_panel",
            withTargetDoc("Cria um painel container na UI in-game, sem precisar de script."),
            IgnisToolRegistry.schemaWith(panelProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                UIPanel panel = new UIPanel(name, args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 300), args.optDouble("height", 200));
                if (args.has("backgroundColor")) panel.setBackgroundColor(IgnisToolRegistry.safeColor(args.optString("backgroundColor"), null));
                try {
                    panel.setLayout(UIPanel.Layout.valueOf(args.optString("layout", "NONE").trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException iae) {
                    return "Erro: layout invalido (use NONE, VERTICAL, HORIZONTAL ou GRID).";
                }
                t.canvas.addChild(panel);
                return "Painel criado: " + name + "." + suffix(t);
            });

        // ui_create_image
        Map<String, String> uiImgProps = new LinkedHashMap<>();
        uiImgProps.put("name", "Nome unico do elemento no canvas alvo");
        uiImgProps.put("path", "Caminho da imagem, relativo ao projeto");
        uiImgProps.put("x", "Posicao X (padrao 20)");
        uiImgProps.put("y", "Posicao Y (padrao 20)");
        uiImgProps.put("width", "Largura em px (padrao 200)");
        uiImgProps.put("height", "Altura em px (padrao 200)");
        uiImgProps.put("scaleMode", "STRETCH|FIT|FILL|NONE|TILE|NINE_SLICE (padrao FIT)");
        uiImgProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_image",
            withTargetDoc("Cria uma imagem na UI in-game (skin de painel/botao, icone). Requer Play para aparecer."),
            IgnisToolRegistry.schemaWith(uiImgProps, List.of("name", "path")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                String path = args.optString("path", "");
                java.io.File resolved = reg.resolveInProject(path);
                if (resolved == null) return "Erro: imagem fora do projeto: " + path;
                UIImage img = new UIImage(resolved.getAbsolutePath(),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 200), args.optDouble("height", 200));
                img.setName(name);
                try {
                    img.setScaleMode(UIImage.ScaleMode.valueOf(
                            args.optString("scaleMode", "FIT").trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException iae) {
                    return "Erro: scaleMode invalido (STRETCH|FIT|FILL|NONE|TILE|NINE_SLICE).";
                }
                t.canvas.addChild(img);
                return "Imagem de UI criada: " + name + "." + suffix(t);
            });

        // ui_set_nine_slice
        Map<String, String> nsProps = new LinkedHashMap<>();
        nsProps.put("name", "Nome de uma UIImage ja criada");
        nsProps.put("left", "Margem esquerda em px da imagem original");
        nsProps.put("right", "Margem direita em px");
        nsProps.put("top", "Margem superior em px");
        nsProps.put("bottom", "Margem inferior em px");
        nsProps.put("objectName", "Opcional: o CanvasComponent onde a UIImage esta");
        reg.add("ui_set_nine_slice",
            "Ativa o modo nine-slice numa UIImage e define as quatro margens (cantos fixos, bordas/miolo esticam).",
            IgnisToolRegistry.schemaWith(nsProps, List.of("name", "left", "right", "top", "bottom")),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                UIComponent el = t.canvas.findByName(args.optString("name", ""));
                if (!(el instanceof UIImage img)) {
                    return "Erro: UIImage nao encontrada: " + args.optString("name", "");
                }
                img.setScaleMode(UIImage.ScaleMode.NINE_SLICE);
                img.setSlices(args.optInt("left"), args.optInt("right"),
                        args.optInt("top"), args.optInt("bottom"));
                return "Nine-slice aplicado em " + img.getName() + " ("
                        + img.getSliceLeft() + "," + img.getSliceRight() + ","
                        + img.getSliceTop() + "," + img.getSliceBottom() + ")." + suffix(t);
            });

        // ui_set_text
        Map<String, String> setTextProps = new LinkedHashMap<>();
        setTextProps.put("name", "Nome do elemento");
        setTextProps.put("text", "Novo texto");
        setTextProps.put("objectName", "Opcional: o CanvasComponent onde o elemento esta");
        reg.add("ui_set_text",
            "Altera o texto de um UILabel ou UIButton ja criado.",
            IgnisToolRegistry.schemaWith(setTextProps, List.of("name", "text")),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                UIComponent el = t.canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                if (el instanceof UILabel l) { l.setText(args.optString("text", "")); return "Texto atualizado." + suffix(t); }
                if (el instanceof UIButton b) { b.setText(args.optString("text", "")); return "Texto atualizado." + suffix(t); }
                return "Erro: elemento nao suporta texto (tipo: " + el.getType() + ").";
            });

        // ui_set_progress_value
        Map<String, String> setProgressProps = new LinkedHashMap<>();
        setProgressProps.put("name", "Nome da barra de progresso");
        setProgressProps.put("value", "Novo valor atual");
        setProgressProps.put("maxValue", "Novo valor maximo (opcional; mantem o atual se omitido)");
        setProgressProps.put("objectName", "Opcional: o CanvasComponent onde a barra esta");
        reg.add("ui_set_progress_value",
            "Atualiza o valor (e opcionalmente o maximo) de uma barra de progresso existente.",
            IgnisToolRegistry.schemaWith(setProgressProps, List.of("name", "value")),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                UIComponent el = t.canvas.findByName(args.optString("name", ""));
                if (!(el instanceof UIProgressBar bar)) return "Erro: barra de progresso nao encontrada: " + args.optString("name", "");
                float value = (float) args.optDouble("value", 0);
                float max = args.has("maxValue") ? (float) args.optDouble("maxValue") : bar.getMaxValue();
                bar.setValue(value, max);
                return "Valor atualizado: " + value + "/" + max + "." + suffix(t);
            });

        // ui_remove_element
        Map<String, String> rmProps = new LinkedHashMap<>();
        rmProps.put("name", "Nome do elemento a remover");
        rmProps.put("objectName", "Opcional: o CanvasComponent onde o elemento esta");
        rmProps.put("dryRun", "Se true, so relata o que removeria (nao aplica)");
        reg.add("ui_remove_element",
            "Remove um elemento de UI (label, botao, barra, painel) pelo nome.",
            IgnisToolRegistry.schemaWith(rmProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                UIComponent el = t.canvas.findByName(name);
                if (el == null) return "Erro: elemento nao encontrado: " + name;
                if (isDryRun(args)) return "[dryRun] removeria '" + name + "' [" + el.getType() + "] do "
                        + t.label + " (nao aplicado).";
                UIComponent parent = el.getParent();
                if (parent != null) parent.removeChild(el); else t.canvas.removeChild(el);
                return "Elemento removido: " + name + "." + suffix(t);
            });

        // ui_clear_all
        Map<String, String> clearProps = new LinkedHashMap<>();
        clearProps.put("objectName", "Opcional: limpa o CanvasComponent deste objeto (senao o canvas global)");
        clearProps.put("dryRun", "Se true, so informa quantos elementos removeria (nao aplica)");
        reg.add("ui_clear_all",
            "Remove TODOS os elementos da UI in-game do canvas alvo (destrutivo — use dryRun antes se em duvida).",
            IgnisToolRegistry.schemaWith(clearProps, List.of()),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                int count = t.canvas.getChildren().size();
                if (isDryRun(args)) return "[dryRun] limparia " + count + " elemento(s) do " + t.label + " (nao aplicado).";
                t.canvas.clearChildren();
                return "UI limpa (" + count + " removido(s))." + suffix(t);
            });

        // ui_list_elements
        Map<String, String> listProps = new LinkedHashMap<>();
        listProps.put("objectName", "Opcional: lista o CanvasComponent deste objeto (senao o canvas global)");
        reg.add("ui_list_elements",
            "Lista os elementos atuais da UI in-game do canvas alvo (nome, tipo, posicao, tamanho).",
            IgnisToolRegistry.schemaWith(listProps, List.of()),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                List<UIComponent> children = t.canvas.getChildren();
                if (children.isEmpty()) return "(nenhum elemento de UI no " + t.label + ")";
                StringBuilder sb = new StringBuilder(t.label + ":\n");
                for (UIComponent c : children) {
                    sb.append(c.getName()).append(" [").append(c.getType()).append("] @ (")
                      .append((int) c.getX()).append(',').append((int) c.getY()).append(") ")
                      .append((int) c.getWidth()).append('x').append((int) c.getHeight()).append('\n');
                }
                return sb.toString();
            });
    }

    // ------------------------------------------------------------------
    // Gestao do CanvasComponent (anexar/detachar/props) — P1 fatia 2
    // ------------------------------------------------------------------

    private void registerCanvasTools() {
        Map<String, String> attachProps = new LinkedHashMap<>();
        attachProps.put("objectName", "Objeto que recebera a UI persistente");
        attachProps.put("sortingOrder", "Ordem de desenho entre canvases (maior na frente; padrao 0)");
        reg.add("ui_attach_canvas",
            "Anexa um CanvasComponent (UI PERSISTENTE) a um objeto, para montar HUD/menu que sobrevive ao Stop e "
            + "salva na cena. Erro se ja existir. As ferramentas ui_create_* com objectName ja anexam sozinhas.",
            IgnisToolRegistry.schemaWith(attachProps, List.of("objectName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String objectName = args.optString("objectName", "").trim();
                GameObject go = reg.findObject(objectName);
                if (go == null) return "Erro: objeto nao encontrado: " + objectName;
                if (go.getComponent(CanvasComponent.class) != null) {
                    return "Erro: '" + objectName + "' ja tem CanvasComponent (use ui_set_canvas_props para ajustar).";
                }
                CanvasComponent cc = new CanvasComponent();
                if (args.has("sortingOrder")) cc.setSortingOrder(args.optInt("sortingOrder"));
                go.addComponent(cc);
                return "CanvasComponent anexado a '" + objectName + "' (ordem " + cc.getSortingOrder() + ").";
            });

        Map<String, String> propsProps = new LinkedHashMap<>();
        propsProps.put("objectName", "Objeto dono do CanvasComponent");
        propsProps.put("sortingOrder", "Nova ordem de desenho entre canvases (maior na frente)");
        propsProps.put("visible", "true/false: desenha e recebe input, ou esconde o canvas inteiro");
        reg.add("ui_set_canvas_props",
            "Ajusta o CanvasComponent de um objeto: ordem de desenho entre canvases e visibilidade do canvas inteiro.",
            IgnisToolRegistry.schemaWith(propsProps, List.of("objectName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String objectName = args.optString("objectName", "").trim();
                GameObject go = reg.findObject(objectName);
                if (go == null) return "Erro: objeto nao encontrado: " + objectName;
                CanvasComponent cc = go.getComponent(CanvasComponent.class);
                if (cc == null) return "Erro: '" + objectName + "' nao tem CanvasComponent (use ui_attach_canvas).";
                if (args.has("sortingOrder")) cc.setSortingOrder(args.optInt("sortingOrder"));
                if (args.has("visible")) cc.setCanvasVisible(args.optBoolean("visible"));
                return "CanvasComponent de '" + objectName + "': ordem " + cc.getSortingOrder()
                        + ", visivel=" + cc.isCanvasVisible() + ".";
            });

        Map<String, String> detachProps = new LinkedHashMap<>();
        detachProps.put("objectName", "Objeto cujo CanvasComponent (e toda a UI) sera removido");
        detachProps.put("dryRun", "Se true, so informa quantos widgets removeria (nao aplica)");
        reg.add("ui_detach_canvas",
            "Remove o CanvasComponent de um objeto E toda a arvore de UI dele (destrutivo — use dryRun antes; "
            + "get_ui_tree para conferir).",
            IgnisToolRegistry.schemaWith(detachProps, List.of("objectName")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String objectName = args.optString("objectName", "").trim();
                GameObject go = reg.findObject(objectName);
                if (go == null) return "Erro: objeto nao encontrado: " + objectName;
                CanvasComponent cc = go.getComponent(CanvasComponent.class);
                if (cc == null) return "Erro: '" + objectName + "' nao tem CanvasComponent.";
                int count = cc.getCanvas().getChildren().size();
                if (isDryRun(args)) return "[dryRun] removeria o CanvasComponent de '" + objectName + "' com "
                        + count + " widget(s) (nao aplicado).";
                go.removeComponent(cc);
                return "CanvasComponent removido de '" + objectName + "' (" + count + " widget(s) descartado(s)).";
            });
    }

    // ------------------------------------------------------------------
    // Widgets que faltavam no MCP (o UIFactory ja os serializava)
    // ------------------------------------------------------------------

    private void registerExtraWidgets() {
        // ui_create_textfield
        Map<String, String> tfProps = new LinkedHashMap<>();
        tfProps.put("name", "Nome unico do elemento no canvas alvo");
        tfProps.put("x", "Posicao X (padrao 20)");
        tfProps.put("y", "Posicao Y (padrao 20)");
        tfProps.put("width", "Largura em px (padrao 220)");
        tfProps.put("height", "Altura em px (padrao 30)");
        tfProps.put("placeholder", "Texto de dica quando vazio");
        tfProps.put("text", "Texto inicial");
        tfProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_textfield",
            withTargetDoc("Cria um campo de texto editavel na UI in-game."),
            IgnisToolRegistry.schemaWith(tfProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                UITextField field = new UITextField(args.optString("placeholder", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 220), args.optDouble("height", 30));
                field.setName(name);
                if (args.has("text")) field.setText(args.optString("text", ""));
                t.canvas.addChild(field);
                return "Campo de texto criado: " + name + "." + suffix(t);
            });

        // ui_create_checkbox
        Map<String, String> cbProps = new LinkedHashMap<>();
        cbProps.put("name", "Nome unico do elemento no canvas alvo");
        cbProps.put("text", "Rotulo ao lado da caixa");
        cbProps.put("checked", "true/false: estado inicial (padrao false)");
        cbProps.put("x", "Posicao X (padrao 20)");
        cbProps.put("y", "Posicao Y (padrao 20)");
        cbProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_checkbox",
            withTargetDoc("Cria uma checkbox (opcao booleana) na UI in-game."),
            IgnisToolRegistry.schemaWith(cbProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                UICheckbox cb = new UICheckbox(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20));
                cb.setName(name);
                cb.setChecked(args.optBoolean("checked", false));
                t.canvas.addChild(cb);
                return "Checkbox criada: " + name + "." + suffix(t);
            });

        // ui_create_slider
        Map<String, String> slProps = new LinkedHashMap<>();
        slProps.put("name", "Nome unico do elemento no canvas alvo");
        slProps.put("x", "Posicao X (padrao 20)");
        slProps.put("y", "Posicao Y (padrao 20)");
        slProps.put("width", "Largura em px (padrao 200)");
        slProps.put("height", "Altura em px (padrao 30)");
        slProps.put("min", "Valor minimo (padrao 0)");
        slProps.put("max", "Valor maximo (padrao 100)");
        slProps.put("value", "Valor inicial (padrao no meio da faixa)");
        slProps.put("objectName", "Opcional: monta no CanvasComponent deste objeto (persistente)");
        reg.add("ui_create_slider",
            withTargetDoc("Cria um slider (valor numerico continuo) na UI in-game."),
            IgnisToolRegistry.schemaWith(slProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, true);
                if (t.error != null) return t.error;
                String name = args.optString("name", "");
                if (t.canvas.findByName(name) != null) return "Erro: ja existe elemento '" + name + "' nesse canvas.";
                double min = args.optDouble("min", 0);
                double max = args.optDouble("max", 100);
                if (max <= min) return "Erro: max deve ser maior que min.";
                UISlider slider = new UISlider(args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 200), args.optDouble("height", 30));
                slider.setName(name);
                slider.setRange(min, max);
                slider.setValue((float) args.optDouble("value", (min + max) / 2.0));
                slider.setShowValue(true);
                t.canvas.addChild(slider);
                return "Slider criado: " + name + " [" + min + ".." + max + "]." + suffix(t);
            });
    }

    // ------------------------------------------------------------------
    // Ancoras, pivo e estilo (valem para volatil e persistente) — P1 fatia 2
    // ------------------------------------------------------------------

    private void registerStyleTools() {
        Map<String, String> anchorProps = new LinkedHashMap<>();
        anchorProps.put("name", "Nome do elemento");
        anchorProps.put("anchorX", "Ancora X no pai (0=esq, 0.5=centro, 1=dir)");
        anchorProps.put("anchorY", "Ancora Y no pai (0=topo, 0.5=meio, 1=base)");
        anchorProps.put("pivotX", "Pivo X do proprio elemento (0-1; opcional)");
        anchorProps.put("pivotY", "Pivo Y do proprio elemento (0-1; opcional)");
        anchorProps.put("objectName", "Opcional: o CanvasComponent onde o elemento esta");
        reg.add("ui_set_anchor",
            "Define a ancora (ponto de referencia no pai) e o pivo (origem do proprio elemento) de um widget, "
            + "para HUD que gruda em cantos/bordas e escala com a tela.",
            IgnisToolRegistry.schemaWith(anchorProps, List.of("name", "anchorX", "anchorY")),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                UIComponent el = t.canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                el.setAnchor(args.optDouble("anchorX", el.getAnchorX()), args.optDouble("anchorY", el.getAnchorY()));
                if (args.has("pivotX") || args.has("pivotY")) {
                    el.setPivot(args.optDouble("pivotX", el.getPivotX()), args.optDouble("pivotY", el.getPivotY()));
                }
                return "Ancora de '" + el.getName() + "' = (" + el.getAnchorX() + "," + el.getAnchorY()
                        + "), pivo = (" + el.getPivotX() + "," + el.getPivotY() + ")." + suffix(t);
            });

        Map<String, String> styleProps = new LinkedHashMap<>();
        styleProps.put("name", "Nome do elemento");
        styleProps.put("backgroundColor", "Cor de fundo em hex (#RRGGBB ou #RRGGBBAA)");
        styleProps.put("textColor", "Cor do texto em hex");
        styleProps.put("borderColor", "Cor da borda em hex");
        styleProps.put("borderWidth", "Espessura da borda em px");
        styleProps.put("borderRadius", "Raio dos cantos em px");
        styleProps.put("fontSize", "Tamanho da fonte em px");
        styleProps.put("padding", "Espacamento interno em px (aplica aos quatro lados)");
        styleProps.put("zOrder", "Ordem de desenho ENTRE irmaos no mesmo canvas (maior na frente)");
        styleProps.put("objectName", "Opcional: o CanvasComponent onde o elemento esta");
        reg.add("ui_set_style",
            "Ajusta o estilo de um widget: cores (fundo/texto/borda), borda, raio, fonte, padding e z-order "
            + "entre irmaos. So aplica os campos informados.",
            IgnisToolRegistry.schemaWith(styleProps, List.of("name")),
            args -> {
                Target t = resolveTarget(args, false);
                if (t.error != null) return t.error;
                UIComponent el = t.canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                if (args.has("backgroundColor")) el.setBackgroundColor(IgnisToolRegistry.safeColor(args.optString("backgroundColor"), el.getBackgroundColor()));
                if (args.has("textColor")) el.setTextColor(IgnisToolRegistry.safeColor(args.optString("textColor"), el.getTextColor()));
                if (args.has("borderColor")) el.setBorderColor(IgnisToolRegistry.safeColor(args.optString("borderColor"), el.getBorderColor()));
                if (args.has("borderWidth")) el.setBorderWidth(args.optInt("borderWidth"));
                if (args.has("borderRadius")) el.setBorderRadius(args.optInt("borderRadius"));
                if (args.has("fontSize")) el.setFontSize(args.optInt("fontSize"));
                if (args.has("padding")) el.setPadding(args.optInt("padding"));
                if (args.has("zOrder")) el.setZOrder(args.optInt("zOrder"));
                return "Estilo de '" + el.getName() + "' atualizado." + suffix(t);
            });
    }
}
