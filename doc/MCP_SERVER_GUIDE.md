# Servidor MCP e Bridge HTTP do IgnisEngine

> Documento vivo — atualizado em 01/07/2026. Descreve a interface de **IA & MCP**
> nas Configurações do editor, o servidor MCP e o bridge HTTP local que expõe as
> ferramentas do motor para agentes de IA. **66 ferramentas** registradas (25
> disponíveis sempre, 41 adicionais com o editor vivo).

---

## 1. Visão geral

O IgnisEngine expõe um conjunto de **ferramentas** (ler a árvore do projeto, criar
e editar scripts, compilar etc.) para que agentes de IA possam operar a engine. Há
dois transportes para essas mesmas ferramentas:

| Transporte | Classe | Uso |
|-----------|--------|-----|
| **STDIO** (MCP clássico) | `com.ignis.mcp.McpServerManager` | Clientes MCP que *lançam* o processo (Claude Desktop, Cursor). Ativado por `--mcp <projeto>`. |
| **HTTP local (URL)** | `com.ignis.mcp.McpHttpBridge` | Agentes que se conectam por **URL** — inclusive IAs usando APIs Gemini/NVIDIA e a futura IA embarcada. |

A **fonte canônica** das ferramentas é a classe `com.ignis.mcp.IgnisToolRegistry`.
Ela descreve cada ferramenta (nome, descrição, schema JSON, executor) de forma
independente do SDK do MCP, garantindo **paridade total** entre os transportes e a
futura IA agêntica. Toda execução passa por `IgnisMcpBridge.runOnFxThread(...)`,
mantendo as mutações do Scene Graph na thread de UI do JavaFX.

```
                         ┌──────────────────────────┐
   Claude/Cursor  ──────▶│  McpServerManager (STDIO) │──┐
                         └──────────────────────────┘  │
                                                        ├──▶ IgnisToolRegistry ──▶ ScriptManager / Game
   Gemini / NVIDIA ─────▶┌──────────────────────────┐  │        (thread de UI via IgnisMcpBridge)
   IA embarcada (futura) │  McpHttpBridge (HTTP/URL) │──┘
                         └──────────────────────────┘
```

---

## 2. Como ativar (interface do editor)

`Configurações → IA & MCP → Servidor MCP`:

1. Abra um projeto (o botão fica desabilitado sem projeto — as ferramentas operam
   sobre a raiz do projeto ativo).
2. Ajuste a **Porta** (padrão `8790`).
3. (Opcional) Marque **Expor na rede/VPN (0.0.0.0)** para permitir conexões de
   outras máquinas (LAN ou VPN). Sem isso, o bridge escuta apenas em `127.0.0.1`.
4. (Opcional) Defina um **Token** — quando preenchido, os endpoints exigem o header
   `Authorization: Bearer <token>`.
5. Clique **Ativar servidor MCP**. A **URL** aparece no campo abaixo; use **Copiar
   URL** para colar na configuração do agente.

O estado (ligado/porta/exposição/token) persiste em `~/.ignis/editor-prefs.json`.
Se **Ativar** ficou marcado, o bridge sobe automaticamente ao abrir o projeto
(`IgnisEditorApp.maybeAutoStartMcp()`), e é encerrado ao fechar o editor.

---

## 3. Endpoints HTTP

Base: `http://<host>:<porta>` (ex.: `http://127.0.0.1:8790`).

| Método | Caminho | Descrição |
|--------|---------|-----------|
| `GET`  | `/health` | Sanidade: `{"status":"ok","tools":N,"authRequired":bool}` |
| `GET`  | `/mcp/tools` | Lista as ferramentas com `name`, `description`, `inputSchema` |
| `POST` | `/mcp/call` | Executa `{"name":"...","arguments":{...}}` → `{"ok":true,"result":"..."}` |

Exemplos (`curl`):

```bash
curl http://127.0.0.1:8790/mcp/tools

curl -X POST http://127.0.0.1:8790/mcp/call \
  -H "Content-Type: application/json" \
  -d '{"name":"get_project_tree","arguments":{}}'

# Com token:
curl -X POST http://127.0.0.1:8790/mcp/call \
  -H "Authorization: Bearer MEUTOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"create_script","arguments":{"scriptName":"EnemyAI"}}'
```

