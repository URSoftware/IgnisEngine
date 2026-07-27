package com.ignis.core;

import org.json.JSONObject;

/**
 * Suíte de testes unitários para o Raycaster2DComponent (Grounded Check, Layer Mask e vetores).
 */
public class Raycaster2DComponentTest {

    public static void main(String[] args) {
        IgnisLogger.info("=== Testando Raycaster2DComponent ===");

        try {
            Game game = new Game();

            // 1. Instanciação e raio direcionado para baixo (Grounded Check)
            GameObject player = new GameObject("Player", game, 100, 100, 32, 32);
            Raycaster2DComponent raycaster = new Raycaster2DComponent(0.0f, 1.0f, 100.0f); // Apontando para baixo
            raycaster.setLayerMask("Ground");
            player.addComponent(raycaster);
            game.addEntity(player);

            // Objeto de chão posicionado logo abaixo (y = 150) com ColliderComponent
            GameObject ground = new GameObject("GroundBlock", game, 100, 150, 100, 20);
            ground.setLayer("Ground");
            ColliderComponent groundCollider = new ColliderComponent();
            groundCollider.setCollisionLayer("Ground");
            ground.addComponent(groundCollider);
            game.addEntity(ground);

            // Atualiza colisor e dispara raio
            groundCollider.awake();
            IgnisSampleCollisions.RaycastResult hit = raycaster.castRay();

            IgnisLogger.info("Raycast atingiu chão? " + hit.hit + " | Distância: " + hit.distance + "px");
            assert hit.hit : "Raycaster2DComponent deveria ter detectado o bloco de chão";
            assert raycaster.isGrounded() : "isGrounded() deveria retornar true";
            assert raycaster.getHitObject() == ground : "Objeto atingido deveria ser o GroundBlock";
            assert hit.distance > 0 && hit.distance <= 60 : "Distância até o chão incorreta";

            // 2. Teste de Filtragem por Layer Mask
            raycaster.setLayerMask("Enemies"); // Muda a máscara para procurar apenas inimigos
            IgnisSampleCollisions.RaycastResult missed = raycaster.castRay();
            assert !missed.hit : "Raycast não deveria colidir com objeto em camada diferente da Layer Mask";

            // 3. Teste de Rotação Vetorial (useGameObjectRotation)
            raycaster.setLayerMask("*"); // Aceita qualquer camada
            raycaster.setDirectionX(1.0f); // Vetor apontando para a direita (1, 0)
            raycaster.setDirectionY(0.0f);
            player.setRotation(90.0); // Rotação de 90 graus rotaciona o raio (1,0) para baixo (0,1)

            IgnisSampleCollisions.RaycastResult rotatedHit = raycaster.castRay();
            assert rotatedHit.hit : "Raio rotacionado em 90 graus deveria atingir o chão posicionado abaixo";

            // 4. Teste de Serialização @Serialize (JSON)
            JSONObject savedProps = raycaster.saveProperties();
            assert Float.compare((float) savedProps.optDouble("distance"), 100.0f) == 0 : "Falha ao serializar distance";
            assert "*".equals(savedProps.optString("layerMask")) : "Falha ao serializar layerMask";
            assert savedProps.optBoolean("useGameObjectRotation") : "Falha ao serializar useGameObjectRotation";

            Raycaster2DComponent loadedRaycaster = new Raycaster2DComponent();
            loadedRaycaster.loadProperties(savedProps, null);
            assert Float.compare(loadedRaycaster.getDistance(), 100.0f) == 0 : "Falha ao desserializar distance";
            assert "*".equals(loadedRaycaster.getLayerMask()) : "Falha ao desserializar layerMask";

            IgnisLogger.info("=== Todos os testes do Raycaster2DComponent passaram com sucesso! ===");

        } catch (Exception e) {
            IgnisLogger.error("Falha no teste do Raycaster2DComponent:", e);
            System.exit(1);
        }
    }
}
