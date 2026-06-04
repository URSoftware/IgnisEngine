# AGENT Mode - Documentação Técnica

## 🏗️ Arquitetura do AGENT Mode

### Componentes Principais

```
┌─────────────────────────────────────────────┐
│           AuxiliaryPanel (GUI)              │
├─────────────────────────────────────────────┤
│ - agentTaskArea: JTextArea (input)          │
│ - agentOutputArea: JTextArea (output)       │
│ - agentStatusLabel: JLabel (status)         │
│ - agentExecuteButton: JButton               │
└────────────────┬────────────────────────────┘
                 │
                 ├─→ handleAgentMode()
                 │   ├─ Verificar API Key
                 │   ├─ Verificar Rate Limit
                 │   ├─ Validar Task
                 │   └─ Executar em Background Thread
                 │
                 ├─→ callGeminiAPI()
                 │   ├─ callGeminiAPIViaREST()
                 │   │  └─ Use Java 11+ HttpClient
                 │   └─ callGeminiAPIViaURLConnection()
                 │      └─ Fallback para versões antigas
                 │
                 ├─→ parseGeminiResponse()
                 │   └─ extractJsonField()
                 │       └─ Parse JSON com escape sequences
                 │
                 └─→ parseAndExecuteAgentActions()
                     ├─ Procurar CREATE_FILE
                     ├─ Procurar EDIT_FILE
                     ├─ Procurar DELETE_FILE
                     ├─ Procurar EXECUTE_ACTION
                     └─ aiIntegration.writeFileContent()
                         └─ Escrever no disco
```

---

## 🔄 Fluxo de Execução Detalhado

### 1. handleAgentMode() - Inicialização

```java
private void handleAgentMode() {
    // 1. Validação de pré-requisitos
    if (!aiIntegration.hasApiKey()) { /* erro */ }
    if (!checkRateLimit()) { /* aguardar */ }
    if (task.isEmpty()) { /* erro */ }
    
    // 2. Confirmação do usuário
    JOptionPane.showConfirmDialog(...)
    
    // 3. Estado UI
    agentStatusLabel.setText("⏳ Executing agent task...")
    agentExecuteButton.setEnabled(false)
    
    // 4. Executar em thread background
    new Thread(() -> {
        try {
            String agentPrompt = buildPrompt(task)
            String response = callGeminiAPI(agentPrompt, true)
            String result = parseAndExecuteAgentActions(response)
            
            SwingUtilities.invokeLater(() -> {
                agentOutputArea.setText(result)
                updateStatus(result)
                agentExecuteButton.setEnabled(true)
            })
        } catch (Exception e) { /* handle error */ }
    }).start()
}
```

### 2. buildPrompt() - Construção do Prompt

O prompt inclui:
```
1. TASK DESCRIPTION
   - Descrição exata do que fazer
   - Contexto do projeto

2. PROJECT STRUCTURE
   - Organização de pastas
   - Arquivos existentes
   - Padrões do projeto

3. DOCUMENTATION
   - Guias de integração
   - Exemplos de código
   - Padrões esperados

4. ACTION MARKERS
   - CREATE_FILE: path
     <content>
     /CREATE_FILE
   
   - EDIT_FILE: path
     <content>
     /EDIT_FILE
   
   - DELETE_FILE: path
   
   - EXECUTE_ACTION: description

5. INSTRUCTIONS
   - Step by step
   - Escape characters properly
   - Complete, functional code
   - One action marker per action
```

### 3. callGeminiAPI() - Chamada da API

```
callGeminiAPI(prompt, agentMode)
    │
    ├─→ callGeminiAPIViaREST()
    │   │
    │   ├─ Build JSON Request Body
    │   │  {
    │   │    "contents": [{
    │   │      "parts": [{
    │   │        "text": "escaped prompt here"
    │   │      }]
    │   │    }]
    │   │  }
    │   │
    │   ├─ HttpClient.newHttpClient()
    │   ├─ HttpRequest -> POST to:
    │   │  https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
    │   │
    │   ├─ Response status check
    │   │  200 → parseGeminiResponse()
    │   │  4xx/5xx → return error message
    │   │
    │   └─ Handle Exceptions
    │       NoClassDefFoundError → fallback to URLConnection
    │
    └─→ callGeminiAPIViaURLConnection() (fallback)
        └─ Java API clássica para < Java 11
```

