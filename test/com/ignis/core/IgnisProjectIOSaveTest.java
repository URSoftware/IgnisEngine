package com.ignis.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gravacao do .ignis — a garantia e que <b>um save que falha nao destroi o projeto
 * anterior</b>.
 *
 * <p>Em 16/07/2026 o auto-save do editor trocou um projeto de 3,5 MB por um zip vazio
 * de 140 bytes: o ZipOutputStream abria o .ignis real direto e
 * {@code new FileOutputStream} trunca o arquivo na hora, entao a excecao que veio
 * depois (durante o {@code toJSON}) deixou o projeto destruido. Serializar num
 * temporario e so entao trocar o arquivo remove essa classe inteira de perda — vale
 * tambem para disco cheio ou a maquina desligando no meio.</p>
 */
class IgnisProjectIOSaveTest {

    /** Cena que falha ao serializar, como o script cujo classloader se perdeu. */
    private static final class CenaQuebrada extends Scene {
        CenaQuebrada(String nome) {
            super(nome);
        }

        @Override
        public org.json.JSONObject toJSON() {
            throw new IllegalStateException("falha simulada na serializacao");
        }
    }

    /** Faz duas serializacoes chegarem juntas depois de seus temporarios serem abertos. */
    private static final class CenaSincronizada extends Scene {
        private final CyclicBarrier barrier;
        private final AtomicBoolean firstSerialization = new AtomicBoolean(true);

        CenaSincronizada(String nome, CyclicBarrier barrier) {
            super(nome);
            this.barrier = barrier;
        }

        @Override
        public org.json.JSONObject toJSON() {
            if (firstSerialization.compareAndSet(true, false)) {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("falha ao sincronizar saves concorrentes", exception);
                }
            }
            return super.toJSON();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void resetProjectsRoot() {
        // Override e estatico: sem isto vaza para os outros testes.
        IgnisProjectIO.setProjectsRootFolderForTest(null);
    }

    private Project projectWith(Scene scene, File ignisFile) {
        Project project = new Project("TesteSave");
        project.clearScenes();
        project.addScene(scene);
        project.setProjectFile(ignisFile);
        return project;
    }

    @Test
    void failedSaveKeepsThePreviousProjectIntact(@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        File projects = root.resolve("projects").toFile();
        IgnisProjectIO.setProjectsRootFolderForTest(projects);
        File ignis = new File(new File(projects, "TesteSave"), "TesteSave.ignis");

        // 1. Um save bom: o projeto existe no disco.
        IgnisProjectIO.save(projectWith(new Scene("MainScene"), ignis), ignis);
        assertTrue(ignis.isFile());
        long tamanhoBom = ignis.length();
        assertTrue(tamanhoBom > 0);

        // 2. Um save que falha no meio da serializacao (foi um NoClassDefFoundError
        //    vindo do reflection do auto-save; a origem nao importa, o efeito sim).
        assertThrows(RuntimeException.class,
                () -> IgnisProjectIO.save(projectWith(new CenaQuebrada("MainScene"), ignis), ignis));

        // 3. O projeto do disco tem de ser exatamente o de antes — nao um zip vazio.
        assertEquals(tamanhoBom, ignis.length(),
                "o save que falhou destruiu o projeto anterior");
        Project recarregado = IgnisProjectIO.load(ignis, new Game());
        assertNotNull(recarregado);
        assertEquals("MainScene", recarregado.getScenes().get(0).getSceneName());

        // 4. E nao pode deixar lixo para tras.
        try (java.util.stream.Stream<Path> arquivos = Files.list(ignis.getParentFile().toPath())) {
            assertTrue(arquivos.noneMatch(p -> p.toString().endsWith(".tmp")),
                    "temporario do save ficou orfao no projeto");
        }
    }

    @Test
    void successfulSaveReplacesTheProject(@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        File projects = root.resolve("projects").toFile();
        IgnisProjectIO.setProjectsRootFolderForTest(projects);
        File ignis = new File(new File(projects, "TesteSave"), "TesteSave.ignis");

        IgnisProjectIO.save(projectWith(new Scene("Primeira"), ignis), ignis);
        IgnisProjectIO.save(projectWith(new Scene("Segunda"), ignis), ignis);

        Project recarregado = IgnisProjectIO.load(ignis, new Game());
        assertEquals("Segunda", recarregado.getScenes().get(0).getSceneName());
    }

    @Test
    void concurrentSavesUseIndependentTemporaryFiles(@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        File projects = root.resolve("projects").toFile();
        IgnisProjectIO.setProjectsRootFolderForTest(projects);
        File ignis = new File(new File(projects, "TesteSave"), "TesteSave.ignis");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> {
                try {
                    IgnisProjectIO.save(
                            projectWith(new CenaSincronizada("Primeira", barrier), ignis), ignis);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            Future<?> second = executor.submit(() -> {
                try {
                    IgnisProjectIO.save(
                            projectWith(new CenaSincronizada("Segunda", barrier), ignis), ignis);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Project recarregado = IgnisProjectIO.load(ignis, new Game());
        String sceneName = recarregado.getScenes().get(0).getSceneName();
        assertTrue(Set.of("Primeira", "Segunda").contains(sceneName));
        try (java.util.stream.Stream<Path> files = Files.list(ignis.getParentFile().toPath())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".ignis.tmp")),
                    "temporario concorrente ficou orfao no projeto");
        }
    }
}
