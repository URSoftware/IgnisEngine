package com.ignis.editor;

import com.ignis.core.IgnisLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/**
 * Manages AI API integration for the Ignis Editor
 * Handles API key storage, project context, and communication with AI services
 */
public class AIIntegration {
    private String apiKey = "";
    private File projectRoot;
    private final String SETTINGS_FILE = "ai_settings.json";
    private String activeProviderName = "Gemini";
    private final Map<String, String> providerKeys = new HashMap<>();
    private final Map<String, AIServiceProvider> providers = new HashMap<>();
    private final Map<String, Long> lastRequestTimes = new HashMap<>();
    private static final long MIN_REQUEST_INTERVAL_MS = 2000;
    
    // Documentation files to include in context
    private static final String[] DOCUMENTATION_FILES = {
        "IGNIS_FILE_SPEC.md",
        "IGNIS_SCRIPT_API.md",
        "IGNIS_SCRIPTS.md",
        "CAMERA_SYSTEM_DOCS.md",
        "PREFAB_SCRIPTS_GUIDE.md"
    };
    
    /**
     * Initializes AI integration with a project root directory
     */
    public AIIntegration(File projectRoot) {
        this.projectRoot = projectRoot;
        
        // Register default providers
        providers.put("Gemini", new GeminiProvider());
        
        // Skeletons for future providers
        providers.put("OpenAI", new AIServiceProvider() {
            @Override public String getName() { return "OpenAI"; }
            @Override public String callAPI(String apiKey, String prompt) throws Exception {
                throw new UnsupportedOperationException("OpenAI integration is under construction. Please use Gemini.");
            }
        });
        providers.put("Claude", new AIServiceProvider() {
            @Override public String getName() { return "Claude"; }
            @Override public String callAPI(String apiKey, String prompt) throws Exception {
                throw new UnsupportedOperationException("Claude integration is under construction. Please use Gemini.");
            }
        });
        
        loadSettings();
    }
    
    public String getActiveProviderName() {
        return activeProviderName;
    }

    public void setActiveProviderName(String name) {
        if (providers.containsKey(name)) {
            this.activeProviderName = name;
            saveSettings();
        }
    }

    public List<String> getAvailableProviders() {
        List<String> list = new ArrayList<>(providers.keySet());
        Collections.sort(list);
        return list;
    }

    /**
     * Gets the active API key
     */
    public String getApiKey() {
        return providerKeys.getOrDefault(activeProviderName, "");
    }
    
    /**
     * Sets and saves the active API key
     */
    public void setApiKey(String key) {
        providerKeys.put(activeProviderName, key != null ? key : "");
        if ("Gemini".equals(activeProviderName)) {
            this.apiKey = key != null ? key : "";
        }
        saveSettings();
    }

    public String getApiKeyFor(String provider) {
        return providerKeys.getOrDefault(provider, "");
    }

    public void setApiKeyFor(String provider, String key) {
        providerKeys.put(provider, key != null ? key : "");
        if ("Gemini".equals(provider)) {
            this.apiKey = key != null ? key : "";
        }
        saveSettings();
    }
    
