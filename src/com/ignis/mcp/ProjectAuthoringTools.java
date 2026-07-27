package com.ignis.mcp;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Contexto de workspace e autoria segura de IgnisScripts para agentes.
 */
final class ProjectAuthoringTools {

    private final IgnisToolRegistry registry;

    ProjectAuthoringTools(IgnisToolRegistry registry) {
        this.registry = registry;
    }

    void registerAll() {
        registry.add("get_project_context",
                "Retorna contexto canonico do projeto ativo: caminhos absolutos do workspace e da raiz runtime, "
                        + "arquivo .ignis, diretorios de scripts/dados/assets e a politica recomendada de autoria. "
                        + "Use antes de ler ou editar codigo; nunca adivinhe caminhos locais.",
                IgnisToolRegistry.objectSchema(),
                args -> projectContext().toString(2));

        Map<String, String> patchProps = new java.util.LinkedHashMap<>();
        patchProps.put("scriptName", "Nome do script (ex: PlayerController)");
        patchProps.put("oldText", "Trecho exato esperado no arquivo");
        patchProps.put("newText", "Trecho substituto");
        patchProps.put("expectedSha256", "Hash SHA-256 retornado por get_script_info; recusa se o arquivo mudou");
        patchProps.put("replaceAll", "Se true, troca todas as ocorrencias; padrao false exige exatamente uma");
        patchProps.put("dryRun", "Se true, valida e calcula o novo hash sem gravar");
        JSONObject patchSchema = IgnisToolRegistry.schemaWith(
                patchProps, List.of("scriptName", "oldText", "newText"));
        setBooleanProperty(patchSchema, "replaceAll", patchProps.get("replaceAll"));
        setBooleanProperty(patchSchema, "dryRun", patchProps.get("dryRun"));
        registry.add("patch_script",
                "Aplica uma substituicao textual exata e atomica em um script. Por padrao exige que oldText ocorra "
                        + "exatamente uma vez; aceita expectedSha256 para detectar edicao concorrente.",
                patchSchema,
                this::patchScript);

        registry.add("get_script_info",
                "Retorna caminho absoluto, tamanho, ultima modificacao e SHA-256 de um script. "
                        + "Passe o hash a write_script/patch_script como expectedSha256.",
                IgnisToolRegistry.schemaWith(
                        Map.of("scriptName", "Nome do script (ex: PlayerController)"),
                        List.of("scriptName")),
                args -> scriptInfo(args.optString("scriptName", "").trim()));
    }

