package com.ignis.mcp;

import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * IgnisMcpBridge - Ponte de execucao thread-safe para o subsistema MCP.
 * Garante que alteracoes no Scene Graph do JavaFX sejam executadas na UI Thread.
 */
public final class IgnisMcpBridge {

    private IgnisMcpBridge() {}

    /**
     * Executa uma acao na thread principal do JavaFX e aguarda o seu retorno de forma sincrona.
     *
     * @param action Acao a ser executada na thread de UI.
     * @param <T> Tipo de retorno esperado.
     * @return O resultado retornado pela acao.
     * @throws Exception Se ocorrer um erro durante a execucao na thread do JavaFX.
     */
    public static <T> T runOnFxThread(Supplier<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(action.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        // Aguarda a conclusao e trata possiveis excecoes
        try {
            return future.get();
        } catch (Exception e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw new RuntimeException("Erro na execucao da thread JavaFX: " + e.getMessage(), e);
        }
    }

    /**
     * Executa uma acao na thread principal do JavaFX sem aguardar pelo retorno.
     *
     * @param action Acao a ser executada de forma assincrona na thread de UI.
     */
    public static void runOnFxThreadLater(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
