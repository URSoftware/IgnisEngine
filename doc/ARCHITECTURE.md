# Arquitetura Técnica do IgnisEngine

> 2026-06-15 · Permite entender o motor sem ler o código-fonte.
> Complementa [PROJECT_INVENTORY.md](PROJECT_INVENTORY.md) · [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md) · [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).

## 1. Visão geral

Motor de jogos **2D em Java 17 puro** com editor visual integrado. Dependência única em runtime: `org.json`. 

O projeto possui **dois entry points** para o editor (devido à migração Swing → JavaFX) e um para o runtime do jogo:
- **Editor JavaFX (Moderno / Padrão):** `com.ignis.editor.fx.IgnisEditorApp` (executado via `mvnw javafx:run` ou `run-editor-javafx.bat`).
- **Editor Swing (Legado):** `com.ignis.editor.Editor` (executado via `mvnw exec:java`).
- **Runtime Standalone:** `com.ignis.runtime.GameRuntime` (para rodar jogos compilados e distribuídos).

### Estrutura de pacotes

```
com.ignis
├── core            # motor: loop, entidades, cena, transform, câmera, input, serialização
│   └── ui          # UI in-game desenhada em canvas (HUD/menus do jogo)
├── editor          # editor visual Swing (legado)
│   └── fx          # editor visual JavaFX (moderno, em migração)
├── imageeditor     # editor de imagens (pintura/camadas)
├── audioeditor     # editor de áudio estilo DAW
├── notes           # sistema de notas/wiki
├── animation       # animação 2D (modelo + runtime)
├── builder         # geração de builds (Java/C++)
├── runtime         # runtime standalone dos jogos
├── community       # hub da comunidade (UI)
└── marketplace     # cliente do marketplace online
```

## 2. Modelo de dados (entidades)

**Não é ECS.** É herança + scripts:

```
GameObject (abstract)
 ├─ campos: id, name, x, y, width, height, rotation, spritePath, visible, nameColor
 ├─ List<IgnisScript> scripts   (comportamento anexado)
 ├─ Animator, MusicPath
 ├─ abstract tick()
 ├─ abstract render(Graphics g)
 ├─ abstract loadProperties(JSONObject) / saveProperties() : JSONObject
 └─ subclasses: Player, Circle, Square, Triangle, Pentagon, Star, MergedShape
Scene
 ├─ List<GameObject> entities
 └─ List<Camera> cameras (uma ativa)
Transform / TransformSpace   # posição/rotação/escala; local vs mundo
```

Comportamento dinâmico vem de **scripts** (`IgnisScript`) anexados ao `GameObject`. Campos de script marcados com `@Serialize` aparecem no Inspector e são persistidos.

## 3. Ciclos de vida do Editor

### A. Editor JavaFX (com.ignis.editor.fx.IgnisEditorApp)
```
IgnisEditorApp.main()
 → inicializa JavaFX Application Thread
 → exibe FxProjectStartupDialog (tela de boas-vindas / projetos recentes / abrir ou novo)
 → ao carregar projeto, monta o Stage principal (IgnisEditorApp)
 → inicializa painéis nativos JavaFX:
     - Hierarchy: TreeView nativo que gerencia o ciclo de vida das entidades na cena
     - Inspector: VBox dinâmico mapeando propriedades do objeto selecionado
     - Asset Browser: Árvore de arquivos do projeto
     - Janelas de ferramentas adicionais (FxCodeEditor, FxImageEditor, FxAudioEditor, FxCommunityWindow, FxAnimationEditor)
 → inicia a Game Thread (Canvas AWT interno)
 → ponte de render: desenha a viewport usando SwingFXUtils (BufferedImage do Game -> WritableImage exposta na UI JavaFX)
 → roteia eventos de teclado e mouse do JavaFX Stage para a Game Thread
 → Play/Stop/Pause alteram o estado da máquina de estados do Game
```

### B. Editor Swing Clássico (com.ignis.editor.Editor)
```
Editor.main()
 → cria JFrame (Editor.java) e painéis Swing (Hierarchy, Inspector, Scene View, Asset Browser)
 → carrega editor_layout.json (preferências)
 → abre/cria Project (.ignis) via IgnisProjectIO
 → instancia Game (Canvas) embutido no Scene View (estado EDITOR)
 → usuário edita: seleção (Hierarchy) → propriedades (Inspector) → manipulação (Scene View)
 → Play: Game muda para estado PLAYING (executa tick/render dos scripts)
 → Stop: volta ao estado EDITOR
 → Build: BuildDialog → Builder gera distribuição
```

## 4. Ciclo de vida do Runtime

```
GameRuntime.main()
 → carrega o .ignis empacotado (IgnisProjectIO/Scene)
 → reconstrói entidades via EntityFactory + scripts (ScriptManager)
 → cria Game (Canvas) em janela própria, estado PLAYING
 → executa o game loop até fechar
```

