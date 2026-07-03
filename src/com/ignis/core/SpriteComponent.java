package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;

/**
 * Componente especializado de renderização de sprites e formas geométricas primitivas.
 * Isola a lógica visual. Desenha o sprite ou a forma na posição atual do GameObject.
 */
public class SpriteComponent extends IgnisScript {
    
    @Serialize
    private Texture2D texture;

    @Serialize
    private String shapeType = "None"; // "None", "Square", "Circle", "Triangle", "Star", "Pentagon"

    @Serialize
    private Color tint = new Color(100, 150, 255); // Usado como cor de preenchimento ou tint

    @Serialize
    private boolean flipX = false;

    @Serialize
    private boolean flipY = false;

    /**
     * Cria um SpriteComponent associando uma textura inicial.
     * @param texture Textura a ser desenhada.
     */
    public SpriteComponent(Texture2D texture) {
        this.texture = texture;
    }

    /**
     * Construtor padrão para instanciação por reflexão e desserialização.
     */
    public SpriteComponent() {
    }

    /**
     * Desenha o sprite ou forma baseado na posição e transform do GameObject pai.
     * @param g Contexto gráfico de desenho.
     */
    public void draw(Graphics2D g) {
        if (gameObject == null) return;

        // Ativa antialiasing para qualidade
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Salva o transform original
        AffineTransform oldTransform = g.getTransform();
        
        // Aplica rotação ao redor do centro do objeto
        if (gameObject.getRotation() != 0) {
            double centerX = gameObject.getX() + gameObject.getWidth() / 2.0;
            double centerY = gameObject.getY() + gameObject.getHeight() / 2.0;
            g.rotate(Math.toRadians(gameObject.getRotation()), centerX, centerY);
        }
        
        if (texture != null && texture.getImage() != null) {
            // Inverte Y para compensar o eixo Y invertido das coordenadas de mundo do IgnisEngine
            AffineTransform flipTransform = g.getTransform();
            g.translate(gameObject.getX(), gameObject.getY() + gameObject.getHeight());
            g.scale(1, -1);
            
            // Aplica espelhamento horizontal e vertical do sprite
            double tx = flipX ? gameObject.getWidth() : 0;
            double ty = flipY ? gameObject.getHeight() : 0;
            double sx = flipX ? -1 : 1;
            double sy = flipY ? -1 : 1;
            g.translate(tx, ty);
            g.scale(sx, sy);
            
            // Desenha a imagem
            g.drawImage(texture.getImage(), 0, 0, gameObject.getWidth(), gameObject.getHeight(), null);
            
            // Restaura transformações de inversão
            g.setTransform(flipTransform);
        } else if (shapeType != null && !shapeType.equalsIgnoreCase("None")) {
            // Renderiza forma geométrica vetorial
            double centerX = gameObject.getX() + gameObject.getWidth() / 2.0;
            double centerY = gameObject.getY() + gameObject.getHeight() / 2.0;
            double x = gameObject.getX();
            double y = gameObject.getY();
            double w = gameObject.getWidth();
            double h = gameObject.getHeight();

            g.setColor(tint != null ? tint : new Color(100, 150, 255));

            switch (shapeType.toLowerCase()) {
                case "square":
                case "rectangle":
                    g.fillRect((int) x, (int) y, (int) w, (int) h);
                    g.setColor(g.getColor().darker());
                    g.drawRect((int) x, (int) y, (int) w, (int) h);
                    break;
                case "circle":
                case "oval":
                    g.fillOval((int) x, (int) y, (int) w, (int) h);
                    g.setColor(g.getColor().darker());
                    g.drawOval((int) x, (int) y, (int) w, (int) h);
                    break;
                case "triangle":
                    int[] txPoints = { (int) x, (int) (x + w / 2.0), (int) (x + w) };
                    int[] tyPoints = { (int) (y + h), (int) y, (int) (y + h) };
                    g.fillPolygon(txPoints, tyPoints, 3);
                    g.setColor(g.getColor().darker());
                    g.drawPolygon(txPoints, tyPoints, 3);
                    break;
                case "star":
                    int points = 5;
                    int[] sxPoints = new int[points * 2];
                    int[] syPoints = new int[points * 2];
                    double outerRadius = Math.min(w, h) / 2.0;
                    double innerRadius = outerRadius * 0.4;
                    for (int i = 0; i < points * 2; i++) {
                        double angle = Math.PI * i / points - Math.PI / 2;
                        double radius = (i % 2 == 0) ? outerRadius : innerRadius;
                        sxPoints[i] = (int) (centerX + radius * Math.cos(angle));
                        syPoints[i] = (int) (centerY + radius * Math.sin(angle));
                    }
                    g.fillPolygon(sxPoints, syPoints, points * 2);
                    g.setColor(g.getColor().darker());
                    g.drawPolygon(sxPoints, syPoints, points * 2);
                    break;
                case "pentagon":
                    int sides = 5;
                    int[] pxPoints = new int[sides];
                    int[] pyPoints = new int[sides];
                    double radius = Math.min(w, h) / 2.0;
                    for (int i = 0; i < sides; i++) {
                        double angle = 2 * Math.PI * i / sides - Math.PI / 2;
                        pxPoints[i] = (int) (centerX + radius * Math.cos(angle));
                        pyPoints[i] = (int) (centerY + radius * Math.sin(angle));
                    }
                    g.fillPolygon(pxPoints, pyPoints, sides);
                    g.setColor(g.getColor().darker());
                    g.drawPolygon(pxPoints, pyPoints, sides);
                    break;
            }
        }
        
        // Restaura transformações
        g.setTransform(oldTransform);
    }
    
    // Getters e Setters
    public Texture2D getTexture() { return texture; }
    public void setTexture(Texture2D texture) { this.texture = texture; }
    public String getShapeType() { return shapeType; }
    public void setShapeType(String shapeType) { this.shapeType = shapeType; }
    public Color getTint() { return tint; }
    public void setTint(Color tint) { this.tint = tint; }
    public boolean isFlipX() { return flipX; }
    public void setFlipX(boolean flipX) { this.flipX = flipX; }
    public boolean isFlipY() { return flipY; }
    public void setFlipY(boolean flipY) { this.flipY = flipY; }
}
