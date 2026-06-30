# Relatório de Novas Funcionalidades e Melhorias no Editor FX (30/06/2026)

Este documento detalha o conjunto de novas funcionalidades e melhorias de usabilidade implementadas no Ignis Engine, abrangendo o suporte a prefabs, painel de console integrado, desfazer (undo) no Inspector de propriedades e suporte inicial a multi-seleção.

> [!NOTE]
> **Autor das Alterações:** ThyagoToledo  
> **Documentador:** Assistente IA Antigravity (Gemini)  
> **Data:** 30 de Junho de 2026  
> **Arquivos Afetados:**  
> - [Game.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/core/Game.java)  
> - [EditorPrefs.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/EditorPrefs.java)  
> - [IgnisEditorApp.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/IgnisEditorApp.java)  
> - [FxConsolePanel.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/FxConsolePanel.java) [NEW]

---

## 1. Prefabs no Editor FX

Os Prefabs funcionam como templates serializáveis (JSON) de GameObjects, permitindo salvar entidades configuradas (com transformações, sprites, colliders, scripts e variáveis customizadas) na pasta `<projeto>/prefabs/*.prefab.json` e reutilizá-las posteriormente.

### Detalhes de Implementação:
- **Gerenciador de Prefabs (`PrefabManager`):** Instanciado de forma lazy na aplicação em conformidade com o diretório ativo do projeto.
- **Salvar como Prefab:** Adicionado ao menu contextual da viewport e da árvore de hierarquia. Apresenta uma caixa de diálogo (`TextInputDialog`) para nomeação do prefab, solicitando confirmação caso o arquivo já exista para evitar sobrescritas acidentais.
- **Instanciar Prefabs:**
  - Através da ação no menu contextual (exibe um `ChoiceDialog` com os prefabs existentes em ordem alfabética).
  - Através do clique duplo em qualquer arquivo `.prefab.json` no navegador de assets.
  - Através do menu contextual de arquivos `.prefab.json` no navegador de assets.
- **Desfazer/Refazer (Undo/Redo):** A instanciação de prefabs registra uma transação no `UndoManager`, permitindo reverter completamente a criação do objeto e remover seus rastros na hierarquia e na seleção do editor.

---

## 2. Console de Erros Integrado

Adicionado um terminal visual dentro do próprio editor para concentrar saídas de log do sistema, execuções de scripts e mensagens de erro do processo de compilação.

### Detalhes de Implementação:
- **Nova Classe [FxConsolePanel.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/FxConsolePanel.java):**
  - Implementa um fluxo "tee" que intercepta as saídas padrão e de erro do sistema (`System.out` e `System.err`) sem interromper a saída nativa no terminal de desenvolvimento.
  - Classifica as linhas de log dinamicamente usando heurística em três níveis de severidade: `INFO` (cor padrão do tema), `WARN` (amarelo/dourado) e `ERROR` (vermelho).
  - Inclui barra de ferramentas com botões alternáveis para filtrar visualmente os níveis de mensagens, contador em tempo real de erros e avisos, caixa de seleção para rolagem automática (Auto-scroll) e opção para limpar o buffer.
- **Interface e Docking:**
  - O console é inserido na janela principal sob um novo `SplitPane` vertical (`centerSplit`), dividindo a viewport e a parte inferior do editor.
  - Adicionado atalho global `Ctrl+J` e uma opção sob o menu *Visualizar > Mostrar Console* para alternar a exibição.
  - A visibilidade do console é mantida e carregada dinamicamente através da persistência do `EditorPrefs` (chave `consoleVisible`).

---

## 3. Desfazer (Undo) na Edição de Campos do Inspector

Anteriormente, alterações feitas por digitação direta nos campos do Inspector (como X, Y, Largura, Altura, Rotação e Nome) não podiam ser desfeitas, e qualquer evento de alteração ao vivo poluía o histórico do `UndoManager`.

### Detalhes de Implementação:
- **Edição Transacional por Foco (`wireUndoableField`):**
  - Quando um campo de texto ganha foco, o editor captura o valor atual do GameObject como ponto de partida (`beginInspectorEdit`).
  - Quando o campo perde o foco (`focusedProperty` passa a falso) ou quando o usuário pressiona a tecla `Enter`, o editor compara o valor atual com o ponto de partida (`commitInspectorEdit`).
  - Se houver alteração real de valor, uma única transação descritiva (ex: "Editar Nome", "Editar X") é empilhada no `UndoManager`. Os listeners de texto continuam atualizando o viewport em tempo real ao digitar, garantindo feedback visual fluido sem sobrecarregar o histórico de Undo.
- **Undo em Controles Discretos:**
  - Alterações no estado da checkbox "Visível" geram transações imediatas no `UndoManager` por não envolverem digitação contínua.

---

## 4. Multi-seleção no Editor FX (Em Progresso)

Iniciados os preparativos arquiteturais para suportar a seleção simultânea de múltiplos objetos no editor.

### Detalhes de Implementação:
- **Suporte no Core da Engine (`Game.java`):**
  - Adicionada a lista privada `editorHighlights` e o método síncrono `setEditorHighlights(List<GameObject>)`.
  - No método de desenho da cena (`renderWorldTo`), caso o estado atual do jogo seja `GameState.EDITING`, cada entidade secundária contida na lista de realces é desenhada com uma borda pontilhada laranja (`Color(255, 160, 40)`), sem exibir alças de redimensionamento ou gizmos de translação ativos.
  - A manipulação de gizmos e arraste lógico de coordenadas permanece restrita à entidade selecionada principal (`selectedObject`), mantendo a integridade física e de simulação da cena.

---

## 5. Resumo das Modificações nos Arquivos

### [Game.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/core/Game.java)
- Adicionado campo `editorHighlights` para armazenar a lista secundária de seleção.
- Adicionado método `setEditorHighlights(List<GameObject> objs)`.
- Adicionado loop de desenho tracejado na cor laranja para elementos em `editorHighlights` dentro de `renderWorldTo()`.

### [EditorPrefs.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/EditorPrefs.java)
- Adicionados métodos `isConsoleVisible()` e `setConsoleVisible(boolean)` para salvar e ler a visibilidade do dock de console no arquivo de preferências do editor.

### [IgnisEditorApp.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/IgnisEditorApp.java)
- Instanciado `FxConsolePanel` na inicialização, registrando a captura dos streams do sistema e adicionando o componente no dock inferior caso configurado.
- Adicionada ação de toggle do console no menu *Visualizar* com atalho `Ctrl+J`.
- Adicionado suporte a `PrefabManager` e implementados os métodos `saveSelectedAsPrefab()`, `instantiatePrefabDialog()` e `instantiatePrefabByName(String)`.
- Vinculado duplo clique em arquivos `.prefab.json` e ação contextual no navegador de arquivos à instanciação de prefabs.
- Criados métodos `wireUndoableField()`, `beginInspectorEdit()`, `commitInspectorEdit()` e `applyInspectorUndo()` no painel do Inspector para implementar o histórico de Undo transacional por foco.
