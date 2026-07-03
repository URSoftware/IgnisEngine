package com.ignis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * World - Definicao de um "mundo"/area jogavel do IgnisEngine.
 *
 * <p>Concentra as propriedades de nivel que nao pertencem a nenhum objeto
 * isolado: limites do mapa (bounds) e barreiras em grade (celulas bloqueadas
 * que o jogador nao atravessa), alem de metadados por mundo (nome, cor ambiente).
 * E a base da "mecanica de mundo" — Fase 1: limites + barreiras + colisao. As
 * transicoes/interiores entre mundos sao a Fase 2 (ver plano no vault).</p>
 *
 * <p>Cada {@link Game} tem no maximo um World ativo (o mundo em que a cena roda).
 * O World e serializado junto da cena (.ignis) e editavel ao vivo por ferramentas
 * MCP (set_world_bounds, block_rect, etc.).</p>
 */
public class World {

    private String name = "Mundo";
    // Cor ambiente (propriedade unica do mundo). Null = sem tint ambiente.
    private Color ambientColor = null;

    // Limites do mapa (o AABB do objeto com worldCollision fica contido aqui).
    private boolean hasBounds = false;
    private double minX, minY, maxX, maxY;

    // Grade de barreiras: celulas de tamanho cellSize marcadas como solidas.
    private int cellSize = 64;
    private final Set<Long> blockedCells = new HashSet<>();

    public World() {}

    public World(String name) { this.name = name; }

    // ------------------------------------------------------------------
    // Metadados
    // ------------------------------------------------------------------

    public String getName() { return name; }
    public void setName(String name) { if (name != null) this.name = name; }

    public Color getAmbientColor() { return ambientColor; }
    public void setAmbientColor(Color c) { this.ambientColor = c; }

    // ------------------------------------------------------------------
    // Limites do mapa
    // ------------------------------------------------------------------

    public boolean hasBounds() { return hasBounds; }
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }

    public void setBounds(double minX, double minY, double maxX, double maxY) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.hasBounds = true;
    }

    public void clearBounds() { this.hasBounds = false; }

    // ------------------------------------------------------------------
    // Grade de barreiras
    // ------------------------------------------------------------------

    public int getCellSize() { return cellSize; }
    public void setCellSize(int cellSize) {
        this.cellSize = Math.max(1, cellSize);
    }

    public int getBlockedCount() { return blockedCells.size(); }

    private static long key(int col, int row) {
        return (((long) col) << 32) | (row & 0xffffffffL);
    }

    public int cellCol(double worldX) { return (int) Math.floor(worldX / cellSize); }
    public int cellRow(double worldY) { return (int) Math.floor(worldY / cellSize); }

    public void blockCell(int col, int row) { blockedCells.add(key(col, row)); }
    public void unblockCell(int col, int row) { blockedCells.remove(key(col, row)); }
    public boolean isCellBlocked(int col, int row) { return blockedCells.contains(key(col, row)); }
    public void clearBarriers() { blockedCells.clear(); }

    /** Bloqueia todas as celulas que tocam o retangulo de mundo dado. */
    public int blockRect(double x, double y, double w, double h) {
        return applyRect(x, y, w, h, true);
    }

    /** Desbloqueia todas as celulas que tocam o retangulo de mundo dado. */
    public int unblockRect(double x, double y, double w, double h) {
        return applyRect(x, y, w, h, false);
    }

    private int applyRect(double x, double y, double w, double h, boolean block) {
        int c0 = cellCol(x), c1 = cellCol(x + Math.max(0, w) - 1e-6);
        int r0 = cellRow(y), r1 = cellRow(y + Math.max(0, h) - 1e-6);
        int count = 0;
        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                if (block) blockedCells.add(key(c, r));
                else blockedCells.remove(key(c, r));
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Colisao
    // ------------------------------------------------------------------

    /** True se o AABB [x, x+w] x [y, y+h] toca alguma celula bloqueada. */
    public boolean overlapsBlocked(double x, double y, int w, int h) {
        if (blockedCells.isEmpty()) return false;
        int c0 = cellCol(x), c1 = cellCol(x + w - 1e-6);
        int r0 = cellRow(y), r1 = cellRow(y + h - 1e-6);
        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                if (isCellBlocked(c, r)) return true;
            }
        }
        return false;
    }

    /**
     * Resolve o movimento de um AABB de (prevX,prevY) para (newX,newY), respeitando
     * limites do mapa e barreiras. Resolucao por eixo (permite deslizar em parede).
     *
     * @return {@code double[]{x, y}} com a posicao final valida.
     */
    public double[] resolveMovement(double prevX, double prevY, double newX, double newY, int w, int h) {
        double x = prevX, y = prevY;

        // Eixo X
        double tryX = newX;
        if (hasBounds) tryX = clamp(tryX, minX, maxX - w);
        if (!overlapsBlocked(tryX, y, w, h)) x = tryX;

        // Eixo Y (usa o X ja resolvido, para deslizar ao longo de paredes)
        double tryY = newY;
        if (hasBounds) tryY = clamp(tryY, minY, maxY - h);
        if (!overlapsBlocked(x, tryY, w, h)) y = tryY;

        return new double[] { x, y };
    }

    private static double clamp(double v, double lo, double hi) {
        if (hi < lo) return lo; // faixa degenerada (mapa menor que o objeto)
        return Math.max(lo, Math.min(hi, v));
    }

    /** Ha alguma regra ativa (limites ou barreiras) que exija resolucao? */
    public boolean isActive() {
        return hasBounds || !blockedCells.isEmpty();
    }

    // ------------------------------------------------------------------
    // Serializacao
    // ------------------------------------------------------------------

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("cellSize", cellSize);
        if (ambientColor != null) json.put("ambientColor", ambientColor.getRGB());
        if (hasBounds) {
            JSONObject b = new JSONObject();
            b.put("minX", minX);
            b.put("minY", minY);
            b.put("maxX", maxX);
            b.put("maxY", maxY);
            json.put("bounds", b);
        }
        if (!blockedCells.isEmpty()) {
            JSONArray cells = new JSONArray();
            for (long k : blockedCells) {
                int col = (int) (k >> 32);
                int row = (int) k;
                cells.put(col + "," + row);
            }
            json.put("blockedCells", cells);
        }
        return json;
    }

    public static World fromJSON(JSONObject json) {
        World w = new World();
        w.name = json.optString("name", "Mundo");
        w.cellSize = Math.max(1, json.optInt("cellSize", 64));
        if (json.has("ambientColor")) w.ambientColor = new Color(json.getInt("ambientColor"), true);
        if (json.has("bounds")) {
            JSONObject b = json.getJSONObject("bounds");
            w.setBounds(b.getDouble("minX"), b.getDouble("minY"), b.getDouble("maxX"), b.getDouble("maxY"));
        }
        if (json.has("blockedCells")) {
            JSONArray cells = json.getJSONArray("blockedCells");
            for (int i = 0; i < cells.length(); i++) {
                String[] parts = cells.getString(i).split(",");
                if (parts.length == 2) {
                    try {
                        w.blockCell(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException ignore) { /* pula celula invalida */ }
                }
            }
        }
        return w;
    }
}
