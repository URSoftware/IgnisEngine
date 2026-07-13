package com.ignis.core;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.json.JSONObject;

/**
 * Emissor de particulas (Fase C do plano do motor grafico).
 *
 * <p>Mantem um <b>pool pre-alocado</b> de particulas (sem alocacao por frame): cada
 * particula tem posicao/velocidade em espaco de mundo, idade e vida. A cada passo, as
 * vivas integram velocidade + gravidade e envelhecem; ao morrer voltam ao pool. A
 * emissao acumula {@code rate * dt} e cria particulas inteiras quando o acumulador
 * passa de 1. Cor e tamanho interpolam do inicio ao fim da vida; a opacidade tambem
 * decai, dando o fade-out tipico de poeira/fogo/explosao.</p>
 *
 * <p>Renderiza-se sozinho (nao usa {@link SpriteComponent}); com sprite desenha a
 * imagem, sem sprite desenha um circulo preenchido. Avancado por {@link #tick()} no
 * loop de Play (passo fixo 1/60), ou por {@link #step(double)} em testes.</p>
 */
public class ParticleEmitter extends GameObject {

    /** Particula do pool. Reutilizada; {@code alive=false} = livre. */
    private static final class Particle {
        double x, y, vx, vy, age, life;
        boolean alive;
    }

    // Configuracao (serializada).
    private int maxParticles = 200;
    private double emissionRate = 40.0;   // particulas por segundo
    private double lifetime = 1.2;        // vida base (s)
    private double lifetimeVar = 0.3;     // variacao +- (s)
    private double velX = 0.0;            // velocidade inicial media (px/s)
    private double velY = 80.0;
    private double velVar = 40.0;         // variacao +- por eixo (px/s)
    private double gravityX = 0.0;        // aceleracao (px/s^2)
    private double gravityY = -120.0;
    private double sizeStart = 10.0;      // diametro inicial (px)
    private double sizeEnd = 2.0;         // diametro final (px)
    private Color colorStart = new Color(255, 200, 80, 255);
    private Color colorEnd = new Color(255, 60, 0, 0);
    private String particleSprite = null; // opcional
    private boolean emitting = true;

    // Estado de runtime (nao serializado).
    private transient Particle[] pool;
    private transient int aliveCount = 0;
    private transient double spawnAccumulator = 0.0;
    private final transient Random rng = new Random();

    public ParticleEmitter() {
        super();
        this.name = "ParticleEmitter";
        this.width = 32;
        this.height = 32;
        this.visible = true;
    }

    private void ensurePool() {
        if (pool == null || pool.length != maxParticles) {
            Particle[] novo = new Particle[Math.max(1, maxParticles)];
            for (int i = 0; i < novo.length; i++) {
                novo[i] = new Particle();
            }
            pool = novo;
            aliveCount = 0;
        }
    }

    /** Emissores espalham particulas alem do proprio AABB — sem culling por camera. */
    @Override
    public boolean isCullable() {
        return false;
    }

    @Override
    public String getType() {
        return "ParticleEmitter";
    }

    @Override
    public void tick() {
        step(1.0 / 60.0);
    }

    /**
     * Avanca a simulacao das particulas em {@code dt} segundos: integra as vivas,
     * remove as expiradas e emite novas conforme a taxa. Exposto para testes.
     */
    public void step(double dt) {
        ensurePool();
        // 1. Integra e envelhece as particulas vivas.
        for (Particle p : pool) {
            if (!p.alive) continue;
            p.vx += gravityX * dt;
            p.vy += gravityY * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.age += dt;
            if (p.age >= p.life) {
                p.alive = false;
                aliveCount--;
            }
        }
        // 2. Emite novas particulas (taxa contínua).
        if (emitting && emissionRate > 0) {
            spawnAccumulator += emissionRate * dt;
            while (spawnAccumulator >= 1.0) {
                spawnAccumulator -= 1.0;
                if (!spawnOne()) break;
            }
        }
    }

    /** Emite {@code count} particulas de uma vez (explosao). Respeita o pool. */
    public void burst(int count) {
        ensurePool();
        for (int i = 0; i < count; i++) {
            if (!spawnOne()) break;
        }
    }

    private boolean spawnOne() {
        for (Particle p : pool) {
            if (!p.alive) {
                double cx = getX() + getWidth() / 2.0;
                double cy = getY() + getHeight() / 2.0;
                p.x = cx;
                p.y = cy;
                p.vx = velX + (rng.nextDouble() * 2 - 1) * velVar;
                p.vy = velY + (rng.nextDouble() * 2 - 1) * velVar;
                p.age = 0;
                p.life = Math.max(0.05, lifetime + (rng.nextDouble() * 2 - 1) * lifetimeVar);
                p.alive = true;
                aliveCount++;
                return true;
            }
        }
        return false; // pool cheio
    }

    /** Numero de particulas atualmente vivas (0 antes de emitir). */
    public int getAliveCount() {
        return aliveCount;
    }

