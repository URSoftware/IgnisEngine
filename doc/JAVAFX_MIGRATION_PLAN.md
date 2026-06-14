# Plano de Migração da Interface do Editor: Swing/AWT → JavaFX

> Status: **F0 + F1 + F2 implementadas na `main`** (infra · casca/ponte · projeto+seleção+Inspector) · Java 17 · JavaFX 17 · 2026-06-14
> Complementa [ARCHITECTURE.md](ARCHITECTURE.md) e [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md).
>
> **Como rodar:** editor JavaFX (em migração) → `mvnw javafx:run` · editor Swing clássico → `mvnw exec:java`.
> Ponte de render: `com.ignis.core.Game#renderWorldTo` → `BufferedImage` → `SwingFXUtils` → `Canvas` em `com.ignis.editor.fx.IgnisEditorApp`.

## Estratégia de branches

- **`Legado`**: versão estável atual (Swing/AWT) + marketplace. Preservada; não migra. Ponto de retorno seguro.
- **`main`**: linha de desenvolvimento da migração JavaFX. Núcleo (`core/`) e marketplace permanecem.
- Migração **incremental**; cada fase é mergeável na `main`.

## 1. Objetivo

Trocar a casca de UI do editor de **Swing** para **JavaFX** (CSS, layout responsivo, binding, animações) **sem reescrever o núcleo da engine** nem o pipeline de render.

## 2. Auditoria de Swing (estado atual)

**223 ocorrências** de tipos Swing/AWT em **14 arquivos**. Distribuição:

| Arquivo | Ocorrências | Papel | Componentes-chave |
|---|---|---|---|
| `editor/Editor.java` | 71 | Janela principal + painéis | `JFrame`, `JTree` (Hierarchy + Asset Browser), `JSplitPane`, `JScrollPane`, `JMenuBar`, `JPopupMenu`, `JTable`/Inspector, `DefaultTreeModel`, `TreeCellRenderer` |
| `audioeditor/AudioEditorFrame.java` | 22 | DAW | `JPanel`, `JSlider`, `JScrollPane`, `JButton`, faixas customizadas |
| `imageeditor/ImageEditorFrame.java` | 22 | Editor de imagem | `JFrame`, `JPanel`, `JScrollPane`, toolbars |
| `notes/NoteSystemFrame.java` | 22 | Notas/wiki | `JFrame`, `JTextPane`/`JTextArea`, `JTree`/lista |
| `editor/AuxiliaryPanel.java` | 19 | Painéis auxiliares | `JPanel`, `JScrollPane`, dock |
| `community/CommunityFrame.java` | 13 | Marketplace | `JFrame`, `JTabbedPane`, `JButton`, `JOptionPane` |
| `editor/AnimationEditorFrame.java` | 12 | Animação | `JFrame`, timeline customizada |
| `editor/BuildDialog.java` | 12 | Diálogo de build | `JDialog`, `JComboBox`, `JProgressBar` |
| `editor/ScriptEditorWindow.java` | 9 | Editor de código | `JFrame`, `JScrollPane`, `JTextPane` |
| `editor/MarkdownViewerFrame.java` | 9 | Viewer markdown | `JFrame`, `JTextPane`/`JEditorPane` |
| `editor/AutocompleteManager.java` | 8 | Autocomplete | `JPopupMenu`/`JList`, `JWindow` |
| `editor/EditorTextPane.java` | 2 | Componente de texto | `extends JTextPane` (customizado) |
| `core/Game.java` | 1 | **Render** | `extends java.awt.Canvas` + `BufferStrategy` + `Graphics2D` |
| `imageeditor/PaintCanvas.java` | 1 | Canvas de pintura | `extends JComponent` (paint customizado) |

Conclusões:
- A **UI do editor** é Swing; o **render do jogo** é AWT puro (`Canvas`/`Graphics2D`) — são camadas distintas.
- Componentes **customizados** (desenho próprio): `EditorTextPane`, `PaintCanvas`, timeline de animação, faixas do DAW, `core/ui/*` (UI in-game, que **não** é Swing e não migra).

## 3. Interop durante a transição

