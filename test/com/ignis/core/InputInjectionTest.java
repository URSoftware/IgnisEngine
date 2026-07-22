package com.ignis.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Injecao programatica de input (ferramentas MCP inject_input/advance_frames). Garante
 * a semantica de "evento de um frame": teclado E mouse injetados viram just-pressed no
 * tick seguinte (quando os scripts leem) e voltam a false no tick posterior — a mesma
 * promocao bufferizada do input real. Cobre a regressao do mouse, que antes tinha o
 * flag "just" limpo antes de os scripts lerem.
 *
 * <p>Headless: exercita {@link Input#update()} direto (o que o {@code tick()} faz no
 * inicio de cada passo), sem JavaFX.</p>
 */
class InputInjectionTest {

    @BeforeEach
    void reset() {
        Input.resetAll();
        Input.update(); // drena qualquer buffer residual do singleton
    }

    @Test
    void injectedKeyIsJustPressedForExactlyOneFrame() {
        Input.injectKey(KeyEvent.VK_SPACE, true);
        // Antes do proximo update ainda nao vale (o buffer so e promovido no tick).
        assertFalse(Input.isKeyPressed(KeyEvent.VK_SPACE));

        Input.update(); // = 1o advance_frames
        assertTrue(Input.isKeyPressed(KeyEvent.VK_SPACE), "tecla deve estar pressionada");
        assertTrue(Input.isKeyJustPressed(KeyEvent.VK_SPACE), "just-pressed no frame da promocao");

        Input.update(); // = 2o advance_frames
        assertTrue(Input.isKeyPressed(KeyEvent.VK_SPACE), "held persiste");
        assertFalse(Input.isKeyJustPressed(KeyEvent.VK_SPACE), "just-pressed dura so um frame");

        Input.injectKey(KeyEvent.VK_SPACE, false);
        Input.update();
        assertFalse(Input.isKeyPressed(KeyEvent.VK_SPACE), "soltou");
        assertTrue(Input.isKeyJustReleased(KeyEvent.VK_SPACE), "just-released no frame do release");
    }

    @Test
    void injectedMouseIsJustPressedForExactlyOneFrame() {
        Input.injectMouseButton(1, true);
        assertFalse(Input.isMouseLeftPressed(), "so vale apos o update");

        Input.update();
        assertTrue(Input.isMouseLeftPressed(), "botao segurado");
        assertTrue(Input.isMouseLeftJustPressed(), "just-pressed no frame da promocao (regressao do mouse)");

        Input.update();
        assertTrue(Input.isMouseLeftPressed(), "held persiste");
        assertFalse(Input.isMouseLeftJustPressed(), "just-pressed dura so um frame");

        Input.injectMouseButton(1, false);
        Input.update();
        assertFalse(Input.isMouseLeftPressed(), "soltou");
    }

    @Test
    void resetAllClearsInjectedState() {
        Input.injectKey(KeyEvent.VK_W, true);
        Input.injectMouseButton(3, true);
        Input.update();
        assertTrue(Input.isKeyPressed(KeyEvent.VK_W));
        assertTrue(Input.isMouseRightPressed());

        Input.resetAll();
        Input.update();
        assertFalse(Input.isKeyPressed(KeyEvent.VK_W), "resetAll zera o teclado");
        assertFalse(Input.isMouseRightPressed(), "resetAll zera o mouse");
    }
}
