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
    
    public static IgnisSoundEngine getInstance() {
        if (instance == null) {
            instance = new IgnisSoundEngine();
        }
        return instance;
    }

    // ==================== CONSTANTES ====================
    
    private static final int MAX_SOUND_EFFECTS = 32;
    
    // ==================== CAMPOS ====================
    
    // Executor para reprodução assíncrona
    private ExecutorService audioExecutor;
    
    // Cache de clips de áudio
    private Map<String, byte[]> audioCache;
    
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
        audioExecutor = Executors.newFixedThreadPool(MAX_SOUND_EFFECTS);
        initialized = true;
        
        System.out.println("[IgnisSoundEngine] Audio engine initialized!");
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
        if (!initialized) return;
        
        audioExecutor.submit(() -> {
            try {
                Clip clip = loadClip(filePath);
                if (clip == null) return;
                
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
                });
                
                clip.start();
                
            } catch (Exception e) {
                System.err.println("[IgnisSoundEngine] Erro ao reproduzir som: " + e.getMessage());
            }
        });
    }
    
    /**
     * Para todos os efeitos sonoros.
     */
    public void stopAllSounds() {
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
    public void playMusic(String filePath, boolean loop, Runnable onComplete) {
        if (!initialized) return;
        
        // Parar música atual se existir
        stopMusic();
        
        try {
            backgroundMusic = loadClip(filePath);
            if (backgroundMusic == null) return;
            
            currentMusicPath = filePath;
            musicLooping = loop;
            musicPaused = false;
            musicPausePosition = 0;
            
            // Aplicar volume
            setClipVolume(backgroundMusic, musicVolume * masterVolume);
            
            // Configurar loop
            if (loop) {
                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            }
            
            // Listener para callback
            backgroundMusic.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && !musicLooping && !musicPaused) {
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
            
            backgroundMusic.start();
            System.out.println("[IgnisSoundEngine] Música iniciada: " + filePath);
            
        } catch (Exception e) {
            System.err.println("[IgnisSoundEngine] Erro ao reproduzir música: " + e.getMessage());
        }
    }
    
    /**
     * Pausa a música de fundo.
     */
    public void pauseMusic() {
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            musicPausePosition = backgroundMusic.getMicrosecondPosition();
            backgroundMusic.stop();
            musicPaused = true;
            System.out.println("[IgnisSoundEngine] Música pausada");
        }
    }
    
    /**
     * Retoma a música de fundo.
     */
    public void resumeMusic() {
        if (backgroundMusic != null && musicPaused) {
            backgroundMusic.setMicrosecondPosition(musicPausePosition);
            if (musicLooping) {
                backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                backgroundMusic.start();
            }
            musicPaused = false;
            System.out.println("[IgnisSoundEngine] Música retomada");
        }
    }
    
    /**
     * Para a música de fundo.
     */
    public void stopMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.stop();
            backgroundMusic.close();
            backgroundMusic = null;
            currentMusicPath = null;
            musicPaused = false;
            musicPausePosition = 0;
            System.out.println("[IgnisSoundEngine] Música parada");
        }
    }
    
    /**
     * Verifica se há música tocando.
     * @return true se música está tocando
     */
    public boolean isMusicPlaying() {
        return backgroundMusic != null && backgroundMusic.isRunning();
    }
    
    /**
     * Verifica se a música está pausada.
     * @return true se música está pausada
     */
    public boolean isMusicPaused() {
        return musicPaused;
    }
    
    /**
     * Retorna o caminho da música atual.
     * @return caminho do arquivo de música ou null se não há música
     */
    public String getCurrentMusicPath() {
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
    
    private void updateMusicVolume() {
        if (backgroundMusic != null && backgroundMusic.isOpen()) {
            setClipVolume(backgroundMusic, musicVolume * masterVolume);
        }
    }
    
    private void setClipVolume(Clip clip, float volume) {
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            // Converter volume linear para decibéis
            float dB = (float) (Math.log10(Math.max(volume, 0.0001)) * 20.0);
            dB = clamp(dB, gainControl.getMinimum(), gainControl.getMaximum());
            gainControl.setValue(dB);
        } catch (Exception e) {
            // Controle de volume não disponível
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
            File file = new File(filePath);
            if (file.exists()) {
                byte[] data = readFileBytes(file);
                audioCache.put(filePath, data);
                System.out.println("[IgnisSoundEngine] Áudio pré-carregado: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("[IgnisSoundEngine] Erro ao pré-carregar: " + e.getMessage());
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
        System.out.println("[IgnisSoundEngine] Cache limpo");
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
                File file = new File(filePath);
                if (!file.exists()) {
                    System.err.println("[IgnisSoundEngine] Arquivo não encontrado: " + filePath);
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
            
            Clip clip = AudioSystem.getClip();
            clip.open(convertedStream);
            
            return clip;
            
        } catch (UnsupportedAudioFileException e) {
            System.err.println("[IgnisSoundEngine] Formato de áudio não suportado: " + filePath);
            System.err.println("  Formatos suportados: WAV, AIFF, AU");
            System.err.println("  Para MP3, é necessário adicionar bibliotecas externas.");
            return null;
        } catch (Exception e) {
            System.err.println("[IgnisSoundEngine] Erro ao carregar áudio: " + e.getMessage());
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
    
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== FINALIZAÇÃO ====================
    
    /**
     * Finaliza o motor de áudio e libera recursos.
     */
    public void shutdown() {
        System.out.println("[IgnisSoundEngine] Finalizando motor de áudio...");
        
        stopMusic();
        stopAllSounds();
        
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
        
        System.out.println("[IgnisSoundEngine] Motor de áudio finalizado");
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
