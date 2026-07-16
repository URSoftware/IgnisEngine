package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache de prefab do {@link PrefabManager}.
 *
 * <p>Antes, cada {@code instantiatePrefab} lia o .prefab.json do disco e reparseava
 * o JSON — num spawner isso e I/O centenas de vezes por segundo. O cache elimina
 * isso; estes testes fixam as duas garantias que importam: reuso real do parse e
 * invalidacao quando o prefab muda (o cache nao pode "congelar" o prefab).</p>
 */
class PrefabManagerCacheTest {

    @TempDir
    Path projectFolder;

    private Path writePrefab(String name, int width) throws Exception {
        Path prefabs = projectFolder.resolve("prefabs");
        Files.createDirectories(prefabs);
        Path file = prefabs.resolve(name + ".prefab.json");
        Files.writeString(file, """
                {
                  "type": "Square",
                  "name": "%s",
                  "prefabName": "%s",
                  "transform": { "x": 0, "y": 0, "width": %d, "height": 32, "rotation": 0 }
                }
                """.formatted(name, name, width));
        return file;
    }

    private PrefabManager newManager() {
        // Game headless: o PrefabManager so o usa como dono das entidades criadas.
        return new PrefabManager(projectFolder.toFile(), new Game(), null);
    }

    @Test
    void doesNotRereadTheFileWhenItHasNotChanged() throws Exception {
        Path file = writePrefab("Orb", 16);
        PrefabManager manager = newManager();

        GameObject first = manager.instantiatePrefab("Orb", 10, 10);
        assertNotNull(first);
        assertEquals(16, first.getWidth());

        // Reescreve o conteudo PRESERVANDO o mtime: para o manager, o arquivo nao
        // mudou. Se ele relesse o disco a cada spawn (o comportamento antigo), a
        // largura nova vazaria aqui — servir 16 prova que o parse foi reusado.
        long originalStamp = file.toFile().lastModified();
        writePrefab("Orb", 64);
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(originalStamp));

        GameObject second = manager.instantiatePrefab("Orb", 20, 20);
        assertEquals(16, second.getWidth(), "arquivo sem mtime novo: deve vir do cache");
        assertNotSame(first, second, "cada spawn e um objeto novo; so o JSON e reusado");
    }

    @Test
    void eachInstanceGetsItsOwnIdentityAndPosition() throws Exception {
        writePrefab("Orb", 16);
        PrefabManager manager = newManager();

        GameObject a = manager.instantiatePrefab("Orb", 30, 40);
        GameObject b = manager.instantiatePrefab("Orb", 50, 60);

        // O JSON compartilhado nao pode vazar estado entre instancias.
        assertNotSame(a, b);
        assertTrue(!a.getId().equals(b.getId()), "cada instancia precisa de id proprio");
        assertEquals(30.0, a.getX());
        assertEquals(50.0, b.getX());
    }

    @Test
    void reparsesAfterThePrefabFileChanges() throws Exception {
        Path file = writePrefab("Orb", 16);
        PrefabManager manager = newManager();

        assertEquals(16, manager.instantiatePrefab("Orb", 0, 0).getWidth());

        // Reescreve com outra largura e forca um mtime novo (a resolucao do FS pode
        // ser de 1s; sem isso o teste ficaria flaky).
        writePrefab("Orb", 64);
        Files.setLastModifiedTime(file,
                java.nio.file.attribute.FileTime.fromMillis(file.toFile().lastModified() + 5000));

        assertEquals(64, manager.instantiatePrefab("Orb", 0, 0).getWidth(),
                "editar o prefab precisa valer no proximo spawn — o cache nao pode congelar");
    }

    @Test
    void savingAPrefabInvalidatesTheCachedCopy() throws Exception {
        writePrefab("Orb", 16);
        PrefabManager manager = newManager();
        assertEquals(16, manager.instantiatePrefab("Orb", 0, 0).getWidth());

        // savePrefab reescreve o arquivo: mesmo dentro do mesmo tick de mtime, o
        // proximo spawn tem de refletir o objeto salvo.
        GameObject source = new GameObject("Orb", new Game(), 0, 0, 99, 32);
        assertTrue(manager.savePrefab(source, "Orb"));

        assertEquals(99, manager.instantiatePrefab("Orb", 0, 0).getWidth());
    }

    @Test
    void missingPrefabStillReturnsNull() {
        PrefabManager manager = newManager();
        org.junit.jupiter.api.Assertions.assertNull(manager.instantiatePrefab("NaoExiste", 0, 0));
    }
}
