# Servidor MCP e Bridge HTTP do IgnisEngine

> Documento vivo — atualizado em 02/07/2026. Descreve a interface de **IA & MCP**
> nas Configurações do editor, o servidor MCP e o bridge HTTP local que expõe as
> ferramentas do motor para agentes de IA. **67 ferramentas** no registry (26
> disponíveis sempre, 41 adicionais com o editor vivo) + 9 exclusivas do STDIO
> (processamento WAV e edição de imagem em camadas).

---

## 1. Visão geral

O IgnisEngine expõe um conjunto de **ferramentas** (ler a árvore do projeto, criar
e editar scripts, compilar etc.) para que agentes de IA possam operar a engine. Há
dois transportes para essas mesmas ferramentas:

| Transporte | Classe | Uso |
|-----------|--------|-----|
| **STDIO** (MCP clássico) | `com.ignis.mcp.McpServerManager` | Clientes MCP que *lançam* o processo (Claude Desktop, Cursor). Ativado por `--mcp <projeto>`. |
| **HTTP local (URL)** | `com.ignis.mcp.McpHttpBridge` | Agentes que se conectam por **URL** — inclusive IAs usando APIs Gemini/NVIDIA e a futura IA embarcada. Serve também um dashboard web em `/`. |

**Paridade entre transportes (02/07/2026):** o STDIO agora serve o mesmo conjunto
base do registry via um adapter genérico (`McpServerManager.registerRegistryTools`
converte cada `ToolDef` para uma tool do SDK). As classes legadas `CoreTools`,
`NoteTools` e `AnimationTools` foram **desregistradas** (duplicavam o registry;
os arquivos permanecem no repositório apenas como histórico). Continuam exclusivas
do STDIO apenas as ferramentas com estado em memória ou processamento pesado:
`AudioTools` (WAV: `read_wav_info`, `trim_wav`, `apply_wav_fades`, `mix_wav_tracks`)
e `ImageTools` (documentos de imagem em camadas: `create_image_document`,
`add_image_layer`, `import_image_to_layer`, `composite_and_save_image`,
`save_flat_image_asset`).

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

### 4.1 Sempre disponíveis (27) — `IgnisToolRegistry.registerDefaults()`

Funcionam mesmo no modo headless (`--mcp <projeto>`, transporte STDIO), pois operam
em arquivos do projeto ou em singletons estáticos do motor — não exigem o editor
JavaFX aberto.

**Orientação (1)**

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `how_to_create_game` | — | Guia passo a passo de como criar jogos no Editor (Cena, hierarquia, mundos/cenas, regras para o objeto aparecer, Play x persistência). **Leia antes de começar.** |

**Projeto, scripts e imagem (9)**

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
| `remove_sprite_background` | `imagePath`, `targetColorHex` (`auto`/cor/lista), `tolerance?` | Remove cor sólida ou quadriculado (checkerboard) do fundo de uma imagem, deixando-a transparente (lógica compartilhada com o STDIO em `ImageTools.removeBackground`) |

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

### 4.2 Somente com editor vivo (84) — `attachLiveEditor(...)`

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

**UI in-game direta, sem escrever script (19)** — via `com.ignis.core.ui.*`

