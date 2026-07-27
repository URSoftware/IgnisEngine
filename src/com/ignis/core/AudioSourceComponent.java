package com.ignis.core;

/**
 * Componente nativo do IgnisEngine para emissão de áudio 2D e 3D espacializado.
 * Suporta arquivo de áudio, controle de volume, pitch, loop, atenuação por distância,
 * panning estéreo e auto-execução (playOnAwake).
 */
public class AudioSourceComponent extends Component {

    @Serialize
    private String audioClip = "";

    @Serialize
    private float volume = 1.0f;

    @Serialize
    private float pitch = 1.0f;

    @Serialize
    private boolean loop = false;

    @Serialize
    private float spatialBlend = 0.0f; // 0.0 = 2D global, 1.0 = 3D espacializado

    @Serialize
    private boolean playOnAwake = false;

    @Serialize
    private float minDistance = 50.0f;

    @Serialize
    private float maxDistance = 500.0f;

    // Controlador de reprodução ativa em runtime
    private transient IgnisSoundEngine.AudioHandle currentHandle;

    public AudioSourceComponent() {
    }

    public AudioSourceComponent(String audioClip) {
        this.audioClip = audioClip;
    }

    @Override
    public void awake() {
        if (playOnAwake && audioClip != null && !audioClip.trim().isEmpty()) {
            play();
        }
    }

    @Override
    public void start() {
        if (playOnAwake && !isPlaying() && audioClip != null && !audioClip.trim().isEmpty()) {
            play();
        }
    }

    @Override
    public void update(float deltaTime) {
        if (currentHandle != null && currentHandle.isPlaying() && spatialBlend > 0.0f) {
            updateSpatialAudio(currentHandle);
        }
    }

    @Override
    public void onDetach() {
        stop();
    }

    /**
     * Inicia a reprodução do clipe de áudio configurado neste componente.
     */
    public void play() {
        if (audioClip == null || audioClip.trim().isEmpty()) {
            return;
        }

        if (currentHandle != null && currentHandle.isPlaying()) {
            currentHandle.stop();
        }

        float effectiveVolume = volume;
        float pan = 0.0f;

        if (spatialBlend > 0.0f) {
            float[] spatial = calculateSpatialParameters();
            effectiveVolume = volume * spatial[0];
            pan = spatial[1];
        }

        currentHandle = IgnisSoundEngine.getInstance().playHandle(
                audioClip,
                effectiveVolume,
                pan,
                pitch,
                loop,
                () -> currentHandle = null
        );
    }

    /**
     * Reproduz um efeito sonoro pontual sem interromper o clipe principal em loop.
     * @param clipPath Caminho do arquivo de som.
     */
    public void playOneShot(String clipPath) {
        playOneShot(clipPath, 1.0f);
    }

    /**
     * Reproduz um efeito sonoro pontual com multiplicador de volume sem interromper o clipe principal.
     * @param clipPath Caminho do arquivo de som.
     * @param volumeScale Multiplicador de volume (0.0 a 1.0).
     */
    public void playOneShot(String clipPath, float volumeScale) {
        if (clipPath == null || clipPath.trim().isEmpty()) {
            return;
        }

        float scale = Math.max(0.0f, Math.min(1.0f, volumeScale));
        float effectiveVolume = volume * scale;
        float pan = 0.0f;

        if (spatialBlend > 0.0f) {
            float[] spatial = calculateSpatialParameters();
            effectiveVolume = effectiveVolume * spatial[0];
            pan = spatial[1];
        }

        IgnisSoundEngine.getInstance().playHandle(
                clipPath,
                effectiveVolume,
                pan,
                pitch,
                false,
                null
        );
    }

    /**
     * Para a reprodução do áudio ativo.
     */
    public void stop() {
        if (currentHandle != null) {
            currentHandle.stop();
            currentHandle = null;
        }
    }

    /**
     * Pausa a reprodução atual.
     */
    public void pause() {
        if (currentHandle != null) {
            currentHandle.pause();
        }
    }

    /**
     * Retoma a reprodução pausada.
     */
    public void resume() {
        if (currentHandle != null) {
            currentHandle.resume();
        }
    }

    /**
     * Retorna se o áudio está sendo reproduzido no momento.
     */
    public boolean isPlaying() {
        return currentHandle != null && currentHandle.isPlaying();
    }

    /**
     * Atualiza volume e panning dinamicamente baseado no AudioListenerComponent ativo na cena.
     */
    private void updateSpatialAudio(IgnisSoundEngine.AudioHandle handle) {
        float[] spatial = calculateSpatialParameters();
        float effectiveVolume = volume * spatial[0];
        float pan = spatial[1];

        handle.setVolume(effectiveVolume);
        handle.setPan(pan);
        handle.setPitch(pitch);
    }

    /**
     * Calcula o fator de atenuação por distância e o balanço estéreo (panning).
     * @return Array onde index 0 é o fator de atenuação (0.0 a 1.0) e index 1 é o pan (-1.0 a 1.0).
     */
    public float[] calculateSpatialParameters() {
        float sourceX = 0.0f;
        float sourceY = 0.0f;

        if (gameObject != null) {
            sourceX = (float) gameObject.getX();
            sourceY = (float) gameObject.getY();
        }

        float listenerX = sourceX;
        float listenerY = sourceY;

        AudioListenerComponent activeListener = AudioListenerComponent.getActiveListener();

        if (activeListener != null && activeListener.gameObject != null) {
            listenerX = (float) activeListener.gameObject.getX();
            listenerY = (float) activeListener.gameObject.getY();
        } else if (gameObject != null && gameObject.getGame() != null && gameObject.getGame().getMainCamera() != null) {
            listenerX = (float) gameObject.getGame().getMainCamera().getX();
            listenerY = (float) gameObject.getGame().getMainCamera().getY();
        }

        double dx = sourceX - listenerX;
        double dy = sourceY - listenerY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        float attenuation = 1.0f;
        if (dist <= minDistance) {
            attenuation = 1.0f;
        } else if (dist >= maxDistance) {
            attenuation = 0.0f;
        } else {
            float distRange = Math.max(1.0f, maxDistance - minDistance);
            attenuation = 1.0f - (float) ((dist - minDistance) / distRange);
        }

        float spatialAttenuationFactor = 1.0f - spatialBlend + (attenuation * spatialBlend);

        float maxSpan = Math.max(1.0f, maxDistance);
        float rawPan = (float) (dx / maxSpan);
        float pan = Math.max(-1.0f, Math.min(1.0f, rawPan)) * spatialBlend;

        return new float[]{ spatialAttenuationFactor, pan };
    }

    // Getters e Setters

    public String getAudioClip() {
        return audioClip;
    }

    public void setAudioClip(String audioClip) {
        this.audioClip = audioClip;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        if (currentHandle != null) {
            currentHandle.setVolume(this.volume);
        }
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(0.1f, Math.min(3.0f, pitch));
        if (currentHandle != null) {
            currentHandle.setPitch(this.pitch);
        }
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public float getSpatialBlend() {
        return spatialBlend;
    }

    public void setSpatialBlend(float spatialBlend) {
        this.spatialBlend = Math.max(0.0f, Math.min(1.0f, spatialBlend));
    }

    public boolean isPlayOnAwake() {
        return playOnAwake;
    }

    public void setPlayOnAwake(boolean playOnAwake) {
        this.playOnAwake = playOnAwake;
    }

    public float getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(float minDistance) {
        this.minDistance = Math.max(0.0f, minDistance);
    }

    public float getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = Math.max(1.0f, maxDistance);
    }
}
