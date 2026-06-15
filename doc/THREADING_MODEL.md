# Modelo de Threads (Threading Model)

> Documentação técnica oficial sobre a arquitetura multithreading, sincronização de tarefas e concorrência na IgnisEngine.

---

## 1. As Três Threads do Sistema

Durante a execução do editor, a IgnisEngine gerencia três threads concorrentes principais, cada uma com responsabilidades específicas e isoladas:

```text
                  ┌───────────────────────────────┐
                  │      Game Thread (Loop)       │
                  │   Lógica, Física, Scripting   │
                  └──────────────┬────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │ (SwingFXUtils)
                 ▼                               ▼
  ┌─────────────────────────────┐ ┌─────────────────────────────┐
  │    JavaFX App Thread (UI)   │ │      Swing EDT (Legado)     │
  │   Hierarchy, Inspector, FX  │ │  DawFrame, PaintCanvas Swing│
  └─────────────────────────────┘ └─────────────────────────────┘
```

1. **Game Thread (Thread do Jogo):**
   - **Origem:** Instanciada e iniciada pela classe `Game.java` ao rodar a simulação do mundo.
   - **Responsabilidade:** Loop contínuo de atualização física, execução do ciclo de scripts (`IgnisScript.update()`), cálculo de detecção de colisões e desenho de entidades na `BufferedImage`.
   - **Características:** Roda o mais próximo possível de 60 frames por segundo, sem bloqueios de IO para manter a simulação fluida.

2. **JavaFX Application Thread (Thread de UI Principal):**
   - **Origem:** Iniciada automaticamente pela biblioteca JavaFX na chamada de `Application.launch()`.
   - **Responsabilidade:** Gerenciar todo o ciclo de vida visual das janelas nativas, manipular a Hierarchy (TreeView), ler e atualizar propriedades no Inspector e renderizar controles de tela.
   - **Características:** Bloqueios nesta thread congelam completamente o visual do editor.

3. **Swing Event Dispatch Thread - EDT (Thread de UI Swing):**
   - **Origem:** Thread de gerenciamento de interface clássica do Java AWT/Swing.
   - **Responsabilidade:** Utilizada como fallback para janelas e sub-editores legados da engine (como o DAW de áudio Swing clássico ou o editor de imagens Swing clássico).
   - **Características:** Executada em paralelo à thread do JavaFX quando esses diálogos específicos são invocados a partir do menu.

---

## 2. Regras de Segurança e Sincronização

Acesso cruzado de threads sem a devida precaução resulta em falhas críticas de runtime ou inconsistência de dados. A engine adota regras estritas de sincronização:

### A. Da Game Thread para a UI do JavaFX
Se um script ou lógica de jogo precisar alterar algum elemento visual do editor JavaFX (por exemplo, atualizar o texto de um nó na árvore da Hierarchy):
- **Ação:** Envolver a chamada utilizando `Platform.runLater()`.
- **Exemplo:**
  ```java
  Platform.runLater(() -> {
      hierarchyTreeView.getRoot().getChildren().add(new TreeItem<>("Novo Objeto"));
  });
  ```

### B. Da Game Thread para a UI Swing
Para diálogos legados:
- **Ação:** Envolver a chamada utilizando `SwingUtilities.invokeLater()`.
- **Exemplo:**
  ```java
  SwingUtilities.invokeLater(() -> {
      legacyBuildDialog.setVisible(true);
  });
  ```

### C. Da Thread de UI para o Estado do Jogo
Quando o usuário digita uma propriedade no Inspector e esta deve ser aplicada à entidade no jogo:
- **Ação:** As alterações devem ser aplicadas utilizando métodos sincronizados ou blocos de sincronização com o objeto `Game` para evitar concorrência com o método `tick()`.
- **Exemplo:**
  ```java
  synchronized(game) {
      selectedEntity.setX(novoValorX);
  }
  ```

---

## 3. A Ponte de Renderização e Sincronismo da BufferedImage

A ponte de renderização é um exemplo crítico de sincronização entre a **Game Thread** e a **JavaFX App Thread**:
- A Game Thread escreve continuamente na `BufferedImage` compartilhada durante a fase de `render()`.
- A JavaFX Thread lê a mesma imagem no ciclo de atualização da interface.
- Para evitar inconsistência visual (como frames corrompidos ou rasgados), a cópia de pixels via `SwingFXUtils.toFXImage` é encapsulada em métodos sincronizados no monitor do objeto de jogo.

---

## 4. Principais Armadilhas e Como Evitá-las

### A. Travamento de UI (UI Freeze)
- **Problema:** Realizar carregamento de arquivos do disco (IO) ou compilação de scripts direto na JavaFX Application Thread trava a interface visual do editor.
- **Solução:** Executar tarefas longas em segundo plano utilizando `Task` ou `Thread` separada e atualizar a UI no final usando `Platform.runLater()`.

### B. Condição de Corrida (Race Conditions)
- **Problema:** Um script do jogo modifica a lista de entidades na Game Thread enquanto o Inspector JavaFX lê a mesma lista para redesenhar a tela.
- **Solução:** Uso de coleções thread-safe (`ConcurrentHashMap`, `CopyOnWriteArrayList`) ou sincronização mútua no monitor principal da instância do jogo.

### C. Deadlocks
- **Problema:** A thread de UI aguarda de forma bloqueante a conclusão de um cálculo da Game Thread, enquanto a Game Thread tenta atualizar a UI chamando `Platform.runLater()` de forma síncrona.
- **Solução:** Evite chamadas bloqueantes entre threads. Utilize fluxos assíncronos baseados em callbacks ou variáveis de sinalização voláteis (`volatile`).

---

## 5. Diferença no Runtime Standalone (`GameRuntime`)

No player autônomo (`com.ignis.runtime.GameRuntime`), o modelo de threads é significativamente simplificado:
- **Sem JavaFX App Thread / Sem Swing EDT:** O executável compilado do jogo roda puramente utilizando a **Game Thread** (AWT Canvas em tela cheia ou janela).
- **Vantagem:** Sem a necessidade de sincronizar a ponte de renderização com janelas complexas do editor, o consumo de recursos (CPU/RAM) cai drasticamente e a taxa de frames por segundo (FPS) sobe de forma expressiva.
