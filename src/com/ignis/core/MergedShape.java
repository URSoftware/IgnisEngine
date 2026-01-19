package com.ignis.core;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * MergedShape - A shape created by merging multiple GameObjects.
 * Combines the visual representation of multiple shapes into one.
 */
public class MergedShape extends GameObject {

    private Color color = new Color(180, 100, 180); // Purple-ish color
    private BufferedImage spriteImage = null;
    private List<ShapeData> mergedShapes = new ArrayList<>();
    
    /**
     * Internal class to store shape data for rendering
     */
    public static class ShapeData {
        public String type;
        public double relativeX, relativeY; // Position relative to merged object center
        public int width, height;
        public double rotation;
        public Color color;
        
        public ShapeData(String type, double relX, double relY, int width, int height, double rotation, Color color) {
            this.type = type;
            this.relativeX = relX;
            this.relativeY = relY;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.color = color;
        }
        
        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("relativeX", relativeX);
            json.put("relativeY", relativeY);
            json.put("width", width);
            json.put("height", height);
            json.put("rotation", rotation);
            json.put("color", color.getRGB());
            return json;
        }
        
        public static ShapeData fromJSON(JSONObject json) {
            return new ShapeData(
                json.getString("type"),
                json.getDouble("relativeX"),
                json.getDouble("relativeY"),
                json.getInt("width"),
                json.getInt("height"),
                json.getDouble("rotation"),
                new Color(json.getInt("color"))
            );
        }
    }

    public MergedShape(String name, Game game, double x, double y, int width, int height) {
        super(name, game, x, y, width, height);
    }

    // Empty constructor for EntityFactory
    public MergedShape() {
        super();
        this.color = new Color(180, 100, 180);
    }
    
    /**
     * Creates a merged shape from multiple GameObjects
     */
    public static MergedShape createFromObjects(List<GameObject> objects, Game game) {
        if (objects == null || objects.isEmpty()) return null;
        
        // Calculate bounding box of all objects
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        
        for (GameObject obj : objects) {
            minX = Math.min(minX, obj.getX());
            minY = Math.min(minY, obj.getY());
            maxX = Math.max(maxX, obj.getX() + obj.getWidth());
            maxY = Math.max(maxY, obj.getY() + obj.getHeight());
        }
        
        int totalWidth = (int) (maxX - minX);
        int totalHeight = (int) (maxY - minY);
        
        // Use first object's name
        String mergedName = objects.get(0).getName() + " (Merged)";
        
        MergedShape merged = new MergedShape(mergedName, game, minX, minY, totalWidth, totalHeight);
        
        // Calculate center of merged shape
        double centerX = minX + totalWidth / 2.0;
        double centerY = minY + totalHeight / 2.0;
        
        // Add all shapes with relative positions
        for (GameObject obj : objects) {
            Color objColor = Color.GRAY;
            
            // Get color from object
            if (obj instanceof Square) {
                objColor = ((Square) obj).getColor();
            } else if (obj instanceof Circle) {
                objColor = ((Circle) obj).getColor();
            } else if (obj instanceof Triangle) {
                objColor = ((Triangle) obj).getColor();
            } else if (obj instanceof Star) {
                objColor = ((Star) obj).getColor();
            } else if (obj instanceof Pentagon) {
                objColor = ((Pentagon) obj).getColor();
            } else if (obj instanceof MergedShape) {
                // If merging with another merged shape, include its sub-shapes
                MergedShape otherMerged = (MergedShape) obj;
                for (ShapeData sd : otherMerged.getMergedShapes()) {
                    double absX = obj.getX() + obj.getWidth() / 2.0 + sd.relativeX;
                    double absY = obj.getY() + obj.getHeight() / 2.0 + sd.relativeY;
                    double relX = absX - centerX;
                    double relY = absY - centerY;
                    merged.addShape(new ShapeData(sd.type, relX, relY, sd.width, sd.height, sd.rotation, sd.color));
                }
                continue;
            }
            
            // Calculate relative position from center
            double objCenterX = obj.getX() + obj.getWidth() / 2.0;
            double objCenterY = obj.getY() + obj.getHeight() / 2.0;
            double relX = objCenterX - centerX;
            double relY = objCenterY - centerY;
            
            merged.addShape(new ShapeData(
                obj.getType(),
                relX, relY,
                obj.getWidth(), obj.getHeight(),
                obj.getRotation(),
                objColor
            ));
        }
        
        return merged;
    }
    
    public void addShape(ShapeData shape) {
        mergedShapes.add(shape);
    }
    
    public List<ShapeData> getMergedShapes() {
        return mergedShapes;
    }

    @Override
    public void tick() {
        // No automatic movement logic
    }

    @Override
    public void render(Graphics g) {
        if (!visible) return;
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Save original transform
        AffineTransform oldTransform = g2d.getTransform();
        
        // Apply rotation around center of merged shape
        double centerX = x + width / 2.0;
        double centerY = y + height / 2.0;
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), centerX, centerY);
        }
        
        // Check if we have a sprite image
        if (spriteImage != null) {
            g2d.drawImage(spriteImage, (int) x, (int) y, width, height, null);
        } else {
            // Render each sub-shape
            for (ShapeData shape : mergedShapes) {
                renderSubShape(g2d, shape, centerX, centerY);
            }
        }
        
        // Restore original transform
        g2d.setTransform(oldTransform);
    }
    
    private void renderSubShape(Graphics2D g2d, ShapeData shape, double parentCenterX, double parentCenterY) {
        AffineTransform oldTransform = g2d.getTransform();
        
        double shapeCenterX = parentCenterX + shape.relativeX;
        double shapeCenterY = parentCenterY + shape.relativeY;
        double shapeX = shapeCenterX - shape.width / 2.0;
        double shapeY = shapeCenterY - shape.height / 2.0;
        
        // Apply individual shape rotation
        if (shape.rotation != 0) {
            g2d.rotate(Math.toRadians(shape.rotation), shapeCenterX, shapeCenterY);
        }
        
        g2d.setColor(shape.color);
        
        switch (shape.type) {
            case "Square":
                g2d.fillRect((int) shapeX, (int) shapeY, shape.width, shape.height);
                g2d.setColor(shape.color.darker());
                g2d.drawRect((int) shapeX, (int) shapeY, shape.width, shape.height);
                break;
                
            case "Circle":
                g2d.fillOval((int) shapeX, (int) shapeY, shape.width, shape.height);
                g2d.setColor(shape.color.darker());
                g2d.drawOval((int) shapeX, (int) shapeY, shape.width, shape.height);
                break;
                
            case "Triangle":
                int[] triX = {
                    (int) shapeCenterX,
                    (int) (shapeX),
                    (int) (shapeX + shape.width)
                };
                int[] triY = {
                    (int) shapeY,
                    (int) (shapeY + shape.height),
                    (int) (shapeY + shape.height)
                };
                g2d.fillPolygon(triX, triY, 3);
                g2d.setColor(shape.color.darker());
                g2d.drawPolygon(triX, triY, 3);
                break;
                
            case "Star":
                int points = 5;
                int[] starX = new int[points * 2];
                int[] starY = new int[points * 2];
                double outerRadius = Math.min(shape.width, shape.height) / 2.0;
                double innerRadius = outerRadius * 0.4;
                
                for (int i = 0; i < points * 2; i++) {
                    double angle = Math.PI * i / points - Math.PI / 2;
                    double radius = (i % 2 == 0) ? outerRadius : innerRadius;
                    starX[i] = (int) (shapeCenterX + radius * Math.cos(angle));
                    starY[i] = (int) (shapeCenterY + radius * Math.sin(angle));
                }
                g2d.fillPolygon(starX, starY, points * 2);
                g2d.setColor(shape.color.darker());
                g2d.drawPolygon(starX, starY, points * 2);
                break;
                
            case "Pentagon":
                int sides = 5;
                int[] pentX = new int[sides];
                int[] pentY = new int[sides];
                double radius = Math.min(shape.width, shape.height) / 2.0;
                
                for (int i = 0; i < sides; i++) {
                    double angle = 2 * Math.PI * i / sides - Math.PI / 2;
                    pentX[i] = (int) (shapeCenterX + radius * Math.cos(angle));
                    pentY[i] = (int) (shapeCenterY + radius * Math.sin(angle));
                }
                g2d.fillPolygon(pentX, pentY, sides);
                g2d.setColor(shape.color.darker());
                g2d.drawPolygon(pentX, pentY, sides);
                break;
                
            default:
                // Default to rectangle
                g2d.fillRect((int) shapeX, (int) shapeY, shape.width, shape.height);
                break;
        }
        
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
        if (props.has("mergedShapes")) {
            mergedShapes.clear();
            JSONArray shapesArray = props.getJSONArray("mergedShapes");
            for (int i = 0; i < shapesArray.length(); i++) {
                mergedShapes.add(ShapeData.fromJSON(shapesArray.getJSONObject(i)));
            }
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
        
        JSONArray shapesArray = new JSONArray();
        for (ShapeData shape : mergedShapes) {
            shapesArray.put(shape.toJSON());
        }
        props.put("mergedShapes", shapesArray);
        
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
