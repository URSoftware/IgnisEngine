# Editor de Imagens Integrado

Editor gráfico nativo do IgnisEngine (item 3 do [Roadmap](ROADMAP.md)) para desenho 2D, pintura, edição de sprites e exportação de texturas sem depender de software externo.

---

## Como Abrir

- No editor do motor: menu **Tools → Image Editor** (`Ctrl+Shift+I`).
- Standalone: `java -cp target/classes com.ignis.imageeditor.ImageEditorFrame`.

Com um projeto aberto, a exportação aponta direto para `project/assets/sprites/`.

---

## Funcionalidades

| Área | Recursos |
|---|---|
| Desenho | Lápis, borracha (com transparência real), linha, retângulo, elipse |
| Pintura | Balde de tinta (flood fill scanline), seletor de cor, conta-gotas |
| Camadas | Adicionar, remover, reordenar, visibilidade, opacidade (modelo) |
| Visualização | Zoom 25%–800% com nearest-neighbor (pixel art nítida), fundo xadrez de transparência |
| Edição | Undo/Redo por camada (`Ctrl+Z` / `Ctrl+Y`, até 25 passos) |
| Arquivos | Novo (dimensões livres), abrir PNG/JPG/GIF/BMP, salvar PNG |
| Integração | **Export to Project Sprites** grava a textura achatada direto nos assets do projeto |

---

## Arquitetura

```
com.ignis.imageeditor/
├── ImageDocument.java    # Modelo puro: canvas + pilha de camadas ARGB (sem Swing)
├── PaintCanvas.java      # Superfície de desenho: ferramentas, undo/redo, zoom
└── ImageEditorFrame.java # Janela: menus, toolbar, painel de camadas, IO
```

- Módulo desacoplado: o editor do motor só conhece o `ImageEditorFrame`; o modelo (`ImageDocument`) não depende de UI e pode ser reutilizado pelo futuro sistema de animação (frames) e pipeline de sprites.
- A composição (achatamento) usa `AlphaComposite` por camada com opacidade.

## Evoluções Planejadas

- Ferramentas de seleção (retangular, laço) e transformação (mover, escalar, rotacionar).
- Persistência de documento em camadas (formato próprio) além do PNG achatado.
- Paleta de cores e atalhos de pixel art.
