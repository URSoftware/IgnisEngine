# Registro de Alterações (Changelog)

> Todas as alterações notáveis neste projeto serão documentadas neste arquivo.
> O formato é baseado no [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/) e este projeto segue o [Versionamento Semântico](https://semver.org/lang/pt-BR/).
---

## [Não lançado]

### Suporte a bibliotecas privadas de projeto (`project/libs/`)
`ScriptManager` compilava scripts um arquivo por vez, sem incluir os demais `.java` do projeto nem `.class` pré-compilados no classpath — qualquer projeto com lógica própria espalhada em mais de um arquivo (ex: classes de domínio reutilizadas por vários scripts) esbarrava em `cannot find symbol` ao compilar pelo editor. Agora `ScriptManager` procura `.jar` soltos em `project/libs/` e os inclui tanto no classpath de compilação (`compileScript`) quanto no `URLClassLoader` de runtime (`reloadClassLoader`) — por-projeto, opcional, sem acoplar a engine a nenhuma dependência específica de jogo. Ver `doc/PROJECT_LIBS_GUIDE.md`. Validado com o projeto `RimuruSurvivors` (`domain-lib` empacotado em `project/libs/rimuru-survivors-domain.jar`, 23 testes JUnit).

### Cache de prefab — fim do I/O por spawn
`PrefabManager.instantiatePrefab` lia o `.prefab.json` do disco e **reparseava o JSON em toda chamada**. Num jogo com spawner (orbes, projéteis) isso é leitura de disco + parse centenas de vezes por segundo — era o custo real por trás das milhares de linhas `Prefab RuntimeVisual instanciada` no log da sessão que estourou a memória do editor em 15/07/2026.

- O JSON parseado passa a ser **cacheado por arquivo**, com o `lastModified` como chave de validade: editar o prefab (no editor ou fora) invalida sozinho — o cache não congela o prefab.
- `savePrefab`/`deletePrefab` invalidam explicitamente: a resolução de mtime do sistema de arquivos pode ser de 1s, e dois saves no mesmo segundo serviriam a versão velha.
- **Seguro por construção:** a desserialização apenas *lê* o JSON — nenhum dos 17 overrides de `loadProperties` muta o objeto recebido —, então a mesma instância pode servir todos os spawns.
- `PrefabManagerCacheTest`: reuso sem reler o arquivo (reescrevendo o conteúdo mas preservando o mtime), identidade/posição próprias por instância, reparse após edição, invalidação no save, e prefab inexistente ainda retornando `null`.

### Mural de coordenação recusa mensagem em branco
`send_message` declarava `text` como obrigatório no schema, mas nada validava o conteúdo: string vazia era aceita e virava uma linha em branco no mural. Entre agentes isso é pior que um erro — quem lê vê `Codex -> Claude:` sem texto e não distingue falha de transporte de "o colega não tinha nada a dizer". Aconteceu de verdade em 16/07/2026 (seq 13 e 14, duas mensagens seguidas). A guarda ficou em `McpCoordination.sendMessage` (não no handler da ferramenta): protege qualquer chamador da camada e é testável headless, já que `call()` sem conflito despacha para a FX thread, ausente na suíte.

### Coordenação multi-agente aplicada de verdade (MCP)
O MCP já tinha a camada de coordenação (`McpCoordination`: presença, mural de mensagens, claims e quadro de tarefas), mas ela era **decorativa fora dos scripts**: o claim só era verificado em `write_script`/`create_script`. Dois agentes podiam reservar `objeto:Hero` e mesmo assim ambos chamarem `set_object_transform` nele — as outras ~115 ferramentas ignoravam claims. Além disso não havia identificação do chamador: o console logava `[MCP] create_object …`, sem dizer **quem** chamou.

- **Gate central de coordenação** em `IgnisToolRegistry.call()`: um mapa `GUARDS` associa cada ferramenta mutante ao recurso que ela disputa (`objeto:<nome>`, `script:<nome>`, `camera:<nome>`, `camera`, `mundo`, `cena`), derivando o alvo do argumento certo de cada uma (`name`, `objectName`, `child`, `scriptName`). Se o recurso pertence a **outro** agente, a chamada é barrada **antes de tocar a cena** (o gate roda antes do dispatch para a FX thread). Cobre ~55 ferramentas de escrita — objetos, scripts, câmeras, mundo, UI, conteúdo (tilemap/partículas/texto/luz) e hierarquia.
- **Escopo amplo para operações destrutivas:** `clear_scene`, `play_game` e `stop_game` não miram um alvo — passariam ilesas por uma checagem por chave. São barradas se **qualquer** recurso estiver reservado por outro agente (`McpCoordination.anyHolderExcept`).
- **Identidade no console:** as chamadas agora logam `[MCP] Ana · create_object … -> ok (3ms)`; conflitos aparecem como `[MCP] Bruno · set_object_transform -> CONFLITO` (nível WARNING). O bridge HTTP não tem sessão por conexão, então a identidade vem do argumento `agent`.
- **`agent` anunciado no catálogo automaticamente:** a injeção acontece no ponto único de registro (`add()`), então toda ferramenta guardada expõe `agent` no `inputSchema` de `/mcp/tools` e ganha a nota de coordenação na descrição — sem tocar nas 7 classes de domínio.
- **Presença passiva:** qualquer ferramenta chamada com `agent` atualiza a presença (`touchAgent`), então um agente que trabalha sem chamar `agent_join` ainda aparece em `agent_list` e não "some" enquanto trabalha.
- **Opt-in e retrocompatível:** chamadas sem `agent` seguem funcionando como antes, sem coordenação — nenhum cliente MCP existente quebra.
- Testes: `McpCoordinationGateTest` (10 casos — bloqueio, dono do claim, recurso vizinho livre, anônimo, ferramenta de leitura, escopo amplo, schema). **124 testes no total.**

### Fase F — decomposição das god classes (em andamento)
`Game.java` acumulava loop, render, input do editor e gerência de cena numa classe de 3126 linhas. A decomposição é incremental: cada extração é um *move* de código puro (sem mudança de lógica), validado por build + suíte completa.

- **Passo 1 — `EditorGizmoRenderer` extraído de `Game`:** toda a renderização de overlays de edição (gizmos de mover/rotacionar/escalar, gizmo de collider e o frustum das câmeras) saiu para uma classe própria. Grupo coeso e puramente visual: só é chamado do passe de render, lê o estado de seleção/ferramenta/arrasto e não muda nada. Vive no mesmo pacote, então lê o estado de arrasto do gizmo (package-private) sem o `Game` expor API pública nova. **`Game.java`: 3126 → 2798 linhas.**
- Corrigido de passagem um javadoc malformado (`/**` órfão) que existia antes do `renderMoveGizmo`.
- **Passo 2 — `SceneOverlayRenderer` extraído de `Game`:** os desenhadores-folha dos overlays da cena — grade do editor (`drawGrid`), overlay do World (limites + barreiras), grade de pintura de barreiras, o passe de iluminação 2D e os alertas do editor. O buffer da máscara de luz (`lightMaskBuffer`, cache puramente de render) migrou para dentro dessa classe, saindo do estado do `Game`. **`Game.java`: 2798 → 2524 linhas** (−602 desde o início da Fase F).
- **Passo 3 — `EditorInputController` extraído de `Game`:** todo o input do editor — listeners de mouse do Canvas, seleção, arrasto de gizmo (mover/rotacionar/escalar), pintura de tiles/barreiras, panning e edição de collider. O estado transiente de interação (posições de início de arraste, flags de pincel, warp de mouse via `Robot`) migrou para o controller, saindo do `Game`. O `Game` mantém delegadores públicos finos (`cancelDrag`, `setupEditorPanning`) e o estado compartilhado (objeto selecionado, ferramenta, modo de arrasto lido pelo gizmo renderer). **`Game.java`: 2524 → 1839 linhas** (−1287 / −41% desde o início da Fase F). Validado em GUI: o editor sobe e seleção/gizmo/frustum renderizam corretamente.

Decomposição do `Game.java` concluída (3126 → 1749, −44%): `EditorGizmoRenderer`, `SceneOverlayRenderer`, `EditorInputController` e `GameLoop`.

- **Passo 5 — `ContentTools` extraído de `IgnisToolRegistry`:** as ferramentas MCP de conteúdo de cena (camadas de fundo, partículas, tilemaps, textos no mundo, luzes 2D — Fases C/D) saíram do registry (~120 ferramentas num arquivo) para uma classe própria. Recebe o registry e registra via `reg.add(...)`; os handlers leem o editor vivo e os helpers compartilhados (`findObject`, `resolveInProject`, `parseColor`, `schemaWith`, `clamp01`), tornados package-private. **`IgnisToolRegistry`: 2902 → 2470 linhas.** Validado por MCP (create_text_object/create_light_object/create_background_layer executam corretamente através da classe extraída).
- **Passo 6 — `InspectorSectionBuilder` extraído de `IgnisEditorApp`:** primeiro corte da maior god class (5271 linhas). Os 14 construtores de seção do Inspector (sprite, collider, rigidbody, health, câmera, camada de fundo, partículas, tilemap, texto, luz, hierarquia, scripts, tags, migração de collider legado) + os helpers de linha (`sectionTitle`, `doubleRow`, `intRow`, `checkRow`, `labeledInspectorRow`) saíram para uma classe própria. Recebe o `IgnisEditorApp` e lê o estado (objeto selecionado, `game`) e os callbacks (`markProjectDirty`, `rebuildInspectorExtras`, undo) dele, tornados package-private; o dispatch por tipo (qual seção construir) continua no editor. **`IgnisEditorApp`: 5271 → 4557 linhas.** Validado em GUI (captura da janela do editor: seleção de um `TextObject` renderiza as seções "Texto no Mundo", "Tags & Camadas" e "Componentes / Scripts" através da classe extraída, sem erros no console).
- **Passo 7 — `EditorMenuBuilder` extraído de `IgnisEditorApp`:** a barra de menus principal (`buildMenuBar`) e os menus de contexto da hierarquia e do viewport. Camada fina que liga cada item de menu/atalho a uma ação do editor (abrir/salvar projeto, criar entidade, undo/redo, diálogos…), agora chamadas via `app.` — ~40 membros do editor tornados package-private (enumerados pelo compilador). **`IgnisEditorApp`: 4557 → 4348 linhas.** Validado em GUI (a barra de menus Arquivo/Editar/Cena/Ferramentas/Visualizar/Ajuda renderiza pela classe extraída, editor funcional, sem erros).
- **Passo 9 — `EditorToolBarBuilder` e `EditorSceneOrganizer` extraídos de `IgnisEditorApp`:** a toolbar principal (abrir/build, play/stop, gizmos, câmera, seletor de cena — inicializa os campos de controle package-private do editor) e o domínio completo do organizador de cenários (seletor da toolbar, CRUD de cenas, troca de cena no editor e em Play via `SceneLoader` de scripts, diálogo "Gerenciar Cenários" com a seção de Mundo). **`IgnisEditorApp`: 4145 → 3709 linhas** (5271 na origem da Fase F, −30%). Validado em GUI via MCP: toolbar renderiza, seletor mostra a cena ativa, play/stop do TensuraGame íntegros.
- **Passo 10 — `IgnisToolRegistry` dividido por domínio:** as ~120 ferramentas restantes saíram do registry para sete classes coesas no mesmo padrão do `ContentTools` — `SoundTools` (música/SFX), `EditorWorkflowTools` (notas de asset, coordenação multi-agente, capturas), `SpriteAnimationTools` (`.anim.json` base + attach/play/stop na cena), `SceneObjectTools` (CRUD/transform/sprite/scripts, hierarquia, prefabs, extras), `CameraTools`, `UiTools` e `WorldTools` (colisão, info de cena, limites/grade/barreiras). O registry vira um coordenador fino: dispatch, helpers compartilhados (package-private) e os grupos headless × editor-vivo (`registerEditorTools`). Os nomes `SoundTools`/`SpriteAnimationTools` evitam sombrear `com.ignis.mcp.tools.AudioTools`/`AnimationTools` (servidor STDIO), que o `McpServerManager` importa por wildcard. **`IgnisToolRegistry`: 2486 → 790 linhas (−68%).** Validado por MCP em runtime (ferramentas de câmera, mundo, captura e prefab respondem pelo caminho novo).
- **Correções de memória após OutOfMemoryError em sessão de jogo** (crash nativo da JVM documentado em `hs_err_pid2452`, 15/07/2026): `compileAllScripts` recarrega o classloader uma vez por lote (antes, um por script); loaders aposentados limitados a 2 gerações abertas; log de diagnóstico por classe carregada (não por instância); `instantiatePrefab` sem log por clone durante PLAYING; cache de preload de áudio limitado a 64MB; editor via `javafx:run` com `-Xmx1536m`.
- **Passo 8 — `EditorPanelBuilder` extraído de `IgnisEditorApp`:** os painéis laterais — a árvore da hierarquia da cena (`buildHierarchy`, com células, filtro, drag-drop, menu de contexto e sincronização de seleção) e o navegador de assets do projeto (`buildAssetBrowser`). Escrevem os campos de painel do editor (`hierarchy`/`assetTree`, package-private) e ligam os handlers às ações/estado do editor via `app.`. **`IgnisEditorApp`: 4348 → 4145 linhas.** Validado em GUI: ambos os painéis renderizam pela classe extraída e a seleção de um objeto via MCP destaca-o na árvore (sincronização de seleção intacta).

## [1.11.0] - 2026-07-14

### Fase E do Motor Gráfico — plataforma (FPS/janela do jogo exportado, pipeline único)
Uma análise de viabilidade contra a arquitetura real corrigiu o plano em três pontos (ver Notas). Gamepad (3.12) foi adiado por decisão explícita.

### Adicionado
- **Render desacoplado do tick no jogo exportado [3.13]:** `Game.run()` agora avança a simulação em passos fixos de 60 Hz e renderiza **separadamente**, limitado por `fpsCap` (0 = sem limite, default 60 = comportamento histórico). Antes `tick()` e `render()` eram chamados juntos, travando o render em 60 fps — a interpolação anti-judder da Fase A (`getRenderAlpha`) **nunca beneficiava o player standalone**, só o editor (cujo `AnimationTimer` roda na taxa do monitor). Subir o `fpsCap` (ex.: 144) faz o jogo exportado interpolar entre ticks. Teto de ticks de recuperação evita a "espiral da morte".
- **Opções de janela no build:** `fpsCap` e `resizable` em `BuildConfig` → `runtime.json` → `GameRuntime`, expostos no diálogo de build do editor. (Resolução e tela cheia já funcionavam.)

### Alterado
- **Pipeline de render único [3.14]:** `render()` (AWT/BufferStrategy — o pipeline do jogo exportado) passa a **delegar o desenho da cena a `renderWorldTo()`**, que vira a fonte única do pipeline gráfico. Antes os dois duplicavam câmera, culling, entidades, iluminação e UI; o passe de luz da Fase D precisou ser inserido nos **dois**. `render()` mantém só o que é dele: BufferStrategy, ajuste de viewport e alertas.

### Removido
- **~200 linhas de código morto em `Game.java`:** a duplicação do pipeline, mais os métodos privados `renderSelection` e `drawWorldOrigin` — chamados apenas pelo `render()` legado sob `gameState == EDITING`, condição que **nunca ocorre** nesse pipeline (ele só pinta no jogo exportado, sempre em PLAYING; no editor o `Game` não é `displayable` e o método retorna cedo).

### Notas
- **Correções ao plano da Fase E:** (1) o item 3.13 dizia "hoje só janela fixa" — **desatualizado**: resolução e tela cheia já funcionavam; o gap real era o render travado no tick. (2) **"vsync" não é implementável** com a API pública do Java2D/AWT (só via classes internas `sun.*`) — substituído por limite de FPS/frame pacing, que é o que se quer na prática. (3) O item 3.14 (interface `Renderer` abstrata) seria especulativo com uma única implementação; a dívida **real** era a duplicação do pipeline — eliminá-la é o pré-requisito de verdade para trocar de backend no futuro.
- **3.12 Gamepad adiado:** exige biblioteca nativa (não há acesso a gamepad em Java puro). JInput está sem manutenção, e vendorizá-la no build offline (`libs/repository`) repetiria a dor que o FXEvents já deu duas vezes (case do path, bytecode de JDK incompatível). Decisão e motivo registrados no plano.
- Cobertura: 106 testes JUnit (+5 em `BuildConfigTest`), 0 violações de Checkstyle.

## [1.10.0] - 2026-07-13

### Fase D do Motor Gráfico — polimento (texto no mundo, nine-slice, luz 2D)
Fase D completa. Antes da implementação, o plano da fase passou por uma análise de viabilidade contra a arquitetura real: itens sem aderência foram descartados (ver Notas).

### Adicionado
- **Texto no mundo (`TextObject`) [3.9]:** entidade de cena com texto multi-linha (`\n`), família/tamanho de fonte, cor (com alpha), negrito/itálico e alinhamento horizontal (`LEFT|CENTER|RIGHT`). Renderiza no espaço do mundo com compensação do eixo Y invertido (mesmo idioma `translate + scale(1,-1)` das formas e do tilemap), rotaciona em torno do centro como as primitivas e dimensiona a própria caixa pelas `FontMetrics` (culling e seleção corretos). zIndex default 100. Round-trip `.ignis`. MCP: `create_text_object`, `set_text`. Inspector "Texto no Mundo" + menu **Cena > Criar Conteúdo > Texto no Mundo**.
- **Nine-slice na UI (`UIImage.ScaleMode.NINE_SLICE`) [3.10]:** composição 3×3 — cantos em tamanho fixo, bordas esticadas num eixo, miolo nos dois — para skins de painel/botão sem distorcer os cantos. Margens `sliceLeft/Right/Top/Bottom` serializadas; guard proporcional quando o destino é menor que a soma dos cantos e clamp das margens ao tamanho da imagem (garante ≥1px de miolo). MCP: `ui_create_image`, `ui_set_nine_slice`.
- **Iluminação 2D (`LightObject` + luz ambiente de cena) [3.11]:** iluminação por máscara de escuridão em Java2D puro (sem OpenGL). A cena tem `ambientLight` (o alpha é a intensidade da escuridão, serializado por cena); cada `LightObject` (cor, raio, intensidade) abre um degradê radial na escuridão via `AlphaComposite.DstOut` e tinge a área com um brilho colorido. Passe em screen-space logo antes da UI, nos dois pipelines (editor FX + player AWT), com buffer de máscara reaproveitado entre frames. Gizmo de raio no editor. MCP: `create_light_object`, `set_light_properties`, `set_scene_ambient_light`. Inspector "Luz 2D" + "Luz Ambiente da Cena" + menu **Cena > Criar Conteúdo > Luz 2D**.

### Notas
- **Fase D concluída.** Cobertura: 101 testes JUnit (+15: `TextObjectTest`, `NineSliceTest`, `LightingTest`, incluindo testes de saída de render que validam o flip Y do texto e o recorte DstOut da luz por pixels), 0 violações de Checkstyle.
- **Descartado do plano por não aderir à arquitetura:** `pulse` (oscilação) da luz; nine-slice no nível de `UIComponent` (fica só em `UIImage`); Inspector FX de nine-slice (a UI in-game é autorada por MCP, não inspecionada no editor FX); alinhamento vertical do texto (`VAlignment`) e seletor de fontes do SO (mantidos como futuros). A alegação de "aceleração por hardware" do design foi corrigida — o motor é Java2D em CPU.

## [1.9.0] - 2026-07-07

### Fase C do Motor Gráfico — conteúdo de cena
Recursos de conteúdo do plano do motor gráfico (parallax, atlas, partículas, tilemap) mais a hierarquia pai-filho. Todas as novas entidades round-trip no `.ignis` e têm ferramentas MCP (paridade HTTP + STDIO).

### Adicionado
- **Parallax (`BackgroundLayer`):** camada de fundo com fatores de parallax por eixo (0 = fixo no mundo, 1 = preso à câmera), tiling opcional e cor sólida. Renderiza atrás das entidades (zIndex -1000) e cobre a tela via culling opt-out. MCP: `create_background_layer`, `set_parallax_factor`.
- **Spritesheet/atlas:** o `spritePath` aceita região embutida — `sheet.png#x,y,w,h` (retângulo) e `sheet.png@col,row,tw,th` (célula de grade). `AssetResolver.loadImageRegion` recorta e cacheia sub-imagens compartilhando a decodificação do arquivo-base. Round-trip grátis (a região viaja no path). MCP: `set_sprite_region`.
- **Partículas (`ParticleEmitter`):** emissor com pool pré-alocado (sem alocação por frame), emissão por taxa contínua e rajada (`burst`), integração velocidade+gravidade, interpolação de cor/tamanho/alpha início→fim. MCP: `create_particle_emitter`, `particle_burst`, `set_particle_emitting`.
- **Tilemap (`TilemapObject`):** grade de tiles multi-camada a partir de um tileset, com culling por tile (reaproveita o recorte de atlas). MCP: `create_tilemap`, `add_tilemap_layer`, `set_tile`, `paint_tiles`, `clear_tilemap_layer`.
- **Hierarquia pai-filho:** `GameObject.setParent/clearParent` com offset local; filhos seguem translação e rotação do pai no Play (`Game.syncHierarchy`, ordem pai-antes-filho); rejeita ciclos e auto-parent; vínculo serializado por id. MCP: `set_parent`, `clear_parent`, `list_children`.
- **Fundação:** `GameObject.isCullable()` (opt-out de culling por câmera) e serialização de propriedades de entidade no `Scene.toJSON` (subclasses persistem seus campos; efeito colateral: `Camera.zoom` passa a round-trip pela cena).
- **Teste de GUI por agentes:** ferramentas MCP `capture_viewport` (render da Scene View em PNG), `capture_editor_window` (snapshot da janela inteira, via `Supplier<BufferedImage>` injetado pelo editor — o registry segue sem dependência de JavaFX) e `select_object` (seleciona por nome, atualizando Hierarchy/Inspector/gizmos). Salvam em `%TEMP%/ignis-captures/` e retornam o caminho; permitem que um agente valide visualmente a cena e a UI. A Fase C foi validada em GUI real por esse mecanismo.
- **Inspector das entidades novas:** seções "Camada de Fundo (Parallax)" (sprite, parallax X/Y, repetir X/Y), "Emissor de Partículas" (taxa, pool, vida, velocidade, gravidade, tamanhos, botão de rajada) e "Tilemap" (tileset, dimensões, adicionar/limpar camada), mais uma seção "Hierarquia" com o pai e botão de remover. Novo menu **Cena > Criar Conteúdo** para criá-las pela UI.

### Corrigido
- Logs informativos do bridge HTTP ("Bridge ativo"/"Bridge encerrado") apareciam como ERRO no console do editor (resquício da migração de logging) — reclassificados como INFO.
- `createEntity` sobrescrevia o tamanho das entidades de conteúdo com 50x50, ignorando o tamanho próprio (tile do fundo, grade do tilemap).

- **Follow de hierarquia no editor:** ao mover um objeto no editor (gizmo, campos X/Y/Rotação do Inspector ou a ferramenta MCP `set_object_transform`), o objeto fica onde foi solto — recapturando o offset local se tiver pai — e os filhos acompanham ao vivo, sem precisar do Play (`Game.syncHierarchyAfterEditorMove`).
- **Pintura de tilemap no viewport:** ferramenta `TILE_PAINT` (espelha o pincel de barreiras): com um tilemap selecionado, o Inspector expõe "Tile a pintar"/"Camada de pintura" e um toggle "Pintar Tiles"; clicar/arrastar no viewport pinta a célula, Ctrl apaga.
- **Preview de partículas no editor:** os emissores animam no modo de edição (`Game.previewEditorParticles`), para autorar o efeito sem entrar em Play.

### Notas
- **Fase C concluída.** Cobertura: 86 testes JUnit (33 novos), 0 violações de Checkstyle.

## [1.8.0] - 2026-07-03

### Adicionado
- **Arquitetura Entidade-Componente (EC) Unificada:** Criacao da classe abstrata `Component` contendo `gameObject` e hooks de ciclo de vida (`awake()`, `start()`, `update(deltaTime)`).
- **Integracao de IgnisScript com Component:** `IgnisScript` passa a herdar de `Component`, removendo campos locais e adotando o gerenciamento robusto de `awoken` para evitar execuções redundantes.
- **Metodos de Componentes em GameObject:** Adicionados os metodos `getComponent(Class<T>)`, `addComponent(Component)` e `update(float)` para obter, adicionar e atualizar pecas modulares acopladas.
- **Componente SpriteComponent:** Componente especializado de renderizacao que desenha texturas respeitando posicao, escala, flip e rotacao do pai. Integrado de forma transparente em todas as formas geometricas (`Square`, `Circle`, etc.) para permitir renderizacao totalmente desacoplada se anexado. Possui suporte reativo e automatico de fallback para recuperar a textura a partir do caminho de imagem do proprio GameObject caso nenhuma textura especifica seja configurada no componente.
- **Componente InputComponent:** Componente de movimentacao por teclado (W, A, S, D) que atualiza a transformacao do GameObject usando velocidade e delta time.
- **Classe Texture2D e Suporte no Inspector:** Wrapper `Texture2D` com suporte de serializacao em JSON. O Inspector do JavaFX (`IgnisEditorApp`) foi atualizado para exibir campos `Texture2D` com um seletor visual nativo de arquivos e botao de limpeza.

### Corrigido
- **Ambiguidade de Tipo em Editor.java:** Resolvido conflito de compilacao entre `java.awt.Component` e `com.ignis.core.Component` importando explicitamente a classe AWT.

## [1.7.0] - 2026-07-03

### Colaboração em tempo real — Parte 2.2b + robustez
Fecha as principais lacunas para a colaboração ficar "100% com boa compatibilidade".

- **Sincronização de código (`script`):** quando alguém edita um script no `FxCodeEditor`, o conteúdo é transmitido (debounce de 500 ms) pelo canal `script`; os demais salvam o arquivo local e atualizam o editor aberto daquele script, com **guarda anti-eco** (não retransmite uma edição que veio de outro colaborador). É o "editar os códigos juntos" pedido. (v1 last-write-wins; OT/CRDT fica para depois.)
- **Interpolação no convidado:** o convidado deixa de aplicar o snapshot "seco" a 12 Hz — agora **interpola** posições dos objetos e da câmera a cada frame em direção ao último snapshot (`CollabBridge.interpolateGuest`), deixando o espelhamento fluido em qualquer taxa de tela.
- **Streaming de assets:** se o convidado referencia um sprite que **não tem localmente**, ele pede ao host (`assetReq`); o host lê o arquivo e envia em base64 (`assetData`, limite 2 MB); o convidado grava no projeto e recarrega. Elimina o caso de "objeto aparece sem imagem" quando os projetos não estão 100% iguais.
- **Token de sessão (segurança):** o host pode definir uma **senha**; convidados a informam no `hello` e são recusados (`denied`) se não bater. Campos de senha adicionados ao painel de Colaboração (host e convidado). Sem senha, comportamento inalterado.

> Restam como polimento/otimização: **cursores** por participante (presença visual) e **delta** de cena (enviar só o que mudou, em vez do snapshot completo). Concorrência de edição de código (dois na mesma linha) ainda é last-write-wins.

## [1.6.0] - 2026-07-03

### Colaboração em tempo real — Parte 2.2a (edição convidado → host)
Agora os **convidados editam de fato** a cena, não só espelham. Mantém o modelo host-autoritativo.

- **Comandos convidado → host:** quando o editor está como **convidado** numa sessão, as ferramentas MCP que **mutam a cena/mundo/câmera** (≈37 ferramentas: `create_object`, `set_object_transform`, `delete_object`, `attach_script`, `play_game`, `block_rect`, `set_camera_follow`, …) são **encaminhadas ao host** em vez de aplicadas localmente (`IgnisToolRegistry.call` intercepta quando `role==GUEST`). O host executa o comando na sua cena autoritativa (via `CollabBridge.setCommandExecutor`, que reusa o registry do MCP) e o snapshot de ~12 Hz rebroadcasta o resultado a todos — inclusive quem enviou.
- **Reuso total:** o convidado edita usando **as mesmas 95 ferramentas** do MCP; nada de protocolo novo por ação. Ferramentas de leitura (`list_*`/`get_*`/`read_*`), áudio e coordenação continuam locais.
- **Zero impacto no uso single-user:** sem sessão ativa (`role==NONE`), nada é encaminhado — comportamento idêntico ao anterior.

> Ainda pendente na colaboração: sincronização de **código** (`script`) e **cursores**, streaming de assets, interpolação no convidado e delta. Segurança: hoje qualquer convidado pode comandar o host (adequado a colaboração confiável em VPN); permissões/papéis por convidado ficam para depois.

## [1.5.0] - 2026-07-03

### Colaboração em tempo real — Parte 2.1 (sincronização de cena)
Primeira etapa para deixar a colaboração (`com.ignis.collab`) 100%. Modelo **host-autoritativo**: o host é a fonte da verdade e os convidados espelham a cena em tempo real.

- **`com.ignis.collab.CollabBridge`:** liga a `CollabSession` (transporte TCP) ao `Game` do editor. **Host** transmite, a ~12 Hz, um snapshot da cena pelo canal `scene` — nome, tipo, transform, zIndex, visibilidade e todas as propriedades visuais (opacity/flip/scale), sprite, além da câmera (posição/zoom) e do estado de Play. **Convidado** aplica o snapshot ao seu próprio `Game` na thread de UI: cria/atualiza/remove objetos para espelhar o host (sprite só recarrega se mudou), sem rodar a simulação local — puro espelho.
- **Efeito:** o convidado vê os objetos se movendo, sendo criados/removidos e o **host jogando (Play)** em tempo real, com o mesmo enquadramento de câmera. Funciona por IP direto ou VPN (o transporte já era TCP linha-JSON).
- Integrado ao `AnimationTimer` do editor (`CollabBridge.init(game)` + `onEditorFrame()` por frame, com throttle interno).

> Escopo desta etapa: espelhamento **host → convidado** (o mais visual). Faltam, na Parte 2.2: comandos **convidado → host** (colaborador editando de fato), sincronização de **código** (`script`) e de **cursores**, streaming de assets, e interpolação no convidado (hoje o movimento chega a 12 Hz). Pré-requisito de uso: convidado e host no **mesmo projeto** (assets locais). Guia completo no vault.

## [1.4.0] - 2026-07-03

### Adicionado — Coordenação multi-agente pelo MCP
Permite que **várias IAs** (ex.: Claude + Gemini) trabalhem no mesmo projeto ao mesmo tempo, **conversando e dividindo o trabalho pelo próprio MCP**, sem conflitos. Nova classe `com.ignis.mcp.McpCoordination` (singleton em memória) com quatro áreas: presença de agentes, mural de mensagens (broadcast e direcionadas, com `seq` para polling incremental), *claims* (reserva de recurso com expiração de 10 min) e quadro de tarefas.

- **11 ferramentas MCP:** `agent_join`, `agent_list`, `send_message`, `read_messages`, `claim`, `release`, `list_claims`, `create_task`, `assign_task`, `complete_task`, `list_tasks`.
- **Enforcement de conflito:** `write_script` e `create_script` aceitam um `agent` opcional; se o script (`script:<nome>`) estiver reservado por **outro** agente, a edição é recusada com instrução para combinar pelo mural. Sem `agent`, comportamento inalterado (retrocompatível).
- Estado acessado sempre na thread de UI (via `IgnisMcpBridge`), logo serializado; ainda assim os métodos são `synchronized`. Validado por teste isolado (2 agentes: presença, claim negado, mensagens diretas/broadcast, release, tarefas).

Fluxo típico: cada IA chama `agent_join` → o usuário cria tarefas (`create_task`) e as distribui (`assign_task`) → as IAs reservam o que vão editar (`claim`), conversam (`send_message`/`read_messages`) e liberam ao terminar (`release`). Total do registry: **95 ferramentas**.

> Limitação: o estado é em memória (perde ao fechar o editor) — persistência em disco fica como follow-up. Próximo passo pedido pelo usuário: garantir a colaboração em tempo real (`com.ignis.collab`) 100%.

## [1.3.1] - 2026-07-03

### Corrigido
- **Orientação de z-order na hierarquia (A):** o painel de hierarquia agora mostra o `zIndex` ao lado de cada objeto (ex.: `Background   z:0`) e o nó raiz virou uma legenda (`Cena  (z menor = atras)`). Esclarece que a ordem de render segue o zIndex — não a posição na lista — resolvendo a confusão de "o Background aparece no topo da hierarquia mas é desenhado atrás". A seleção da hierarquia é por índice, então o texto novo não afeta o clique.
- **Câmera fantasma (B):** ao abrir um projeto, `clearGameCameras()` limpava a lista de câmeras mas deixava a `mainCamera` órfã — `getActiveCamera()` a retornava, mas `getCameras()`/`list_cameras` (MCP) ficavam vazios. Agora a `mainCamera` é recolocada na lista após o clear, mantendo o estado consistente entre o motor, o editor e o MCP.

## [1.3.0] - 2026-07-03

### Sistema de Mundos — Fase 1 (limites + barreiras + colisão)
Nova mecânica de mundo (vault: `ignisengine-sistema-de-mundos`), a partir do feedback de que o jogador não conseguia percorrer/enxergar o mapa todo e faltavam barreiras.

- **Classe `com.ignis.core.World`:** limites do mapa (retângulo opcional) + grade de barreiras (`cellSize` + células sólidas), com colisão AABB resolvida **por eixo** (`resolveMovement` — permite deslizar em parede), propriedades (nome, cor ambiente) e serialização.
- **Integração no motor:** `Game` tem um World vivo; `GameObject.worldCollision` (flag opt-in) faz o `tick()` clampar a posição contra limites/barreiras (usa `prevX/prevY` da interpolação como origem do movimento). A **câmera deixa de mostrar além do mapa**: quando há limites, o Game clampa a posição da câmera ativa a um retângulo *inset* pela metade da área visível (resolve "visibilidade centralizada") — funciona com follow, shake ou posicionamento manual por script. **Overlay no editor**: limites (contorno azul) e barreiras (células vermelhas) desenhados em modo de edição.
- **Serialização:** o World é salvo junto da cena (`Scene.toJSON/fromJSON`) e o flag `worldCollision` por objeto; `IgnisEditorApp` sincroniza `game.world ↔ scene.world` no load/save. Também passou a serializar o `worldCollision` no nível do Scene.
- **11 ferramentas MCP:** `set_world_bounds`, `clear_world_bounds`, `set_world_grid`, `block_rect`, `unblock_rect`, `block_cell`, `unblock_cell`, `clear_barriers`, `set_object_world_collision`, `set_world_property`, `get_world_info`. Total do registry: **84 ferramentas**.

> Fase 2 (interiores/áreas trocáveis, portais, propriedades por mundo, persistência do jogador entre áreas) e Fase 1.5 (pintar barreiras arrastando o mouse no editor) estão desenhadas no plano do vault. Limitações da Fase 1: a colisão testa a posição-alvo (não o trajeto — risco de tunelar a velocidades > 1 célula/tick) e teleportar um objeto com `world_collision` para dentro de uma parede é revertido.

## [1.2.0] - 2026-07-02

### Motor gráfico 2D — Fase B (fundações de cena)
Segunda fase do plano do motor gráfico (vault: `ignisengine-plano-motor-grafico`).

- **Propriedades visuais no GameObject (B.1):** novos campos `opacity` (0–1), `flipX`/`flipY` (espelhamento), `scaleX`/`scaleY` (multiplicadores visuais), aplicados pelo pipeline de render (`AlphaComposite` para opacidade; transform em torno do centro para flip/escala) — valem para **todas** as formas sem editar cada `render()`. Expostos via MCP `set_object_visual`. *Tint (multiply) fica deferido: Java2D não tem blend multiply nativo.*
- **zIndex serializado + ordem de render (B.2):** cada `GameObject` tem `zIndex` (int); o render passou a ordenar por ele (sort estável — empate mantém a ordem da hierarquia, modelo "Order in Layer" da Unity), em vez de depender só da posição na lista. MCP `reorder_object_z` agora seta `zIndex` (`top`/`bottom`/`up`/`down`/numérico).
- **Câmera nativa: follow / shake / bounds (B.7):** `Camera.follow(target, smoothing)`, `shake(intensity, duration)` (decaimento linear, aplicado sobre a posição-base sem contaminá-la — corrige o shake antigo via script que deslocava sem restaurar) e `setBounds(...)`; avançados por `Camera.update(dt)`, chamado por `Game.tick()` para a câmera ativa no Play. MCP: `set_camera_follow`, `stop_camera_follow`, `camera_shake`, `set_camera_bounds`, `clear_camera_bounds`.
- **Bug corrigido — rotação não era persistida:** `rotation` do GameObject **não era serializada** no `.ignis` (girar → salvar → reabrir perdia o ângulo). Agora `rotation` (e os novos `zIndex`/`opacity`/`flip*`/`scale*`) são serializados no nível do `Scene` (`toJSON`/`fromJSON`), todos opcionais na leitura para compatibilidade com projetos antigos.

Total de ferramentas MCP: **73** (as 67 anteriores + `set_object_visual`, `set_camera_follow`, `stop_camera_follow`, `camera_shake`, `set_camera_bounds`, `clear_camera_bounds`).

> Limitação conhecida: a interpolação de render (Fase A) suaviza as **entidades**, mas a **câmera** ainda não é interpolada — panorâmicas rápidas com `follow` podem apresentar leve judder em monitores > 60 Hz. Anotado como follow-up (extensão da A.2).

## [1.1.0] - 2026-07-02

### Motor gráfico 2D — Fase A (fundações do pipeline de render)
Primeira fase do plano do motor gráfico (`doc/` no vault: `ignisengine-plano-motor-grafico`).

- **Thread-safety entre simulação e render (A.1):** `Game.tick()` agora é `synchronized` no mesmo monitor de `render()`/`renderWorldTo()`. A simulação roda na thread do loop do jogo enquanto o editor JavaFX renderiza na thread do `AnimationTimer`; sem exclusão mútua, o render podia ler um objeto com `x` já atualizado e `y` ainda antigo (frame "rasgado"). A lista de entidades já era `CopyOnWriteArrayList` (protegia a lista, não os campos dos objetos).
- **Interpolação de render / anti-judder (A.2):** a simulação é fixa em 60 Hz, mas o editor renderiza na taxa do monitor (75/120/144 Hz). Cada `GameObject` agora guarda o transform do início do tick anterior (`capturePreviousTransform()`, chamado no começo de `tick()`), e os dois pipelines de render interpolam a posição (`prevX/prevY → x/y`) por um `alpha` = fração do tick decorrida (`Game.getRenderAlpha()`), via translate do `Graphics2D`. Teleportes (salto > 256 px/tick) são cortados a seco em vez de deslizar pela tela. Fora do modo Play, `alpha = 1.0` → identidade (editor em edição não é afetado). Campos `prev*` são `transient` (não serializados).
- **Culling por câmera (A.3):** os dois loops de render agora pulam entidades cujo AABB (conservador, usando a diagonal + folga, para cobrir rotação) não intersecta o retângulo visível da câmera (`Camera.getVisibleWorldBounds()`). Só ativo quando a transform de câmera está aplicada; nunca corta algo parcialmente visível. Reduz o custo de render em cenas grandes (pré-requisito para tilemaps).

> Observação: por hora `tick()` e o render compartilham o lock durante o desenho inteiro — correto, mas o plano prevê um *snapshot de render* por tick como otimização futura (Fase A do plano, item de performance). Verificação visual do anti-judder requer reabrir o editor num monitor > 60 Hz.

## [1.0.9] - 2026-07-02

### Corrigido
- **Rotação dupla no render de GameObjects (motor 2D):** os dois pipelines de renderização (`Game.render()` legado/AWT e `Game.renderWorldTo()` do editor JavaFX) aplicavam a rotação da entidade **antes** de chamar `entity.render()`, mas todas as 7 formas concretas (`Square`, `Circle`, `Triangle`, `Star`, `Pentagon`, `Player`, `MergedShape`) já rotacionam internamente no próprio `render()`. Resultado: um objeto com rotação 30° era desenhado a 60°, desalinhado do contorno de seleção/gizmos (que rotacionam 1×) e da física de colisão (que usa o ângulo lógico 1×). A rotação externa foi removida dos dois pipelines — o desenho agora bate com o ângulo lógico, a seleção e os colliders.

### Adicionado
- **Paridade STDIO ↔ Registry no MCP:** `McpServerManager` agora serve o mesmo conjunto base do `IgnisToolRegistry` via adapter genérico (`registerRegistryTools`, ToolDef→SDK). As classes legadas `CoreTools`, `NoteTools` e `AnimationTools` foram desregistradas (duplicavam o registry; arquivos mantidos como histórico). Permanecem exclusivas do STDIO apenas `AudioTools` (processamento WAV) e `ImageTools` (documentos de imagem em camadas, com estado em memória).
- **`remove_sprite_background` no registry HTTP:** a ferramenta de remoção de fundo (antes exclusiva do STDIO) foi portada para o `IgnisToolRegistry` com validação anti path-traversal — agora disponível no bridge HTTP/dashboard e nos dois transportes, a partir de uma única implementação compartilhada (`ImageTools.removeBackground`, extraída como método estático público). Total do registry: **67 ferramentas** (26 base + 41 com editor vivo).
- **Log de auditoria das chamadas MCP:** `IgnisToolRegistry.call()` registra cada chamada de agente (`[MCP] nome {args truncados} -> ok/ERRO (Xms)`) via `System.out`, exibida ao vivo no Console do editor (`FxConsolePanel`) — fecha a pendência de auditoria documentada no guia do MCP.

## [1.0.8] - 2026-07-02

### Adicionado
- **Console Dashboard Web Premium para o MCP**: Implementacao do endpoint raiz `/` (e `/index.html`) no `McpHttpBridge`, servindo um dashboard interativo moderno e responsivo (HTML5/CSS3/JS, sem dependencias adicionais). A interface oferece busca instantanea em tempo real de todas as 66 ferramentas registradas, visualizacao interativa do schema de parametros com campos de formulario gerados dinamicamente para execucao imediata no sandbox (com saida formatada em console escuro), monitoramento visual do status do bridge e botoes de atalhos rapidos (Status da Cena, Listar Objetos, Play Game, Stop Game e Salvar Projeto) sem emojis ou placeholders, creditando o autor ThyagoToledo.
- **Polimento de Sprites 2D dos Personagens e Remocao de Quadriculados**: Geracao e substituicao dos sprites do heroi (`hero.png`) e do slime (`slime.png`) na pasta de assets do projeto (`projects/MyGame/project/assets/sprites/`) por sprites autorais transparentes em alta resolucao de pixel art 2D. Aprimoramento da ferramenta `remove_sprite_background` no MCP para aceitar o modo `"auto"` (que coleta cores de bordas/cantos) e listas separadas por virgula, limpando completamente fundos quadriculados (checkerboards) de transparencia e deixando os personagens transparentes sobre o gramado.
- **Mecanica de Exploracao com HUD de Instrucoes**: Adicionado cenario de fundo (`grass.jpg`) em Z-order `0`, fase de exploracao controlada por WASD/Setas com camera seguindo o jogador e aviso amarelo no HUD (*"EXPLORACAO: Mova o Heroi usando as teclas WASD ou Setas ate o Slime!"*) limpando ao iniciar o combate por proximidade.
- **Z-Order Explicito no MCP**: Aprimorada a ferramenta `list_scene_objects` para retornar o indice numerico de empilhamento de renderizacao de cada GameObject (Z-index `[0]` ate `[N]`), permitindo a agentes remotos e programadores controlarem e entenderem quais objetos estao na frente/atras de quais na cena.

### Corrigido
- **Compatibilidade de Escape de Text Block em Java**: Correcao de erros de compilacao relacionados aos caracteres de escape em literais de template JavaScript (`\\${...}`) inseridos dentro do text block Java de `McpHttpBridge.java`.
- **Auto-carregamento do Ultimo Projeto no Startup**: Resolvido problema de bloqueio de inicializacao do MCP na porta 8898 adicionando auto-load do ultimo projeto ativo (`EditorPrefs.getLastProject()`) direto no startup do editor JavaFX.

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