## 5. Game loop e renderização

`Game extends java.awt.Canvas implements Runnable` — thread dedicada.

```
loop (thread do jogo):
  tick():
    - processa Input (listeners AWT no Canvas)
    - atualiza entidades: GameObject.tick() → executa scripts (IgnisScript)
    - física/colisões (IgnisSampleCollisions)
    - anima (Animator), áudio (IgnisSoundEngine)
  render():
    - BufferStrategy (double buffering)
    - Graphics2D g = bufferStrategy.getDrawGraphics()
    - aplica Camera/Viewport (translação/zoom)
    - para cada entidade visível: GameObject.render(g)
    - UICanvas (UI in-game) desenha por cima quando PLAYING
    - bufferStrategy.show()
```

### Ponte de Render para JavaFX
Quando executado no modo JavaFX, o game loop renderiza para uma `BufferedImage` interna por meio do método `Game.renderWorldTo()`. O editor JavaFX converte essa imagem periodicamente para um `WritableImage` via `SwingFXUtils.toFXImage()` e a desenha em um componente `ImageView` do JavaFX na thread da UI.

## 6. Fluxo de serialização (`.ignis`)

```
Salvar:
  IgnisProjectIO.save()
   → Scene percorre entities
   → cada GameObject.saveProperties() → JSONObject (campos próprios)
   → scripts: ScriptSerializationHelper lê campos @Serialize por reflexão
   → grava arquivo .ignis (JSON, org.json)

Carregar:
  IgnisProjectIO.load()
   → lê JSON
   → EntityFactory.create(type) recria cada GameObject
   → GameObject.loadProperties(JSONObject)
   → ScriptManager reanexa scripts; aplica variáveis @Serialize pendentes
```

## 7. Fluxo de assets

```
AssetResolver resolve caminhos relativos do projeto (sprites, sons).
PrefabManager salva/instancia prefabs (entidades pré-configuradas).
Builder copia assets para a distribuição final.
```
(Sem cache de assets hoje — ver dívidas.)

## 8. UI in-game (`core/ui`)

Sistema próprio de UI **desenhado no canvas do jogo** (independente do Swing/JavaFX do editor):
`UICanvas` agrega `UIComponent`s (`UIButton`, `UILabel`, `UIPanel`, `UISlider`, `UITextField`, `UIProgressBar`, `UICheckbox`, `UIToggle`, `UIImage`), criados via `UIFactory`; ícones por `VectorIcon`. Recebe prioridade de input quando o jogo está `PLAYING`.

## 9. Builder

```
Builder + BuildConfig (alvo/opções)
 → seleciona BuildStrategy por BuildTarget:
     JavaBuildStrategy  → distribuição JVM (Win/Linux/macOS)
     CppExportStrategy  → geração de projeto C++ (consoles) [a validar]
 → BuildIO/BuildLogger registram artefatos e logs
 → BuildResult com status
```

## 10. Marketplace (integração)

```
Editor (CommunityFrame ou FxCommunityWindow)
 → MarketplaceClient (java.net.http) → API Next.js (Vercel) + Neon
     GET /api/items        (catálogo, público; fallback mock offline)
     POST /api/items       (publicar; Authorization: Bearer <token>)
 → 1-click install copia o pacote (URL Git) para o projeto
Backend: OAuth GitHub, gate de segurança (valida repo), admin (ban), tokens, legal.
```

## 11. Threading e Concorrência

O IgnisEngine gerencia múltiplas threads para separar a simulação de jogo e a interface visual:
1. **Game Thread:** O loop principal do jogo (em `Game.java`), responsável por atualizações (tick) e renderização gráfica contínua.
2. **EDT (Event Dispatch Thread):** Usada quando o editor legado (Swing) está ativo, tratando cliques e repinturas da UI clássica.
3. **JavaFX Application Thread:** A thread principal de UI para o editor JavaFX. Qualquer alteração em elementos visuais do JavaFX deve ocorrer obrigatoriamente nesta thread.

### Regras de Ouro de Sincronização:
- Alterar componentes JavaFX a partir da Game Thread: envolver o código em `Platform.runLater()`.
- Alterar componentes Swing a partir da Game Thread: envolver o código em `SwingUtilities.invokeLater()`.
- Acessar estados compartilhados do jogo: utilizar travas de sincronização apropriadas para evitar condições de corrida entre a thread de simulação e a thread de interface.

## 12. IA (Agent Mode)

`AIIntegration` usa `AIServiceProvider` (abstração) com `GeminiProvider` (Gemini 2.5 Flash) para assistente/“Agent Mode” no editor. Estende-se a multi-provedor no futuro. Docs: `doc/AGENT_MODE_*`.