### 4. parseGeminiResponse() - Parser JSON

O Gemini retorna:
```json
{
  "candidates": [{
    "content": {
      "parts": [{
        "text": "CREATE_FILE: path\n... content ...\n/CREATE_FILE"
      }]
    }
  }]
}
```

**Processo de Parsing:**
1. Verificar se há `"error"` na resposta
2. Procurar por `"text":` no JSON
3. Chamar `extractJsonField(json, "\"text\"")`
4. Desescapar caracteres especiais:
   - `\\n` → `\n` (newline)
   - `\\t` → `\t` (tab)
   - `\\"` → `"` (quote)
   - `\\\\` → `\` (backslash)

```java
private String extractJsonField(String json, String fieldName) {
    // 1. Find field name marker
    int fieldIndex = json.indexOf(fieldName + ":");
    
    // 2. Find opening quote
    int startIdx = json.indexOf('"', fieldIndex)
    
    // 3. Parse até closing quote, lidando com escapes
    StringBuilder result = new StringBuilder()
    for (int i = startIdx+1; i < json.length(); i++) {
        if (json[i] == '\\' && i+1 < json.length()) {
            // Handle escape sequence
            switch(json[i+1]) {
                case 'n': result.append('\n'); i++; break
                case 't': result.append('\t'); i++; break
                case '"': result.append('"'); i++; break
                // etc...
            }
        } else if (json[i] == '"') {
            // End of string found
            break
        } else {
            result.append(json[i])
        }
    }
    return result.toString()
}
```

### 5. parseAndExecuteAgentActions() - Execução

```
parseAndExecuteAgentActions(response)
    │
    ├─ Log: Response length
    ├─ Check: Error messages
    │
    ├─ Parse CREATE_FILE
    │  └─ response.split("CREATE_FILE:")
    │     └─ Extract path + content
    │         └─ aiIntegration.writeFileContent(path, content)
    │             └─ File created on disk
    │
    ├─ Parse EDIT_FILE
    │  └─ Same as CREATE_FILE
    │     └─ Overwrites existing file
    │
    ├─ Parse DELETE_FILE
    │  └─ Log que requer manual review
    │
    ├─ Parse EXECUTE_ACTION
    │  └─ Log description
    │
    └─ Generate Report
       └─ Show success/failures
           └─ Display Agent Creation Log
```

---

## 🛠️ Componentes Auxiliares

### checkRateLimit()

```java
private synchronized boolean checkRateLimit() {
    long currentTime = System.currentTimeMillis()
    long timeSinceLastRequest = currentTime - lastRequestTime
    
    if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
        // 12 segundos ainda não passaram
        return false
    }
    
    lastRequestTime = currentTime
    return true
}
```

**Propósito:**
- Free tier tem limite de 5 requisições/minuto
- 12 segundos entre requisições = ~5 por minuto (seguro)
- Previne erro HTTP 429

### escapeJson()

```java
private String escapeJson(String text) {
    return text.replace("\\", "\\\\")
               .replace("\"", "\\\"")
               .replace("\n", "\\n")
               .replace("\r", "\\r")
               .replace("\t", "\\t")
}
```

**Exemplo:**
```
Input:  Hello "world"\nHow are you?
Output: Hello \"world\"\\nHow are you?
```

---

## 📊 Estrutura de Dados

### AlertMessage (para integração com sistema de alerta)

```java
static class AlertMessage {
    String message
    long timestamp
    
    AlertMessage(String message) {
        this.message = message
        this.timestamp = System.currentTimeMillis()
    }
}
```

### Config Constantes

| Constante | Valor | Uso |
|---|---|---|
| `MIN_REQUEST_INTERVAL_MS` | 12000 | Rate limiting (12 seg) |
| `RATE_LIMIT_WARNING` | String | Mensagem de aviso |
| API Endpoint | `https://...v1beta/models/gemini-2.5-flash:generateContent` | URL da API |

