package com.ignis.imageeditor;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drawing surface of the image editor. Renders the document composite over a
 * transparency checkerboard, applies the active tool to the active layer and
 * keeps a bounded undo/redo stack of layer snapshots.
 */
public class PaintCanvas extends JPanel {

    /** Tools available in the editor. */
    public enum ToolType {
        PENCIL, ERASER, LINE, RECTANGLE, ELLIPSE, FILL, EYEDROPPER
    }

    private static final int MAX_UNDO = 25;
    private static final int CHECKER = 8;

    private ImageDocument document;
    private ToolType tool = ToolType.PENCIL;
    private Color color = Color.BLACK;
    private int brushSize = 4;
    private double zoom = 1.0;

    private Point dragStart;
    private Point dragCurrent;
    private boolean painting;
    private Point mouseImagePos;

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();

    /** Notified when the document changes (repaint previews, title dirty flag). */
    public interface CanvasListener {
        void onDocumentChanged();

        void onColorPicked(Color picked);

        void onMouseMoved(Point imagePos);
    }

    private CanvasListener listener;

    private static class UndoEntry {
        final ImageDocument.Layer layer;
        final BufferedImage snapshot;

        UndoEntry(ImageDocument.Layer layer, BufferedImage snapshot) {
            this.layer = layer;
            this.snapshot = snapshot;
        }
    }

