package com.ignis.core;

/**
 * Componente nativo que representa o ouvinte de áudio da cena (os ouvidos do mundo).
 * Utilizado pelo {@link AudioSourceComponent} para calcular o Spatial Blend (atenuação 3D por distância e panning estéreo).
 */
public class AudioListenerComponent extends Component {

    private static AudioListenerComponent activeListener;

    public AudioListenerComponent() {
    }

    @Override
    public void awake() {
        if (activeListener == null) {
            activeListener = this;
        }
    }

    @Override
    public void start() {
        if (activeListener == null) {
            activeListener = this;
        }
    }

    @Override
    public void onDetach() {
        if (activeListener == this) {
            activeListener = null;
        }
    }

    /**
     * Retorna o {@link AudioListenerComponent} ativo primário na cena.
     */
    public static AudioListenerComponent getActiveListener() {
        return activeListener;
    }

    /**
     * Define explicitamente o listener ativo.
     */
    public static void setActiveListener(AudioListenerComponent listener) {
        activeListener = listener;
    }
}