---

## 🔍 Logging e Debug

### Padrões de Log

**Sucesso:**
```
[AGENT] Sending task to Gemini...
[AGENT] Task: Create a new script...

[API RESPONSE] Received 2847 bytes
[PARSED] Successfully extracted 2800 chars

[AGENT] Parsing CREATE_FILE actions...
[AGENT] Creating file: scripts/Test.ignis

✓ Created file: scripts/Test.ignis (1250 bytes)
✅ Agent task completed successfully!
```

**Erro API:**
```
[API ERROR] Error response: {"error":{"code":429}}
API Error: 429
```

**Erro de Parsing:**
```
[PARSE ERROR] Could not extract text field from: {"candidates":[...]}
Unexpected response format: ...
```

---

## ⚠️ Tratamento de Erros

### Níveis de Erro

| Nível | Exemplo | Handling |
|---|---|---|
| **FATAL** | API Key inválida | Mostrar diálogo, permitir retry |
| **QUOTA** | Error 429 | Informar rate limit, sugerir esperar |
| **PARSE** | JSON malformado | Mostrar resposta bruta para debug |
| **FILE** | Falha ao escrever | Log individual, continuar com próximo |

### Try-Catch Strategy

```
try {
    // Chamada a API
    String response = callGeminiAPI(prompt, true)
    
    // Parse resposta
    String result = parseAndExecuteAgentActions(response)
    
    // UI update
    agentOutputArea.setText(result)
} catch (Exception e) {
    // Log stack trace
    e.printStackTrace()
    
    // Construir mensagem de erro
    String errorMsg = "Error: " + e.getMessage() + "\n\n"
    errorMsg += "Stack trace:\n"
    
    // Mostrar ao usuário
    agentOutputArea.setText(errorMsg)
    agentStatusLabel.setText("❌ Error")
}
```

---

## 🚀 Otimizações

### 1. Thread Background
- Requisição à API é não-bloqueante
- UI continua responsivo
- Usuário pode ver status "⏳ Processing..."

### 2. Lazy Parsing
- Apenas parseia ações que encontra
- Não valida estrutura JSON inteira
- Rápido mesmo com respostas grandes

### 3. Streaming
- Não carrega resposta inteira em memória
- BufferedReader para URLConnection
- Concatena linha por linha

---

## 🔐 Segurança

### API Key Handling
```
✅ Salvo em AIIntegration (encriptado)
✅ Não exibido em console
✅ JPasswordField para input
❌ Nunca log em arquivo
❌ Nunca transmitido sem HTTPS
```

### FILE I/O
```
✅ Escreve apenas em diretório do projeto
✅ Cria diretório se não existir
✅ Sobrescreve apenas com confirmação
❌ Não permite path traversal (../../../)
❌ Não escreve arquivos do sistema
```

---

## 📈 Performance

### Benchmarks Esperados

| Operação | Tempo Esperado |
|---|---|
| Rate limit check | < 1ms |
| Build prompt | < 10ms |
| Call API | 3-8 segundos |
| Parse response | < 50ms |
| Write files | < 100ms (por arquivo) |
| **Total** | **5-15 segundos** |

---

## 🔮 Melhorias Futuras

1. **Suporte a múltiplas extensões de arquivo**
   - `.ignis` (atual)
   - `.java` (futura)
   - `.json` (futura)

2. **Validação de código antes de escrever**
   - Sintaxe check
   - Lint
   - Type validation

3. **Integração com controle de versão**
   - Git diff preview
   - Auto-commit
   - Rollback suporte

4. **Histórico de tarefas**
   - Log de tudo que foi feito
   - Undo de últimas ações
   - Reexecução de tarefas

5. **Modelos alternativos**
   - Suporte a GPT-4
   - Suporte a Claude
   - Fallback automático

