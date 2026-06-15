# Sincronizacao Local e Manutencao — 2026-06-15

## Contexto

A pasta `IgnisEngine-main` na area de trabalho era o ZIP original extraido do repositorio,
nao um clone git. Todas as alteracoes feitas pelo agente anterior (Claude/Cursor) iam para
clones temporarios e eram enviadas ao remote (`origin/main`), mas a pasta local permanecia
desatualizada. Quando o usuario executava o `run-editor-javafx.bat`, rodava a versao antiga.

## Problema Identificado

| Sintoma | Causa Raiz |
|---|---|
| `.bat` nao refletia mudancas | Pasta local era ZIP, nao clone git |
| Alteracoes "sumiam" | Commits iam para clones temporarios |
| Pasta `%TEMP%` lixo na raiz | Comando de clone anterior errou o caminho de destino |

## Acoes Realizadas

### Pelo agente anterior (Claude/Cursor)

1. Diagnosticou que a pasta local era um ZIP antigo
2. Identificou `projects/MyGame/` como projeto local do usuario (nao existe no repo)
3. Fez backup de `projects/`
4. Removeu a pasta `%TEMP%` (lixo)
5. Inicializou git na pasta local (`git init`)
6. Conectou ao remote e fez `git fetch origin`
7. Executou `git reset --hard origin/main` para trazer o codigo atualizado (commit `9f0be7c`)
8. **Parou durante a verificacao final**

### Pelo agente atual (Gemini)

1. **Verificou integridade do MyGame** — Confirmou que `projects/MyGame/` foi preservado
   intacto com todas as 7 pastas (assets, data, notes, plugins, prefabs, scenes, scripts)
   e o arquivo `.ignis`
2. **Verificou sincronizacao** — Confirmou que HEAD local (`9f0be7c`) estava identico ao
   `origin/main` com zero diff
3. **Inicializou submodule marketplace** — O diretorio `marketplace/` estava vazio apos
   o reset; executou `git submodule update --init --recursive` para popular com o conteudo
   do repositorio `ThyagoToledo/IginisMarketePlace`
4. **Verificou ausencia de lixo** — Confirmou que nao restavam pastas de backup,
   temporarios ou a pasta `%TEMP%`
5. **Atualizou `.gitignore`** — Adicionou regras para proteger:
   - `projects/MyGame/` (projeto local, nao deve subir ao repo)
   - `.claude/` (configuracao do assistente IA)
6. **Verificou arquivos criticos** — Confirmou presenca de `run-editor-javafx.bat`,
   `pom.xml`, `mvnw.cmd`, `Editor.java`, `Game.java`, banner, e todos os 41 docs
7. **Commit e push** — `4ba0265` ("chore: protege projetos locais (MyGame) e config
   .claude no .gitignore")
8. **Status final** — Working tree clean, branch `main` sincronizada

## Estado Final

```
git log --oneline -1  →  4ba0265 chore: protege projetos locais...
git status            →  nothing to commit, working tree clean
git diff origin/main  →  (vazio)
```

| Item | Status |
|---|---|
| Pasta local = clone git | OK |
| Remote conectado | `origin` → `https://github.com/URSoftware/IgnisEngine.git` |
| Branch | `main` |
| MyGame preservado | OK (untracked, ignorado pelo .gitignore) |
| Submodule marketplace | Inicializado |
| Lixo removido | OK |
| `.bat` funcional | Aponta para codigo atualizado |
