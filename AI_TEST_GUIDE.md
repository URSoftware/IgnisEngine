# Ignis AI Integration - Quick Test Guide

## What Was Fixed

### 1. **API Model Updated** ✅
- **Before**: Using `gemini-pro` (descontinuado) com `v1beta`
- **After**: Using `gemini-2.0-flash` com `v1` (modelo atual e funcionando)

### 2. **Error Handling Improved** ✅
- Agora mostra erros detalhados da API
- Status visual melhorado (⏳ processando, ✓ sucesso, ❌ erro)
- Stack traces de exceções exibidos para debug

### 3. **AGENT Mode Output** ✅
- Agora mostra feedback quando não há ações estruturadas
- Lista exatamente quais arquivos foram criados/editados
- Melhor logging de erros e ações

---

## Quick Test Steps

### Test 1: ASK Mode (Simples)

1. Abra o Ignis Editor
2. Carregue um projeto (ou crie um novo)
3. Vá para a aba **Auxiliary** → **⚙️ Settings**
4. Cole sua API key do Google Generative AI (https://aistudio.google.com/app/apikey)
5. Clique "💾 Save API Key"
6. Status deve mostrar: "Status: ✓ Configured"

7. Vá para **❓ Ask** tab
8. Digite uma pergunta simples:
   ```
   What game engine is Ignis?
   ```
9. Clique "🚀 Send Question"
10. Espere 2-5 segundos

**Resultado esperado**: Você deve receber uma resposta sobre o Ignis Engine

---

### Test 2: ASK Mode (Com Contexto do Projeto)

1. No mesmo projeto, vá para **❓ Ask**
2. Digite uma pergunta sobre seu projeto:
   ```
   Based on my project structure, what's the best way to organize script files?
   ```
3. Clique "🚀 Send Question"

**Resultado esperado**: A IA deve analisar sua estrutura de projeto e dar recomendações

---

### Test 3: AGENT Mode (Criar Arquivo)

1. Vá para **🤖 Agent** tab
2. Limpe o campo "Task Description" e digite:
   ```
   Create a simple test.txt file in the project root with the content "Hello from Ignis AI"
   ```
3. Clique "⚡ Execute Task"
4. Confirme no diálogo que aparece
5. Espere 3-8 segundos

**Resultado esperado**: 
- Output mostra "✓ Created file: test.txt"
- Arquivo `test.txt` aparece na pasta do projeto com conteúdo "Hello from Ignis AI"

---

### Test 4: AGENT Mode (Criar Script)

1. Vá para **🤖 Agent** tab
2. Digite:
   ```
   Create a simple script file at scripts/HelloWorld.ignis that prints "Hello World" when the game starts
   ```
3. Clique "⚡ Execute Task"
4. Confirme
5. Espere 3-8 segundos

**Resultado esperado**:
- Output mostra "✓ Created file: scripts/HelloWorld.ignis"
- Arquivo aparece em `project/scripts/HelloWorld.ignis`
- Contém código válido de script Ignis

---

## Diagnóstico de Problemas

### Problema: "API Error: 404"
**Solução**: 
- ✓ Já foi corrigido! Atualizar para o arquivo novo

### Problema: "API Key not configured"
**Solução**:
1. Vá para Settings tab
2. Verifique se a chave foi inserida (não vazio)
3. Clique "Save API Key" novamente
4. Recarregue o projeto

### Problema: "Network error"
**Solução**:
1. Verifique internet conectando a google.com
2. Verifique se firewall não bloqueia googleapis.com
3. Tente novamente em alguns segundos

### Problema: AGENT Mode não cria arquivo
**Solução**:
1. Verifique o output - deve mostrar "✓ Created file: ..."
2. Se mostrar "No structured actions found", a IA não formatou corretamente
3. Tente com instrução mais simples
4. Verifique permissões de escrita na pasta do projeto

### Problema: Resposta lenta (>10 segundos)
**Solução**:
- Normal para projetos grandes
- Primeira requisição é mais lenta
- Próximas são mais rápidas (cache)

---

## Estrutura de Arquivos para Referência

Quando você abre um projeto, a estrutura esperada é:

```
MyProject/
├── MyProject.ignis           # Arquivo do projeto
├── ai_settings.json          # NOVO: Armazena API key (git ignore this!)
└── project/
    ├── assets/
    │   ├── sprites/
    │   ├── animations/
    │   ├── fonts/
    │   ├── music/
    │   ├── sounds/
    │   └── ...
    ├── scripts/              # Local para criar scripts com AGENT
    │   └── HelloWorld.ignis  # Arquivo criado pelo AGENT
    ├── scenes/
    ├── prefabs/
    ├── ui/
    └── data/
```

---

## Próximas Melhorias Possíveis

- [ ] Histórico de conversas em ASK
- [ ] Streaming de respostas (resposta em tempo real conforme a IA escreve)
- [ ] Syntax highlighting nas respostas
- [ ] Suporte a modelos locais (Ollama)
- [ ] Botão para copiar resposta
- [ ] Edição de prompts salvos freq

uentes

---

## Feedback

Se você encontrar algum problema:

1. Anote a mensagem de erro exata
2. Tente novamente uma ou duas vezes
3. Verif ique sua API key
4. Verifique sua conexão de internet
5. Relate o erro com a mensagem completa

