package com.ignis.core;

/**
 * Loop do jogo em thread propria (Fase F -- decomposicao do {@link Game}). A
 * <b>simulacao</b> roda em passos fixos de {@link Game#TICKS_PER_SECOND} Hz; o
 * <b>render</b> e desacoplado e limitado por {@link Game#getFpsCap()} (Fase E 3.13).
 *
 * <p>Detem a thread e a flag de execucao — estado de ciclo de vida que nao pertence
 * ao {@code Game}. Chama {@code game.tick()} e {@code game.render()}; o {@code Game}
 * segue dono da simulacao e do desenho.</p>
 */
final class GameLoop implements Runnable {

    private final Game game;
    private Thread thread;
    private volatile boolean running = false;

    // Teto de ticks de recuperacao por iteracao (evita a "espiral da morte" quando
    // um frame demora demais: o jogo desacelera em vez de acumular ticks infinitamente).
    private static final int MAX_CATCHUP_TICKS = 5;

    GameLoop(Game game) {
        this.game = game;
    }

    synchronized void start() {
        running = true;
        thread = new Thread(this, "IgnisGameLoop");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        // Joining from the loop thread itself would deadlock (self-join)
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        final double ns = 1_000_000_000.0 / Game.TICKS_PER_SECOND;
        double delta = 0;
        long lastRenderNanos = 0L;

        while (running) {
            try {
                long now = System.nanoTime();
                delta += (now - lastTime) / ns;
                lastTime = now;

                // Simulacao: passos fixos, com teto de recuperacao.
                int ticks = 0;
                while (delta >= 1 && ticks < MAX_CATCHUP_TICKS) {
                    game.tick();
                    delta--;
                    ticks++;
                }
                if (delta > MAX_CATCHUP_TICKS) {
                    delta = 0; // atraso grande demais: descarta em vez de acumular
                }

                // Render: independente do tick, limitado pelo fpsCap (0 = sem limite).
                int cap = game.getFpsCap();
                boolean renderDue = cap <= 0
                        || (now - lastRenderNanos) >= (1_000_000_000L / cap);
                if (renderDue) {
                    game.render();
                    lastRenderNanos = now;
                } else {
                    // Cede a CPU ate o proximo quadro/tick em vez de girar em vazio.
                    Thread.sleep(1);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                IgnisLogger.error("Erro na thread do loop do jogo: " + t.getMessage(), t);
                // Avoid fast spinning if there's a persistent error
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
