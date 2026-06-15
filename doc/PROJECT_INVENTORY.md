# Inventário Completo do IgnisEngine

> Auditoria de 2026-06-15 · Branch `main` · Java 17 · ~36.500 linhas em 85 arquivos `.java`
> Documento de inventário. Ver também: [MASTER_ROADMAP.md](MASTER_ROADMAP.md), [ARCHITECTURE.md](ARCHITECTURE.md), [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md), [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).

## Como ler

Estado de cada sistema:
- **Concluído** — implementado e em uso.
- **Parcial** — funciona mas incompleto / com lacunas.
- **Experimental** — instável ou prova de conceito.
- **Planejado** — previsto, não iniciado.
- **Obsoleto** — candidato a remoção/substituição.

> Nota arquitetural importante: o IgnisEngine **não usa ECS formal**. O modelo é
> **herança de `GameObject` (abstrato) + comportamento via scripts (`IgnisScript`)**
> anexados, com serialização JSON. Onde o roadmap original falava "ECS", leia
> "modelo GameObject + scripts".

---

## 1. Core — Motor

| Sistema | Estado | Arquivos principais | Observações |
|---|---|---|---|
| Game loop / ciclo tick-render | Concluído | `core/Game.java` (2003) | `Game extends java.awt.Canvas`, thread própria, `BufferStrategy` + `Graphics2D`. Centraliza loop, input, estados (EDITOR/PLAYING). Arquivo grande (ver dívidas). |
| Modelo de entidade | Concluído | `core/GameObject.java` (548) | Classe **abstrata**: `tick()`, `render(Graphics)`, `loadProperties/saveProperties(JSONObject)`. Carrega `List<IgnisScript>`, sprite, `Animator`, `MusicPath`. |
| Formas/entidades concretas | Concluído | `Circle, Square, Triangle, Pentagon, Star, MergedShape, Player` | Subclasses de `GameObject`. `MergedShape` (357) combina formas. |
| Cena | Concluído | `core/Scene.java` (373) | `List<GameObject> entities` + `List<Camera> cameras`; serialização via `EntityFactory`. |
| Transform | Concluído | `core/Transform.java` (322), `TransformSpace.java` | Posição/rotação/escala; espaço local/mundo. |
| Câmera | Concluído | `core/Camera.java` (478) | Múltiplas câmeras por cena, câmera ativa. |
| Viewport | Concluído | `core/Viewport.java` (221) | Área de renderização/zoom no editor. |
| Input | Concluído | `core/Input.java` (301) | Teclado/mouse via listeners AWT no `Canvas`. |
| Fábrica de entidades | Concluído | `core/EntityFactory.java` | Cria `GameObject` por tipo na desserialização. |
| Projeto/IO | Concluído | `core/Project.java`, `core/IgnisProjectIO.java` (303) | Lê/grava `.ignis` (JSON). |
| Serialização | Concluído | `core/Serialize.java` (anotação), `ScriptSerializationHelper.java` (166) | `@Serialize` em campos de script → persistência + inspector via reflexão. |
| Prefabs | Concluído | `core/PrefabManager.java` (341) | Salva/instancia prefabs. |
| Assets | Parcial | `core/AssetResolver.java` (142) | Resolve caminhos de assets; sem cache/gerência avançada. |

### 1.1 Scripting

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Linguagem/runtime IgnisScript | Concluído | `core/IgnisScript.java` (1610) | Sistema de scripts próprio anexado a `GameObject`. Arquivo muito grande. |
| Gerenciador de scripts | Concluído | `core/ScriptManager.java` (416) | Registro/ciclo de vida dos scripts. |
| Editor de código (Swing) | Concluído | `editor/EditorTextPane.java` (361), `AutocompleteManager.java` (439), `ScriptEditorWindow.java` (348) | Editor temático com autocomplete. |
| Docs/refs do script | Concluído | `doc/IGNIS_SCRIPT_API.md`, `IGNISSCRIPT_QUICK_REFERENCE.md` | Documentação existente. |

