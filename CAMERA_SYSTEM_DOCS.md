# Sistema de Câmera e Viewport - Ignis Engine

## Visão Geral das Mudanças

Este documento descreve a grande atualização do sistema de renderização do Ignis Engine, introduzindo um sistema profissional de câmera com matriz de transformação de visualização, gerenciamento de viewports e funções de conversão de espaço.

---

## 1. Novos Arquivos Criados

### 1.1 `Transform.java`
**Localização:** `src/com/ignis/core/Transform.java`

Componente fundamental para armazenar dados espaciais:
- **Posição (X, Y):** Coordenadas no mundo
- **Rotação:** Em graus (normalizada para 0-360)
- **Escala (scaleX, scaleY):** Fator de escala

```java
Transform transform = new Transform(100, 200);  // Posição
transform.setRotation(45);                      // Rotação
transform.setScale(2.0, 2.0);                   // Escala
```

### 1.2 `Viewport.java`
**Localização:** `src/com/ignis/core/Viewport.java`

Gerencia o mapeamento entre espaço do mundo e espaço da tela:
- **Resolução de Referência:** 1920x1080 (fixa)
- **Modo Expand:** Quando a janela é redimensionada, mostra mais/menos do mundo em vez de esticar
- **Suporte a múltiplos viewports:** Para split-screen ou minimapa

```java
Viewport viewport = new Viewport(1280, 720);
double visibleWidth = viewport.getVisibleWorldWidth();   // Largura visível do mundo
double visibleHeight = viewport.getVisibleWorldHeight(); // Altura visível do mundo
```

### 1.3 `Camera.java`
**Localização:** `src/com/ignis/core/Camera.java`

Entidade de câmera que estende GameObject:
- **Transform próprio:** Posição, rotação e zoom
- **Matriz de Visualização (View Matrix):** Consolidação de todas as transformações
- **Funções de Conversão:** WorldToScreen e ScreenToWorld

```java
Camera camera = new Camera("MainCamera", game, 0, 0);
camera.setZoom(1.5);                           // Zoom 150%
camera.setPosition(500, 300);                  // Mover câmera
Point2D.Double screen = camera.worldToScreen(100, 100);  // Converter mundo → tela
Point2D.Double world = camera.screenToWorld(640, 360);   // Converter tela → mundo
```

---

## 2. Sistema de Matriz de Visualização (View Matrix)

A câmera utiliza uma `AffineTransform` para consolidar todas as transformações em uma única matriz:

### Ordem das Transformações:
1. **Translação para o centro da tela** - O ponto (0,0) do mundo aparece no centro
2. **Aplicação do Zoom** - Escala a partir do centro
3. **Rotação da Câmera** - Gira o mundo ao redor do foco
4. **Translação inversa da posição** - Move o mundo na direção oposta à câmera

### Benefícios:
- ✅ Uma única operação de matriz por frame
- ✅ Suporte completo a zoom e rotação
- ✅ Origem centralizada (câmera em 0,0 mostra centro da tela)

---

## 3. Centralização da Origem (Pivot)

**Comportamento Anterior:** Ponto (0,0) ficava no canto superior esquerdo.

**Novo Comportamento:** Quando a câmera está em (0,0), o ponto zero do mundo aparece **no centro da tela**.

Isso é calculado automaticamente usando o centro do viewport:
```java
double screenCenterX = viewport.getScreenCenterX();  // 960 em 1920 de largura
double screenCenterY = viewport.getScreenCenterY();  // 540 em 1080 de altura
```

---

## 4. Funções de Conversão de Espaço

### 4.1 WorldToScreen
Converte uma posição no mundo para pixel na tela.

**Uso:** Essencial para renderização.
```java
Point2D.Double screenPos = camera.worldToScreen(worldX, worldY);
g2d.drawImage(sprite, (int)screenPos.x, (int)screenPos.y, null);
```

### 4.2 ScreenToWorld
Converte a posição do cursor do mouse para coordenada no mundo.

**Uso:** Essencial para seleção de objetos e interatividade.
```java
Point2D.Double worldPos = camera.screenToWorld(mouseX, mouseY);
// Agora worldPos.x e worldPos.y são as coordenadas do mundo onde o mouse está
```

---

## 5. Pipeline de Renderização Câmera-Dependente

### Fluxo de Renderização:
1. O motor seleciona a **câmera ativa**
2. A câmera gera sua **matriz de transformação atual**
3. Para cada objeto na lista de renderização (respeitando Z-Index):
   - Aplica a matriz da câmera às coordenadas globais
   - Renderiza o objeto na posição resultante
