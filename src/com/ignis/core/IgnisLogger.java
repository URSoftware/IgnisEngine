package com.ignis.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Log privado e centralizado do IgnisEngine.
 * Permite direcionar logs de compilacao, execucao e erros de scripts para a UI FX
 * (FxConsolePanel) de forma limpa, isolada e sem a poluicao do System.out/err do computador.
 */
public final class IgnisLogger {

    public enum Level { INFO, WARN, ERROR, SCRIPT }

    public interface LogListener {
        void onLog(Level level, String message);
    }

    private static final List<LogListener> listeners = new ArrayList<>();

    public static synchronized void addListener(LogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    public static void script(String message) {
        log(Level.SCRIPT, message);
    }

    /**
     * Loga uma mensagem de erro acompanhada do stack trace da excecao — substitui o
     * padrao {@code e.printStackTrace()}, roteando o rastro para os listeners (ex.:
     * console do editor) em vez de despejar direto no stderr do sistema.
     */
    public static void error(String message, Throwable t) {
        if (t == null) {
            log(Level.ERROR, message);
            return;
        }
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        log(Level.ERROR, message + "\n" + sw);
    }

    public static synchronized void log(Level level, String message) {
        if (message == null) return;
        
        // Notifica todos os listeners registrados (ex: FxConsolePanel)
        for (LogListener listener : listeners) {
            try {
                listener.onLog(level, message);
            } catch (Exception ignore) {
                // garante que erro em um listener nao quebra o log de outros
            }
        }

        // Mantem o print no console do terminal de comando do computador
        // para depuracao externa, mas com uma tag identificavel.
        if (level == Level.ERROR) {
            System.err.println("[IgnisEngine] ERROR: " + message);
        } else if (level == Level.WARN) {
            System.out.println("[IgnisEngine] WARNING: " + message);
        } else if (level == Level.SCRIPT) {
            System.out.println("[IgnisEngine] SCRIPT: " + message);
        } else {
            System.out.println("[IgnisEngine] INFO: " + message);
        }
    }
}
