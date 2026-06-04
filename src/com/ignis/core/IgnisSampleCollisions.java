package com.ignis.core;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * IgnisSampleCollisions - Advanced Collision Detection System for Ignis Engine
 * 
 * This system provides:
 * - Multiple collision primitives (AABB, Circle, Polygon with SAT)
 * - Two-phase collision detection (Broad Phase + Narrow Phase)
 * - Collision resolution with MTV (Minimum Translation Vector)
 * - Trigger and Collision modes
 * - Continuous Collision Detection (CCD) for fast-moving objects
 * 
 * Usage:
 * 1. Attach a Collider to your GameObject
 * 2. Configure as Trigger or Collision type
 * 3. The engine automatically handles collision detection and resolution
 */
public class IgnisSampleCollisions {

    // ==================== COLLIDER TYPES ====================
    
    /**
     * Collider type enumeration
     */
    public enum ColliderType {
        NONE,           // No collider
        AABB,           // Axis-Aligned Bounding Box (non-rotating rectangle)
        CIRCLE,         // Circle collider
        POLYGON         // Polygon collider (supports SAT for rotated shapes)
    }
    
    /**
     * Collision mode - determines behavior on collision
     */
    public enum CollisionMode {
        TRIGGER,        // Only triggers events, no physical response
        COLLISION       // Physical collision with response (MTV resolution)
    }
    
    // ==================== COLLIDER BASE CLASS ====================
    
    /**
     * Base Collider class - attach to GameObjects for collision detection
     */
    public static class Collider {
        protected GameObject owner;
        protected ColliderType type = ColliderType.NONE;
        protected CollisionMode mode = CollisionMode.COLLISION;
        protected boolean enabled = true;
        
        // Offset from owner's position
        protected double offsetX = 0;
        protected double offsetY = 0;
        
        // Layer and mask for filtering collisions
        protected int layer = 0;          // This object's layer (0-31)
        protected int collisionMask = -1; // Which layers this can collide with (bitmask, -1 = all)
        
        // CCD settings
        protected boolean useCCD = false; // Use Continuous Collision Detection
        
        public Collider(GameObject owner) {
            this.owner = owner;
        }
        
        public GameObject getOwner() { return owner; }
        public ColliderType getType() { return type; }
        public CollisionMode getMode() { return mode; }
        public void setMode(CollisionMode mode) { this.mode = mode; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getOffsetX() { return offsetX; }
        public double getOffsetY() { return offsetY; }
        public void setOffset(double x, double y) { this.offsetX = x; this.offsetY = y; }
        public int getLayer() { return layer; }
        public void setLayer(int layer) { this.layer = layer; }
        public int getCollisionMask() { return collisionMask; }
        public void setCollisionMask(int mask) { this.collisionMask = mask; }
        public boolean useCCD() { return useCCD; }
        public void setUseCCD(boolean use) { this.useCCD = use; }
        
        /**
         * Gets the world X position of the collider center
         */
        public double getWorldX() {
            return owner.getX() + owner.getWidth() / 2.0 + offsetX;
        }
        
        /**
         * Gets the world Y position of the collider center
         */
        public double getWorldY() {
            return owner.getY() + owner.getHeight() / 2.0 + offsetY;
        }
        
        /**
         * Checks if this collider can collide with another based on layers
         */
        public boolean canCollideWith(Collider other) {
            if (!enabled || !other.enabled) return false;
            if (owner == other.owner) return false;
            
            // Check layer masks
            boolean thisCanHitOther = (collisionMask & (1 << other.layer)) != 0;
            boolean otherCanHitThis = (other.collisionMask & (1 << layer)) != 0;
            
            return thisCanHitOther || otherCanHitThis;
        }
        
        /**
         * Debug render the collider
         */
        public void debugRender(Graphics2D g2d) {
            // Override in subclasses
        }
    }
    
    // ==================== AABB COLLIDER ====================
    
    /**
     * Axis-Aligned Bounding Box collider.
     * A rectangle that doesn't rotate - always aligned with world axes.
     * Most efficient for static objects and simple collision.
     */
    public static class AABBCollider extends Collider {
        private double width;
        private double height;
        
        public AABBCollider(GameObject owner) {
            super(owner);
            this.type = ColliderType.AABB;
            this.width = owner.getWidth();
            this.height = owner.getHeight();
        }
        
        public AABBCollider(GameObject owner, double width, double height) {
            super(owner);
            this.type = ColliderType.AABB;
            this.width = width;
            this.height = height;
        }
        
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setSize(double width, double height) {
            this.width = width;
            this.height = height;
        }
        
        /**
         * Gets the minimum X of the AABB in world space
         */
        public double getMinX() {
            return owner.getX() + offsetX;
        }
        
