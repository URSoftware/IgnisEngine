package com.ignis.editor.fx;

import com.ignis.imageeditor.ImageDocument;
import com.ignis.imageeditor.PaintCanvas.ToolType;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * JavaFX implementation of the PaintCanvas drawing surface.
 * Ports com.ignis.imageeditor.PaintCanvas.
 */
public class FxPaintCanvas extends Canvas {

    private static final int MAX_UNDO = 25;
    private static final int CHECKER = 8;

    private ImageDocument document;
    private ToolType tool = ToolType.PENCIL;
    private java.awt.Color color = java.awt.Color.BLACK;
    private int brushSize = 4;
    private double zoom = 1.0;

    private Point dragStart;
    private Point dragCurrent;
    private boolean painting;
    private Point mouseImagePos;

    // Selection
    private Rectangle selection = null;
    private float dashPhase = 0.0f;
    private AnimationTimer selectionTimer;

    // Panning
    private boolean spacePressed = false;
    private Point lastScreenPos = null;

    // Stabilizer (Tablet/Stylus connection)
    private boolean useStabilizer = true;
    private double stabilizerFactor = 0.25; // Lower values = smoother
    private double smoothedX;
    private double smoothedY;
    private boolean firstStabilizerPoint = true;

    // Brush Tip caching
    private BufferedImage brushTip = null;
    private java.awt.Color lastBrushTipColor = null;
    private int lastBrushTipSize = -1;
    private ToolType lastBrushTipTool = null;

    // Grid size (0 = None, 1 = Pixel, 8 = 8x8, 16 = 16x16, 32 = 32x32)
    private int gridSize = 1;

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();

    public interface CanvasListener {
        void onDocumentChanged();
        void onColorPicked(java.awt.Color picked);
        void onMouseMoved(Point imagePos);
        void onHistoryUpdated();
    }

    private CanvasListener listener;

    public static class UndoEntry {
        final ImageDocument.Layer layer;
        final BufferedImage snapshot;
        final String actionName;

        UndoEntry(ImageDocument.Layer layer, BufferedImage snapshot, String actionName) {
            this.layer = layer;
            this.snapshot = snapshot;
            this.actionName = actionName;
        }
    }

