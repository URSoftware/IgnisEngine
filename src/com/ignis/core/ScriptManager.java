package com.ignis.core;

import javax.tools.*;
import java.io.*;
import java.net.*;
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
    
    // Custom ClassLoader to load scripts
    private URLClassLoader scriptClassLoader;
    
    // Cache of loaded script classes
    private Map<String, Class<? extends IgnisScript>> scriptClasses = new HashMap<>();

    /**
     * Creates a new ScriptManager for a project
     * @param projectFolder The "project" folder of the project (contains the scripts/ folder)
     */
    public ScriptManager(File projectFolder) {
        this.scriptsFolder = new File(projectFolder, "scripts");
        this.compiledFolder = new File(projectFolder, "scripts/compiled");
        
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs();
        }
        if (!compiledFolder.exists()) {
            compiledFolder.mkdirs();
        }
    }

    /**
     * Compiles a .java script file
     * @param scriptFile The .java file to be compiled
     * @return true if compilation was successful
     */
    public boolean compileScript(File scriptFile) {
        try {
            // Get Java compiler
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                System.err.println("Java compiler not available. Run with JDK, not JRE.");
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
                // Reload ClassLoader to get new classes
                reloadClassLoader();
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
            System.out.println("[ScriptManager] No JDK compiler available; "
                    + "using pre-compiled scripts from compiled/.");
            return 0;
        }

        int count = 0;
        for (File script : scripts) {
            if (compileScript(script)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Reloads the ClassLoader to load updated compiled classes
     */
    private void reloadClassLoader() {
        try {
            if (scriptClassLoader != null) {
                scriptClassLoader.close();
            }
            
            URL[] urls = { compiledFolder.toURI().toURL() };
            scriptClassLoader = new URLClassLoader(urls, getClass().getClassLoader());
            
            // Clear cache
            scriptClasses.clear();
            
        } catch (Exception e) {
            System.err.println("Error reloading ClassLoader: " + e.getMessage());
        }
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
                return scriptClass;
            } else {
                System.err.println("Class " + className + " does not extend IgnisScript");
                return null;
            }
            
        } catch (ClassNotFoundException e) {
            System.err.println("Script not found: " + className);
            return null;
        }
    }

    /**
     * Creates a script instance
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
            
            IgnisScript script = scriptClass.getDeclaredConstructor().newInstance();
            script.init(gameObject, game);
            
            return script;
            
        } catch (Exception e) {
            System.err.println("Error creating script instance: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("Script already exists: " + scriptName);
            return false;
        }
        
        String template = generateScriptTemplate(scriptName);
        
        try {
            Files.write(scriptFile.toPath(), template.getBytes("UTF-8"));
            System.out.println("Script created: " + scriptFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("Error creating script: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gera o template de código para um novo script.
     */
    private String generateScriptTemplate(String className) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("import com.ignis.core.IgnisScript;\n");
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
            System.err.println("Error reading script: " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves the content of a script
     */
    public boolean saveScriptContent(String scriptName, String content) {
        File scriptFile = new File(scriptsFolder, scriptName + ".java");
        
        try {
            Files.write(scriptFile.toPath(), content.getBytes("UTF-8"));
            return true;
        } catch (IOException e) {
            System.err.println("Error saving script: " + e.getMessage());
            return false;
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
        try {
            if (scriptClassLoader != null) {
                scriptClassLoader.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
