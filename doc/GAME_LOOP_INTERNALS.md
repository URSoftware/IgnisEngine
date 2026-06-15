# Funcionamento Interno do Game Loop (Game Loop Internals)

> Documentação técnica detalhada sobre o ciclo de atualização e renderização, gerenciamento de estados de jogo e comportamento interno do motor `Game.java`.

---

## 1. Ciclo de Execução e Game Loop

O motor central da engine (`com.ignis.core.Game`) gerencia o loop principal do jogo através de uma thread dedicada (Game Thread) executando um ciclo contínuo.

### Fases do Game Loop
A cada frame, o loop executa duas etapas sequenciais:

1. **`tick()` (Simulação Físico-Lógica):**
   - **Processamento de Input:** Atualiza os estados de teclas e cliques do mouse via classe `Input`.
   - **Atualização Lógica:** Dispara o método `tick()` de todos os `GameObject`s ativos no cenário.
   - **Execução de Scripts:** Roda o ciclo de execução dos scripts (`IgnisScript.update()`) gerenciados pelo `ScriptManager`.
   - **Resolução de Colisões:** Invoca o resolvedor de física e colisões (`IgnisSampleCollisions`) para ajustar posições e disparar eventos/alertas.
   - **Atualização Visual/Sonora:** Atualiza os estados do `Animator` e ajusta os canais de reprodução de áudio.

2. **`render()` (Desenho Gráfico):**
   - No modo runtime standalone: Utiliza `BufferStrategy` (double buffering ou triple buffering) para obter um contexto gráfico AWT `Graphics2D` direto do Canvas e desenha as entidades.
   - No modo editor JavaFX: O desenho gráfico é roteado e convertido para exibição na thread de UI (veja Seção 3).

---

## 2. Máquina de Estados do Jogo

O ciclo de vida da simulação do mundo possui uma máquina de estados com três modos definidos pelo enum `GameState`:

```text
    ┌─────────────┐   Play / Simular    ┌─────────────┐
    │   EDITING   ├────────────────────>│   PLAYING   │
    └──────▲──────┘                     └────┬───▲────┘
           │                                 │   │
           │                               Pause │ Resume
           │          Stop / Parar           │   │
           └─────────────────────────────────┴───┘
                                        ┌─────────────┐
                                        │   PAUSED    │
                                        └─────────────┘
```

- **`EDITING` (Modo Editor):**
  - A simulação de física e scripts fica pausada.
  - Habilita ferramentas de edição visual, como seleção, translação, rotação, escala e encaixe na grade (grid snapping).
  - Permite criar, modificar ou remover entidades na Hierarchy e no Inspector.
- **`PLAYING` (Modo Simulação Ativa):**
  - Scripts e físicas são processados continuamente a cada tick.
  - Ferramentas de edição visual e manipulação de gizmos são desabilitadas na Scene View.
  - A UI do jogo (`UICanvas`) é renderizada por cima e consome interações de input.
- **`PAUSED` (Modo Pausa):**
  - Congela a física e a lógica dos scripts, mas mantém o estado das entidades nas posições atuais.
  - Pausa a reprodução de músicas e áudios de efeitos.

---

## 3. Sistema de Snapshots (EntitySnapshot)

Para evitar que a execução de scripts e físicas no modo `PLAYING` destrua o layout original da cena criado no editor, a engine implementa o sistema de **Snapshots**:

- **Ao transicionar de `EDITING` para `PLAYING` (`playWorld()`):**
  - Invoca o método `saveInitialSnapshots()`, que grava em memória uma imagem compacta com o estado de cada entidade (ID, posição `x/y`, dimensões `width/height` e rotação).
- **Ao transicionar de `PLAYING` para `EDITING` (`stopWorld()`):**
  - Restaura as variáveis de cada entidade utilizando o mapa de snapshots armazenado.
  - Para a música e sons em execução e limpa todos os componentes de UI gerados dinamicamente por scripts.
  - Garante que a cena volte exatamente ao estado original de design.

---

## 4. Ponte de Render para o Editor JavaFX (`renderWorldTo`)

Como a engine é desenhada internamente em um canvas AWT clássico (`java.awt.Graphics2D`), a integração visual com a interface JavaFX utiliza uma ponte de renderização assíncrona:

```text
[Game Thread (AWT)]                                 [UI Thread (JavaFX)]
  Game.renderWorldTo(g2d)                             SwingFXUtils.toFXImage()
  └─ Desenha em BufferedImage ──(Buffer Compartilhado)──> Converte para WritableImage
                                                            └─ Exibe no ImageView
```

1. O loop principal do `Game` detecta se está rodando acoplado ao editor.
2. Em vez de repintar a tela via `BufferStrategy`, invoca o método sincronizado `renderWorldTo(Graphics2D g2d, int width, int height, GameObject selected)`.
3. O método desenha a cena, a grade de auxílio, gizmos de seleção (no modo `EDITING`) e o canvas de UI in-game em uma `BufferedImage` na memória de vídeo.
4. O editor JavaFX (`IgnisEditorApp`) captura essa imagem periodicamente de forma segura contra concorrência e a converte usando `SwingFXUtils.toFXImage()` para ser exibida em um controle `ImageView` JavaFX a taxas de quadro fluidas (visando 60 FPS).

---

## 5. Manipulação de Entidades e Gizmos de Seleção

### Gerenciamento de Entidades
A engine gerencia a coleção de objetos na cena de forma segura por meio de filas:
- Métodos `addEntity()`, `removeEntity()`, `clearScene()`.
- O jogo protege a iteração de atualização lógica e de física de modo a evitar erros de concorrência (`ConcurrentModificationException`) caso um script tente destruir um objeto no meio do ciclo de atualização.

### Gizmos e Seleção Visual (Move, Rotate, Scale)
No modo `EDITING`, ao selecionar um objeto, a Scene View desenha sobrepostos:
- **Gizmo de Translação (Move):** Alças direcionais nos eixos X (vermelho) e Y (verde) para movimentar a entidade com precisão.
- **Gizmo de Rotação (Rotate):** Um anel que permite girar o objeto visualmente com o cursor do mouse.
- **Gizmo de Escala (Scale):** Pontos de controle nos cantos do retângulo limitador para ajustar a largura e altura.
