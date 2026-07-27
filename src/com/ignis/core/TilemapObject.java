package com.ignis.core;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import org.json.JSONObject;

/**
 * Entidade de Mapa de tiles (Fase C do plano do motor gráfico).
 *
 * <p>Refatorada para o modelo Entidade-Componente (EC): atua como um wrapper conveniente
 * que contém um {@link TilemapRendererComponent} nativo acoplado. Mantém total
 * retrocompatibilidade com cenas e chamadas existentes.</p>
 */
public class TilemapObject extends GameObject {

    public static final int EMPTY = TilemapRendererComponent.EMPTY;
    private final TilemapRendererComponent rendererComponent;

    public TilemapObject() {
        super();
        this.name = "TilemapObject";
        this.zIndex = -100;
        this.visible = true;
        this.rendererComponent = new TilemapRendererComponent();
        this.addComponent(rendererComponent);
        syncSize();
    }

    /** Reconfigura a grade do mapa delegando ao TilemapRendererComponent. */
    public void configure(String tilesetPath, int tileW, int tileH, int cols, int rows) {
        rendererComponent.configure(tilesetPath, tileW, tileH, cols, rows);
        syncSize();
    }

    private void syncSize() {
        if (rendererComponent != null) {
            this.width = rendererComponent.getCols() * rendererComponent.getTileW();
            this.height = rendererComponent.getRows() * rendererComponent.getTileH();
        }
    }

    public int addLayer() {
        return rendererComponent.addLayer();
    }

    public int getLayerCount() {
        return rendererComponent.getLayerCount();
    }

    public void setTile(int layer, int col, int row, int tileIndex) {
        rendererComponent.setTile(layer, col, row, tileIndex);
    }

    public int getTile(int layer, int col, int row) {
        return rendererComponent.getTile(layer, col, row);
    }

    public int cellColAtWorld(double worldX) {
        return rendererComponent.cellColAtWorld(worldX);
    }

    public int cellRowAtWorld(double worldY) {
        return rendererComponent.cellRowAtWorld(worldY);
    }

    public void fillTiles(int layer, int col0, int row0, int col1, int row1, int tileIndex) {
        rendererComponent.fillTiles(layer, col0, row0, col1, row1, tileIndex);
    }

    @Override
    public boolean isCullable() {
        return false;
    }

    @Override
    public String getType() {
        return "TilemapObject";
    }

    @Override
    public void render(Graphics g) {
        if (!visible) return;
        rendererComponent.draw((Graphics2D) g);
    }

    public TilemapRendererComponent getRendererComponent() {
        return rendererComponent;
    }

    public String getTilesetPath() { return rendererComponent.getTilesetPath(); }
    public void setTilesetPath(String p) { rendererComponent.setTilesetPath(p); }
    public int getTileW() { return rendererComponent.getTileW(); }
    public int getTileH() { return rendererComponent.getTileH(); }
    public int getCols() { return rendererComponent.getCols(); }
    public int getRows() { return rendererComponent.getRows(); }

    @Override
    public JSONObject saveProperties() {
        return rendererComponent.saveProperties();
    }

    @Override
    public void loadProperties(JSONObject props) {
        rendererComponent.loadProperties(props, null);
        syncSize();
    }
}
