# AGENT Mode - Teste e Validação

## 🧪 Testes Recomendados

### Teste 1: Criar um Script Simples
**Dificuldade:** Fácil  
**Tempo esperado:** 5-10 segundos

**Tarefa:**
```
Create a simple script named 'HelloWorld.ignis' in the scripts folder 
that prints "Hello from Ignis" when initialized.
```

**Resultado esperado:**
```
Agent Execution Log:
====================

Raw Response Length: 1234 chars

✓ Created file: scripts/HelloWorld.ignis (250 bytes)

✅ Agent task completed successfully!
```

**Verificação:**
- Arquivo aparece em `projects/Game 01/project/scripts/HelloWorld.ignis`
- Contém código válido .ignis

---

### Teste 2: Múltiplas Ações
**Dificuldade:** Média  
**Tempo esperado:** 10-15 segundos

**Tarefa:**
```
Create three new scripts:
1. 'Position.ignis' - to handle entity position/movement
2. 'Health.ignis' - to handle entity health system
3. 'Collision.ignis' - to handle collision detection

Make them simple but complete with basic structure.
```

**Resultado esperado:**
```
Agent Execution Log:
====================

✓ Created file: scripts/Position.ignis (550 bytes)
✓ Created file: scripts/Health.ignis (480 bytes)
✓ Created file: scripts/Collision.ignis (620 bytes)

✅ Agent task completed successfully!
```

---

### Teste 3: Verificar Logging
**Dificuldade:** Técnica

**Passos:**
1. Abra o console do editor (View → Toggle Developer Tools)
2. Execute qualquer tarefa do AGENT
3. Procure por logs com padrão `[AGENT]`, `[API RESPONSE]`, `[PARSED]`

**Verificação de Saúde:**
```
[AGENT] Sending task to Gemini...
[AGENT] Task: Create a simple script...

[API RESPONSE] Received 1850 bytes
[PARSED] Successfully extracted 1800 chars

[AGENT] Parsing CREATE_FILE actions...
[AGENT] Creating file: scripts/Test.ignis

✓ Task successful!
```

---

## 📊 Checklist de Validação

- [ ] API Key está configurada e ativa
- [ ] Rate limit mostra aviso se testar duas vezes rápido
- [ ] Tarefas simples são executadas em 5-15 segundos
- [ ] Arquivos são criados no diretório correto
- [ ] Console mostra logs `[AGENT]` durante execução
- [ ] Mensagens de erro aparecem com clareza quando há problema
- [ ] Múltiplas ações (CREATE_FILE, EDIT_FILE) funcionam juntas

---

## 🔍 O Que Procurar em Caso de Problema

### Sintoma: "No structured actions found in response!"

**Verifique:**
1. A resposta contém texto que começa com `CREATE_FILE:` ou `EDIT_FILE:`?
2. O console mostra a resposta completa?
3. O Gemini retornou análise mas não formatou ações?

**Ação:**
1. Reescreva a tarefa mais explicitamente
2. Peça para usar exatamente: `CREATE_FILE: path/file.ext`
3. Inclua um exemplo na tarefa

---

### Sintoma: "Failed to create file: ..."

**Verifique:**
1. O caminho do arquivo está correto?
2. O diretório existe?
3. Você tem permissão de escrita?

**Ação:**
1. Verifique o caminho no Project Files panel
2. Certifique-se que `projects/Game 01/project/scripts/` existe
3. Tente um arquivo em uma pasta existente

---

### Sintoma: "API Error"

**Verifique:**
1. Está vendo "API Error 429"? → Rate limit atingido
2. Está vendo "API Error 400"? → Problema no formato da requisição
3. Está vendo "API Error 401"? → API Key inválida

**Ação:**
- Para 429: Aguarde 12 segundos
- Para 400/401: Valide sua API Key

---

## 📈 Progressão de Testes

### Nível 1: Funcionamento Básico
- [ ] Teste 1: Criar script simples
- [ ] Teste 2: Múltiplas ações
- [ ] Teste 3: Verificar arquivo criado

### Nível 2: Casos de Uso Reais
- [ ] Criar um sistema de jogador com movimento
- [ ] Criar inimigos com IA básica
- [ ] Criar sistema de colisão

### Nível 3: Fluxo Completo
- [ ] Tarefa complexa com 5+ ações
- [ ] Editar arquivos existentes
- [ ] Gerar documentação junto

---

## 🎯 Métricas de Sucesso

Se você vê isso, o AGENT mode está funcionando perfeitamente:

✅ **Tempo de Resposta**: 5-15 segundos
✅ **Taxa de Sucesso**: > 90% das tarefas completadas
✅ **Logs Claros**: Console mostra [AGENT] em todas as etapas
✅ **Arquivos Criados**: Aparecem imediatamente no projeto
✅ **Conteúdo Válido**: Código é sintaticamente correto

---

## 📝 Relatório de Teste

Ao testar, documente:

```
Data: [data]
Tarefa: [descrição]
Tempo: [segundos]
Status: [Sucesso/Falha]
Resultado: [output completo]
Observações: [qualquer coisa relevante]
```

Exemplo:
```
Data: 01/04/2024
Tarefa: Criar script Player.ignis com movimento
Tempo: 8 segundos
Status: Sucesso
Resultado: ✓ Created file: scripts/Player.ignis (1250 bytes)
Observações: Funcionou perfeitamente, código bem estruturado
```

