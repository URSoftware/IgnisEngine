# Roadmap Mestre — IgnisEngine

> 2026-06-15 · Referência principal de evolução do projeto.
> Base: [PROJECT_INVENTORY.md](PROJECT_INVENTORY.md) · [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md) · [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).
> Legenda: Prioridade 🔴 Alta / 🟠 Média / 🟢 Baixa · Complexidade B/M/A.

Cada item planejado traz: **Prioridade · Complexidade · Dependências · Benefício**.

---

## Core Engine

**Concluído:** game loop (tick/render), `GameObject` (modelo herança + scripts), `Scene`, `Transform`, `Camera`, `Viewport`, `Input`, prefabs, serialização `.ignis` (JSON), ponte de render para JavaFX (`renderWorldTo` -> `SwingFXUtils`).

**Em andamento:** consolidação de WIP recente (ícones vetoriais, editor de código).

**Planejado:**
- Camada `Renderer` desacoplada do toolkit — 🔴 · M · dep: nenhuma · habilita JavaFX e testes de render.
- Quebra de `Game.java` em loop/render/input — 🔴 · M · dep: Renderer · manutenção/escala.
- Cache de assets no `AssetResolver` — 🟢 · B · dep: nenhuma · performance de IO.
- Serialização por reflexão mais ampla (menos `saveProperties` manual) — 🟠 · M · dep: nenhuma · evolução de schema segura.

## Editor

**Concluído:** janela principal, Hierarchy, Inspector, Scene View, Asset Browser, editor de código (autocomplete), visualizador Markdown, layout persistido.

**Em andamento:** editor de código temático (WIP).

**Planejado:**
- Extrair painéis do `Editor.java` — 🔴 · M · dep: nenhuma · organização do código legador Swing.
- Multi-abas para edição de múltiplos scripts/cenas — 🟠 · M · dep: nenhuma · UX de desenvolvimento.
- Desfazer/Refazer (Undo/Redo) no histórico de ações do editor — 🔴 · A · dep: comando centralizado · confiabilidade.

## Sub-editores Especializados

**Concluído:**
- `imageeditor` (PaintCanvas, camadas, ferramentas básicas).
- `audioeditor` (DawFrame, equalizador, timeline).
- `notes` (Wiki/MarkdownViewer).
- `animation` (Model/Timeline/SpriteSheet para animações 2D).

**Planejado:**
- Visualizador/Editor de Curvas (para interpolação de animações) — 🟠 · M · dep: animation · controle de movimento.
- Editor de Partículas integrado — 🟠 · M · dep: core/renderer · efeitos visuais ricos.

## Compilação e Distribuição (Builder)

**Concluído:** exportação para executável JVM (Windows/Linux/macOS), empacotamento em JAR auto-executável, log de build unificado.

**Planejado:**
- Validação real da exportação C++ (`CppExportStrategy`) em consoles/desktop nativo — 🔴 · A · dep: nenhuma · portabilidade máxima.
- Compilação Ahead-Of-Time (AOT) usando GraalVM para gerar binários nativos sem JVM — 🟢 · A · dep: nenhuma · startup time / proteção de código.

## Sistema de Scripting

**Concluído:** chamadas em tempo de tick, controle de componentes (Transform/Camera/Sound), detecção de colisões scriptada, hot-reload em runtime.

**Planejado:**
- Visual Scripting (Nós/Fluxo) para não-programadores — 🟢 · A · dep: editor · acessibilidade.
- Depurador de Scripts (breakpoints, variáveis em tempo real) — 🟠 · A · dep: nenhuma · produtividade.

## Inteligência Artificial (Agent Mode)

**Concluído:** integração com Gemini, assistente contextual no editor, geração de scripts e auxílio de design.

**Planejado:**
- Multi-provedor de IA (OpenAI/Anthropic/local) sobre `AIServiceProvider` — 🟠 · M · dep: nenhuma · flexibilidade.
- Ações de IA no editor (gerar script/cena) com sandbox — 🟢 · A · dep: loader de plugins/sandbox · produtividade.

## Comunidade e Marketplace

**Concluído:** backend web (Next.js + Neon, Vercel), OAuth GitHub, gate de segurança, admin (ban), tokens de publicação, camada legal; cliente Java (`MarketplaceClient`) + `CommunityFrame` (2 botões: site/token), 1-click install.

**Planejado:**
- Loader de plugins com sandbox no editor — 🟠 · M · dep: nenhuma · segurança/feature.
- Device Flow do GitHub para login do editor (em vez de token manual) — 🟢 · M · dep: nenhuma · UX.
- Versionamento/atualização de pacotes instalados — 🟢 · M · dep: loader · manutenção de pacotes.

## Migração JavaFX (feature mestre)

**Concluído:** F0 (infra/deps) → F1 (casca/ponte de render) → F2 (painéis nativos TreeView/Inspector) → F3 (janelas-ferramenta) → F3.5 (janelas vinculadas ao menu, roteamento de input, tela de projetos recentes, salvar/abrir, Asset Browser). Ver [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).

**Em andamento:** F4 (Tema CSS escuro e limpeza geral).

---

## Próximas Prioridades (ordenado)

1. 🔴 **Fase 4 da migração JavaFX** (Tema CSS escuro, layouts flexíveis SplitPane/Stage, remoção de dependências legadas).
2. 🟠 **Testes automatizados** (serialização `.ignis` round-trip, colisões).
3. 🟠 **Loader de plugins com sandbox** (alinha com o marketplace).
4. 🟠 **Quebrar `Game.java`** (desacoplar loop, render e input).
5. 🟠 **Validar exportação C++** do Builder.
6. 🟢 **Cache de assets no `AssetResolver`** (performance de IO).
