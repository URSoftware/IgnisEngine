# Detalhamento das Novas Funcionalidades - 02/07/2026

Este documento descreve detalhadamente as melhorias e novos recursos implementados no motor Ignis Engine e em seus projetos na data de 02/07/2026 por IA, sob a autoria exclusiva de ThyagoToledo.

---

## 1. Dashboard Web Interativo do MCP (McpHttpBridge.java)

### Proposito
Facilitar a interacao e o teste manual das ferramentas do Model Context Protocol (MCP) do Ignis Engine diretamente pelo navegador de internet (no endereco `http://127.0.0.1:8898/` ou `http://127.0.0.1:8903/`), fornecendo um sandbox amigavel e visual que dispensa o uso de clientes externos pesados ou a digitacao de chamadas curl manuais no terminal.

### O que faz
- **Registro do Caminho Raiz (`/` e `/index.html`):** Adicionado contexto no servidor HTTP embutido para responder requisicoes na raiz. Caso seja requisitado qualquer outro arquivo inexistente, o servidor retorna 404 de forma limpa.
- **Explorador e Filtro de Ferramentas:** Carrega dinamicamente a lista de todas as 66 ferramentas registradas no `IgnisToolRegistry` chamando o endpoint `/mcp/tools`. Apresenta uma barra de pesquisa lateral reativa por JS que filtra as ferramentas instantaneamente conforme o usuario digita pelo nome ou descricao.
- **Formularios Dinamicos por Schema:** Ao selecionar uma ferramenta, o painel central analisa o JSON Schema correspondente as propriedades de entrada (`inputSchema.properties`) e gera inputs HTML adequados com placeholders explicativos. Os campos marcados como obrigatorios no schema (`required`) sao renderizados com marcacao de asterisco e atributo de validacao nativa do HTML5.
- **Sandbox Terminal de Execucao:** Ao enviar o formulario, faz uma chamada POST para `/mcp/call` enviando os argumentos preenchidos. A resposta do servidor e recebida, sanitizada contra XSS e impressa com formatacao legivel e cores de sucesso (verde) ou erro (vermelho) dentro de um terminal escuro simulado no lado direito da tela.
- **Quick Command Hub (Atalhos Rapidos):** Disponibiliza no topo da tela um painel com botoes de atalho para comandos frequentes da cena do editor. Com um unico clique, o usuario pode:
  - Obter o Status Geral da Cena (`get_scene_info`)
  - Listar todos os GameObjects (`list_scene_objects`)
  - Pressionar Play para rodar a simulacao do jogo (`play_game`)
  - Parar o jogo (`stop_game`)
  - Gravar as alteracoes no disco (`save_project`)
- **Design Visual Premium:** Criado com CSS Vanilla customizado com tema escuro (Dracula-like) usando tons de roxo neon (`#8a5cf5`), azul ciano (`#00f0ff`) e fundos escuros (`#0c0f1d`). Utiliza as fontes Outfit/Inter e JetBrains Mono carregadas do Google Fonts, com efeitos suaves de hover nos botoes e paineis bem delineados sem o uso de qualquer placeholder ou emojis.

---

## 2. Polimento de Sprites 2D, Transparencia e Ferramenta de Background (MyGame)

### Proposito
Elevar a fidelidade visual e a apresentacao do jogo de combate por turnos criado para testes (`projects/MyGame`) substituindo os antigos placeholders por sprites de alta qualidade e fornecendo ferramentas robustas para automatizar a remocao completa de fundos solidos ou quadriculados (checkerboards).

### O que faz
- **Sprite do Heroi (`assets/sprites/hero.png`):** Substituido por uma imagem PNG transparente com visual de guerreiro/cavaleiro em pixel art 2D retro estilo 16-bit.
- **Sprite do Slime (`assets/sprites/slime.png`):** Substituido por um sprite de monstro gelatinoso verde em pixel art fofo.
- **Ferramenta `remove_sprite_background` Aprimorada (ImageTools.java):** Atualizada para aceitar o modo `"auto"`. Quando ativado, o motor analisa todas as bordas externas da imagem (top, bottom, left, right) coletando dinamicamente as cores de fundo (incluindo padroes quadriculados alternados e ruidos de compressao) e as remove da imagem com a tolerancia configurada. Tambem suporta passar uma lista de cores separadas por virgula no parametro `targetColorHex` (ex: `#ffffff,#cccccc`).
- **Remocao de Quadriculados (Checkerboards):** Processado os arquivos `hero.png` e `slime.png` originais de IA com o novo algoritmo inteligente de bordas, eliminando completamente a grade de transparencia cinza/branca que estava gravada na imagem original e deixando os personagens 100% transparentes sobre a grama.
- **Background do Mapa (`assets/sprites/grass.jpg`):** Adicionado ao projeto e configurado no GameObject `Background` da cena ativa com Z-order reposicionado para o fundo (`bottom`/indice `0`), servindo como cenario de gramado para a exploracao.

