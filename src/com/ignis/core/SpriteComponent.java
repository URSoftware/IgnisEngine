package com.ignis.core;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * Componente especializado de renderizacao de sprites.
 * Isole a logica visual. Desenha o sprite na posicao atual do GameObject.
 */
public class SpriteComponent extends IgnisScript {
    
    @Serialize
    private Texture2D texture;

    /**
     * Cria um SpriteComponent associando uma textura inicial.
     * @param texture Textura a ser desenhada.
     */
    public SpriteComponent(Texture2D texture) {
        this.texture = texture;
    }

    /**
     * Construtor padrao para instanciacao por reflexao e desserializacao.
     */
    public SpriteComponent() {
    }

    /**
     * Desenha o sprite baseado na posicao e transform do GameObject pai.
     * @param g Contexto grafico de desenho.
     */
    public void draw(Graphics2D g) {
        if (texture != null && texture.getImage() != null && gameObject != null) {
            // Salva o transform original
            AffineTransform oldTransform = g.getTransform();
            
            // Aplica rotacao ao redor do centro do objeto
            if (gameObject.getRotation() != 0) {
                double centerX = gameObject.getX() + gameObject.getWidth() / 2.0;
                double centerY = gameObject.getY() + gameObject.getHeight() / 2.0;
                g.rotate(Math.toRadians(gameObject.getRotation()), centerX, centerY);
            }
            
            // Inverte Y para compensar o eixo Y invertido das coordenadas de mundo do IgnisEngine
            AffineTransform flipTransform = g.getTransform();
            g.translate(gameObject.getX(), gameObject.getY() + gameObject.getHeight());
            g.scale(1, -1);
            
            // Desenha a imagem da textura
            g.drawImage(texture.getImage(), 0, 0, gameObject.getWidth(), gameObject.getHeight(), null);
            
            // Restaura transformacoes
            g.setTransform(flipTransform);
            g.setTransform(oldTransform);
        }
    }
    
    /**
     * Retorna a textura associada a este componente.
     */
    public Texture2D getTexture() {
        return texture;
    }
    
    /**
     * Define a textura associada a este componente.
     */
    public void setTexture(Texture2D texture) {
        this.texture = texture;
    }
}