    String validateScriptName(String name) {
        if (name == null || name.isBlank()) return "Erro: 'scriptName' obrigatorio.";
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return "Erro: scriptName deve ser um identificador Java simples, sem caminho ou extensao.";
        }
        return null;
    }

    File scriptFile(String scriptName) {
        return new File(registry.scriptManager().getScriptsFolder(), scriptName + ".java");
    }

    String sha256(File file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file.toPath())));
    }

    String sha256(String content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    String canonicalPath(File file) {
        File canonical = canonicalFile(file);
        return canonical != null ? canonical.getPath() : "";
    }

    private JSONObject projectContext() {
        File runtimeRoot = canonicalFile(registry.projectFolder);
        File workspaceRoot = detectWorkspaceRoot(runtimeRoot);
        File descriptor = findProjectDescriptor(workspaceRoot);
        File engineRoot = detectEngineRoot();

        JSONObject paths = new JSONObject()
                .put("runtimeProjectRoot", canonicalPath(runtimeRoot))
                .put("workspaceRoot", canonicalPath(workspaceRoot))
                .put("scriptsRoot", canonicalPath(new File(runtimeRoot, "scripts")))
                .put("dataRoot", canonicalPath(new File(runtimeRoot, "data")))
                .put("assetsRoot", canonicalPath(new File(runtimeRoot, "assets")))
                .put("libsRoot", canonicalPath(new File(runtimeRoot, "libs")));
        if (descriptor != null) paths.put("ignisProjectFile", canonicalPath(descriptor));
        if (engineRoot != null) paths.put("engineSourceRoot", canonicalPath(engineRoot));

        return new JSONObject()
                .put("projectName", workspaceRoot.getName())
                .put("paths", paths)
                .put("authoring", new JSONObject()
                        .put("ignisScripts", "Use get_script_info + patch_script/write_script. Essas operacoes "
                                + "respeitam claims, detectam conflito por SHA-256 e gravam atomicamente.")
                        .put("sceneAndAssets", "Prefira ferramentas semanticas do MCP; elas conhecem o editor, "
                                + "o modo Play/Edit, validacao e coordenacao.")
                        .put("domainAndBuildCode", "Se o agente possui filesystem, edite a partir de workspaceRoot "
                                + "(por exemplo domain-lib). O MCP limita autoria de codigo a scripts do runtime.")
                        .put("rule", "Nunca derive ou adivinhe um caminho local; use os caminhos retornados aqui."))
                .put("editorAttached", registry.hasLiveEditor());
    }

    private String patchScript(JSONObject args) throws Exception {
        String name = args.optString("scriptName", "").trim();
        String validation = validateScriptName(name);
        if (validation != null) return validation;

        String oldText = args.optString("oldText", "");
        String newText = args.optString("newText", "");
        if (oldText.isEmpty()) return "Erro: 'oldText' nao pode ser vazio.";

        File script = scriptFile(name);
        if (!script.isFile()) return "Erro: script nao encontrado: " + name;
        String current = Files.readString(script.toPath(), StandardCharsets.UTF_8);
        String currentHash = sha256(current);
        String expectedHash = args.optString("expectedSha256", "").trim();
        if (!expectedHash.isEmpty() && !expectedHash.equalsIgnoreCase(currentHash)) {
            return "CONFLITO: o script mudou desde a leitura. esperado=" + expectedHash
                    + ", atual=" + currentHash + ". Leia novamente antes de editar.";
        }

        int occurrences = countOccurrences(current, oldText);
        boolean replaceAll = args.optBoolean("replaceAll", false);
        if (occurrences == 0) return "Erro: oldText nao foi encontrado; nenhuma gravacao realizada.";
        if (!replaceAll && occurrences != 1) {
            return "Erro: oldText ocorre " + occurrences
                    + " vezes. Torne o trecho unico ou passe replaceAll=true conscientemente.";
        }

        String updated = replaceAll
                ? current.replace(oldText, newText)
                : replaceFirstLiteral(current, oldText, newText);
        String updatedHash = sha256(updated);
        if (args.optBoolean("dryRun", false)) {
            return "DRY-RUN: patch_script validado; nenhuma gravacao.\npath=" + canonicalPath(script)
                    + "\noccurrences=" + occurrences + "\nsha256Before=" + currentHash
                    + "\nsha256After=" + updatedHash;
        }

        boolean saved = registry.scriptManager().saveScriptContent(name, updated);
        return saved ? "Patch aplicado atomicamente: " + name + "\npath=" + canonicalPath(script)
                + "\noccurrences=" + (replaceAll ? occurrences : 1) + "\nsha256=" + updatedHash
                : "Erro ao salvar script: " + name;
    }

    private String scriptInfo(String name) throws Exception {
        String validation = validateScriptName(name);
        if (validation != null) return validation;
        File script = scriptFile(name);
        if (!script.isFile()) return "Erro: script nao encontrado: " + name;
        return new JSONObject()
                .put("scriptName", name)
                .put("path", canonicalPath(script))
                .put("bytes", script.length())
                .put("lastModifiedEpochMs", script.lastModified())
                .put("sha256", sha256(script))
                .toString(2);
    }

    private static void setBooleanProperty(JSONObject schema, String name, String description) {
        schema.getJSONObject("properties").put(name, new JSONObject()
                .put("type", "boolean")
                .put("description", description));
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(search, offset)) >= 0) {
            count++;
            offset += search.length();
        }
        return count;
    }

    private static String replaceFirstLiteral(String text, String search, String replacement) {
        int index = text.indexOf(search);
        return text.substring(0, index) + replacement + text.substring(index + search.length());
    }

    private static File detectWorkspaceRoot(File runtimeRoot) {
        File parent = runtimeRoot.getParentFile();
        if (parent == null) return runtimeRoot;
        if (new File(parent, "pom.xml").isFile() || new File(parent, "build.json").isFile()
                || findProjectDescriptor(parent) != null) {
            return canonicalFile(parent);
        }
        return runtimeRoot;
    }

    private static File findProjectDescriptor(File directory) {
        if (directory == null || !directory.isDirectory()) return null;
        File[] descriptors = directory.listFiles((dir, name) -> name.endsWith(".ignis"));
        if (descriptors == null || descriptors.length == 0) return null;
        Arrays.sort(descriptors, Comparator.comparing(File::getName));
        return canonicalFile(descriptors[0]);
    }

    private static File detectEngineRoot() {
        File current = canonicalFile(new File(System.getProperty("user.dir", ".")));
        for (int depth = 0; current != null && depth < 5; depth++, current = current.getParentFile()) {
            if (new File(current, "pom.xml").isFile()
                    && new File(current, "src/com/ignis").isDirectory()) {
                return current;
            }
        }
        return null;
    }

    private static File canonicalFile(File file) {
        if (file == null) return null;
        try {
            return file.getCanonicalFile();
        } catch (Exception exception) {
            return file.getAbsoluteFile();
        }
    }
}
