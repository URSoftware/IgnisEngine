package com.ignis.editor.fx;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;

/**
 * Ponte de imagem AWT -> JavaFX desacoplada do toolkit Swing (Fase F4-C).
 *
 * <p>Substitui {@code javafx.embed.swing.SwingFXUtils.toFXImage}, eliminando a
 * dependencia do modulo {@code javafx-swing} — pre-requisito para remove-lo do
 * pom e fechar a migracao da UI. A conversao usa apenas {@link PixelWriter} +
 * {@link PixelFormat}, disponiveis em {@code javafx-graphics}.
 *
 * <p>Caminho rapido (sem copia intermediaria) quando o {@link BufferedImage} e
 * {@code TYPE_INT_ARGB} com scanline contiguo (caso do buffer do viewport, gerado
 * por frame): le o array {@link DataBufferInt} direto. Caso contrario, recorre a
 * {@link BufferedImage#getRGB} (universal, cobre sprites carregados via ImageIO).
 */
public final class FxImageBridge {

    private FxImageBridge() {}

    /**
     * Converte {@code src} para uma {@link WritableImage}, reutilizando {@code dest}
     * se as dimensoes coincidirem (evita realocar por frame). Passe {@code null} em
     * {@code dest} para criar uma nova. Retorna a imagem destino (nunca {@code null}
     * se {@code src} nao for nulo).
     */
    public static WritableImage toFXImage(BufferedImage src, WritableImage dest) {
        if (src == null) return dest;
        int w = src.getWidth();
        int h = src.getHeight();
        if (dest == null || (int) dest.getWidth() != w || (int) dest.getHeight() != h) {
            dest = new WritableImage(w, h);
        }
        PixelWriter pw = dest.getPixelWriter();
        pw.setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), extractArgb(src, w, h), 0, w);
        return dest;
    }

    // Retorna os pixels de src como int[] ARGB nao-premultiplicado, com stride == w.
    private static int[] extractArgb(BufferedImage src, int w, int h) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            Raster raster = src.getRaster();
            if (raster.getDataBuffer() instanceof DataBufferInt
                    && raster.getSampleModel() instanceof SinglePixelPackedSampleModel
                    && ((SinglePixelPackedSampleModel) raster.getSampleModel()).getScanlineStride() == w) {
                // Acesso direto, sem copia: setPixels apenas le este array.
                return ((DataBufferInt) raster.getDataBuffer()).getData();
            }
        }
        // Universal: normaliza qualquer tipo/raster para ARGB contiguo.
        int[] argb = new int[w * h];
        src.getRGB(0, 0, w, h, argb, 0, w);
        return argb;
    }
}
