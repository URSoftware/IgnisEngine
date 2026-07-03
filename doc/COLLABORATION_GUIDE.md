# Colaboração em Tempo Real (tipo CodeTogether Cloud)

> Documento de arquitetura e referência — atualizado em 03/07/2026. Descreve o
> sistema de colaboração implementado: transporte, sincronização completa do
> projeto (arquivos), espelhamento de cena ao vivo, edição convidado→host,
> sincronização de código, ponteiro virtual dos participantes e segurança.

---

## 1. Objetivo

Permitir que **outras pessoas visualizem e editem** o projeto no editor **em tempo
real**, semelhante ao CodeTogether Cloud, funcionando tanto por:
- **VPN** (ex.: Radmin VPN, Hamachi, Tailscale) — usando o IP virtual do host; quanto
- **IP direto / URL** gerado pela própria máquina do host.

Ao entrar numa sessão, o convidado **recebe uma cópia temporária do projeto do
host** (cenas, assets, scripts, prefabs, configurações) e passa a ver exatamente o
mesmo ambiente de trabalho, com atualizações ao vivo. Quando o host aperta Play,
todos assistem ao teste em tempo real.

---

## 2. Arquitetura de transporte

Modelo **estrela com relay**: o host é o hub; convidados conectam a ele. Não depende
de servidor na nuvem — o "URL"/endereço é o do próprio host (por isso VPN funciona
sem configuração extra).

```
   Convidado A ─┐
                ├─▶  CollabServer (host)  ─── retransmite ──▶ demais convidados
   Convidado B ─┘         │
                          └── dispatchInbound() ──▶ CollabSession ──▶ Listeners (editor/UI)
```

| Classe | Arquivo | Papel |
|--------|---------|-------|
| `CollabSession` | `collab/CollabSession.java` | Fachada singleton: host/join, envio, listeners |
| `CollabServer` | `collab/CollabServer.java` | Host: `ServerSocket`, presença, broadcast/direcionado |
| `CollabClient` | `collab/CollabClient.java` | Convidado: conecta, envia/recebe |
| `CollabBridge` | `collab/CollabBridge.java` | Cena ao vivo, comandos, cursores, assets sob demanda |
| `CollabProjectSync` | `collab/CollabProjectSync.java` | Sincronização de arquivos do projeto (inicial + contínua) |

**Protocolo:** TCP com **uma linha JSON por mensagem** (`\n`-delimitado, UTF-8).
Transporte-agnóstico → funciona em IP direto ou qualquer VPN de camada 3.

### 2.1 Tipos de mensagem
| `type` | Campos | Uso |
|--------|--------|-----|
| `hello` | `name`, `uid`, `token` | Convidado se apresenta ao conectar |
| `denied` | `reason` | Host recusa entrada (senha errada) |
| `presence` | `participants[]` | Host anuncia a lista atualizada de participantes |
| `chat` | `from`, `text` | Mensagem de chat |
| `event` | `channel`, `from`, `payload`, `to?` | Retransmissão por canal; `to` (uid) restringe a entrega a um único participante |

Cada participante tem um **uid** (host = `"host"`, convidados = `g-xxxxxxxx` gerado
no join). Mensagens com `to` são entregues **apenas ao destinatário** — a
transferência de projeto para um convidado não inunda os demais.

### 2.2 Canais de evento (`event.channel`)
- `scene` — snapshot da cena ao vivo, comandos de edição do convidado, assets sob demanda
- `script` — conteúdo de scripts editados (debounce de 500 ms, anti-eco)
- `project` — sincronização de arquivos do projeto (manifesto, chunks, exclusões)
- `cursor` — ponteiro virtual, seleção e ferramenta ativa de cada participante
- `play` — reservado (o estado de Play hoje viaja dentro do snapshot `scene`)

---

## 3. Sincronização do projeto (`project`) — `CollabProjectSync`

