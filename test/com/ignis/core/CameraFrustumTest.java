package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes do retangulo de captura da camera ({@link Camera#getFrustumWorldRect}),
 * base do visualizador de campo de visao do editor.
 */
class CameraFrustumTest {

    private static final double EPS = 0.0001;

    @Test
    void frustumCentradoNaCameraComZoom1() {
        Camera cam = new Camera();
        cam.setPosition(100, 50);
        cam.setZoom(1.0);

        double[] r = cam.getFrustumWorldRect(800, 600);
        assertEquals(100 - 400, r[0], EPS, "minX = centroX - largura/2");
        assertEquals(50 - 300, r[1], EPS, "minY = centroY - altura/2");
        assertEquals(800, r[2], EPS, "largura = design / zoom");
        assertEquals(600, r[3], EPS, "altura = design / zoom");
    }

    @Test
    void zoomEncolheAreaCapturada() {
        Camera cam = new Camera();
        cam.setPosition(0, 0);
        cam.setZoom(2.0);

        double[] r = cam.getFrustumWorldRect(800, 600);
        assertEquals(400, r[2], EPS, "zoom 2 => metade da largura capturada");
        assertEquals(300, r[3], EPS, "zoom 2 => metade da altura capturada");
        assertEquals(-200, r[0], EPS);
        assertEquals(-150, r[1], EPS);
    }
}
