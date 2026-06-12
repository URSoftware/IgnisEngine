package com.ignis.editor;

/**
 * Common interface for AI Service Providers (e.g., Gemini, OpenAI, Claude).
 * Decouples the REST communication and JSON parsing from Swing view classes.
 */
public interface AIServiceProvider {
    /**
     * Gets the provider's display name.
     */
    String getName();

    /**
     * Calls the provider's API with the given key and prompt.
     */
    String callAPI(String apiKey, String prompt) throws Exception;
}
