# Relatório de Correções e Otimizações no Editor JavaFX (16/06/2026)

Este documento registra detalhadamente as correções de bugs e otimizações de performance realizadas no editor JavaFX do Ignis Engine, especificando as alterações e o impacto no comportamento da ferramenta.

> [!IMPORTANT]
> **Autor:** Assistente IA Antigravity (Gemini)  
> **Data:** 16 de Junho de 2026  
> **Arquivos Modificados:**  
> - [Game.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/core/Game.java)  
> - [IgnisEditorApp.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/IgnisEditorApp.java)

---

## 1. Problemas Resolvidos e Causa Raiz

### 1.1. Loop de Seleção Infinita no Inspector
- **Sintomas:** Ao clicar com o botão esquerdo na Viewport fora de um menu contextual, ou ao ter objetos muito próximos/sobrepostos na cena, o Inspector de Propriedades entrava em um ciclo contínuo de atualizações rápidas, alternando indefinidamente a seleção entre diferentes entidades (ex: Player, Mapa, Círculo).
- **Causa Raiz:** O selection listener adicionado ao `Game` utilizava `Platform.runLater` para despachar eventos de seleção para a thread do JavaFX. Caso múltiplos eventos fossem disparados em rápida sucessão, as lambdas eram empilhadas e executadas com o valor desatualizado de `selected`, causando um ping-pong de seleção circular infinita.
- **Resolução:** Adicionada uma verificação de consistência: a alteração de seleção só é efetuada no thread do JavaFX se a entidade alvo (`go`) for exatamente a mesma que a engine atualmente considera como selecionada (`game.getSelectedObject()`). Notificações obsoletas ou enfileiradas incorretamente são agora sumariamente descartadas.

### 1.2. Arrastes Presos de Gizmos e Saltos de Coordenadas
- **Sintomas:** Após abrir o menu contextual da Viewport com o botão direito e clicar com o botão esquerdo fora dele (fechando o menu), a viewport assumia um comportamento instável, com saltos bruscos nas coordenadas dos objetos ao mover o mouse.
- **Causa Raiz:** O estado interno de arraste do mouse (`currentDragMode` no `Game.java`) não era devidamente resetado ao abrir ou dispensar o menu de contexto. Isso deixava o editor preso no modo `GizmoDragMode.CENTER`, de modo que qualquer movimento subsequente do mouse interpretava a ação como arraste de entidade.
- **Resolução:** Adicionado um listener de fechamento no menu de contexto (`viewportMenu.setOnHidden`) que chama explicitamente `game.cancelDrag()`. Isso garante que todo e qualquer estado de arraste residual de gizmos ou translação de câmera seja cancelado imediatamente ao dispensar o HUD.

### 1.3. Ordenação Invertida de Elementos na Cena
- **Sintomas:** As ações "Mover para o topo" e "Mover para o fundo" nos menus contextuais operavam de forma oposta à esperada: "Mover para o topo" mandava a entidade para trás (fundo da cena) e "Mover para o fundo" mandava para frente (topo).
- **Causa Raiz:** O renderizador do Ignis Engine (`Game.java`) desenha a lista de entidades em ordem crescente: índices iniciais (0) são renderizados por primeiro (ficando ao fundo), enquanto índices finais (N) são renderizados por último (ficando no topo/frente). No entanto, o `IgnisEditorApp` atribuía incorretamente o índice `0` para "Mover para o topo" e `Integer.MAX_VALUE` para "Mover para o fundo".
- **Resolução:** Invertidos os parâmetros de ordenação nos menus contextuais (`buildSceneMenu` e `buildHierarchyContextMenu`):
  - "Mover para o topo" -> Mover para `Integer.MAX_VALUE` (clamp automático para o fim da lista).
  - "Mover para o fundo" -> Mover para `0` (primeiro elemento da lista).

### 1.4. Comportamento Inativo de Mover para Cima (moveEntityUp)
- **Sintomas:** Ao tentar mover uma entidade um nível acima ("Mover para cima") na hierarquia, a ação era um no-op, mantendo a entidade no mesmo índice de renderização.
- **Causa Raiz:** No método `Game.moveEntityToIndex()`, havia uma lógica que decrementava o novo índice caso ele fosse maior que o índice atual (`if (newIndex > currentIndex) { newIndex--; }`). Ao mover uma entidade um nível acima (de `i` para `i + 1`), a verificação ativava e alterava o destino de volta para `i`, cancelando a movimentação.
- **Resolução:** Removido o decremento redundante em `Game.moveEntityToIndex()`. O clamp de limite inferior e superior que é aplicado em seguida já resolve com segurança a consistência dos índices da lista de entidades após a remoção temporária do objeto.