Antes, criar UI (botões, barras, texto) só era possível escrevendo um `IgnisScript`
(`createButton`/`createLabel`/... protegidos). Estas ferramentas usam o mesmo
`UICanvas` diretamente, permitindo montar HUD/menus **sem nenhum script** — no canvas
global volátil OU, com `objectName`, num `CanvasComponent` **persistente** por objeto:

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
Todas aceitam **`objectName?`**: com ele, montam no `CanvasComponent` daquele objeto —
**UI PERSISTENTE** (serializa na cena, reabre pronta, anexa o componente sozinho); sem ele,
no canvas global de runtime — **VOLÁTIL** (o Stop limpa, não vai ao `.ignis`). Nomes são
únicos **por canvas**. Ao contrário da cena, edição de UI persistente em Play **não** é
descartada no Stop.

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `ui_create_label` | `name`, `text`, `x?`, `y?`, `width?`, `height?`, `color?`, `objectName?` | Cria um texto na UI |
| `ui_create_button` | `name`, `text`, `x?`, `y?`, `width?`, `height?`, `removeOnClick?`, `actionData?`, `objectName?` | Cria um botão. `actionData` = ação declarativa persistida (ex. `signal:abrir_menu`), lida por um script (o motor não interpreta) |
| `ui_create_progressbar` | `name`, `x?`, `y?`, `width?`, `height?`, `value?`, `maxValue?`, `fillColor?`, `objectName?` | Cria uma barra (HP/mana/loading) |
| `ui_create_panel` | `name`, `x?`, `y?`, `width?`, `height?`, `backgroundColor?`, `layout?`, `objectName?` | Cria um painel container (`NONE`/`VERTICAL`/`HORIZONTAL`/`GRID`) |
| `ui_create_image` | `name`, `path`, `x?`, `y?`, `width?`, `height?`, `scaleMode?`, `objectName?` | Cria uma imagem de UI |
| `ui_create_textfield` | `name`, `x?`, `y?`, `width?`, `height?`, `placeholder?`, `text?`, `objectName?` | Cria um campo de texto editável |
| `ui_create_checkbox` | `name`, `text?`, `checked?`, `x?`, `y?`, `objectName?` | Cria uma checkbox |
| `ui_create_slider` | `name`, `x?`, `y?`, `width?`, `height?`, `min?`, `max?`, `value?`, `objectName?` | Cria um slider (valor contínuo) |
| `ui_set_nine_slice` | `name`, `left`, `right`, `top`, `bottom`, `objectName?` | Ativa nine-slice numa UIImage |
| `ui_set_text` | `name`, `text`, `objectName?` | Altera o texto de um label/botão existente |
| `ui_set_progress_value` | `name`, `value`, `maxValue?`, `objectName?` | Atualiza o valor de uma barra |
| `ui_set_anchor` | `name`, `anchorX`, `anchorY`, `pivotX?`, `pivotY?`, `objectName?` | Âncora no pai (0-1) e pivô próprio — HUD que gruda em cantos |
| `ui_set_style` | `name`, `backgroundColor?`, `textColor?`, `borderColor?`, `borderWidth?`, `borderRadius?`, `fontSize?`, `padding?`, `zOrder?`, `objectName?` | Estilo/fonte/z-order entre irmãos (só os campos informados) |
| `ui_remove_element` | `name`, `objectName?`, `dryRun?` | Remove um elemento pelo nome |
| `ui_clear_all` | `objectName?`, `dryRun?` | Remove todos os elementos do canvas alvo |
| `ui_list_elements` | `objectName?` | Lista os elementos do canvas alvo (nome, tipo, posição, tamanho) |
| `ui_attach_canvas` | `objectName`, `sortingOrder?` | Anexa um `CanvasComponent` (UI persistente) a um objeto |
| `ui_set_canvas_props` | `objectName`, `sortingOrder?`, `visible?` | Ordem de desenho entre canvases e visibilidade do canvas inteiro |
| `ui_detach_canvas` | `objectName`, `dryRun?` | Remove o `CanvasComponent` e toda a UI dele (destrutivo) |

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

**Ciclo de vida do editor (2)** — `EditorLifecycleTools`

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `restart_editor` | — | Salva e relança o editor; a nova JVM reabre o projeto e re-sobe o bridge na mesma porta/token. Mural/claims/tarefas sobrevivem (`.ignis/coordination.json`). Aguarde ~5-10s e reconecte. |
| `get_editor_status` | — | Projeto, cenas, nº de objetos/scripts, URL/porta do bridge e agentes ativos. Confirma que o editor voltou após o restart. |