---

## 4. Ferramentas registradas

### 4.1 Sempre disponíveis (25) — `IgnisToolRegistry.registerDefaults()`

Funcionam mesmo no modo headless (`--mcp <projeto>`, transporte STDIO), pois operam
em arquivos do projeto ou em singletons estáticos do motor — não exigem o editor
JavaFX aberto.

**Projeto e scripts (8)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `get_project_tree` | — | Árvore recursiva de arquivos/pastas do projeto |
| `list_scripts` | — | Lista os IgnisScripts disponíveis |
| `read_script` | `scriptName` | Lê o código-fonte de um script |
| `write_script` | `scriptName`, `content` | Sobrescreve o código de um script |
| `create_script` | `scriptName` | Cria script novo pelo template do motor |
| `compile_project` | — | Compila todos os scripts e retorna o total |
| `read_file` | `path` | Lê arquivo texto (relativo à raiz, com proteção anti path-traversal) |
| `generate_sprite` | `name`, `shape?`, `width?`, `height?`, `color?`, `outlineColor?`, `symbol?` | Gera um sprite 2D procedural (forma+cor+símbolo) via `Graphics2D`, sem depender de imagem externa |

**Áudio (7)** — via `com.ignis.core.IgnisSoundEngine.getInstance()` (singleton estático)

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `play_sound_preview` | `soundPath`, `volume?` | Toca um efeito sonoro de preview |
| `play_music_preview` | `musicPath`, `loop?` | Toca uma música de fundo de preview |
| `stop_all_audio` | — | Para todos os sons e a música |
| `pause_resume_music` | `action?` (`pause`/`resume`/`toggle`) | Pausa/retoma a música preservando a posição |
| `set_audio_volumes` | `masterVolume?`, `musicVolume?`, `sfxVolume?` | Ajusta os volumes globais (0.0–1.0) |
| `list_audio_assets` | `category?` (`sounds`/`music`/`all`) | Lista os arquivos de áudio do projeto |
| `get_audio_status` | — | Estado atual: música tocando/pausada, volumes |

**Assets e notas (6)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `list_assets` | `category?` | Lista arquivos em `assets/<categoria>` (todas se omitido) |
| `import_asset_from_path` | `sourcePath`, `category`, `overwrite?` | Copia um arquivo externo para `assets/<category>/` |
| `list_notes` | — | Lista as páginas de notas/wiki do projeto |
| `create_note` | `title` | Cria uma nova nota |
| `read_note` | `fileName` | Lê título e conteúdo de uma nota |
| `write_note` | `fileName`, `title`, `content` | Sobrescreve uma nota existente |

**Animação — definições (4)** — arquivos `.anim.json` via `com.ignis.animation.AnimationIO`

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `list_animations` | — | Lista as animações do projeto (loop, curva, nº de frames) |
| `create_animation` | `name`, `loop?`, `curveType?` | Cria um clipe de animação vazio |
| `add_animation_frame` | `animName`, `spritePath`, `duration` | Adiciona um keyframe ao final da animação |
| `read_animation` | `animName` | Lê a definição JSON completa da animação |

### 4.2 Somente com editor vivo (41) — `attachLiveEditor(...)`

Quando o bridge roda **dentro do editor JavaFX** (não no modo headless), o
`IgnisEditorApp` registra o *contexto vivo* (`McpService.setEditorContext(...)`) — o
`Game` da cena e hooks de Play/Stop/Refresh/Save — habilitando estas ferramentas.
Todas rodam na thread de UI via `IgnisMcpBridge`; os hooks invocam os métodos reais
do editor, então botões e viewport ficam sincronizados.

**Cena e Play (9)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `list_scene_objects` | — | Lista os GameObjects da cena (nome, pos, tamanho, scripts) |
| `create_object` | `name`, `type?`, `x?`, `y?`, `width?`, `height?` | Cria uma forma (`square`/`circle`/`triangle`/`star`/`pentagon`/`player`) — `GameObject` é abstrato, instanciado via fábrica |
| `set_object_transform` | `name`, `x?`, `y?`, `width?`, `height?`, `rotation?` | Move/redimensiona/rotaciona um objeto |
| `set_object_sprite` | `name`, `path` | Define o sprite do objeto (validado contra path-traversal) |
| `delete_object` | `name` | Remove um GameObject da cena |
| `attach_script` | `objectName`, `scriptName` | Anexa um IgnisScript ao objeto |
| `play_game` / `stop_game` | — | Equivalem aos botões Play/Stop do editor |
| `save_project` | — | Salva a cena no arquivo `.ignis` |

