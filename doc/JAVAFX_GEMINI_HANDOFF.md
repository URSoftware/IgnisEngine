# Handoff — Migração JavaFX (item 2 da Fase 3) · responsável: Gemini

> **STATUS 2026-06-15 — FASE 3 CONCLUÍDA E INTEGRADA.** As 6 telas-ferramenta foram migradas para JavaFX (`FxCommunityWindow`, `FxNotesWindow`, `FxAnimationEditor`, `FxImageEditor`/`FxPaintCanvas`, `FxAudioEditor`, `FxCodeEditor`) e **todas estão ligadas ao menu Ferramentas** da casca `IgnisEditorApp` — incluindo o `FxCodeEditor`, o último a ser fiado. Além disso, o **input de teclado/mouse do jogo passou a ser roteado para o viewport FX** (`IgnisEditorApp.wireFxInputToEngine`, aditivo, via callbacks AWT do `Input`). A casca JavaFX não usa mais nenhum fallback Swing. Detalhes em [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md) (Fase 3). Próxima fase: **F4** (tema CSS, layout persistido; remover `javafx-swing` só após desacoplar o `Renderer`).

> Divisão da Fase 3 da migração Swing→JavaFX (branch `main`):
> - **Claude:** passo 1 (`BuildDialog` nativo) + passo 3 (ToolBar, atalhos, Play/Stop).
> - **Gemini (este documento):** item 2 — migrar nativamente as telas-ferramenta listadas abaixo.
>
> Contexto completo: [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).

## 1. Antes de tudo — leia o contexto (Check Local First)

Vault pessoal (regras + conhecimento):
- `C:\Users\vinic\OneDrive\Desktop\vault\doc\00_MOC.md`
- vault `concepts/ignisengine-javafx-migracao.md` (plano + estado das fases)
- vault `concepts/ignisengine-decisoes-arquiteturais.md`
- vault `concepts/karpathy-llm-wiki.md` (como navegar o vault economizando tokens)

Documentação técnica no projeto (`doc/`):
- **JAVAFX_MIGRATION_PLAN.md** — OBRIGATÓRIO. Tabela de mapeamento Swing→JavaFX (seção 5), riscos (6), estratégia (7-8), fases (9).
- ARCHITECTURE.md (fluxos, ciclo de vida, threading EDT × FX).
- PROJECT_INVENTORY.md (estado de cada sistema).
- ARCHITECTURE_AUDIT.md (dívidas, componentes customizados).

Código JavaFX JÁ PRONTO (use como REFERÊNCIA de padrão; NÃO altere):
- `src/com/ignis/editor/fx/IgnisEditorApp.java` (casca + Hierarchy + Inspector + ponte de render).
- `src/com/ignis/core/Game.java` → `renderWorldTo(...)` (ponte BufferedImage→Canvas).
- `pom.xml` (deps JavaFX 17 + javafx-maven-plugin já configurados).

## 2. SEU ESCOPO (item 2) — migrar nativamente para JavaFX

Leia a classe Swing existente e reproduza comportamento/recursos em JavaFX:

| Tela | Arquivos Swing atuais |
|---|---|
| Comunidade/Marketplace | `community/CommunityFrame.java` |
| Sistema de Notas | `notes/NoteSystemFrame.java` |
| Editor de Animação | `editor/AnimationEditorFrame.java` |
| Editor de Imagens | `imageeditor/ImageEditorFrame.java`, `PaintCanvas.java`, `ImageDocument.java` |
| Editor de Áudio (DAW) | `audioeditor/AudioEditorFrame.java`, `WavAudioProcessor.java` |
| Editor de Código | `editor/ScriptEditorWindow.java`, `EditorTextPane.java`, `AutocompleteManager.java` → usar **RichTextFX (CodeArea)** |

**NÃO É SEU (é do Claude — não toque):** `BuildDialog`; a casca `IgnisEditorApp` (viewport, Hierarchy, Inspector, menu); ToolBar; atalhos; Play/Stop; `Game.renderWorldTo`; `pom.xml` (exceto adicionar a dependência do RichTextFX, que é aditiva).

