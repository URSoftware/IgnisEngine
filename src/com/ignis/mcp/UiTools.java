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
 * Ferramentas MCP de UI direta: paineis, labels, botoes, barras e imagens no UICanvas da cena.
 * Extraido do {@link IgnisToolRegistry} (Fase F, passo 10 — divisao por dominio):
 * registra as ferramentas via {@code reg.add(...)} e usa os helpers
 * package-private do registry (findObject, resolveInProject, schemaWith, ...).
 */
final class UiTools {

    private final IgnisToolRegistry reg;

    UiTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerUiDirectTools();
    }

    private void registerUiDirectTools() {
        // ui_create_label
        Map<String, String> labelProps = new LinkedHashMap<>();
        labelProps.put("name", "Nome unico do elemento (para consultar/alterar depois)");
        labelProps.put("text", "Texto a exibir");
        labelProps.put("x", "Posicao X em pixels de tela (padrao 20)");
        labelProps.put("y", "Posicao Y em pixels de tela (padrao 20)");
        labelProps.put("width", "Largura em px (padrao 240)");
        labelProps.put("height", "Altura em px (padrao 26)");
        labelProps.put("color", "Cor do texto em hex, ex: #FFFFFF (padrao branco)");
        reg.add("ui_create_label",
            "Cria um texto (label) na UI in-game, sem precisar de script. Requer Play para aparecer.",
            IgnisToolRegistry.schemaWith(labelProps, List.of("name", "text")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                UILabel label = new UILabel(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 240), args.optDouble("height", 26));
                label.setName(args.optString("name", ""));
                if (args.has("color")) label.setTextColor(IgnisToolRegistry.safeColor(args.optString("color"), Color.WHITE));
                canvas.addChild(label);
                return "Label criado: " + args.optString("name", "");
            });

        // ui_create_button
        Map<String, String> buttonProps = new LinkedHashMap<>();
        buttonProps.put("name", "Nome unico do elemento");
        buttonProps.put("text", "Texto do botao");
        buttonProps.put("x", "Posicao X (padrao 20)");
        buttonProps.put("y", "Posicao Y (padrao 20)");
        buttonProps.put("width", "Largura em px (padrao 150)");
        buttonProps.put("height", "Altura em px (padrao 40)");
        buttonProps.put("removeOnClick", "true para o botao se auto-remover ao ser clicado (padrao false)");
        reg.add("ui_create_button",
            "Cria um botao na UI in-game, sem precisar de script. Requer Play para aparecer/clicar.",
            IgnisToolRegistry.schemaWith(buttonProps, List.of("name", "text")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                UIButton btn = new UIButton(args.optString("text", ""),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 150), args.optDouble("height", 40));
                btn.setName(args.optString("name", ""));
                if (args.optBoolean("removeOnClick", false)) {
                    btn.setOnClick(() -> canvas.removeChild(btn));
                }
                canvas.addChild(btn);
                return "Botao criado: " + args.optString("name", "");
            });

        // ui_create_progressbar
        Map<String, String> pbProps = new LinkedHashMap<>();
        pbProps.put("name", "Nome unico do elemento");
        pbProps.put("x", "Posicao X (padrao 20)");
        pbProps.put("y", "Posicao Y (padrao 20)");
        pbProps.put("width", "Largura em px (padrao 200)");
        pbProps.put("height", "Altura em px (padrao 22)");
        pbProps.put("value", "Valor atual (padrao igual ao maxValue, ou 100)");
        pbProps.put("maxValue", "Valor maximo (padrao 100)");
        pbProps.put("fillColor", "Cor de preenchimento em hex (padrao verde)");
        reg.add("ui_create_progressbar",
            "Cria uma barra de progresso (HP, mana, loading...) na UI in-game, sem precisar de script.",
            IgnisToolRegistry.schemaWith(pbProps, List.of("name")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                float max = (float) args.optDouble("maxValue", 100);
                float value = (float) args.optDouble("value", max);
                UIProgressBar bar = new UIProgressBar(args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 200), args.optDouble("height", 22));
                bar.setName(args.optString("name", ""));
                bar.setValue(value, max);
                bar.setFillColor(IgnisToolRegistry.safeColor(args.optString("fillColor", ""), new Color(60, 190, 90)));
                canvas.addChild(bar);
                return "Barra de progresso criada: " + args.optString("name", "");
            });

        // ui_create_panel
        Map<String, String> panelProps = new LinkedHashMap<>();
        panelProps.put("name", "Nome unico do elemento");
        panelProps.put("x", "Posicao X (padrao 20)");
        panelProps.put("y", "Posicao Y (padrao 20)");
        panelProps.put("width", "Largura em px (padrao 300)");
        panelProps.put("height", "Altura em px (padrao 200)");
        panelProps.put("backgroundColor", "Cor de fundo em hex (padrao cinza escuro translucido)");
        panelProps.put("layout", "NONE, VERTICAL, HORIZONTAL ou GRID (padrao NONE)");
        reg.add("ui_create_panel",
            "Cria um painel container na UI in-game, sem precisar de script.",
            IgnisToolRegistry.schemaWith(panelProps, List.of("name")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                UIPanel panel = new UIPanel(args.optString("name", ""), args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 300), args.optDouble("height", 200));
                if (args.has("backgroundColor")) panel.setBackgroundColor(IgnisToolRegistry.safeColor(args.optString("backgroundColor"), null));
                try {
                    panel.setLayout(UIPanel.Layout.valueOf(args.optString("layout", "NONE").trim().toUpperCase()));
                } catch (IllegalArgumentException iae) {
                    return "Erro: layout invalido (use NONE, VERTICAL, HORIZONTAL ou GRID).";
                }
                canvas.addChild(panel);
                return "Painel criado: " + args.optString("name", "");
            });

        // ui_set_text
        reg.add("ui_set_text",
            "Altera o texto de um UILabel ou UIButton ja criado.",
            IgnisToolRegistry.schemaWith(new LinkedHashMap<>(Map.of("name", "Nome do elemento", "text", "Novo texto")),
                    List.of("name", "text")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                if (el instanceof UILabel) { ((UILabel) el).setText(args.optString("text", "")); return "Texto atualizado."; }
                if (el instanceof UIButton) { ((UIButton) el).setText(args.optString("text", "")); return "Texto atualizado."; }
                return "Erro: elemento nao suporta texto (tipo: " + el.getType() + ").";
            });

        // ui_set_progress_value
        Map<String, String> setProgressProps = new LinkedHashMap<>();
        setProgressProps.put("name", "Nome da barra de progresso");
        setProgressProps.put("value", "Novo valor atual");
        setProgressProps.put("maxValue", "Novo valor maximo (opcional; mantem o atual se omitido)");
        reg.add("ui_set_progress_value",
            "Atualiza o valor (e opcionalmente o maximo) de uma barra de progresso existente.",
            IgnisToolRegistry.schemaWith(setProgressProps, List.of("name", "value")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (!(el instanceof UIProgressBar)) return "Erro: barra de progresso nao encontrada: " + args.optString("name", "");
                UIProgressBar bar = (UIProgressBar) el;
                float value = (float) args.optDouble("value", 0);
                float max = args.has("maxValue") ? (float) args.optDouble("maxValue") : bar.getMaxValue();
                bar.setValue(value, max);
                return "Valor atualizado: " + value + "/" + max;
            });

        // ui_create_image
        Map<String, String> uiImgProps = new LinkedHashMap<>();
        uiImgProps.put("name", "Nome unico do elemento");
        uiImgProps.put("path", "Caminho da imagem, relativo ao projeto");
        uiImgProps.put("x", "Posicao X (padrao 20)");
        uiImgProps.put("y", "Posicao Y (padrao 20)");
        uiImgProps.put("width", "Largura em px (padrao 200)");
        uiImgProps.put("height", "Altura em px (padrao 200)");
        uiImgProps.put("scaleMode", "STRETCH|FIT|FILL|NONE|TILE|NINE_SLICE (padrao FIT)");
        reg.add("ui_create_image",
            "Cria uma imagem na UI in-game (skin de painel/botao, icone). Requer Play para aparecer.",
            IgnisToolRegistry.schemaWith(uiImgProps, List.of("name", "path")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                if (canvas.findByName(args.optString("name", "")) != null) return "Erro: ja existe elemento com esse nome.";
                String path = args.optString("path", "");
                File resolved = reg.resolveInProject(path);
                if (resolved == null) return "Erro: imagem fora do projeto: " + path;
                UIImage img = new UIImage(resolved.getAbsolutePath(),
                        args.optDouble("x", 20), args.optDouble("y", 20),
                        args.optDouble("width", 200), args.optDouble("height", 200));
                img.setName(args.optString("name", ""));
                try {
                    img.setScaleMode(UIImage.ScaleMode.valueOf(
                            args.optString("scaleMode", "FIT").trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException iae) {
                    return "Erro: scaleMode invalido (STRETCH|FIT|FILL|NONE|TILE|NINE_SLICE).";
                }
                canvas.addChild(img);
                return "Imagem de UI criada: " + args.optString("name", "");
            });

        // ui_set_nine_slice
        Map<String, String> nsProps = new LinkedHashMap<>();
        nsProps.put("name", "Nome de uma UIImage ja criada");
        nsProps.put("left", "Margem esquerda em px da imagem original");
        nsProps.put("right", "Margem direita em px");
        nsProps.put("top", "Margem superior em px");
        nsProps.put("bottom", "Margem inferior em px");
        reg.add("ui_set_nine_slice",
            "Ativa o modo nine-slice numa UIImage e define as quatro margens (cantos fixos, bordas/miolo esticam).",
            IgnisToolRegistry.schemaWith(nsProps, List.of("name", "left", "right", "top", "bottom")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (!(el instanceof UIImage)) {
                    return "Erro: UIImage nao encontrada: " + args.optString("name", "");
                }
                UIImage img = (UIImage) el;
                img.setScaleMode(UIImage.ScaleMode.NINE_SLICE);
                img.setSlices(args.optInt("left"), args.optInt("right"),
                        args.optInt("top"), args.optInt("bottom"));
                return "Nine-slice aplicado em " + img.getName() + " ("
                        + img.getSliceLeft() + "," + img.getSliceRight() + ","
                        + img.getSliceTop() + "," + img.getSliceBottom() + ")";
            });

        // ui_remove_element
        reg.add("ui_remove_element",
            "Remove um elemento de UI (label, botao, barra, painel) pelo nome.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do elemento a remover"), List.of("name")),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                UIComponent el = canvas.findByName(args.optString("name", ""));
                if (el == null) return "Erro: elemento nao encontrado: " + args.optString("name", "");
                UIComponent parent = el.getParent();
                if (parent != null) parent.removeChild(el); else canvas.removeChild(el);
                return "Elemento removido: " + args.optString("name", "");
            });

        // ui_clear_all
        reg.add("ui_clear_all",
            "Remove todos os elementos da UI in-game atual.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                canvas.clearChildren();
                return "UI limpa.";
            });

        // ui_list_elements
        reg.add("ui_list_elements",
            "Lista os elementos atuais da UI in-game (nome, tipo, posicao, tamanho).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                UICanvas canvas = reg.ensureUiCanvas();
                if (canvas == null) return "Erro: editor nao disponivel.";
                List<UIComponent> children = canvas.getChildren();
                if (children.isEmpty()) return "(nenhum elemento de UI)";
                StringBuilder sb = new StringBuilder();
                for (UIComponent c : children) {
                    sb.append(c.getName()).append(" [").append(c.getType()).append("] @ (")
                      .append((int) c.getX()).append(',').append((int) c.getY()).append(") ")
                      .append((int) c.getWidth()).append('x').append((int) c.getHeight()).append('\n');
                }
                return sb.toString();
            });
    }
}
