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

public class Player extends GameObject {

    private double speed = 1.0;
    private int health = 100;
    private BufferedImage spriteImage = null;

    public Player(String name, Game game, double x, double y, int width, int height) {
        super(name, game, x, y, width, height);
    }

    // Construtor vazio para EntityFactory
    public Player() {
        super();
    }

    @Override
    public void tick() {
        x += speed;

        // Usa o tamanho real do Canvas em vez de constante
        if (x > game.getWidth()) {
            x = -width;
        }
    }

    @Override
    public void render(Graphics g) {
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
            // Original blue square
            g2d.setColor(Color.BLUE);
            g2d.fillRect((int) x, (int) y, width, height);
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
        if (props.has("speed")) {
            this.speed = props.getDouble("speed");
        }
        if (props.has("health")) {
            this.health = props.getInt("health");
        }
        if (props.has("spritePath")) {
            this.spritePath = props.getString("spritePath");
            loadSprite();
        }
    }

    @Override
    public JSONObject saveProperties() {
        JSONObject props = new JSONObject();
        props.put("speed", speed);
        props.put("health", health);
        if (spritePath != null && !spritePath.isEmpty()) {
            props.put("spritePath", spritePath);
        }
        return props;
    }

    // Getters e Setters
    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }
    
    public BufferedImage getSpriteImage() {
        return spriteImage;
    }
}