    public PaintCanvas(ImageDocument document) {
        this.document = document;
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseImagePos = toImage(e.getPoint());
                onPress(mouseImagePos);
                if (listener != null) {
                    listener.onMouseMoved(mouseImagePos);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseImagePos = toImage(e.getPoint());
                onDrag(mouseImagePos);
                if (listener != null) {
                    listener.onMouseMoved(mouseImagePos);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onRelease(toImage(e.getPoint()));
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseImagePos = toImage(e.getPoint());
                repaint();
                if (listener != null) {
                    listener.onMouseMoved(mouseImagePos);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseImagePos = null;
                repaint();
                if (listener != null) {
                    listener.onMouseMoved(null);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        updatePreferredSize();
    }

    // ==================== STATE ====================

    public void setDocument(ImageDocument document) {
        this.document = document;
        undoStack.clear();
        redoStack.clear();
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public ImageDocument getDocument() {
        return document;
    }

    public void setListener(CanvasListener listener) {
        this.listener = listener;
    }

    public void setTool(ToolType tool) {
        this.tool = tool;
    }

    public ToolType getTool() {
        return tool;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
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
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public double getZoom() {
        return zoom;
    }

    private void updatePreferredSize() {
        setPreferredSize(new Dimension(
                (int) Math.ceil(document.getWidth() * zoom),
                (int) Math.ceil(document.getHeight() * zoom)));
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
            pushUndo();
            floodFill(p);
            fireChanged();
            return;
        }
        pushUndo();
        painting = true;
        dragStart = p;
        dragCurrent = p;
        if (tool == ToolType.PENCIL || tool == ToolType.ERASER) {
            stroke(p, p);
        }
        repaint();
    }

    private void onDrag(Point p) {
        if (!painting) {
            return;
        }
        if (tool == ToolType.PENCIL || tool == ToolType.ERASER) {
            stroke(dragCurrent, p);
            dragCurrent = p;
        } else {
            // Shape preview: just remember the current corner
            dragCurrent = p;
        }
        repaint();
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
        }
        dragStart = null;
        dragCurrent = null;
        fireChanged();
    }

    private Graphics2D activeGraphics() {
        Graphics2D g = document.getActiveLayer().getImage().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (tool == ToolType.ERASER) {
            g.setComposite(AlphaComposite.Clear);
        } else {
            g.setColor(color);
        }
        g.setStroke(new BasicStroke(brushSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return g;
    }

    private void stroke(Point from, Point to) {
        Graphics2D g = activeGraphics();
        g.drawLine(from.x, from.y, to.x, to.y);
        g.dispose();
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

    private void pickColor(Point p) {
        BufferedImage composite = document.composite();
        if (p.x >= 0 && p.y >= 0 && p.x < composite.getWidth() && p.y < composite.getHeight()) {
            Color picked = new Color(composite.getRGB(p.x, p.y), true);
            if (picked.getAlpha() > 0) {
                color = new Color(picked.getRed(), picked.getGreen(), picked.getBlue());
                if (listener != null) {
                    listener.onColorPicked(color);
                }
            }
        }
    }

    /** Scanline flood fill on the active layer. */
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
            int west = cur.x;
            int east = cur.x;
            while (west > 0 && img.getRGB(west - 1, cur.y) == target) {
                west--;
            }
            while (east < w - 1 && img.getRGB(east + 1, cur.y) == target) {
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
        repaint();
    }

    // ==================== UNDO / REDO ====================

    private void pushUndo() {
        undoStack.push(new UndoEntry(document.getActiveLayer(), document.getActiveLayer().snapshot()));
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        UndoEntry entry = undoStack.pop();
        redoStack.push(new UndoEntry(entry.layer, entry.layer.snapshot()));
        entry.layer.restore(entry.snapshot);
        fireChanged();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        UndoEntry entry = redoStack.pop();
        undoStack.push(new UndoEntry(entry.layer, entry.layer.snapshot()));
        entry.layer.restore(entry.snapshot);
        fireChanged();
    }

    private void fireChanged() {
        repaint();
        if (listener != null) {
            listener.onDocumentChanged();
        }
    }

    // ==================== RENDERING ====================

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;

        int w = (int) Math.ceil(document.getWidth() * zoom);
        int h = (int) Math.ceil(document.getHeight() * zoom);

        // Transparency checkerboard
        for (int y = 0; y < h; y += CHECKER) {
            for (int x = 0; x < w; x += CHECKER) {
                boolean dark = ((x / CHECKER) + (y / CHECKER)) % 2 == 0;
                g.setColor(dark ? new Color(60, 60, 60) : new Color(80, 80, 80));
                g.fillRect(x, y, CHECKER, CHECKER);
            }
        }

        // Document composite (nearest neighbor keeps pixel art crisp when zoomed)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(document.composite(), 0, 0, w, h, null);

        // Shape preview while dragging
        if (painting && dragStart != null && dragCurrent != null
                && (tool == ToolType.LINE || tool == ToolType.RECTANGLE || tool == ToolType.ELLIPSE)) {
            g.setColor(color);
            g.setStroke(new BasicStroke((float) (brushSize * zoom),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Point a = new Point((int) (dragStart.x * zoom), (int) (dragStart.y * zoom));
            Point b = new Point((int) (dragCurrent.x * zoom), (int) (dragCurrent.y * zoom));
            int x = Math.min(a.x, b.x);
            int y = Math.min(a.y, b.y);
            int pw = Math.abs(a.x - b.x);
            int ph = Math.abs(a.y - b.y);
            switch (tool) {
                case LINE -> g.drawLine(a.x, a.y, b.x, b.y);
                case RECTANGLE -> g.drawRect(x, y, pw, ph);
                case ELLIPSE -> g.drawOval(x, y, pw, ph);
                default -> {
                }
            }
        }

        // Pixel grid (only when zoomed in at 400% or more)
        if (zoom >= 4.0) {
            g.setColor(new Color(128, 128, 128, 80)); // Semi-transparent grey
            g.setStroke(new BasicStroke(1.0f));
            for (int x = 1; x < document.getWidth(); x++) {
                int px = (int) (x * zoom);
                g.drawLine(px, 0, px, h);
            }
            for (int y = 1; y < document.getHeight(); y++) {
                int py = (int) (y * zoom);
                g.drawLine(0, py, w, py);
            }
        }

        // Brush area of influence preview (non-destructive)
        if (mouseImagePos != null && (tool == ToolType.PENCIL || tool == ToolType.ERASER)) {
            double cx = (mouseImagePos.x + 0.5) * zoom;
            double cy = (mouseImagePos.y + 0.5) * zoom;
            double r = (brushSize * zoom) / 2.0;

            g.setStroke(new BasicStroke(1.0f));
            
            // Outer white ring
            g.setColor(Color.WHITE);
            g.drawOval((int) (cx - r - 1), (int) (cy - r - 1), (int) (2 * r + 2), (int) (2 * r + 2));
            
            // Inner black ring
            g.setColor(Color.BLACK);
            g.drawOval((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
        }

        // Canvas border
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, w - 1, h - 1);
    }
}
