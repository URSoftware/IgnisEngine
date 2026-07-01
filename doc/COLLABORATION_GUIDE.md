# Colaboração em Tempo Real (tipo CodeTogether Cloud)

> Documento de plano e referência — 30/06/2026. Descreve a fundação de colaboração
> já implementada e o plano de sincronização completa (edição de código, objetos se
> movendo, Play compartilhado).

---

## 1. Objetivo

Permitir que **outras pessoas visualizem e editem** o projeto no editor **em tempo
real**, semelhante ao CodeTogether Cloud, funcionando tanto por:
- **VPN** (ex.: Radmin VPN, Hamachi, Tailscale) — usando o IP virtual do host; quanto
- **IP direto / URL** gerado pela própria máquina do host.

Colaboradores devem conseguir: editar código, ver objetos se movendo, e **quando o
host aperta Play, todos testam junto** — além das demais funcionalidades do editor.

---

## 2. Arquitetura de transporte (implementada)

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
| `CollabServer` | `collab/CollabServer.java` | Host: `ServerSocket`, presença, broadcast |
| `CollabClient` | `collab/CollabClient.java` | Convidado: conecta, envia/recebe |

**Protocolo:** TCP com **uma linha JSON por mensagem** (`\n`-delimitado, UTF-8).
Transporte-agnóstico → funciona em IP direto ou qualquer VPN de camada 3.

### 2.1 Tipos de mensagem
| `type` | Campos | Uso |
|--------|--------|-----|
| `hello` | `name` | Convidado se apresenta ao conectar |
| `presence` | `participants[]` | Host anuncia a lista atualizada de participantes |
| `chat` | `from`, `text` | Mensagem de chat |
| `event` | `channel`, `from`, `payload` | Retransmissão genérica por canal |

### 2.2 Canais de evento (`event.channel`)
- `scene` — mudanças de cena/GameObjects (criar, mover, deletar, propriedades)
- `script` — edições de código (patches/cursores no editor)
- `play` — estado de Play do host (start/stop, snapshots do runtime)
- `cursor` — presença de cursor/seleção de cada colaborador

---

## 3. Como usar (interface do editor)

`Configurações → Colaboração`:

**Hospedar:**
1. Defina seu **Nome de exibição**.
2. Escolha a **Porta** (padrão `8791`).
3. **Hospedar sessão** → o **endereço para compartilhar** (`<IP-LAN/VPN>:porta`)
   aparece; use **Copiar** e envie aos colaboradores.

**Entrar:**
1. Preencha o **Endereço do host** (ex.: o IP `25.x.x.x` da Radmin VPN) e a **Porta**.
2. **Entrar na sessão**.

A lista de **Participantes** e o status atualizam ao vivo. As preferências (nome,
porta) persistem em `~/.ignis/editor-prefs.json`. A sessão é encerrada ao fechar o
editor.

> Descoberta do IP: `McpHttpBridge.localLanAddress()` escolhe o primeiro IPv4 não-loopback
> ativo — normalmente o da VPN quando ela está conectada.

---

## 4. Plano de sincronização (o que falta ligar)

A **fundação de transporte está pronta** (presença, chat, canais). Falta conectar os
subsistemas do editor aos canais — trabalho por camadas:

### 4.1 Edição de código (`script`)
- **Emitir:** ao editar no `FxCodeEditor`, enviar patches (diffs) via
  `sendEvent(CH_SCRIPT, {file, ops})`. Início simples: enviar o conteúdo inteiro em
  *debounce*; evolução: **OT (Operational Transform) ou CRDT** para edição concorrente
  sem conflito.
- **Aplicar:** no `onEvent(CH_SCRIPT, ...)`, aplicar o patch ao buffer do arquivo
  correspondente (com guarda para não ecoar a própria mudança).

### 4.2 Cena / objetos se movendo (`scene`)
- **Emitir:** hooks nas operações do editor (mover/criar/deletar/alterar propriedade)
  — reutilizar os comandos do `UndoManager` como fonte de eventos.
- **Aplicar:** aplicar a mutação no `Game`/`Scene` na thread de UI. Para arrasto
  contínuo, enviar em taxa limitada (ex.: 20–30 Hz) e interpolar no receptor.
- **Snapshot inicial:** ao um convidado entrar, o host envia o **estado completo da
  cena** (serialização `.ignis`) para sincronizar o ponto de partida.

### 4.3 Play compartilhado (`play`)
- **Host aperta Play:** enviar `play:start`; o runtime autoritativo roda **no host**.
- **Streaming de estado:** transmitir snapshots de transform/estado dos GameObjects a
  cada tick (ou delta) pelo canal `play`; convidados renderizam em modo espectador.
- **Input remoto (fase 2):** convidados enviam input pelo canal `play` para testes
  multiplayer reais. Modelo host-autoritativo evita divergência.
- **Play stop:** `play:stop` volta todos ao modo de edição.

### 4.4 Presença de cursor/seleção (`cursor`)
- Enviar posição do mouse/seleção em baixa frequência; desenhar cursores/《flags》
  coloridas por participante no viewport e na hierarquia.

---

## 5. Controle de concorrência e permissões (futuro)

- **Locking otimista** por arquivo/objeto para evitar sobrescrita durante a fase sem
  OT/CRDT.
- **Papéis:** host (dono), editor, espectador — o host concede permissões.
- **Autoridade:** o host é a fonte de verdade do estado salvo; convidados propõem
  mudanças que o host aplica/retransmite.

---

## 6. Segurança

- Conexão TCP simples na LAN/VPN. Para uso fora de rede confiável, adicionar:
  **token de sessão** no `hello`, e opcionalmente **TLS** (`SSLServerSocket`).
- O host controla quem entra (fase futura: aprovação de entrada + expulsão).

---

## 7. Estado atual × pendências

**Pronto:**
- Transporte TCP host/convidado, presença ao vivo, chat, canais genéricos de evento.
- UI de hospedar/entrar/copiar endereço, com nome e porta persistidos.
- Compatível com VPN (Radmin etc.) e IP direto.

**Pendente (plano acima):**
- Ligar `script`/`scene`/`play`/`cursor` aos subsistemas reais do editor.
- Snapshot inicial de cena ao entrar; OT/CRDT para código; streaming de Play.
- Permissões/papéis, locking, token de sessão e TLS.

Ver também: [[MCP_SERVER_GUIDE]], [[AGENTIC_AI_PLAN]].
