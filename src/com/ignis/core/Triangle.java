package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.json.JSONObject;

/**
 * Basic scene object - a triangle.
 * Has no own movement logic.
 * Movement will be done by user scripts.
 */
public class Triangle extends GameObject {

    private Color color = new Color(150, 255, 100);
    private BufferedImage spriteImage = null;

    public Triangle(String name, Game game, double x, double y, int width, int height) {
        super(name, game, x, y, width, height);
    }

    // Empty constructor for EntityFactory
    public Triangle() {
        super();
        this.color = new Color(150, 255, 100);
    }

    @Override
    public void tick() {
        // No automatic movement logic
        // Movement will be done by user scripts
    }

    @Override
    public void render(Graphics g) {
        if (!visible) return;
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Save original transform
        AffineTransform oldTransform = g2d.getTransform();
        
        // Apply rotation around center
        if (rotation != 0) {
            double centerX = x + width / 2.0;
            double centerY = y + height / 2.0;
            g2d.rotate(Math.toRadians(rotation), centerX, centerY);
        }
        
        // Check if we have a sprite image
        if (spriteImage != null) {
            // Render the sprite image
            g2d.drawImage(spriteImage, (int) x, (int) y, width, height, null);
        } else {
            // Calculate triangle points
            int[] xPoints = {
                (int) x + width / 2,  // Top point
                (int) x,              // Bottom left
                (int) x + width       // Bottom right
            };
            int[] yPoints = {
                (int) y,              // Top point
                (int) y + height,     // Bottom left
                (int) y + height      // Bottom right
            };
            
            Polygon triangle = new Polygon(xPoints, yPoints, 3);
            
            // Fill the triangle
            g2d.setColor(color);
            g2d.fillPolygon(triangle);
            
            // Border
            g2d.setColor(color.darker());
            g2d.drawPolygon(triangle);
        }
        
        // Restore original transform
        g2d.setTransform(oldTransform);
    }
    
    /**
     * Load sprite image from file path
     */
    public void loadSprite() {
        if (spritePath != null && !spritePath.isEmpty()) {
            try {
                File imageFile = new File(spritePath);
                if (imageFile.exists()) {
                    spriteImage = ImageIO.read(imageFile);
                } else {
                    System.err.println("Sprite file not found: " + spritePath);
                    spriteImage = null;
                }
            } catch (Exception e) {
                System.err.println("Failed to load sprite: " + e.getMessage());
                spriteImage = null;
            }
        } else {
            spriteImage = null;
        }
    }
    
    @Override
    public void setSpritePath(String path) {
        super.setSpritePath(path);
        loadSprite();
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props.has("color")) {
            this.color = new Color(props.getInt("color"));
        }
        if (props.has("spritePath")) {
            this.spritePath = props.getString("spritePath");
            loadSprite();
        }
        if (props.has("visible")) {
            this.visible = props.getBoolean("visible");
        }
    }

    @Override
    public JSONObject saveProperties() {
        JSONObject props = new JSONObject();
        props.put("color", color.getRGB());
        props.put("visible", visible);
        if (spritePath != null && !spritePath.isEmpty()) {
            props.put("spritePath", spritePath);
        }
        return props;
    }

    // Getters and Setters
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    
    public BufferedImage getSpriteImage() {
        return spriteImage;
    }
}
