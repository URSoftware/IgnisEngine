# Guia do Sistema de Interface do Usuário (UI System Guide)

> Documentação oficial do framework de UI in-game da IgnisEngine. Este sistema desenha os componentes diretamente no canvas do jogo, de maneira independente da UI do editor (Swing/JavaFX).

---

## 1. Arquitetura do Sistema de UI

O sistema de UI in-game é estruturado de forma hierárquica, sendo composto pelos seguintes blocos principais:

- **`UIComponent` (Classe Base):** Classe abstrata que serve de base para todos os elementos visuais. Define atributos comuns como dimensões, posicionamento, âncoras, pivô, estilização (cor de fundo, bordas, padding, fontes), gerenciamento de estado (interativo, hover, pressed, focused) e callbacks.
- **`UICanvas` (Raiz):** Container principal que gerencia o fluxo de atualização (`tick()`), desenho (`render()`) e distribuição de eventos de entrada (teclado, cliques e movimento de mouse) para todos os componentes filhos. O jogo possui uma instância ativa de `UICanvas`.

### Modos de Renderização (`RenderMode`)
O canvas suporta três modos distintos de renderização:
1. **`SCREEN_SPACE_OVERLAY`:** A UI é desenhada no topo de tudo nas coordenadas de tela. Não se move e não é influenciada pela escala ou rotação da câmera.
2. **`SCREEN_SPACE_CAMERA`:** A UI é desenhada no topo, mas seu posicionamento é adaptado à câmera principal.
3. **`WORLD_SPACE`:** A UI é desenhada diretamente no mundo físico do jogo (ideal para exibir barras de vida sobre a cabeça de inimigos, placas informativas no cenário, etc.).

### Modos de Escalonamento (`ScaleMode`)
Para tratar diferentes proporções e resoluções de tela:
- **`CONSTANT_PIXEL_SIZE`:** Componentes mantêm o mesmo tamanho em pixels independentemente da resolução.
- **`SCALE_WITH_SCREEN_SIZE`:** Redimensiona a interface proporcionalmente com base em uma resolução de referência (padrão: 1920x1080).
- **`SCALE_WITH_SCREEN_WIDTH` / `SCALE_WITH_SCREEN_HEIGHT`:** Redimensiona com base apenas em um dos eixos.

---

## 2. Sistema de Âncoras e Pivôs

A engine utiliza um sistema profissional de posicionamento flexível inspirado nas ferramentas modernas do mercado:

### Âncora (`anchorX`, `anchorY`)
Define o ponto de origem no componente pai (varia de `0.0` a `1.0`).
- `(0.0, 0.0)` representa o canto superior esquerdo do componente pai.
- `(0.5, 0.5)` representa o centro exato.
- `(1.0, 1.0)` representa o canto inferior direito.

### Pivô (`pivotX`, `pivotY`)
Define o ponto de origem do próprio componente sendo posicionado (varia de `0.0` a `1.0`).
- `(0.0, 0.0)` significa que a posição `(x, y)` do objeto refere-se ao seu canto superior esquerdo.
- `(0.5, 0.5)` significa que a posição indica o centro geométrico do componente (ideal para centralizar botões ou textos na tela).

---

## 3. Catálogo de Componentes e Propriedades

### `UIButton`
Um botão interativo que dispara uma ação ao ser clicado.
- **Propriedades:** `text`, `hoveredColor`, `pressedColor`, `onClick` (Runnable).
- **Estados Visuais:** Transição automática de cores baseado nos estados Mouse Over (hover) e Clique (press).

### `UILabel`
Componente para exibição de texto estático ou dinâmico.
- **Propriedades:** `text`, `fontFamily`, `fontSize`, `fontStyle`, `alignment` (LEFT, CENTER, RIGHT).

### `UIPanel`
Container retangular genérico para agrupar outros componentes ou servir de plano de fundo de janelas.
- **Propriedades:** `backgroundColor`, `borderColor`, `borderWidth`, `borderRadius` (cantos arredondados).

### `UITextField`
Campo de digitação para entrada de dados do usuário por meio do teclado.
- **Propriedades:** `text` (conteúdo), `placeholder`, `maxLength`, `focused` (indica se está capturando o input do teclado), `onValueChange` (Consumer).

### `UISlider`
Barra de rolagem para ajuste de valores contínuos (ex: ajuste de volume, sensibilidade).
- **Propriedades:** `value` (0.0 a 1.0), `sliderWidth`, `knobSize`, `onValueChange`.

### `UIProgressBar`
Barra visual para exibir progresso ou proporção (ideal para HUD de vida ou carregamento).
- **Propriedades:** `progress` (0.0 a 1.0), `fillColor`, `emptyColor`, `showPercentage` (exibe texto centralizado com porcentagem).

### `UICheckbox`
Caixa de seleção binária (marcado/desmarcado).
- **Propriedades:** `checked` (boolean), `checkmarkColor`, `label`.

### `UIToggle`
Um botão de alternância de estado liga/desliga estilo interruptor (switch).
- **Propriedades:** `active` (boolean), `trackColor`, `thumbColor`.

### `UIImage`
Exibe uma textura gráfica/imagem na interface.
- **Propriedades:** `spritePath` (caminho da imagem no projeto), `keepAspectRatio` (preserva proporções).

### `VectorIcon`
Desenha ícones vetoriais em alta resolução por código sem necessidade de carregar imagens de disco.
- **Propriedades:** `iconType` (representa ícones comuns como PLAY, PAUSE, GEAR, HOME, etc.), `color`.

---

## 4. Estilização Visual

