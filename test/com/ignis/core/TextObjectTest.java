package com.ignis.core;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Texto no mundo (Fase D, item 3.9): estilo, dimensionamento pelas metricas da
 * fonte e round-trip da cena. Sem GUI — exercita o modelo e a serializacao.
 */
class TextObjectTest {

    @Test
    void larguraAlturaDerivamDoTextoEFonte() {
        TextObject t = new TextObject();
        t.setFontSize(20);
        t.setText("A");
        int w1 = t.getWidth();
        int h1 = t.getHeight();
        assertTrue(w1 > 0 && h1 > 0, "caixa dimensionada pela fonte");

        // Texto mais largo aumenta a largura; segunda linha aumenta a altura.
        t.setText("AAAAAAAAAA");
        assertTrue(t.getWidth() > w1, "texto mais longo alarga a caixa");

        t.setText("linha1\nlinha2");
        assertTrue(t.getHeight() > h1, "duas linhas aumentam a altura");
    }

    @Test
    void fonteMaiorAumentaAltura() {
        TextObject small = new TextObject();
        small.setText("Ig");
        small.setFontSize(12);
        int hSmall = small.getHeight();

        TextObject big = new TextObject();
        big.setText("Ig");
        big.setFontSize(48);
        assertTrue(big.getHeight() > hSmall, "fonte maior => caixa mais alta");
    }

    @Test
    void estiloSobreviveAoRoundTrip() {
        Scene scene = new Scene("T");
        TextObject t = new TextObject();
        t.setName("Placa");
        t.setText("Fim de Fase\nParabéns");
        t.setFontFamily("Serif");
        t.setFontSize(36);
        t.setColor(new Color(0x80, 0xFF, 0x40, 0xC0));
        t.setBold(true);
        t.setItalic(true);
        t.setAlign(TextObject.TextAlign.CENTER);
        t.setX(120);
        t.setY(240);
        scene.addEntity(t);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Placa");
        assertNotNull(back);
        assertInstanceOf(TextObject.class, back);
        TextObject b = (TextObject) back;
        assertEquals("Fim de Fase\nParabéns", b.getText());
        assertEquals("Serif", b.getFontFamily());
        assertEquals(36, b.getFontSize());
        assertEquals(new Color(0x80, 0xFF, 0x40, 0xC0).getRGB(), b.getColor().getRGB());
        assertTrue(b.isBold());
        assertTrue(b.isItalic());
        assertEquals(TextObject.TextAlign.CENTER, b.getAlign());
        assertEquals(120, b.getX(), 0.001);
        assertEquals(240, b.getY(), 0.001);
    }

    @Test
    void alinhamentoInvalidoCaiParaLeft() {
        TextObject t = new TextObject();
        org.json.JSONObject props = t.saveProperties();
        props.put("align", "DIAGONAL"); // valor invalido
        t.loadProperties(props);
        assertEquals(TextObject.TextAlign.LEFT, t.getAlign());
    }

    @Test
    void textoNaoECullavelPorPadraoNao() {
        // Texto tem caixa finita: participa do culling por AABB como um objeto comum.
        assertTrue(new TextObject().isCullable());
    }

    @Test
    void renderDesenhaInkVisivelAncoradaNaCaixa() {
        // Valida a saida de render (o que o teste de modelo nao pega): o texto
        // produz pixels visiveis e o flip do eixo Y (mesma tecnica do Square/Tilemap,
        // translate + scale(1,-1)) ancora a tinta na banda [0..height] da caixa —
        // baseline embaixo, confirmando que nao desenha fora/invertido para cima.
        TextObject t = new TextObject();
        t.setColor(java.awt.Color.WHITE);
        t.setFontSize(40);
        t.setText("Ig");
        t.setX(0);
        t.setY(0);
        int w = t.getWidth();
        int h = t.getHeight();
        int margin = 4;
        java.awt.image.BufferedImage out =
                new java.awt.image.BufferedImage(w + margin, h + margin,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        t.render(g);
        g.dispose();

        int ink = 0;
        int minRow = Integer.MAX_VALUE, maxRow = -1;
        for (int yy = 0; yy < out.getHeight(); yy++) {
            for (int xx = 0; xx < out.getWidth(); xx++) {
                if ((out.getRGB(xx, yy) >>> 24) > 0) {
                    ink++;
                    minRow = Math.min(minRow, yy);
                    maxRow = Math.max(maxRow, yy);
                }
            }
        }
        assertTrue(ink > 0, "texto deve desenhar pixels visiveis");
        assertTrue(minRow >= 0 && maxRow <= h,
                "tinta confinada a banda vertical da caixa [0.." + h + "], sem vazar acima/abaixo");
        // Baseline embaixo apos o flip: a tinta se concentra na metade inferior da caixa.
        assertTrue(maxRow > h / 2, "tinta ancorada na parte de baixo da caixa (baseline)");
    }
}
