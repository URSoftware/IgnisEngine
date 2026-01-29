package com.ignis.core;

import org.json.JSONObject;

/**
 * Transform Component - Stores raw positional data for entities.
 * 
 * This component holds:
 * - Position (x, y) in world coordinates
 * - Rotation in degrees
 * - Scale (scaleX, scaleY)
 * 
 * The Transform is the fundamental component for spatial representation
 * in the Ignis Engine.
 */
public class Transform {

    private double x;
    private double y;
    private double rotation;
    private double scaleX;
    private double scaleY;

    /**
     * Creates a Transform at the origin with no rotation and uniform scale of 1.
     */
    public Transform() {
        this.x = 0;
        this.y = 0;
        this.rotation = 0;
        this.scaleX = 1.0;
        this.scaleY = 1.0;
    }

    /**
     * Creates a Transform with the specified position.
     * 
     * @param x The x-coordinate in world space
     * @param y The y-coordinate in world space
     */
    public Transform(double x, double y) {
        this.x = x;
        this.y = y;
        this.rotation = 0;
        this.scaleX = 1.0;
        this.scaleY = 1.0;
    }

    /**
     * Creates a Transform with full parameters.
     * 
     * @param x The x-coordinate in world space
     * @param y The y-coordinate in world space
     * @param rotation The rotation in degrees
     * @param scaleX The horizontal scale factor
     * @param scaleY The vertical scale factor
     */
    public Transform(double x, double y, double rotation, double scaleX, double scaleY) {
        this.x = x;
        this.y = y;
        this.rotation = normalizeRotation(rotation);
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    /**
     * Creates a copy of another Transform.
     * 
     * @param other The transform to copy
     */
    public Transform(Transform other) {
        this.x = other.x;
        this.y = other.y;
        this.rotation = other.rotation;
        this.scaleX = other.scaleX;
        this.scaleY = other.scaleY;
    }

    // ==================== POSITION ====================

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    /**
     * Sets both x and y position.
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Translates the position by the given amounts in WORLD space.
     */
    public void translate(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }
    
    /**
     * Translates the position by the given amounts in the specified space.
     * 
     * @param dx Movement along X axis
     * @param dy Movement along Y axis
     * @param space The coordinate space (WORLD or LOCAL)
     * 
     * In WORLD space: dx moves right, dy moves down
     * In LOCAL space: dx moves forward (based on rotation), dy moves left
     */
    public void translate(double dx, double dy, TransformSpace space) {
        if (space == null || space == TransformSpace.WORLD) {
            // World space - direct translation
            this.x += dx;
            this.y += dy;
        } else {
            // Local space - rotate the translation vector by object's rotation
            double rad = Math.toRadians(this.rotation);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            
            // Rotate the direction vector
            double worldDx = dx * cos - dy * sin;
            double worldDy = dx * sin + dy * cos;
            
            this.x += worldDx;
            this.y += worldDy;
        }
    }
    
    /**
     * Moves forward (in the direction the object is facing) by the specified amount.
     * Equivalent to translate(amount, 0, TransformSpace.LOCAL)
     * 
     * @param amount Distance to move forward
     */
    public void moveForward(double amount) {
        translate(amount, 0, TransformSpace.LOCAL);
    }
    
    /**
     * Moves backward by the specified amount.
     * 
     * @param amount Distance to move backward
     */
    public void moveBackward(double amount) {
        translate(-amount, 0, TransformSpace.LOCAL);
    }
    
    /**
     * Moves right (perpendicular to facing direction) by the specified amount.
     * 
     * @param amount Distance to strafe right
     */
    public void strafeRight(double amount) {
        translate(0, amount, TransformSpace.LOCAL);
    }
    
    /**
     * Moves left (perpendicular to facing direction) by the specified amount.
     * 
     * @param amount Distance to strafe left
     */
    public void strafeLeft(double amount) {
        translate(0, -amount, TransformSpace.LOCAL);
    }
    
    /**
     * Gets the forward direction vector based on current rotation.
     * 
     * @return Array with [dirX, dirY] normalized direction
     */
    public double[] getForwardDirection() {
        double rad = Math.toRadians(this.rotation);
        return new double[] { Math.cos(rad), Math.sin(rad) };
    }
    
    /**
     * Gets the right direction vector (perpendicular to forward).
     * 
     * @return Array with [dirX, dirY] normalized direction
     */
    public double[] getRightDirection() {
        double rad = Math.toRadians(this.rotation + 90);
        return new double[] { Math.cos(rad), Math.sin(rad) };
    }

    // ==================== ROTATION ====================

    public double getRotation() {
        return rotation;
    }

    public void setRotation(double rotation) {
        this.rotation = normalizeRotation(rotation);
    }

    /**
     * Rotates by the given amount in degrees.
     */
    public void rotate(double degrees) {
        this.rotation = normalizeRotation(this.rotation + degrees);
    }

    /**
     * Gets the rotation in radians.
     */
    public double getRotationRadians() {
        return Math.toRadians(rotation);
    }

    /**
     * Normalizes rotation to 0-360 degrees.
     */
    private double normalizeRotation(double rot) {
        rot = rot % 360;
        if (rot < 0) rot += 360;
        return rot;
    }

    // ==================== SCALE ====================

    public double getScaleX() {
        return scaleX;
    }

    public void setScaleX(double scaleX) {
        this.scaleX = scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public void setScaleY(double scaleY) {
        this.scaleY = scaleY;
    }

    /**
     * Sets both scale factors.
     */
    public void setScale(double scaleX, double scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    /**
     * Sets uniform scale for both axes.
     */
    public void setUniformScale(double scale) {
        this.scaleX = scale;
        this.scaleY = scale;
    }

    // ==================== SERIALIZATION ====================

    /**
     * Serializes the transform to JSON.
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("x", x);
        json.put("y", y);
        json.put("rotation", rotation);
        json.put("scaleX", scaleX);
        json.put("scaleY", scaleY);
        return json;
    }

    /**
     * Deserializes a transform from JSON.
     */
    public static Transform fromJSON(JSONObject json) {
        Transform transform = new Transform();
        transform.x = json.optDouble("x", 0);
        transform.y = json.optDouble("y", 0);
        transform.rotation = json.optDouble("rotation", 0);
        transform.scaleX = json.optDouble("scaleX", 1.0);
        transform.scaleY = json.optDouble("scaleY", 1.0);
        return transform;
    }

    /**
     * Copies values from another Transform.
     */
    public void copyFrom(Transform other) {
        this.x = other.x;
        this.y = other.y;
        this.rotation = other.rotation;
        this.scaleX = other.scaleX;
        this.scaleY = other.scaleY;
    }

    /**
     * Resets the transform to default values.
     */
    public void reset() {
        this.x = 0;
        this.y = 0;
        this.rotation = 0;
        this.scaleX = 1.0;
        this.scaleY = 1.0;
    }

    @Override
    public String toString() {
        return String.format("Transform(x=%.2f, y=%.2f, rot=%.1f, scale=(%.2f, %.2f))",
                x, y, rotation, scaleX, scaleY);
    }
}
