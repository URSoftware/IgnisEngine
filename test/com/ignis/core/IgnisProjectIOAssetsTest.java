package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do empacotamento de assets no .ignis (item 3 do plano de melhorias):
 * {@link IgnisProjectIO#copyAssetsToZip} deve incluir recursivamente os arquivos da
 * pasta de assets, preservando a estrutura de subpastas.
 */
class IgnisProjectIOAssetsTest {

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            byte[] buffer = new byte[4096];
            while ((e = zis.getNextEntry()) != null) {
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    content.write(buffer, 0, read);
                }
                entries.put(e.getName(), content.toByteArray());
            }
        }
        return entries;
    }

    @Test
    void empacotaAssetsRecursivamente(@TempDir Path tmp) throws Exception {
        File assets = tmp.resolve("assets").toFile();
        File sprites = new File(assets, "sprites");
        assertTrue(sprites.mkdirs());
        byte[] pngBytes = { 1, 2, 3, 4, 5 };
        try (FileOutputStream fos = new FileOutputStream(new File(sprites, "hero.png"))) {
            fos.write(pngBytes);
        }
        try (FileOutputStream fos = new FileOutputStream(new File(assets, "config.txt"))) {
            fos.write("cfg".getBytes("UTF-8"));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            IgnisProjectIO.copyAssetsToZip(zos, assets, "assets/");
        }

        Map<String, byte[]> entries = unzip(baos.toByteArray());
        assertTrue(entries.containsKey("assets/"), "entrada de diretório assets/ presente");
        assertTrue(entries.containsKey("assets/sprites/hero.png"), "sprite empacotado com o caminho relativo");
        assertTrue(entries.containsKey("assets/config.txt"), "arquivo raiz de assets empacotado");
        assertArrayEquals(pngBytes, entries.get("assets/sprites/hero.png"), "conteúdo binário preservado");
    }

    @Test
    void pastaInexistenteAindaGravaEntradaDeDiretorio(@TempDir Path tmp) throws Exception {
        File assets = tmp.resolve("assets").toFile(); // não criada
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            IgnisProjectIO.copyAssetsToZip(zos, assets, "assets/");
        }
        Map<String, byte[]> entries = unzip(baos.toByteArray());
        assertTrue(entries.containsKey("assets/"), "sem pasta, ainda grava a entrada assets/ (estrutura)");
    }
}