4. Restaura a transformação original para UI/overlays

```java
// No método render() do Game:
AffineTransform originalTransform = g2d.getTransform();
activeCamera.applyTransform(g2d);

for (GameObject entity : entities) {
    entity.render(g);  // Objetos são desenhados em coordenadas de mundo
}

g2d.setTransform(originalTransform);  // Restaurar para UI
```

---

## 6. Gestão de Múltiplas Câmeras

O sistema suporta múltiplas câmeras para cenários avançados:

```java
// Câmera principal
Camera mainCam = game.getMainCamera();

// Adicionar câmera secundária (ex: minimapa)
Camera minimap = new Camera("Minimap", game, 0, 0);
minimap.setZoom(0.1);  // Zoom muito afastado
game.addCamera(minimap);

// Trocar câmera ativa
game.setMainCamera(otherCamera);
```

---

## 7. Controles do Editor

### Novos Controles na Toolbar:
- **Zoom In (+):** Aproxima a câmera (Ctrl+=)
- **Zoom Out (-):** Afasta a câmera (Ctrl+-)
- **Reset (⊙):** Volta câmera para (0,0) com zoom 100% (Home)
- **Indicador de Zoom:** Mostra porcentagem atual
- **Indicador de Posição:** Mostra posição da câmera

### Controles do Mouse:
- **Scroll do Mouse:** Zoom in/out
- **Botão do Meio (arrastar):** Pan da câmera
- **F:** Focar na seleção (centra câmera no objeto selecionado)

### Menu View:
- Zoom In (Ctrl+=)
- Zoom Out (Ctrl+-)
- Zoom to 100% (Ctrl+0)
- Reset Camera (Home)
- Focus on Selected (F)

---

## 8. Inspector de Câmera

Quando uma entidade Camera é selecionada, o Inspector mostra:
- **Zoom:** Campo editável para valor de zoom
- **Active Camera:** Checkbox indicando se é a câmera ativa
- **Set as Main Camera:** Botão para definir como câmera principal

---

## 9. Arquivos Modificados

### `Game.java`
- Adicionado sistema de câmera e viewport
- Renderização agora usa matriz de transformação da câmera
- Funções worldToScreen/screenToWorld disponíveis globalmente
- Detecção de objetos agora converte coordenadas corretamente
- Suporte a pan e zoom no editor

### `Scene.java`
- Gerenciamento de câmeras por cena
- Serialização/deserialização de câmeras
- Referência à câmera ativa da cena

### `EntityFactory.java`
- Adicionado suporte ao tipo "Camera"

### `Editor.java`
- Controles de câmera na toolbar
- Suporte a zoom com scroll do mouse
- Pan com botão do meio do mouse
- Menu View com atalhos de teclado
- Seção de câmera no Inspector
- Labels de zoom e posição atualizadas dinamicamente

---

## 10. Compatibilidade

### Projetos Existentes:
- ✅ Projetos antigos continuam funcionando
- ✅ Se não houver câmera na cena, uma é criada automaticamente
- ✅ Objetos existentes não precisam de modificação

### Para Desenvolvedores:
```java
// Acessar câmera principal
Camera cam = game.getMainCamera();

// Converter coordenadas do mouse para mundo
Point2D.Double worldPos = game.screenToWorld(mouseX, mouseY);

// Verificar se objeto está visível na câmera
boolean visible = cam.isVisible(obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight());
```

---

## 11. Próximos Passos Sugeridos

1. **Grid Visual:** Implementar grade no editor que respeita zoom/pan
2. **Culling de Objetos:** Não renderizar objetos fora da view
3. **Câmera Follow:** Componente para câmera seguir um objeto
4. **Shake da Câmera:** Efeito de tremor para feedback
5. **Transições de Câmera:** Interpolação suave entre posições

---

## Resumo

Esta atualização transforma o Ignis Engine de um sistema de renderização simples para um motor profissional com:

| Antes | Depois |
|-------|--------|
| Origem no canto superior esquerdo | Origem centralizada na tela |
| Sem suporte a zoom | Zoom óptico completo |
| Sem suporte a pan | Pan livre no editor |
| Coordenadas diretas | Sistema de matriz de visualização |
| Sem conversão de espaço | WorldToScreen / ScreenToWorld |
| Uma view fixa | Suporte a múltiplas câmeras |

O sistema está pronto para jogos 2D profissionais com câmera dinâmica, suporte a zoom, e renderização otimizada.
