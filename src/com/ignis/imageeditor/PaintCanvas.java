package com.ignis.imageeditor;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.JViewport;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Drawing surface of the image editor. Renders the document composite over a
 * transparency checkerboard, applies the active tool to the active layer and
 * keeps a bounded undo/redo stack of layer snapshots.
 */
public class PaintCanvas extends JPanel {

    /** Tools available in the editor. */
    public enum ToolType {
        PENCIL, ERASER, LINE, RECTANGLE, ELLIPSE, FILL, EYEDROPPER, BRUSH, SELECTION, MOVE
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

    // Selection
    private Rectangle selection = null;
    private float dashPhase = 0.0f;
    private Timer selectionTimer;

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
    private Color lastBrushTipColor = null;
    private int lastBrushTipSize = -1;
    private ToolType lastBrushTipTool = null;

    // Grid size (0 = None, 1 = Pixel, 8 = 8x8, 16 = 16x16, 32 = 32x32)
    private int gridSize = 1;

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();

    /** Notified when the document changes (repaint previews, title dirty flag). */
    public interface CanvasListener {
        void onDocumentChanged();
        void onColorPicked(Color picked);
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

    public PaintCanvas(ImageDocument document) {
        this.document = document;
        setOpaque(true);
        setFocusable(true);

        setupKeyboardActions();

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (spacePressed) {
                    lastScreenPos = e.getLocationOnScreen();
                    return;
                }
                
                // Block if layer is locked
                if (document.getActiveLayer().isLocked()) {
                    return;
                }

                mouseImagePos = toImage(e.getPoint());
                onPress(mouseImagePos);
                if (listener != null) {
                    listener.onMouseMoved(mouseImagePos);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (spacePressed && lastScreenPos != null) {
                    Point currentScreenPos = e.getLocationOnScreen();
                    int dx = currentScreenPos.x - lastScreenPos.x;
                    int dy = currentScreenPos.y - lastScreenPos.y;
                    lastScreenPos = currentScreenPos;

                    Container parent = getParent();
                    if (parent instanceof JViewport) {
                        JViewport viewport = (JViewport) parent;
                        Point viewPos = viewport.getViewPosition();
                        viewPos.x = Math.max(0, viewPos.x - dx);
                        viewPos.y = Math.max(0, viewPos.y - dy);
                        viewport.setViewPosition(viewPos);
                    }
                    return;
                }

                // Block if layer is locked
                if (document.getActiveLayer().isLocked()) {
                    return;
                }

                mouseImagePos = toImage(e.getPoint());
                onDrag(mouseImagePos);
                if (listener != null) {
                    listener.onMouseMoved(mouseImagePos);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (spacePressed) {
                    lastScreenPos = null;
                    return;
                }

                // Block if layer is locked
                if (document.getActiveLayer().isLocked()) {
                    return;
                }

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

        // Dash selection animation timer
        selectionTimer = new Timer(100, (ActionEvent e) -> {
            if (selection != null) {
                dashPhase += 1.0f;
                repaint();
            }
        });
        selectionTimer.start();
    }

    private void setupKeyboardActions() {
        // Spacebar Panning key bindings
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "spacePressed");
        getActionMap().put("spacePressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!spacePressed) {
                    spacePressed = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }
        });

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "spaceReleased");
        getActionMap().put("spaceReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                spacePressed = false;
                setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    // ==================== STATE ====================

    public void setDocument(ImageDocument document) {
        this.document = document;
        undoStack.clear();
        redoStack.clear();
        selection = null;
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
        if (tool != ToolType.SELECTION) {
            selection = null;
        }
        repaint();
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

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
        repaint();
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
        repaint();
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
        repaint();
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
            // Shape preview or Selection box
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
            // Commit moved coordinates
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
                    alphaFactor = Math.pow(alphaFactor, 1.5); // Smooth ease-out falloff
                    
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
            
            // Interpolate points for smooth continuous painting
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
            // Draw shifted image
            g.drawImage(backup, dx, dy, null);
        } else {
            // Restore entire unselected area
            g.drawImage(backup, 0, 0, null);
            // Clear old selection bounds
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(selection.x, selection.y, selection.width, selection.height);
            g.setComposite(AlphaComposite.SrcOver);
            // Draw cropped subimage shifted by dx, dy
            try {
                BufferedImage sub = backup.getSubimage(selection.x, selection.y, selection.width, selection.height);
                g.drawImage(sub, selection.x + dx, selection.y + dy, null);
            } catch (Exception ex) {
                // Bounds safety fallback
                g.drawImage(backup, dx, dy, null);
            }
        }
        g.dispose();
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
            
            // Check selection bounds
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
        repaint();
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

        // Shape preview or selection drag preview while dragging
        if (painting && dragStart != null && dragCurrent != null) {
            if (tool == ToolType.LINE || tool == ToolType.RECTANGLE || tool == ToolType.ELLIPSE) {
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
                    case ELLIPSE -> g.drawOval(x, y, w, h);
                    default -> {
                    }
                }
            } else if (tool == ToolType.SELECTION) {
                g.setColor(new Color(0, 120, 215, 60));
                Point a = new Point((int) (dragStart.x * zoom), (int) (dragStart.y * zoom));
                Point b = new Point((int) (dragCurrent.x * zoom), (int) (dragCurrent.y * zoom));
                int x = Math.min(a.x, b.x);
                int y = Math.min(a.y, b.y);
                int pw = Math.abs(a.x - b.x);
                int ph = Math.abs(a.y - b.y);
                g.fillRect(x, y, pw, ph);
                
                g.setColor(new Color(0, 120, 215));
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4f, 4f}, 0.0f));
                g.drawRect(x, y, pw, ph);
            }
        }

        // Draw selection marching ants
        if (selection != null) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.0f));
            int sx = (int) (selection.x * zoom);
            int sy = (int) (selection.y * zoom);
            int sw = (int) (selection.width * zoom);
            int sh = (int) (selection.height * zoom);
            g.drawRect(sx, sy, sw, sh);

            g.setColor(Color.BLACK);
            // Dashed outline (marching ants effect)
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f, 4.0f}, dashPhase));
            g.drawRect(sx, sy, sw, sh);
        }

        // Configurable grid lines
        if (gridSize > 0 && zoom >= 2.0) {
            g.setColor(new Color(128, 128, 128, 60)); // Semi-transparent grey grid lines
            g.setStroke(new BasicStroke(1.0f));
            for (int x = gridSize; x < document.getWidth(); x += gridSize) {
                int px = (int) (x * zoom);
                g.drawLine(px, 0, px, h);
            }
            for (int y = gridSize; y < document.getHeight(); y += gridSize) {
                int py = (int) (y * zoom);
                g.drawLine(0, py, w, py);
            }
        }

        // Brush area of influence preview (non-destructive)
        if (mouseImagePos != null && (tool == ToolType.PENCIL || tool == ToolType.ERASER || tool == ToolType.BRUSH)) {
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