**Observabilidade e validação (6)** — `RuntimeInspectionTools` (read-only)

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `list_runtime_objects` | — | ID, tipo, pos, tamanho, z, visível, pai, scripts e componentes de cada objeto (inclui o que scripts esconderam no Play) |
| `get_runtime_metrics` | — | Contagens (objetos/visíveis/scripts/componentes), mundo com limites, taxa de sim e memória JVM |
| `validate_scene` | — | Linter da cena: nomes duplicados, sprite/script ausente, pai quebrado, objeto fora do mundo. `OK` se nada. Mesma regra do menu **Cena > Validar Cena…** do editor (via `com.ignis.core.SceneValidator`) |
| `snapshot_scene` | `label?` | Fotografa a cena sob um rótulo (em memória, até 16) para comparar depois |
| `compare_scene_snapshot` | `before`, `after?` | Diff entre dois snapshots (`current` = cena viva): `+N -M ~K` com detalhes. Separa edição persistente de runtime transitório |
| `get_ui_tree` | `objectName?` | Árvore da UI in-game: widget, bounds absolutos, âncora/pivô, z, texto, visível/interativo/focado — e a origem (canvas global volátil ou `CanvasComponent` persistente) |

**Teste de runtime — input e tempo determinísticos (8)** — `RuntimeTestingTools`

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `inject_input` | `action?`, `key?`, `state?`, `mouseButton?`, `x?`, `y?`, `durationFrames?` | Injeta teclado/mouse sem foco de janela (vira "just pressed" no próximo frame). `x`/`y` reposicionam o cursor virtual. Com `durationFrames`, segura N frames e solta sozinho (requer Play/pausado) |
| `click_ui` | `x`, `y`, `button?` | **Clica na UI por coordenada** (press+release roteados aos CanvasComponents/canvas global): dispara o `onClick` de um botão/escolha de diálogo. Requer Play/pausado. Retorna se um widget consumiu |
| `move_mouse` | `x`, `y` | Move o cursor virtual e roteia hover para a UI (destaca botões sob o ponto) |
| `release_all_inputs` | — | Solta todas as teclas/botões (zera o input) |
| `advance_frames` | `count?`, `fixedDelta?` | Avança N passos de 1/60s de forma determinista (mesmo pausado) |
| `pause_game` | — | Pausa a simulação (base do passo a passo) |
| `resume_game` | — | Retoma a simulação pausada |
| `run_input_tape` | `tape`, `maxFrames?` | Reproduz uma fita `[{at, action\|key\|mouseButton\|clickUi, x?, y?, state?}]` frame a frame (inclui cliques de UI por coordenada); ao final (mesmo após exceção) zera TODO o input |

Fluxo determinista: `play_game` → `pause_game` → `inject_input` → `advance_frames` →
`list_runtime_objects`/`capture_viewport` → `release_all_inputs` → `stop_game`.

**Cutscenes — timeline por tracks/keyframes (11)** — `CutsceneTools` (P1)

Timeline determinística em frames de simulação (60/s), persistida em
`cutscenes/<nome>.cutscene.json`. Tracks `ACTOR`/`CAMERA` interpolam `x`/`y` com easing
(`LINEAR`/`EASE_IN`/`EASE_OUT`/`EASE_IN_OUT`/`STEP`, curva de saída do keyframe);
`DIALOG`/`AUDIO`/`SIGNAL`/`FLAG` disparam eventos no frame exato (reportados ao chamador —
o jogo decide como reagir).

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `create_cutscene` | `name`, `durationFrames?` | Cria a cutscene vazia no projeto |
| `list_cutscenes` | — | Lista as cutscenes (duração, nº de tracks) |
| `get_cutscene` | `name` | Timeline completa em JSON |
| `set_cutscene_duration` | `name`, `durationFrames` | Altera a duração total |
| `add_cutscene_track` | `name`, `type`, `target?` | Adiciona uma track vazia |
| `add_cutscene_keyframe` | `name`, `type`, `frame`, `target?`, `x?`, `y?`, `visible?`, `easing?`, `text?`, `data?` | Grava/substitui um keyframe (cria a track se preciso) |
| `remove_cutscene_keyframe` | `name`, `type`, `frame`, `target?` | Remove um keyframe |
| `delete_cutscene` | `name` | Apaga a cutscene |
| `validate_cutscene` | `name` | Ator ausente na cena, keyframe além da duração, diálogo sem texto, áudio sem asset |
| `preview_cutscene` | `name`, `frame?` | Scrub read-only: pose interpolada + eventos do frame, SEM tocar a cena |
| `run_cutscene` | `name`, `fromFrame?`, `toFrame?`, `skip?` | Executa no Play frame a frame (pose + 1 passo de sim); `skip=true` pula ao estado final listando os eventos — mesmo estado da conclusão natural |