        /**
         * Gets the minimum Y of the AABB in world space
         */
        public double getMinY() {
            return owner.getY() + offsetY;
        }
        
        /**
         * Gets the maximum X of the AABB in world space
         */
        public double getMaxX() {
            return owner.getX() + offsetX + width;
        }
        
        /**
         * Gets the maximum Y of the AABB in world space
         */
        public double getMaxY() {
            return owner.getY() + offsetY + height;
        }
        
        @Override
        public void debugRender(Graphics2D g2d) {
            if (!enabled) return;
            
            Color color = (mode == CollisionMode.TRIGGER) ? 
                new Color(0, 255, 0, 100) : new Color(0, 150, 255, 100);
            
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawRect((int)getMinX(), (int)getMinY(), (int)width, (int)height);
            
            // Fill with semi-transparent color
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            g2d.fillRect((int)getMinX(), (int)getMinY(), (int)width, (int)height);
        }
    }
    
    // ==================== CIRCLE COLLIDER ====================
    
    /**
     * Circle collider - perfect for round objects, projectiles, etc.
     * Efficient for radial collision detection.
     */
    public static class CircleCollider extends Collider {
        private double radius;
        
        public CircleCollider(GameObject owner) {
            super(owner);
            this.type = ColliderType.CIRCLE;
            // Default radius is half the diagonal of the object
            this.radius = Math.min(owner.getWidth(), owner.getHeight()) / 2.0;
        }
        
        public CircleCollider(GameObject owner, double radius) {
            super(owner);
            this.type = ColliderType.CIRCLE;
            this.radius = radius;
        }
        
        public double getRadius() { return radius; }
        public void setRadius(double radius) { this.radius = radius; }
        
        @Override
        public void debugRender(Graphics2D g2d) {
            if (!enabled) return;
            
            Color color = (mode == CollisionMode.TRIGGER) ? 
                new Color(0, 255, 0, 100) : new Color(0, 150, 255, 100);
            
            double cx = getWorldX();
            double cy = getWorldY();
            int r = (int)radius;
            
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawOval((int)(cx - r), (int)(cy - r), r * 2, r * 2);
            
            // Fill with semi-transparent color
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            g2d.fillOval((int)(cx - r), (int)(cy - r), r * 2, r * 2);
        }
    }
    
    // ==================== POLYGON COLLIDER (SAT) ====================
    
    /**
     * Polygon collider using Separating Axis Theorem (SAT).
     * Supports complex shapes and rotation.
     * Most expensive but most flexible collision detection.
     */
    public static class PolygonCollider extends Collider {
        // Vertices in local space (relative to object center)
        private double[] localVerticesX;
        private double[] localVerticesY;
        private int vertexCount;
        
        public PolygonCollider(GameObject owner) {
            super(owner);
            this.type = ColliderType.POLYGON;
            // Default: create rectangle polygon matching object bounds
            createRectangle(owner.getWidth(), owner.getHeight());
        }
        
        public PolygonCollider(GameObject owner, double[] verticesX, double[] verticesY) {
            super(owner);
            this.type = ColliderType.POLYGON;
            setVertices(verticesX, verticesY);
        }
        
        /**
         * Creates a rectangle polygon
         */
        public void createRectangle(double width, double height) {
            double hw = width / 2.0;
            double hh = height / 2.0;
            localVerticesX = new double[] { -hw, hw, hw, -hw };
            localVerticesY = new double[] { -hh, -hh, hh, hh };
            vertexCount = 4;
        }
        
        /**
         * Creates a regular polygon with n sides
         */
        public void createRegularPolygon(int sides, double radius) {
            if (sides < 3) sides = 3;
            localVerticesX = new double[sides];
            localVerticesY = new double[sides];
            vertexCount = sides;
            
            for (int i = 0; i < sides; i++) {
                double angle = (2 * Math.PI * i / sides) - Math.PI / 2;
                localVerticesX[i] = Math.cos(angle) * radius;
                localVerticesY[i] = Math.sin(angle) * radius;
            }
        }
        
        /**
         * Sets custom vertices
         */
        public void setVertices(double[] verticesX, double[] verticesY) {
            if (verticesX.length != verticesY.length || verticesX.length < 3) {
                throw new IllegalArgumentException("Polygon must have at least 3 vertices");
            }
            this.localVerticesX = verticesX.clone();
            this.localVerticesY = verticesY.clone();
            this.vertexCount = verticesX.length;
        }
        
        public int getVertexCount() { return vertexCount; }
        
