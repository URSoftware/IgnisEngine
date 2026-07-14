package com.ignis.builder;

import org.junit.jupiter.api.Test;

import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuracao de build/janela do jogo exportado (Fase E, item 3.13): round-trip
 * das opcoes de janela e do limite de FPS, mais os defaults e o clamp.
 */
class BuildConfigTest {

    @Test
    void defaultsDaJanelaEDoFps() {
        BuildConfig c = new BuildConfig();
        assertEquals(1280, c.getWidth());
        assertEquals(720, c.getHeight());
        assertFalse(c.isFullscreen());
        assertTrue(c.isResizable(), "janela redimensionavel por padrao");
        assertEquals(60, c.getFpsCap(), "limite de FPS default = comportamento historico");
    }

    @Test
    void fpsCapNegativoViraSemLimite() {
        BuildConfig c = new BuildConfig();
        c.setFpsCap(-5);
        assertEquals(0, c.getFpsCap(), "valor negativo e normalizado para 0 (sem limite)");
    }

    @Test
    void opcoesDeJanelaSobrevivemAoRoundTrip() {
        BuildConfig c = new BuildConfig();
        c.setGameName("Meu Jogo");
        c.setWidth(1920);
        c.setHeight(1080);
        c.setFullscreen(true);
        c.setResizable(false);
        c.setFpsCap(144);

        BuildConfig back = BuildConfig.fromJSON(c.toJSON());
        assertEquals("Meu Jogo", back.getGameName());
        assertEquals(1920, back.getWidth());
        assertEquals(1080, back.getHeight());
        assertTrue(back.isFullscreen());
        assertFalse(back.isResizable());
        assertEquals(144, back.getFpsCap());
    }

    @Test
    void buildJsonAntigoSemOsCamposNovosUsaOsDefaults() {
        // Compatibilidade retroativa: um build.json gerado por uma versao anterior
        // (sem resizable/fpsCap) precisa carregar sem quebrar, assumindo os defaults.
        JSONObject legado = new JSONObject();
        legado.put("gameName", "Antigo");
        legado.put("width", 800);
        legado.put("height", 600);
        legado.put("fullscreen", false);

        BuildConfig c = BuildConfig.fromJSON(legado);
        assertEquals("Antigo", c.getGameName());
        assertEquals(800, c.getWidth());
        assertTrue(c.isResizable(), "campo ausente cai no default");
        assertEquals(60, c.getFpsCap(), "campo ausente cai no default");
    }

    @Test
    void fpsCapZeroSignificaSemLimite() {
        BuildConfig c = new BuildConfig();
        c.setFpsCap(0);
        assertEquals(0, BuildConfig.fromJSON(c.toJSON()).getFpsCap(),
                "0 (sem limite) sobrevive ao round-trip e nao vira o default");
    }
}