Todo componente expõe métodos de configuração estética:

```java
component.setBackgroundColor(new Color(40, 40, 40, 220)); // Fundo cinza escuro translúcido
component.setBorderColor(new Color(255, 165, 0));         // Borda laranja
component.setBorderWidth(2);                               // Espessura da borda em pixels
component.setBorderRadius(8);                              // Cantos arredondados
component.setFontFamily("Arial");                          // Família tipográfica
component.setFontSize(16);                                 // Tamanho do texto
component.setTextColor(Color.WHITE);                       // Cor da fonte
```

---

## 5. API de UI no IgnisScript

Dentro de seus scripts de comportamento, a classe `IgnisScript` fornece métodos auxiliares convenientes para gerenciar a interface:

| Assinatura do Método | Descrição |
|---|---|
| `createCanvas(name)` | Cria um novo canvas e define-o como ativo na cena. |
| `createButton(text, x, y)` | Cria um botão nas coordenadas especificadas. |
| `createButton(text, x, y, width, height)` | Cria um botão com dimensões definidas. |
| `createButton(text, x, y, Runnable onClick)` | Cria um botão e vincula uma ação ao clique. |
| `createLabel(text, x, y)` | Adiciona um elemento de texto na tela. |
| `createLabel(text, x, y, width, height)` | Adiciona um texto com limites dimensionais. |
| `createPanel(x, y, width, height)` | Instancia um painel retangular no Canvas ativo. |
| `setUICanvas(UICanvas canvas)` | Define o Canvas ativo do jogo. |
| `getUICanvas()` | Retorna a instância do Canvas atualmente ativa. |
| `addToUI(UIComponent component)` | Adiciona qualquer componente personalizado ao Canvas ativo. |
| `removeFromUI(UIComponent component)` | Remove o componente do Canvas ativo. |

---

## 6. Exemplos Práticos de Implementação

### A. HUD de Vida e Pontuação de Jogo
```java
public class GameHUD extends IgnisScript {
    private UIProgressBar healthBar;
    private UILabel scoreLabel;
    private int score = 0;

    @Override
    public void start() {
        // Inicializa o canvas
        createCanvas("GameHUD");

        // Painel de Fundo da HUD
        createPanel(10, 10, 300, 80);

        // Label de Vida
        createLabel("HP:", 20, 20, 50, 20);

        // Barra de progresso da Vida
        healthBar = new UIProgressBar("HealthBar", 70, 20, 220, 20);
        healthBar.setProgress(1.0); // 100% de vida inicial
        healthBar.setFillColor(Color.GREEN);
        addToUI(healthBar);

        // Label para a Pontuação
        scoreLabel = createLabel("SCORE: 0000", 20, 50, 270, 20);
        scoreLabel.setFontSize(16);
    }

    public void addScore(int points) {
        score += points;
        scoreLabel.setText("SCORE: " + String.format("%04d", score));
    }

    public void updateHealth(double pct) {
        healthBar.setProgress(pct);
        if (pct < 0.3) {
            healthBar.setFillColor(Color.RED);
        } else if (pct < 0.6) {
            healthBar.setFillColor(Color.YELLOW);
        } else {
            healthBar.setFillColor(Color.GREEN);
        }
    }
}
```

### B. Menu de Pause Interativo
```java
public class PauseMenu extends IgnisScript {
    private UIPanel menuPanel;

    @Override
    public void start() {
        // O menu de pause inicia inativo
    }

    @Override
    public void update() {
        if (isKeyJustPressed("ESCAPE")) {
            togglePause();
        }
    }

    private void togglePause() {
        if (menuPanel == null) {
            // Criar Painel de pause centralizado
            menuPanel = new UIPanel("PausePanel", 0, 0, 400, 300);
            menuPanel.setAnchorX(0.5); menuPanel.setAnchorY(0.5); // Centraliza no pai
            menuPanel.setPivotX(0.5); menuPanel.setPivotY(0.5);   // Pivô no centro do painel
            menuPanel.setBackgroundColor(new Color(20, 20, 20, 240));
            menuPanel.setBorderColor(Color.GRAY);
            menuPanel.setBorderWidth(3);
            menuPanel.setBorderRadius(12);

            // Título do Menu
            UILabel title = new UILabel("JOGO PAUSADO", 0, -100, 300, 40);
            title.setAnchorX(0.5); title.setAnchorY(0.5);
            title.setPivotX(0.5); title.setPivotY(0.5);
            title.setFontSize(22);
            title.setAlignment(UILabel.Alignment.CENTER);
            menuPanel.addChild(title);

            // Botão Continuar
            UIButton btnResume = new UIButton("Continuar", 0, -20, 250, 40);
            btnResume.setAnchorX(0.5); btnResume.setAnchorY(0.5);
            btnResume.setPivotX(0.5); btnResume.setPivotY(0.5);
            btnResume.setOnClick(() -> resumeGame());
            menuPanel.addChild(btnResume);

            // Botão Sair
            UIButton btnQuit = new UIButton("Sair do Jogo", 0, 40, 250, 40);
            btnQuit.setAnchorX(0.5); btnQuit.setAnchorY(0.5);
            btnQuit.setPivotX(0.5); btnQuit.setPivotY(0.5);
            btnQuit.setOnClick(() -> System.exit(0));
            menuPanel.addChild(btnQuit);

            addToUI(menuPanel);
            game.pause();
        } else {
            resumeGame();
        }
    }

    private void resumeGame() {
        if (menuPanel != null) {
            removeFromUI(menuPanel);
            menuPanel = null;
            game.resume();
        }
    }
}
```
