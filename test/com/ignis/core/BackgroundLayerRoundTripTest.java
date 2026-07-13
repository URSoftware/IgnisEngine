package com.ignis.core;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip da BackgroundLayer (Fase C, parallax): tipo e propriedades de
 * parallax/tiling/cor precisam sobreviver ao save/load da cena. Regressao da
 * fundacao "Scene serializa entity.saveProperties()" — sem ela a camada voltava
 * como GameObject puro, perdendo o comportamento.
 */
class BackgroundLayerRoundTripTest {

    @Test
    void tipoEPropriedadesSobrevivemAoSaveLoad() {
        Scene scene = new Scene("Bg");
        BackgroundLayer bg = new BackgroundLayer();
        bg.setName("Ceu");
        bg.setParallaxX(0.2);
        bg.setParallaxY(0.7);
        bg.setRepeatX(true);
        bg.setRepeatY(false);
        bg.setImagePath("assets/sprites/ceu.png");
        bg.setZIndex(-500);
        scene.addEntity(bg);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Ceu");

        assertNotNull(back, "camada deve sobreviver ao round-trip");
        assertInstanceOf(BackgroundLayer.class, back,
                "deve voltar como BackgroundLayer, nao GameObject puro");
        BackgroundLayer bgBack = (BackgroundLayer) back;
        assertEquals(0.2, bgBack.getParallaxX(), 0.001);
        assertEquals(0.7, bgBack.getParallaxY(), 0.001);
        assertTrue(bgBack.isRepeatX());
        assertFalse(bgBack.isRepeatY());
        assertEquals("assets/sprites/ceu.png", bgBack.getImagePath());
        assertEquals(-500, bgBack.getZIndex());
    }

    @Test
    void fundoNaoEDescartadoPeloCulling() {
        BackgroundLayer bg = new BackgroundLayer();
        assertFalse(bg.isCullable(),
                "fundo preenche a tela toda e nunca deve ser cortado pelo culling");
    }

    @Test
    void gameObjectPuroContinuaCullable() {
        GameObject go = new GameObject("Comum", null, 0, 0, 32, 32);
        assertTrue(go.isCullable(), "objeto comum permanece sujeito ao culling");
    }
}
