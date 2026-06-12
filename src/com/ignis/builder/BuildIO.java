package com.ignis.builder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File-system helpers shared by build strategies.
 */
final class BuildIO {

    private BuildIO() {
    }

    /** Recursively copies a directory, skipping top-level entries whose name is in excludeNames. */
    static void copyDirectory(File source, File destination, Set<String> excludeNames) throws IOException {
        if (!source.isDirectory()) {
            throw new IOException("Not a directory: " + source);
        }
        Files.createDirectories(destination.toPath());
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (excludeNames != null && excludeNames.contains(child.getName())) {
                continue;
            }
            File target = new File(destination, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target, null);
            } else {
                Files.copy(child.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Deletes a directory tree (used to clean previous build output). */
    static void deleteDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectory(child);
                } else if (!child.delete()) {
                    throw new IOException("Could not delete " + child);
                }
            }
        }
        if (!dir.delete()) {
            throw new IOException("Could not delete " + dir);
        }
    }

    /** Zips the content of a directory (the directory itself becomes the zip root folder). */
    static void zipDirectory(File dir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipRecursive(dir, dir.getName(), zos);
        }
    }

    private static void zipRecursive(File file, String entryName, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                zipRecursive(child, entryName + "/" + child.getName(), zos);
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file.toPath(), zos);
            zos.closeEntry();
        }
    }

    /**
     * Creates an executable jar from the engine's compiled classes.
     * Works both when the engine runs from a classes directory (IDE / mvn exec)
     * and when it runs from a packaged jar.
     */
    static void createEngineJar(File codeSource, File outputJar, String mainClass, String classPath)
            throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        if (classPath != null && !classPath.isEmpty()) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        }

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
            if (codeSource.isDirectory()) {
                addClassesDir(codeSource, codeSource, jos);
            } else {
                copyJarEntries(codeSource, jos);
            }
        }
    }

    private static void addClassesDir(File root, File current, JarOutputStream jos) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        Path rootPath = root.toPath();
        for (File child : children) {
            if (child.isDirectory()) {
                addClassesDir(root, child, jos);
            } else {
                String entryName = rootPath.relativize(child.toPath()).toString().replace('\\', '/');
                jos.putNextEntry(new JarEntry(entryName));
                Files.copy(child.toPath(), jos);
                jos.closeEntry();
            }
        }
    }

    private static void copyJarEntries(File sourceJar, JarOutputStream jos) throws IOException {
        try (JarFile jar = new JarFile(sourceJar)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                jos.putNextEntry(new JarEntry(entry.getName()));
                try (InputStream in = jar.getInputStream(entry)) {
                    transfer(in, jos);
                }
                jos.closeEntry();
            }
        }
    }

    private static void transfer(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    /** Locates the code source (classes dir or jar) of a loaded class. */
    static File codeSourceOf(Class<?> clazz) throws IOException {
        try {
            return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IOException("Could not locate code source of " + clazz.getName(), e);
        }
    }

    static void writeString(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** Best-effort executable bit (no-op on Windows file systems). */
    static void makeExecutable(File file) {
        if (!file.setExecutable(true, false)) {
            // Ignored: irrelevant on Windows; zip distribution notes chmod in README
        }
    }

    /** Reads a single entry from a zip file as UTF-8 text, or null when absent. */
    static String readZipEntryText(File zipFile, String entryName) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            java.util.zip.ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                transfer(in, baos);
                return baos.toString("UTF-8");
            }
        }
    }

    /** Lists entry names of a zip matching a prefix and suffix. */
    static java.util.List<String> listZipEntries(File zipFile, String prefix, String suffix) throws IOException {
        java.util.List<String> names = new java.util.ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()
                        && entry.getName().startsWith(prefix)
                        && entry.getName().endsWith(suffix)) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }
}
