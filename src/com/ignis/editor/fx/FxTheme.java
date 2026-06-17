package com.ignis.editor.fx;

import javafx.scene.Scene;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;

/**
 * Tema escuro unificado do editor JavaFX (Fase F4-A da migracao).
 *
 * <p>Resolve o stylesheet {@code ignis-dark.css} (empacotado no classpath ao lado
 * desta classe; a config de resources do pom copia non-java de {@code src/} para
 * {@code target/classes}) e o aplica a qualquer {@link Scene}. Inclui fallback
 * para o arquivo em {@code src/} quando rodando sem os recursos processados.
 *
 * <p>Best-effort: se o CSS nao for encontrado, falha silenciosamente e a cena
 * usa o tema padrao do JavaFX. Nada em {@code com.ignis.core} depende disto.
 */
public final class FxTheme {

    private static final String RESOURCE = "ignis-dark.css";
    private static final String DEV_PATH = "src/com/ignis/editor/fx/ignis-dark.css";
    // Marca cenas que passaram por apply(), para o refresh global so tocar nas
    // janelas que nós tematizamos (ex: nao mexer no FxCodeEditor, que tem tema proprio).
    private static final String MARK = "ignis-themed";

    private static String cachedUrl;
    private static boolean resolved;

    private FxTheme() {}

    /** O tema escuro Ignis esta ativo? (caso contrario, tema padrao do JavaFX). */
    public static boolean isDark() {
        return !"default".equalsIgnoreCase(EditorPrefs.getEditorTheme());
    }

    /** URL externa do stylesheet, ou {@code null} se nao localizado. */
    public static synchronized String stylesheet() {
        if (resolved) return cachedUrl;
        resolved = true;
        URL url = FxTheme.class.getResource(RESOURCE);
        if (url != null) {
            cachedUrl = url.toExternalForm();
        } else {
            File f = new File(DEV_PATH);
            if (f.isFile()) cachedUrl = f.toURI().toString();
        }
        return cachedUrl;
    }

    /**
     * Aplica (ou remove) o tema na cena conforme a preferencia atual. Idempotente.
     * Marca a cena para que {@link #refreshAllWindows()} possa retematiza-la depois.
     */
    public static void apply(Scene scene) {
        if (scene == null) return;
        scene.getProperties().put(MARK, Boolean.TRUE);
        String ss = stylesheet();
        if (ss == null) return;
        if (isDark()) {
            if (!scene.getStylesheets().contains(ss)) scene.getStylesheets().add(ss);
        } else {
            scene.getStylesheets().remove(ss);
        }
    }

    /**
     * Reaplica o tema a todas as janelas abertas que já foram tematizadas via
     * {@link #apply(Scene)} — usado quando o usuario troca o tema nas Configuracoes.
     */
    public static void refreshAllWindows() {
        for (Window w : Window.getWindows()) {
            Scene s = w.getScene();
            if (s != null && Boolean.TRUE.equals(s.getProperties().get(MARK))) {
                apply(s);
            }
        }
    }
}
