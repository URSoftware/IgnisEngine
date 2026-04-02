package com.ignis.editor;

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
        loadSettings();
    }
    
    /**
     * Gets the current API key
     */
    public String getApiKey() {
        return apiKey;
    }
    
    /**
     * Sets and saves the API key
     */
    public void setApiKey(String key) {
        this.apiKey = key != null ? key : "";
        saveSettings();
    }
    
    /**
     * Checks if API key is configured
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
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
                    System.err.println("Error reading documentation file: " + docFile);
                    e.printStackTrace();
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
                System.err.println("Security error: Attempted to write outside project directory");
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
            System.err.println("Error writing file: " + e.getMessage());
            e.printStackTrace();
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
     * Loads AI settings from local storage
     */
    private void loadSettings() {
        try {
            File settingsFile = new File(projectRoot, SETTINGS_FILE);
            if (settingsFile.exists()) {
                String content = new String(Files.readAllBytes(settingsFile.toPath()), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                apiKey = json.optString("apiKey", "");
            }
        } catch (Exception e) {
            System.err.println("Error loading AI settings: " + e.getMessage());
        }
    }
    
    /**
     * Saves AI settings to local storage
     */
    private void saveSettings() {
        try {
            JSONObject json = new JSONObject();
            json.put("apiKey", apiKey);
            
            File settingsFile = new File(projectRoot, SETTINGS_FILE);
            Files.write(settingsFile.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error saving AI settings: " + e.getMessage());
        }
    }
}