### 1.5. Clique Direito Não Selecionava Itens na Hierarchy Tree
- **Sintomas:** Ao clicar com o botão direito em um item da árvore de hierarquia para abrir seu menu de contexto, as ações atuavam sobre o elemento que havia sido selecionado anteriormente com o botão esquerdo, e não sobre o objeto que estava sob o cursor do botão direito.
- **Causa Raiz:** Por padrão, a classe `TreeView` do JavaFX apenas atualiza sua seleção com o botão esquerdo do mouse (`PRIMARY`). O clique com o botão direito (`SECONDARY`) apenas exibe o menu contextual sem alterar a seleção interna do controle.
- **Resolução:** Implementada uma `setCellFactory` customizada na TreeView da hierarquia em `IgnisEditorApp.java`. Essa fábrica de células intercepta eventos de clique com o botão direito e seleciona explicitamente o item da célula correspondente no modelo de seleção da árvore antes da exibição do menu de contexto.

### 1.6. Desperdício de Overhead com Repaints AWT
- **Sintomas:** Sob carga pesada de movimentação de entidades, o editor apresentava micro-travamentos e consumo de CPU desnecessariamente alto.
- **Causa Raiz:** O método `repaint()` herdado do AWT `Canvas` em `Game.java` era invocado em diversas operações de atualização (como alteração de cursor ou atualização de seleção). Sob o editor JavaFX, o renderizador opera através de um `AnimationTimer` que chama `renderWorldTo()` a 60 FPS de forma assíncrona. As chamadas a `repaint()` disparavam o pipeline de renderização AWT/Swing redundante em segundo plano, gerando processamento inútil.
- **Resolução:** Introduzida a flag `suppressAwtRepaint` e o método `setSuppressAwtRepaint(boolean)` em `Game.java`. Quando ativada, ela faz com que chamadas a `repaint()` sejam ignoradas. Essa flag é ativada em `IgnisEditorApp.java` logo após a inicialização da engine.

---

## 2. Detalhamento Técnico das Modificações

### 2.1. Alterações em [Game.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/core/Game.java)

- **Supressão de Repaints AWT:**
  ```java
  private boolean suppressAwtRepaint = false;

  public void setSuppressAwtRepaint(boolean suppress) {
      this.suppressAwtRepaint = suppress;
  }

  @Override
  public void repaint() {
      if (suppressAwtRepaint) return;
      super.repaint();
  }
  ```

- **Otimização no handleMouseRelease:**
  Remoção da chamada redundante a `notifySelectionListeners()` no término de arrastes. Isso evita filas desnecessárias de lambdas em threads UI.

- **Correção em moveEntityToIndex:**
  ```diff
  - if (newIndex > currentIndex) {
  -     newIndex--;
  - }
  + // Nenhum ajuste de indice: newIndex indica a posicao FINAL desejada na lista
  + // resultante. O clamp abaixo garante que o valor fique no intervalo valido
  + // apos a remocao.
  ```

### 2.2. Alterações em [IgnisEditorApp.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEditorApp.java)

- **Ativação da supressão de Repaints:**
  ```java
  game.setSuppressAwtRepaint(true);
  ```

- **Guarda contra Seleção circular no Listener:**
  ```java
  game.addSelectionListener(go -> {
      Platform.runLater(() -> {
          if (suppressSelectionEvents) return;
          if (game.getSelectedObject() == go && selected != go) {
              selectEntity(go);
          }
      });
  });
  ```

- **Correção da inversão de índices de ordenação:**
  ```java
  MenuItem top = new MenuItem("Mover para o topo");
  top.setOnAction(e -> moveSelectedTo(Integer.MAX_VALUE));
  MenuItem bottom = new MenuItem("Mover para o fundo");
  bottom.setOnAction(e -> moveSelectedTo(0));
  ```

- **Reset de arrastes na ocultação do menu de contexto:**
  ```java
  viewportMenu.setOnHidden(e -> game.cancelDrag());
  ```

- **Cell Factory para seleção no clique secundário na Hierarquia:**
  ```java
  tree.setCellFactory(tv -> {
      javafx.scene.control.TreeCell<String> cell = new javafx.scene.control.TreeCell<>() {
          @Override protected void updateItem(String item, boolean empty) {
              super.updateItem(item, empty);
              setText(empty ? null : item);
          }
      };
      cell.setOnMousePressed(e -> {
          if (!cell.isEmpty() && e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
              tv.getSelectionModel().select(cell.getTreeItem());
          }
      });
      return cell;
  });
  ```

---

## 3. Verificação de Integridade e Compilação

Após as modificações, o projeto foi totalmente recompilado para assegurar que não foram inseridos erros de sintaxe ou de vinculação de tipos.

### Comando Executado:
```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-24&& .\mvnw.cmd compile"
```

### Resultado:
- **Status:** Compilação bem-sucedida (`BUILD SUCCESS`).
- **Arquivos verificados:** Todos os fontes de backend e UI JavaFX integrados sem avisos ou erros.
