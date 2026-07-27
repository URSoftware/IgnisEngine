package com.ignis.core;

import javax.tools.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Script manager for the Ignis Engine.
 * Responsible for loading, compiling and managing user scripts.
 * 
 * Scripts are .java files that extend IgnisScript and are stored
 * in the scripts/ folder of the project.
 */
public class ScriptManager {

    // Folder where compiled scripts will be stored
    private File scriptsFolder;
    private File compiledFolder;

    // Pasta opcional "libs/" na raiz do projeto: qualquer .jar solto ali entra no
    // classpath de compilacao dos scripts e no ClassLoader de runtime. Isso deixa um
    // projeto trazer sua propria camada de dominio/bibliotecas privadas sem que a
    // engine precise depender dela - nenhum outro projeto e afetado, porque cada
    // ScriptManager so olha para a libs/ do seu proprio projectFolder.
    private File libsFolder;

    // Custom ClassLoader to load scripts
    private URLClassLoader scriptClassLoader;
    private final List<URLClassLoader> retiredClassLoaders = new ArrayList<>();
    private URLClassLoader projectLibraryClassLoader;
    private List<URL> projectLibraryUrls = Collections.emptyList();
    private List<String> projectLibrarySignatures = Collections.emptyList();
    private final List<URLClassLoader> retiredProjectLibraryClassLoaders = new ArrayList<>();

    // Nao existe teto durante uma troca de geracao, e e de proposito. Ja tentei
    // limitar isto de duas formas e as duas foram piores (16/07/2026):
    //
    //   1. Fechar os mais antigos -> derrubou o auto-save do editor com
    //      NoClassDefFoundError. Um loader aposentado ainda e o loader de definicao
    //      das instancias que estao na cena, e resolucao de tipo e preguicosa: o tipo
    //      de um campo so carrega quando o reflection olha para ele. A premissa de que
    //      "depois de N gerações a cena ja foi recriada" e falsa — basta recompilar
    //      sem recarregar a cena.
    //   2. Soltar a referencia sem fechar -> o close() perde o rastro deles e o handle
    //      do .jar fica preso ate o GC (no Windows isso impede ate apagar o arquivo).
    //
    // A lista fica completa enquanto as instancias antigas ainda vivem. O editor
    // chama releaseRetiredClassLoaders() somente depois de substituir todos os
    // scripts da cena. Ha um loader por LOTE (ver compileAllScripts), nao por script.

    // Cache of loaded script classes
    private Map<String, Class<? extends IgnisScript>> scriptClasses = new HashMap<>();

