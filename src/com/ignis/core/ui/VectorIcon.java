package com.ignis.core.ui;

import com.ignis.core.IgnisLogger;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Color;
import java.awt.BasicStroke;

/**
 * Scalable, high-resolution Vector Icons drawn programmatically using Graphics2D.
 * Adapts to host foreground and supports custom sizing and thematic coloring.
 */
public class VectorIcon implements Icon {

    public enum VectorIconType {
        NEW_PROJECT, OPEN_PROJECT, SAVE, PLAY, PAUSE, STOP,
        FOLDER, FILE, SCRIPT, SCENE, PREFAB, AUDIO, IMAGE, ANIMATION,
        HIERARCHY, INSPECTOR, SEARCH, SETTINGS, TOOLS, COMPONENTS,
        CONSOLE, BUILD, TIMELINE, ASSETS, REFRESH, COMPILE
    }

    private static final java.util.Map<String, VectorIcon> cache = new java.util.HashMap<>();

    public static VectorIcon get(VectorIconType type, int size) {
        String key = type.name() + "_" + size;
        return cache.computeIfAbsent(key, k -> new VectorIcon(type, size));
    }

    public static VectorIcon get(VectorIconType type, int size, Color customColor) {
        String key = type.name() + "_" + size + "_" + (customColor != null ? customColor.getRGB() : "null");
        return cache.computeIfAbsent(key, k -> new VectorIcon(type, size, customColor));
    }

    private final VectorIconType type;
    private final int size;
    private Color customColor;

    public VectorIcon(VectorIconType type) {
        this(type, 16);
    }

    public VectorIcon(VectorIconType type, int size) {
        this.type = type;
        this.size = size;
    }

