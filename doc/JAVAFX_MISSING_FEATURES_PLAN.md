# Roteiro de Implementação de Paridade de Recursos do Editor JavaFX (JavaFX Feature Parity Plan)

> Planejamento técnico para atingir a paridade total de recursos entre o editor legado (Swing) e o editor moderno (JavaFX), incluindo correções estéticas e melhorias de usabilidade no editor de scripts.

---

## 1. Problemas e Funcionalidades Ausentes Identificadas

Durante o processo de auditoria de paridade entre a versão JavaFX e a Swing clássica, foram mapeadas as seguintes lacunas funcionais e bugs visuais prioritários:

### A. Editor de Código de Scripts (`FxCodeEditor.java`)
1. **Falha Crítica nos Temas de Destaque de Sintaxe (CSS Specificity Bug):**
   - **Causa:** O seletor CSS `.code-area .text` possui uma especificidade superior à dos seletores de classe única de destaque (`.keyword`, `.type`, `.string`, `.comment`, `.number`). Isso faz com que todo o texto do editor apareça na cor padrão do tema (branco ou preto), quebrando a coloração visual de sintaxe.
   - **Correção:** Alterar os seletores no CSS dinâmico para `.code-area .keyword`, `.code-area .type`, etc. Isso eleva a especificidade ao mesmo nível de `.code-area .text`, garantindo a correta substituição de cor.
2. **Persistência de Tema:**
   - O tema selecionado pelo usuário não é salvo, voltando para "Classic Dark" a cada inicialização da janela.
   - **Melhoria:** Estender a classe `EditorPrefs` para persistir o nome do tema preferido do editor de código do desenvolvedor.

### B. Interação na Viewport e Roteamento de Eventos (Grave Lacuna de Input)
- **Problema:** A viewport de visualização em JavaFX utiliza um elemento `Canvas` para renderizar a imagem de buffer gerada pelo loop principal do jogo. Os eventos de mouse e clique na viewport JavaFX são interceptados e enviados apenas para o singleton `Input.java` (para detecção in-game), mas **não** são propagados para a fila de eventos AWT da instância `Game.java`.
- **Impacto:** Isso impede completamente o funcionamento de recursos essenciais do editor, tais como:
  - Seleção visual de entidades clicando na viewport.
  - Movimentação do foco de câmera (Panning com botão do meio do mouse).
  - Manipulação e arrasto de entidades usando os Gizmos de transformação (Move, Rotate, Scale).
- **Correção:** Atualizar o roteamento de entrada em `wireFxInputToEngine` em `IgnisEditorApp.java` para que, ao capturar eventos de mouse do JavaFX, crie o evento AWT correspondente e invoque explicitamente o método `game.dispatchEvent(awtMouseEvent)`.

### C. Menu de Visualização e Opções de Grade/Câmera (`IgnisEditorApp.java`)
- O menu "Visualizar" está vazio, enquanto na versão clássica ele fornecia opções essenciais. Adicionaremos as seguintes opções:
  - **Menu Grade:**
    - Exibir/Ocultar Grade (Show Grid) com sincronismo dinâmico.
    - Snap to Grid (Encaixe na Grade).
    - Submenu Tamanho da Grade (Grid Size: 16, 32, 64, 128 pixels).
  - **Menu Câmera:**
    - Zoom In (Aproximar Câmera) / Zoom Out (Afastar Câmera).
    - Zoom para 100% (Redefinir escala).
    - Resetar Câmera (Retornar posição para origem).
    - Focar no Objeto Selecionado.
- **Botões e Indicadores na Barra de Ferramentas Principal:**
  - Adicionar controle de zoom e indicador de posição da câmera na ToolBar superior da aplicação (assim como no editor Swing).
  - Mapear a roda de rolagem do mouse (Scroll Event) na viewport do JavaFX para controlar o fator de zoom da câmera da cena de forma contínua.

### D. Menus de Contexto e Ações de Cliques (HUDs Específicas)
- **Menu de Contexto da Hierarchy e Viewport:**
  - Adicionar suporte a clique com o botão direito (ou ações de menu secundário) na Hierarchy e na área de renderização do Viewport para exibir o menu com opções de:
    - Criar Objeto (Square, Circle, Triangle, Star, Pentagon, Player, Camera).
    - Renomear, Duplicar, Deletar.
    - Mapear ordenação de renderização (Move Up, Move Down, Move to Top, Move to Bottom).
    - Mesclar objetos (Merge Selected Objects).
    - Salvar como Prefab.
    - Adicionar ou criar scripts associados.
- **Menu de Contexto do Asset Browser:**
  - Exibir popup de arquivos com opções para criar pastas, criar scripts, deletar, renomear e abrir o script no editor integrado ou em uma IDE externa.

### E. Atalhos Globais de Teclado
Mapear e filtrar atalhos do teclado na cena principal para acionar comandos rápidos:
- `Delete` para remover objeto selecionado.
- `F2` para renomear objeto selecionado.
- `Ctrl+D` para duplicar objeto selecionado.
- `Ctrl+C` e `Ctrl+V` para copiar e colar objetos.
- `Ctrl+=` e `Ctrl+-` para controle de zoom da câmera.
- `Ctrl+0` para redefinir zoom.
- `Home` para redefinir câmera para a origem.
- `F` para focar na entidade selecionada.

