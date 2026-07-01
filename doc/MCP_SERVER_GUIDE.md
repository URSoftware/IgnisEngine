# Servidor MCP e Bridge HTTP do IgnisEngine

> Documento vivo — atualizado em 30/06/2026. Descreve a interface de **IA & MCP**
> nas Configurações do editor, o servidor MCP e o bridge HTTP local que expõe as
> ferramentas do motor para agentes de IA.

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

## 4. Ferramentas registradas (v1)

Definidas em `IgnisToolRegistry.registerDefaults()`:

| Ferramenta | Argumentos | O que faz |
|-----------|-----------|-----------|
| `get_project_tree` | — | Árvore recursiva de arquivos/pastas do projeto |
| `list_scripts` | — | Lista os IgnisScripts disponíveis |
| `read_script` | `scriptName` | Lê o código-fonte de um script |
| `write_script` | `scriptName`, `content` | Sobrescreve o código de um script |
| `create_script` | `scriptName` | Cria script novo pelo template do motor |
| `compile_project` | — | Compila todos os scripts e retorna o total |
| `read_file` | `path` | Lê arquivo texto (relativo à raiz, com proteção anti path-traversal) |

Delegam ao `com.ignis.core.ScriptManager` do projeto ativo — uma única fonte de
verdade das operações do motor.

---

## 5. Segurança

- **Bind local por padrão** (`127.0.0.1`): nada é exposto sem ação explícita.
- **Token Bearer opcional** protege `/mcp/tools` e `/mcp/call` (o `/health` fica
  aberto de propósito, para diagnóstico).
- **Anti path-traversal** em `read_file`: o caminho é resolvido de forma canônica e
  rejeitado se sair da raiz do projeto.
- Ao **expor na rede/VPN**, recomenda-se **sempre** definir um token.

---

## 6. Pendências / próximos passos

- **Paridade STDIO ↔ Registry:** hoje o `McpServerManager` (STDIO) ainda registra as
  ferramentas legadas diretamente no SDK; migrá-lo para consumir o `IgnisToolRegistry`
  elimina a duplicação e garante o mesmo conjunto nos dois transportes.
- **Transporte MCP-over-HTTP oficial (SSE):** o bridge atual é um JSON/HTTP simples
  (suficiente para function-calling de LLMs). Para clientes MCP nativos por rede,
  avaliar o `HttpServletSseServerTransportProvider` do SDK.
- **Mais ferramentas:** manipulação de cena (criar/mover GameObjects), assets,
  animação e áudio — expandindo o registry.
- **Auditoria/log:** painel no editor mostrando cada chamada de ferramenta recebida.

Ver também: [[AGENTIC_AI_PLAN]] e [[COLLABORATION_GUIDE]].
