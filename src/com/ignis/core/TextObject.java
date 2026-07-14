package com.ignis.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import org.json.JSONObject;

/**
 * Texto renderizado no espaco do mundo (Fase D do plano do motor grafico, item 3.9).
 *
 * <p>Distinto da UI in-game (que vive em screen-space, {@code core/ui}): um
 * {@code TextObject} e uma entidade da cena com posicao/rotacao/zIndex de mundo,
 * util para placas, nomes sobre personagens, dano flutuante e rotulos de nivel.
 * Renderiza-se por conta propria (nao usa {@link SpriteComponent}).</p>
 *
 * <p>Compensa o eixo Y invertido do mundo com a mesma tecnica das formas e do
 * tilemap ({@code translate + scale(1,-1)}) para que o texto saia legivel, e
 * rotaciona em torno do centro da caixa como as formas primitivas. A largura/altura
 * da caixa (usadas por selecao, gizmos e culling) sao derivadas das metricas da
 * fonte a cada mudanca de texto/fonte via {@link #recomputeBounds()}.</p>
 */
public class TextObject extends GameObject {

    /** Alinhamento horizontal do texto dentro da caixa. */
    public enum TextAlign { LEFT, CENTER, RIGHT }

    private String text = "Texto";
    private String fontFamily = Font.SANS_SERIF;
    private int fontSize = 24;
    private Color color = Color.WHITE;
    private boolean bold = false;
    private boolean italic = false;
    private TextAlign align = TextAlign.LEFT;

    // Buffer minusculo so para obter FontMetrics sem um componente AWT vivo — permite
    // dimensionar a caixa em runtime headless/teste (sem editor aberto).
    private static final BufferedImage METRICS_IMG = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    public TextObject() {
        super();
        this.name = "TextObject";
        this.zIndex = 100; // texto de mundo costuma ficar na frente das entidades
        this.visible = true;
        recomputeBounds();
    }

    // ---- Propriedades ----

    public String getText() { return text; }
    public void setText(String t) { this.text = (t != null) ? t : ""; recomputeBounds(); }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String f) {
        this.fontFamily = (f != null && !f.isEmpty()) ? f : Font.SANS_SERIF;
        recomputeBounds();
    }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int s) { this.fontSize = Math.max(1, s); recomputeBounds(); }

    public Color getColor() { return color; }
    public void setColor(Color c) { this.color = (c != null) ? c : Color.WHITE; }

    public boolean isBold() { return bold; }
    public void setBold(boolean b) { this.bold = b; recomputeBounds(); }

    public boolean isItalic() { return italic; }
    public void setItalic(boolean i) { this.italic = i; recomputeBounds(); }

    public TextAlign getAlign() { return align; }
    public void setAlign(TextAlign a) { this.align = (a != null) ? a : TextAlign.LEFT; }

    private Font buildFont() {
        int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
        return new Font(fontFamily, style, fontSize);
    }

    /**
     * Recalcula largura/altura da caixa a partir das metricas da fonte (maior linha
     * x numero de linhas). Chamado sempre que texto/fonte muda para manter selecao,
     * gizmos e culling coerentes com o que e desenhado.
     */
    private void recomputeBounds() {
        Graphics2D g = METRICS_IMG.createGraphics();
        try {
            FontMetrics fm = g.getFontMetrics(buildFont());
            String[] lines = text.isEmpty() ? new String[] {""} : text.split("\n", -1);
            int maxW = 0;
            for (String line : lines) {
                maxW = Math.max(maxW, fm.stringWidth(line));
            }
            this.width = Math.max(1, maxW);
            this.height = Math.max(1, lines.length * fm.getHeight());
        } finally {
            g.dispose();
        }
    }

    @Override
    public String getType() {
        return "TextObject";
    }

    @Override
    public void render(Graphics g) {
        if (!visible || text == null || text.isEmpty()) return;
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        AffineTransform old = g2d.getTransform();

        // Rotacao em torno do centro da caixa, mesma convencao das formas primitivas.
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), x + width / 2.0, y + height / 2.0);
        }

        // Compensa o Y invertido do mundo: apos isto, (0,0) e o topo-esquerda da caixa
        // e o eixo Y cresce para baixo (espaco tipo-tela), com o texto legivel.
        g2d.translate(x, y + height);
        g2d.scale(1, -1);

        Font font = buildFont();
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.setColor(color);

        String[] lines = text.split("\n", -1);
        int lineH = fm.getHeight();
        int ascent = fm.getAscent();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lw = fm.stringWidth(line);
            int lx = switch (align) {
                case CENTER -> (width - lw) / 2;
                case RIGHT -> width - lw;
                default -> 0;
            };
            int ly = i * lineH + ascent;
            g2d.drawString(line, lx, ly);
        }

        g2d.setTransform(old);
    }

    @Override
    public JSONObject saveProperties() {
        JSONObject p = new JSONObject();
        p.put("text", text);
        p.put("fontFamily", fontFamily);
        p.put("fontSize", fontSize);
        p.put("color", color.getRGB());
        p.put("bold", bold);
        p.put("italic", italic);
        p.put("align", align.name());
        return p;
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props == null) return;
        if (props.has("text")) text = props.getString("text");
        if (props.has("fontFamily")) fontFamily = props.getString("fontFamily");
        fontSize = Math.max(1, props.optInt("fontSize", fontSize));
        if (props.has("color")) color = new Color(props.getInt("color"), true);
        bold = props.optBoolean("bold", bold);
        italic = props.optBoolean("italic", italic);
        if (props.has("align")) {
            try {
                align = TextAlign.valueOf(props.getString("align"));
            } catch (IllegalArgumentException ignored) {
                align = TextAlign.LEFT;
            }
        }
        recomputeBounds();
    }
}