---

## 2. Cronograma e Status da Implementação (Concluído)

Todas as fases do planejamento foram totalmente integradas e validadas:

```text
Passo 1: Corrigir Destaque de Sintaxe e Persistência no FxCodeEditor.java
  └─ [Concluído] Ajustar especificidade dos seletores de cores no CSS (.code-area .keyword, etc.)
  └─ [Concluído] Integrar persistência do tema em EditorPrefs

Passo 2: Habilitar Roteamento de Eventos AWT no IgnisEditorApp.java
  └─ [Concluído] Adicionar game.dispatchEvent(awtMouseEvent) nos handlers de mouse do viewport
  └─ [Concluído] Sincronizar tamanho e viewport do Game de forma dinâmica com o Canvas JavaFX

Passo 3: Adicionar Recursos de Câmera, Grade e Zoom
  └─ [Concluído] Criar botões de zoom, reset, foco e indicadores na ToolBar e no menu "Visualizar"
  └─ [Concluído] Sincronizar ScrollEvent de zoom na viewport com a câmera
  └─ [Concluído] Adicionar submenu de tamanho da grade e suporte a depuração de colisores

Passo 4: Implementar Menus de Contexto Completos
  └─ [Concluído] Adicionar ações completas (Criar, duplicar, deletar, copiar, colar, ordenar) na Hierarchy e Viewport

Passo 5: Vincular Atalhos de Teclado Globais
  └─ [Concluído] Adicionar filtros de atalhos na cena sem interromper a digitação nos campos do Inspector

---

## 3. Otimizações de UX e Estilo Temático (Fase Recente — Concluído)

Nesta fase subsequente, focamos na usabilidade do motor gráfico e do editor de código para garantir uma transição premium da versão Swing:

### A. Resolução de Lags e Travamentos de Cursor
- **Problema:** Ao passar o mouse ou clicar na viewport JavaFX (que renderiza a tela do jogo em cima de um Canvas AWT), ocorriam engasgos periódicos ("gargalos") na thread principal do JavaFX. A causa era a chamada de `setCursor()` no Canvas nativo do Swing/AWT, que tentava se sincronizar de forma síncrona com o gerenciador de janelas do sistema operacional em um componente sem peer nativo ativo (ambiente headless sob JavaFX).
- **Correção:** Sobrescrito `setCursor(Cursor)` em `Game.java` para validar `isDisplayable()` antes de delegar à superclasse, eliminando 100% dos travamentos.

### B. Sincronização do Inspector em Tempo Real (60 FPS)
- **Implementação:** Integrado o método `updateInspectorFields()` no `AnimationTimer` da ponte de renderização. Os campos de propriedades (X, Y, Largura, Altura, Rotação e Visibilidade) agora sincronizam continuamente com a posição e escala das entidades na viewport enquanto elas são manipuladas via Gizmos.
- **Experiência do Usuário:** Para evitar bugs de caret e loops infinitos de entrada de dados, os campos de texto do Inspector só atualizam seus valores se não estiverem com foco ativo do teclado.

### C. HUDs Dedicadas de Clique Esquerdo (Menus Contextuais)
- **Hierarchy:** Cliques com o botão esquerdo simples agora abrem diretamente a HUD rápida de opções de entidades ou o menu de criação de objetos primitivos.
- **Assets Tree:** Configurado um listener de clique esquerdo para abrir a HUD de operações em arquivos do navegador de assets (com suporte a *Abrir/Editar*, *Renomear*, *Deletar*, *Copiar Caminho* e *Criar Novo Script*).
- **Viewport Canvas:** Cliques esquerdos simples (sem arraste) efetuam apenas a seleção do objeto (atualizando a Hierarchy e o Inspector). A HUD rápida (menu de contexto) da viewport é disparada exclusivamente com o clique do botão direito do mouse, garantindo uma navegação e seleção livre de interferências de menus.
- **Dismiss Automático:** Todas as HUDs do botão esquerdo utilizam auto-hide padrão, fechando instantaneamente quando o usuário clica com o botão esquerdo em qualquer outra área do editor.

### D. Visual Temático Premium no Editor de Código
- **Estilização Dinâmica:** Removemos todas as estilizações de cor em linha (inline CSS) das barras de ferramentas, status e botões de `FxCodeEditor.java`. Substituímos por seletores de classes CSS e criamos regras dinâmicas em `applyTheme(EditorTheme)` baseadas nas cores de realce do tema de código selecionado (ex: Dracula, Monokai, One Dark). Os botões de ação e ComboBoxes agora adaptam suas cores e estados ativos para combinar perfeitamente com a paleta do tema ativo.
- **Identidade Visual:** Aplicamos o ícone oficial da engine (`Icons/IconeIgnis.png`) em todos os `Stage`s principais (`IgnisEditorApp` e `FxCodeEditor`) para unificação visual da barra de tarefas e cabeçalhos.

```