### 3.1 Sincronização inicial (entrada do convidado)
1. Ao conectar, o convidado envia `{req:"manifest", uid}`.
2. O host **salva o projeto** (hook `preSyncHook` → `saveProjectSilently`) e responde
   (direcionado) com o **manifesto**: nome do projeto, versão do motor, caminho do
   `.ignis` e a lista de arquivos `{p: caminho relativo, s: tamanho, h: SHA-256}`.
   Ficam de fora `scripts/compiled/` (recompilado no destino), `.class`, temporários.
3. O convidado valida a **versão do motor** (aviso se divergir), compara o manifesto
   com seu cache local e pede **só o que falta ou mudou** (`{req:"files", paths}`).
4. O host envia cada arquivo em **chunks base64 de ~192 KB** (`{f, i, n, b64, h}`),
   direcionados. O convidado remonta, **valida o SHA-256** contra o manifesto e grava.
5. Com tudo válido, o editor do convidado **abre a cópia sincronizada**
   automaticamente (título indica `[sessao colaborativa]`).

### 3.2 Diretório temporário e cache
- Os arquivos vivem em `~/.ignis/collab-cache/<host>_<porta>/<Projeto>/` —
  **isolado por sessão** e **nunca sobrescreve projetos locais** do convidado.
- O diretório funciona como **cache**: ao reentrar na mesma sessão, apenas o delta
  é transferido (reentrada típica: alguns milissegundos).
- Arquivos locais que não constam no manifesto são removidos da cópia (o convidado
  vê exatamente o projeto do host).
- **Limpeza automática:** caches sem uso há mais de 7 dias são apagados ao iniciar
  uma sessão; há também o botão **Limpar cache de sessoes** em
  Configurações → Colaboração.
- Proteções no editor (convidado): a cópia da sessão **não entra nos recentes**, e
  Salvar/Auto-Save são bloqueados nela (quem salva é o host — as edições do
  convidado chegam a ele pelos comandos do MCP e pelo canal `script`).

### 3.3 Sincronização contínua (watcher do host)
- Enquanto hospeda, um `WatchService` recursivo observa a pasta `project/` do host
  (assets, scripts, prefabs, data...), com **debounce de 400 ms**.
- Arquivo criado/alterado → retransmitido em chunks a todos; excluído → `{del}`.
- No convidado: o arquivo é gravado na cópia da sessão, o cache de imagens é
  invalidado, o Asset Browser é atualizado e scripts `.java` são **recompilados**.
- A **cena** não passa pelo watcher: ela é espelhada ao vivo pelo snapshot (abaixo).

### 3.4 Segurança da transferência
- Caminhos relativos validados (sem absolutos, sem `..`, sem `:`) nos dois lados.
- Hash SHA-256 conferido por arquivo; arquivos > 64 MB ficam fora da sessão (log).
- O convidado só grava em `~/.ignis/collab-cache` — nunca no projeto local. Até a
  cópia da sessão abrir, snapshots e assets recebidos **não são aplicados** ao
  projeto local antigo (isolamento).

---

## 4. Cena ao vivo, edição e código

### 4.1 Espelhamento da cena (host → convidados) — `CollabBridge`
- O host transmite um **snapshot** a ~12 Hz pelo canal `scene`: por objeto —
  nome/tipo/transform/zIndex/visível/opacity/flip/scale/sprite, **scripts anexados**,
  **tint/forma do SpriteComponent** e **collider (tipo/modo)** — mais câmera
  (pos/zoom) e flag de Play.
- O convidado aplica na thread de UI: find-or-create por nome, remove o que sumiu,
  espelha câmera e componentes, e **interpola** posições/câmera entre snapshots
  (movimento fluido). Scripts anexados no host são instanciados no convidado pelo
  `ScriptManager` local (os `.java` chegam pela sincronização de projeto).

### 4.2 Edição convidado → host (host-autoritativo)
- Quando o editor é convidado, as ~37 ferramentas do MCP que **mutam** a cena são
  interceptadas em `IgnisToolRegistry.call` e **encaminhadas ao host**
  (`{cmd, args}` no canal `scene`). O host executa na cena autoritativa e o snapshot
  rebroadcasta o resultado. Sem sessão, nada muda (single-user intacto).

### 4.3 Código (`script`)
- `FxCodeEditor` transmite o conteúdo com debounce de 500 ms; o receptor salva no
  disco e atualiza o editor aberto, com guarda anti-eco. v1 last-write-wins.

