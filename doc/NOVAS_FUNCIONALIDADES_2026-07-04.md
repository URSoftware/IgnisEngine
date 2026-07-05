# Detalhamento das Novas Funcionalidades - 04/07/2026

Este documento descreve as melhorias implementadas no motor Ignis Engine na data de 04/07/2026 por IA, sob a autoria exclusiva de ThyagoToledo. Cobre a continuacao do sistema de fisica/colisao: a unificacao do collider no padrao Entidade-Componente (item 8c) e o gizmo visual de collider no viewport (item 8b).

---

## 1. ColliderComponent como fonte unica da hitbox (item 8c)

### Proposito
Aposentar o par legado `GameObject.colliderType/collisionMode` e concentrar toda a definicao de colisao no `ColliderComponent`, tornando-o a fonte unica de verdade para geometria, propriedades fisicas e integracao com o `CollisionManager`.

### O que faz
- **Geometria na hitbox (`ColliderComponent.java`):** Novos campos `@Serialize` `width`, `height`, `radius`, `offsetX`, `offsetY` (alem de `friction`, `bounciness`, `isTrigger`, `enabled` e `collisionLayer`). Tamanhos `<= 0` assumem automaticamente a dimensao do dono (`effectiveWidth/effectiveHeight/effectiveRadius`).
- **Semantica de offset:** para `Box`/`Capsule` o offset desloca o canto superior-esquerdo da hitbox; para `Sphere` desloca o centro do circulo. Ambos default 0 (hitbox coincide com o dono).
- **Ponte de runtime:** o componente constroi e mantem sincronizado um `IgnisSampleCollisions.Collider` concreto (`AABBCollider` para Box/Capsule, `CircleCollider` para Sphere), registrando-o e desregistrando-o no `CollisionManager` via `awake()`, `update()` e o novo hook `Component.onDetach()`. Isso torna o `ColliderComponent` funcional em tempo de execucao — antes era apenas um stub.
- **Mapeamento de forma:** `resolveColliderType()` traduz a forma logica (`Box`/`Sphere`/`Capsule`) para o tipo concreto do motor; a troca de forma reconstroi o collider e remove o antigo do manager (sem hitbox fantasma).
- **Bounds em mundo:** `getWorldBounds()` e `resizeToWorldBounds()` expoem/aplicam o retangulo da hitbox em coordenadas de mundo — base do gizmo (item 8b).
- **Serializacao:** persistencia automatica pelos campos `@Serialize` (mecanismo generico do item 5); `loadProperties()` foi sobrescrito para reconstruir/re-registrar o collider concreto com a geometria carregada.
- **Registro em massa:** `Game.refreshColliders()` passou a preferir o `ColliderComponent` (via `ensureRegistered()`), caindo no collider legado apenas para objetos ainda nao migrados.

### Aposentadoria do legado
- Os metodos `getColliderType/setColliderType/getCollisionMode/setCollisionMode` do `GameObject` foram marcados `@Deprecated` (mantidos para scripts legados e o editor Swing).
- O Inspector JavaFX nao edita mais `colliderType/collisionMode`. Objetos que ainda usam o par legado exibem uma secao **"Collider (legado)"** com um botao **"Migrar para ColliderComponent"**, que converte forma/modo/tamanho/offset e desliga o collider antigo.

---

## 2. Gizmo visual de collider no viewport (item 8b)

### Proposito
Permitir redimensionar a hitbox diretamente no viewport, com alcas nas bordas e cantos, sem depender de digitar numeros no Inspector.