**Animação — runtime (4)** — via `GameObject.getOrCreateAnimator()`

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `attach_animation` | `objectName`, `animName`, `setAsDefault?` | Anexa uma animação ao `Animator` do objeto |
| `play_animation` | `objectName`, `animName`, `waitForCurrent?` | Toca uma animação já anexada |
| `stop_animation` | `objectName` | Para a animação e restaura o sprite anterior |
| `get_animation_status` | `objectName` | Animação atual, se está tocando, animações disponíveis |

**Prefabs (5)** — via `Game.getPrefabManager()`

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `list_prefabs` | — | Lista os prefabs do projeto |
| `save_prefab` | `objectName`, `prefabName` | Salva um objeto da cena como prefab reutilizável |
| `instantiate_prefab` | `prefabName`, `x?`, `y?` | Instancia um prefab (`x`/`y` juntos ou nenhum — usa a posição salva) |
| `delete_prefab` | `prefabName` | Remove um prefab do disco |
| `prefab_exists` | `prefabName` | Verifica se um prefab existe |

**Colisão (1)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `set_object_collider` | `objectName`, `colliderType` (`NONE`/`AABB`/`CIRCLE`/`POLYGON`), `collisionMode?`, `layer?`, `mask?` | Configura tipo, modo e camada/máscara de colisão de um objeto |

**Câmera (5)** — via `com.ignis.core.Camera` + `Game.addCamera`/`setMainCamera`

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `list_cameras` | — | Lista as câmeras da cena (posição, zoom, qual é a ativa) |
| `create_camera` | `name`, `x?`, `y?`, `zoom?`, `rotation?`, `setActive?` | Cria uma nova câmera |
| `set_active_camera` | `name` | Define a câmera principal/ativa |
| `set_camera_transform` | `name`, `x?`, `y?`, `zoom?`, `rotation?` | Move/aplica zoom/rotaciona uma câmera |
| `convert_coordinates` | `direction` (`world_to_screen`/`screen_to_world`), `x`, `y` | Converte coordenadas usando a câmera ativa (mira/HUD) |

**UI in-game direta, sem escrever script (9)** — via `com.ignis.core.ui.*`

Antes, criar UI (botões, barras, texto) só era possível escrevendo um `IgnisScript`
(`createButton`/`createLabel`/... protegidos). Estas ferramentas usam o mesmo
`UICanvas` diretamente, permitindo montar HUD/menus **sem nenhum script**:

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `ui_create_label` | `name`, `text`, `x?`, `y?`, `width?`, `height?`, `color?` | Cria um texto na UI |
| `ui_create_button` | `name`, `text`, `x?`, `y?`, `width?`, `height?`, `removeOnClick?` | Cria um botão (opcionalmente auto-removível ao clicar) |
| `ui_create_progressbar` | `name`, `x?`, `y?`, `width?`, `height?`, `value?`, `maxValue?`, `fillColor?` | Cria uma barra (HP/mana/loading) |
| `ui_create_panel` | `name`, `x?`, `y?`, `width?`, `height?`, `backgroundColor?`, `layout?` | Cria um painel container (`NONE`/`VERTICAL`/`HORIZONTAL`/`GRID`) |
| `ui_set_text` | `name`, `text` | Altera o texto de um label/botão existente |
| `ui_set_progress_value` | `name`, `value`, `maxValue?` | Atualiza o valor de uma barra |
| `ui_remove_element` | `name` | Remove um elemento pelo nome |
| `ui_clear_all` | — | Remove todos os elementos de UI |
| `ui_list_elements` | — | Lista os elementos atuais (nome, tipo, posição, tamanho) |