### 1.2 Física / Colisões

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Colisões | Parcial | `core/IgnisSampleCollisions.java` (1215) | Detecção/respostas de colisão + alertas. Não há motor de física rígida desacoplado (sem rigidbodies/solver). Nome "Sample" sugere base/exemplo. |

### 1.3 Áudio (engine)

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Engine de som | Concluído | `core/IgnisSoundEngine.java` (591), `core/MusicPath.java` (350) | Reprodução, trilhas por objeto. |

---

## 2. UI in-game (`core/ui`)

Sistema de UI **desenhado em canvas** (separado do Swing/JavaFX do editor) — para HUDs/menus dentro do jogo.

| Sistema | Estado | Arquivos |
|---|---|---|
| Canvas + componentes | Concluído | `UICanvas` (418), `UIComponent` (754), `UIButton`, `UILabel`, `UIPanel`, `UIImage`, `UISlider`, `UITextField` (590), `UIProgressBar`, `UICheckbox`, `UIToggle`, `UIFactory` (211) |
| Ícones vetoriais | Concluído | `core/ui/VectorIcon.java` (274) | Ícones desenhados por código (recente). |

---

## 3. Editor (Swing - Legado)

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Janela principal / Hierarchy / Inspector / Scene View / Asset Browser | Concluído | `editor/Editor.java` (5580) | **Monolito** — concentra quase todos os painéis. Maior dívida técnica. |
| Painel auxiliar | Concluído | `editor/AuxiliaryPanel.java` (829) | Painéis dockáveis adicionais. |
| Editor de código (janela) | Concluído | `editor/ScriptEditorWindow.java`, `EditorTextPane.java`, `AutocompleteManager.java` | (ver 1.1) |
| Visualizador Markdown | Concluído | `editor/MarkdownViewerFrame.java` (433) | Renderiza docs/markdown. |
| Diálogo de Build | Concluído | `editor/BuildDialog.java` (221) | UI do Builder. |
| Integração IA (Gemini) | Concluído | `editor/AIIntegration.java` (402), `AIServiceProvider.java`, `GeminiProvider.java` (184) | "Agent Mode" / assistente (Gemini 2.5 Flash). Ver docs `AGENT_MODE_*`. |
| Layout persistido | Concluído | `editor_layout.json` | Preferências/posições salvas. |

---

## 3.1 Editor Moderno (JavaFX - Principal)

| Arquivo / Classe | Tamanho (Bytes) | Descrição / Responsabilidade | Estado |
|---|---|---|---|
| `editor/fx/IgnisEditorApp.java` | ~53.655 | Janela principal do editor moderno, implementando Hierarchy (TreeView), Inspector, Scene View (ImageView render bridge) e painéis. | Concluído |
| `editor/fx/FxProjectStartupDialog.java` | ~6.340 | Diálogo inicial para gerenciar e abrir projetos recentes ou criar novos. | Concluído |
| `editor/fx/FxCodeEditor.java` | ~41.211 | Editor de código integrado para scripts IgnisScript, com destaque de sintaxe e painéis de ajuda. | Concluído |
| `editor/fx/FxImageEditor.java` | ~25.897 | Painel do editor de imagens para spritesheets e texturas com suporte a camadas. | Concluído |
| `editor/fx/FxPaintCanvas.java` | ~26.159 | Área de pintura interativa utilizada pelo editor de imagens JavaFX. | Concluído |
| `editor/fx/FxAudioEditor.java` | ~45.148 | Interface completa de DAW (Digital Audio Workstation) para visualização e mixagem de som. | Concluído |
| `editor/fx/FxAnimationEditor.java` | ~22.309 | Painel de controle de animações baseado em spritesheets e quadros (keyframes). | Concluído |
| `editor/fx/FxCommunityWindow.java` | ~22.861 | Janela integrada da comunidade e do marketplace de plugins. | Concluído |
| `editor/fx/FxBuildDialog.java` | ~8.057 | Interface de build que gerencia as opções de compilação da engine. | Concluído |
| `editor/fx/EditorPrefs.java` | ~3.693 | Utilitário de leitura e persistência de preferências de usuário no JavaFX. | Concluído |

---

