# Relatório Detalhado de Implementações e Pendências no Editor FX (30/06/2026)

Este relatório descreve detalhadamente o comportamento, o propósito técnico e as pendências de implementação (o que falta implementar) para as quatro tarefas do editor JavaFX do Ignis Engine.

---

## 1. Prefabs no Editor FX

### O que faz:
Permite a criação e instanciação de templates de GameObjects reutilizáveis. O editor serializa o objeto selecionado em formato JSON e o salva em `<projeto>/prefabs/[nome].prefab.json`. O navegador de assets monitora essa pasta e exibe os prefabs. A partir daí, o usuário pode instanciar o prefab na cena através do menu contextual (clique direito), da caixa de diálogo "Instanciar Prefab" ou por clique duplo direto no arquivo `.prefab.json` na árvore de assets.

### Propósito Técnico:
- **Reutilização de Design:** Evita que o designer de fases precise reconfigurar sprites, colisores, scripts e variáveis para objetos repetidos (como inimigos, moedas, blocos de cenário).
- **Consistência de Dados:** Garante que todos os objetos criados a partir do mesmo template compartilhem a mesma estrutura inicial.
- **Integração com Histórico:** A instanciação cria um GameObject completo e registra um comando no `UndoManager`, permitindo a remoção limpa em caso de desfazer (Undo).

### O que faltou implementar (Pendências):
- **Modo de Edição de Prefab (Prefab Mode):** Falta criar uma interface dedicada (estilo o Prefab Mode da Unity) para editar o arquivo `.prefab.json` isoladamente, sem precisar instanciá-lo na cena ativa para depois salvá-lo novamente.
- **Vinculação Ativa (Prefab Connection):** Atualmente, os objetos instanciados são cópias independentes. Se o arquivo do prefab for atualizado, as instâncias já presentes na cena não sofrem atualização automática (herança de prefabs).
- **Diferenciação Visual na Hierarquia:** Os itens da hierarquia que são instâncias de prefabs deveriam ser exibidos com uma cor de texto diferente (ex.: azul) ou um ícone indicando que são vinculados a um prefab.

---

## 2. Console de Erros Integrado

### O que faz:
Cria uma área de exibição de log no dock inferior do editor JavaFX (`FxConsolePanel`). O console captura e espelha em tempo real as saídas `System.out` e `System.err` através de um fluxo redirecionador personalizado (*tee*). Ele analisa as mensagens linha por linha, classificando-as visualmente em três categorias de severidade (INFO, WARNING, ERROR) com base em palavras-chave e no stream de origem.

### Propósito Técnico:
- **Visibilidade de Debug:** Permite que desenvolvedores acompanhem a saída de seus IgnisScripts e erros de compilação sem precisar alternar entre o editor de jogo e a janela do terminal do sistema operacional.
- **Filtragem de Ruído:** Melhora a identificação de problemas através de botões de filtro na barra de ferramentas que mostram ou ocultam mensagens com base em sua severidade.
- **Ergonometria Visual:** A rolagem automática inteligente (*auto-scroll*) mantém as mensagens mais recentes visíveis, e as cores personalizadas integradas respeitam a estética do tema escuro do editor.

### O que faltou implementar (Pendências):
- **Navegação de Erro ao Clique (Go to Error Line):** Falta implementar uma lógica onde, ao dar um duplo-clique em uma linha de erro de compilação ou exceção de script no console, o arquivo de script correspondente seja aberto automaticamente no editor de código integrado ([FxCodeEditor.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/editor/fx/FxCodeEditor.java)) posicionado na linha que causou o problema.
- **Barra de Pesquisa e Filtro de Texto:** Adicionar uma caixa de texto para filtrar logs em tempo real por termos de busca específicos ou expressões regulares.
- **Exportação de Logs:** Implementar a funcionalidade de salvar/exportar o buffer do console em um arquivo `.txt` ou `.log` local.

---

## 3. Desfazer (Undo) na Edição de Campos do Inspector

### O que faz:
Gerencia as ações de desfazer e refazer para as propriedades digitadas nos campos de texto do Inspector (X, Y, Largura, Altura, Rotação e Nome) de forma transacional. O editor escuta o ganho de foco do campo para armazenar o valor inicial do objeto (`beginInspectorEdit`) e, ao perder o foco ou pressionar Enter, valida se houve alteração real para empilhar um único comando consolidado no `UndoManager` (`commitInspectorEdit`).

### Propósito Técnico:
- **Prevenção de Inundação de Histórico:** Evita que cada tecla digitada (por exemplo, ao digitar "150" caractere por caractere: "1", "15", "150") crie múltiplos comandos de desfazer separados, o que tornaria o histórico de Undo inútil para o usuário.
- **Mudanças Discretas Imediatas:** Controles de clique direto (como a checkbox de visibilidade) criam uma transação no `UndoManager` imediatamente, sem a necessidade de esperar perda de foco.

### O que faltou implementar (Pendências):
- **Suporte para Sliders e Arrastes de Valores:** Se forem implementados sliders ou campos arrastáveis (drag-to-modify) no Inspector, eles precisarão de uma lógica similar que capture o valor no início do clique (Mouse Pressed) e comite o valor acumulado no término do clique (Mouse Released).
- **Undo para Outros Componentes:** Propriedades mais complexas do Inspector (como propriedades de Script adicionadas dinamicamente, seletores de cores e caixas de colisão) ainda não utilizam o sistema transacional de perda de foco e precisam ser portadas para este padrão.

---

## 4. Multi-seleção no Editor FX (Em Progresso)

### O que faz:
Adiciona suporte básico no renderizador da engine ([Game.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEngine-main/src/com/ignis/core/Game.java)) para exibir contornos pontilhados na cor laranja (`Color(255, 160, 40)`) ao redor de uma lista de entidades secundárias (`editorHighlights`) durante o modo de edição, permitindo feedback visual de que múltiplos elementos estão agrupados na seleção.

### Propósito Técnico:
- **Identificação Visual:** Permitir que o usuário saiba quais objetos pertencem à sua seleção secundária sem exibir o gizmo ativo de transformação para cada um, evitando poluição visual na viewport.
- **Isolamento de Controles:** Mantém a manipulação física (arraste e redimensionamento via gizmo) vinculada apenas ao objeto primário selecionado, preparando a arquitetura de movimentação em bloco.

### O que faltou implementar (Pendências):
- **Habilitação de Seleção Múltipla na TreeView:** A árvore de hierarquia (`TreeView`) em [IgnisEditorApp.java](file:///c:/Users/thyag/OneDrive/Desktop/IgnisEditorApp.java) ainda opera no modo de seleção única. É necessário alterar para `SelectionMode.MULTIPLE`.
- **Comportamento de Clique na Viewport e Hierarquia:** Falta implementar a lógica que captura cliques com as teclas `Ctrl` ou `Shift` pressionadas para adicionar ou remover entidades da lista de seleção, atualizando os destaques pontilhados.
- **Gizmo de Transformação Coletivo:** Atualmente, as transformações de translação, rotação e escala ocorrem apenas no objeto principal selecionado. Falta implementar a movimentação proporcional em grupo, onde mover o objeto âncora (com o gizmo) translada proporcionalmente todos os demais objetos selecionados na lista de realces.
- **Ações de Menu em Bloco:** Operações como duplicar (Duplicate), deletar (Delete) e mover (ordenar) ainda atuam estritamente sobre a entidade principal selecionada. É necessário adaptar estas funções para varrer a lista de seleção múltipla e efetuar as operações em bloco em uma única transação de Undo.
