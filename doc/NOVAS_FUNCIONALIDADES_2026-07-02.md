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

## 2. Polimento de Sprites 2D dos Personagens (MyGame)

### Proposito
Elevar a fidelidade visual e a apresentacao do jogo de combate por turnos criado para testes (`projects/MyGame`) substituindo os antigos placeholders de formas geometricas ou arquivos temporarios de poucos bytes por sprites artisticos transparentes.

### O que faz
- **Sprite do Heroi (`assets/sprites/hero.png`):** Substituido por uma imagem PNG transparente com visual de guerreiro/cavaleiro em pixel art 2D retro estilo 16-bit.
- **Sprite do Slime (`assets/sprites/slime.png`):** Substituido por um sprite autoral de monstro gelatinoso verde em pixel art fofo, perfeitamente dimensionado para se opor ao Heroi no tabuleiro de combate.
- Ambos os arquivos foram fisicamente gravados no diretorio de assets do projeto do usuario (`projects/MyGame/project/assets/sprites/`). A simulacao e o script `CombatManager.java` leem e animam esses sprites ao aplicar os efeitos visuais de idle bobbing e shake de impacto.

---

## 3. O que faltou / Proximos Passos de Implementacao

- **Hot-Reloading Visual dos Sprites:** Atualmente, quando um sprite e sobrescrito no disco com a simulacao em execucao, o `SpriteRenderer` do motor continua exibindo a imagem antiga em cache ate que o jogo seja parado e reiniciado. Uma futura melhoria seria adicionar um listener de arquivos (`FileWatcher`) na pasta `assets/` para atualizar dinamicamente as texturas na memoria do motor ao detectar alteracoes fisicas.
- **Upload de Assets via Dashboard:** Expandir o sandbox web do MCP com uma area de upload (drag-and-drop) para que o usuario possa enviar novas imagens (`.png`/`.jpg`) ou scripts (`.java`) diretamente do navegador para a pasta de assets/scripts do projeto selecionado.
- **Auditoria Interna no Editor:** Criar uma aba ou secao no console do editor JavaFX que liste o historico de conexoes e ferramentas MCP executadas remotamente pelos agentes, permitindo ao desenvolvedor auditar em tempo real quais arquivos ou objetos estao sendo lidos ou modificados por IA.
