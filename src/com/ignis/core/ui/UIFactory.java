package com.ignis.core.ui;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * UIFactory - Fábrica para criação de componentes de UI a partir de JSON.
 * 
 * Esta classe é responsável por deserializar componentes de UI salvos em JSON,
 * permitindo carregar layouts de interface de arquivos.
 */
public class UIFactory {

    /**
     * Cria um UIComponent a partir de JSON.
     * @param json Objeto JSON com os dados do componente
     * @return Componente criado, ou null se o tipo for desconhecido
     */
    public static UIComponent createFromJSON(JSONObject json) {
        if (json == null) return null;
        
        String type = json.optString("type", "");
        UIComponent component = null;
        
        switch (type) {
            case "Canvas":
                component = UICanvas.fromJSON(json);
                break;
            case "Button":
                component = UIButton.fromJSON(json);
                break;
            case "Label":
                component = UILabel.fromJSON(json);
                break;
            case "Panel":
                component = UIPanel.fromJSON(json);
                break;
            case "Image":
                component = UIImage.fromJSON(json);
                break;
            case "ProgressBar":
                component = UIProgressBar.fromJSON(json);
                break;
            case "TextField":
                component = UITextField.fromJSON(json);
                break;
            case "Slider":
                component = UISlider.fromJSON(json);
                break;
            case "Checkbox":
                component = UICheckbox.fromJSON(json);
                break;
            default:
                System.err.println("[UIFactory] Tipo de componente desconhecido: " + type);
                return null;
        }
        
        // Carregar filhos recursivamente (exceto para tipos que já carregam)
        if (component != null && !type.equals("Canvas") && !type.equals("Panel")) {
            JSONArray childrenArray = json.optJSONArray("children");
            if (childrenArray != null) {
                for (int i = 0; i < childrenArray.length(); i++) {
                    JSONObject childJson = childrenArray.getJSONObject(i);
                    UIComponent child = createFromJSON(childJson);
                    if (child != null) {
                        component.addChild(child);
                    }
                }
            }
        }
        
        return component;
    }
    
    /**
     * Cria um novo Canvas vazio.
     */
    public static UICanvas createCanvas() {
        return new UICanvas();
    }
    
    /**
     * Cria um novo Canvas com nome.
     */
    public static UICanvas createCanvas(String name) {
        return new UICanvas(name);
    }
    
    /**
     * Cria um novo Button.
     */
    public static UIButton createButton(String text, double x, double y) {
        return new UIButton(text, x, y, 150, 40);
    }
    
    /**
     * Cria um novo Button com dimensões.
     */
    public static UIButton createButton(String text, double x, double y, double width, double height) {
        return new UIButton(text, x, y, width, height);
    }
    
    /**
     * Cria um novo Label.
     */
    public static UILabel createLabel(String text, double x, double y) {
        return new UILabel(text, x, y);
    }
    
    /**
     * Cria um novo Panel.
     */
    public static UIPanel createPanel(double x, double y, double width, double height) {
        return new UIPanel(x, y, width, height);
    }
    
    /**
     * Cria um menu vertical com botões.
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura dos botões
     * @param buttonLabels Labels dos botões
     * @param onClick Callback para todos os botões (recebe índice)
     * @return Painel com os botões
     */
    public static UIPanel createVerticalMenu(double x, double y, double width, 
            String[] buttonLabels, java.util.function.Consumer<Integer> onClick) {
        
        UIPanel panel = new UIPanel(x, y, width, 0);
        panel.setLayout(UIPanel.Layout.VERTICAL);
        panel.setSpacing(10);
        panel.setFitContent(true);
        panel.setBackgroundColor(new java.awt.Color(0, 0, 0, 0));
        panel.setBorderWidth(0);
        
        for (int i = 0; i < buttonLabels.length; i++) {
            final int index = i;
            UIButton btn = new UIButton(buttonLabels[i], 0, 0, width - 20, 40);
            btn.setOnClick(() -> onClick.accept(index));
            panel.addChild(btn);
        }
        
        return panel;
    }
    
    /**
     * Cria uma barra de status (HP/MP).
     * @param x Posição X
     * @param y Posição Y
     * @param width Largura
     * @param label Label opcional
     * @param fillColor Cor de preenchimento
     * @return Painel com label e barra
     */
    public static UIPanel createStatusBar(double x, double y, double width, 
            String label, java.awt.Color fillColor) {
        
        UIPanel panel = new UIPanel(x, y, width, 30);
        panel.setLayout(UIPanel.Layout.HORIZONTAL);
        panel.setSpacing(5);
        panel.setBackgroundColor(new java.awt.Color(0, 0, 0, 0));
        panel.setBorderWidth(0);
        panel.setPadding(0);
        
        if (label != null && !label.isEmpty()) {
            UILabel lbl = new UILabel(label, 0, 0, 40, 25);
            lbl.setAlignment(UILabel.Alignment.RIGHT);
            panel.addChild(lbl);
        }
        
        UIProgressBar bar = new UIProgressBar(0, 0, width - 50, 25);
        bar.setFillColor(fillColor);
        bar.setShowText(true);
        panel.addChild(bar);
        
        return panel;
    }
    
    /**
     * Cria um diálogo simples.
     * @param title Título do diálogo
     * @param message Mensagem
     * @param x Posição X
     * @param y Posição Y
     * @param onOk Callback do botão OK
     * @return Painel do diálogo
     */
    public static UIPanel createDialog(String title, String message, double x, double y, Runnable onOk) {
        UIPanel dialog = new UIPanel(x, y, 300, 150);
        dialog.setLayout(UIPanel.Layout.VERTICAL);
        dialog.setSpacing(15);
        
        UILabel titleLabel = new UILabel(title, 0, 0, 280, 30);
        titleLabel.setFontSize(16);
        titleLabel.setFontStyle(java.awt.Font.BOLD);
        titleLabel.setAlignment(UILabel.Alignment.CENTER);
        dialog.addChild(titleLabel);
        
        UILabel msgLabel = new UILabel(message, 0, 0, 280, 40);
        msgLabel.setAlignment(UILabel.Alignment.CENTER);
        msgLabel.setMultiline(true);
        dialog.addChild(msgLabel);
        
        UIButton okBtn = new UIButton("OK", 0, 0, 100, 35);
        okBtn.applyStyle("primary");
        okBtn.setOnClick(onOk);
        dialog.addChild(okBtn);
        
        return dialog;
    }
}
