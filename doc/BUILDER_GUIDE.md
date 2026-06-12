# Builder — Geração de Binários Finais

Sistema responsável pela geração das distribuições finais dos jogos criados no IgnisEngine (item 1 e 2 do [Roadmap](ROADMAP.md)).

---

## Visão Geral

O Builder é um módulo desacoplado (`com.ignis.builder`) que empacota um projeto `.ignis` em distribuições executáveis. Possui duas estratégias de compilação, despachadas automaticamente por plataforma:

| Estratégia | Plataformas | Saída |
|---|---|---|
| `JavaBuildStrategy` | Windows, Linux, macOS | Distribuição JVM (jar executável + launcher) |
| `CppExportStrategy` | Xbox, PlayStation, Nintendo Switch (futuro) | Projeto C++ compilável (CMake) |

Componentes:

```
com.ignis.builder/
├── Builder.java            # Orquestrador + CLI headless
├── BuildTarget.java        # Plataformas e estratégia de cada uma
├── BuildConfig.java        # Configuração por projeto (build.json)
├── BuildStrategy.java      # Contrato das estratégias
├── JavaBuildStrategy.java  # Build JVM
├── CppExportStrategy.java  # Exportação nativa C++
├── BuildResult.java        # Resultado por target
├── BuildLogger.java        # Log de progresso (UI ou console)
└── BuildIO.java            # Utilitários de arquivo/zip/jar

com.ignis.runtime/
└── GameRuntime.java        # Entry point do jogo distribuído (sem editor)
```

---

## Como Usar (Editor)

1. Abra um projeto no editor.
2. Menu **Build → Build Project...** (`Ctrl+Shift+B`).
3. Configure nome, versão, resolução, fullscreen e marque as plataformas.
4. Clique em **Build**. O log mostra o progresso; ao final, **Open Output Folder** abre a saída.

As configurações são persistidas em `projects/[Nome]/build.json`.

---

## Como Usar (Linha de Comando)

```bash
java -cp target/classes;json.jar com.ignis.builder.Builder "projects/Game 01/Game 01.ignis" WINDOWS LINUX XBOX
```

Sem targets explícitos, usa os targets salvos no `build.json` (fallback: WINDOWS).

---

## Saída do Build Java

```
projects/[Nome]/build/[plataforma]/[GameName]/
├── engine/
│   ├── ignis-engine.jar    # Jar executável (Main-Class: com.ignis.runtime.GameRuntime)
│   └── json.jar            # Dependência org.json
├── projects/[Nome]/        # Cópia integral do projeto (layout preservado)
├── runtime.json            # Projeto, título, resolução, fullscreen
├── [GameName].bat|.sh      # Launcher (faz cd para a raiz do app)
└── README.txt              # Instruções de execução
```

Também é gerado o zip distribuível `[GameName]-[versão]-[plataforma].zip`.

O layout `projects/[Nome]/` é preservado de propósito: caminhos relativos de assets (ex.: `MusicPath`) resolvem contra o diretório de trabalho exatamente como no editor — por isso o launcher define o diretório de trabalho antes de iniciar a JVM.

Requisito da máquina do jogador: Java 17+.

### GameRuntime

O `GameRuntime` é o ponto de entrada das distribuições: carrega o `.ignis` indicado em `runtime.json` (ou em `args[0]`), instancia `Game` com os visuais de editor desligados (grid, gizmos, colliders, câmera de editor), conecta `ScriptManager`/`PrefabManager` e entra direto em modo PLAYING.

---

## Saída da Exportação C++

```
projects/[Nome]/build/[console]/[GameName]-cpp/
├── CMakeLists.txt          # Projeto CMake (C++17)
├── generated/scene_data.h  # Cenas convertidas em código intermediário C++
├── src/
│   ├── main.cpp            # Entry point portátil
│   ├── ignis_runtime.hpp   # Hooks do runtime (init/tick/render/shutdown)
│   └── ignis_runtime.cpp
├── export/                 # Conteúdo do jogo (assets, scripts, cenas, data)
└── platform/[console]/README.md  # Ponto de integração com o SDK proprietário
```

A exportação converte as estruturas do projeto (cenas/entidades lidas do `.ignis`) em headers C++ (`scene_data.h`) e copia o conteúdo do jogo como dado intermediário. Os SDKs de console são proprietários e não redistribuíveis; o projeto gerado compila um núcleo portátil e documenta em `platform/` onde cada SDK se conecta (GDK para Xbox, SDK PlayStation, Nintendo Dev Interface).

---

## Decisões de Arquitetura

- Módulo 100% desacoplado da UI: o editor consome o Builder via `BuildDialog`; o mesmo código roda headless via CLI.
- O jar do motor é gerado a partir do code source em execução (funciona rodando de `target/classes` ou de jar empacotado).
- Configuração por plataforma: `BuildConfig` aceita overrides por target (`platforms` no `build.json`), preparando configurações específicas por console.
- Targets futuros (Nintendo Switch) ficam visíveis porém desabilitados, falhando com mensagem clara se forçados via CLI.

## Limitações Conhecidas

- Sprites referenciados por caminho absoluto fora da pasta do projeto não são incluídos no pacote (corrigir no fluxo de import de assets do editor).
- O build Java requer Java instalado na máquina do jogador (empacotamento de JRE via jlink/jpackage é evolução planejada).
- O runtime C++ é um esqueleto compilável: a portagem do loop de jogo (tick/render/colisões) para C++ é a próxima etapa do item 2 do roadmap.
