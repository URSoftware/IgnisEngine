package com.ignis.editor;

import com.ignis.core.IgnisLogger;

/**
 * Concrete implementation of AIServiceProvider for Google's Gemini API
 * (using gemini-2.5-flash model).
 */
public class GeminiProvider implements AIServiceProvider {

    @Override
    public String getName() {
        return "Gemini";
    }

    @Override
    public String callAPI(String apiKey, String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API Key is not configured.");
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
        
        // Build JSON request body
        String jsonBody = "{\n" +
            "  \"contents\": [{\n" +
            "    \"parts\": [{\n" +
            "      \"text\": \"" + escapeJson(prompt) + "\"\n" +
            "    }]\n" +
            "  }]\n" +
            "}";
        
        try {
            // Try with Java 11+ HttpClient
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseGeminiResponse(response.body());
            } else {
                String errorMsg = "API Error: " + response.statusCode() + "\n";
                errorMsg += response.body();
                IgnisLogger.error("API Error Response: " + errorMsg);
                return errorMsg;
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Fallback for older Java or in case of HttpClient issues, try legacy URLConnection
            return callGeminiAPIViaURLConnection(url, jsonBody);
        }
    }

    private String callGeminiAPIViaURLConnection(String urlString, String jsonBody) throws Exception {
        java.net.URL url = new java.net.URL(urlString);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (java.io.OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        int status = conn.getResponseCode();
        if (status == 200) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                return parseGeminiResponse(response.toString());
            }
        } else {
            // Read error response
            StringBuilder errorResponse = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    errorResponse.append(line);
                }
            } catch (Exception e) {
                // Ignore error reading error stream
            }
            String errMsg = "API Error: " + status + "\n" + errorResponse.toString();
            IgnisLogger.error(errMsg);
            return errMsg;
        }
    }

    private String parseGeminiResponse(String jsonResponse) {
        try {
            IgnisLogger.info("[API RESPONSE] Received " + jsonResponse.length() + " bytes");
            
            // Check if it's an error response
            if (jsonResponse.contains("\"error\"")) {
                IgnisLogger.error("[API ERROR] Error response: " + jsonResponse);
                return "API Error: " + jsonResponse;
            }
            
            String text = extractJsonField(jsonResponse, "\"text\"");
            if (text != null && !text.isEmpty()) {
                IgnisLogger.info("[PARSED] Successfully extracted " + text.length() + " chars");
                return text;
            }
            
            IgnisLogger.error("[PARSE ERROR] Could not extract text field from: " + jsonResponse);
            return "Unexpected response format:\n\n" + jsonResponse;
        } catch (Exception e) {
            IgnisLogger.error("[PARSE EXCEPTION] " + e.getMessage(), e);
            return "Error parsing response: " + e.getMessage();
        }
    }

    private String extractJsonField(String json, String fieldName) {
        String marker = fieldName + ":";
        int fieldIndex = json.indexOf(marker);
        if (fieldIndex == -1) {
            return null;
        }
        
        int startIdx = json.indexOf('"', fieldIndex + marker.length());
        if (startIdx == -1) {
            return null;
        }
        
        startIdx++;
        
        StringBuilder result = new StringBuilder();
        boolean inString = true;
        for (int i = startIdx; i < json.length() && inString; i++) {
            char c = json.charAt(i);
            
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n':
                        result.append('\n');
                        i++;
                        break;
                    case 't':
                        result.append('\t');
                        i++;
                        break;
                    case 'r':
                        result.append('\r');
                        i++;
                        break;
                    case '"':
                        result.append('"');
                        i++;
                        break;
                    case '\\':
                        result.append('\\');
                        i++;
                        break;
                    default:
                        result.append(c);
                }
            } else if (c == '"') {
                inString = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
