# Plano de IA Agêntica no IgnisEngine (Gemini / NVIDIA / IA embarcada)

> Documento de plano — 30/06/2026. Descreve o que já existe e como pretendemos
> evoluir para uma **IA agêntica** que opera o editor usando as ferramentas do MCP.
> Complementa: [[MCP_SERVER_GUIDE]].

---

## 1. Objetivo

Ter, dentro do editor, um agente de IA que **entende um pedido em linguagem natural**
("crie um inimigo que persegue o player") e **executa** as ações necessárias
chamando as ferramentas do motor (criar/editar scripts, compilar, mexer na cena),
observando os resultados e iterando — um laço *pensar → agir → observar*.

Requisitos do usuário atendidos por este plano:
- Funcionar com **APIs gratuitas do Gemini** (Google AI Studio) e da **NVIDIA**
  (build.nvidia.com — endpoint compatível com OpenAI).
- Permitir, no futuro, uma **IA agêntica local/embarcada** com acesso às **mesmas
  ferramentas** do MCP.

---

## 2. O que já foi implementado (fundação)

| Peça | Arquivo | Papel |
|------|---------|-------|
| Registry de ferramentas | `mcp/IgnisToolRegistry.java` | Fonte canônica das ferramentas (nome, schema, executor) |
| Bridge HTTP (URL) | `mcp/McpHttpBridge.java` | Expõe as ferramentas por URL para qualquer agente |
| Fachada MCP | `mcp/McpService.java` | Ciclo de vida + registry compartilhado |
| Provider Gemini | `editor/GeminiProvider.java` | Chamada REST ao `gemini-2.5-flash` |
| Provider NVIDIA | `editor/NvidiaProvider.java` | Chamada ao endpoint OpenAI-compatível da NVIDIA |
| Interface comum | `editor/AIServiceProvider.java` | Contrato `callAPI(key, prompt)` |
| Executor de ferramentas | `editor/AgentToolExecutor.java` | Cola entre a resposta do LLM e o registry |
| UI de configuração | `editor/fx/FxSettingsWindow.java` (aba *IA & MCP*) | Provedor ativo, chaves, toggle de function-calling |
| Preferências | `editor/fx/EditorPrefs.java` | `aiProvider`, `geminiApiKey`, `nvidiaApiKey`, `aiToolsEnabled` |

### 2.1 Como o function-calling funciona hoje (baseado em prompt)

Gemini e NVIDIA, neste projeto, são chamados como **texto→texto**. O
`AgentToolExecutor` fornece as duas metades do laço:

1. `toolManifest()` — gera a descrição das ferramentas para injetar no *system
   prompt*, instruindo o modelo a responder com um JSON:
   `{"tool":"<nome>","arguments":{...}}`.
