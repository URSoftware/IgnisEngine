package com.ignis.mcp;

import com.ignis.core.Game;

import java.io.File;

/**
 * McpService - Fachada de alto nivel para o subsistema MCP via HTTP.
 *
 * <p>Concentra o ciclo de vida do {@link McpHttpBridge} e do
 * {@link IgnisToolRegistry} associado ao projeto ativo, para ser usada tanto pela
 * janela de Configuracoes (ligar/desligar, copiar URL) quanto pelo carregamento
 * de projeto do editor (auto-start). Mantem uma referencia ao registry para que
 * uma futura IA agentica embarcada compartilhe exatamente as mesmas ferramentas
 * expostas aos agentes externos.</p>
 */
public final class McpService {

    private static IgnisToolRegistry registry;
    private static File activeProject;

    // Contexto vivo do editor, registrado uma vez no carregamento do projeto.
    private static Game editorGame;
    private static Runnable editorPlay, editorStop, editorRefresh, editorSave;

    // Capturador da janela do editor (snapshot JavaFX -> BufferedImage), injetado
    // pelo IgnisEditorApp. Propagado a cada registry novo criado no start().
    private static java.util.function.Supplier<java.awt.image.BufferedImage> windowCaptureSupplier;

    private McpService() {}

    /** Registra o capturador da janela do editor para a ferramenta capture_editor_window. */
    public static synchronized void setWindowCaptureSupplier(
            java.util.function.Supplier<java.awt.image.BufferedImage> supplier) {
        windowCaptureSupplier = supplier;
        if (registry != null) {
            registry.setWindowCaptureSupplier(supplier);
        }
    }

    /**
     * Registra o editor vivo (chamado pelo IgnisEditorApp ao abrir um projeto),
     * para que o bridge exponha ferramentas de cena e Play. Se o bridge ja estiver
     * ativo, o contexto passa a valer no proximo start.
     */
    public static synchronized void setEditorContext(Game game, Runnable play, Runnable stop,
                                                     Runnable refresh, Runnable save) {
        editorGame = game;
        editorPlay = play;
        editorStop = stop;
        editorRefresh = refresh;
        editorSave = save;
        if (registry != null && game != null) {
            registry.attachLiveEditor(game, play, stop, refresh, save);
        }
    }

    /**
     * Sobe (ou reinicia) o bridge HTTP do MCP para o projeto informado.
     *
     * @param projectFolder raiz do projeto ativo.
     * @param exposeNetwork {@code true} vincula a 0.0.0.0 (VPN/LAN); caso contrario 127.0.0.1.
     * @param port          porta TCP.
     * @param token         token Bearer opcional (pode ser {@code null}/vazio).
     * @return a URL base do bridge.
     */
    public static synchronized String start(File projectFolder, boolean exposeNetwork, int port, String token)
            throws Exception {
        if (projectFolder == null || !projectFolder.isDirectory()) {
            throw new IllegalStateException("Projeto invalido para iniciar o MCP.");
        }
        activeProject = projectFolder;
        registry = new IgnisToolRegistry(projectFolder);
        if (editorGame != null) {
            registry.attachLiveEditor(editorGame, editorPlay, editorStop, editorRefresh, editorSave);
        }
        if (windowCaptureSupplier != null) {
            registry.setWindowCaptureSupplier(windowCaptureSupplier);
        }
        String host = exposeNetwork ? "0.0.0.0" : "127.0.0.1";
        McpHttpBridge bridge = McpHttpBridge.start(registry, host, port, token);
        return bridge.getUrl();
    }

    public static synchronized void stop() {
        McpHttpBridge.stop();
    }

    public static synchronized boolean isRunning() {
        return McpHttpBridge.isRunning();
    }

    public static synchronized String getUrl() {
        McpHttpBridge b = McpHttpBridge.current();
        return b != null ? b.getUrl() : null;
    }

    /** Registry ativo (compartilhado com a IA agentica embarcada), ou {@code null}. */
    public static synchronized IgnisToolRegistry getRegistry() {
        return registry;
    }

    public static synchronized File getActiveProject() {
        return activeProject;
    }
}
