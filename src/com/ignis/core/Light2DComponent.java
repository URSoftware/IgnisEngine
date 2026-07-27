package com.ignis.core;

import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Componente nativo de Iluminação Dinâmica 2D do IgnisEngine.
 * Suporta luzes Point (pontual), Spot (foco em cone) e Global (ambiente).
 * Permite alteração dinâmica do Tint nos SpriteComponents atingidos e projeção
 * de sombras com oclusão por ColliderComponents.
 */
public class Light2DComponent extends Component {

    @Serialize
    private String lightType = "Point"; // "Point", "Spot", "Global"

    @Serialize
    private float intensity = 1.0f;

    @Serialize
    private String colorHex = "#FFF0C8"; // Tom de iluminação hexadecimal

    @Serialize
    private float radius = 200.0f; // Alcance em pixels para Point e Spot

    @Serialize
    private float spotAngle = 45.0f; // Ângulo do cone em graus para Spot

    @Serialize
    private boolean castsShadows = false; // Se true, colliders bloqueiam a passagem da luz

    @Serialize
    private boolean enabled = true;

    // Cache transiente da cor parseada do Hex
    private transient Color parsedColor = new Color(255, 240, 200);

    public Light2DComponent() {
    }

    public Light2DComponent(String lightType, float intensity, String colorHex, float radius) {
        this.lightType = lightType;
        this.intensity = intensity;
        this.colorHex = colorHex;
        this.radius = radius;
        updateParsedColor();
    }

    @Override
    public void awake() {
        updateParsedColor();
    }

    @Override
    public void start() {
        updateParsedColor();
    }

    @Override
    public void update(float deltaTime) {
        if (!enabled || gameObject == null || gameObject.getGame() == null) {
            return;
        }

        updateParsedColor();
        Game game = gameObject.getGame();

        // Se for luz Global, atualiza a luz ambiente do mundo
        if ("Global".equalsIgnoreCase(lightType)) {
            int alpha = (int) Math.max(0, Math.min(255, (1.0f - Math.min(1.0f, intensity)) * 200));
            Color ambient = new Color(parsedColor.getRed(), parsedColor.getGreen(), parsedColor.getBlue(), alpha);
            game.setAmbientLight(ambient);
            return;
        }

        // Iluminação dinâmica de sprites (Point / Spot)
        double lx = gameObject.getX();
        double ly = gameObject.getY();
        double lightAngleRad = Math.toRadians(gameObject.getRotation());

        List<GameObject> entities = game.getEntities();
        for (GameObject target : entities) {
            if (target == null || target == gameObject || !target.isVisible()) {
                continue;
            }

            SpriteComponent sprite = target.getComponent(SpriteComponent.class);
            if (sprite == null) {
                continue;
            }

            double tx = target.getX();
            double ty = target.getY();

            double dx = tx - lx;
            double dy = ty - ly;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > radius) {
                continue; // Fora do alcance da luz
            }

            // Para Spot light, verifica se o alvo está dentro do cone de abertura
            if ("Spot".equalsIgnoreCase(lightType)) {
                double angleToTarget = Math.atan2(dy, dx);
                double angleDiff = Math.toDegrees(normalizeAngle(angleToTarget - lightAngleRad));
                if (Math.abs(angleDiff) > (spotAngle / 2.0f)) {
                    continue; // Fora do cone do spot
                }
            }

            // Projeção de Sombras: Verifica se há algum ColliderComponent bloqueando o raio
            if (castsShadows && isLightRayBlocked(game, lx, ly, tx, ty, target)) {
                continue; // Obstruído por sombra de colisor
            }

            // Fator de atenuação por distância (1.0 no centro, 0.0 na borda)
            float attenuation = 1.0f - (float) (dist / Math.max(1.0, radius));
            float factor = Math.max(0.0f, Math.min(1.0f, attenuation * intensity));

            // Aplica tinting dinâmico no SpriteComponent do objeto atingido
            Color originalTint = sprite.getTint();
            Color blended = blendColors(originalTint != null ? originalTint : Color.WHITE, parsedColor, factor);
            sprite.setTint(blended);
        }
    }

    /**
     * Verifica se o segmento de reta entre a luz (lx, ly) e o alvo (tx, ty) intercepta a hitbox de algum colisor.
     */
    public boolean isLightRayBlocked(Game game, double lx, double ly, double tx, double ty, GameObject target) {
        Line2D.Double ray = new Line2D.Double(lx, ly, tx, ty);
        for (GameObject obstacle : game.getEntities()) {
            if (obstacle == null || obstacle == gameObject || obstacle == target || !obstacle.isVisible()) {
                continue;
            }
            ColliderComponent collider = obstacle.getComponent(ColliderComponent.class);
            if (collider != null && collider.isEnabled()) {
                double[] b = collider.getWorldBounds();
                Rectangle2D.Double rect = new Rectangle2D.Double(b[0], b[1], b[2] - b[0], b[3] - b[1]);
                if (ray.intersects(rect)) {
                    return true; // Raio de luz obstruído
                }
            }
        }
        return false;
    }

    private static double normalizeAngle(double angleRad) {
        while (angleRad > Math.PI) angleRad -= 2 * Math.PI;
        while (angleRad < -Math.PI) angleRad += 2 * Math.PI;
        return angleRad;
    }

    private Color blendColors(Color base, Color light, float factor) {
        int r = (int) Math.min(255, base.getRed() + (light.getRed() - base.getRed()) * factor);
        int g = (int) Math.min(255, base.getGreen() + (light.getGreen() - base.getGreen()) * factor);
        int b = (int) Math.min(255, base.getBlue() + (light.getBlue() - base.getBlue()) * factor);
        return new Color(r, g, b, base.getAlpha());
    }

    private void updateParsedColor() {
        if (colorHex != null && !colorHex.trim().isEmpty()) {
            try {
                String hex = colorHex.trim();
                if (!hex.startsWith("#")) hex = "#" + hex;
                parsedColor = Color.decode(hex);
            } catch (Exception e) {
                parsedColor = Color.WHITE;
            }
        }
    }

    // Getters e Setters
    public String getLightType() { return lightType; }
    public void setLightType(String lightType) { this.lightType = lightType; }

    public float getIntensity() { return intensity; }
    public void setIntensity(float intensity) { this.intensity = Math.max(0.0f, Math.min(10.0f, intensity)); }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; updateParsedColor(); }

    public Color getColor() { return parsedColor; }
    public void setColor(Color color) {
        if (color != null) {
            this.parsedColor = color;
            this.colorHex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
        }
    }

    public float getRadius() { return radius; }
    public void setRadius(float radius) { this.radius = Math.max(1.0f, radius); }

    public float getSpotAngle() { return spotAngle; }
    public void setSpotAngle(float spotAngle) { this.spotAngle = Math.max(1.0f, Math.min(360.0f, spotAngle)); }

    public boolean isCastsShadows() { return castsShadows; }
    public void setCastsShadows(boolean castsShadows) { this.castsShadows = castsShadows; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
