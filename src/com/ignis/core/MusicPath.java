package com.ignis.core;

import org.json.JSONObject;

/**
 * MusicPath - Componente para seleção e gerenciamento de arquivos de áudio.
 * 
 * Esta classe permite associar arquivos de música/som a GameObjects de forma
 * visual, permitindo selecionar arquivos diretamente do gerenciador de diretórios
 * do editor.
 * 
 * Uso no Inspector:
 * - Arraste arquivos de áudio do File Browser para o campo MusicPath
 * - Ou clique no botão "Browse" para selecionar um arquivo
 * 
 * Uso em Scripts:
 * ```java
 * // Obter o caminho da música configurado
 * String musicFile = gameObject.getMusicPath().getPath();
 * playMusic(musicFile);
 * 
 * // Ou usar o método de conveniência
 * gameObject.getMusicPath().play();
 * ```
 */
public class MusicPath {

    // ==================== CAMPOS ====================
    
    /** Caminho relativo do arquivo de áudio (relativo à pasta do projeto) */
    private String path;
    
    /** Nome de exibição do arquivo */
    private String displayName;
    
    /** Volume padrão (0.0 a 1.0) */
    private float volume = 1.0f;
    
    /** Se deve tocar em loop */
    private boolean loop = false;
    
    /** Se deve tocar automaticamente quando o jogo iniciar */
    private boolean autoPlay = false;
    
    /** Se é música de fundo (usa o canal de música) ou efeito sonoro (usa canal de SFX) */
    private boolean isBackgroundMusic = true;
    
    /** Caminho absoluto resolvido (calculado em runtime) */
    private transient String resolvedPath;
    
    // ==================== CONSTRUTORES ====================
    
    /**
     * Cria um MusicPath vazio.
     */
    public MusicPath() {
        this.path = "";
        this.displayName = "(Nenhum arquivo)";
    }
    
    /**
     * Cria um MusicPath com o caminho especificado.
     * @param path Caminho do arquivo de áudio (relativo ou absoluto)
     */
    public MusicPath(String path) {
        setPath(path);
    }
    
    /**
     * Cria um MusicPath com configurações completas.
     * @param path Caminho do arquivo de áudio
     * @param volume Volume (0.0 a 1.0)
     * @param loop Se deve tocar em loop
     * @param isMusic Se é música de fundo (true) ou efeito sonoro (false)
     */
    public MusicPath(String path, float volume, boolean loop, boolean isMusic) {
        setPath(path);
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        this.loop = loop;
        this.isBackgroundMusic = isMusic;
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    /**
     * Obtém o caminho do arquivo de áudio.
     * @return Caminho do arquivo
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Define o caminho do arquivo de áudio.
     * @param path Caminho do arquivo (relativo ou absoluto)
     */
    public void setPath(String path) {
        this.path = path != null ? path : "";
        
        // Extrair nome de exibição do caminho
        if (this.path.isEmpty()) {
            this.displayName = "(Nenhum arquivo)";
        } else {
            int lastSep = Math.max(this.path.lastIndexOf('/'), this.path.lastIndexOf('\\'));
            this.displayName = lastSep >= 0 ? this.path.substring(lastSep + 1) : this.path;
        }
        
        // Limpar caminho resolvido para recalcular
        this.resolvedPath = null;
    }
    
    /**
     * Obtém o nome de exibição do arquivo.
     * @return Nome do arquivo para exibição
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Obtém o volume.
     * @return Volume (0.0 a 1.0)
     */
    public float getVolume() {
        return volume;
    }
    
    /**
     * Define o volume.
     * @param volume Volume (0.0 a 1.0)
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }
    
    /**
     * Verifica se está configurado para loop.
     * @return true se deve tocar em loop
     */
    public boolean isLoop() {
        return loop;
    }
    
    /**
     * Define se deve tocar em loop.
     * @param loop true para tocar em loop
     */
    public void setLoop(boolean loop) {
        this.loop = loop;
    }
    
    /**
     * Verifica se está configurado para tocar automaticamente.
     * @return true se deve tocar automaticamente
     */
    public boolean isAutoPlay() {
        return autoPlay;
    }
    
    /**
     * Define se deve tocar automaticamente quando o jogo iniciar.
     * @param autoPlay true para tocar automaticamente
     */
    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }
    
    /**
     * Verifica se é música de fundo.
     * @return true se é música de fundo, false se é efeito sonoro
     */
    public boolean isBackgroundMusic() {
        return isBackgroundMusic;
    }
    
    /**
     * Define se é música de fundo ou efeito sonoro.
     * @param isBackgroundMusic true para música de fundo
     */
    public void setBackgroundMusic(boolean isBackgroundMusic) {
        this.isBackgroundMusic = isBackgroundMusic;
    }
    
    /**
     * Verifica se um arquivo foi selecionado.
     * @return true se há um arquivo válido configurado
     */
    public boolean hasFile() {
        return path != null && !path.isEmpty();
    }
    
