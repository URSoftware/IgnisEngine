package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.json.JSONObject;

/**
 * Pentagon shape - a regular 5-sided polygon.
 * Has no own movement logic.
 * Movement will be done by user scripts.
 */
public class Pentagon extends GameObject {

    private Color color = new Color(150, 100, 200); // Purple color
    private BufferedImage spriteImage = null;

    public Pentagon(String name, Game game, double x, double y, int width, int height) {
        super(name, game, x, y, width, height);
    }

    // Empty constructor for EntityFactory
    public Pentagon() {
        super();
        this.color = new Color(150, 100, 200);
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
        double centerX = x + width / 2.0;
        double centerY = y + height / 2.0;
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), centerX, centerY);
        }
        
        // Check if we have a sprite image
        if (spriteImage != null) {
            g2d.drawImage(spriteImage, (int) x, (int) y, width, height, null);
        } else {
            // Draw pentagon shape
            int sides = 5;
            int[] xPoints = new int[sides];
            int[] yPoints = new int[sides];
            
            double radius = Math.min(width, height) / 2.0;
            
            for (int i = 0; i < sides; i++) {
                double angle = 2 * Math.PI * i / sides - Math.PI / 2;
                xPoints[i] = (int) (centerX + radius * Math.cos(angle));
                yPoints[i] = (int) (centerY + radius * Math.sin(angle));
            }
            
            // Fill the pentagon
            g2d.setColor(color);
            g2d.fillPolygon(xPoints, yPoints, sides);
            
            // Border
            g2d.setColor(color.darker());
            g2d.drawPolygon(xPoints, yPoints, sides);
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
