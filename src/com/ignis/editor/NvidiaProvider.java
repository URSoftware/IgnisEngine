package com.ignis.editor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Implementacao de {@link AIServiceProvider} para as APIs de IA da NVIDIA
 * (endpoint compativel com OpenAI em {@code integrate.api.nvidia.com}).
 *
 * <p>Usa o modelo aberto {@code meta/llama-3.1-8b-instruct} por padrao (gratuito
 * no plano de avaliacao da NVIDIA). O contrato e o mesmo do {@link GeminiProvider}:
 * recebe a chave e o prompt e devolve o texto da resposta, mantendo a integracao
 * agnostica na camada de UI.</p>
 */
public class NvidiaProvider implements AIServiceProvider {

    private static final String ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions";
    private final String model;

    public NvidiaProvider() {
        this("meta/llama-3.1-8b-instruct");
    }

    public NvidiaProvider(String model) {
        this.model = model;
    }

    @Override
    public String getName() {
        return "NVIDIA";
    }

    @Override
    public String callAPI(String apiKey, String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("NVIDIA API Key nao configurada.");
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 2048);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", messages);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseResponse(response.body());
        }
        String err = "API Error: " + response.statusCode() + "\n" + response.body();
        System.err.println("[NVIDIA] " + err);
        return err;
    }

    private String parseResponse(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("error")) return "API Error: " + obj.get("error").toString();
            JSONArray choices = obj.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                if (message != null) return message.optString("content", "").trim();
            }
            return "Formato de resposta inesperado:\n\n" + json;
        } catch (Exception e) {
            return "Erro ao interpretar resposta: " + e.getMessage();
        }
    }
}