    /**
     * Verifica se o arquivo existe.
     * @return true se o arquivo existe no sistema de arquivos
     */
    public boolean fileExists() {
        if (!hasFile()) return false;
        java.io.File file = new java.io.File(getResolvedPath());
        return file.exists() && file.isFile();
    }
    
    /**
     * Obtém o caminho absoluto resolvido do arquivo.
     * @return Caminho absoluto do arquivo
     */
    public String getResolvedPath() {
        if (resolvedPath == null && hasFile()) {
            // Se já é absoluto, usar diretamente
            java.io.File file = new java.io.File(path);
            if (file.isAbsolute()) {
                resolvedPath = path;
            } else {
                // Tentar resolver relativo ao diretório de trabalho
                resolvedPath = new java.io.File(System.getProperty("user.dir"), path).getAbsolutePath();
            }
        }
        return resolvedPath != null ? resolvedPath : path;
    }
    
    // ==================== MÉTODOS DE ÁUDIO ====================
    
    /**
     * Reproduz o áudio configurado.
     * Usa as configurações de volume, loop e tipo (música/SFX).
     */
    public void play() {
        if (!hasFile()) {
            System.out.println("[MusicPath] Nenhum arquivo de áudio configurado.");
            return;
        }
        
        String audioPath = getResolvedPath();
        IgnisSoundEngine engine = IgnisSoundEngine.getInstance();
        
        if (isBackgroundMusic) {
            engine.playMusic(audioPath, loop);
            engine.setMusicVolume(volume);
        } else {
            engine.playSound(audioPath, volume, loop, null);
        }
    }
    
    /**
     * Reproduz o áudio com callback ao finalizar.
     * Nota: O callback só funciona para efeitos sonoros, não para música de fundo.
     * @param onComplete Ação a executar quando o áudio terminar
     */
    public void play(Runnable onComplete) {
        if (!hasFile()) return;
        
        String audioPath = getResolvedPath();
        IgnisSoundEngine engine = IgnisSoundEngine.getInstance();
        
        if (isBackgroundMusic) {
            // Música de fundo não suporta callback diretamente
            engine.playMusic(audioPath, loop);
            engine.setMusicVolume(volume);
            // Se houver callback e não for loop, agendar execução
            // (nota: isso é uma aproximação, não é preciso)
        } else {
            engine.playSoundWithCallback(audioPath, onComplete);
        }
    }
    
    /**
     * Para a reprodução do áudio.
     */
    public void stop() {
        IgnisSoundEngine engine = IgnisSoundEngine.getInstance();
        if (isBackgroundMusic) {
            engine.stopMusic();
        }
    }
    
    /**
     * Pausa a reprodução (apenas para música de fundo).
     */
    public void pause() {
        if (isBackgroundMusic) {
            IgnisSoundEngine.getInstance().pauseMusic();
        }
    }
    
    /**
     * Retoma a reprodução (apenas para música de fundo).
     */
    public void resume() {
        if (isBackgroundMusic) {
            IgnisSoundEngine.getInstance().resumeMusic();
        }
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    /**
     * Serializa para JSON.
     * @return Objeto JSON com os dados
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("path", path);
        json.put("volume", volume);
        json.put("loop", loop);
        json.put("autoPlay", autoPlay);
        json.put("isBackgroundMusic", isBackgroundMusic);
        return json;
    }
    
    /**
     * Deserializa de JSON.
     * @param json Objeto JSON com os dados
     * @return Nova instância de MusicPath
     */
    public static MusicPath fromJSON(JSONObject json) {
        MusicPath mp = new MusicPath();
        mp.path = json.optString("path", "");
        mp.volume = (float) json.optDouble("volume", 1.0);
        mp.loop = json.optBoolean("loop", false);
        mp.autoPlay = json.optBoolean("autoPlay", false);
        mp.isBackgroundMusic = json.optBoolean("isBackgroundMusic", true);
        
        // Atualizar displayName
        if (!mp.path.isEmpty()) {
            int lastSep = Math.max(mp.path.lastIndexOf('/'), mp.path.lastIndexOf('\\'));
            mp.displayName = lastSep >= 0 ? mp.path.substring(lastSep + 1) : mp.path;
        }
        
        return mp;
    }
    
    /**
     * Cria uma cópia deste MusicPath.
     * @return Nova instância com os mesmos valores
     */
    public MusicPath copy() {
        MusicPath copy = new MusicPath();
        copy.path = this.path;
        copy.displayName = this.displayName;
        copy.volume = this.volume;
        copy.loop = this.loop;
        copy.autoPlay = this.autoPlay;
        copy.isBackgroundMusic = this.isBackgroundMusic;
        return copy;
    }
    
    @Override
    public String toString() {
        return "MusicPath{" + displayName + ", vol=" + volume + ", loop=" + loop + ", autoPlay=" + autoPlay + "}";
    }
}