## 4. Sub-editores (Swing)

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Editor de imagens | Concluído | `imageeditor/ImageEditorFrame.java` (617), `PaintCanvas.java` (826), `ImageDocument.java` (174) | Pintura/camadas (canvas customizado). |
| Editor de áudio (DAW) | Concluído | `audioeditor/AudioEditorFrame.java` (1317), `WavAudioProcessor.java` (282) | Multipista/mixagem; arquivo grande. |
| Sistema de notas | Concluído | `notes/NoteSystemFrame.java` (605) | Wiki/notas estilo Notion. |
| Animação (modelo + runtime) | Concluído | `animation/Animator.java` (196), `SpriteAnimation.java` (160), `AnimationFrame.java`, `AnimationIO.java`; editor: `editor/AnimationEditorFrame.java` (581) | 2D por sprites/keyframes. 3D (skeletal) **Planejado**. |

---

## 5. Builder e Runtime

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Builder multiplataforma | Concluído | `builder/Builder.java`, `BuildConfig`, `BuildIO`, `BuildResult`, `BuildLogger`, `BuildStrategy`, `BuildTarget` | Orquestra builds por estratégia/alvo. |
| Estratégia Java | Concluído | `builder/JavaBuildStrategy.java` (157) | Distribuição JVM (Win/Linux/macOS). |
| Exportação C++ | Parcial/Experimental | `builder/CppExportStrategy.java` (291) | Geração de projeto C++ (consoles). Validar maturidade. |
| Runtime standalone | Concluído | `runtime/GameRuntime.java` (212) | Carrega `.ignis` e executa o jogo distribuído. |

---

## 6. Comunidade e Marketplace

| Sistema | Estado | Arquivos | Observações |
|---|---|---|---|
| Cliente do marketplace | Concluído | `marketplace/MarketplaceClient.java` (273), `MarketplaceItem.java` | HTTP (`java.net.http`) + fallback offline + token de publicação. |
| Hub da comunidade (UI) | Concluído | `community/CommunityFrame.java` (451) | Catálogo, 1-click install, 2 botões de publicar (site/token). |
| Backend web (submodule) | Concluído | `marketplace/` (Next.js + Neon, repo `ThyagoToledo/IginisMarketePlace`) | OAuth GitHub, gate de segurança, admin, tokens, legal. Em produção na Vercel. |

---

## 7. Infraestrutura

| Item | Estado | Observações |
|---|---|---|
| Build | Concluído | Maven (`pom.xml`), `maven.compiler.release=17`, plugins compiler + exec (`mainClass=com.ignis.editor.Editor`). |
| Maven Wrapper | Concluído | `mvnw`, `mvnw.cmd`, `.mvn/wrapper`. |
| Dependências | Concluído | Apenas `org.json:json:20231013` e dependências JavaFX 17 (nos módulos `javafx-controls`, `javafx-fxml`, `javafx-web`, `javafx-swing` e `javafx-media`). |
| Estrutura de pacotes | Concluído | `com.ignis.{core, core.ui, editor, editor.fx, imageeditor, audioeditor, notes, animation, builder, runtime, community, marketplace}`. |
| Plugins | Parcial | Instalação de plugins via marketplace (cópia para `plugins/`); sem sandbox/loader real ainda. |

---

## Resumo por classificação

- **Concluído:** Core (loop, GameObject, Scene, Transform, Câmera, Input, serialização, prefabs), scripting, áudio, UI in-game, editor principal Swing, editor principal JavaFX (10 arquivos novos), sub-editores (imagem/áudio/notas/animação 2D), Builder Java, runtime, marketplace (cliente + web).
- **Parcial:** Assets (sem cache/gerência), colisões (sem motor de física desacoplado), exportação C++, sistema de plugins (sem sandbox/loader).
- **Experimental:** Exportação C++ (validar geração compilável real).
- **Planejado:** Animação 3D (skeletal/blend trees), física rígida desacoplada, multi-provedor de IA.
- **Obsoleto:** ver [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md) (candidatos: trechos de `IgnisSampleCollisions`, código morto em `Editor.java`).