    public VectorIcon(VectorIconType type, int size, Color customColor) {
        this.type = type;
        this.size = size;
        this.customColor = customColor;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.translate(x, y);

        double scale = size / 16.0;
        g2d.scale(scale, scale);

        Color drawColor = customColor;
        if (drawColor == null) {
            if (c != null && c.getForeground() != null) {
                drawColor = c.getForeground();
            } else {
                drawColor = Color.WHITE;
            }
        }
        g2d.setColor(drawColor);

        try {
            switch (type) {
                case PLAY -> {
                    g2d.setColor(customColor != null ? customColor : new Color(46, 204, 113)); // VS Code Green
                    int[] px = {3, 3, 13};
                    int[] py = {2, 14, 8};
                    g2d.fillPolygon(px, py, 3);
                }
                case PAUSE -> {
                    g2d.setColor(customColor != null ? customColor : new Color(241, 196, 15)); // VS Code Yellow
                    g2d.fillRect(3, 2, 4, 12);
                    g2d.fillRect(9, 2, 4, 12);
                }
                case STOP -> {
                    g2d.setColor(customColor != null ? customColor : new Color(231, 76, 60)); // Red Stop
                    g2d.fillRect(2, 2, 12, 12);
                }
                case NEW_PROJECT -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRoundRect(1, 3, 14, 10, 2, 2);
                    g2d.drawLine(1, 6, 15, 6);
                    g2d.setColor(new Color(52, 152, 219));
                    g2d.drawLine(8, 8, 8, 12);
                    g2d.drawLine(6, 10, 10, 10);
                }
                case OPEN_PROJECT -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRoundRect(1, 3, 14, 10, 2, 2);
                    g2d.drawLine(1, 6, 15, 6);
                    g2d.drawLine(4, 3, 7, 3);
                }
                case SAVE -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(2, 2, 12, 12);
                    g2d.fillRect(5, 2, 6, 4);
                    g2d.drawRect(4, 9, 8, 5);
                }
                case FOLDER -> {
                    g2d.setColor(new Color(243, 156, 18));
                    int[] px = {1, 5, 7, 14, 14, 1};
                    int[] py = {3, 3, 5, 5, 13, 13};
                    g2d.fillPolygon(px, py, 6);
                    g2d.setColor(new Color(241, 196, 15));
                    g2d.fillRect(1, 6, 13, 7);
                }
                case FILE -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(3, 1, 10, 14);
                    g2d.drawLine(3, 4, 13, 4);
                }
                case SCRIPT -> {
                    g2d.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 12));
                    g2d.setColor(new Color(155, 89, 182));
                    g2d.drawString("{}", 1, 12);
                }
                case SCENE -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(2, 2, 12, 12);
                    int[] px = {2, 6, 10, 14};
                    int[] py = {14, 8, 11, 14};
                    g2d.drawPolyline(px, py, 4);
                }
                case PREFAB -> {
                    g2d.setColor(new Color(52, 152, 219));
                    g2d.fillRect(3, 3, 10, 10);
                    g2d.setColor(Color.WHITE);
                    g2d.drawRect(3, 3, 10, 10);
                }
                case AUDIO -> {
                    g2d.setColor(new Color(46, 204, 113));
                    g2d.fillRect(9, 2, 4, 2);
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(9, 2, 9, 11);
                    g2d.fillOval(5, 9, 5, 4);
                }
                case IMAGE -> {
                    g2d.setColor(new Color(230, 126, 34));
                    g2d.fillOval(3, 3, 10, 10);
                    g2d.setColor(Color.WHITE);
                    g2d.fillOval(5, 5, 2, 2);
                    g2d.fillOval(9, 5, 2, 2);
                    g2d.fillOval(7, 9, 2, 2);
                }
                case ANIMATION -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(2, 2, 12, 12);
                    g2d.fillRect(3, 3, 2, 2);
                    g2d.fillRect(3, 11, 2, 2);
                    g2d.fillRect(11, 3, 2, 2);
                    g2d.fillRect(11, 11, 2, 2);
                    g2d.drawLine(6, 2, 6, 14);
                    g2d.drawLine(10, 2, 10, 14);
                }
                case HIERARCHY -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(2, 2, 4, 3);
                    g2d.drawRect(7, 7, 4, 3);
                    g2d.drawRect(7, 12, 4, 3);
                    g2d.drawLine(4, 5, 4, 13);
                    g2d.drawLine(4, 8, 7, 8);
                    g2d.drawLine(4, 13, 7, 13);
                }
                case INSPECTOR -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawOval(2, 5, 12, 6);
                    g2d.fillOval(6, 6, 4, 4);
                }
                case SEARCH -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawOval(2, 2, 8, 8);
                    g2d.drawLine(9, 9, 14, 14);
                }
                case SETTINGS -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawOval(4, 4, 8, 8);
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4.0;
                        int x1 = (int) (8 + 4 * Math.cos(angle));
                        int y1 = (int) (8 + 4 * Math.sin(angle));
                        int x2 = (int) (8 + 7 * Math.cos(angle));
                        int y2 = (int) (8 + 7 * Math.sin(angle));
                        g2d.drawLine(x1, y1, x2, y2);
                    }
                }
                case TOOLS -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(2, 14, 14, 2);
                    g2d.drawLine(14, 14, 2, 2);
                }
                case COMPONENTS -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(3, 4, 10, 8);
                    g2d.drawOval(5, 2, 2, 2);
                    g2d.drawOval(9, 2, 2, 2);
                }
                case CONSOLE -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(2, 3, 6, 7);
                    g2d.drawLine(6, 7, 2, 11);
                    g2d.drawLine(7, 11, 13, 11);
                }
                case BUILD -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(2, 4, 12, 10);
                    g2d.drawLine(2, 4, 8, 8);
                    g2d.drawLine(14, 4, 8, 8);
                    g2d.drawLine(8, 8, 8, 14);
                }
                case TIMELINE -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawLine(1, 3, 15, 3);
                    g2d.drawLine(1, 8, 15, 8);
                    g2d.drawLine(1, 13, 15, 13);
                    g2d.fillRect(5, 2, 2, 3);
                    g2d.fillRect(10, 7, 2, 3);
                    g2d.fillRect(3, 12, 2, 3);
                }
                case ASSETS -> {
                    g2d.setStroke(new BasicStroke(1.2f));
                    g2d.drawRect(2, 2, 5, 5);
                    g2d.drawRect(9, 2, 5, 5);
                    g2d.drawRect(2, 9, 5, 5);
                    g2d.drawRect(9, 9, 5, 5);
                }
                case REFRESH -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawArc(2, 2, 12, 12, 45, 270);
                    g2d.drawLine(11, 5, 14, 2);
                    g2d.drawLine(11, 5, 8, 2);
                }
                case COMPILE -> {
                    g2d.setStroke(new BasicStroke(1.5f));
                    g2d.drawLine(4, 12, 12, 4);
                    g2d.fillRect(10, 2, 4, 4);
                }
                default -> {
                    throw new UnsupportedOperationException("Unknown vector icon type");
                }
            }
        } catch (Exception ex) {
            IgnisLogger.error("[VectorIcon] Error rendering icon " + type + ": " + ex.getMessage());
            // Safe fallback rendering: red square with a warning cross / question mark look
            g2d.setColor(Color.RED);
            g2d.drawRect(1, 1, 14, 14);
            g2d.drawLine(1, 1, 15, 15);
            g2d.drawLine(15, 1, 1, 15);
        } finally {
            g2d.dispose();
        }
    }
}
