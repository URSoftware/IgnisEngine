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
}