    /**
     * Creates a new ScriptManager for a project
     * @param projectFolder The "project" folder of the project (contains the scripts/ folder)
     */
    public ScriptManager(File projectFolder) {
        this.scriptsFolder = new File(projectFolder, "scripts");
        this.compiledFolder = new File(projectFolder, "scripts/compiled");
        this.libsFolder = new File(projectFolder, "libs");

        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs();
        }
        if (!compiledFolder.exists()) {
            compiledFolder.mkdirs();
        }
        // Configura automaticamente ignis-engine-api.jar e arquivos IDE (.vscode, .classpath, pom.xml)
        IgnisProjectIO.setupIdeConfig(projectFolder);
    }

    /**
     * Jars soltos em libs/ na raiz do projeto (nao a engine). Vazio se a pasta nao
     * existir ou nao tiver nenhum .jar - um projeto sem bibliotecas privadas nao
     * precisa criar essa pasta.
     */
    private List<File> discoverLibJars() {
        if (!libsFolder.exists()) {
            return Collections.emptyList();
        }
        File[] jars = libsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            // Este jar existe para IDEs externas. Em runtime as classes da engine ja
            // estao no parent classloader; inclui-lo novamente so duplica tipos e
            // mantem o arquivo bloqueado no Windows durante restart/atualizacao.
            return lower.endsWith(".jar") && !lower.equals("ignis-engine-api.jar");
        });
        if (jars == null || jars.length == 0) {
            return Collections.emptyList();
        }
        List<File> result = Arrays.asList(jars);
        Collections.sort(result, Comparator.comparing(File::getName));
        return result;
    }

    /**
     * Pasta "libs/" deste projeto (pode nao existir se o projeto nunca a usou).
     */
    public File getLibsFolder() {
        return libsFolder;
    }

    /**
     * Compiles a .java script file
     * @param scriptFile The .java file to be compiled
     * @return true if compilation was successful
     */
    public boolean compileScript(File scriptFile) {
        return compileScript(scriptFile, true);
    }

    private boolean compileScript(File scriptFile, boolean reloadAfter) {
        try {
            // Get Java compiler
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                IgnisLogger.error("Java compiler not available. Run with JDK, not JRE.");
                return false;
            }

            // Configure diagnostics
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            // Prepare source files
            Iterable<? extends JavaFileObject> compilationUnits = 
                fileManager.getJavaFileObjectsFromFiles(Arrays.asList(scriptFile));

            // Configure compilation options
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(compiledFolder.getAbsolutePath());
            
            // Add classpath (include engine classes)
            String classpath = System.getProperty("java.class.path");
            File targetClasses = new File("target/classes");
            if (targetClasses.exists()) {
                classpath = targetClasses.getAbsolutePath() + File.pathSeparator + classpath;
            }
            // Bibliotecas privadas do projeto (project/libs/*.jar), se houver - assim um
            // script pode referenciar uma classe de outro arquivo do proprio projeto
            // desde que ela esteja empacotada num jar aqui, sem a engine depender dela.
            for (File jar : discoverLibJars()) {
                classpath = classpath + File.pathSeparator + jar.getAbsolutePath();
            }
            options.add("-classpath");
            options.add(classpath);

            // Compile
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits);
            
            boolean success = task.call();

            // Show errors/warnings
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                String msg = String.format("Line %d: %s",
                    diagnostic.getLineNumber(),
                    diagnostic.getMessage(null));
                
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    com.ignis.core.IgnisLogger.error(scriptFile.getName() + " -> " + msg);
                } else {
                    com.ignis.core.IgnisLogger.warn(scriptFile.getName() + " -> " + msg);
                }
            }

            fileManager.close();
            
            if (success) {
                com.ignis.core.IgnisLogger.info("Script compilado com sucesso: " + scriptFile.getName());
                // Reload ClassLoader to get new classes (compileAllScripts adia a
                // recarga para o fim do lote — uma recarga por lote, nao por script).
                if (reloadAfter) {
                    reloadClassLoader();
                }
            }
            
            return success;
            
        } catch (Exception e) {
            com.ignis.core.IgnisLogger.error("Erro ao compilar script " + scriptFile.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Compiles all scripts in the scripts/ folder
     * @return Number of scripts compiled successfully
     */
    public int compileAllScripts() {
        File[] scripts = scriptsFolder.listFiles((dir, name) -> name.endsWith(".java"));
        if (scripts == null || scripts.length == 0) {
            return 0;
        }

        // On a JRE (e.g. a distributed build) there is no compiler. Rather than
        // failing once per script, fall back to the pre-compiled classes that
        // ship in scripts/compiled/ — loaded lazily by loadScriptClass().
        if (ToolProvider.getSystemJavaCompiler() == null) {
            IgnisLogger.info("[ScriptManager] No JDK compiler available; "
                    + "using pre-compiled scripts from compiled/.");
            return 0;
        }

        int count = 0;
        for (File script : scripts) {
            if (compileScript(script, false)) {
                count++;
            }
        }
        if (count > 0) {
            // Uma unica recarga para o lote inteiro: recarregar por script aposentava
            // um classloader por arquivo a cada Play, acumulando memoria a toa.
            reloadClassLoader();
        }
        return count;
    }

    /**
     * Reloads the ClassLoader to load updated compiled classes
     */
    private void reloadClassLoader() {
        try {
            if (scriptClassLoader != null) {
                // Existing scene instances still belong to this loader. Closing it here
                // breaks lazy dependency resolution when Play recompiles the scripts.
                retiredClassLoaders.add(scriptClassLoader);
            }

            List<URL> urls = new ArrayList<>();
            urls.add(compiledFolder.toURI().toURL());

            // Scripts are reloaded often, while project libraries are stable for the
            // editor session. Keeping libs in the short-lived script loader made a
            // closed retired generation unable to resolve a library type lazily.
            // A dedicated parent keeps project types available until close().
            ClassLoader runtimeParent = ensureProjectLibraryClassLoader();
            scriptClassLoader = new URLClassLoader(urls.toArray(new URL[0]), runtimeParent);
            IgnisLogger.info("[ScriptManager] Script runtime classpath: " + urls);
            IgnisLogger.info("[ScriptManager] Project library classpath: " + projectLibraryUrls);

            // Clear cache
            scriptClasses.clear();

        } catch (Exception e) {
            IgnisLogger.error("Error reloading ClassLoader: " + e.getMessage());
        }
    }

    private ClassLoader ensureProjectLibraryClassLoader() throws MalformedURLException {
        List<File> discoveredJars = discoverLibJars();
        List<URL> discoveredUrls = new ArrayList<>();
        List<String> discoveredSignatures = new ArrayList<>();
        for (File jar : discoveredJars) {
            discoveredUrls.add(jar.toURI().toURL());
            discoveredSignatures.add(librarySignature(jar));
        }

        if (projectLibraryClassLoader != null
                && projectLibraryUrls.equals(discoveredUrls)
                && projectLibrarySignatures.equals(discoveredSignatures)) {
            return projectLibraryClassLoader;
        }

        if (projectLibraryClassLoader != null) {
            // A live script generation may still have this loader as its parent.
            // Retain it until ScriptManager.close(), just like an open project jar.
            retiredProjectLibraryClassLoaders.add(projectLibraryClassLoader);
        }
        projectLibraryUrls = Collections.unmodifiableList(new ArrayList<>(discoveredUrls));
        projectLibrarySignatures = Collections.unmodifiableList(new ArrayList<>(discoveredSignatures));
        projectLibraryClassLoader = new URLClassLoader(
                discoveredUrls.toArray(new URL[0]), getClass().getClassLoader());
        return projectLibraryClassLoader;
    }

    /**
     * URL equality is insufficient for project libraries: a build can replace a
     * JAR at the same path while the editor remains open. Include file metadata
     * so the next script generation gets a fresh parent loader without closing
     * the loader that still owns the live scene instances.
     */
    private String librarySignature(File jar) {
        return jar.getAbsolutePath() + "|" + jar.length() + "|" + jar.lastModified();
    }

    /**
     * Loads a script class by name
     * @param className Simple class name (e.g., "PlayerMovement")
     * @return The script class, or null if not found
     */
    @SuppressWarnings("unchecked")
    public Class<? extends IgnisScript> loadScriptClass(String className) {
        // Check cache
        if (scriptClasses.containsKey(className)) {
            return scriptClasses.get(className);
        }
        
        try {
            if (scriptClassLoader == null) {
                reloadClassLoader();
            }
            
            Class<?> clazz = scriptClassLoader.loadClass(className);
            
            if (IgnisScript.class.isAssignableFrom(clazz)) {
                Class<? extends IgnisScript> scriptClass = (Class<? extends IgnisScript>) clazz;
                scriptClasses.put(className, scriptClass);
                // Diagnostico de qual loader serviu a classe — uma vez por classe por
                // recarga (por instancia inundava o console em jogos com spawners).
                IgnisLogger.info("[ScriptManager] Classe " + className + " carregada por "
                        + scriptClass.getClassLoader());
                return scriptClass;
            } else {
                IgnisLogger.error("Class " + className + " does not extend IgnisScript");
                return null;
            }
            
        } catch (ClassNotFoundException e) {
            IgnisLogger.error("Script not found: " + className);
            return null;
        }
    }

    /**
     * Creates a detached script instance. Initialization belongs to
     * {@link GameObject#addComponent(Component)}, the single attachment boundary.
     * @param className Script class name
     * @param gameObject GameObject to which the script will be attached
     * @param game Reference to the Game
     * @return Script instance, or null if there's an error
     */
    public IgnisScript createScriptInstance(String className, GameObject gameObject, Game game) {
        try {
            Class<? extends IgnisScript> scriptClass = loadScriptClass(className);
            if (scriptClass == null) {
                return null;
            }
            
            return scriptClass.getDeclaredConstructor().newInstance();
            
        } catch (Exception e) {
            IgnisLogger.error("Error creating script instance: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Lists all available scripts (.java files)
     * @return List of script names
     */
    public List<String> listAvailableScripts() {
        List<String> scripts = new ArrayList<>();
        File[] files = scriptsFolder.listFiles((dir, name) -> name.endsWith(".java"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(".java", "");
                scripts.add(name);
            }
        }
        
        Collections.sort(scripts);
        return scripts;
    }

    /**
     * Returns whether the top-level class for a script is already available in
     * the compiled output. MCP attachments may legitimately happen before the
     * next compilation pass; checking first avoids logging a misleading
     * ClassNotFoundException while the serialized attachment is still valid.
     */
    public boolean hasCompiledScript(String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            return false;
        }
        return new File(compiledFolder, scriptName + ".class").isFile();
    }

    /**
     * Lists all available compiled scripts
     * @return List of script class names
     */
    public List<String> listCompiledScripts() {
        List<String> scripts = new ArrayList<>();
        File[] files = compiledFolder.listFiles((dir, name) -> name.endsWith(".class"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(".class", "");
                // Ignore inner classes (contain $)
                if (!name.contains("$")) {
                    scripts.add(name);
                }
            }
        }
        
        Collections.sort(scripts);
        return scripts;
    }

    /**
     * Creates a new script file with basic template
     * @param scriptName Script name (without extension)
     * @return true if file was created successfully
     */
    public boolean createNewScript(String scriptName) {
        // Sanitize name
        scriptName = sanitizeClassName(scriptName);
        
        File scriptFile = new File(scriptsFolder, scriptName + ".java");
        
        if (scriptFile.exists()) {
            IgnisLogger.error("Script already exists: " + scriptName);
            return false;
        }
        
        String template = generateScriptTemplate(scriptName);
        
        try {
            writeAtomically(scriptFile.toPath(), template);
            IgnisLogger.info("Script created: " + scriptFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            IgnisLogger.error("Error creating script: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gera o template de código para um novo script.
     */
    private String generateScriptTemplate(String className) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("import com.ignis.core.IgnisScript;\n");
        sb.append("import com.ignis.core.Serialize;\n");
        sb.append("import com.ignis.core.GameObject;\n");
        sb.append("\n");
        sb.append("public class ").append(className).append(" extends IgnisScript {\n");
        sb.append("\n");
        sb.append("    @Override\n");
        sb.append("    public void start() { // Called once when initializing world simulation\n");
        sb.append("        \n");
        sb.append("    }\n");
        sb.append("\n");
        sb.append("    @Override\n");
        sb.append("    public void tick() { // Called once every frame\n");
        sb.append("        \n");
        sb.append("    }\n");
        sb.append("}\n");
        
        return sb.toString();
    }

    /**
     * Sanitizes a name to be used as a Java class name
     */
    private String sanitizeClassName(String name) {
        // Remove extension if present
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }
        
        // Remove invalid characters
        name = name.replaceAll("[^a-zA-Z0-9_]", "");
        
        // Ensure it starts with uppercase letter
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        
        // If empty, use default name
        if (name.isEmpty()) {
            name = "NewScript";
        }
        
        return name;
    }

    /**
     * Returns the content of a script file
     */
    public String readScriptContent(String scriptName) {
        File scriptFile = new File(scriptsFolder, scriptName + ".java");
        if (!scriptFile.exists()) {
            return null;
        }
        
        try {
            return new String(Files.readAllBytes(scriptFile.toPath()), "UTF-8");
        } catch (IOException e) {
            IgnisLogger.error("Error reading script: " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves the content of a script
     */
    public boolean saveScriptContent(String scriptName, String content) {
        File scriptFile = new File(scriptsFolder, scriptName + ".java");
        
        try {
            writeAtomically(scriptFile.toPath(), content);
            return true;
        } catch (IOException e) {
            IgnisLogger.error("Error saving script: " + e.getMessage());
            return false;
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(),
                target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Deletes a script
     */
    public boolean deleteScript(String scriptName) {
        File scriptFile = new File(scriptsFolder, scriptName + ".java");
        File classFile = new File(compiledFolder, scriptName + ".class");
        
        boolean deleted = false;
        
        if (scriptFile.exists()) {
            deleted = scriptFile.delete();
        }
        
        if (classFile.exists()) {
            classFile.delete();
        }
        
        // Remove from cache
        scriptClasses.remove(scriptName);
        
        return deleted;
    }

    /**
     * Returns the scripts folder
     */
    public File getScriptsFolder() {
        return scriptsFolder;
    }

    /**
     * Closes ScriptManager resources
     */
    public void close() {
        closeClassLoader(scriptClassLoader);
        releaseRetiredClassLoaders();
        closeClassLoader(projectLibraryClassLoader);
        for (URLClassLoader classLoader : retiredProjectLibraryClassLoaders) {
            closeClassLoader(classLoader);
        }
        retiredProjectLibraryClassLoaders.clear();
        scriptClassLoader = null;
        projectLibraryClassLoader = null;
        projectLibraryUrls = Collections.emptyList();
        projectLibrarySignatures = Collections.emptyList();
    }

    /**
     * Releases loaders whose script instances have already been replaced by the
     * caller. Calling this before rebuilding the scene can break lazy type
     * resolution, so the lifecycle boundary intentionally stays explicit.
     *
     * @return number of retired loaders released
     */
    public synchronized int releaseRetiredClassLoaders() {
        int released = retiredClassLoaders.size();
        for (URLClassLoader classLoader : retiredClassLoaders) {
            closeClassLoader(classLoader);
        }
        retiredClassLoaders.clear();
        return released;
    }

    int retainedClassLoaderCount() {
        return retiredClassLoaders.size();
    }

    private void closeClassLoader(URLClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException ignored) {
            // The operating system will release these read-only handles on process exit.
        }
    }
}
