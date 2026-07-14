package com.ignis.core.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Nine-slice (Fase D, item 3.10): a composicao 3x3 preserva os cantos sem
 * distorcao, estica bordas/miolo e sobrevive a destinos menores que as margens.
 * Exercita {@link UIImage#renderNineSlice} diretamente (sem GUI).
 */
class NineSliceTest {

    /**
     * Fonte 4x4 com margens de 1px: cada uma das 9 regioes recebe uma cor unica
     * (colunas/linhas de larguras 1,2,1). Assim da para conferir qual regiao-fonte
     * caiu em cada pedaco do destino.
     */
    private BufferedImage nineColorSource() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int[] cols = {0, 1, 3, 4};
        int[] rows = {0, 1, 3, 4};
        Color[][] colors = {
            {new Color(10, 0, 0), new Color(20, 0, 0), new Color(30, 0, 0)},
            {new Color(0, 10, 0), new Color(0, 20, 0), new Color(0, 30, 0)},
            {new Color(0, 0, 10), new Color(0, 0, 20), new Color(0, 0, 30)},
        };
        Graphics2D g = img.createGraphics();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                g.setColor(colors[r][c]);
                g.fillRect(cols[c], rows[r], cols[c + 1] - cols[c], rows[r + 1] - rows[r]);
            }
        }
        g.dispose();
        return img;
    }

    @Test
    void cantosPreservadosBordasEMioloEsticados() {
        BufferedImage src = nineColorSource();
        int dw = 20, dh = 20;
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        UIImage.renderNineSlice(g, src, dw, dh, 1, 1, 1, 1);
        g.dispose();

        // Cantos (dest 1x1) = pixel-fonte do canto correspondente, sem distorcao.
        assertEquals(new Color(10, 0, 0).getRGB(), out.getRGB(0, 0), "canto superior-esquerdo");
        assertEquals(new Color(30, 0, 0).getRGB(), out.getRGB(dw - 1, 0), "canto superior-direito");
        assertEquals(new Color(0, 0, 10).getRGB(), out.getRGB(0, dh - 1), "canto inferior-esquerdo");
        assertEquals(new Color(0, 0, 30).getRGB(), out.getRGB(dw - 1, dh - 1), "canto inferior-direito");

        // Bordas esticadas: topo-centro, meio-esquerda.
        assertEquals(new Color(20, 0, 0).getRGB(), out.getRGB(dw / 2, 0), "borda superior (esticada em X)");
        assertEquals(new Color(0, 10, 0).getRGB(), out.getRGB(0, dh / 2), "borda esquerda (esticada em Y)");

        // Miolo esticado nos dois eixos.
        assertEquals(new Color(0, 20, 0).getRGB(), out.getRGB(dw / 2, dh / 2), "miolo (esticado em X e Y)");
    }

    @Test
    void destinoMenorQueMargensNaoQuebra() {
        BufferedImage src = nineColorSource();
        // Destino 1x1: as margens (1+1) nao cabem; o guard proporcional deve reduzi-las
        // sem lancar excecao nem inverter fatias.
        BufferedImage out = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        assertDoesNotThrow(() -> UIImage.renderNineSlice(g, src, 1, 1, 1, 1, 1, 1));
        g.dispose();
    }

    @Test
    void margensMaioresQueImagemSaoLimitadas() {
        BufferedImage src = nineColorSource(); // 4x4
        BufferedImage out = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // Margens absurdas (100px) numa imagem 4x4: clamp evita cantos sobrepostos.
        assertDoesNotThrow(() -> UIImage.renderNineSlice(g, src, 30, 30, 100, 100, 100, 100));
        g.dispose();
        // Ainda desenha algo visivel (nao ficou tudo transparente).
        int painted = 0;
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                if ((out.getRGB(x, y) >>> 24) > 0) painted++;
            }
        }
        assertEquals(900, painted, "toda a area de destino coberta mesmo com margens exageradas");
    }

    @Test
    void roundTripDasMargensNaCena() {
        UIImage img = new UIImage();
        img.setScaleMode(UIImage.ScaleMode.NINE_SLICE);
        img.setSlices(4, 6, 8, 10);
        UIImage back = UIImage.fromJSON(img.toJSON());
        assertEquals(UIImage.ScaleMode.NINE_SLICE, back.getScaleMode());
        assertEquals(4, back.getSliceLeft());
        assertEquals(6, back.getSliceRight());
        assertEquals(8, back.getSliceTop());
        assertEquals(10, back.getSliceBottom());
    }
}