        /**
         * Gets world-space vertices (transformed by owner's position and rotation)
         */
        public double[][] getWorldVertices() {
            double[][] result = new double[2][vertexCount];
            
            double cx = owner.getX() + owner.getWidth() / 2.0 + offsetX;
            double cy = owner.getY() + owner.getHeight() / 2.0 + offsetY;
            double rad = Math.toRadians(owner.getRotation());
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            
            for (int i = 0; i < vertexCount; i++) {
                // Rotate and translate
                result[0][i] = cx + localVerticesX[i] * cos - localVerticesY[i] * sin;
                result[1][i] = cy + localVerticesX[i] * sin + localVerticesY[i] * cos;
            }
            
            return result;
        }
        
        /**
         * Gets the edge normals (perpendicular to each edge)
         */
        public double[][] getNormals() {
            double[][] vertices = getWorldVertices();
            double[][] normals = new double[2][vertexCount];
            
            for (int i = 0; i < vertexCount; i++) {
                int j = (i + 1) % vertexCount;
                double edgeX = vertices[0][j] - vertices[0][i];
                double edgeY = vertices[1][j] - vertices[1][i];
                
                // Perpendicular (normal)
                double len = Math.sqrt(edgeX * edgeX + edgeY * edgeY);
                normals[0][i] = -edgeY / len;
                normals[1][i] = edgeX / len;
            }
            
            return normals;
        }
        
        @Override
        public void debugRender(Graphics2D g2d) {
            if (!enabled) return;
            
            double[][] vertices = getWorldVertices();
            
            Color color = (mode == CollisionMode.TRIGGER) ? 
                new Color(0, 255, 0, 100) : new Color(0, 150, 255, 100);
            
            // Draw polygon outline
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2f));
            
            int[] xPoints = new int[vertexCount];
            int[] yPoints = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) {
                xPoints[i] = (int)vertices[0][i];
                yPoints[i] = (int)vertices[1][i];
            }
            
            g2d.drawPolygon(xPoints, yPoints, vertexCount);
            
