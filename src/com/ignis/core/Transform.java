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
     * Translates the position by the given amounts.
     */
    public void translate(double dx, double dy) {
        this.x += dx;
        this.y += dy;
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