---

## 3. Mecanica de Jogo: Exploracao de Mapa & Trigger de Combate (CombatManager.java)

### Proposito
Implementar uma transicao de jogabilidade realista onde o jogador inicia explorando o mapa livremente antes de ser puxado para a arena de combate por turnos ao se aproximar de um inimigo.

### O que faz
- **Fase de Exploracao (`MAP_EXPLORATION`):** O `CombatManager` inicia no modo de exploracao do mapa. O jogador move o heroi usando WASD ou as setas do teclado. A camera do motor segue o heroi dinamicamente mantendo-o no centro da tela.
- **Instrucoes Visuais no HUD:** Para evitar duvidas sobre a fase ativa, um texto de aviso amarelo e impresso no topo da tela informando ao jogador: *"EXPLORACAO: Mova o Heroi usando as teclas WASD ou Setas ate o Slime!"*.
- **Trigger por Proximidade:** No loop de atualizacao (`tick()`), o script calcula a distancia Euclidiana 2D entre o Heroi e o Slime. Quando a distancia e menor que 120 pixels, a fase e transicionada.
- **Fase de Combate por Turnos (`PLAYER_TURN`/`ENEMY_TURN`):** O heroi e o slime sao teleportados para suas posicoes de combate correspondentes na arena. A camera e centralizada em `(0, 0)`, a UI de progresso de vida, botoes de ataque/defesa e o console de log de combate sao criados dinamicamente via script. A mensagem HUD de exploracao e limpa.
- **Loop de Gameplay Infinito:** Ao vencer ou perder o combate, a UI exibe um botao correspondente ("Explorar" ou "Tentar Novamente"). Clicar nele reinicia o heroi na origem, cura sua vida e reposiciona o Slime em um ponto aleatorio distante no mapa para que o jogador continue explorando e batalhando.

---

## 4. Organizacao de Objetos de Cena e Z-Order (IgnisToolRegistry.java)

### Proposito
Fornecer clareza e controle sobre a ordem de empilhamento visual (renderizacao) de GameObjects da cena ativa para o usuario e para agentes de IA que utilizam o MCP.

### O que faz
- **Ordem de Renderizacao Explicita no MCP:** A ferramenta `list_scene_objects` foi aprimorada para retornar os GameObjects listados com seu respectivo indice numerico de Z-order (Z-index: `[0]` sendo a camada mais ao fundo, desenhada primeiro, e indices maiores sendo desenhados por cima).
- **Instrucoes no Header:** A resposta do MCP inclui uma nota explicativa canonica: *"Ordem de renderizacao (Z-order): indices menores sao desenhados primeiro (ficam atras), indices maiores sao desenhados por cima."* Isso permite que a IA manipule a profundidade da cena sabendo exatamente qual objeto esta encobrindo qual.

---

## 5. Inicializacao Automatica com Ultimo Projeto (IgnisEditorApp.java)

### Proposito
Acelerar o ciclo de desenvolvimento e testes evitando que a tela de selecao de projetos (`FxProjectStartupDialog`) bloqueie o carregamento do motor a cada reinicializacao.

### O que faz
- **Auto-load do Ultimo Projeto:** Ao iniciar a aplicacao JavaFX, o metodo `start` consulta as preferencias do usuario (`EditorPrefs.getLastProject()`). Se houver um caminho registrado de projeto recente e valido, a engine realiza o carregamento automatico da cena e compila os scripts. O dialogo de selecao inicial de projeto so e apresentado caso o ultimo projeto nao exista ou falhe ao carregar.

---

## 6. O que faltou / Proximos Passos de Implementacao

- **Hot-Reloading Visual dos Sprites:** Atualmente, quando um sprite e sobrescrito no disco com a simulacao em execucao, o `SpriteRenderer` do motor continua exibindo a imagem antiga em cache ate que o jogo seja parado e reiniciado. Uma futura melhoria seria adicionar um listener de arquivos (`FileWatcher`) na pasta `assets/` para atualizar dinamicamente as texturas na memoria do motor ao detectar alteracoes fisicas.
- **Upload de Assets via Dashboard:** Expandir o sandbox web do MCP com uma area de upload (drag-and-drop) para que o usuario possa enviar novas imagens (`.png`/`.jpg`) ou scripts (`.java`) diretamente do navegador para a pasta de assets/scripts do projeto selecionado.
- **Auditoria Interna no Editor:** Criar uma aba ou secao no console do editor JavaFX que liste o historico de conexoes e ferramentas MCP executadas remotamente pelos agentes, permitindo ao desenvolvedor auditar em tempo real quais arquivos ou objetos estao sendo lidos ou modificados por IA.
