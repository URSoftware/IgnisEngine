# Registro de Alterações (Changelog)

> Todas as alterações notáveis neste projeto serão documentadas neste arquivo.
> O formato é baseado no [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/) e este projeto segue o [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [1.0.8] - 2026-07-02

### Adicionado
- **Console Dashboard Web Premium para o MCP**: Implementacao do endpoint raiz `/` (e `/index.html`) no `McpHttpBridge`, servindo um dashboard interativo moderno e responsivo (HTML5/CSS3/JS, sem dependencias adicionais). A interface oferece busca instantanea em tempo real de todas as 66 ferramentas registradas, visualizacao interativa do schema de parametros com campos de formulario gerados dinamicamente para execucao imediata no sandbox (com saida formatada em console escuro), monitoramento visual do status do bridge e botoes de atalhos rapidos (Status da Cena, Listar Objetos, Play Game, Stop Game e Salvar Projeto) sem emojis ou placeholders, creditando o autor ThyagoToledo.
- **Polimento de Sprites 2D dos Personagens**: Geracao e substituicao dos sprites do heroi (`hero.png`) e do slime (`slime.png`) na pasta de assets do projeto (`projects/MyGame/project/assets/sprites/`) por sprites autorais transparentes em alta resolucao de pixel art 2D, eliminando os antigos placeholders e melhorando a fidelidade visual do jogo de combate em turnos.

### Corrigido
- **Compatibilidade de Escape de Text Block em Java**: Correcao de erros de compilacao relacionados aos caracteres de escape em literais de template JavaScript (`\\${...}`) inseridos dentro do text block Java de `McpHttpBridge.java`.

## [1.0.7] - 2026-07-01

### Adicionado
- **49 novas ferramentas no MCP** (`IgnisToolRegistry`), levando o total de 17 para **66** (25 sempre disponíveis + 41 com editor vivo). Cobrem: **áudio** (play/stop/volumes/preview/status, via `IgnisSoundEngine`), **assets e notas** (listar/importar assets, CRUD de notas/wiki), **animação** (criar clipes, adicionar frames, anexar/tocar/parar num objeto vivo, via `Animator`/`AnimationIO`), **prefabs** (listar/salvar/instanciar/deletar, via `PrefabManager`), **colisão** (`set_object_collider`: tipo/modo/camada/máscara), **câmera** (criar/listar/ativar/transformar câmeras, converter coordenadas mundo↔tela), **UI in-game direta** (criar labels/botões/barras/painéis **sem precisar escrever um IgnisScript**, usando o mesmo `UICanvas`) e **extras de GameObject** (visibilidade, cor do nome, z-order, inspeção completa, busca por tipo, remover script, limpar cena).
- **Correção de segurança de path-traversal**: novo helper compartilhado `resolveWithin(base, relative)` substitui a checagem antiga (`startsWith` textual, vulnerável a escapar para uma pasta irmã tipo `Project`→`ProjectEvil`) por comparação de prefixo com separador de caminho. Aplicado em `read_file`, `set_object_sprite`, `add_animation_frame`, `list_assets`, `import_asset_from_path` (categoria de destino) e `read_note`/`write_note` (escopados a `notes/`). `create_note` sanitiza o nome de arquivo derivado do título. Listagens (`list_notes`, `list_assets`, `list_audio_assets`) ignoram links simbólicos.
- **Correção de lógica**: `instantiate_prefab` não ignora mais silenciosamente `x`/`y` quando só um dos dois é informado — agora exige ambos juntos ou nenhum, com mensagem de erro clara.

### Processo
- Ferramentas pesquisadas via workflow de 8 agentes em paralelo mapeando as APIs do motor (áudio, animação, prefabs, colisão/câmera, assets/notas/marketplace, projeto/cenas/build, UI direta, `GameObject`), verificadas manualmente linha a linha antes da implementação, e revisadas por um segundo workflow adversarial (4 agentes) que encontrou os 6 problemas corrigidos acima antes do primeiro teste ao vivo.

## [1.0.6] - 2026-07-01

### Adicionado
- **Nova ferramenta MCP `generate_sprite`**: Permite gerar sprites procedurais simples (`square`, `circle`, `triangle`, `diamond`, `blob`) com suporte a cores customizadas em hex, bordas e símbolos de caractere no centro, salvando o asset final em `assets/sprites/<name>.png`.

### Corrigido
- **Desenho de UI in-game no Viewport JavaFX**: Corrigido bug em `Game.renderWorldTo` onde o `UICanvas` não era desenhado. Agora elementos de UI (botões, labels e barras) criados dinamicamente via script aparecem e respondem corretamente no viewport JavaFX.

## [1.0.5] - 2026-06-30

### Adicionado
- **Ferramentas de cena e Play no MCP (contexto vivo do editor):** o `IgnisToolRegistry` ganhou 9 ferramentas ativas quando o bridge roda dentro do editor JavaFX — `list_scene_objects`, `create_object` (com `type`: square/circle/triangle/star/pentagon/player via fábrica, já que `GameObject` é abstrato), `set_object_transform`, `set_object_sprite`, `delete_object`, `attach_script`, `play_game`, `stop_game` e `save_project`. Com elas, um agente monta e testa um jogo de ponta a ponta pela URL local.
- **Registro do editor vivo:** `McpService.setEditorContext(game, play, stop, refresh, save)` e `IgnisToolRegistry.attachLiveEditor(...)` ligam o registry ao `Game` da cena e aos hooks reais do editor (`playWorld`/`stopWorld`/`refreshHierarchy`/`saveProjectSilently`), executados na thread de UI via `IgnisMcpBridge`. No modo headless (`--mcp`) essas ferramentas não são registradas.

## [1.0.4] - 2026-06-30

### Adicionado
- **Interface de IA & MCP nas Configurações:** Nova aba em `FxSettingsWindow` para **ativar/desativar o servidor MCP**, ajustar porta, expor na rede/VPN, definir token opcional e **copiar a URL local** para colar em agentes de IA. Estado persistido em `EditorPrefs` e auto-start ao abrir o projeto.
- **Bridge HTTP do MCP (URL local):** `com.ignis.mcp.McpHttpBridge` (servidor `com.sun.net.httpserver`, sem novas dependências) expõe as ferramentas do motor por `GET /mcp/tools` e `POST /mcp/call`, com token Bearer opcional e proteção anti path-traversal. Fachada de ciclo de vida em `McpService`.
- **Registry canônico de ferramentas:** `com.ignis.mcp.IgnisToolRegistry` centraliza nome/descrição/schema/executor das ferramentas (árvore do projeto, listar/ler/escrever/criar scripts, compilar, ler arquivo), compartilhado entre STDIO, HTTP e a futura IA embarcada. Execução na thread de UI via `IgnisMcpBridge`.
- **Provider NVIDIA + scaffolding agêntico:** `NvidiaProvider` (endpoint OpenAI-compatível da NVIDIA) somando-se ao `GeminiProvider`; `AgentToolExecutor` liga as respostas dos LLMs (Gemini/NVIDIA) às ferramentas do MCP via function-calling por prompt. Chaves e provedor ativo configuráveis na aba IA & MCP.
- **Colaboração em tempo real (tipo CodeTogether):** Novo pacote `com.ignis.collab` (`CollabSession`/`CollabServer`/`CollabClient`) com transporte TCP (JSON por linha) para **hospedar/entrar** em sessões via IP direto ou VPN (Radmin/Hamachi/Tailscale), presença ao vivo, chat e canais de evento `scene`/`script`/`play`/`cursor`. Aba **Colaboração** nas Configurações para hospedar, entrar e copiar o endereço.

### Documentação
- Novos guias no vault: `MCP_SERVER_GUIDE.md`, `AGENTIC_AI_PLAN.md` (plano da IA agêntica futura, incl. IA local) e `COLLABORATION_GUIDE.md` (plano de sincronização completa).

## [1.0.3] - 2026-06-30

### Adicionado
- **Gerenciamento de Prefabs no Editor FX:** Integração completa do `PrefabManager` com a UI JavaFX. Suporta salvar a entidade selecionada como prefab (JSON), exibir a lista de prefabs disponíveis para instanciação e carregar/instanciar via duplo-clique no Asset Browser ou menu contextual, com suporte completo a Undo/Redo.
- **Console de Logs e Erros Integrado:** Novo componente `FxConsolePanel` no dock inferior do editor. Captura e espelha em tempo real streams de `System.out` e `System.err` com classificação de cores e severidades (Info/Avisos/Erros), opção de Auto-scroll, filtro dinâmico e persistência da exibição.
- **Mecanismo de Desfazer (Undo) no Inspector:** Implementação de `wireUndoableField` que captura o valor inicial do campo ao receber foco e registra uma transação no `UndoManager` quando o foco é perdido ou a tecla Enter é pressionada (apenas se o valor mudou). Opcionalmente suporta Undo imediato para caixas de seleção (como visibilidade).
- **Suporte para Multi-seleção Visual (Em Progresso):** Adicionado controle de `editorHighlights` e método `setEditorHighlights` em `Game.java`, desenhando um contorno pontilhado laranja ao redor das entidades selecionadas secundariamente no Canvas AWT.

## [1.0.2] - 2026-06-16

### Corrigido
- **Loop de Seleção Infinita no Inspector:** Otimizado o selection listener em `IgnisEditorApp` para verificar e rejeitar notificações obsoletas enfileiradas pela thread JavaFX, eliminando a alternância infinita entre entidades sobrepostas.
- **Warp de Mouse e Arrastes Presos:** Adicionado reset de arraste residual via `viewportMenu.setOnHidden` e `game.cancelDrag()` ao dispensar o menu de contexto, resolvendo saltos descontrolados de coordenadas.
- **Ordenação Invertida de Elementos na Cena:** Corrigidos os índices de movimentação para o topo (`Integer.MAX_VALUE`) e para o fundo (`0`) nos menus contextuais da Viewport e da Hierarchy.
- **Bug na Movimentação para Cima (moveEntityUp):** Removido ajuste incorreto de decremento de índice em `Game.moveEntityToIndex()`, solucionando o comportamento onde mover para cima resultava em no-op.
- **Seleção com Clique Direito na Hierarchy Tree:** Adicionado `setCellFactory` personalizado para interceptar cliques com botão direito (`SECONDARY`) na TreeView do JavaFX e selecionar o item sob o cursor antes de abrir o menu de contexto.
- **Performance de Renderização Desperdiçada:** Implementado mecanismo de supressão de `repaint()` do AWT/Swing (`setSuppressAwtRepaint(true)`) quando executando o editor JavaFX, poupando processamento redundante do pipeline AWT.

## [1.0.1] - 2026-06-15

### Adicionado
- **Sincronização em Tempo Real (60 FPS):** Atualização automática no Inspector dos campos X, Y, Largura, Altura, Rotação e Visibilidade com base na viewport, ignorando campos sob foco do teclado para evitar conflitos de digitação.
- **HUDs do Botão Esquerdo:** Ações contextuais acionadas por clique esquerdo simples na Hierarchy Tree, Assets Tree e Viewport Canvas, integrando comportamento de fechamento automático.
- **Visual Temático no Editor de Código:** Estilização dinâmica por classes CSS aplicadas em `FxCodeEditor` que herdam a paleta do tema de realce ativo (Dracula, Monokai, One Dark).
- **Logotipos e Ícones da Engine:** Carregamento e definição da logo oficial `Icons/IconeIgnis.png` nos estágios da aplicação e do editor.

### Modificado
- **Mitigação de Lag no Viewport:** Correção do método `setCursor()` em `Game.java` para ignorar chamadas quando o componente não possui peer nativo ativo sob JavaFX (evitando locks no AWT Toolkit).

## [1.0.0] - 2026-06-15

### Adicionado
- **Interface Gráfica JavaFX:** Casca visual do editor completamente reescrita em JavaFX 17 (na branch `main`), substituindo as janelas e frames antigos em Swing.
- **Painéis Modernos:** Hierarchy (TreeView), Inspector reativo para propriedades, Asset Browser para arquivos do projeto e FxProjectStartupDialog para seleção de projetos recentes.
- **Sub-editores JavaFX:** FxCodeEditor com realce de sintaxe para IgnisScript, FxImageEditor com camadas, FxAudioEditor com timeline de DAW, FxAnimationEditor e FxCommunityWindow.
- **Ponte de Renderização:** Transição assíncrona da simulação do Canvas AWT para componentes JavaFX usando `SwingFXUtils`.
- **Vault de Documentação:** Criação de guias técnicos abrangentes sobre o modelo de threads, internals do game loop, sistema de UI, mixagem de áudio, sistema de câmera e guias de setup do desenvolvedor.
- **Guia de Contribuição e Conduta:** Arquivos `CONTRIBUTING.md` e `CODE_OF_CONDUCT.md` na raiz do repositório para orientação de novos desenvolvedores.

### Modificado
- **README do Projeto:** Redesenhado completamente para adotar uma linguagem visual profissional e livre de emojis, atuando como o Hub Central do Vault de documentação.
- **Especificações de Câmera e Viewport:** Otimização da matriz de translação e zoom centralizada no foco do viewport.
- **Fábrica de Entidades:** Otimização dos mapeamentos estáticos na classe `EntityFactory` para suportar novas formas vetoriais nativas.

### Removido
- Documentos de planejamento locais e históricos obsoletos (`ROADMAP.md` e `SYNC_LOCAL_2026-06-15.md`).
- Tipos de entidades inexistentes do validador de arquivos `.ignis`.
