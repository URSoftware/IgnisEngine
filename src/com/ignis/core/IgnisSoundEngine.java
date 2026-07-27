package com.ignis.core;

import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Motor de áudio do Ignis Engine.
 * Suporta reprodução de áudio em formatos WAV e MP3.
 * Oferece funcionalidades para efeitos sonoros e música de fundo.
 */
public class IgnisSoundEngine {

    // ==================== SINGLETON ====================
    
    private static IgnisSoundEngine instance;

    public static synchronized IgnisSoundEngine getInstance() {
        if (instance == null) {
            instance = new IgnisSoundEngine();
        }
        return instance;
    }

    // ==================== CONSTANTES ====================

    // Concurrent SFX voices. Threads are daemon so they never keep the JVM
    // alive on their own (defense-in-depth against orphan processes).
    private static final int MAX_SOUND_EFFECTS = 8;

    // Teto do cache de audio pre-carregado. preload() guarda o arquivo INTEIRO em
    // memoria; sem limite, um jogo que pre-carrega varias musicas esgota a RAM
    // (um dos fatores do OutOfMemoryError observado no editor em 15/07/2026).
    // Acima do teto o audio continua funcionando — apenas passa a ler do disco.
    private static final long MAX_AUDIO_CACHE_BYTES = 64L * 1024 * 1024;

    // ==================== CAMPOS ====================

    // Executor para reprodução assíncrona
    private ExecutorService audioExecutor;

    // Cache de clips de áudio
    private Map<String, byte[]> audioCache;
    private final java.util.concurrent.atomic.AtomicLong audioCacheBytes =
            new java.util.concurrent.atomic.AtomicLong();
    
    // Música de fundo atual
    private Clip backgroundMusic;
    private String currentMusicPath;
    private boolean musicLooping;
    
    // Controle de volume (0.0 a 1.0)
    private float masterVolume = 1.0f;
    private float musicVolume = 0.8f;
    private float sfxVolume = 1.0f;
    
    // Estado
    private boolean initialized = false;
    private boolean musicPaused = false;
    private long musicPausePosition = 0;
    /** True while the game world is paused; blocks new SFX voices. */
    private volatile boolean audioPaused = false;
    
    // Callbacks
    private Map<String, Runnable> soundCallbacks;
    private Map<String, Runnable> musicCallbacks;
    
    // Lista de efeitos sonoros ativos
    private List<Clip> activeSoundEffects;

    // ==================== CONSTRUTOR ====================
    
    private IgnisSoundEngine() {
        audioCache = new ConcurrentHashMap<>();
        soundCallbacks = new ConcurrentHashMap<>();
        musicCallbacks = new ConcurrentHashMap<>();
        activeSoundEffects = Collections.synchronizedList(new ArrayList<>());
        audioExecutor = Executors.newFixedThreadPool(MAX_SOUND_EFFECTS, runnable -> {
            Thread thread = new Thread(runnable, "IgnisAudio");
            thread.setDaemon(true);
            return thread;
        });
        initialized = true;
        
        IgnisLogger.info("[IgnisSoundEngine] Motor de áudio inicializado!");
    }

    // ==================== MÉTODOS DE EFEITOS SONOROS ====================
    
    /**
     * Reproduz um efeito sonoro.
     * @param filePath Caminho do arquivo de áudio (WAV ou MP3)
     */
    public void playSound(String filePath) {
        playSound(filePath, sfxVolume, false, null);
    }
    
    /**
     * Reproduz um efeito sonoro com volume personalizado.
     * @param filePath Caminho do arquivo de áudio
     * @param volume Volume (0.0 a 1.0)
     */
    public void playSound(String filePath, float volume) {
        playSound(filePath, volume, false, null);
    }
    
    /**
     * Reproduz um efeito sonoro com callback ao finalizar.
     * @param filePath Caminho do arquivo de áudio
     * @param onComplete Callback executado ao terminar
     */
    public void playSoundWithCallback(String filePath, Runnable onComplete) {
        playSound(filePath, sfxVolume, false, onComplete);
    }
    
