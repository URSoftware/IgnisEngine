package com.ignis.core;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.json.JSONObject;

/**
 * Ponto de luz 2D (Fase D do plano do motor grafico, item 3.11).
 *
 * <p>Iluminacao por <b>mascara de escuridao</b>, sem OpenGL nem shaders — coerente
 * com o render Java2D puro do motor. A cena tem uma <b>luz ambiente</b>
 * ({@code Scene.ambientLight}, cuja componente alpha e a intensidade da escuridao).
 * Cada {@code LightObject} abre um buraco suave (degradê radial) nessa escuridao via
 * {@link AlphaComposite#DstOut}, revelando a cena por baixo, e opcionalmente tinge a
 * area iluminada com {@link #lightColor}. O passe roda em screen-space logo antes da
 * UI (ver {@code Game.renderLightingPass}).</p>
 *
 * <p>A composicao da mascara vive em {@link #composeMask} — estatica e sem estado —
 * para poder ser exercitada por teste headless (sem instanciar {@link Game}).</p>
 */
public class LightObject extends GameObject {

    private Color lightColor = new Color(255, 240, 200); // tom quente de lampada/tocha
    private double radius = 160.0;
    private double intensity = 1.0; // 0..1: quanto a luz remove da escuridao no centro

    public LightObject() {
        super();
        this.name = "LightObject";
        this.zIndex = 0;
        this.visible = true;
        this.width = 16;
        this.height = 16;
    }

    // ---- Propriedades ----

    public Color getLightColor() { return lightColor; }
    public void setLightColor(Color c) { this.lightColor = (c != null) ? c : Color.WHITE; }

    public double getRadius() { return radius; }
    public void setRadius(double r) { this.radius = Math.max(1, r); }

    public double getIntensity() { return intensity; }
    public void setIntensity(double v) { this.intensity = Math.max(0, Math.min(1, v)); }

    @Override
    public String getType() {
        return "LightObject";
    }

    /**
     * No espaco do mundo o {@code LightObject} desenha apenas um gizmo (circulo do
     * raio + centro) no modo de edicao, para o autor ver a posicao/alcance. A luz
     * de fato e composta no passe de iluminacao em screen-space, nao aqui.
     */
    @Override
    public void render(Graphics g) {
        if (!visible || game == null || game.getGameState() != Game.GameState.EDITING) return;
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(lightColor.getRed(), lightColor.getGreen(), lightColor.getBlue(), 120));
        g2d.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND, 1.0f, new float[] {6f, 6f}, 0f));
        int r = (int) radius;
        g2d.drawOval((int) x - r, (int) y - r, r * 2, r * 2);
        g2d.drawLine((int) x - 6, (int) y, (int) x + 6, (int) y);
        g2d.drawLine((int) x, (int) y - 6, (int) x, (int) y + 6);
    }

    /**
     * Compoe a mascara de iluminacao num buffer ARGB {@code w x h}: preenche com a
     * escuridao {@code ambient} e, para cada luz, abre um degradê radial via DstOut
     * (centro remove ate {@code intensity} da escuridao, borda nao remove nada). O
     * {@code worldToDevice} mapeia coordenadas de mundo para pixels do buffer (a
     * transform de camera); passe {@code null} para 1:1 (mundo == tela). Reutiliza
     * {@code reuse} quando o tamanho bate (evita realocar por frame).
     */
    public static BufferedImage composeMask(BufferedImage reuse, int w, int h, Color ambient,
                                            List<LightObject> lights, AffineTransform worldToDevice) {
        if (w <= 0 || h <= 0 || ambient == null) return reuse;
        BufferedImage mask = (reuse != null && reuse.getWidth() == w && reuse.getHeight() == h)
                ? reuse : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        try {
            // Limpa e pinta a escuridao ambiente em screen-space.
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(ambient);
            g.fillRect(0, 0, w, h);

            if (lights != null && !lights.isEmpty()) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (worldToDevice != null) g.transform(worldToDevice);
                g.setComposite(AlphaComposite.DstOut);
                for (LightObject light : lights) {
                    if (light == null || !light.isVisible()) continue;
                    double r = Math.max(1, light.getRadius());
                    float inten = (float) Math.max(0, Math.min(1, light.getIntensity()));
                    if (inten <= 0) continue;
                    double cx = light.getX();
                    double cy = light.getY();
                    RadialGradientPaint paint = new RadialGradientPaint(
                            new Point2D.Double(cx, cy), (float) r,
                            new float[] {0f, 1f},
                            new Color[] {new Color(0f, 0f, 0f, inten), new Color(0f, 0f, 0f, 0f)});
                    g.setPaint(paint);
                    g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
                }
            }
        } finally {
            g.dispose();
        }
        return mask;
    }

    @Override
    public JSONObject saveProperties() {
        JSONObject p = new JSONObject();
        p.put("lightColor", lightColor.getRGB());
        p.put("radius", radius);
        p.put("intensity", intensity);
        return p;
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props == null) return;
        if (props.has("lightColor")) lightColor = new Color(props.getInt("lightColor"), true);
        radius = Math.max(1, props.optDouble("radius", radius));
        intensity = Math.max(0, Math.min(1, props.optDouble("intensity", intensity)));
    }
}
