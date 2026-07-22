package com.ignis.core;

import org.junit.jupiter.api.Test;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputFocusTest {

    @Test
    void losingFocusClearsKeyboardAndMouseState() {
        Game game = new Game();
        Input input = Input.getInstance();

        input.keyPressed(key(game, KeyEvent.KEY_PRESSED, KeyEvent.VK_W));
        input.mousePressed(mouse(game, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1));
        // Mouse e teclado usam o mesmo buffer: o update() promove ambos ao snapshot
        // lido pelos scripts (antes o mouse setava o held direto, sem passar por aqui).
        Input.update();

        assertTrue(Input.isKeyPressed(KeyEvent.VK_W));
        assertTrue(Input.isMouseLeftPressed());

        FocusEvent event = new FocusEvent(game, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : game.getFocusListeners()) {
            listener.focusLost(event);
        }

        assertFalse(Input.isKeyPressed(KeyEvent.VK_W));
        assertFalse(Input.isMouseLeftPressed());
    }

    @Test
    void focusLossCanRaceWithEventsWithoutCorruptingInputState() throws InterruptedException {
        Game game = new Game();
        Input input = Input.getInstance();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);

        Thread events = new Thread(() -> runSafely(failure, () -> {
            await(start);
            for (int i = 0; i < 5_000; i++) {
                input.keyPressed(key(game, KeyEvent.KEY_PRESSED, KeyEvent.VK_A));
                input.keyReleased(key(game, KeyEvent.KEY_RELEASED, KeyEvent.VK_A));
            }
        }), "input-event-test");
        Thread focus = new Thread(() -> runSafely(failure, () -> {
            await(start);
            FocusEvent event = new FocusEvent(game, FocusEvent.FOCUS_LOST);
            for (int i = 0; i < 2_000; i++) {
                for (FocusListener listener : game.getFocusListeners()) {
                    listener.focusLost(event);
                }
            }
        }), "input-focus-test");

        events.start();
        focus.start();
        start.countDown();
        for (int i = 0; i < 5_000; i++) {
            Input.update();
            Input.isKeyPressed(KeyEvent.VK_A);
        }
        events.join();
        focus.join();

        assertNull(failure.get(), () -> "Input concorrente falhou: " + failure.get());
        FocusEvent finalFocusLoss = new FocusEvent(game, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : game.getFocusListeners()) {
            listener.focusLost(finalFocusLoss);
        }
        assertFalse(Input.isKeyPressed(KeyEvent.VK_A));
    }

    private static KeyEvent key(Game game, int id, int keyCode) {
        return new KeyEvent(game, id, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static MouseEvent mouse(Game game, int id, int button) {
        return new MouseEvent(game, id, 0L, 0, 4, 4, 1, false, button);
    }

    private static void runSafely(AtomicReference<Throwable> failure, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        latch.await();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