**Extras de GameObject (7)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `set_object_visible` | `name`, `visible` | Mostra/esconde um objeto |
| `set_object_name_color` | `name`, `color` | Cor do nome na hierarquia do editor |
| `reorder_object_z` | `name`, `position` (`top`/`bottom`/`up`/`down`/índice) | Altera a profundidade (z-order) |
| `get_object_info` | `name` | Dump completo: transform, tipo, visibilidade, sprite, scripts, collider |
| `find_objects_by_type` | `type` | Busca objetos por tipo (ex: `Square`, `Player`) |
| `remove_script_from_object` | `objectName`, `scriptName` | Remove um script anexado |
| `clear_scene` | `preserveCameras?` | Remove todos os GameObjects da cena |

**Info geral (1)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `get_scene_info` | — | Resumo: nº de objetos, câmeras, estado do jogo (edição/play) |

> Importante: ferramentas novas exigem reiniciar o editor após atualizar o build
> (Java não faz hot-reload). Ao reabrir com o MCP habilitado, o bridge sobe já com o
> contexto vivo. `GET /health` retorna `tools: N` — confira o total antes de assumir
> que uma ferramenta nova está disponível.
>
> **Nota de arquitetura (Project/Scene):** as ferramentas de cena usam sempre o
> `Game` **vivo** do editor (`liveGame.getEntities()`/`getCameras()`), nunca leem o
> `.ignis` do disco. Isso é proposital — `Game` não guarda uma referência pública a
> `Project`/`Scene`; ele opera sobre listas soltas de entidades/câmeras. Ferramentas
> que carregassem o projeto do disco (`IgnisProjectIO.load`) veriam o último estado
> **salvo**, divergente do que está sendo editado ao vivo — por isso não foram
> implementadas ferramentas de troca/criação de cena via disco nesta rodada (ver
> Pendências).

---

## 5. Exemplo funcional: jogo de combate por turnos criado via MCP

Validação de ponta a ponta (30/06–01/07/2026): um agente montou um jogo completo
usando **somente chamadas HTTP** ao bridge, sem tocar no editor manualmente.

1. `generate_sprite` × 2 → `assets/sprites/hero.png` (quadrado azul, símbolo "H") e
   `assets/sprites/slime.png` (blob verde, símbolo "S").
2. `create_script` + `write_script` + `compile_project` → dois scripts:
   - **`CombatManager.java`** (anexado ao Hero): monta a UI de combate
     (`createProgressBar`, `createLabel`, `createButton`), alterna turnos, calcula
     dano aleatório, aplica redução ao defender, detecta vitória/derrota e aplica um
     "shake" visual em quem leva o hit.
   - **`IdleBob.java`** (anexado ao Slime): flutuação senoidal independente, sem
     depender de outro script.
3. `create_object` × 2 (Hero, Slime) + `set_object_sprite` × 2 + `attach_script` × 2
   + `save_project` + `play_game`.

**Bug real encontrado e corrigido no processo:** ao testar, os botões/barras da UI
não apareciam no viewport. Causa: `Game.renderWorldTo` (pipeline do editor JavaFX)
nunca desenhava o `UICanvas` — esse desenho só existia no `render()` legado
(AWT/Swing), que a FX não chama. Qualquer UI criada por script (`createButton`,
`createLabel`, `createProgressBar`, …) ficava **ativa na lógica, mas invisível** no
editor FX. Corrigido adicionando o mesmo bloco de overlay em screen-space
(`uiCanvas.updateScreenSize` + `uiCanvas.renderAll` + `renderAlerts`) ao final de
`renderWorldTo` — conserta a UI in-game para **qualquer** jogo no editor FX, não só
este exemplo. Detalhes também no vault (Brain), doc
`ignisengine-fix-ui-canvas-renderworldto`.

Também foi encontrado e corrigido durante a mesma sessão: `type:"player"` em
`create_object` instancia a classe `Player`, que tem um `tick()` demo hardcoded
(`x += speed`, anda sozinha). Para objetos controlados por script, usar
`type:"square"` (ou outra forma) — o `tick()` dessas classes é vazio, deixando o
movimento inteiramente por conta dos scripts anexados.

---

## 6. Segurança

- **Bind local por padrão** (`127.0.0.1`): nada é exposto sem ação explícita.
- **Token Bearer opcional** protege `/mcp/tools` e `/mcp/call` (o `/health` fica
  aberto de propósito, para diagnóstico).
