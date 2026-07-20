package com.ignis.core;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private static final int RECENT_LOG_LIMIT = 500;
    private static final Deque<LogEntry> recentLogs = new ArrayDeque<>();
    private static long nextSequence = 1;

    public record LogEntry(long sequence, Level level, String message) {}

    public static synchronized void addListener(LogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static synchronized void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public static synchronized List<LogEntry> recentLogs(int maxEntries, Level level) {
        int limit = Math.max(1, Math.min(RECENT_LOG_LIMIT, maxEntries));
        List<LogEntry> matches = recentLogs.stream()
                .filter(entry -> level == null || entry.level() == level)
                .toList();
        int from = Math.max(0, matches.size() - limit);
        return List.copyOf(matches.subList(from, matches.size()));
    }

    public static synchronized void clearRecentLogs() {
        recentLogs.clear();
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

        recentLogs.addLast(new LogEntry(nextSequence++, level, message));
        while (recentLogs.size() > RECENT_LOG_LIMIT) {
            recentLogs.removeFirst();
        }
        
        // Notifica todos os listeners registrados (ex: FxConsolePanel)
        for (LogListener listener : listeners) {
            try {
                listener.onLog(level, message);
            } catch (Exception ignore) {
                // garante que erro em um listener nao quebra o log de outros
            }
        }

        // Mantem o print no terminal para depuracao externa, com tag identificavel.
        // SEMPRE em stderr: stdout fica limpo para protocolos que o usam como canal
        // de dados (ex.: servidor MCP STDIO, onde qualquer print em stdout corrompe
        // o JSON-RPC). Consoles de UI recebem via listener, nao por stream.
        if (level == Level.ERROR) {
            System.err.println("[IgnisEngine] ERROR: " + message);
        } else if (level == Level.WARN) {
            System.err.println("[IgnisEngine] WARNING: " + message);
        } else if (level == Level.SCRIPT) {
            System.err.println("[IgnisEngine] SCRIPT: " + message);
        } else {
            System.err.println("[IgnisEngine] INFO: " + message);
        }
    }
}
