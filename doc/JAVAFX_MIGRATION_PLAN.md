# Plano de Migração da Interface do Editor: Swing/AWT → JavaFX

> Status: planejado · Java alvo: 17 LTS · JavaFX alvo: 17 LTS
> Documento de planejamento. Nenhuma migração foi executada ainda.

## 1. Objetivo

Migrar a interface do editor IgnisEngine de **Swing/AWT** para **JavaFX**, ganhando:
estilização via CSS, layout responsivo, propriedades observáveis (binding),
animações nativas e uma base de UI moderna — **sem reescrever o núcleo da engine**.

## 2. Situação atual (levantamento)

- ~27.780 linhas de Java em 70 arquivos; **38 arquivos usam `javax.swing`/`java.awt`**.
- Monolito de UI: `editor/Editor.java` (~5.962 linhas) — janela principal, docking, Hierarchy/Viewport/Inspector, menus.
- Janelas auxiliares Swing: `audioeditor/AudioEditorFrame` (763), `notes/NoteSystemFrame` (474), `imageeditor/ImageEditorFrame` (471) + `PaintCanvas` (438), `editor/AnimationEditorFrame` (363), `community/CommunityFrame` (334), `editor/BuildDialog` (221), `editor/AuxiliaryPanel` (829).
- **Núcleo de render acoplado a AWT**: `core/Game.java` faz `extends java.awt.Canvas` e desenha via `BufferStrategy` + `Graphics2D`. `runtime/GameRuntime` (212) usa o mesmo caminho.
- Persistência de layout: `editor_layout.json` (preferências do editor).

### Implicação central

A UI (Swing) e o **render do jogo** (AWT Canvas/Graphics2D) são coisas separadas.
Podemos trocar a casca de UI por JavaFX mantendo o render Graphics2D, via uma
**ponte de frame** (render offscreen → imagem → Canvas JavaFX). Reescrever o
pipeline gráfico NÃO faz parte desta migração.

## 3. Estratégia: incremental com ponte (não big-bang)

Princípio: o editor permanece funcional em todas as fases. JavaFX e Swing convivem
durante a transição usando dois mecanismos de interop:

- **`SwingNode`** (pacote `javafx.embed.swing`): embute um `JComponent` Swing existente
  dentro da cena JavaFX. Usado para portar telas grandes aos poucos.
- **`SwingFXUtils`**: converte `BufferedImage` (AWT) ↔ `WritableImage` (JavaFX).
  Base da ponte de render do Viewport.
- **`JFXPanel`**: embute uma cena JavaFX dentro de um `JFrame` Swing. Usado no
  sentido inverso, se precisarmos abrir uma tela JavaFX nova antes de migrar o shell.

### Ponte de render do Viewport (decisão-chave)

Hoje: `Game extends Canvas` desenha direto no `BufferStrategy`.
Alvo: desenhar o frame em um `BufferedImage` offscreen (mesmo `Graphics2D`),
converter com `SwingFXUtils.toFXImage(...)` e pintar em um `javafx.scene.canvas.Canvas`
no thread do JavaFX (`AnimationTimer`). Isso desacopla o loop do jogo da janela e
elimina a dependência do `BufferStrategy` no editor.

> Alternativa rejeitada: embutir o `Canvas` AWT pesado direto na cena JavaFX —
> mistura de componentes heavyweight/lightweight causa artefatos e problemas de foco.

## 4. Fases

### Fase 0 — Infra e build (sem mudança visual)
- Adicionar dependências JavaFX 17 ao `pom.xml`: `javafx-controls`, `javafx-graphics`, `javafx-fxml` (opcional), `javafx-swing` (SwingNode/JFXPanel/SwingFXUtils).
- Configurar `javafx-maven-plugin` (goal `run`) e/ou empacotamento com módulos.
- Criar pacote novo `com.ignis.editor.fx` (mantém o Swing atual intacto em paralelo).
- Critério de aceite: `mvn compile` ok; app Swing continua rodando igual.