- **Anti path-traversal centralizado:** todo caminho relativo fornecido por um
  agente passa por `resolveWithin(base, relative)` — resolve o caminho de forma
  canônica e só aceita se o resultado ficar **dentro** de `base` (comparação exata
  de prefixo com separador, não apenas `startsWith` textual — evita escapar para uma
  pasta irmã, ex.: `Project` → `ProjectEvil`). Usado por `read_file`,
  `set_object_sprite`, `add_animation_frame` (valida `spritePath`), `list_assets`
  (valida `category`), `import_asset_from_path` (valida a **categoria de destino** —
  o `sourcePath` de origem fica livre, pois importar de fora do projeto é o
  propósito da ferramenta) e `read_note`/`write_note` (escopadas a `notes/`, não à
  raiz do projeto).
- **Nomes derivados sanitizados:** `create_note` deriva o nome do arquivo do
  `title` recebido — remove qualquer caractere fora de `[a-z0-9-_ ]` antes de gravar,
  para que um título tipo `"../../evil"` não escreva fora de `notes/`.
- **Filtro de symlinks** nas listagens (`list_notes`, `list_assets`,
  `list_audio_assets`): arquivos que são links simbólicos são ignorados, para não
  expor conteúdo fora do projeto via um link plantado na pasta. Exige acesso prévio
  ao filesystem local para ser explorado — mitigação defensiva, não o vetor
  principal (esse é o path-traversal via argumentos HTTP, já coberto acima).
- Ao **expor na rede/VPN**, recomenda-se **sempre** definir um token.

> Esses pontos foram encontrados por uma revisão adversarial (workflow com 4
> agentes auditando cada categoria de ferramenta contra o código-fonte real do
> motor) rodada logo após a implementação das 49 ferramentas novas — 5 caminhos de
> path-traversal de alta severidade e 1 bug de lógica (`instantiate_prefab` ignorava
> `x`/`y` silenciosamente se só um dos dois fosse informado, caindo para a posição
> salva no prefab — agora exige os dois juntos ou nenhum, com erro claro). Todos
> corrigidos antes do primeiro teste ao vivo.

---

## 7. Pendências / próximos passos

- **Paridade STDIO ↔ Registry:** hoje o `McpServerManager` (STDIO) ainda registra as
  ferramentas legadas diretamente no SDK; migrá-lo para consumir o `IgnisToolRegistry`
  elimina a duplicação e garante o mesmo conjunto nos dois transportes.
- **Transporte MCP-over-HTTP oficial (SSE):** o bridge atual é um JSON/HTTP simples
  (suficiente para function-calling de LLMs). Para clientes MCP nativos por rede,
  avaliar o `HttpServletSseServerTransportProvider` do SDK.
- **Projeto/Cenas via disco (`list_scenes`/`switch_scene`/`create_scene`/
  `build_project`):** deliberadamente **não implementadas** nesta rodada. O motor
  não expõe uma `Scene` viva a partir de `Game` (`Game.getCurrentScene()` **não
  existe**, apesar de uma pesquisa inicial ter sugerido o contrário — verificado por
  leitura direta do código-fonte); e o projeto só suporta **uma** cena ativa em
  memória. Ferramentas de disco (`IgnisProjectIO.load`) veriam o último estado
  salvo, divergente da edição ao vivo. `build_project` também foi descartado por
  ora: é uma operação longa/bloqueante que, executada dentro do `runOnFxThread`
  síncrono do `call()`, travaria a UI do editor inteira durante o build.
- **Marketplace (leitura):** `MarketplaceClient` tem `fetchCatalog()`/`publish()`
  prontos, mas dependem de rede e de um token de publicação — fora do escopo desta
  rodada (foco em ferramentas que operam localmente/offline).
- **Mais ferramentas:** UI com layout automático populando dinamicamente
  (data-binding), colisão com raycast exposto via MCP, câmera shake com tween.
- **Auditoria/log:** painel no editor mostrando cada chamada de ferramenta recebida.

Ver também: [[AGENTIC_AI_PLAN]] e [[COLLABORATION_GUIDE]].
