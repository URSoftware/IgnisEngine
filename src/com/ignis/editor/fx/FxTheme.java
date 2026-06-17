package com.ignis.editor.fx;

import javafx.scene.Scene;

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

    private static String cachedUrl;
    private static boolean resolved;

    private FxTheme() {}

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

    /** Aplica o tema escuro a uma cena (idempotente; ignora se ja aplicado). */
    public static void apply(Scene scene) {
        if (scene == null) return;
        String ss = stylesheet();
        if (ss != null && !scene.getStylesheets().contains(ss)) {
            scene.getStylesheets().add(ss);
        }
    }
}
