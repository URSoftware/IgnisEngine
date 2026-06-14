# Auditoria Arquitetural do IgnisEngine

> 2026-06-14 · Complementa [PROJECT_INVENTORY.md](PROJECT_INVENTORY.md) e [ARCHITECTURE.md](ARCHITECTURE.md).
> Foco: qualidade interna, riscos e dívidas. Nenhuma refatoração foi executada nesta auditoria.

## 1. Avaliação por dimensão

| Dimensão | Nota | Resumo |
|---|---|---|
| Acoplamento | ⚠️ Médio-alto | `Editor.java` (5580) e `Game.java` (2003) concentram responsabilidades demais. Render acoplado a AWT (`Game extends Canvas`). |
| Coesão | ⚠️ Variável | Pacotes bem separados por feature, mas classes-monolito violam responsabilidade única. |
| Modularidade | ✅ Boa (no macro) | Builder, marketplace, sub-editores e UI in-game são módulos desacopláveis. Ruim no micro (monolitos). |
| Escalabilidade | ⚠️ Média | Loop/render em `Canvas`/`BufferStrategy` funciona, mas o monolito do editor dificulta crescer painéis. |
| Extensibilidade | ✅ Boa | `BuildStrategy`/`BuildTarget` e `AIServiceProvider` usam estratégia/provedor; fácil estender. Plugins ainda sem loader. |
| Manutenibilidade | ⚠️ Comprometida | Arquivos de 1000–5500 linhas elevam o custo de mudança e o risco de regressão. |

## 2. Componentes super-acoplados / monolitos

| Arquivo | Linhas | Problema | Recomendação |
|---|---|---|---|
| `editor/Editor.java` | 5580 | Janela + Hierarchy + Inspector + Scene View + Asset Browser + menus + ações num só arquivo | Extrair painéis em classes próprias (`HierarchyPanel`, `InspectorPanel`, `SceneViewPanel`, `AssetBrowserPanel`) **antes** da migração JavaFX |
| `core/Game.java` | 2003 | Loop + render + input + estados + integrações | Separar `GameLoop`, `Renderer`, `InputRouter`; isolar render do toolkit (ponte) |
| `core/IgnisScript.java` | 1610 | Runtime de script monolítico | Modularizar por responsabilidade (parser/exec/API) — futuro |
| `audioeditor/AudioEditorFrame.java` | 1317 | UI DAW monolítica | Extrair faixas/mixer/transport |
| `core/IgnisSampleCollisions.java` | 1215 | Colisão + alertas + exemplos juntos | Separar motor de colisão de exemplos/alertas |

## 3. Acoplamento ao toolkit gráfico (crítico para JavaFX)

- **Render core preso ao AWT:** `Game extends java.awt.Canvas`, desenho via `BufferStrategy` + `Graphics2D`; `GameObject.render(Graphics g)` recebe `java.awt.Graphics`.
- **Implicação:** migrar o editor para JavaFX exige **ponte de render** (offscreen `BufferedImage` → `SwingFXUtils.toFXImage` → `Canvas` JavaFX) sem reescrever o pipeline. Ver [JAVAFX_MIGRATION_PLAN.md](JAVAFX_MIGRATION_PLAN.md).
- **Input:** listeners AWT no `Canvas` precisarão de remapeamento na ponte.

## 4. Código morto, redundância e candidatos a remoção

- `core/IgnisSampleCollisions.java` — nome "Sample" + tamanho sugerem mistura de exemplo e produção; auditar trechos não usados.
- `Editor.java` — alta probabilidade de código morto (handlers/painéis legados) acumulado; varrer com IDE (símbolos não referenciados) na fase de extração.
- WIP recente não commitado na working tree (`Editor.java`, `VectorIcon`, `AutocompleteManager`, `ScriptEditorWindow`, `EditorTextPane`, `MarkdownViewerFrame`) — consolidar e revisar.
- Verificar duplicação entre `UICanvas` (UI in-game) e widgets do editor Swing (conceitos parecidos, implementações separadas — esperado, mas vigiar).

## 5. Dependências

- Runtime: apenas `org.json` — **excelente** (baixa superfície). Manter assim.
- Nenhuma dependência desnecessária detectada no `pom.xml`.
- JavaFX entrará como dependência só na fase de migração (ver plano).

## 6. Gargalos técnicos

- **Render por cópia** (futuro, na ponte JavaFX): cópia de `BufferedImage` por frame pode custar; mitigar com reuso de buffer / `PixelBuffer`.
- **Serialização JSON manual** (`saveProperties/loadProperties` por classe): verboso e propenso a erro ao evoluir campos; considerar geração/reflexão mais ampla.
- **AssetResolver sem cache:** recarga de assets pode repetir IO; adicionar cache.
- **EDT vs thread do jogo:** fronteiras entre Swing (EDT) e a thread de loop precisam de disciplina (`invokeLater`); risco de condições de corrida ao crescer.

## 7. Dívidas técnicas priorizadas

| Dívida | Impacto | Esforço | Prioridade |
|---|---|---|---|
| `Editor.java` monolítico (5580) | Alto (bloqueia JavaFX e manutenção) | Alto | 🔴 Alta |
| Render acoplado ao AWT | Alto (define estratégia JavaFX) | Médio | 🔴 Alta |
| `Game.java` com múltiplas responsabilidades | Alto | Médio-alto | 🔴 Alta |
| Plugins sem sandbox/loader real | Médio (segurança/feature) | Médio | 🟠 Média |
| Colisões sem motor de física desacoplado | Médio | Alto | 🟠 Média |
| Serialização JSON manual por classe | Médio (evolução de schema) | Médio | 🟠 Média |
| Exportação C++ a validar | Médio | Alto | 🟠 Média |
| Ausência de testes automatizados | Médio-alto (regressão) | Médio | 🟠 Média |
| Cache de assets ausente | Baixo | Baixo | 🟢 Baixa |

## 8. Melhorias recomendadas (acionáveis)

1. **Extrair painéis do `Editor.java`** em classes coesas (pré-requisito da migração JavaFX).
2. **Isolar o render do toolkit:** introduzir uma camada `Renderer` que desenha em `BufferedImage` (habilita tanto Swing quanto JavaFX via ponte).
3. **Quebrar `Game.java`** em loop/render/input.
4. **Adicionar testes** (pelo menos serialização `.ignis` round-trip e colisões).
5. **Cache no `AssetResolver`.**
6. **Loader de plugins** com sandbox (alinha com regras do marketplace).
7. **Separar exemplos de produção** em `IgnisSampleCollisions`.
8. **CI** (compilar + futuros testes) — hoje build é manual via Maven.

## 9. Problemas encontrados (lista)

- Monolitos (Editor/Game/IgnisScript/AudioEditor/Collisions) — manutenção cara.
- Render fortemente acoplado ao AWT — risco central da migração.
- Sem testes automatizados — regressões silenciosas.
- Plugins sem sandbox — risco de segurança ao executar terceiros.
- Serialização manual — fácil esquecer campos ao evoluir.
- WIP não commitado acumulado na working tree — risco de perda/conflito (há atividade concorrente no repo).
