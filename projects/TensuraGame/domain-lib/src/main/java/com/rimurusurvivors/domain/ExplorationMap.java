package com.rimurusurvivors.domain;

import java.util.Set;

/**
 * Grade logica de colisao de uma area de exploracao. Guarda apenas quais celulas
 * sao barreira — nao sabe nada de tileset, indice de atlas ou sprite: essa traducao
 * fica no apresentador (contrato de IDs semanticos do vault).
 */
public final class ExplorationMap {

    private final int cols;
    private final int rows;
    private final double cellSize;
    private final Set<Cell> blockedCells;

    public ExplorationMap(int cols, int rows, double cellSize, Set<Cell> blockedCells) {
        if (cols <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Map dimensions must be positive.");
        }
        if (cellSize <= 0) {
            throw new IllegalArgumentException("Cell size must be positive.");
        }
        this.cols = cols;
        this.rows = rows;
        this.cellSize = cellSize;
        this.blockedCells = blockedCells == null ? Set.of() : Set.copyOf(blockedCells);
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public double cellSize() {
        return cellSize;
    }

    public double width() {
        return cols * cellSize;
    }

    public double height() {
        return rows * cellSize;
    }

    /** Um ponto do mundo e bloqueado se cair fora da grade ou numa celula-barreira. */
    public boolean isBlockedAt(double worldX, double worldY) {
        int col = (int) Math.floor(worldX / cellSize);
        int row = (int) Math.floor(worldY / cellSize);
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return true;
        }
        return blockedCells.contains(new Cell(col, row));
    }

    public record Cell(int col, int row) {
    }
}
