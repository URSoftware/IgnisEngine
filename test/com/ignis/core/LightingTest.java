package com.ignis.core;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iluminacao 2D (Fase D, item 3.11): a mascara de escuridao, o recorte das luzes
 * via DstOut e o round-trip de luz/luz-ambiente pela cena. Sem GUI — exercita a
 * composicao ({@link LightObject#composeMask}) e a serializacao.
 */
class LightingTest {

    private LightObject lightAt(double x, double y, double radius, double intensity) {
        LightObject l = new LightObject();
        l.setX(x);
        l.setY(y);
        l.setRadius(radius);
        l.setIntensity(intensity);
        return l;
    }

    @Test
    void luzRemoveEscuridaoNoCentroEPreservaNasBordas() {
        Color ambient = new Color(0, 0, 20, 200); // escuridao azulada, alpha 200
        LightObject light = lightAt(50, 50, 40, 1.0);
        BufferedImage mask = LightObject.composeMask(null, 100, 100, ambient, List.of(light), null);

        int centerAlpha = (mask.getRGB(50, 50) >>> 24) & 0xFF;
        int cornerAlpha = (mask.getRGB(2, 2) >>> 24) & 0xFF;
        assertTrue(centerAlpha < cornerAlpha,
                "centro da luz (intensidade 1) deve ficar quase sem escuridao");
        assertEquals(200, cornerAlpha, "canto fora do raio mantem a escuridao ambiente");
    }

    @Test
    void intensidadeParcialRemoveParcialmente() {
        Color ambient = new Color(0, 0, 0, 200);
        LightObject light = lightAt(50, 50, 40, 0.5);
        BufferedImage mask = LightObject.composeMask(null, 100, 100, ambient, List.of(light), null);
        int centerAlpha = (mask.getRGB(50, 50) >>> 24) & 0xFF;
        // DstOut com src alpha 0.5: dest_alpha *= (1 - 0.5) => ~100.
        assertTrue(centerAlpha > 60 && centerAlpha < 140,
                "intensidade 0.5 remove ~metade da escuridao no centro (obtido: " + centerAlpha + ")");
    }

    @Test
    void bufferReaproveitadoQuandoTamanhoBate() {
        Color ambient = new Color(0, 0, 0, 200);
        BufferedImage first = LightObject.composeMask(null, 64, 64, ambient, List.of(), null);
        BufferedImage second = LightObject.composeMask(first, 64, 64, ambient, List.of(), null);
        assertTrue(first == second, "mesmo tamanho reaproveita o buffer (sem realocar)");
        BufferedImage third = LightObject.composeMask(first, 80, 80, ambient, List.of(), null);
        assertTrue(third != first, "tamanho diferente realoca");
    }

    @Test
    void luzERoundTripDaCena() {
        Scene scene = new Scene("Noite");
        LightObject light = new LightObject();
        light.setName("Tocha");
        light.setX(300);
        light.setY(150);
        light.setLightColor(new Color(255, 80, 40));
        light.setRadius(220);
        light.setIntensity(0.75);
        scene.addEntity(light);
        scene.setAmbientLight(new Color(5, 5, 16, 224));

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Tocha");
        assertNotNull(back);
        assertInstanceOf(LightObject.class, back);
        LightObject b = (LightObject) back;
        assertEquals(new Color(255, 80, 40).getRGB(), b.getLightColor().getRGB());
        assertEquals(220, b.getRadius(), 0.001);
        assertEquals(0.75, b.getIntensity(), 0.001);
        assertNotNull(loaded.getAmbientLight());
        assertEquals(new Color(5, 5, 16, 224).getRGB(), loaded.getAmbientLight().getRGB());
    }

    @Test
    void cenaSemAmbientLightNaoSerializaCampo() {
        Scene scene = new Scene("Dia");
        scene.addEntity(lightAt(0, 0, 100, 1.0));
        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        assertNull(loaded.getAmbientLight(), "cena sem luz ambiente permanece sem escuridao");
    }
}
