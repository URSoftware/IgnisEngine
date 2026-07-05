package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de tags e camadas do {@link GameObject}: defaults, hasTag (case-insensitive)
 * e round-trip de serializacao pela {@link Scene}. (A busca por tag no {@link Game}
 * nao e testada aqui porque construir Game exige AWT nao-headless.)
 */
class TagLayerTest {

    @Test
    void defaultsSaoVaziosEDefault() {
        GameObject go = new GameObject();
        assertEquals("", go.getTag());
        assertEquals("Default", go.getLayer());
    }

    @Test
    void hasTagIgnoraCaixaEVazioNormaliza() {
        GameObject go = new GameObject();
        go.setTag("Player");
        assertTrue(go.hasTag("player"));
        assertTrue(go.hasTag("PLAYER"));
        assertFalse(go.hasTag("enemy"));

        go.setTag(null);
        assertEquals("", go.getTag());
        go.setLayer(null);
        assertEquals("Default", go.getLayer(), "camada nula/vazia volta para Default");
    }

    @Test
    void serializacaoDeTagELayerNaScene() {
        Scene scene = new Scene("S");
        GameObject go = new GameObject();
        go.setName("Hero");
        go.setTag("Player");
        go.setLayer("Foreground");
        scene.addEntity(go);

        JSONObject json = scene.toJSON();
        Scene loaded = Scene.fromJSON(json, null);
        GameObject restored = loaded.getEntities().get(0);

        assertEquals("Player", restored.getTag());
        assertEquals("Foreground", restored.getLayer());
    }
}
