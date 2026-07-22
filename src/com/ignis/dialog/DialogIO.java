package com.ignis.dialog;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistência de diálogos em {@code <projeto>/dialogs/<id>.dialog.json}. Escrita
 * atômica (tmp + move) como {@code CutsceneIO}/coordenação MCP: um crash no meio do
 * save nunca deixa um diálogo corrompido pela metade.
 */
public final class DialogIO {

    public static final String DIR = "dialogs";
    public static final String EXT = ".dialog.json";

    private DialogIO() { }

    /** Id válido: letras, dígitos, '-' e '_' (vira nome de arquivo, sem path-traversal). */
    public static boolean isValidId(String id) {
        return id != null && !id.isEmpty() && id.matches("[A-Za-z0-9_-]+");
    }

    public static File fileFor(File projectFolder, String id) {
        return new File(new File(projectFolder, DIR), id + EXT);
    }

    public static boolean exists(File projectFolder, String id) {
        return fileFor(projectFolder, id).isFile();
    }

    public static List<String> listIds(File projectFolder) {
        List<String> ids = new ArrayList<>();
        File dir = new File(projectFolder, DIR);
        File[] files = dir.listFiles((d, n) -> n.endsWith(EXT));
        if (files != null) {
            for (File f : files) {
                ids.add(f.getName().substring(0, f.getName().length() - EXT.length()));
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static void save(File projectFolder, Dialog dialog) throws IOException {
        File target = fileFor(projectFolder, dialog.getId());
        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta " + dir);
        }
        Path tmp = new File(dir, dialog.getId() + EXT + ".tmp").toPath();
        Files.write(tmp, dialog.toJSON().toString(2).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** @return o diálogo, ou null se o arquivo não existe ou está corrompido. */
    public static Dialog load(File projectFolder, String id) {
        File f = fileFor(projectFolder, id);
        if (!f.isFile()) return null;
        try {
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            return Dialog.fromJSON(new JSONObject(content));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean delete(File projectFolder, String id) {
        return fileFor(projectFolder, id).delete();
    }
}