**Diálogos — grafo de nós data-driven (8)** — `DialogTools` (P1 fatia 2b)

Grafo de nós persistido em `dialogs/<id>.dialog.json`. Cada nó tem speaker, retrato,
texto e saída via `next` (linear) OU `choices` (ramificação; uma escolha pode setar flag
e ter condição). A engine **não** exibe sozinha: um script lê o JSON e desenha com a UI
persistente (`ui_*` com `objectName`). Ponte: uma track `DIALOG` de cutscene pode citar
`dialog:<id>#<nó>` no campo `data`. Não armazena texto protegido copiado da obra.

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `create_dialog` | `id`, `start?` | Cria o diálogo vazio no projeto |
| `list_dialogs` | — | Lista os diálogos (nº de nós, start) |
| `get_dialog` | `id` | Grafo completo em JSON |
| `set_dialog_node` | `id`, `nodeId`, `speaker?`, `portrait?`, `text?`, `next?`, `choices?`, `makeStart?` | Cria/substitui um nó (choices = array JSON de `{text, next, setFlag?, condition?}`) |
| `remove_dialog_node` | `id`, `nodeId` | Remove um nó |
| `delete_dialog` | `id` | Apaga o diálogo |
| `validate_dialog` | `id` | Start ausente, refs quebradas, nós inalcançáveis, texto/escolha vazios, retrato ausente, condição nunca setada, ciclo sem terminal |
| `preview_dialog` | `id`, `fromNode?`, `choicesPath?` | Percorre o grafo (seguindo `choicesPath` = índices de escolha) e devolve a transcrição, sem Play |

**Contrato uniforme das ferramentas que mutam a cena** (gate central do `call()`, sobre
`SCENE_MUTATING` = mutações de cena/objeto/câmera/mundo, exceto `play_game`/`stop_game`/
`save_project`). Toda ferramenta desse grupo aceita e anuncia no schema:

| Parâmetro | Efeito |
|-----------|--------|
| `dryRun` | Não aplica: valida e relata o que faria (`[dryRun] ... (modo=...)`). |
| `diff` | Anexa `diff: +N -M ~K` com os objetos adicionados/removidos/alterados. |
| `allowInPlay` | Em Play, a mutação é RECUSADA por padrão (o Stop descarta a edição); `true` aplica só no runtime transitório. |

Toda resposta mutável termina com `[modo=editing|playing]`. A recusa-em-Play e o `dryRun`
rodam antes de tocar a cena.

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

- ~~**Paridade STDIO ↔ Registry**~~ — **feito em 02/07/2026** (adapter genérico em
  `McpServerManager.registerRegistryTools`; legadas duplicadas desregistradas).
- ~~**Auditoria/log**~~ — **feito em 02/07/2026**: `IgnisToolRegistry.call()` loga
  cada chamada (`[MCP] nome {args} -> ok/ERRO (Xms)`) via `System.out`, que o
  Console do editor (`FxConsolePanel`) já captura e exibe ao vivo.
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

Ver também: [[AGENTIC_AI_PLAN]] e [[COLLABORATION_GUIDE]].
