package com.ignis.cutscene;

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
 * Persistencia de cutscenes em {@code <projeto>/cutscenes/<nome>.cutscene.json}.
 * Escrita atomica (tmp + move) como a coordenacao MCP: um crash no meio do save
 * nunca deixa uma cutscene corrompida pela metade.
 */
public final class CutsceneIO {

    public static final String DIR = "cutscenes";
    public static final String EXT = ".cutscene.json";

    private CutsceneIO() { }

    /** Nome valido de cutscene: letras, digitos, '-' e '_' (vira nome de arquivo). */
    public static boolean isValidName(String name) {
        return name != null && !name.isEmpty() && name.matches("[A-Za-z0-9_-]+");
    }

    public static File fileFor(File projectFolder, String name) {
        return new File(new File(projectFolder, DIR), name + EXT);
    }

    public static boolean exists(File projectFolder, String name) {
        return fileFor(projectFolder, name).isFile();
    }

    public static List<String> listNames(File projectFolder) {
        List<String> names = new ArrayList<>();
        File dir = new File(projectFolder, DIR);
        File[] files = dir.listFiles((d, n) -> n.endsWith(EXT));
        if (files != null) {
            for (File f : files) {
                names.add(f.getName().substring(0, f.getName().length() - EXT.length()));
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    public static void save(File projectFolder, Cutscene cs) throws IOException {
        File target = fileFor(projectFolder, cs.getName());
        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta " + dir);
        }
        Path tmp = new File(dir, cs.getName() + EXT + ".tmp").toPath();
        Files.write(tmp, cs.toJSON().toString(2).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** @return a cutscene, ou null se o arquivo nao existe ou esta corrompido. */
    public static Cutscene load(File projectFolder, String name) {
        File f = fileFor(projectFolder, name);
        if (!f.isFile()) return null;
        try {
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            return Cutscene.fromJSON(new JSONObject(content));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean delete(File projectFolder, String name) {
        return fileFor(projectFolder, name).delete();
    }
}
