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
 * Basic scene object - a circle.
 * Has no own movement logic.
 * Movement will be done by user scripts.
 */
public class Circle extends GameObject {

    private Color color = new Color(255, 150, 100);
    private BufferedImage spriteImage = null;

    public Circle(String name, Game game, double x, double y, int width, int height) {
        super(name, game, x, y, width, height);
    }

    // Empty constructor for EntityFactory
    public Circle() {
        super();
        this.color = new Color(255, 150, 100);
    }

    @Override
    public void tick() {
        // No automatic movement logic
        // Movement will be done by user scripts
    }
    @Override
    public void render(Graphics g) {
        if (!visible) return;
        if (renderSpriteComponent(g)) return;
        
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
            // Flip vertically to compensate for inverted Y-axis in world coordinates
            AffineTransform flipTransform = g2d.getTransform();
            g2d.translate(x, y + height);
            g2d.scale(1, -1);
            g2d.drawImage(spriteImage, 0, 0, width, height, null);
            g2d.setTransform(flipTransform);
        } else {
            // Fill the circle with color
            g2d.setColor(color);
            g2d.fillOval((int) x, (int) y, width, height);
            
            // Border
            g2d.setColor(color.darker());
            g2d.drawOval((int) x, (int) y, width, height);
        }
        
        // Restore original transform
        g2d.setTransform(oldTransform);
    }
    
    /**
     * Load sprite image from file path
     */
    public void loadSprite() {
        spriteImage = AssetResolver.loadImage(spritePath);
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
