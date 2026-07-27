package com.ignis.core;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Componente nativo do IgnisEngine para renderização otimizada de Tilemaps 2D (grade).
 * Suporta múltiplas camadas de tiles, Viewport Culling (desenha apenas tiles visíveis na câmera)
 * e o algoritmo de Greedy Meshing para fusão de blocos e geração de colisores compostos.
 */
public class TilemapRendererComponent extends Component {

    public static final int EMPTY = -1;
    private static final long MAX_DRAW_TILES = 20000;

    @Serialize
    private String tilesetPath = null;

    @Serialize
    private int tileW = 32;

    @Serialize
    private int tileH = 32;

    @Serialize
    private int cols = 20;

    @Serialize
    private int rows = 15;

    @Serialize
    private int sortingOrder = -100;

    // Camadas de tiles (cada uma é uma matriz plana cols * rows de índices)
    private final List<int[]> layers = new ArrayList<>();

    public TilemapRendererComponent() {
        addLayer();
    }

    public TilemapRendererComponent(String tilesetPath, int tileW, int tileH, int cols, int rows) {
        configure(tilesetPath, tileW, tileH, cols, rows);
    }

    /**
     * Reconfigura as dimensões da grade e preserxa/recria as camadas.
     */
    public void configure(String tilesetPath, int tileW, int tileH, int cols, int rows) {
        this.tilesetPath = (tilesetPath != null && !tilesetPath.trim().isEmpty()) ? tilesetPath.trim() : null;
        this.tileW = Math.max(1, tileW);
        this.tileH = Math.max(1, tileH);
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        layers.clear();
        addLayer();
    }

    /**
     * Adiciona uma nova camada de tiles vazia e retorna seu índice.
     */
    public int addLayer() {
        int[] layer = new int[cols * rows];
        Arrays.fill(layer, EMPTY);
        layers.add(layer);
        return layers.size() - 1;
    }

    public int getLayerCount() {
        return layers.size();
    }

    public List<int[]> getLayers() {
        return layers;
    }

    private boolean inBounds(int col, int row) {
        return col >= 0 && col < cols && row >= 0 && row < rows;
    }

    /**
     * Define o índice do tile em uma célula de uma camada específica.
     */
    public void setTile(int layer, int col, int row, int tileIndex) {
        if (layer < 0 || layer >= layers.size() || !inBounds(col, row)) return;
        layers.get(layer)[row * cols + col] = tileIndex;
    }

    /**
     * Retorna o índice do tile de uma célula ou EMPTY (-1) se fora dos limites.
     */
    public int getTile(int layer, int col, int row) {
        if (layer < 0 || layer >= layers.size() || !inBounds(col, row)) return EMPTY;
        return layers.get(layer)[row * cols + col];
    }

    /**
     * Preenche uma região retangular de células na camada informada com um índice de tile.
     */
    public void fillTiles(int layer, int col0, int row0, int col1, int row1, int tileIndex) {
        int a = Math.min(col0, col1), b = Math.max(col0, col1);
        int c = Math.min(row0, row1), d = Math.max(row0, row1);
        for (int col = a; col <= b; col++) {
            for (int row = c; row <= d; row++) {
                setTile(layer, col, row, tileIndex);
            }
        }
    }

    /**
     * Limpa uma camada inteira redefinindo suas células para EMPTY (-1).
     */
    public void clearLayer(int layer) {
        if (layer >= 0 && layer < layers.size()) {
            Arrays.fill(layers.get(layer), EMPTY);
        }
    }

    /** Coluna da célula sob a coordenada X de mundo. */
    public int cellColAtWorld(double worldX) {
        double x = (gameObject != null) ? gameObject.getX() : 0.0;
        return (int) Math.floor((worldX - x) / tileW);
    }

    /** Linha da célula sob a coordenada Y de mundo. */
    public int cellRowAtWorld(double worldY) {
        double y = (gameObject != null) ? gameObject.getY() : 0.0;
        return (int) Math.floor((y - worldY) / tileH);
    }

    private int tilesPerRow(BufferedImage tileset) {
        return Math.max(1, tileset.getWidth() / tileW);
    }