    public FxPaintCanvas(ImageDocument document) {
        this.document = document;
        updateSize();

        setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (spacePressed) {
                lastScreenPos = new Point((int) e.getScreenX(), (int) e.getScreenY());
                return;
            }
            if (this.document.getActiveLayer().isLocked()) {
                return;
            }
            mouseImagePos = toImage(new Point((int) e.getX(), (int) e.getY()));
            onPress(mouseImagePos);
            if (listener != null) {
                listener.onMouseMoved(mouseImagePos);
            }
        });

        setOnMouseDragged(e -> {
            if (spacePressed && lastScreenPos != null) {
                // Panning logic delegated to parent ScrollPane if preferred,
                // but can support local coordinate panning updates
                return;
            }
            if (this.document.getActiveLayer().isLocked()) {
                return;
            }
            mouseImagePos = toImage(new Point((int) e.getX(), (int) e.getY()));
            onDrag(mouseImagePos);
            if (listener != null) {
                listener.onMouseMoved(mouseImagePos);
            }
        });

        setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (spacePressed) {
                lastScreenPos = null;
                return;
            }
            if (this.document.getActiveLayer().isLocked()) {
                return;
            }
            onRelease(toImage(new Point((int) e.getX(), (int) e.getY())));
        });

        setOnMouseMoved(e -> {
            mouseImagePos = toImage(new Point((int) e.getX(), (int) e.getY()));
            draw();
            if (listener != null) {
                listener.onMouseMoved(mouseImagePos);
            }
        });

        setOnMouseExited(e -> {
            mouseImagePos = null;
            draw();
            if (listener != null) {
                listener.onMouseMoved(null);
            }
        });

        // Dash selection animation timer
        selectionTimer = new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 100_000_000L) { // 100ms
                    if (selection != null) {
                        dashPhase += 1.0f;
                        draw();
                    }
                    lastUpdate = now;
                }
            }
        };
        selectionTimer.start();
        draw();
    }

    public void setDocument(ImageDocument document) {
        this.document = document;
        undoStack.clear();
        redoStack.clear();
        selection = null;
        updateSize();
        draw();
    }

    public ImageDocument getDocument() {
        return document;
    }

    public void setListener(CanvasListener listener) {
        this.listener = listener;
    }

    public void setTool(ToolType tool) {
        this.tool = tool;
        if (tool != ToolType.SELECTION) {
            selection = null;
        }
        draw();
    }

    public ToolType getTool() {
        return tool;
    }

    public void setColor(java.awt.Color color) {
        this.color = color;
    }

    public java.awt.Color getColor() {
        return color;
    }

    public void setBrushSize(int brushSize) {
        this.brushSize = Math.max(1, brushSize);
    }

    public int getBrushSize() {
        return brushSize;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.125, Math.min(32, zoom));
        updateSize();
        draw();
    }

    public double getZoom() {
        return zoom;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
        draw();
    }

    public int getGridSize() {
        return gridSize;
    }

    public void setUseStabilizer(boolean use) {
        this.useStabilizer = use;
    }

    public boolean isUseStabilizer() {
        return useStabilizer;
    }

    public Rectangle getSelection() {
        return selection;
    }

    public void setSelection(Rectangle r) {
        this.selection = r;
        draw();
    }

    private void updateSize() {
        setWidth(Math.ceil(document.getWidth() * zoom));
        setHeight(Math.ceil(document.getHeight() * zoom));
    }

    private Point toImage(Point p) {
        return new Point((int) (p.x / zoom), (int) (p.y / zoom));
    }

    // ==================== TOOL HANDLING ====================

    private void onPress(Point p) {
        if (tool == ToolType.EYEDROPPER) {
            pickColor(p);
            return;
        }
        if (tool == ToolType.FILL) {
            pushUndo("Flood Fill");
            floodFill(p);
            fireChanged();
            return;
        }
        
        pushUndo(getToolName(tool));
        painting = true;
        dragStart = p;
        dragCurrent = p;

        if (useStabilizer && (tool == ToolType.PENCIL || tool == ToolType.ERASER || tool == ToolType.BRUSH)) {
            smoothedX = p.x;
            smoothedY = p.y;
            firstStabilizerPoint = true;
        }

        if (tool == ToolType.PENCIL || tool == ToolType.ERASER || tool == ToolType.BRUSH) {
            stroke(p, p);
        }
        draw();
    }

    private void onDrag(Point p) {
        if (!painting) {
            return;
        }

        if (tool == ToolType.PENCIL || tool == ToolType.ERASER || tool == ToolType.BRUSH) {
            Point from = dragCurrent;
            Point to = p;

            if (useStabilizer) {
                if (firstStabilizerPoint) {
                    smoothedX = p.x;
                    smoothedY = p.y;
                    firstStabilizerPoint = false;
                } else {
                    smoothedX = smoothedX + (p.x - smoothedX) * stabilizerFactor;
                    smoothedY = smoothedY + (p.y - smoothedY) * stabilizerFactor;
                }
                to = new Point((int) Math.round(smoothedX), (int) Math.round(smoothedY));
            }

            stroke(from, to);
            dragCurrent = to;
        } else if (tool == ToolType.MOVE) {
            moveLayerPixels(p);
            dragCurrent = p;
        } else {
            dragCurrent = p;
        }
        draw();
    }

    private void onRelease(Point p) {
        if (!painting) {
            return;
        }
        painting = false;
        
        if (tool == ToolType.LINE || tool == ToolType.RECTANGLE || tool == ToolType.ELLIPSE) {
            Graphics2D g = activeGraphics();
            drawShape(g, dragStart, p);
            g.dispose();
        } else if (tool == ToolType.SELECTION) {
            int x = Math.min(dragStart.x, p.x);
            int y = Math.min(dragStart.y, p.y);
            int w = Math.abs(dragStart.x - p.x);
            int h = Math.abs(dragStart.y - p.y);
            if (w > 1 && h > 1) {
                selection = new Rectangle(x, y, w, h);
            } else {
                selection = null;
            }
        } else if (tool == ToolType.MOVE) {
            int dx = p.x - dragStart.x;
            int dy = p.y - dragStart.y;
            if (selection != null && (dx != 0 || dy != 0)) {
                selection.translate(dx, dy);
            }
        }
        
        dragStart = null;
        dragCurrent = null;
        fireChanged();
    }

    private String getToolName(ToolType t) {
        return switch (t) {
            case PENCIL -> "Pencil Draw";
            case ERASER -> "Eraser Clean";
            case LINE -> "Draw Line";
            case RECTANGLE -> "Draw Rect";
            case ELLIPSE -> "Draw Oval";
            case FILL -> "Flood Fill";
            case BRUSH -> "Brush Stroke";
            case SELECTION -> "Select Region";
            case MOVE -> "Move Pixels";
            default -> "Edit";
        };
    }

    private Graphics2D activeGraphics() {
        Graphics2D g = document.getActiveLayer().getImage().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (selection != null) {
            g.setClip(selection);
        }

        if (tool == ToolType.ERASER) {
            g.setComposite(AlphaComposite.Clear);
        } else {
            g.setColor(color);
        }
        g.setStroke(new BasicStroke(brushSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return g;
    }

    private void updateBrushTip() {
        if (brushTip != null && lastBrushTipColor == color && lastBrushTipSize == brushSize && lastBrushTipTool == tool) {
            return;
        }
        
        int r = Math.max(1, brushSize);
        brushTip = new BufferedImage(r, r, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = brushTip.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        double radius = r / 2.0;
        for (int y = 0; y < r; y++) {
            for (int x = 0; x < r; x++) {
                double dx = x - radius + 0.5;
                double dy = y - radius + 0.5;
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist < radius) {
                    double alphaFactor = 1.0 - (dist / radius);
                    alphaFactor = Math.pow(alphaFactor, 1.5);
                    
                    if (tool == ToolType.ERASER) {
                        int alpha = (int) (alphaFactor * 255);
                        brushTip.setRGB(x, y, alpha << 24);
                    } else {
                        int alpha = (int) (alphaFactor * color.getAlpha());
                        int rgb = (alpha << 24) | (color.getRGB() & 0x00FFFFFF);
                        brushTip.setRGB(x, y, rgb);
                    }
                } else {
                    brushTip.setRGB(x, y, 0);
                }
            }
        }
        g.dispose();
        
        lastBrushTipColor = color;
        lastBrushTipSize = brushSize;
        lastBrushTipTool = tool;
    }

    private void stroke(Point from, Point to) {
        if (tool == ToolType.BRUSH) {
            updateBrushTip();
            Graphics2D g = document.getActiveLayer().getImage().createGraphics();
            if (selection != null) {
                g.setClip(selection);
            }
            
            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dist = Math.sqrt(dx*dx + dy*dy);
            int steps = (int) Math.ceil(dist);
            
            double radius = brushSize / 2.0;
            if (steps == 0) {
                g.drawImage(brushTip, (int) Math.round(from.x - radius), (int) Math.round(from.y - radius), null);
            } else {
                for (int i = 0; i <= steps; i++) {
                    double t = (double) i / steps;
                    double cx = from.x + dx * t;
                    double cy = from.y + dy * t;
                    g.drawImage(brushTip, (int) Math.round(cx - radius), (int) Math.round(cy - radius), null);
                }
            }
            g.dispose();
        } else {
            Graphics2D g = activeGraphics();
            g.drawLine(from.x, from.y, to.x, to.y);
            g.dispose();
        }
    }

    private void drawShape(Graphics2D g, Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        int w = Math.abs(a.x - b.x);
        int h = Math.abs(a.y - b.y);
        switch (tool) {
            case LINE -> g.drawLine(a.x, a.y, b.x, b.y);
            case RECTANGLE -> g.drawRect(x, y, w, h);
            case ELLIPSE -> g.drawOval(x, y, w, h);
            default -> {
            }
        }
    }

    private void moveLayerPixels(Point p) {
        int dx = p.x - dragStart.x;
        int dy = p.y - dragStart.y;
        if (dx == 0 && dy == 0) return;

        if (undoStack.isEmpty()) return;
        
        BufferedImage backup = undoStack.peek().snapshot;
        ImageDocument.Layer activeLayer = document.getActiveLayer();

        Graphics2D g = activeLayer.getImage().createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, document.getWidth(), document.getHeight());
        g.setComposite(AlphaComposite.SrcOver);

        if (selection == null) {
            g.drawImage(backup, dx, dy, null);
        } else {
            g.drawImage(backup, 0, 0, null);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(selection.x, selection.y, selection.width, selection.height);
            g.setComposite(AlphaComposite.SrcOver);
            try {
                BufferedImage sub = backup.getSubimage(selection.x, selection.y, selection.width, selection.height);
                g.drawImage(sub, selection.x + dx, selection.y + dy, null);
            } catch (Exception ex) {
                g.drawImage(backup, dx, dy, null);
            }
        }
        g.dispose();
    }

    private void pickColor(Point p) {
        BufferedImage composite = document.composite();
        if (p.x >= 0 && p.y >= 0 && p.x < composite.getWidth() && p.y < composite.getHeight()) {
            java.awt.Color picked = new java.awt.Color(composite.getRGB(p.x, p.y), true);
            if (picked.getAlpha() > 0) {
                color = new java.awt.Color(picked.getRed(), picked.getGreen(), picked.getBlue());
                if (listener != null) {
                    listener.onColorPicked(color);
                }
            }
        }
    }

    private void floodFill(Point p) {
        BufferedImage img = document.getActiveLayer().getImage();
        int w = img.getWidth();
        int h = img.getHeight();
        if (p.x < 0 || p.y < 0 || p.x >= w || p.y >= h) {
            return;
        }
        int target = img.getRGB(p.x, p.y);
        int replacement = color.getRGB();
        if (target == replacement) {
            return;
        }
        Deque<Point> queue = new ArrayDeque<>();
        queue.add(p);
        while (!queue.isEmpty()) {
            Point cur = queue.poll();
            if (cur.x < 0 || cur.y < 0 || cur.x >= w || cur.y >= h
                    || img.getRGB(cur.x, cur.y) != target) {
                continue;
            }
            
            if (selection != null && !selection.contains(cur)) {
                continue;
            }

            int west = cur.x;
            int east = cur.x;
            while (west > 0 && img.getRGB(west - 1, cur.y) == target && (selection == null || selection.contains(west - 1, cur.y))) {
                west--;
            }
            while (east < w - 1 && img.getRGB(east + 1, cur.y) == target && (selection == null || selection.contains(east + 1, cur.y))) {
                east++;
            }
            for (int x = west; x <= east; x++) {
                img.setRGB(x, cur.y, replacement);
                if (cur.y > 0 && img.getRGB(x, cur.y - 1) == target) {
                    queue.add(new Point(x, cur.y - 1));
                }
                if (cur.y < h - 1 && img.getRGB(x, cur.y + 1) == target) {
                    queue.add(new Point(x, cur.y + 1));
                }
            }
        }
        draw();
    }

    // ==================== UNDO / REDO ====================

    private void pushUndo(String actionName) {
        undoStack.push(new UndoEntry(document.getActiveLayer(), document.getActiveLayer().snapshot(), actionName));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        if (listener != null) {
            listener.onHistoryUpdated();
        }
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        UndoEntry entry = undoStack.pop();
        redoStack.push(new UndoEntry(entry.layer, entry.layer.snapshot(), entry.actionName));
        entry.layer.restore(entry.snapshot);
        fireChanged();
        if (listener != null) {
            listener.onHistoryUpdated();
        }
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        UndoEntry entry = redoStack.pop();
        undoStack.push(new UndoEntry(entry.layer, entry.layer.snapshot(), entry.actionName));
        entry.layer.restore(entry.snapshot);
        fireChanged();
        if (listener != null) {
            listener.onHistoryUpdated();
        }
    }

    public List<UndoEntry> getUndoStack() {
        return new ArrayList<>(undoStack);
    }

    public List<UndoEntry> getRedoStack() {
        return new ArrayList<>(redoStack);
    }

    public void revertToHistoryStep(int stepsBack) {
        if (stepsBack <= 0 || stepsBack > undoStack.size()) return;
        
        UndoEntry target = null;
        for (int i = 0; i < stepsBack; i++) {
            UndoEntry entry = undoStack.pop();
            redoStack.push(new UndoEntry(entry.layer, entry.layer.snapshot(), entry.actionName));
            target = entry;
        }
        if (target != null) {
            target.layer.restore(target.snapshot);
        }
        fireChanged();
        if (listener != null) {
            listener.onHistoryUpdated();
        }
    }

    private void fireChanged() {
        draw();
        if (listener != null) {
            listener.onDocumentChanged();
        }
    }

    // ==================== RENDERING ====================

    private WritableImage fxImage;

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        gc.clearRect(0, 0, w, h);

        if (w <= 0 || h <= 0) return;

        // Transparency checkerboard
        for (int y = 0; y < h; y += CHECKER) {
            for (int x = 0; x < w; x += CHECKER) {
                boolean dark = ((x / CHECKER) + (y / CHECKER)) % 2 == 0;
                gc.setFill(dark ? Color.web("#3c3c3c") : Color.web("#505050"));
                gc.fillRect(x, y, CHECKER, CHECKER);
            }
        }

        // Composite image render
        BufferedImage composite = document.composite();
        if (fxImage == null || fxImage.getWidth() != composite.getWidth() || fxImage.getHeight() != composite.getHeight()) {
            fxImage = new WritableImage(composite.getWidth(), composite.getHeight());
        }
        SwingFXUtils.toFXImage(composite, fxImage);
        gc.setImageSmoothing(false);
        gc.drawImage(fxImage, 0, 0, w, h);

        // Shape/Selection Drag Previews
        if (painting && dragStart != null && dragCurrent != null) {
            if (tool == ToolType.LINE || tool == ToolType.RECTANGLE || tool == ToolType.ELLIPSE) {
                gc.setStroke(Color.rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0));
                gc.setLineWidth(brushSize * zoom);
                gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
                gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

                double ax = dragStart.x * zoom;
                double ay = dragStart.y * zoom;
                double bx = dragCurrent.x * zoom;
                double by = dragCurrent.y * zoom;
                double minX = Math.min(ax, bx);
                double minY = Math.min(ay, by);
                double rectW = Math.abs(ax - bx);
                double rectH = Math.abs(ay - by);

                switch (tool) {
                    case LINE -> gc.strokeLine(ax, ay, bx, by);
                    case RECTANGLE -> gc.strokeRect(minX, minY, rectW, rectH);
                    case ELLIPSE -> gc.strokeOval(minX, minY, rectW, rectH);
                    default -> {}
                }
            } else if (tool == ToolType.SELECTION) {
                double ax = dragStart.x * zoom;
                double ay = dragStart.y * zoom;
                double bx = dragCurrent.x * zoom;
                double by = dragCurrent.y * zoom;
                double minX = Math.min(ax, bx);
                double minY = Math.min(ay, by);
                double rectW = Math.abs(ax - bx);
                double rectH = Math.abs(ay - by);

                gc.setFill(Color.web("#0078d73c"));
                gc.fillRect(minX, minY, rectW, rectH);

                gc.setStroke(Color.web("#0078d7"));
                gc.setLineWidth(1.0);
                gc.setLineDashes(4f);
                gc.setLineDashOffset(0f);
                gc.strokeRect(minX, minY, rectW, rectH);
                gc.setLineDashes(null);
            }
        }

        // Marching ants selection border
        if (selection != null) {
            double sx = selection.x * zoom;
            double sy = selection.y * zoom;
            double sw = selection.width * zoom;
            double sh = selection.height * zoom;

            gc.setLineWidth(1.0);
            gc.setStroke(Color.WHITE);
            gc.strokeRect(sx, sy, sw, sh);

            // Marching ants dashed outline
            gc.setStroke(Color.BLACK);
            gc.setLineDashes(4.0f);
            gc.setLineDashOffset(dashPhase);
            gc.strokeRect(sx, sy, sw, sh);
            gc.setLineDashes(null);
        }

        // Grid lines
        if (gridSize > 0 && zoom >= 2.0) {
            gc.setStroke(Color.rgb(128, 128, 128, 60 / 255.0));
            gc.setLineWidth(1.0);
            for (double x = gridSize; x < document.getWidth(); x += gridSize) {
                double px = x * zoom;
                gc.strokeLine(px, 0, px, h);
            }
            for (double y = gridSize; y < document.getHeight(); y += gridSize) {
                double py = y * zoom;
                gc.strokeLine(0, py, w, py);
            }
        }

        // Brush cursor preview ring
        if (mouseImagePos != null && (tool == ToolType.PENCIL || tool == ToolType.ERASER || tool == ToolType.BRUSH)) {
            double cx = (mouseImagePos.x + 0.5) * zoom;
            double cy = (mouseImagePos.y + 0.5) * zoom;
            double r = (brushSize * zoom) / 2.0;

            gc.setLineWidth(1.0);
            gc.setStroke(Color.WHITE);
            gc.strokeOval(cx - r - 1, cy - r - 1, 2 * r + 2, 2 * r + 2);

            gc.setStroke(Color.BLACK);
            gc.strokeOval(cx - r, cy - r, 2 * r, 2 * r);
        }

        // Canvas border
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.0);
        gc.strokeRect(0, 0, w, h);
    }
}