            // Fill with semi-transparent color
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            g2d.fillPolygon(xPoints, yPoints, vertexCount);
        }
    }
    
    // ==================== COLLISION RESULT ====================
    
    /**
     * Result of a collision check, containing all relevant information
     */
    public static class CollisionResult {
        public boolean collided;
        public GameObject objectA;
        public GameObject objectB;
        public Collider colliderA;
        public Collider colliderB;
        
        // Minimum Translation Vector (MTV) - the smallest vector to separate the objects
        public double mtvX;
        public double mtvY;
        public double mtvMagnitude;
        
        // Contact point (approximation)
        public double contactX;
        public double contactY;
        
        public CollisionResult() {
            this.collided = false;
        }
        
        public CollisionResult(boolean collided, GameObject a, GameObject b, 
                              Collider colA, Collider colB,
                              double mtvX, double mtvY) {
            this.collided = collided;
            this.objectA = a;
            this.objectB = b;
            this.colliderA = colA;
            this.colliderB = colB;
            this.mtvX = mtvX;
            this.mtvY = mtvY;
            this.mtvMagnitude = Math.sqrt(mtvX * mtvX + mtvY * mtvY);
        }
    }
    
    // ==================== SPATIAL PARTITIONING (BROAD PHASE) ====================
    
    /**
     * Spatial Grid for broad phase collision detection.
     * Divides the world into cells and only checks collisions between objects in the same or adjacent cells.
     */
    public static class SpatialGrid {
        private int cellSize;
        private java.util.Map<Long, List<Collider>> cells;
        
        public SpatialGrid(int cellSize) {
            this.cellSize = cellSize;
            this.cells = new java.util.HashMap<>();
        }
        
        /**
         * Clears the grid
         */
        public void clear() {
            cells.clear();
        }
        
        /**
         * Gets cell key from world coordinates
         */
        private long getCellKey(int cellX, int cellY) {
            return ((long)cellX << 32) | (cellY & 0xFFFFFFFFL);
        }
        
        /**
         * Inserts a collider into the grid
         */
        public void insert(Collider collider) {
            if (!collider.isEnabled()) return;
            
            // Get bounds of the collider
            double minX, minY, maxX, maxY;
            
            if (collider instanceof AABBCollider) {
                AABBCollider aabb = (AABBCollider) collider;
                minX = aabb.getMinX();
                minY = aabb.getMinY();
                maxX = aabb.getMaxX();
                maxY = aabb.getMaxY();
            } else if (collider instanceof CircleCollider) {
                CircleCollider circle = (CircleCollider) collider;
                double r = circle.getRadius();
                minX = circle.getWorldX() - r;
                minY = circle.getWorldY() - r;
                maxX = circle.getWorldX() + r;
                maxY = circle.getWorldY() + r;
            } else if (collider instanceof PolygonCollider) {
                PolygonCollider poly = (PolygonCollider) collider;
                double[][] vertices = poly.getWorldVertices();
                minX = maxX = vertices[0][0];
                minY = maxY = vertices[1][0];
                for (int i = 1; i < poly.getVertexCount(); i++) {
                    minX = Math.min(minX, vertices[0][i]);
                    maxX = Math.max(maxX, vertices[0][i]);
                    minY = Math.min(minY, vertices[1][i]);
                    maxY = Math.max(maxY, vertices[1][i]);
                }
            } else {
                return;
            }
            
            // Insert into all overlapping cells
            int startCellX = (int)Math.floor(minX / cellSize);
            int startCellY = (int)Math.floor(minY / cellSize);
            int endCellX = (int)Math.floor(maxX / cellSize);
            int endCellY = (int)Math.floor(maxY / cellSize);
            
            for (int cx = startCellX; cx <= endCellX; cx++) {
                for (int cy = startCellY; cy <= endCellY; cy++) {
                    long key = getCellKey(cx, cy);
                    cells.computeIfAbsent(key, k -> new ArrayList<>()).add(collider);
                }
            }
        }
        
        /**
         * Gets potential collision pairs (broad phase)
         */
        public List<ColliderPair> getPotentialPairs() {
            Set<ColliderPair> pairs = new HashSet<>();
            
            for (List<Collider> cell : cells.values()) {
                for (int i = 0; i < cell.size(); i++) {
                    for (int j = i + 1; j < cell.size(); j++) {
                        Collider a = cell.get(i);
                        Collider b = cell.get(j);
                        
                        if (a.canCollideWith(b)) {
                            pairs.add(new ColliderPair(a, b));
                        }
                    }
                }
            }
            
            return new ArrayList<>(pairs);
        }
    }
    
    /**
     * Pair of colliders for collision checking
     */
    public static class ColliderPair {
        public Collider a;
        public Collider b;
        
        public ColliderPair(Collider a, Collider b) {
            // Ensure consistent ordering for hash/equals
            if (System.identityHashCode(a) < System.identityHashCode(b)) {
                this.a = a;
                this.b = b;
            } else {
                this.a = b;
                this.b = a;
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ColliderPair)) return false;
            ColliderPair other = (ColliderPair) o;
            return a == other.a && b == other.b;
        }
        
        @Override
        public int hashCode() {
            return System.identityHashCode(a) * 31 + System.identityHashCode(b);
        }
    }
    
    // ==================== NARROW PHASE COLLISION DETECTION ====================
    
    /**
     * Tests collision between two AABB colliders
     */
    public static CollisionResult testAABBvsAABB(AABBCollider a, AABBCollider b) {
        double aMinX = a.getMinX(), aMaxX = a.getMaxX();
        double aMinY = a.getMinY(), aMaxY = a.getMaxY();
        double bMinX = b.getMinX(), bMaxX = b.getMaxX();
        double bMinY = b.getMinY(), bMaxY = b.getMaxY();
        
        // Check for overlap
        if (aMaxX <= bMinX || bMaxX <= aMinX || aMaxY <= bMinY || bMaxY <= aMinY) {
            return new CollisionResult();
        }
        
        // Calculate overlap on each axis
        double overlapX1 = aMaxX - bMinX;
        double overlapX2 = bMaxX - aMinX;
        double overlapY1 = aMaxY - bMinY;
        double overlapY2 = bMaxY - aMinY;
        
        // Find minimum overlap (MTV)
        double mtvX = 0, mtvY = 0;
        double minOverlapX = Math.min(overlapX1, overlapX2);
        double minOverlapY = Math.min(overlapY1, overlapY2);
        
        if (minOverlapX < minOverlapY) {
            mtvX = (overlapX1 < overlapX2) ? -overlapX1 : overlapX2;
        } else {
            mtvY = (overlapY1 < overlapY2) ? -overlapY1 : overlapY2;
        }
        
        return new CollisionResult(true, a.getOwner(), b.getOwner(), a, b, mtvX, mtvY);
    }
    
    /**
     * Tests collision between two Circle colliders
     */
    public static CollisionResult testCirclevsCircle(CircleCollider a, CircleCollider b) {
        double ax = a.getWorldX(), ay = a.getWorldY();
        double bx = b.getWorldX(), by = b.getWorldY();
        double ar = a.getRadius(), br = b.getRadius();
        
        double dx = bx - ax;
        double dy = by - ay;
        double distSq = dx * dx + dy * dy;
        double minDist = ar + br;
        
        if (distSq >= minDist * minDist) {
            return new CollisionResult();
        }
        
        double dist = Math.sqrt(distSq);
        double overlap = minDist - dist;
        
        // Normalize direction
        double mtvX, mtvY;
        if (dist > 0.0001) {
            mtvX = (dx / dist) * overlap;
            mtvY = (dy / dist) * overlap;
        } else {
            // Circles are at the same position, push along X
            mtvX = overlap;
            mtvY = 0;
        }
        
        CollisionResult result = new CollisionResult(true, a.getOwner(), b.getOwner(), a, b, -mtvX, -mtvY);
        result.contactX = ax + dx * 0.5;
        result.contactY = ay + dy * 0.5;
        
        return result;
    }
    
    /**
     * Tests collision between AABB and Circle
     */
    public static CollisionResult testAABBvsCircle(AABBCollider aabb, CircleCollider circle) {
        double circleX = circle.getWorldX();
        double circleY = circle.getWorldY();
        double radius = circle.getRadius();
        
        // Find closest point on AABB to circle center
        double closestX = Math.max(aabb.getMinX(), Math.min(circleX, aabb.getMaxX()));
        double closestY = Math.max(aabb.getMinY(), Math.min(circleY, aabb.getMaxY()));
        
        double dx = circleX - closestX;
        double dy = circleY - closestY;
        double distSq = dx * dx + dy * dy;
        
        if (distSq >= radius * radius) {
            return new CollisionResult();
        }
        
        double dist = Math.sqrt(distSq);
        double overlap = radius - dist;
        
        double mtvX, mtvY;
        if (dist > 0.0001) {
            mtvX = (dx / dist) * overlap;
            mtvY = (dy / dist) * overlap;
        } else {
            // Circle center is inside AABB, find best direction to push
            double toLeft = circleX - aabb.getMinX();
            double toRight = aabb.getMaxX() - circleX;
            double toTop = circleY - aabb.getMinY();
            double toBottom = aabb.getMaxY() - circleY;
            
            double minDist = Math.min(Math.min(toLeft, toRight), Math.min(toTop, toBottom));
            
            if (minDist == toLeft) { mtvX = -toLeft - radius; mtvY = 0; }
            else if (minDist == toRight) { mtvX = toRight + radius; mtvY = 0; }
            else if (minDist == toTop) { mtvX = 0; mtvY = -toTop - radius; }
            else { mtvX = 0; mtvY = toBottom + radius; }
        }
        
        CollisionResult result = new CollisionResult(true, aabb.getOwner(), circle.getOwner(), aabb, circle, -mtvX, -mtvY);
        result.contactX = closestX;
        result.contactY = closestY;
        
        return result;
    }
    
    /**
     * Tests collision between two Polygon colliders using SAT
     */
    public static CollisionResult testPolygonvsPolygon(PolygonCollider a, PolygonCollider b) {
        double[][] verticesA = a.getWorldVertices();
        double[][] verticesB = b.getWorldVertices();
        double[][] normalsA = a.getNormals();
        double[][] normalsB = b.getNormals();
        
        double minOverlap = Double.MAX_VALUE;
        double mtvAxisX = 0, mtvAxisY = 0;
        
        // Test all axes from polygon A
        for (int i = 0; i < a.getVertexCount(); i++) {
            double axisX = normalsA[0][i];
            double axisY = normalsA[1][i];
            
            double[] projA = projectPolygon(verticesA, a.getVertexCount(), axisX, axisY);
            double[] projB = projectPolygon(verticesB, b.getVertexCount(), axisX, axisY);
            
            double overlap = getOverlap(projA, projB);
            if (overlap <= 0) {
                return new CollisionResult(); // Separating axis found
            }
            
            if (overlap < minOverlap) {
                minOverlap = overlap;
                mtvAxisX = axisX;
                mtvAxisY = axisY;
            }
        }
        
        // Test all axes from polygon B
        for (int i = 0; i < b.getVertexCount(); i++) {
            double axisX = normalsB[0][i];
            double axisY = normalsB[1][i];
            
            double[] projA = projectPolygon(verticesA, a.getVertexCount(), axisX, axisY);
            double[] projB = projectPolygon(verticesB, b.getVertexCount(), axisX, axisY);
            
            double overlap = getOverlap(projA, projB);
            if (overlap <= 0) {
                return new CollisionResult(); // Separating axis found
            }
            
            if (overlap < minOverlap) {
                minOverlap = overlap;
                mtvAxisX = axisX;
                mtvAxisY = axisY;
            }
        }
        
        // Ensure MTV points from A to B
        double centerAX = 0, centerAY = 0;
        double centerBX = 0, centerBY = 0;
        for (int i = 0; i < a.getVertexCount(); i++) {
            centerAX += verticesA[0][i];
            centerAY += verticesA[1][i];
        }
        centerAX /= a.getVertexCount();
        centerAY /= a.getVertexCount();
        
        for (int i = 0; i < b.getVertexCount(); i++) {
            centerBX += verticesB[0][i];
            centerBY += verticesB[1][i];
        }
        centerBX /= b.getVertexCount();
        centerBY /= b.getVertexCount();
        
        double dirX = centerBX - centerAX;
        double dirY = centerBY - centerAY;
        
        if (dirX * mtvAxisX + dirY * mtvAxisY < 0) {
            mtvAxisX = -mtvAxisX;
            mtvAxisY = -mtvAxisY;
        }
        
        return new CollisionResult(true, a.getOwner(), b.getOwner(), a, b, 
                                   mtvAxisX * minOverlap, mtvAxisY * minOverlap);
    }
    
    /**
     * Projects polygon vertices onto an axis
     */
    private static double[] projectPolygon(double[][] vertices, int count, double axisX, double axisY) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        
        for (int i = 0; i < count; i++) {
            double proj = vertices[0][i] * axisX + vertices[1][i] * axisY;
            min = Math.min(min, proj);
            max = Math.max(max, proj);
        }
        
        return new double[] { min, max };
    }
    
    /**
     * Gets overlap between two projections
     */
    private static double getOverlap(double[] projA, double[] projB) {
        return Math.min(projA[1], projB[1]) - Math.max(projA[0], projB[0]);
    }
    
    /**
     * Tests collision between any two colliders
     */
    public static CollisionResult testCollision(Collider a, Collider b) {
        // AABB vs AABB
        if (a instanceof AABBCollider && b instanceof AABBCollider) {
            return testAABBvsAABB((AABBCollider)a, (AABBCollider)b);
        }
        
        // Circle vs Circle
        if (a instanceof CircleCollider && b instanceof CircleCollider) {
            return testCirclevsCircle((CircleCollider)a, (CircleCollider)b);
        }
        
        // AABB vs Circle
        if (a instanceof AABBCollider && b instanceof CircleCollider) {
            return testAABBvsCircle((AABBCollider)a, (CircleCollider)b);
        }
        if (a instanceof CircleCollider && b instanceof AABBCollider) {
            CollisionResult result = testAABBvsCircle((AABBCollider)b, (CircleCollider)a);
            if (result.collided) {
                // Swap objects and invert MTV
                GameObject temp = result.objectA;
                result.objectA = result.objectB;
                result.objectB = temp;
                Collider tempC = result.colliderA;
                result.colliderA = result.colliderB;
                result.colliderB = tempC;
                result.mtvX = -result.mtvX;
                result.mtvY = -result.mtvY;
            }
            return result;
        }
        
        // Polygon vs Polygon
        if (a instanceof PolygonCollider && b instanceof PolygonCollider) {
            return testPolygonvsPolygon((PolygonCollider)a, (PolygonCollider)b);
        }
        
        // For mixed types involving Polygon, convert AABB/Circle to Polygon
        // This is a simplified approach - could be optimized
        if (a instanceof PolygonCollider || b instanceof PolygonCollider) {
            PolygonCollider polyA = toPolygon(a);
            PolygonCollider polyB = toPolygon(b);
            if (polyA != null && polyB != null) {
                return testPolygonvsPolygon(polyA, polyB);
            }
        }
        
        return new CollisionResult();
    }
    
    /**
     * Converts any collider to a polygon collider
     */
    private static PolygonCollider toPolygon(Collider c) {
        if (c instanceof PolygonCollider) return (PolygonCollider) c;
        
        if (c instanceof AABBCollider) {
            AABBCollider aabb = (AABBCollider) c;
            PolygonCollider poly = new PolygonCollider(c.getOwner());
            poly.createRectangle(aabb.getWidth(), aabb.getHeight());
            poly.setOffset(c.getOffsetX(), c.getOffsetY());
            return poly;
        }
        
        if (c instanceof CircleCollider) {
            CircleCollider circle = (CircleCollider) c;
            PolygonCollider poly = new PolygonCollider(c.getOwner());
            poly.createRegularPolygon(16, circle.getRadius()); // Approximate circle with 16 sides
            poly.setOffset(c.getOffsetX(), c.getOffsetY());
            return poly;
        }
        
        return null;
    }
    
    // ==================== CONTINUOUS COLLISION DETECTION (CCD) ====================
    
    /**
     * Performs a raycast from a point in a direction
     * Returns the first collision found along the ray
     */
    public static RaycastResult raycast(double startX, double startY, 
                                        double dirX, double dirY, 
                                        double maxDistance,
                                        List<Collider> colliders) {
        // Normalize direction
        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.0001) return new RaycastResult();
        dirX /= len;
        dirY /= len;
        
        RaycastResult closest = null;
        double closestDist = maxDistance;
        
        for (Collider collider : colliders) {
            if (!collider.isEnabled()) continue;
            
            RaycastResult hit = raycastCollider(startX, startY, dirX, dirY, maxDistance, collider);
            if (hit.hit && hit.distance < closestDist) {
                closest = hit;
                closestDist = hit.distance;
            }
        }
        
        return closest != null ? closest : new RaycastResult();
    }
    
    /**
     * Raycasts against a single collider
     */
    private static RaycastResult raycastCollider(double startX, double startY,
                                                  double dirX, double dirY,
                                                  double maxDistance,
                                                  Collider collider) {
        if (collider instanceof CircleCollider) {
            return raycastCircle(startX, startY, dirX, dirY, maxDistance, (CircleCollider) collider);
        } else if (collider instanceof AABBCollider) {
            return raycastAABB(startX, startY, dirX, dirY, maxDistance, (AABBCollider) collider);
        }
        // For polygons, use AABB approximation
        return new RaycastResult();
    }
    
    /**
     * Raycasts against a circle
     */
    private static RaycastResult raycastCircle(double startX, double startY,
                                                double dirX, double dirY,
                                                double maxDistance,
                                                CircleCollider circle) {
        double cx = circle.getWorldX();
        double cy = circle.getWorldY();
        double r = circle.getRadius();
        
        double fx = startX - cx;
        double fy = startY - cy;
        
        double a = dirX * dirX + dirY * dirY;
        double b = 2 * (fx * dirX + fy * dirY);
        double c = fx * fx + fy * fy - r * r;
        
        double discriminant = b * b - 4 * a * c;
        
        if (discriminant < 0) {
            return new RaycastResult();
        }
        
        double sqrtDisc = Math.sqrt(discriminant);
        double t = (-b - sqrtDisc) / (2 * a);
        
        if (t < 0) {
            t = (-b + sqrtDisc) / (2 * a);
        }
        
        if (t < 0 || t > maxDistance) {
            return new RaycastResult();
        }
        
        RaycastResult result = new RaycastResult();
        result.hit = true;
        result.distance = t;
        result.hitX = startX + dirX * t;
        result.hitY = startY + dirY * t;
        result.normalX = (result.hitX - cx) / r;
        result.normalY = (result.hitY - cy) / r;
        result.collider = circle;
        result.gameObject = circle.getOwner();
        
        return result;
    }
    
    /**
     * Raycasts against an AABB
     */
    private static RaycastResult raycastAABB(double startX, double startY,
                                              double dirX, double dirY,
                                              double maxDistance,
                                              AABBCollider aabb) {
        double tmin = 0;
        double tmax = maxDistance;
        
        double minX = aabb.getMinX(), maxX = aabb.getMaxX();
        double minY = aabb.getMinY(), maxY = aabb.getMaxY();
        
        double normalX = 0, normalY = 0;
        
        // X axis
        if (Math.abs(dirX) < 0.0001) {
            if (startX < minX || startX > maxX) return new RaycastResult();
        } else {
            double t1 = (minX - startX) / dirX;
            double t2 = (maxX - startX) / dirX;
            double n = -1;
            
            if (t1 > t2) { double temp = t1; t1 = t2; t2 = temp; n = 1; }
            
            if (t1 > tmin) { tmin = t1; normalX = n * Math.signum(dirX) * -1; normalY = 0; }
            if (t2 < tmax) tmax = t2;
            
            if (tmin > tmax) return new RaycastResult();
        }
        
        // Y axis
        if (Math.abs(dirY) < 0.0001) {
            if (startY < minY || startY > maxY) return new RaycastResult();
        } else {
            double t1 = (minY - startY) / dirY;
            double t2 = (maxY - startY) / dirY;
            double n = -1;
            
            if (t1 > t2) { double temp = t1; t1 = t2; t2 = temp; n = 1; }
            
            if (t1 > tmin) { tmin = t1; normalX = 0; normalY = n * Math.signum(dirY) * -1; }
            if (t2 < tmax) tmax = t2;
            
            if (tmin > tmax) return new RaycastResult();
        }
        
        if (tmin < 0 || tmin > maxDistance) return new RaycastResult();
        
        RaycastResult result = new RaycastResult();
        result.hit = true;
        result.distance = tmin;
        result.hitX = startX + dirX * tmin;
        result.hitY = startY + dirY * tmin;
        result.normalX = normalX;
        result.normalY = normalY;
        result.collider = aabb;
        result.gameObject = aabb.getOwner();
        
        return result;
    }
    
    /**
     * Result of a raycast operation
     */
    public static class RaycastResult {
        public boolean hit = false;
        public double distance;
        public double hitX, hitY;
        public double normalX, normalY;
        public Collider collider;
        public GameObject gameObject;
    }
    
    // ==================== COLLISION MANAGER ====================
    
    /**
     * Main collision manager - handles the full collision pipeline
     */
    public static class CollisionManager {
        private SpatialGrid spatialGrid;
        private List<Collider> colliders = new ArrayList<>();
        private List<CollisionResult> currentCollisions = new ArrayList<>();
        private Set<ColliderPair> previousCollisions = new HashSet<>();
        
        // Debug rendering
        private boolean debugDraw = false;
        
        public CollisionManager() {
            this(128); // Default cell size
        }
        
        public CollisionManager(int cellSize) {
            this.spatialGrid = new SpatialGrid(cellSize);
        }
        
        /**
         * Registers a collider with the manager
         */
        public void addCollider(Collider collider) {
            if (!colliders.contains(collider)) {
                colliders.add(collider);
            }
        }
        
        /**
         * Removes a collider from the manager
         */
        public void removeCollider(Collider collider) {
            colliders.remove(collider);
        }
        
        /**
         * Clears all colliders
         */
        public void clear() {
            colliders.clear();
            currentCollisions.clear();
            previousCollisions.clear();
        }
        
        /**
         * Gets all registered colliders
         */
        public List<Collider> getColliders() {
            return colliders;
        }
        
        /**
         * Updates collision detection - call once per frame
         */
        public void update() {
            currentCollisions.clear();
            
            // Broad phase - populate spatial grid
            spatialGrid.clear();
            for (Collider c : colliders) {
                spatialGrid.insert(c);
            }
            
            // Get potential pairs from spatial grid
            List<ColliderPair> potentialPairs = spatialGrid.getPotentialPairs();
            
            // Narrow phase - precise collision testing
            Set<ColliderPair> newCollisions = new HashSet<>();
            
            for (ColliderPair pair : potentialPairs) {
                CollisionResult result = testCollision(pair.a, pair.b);
                
                if (result.collided) {
                    currentCollisions.add(result);
                    newCollisions.add(pair);
                    
                    // Handle collision/trigger callbacks
                    handleCollisionCallbacks(pair, result, !previousCollisions.contains(pair));
                    
                    // Apply collision resolution if in COLLISION mode
                    if (pair.a.getMode() == CollisionMode.COLLISION && 
                        pair.b.getMode() == CollisionMode.COLLISION) {
                        resolveCollision(result);
                    }
                }
            }
            
            // Check for collision exits
            for (ColliderPair oldPair : previousCollisions) {
                if (!newCollisions.contains(oldPair)) {
                    // Collision ended
                    if (oldPair.a.isEnabled() && oldPair.b.isEnabled()) {
                        // Notify exit (could add onCollisionExit callback here)
                    }
                }
            }
            
            previousCollisions = newCollisions;
        }
        
        /**
         * Handles collision callbacks to game objects
         */
        private void handleCollisionCallbacks(ColliderPair pair, CollisionResult result, boolean isNewCollision) {
            GameObject objA = pair.a.getOwner();
            GameObject objB = pair.b.getOwner();
            
            // Notify both objects
            objA.notifyCollision(objB);
            objB.notifyCollision(objA);
        }
        
        /**
         * Resolves collision by applying MTV
         */
        private void resolveCollision(CollisionResult result) {
            // Move object A by half the MTV
            // Move object B by negative half the MTV
            // This distributes the separation equally
            
            double halfMtvX = result.mtvX * 0.5;
            double halfMtvY = result.mtvY * 0.5;
            
            result.objectA.setX(result.objectA.getX() - halfMtvX);
            result.objectA.setY(result.objectA.getY() - halfMtvY);
            
            result.objectB.setX(result.objectB.getX() + halfMtvX);
            result.objectB.setY(result.objectB.getY() + halfMtvY);
        }
        
        /**
         * Performs CCD check for a fast-moving object
         */
        public RaycastResult sweepTest(Collider movingCollider, double velocityX, double velocityY) {
            if (!movingCollider.useCCD()) return new RaycastResult();
            
            double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            if (speed < 0.001) return new RaycastResult();
            
            // Raycast from current position in velocity direction
            double startX = movingCollider.getWorldX();
            double startY = movingCollider.getWorldY();
            double dirX = velocityX / speed;
            double dirY = velocityY / speed;
            
            // Exclude the moving collider from the search
            List<Collider> others = new ArrayList<>(colliders);
            others.remove(movingCollider);
            
            return raycast(startX, startY, dirX, dirY, speed, others);
        }
        
        /**
         * Gets current frame's collisions
         */
        public List<CollisionResult> getCurrentCollisions() {
            return currentCollisions;
        }
        
        /**
         * Debug render all colliders
         */
        public void debugRender(Graphics2D g2d) {
            if (!debugDraw) return;
            
            for (Collider c : colliders) {
                c.debugRender(g2d);
            }
        }
        
        public void setDebugDraw(boolean debug) {
            this.debugDraw = debug;
        }
        
        public boolean isDebugDraw() {
            return debugDraw;
        }
    }
}