    /**
     * Passe de renderização otimizado em lote com Viewport Culling.
     */
    public void draw(Graphics2D g2d) {
        if (tilesetPath == null || gameObject == null || !gameObject.isVisible()) return;
        BufferedImage tileset = AssetResolver.loadImage(tilesetPath);
        if (tileset == null) return;

        double x = gameObject.getX();
        double y = gameObject.getY();

        // Faixa de células visíveis (Viewport Culling). Sem câmera, desenha tudo.
        int c0 = 0, c1 = cols - 1, r0 = 0, r1 = rows - 1;
        Camera cam = (gameObject.getGame() != null) ? gameObject.getGame().getViewCamera() : null;
        if (cam != null) {
            double[] vis = cam.getVisibleWorldBounds(); // [minX, minY, maxX, maxY]
            c0 = clampCol((int) Math.floor((vis[0] - x) / tileW));
            c1 = clampCol((int) Math.ceil((vis[2] - x) / tileW));
            r0 = clampRow((int) Math.floor((y - vis[3]) / tileH));
            r1 = clampRow((int) Math.ceil((y - vis[1]) / tileH));
        }

        long tilesToDraw = (long) (c1 - c0 + 1) * (r1 - r0 + 1) * layers.size();
        if (tilesToDraw > MAX_DRAW_TILES) return; // Proteção contra zoom-out extremo

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

                    double wx = x + (double) col * tileW;
                    double wyTop = y - (double) row * tileH;

                    AffineTransform saved = g2d.getTransform();
                    g2d.translate(wx, wyTop);
                    g2d.scale(1, -1);
                    g2d.drawImage(tile, 0, 0, tileW, tileH, null);
                    g2d.setTransform(saved);
                }
            }
        }
    }

    /**
     * Algoritmo de Greedy Meshing para fundir blocos sólidos contínuos em uma camada
     * e gerar a lista mínima de colisores compostos ({@link ColliderComponent}).
     *
     * @param layerIndex Índice da camada contendo os tiles sólidos.
     * @return Lista de {@link ColliderComponent}s gerados com posições e offsets ajustados.
     */
    public List<ColliderComponent> generateGreedyColliders(int layerIndex) {
        List<ColliderComponent> createdColliders = new ArrayList<>();
        if (layerIndex < 0 || layerIndex >= layers.size()) return createdColliders;

        int[] grid = layers.get(layerIndex);
        boolean[][] visited = new boolean[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r * cols + c] == EMPTY) {
                    visited[r][c] = true;
                }
            }
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (visited[r][c]) continue;

                // Determina a largura máxima contínua não visitada na linha r
                int widthInTiles = 1;
                while (c + widthInTiles < cols && !visited[r][c + widthInTiles]) {
                    widthInTiles++;
                }

                // Determina a altura máxima contínua mantendo a mesma largura nas linhas seguintes
                int heightInTiles = 1;
                boolean canExtend = true;
                while (r + heightInTiles < rows && canExtend) {
                    for (int k = 0; k < widthInTiles; k++) {
                        if (visited[r + heightInTiles][c + k]) {
                            canExtend = false;
                            break;
                        }
                    }
                    if (canExtend) {
                        heightInTiles++;
                    }
                }

                // Marca todas as células do retângulo consolidado como visitadas
                for (int hr = 0; hr < heightInTiles; hr++) {
                    for (int wc = 0; wc < widthInTiles; wc++) {
                        visited[r + hr][c + wc] = true;
                    }
                }

                // Calcula dimensões e offsets em coordenadas locais do GameObject
                double boxWidth = widthInTiles * tileW;
                double boxHeight = heightInTiles * tileH;
                double offsetX = c * tileW;
                double offsetY = r * tileH;

                ColliderComponent cc = new ColliderComponent();
                cc.setShape("Box");
                cc.setWidth(boxWidth);
                cc.setHeight(boxHeight);
                cc.setOffsetX(offsetX);
                cc.setOffsetY(offsetY);

                createdColliders.add(cc);
            }
        }
        return createdColliders;
    }

    private int clampCol(int c) {
        return Math.max(0, Math.min(cols - 1, c));
    }

    private int clampRow(int r) {
        return Math.max(0, Math.min(rows - 1, r));
    }

    // Customização de Serialização para a estrutura de camadas de Tilemaps

    @Override
    public JSONObject saveProperties() {
        JSONObject p = super.saveProperties();
        if (tilesetPath != null) p.put("tilesetPath", tilesetPath);
        p.put("tileW", tileW);
        p.put("tileH", tileH);
        p.put("cols", cols);
        p.put("rows", rows);
        p.put("sortingOrder", sortingOrder);

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
    public void loadProperties(JSONObject props, ScriptSerializationHelper.GameObjectResolver resolver) {
        super.loadProperties(props, resolver);
        if (props == null) return;
        if (props.has("tilesetPath")) tilesetPath = props.getString("tilesetPath");
        tileW = Math.max(1, props.optInt("tileW", tileW));
        tileH = Math.max(1, props.optInt("tileH", tileH));
        cols = Math.max(1, props.optInt("cols", cols));
        rows = Math.max(1, props.optInt("rows", rows));
        sortingOrder = props.optInt("sortingOrder", sortingOrder);

        layers.clear();
        JSONArray layersJson = props.optJSONArray("layers");
        if (layersJson != null && layersJson.length() > 0) {
            for (int i = 0; i < layersJson.length(); i++) {
                JSONArray l = layersJson.getJSONArray(i);
                int[] layer = new int[cols * rows];
                Arrays.fill(layer, EMPTY);
                for (int j = 0; j < Math.min(l.length(), layer.length); j++) {
                    layer[j] = l.getInt(j);
                }
                layers.add(layer);
            }
        } else {
            addLayer();
        }
    }

    // Getters e Setters

    public String getTilesetPath() { return tilesetPath; }
    public void setTilesetPath(String path) { this.tilesetPath = (path != null && path.isEmpty()) ? null : path; }

    public int getTileW() { return tileW; }
    public void setTileW(int tileW) { this.tileW = Math.max(1, tileW); }

    public int getTileH() { return tileH; }
    public void setTileH(int tileH) { this.tileH = Math.max(1, tileH); }

    public int getCols() { return cols; }
    public void setCols(int cols) { this.cols = Math.max(1, cols); }

    public int getRows() { return rows; }
    public void setRows(int rows) { this.rows = Math.max(1, rows); }

    public int getSortingOrder() { return sortingOrder; }
    public void setSortingOrder(int sortingOrder) { this.sortingOrder = sortingOrder; }
}