### O que faz
- **Ativacao:** com **Show Colliders** ligado (menu do editor) e um objeto com `ColliderComponent` selecionado, o viewport desenha o contorno da hitbox (tracejado — ciano para colisao, verde para trigger) e 8 alcas (4 cantos + 4 arestas).
- **Redimensionamento:** arrastar uma alca ajusta as bordas da hitbox em coordenadas de mundo (`handleColliderDrag` -> `ColliderComponent.resizeToWorldBounds`), com tamanho minimo de 1px e suporte a arrastar uma borda alem da oposta. Para `Sphere`, os limites viram raio (metade do menor lado).
- **Precedencia e cursores:** as alcas de collider tem precedencia sobre o gizmo de transform quando ativas; o hover exibe o cursor de redimensionamento direcional correspondente (N, NE, E, ...).
- **Integracao FX:** todo o input flui pelo roteamento existente (`dispatchEvent` do viewport JavaFX -> listeners AWT do engine -> `handleMousePress/Drag/Release`), e o desenho ocorre no pipeline `renderWorldTo`. O fim do arraste dispara `TransformListener.onTransformEnd`, marcando o projeto como modificado.

---

## 3. Organizador de Cenários e cena inicial

### Proposito
Expor no editor JavaFX o suporte a multiplas cenas que ja existia no modelo de dados (`Project.scenes`), permitindo criar, trocar, organizar e escolher a cena inicial do jogo.

### O que faz
- **Seletor de cena na toolbar:** `ComboBox` "Cena:" que troca a cena ativa; a cena inicial aparece marcada com ⭐.
- **Gerenciador de Cenários (`Cena > Gerenciar Cenários…`):** dialogo com a lista de cenas e acoes **Nova, Ativar, Renomear, Duplicar, Definir como inicial, Deletar** (com confirmacao; nao permite remover a unica cena). Duplicacao via round-trip JSON (`Scene.toJSON/fromJSON`) com nome unico automatico.
- **Troca de cena segura:** `switchEditorToScene` persiste a cena atual no projeto (`syncEntitiesToScene`), carrega a nova no game vivo (entidades + cameras via `addEntityTracked` + mundo) e recarrega os scripts. Bloqueada durante o Play (pede Stop antes).
- **Cena inicial:** botao "Definir como inicial ⭐" grava `Project.mainScene`, persistido no `.ignis`.

---

## 4. Botão de criação de mundos (World)

### Proposito
Permitir criar e configurar o `World` (limites do mapa) de cada cena pela interface, em vez de depender apenas de scripts.

### O que faz
- Dentro do Gerenciador de Cenários, secao **"Mundo da cena"** com **Criar Mundo** (limites default 1920x1080 centrados na origem), edicao dos limites `Min X/Min Y/Max X/Max Y` (**Aplicar limites**) e **Remover Mundo**.
- O mundo da cena ativa e refletido imediatamente no game vivo (`game.setWorld`) e persistido via `Scene.setWorld`. O overlay de limites/barreiras ja existente passa a ser configuravel pela UI.

---

## 5. Visualizador de câmera (campo de visão)

### Proposito
Mostrar no viewport "para onde a camera aponta" — o retangulo do mundo que cada camera vai capturar em runtime, para o criador decidir o enquadramento.

### O que faz
- **`Camera.getFrustumWorldRect(designW, designH)`:** calcula o retangulo capturado (centrado na posicao da camera, encolhendo com o zoom), independente de haver um `Viewport` vivo.
- **Render no editor (`Game.renderCameraBounds`):** desenha o frustum de cada camera em espaco de mundo, com cruz no centro (posicao) e o nome. A **camera ativa** recebe destaque amarelo preenchido; as demais ficam tracejadas em cinza; a selecionada fica com contorno solido.
- **Toggle:** `Visualizar > Mostrar Câmera (campo de visão)` (ligado por padrao), via a flag `Game.showCameraBounds`.

---

## 6. Testes

- `ColliderComponentTest` (13 testes): bounds default/offset, tamanho explicito, geometria de Sphere, mapeamento de forma->tipo, round-trip de redimensionamento (gizmo), clamp minimo, construcao/sincronizacao do collider de runtime, trigger e round-trip de serializacao.
- `CameraFrustumTest` (2 testes): frustum centrado na camera e encolhimento com o zoom.
- Suite total: **38 testes, verdes** (`mvnw test`).