    /**
     * Reproduz um efeito sonoro com todas as opções.
     * @param filePath Caminho do arquivo de áudio
     * @param volume Volume (0.0 a 1.0)
     * @param loop Se deve repetir em loop
     * @param onComplete Callback ao finalizar (ignorado se loop=true)
     */
    public void playSound(String filePath, float volume, boolean loop, Runnable onComplete) {
        if (!initialized || audioPaused) return;
        
        audioExecutor.submit(() -> {
            try {
                Clip clip = loadClip(filePath);
                if (clip == null) return;

                // Loading is asynchronous. Re-check under the same monitor used
                // by pauseAllAudio so a voice cannot start after the pause edge.
                synchronized (this) {
                    if (!initialized || audioPaused) {
                        clip.close();
                        return;
                    }

                    // The actual clip setup remains inside this monitor.

                // Aplicar volume
                setClipVolume(clip, volume * sfxVolume * masterVolume);
                
                // Configurar loop
                if (loop) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                }
                
                // Adicionar à lista de efeitos ativos
                activeSoundEffects.add(clip);
                
                // Listener para limpeza e callback
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && !loop) {
                        activeSoundEffects.remove(clip);
                        clip.close();
                        
                        if (!audioPaused) {
                            if (onComplete != null) {
                                onComplete.run();
                            }

                            // Executar callback registrado
                            String callbackKey = getCallbackKey(filePath);
                            Runnable callback = soundCallbacks.get(callbackKey);
                            if (callback != null) {
                                callback.run();
                            }
                        }
                    }
                });
                
                clip.start();
                }
                
            } catch (Exception e) {
                IgnisLogger.error("[IgnisSoundEngine] Erro ao reproduzir som: " + e.getMessage());
            }
        });
    }
    
    /**
     * Para todos os efeitos sonoros.
     */
    public synchronized void stopAllSounds() {
        synchronized (activeSoundEffects) {
            for (Clip clip : new ArrayList<>(activeSoundEffects)) {
                if (clip.isOpen()) {
                    clip.stop();
                    clip.close();
                }
            }
            activeSoundEffects.clear();
        }
    }

    // ==================== MÉTODOS DE MÚSICA ====================
    
    /**
     * Reproduz música de fundo.
     * @param filePath Caminho do arquivo de música
     */
    public void playMusic(String filePath) {
        playMusic(filePath, true);
    }
    
    /**
     * Reproduz música de fundo.
     * @param filePath Caminho do arquivo de música
     * @param loop Se deve repetir em loop
     */
    public void playMusic(String filePath, boolean loop) {
        playMusic(filePath, loop, null);
    }
    
    /**
     * Reproduz música de fundo com callback.
     * @param filePath Caminho do arquivo de música
     * @param loop Se deve repetir em loop
     * @param onComplete Callback ao finalizar (ignorado se loop=true)
     */
    public synchronized void playMusic(String filePath, boolean loop, Runnable onComplete) {
        if (!initialized || audioPaused) return;
        
        // Parar música atual se existir
        stopMusic();
        
        try {
            // Keep a local reference until setup is complete. The shared field
            // can be cleared by a lifecycle edge after this method returns.
            Clip clip = loadClip(filePath);
            if (clip == null) return;
            backgroundMusic = clip;
            
            currentMusicPath = filePath;
            musicLooping = loop;
            musicPaused = false;
            musicPausePosition = 0;
            
            // Aplicar volume
            setClipVolume(clip, musicVolume * masterVolume);
            
            // Configurar loop
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
            
            // Listener para callback
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && backgroundMusic == clip
                        && !musicLooping && !musicPaused) {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    
                    // Executar callback registrado
                    String callbackKey = getCallbackKey(filePath);
                    Runnable callback = musicCallbacks.get(callbackKey);
                    if (callback != null) {
                        callback.run();
                    }
                }
            });
            
            clip.start();
            IgnisLogger.info("[IgnisSoundEngine] Música iniciada: " + filePath);
            
        } catch (Exception e) {
            IgnisLogger.error("[IgnisSoundEngine] Erro ao reproduzir música: " + e.getMessage());
        }
    }
    
    /**
     * Pausa a música de fundo.
     */
    public synchronized void pauseMusic() {
        Clip clip = backgroundMusic;
        if (clip != null && clip.isOpen() && !musicPaused) {
            musicPausePosition = clip.getMicrosecondPosition();
            // Set before stop(): some mixers emit STOP synchronously.
            musicPaused = true;
            clip.stop();
            IgnisLogger.info("[IgnisSoundEngine] Música pausada");
        }
    }
    
    /**
     * Retoma a música de fundo.
     */
    public synchronized void resumeMusic() {
        Clip clip = backgroundMusic;
        if (clip != null && clip.isOpen() && musicPaused) {
                clip.setMicrosecondPosition(musicPausePosition);
                if (musicLooping) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    clip.start();
                }
            musicPaused = false;
            IgnisLogger.info("[IgnisSoundEngine] Música retomada");
        }
    }

    /** Pauses BGM and stops SFX voices while the world is paused. */
    public synchronized void pauseAllAudio() {
        if (audioPaused) return;
        audioPaused = true;
        pauseMusic();
        stopAllSounds();
    }

    /** Allows new SFX voices and resumes the paused BGM. */
    public synchronized void resumeAllAudio() {
        if (!audioPaused) return;
        audioPaused = false;
        resumeMusic();
    }

    public synchronized boolean isAudioPaused() {
        return audioPaused;
    }

    /** Returns the number of currently registered SFX voices. */
    public int getActiveSoundEffectCount() {
        synchronized (activeSoundEffects) {
            return activeSoundEffects.size();
        }
    }

    /** Stops all channels and clears the global pause flag. */
    public synchronized void stopAllAudio() {
        audioPaused = false;
        stopMusic();
        stopAllSounds();
    }
    
    /**
     * Para a música de fundo.
     */
    public synchronized void stopMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.close();
            backgroundMusic = null;
            currentMusicPath = null;
            musicPaused = false;
            musicPausePosition = 0;
            IgnisLogger.info("[IgnisSoundEngine] Música parada");
        }
    }
    
    /**
     * Verifica se há música tocando.
     * @return true se música está tocando
     */
    public synchronized boolean isMusicPlaying() {
        return backgroundMusic != null && backgroundMusic.isRunning();
    }
    
    /**
     * Verifica se a música está pausada.
     * @return true se música está pausada
     */
    public synchronized boolean isMusicPaused() {
        return musicPaused;
    }
    
    /**
     * Retorna o caminho da música atual.
     * @return caminho do arquivo de música ou null se não há música
     */
    public synchronized String getCurrentMusicPath() {
        return currentMusicPath;
    }

    // ==================== CONTROLE DE VOLUME ====================
    
    /**
     * Define o volume master (afeta tudo).
     * @param volume Volume (0.0 a 1.0)
     */
    public void setMasterVolume(float volume) {
        this.masterVolume = clamp(volume, 0.0f, 1.0f);
        updateMusicVolume();
    }
    
    /**
     * Define o volume da música.
     * @param volume Volume (0.0 a 1.0)
     */
    public void setMusicVolume(float volume) {
        this.musicVolume = clamp(volume, 0.0f, 1.0f);
        updateMusicVolume();
    }
    
    /**
     * Define o volume dos efeitos sonoros.
     * @param volume Volume (0.0 a 1.0)
     */
    public void setSfxVolume(float volume) {
        this.sfxVolume = clamp(volume, 0.0f, 1.0f);
    }
    
    /**
     * Obtém o volume master.
     * @return Volume master
     */
    public float getMasterVolume() {
        return masterVolume;
    }
    
    /**
     * Obtém o volume da música.
     * @return Volume da música
     */
    public float getMusicVolume() {
        return musicVolume;
    }
    
    /**
     * Obtém o volume dos efeitos sonoros.
     * @return Volume dos efeitos
     */
    public float getSfxVolume() {
        return sfxVolume;
    }
    
    private synchronized void updateMusicVolume() {
        Clip clip = backgroundMusic;
        if (clip != null && clip.isOpen()) {
            setClipVolume(clip, musicVolume * masterVolume);
        }
    }
    
    public void setClipVolume(Clip clip, float volume) {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                // Converter volume linear para decibéis
                float dB = (float) (Math.log10(Math.max(volume, 0.0001)) * 20.0);
                dB = clamp(dB, gainControl.getMinimum(), gainControl.getMaximum());
                gainControl.setValue(dB);
            }
        } catch (Exception e) {
            // Controle de volume não disponível
        }
    }

    public void setClipPan(Clip clip, float pan) {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(FloatControl.Type.PAN)) {
                FloatControl panControl = (FloatControl) clip.getControl(FloatControl.Type.PAN);
                float clampedPan = clamp(pan, panControl.getMinimum(), panControl.getMaximum());
                panControl.setValue(clampedPan);
            }
        } catch (Exception e) {
            // Controle de balanço estéreo não disponível
        }
    }

    public void setClipPitch(Clip clip, float pitch) {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(FloatControl.Type.SAMPLE_RATE)) {
                FloatControl rateControl = (FloatControl) clip.getControl(FloatControl.Type.SAMPLE_RATE);
                float defaultRate = rateControl.getValue();
                float targetRate = clamp(defaultRate * pitch, rateControl.getMinimum(), rateControl.getMaximum());
                rateControl.setValue(targetRate);
            }
        } catch (Exception e) {
            // Controle de pitch/frequência não disponível
        }
    }

    /**
     * Cria e inicia a reprodução de um áudio retornando um controlador {@link AudioHandle}.
     */
    public AudioHandle playHandle(String filePath, float volume, float pan, float pitch, boolean loop, Runnable onComplete) {
        if (!initialized || filePath == null || filePath.trim().isEmpty()) return null;
        try {
            Clip clip = loadClip(filePath);
            if (clip == null) return null;

            AudioHandle handle = new AudioHandle(this, clip, filePath, volume, pan, pitch, loop, onComplete);

            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }

            activeSoundEffects.add(clip);

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && !loop && !handle.isPaused()) {
                    activeSoundEffects.remove(clip);
                    clip.close();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    String callbackKey = getCallbackKey(filePath);
                    Runnable cb = soundCallbacks.get(callbackKey);
                    if (cb != null) {
                        cb.run();
                    }
                }
            });

            clip.start();
            return handle;
        } catch (Exception e) {
            IgnisLogger.error("[IgnisSoundEngine] Erro ao criar AudioHandle: " + e.getMessage());
            return null;
        }
    }

    /**
     * Objeto de controle de áudio ativo para atualização dinâmica por frame (volume, panning, pitch, pause/resume/stop).
     */
    public static class AudioHandle {
        private final IgnisSoundEngine engine;
        private final Clip clip;
        private final String filePath;
        private float baseVolume;
        private float pan;
        private float pitch;
        private final boolean loop;
        private boolean paused = false;
        private long pausePosition = 0;
        private final Runnable onComplete;

        public AudioHandle(IgnisSoundEngine engine, Clip clip, String filePath, float volume, float pan, float pitch, boolean loop, Runnable onComplete) {
            this.engine = engine;
            this.clip = clip;
            this.filePath = filePath;
            this.baseVolume = volume;
            this.pan = pan;
            this.pitch = pitch;
            this.loop = loop;
            this.onComplete = onComplete;
            applyControls();
        }

        public void applyControls() {
            if (clip != null && clip.isOpen()) {
                float effectiveVolume = baseVolume * engine.getSfxVolume() * engine.getMasterVolume();
                engine.setClipVolume(clip, effectiveVolume);
                engine.setClipPan(clip, pan);
                engine.setClipPitch(clip, pitch);
            }
        }

        public void setVolume(float volume) {
            this.baseVolume = Math.max(0.0f, Math.min(1.0f, volume));
            applyControls();
        }

        public void setPan(float pan) {
            this.pan = Math.max(-1.0f, Math.min(1.0f, pan));
            applyControls();
        }

        public void setPitch(float pitch) {
            this.pitch = Math.max(0.1f, Math.min(3.0f, pitch));
            applyControls();
        }

        public float getVolume() { return baseVolume; }
        public float getPan() { return pan; }
        public float getPitch() { return pitch; }
        public String getFilePath() { return filePath; }

        public void stop() {
            if (clip != null && clip.isOpen()) {
                clip.stop();
                clip.close();
            }
            if (engine != null) {
                engine.activeSoundEffects.remove(clip);
            }
        }

        public void pause() {
            if (clip != null && clip.isRunning()) {
                pausePosition = clip.getMicrosecondPosition();
                clip.stop();
                paused = true;
            }
        }

        public void resume() {
            if (clip != null && paused) {
                clip.setMicrosecondPosition(pausePosition);
                if (loop) clip.loop(Clip.LOOP_CONTINUOUSLY);
                else clip.start();
                paused = false;
            }
        }

        public boolean isPlaying() {
            return clip != null && clip.isRunning();
        }

        public boolean isPaused() {
            return paused;
        }

        public Clip getClip() {
            return clip;
        }
    }

    // ==================== CALLBACKS ====================
    
    /**
     * Registra um callback para quando um som terminar.
     * @param soundName Nome identificador do som
     * @param callback Ação a executar
     */
    public void onSoundComplete(String soundName, Runnable callback) {
        soundCallbacks.put(soundName, callback);
    }
    
    /**
     * Registra um callback para quando uma música terminar.
     * @param musicName Nome identificador da música
     * @param callback Ação a executar
     */
    public void onMusicComplete(String musicName, Runnable callback) {
        musicCallbacks.put(musicName, callback);
    }
    
    /**
     * Remove um callback de som.
     * @param soundName Nome do som
     */
    public void removeSoundCallback(String soundName) {
        soundCallbacks.remove(soundName);
    }
    
    /**
     * Remove um callback de música.
     * @param musicName Nome da música
     */
    public void removeMusicCallback(String musicName) {
        musicCallbacks.remove(musicName);
    }
    
    private String getCallbackKey(String filePath) {
        if (filePath == null) return "";
        File file = new File(filePath);
        return file.getName();
    }

    // ==================== PRÉ-CARREGAMENTO ====================
    
    /**
     * Pré-carrega um arquivo de áudio no cache.
     * @param filePath Caminho do arquivo
     */
    public void preload(String filePath) {
        try {
            File file = resolveAudioFile(filePath);
            if (file.exists()) {
                byte[] data = readFileBytes(file);
                byte[] previous = audioCache.get(filePath);
                long delta = data.length - (previous != null ? previous.length : 0L);
                if (audioCacheBytes.get() + delta > MAX_AUDIO_CACHE_BYTES) {
                    IgnisLogger.warn("[IgnisSoundEngine] Cache de áudio cheio ("
                            + (MAX_AUDIO_CACHE_BYTES / (1024 * 1024)) + "MB); '" + filePath
                            + "' será lido do disco ao tocar.");
                    return;
                }
                audioCache.put(filePath, data);
                audioCacheBytes.addAndGet(delta);
                IgnisLogger.info("[IgnisSoundEngine] Áudio pré-carregado: " + filePath);
            }
        } catch (Exception e) {
            IgnisLogger.error("[IgnisSoundEngine] Erro ao pré-carregar: " + e.getMessage());
        }
    }
    
    /**
     * Pré-carrega múltiplos arquivos.
     * @param filePaths Lista de caminhos
     */
    public void preloadAll(String... filePaths) {
        for (String path : filePaths) {
            preload(path);
        }
    }
    
    /**
     * Limpa o cache de áudio.
     */
    public void clearCache() {
        audioCache.clear();
        audioCacheBytes.set(0);
        IgnisLogger.info("[IgnisSoundEngine] Cache limpo");
    }

    // ==================== MÉTODOS INTERNOS ====================
    
    private Clip loadClip(String filePath) {
        try {
            AudioInputStream audioStream;
            
            // Verificar cache primeiro
            if (audioCache.containsKey(filePath)) {
                byte[] data = audioCache.get(filePath);
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                audioStream = AudioSystem.getAudioInputStream(bais);
            } else {
                File file = resolveAudioFile(filePath);
                if (!file.exists()) {
                    IgnisLogger.error("[IgnisSoundEngine] Arquivo não encontrado: " + filePath);
                    return null;
                }
                audioStream = AudioSystem.getAudioInputStream(file);
            }
            
            // Converter para formato compatível se necessário
            AudioFormat baseFormat = audioStream.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
            );
            
            AudioInputStream convertedStream = audioStream;
            if (!baseFormat.matches(targetFormat)) {
                convertedStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
            }

            // clip.open() copies the stream fully into memory; the source streams
            // (and any underlying file handle) must be closed to avoid leaking
            // file descriptors on every non-cached play.
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(convertedStream);
                return clip;
            } finally {
                convertedStream.close();
                audioStream.close();
            }

        } catch (UnsupportedAudioFileException e) {
            IgnisLogger.error("[IgnisSoundEngine] Formato de áudio não suportado: " + filePath);
            IgnisLogger.error("  Formatos suportados: WAV, AIFF, AU");
            IgnisLogger.error("  Para MP3, é necessário adicionar bibliotecas externas.");
            return null;
        } catch (Exception e) {
            IgnisLogger.error("[IgnisSoundEngine] Erro ao carregar áudio: " + e.getMessage());
            return null;
        }
    }
    
    private byte[] readFileBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
    
    static File resolveAudioFile(String filePath) {
        return AssetResolver.resolve(filePath);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== FINALIZAÇÃO ====================
    
    /**
     * Finaliza o motor de áudio e libera recursos.
     */
    public void shutdown() {
        IgnisLogger.info("[IgnisSoundEngine] Finalizando motor de áudio...");
        
        stopAllAudio();
        
        audioExecutor.shutdown();
        try {
            if (!audioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                audioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            audioExecutor.shutdownNow();
        }
        
        clearCache();
        soundCallbacks.clear();
        musicCallbacks.clear();
        
        initialized = false;
        instance = null;
        
        IgnisLogger.info("[IgnisSoundEngine] Motor de áudio finalizado");
    }

    // ==================== MÉTODOS ESTÁTICOS DE CONVENIÊNCIA ====================
    
    /**
     * Atalho para reproduzir um efeito sonoro.
     */
    public static void sfx(String filePath) {
        getInstance().playSound(filePath);
    }
    
    /**
     * Atalho para reproduzir música.
     */
    public static void music(String filePath) {
        getInstance().playMusic(filePath);
    }
    
    /**
     * Atalho para parar música.
     */
    public static void stopBGM() {
        getInstance().stopMusic();
    }
}
