# Guia do Sistema de Áudio e DAW (Audio System & DAW Guide)

> Documentação oficial do motor de áudio e do editor de áudio (DAW) integrado da IgnisEngine.

---

## 1. Arquitetura do IgnisSoundEngine

A reprodução sonora in-game é centralizada no **`IgnisSoundEngine`**, implementado como um Singleton thread-safe.

### Principais Características da Engine:
- **Execução Assíncrona:** A engine gerencia uma pool de threads fixa (`Executors.newFixedThreadPool`) com o máximo de 8 vozes de efeitos simultâneas para evitar travamento da Game Thread.
- **Daemon Threads:** As threads de áudio são configuradas explicitamente como daemon (`thread.setDaemon(true)`), garantindo que processos órfãos não fiquem rodando em segundo plano travando a JVM caso o jogo ou editor principal seja encerrado.
- **Cache de Áudio:** Efeitos carregados de disco são cacheados na memória RAM para evitar operações lentas de leitura e gargalos de IO durante o gameplay.
- **Controle de Canais:** Permite definir volumes independentes para Master, Música de Fundo (BGM) e Efeitos Sonoros (SFX).

---

## 2. MusicPath: Associação de Áudio a GameObjects

A classe `MusicPath` atua como um componente que pode ser anexado a qualquer `GameObject`. Ela permite que objetos do jogo tenham clipes de áudio ou músicas associados à sua própria posição espacial e ao seu ciclo de vida.
- **Funcionalidade:** Permite configurar músicas de fundo ou ruídos ambientais específicos por objeto ou sala.
- **Persistência:** O caminho do arquivo e propriedades de volume/loop são serializados no JSON do objeto sob o esquema `.ignis`.

---

## 3. Editor de Áudio (DAW - Digital Audio Workstation)

O editor conta com uma interface nativa de mixagem e edição de áudio, disponível no menu do editor:
- **Swing:** `AudioEditorFrame` / `WavAudioProcessor`.
- **JavaFX:** `FxAudioEditor`.

### Funcionalidades do Editor:
- **Waveform View:** Renderiza de forma gráfica a forma de onda (waveform) do arquivo de som carregado no projeto.
- **Timeline e Edição:** Permite definir pontos de início e fim, recortar partes do áudio, aplicar efeitos de fade-in/fade-out e configurar loops perfeitos.
- **Equalizador Gráfico:** Filtros de áudio ajustáveis (Grave, Médio, Agudo) e processamento em tempo real.

---

## 4. Formatos de Arquivo Suportados

| Formato | Tipo | Uso Recomendado | Observações |
|---|---|---|---|
| **WAV** | Sem compressão | Efeitos Sonoros (SFX) | Latência zero de decodificação. Ideal para sons curtos (pulos, tiros, colisões). |
| **MP3** | Comprimido | Trilha Sonora / Músicas (BGM) | Baixo consumo de RAM e espaço em disco. Decodificado em streaming. |

---

## 5. API de Áudio no IgnisScript

Métodos de áudio expostos em `IgnisScript` para controle via scripts:

| Assinatura do Método | Descrição |
|---|---|
| `playSound(filePath)` | Reproduz um efeito sonoro (SFX) a partir do caminho relativo. |
| `playSound(filePath, volume)` | Reproduz um efeito sonoro com volume customizado (0.0 a 1.0). |
| `playSoundWithCallback(filePath, Runnable onComplete)` | Reproduz o som e dispara a ação após a finalização do áudio. |
| `stopAllSounds()` | Interrompe imediatamente todos os efeitos sonoros em execução. |
| `playMusic(filePath)` | Inicia a reprodução de uma música de fundo (BGM) em loop. |
| `playMusic(filePath, boolean loop)` | Inicia a reprodução de música, permitindo habilitar/desabilitar loop. |
| `stopMusic()` | Interrompe a música de fundo atual. |
| `pauseMusic()` | Pausa temporariamente a reprodução da música (preserva posição). |
| `resumeMusic()` | Retoma a música pausada do ponto exato onde parou. |
| `pauseAllAudio()` | Pausa BGM, encerra SFX ativos e bloqueia novos SFX durante a pausa do mundo. |
| `resumeAllAudio()` | Libera novos SFX e retoma o BGM pausado. |
| `stopAllAudio()` | Para BGM e SFX e limpa o estado de pausa global. |
| `setMasterVolume(float vol)` | Altera o volume global do jogo (0.0 a 1.0). |
| `setMusicVolume(float vol)` | Altera o volume do canal de músicas (BGM) (0.0 a 1.0). |
| `setSFXVolume(float vol)` | Altera o volume do canal de efeitos sonoros (SFX) (0.0 a 1.0). |

---

## 6. Boas Práticas de Áudio

1. **Evitar concorrência excessiva de canais:** Embora a pool suporte até 8 efeitos em paralelo, evite disparar o mesmo efeito sonoro consecutivamente a cada frame (ex: colisões repetidas) para não esgotar as vozes de áudio.
2. **Fechamento e Liberação de Recursos:** Sempre libere recursos de clipes de áudio dinâmicos. O `IgnisSoundEngine` fecha automaticamente fluxos inativos, mas scripts devem evitar loops infinitos de criação de novos canais.
3. **Paths Relativos:** Utilize sempre caminhos relativos à raiz do projeto (`assets/sounds/...`) para garantir a portabilidade do projeto ao exportar ou empacotar.