2. `tryHandleToolCall(resposta)` — detecta esse JSON na resposta (tolerando cercas
   de código ```), executa a ferramenta via `IgnisToolRegistry` e devolve o
   resultado — que é então realimentado ao modelo na próxima rodada.

O registry usado é exatamente o publicado pelo `McpService`, então a IA do editor e
os agentes externos (via URL) enxergam **o mesmo conjunto de ferramentas**.

---

## 3. Arquitetura-alvo do laço agêntico

```
  Usuário (pedido)
       │
       ▼
  ┌─────────────┐   monta prompt (sistema + manifesto de ferramentas + histórico)
  │ AgentRunner │ ─────────────────────────────────────────────┐
  └─────────────┘                                               ▼
       ▲                                             ┌────────────────────┐
       │  resultado da ferramenta                    │ AIServiceProvider   │
       │  (realimentado)                             │ Gemini | NVIDIA |   │
       │                                             │ (local no futuro)   │
  ┌─────────────────┐   resposta do modelo           └────────────────────┘
  │ AgentToolExecutor│◀───────────────────────────────────────┘
  └─────────────────┘
       │  é chamada de ferramenta? 
       │  sim → executa no IgnisToolRegistry → volta pro topo
       │  não → resposta final ao usuário
       ▼
   GameObjects / Scripts / Cena (thread de UI)
```

### 3.1 Componente a criar: `AgentRunner`
- Mantém o **histórico** da conversa e o orçamento de iterações (ex.: máx. 8 passos).
- Monta o prompt: *persona* (engenheiro do IgnisEngine) + `toolManifest()` + histórico.
- Chama o `AIServiceProvider` ativo (`EditorPrefs.getAiProvider()`), passando a chave
  correspondente.
- Passa a resposta ao `AgentToolExecutor.tryHandleToolCall()`:
  - se for chamada de ferramenta → executa, anexa o resultado ao histórico, repete;
  - senão → entrega a resposta final ao usuário (painel de chat da IA).
- Roda em thread de segundo plano; efeitos na cena via `IgnisMcpBridge`.

### 3.2 Painel de chat (UI)
- Nova janela/aba "Assistente IA" com histórico, campo de pergunta e um **log das
  ferramentas usadas** (transparência: o usuário vê o que a IA executou).
- Botão "Aprovar antes de executar" (modo seguro) vs. "Autônomo".

---

## 4. Providers gratuitos — detalhes de integração

### 4.1 Gemini (Google AI Studio)
- Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=...`
- Já implementado em `GeminiProvider`. Chave: https://aistudio.google.com/apikey
- **Evolução recomendada:** migrar para o **function-calling nativo** do Gemini
  (campo `tools` com `functionDeclarations`), convertendo os schemas do registry — 
  mais confiável que o esquema baseado em prompt.

### 4.2 NVIDIA (build.nvidia.com)
- Endpoint: `https://integrate.api.nvidia.com/v1/chat/completions` (OpenAI-compatível).
- Já implementado em `NvidiaProvider` (modelo padrão `meta/llama-3.1-8b-instruct`).
  Chave: https://build.nvidia.com
- **Evolução recomendada:** usar o campo `tools`/`tool_calls` (padrão OpenAI) para
  function-calling nativo; permitir escolher o modelo (ex.: Nemotron) na UI.

### 4.3 Tabela de function-calling
| Provider | Hoje | Alvo |
|----------|------|------|
| Gemini | prompt → JSON `{"tool":...}` | `functionDeclarations` nativas |
| NVIDIA | prompt → JSON `{"tool":...}` | `tools`/`tool_calls` (OpenAI) |
| Local (futuro) | — | mesma interface `AIServiceProvider` + tools nativas |

---

## 5. IA agêntica **local/embarcada** (futuro)

Meta: rodar um modelo local (sem depender de API externa) com acesso às ferramentas.

- **Interface:** implementar `AIServiceProvider` para um runtime local
  (ex.: `LocalLlmProvider`) — **nenhuma outra camada muda**, pois o `AgentRunner` e o
  `AgentToolExecutor` já são agnósticos ao provedor.
- **Opções de runtime:**
  - **Servidor local compatível com OpenAI** (Ollama, LM Studio, llama.cpp server):
    o `NvidiaProvider` já é praticamente reaproveitável — basta trocar a URL base para
    `http://localhost:11434/v1/...`. Caminho de menor esforço.
  - **In-process** via ONNX Runtime / GGUF binding em Java: mais complexo, mas 100%
    offline e sem processo externo.
- **Ferramentas:** o modelo local usa o **mesmo `IgnisToolRegistry`** — paridade
  garantida por construção. Não conectar pela URL (overhead de rede desnecessário):
  chamar `McpService.getRegistry().call(...)` diretamente in-process.
- **Segurança/limites:** manter o modo "aprovar antes de executar" como padrão para
  modelos locais menores (mais sujeitos a erro).

---

## 6. Roadmap incremental

1. **[base — feito]** Registry + bridge HTTP + providers + UI de chaves + executor.
2. `AgentRunner` (laço pensar→agir→observar) + painel de chat com log de ferramentas.
3. Function-calling **nativo** (Gemini `functionDeclarations`; NVIDIA `tools`).
4. Expandir o registry (cena, assets, animação, áudio).
5. `LocalLlmProvider` via servidor OpenAI-compatível local (Ollama/LM Studio).
6. Modo autônomo com salvaguardas (limite de passos, aprovação, undo automático).

---

## 7. Pendências conhecidas

- O laço agêntico (`AgentRunner`) ainda **não** está implementado — hoje há as peças
  (manifesto + executor), mas falta o orquestrador e o painel de chat dedicado.
- Function-calling é baseado em prompt (frágil com modelos pequenos) até a migração
  para os formatos nativos.
- Falta seleção de modelo por provider na UI (fixos por enquanto).

Ver também: [[MCP_SERVER_GUIDE]], [[COLLABORATION_GUIDE]].
