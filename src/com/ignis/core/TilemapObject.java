package com.ignis.core;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Mapa de tiles (Fase C do plano do motor grafico).
 *
 * <p>Um {@code TilemapObject} desenha uma grade de tiles a partir de um <b>tileset</b>
 * (uma imagem dividida em celulas {@code tileW x tileH}). O mapa tem {@code cols x rows}
 * celulas e uma ou mais <b>camadas</b>, cada uma um vetor de indices de tile
 * ({@code -1} = vazio). O indice {@code i} referencia a celula
 * {@code (i % tilesPorLinha, i / tilesPorLinha)} do tileset, recortada via
 * {@link AssetResolver#loadImageRegion} (reaproveita o cache de atlas da Fase C).</p>
 *
 * <p>Renderiza em lote com <b>culling por tile</b>: so desenha as celulas dentro do
 * retangulo visivel da camera. A origem {@code (x,y)} do objeto e o canto superior
 * esquerdo do mapa; linhas crescem para baixo na tela. Desativa o culling por AABB
 * ({@link #isCullable()} = false) porque cuida do proprio recorte.</p>
 */
public class TilemapObject extends GameObject {

    public static final int EMPTY = -1;

    private String tilesetPath = null;
    private int tileW = 32;
    private int tileH = 32;
    private int cols = 20;
    private int rows = 15;
    // Cada camada e um vetor cols*rows de indices de tile (-1 = vazio).
    private final List<int[]> layers = new ArrayList<>();

    // Teto de tiles desenhados por frame (protege contra zoom-out extremo).
    private static final long MAX_DRAW_TILES = 20000;

    public TilemapObject() {
        super();
        this.name = "TilemapObject";
        this.zIndex = -100; // atras das entidades comuns, na frente de fundos distantes
        this.visible = true;
        addLayer(); // sempre nasce com uma camada
        syncSize();
    }

    /** Reconfigura a grade do mapa, preservando/recriando as camadas vazias. */
    public void configure(String tilesetPath, int tileW, int tileH, int cols, int rows) {
        this.tilesetPath = tilesetPath;
        this.tileW = Math.max(1, tileW);
        this.tileH = Math.max(1, tileH);
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        layers.clear();
        addLayer();
        syncSize();
    }

    private void syncSize() {
        this.width = cols * tileW;
        this.height = rows * tileH;
    }

    /** Adiciona uma nova camada vazia e retorna seu indice. */
    public int addLayer() {
        int[] layer = new int[cols * rows];
        java.util.Arrays.fill(layer, EMPTY);
        layers.add(layer);
        return layers.size() - 1;
    }

    public int getLayerCount() {
        return layers.size();
    }

    private boolean inBounds(int col, int row) {
        return col >= 0 && col < cols && row >= 0 && row < rows;
    }

    /** Define o tile (indice do tileset, ou -1) numa celula de uma camada. */
    public void setTile(int layer, int col, int row, int tileIndex) {
        if (layer < 0 || layer >= layers.size() || !inBounds(col, row)) return;
        layers.get(layer)[row * cols + col] = tileIndex;
    }

    /** Le o tile de uma celula, ou {@link #EMPTY} se fora dos limites. */
    public int getTile(int layer, int col, int row) {
        if (layer < 0 || layer >= layers.size() || !inBounds(col, row)) return EMPTY;
        return layers.get(layer)[row * cols + col];
    }

    /** Preenche um retangulo de celulas [col0..col1] x [row0..row1] com um indice. */
    public void fillTiles(int layer, int col0, int row0, int col1, int row1, int tileIndex) {
        int a = Math.min(col0, col1), b = Math.max(col0, col1);
        int c = Math.min(row0, row1), d = Math.max(row0, row1);
        for (int col = a; col <= b; col++) {
            for (int row = c; row <= d; row++) {
                setTile(layer, col, row, tileIndex);
            }
        }
    }

    /** Numero de colunas de tiles do tileset (para converter indice em celula-fonte). */
    private int tilesPerRow(BufferedImage tileset) {
        return Math.max(1, tileset.getWidth() / tileW);
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
        if (!visible || tilesetPath == null) return;
        BufferedImage tileset = AssetResolver.loadImage(tilesetPath);
        if (tileset == null) return;
        Graphics2D g2d = (Graphics2D) g;

        // Faixa de celulas visiveis (culling por tile). Sem camera, desenha tudo.
        int c0 = 0, c1 = cols - 1, r0 = 0, r1 = rows - 1;
        Camera cam = (game != null) ? game.getViewCamera() : null;
        if (cam != null) {
            double[] vis = cam.getVisibleWorldBounds(); // [minX,minY,maxX,maxY]
            c0 = clampCol((int) Math.floor((vis[0] - x) / tileW));
            c1 = clampCol((int) Math.ceil((vis[2] - x) / tileW));
            // Linhas crescem para baixo (mundo Y para cima): row = (y - worldY)/tileH.
            r0 = clampRow((int) Math.floor((y - vis[3]) / tileH));
            r1 = clampRow((int) Math.ceil((y - vis[1]) / tileH));
        }
        long tiles = (long) (c1 - c0 + 1) * (r1 - r0 + 1) * layers.size();
        if (tiles > MAX_DRAW_TILES) return; // zoom-out extremo: evita travar

        int perRow = tilesPerRow(tileset);
        for (int[] layer : layers) {
            for (int row = r0; row <= r1; row++) {
                for (int col = c0; col <= c1; col++) {
                    int idx = layer[row * cols + col];
                    if (idx < 0) continue;
                    int sx = (idx % perRow) * tileW;
                    int sy = (idx / perRow) * tileH;
                    BufferedImage tile = AssetResolver.loadImageRegion(tilesetPath, sx, sy, tileW, tileH);
                    if (tile == null) continue;
                    // Canto superior-esquerdo do tile no mundo. Y cresce para baixo na
                    // tela: a linha 'row' fica abaixo da origem y.
                    double wx = x + (double) col * tileW;
                    double wyTop = y - (double) row * tileH;
                    // Compensa o eixo Y invertido do mundo (mesma tecnica do SpriteComponent).
                    AffineTransform saved = g2d.getTransform();
                    g2d.translate(wx, wyTop);
                    g2d.scale(1, -1);
                    g2d.drawImage(tile, 0, 0, tileW, tileH, null);
                    g2d.setTransform(saved);
                }
            }
        }
    }

    private int clampCol(int c) {
        return Math.max(0, Math.min(cols - 1, c));
    }

    private int clampRow(int r) {
        return Math.max(0, Math.min(rows - 1, r));
    }

    // ---- Getters ----

    public String getTilesetPath() { return tilesetPath; }
    public void setTilesetPath(String p) { this.tilesetPath = (p != null && p.isEmpty()) ? null : p; }
    public int getTileW() { return tileW; }
    public int getTileH() { return tileH; }
    public int getCols() { return cols; }
    public int getRows() { return rows; }

    @Override
    public JSONObject saveProperties() {
        JSONObject p = new JSONObject();
        if (tilesetPath != null) p.put("tilesetPath", tilesetPath);
        p.put("tileW", tileW);
        p.put("tileH", tileH);
        p.put("cols", cols);
        p.put("rows", rows);
        JSONArray layersJson = new JSONArray();
        for (int[] layer : layers) {
            JSONArray l = new JSONArray();
            for (int v : layer) l.put(v);
            layersJson.put(l);
        }
        p.put("layers", layersJson);
        return p;
    }

    @Override
    public void loadProperties(JSONObject props) {
        if (props == null) return;
        if (props.has("tilesetPath")) tilesetPath = props.getString("tilesetPath");
        tileW = Math.max(1, props.optInt("tileW", tileW));
        tileH = Math.max(1, props.optInt("tileH", tileH));
        cols = Math.max(1, props.optInt("cols", cols));
        rows = Math.max(1, props.optInt("rows", rows));
        layers.clear();
        JSONArray layersJson = props.optJSONArray("layers");
        if (layersJson != null && layersJson.length() > 0) {
            for (int i = 0; i < layersJson.length(); i++) {
                JSONArray l = layersJson.getJSONArray(i);
                int[] layer = new int[cols * rows];
                java.util.Arrays.fill(layer, EMPTY);
                for (int j = 0; j < Math.min(l.length(), layer.length); j++) {
                    layer[j] = l.getInt(j);
                }
                layers.add(layer);
            }
        } else {
            addLayer();
        }
        syncSize();
    }
}