    @Override
    public void render(Graphics g) {
        if (!visible || pool == null) return;
        Graphics2D g2d = (Graphics2D) g;
        BufferedImage img = (particleSprite != null) ? AssetResolver.loadImage(particleSprite) : null;
        Composite oldComposite = g2d.getComposite();
        Color oldColor = g2d.getColor();

        for (Particle p : pool) {
            if (!p.alive) continue;
            double t = Math.min(1.0, p.age / p.life); // 0 no nascimento, 1 na morte
            double size = lerp(sizeStart, sizeEnd, t);
            float alpha = (float) clamp01(lerp(colorStart.getAlpha(), colorEnd.getAlpha(), t) / 255.0);
            if (alpha <= 0) continue;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            int s = (int) Math.max(1, size);
            int dx = (int) (p.x - s / 2.0);
            int dy = (int) (p.y - s / 2.0);
            if (img != null) {
                // Compensa o eixo Y invertido do mundo (mesma tecnica do SpriteComponent),
                // senao um sprite assimetrico apareceria de cabeca para baixo.
                java.awt.geom.AffineTransform saved = g2d.getTransform();
                g2d.translate(dx, dy + s);
                g2d.scale(1, -1);
                g2d.drawImage(img, 0, 0, s, s, null);
                g2d.setTransform(saved);
            } else {
                g2d.setColor(lerpColor(colorStart, colorEnd, t));
                g2d.fillOval(dx, dy, s, s);
            }
        }
        g2d.setComposite(oldComposite);
        g2d.setColor(oldColor);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Interpolacao RGB (o alpha e tratado a parte via AlphaComposite). */
    private static Color lerpColor(Color a, Color b, double t) {
        int r = (int) lerp(a.getRed(), b.getRed(), t);
        int g = (int) lerp(a.getGreen(), b.getGreen(), t);
        int bl = (int) lerp(a.getBlue(), b.getBlue(), t);
        return new Color(clamp255(r), clamp255(g), clamp255(bl));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ---- Getters/setters ----

    public int getMaxParticles() { return maxParticles; }
    public void setMaxParticles(int v) { this.maxParticles = Math.max(1, v); pool = null; }

    public double getEmissionRate() { return emissionRate; }
    public void setEmissionRate(double v) { this.emissionRate = Math.max(0, v); }

    public double getLifetime() { return lifetime; }
    public void setLifetime(double v) { this.lifetime = Math.max(0.05, v); }

    public double getLifetimeVar() { return lifetimeVar; }
    public void setLifetimeVar(double v) { this.lifetimeVar = Math.max(0, v); }

    public double getVelX() { return velX; }
    public void setVelX(double v) { this.velX = v; }

    public double getVelY() { return velY; }
    public void setVelY(double v) { this.velY = v; }

    public double getVelVar() { return velVar; }
    public void setVelVar(double v) { this.velVar = Math.max(0, v); }

    public double getGravityX() { return gravityX; }
    public void setGravityX(double v) { this.gravityX = v; }

    public double getGravityY() { return gravityY; }
    public void setGravityY(double v) { this.gravityY = v; }

    public double getSizeStart() { return sizeStart; }
    public void setSizeStart(double v) { this.sizeStart = Math.max(0, v); }

    public double getSizeEnd() { return sizeEnd; }
    public void setSizeEnd(double v) { this.sizeEnd = Math.max(0, v); }

    public Color getColorStart() { return colorStart; }
    public void setColorStart(Color c) { if (c != null) this.colorStart = c; }

    public Color getColorEnd() { return colorEnd; }
    public void setColorEnd(Color c) { if (c != null) this.colorEnd = c; }

    public String getParticleSprite() { return particleSprite; }
    public void setParticleSprite(String p) { this.particleSprite = (p != null && p.isEmpty()) ? null : p; }

    public boolean isEmitting() { return emitting; }
    public void setEmitting(boolean e) { this.emitting = e; }

    @Override
    public JSONObject saveProperties() {
        JSONObject p = new JSONObject();
        p.put("maxParticles", maxParticles);
        p.put("emissionRate", emissionRate);
        p.put("lifetime", lifetime);
        p.put("lifetimeVar", lifetimeVar);
        p.put("velX", velX);
        p.put("velY", velY);
        p.put("velVar", velVar);
        p.put("gravityX", gravityX);
        p.put("gravityY", gravityY);
        p.put("sizeStart", sizeStart);
        p.put("sizeEnd", sizeEnd);
        p.put("colorStart", colorStart.getRGB());
        p.put("colorEnd", colorEnd.getRGB());
        p.put("emitting", emitting);
        if (particleSprite != null) p.put("particleSprite", particleSprite);
        return p;
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props == null) return;
        maxParticles = props.optInt("maxParticles", maxParticles);
        emissionRate = props.optDouble("emissionRate", emissionRate);
        lifetime = props.optDouble("lifetime", lifetime);
        lifetimeVar = props.optDouble("lifetimeVar", lifetimeVar);
        velX = props.optDouble("velX", velX);
        velY = props.optDouble("velY", velY);
        velVar = props.optDouble("velVar", velVar);
        gravityX = props.optDouble("gravityX", gravityX);
        gravityY = props.optDouble("gravityY", gravityY);
        sizeStart = props.optDouble("sizeStart", sizeStart);
        sizeEnd = props.optDouble("sizeEnd", sizeEnd);
        if (props.has("colorStart")) colorStart = new Color(props.getInt("colorStart"), true);
        if (props.has("colorEnd")) colorEnd = new Color(props.getInt("colorEnd"), true);
        emitting = props.optBoolean("emitting", emitting);
        if (props.has("particleSprite")) setParticleSprite(props.getString("particleSprite"));
        pool = null; // recria o pool no proximo passo com o novo maxParticles
    }
}
