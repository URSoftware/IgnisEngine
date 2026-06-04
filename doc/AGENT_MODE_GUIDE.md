# AGENT Mode - Guia Completo

## 📋 Resumo das Melhorias

O AGENT mode foi reformulado para funcionar corretamente com o Gemini 2.5 Flash. Principais melhorias:

### 1. **Parser JSON Robusto**
- Agora extrai corretamente caracteres escapados (`\n`, `\"`, `\\`, etc.)
- Suporta respostas JSON complexas
- Melhor tratamento de erros da API
- Log detalhado de debug

### 2. **Prompt Aprimorado para o Gemini**
- Instruções muito claras e precisas
- Exemplos exatos de formatação de ações
- Instruções sobre escape de caracteres
- Passo a passo bem definido

### 3. **Parser de Ações Mais Robusto**
- Melhor detecção de ações CREATE_FILE, EDIT_FILE, DELETE_FILE
- Log detalhado de cada operação
- Mensagens claras de sucesso/falha
- Suporte para tamanho de arquivo e bytes processados

### 4. **Logging e Debug Aprimorado**
- Console log para cada etapa ([AGENT], [API RESPONSE], [PARSED], etc.)
- Informações detalhadas sobre resposta da API
- Dicas úteis quando algo não funciona
- Mostra exatamente o que foi recebido vs. esperado

---

## 🚀 Como Usar o AGENT Mode

### Passo 1: Configurar API Key
1. Vá para a aba **Settings**
2. Cole sua API Key do Google Gemini
3. Clique em **Save API Key**
4. Verifique se o status mostra "✓ Configured"

### Passo 2: Descrever a Tarefa
1. Vá para a aba **Agent**
2. Na seção "Task Description", descreva claramente o que quer fazer
3. Exemplos de tarefas válidas:
   - "Create a new script 'Player.ignis' that handles player movement with WASD keys"
   - "Create a collision detection script 'Collision.ignis' with example code"
   - "Edit the Player script to add jump functionality"

### Passo 3: Executar
1. Clique em **Execute Task**
2. Confirme que deseja realizar as modificações
3. Aguarde (geralmente 5-10 segundos)
4. Revise o resultado na seção "Agent Actions & Results"

---

## 📝 Exemplos de Tarefas

### Exemplo 1: Criar um Script Novo
```
Task: Create a new script 'Bullet.ignis' in the scripts folder 
that represents a projectile with velocity and collision detection. 
Include methods to update position and handle collisions.
```

**Resultado esperado:**
```
Agent Execution Log:
====================

Raw Response Length: 2847 chars

✓ Created file: scripts/Bullet.ignis (1250 bytes)

✅ Agent task completed successfully!
```

### Exemplo 2: Editar um Script Existente
```
Task: Edit the Player.ignis script to add a method called 'takeDamage' 
that reduces health by a given amount and triggers a hurt animation.
Include parameter validation.
```

---

## 🔧 Ações Disponíveis para o AGENT

O Gemini pode executar as seguintes ações automaticamente:

### CREATE_FILE - Criar Novo Arquivo
```
CREATE_FILE: scripts/MyScript.ignis
[conteúdo completo do arquivo aqui]
/CREATE_FILE
```

### EDIT_FILE - Editar Arquivo Existente
```
EDIT_FILE: scripts/MyScript.ignis
[conteúdo novo completo do arquivo aqui]
/EDIT_FILE
```

### DELETE_FILE - Deletar Arquivo
```
DELETE_FILE: scripts/OldScript.ignis
```
*(Requer confirmação manual - não deleta automaticamente)*

### EXECUTE_ACTION - Descrever uma Ação
```
EXECUTE_ACTION: Updated the collision system to support physics-based interactions
```

---

## 🐛 Debug e Troubleshooting

### Problema: "No structured actions found in response!"
**Causa:** O Gemini respondeu mas não usou os marcadores de ação (CREATE_FILE, EDIT_FILE, etc.)

**Solução:**
1. Revise a resposta do Gemini no log
2. Reformule a tarefa com mais clareza
3. Peça explicitamente para usar os marcadores

**Exemplo de tarefa melhor:**
```
Create a new script file called 'Player.ignis' using the CREATE_FILE action. 
The file should contain code to handle player movement.
```

### Problema: "API Error 429"
**Causa:** Atingiu o limite de requisições (5/min no free tier)

**Solução:**
- Aguarde 12 segundos antes de tentar novamente
- O sistema tem rate-limiting automático para evitar isso
- Para mais requisições, habilite billing na Google Cloud Console

### Problema: "Invalid CREATE_FILE action (missing content)"
**Causa:** O Gemini usou o marcador mas não incluiu o conteúdo

**Solução:**
- Reescreva a tarefa pedindo "código completo"
- Use exemplos na descrição

