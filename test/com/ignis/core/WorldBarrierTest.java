package com.ignis.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da grade de barreiras do {@link World} (pintura de barreiras do editor):
 * bloquear/apagar celulas, mapeamento mundo->celula e snapshot/restore para undo.
 */
class WorldBarrierTest {

    @Test
    void blockAndUnblockCell() {
        World w = new World("W");
        assertFalse(w.isCellBlocked(2, 3));
        w.blockCell(2, 3);
        assertTrue(w.isCellBlocked(2, 3));
        assertEquals(1, w.getBlockedCount());
        w.unblockCell(2, 3);
        assertFalse(w.isCellBlocked(2, 3));
        assertEquals(0, w.getBlockedCount());
    }

    @Test
    void cellMappingUsesCellSize() {
        World w = new World("W");
        w.setCellSize(64);
        assertEquals(0, w.cellCol(0));
        assertEquals(0, w.cellCol(63));
        assertEquals(1, w.cellCol(64));
        assertEquals(-1, w.cellCol(-1));
        assertEquals(2, w.cellRow(130));
    }

    @Test
    void snapshotRestoreRoundTrip() {
        World w = new World("W");
        w.blockCell(0, 0);
        w.blockCell(1, 0);
        w.blockCell(0, 1);
        Set<Long> snap = w.snapshotBlockedCells();
        assertEquals(3, snap.size());

        // Modifica depois do snapshot.
        w.blockCell(5, 5);
        w.unblockCell(0, 0);
        assertTrue(w.isCellBlocked(5, 5));
        assertFalse(w.isCellBlocked(0, 0));

        // Restaura o snapshot (undo).
        w.restoreBlockedCells(snap);
        assertEquals(3, w.getBlockedCount());
        assertTrue(w.isCellBlocked(0, 0));
        assertTrue(w.isCellBlocked(1, 0));
        assertTrue(w.isCellBlocked(0, 1));
        assertFalse(w.isCellBlocked(5, 5));
    }

    @Test
    void snapshotIsIndependentCopy() {
        World w = new World("W");
        w.blockCell(1, 1);
        Set<Long> snap = w.snapshotBlockedCells();
        // Alterar o mundo nao deve alterar o snapshot ja capturado.
        w.blockCell(2, 2);
        assertEquals(1, snap.size(), "snapshot deve ser uma copia imutavel do momento");
    }

    @Test
    void restoreBlockedCellsWithNullClears() {
        World w = new World("W");
        w.blockCell(1, 1);
        w.restoreBlockedCells(null);
        assertEquals(0, w.getBlockedCount());
    }
}
