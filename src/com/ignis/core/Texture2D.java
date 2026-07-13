package com.ignis.core;

import java.awt.image.BufferedImage;

/**
 * Representa uma textura 2D carregada no motor.
 * Serve como um wrapper desacoplado para imagens de sprites.
 *
 * <p>Suporta <b>spritesheet/atlas</b> (Fase C do plano do motor grafico) embutindo
 * a regiao no proprio caminho, o que faz a serializacao existente (que guarda
 * {@code getPath()}) preservar a regiao sem alteracoes:</p>
 * <ul>
 *   <li>{@code sheet.png#x,y,w,h} &mdash; recorte por retangulo em pixels.</li>
 *   <li>{@code sheet.png@col,row,tw,th} &mdash; celula de grade (coluna, linha) com
 *       tamanho de tile {@code tw x th}, equivalente ao retangulo
 *       {@code (col*tw, row*th, tw, th)}.</li>
 * </ul>
 * <p>Sem sufixo, carrega a imagem inteira (comportamento antigo). A decodificacao do
 * arquivo-base e compartilhada entre todas as regioes via {@link AssetResolver}.</p>
 */
public class Texture2D {
    private final String path;
    private final BufferedImage image;

    /**
     * Cria uma nova instancia de textura carregando a imagem a partir do caminho
     * (com suporte a sufixo de regiao de atlas).
     * @param path Caminho relativo do asset de imagem, opcionalmente com regiao.
     */
    public Texture2D(String path) {
        this.path = path;
        this.image = loadFromPath(path);
    }

    private static BufferedImage loadFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int hash = path.indexOf('#');
        int at = path.indexOf('@');
        // Base = caminho sem o sufixo de regiao (se houver). Regiao invalida cai
        // para a imagem-base inteira, nunca para o caminho com o sufixo cru.
        String base = path;
        if (hash >= 0) base = path.substring(0, hash);
        else if (at >= 0) base = path.substring(0, at);
        try {
            if (hash >= 0) {
                int[] r = parseInts(path.substring(hash + 1), 4);
                if (r != null) {
                    return AssetResolver.loadImageRegion(base, r[0], r[1], r[2], r[3]);
                }
            } else if (at >= 0) {
                int[] g = parseInts(path.substring(at + 1), 4);
                if (g != null) {
                    // col,row,tw,th -> retangulo em pixels
                    return AssetResolver.loadImageRegion(base, g[0] * g[2], g[1] * g[3], g[2], g[3]);
                }
            }
        } catch (Exception e) {
            IgnisLogger.error("Regiao de textura invalida em '" + path + "': " + e.getMessage());
        }
        return AssetResolver.loadImage(base);
    }

    /** Interpreta exatamente {@code count} inteiros separados por virgula, ou null. */
    private static int[] parseInts(String spec, int count) {
        if (spec == null) return null;
        String[] parts = spec.split(",");
        if (parts.length != count) return null;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    /**
     * Retorna a imagem processada (BufferedImage) — a regiao recortada quando o
     * caminho carrega um sufixo de atlas, ou a imagem inteira caso contrario.
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Retorna o caminho do asset (incluindo o sufixo de regiao, se houver).
     */
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path != null ? path : "";
    }
}
