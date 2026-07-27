package com.ignis.core;

import org.json.JSONObject;
import java.awt.Color;

/**
 * Suíte de testes unitários para a validação do Light2DComponent (Point, Spot, Global, sombras e tinting de SpriteComponent).
 */
public class Light2DComponentTest {

    public static void main(String[] args) {
        IgnisLogger.info("=== Testando Light2DComponent ===");

        try {
            // Setup de jogo simulado
            Game game = new Game();
            
            // 1. Instanciação e anexação do Light2DComponent (Luz Pontual)
            GameObject lightObject = new GameObject("PointLight", game, 100, 100, 16, 16);
            Light2DComponent pointLight = new Light2DComponent("Point", 1.0f, "#FF0000", 200.0f); // Luz Vermelha
            lightObject.addComponent(pointLight);
            game.addEntity(lightObject);

            // Objeto com SpriteComponent no alcance da luz (distância = 100px)
            GameObject spriteObject = new GameObject("PlayerSprite", game, 200, 100, 32, 32);
            SpriteComponent sprite = new SpriteComponent();
            sprite.setTint(new Color(255, 255, 255)); // Tint inicial branco
            spriteObject.addComponent(sprite);
            game.addEntity(spriteObject);

            // Executa um tick de update para calcular o tinting dinâmico
            pointLight.update(0.1f);

            Color tinted = sprite.getTint();
            IgnisLogger.info("Cor do Sprite após iluminação Point: R=" + tinted.getRed() + ", G=" + tinted.getGreen() + ", B=" + tinted.getBlue());
            assert tinted.getRed() > tinted.getGreen() : "Sprite deveria ter o tom avermelhado da luz Point";

            // 2. Teste de Spot Light (Foco em Cone)
            pointLight.setLightType("Spot");
            pointLight.setSpotAngle(40.0f); // Cone de 40 graus
            lightObject.setRotation(0.0); // Apontando para a direita (0 rad)

            // Objeto dentro do cone (dx=100, dy=0 => ângulo 0 graus)
            pointLight.update(0.1f);
            Color inSpotTint = sprite.getTint();
            assert inSpotTint.getRed() > 150 : "Objeto dentro do cone do Spot deveria ser iluminado";

            // Reposiciona o objeto fora do cone de 40 graus (dx=50, dy=100 => ângulo ~63 graus)
            spriteObject.setY(200);
            sprite.setTint(new Color(255, 255, 255)); // Reset tint
            pointLight.update(0.1f);
            Color outSpotTint = sprite.getTint();
            assert outSpotTint.getRed() == 255 && outSpotTint.getGreen() == 255 : "Objeto fora do cone não deve ser afetado pelo Spot";

            // 3. Teste de Projeção de Sombras (Casts Shadows)
            spriteObject.setY(100); // Volta à linha da luz
            pointLight.setLightType("Point");
            pointLight.setCastsShadows(true);

            // Adiciona uma parede/obstáculo com ColliderComponent entre a luz (100,100) e o sprite (200,100)
            GameObject wall = new GameObject("WallObstacle", game, 150, 100, 20, 100);
            ColliderComponent wallCollider = new ColliderComponent();
            wall.addComponent(wallCollider);
            game.addEntity(wall);

            sprite.setTint(new Color(255, 255, 255));
            pointLight.update(0.1f);

            Color shadowedTint = sprite.getTint();
            assert shadowedTint.getRed() == 255 && shadowedTint.getGreen() == 255 : "Luz deveria ser bloqueada pela sombra do collider da parede";

            // 4. Teste de Serialização @Serialize (JSON)
            JSONObject savedProps = pointLight.saveProperties();
            assert "Point".equals(savedProps.optString("lightType")) : "Falha ao serializar lightType";
            assert Float.compare((float) savedProps.optDouble("radius"), 200.0f) == 0 : "Falha ao serializar radius";
            assert savedProps.optBoolean("castsShadows") : "Falha ao serializar castsShadows";

            Light2DComponent loadedLight = new Light2DComponent();
            loadedLight.loadProperties(savedProps, null);
            assert "Point".equals(loadedLight.getLightType()) : "Falha ao desserializar lightType";
            assert loadedLight.isCastsShadows() : "Falha ao desserializar castsShadows";

            IgnisLogger.info("=== Todos os testes do Light2DComponent passaram com sucesso! ===");

        } catch (Exception e) {
            IgnisLogger.error("Falha no teste do Light2DComponent:", e);
            System.exit(1);
        }
    }
}
