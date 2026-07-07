# Status do Editor JavaFX (JavaFX Editor Status)

> Visão geral do progresso de desenvolvimento e status da migração da interface visual do editor (Swing → JavaFX) no IgnisEngine.

---

## 1. Visão Geral da Migração

A migração da interface de usuário (UI) do editor visual do IgnisEngine foi dividida em etapas incrementais na branch `main` com o objetivo de substituir a interface clássica em Swing por uma casca nativa em **JavaFX 17**, sem a necessidade de reescrever o núcleo do motor (core).

### Status de Progresso das Fases:
- **`F0` - Infraestrutura:** Adição de dependências JavaFX ao `pom.xml`, configuração do `javafx-maven-plugin` e estrutura de pacotes. **[CONCLUÍDO]**
- **`F1` - Casca e Ponte de Render:** Substituição do JFrame principal, inicialização da thread do JavaFX e criação do viewport de renderização utilizando `SwingFXUtils`. **[CONCLUÍDO]**
- **`F2` - Painéis Principais:** Criação dos painéis nativos de Hierarchy (TreeView nativo), Inspector (formulários reativos de propriedades) e Asset Browser (navegador de arquivos). **[CONCLUÍDO]**
- **`F3` - Janelas de Ferramentas:** Migração e conexão de sub-editores integrados aos menus principais. **[CONCLUÍDO]**
- **`F3.5` - Roteamento de Entrada e Gerenciamento:** Tela de boas-vindas / recentes, operações de arquivo (salvar/abrir/fechar), reordenação e manipulação de entidades e captura fluida de eventos de teclado/mouse para o jogo. **[CONCLUÍDO]**
- **`F4` - Tema CSS e Limpeza (Próximo Passo):** Criação de temas escuros/claros unificados via CSS modular, aplicação de SplitPanes flexíveis na área de trabalho e remoção de componentes órfãos da ponte de renderização antiga. **[PLANEJADO / EM ANDAMENTO]**

---

## 2. Paridade de Features (Tabela de Status)

A tabela abaixo exibe a paridade atual do Editor JavaFX em relação ao Editor Swing clássico:

| Categoria | Funcionalidade | Status no JavaFX | Observação |
|---|---|---|---|
| **Cenas e Projetos** | FxProjectStartupDialog (Novos/Recentes) | **Funcional** | Tela inicial moderna de gerenciamento de projetos. |
| | Salvar, Abrir e Salvar Como | **Funcional** | Integração total com `IgnisProjectIO`. |
| **Hierarquia** | Hierarchy TreeView | **Funcional** | Exibição de nós de cena e seleção ativa. |
| | Criar, Duplicar, Deletar e Renomear | **Funcional** | Ações via menus de contexto (ContextMenu). |
| | Reordenar com Drag-and-Drop | **Pendente** | Ordenação manual de camadas arrastando nós. |
| **Inspetor** | Edição de Transformações e Propriedades | **Funcional** | Vinculado a campos de texto numéricos reativos. |
| | Listagem e Vinculação de Scripts | **Parcial** | Listagem de scripts ativa; vinculação via menu de contexto. |
| **Sub-Editores** | FxCodeEditor (Editor de Código) | **Funcional** | Code editor nativo com destaque e autocomplete. |
| | FxImageEditor (Editor de Sprites) | **Funcional** | Paint canvas nativo em JavaFX com camadas. |
| | FxAudioEditor (DAW) | **Funcional** | Timeline, equalizador e forma de onda nativos. |
| | FxAnimationEditor (Animações) | **Funcional** | Editor de quadros nativo JavaFX. |
| | FxCommunityWindow (Marketplace) | **Funcional** | Integração e download 1-click de plugins. |
| **Simulação** | Play / Stop / Pause | **Funcional** | Botões e mapeamento de estados da Game Thread. |
| | Roteamento de Teclado e Mouse | **Funcional** | Eventos capturados da janela JavaFX e enviados à Game Thread. |

---

## 2.1. Correções e Melhorias — 2026-06-15

Rodada de auditoria + correção de bugs de UX reportados, com revisão adversarial. Tudo compila (`mvnw compile` BUILD SUCCESS, 86 fontes). **Pendente de validação manual em GUI.**

### Seleção, picking e clique direito (`IgnisEditorApp.wireFxInputToEngine`, `Game.getObjectAt`/`handleMousePress`/`renderWorldTo`)
- **Inspector agora atualiza ao clicar no viewport.** Causa-raiz: `selectEntity()` delegava a escrita do Inspector ao listener da árvore, que abortava por `suppressSelectionEvents`. Agora `selectEntity` é a **fonte única** e chama `setSelected()` sempre (Hierarchy vira só efeito visual).
- **Clique direito** não aciona mais seleção/drag do engine — o botão `SECONDARY` não é encaminhado a `game.dispatchEvent`; o menu de contexto é tratado só em `setOnContextMenuRequested`. O `Input` (scripts em Play) continua recebendo todos os botões.
- **Seleção duplicada eliminada:** removido o `setOnMouseClicked` que selecionava em paralelo ao engine (`handleMousePress`). O core é a fonte única no botão esquerdo, propagando via `selectionListener`.
- **Seleção de objetos sobrepostos:** `getObjectAt(x,y,afterCurrent)` coleta todos os objetos sob o ponto e **cicla** entre os empilhados a cada clique; o hit-test passou a respeitar a **rotação** do objeto (antes era AABB não-rotacionado).
- **Indicador visual restaurado:** `renderWorldTo` desenha borda **tracejada** + 4 **alças de canto** (espelha o `renderSelection` do editor Swing), no lugar do retângulo verde fino.
- **Regressão corrigida (review):** clicar-e-arrastar um objeto não-selecionado num único gesto não cancela mais o drag (`setSelected` só chama `cancelDrag` quando a seleção **não** veio do próprio engine).

