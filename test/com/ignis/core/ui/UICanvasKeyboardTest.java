package com.ignis.core.ui;

import org.junit.jupiter.api.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da navegação por teclado da {@link UICanvas} (item 12 do plano de melhorias):
 * Tab cicla o foco entre componentes focáveis e Enter/Espaço ativa o focado.
 */
class UICanvasKeyboardTest {

    // Canvas AWT como origem dos KeyEvent (headless-safe: nao cria janela).
    private static final Component SRC = new Canvas();

    private static KeyEvent press(int vk) {
        return new KeyEvent(SRC, KeyEvent.KEY_PRESSED, 0L, 0, vk, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void tabCiclaFocoEEnterAtivaOFocado() {
        UICanvas canvas = new UICanvas();
        int[] clicksA = {0};
        int[] clicksB = {0};
        UIButton a = new UIButton("A", 0, 0, 10, 10);
        a.setOnClick(() -> clicksA[0]++);
        UIButton b = new UIButton("B", 0, 0, 10, 10);
        b.setOnClick(() -> clicksB[0]++);
        canvas.addChild(a);
        canvas.addChild(b);

        assertNull(canvas.getFocusedComponent(), "sem foco inicial");

        assertTrue(canvas.processKeyPressed(press(KeyEvent.VK_TAB)), "Tab deve ser consumido");
        assertSame(a, canvas.getFocusedComponent(), "primeiro Tab foca o primeiro");

        canvas.processKeyPressed(press(KeyEvent.VK_TAB));
        assertSame(b, canvas.getFocusedComponent(), "segundo Tab foca o segundo");

        canvas.processKeyPressed(press(KeyEvent.VK_ENTER));
        assertEquals(1, clicksB[0], "Enter ativa o focado (B)");
        assertEquals(0, clicksA[0], "componente nao focado nao e ativado");

        canvas.processKeyPressed(press(KeyEvent.VK_TAB));
        assertSame(a, canvas.getFocusedComponent(), "Tab cicla de volta ao primeiro");
    }

    @Test
    void shiftTabNavegaParaTras() {
        UICanvas canvas = new UICanvas();
        UIButton a = new UIButton("A", 0, 0, 10, 10);
        a.setOnClick(() -> { });
        UIButton b = new UIButton("B", 0, 0, 10, 10);
        b.setOnClick(() -> { });
        canvas.addChild(a);
        canvas.addChild(b);

        canvas.moveFocus(false); // para tras a partir do nada -> ultimo
        assertSame(b, canvas.getFocusedComponent());
        canvas.moveFocus(false);
        assertSame(a, canvas.getFocusedComponent());
    }
}