- **`SwingNode`** (`javafx.embed.swing`): embute um `JComponent` Swing dentro da cena JavaFX (portar telas grandes aos poucos).
- **`JFXPanel`**: embute cena JavaFX dentro de `JFrame` Swing (sentido inverso).
- **`SwingFXUtils`**: `BufferedImage` (AWT) ↔ `WritableImage` (JavaFX) — base da ponte de render.

## 4. Ponte de render do Viewport (decisão-chave)

Hoje: `Game extends Canvas` desenha no `BufferStrategy`.
Alvo: desenhar o frame em `BufferedImage` offscreen (mesmo `Graphics2D`) → `SwingFXUtils.toFXImage(...)` → pintar em `javafx.scene.canvas.Canvas` via `AnimationTimer`. Desacopla o loop da janela e remove o `BufferStrategy` no editor.

> Rejeitado: embutir o `Canvas` AWT pesado direto na cena JavaFX (heavyweight/lightweight quebra foco e render). Pré-requisito: extrair uma camada `Renderer` que desenhe em `BufferedImage` (ver auditoria).

## 5. Mapeamento Swing → JavaFX

| Swing/AWT (atual) | JavaFX (futuro) | Notas |
|---|---|---|
| `JFrame` | `Stage` + `Scene` | janela principal e janelas-ferramenta |
| `JDialog` | `Stage` (modal) / `Dialog` | BuildDialog, prompts |
| `JPanel` | `Pane` / `Region` / `VBox`/`HBox` | layout |
| `JSplitPane` | `SplitPane` | divisórias do editor |
| `JScrollPane` | `ScrollPane` | listas/áreas roláveis |
| `JTabbedPane` | `TabPane` | CommunityFrame, painéis |
| `JTree` + `DefaultTreeModel` | `TreeView<T>` + `TreeItem<T>` | Hierarchy, Asset Browser |
| `TreeCellRenderer` | `TreeCell` / `cellFactory` | ícones por tipo de nó |
| `JTable` + `DefaultTableModel` | `TableView<T>` + `TableColumn` | tabelas/Inspector tabular |
| Inspector (form) | `GridPane` + propriedades observáveis (binding) | `Property<T>`/`bindBidirectional` |
| `JTextArea` | `TextArea` | textos simples |
| `JTextPane` / `JEditorPane` | `TextArea` / **`RichTextFX` (CodeArea)** | editor de código (syntax/highlight) |
| `EditorTextPane` (custom) | `CodeArea` (RichTextFX) | reescrever sobre RichTextFX |
| `JMenuBar` / `JMenu` | `MenuBar` / `Menu` | menus |
| `JPopupMenu` | `ContextMenu` | menus de contexto |
| `JToolBar` | `ToolBar` | barras de ferramenta |
| `JButton` | `Button` | botões |
| `JComboBox` | `ComboBox` | seletores |
| `JCheckBox` | `CheckBox` | flags |
| `JSlider` | `Slider` | DAW, valores |
| `JProgressBar` | `ProgressBar` | build/instalação |
| `JList` | `ListView` | autocomplete, listas |
| `JTextField` | `TextField` | entradas |
| `JFileChooser` | `FileChooser` / `DirectoryChooser` | abrir/salvar |
| `JOptionPane` | `Alert` / `Dialog` | mensagens |
| `JComponent` custom (`PaintCanvas`) | `Canvas` (GraphicsContext) | canvas de pintura |
| `java.awt.Canvas` + `BufferStrategy` (`Game`) | `Canvas` FX via **ponte** (seção 4) | render do jogo |
| Listeners AWT (Mouse/Key) | handlers JavaFX (`setOnMouse*`/`setOnKey*`) | remapear input |
| `SwingUtilities.invokeLater` | `Platform.runLater` | thread de UI |

## 6. Riscos da migração