## 3. Como migrar (padrão técnico)

- Cada tela vira uma **classe NOVA** em `com.ignis.editor.fx` (ex.: `FxCommunityWindow`, `FxNotesWindow`, `FxAnimationEditor`, `FxImageEditor`, `FxAudioEditor`, `FxCodeEditor`), estendendo `Stage` (ou `Dialog` quando fizer sentido). **NÃO modifique as classes Swing originais** — elas continuam existindo (Legado + interop atual dependem delas).
- Mapeamento (ver tabela no plano): `JFrame→Stage`, `JDialog→Dialog/Stage`, `JPanel→Pane/VBox/HBox`, `JTabbedPane→TabPane`, `JTree→TreeView`, `JTable→TableView`, `JTextArea/JTextPane→TextArea/CodeArea`, `JButton→Button`, `JSlider→Slider`, `JFileChooser→FileChooser`, `JComponent` custom (`PaintCanvas`)→`javafx.scene.canvas.Canvas`, `JOptionPane→Alert/Dialog`.
- **Reaproveite a LÓGICA, não a UI**: chame as mesmas classes de domínio/IO (`ImageDocument`, `WavAudioProcessor`, `ScriptManager`, `MarketplaceClient`, etc.). Só a camada visual muda.
- **Threading**: nada de Swing nas telas novas. Toda UI no FX Application Thread; trabalho pesado em background + `Platform.runLater`.
- Pintura/desenho (`PaintCanvas`): use `Canvas` + `GraphicsContext` do JavaFX (não `BufferedImage`), salvo se precisar reusar render existente — aí siga o padrão de `renderWorldTo`.
- Construtores atuais (replicar dependências): `AudioEditorFrame()`; `ImageEditorFrame(File exportFolder)`; `CommunityFrame(File projectFolder)`; `NoteSystemFrame(File projectFolder, AIIntegration ai)`; `AnimationEditorFrame(File projectFolder, File spritesFolder, GameObject target)`.

## 4. Regras rígidas (NÃO violar)

- Trabalhe SOMENTE na branch **`main`**. NUNCA toque na **`Legado`** (versão Swing estável; deve ficar intacta).
- NÃO reescreva o núcleo da engine (`com.ignis.core`) — só a camada de UI.
- NÃO edite `IgnisEditorApp.java` (é do Claude). Exponha em cada tela nova um ponto de entrada simples (construtor + `show()`, ou `static open(...)`) e **entregue um trecho de "como abrir"** para o Claude ligar no menu Ferramentas.
- Git: commits SOMENTE com o perfil **ThyagoToledo** (thyago10a2007@gmail.com). **NUNCA** adicione `Co-Authored-By` nem qualquer atribuição a IA. 1 tela = 1 commit. Push em `origin/main`. Conventional commits.
- Antes de cada commit: `mvnw -o compile` deve dar **BUILD SUCCESS**.
- Ao terminar cada tela: atualize `doc/JAVAFX_MIGRATION_PLAN.md` (marque feita) e a nota do vault `concepts/ignisengine-javafx-migracao.md`. Gits do projeto e do vault são **separados** — nunca misture.

## 5. Verificação

- Rodar: `mvnw javafx:run` (mainClass `com.ignis.editor.fx.IgnisEditorApp`).
- Sem testar GUI? Garanta a compilação e descreva o teste manual para o usuário validar.

## 6. Ordem sugerida (da mais simples à mais complexa)

CommunityFrame → NoteSystemFrame → AnimationEditorFrame → ImageEditorFrame(+PaintCanvas) → AudioEditorFrame → Editor de Código (RichTextFX).

Comece lendo `JAVAFX_MIGRATION_PLAN.md` e `IgnisEditorApp.java` para absorver o padrão; depois migre `CommunityFrame` e siga a ordem.
