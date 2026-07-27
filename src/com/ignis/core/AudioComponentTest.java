package com.ignis.core;

import org.json.JSONObject;

/**
 * Testes unitários e de integração para AudioSourceComponent e AudioListenerComponent.
 */
public class AudioComponentTest {

    public static void main(String[] args) {
        IgnisLogger.info("=== Testando AudioSourceComponent e AudioListenerComponent ===");

        try {
            // 1. Instanciação e anexação aos GameObjects
            GameObject listenerObject = new GameObject("PlayerListener", null, 100, 100, 32, 32);
            AudioListenerComponent listener = new AudioListenerComponent();
            listenerObject.addComponent(listener);

            assert AudioListenerComponent.getActiveListener() == listener : "AudioListenerComponent ativo deve ser o anexado";

            GameObject sourceObject = new GameObject("AudioEmitter", null, 300, 100, 32, 32); // 200px a direita
            AudioSourceComponent audioSource = new AudioSourceComponent();
            audioSource.setAudioClip("assets/sounds/test.wav");
            audioSource.setVolume(0.8f);
            audioSource.setSpatialBlend(1.0f); // 3D Espacializado
            audioSource.setMinDistance(50.0f);
            audioSource.setMaxDistance(500.0f);
            sourceObject.addComponent(audioSource);

            // 2. Teste do Cálculo Espacial (Atenuação 3D e Panning Estéreo)
            float[] spatial = audioSource.calculateSpatialParameters();
            float attenuation = spatial[0];
            float pan = spatial[1];

            IgnisLogger.info("Atenuação calculada (dist=200, min=50, max=500): " + attenuation);
            IgnisLogger.info("Pan calculado (dx=+200, max=500): " + pan);

            // Distância é 200. Atenuação deve estar em ~0.66 (entre 1.0 a 50px e 0.0 a 500px).
            assert attenuation > 0.5f && attenuation < 0.8f : "Atenuação espacial incorreta";
            // Emissor está 200px à direita do ouvinte. Pan deve ser positivo (+0.4)
            assert pan > 0.3f && pan < 0.5f : "Pan estéreo incorreto para fonte à direita";

            // 3. Teste de Serialização @Serialize (JSON)
            JSONObject savedProps = audioSource.saveProperties();
            assert "assets/sounds/test.wav".equals(savedProps.optString("audioClip")) : "Falha na serialização de audioClip";
            assert Float.compare((float) savedProps.optDouble("volume"), 0.8f) == 0 : "Falha na serialização de volume";
            assert Float.compare((float) savedProps.optDouble("spatialBlend"), 1.0f) == 0 : "Falha na serialização de spatialBlend";

            // Teste de Desserialização
            AudioSourceComponent loadedSource = new AudioSourceComponent();
            loadedSource.loadProperties(savedProps, null);
            assert "assets/sounds/test.wav".equals(loadedSource.getAudioClip()) : "Falha na desserialização de audioClip";
            assert Float.compare(loadedSource.getVolume(), 0.8f) == 0 : "Falha na desserialização de volume";
            assert Float.compare(loadedSource.getSpatialBlend(), 1.0f) == 0 : "Falha na desserialização de spatialBlend";

            // 4. Teste de Desanexação (onDetach)
            listenerObject.removeComponent(listener);
            assert AudioListenerComponent.getActiveListener() == null : "ActiveListener deveria ser anulado em onDetach";

            sourceObject.removeComponent(audioSource);
            assert !audioSource.isPlaying() : "AudioSource não deve estar tocando após onDetach";

            IgnisLogger.info("=== Todos os testes do Sistema de Som passaram com sucesso! ===");

        } catch (Exception e) {
            IgnisLogger.error("Falha no teste do Sistema de Som:", e);
            System.exit(1);
        }
    }
}
