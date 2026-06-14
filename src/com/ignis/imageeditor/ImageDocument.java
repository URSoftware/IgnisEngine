package com.ignis.imageeditor;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Document model of the integrated image editor: a fixed-size canvas composed
 * of a stack of layers, each one an ARGB image. Pure model — no Swing
 * dependencies — so it can be reused by future tools (sprite pipeline,
 * animation frames).
 */
public class ImageDocument {

    /** One paintable layer of the document. */
    public static class Layer {
        private String name;
        private BufferedImage image;
        private boolean visible = true;
        private float opacity = 1.0f;
        private boolean locked = false;

        public Layer(String name, int width, int height) {
            this.name = name;
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BufferedImage getImage() {
            return image;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public float getOpacity() {
            return opacity;
        }

        public void setOpacity(float opacity) {
            this.opacity = Math.max(0f, Math.min(1f, opacity));
        }

        public boolean isLocked() {
            return locked;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        /** Deep copy of the layer's pixels (used by the undo stack). */
        public BufferedImage snapshot() {
            BufferedImage copy = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            return copy;
        }

        public void restore(BufferedImage snapshot) {
            Graphics2D g = image.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(snapshot, 0, 0, null);
            g.dispose();
        }
    }

    private final int width;
    private final int height;
    private final List<Layer> layers = new ArrayList<>();
    private int activeLayerIndex = 0;

    public ImageDocument(int width, int height) {
        this.width = width;
        this.height = height;
        layers.add(new Layer("Background", width, height));
    }

    /** Creates a document from an existing image (single layer). */
    public static ImageDocument fromImage(BufferedImage source) {
        ImageDocument doc = new ImageDocument(source.getWidth(), source.getHeight());
        Graphics2D g = doc.getActiveLayer().getImage().createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return doc;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public Layer getActiveLayer() {
        return layers.get(activeLayerIndex);
    }

    public int getActiveLayerIndex() {
        return activeLayerIndex;
    }

    public void setActiveLayerIndex(int index) {
        if (index >= 0 && index < layers.size()) {
            activeLayerIndex = index;
        }
    }

    public Layer addLayer(String name) {
        Layer layer = new Layer(name, width, height);
        // New layers go on top (end of list = topmost when compositing)
        layers.add(layer);
        activeLayerIndex = layers.size() - 1;
        return layer;
    }

    /** Removes the layer at index; a document always keeps at least one layer. */
    public boolean removeLayer(int index) {
        if (layers.size() <= 1 || index < 0 || index >= layers.size()) {
            return false;
        }
        layers.remove(index);
        activeLayerIndex = Math.min(activeLayerIndex, layers.size() - 1);
        return true;
    }

    /** Moves a layer one position up (towards the top of the stack). */
    public void moveLayer(int index, int direction) {
        int target = index + direction;
        if (index < 0 || index >= layers.size() || target < 0 || target >= layers.size()) {
            return;
        }
        Layer layer = layers.remove(index);
        layers.add(target, layer);
        activeLayerIndex = target;
    }

    /** Flattens all visible layers into a single ARGB image. */
    public BufferedImage composite() {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        for (Layer layer : layers) {
            if (layer.isVisible()) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.getOpacity()));
                g.drawImage(layer.getImage(), 0, 0, null);
            }
        }
        g.dispose();
        return result;
    }
}