### 4.4 Assets sob demanda
- Se um snapshot referencia um sprite que o convidado ainda não tem, ele o pede ao
  host (`assetReq`/`assetData`, ≤ 2 MB) — cobre a janela entre o snapshot e a chegada
  do arquivo pelo canal `project`.

---

## 5. Ponteiro virtual e indicação de atividade (`cursor`)

- Cada participante transmite (~20 Hz, com throttle) a posição do mouse na **Scene
  View em coordenadas de mundo**, a **seleção atual** e a **ferramenta ativa**;
  mudanças de seleção são enviadas imediatamente.
- Os demais veem, desenhados sobre o viewport (`CollabBridge.renderOverlay`):
  - uma **seta colorida** (cor exclusiva por participante, paleta por hash do nome)
    com **etiqueta** `nome · ferramenta`, com movimento interpolado (suave);
  - um **contorno tracejado colorido** em volta do objeto que o participante está
    manipulando (indicação de atividade, discreta);
  - cursores inativos há mais de 5 s desaparecem sozinhos.
- Contexto atual: Scene View (Hierarchy/Inspector/Asset Browser são evolução futura).

---

## 6. Como usar (interface do editor)

`Configurações → Colaboração`:

**Hospedar:**
1. Defina seu **Nome de exibição** e (opcional) uma **senha de sessão**.
2. Escolha a **Porta** (padrão `8791`) e clique **Hospedar sessão**.
3. Copie o **endereço para compartilhar** (`<IP-LAN/VPN>:porta`) e envie.

**Entrar:**
1. Preencha o **Endereço do host** (ex.: o IP `25.x.x.x` da Radmin VPN), a **Porta**
   e a senha (se houver) → **Entrar na sessão**.
2. O projeto do host é baixado e aberto automaticamente (progresso no status).

> Descoberta do IP: `McpHttpBridge.localLanAddress()` escolhe o primeiro IPv4
> não-loopback ativo — normalmente o da VPN quando ela está conectada.

---

## 7. Segurança

- **Senha de sessão (token):** enviada no `hello`; o host recusa (`denied`) se não
  bater.
- Conexão TCP simples na LAN/VPN. Para redes não confiáveis, a evolução planejada é
  **TLS** (`SSLServerSocket`) + aprovação de entrada/expulsão pelo host.
- Transferências de projeto validam caminho e hash nos dois lados (ver 3.4).

---

## 8. Estado atual × evoluções futuras

**Pronto:**
- Transporte TCP host/convidado, presença, chat, canais, mensagens direcionadas (uid).
- **Sincronização inicial do projeto** com manifesto/hash/chunks/cache + abertura
  automática no convidado; **sincronização contínua** por watcher (assets, scripts,
  prefabs, data) com recompilação de scripts.
- Espelhamento de cena a ~12 Hz com interpolação, componentes (scripts, tint, forma,
  collider), câmera e Play; edição convidado→host pelas ferramentas do MCP; sync de
  código com debounce/anti-eco; assets sob demanda; senha de sessão.
- **Ponteiro virtual** com nome/cor/ferramenta + contorno de seleção remota na Scene
  View; limpeza automática de cache; isolamento total do projeto local do convidado.

**Evoluções futuras (a arquitetura já comporta):**
- **Delta de cena** (enviar só objetos alterados) em vez de snapshot completo — para
  cenas grandes; o formato JSON por canal permite introduzir `{diff}` sem quebrar.
- **OT/CRDT** para edição concorrente de código (hoje last-write-wins).
- **Papéis/permissões** (host, editor, espectador) e **locking** por recurso — o
  `hello`/presença já carregam identidade (uid) para pendurar papéis.
- Cursor na Hierarchy/Inspector/Asset Browser; **chat integrado** na UI principal;
  comentários em objetos; histórico de alterações.
- Sessões via Internet (TLS + relay opcional); sincronização seletiva de arquivos.

Ver também: [[MCP_SERVER_GUIDE]], [[AGENTIC_AI_PLAN]].
