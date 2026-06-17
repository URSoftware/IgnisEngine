package com.ignis.editor.fx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Preferencias persistentes do editor JavaFX (estilo VSCode): ultimo projeto
 * aberto e lista de projetos recentes (MRU). Guardado em
 * {@code user.home/.ignis/editor-prefs.json} (mesma convencao .ignis usada por
 * notas/plugins/AI). I/O best-effort: qualquer falha e silenciosa e nunca
 * quebra o editor. Nada em com.ignis.core depende desta classe.
 */
public final class EditorPrefs {

    private static final int MAX_RECENT = 10;
    private static final String LAST = "lastProject";
    private static final String RECENTS = "recentProjects";

    private EditorPrefs() {}

    private static File prefsFile() {
        File dir = new File(System.getProperty("user.home"), ".ignis");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "editor-prefs.json");
    }

    private static JSONObject read() {
        try {
            File f = prefsFile();
            if (f.isFile()) {
                String txt = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                return new JSONObject(txt);
            }
        } catch (Exception ignore) { /* best-effort */ }
        return new JSONObject();
    }

    private static void write(JSONObject json) {
        try {
            Files.write(prefsFile().toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignore) { /* best-effort */ }
    }

    /** Caminho absoluto do ultimo .ignis aberto, ou null se nao houver. */
    public static String getLastProject() {
        String v = read().optString(LAST, "");
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** Lista de caminhos .ignis recentes (mais recente primeiro). */
    public static List<String> getRecentProjects() {
        List<String> out = new ArrayList<>();
        JSONArray arr = read().optJSONArray(RECENTS);
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (s != null && !s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    /** Marca um .ignis como o mais recente (dedupe, MRU, truncado) e como ultimo projeto. */
    public static void addRecent(File ignisFile) {
        if (ignisFile == null) return;
        String path = ignisFile.getAbsolutePath();
        List<String> recents = getRecentProjects();
        recents.removeIf(p -> p.equalsIgnoreCase(path));
        recents.add(0, path);
        while (recents.size() > MAX_RECENT) recents.remove(recents.size() - 1);

        JSONObject json = read();
        json.put(LAST, path);
        json.put(RECENTS, new JSONArray(recents));
        write(json);
    }

    /** Remove os recentes cujo arquivo nao existe mais; retorna a lista valida (MRU). */
    public static List<String> clearMissing() {
        List<String> recents = getRecentProjects();
        List<String> valid = new ArrayList<>();
        for (String p : recents) {
            if (new File(p).isFile()) valid.add(p);
        }
        if (valid.size() != recents.size()) {
            JSONObject json = read();
            json.put(RECENTS, new JSONArray(valid));
            String last = json.optString(LAST, "");
            if (last == null || last.isEmpty() || !new File(last).isFile()) json.remove(LAST);
            write(json);
        }
        return valid;
    }

    /** Obtem o tema ativo do editor de codigo (default: Classic Dark) */
    public static String getCodeEditorTheme() {
        return read().optString("codeEditorTheme", "Classic Dark");
    }

    /** Salva o tema ativo do editor de codigo */
    public static void setCodeEditorTheme(String themeName) {
        JSONObject json = read();
        json.put("codeEditorTheme", themeName);
        write(json);
    }

    /** Auto Save ligado? (scripts e projeto). Default: desligado. */
    public static boolean isAutoSave() {
        return read().optBoolean("autoSave", false);
    }

    /** Liga/desliga o Auto Save. */
    public static void setAutoSave(boolean on) {
        JSONObject json = read();
        json.put("autoSave", on);
        write(json);
    }

    /** Intervalo do Auto Save em segundos (default 30, minimo 5). */
    public static int getAutoSaveIntervalSeconds() {
        int v = read().optInt("autoSaveIntervalSeconds", 30);
        return Math.max(5, v);
    }

    /** Define o intervalo do Auto Save em segundos (minimo 5). */
    public static void setAutoSaveIntervalSeconds(int seconds) {
        JSONObject json = read();
        json.put("autoSaveIntervalSeconds", Math.max(5, seconds));
        write(json);
    }

    // ---------------- Preferencias centralizadas (janela de Configuracoes) ----------------
    // Acessadas pela FxSettingsWindow; cada uma persiste de imediato (estilo VSCode).

    /** Tema da interface do editor: "dark" (Ignis) ou "default" (JavaFX). */
    public static String getEditorTheme() {
        return read().optString("editorTheme", "dark");
    }

    public static void setEditorTheme(String theme) {
        JSONObject json = read();
        json.put("editorTheme", theme);
        write(json);
    }

    /** Lembrar tamanho/posicao da janela e divisores entre sessoes (default: ligado). */
    public static boolean isRememberLayout() {
        return read().optBoolean("rememberLayout", true);
    }

    public static void setRememberLayout(boolean on) {
        JSONObject json = read();
        json.put("rememberLayout", on);
        write(json);
    }

    /** Tamanho da fonte do editor de codigo, em px (default 14, faixa 8..40). */
    public static int getCodeEditorFontSize() {
        int v = read().optInt("codeEditorFontSize", 14);
        return Math.max(8, Math.min(40, v));
    }

    public static void setCodeEditorFontSize(int px) {
        JSONObject json = read();
        json.put("codeEditorFontSize", Math.max(8, Math.min(40, px)));
        write(json);
    }

    /** Grade visivel por padrao no viewport (usa {@code def} se nunca definido). */
    public static boolean getGridVisible(boolean def) {
        return read().optBoolean("gridVisible", def);
    }

    public static void setGridVisible(boolean on) {
        JSONObject json = read();
        json.put("gridVisible", on);
        write(json);
    }

    /** Tamanho da grade em px (usa {@code def} se nunca definido). */
    public static int getGridSize(int def) {
        return read().optInt("gridSize", def);
    }

    public static void setGridSize(int size) {
        JSONObject json = read();
        json.put("gridSize", size);
        write(json);
    }

    /** Mostrar colliders por padrao no viewport (usa {@code def} se nunca definido). */
    public static boolean getShowColliders(boolean def) {
        return read().optBoolean("showColliders", def);
    }

    public static void setShowColliders(boolean on) {
        JSONObject json = read();
        json.put("showColliders", on);
        write(json);
    }

    // ---------------- Layout da janela (Fase F4-B) ----------------
    // Persistencia estilo VSCode do tamanho/posicao da janela principal e das
    // posicoes dos divisores dos SplitPane. Tudo opcional: se ausente, o editor
    // usa os defaults atuais. Chaves agrupadas sob "layout".

    private static JSONObject layout(JSONObject root) {
        JSONObject l = root.optJSONObject("layout");
        if (l == null) { l = new JSONObject(); root.put("layout", l); }
        return l;
    }

    /** Bounds salvos da janela [x, y, largura, altura], ou {@code null} se nao houver. */
    public static double[] getWindowBounds() {
        JSONObject l = read().optJSONObject("layout");
        if (l == null || !l.has("winW") || !l.has("winH")) return null;
        return new double[] {
                l.optDouble("winX", Double.NaN),
                l.optDouble("winY", Double.NaN),
                l.optDouble("winW", Double.NaN),
                l.optDouble("winH", Double.NaN)
        };
    }

    /** A janela estava maximizada no ultimo fechamento? */
    public static boolean isWindowMaximized() {
        JSONObject l = read().optJSONObject("layout");
        return l != null && l.optBoolean("winMax", false);
    }

    /**
     * Salva o estado da janela. Quando maximizada, preserva os ultimos bounds
     * restaurados (passe {@code Double.NaN} para nao sobrescrever) e apenas marca
     * o flag de maximizacao.
     */
    public static void saveWindowState(double x, double y, double w, double h, boolean maximized) {
        JSONObject json = read();
        JSONObject l = layout(json);
        if (!Double.isNaN(w) && !Double.isNaN(h)) {
            l.put("winX", x);
            l.put("winY", y);
            l.put("winW", w);
            l.put("winH", h);
        }
        l.put("winMax", maximized);
        write(json);
    }

    /** Posicoes salvas dos divisores de um SplitPane nomeado, ou {@code null}. */
    public static double[] getDividers(String name) {
        JSONObject l = read().optJSONObject("layout");
        if (l == null) return null;
        JSONArray arr = l.optJSONArray("div_" + name);
        if (arr == null) return null;
        double[] out = new double[arr.length()];
        for (int i = 0; i < out.length; i++) out[i] = arr.optDouble(i, Double.NaN);
        return out;
    }

    /** Salva as posicoes dos divisores de um SplitPane nomeado. */
    public static void saveDividers(String name, double[] positions) {
        if (positions == null) return;
        JSONObject json = read();
        JSONObject l = layout(json);
        JSONArray arr = new JSONArray();
        for (double p : positions) arr.put(p);
        l.put("div_" + name, arr);
        write(json);
    }
}

