package com.ignis.core;

import java.awt.Rectangle;

/**
 * Viewport - Manages the mapping between world space and screen space.
 * 
 * The Viewport separates the concept of "where we are in the world" (Camera)
 * from "where things appear on screen" (Viewport).
 * 
 * Key features:
 * - Reference resolution: 1920x1080 (fixed design resolution)
 * - Expand mode: When window resizes, shows more/less of the world instead of stretching
 * - Support for multiple viewports (e.g., main view + minimap)
 */
public class Viewport {

    // Reference resolution (design resolution)
    public static final int REFERENCE_WIDTH = 1920;
    public static final int REFERENCE_HEIGHT = 1080;

    // Screen rectangle where this viewport renders (in pixels)
    private Rectangle screenRect;

    // Actual window/canvas size
    private int windowWidth;
    private int windowHeight;

    // Scale factor based on window size vs reference resolution
    private double scaleFactorX;
    private double scaleFactorY;

    // Whether to use letterboxing (false = expand mode, true = letterbox)
    private boolean letterboxMode;

    /**
     * Creates a viewport that fills the entire window.
     * 
     * @param windowWidth  The current window width
     * @param windowHeight The current window height
     */
    public Viewport(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.screenRect = new Rectangle(0, 0, windowWidth, windowHeight);
        this.letterboxMode = false;
        updateScaleFactors();
    }

    /**
     * Creates a viewport at a specific screen location (for split-screen or minimap).
     * 
     * @param screenX      X position on screen
     * @param screenY      Y position on screen
     * @param width        Width of viewport on screen
     * @param height       Height of viewport on screen
     * @param windowWidth  The current window width
     * @param windowHeight The current window height
     */
    public Viewport(int screenX, int screenY, int width, int height, int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.screenRect = new Rectangle(screenX, screenY, width, height);
        this.letterboxMode = false;
        updateScaleFactors();
    }

    /**
     * Updates the viewport when the window is resized.
     * 
     * @param newWidth  New window width
     * @param newHeight New window height
     */
    public void resize(int newWidth, int newHeight) {
        this.windowWidth = newWidth;
        this.windowHeight = newHeight;
        // Full-screen viewport updates to fill window
        this.screenRect = new Rectangle(0, 0, newWidth, newHeight);
        updateScaleFactors();
    }

    /**
     * Updates scale factors based on current window size.
     * In Expand mode, we calculate how much of the world is visible.
     */
    private void updateScaleFactors() {
        if (letterboxMode) {
            // Letterbox: maintain aspect ratio
            double refAspect = (double) REFERENCE_WIDTH / REFERENCE_HEIGHT;
            double winAspect = (double) screenRect.width / screenRect.height;

            if (winAspect > refAspect) {
                // Window is wider than reference
                scaleFactorY = (double) screenRect.height / REFERENCE_HEIGHT;
                scaleFactorX = scaleFactorY;
            } else {
                // Window is taller than reference
                scaleFactorX = (double) screenRect.width / REFERENCE_WIDTH;
                scaleFactorY = scaleFactorX;
            }
        } else {
            // Expand mode: independent scaling shows more/less world
            scaleFactorX = (double) screenRect.width / REFERENCE_WIDTH;
            scaleFactorY = (double) screenRect.height / REFERENCE_HEIGHT;
        }
    }

    /**
     * Gets the visible world width based on current viewport size.
     * At reference resolution, this returns REFERENCE_WIDTH.
     */
    public double getVisibleWorldWidth() {
        return screenRect.width / scaleFactorX;
    }

    /**
     * Gets the visible world height based on current viewport size.
     * At reference resolution, this returns REFERENCE_HEIGHT.
     */
    public double getVisibleWorldHeight() {
        return screenRect.height / scaleFactorY;
    }

    /**
     * Gets the center X of the screen (half the viewport width).
     * Used to center the camera's (0,0) at the screen center.
     */
    public double getScreenCenterX() {
        return screenRect.x + screenRect.width / 2.0;
    }

    /**
     * Gets the center Y of the screen (half the viewport height).
     * Used to center the camera's (0,0) at the screen center.
     */
    public double getScreenCenterY() {
        return screenRect.y + screenRect.height / 2.0;
    }

    /**
     * Gets the viewport's visible world center X offset.
     * This is half of the visible world width.
     */
    public double getWorldCenterOffsetX() {
        return getVisibleWorldWidth() / 2.0;
    }

    /**
     * Gets the viewport's visible world center Y offset.
     * This is half of the visible world height.
     */
    public double getWorldCenterOffsetY() {
        return getVisibleWorldHeight() / 2.0;
    }

    // ==================== GETTERS & SETTERS ====================

    public Rectangle getScreenRect() {
        return screenRect;
    }

    public void setScreenRect(Rectangle rect) {
        this.screenRect = rect;
        updateScaleFactors();
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public double getScaleFactorX() {
        return scaleFactorX;
    }

    public double getScaleFactorY() {
        return scaleFactorY;
    }

    /**
     * Gets the uniform scale factor (average of X and Y).
     * Useful when you need a single scale value.
     */
    public double getUniformScaleFactor() {
        return (scaleFactorX + scaleFactorY) / 2.0;
    }

    public boolean isLetterboxMode() {
        return letterboxMode;
    }

    public void setLetterboxMode(boolean letterbox) {
        this.letterboxMode = letterbox;
        updateScaleFactors();
    }

    public int getWidth() {
        return screenRect.width;
    }

    public int getHeight() {
        return screenRect.height;
    }

    public int getX() {
        return screenRect.x;
    }

    public int getY() {
        return screenRect.y;
    }

    @Override
    public String toString() {
        return String.format("Viewport(screen=%s, scale=(%.3f, %.3f), visibleWorld=(%.0f x %.0f))",
                screenRect, scaleFactorX, scaleFactorY, getVisibleWorldWidth(), getVisibleWorldHeight());
    }
}
