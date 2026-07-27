package com.ignis.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProjectAuthoringTest {

    @TempDir
    File temporaryFolder;

    @Test
    void exposesCanonicalWorkspaceAndRuntimePaths() throws Exception {
        File workspace = new File(temporaryFolder, "SampleGame");
        File runtime = new File(workspace, "project");
        assertTrue(runtime.mkdirs());
        Files.writeString(new File(workspace, "SampleGame.ignis").toPath(), "descriptor",
                StandardCharsets.UTF_8);

        IgnisToolRegistry registry = new IgnisToolRegistry(runtime);
        JSONObject context = new JSONObject(call(registry, "get_project_context", new JSONObject()));
        JSONObject paths = context.getJSONObject("paths");

        assertEquals(workspace.getCanonicalPath(), paths.getString("workspaceRoot"));
        assertEquals(runtime.getCanonicalPath(), paths.getString("runtimeProjectRoot"));
        assertEquals(new File(workspace, "SampleGame.ignis").getCanonicalPath(),
                paths.getString("ignisProjectFile"));
        assertTrue(context.getJSONObject("authoring").getString("ignisScripts").contains("patch_script"));
    }

    @Test
    void rejectsScriptPathsAndAppliesHashGuardedAtomicPatch() throws Exception {
        File runtime = new File(temporaryFolder, "project");
        assertTrue(runtime.mkdirs());
        IgnisToolRegistry registry = new IgnisToolRegistry(runtime);

        String invalid = call(registry, "create_script",
                new JSONObject().put("scriptName", "../Outside"));
        assertTrue(invalid.startsWith("Erro: scriptName"));
        assertFalse(new File(temporaryFolder, "Outside.java").exists());

        String created = call(registry, "create_script",
                new JSONObject().put("scriptName", "PlayerController"));
        assertTrue(created.contains("Script criado atomicamente"));

        JSONObject info = new JSONObject(call(registry, "get_script_info",
                new JSONObject().put("scriptName", "PlayerController")));
        String initialHash = info.getString("sha256");
        File script = new File(new File(runtime, "scripts"), "PlayerController.java");
        String original = Files.readString(script.toPath(), StandardCharsets.UTF_8);

        String dryRun = call(registry, "patch_script", new JSONObject()
                .put("scriptName", "PlayerController")
                .put("oldText", "public class PlayerController")
                .put("newText", "public final class PlayerController")
                .put("expectedSha256", initialHash)
                .put("dryRun", true));
        assertTrue(dryRun.startsWith("DRY-RUN"));
        assertEquals(original, Files.readString(script.toPath(), StandardCharsets.UTF_8));

        String applied = call(registry, "patch_script", new JSONObject()
                .put("scriptName", "PlayerController")
                .put("oldText", "public class PlayerController")
                .put("newText", "public final class PlayerController")
                .put("expectedSha256", initialHash));
        assertTrue(applied.contains("Patch aplicado atomicamente"));
        assertTrue(Files.readString(script.toPath(), StandardCharsets.UTF_8)
                .contains("public final class PlayerController"));

        String staleWrite = call(registry, "write_script", new JSONObject()
                .put("scriptName", "PlayerController")
                .put("content", original)
                .put("expectedSha256", initialHash));
        assertTrue(staleWrite.startsWith("CONFLITO:"));
        assertTrue(Files.readString(script.toPath(), StandardCharsets.UTF_8)
                .contains("public final class PlayerController"));

        File[] leftovers = script.getParentFile().listFiles((dir, name) -> name.endsWith(".tmp"));
        assertTrue(leftovers == null || leftovers.length == 0);
    }

    @Test
    void refusesAmbiguousPatchUnlessReplaceAllIsExplicit() throws Exception {
        File runtime = new File(temporaryFolder, "project");
        assertTrue(runtime.mkdirs());
        IgnisToolRegistry registry = new IgnisToolRegistry(runtime);
        call(registry, "create_script", new JSONObject().put("scriptName", "Repeated"));
        File script = new File(new File(runtime, "scripts"), "Repeated.java");
        Files.writeString(script.toPath(), "token token", StandardCharsets.UTF_8);

        String refused = call(registry, "patch_script", new JSONObject()
                .put("scriptName", "Repeated")
                .put("oldText", "token")
                .put("newText", "value"));
        assertTrue(refused.contains("ocorre 2 vezes"));
        assertEquals("token token", Files.readString(script.toPath(), StandardCharsets.UTF_8));

        String applied = call(registry, "patch_script", new JSONObject()
                .put("scriptName", "Repeated")
                .put("oldText", "token")
                .put("newText", "value")
                .put("replaceAll", true));
        assertTrue(applied.contains("occurrences=2"));
        assertEquals("value value", Files.readString(script.toPath(), StandardCharsets.UTF_8));
    }

    private static String call(IgnisToolRegistry registry, String tool, JSONObject arguments) throws Exception {
        return registry.get(tool).handler.execute(arguments);
    }
}