    /**
     * Checks if API key is configured
     */
    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.trim().isEmpty();
    }

    private synchronized boolean checkRateLimit(String provider) {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastRequestTimes.getOrDefault(provider, 0L);
        long timeSinceLastRequest = currentTime - lastTime;
        
        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            return false;
        }
        
        lastRequestTimes.put(provider, currentTime);
        return true;
    }

    /**
     * Calls the active AI provider with the configured key and prompt
     */
    public String callActiveAI(String prompt) throws Exception {
        AIServiceProvider provider = providers.get(activeProviderName);
        if (provider == null) {
            throw new IllegalStateException("Active AI provider '" + activeProviderName + "' is not registered.");
        }
        
        if (!checkRateLimit(activeProviderName)) {
            throw new Exception("Rate limit reached. Please wait a moment before sending another prompt.");
        }
        
        String key = getApiKey();
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException(activeProviderName + " API key is not configured in Settings.");
        }
        
        return provider.callAPI(key, prompt);
    }
    
    /**
     * Gets the documentation context as a string
     * Reads all documentation files from the project root
     */
    public String getDocumentationContext() {
        StringBuilder context = new StringBuilder();
        context.append("# Ignis Engine Documentation\n\n");
        
        for (String docFile : DOCUMENTATION_FILES) {
            File file = new File(projectRoot.getParent(), docFile);
            if (file.exists() && file.isFile()) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    context.append("## ").append(docFile).append("\n\n");
                    context.append(content).append("\n\n---\n\n");
                } catch (IOException e) {
                    IgnisLogger.error("Error reading documentation file: " + docFile, e);
                }
            }
        }
        
        return context.toString();
    }
    
    /**
     * Gets the project file structure as a string
     * Lists all files in the project for context
     */
    public String getProjectStructure() {
        StringBuilder structure = new StringBuilder();
        structure.append("# Project Structure\n\n");
        appendDirectoryStructure(projectRoot, structure, 0);
        return structure.toString();
    }
    
    /**
     * Recursively appends directory structure to a StringBuilder
     */
    private void appendDirectoryStructure(File directory, StringBuilder sb, int depth) {
        if (depth > 5) return; // Limit recursion depth
        
        String indent = "  ".repeat(depth);
        File[] files = directory.listFiles();
        
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                // Skip certain directories
                if (file.getName().startsWith(".") || file.getName().equals("target")) {
                    continue;
                }
                
                if (file.isDirectory()) {
                    sb.append(indent).append("📁 ").append(file.getName()).append("/\n");
                    appendDirectoryStructure(file, sb, depth + 1);
                } else {
                    sb.append(indent).append("📄 ").append(file.getName()).append("\n");
                }
            }
        }
    }
    
    /**
     * Gets the contents of a specific file in the project
     */
    public String getFileContent(String relativePath) {
        File file = new File(projectRoot, relativePath);
        
        // Security check: ensure the file is within the project root
        try {
            if (!file.getCanonicalPath().startsWith(projectRoot.getCanonicalPath())) {
                return "Error: File is outside project directory";
            }
        } catch (IOException e) {
            return "Error: Invalid file path";
        }
        
        if (!file.exists()) {
            return "Error: File not found";
        }
        
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
    
    /**
     * Writes content to a file in the project
     * Used by AGENT mode to modify project files
     */
    public boolean writeFileContent(String relativePath, String content) {
        File file = new File(projectRoot, relativePath);
        
        // Security check
        try {
            if (!file.getCanonicalPath().startsWith(projectRoot.getCanonicalPath())) {
                IgnisLogger.error("Security error: Attempted to write outside project directory");
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        
        // Create parent directories if needed
        file.getParentFile().mkdirs();
        
        try {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            IgnisLogger.error("Error writing file: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Lists all files in a directory within the project
     */
    public List<String> listDirectory(String relativePath) {
        File dir = new File(projectRoot, relativePath);
        List<String> files = new ArrayList<>();
        
        try {
            if (!dir.getCanonicalPath().startsWith(projectRoot.getCanonicalPath())) {
                return files;
            }
        } catch (IOException e) {
            return files;
        }
        
        if (!dir.isDirectory()) {
            return files;
        }
        
        File[] dirFiles = dir.listFiles();
        if (dirFiles != null) {
            for (File file : dirFiles) {
                files.add(file.getName());
            }
            Collections.sort(files);
        }
        
        return files;
    }
    
    /**
     * Searches for files by pattern in the project
     */
    public List<String> searchFiles(String pattern) {
        List<String> results = new ArrayList<>();
        String lowerPattern = pattern.toLowerCase();
        searchFilesRecursive(projectRoot, "", lowerPattern, results, 0);
        return results;
    }
    
    /**
     * Recursively searches for files matching a pattern
     */
    private void searchFilesRecursive(File dir, String relativePath, String pattern, List<String> results, int depth) {
        if (depth > 5 || results.size() > 100) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            
            String filePath = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
            
            if (file.isDirectory()) {
                searchFilesRecursive(file, filePath, pattern, results, depth + 1);
            } else if (file.getName().toLowerCase().contains(pattern)) {
                results.add(filePath);
            }
        }
    }
    
    /**
     * Loads AI settings from secure user home directory with legacy fallback
     */
    private void loadSettings() {
        try {
            File userHomeDir = new File(System.getProperty("user.home"), ".ignis");
            File secureSettingsFile = new File(userHomeDir, SETTINGS_FILE);
            
            if (secureSettingsFile.exists()) {
                String content = new String(Files.readAllBytes(secureSettingsFile.toPath()), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                apiKey = json.optString("apiKey", "");
                activeProviderName = json.optString("activeProvider", "Gemini");
                if (json.has("apiKeys")) {
                    JSONObject keysObj = json.getJSONObject("apiKeys");
                    for (String key : keysObj.keySet()) {
                        providerKeys.put(key, keysObj.getString(key));
                    }
                }
                // Backwards compatibility if apiKeys was empty but apiKey existed
                if (!providerKeys.containsKey("Gemini") && !apiKey.isEmpty()) {
                    providerKeys.put("Gemini", apiKey);
                }
            } else {
                // Legacy fallback: read from project root and migrate
                File projectSettingsFile = new File(projectRoot, SETTINGS_FILE);
                if (projectSettingsFile.exists()) {
                    String content = new String(Files.readAllBytes(projectSettingsFile.toPath()), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(content);
                    apiKey = json.optString("apiKey", "");
                    activeProviderName = json.optString("activeProvider", "Gemini");
                    if (!apiKey.isEmpty()) {
                        providerKeys.put("Gemini", apiKey);
                    }
                    saveSettings(); // Save to home & delete legacy
                }
            }
        } catch (Exception e) {
            IgnisLogger.error("Error loading AI settings: " + e.getMessage());
        }
    }
    
    /**
     * Saves AI settings securely to user home directory and deletes legacy project root files
     */
    private void saveSettings() {
        try {
            File userHomeDir = new File(System.getProperty("user.home"), ".ignis");
            if (!userHomeDir.exists()) {
                userHomeDir.mkdirs();
            }
            File secureSettingsFile = new File(userHomeDir, SETTINGS_FILE);
            
            JSONObject json = new JSONObject();
            json.put("apiKey", apiKey);
            json.put("activeProvider", activeProviderName);
            
            JSONObject keysObj = new JSONObject();
            for (Map.Entry<String, String> entry : providerKeys.entrySet()) {
                keysObj.put(entry.getKey(), entry.getValue());
            }
            json.put("apiKeys", keysObj);
            
            Files.write(secureSettingsFile.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
            
            // Delete project root legacy file if it exists to avoid exposing API key
            File projectSettingsFile = new File(projectRoot, SETTINGS_FILE);
            if (projectSettingsFile.exists()) {
                projectSettingsFile.delete();
            }
        } catch (Exception e) {
            IgnisLogger.error("Error saving AI settings: " + e.getMessage());
        }
    }
}