| Risco | Severidade | Mitigação |
|---|---|---|
| Render acoplado ao AWT (`Game extends Canvas`) | Alta | Ponte de render (seção 4); extrair `Renderer` antes |
| `Editor.java` monolítico (5580 linhas, 71 usos Swing) | Alta | Extrair painéis em classes coesas **antes** de portar |
| Componentes customizados (`EditorTextPane`, `PaintCanvas`, timeline, faixas DAW) | Média-alta | Reescrever sobre `Canvas`/RichTextFX; portar por último |
| Threading (EDT × FX Application Thread × thread do jogo) | Média | Encapsular fronteiras; `Platform.runLater`/`invokeLater` |
| Empacotamento (JavaFX fora da JDK) | Média | `jlink`/`jpackage` ou shading; validar no Builder |
| Performance da cópia de frame por ponte | Média | Reuso de buffer / `PixelBuffer` (JavaFX 13+) |
| Input via listeners AWT no Canvas | Média | Remapear para handlers JavaFX na ponte |
| Atividade concorrente / WIP não commitado no repo | Média | Consolidar WIP antes de mudanças estruturais |

## 7. Estratégias possíveis

### A) Migração Total (big-bang)
- **Prós:** sem código de interop; base final limpa de uma vez.
- **Contras:** editor quebrado por muito tempo; altíssimo risco; difícil revisar/reverter; incompatível com o monolito `Editor.java`. **Não recomendada.**

### B) Migração Gradual (incremental)
- **Prós:** editor sempre funcional; cada fase mergeável/reversível; risco distribuído; permite medir FPS da ponte cedo.
- **Contras:** convivência Swing+JavaFX temporária (SwingNode/JFXPanel); custo de interop transitório.

### C) Arquitetura Híbrida (permanente)
- **Prós:** mantém telas estáveis em Swing indefinidamente; foca JavaFX no que dá mais retorno (shell + viewport).
- **Contras:** dois toolkits para sempre (manutenção dupla, tema inconsistente); dívida arquitetural permanente.

## 8. Recomendação técnica

**Migração Gradual (B)** com uso **temporário** de interop, evoluindo para 100% JavaFX — usando a **Híbrida (C) apenas como estado de transição**, não como destino.

Justificativa: o render acoplado ao AWT e o `Editor.java` monolítico tornam o big-bang inviável; a gradual mantém o editor utilizável, valida a ponte de render cedo (maior risco técnico) e permite reverter por fase. A `Legado` garante o ponto de retorno.

## 9. Fases (ordem recomendada)

- ✅ **F0 — Infra/build (feito):** deps JavaFX 17 (`javafx-controls`, `javafx-graphics`, `javafx-swing`) + `javafx-maven-plugin`; pacote `com.ignis.editor.fx` (Swing intacto). Compila (`mvnw compile`).
- ✅ **F1 — Casca + ponte (feito):** `IgnisEditorApp` (`Application`/`BorderPane`/`MenuBar`/`SplitPane`); ponte de render num `Canvas` FX central (`Game.renderWorldTo` → `SwingFXUtils` → `Canvas` via `AnimationTimer`); Hierarchy já nativa (`TreeView`); Inspector placeholder. Pendente: validar FPS em uso real e ligar o viewport a um projeto/cena carregado (hoje cena de amostra).
- ✅ **F2 — Painéis nativos (feito):** abrir projeto `.ignis` real no viewport (FileChooser → `IgnisProjectIO.load`); Hierarchy `TreeView` com seleção que desenha contorno no viewport (`Game.renderWorldTo` com objeto selecionado); Inspector `GridPane` editável (nome/x/y/largura/altura/rotação/visível) escrevendo no `GameObject` em tempo real. Pendente: ToolBar, atalhos, e Play/Stop no viewport JavaFX.
- **F3 — Janelas-ferramenta:** migrar uma a uma (BuildDialog → Community → Notes → Animation → ImageEditor/PaintCanvas → AudioEditor → editor de código com RichTextFX).
- **F4 — Tema e limpeza:** CSS escuro (substitui cores hardcoded), layout persistido (SplitPane/Stage), remover `javafx-swing`. `runtime/GameRuntime` pode permanecer AWT (menor footprint) — decidir.

## 10. Pré-requisitos (antes de iniciar código JavaFX)

1. Extrair painéis do `Editor.java` em classes coesas.
2. Introduzir camada `Renderer` que desenha em `BufferedImage` (desacopla do toolkit).
3. (Opcional) Testes de serialização/colisão para detectar regressões durante a migração.

## 11. Critérios de sucesso

Paridade funcional com o editor Swing; viewport fluido pela ponte; tema unificado por CSS; zero `javax.swing` no pacote do editor (runtime à parte); Builder continua gerando binários válidos.