### Fase 1 — Casca JavaFX + Viewport por ponte
- Nova `IgnisEditorApp extends javafx.application.Application` com `BorderPane`
  (MenuBar topo, painéis laterais, centro = Viewport).
- Implementar a **ponte de render** (seção 3) num `Canvas` JavaFX central.
- Hierarchy/Inspector ainda em Swing, embutidos via `SwingNode` (temporário).
- Critério de aceite: editar e ver a cena renderizando dentro da janela JavaFX.

### Fase 2 — Painéis principais nativos
- Reescrever **Hierarchy** como `TreeView<GameObject>` (binding ao modelo de cena).
- Reescrever **Inspector** com `PropertySheet`/`GridPane` + propriedades observáveis.
- Toolbar/menus nativos (`MenuBar`, `ToolBar`), atalhos via `KeyCombination`.
- Remover os `SwingNode` desses painéis.

### Fase 3 — Janelas-ferramenta
Migrar uma a uma (cada uma é uma `Stage` independente, baixo acoplamento):
`BuildDialog` → `CommunityFrame` → `NoteSystemFrame` → `AnimationEditorFrame` →
`ImageEditorFrame`/`PaintCanvas` → `AudioEditorFrame`. Ordem por complexidade crescente.

### Fase 4 — Estilo, layout e limpeza
- Tema escuro em CSS JavaFX (substitui as cores hardcoded `new Color(45,45,45)` etc.).
- Persistência de layout migrada de `editor_layout.json` para o modelo JavaFX
  (divisores de `SplitPane`, tamanho/posição da `Stage`).
- Remover `javafx-swing`/SwingNode quando não houver mais Swing no editor.
- `runtime/GameRuntime`: decidir se o runtime distribuído também adota a ponte
  JavaFX ou mantém AWT puro (runtime pode continuar AWT — menor footprint).

## 5. Riscos e mitigação

| Risco | Mitigação |
|-------|-----------|
| Threading: regra do FX Application Thread vs EDT do Swing | `Platform.runLater` / `SwingUtilities.invokeLater` nas fronteiras; encapsular |
| Performance da ponte de render (cópia de imagem por frame) | Reutilizar buffers; medir FPS; `PixelBuffer` (JavaFX 13+) se necessário |
| Empacotamento (JavaFX não vem na JDK) | jlink/jpackage ou shading; testar no Builder multiplataforma |
| `Editor.java` monolítico | Extrair em componentes antes de portar (refactor preparatório) |
| Input do jogo hoje via listeners AWT no Canvas | Mapear eventos do `Canvas` JavaFX para o `Input` da engine na ponte |

## 6. Impacto no `pom.xml` (Fase 0, esboço)

```xml
<properties>
  <javafx.version>17.0.12</javafx.version>
</properties>
<dependencies>
  <dependency><groupId>org.openjfx</groupId><artifactId>javafx-controls</artifactId><version>${javafx.version}</version></dependency>
  <dependency><groupId>org.openjfx</groupId><artifactId>javafx-graphics</artifactId><version>${javafx.version}</version></dependency>
  <dependency><groupId>org.openjfx</groupId><artifactId>javafx-swing</artifactId><version>${javafx.version}</version></dependency>
</dependencies>
```
Plugin `org.openjfx:javafx-maven-plugin` com `mainClass=com.ignis.editor.fx.IgnisEditorApp`.

## 7. Critérios de sucesso da migração

- Paridade funcional com o editor Swing atual.
- Render do Viewport fluido pela ponte (sem regressão de FPS perceptível).
- Tema visual unificado por CSS.
- Zero dependência de `javax.swing` no pacote do editor (runtime à parte).
- Build multiplataforma do Builder continua gerando binários válidos.

## 8. Ordem de execução recomendada

Fase 0 → Fase 1 (prova de conceito da ponte) → validar FPS → Fase 2 → Fase 3
(janela por janela) → Fase 4. Cada fase é mergeável e reversível.
