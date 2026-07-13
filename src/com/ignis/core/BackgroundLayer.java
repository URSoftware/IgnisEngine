package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import org.json.JSONObject;

/**
 * Camada de fundo com efeito de parallax (Fase C do plano do motor grafico).
 *
 * <p>Uma {@code BackgroundLayer} preenche a Scene View com um sprite (opcionalmente
 * repetido em X/Y) ou uma cor solida, deslocado em funcao da posicao da camera pelo
 * fator de parallax. Convencao do plano: {@code parallax = 0} deixa a camada fixa no
 * mundo (rola como um objeto normal, tipico de primeiro plano) e {@code parallax = 1}
 * a prende a camera (fixa na tela, tipico de ceu/fundo distante). Valores intermediarios
 * dao a sensacao de profundidade.</p>
 *
 * <p>Renderiza-se por conta propria (nao usa {@link SpriteComponent}) e desativa o
 * culling por camera ({@link #isCullable()} = false), ja que sempre cobre a tela.
 * O {@code zIndex} default e bem negativo para ficar atras das entidades; ajuste-o
 * para empilhar varias camadas (fundo distante com zIndex menor que o proximo).</p>
 */
public class BackgroundLayer extends GameObject {

    // Fatores de parallax por eixo (0 = fixo no mundo, 1 = preso a camera).
    private double parallaxX = 0.5;
    private double parallaxY = 0.5;

    // Repeticao (tiling) do sprite ao longo de cada eixo para cobrir a tela.
    private boolean repeatX = true;
    private boolean repeatY = true;

    // Caminho do sprite da camada (proprio, para nao criar um SpriteComponent que
    // seria desenhado tambem pelo pipeline padrao). Pode ser null.
    private String imagePath = null;

    // Cor de preenchimento solido, usada quando nao ha sprite (ou como fundo atras
    // de um sprite com transparencia). Null = sem cor.
    private Color color = null;

    // Teto de tiles desenhados por frame — protege contra loop gigante em zoom-out
    // extremo (uma camada minuscula repetida cobrindo um mundo enorme).
    private static final long MAX_TILES = 4096;

    public BackgroundLayer() {
        super();
        this.name = "BackgroundLayer";
        this.width = 256;
        this.height = 256;
        this.zIndex = -1000;
        this.visible = true;
    }

    // ---- Propriedades ----

    public double getParallaxX() { return parallaxX; }
    public void setParallaxX(double v) { this.parallaxX = v; }

    public double getParallaxY() { return parallaxY; }
    public void setParallaxY(double v) { this.parallaxY = v; }

    /** Define o mesmo fator de parallax para os dois eixos. */
    public void setParallax(double v) { this.parallaxX = v; this.parallaxY = v; }

    public boolean isRepeatX() { return repeatX; }
    public void setRepeatX(boolean v) { this.repeatX = v; }

    public boolean isRepeatY() { return repeatY; }
    public void setRepeatY(boolean v) { this.repeatY = v; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String path) { this.imagePath = (path != null && path.isEmpty()) ? null : path; }

    public Color getColor() { return color; }
    public void setColor(Color c) { this.color = c; }

    /** Fundos cobrem a tela toda — nunca sao descartados pelo culling por camera. */
    @Override
    public boolean isCullable() {
        return false;
    }

    @Override
    public String getType() {
        return "BackgroundLayer";
    }

    @Override
    public void render(Graphics g) {
        if (!visible) return;
        Graphics2D g2d = (Graphics2D) g;
        BufferedImage img = (imagePath != null) ? AssetResolver.loadImage(imagePath) : null;
        Camera cam = (game != null) ? game.getViewCamera() : null;

        // Sem camera viva (ex.: runtime headless/teste): desenho simples ancorado
        // na posicao logica, sem parallax nem tiling.
        if (cam == null) {
            if (color != null) {
                g2d.setColor(color);
                g2d.fillRect((int) x, (int) y, Math.max(1, width), Math.max(1, height));
            }
            if (img != null) {
                g2d.drawImage(img, (int) x, (int) y, tileW(img), tileH(img), null);
            }
            return;
        }

        double[] vis = cam.getVisibleWorldBounds(); // [minX, minY, maxX, maxY]
        double tx = cam.getX() * parallaxX;
        double ty = cam.getY() * parallaxY;

        AffineTransform saved = g2d.getTransform();
        g2d.translate(tx, ty);

        // Regiao visivel expressa no espaco apos a translacao de parallax.
        double lminX = vis[0] - tx, lmaxX = vis[2] - tx;
        double lminY = vis[1] - ty, lmaxY = vis[3] - ty;

        if (color != null) {
            g2d.setColor(color);
            g2d.fillRect((int) Math.floor(lminX), (int) Math.floor(lminY),
                    (int) Math.ceil(lmaxX - lminX), (int) Math.ceil(lmaxY - lminY));
        }

        if (img != null) {
            int w = tileW(img);
            int h = tileH(img);
            int startCol = repeatX ? (int) Math.floor((lminX - x) / w) : 0;
            int endCol = repeatX ? (int) Math.ceil((lmaxX - x) / w) : 0;
            int startRow = repeatY ? (int) Math.floor((lminY - y) / h) : 0;
            int endRow = repeatY ? (int) Math.ceil((lmaxY - y) / h) : 0;

            long tiles = (long) (endCol - startCol + 1) * (endRow - startRow + 1);
            if (tiles > MAX_TILES) {
                g2d.drawImage(img, (int) x, (int) y, w, h, null);
            } else {
                for (int c = startCol; c <= endCol; c++) {
                    for (int r = startRow; r <= endRow; r++) {
                        g2d.drawImage(img, (int) (x + (long) c * w), (int) (y + (long) r * h), w, h, null);
                    }
                }
            }
        }

        g2d.setTransform(saved);
    }

    private int tileW(BufferedImage img) {
        return (width > 0) ? width : img.getWidth();
    }

    private int tileH(BufferedImage img) {
        return (height > 0) ? height : img.getHeight();
    }

    @Override
    public JSONObject saveProperties() {
        JSONObject p = new JSONObject();
        p.put("parallaxX", parallaxX);
        p.put("parallaxY", parallaxY);
        p.put("repeatX", repeatX);
        p.put("repeatY", repeatY);
        if (imagePath != null) p.put("imagePath", imagePath);
        if (color != null) p.put("color", color.getRGB());
        return p;
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props == null) return;
        parallaxX = props.optDouble("parallaxX", parallaxX);
        parallaxY = props.optDouble("parallaxY", parallaxY);
        repeatX = props.optBoolean("repeatX", repeatX);
        repeatY = props.optBoolean("repeatY", repeatY);
        if (props.has("imagePath")) setImagePath(props.getString("imagePath"));
        if (props.has("color")) color = new Color(props.getInt("color"), true);
    }
}