### Editor de Scripts (`FxCodeEditor`)
- **Números de linha sempre pretos** sobre gutter claro fixo (`#d8d8d8`), independente do tema — forçado por CSS `!important` **e** por estilo inline na `paragraphGraphicFactory` (vence o `.lineno{#666}` default do RichTextFX). Antes usavam o foreground do tema com 50% de alpha e sumiam em temas escuros.

### Auto Save (`EditorPrefs`, `FxCodeEditor`, `IgnisEditorApp`)
- **`☑ Auto Save` no menu Arquivo** (persistido em `EditorPrefs`, `~/.ignis/editor-prefs.json`).
- **Scripts:** autosave religado (estava morto — dependia de `editor != null`); agora lê `EditorPrefs.isAutoSave()`, com intervalo configurável e save on-blur/on-close; só salva quando há mudança (`modified`).
- **Projeto:** novo autosave com *dirty-flag* (`markProjectDirty` em criar/duplicar/deletar/renomear/reordenar/editar-Inspector + fim de drag via `TransformListener`); só salva com projeto aberto, sujo e **fora do Play**; usa caminho silencioso (sem `Alert` modal a cada intervalo).

## 2.2. Roadmap priorizado (levantamento de paridade)

**Faltando — alta prioridade (destravam o uso real):**
1. **Undo/Redo** de cena (Ctrl+Z/Y) — modelo de Command (o core já tem `TransformListener`).
2. **Inspector completo** — seções Cor/Aparência, Sprite, Collider, Câmera, Scripts (hoje só nome/x/y/w/h/rot/visível).
3. **Scripts em objetos** — anexar/criar/remover script + editar variáveis `@public` (GameObject picker com pick-mode no viewport).
4. **Import de imagem → sprite** no objeto (copiar para `assets/sprites`, caminho relativo).
5. **Prefabs** (salvar/instanciar) — core já tem `PrefabManager`.

**Backlog médio:** multi-seleção, merge de objetos, drag-and-drop na Hierarchy/Asset Browser, asset context menu (New Folder/Compile), persistência de layout (dividers), Markdown viewer FX, copiar/colar entre cenas.

**Estratégico (faltam em ambos os editores):** painel de Console/erros de compilação, multi-cena no UI, parentesco/aninhamento na hierarquia, multi-aba.

**Polimento pendente:** tema escuro CSS global unificado (`ignis-dark.css` substituindo ~96 `setStyle` inline) — amplo, requer validação visual; tirar `updateInspectorFields()` do loop 60fps; parar/pausar o `AnimationTimer` da ponte de render.

---

## 3. A Ponte de Renderização (`SwingFXUtils`)

A cena do jogo é renderizada no motor clássico `AWT/Graphics2D` em uma thread de simulação em segundo plano. 
Para exibir essa viewport de forma fluida no editor moderno JavaFX, utiliza-se uma ponte de buffers:

1. O loop principal do jogo desenha as entidades e o grid do editor em uma `BufferedImage` na memória física do computador.
2. A thread de UI do JavaFX executa periodicamente um ciclo de atualização na classe `IgnisEditorApp` que captura a `BufferedImage` do motor de jogo.
3. Converte a imagem usando `SwingFXUtils.toFXImage(bufferedImage, writableImage)`.
4. Atualiza o elemento visual `ImageView` exibido no centro do editor.

### Limitações da Ponte de Render:
- **Sobrecarga de Conversão (Overhead):** Copiar e converter arrays de pixels AWT para texturas nativas do JavaFX na thread da UI a cada frame pode sobrecarregar a CPU em resoluções altas (como 4K), limitando o FPS.
- **Sincronização de Threads:** Requer sincronismo fino contra concorrência para evitar que a thread de UI tente ler a imagem enquanto a thread do jogo a está modificando, gerando flickering (piscadas na tela) ou quebras de memória.

---

## 4. Como Executar e Testar

### Editor Moderno (JavaFX) - Recomendado
```bash
# Windows
run-editor-javafx.bat

# Outros Sistemas
./mvnw javafx:run
```

### Editor Clássico (Swing) - Fallback
Removido da branch `main` em 05/07/2026; disponível apenas na branch `Legado` (o `exec:java` foi retirado do `pom`).
