package com.ignis.core;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip da cena (toJSON -> fromJSON): NADA anexado a uma entidade pode se
 * perder ao salvar. Este é o teste de regressão do bug que apagou os scripts e
 * a textura do MyGame ao salvar (instâncias fora de components não eram
 * serializadas — plano de consertos, item 2.1).
 */
class SceneRoundTripTest {

    /** Script de usuário mínimo com variável serializável. */
    static class StubScript extends IgnisScript {
        @Serialize
        public int velocidade = 7;
    }

    private static GameObject entidadeBase(String name) {
        GameObject go = new GameObject(name, null, 10, 20, 64, 32);
        return go;
    }

    @Test
    void scriptAnexadoSobreviveAoSaveLoad() {
        Scene scene = new Scene("TestScene");
        GameObject go = entidadeBase("Player");
        StubScript script = new StubScript();
        script.velocidade = 42;
        go.addComponent(script);
        scene.addEntity(go);

        JSONObject json = scene.toJSON();
        Scene loaded = Scene.fromJSON(json, null);

        GameObject back = loaded.findEntityByName("Player");
        assertNotNull(back, "entidade deve sobreviver ao round-trip");
        assertTrue(back.getScriptNames().contains("StubScript"),
                "ANEXO do script deve sobreviver ao save/load (bug da perda de scripts)");
        assertTrue(loaded.hasPendingVariables(back, "StubScript"),
                "variaveis @Serialize devem ficar pendentes para aplicar na instanciacao");
    }

    @Test
    void anexoSemInstanciaViva_naoSomeDoJson() {
        // Cenario: compilacao do script falhou — o objeto tem o NOME anexado mas
        // nenhuma instancia em components. O vinculo nao pode sumir ao salvar.
        Scene scene = new Scene("TestScene");
        GameObject go = entidadeBase("Inimigo");
        go.getScriptNames().add("ScriptSemCompilar");
        scene.addEntity(go);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);

        GameObject back = loaded.findEntityByName("Inimigo");
        assertNotNull(back);
        assertTrue(back.getScriptNames().contains("ScriptSemCompilar"),
                "anexo sem instancia viva deve ser preservado pelo save");
    }

    @Test
    void spriteComponentComTexturaSobreviveAoSaveLoad() {
        Scene scene = new Scene("TestScene");
        GameObject go = entidadeBase("Background");
        SpriteComponent sprite = new SpriteComponent(new Texture2D("assets/sprites/grass.jpg"));
        sprite.setShapeType("None");
        go.addComponent(sprite);
        scene.addEntity(go);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);

        GameObject back = loaded.findEntityByName("Background");
        assertNotNull(back);
        SpriteComponent backSprite = back.getComponent(SpriteComponent.class);
        assertNotNull(backSprite, "SpriteComponent deve sobreviver ao round-trip");
        assertNotNull(backSprite.getTexture(), "textura deve sobreviver ao round-trip");
        assertEquals("assets/sprites/grass.jpg", backSprite.getTexture().getPath(),
                "caminho da textura deve ser preservado (bug da grass.jpg sumida)");
    }

    @Test
    void transformEPropriedadesBasicasSobrevivem() {
        Scene scene = new Scene("TestScene");
        GameObject go = entidadeBase("Caixa");
        go.setRotation(45);
        go.setZIndex(3);
        go.setVisible(false);
        scene.addEntity(go);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Caixa");

        assertNotNull(back);
        assertEquals(10, back.getX(), 0.001);
        assertEquals(20, back.getY(), 0.001);
        assertEquals(64, back.getWidth());
        assertEquals(32, back.getHeight());
        assertEquals(45, back.getRotation(), 0.001);
        assertEquals(3, back.getZIndex());
        assertEquals(false, back.isVisible());
    }

    @Test
    void colliderEHealthComponentsSobrevivem() {
        Scene scene = new Scene("TestScene");
        GameObject go = entidadeBase("Tanque");
        ColliderComponent collider = new ColliderComponent();
        collider.setShape("Sphere");
        collider.setFriction(0.8);
        collider.setTrigger(true);
        HealthComponent health = new HealthComponent();
        health.setHealth(55);
        go.addComponent(collider);
        go.addComponent(health);
        scene.addEntity(go);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Tanque");

        assertNotNull(back);
        ColliderComponent backCollider = back.getComponent(ColliderComponent.class);
        HealthComponent backHealth = back.getComponent(HealthComponent.class);
        assertNotNull(backCollider);
        assertNotNull(backHealth);
        assertEquals("Sphere", backCollider.getShape());
        assertEquals(0.8, backCollider.getFriction(), 0.001);
        assertTrue(backCollider.isTrigger());
        assertEquals(55, backHealth.getHealth());
    }
}
