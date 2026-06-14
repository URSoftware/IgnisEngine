# Roadmap Mestre — IgnisEngine

> 2026-06-14 · Referência principal de evolução do projeto.
> Base: [PROJECT_INVENTORY.md](PROJECT_INVENTORY.md) · [ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md) · [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).
> Legenda: Prioridade 🔴 Alta / 🟠 Média / 🟢 Baixa · Complexidade B/M/A.

Cada item planejado traz: **Prioridade · Complexidade · Dependências · Benefício**.

---

## Core Engine

**Concluído:** game loop (tick/render), `GameObject` (modelo herança + scripts), `Scene`, `Transform`, `Camera`, `Viewport`, `Input`, prefabs, serialização `.ignis` (JSON).

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
- Extrair painéis do `Editor.java` (5580) em classes coesas — 🔴 · A · dep: nenhuma · pré-requisito da migração JavaFX.
- Migração da UI para JavaFX (feature mestre da `main`) — 🔴 · A · dep: extração de painéis + Renderer · UI moderna/CSS/binding.

## Renderização

**Concluído:** pipeline AWT (`Game extends Canvas`, `BufferStrategy`, `Graphics2D`), double buffering, viewport/zoom, múltiplas câmeras.

**Em andamento:** —

**Planejado:**
- Ponte de render para JavaFX (offscreen `BufferedImage` → `Canvas` FX) — 🔴 · M · dep: Renderer · base da migração.
- Otimização da cópia de frame (reuso de buffer / `PixelBuffer`) — 🟠 · M · dep: ponte · FPS.

## Física / Colisões

**Concluído:** detecção/respostas básicas + alertas (`IgnisSampleCollisions`).

**Planejado:**
- Motor de física desacoplado (rigidbodies, solver, fricção/restituição) — 🟠 · A · dep: separar exemplos de produção · gameplay robusto.
- Separar exemplos de produção em `IgnisSampleCollisions` — 🟠 · M · dep: nenhuma · clareza/manutenção.

## Áudio

**Concluído:** engine de som (`IgnisSoundEngine`, `MusicPath`), editor DAW (`AudioEditorFrame`, multipista/mix), processamento WAV.

**Planejado:**
- Modularizar a UI do DAW (faixas/mixer/transport) — 🟠 · M · dep: nenhuma · manutenção.
- Mais formatos além de WAV — 🟢 · M · dep: nenhuma · alcance.

## Animação

**Concluído:** 2D por sprites/keyframes (`Animator`, `SpriteAnimation`, `AnimationEditorFrame`), IO de animação.

**Planejado:**
- Animação 3D (skeletal / blend trees) — 🟢 · A · dep: pipeline de render · escopo futuro.
- Timeline mais rica (curvas/easing) — 🟠 · M · dep: nenhuma · qualidade de animação.

## Builder

**Concluído:** orquestração multiplataforma (`Builder`, `BuildStrategy`, `BuildTarget`), estratégia Java (JVM Win/Linux/macOS), runtime standalone.

**Em andamento/Experimental:** exportação C++ (`CppExportStrategy`).

**Planejado:**
- Validar/concluir exportação C++ compilável (consoles) — 🟠 · A · dep: nenhuma · alvo de consoles.
- `jpackage`/`jlink` para distribuir com JavaFX (pós-migração) — 🟠 · M · dep: migração JavaFX · empacotamento.

## IA

**Concluído:** integração Gemini ("Agent Mode") via `AIIntegration`/`GeminiProvider`, abstração `AIServiceProvider`.

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

**Em andamento:** terreno preparado (branches `Legado`/`main`, plano técnico). Ver [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).

**Planejado (fases):** F0 infra (deps/plugin) → F1 casca + ponte de render → F2 painéis nativos (TreeView/Inspector) → F3 janelas-ferramenta → F4 tema CSS e limpeza.

---

## Próximas Prioridades (ordenado)

1. 🔴 **Extrair painéis do `Editor.java`** (pré-requisito da migração).
2. 🔴 **Camada `Renderer` desacoplada do toolkit** (habilita ponte JavaFX + testes).
3. 🔴 **Fase 0 da migração JavaFX** (deps + `javafx-maven-plugin` + pacote `editor.fx`).
4. 🔴 **Ponte de render** (prova de conceito Viewport em JavaFX) + medir FPS.
5. 🟠 **Testes automatizados** (serialização `.ignis` round-trip, colisões).
6. 🟠 **Loader de plugins com sandbox** (alinha com o marketplace).
7. 🟠 **Quebrar `Game.java`** (loop/render/input).
8. 🟠 **Validar exportação C++** do Builder.
