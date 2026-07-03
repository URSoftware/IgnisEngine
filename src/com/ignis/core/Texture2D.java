package com.ignis.core;

import java.awt.image.BufferedImage;

/**
 * Representa uma textura 2D carregada no motor.
 * Serve como um wrapper desacoplado para imagens de sprites.
 */
public class Texture2D {
    private final String path;
    private final BufferedImage image;

    /**
     * Cria uma nova instancia de textura carregando a imagem a partir do caminho.
     * @param path Caminho relativo do asset de imagem.
     */
    public Texture2D(String path) {
        this.path = path;
        this.image = AssetResolver.loadImage(path);
    }

    /**
     * Retorna a imagem processada (BufferedImage).
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Retorna o caminho relativo do asset de imagem.
     */
    public String getPath() {
        return path;
    }
    
    @Override
    public String toString() {
        return path != null ? path : "";
    }
}