---

## 📊 Estrutura do Fluxo

```
┌─────────────────────────────────────────┐
│   Usuário clica "Execute Task"          │
└────────────────┬────────────────────────┘
                 │
                 ▼
        ┌────────────────────┐
        │  Rate Limit Check   │ (12 seg mínimo)
        └────────┬───────────┘
                 │ ✓ Pass
                 ▼
    ┌──────────────────────────────┐
    │ Build Agent Prompt with:    │
    │ - Task description          │
    │ - Project structure         │
    │ - Documentation             │
    │ - Action markers examples   │
    └──────────────┬──────────────┘
                   │
                   ▼
        ┌──────────────────────────┐
        │  Call Gemini API REST    │
        │ (v1beta/gemini-2.5-flash) │
        └──────────────┬───────────┘
                       │
                       ▼
            ┌──────────────────────┐
            │ Parse JSON Response  │
            │ (Extract "text" field) │
            └──────────┬───────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ parseAndExecuteAgentActions  │
        │ - Procura por CREATE_FILE    │
        │ - Procura por EDIT_FILE      │
        │ - Procura por DELETE_FILE    │
        │ - Gera log detalhado        │
        └──────────────┬───────────────┘
                       │
                       ▼
        ┌──────────────────────────┐
        │  aiIntegration.writeFile │
        │  (Cria/Edita arquivo)    │
        └──────────────┬───────────┘
                       │
                       ▼
        ┌──────────────────────────┐
        │  Mostrar Resultado no UI  │
        │  (Agent Output Area)      │
        └──────────────────────────┘
```

---

## 🔍 Monitoramento de Debug

Abra o **Console do Editor** (View → Toggle Developer Tools) para ver:

```
[AGENT] Sending task to Gemini...
[AGENT] Task: Create a new script...

[API RESPONSE] Received 2847 bytes
[PARSED] Successfully extracted 2800 chars

[AGENT] Parsing CREATE_FILE actions...
[AGENT] Creating file: scripts/MyScript.ignis

[AGENT] Parsing EDIT_FILE actions...
[AGENT] Parsing DELETE_FILE actions...
[AGENT] Parsing EXECUTE_ACTION...
```

---

## ⚙️ Configurações Técnicas

| Configuração | Valor | Propósito |
|---|---|---|
| API Endpoint | `v1beta/models/gemini-2.5-flash:generateContent` | Usar modelo estável atual |
| Min Request Interval | 12 segundos | Rate limiting (free tier: 5 req/min) |
| Max Alerts on Screen | 5 | Limite de alertas simultâneos |
| Alert Display Time | 3 segundos | Duração de exibição de alerta |

---

## 💡 Dicas para Melhores Resultados

### 1. **Seja Específico**
❌ Ruim: "Make a player script"
✅ Bom: "Create a script called 'Player.ignis' that handles movement with WASD keys and includes methods for jump and dash"

### 2. **Peca por Código Completo**
❌ Ruim: "Add a function"
✅ Bom: "Create a complete, functional script with all necessary methods and comments"

### 3. **Cite o Projeto**
❌ Ruim: "Create a script"
✅ Bom: "Create a script for the Ignis Game Engine that..."

### 4. **Mencione o Formato**
❌ Ruim: "Create collision detection"
✅ Bom: "Create a new .ignis script file with collision detection code"

### 5. **Revise Sempre**
- Verifique o arquivo criado
- Teste o código no editor
- Compare com documentação de padrão

---

## 🎯 Casos de Uso Comuns

### Gerar Boilerplate Scripts
```
Create an empty Player.ignis script with the standard structure 
including init() and update() methods with comments.
```

### Criar Sistemas Completos
```
Create three related scripts: Player.ignis, Enemy.ignis, and Collision.ignis
that work together to create a simple game with enemy AI and collision detection.
```

### Migração de Código
```
I have a game script from another engine. Create an equivalent Ignis script 
by translating the logic while maintaining the same functionality.
```

### Adicionar Funcionalidades
```
Edit the Game.ignis script to add a method for managing multiple scenes
and transitioning between them with proper cleanup.
```

---

## 🚨 Limitações Conhecidas

1. **Tamanho de Arquivo**: Gemini funciona melhor com arquivos < 50KB
2. **Complexidade**: Scripts muito complexos podem precisar ser divididos
3. **Unicode**: Alguns caracteres especiais podem gerar escape incorreto
4. **Formatos**: Apenas CREATE_FILE, EDIT_FILE, DELETE_FILE são suportados

---

## 📞 Suporte

Se encontrar problemas:
1. Verifique o console de debug ([AGENT] logs)
2. Revise a tarefa para maior clareza
3. Teste dengan uma tarefa simples primeiro
4. Valide sua API Key está ativa

