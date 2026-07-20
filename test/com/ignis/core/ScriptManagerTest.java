package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptManagerTest {

    @Test
    void reportsCompiledScriptWithoutTryingToLoadMissingClass() throws Exception {
        Path projectFolder = Files.createTempDirectory("ignis-script-compiled-check");
        ScriptManager manager = new ScriptManager(projectFolder.toFile());

        assertFalse(manager.hasCompiledScript("FreshScript"));

        Path compiled = projectFolder.resolve("scripts/compiled/FreshScript.class");
        Files.createDirectories(compiled.getParent());
        Files.write(compiled, new byte[] {0});

        assertTrue(manager.hasCompiledScript("FreshScript"));
        assertFalse(manager.hasCompiledScript(""));
        assertFalse(manager.hasCompiledScript(null));
    }

    @TempDir
    Path projectFolder;

    @Test
    void initializesScriptExactlyOnceWhenAttached() throws Exception {
        Path script = projectFolder.resolve("scripts/CountingScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script,
                "public class CountingScript extends com.ignis.core.IgnisScript {\n"
                + "  public static int initCount;\n"
                + "  @Override public void init(com.ignis.core.GameObject owner, com.ignis.core.Game game) {\n"
                + "    super.init(owner, game); initCount++;\n"
                + "  }\n"
                + "}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        assertTrue(manager.compileScript(script.toFile()));
        Game game = new Game();
        GameObject owner = new GameObject("Owner", game, 0, 0, 16, 16);

        IgnisScript instance = manager.createScriptInstance("CountingScript", owner, game);
        assertNotNull(instance);
        assertEquals(0, instance.getClass().getField("initCount").getInt(null));

        owner.addComponent(instance);
        assertEquals(1, instance.getClass().getField("initCount").getInt(null));
        manager.close();
    }

    @Test
    void retainsPreviousClassLoadersUntilManagerCloses() throws Exception {
        Path script = projectFolder.resolve("scripts/TestScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "public class TestScript extends com.ignis.core.IgnisScript {}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        assertTrue(manager.compileScript(script.toFile()));
        assertTrue(manager.compileScript(script.toFile()));
        assertEquals(1, manager.retainedClassLoaderCount());

        manager.close();
        assertEquals(0, manager.retainedClassLoaderCount());
    }

    @Test
    void releasesRetiredClassLoadersAtExplicitSceneBoundary() throws Exception {
        Path script = projectFolder.resolve("scripts/TestScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "public class TestScript extends com.ignis.core.IgnisScript {}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        for (int i = 0; i < 4; i++) {
            assertTrue(manager.compileScript(script.toFile()));
        }
        assertEquals(3, manager.retainedClassLoaderCount());

        assertEquals(3, manager.releaseRetiredClassLoaders());
        assertEquals(0, manager.retainedClassLoaderCount());
        assertEquals(0, manager.releaseRetiredClassLoaders());

        manager.close();
    }

    /**
     * Todo loader aposentado fica rastreado ate o close(), sem teto.
     *
     * <p>Ja tentei limitar isto e as duas tentativas foram piores: fechar os antigos
     * quebra a resolucao preguicosa de tipos das instancias que ainda vivem neles, e
     * soltar a referencia sem fechar faz o close() perder o rastro e vazar o handle do
     * .jar. Perder o rastro de um loader e um vazamento de recurso — este teste trava
     * isso.</p>
     */
    @Test
    void everyRetiredClassLoaderIsTrackedUntilClose() throws Exception {
        Path script = projectFolder.resolve("scripts/TestScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "public class TestScript extends com.ignis.core.IgnisScript {}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        for (int i = 0; i < 6; i++) {
            assertTrue(manager.compileScript(script.toFile()));
        }
        // 6 recargas -> 5 loaders aposentados, todos rastreados (o 6o e o atual).
        assertEquals(5, manager.retainedClassLoaderCount());

        manager.close();
        assertEquals(0, manager.retainedClassLoaderCount());
    }

    /**
     * Regressao do crash de 16/07/2026: o auto-save do editor morria com
     * {@code NoClassDefFoundError} porque o ScriptManager fechava os classloaders
     * aposentados.
     *
     * <p>Uma instancia que esta na cena continua pertencendo ao loader que a definiu,
     * e resolucao de tipo e <b>preguicosa</b>: o tipo de um campo so e carregado
     * quando alguem olha para ele — por exemplo o reflection do auto-save chamando
     * {@code getDeclaredFields()}. Se aquele loader foi fechado no meio do caminho, a
     * resolucao falha. Compilar algumas vezes sem recarregar a cena basta para
     * reproduzir; nao adianta supor que "a cena ja foi recriada".</p>
     */
    /**
     * Empacota uma classe de dominio num jar dentro de {@code project/libs}, como faz
     * o TensuraGame com rimuru-survivors-domain.jar. E o unico jeito de um script ter
     * um campo cujo tipo so o classloader do projeto resolve — que e o cenario do
     * crash.
     */
    private void writeDomainLibJar() throws Exception {
        Path build = projectFolder.resolve("build-lib");
        Path source = build.resolve("com/exemplo/dominio/RunState.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.exemplo.dominio;\npublic class RunState {}\n");

        Path classes = build.resolve("classes");
        Files.createDirectories(classes);
        // getTask (e nao compiler.run): o run() carrega recursos de locale e esbarra
        // no aviso do JaCoCo com JDK-25. O proprio ScriptManager usa getTask.
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        javax.tools.StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends javax.tools.JavaFileObject> units =
                files.getJavaFileObjectsFromFiles(java.util.List.of(source.toFile()));
        assertTrue(compiler.getTask(null, files, null,
                java.util.List.of("-d", classes.toString()), null, units).call());
        files.close();

        Path libs = projectFolder.resolve("libs");
        Files.createDirectories(libs);
        Path classFile = classes.resolve("com/exemplo/dominio/RunState.class");
        try (java.util.jar.JarOutputStream jar = new java.util.jar.JarOutputStream(
                Files.newOutputStream(libs.resolve("dominio.jar")))) {
            jar.putNextEntry(new java.util.zip.ZipEntry("com/exemplo/dominio/RunState.class"));
            jar.write(Files.readAllBytes(classFile));
            jar.closeEntry();
        }
    }

    /**
     * Regressao do crash de 16/07/2026: o auto-save do editor morria com
     * {@code NoClassDefFoundError: com/rimurusurvivors/domain/RunSimulation} porque o
     * ScriptManager fechava os classloaders aposentados.
     *
     * <p>Uma instancia que esta na cena continua pertencendo ao loader que a definiu,
     * e resolucao de tipo e <b>preguicosa</b>: o tipo de um campo so e carregado
     * quando alguem olha para ele — por exemplo o reflection do auto-save chamando
     * {@code getDeclaredFields()}. Se aquele loader foi fechado no meio do caminho, a
     * resolucao falha. Bastam algumas recompilacoes sem recarregar a cena; a premissa
     * de que "depois de N gerações a cena ja foi recriada" e falsa.</p>
     */
    @Test
    void oldClassesStillResolveLibTypesAfterManyRecompiles() throws Exception {
        writeDomainLibJar();

        Path script = projectFolder.resolve("scripts/OwnerScript.java");
        Files.createDirectories(script.getParent());
        Files.writeString(script, "public class OwnerScript extends com.ignis.core.IgnisScript {\n"
                + "    private com.exemplo.dominio.RunState estado;\n"
                + "}\n");

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        assertEquals(1, manager.compileAllScripts());

        // A classe que "esta na cena", presa ao loader daquele momento.
        Class<?> owner = manager.loadScriptClass("OwnerScript");
        assertNotNull(owner);

        // Recompila varias vezes, como quem mexe no projeto pelo editor: o loader do
        // 'owner' e aposentado e sai da lista de referencias.
        for (int i = 0; i < 4; i++) {
            assertEquals(1, manager.compileAllScripts());
        }

        // Exatamente o que o auto-save faz (ScriptSerializationHelper.getSerializedFields).
        // Com o loader aposentado FECHADO, isto lancava NoClassDefFoundError.
        java.lang.reflect.Field[] fields = owner.getDeclaredFields();
        assertEquals(1, fields.length);
        assertEquals("RunState", fields[0].getType().getSimpleName());

        manager.close();
    }

    @Test
    void compileAllReloadsClassLoaderOncePerBatch() throws Exception {
        for (String name : new String[] {"AlphaScript", "BetaScript", "GammaScript"}) {
            Path script = projectFolder.resolve("scripts/" + name + ".java");
            Files.createDirectories(script.getParent());
            Files.writeString(script,
                    "public class " + name + " extends com.ignis.core.IgnisScript {}\n");
        }

        ScriptManager manager = new ScriptManager(projectFolder.toFile());
        assertEquals(3, manager.compileAllScripts());
        // Primeiro lote: nenhum loader anterior para aposentar.
        assertEquals(0, manager.retainedClassLoaderCount());

        assertEquals(3, manager.compileAllScripts());
        // Segundo lote: UMA recarga -> UM aposentado (e nao um por script).
        assertEquals(1, manager.retainedClassLoaderCount());

        manager.close();
    }
}
