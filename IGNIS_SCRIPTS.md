# IgnisScripts - Guia Completo e Detalhado

Este documento explica **tudo** sobre o sistema de scripts do motor Ignis Engine, de forma didática e completa.

---

## 📚 Índice

1. [O que são IgnisScripts?](#o-que-são-ignisscripts)
2. [Estrutura Básica](#estrutura-básica-de-um-script)
3. [Ciclo de Vida - Métodos Principais](#ciclo-de-vida---métodos-principais)
4. [Variáveis de Contexto](#variáveis-de-contexto)
5. [Métodos de Movimento](#métodos-de-movimento)
6. [Sistema de Input](#sistema-de-input-teclado-e-mouse)
7. [Sistema de Áudio](#sistema-de-áudio-ignissoundengine)
8. [Métodos de Busca e Interação](#métodos-de-busca-e-interação)
9. [Controle do Script](#controle-do-script)
10. [Variáveis no Inspector](#variáveis-no-inspector)
11. [Sistema de Coordenadas](#sistema-de-coordenadas)
12. [Exemplos Práticos](#exemplos-práticos-completos)
13. [Boas Práticas](#boas-práticas)

---

## O que são IgnisScripts?

**IgnisScripts** são classes Java que você cria para dar comportamento aos objetos do seu jogo. Pense neles como "cérebros" que você anexa aos objetos para fazê-los agir de determinada forma.

### Analogia
Imagine que você tem um boneco de brinquedo (o `GameObject`). Sozinho, ele não faz nada. Mas se você colocar um controle remoto nele (o `IgnisScript`), ele passa a se mover, reagir e interagir com o mundo.

### Características
- Todo script **deve** estender a classe `IgnisScript`
- Um objeto pode ter **múltiplos** scripts
- Scripts são **executados automaticamente** pelo motor do jogo
- Você pode acessar e modificar propriedades do objeto diretamente

---

## Estrutura Básica de um Script

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import com.ignis.core.GameObject;
import java.awt.event.KeyEvent;

public class MeuScript extends IgnisScript {

    // ═══════════════════════════════════════════════════════════
    // VARIÁVEIS DO SCRIPT
    // Declare aqui as variáveis que seu script precisa.
    // Elas aparecerão no Inspector para fácil ajuste!
    // ═══════════════════════════════════════════════════════════
    
    private double speed = 5.0;
    private int health = 100;
    private boolean isAlive = true;

    // ═══════════════════════════════════════════════════════════
    // MÉTODO START
    // Executado UMA ÚNICA VEZ quando o jogo começa.
    // Use para inicialização.
    // ═══════════════════════════════════════════════════════════
    
    @Override
    public void start() {
        log("O jogo começou! Meu objeto está em: " + transform.x + ", " + transform.y);
    }

    // ═══════════════════════════════════════════════════════════
    // MÉTODO TICK
    // Executado A CADA FRAME (60+ vezes por segundo).
    // Use para lógica contínua como movimento.
    // ═══════════════════════════════════════════════════════════
    
    @Override
    public void tick() {
        // Sua lógica de jogo aqui
        double dx = Input.getHorizontalAxis() * speed;
        double dy = Input.getVerticalAxis() * speed;
        move(dx, dy);
    }

    // ═══════════════════════════════════════════════════════════
    // MÉTODO ON COLLISION
    // Executado quando COLIDE com outro objeto.
    // O parâmetro 'other' é o objeto com quem colidiu.
    // ═══════════════════════════════════════════════════════════
    
    @Override
    public void onCollision(GameObject other) {
        log("Colidiu com: " + other.getName());
    }
}
```

---

## Ciclo de Vida - Métodos Principais

Estes são os métodos que você pode sobrescrever (@Override) para definir o comportamento do seu script.

### `start()`

```java
@Override
public void start() {
    // Seu código aqui
}
```

| Aspecto | Descrição |
|---------|-----------|
| **Quando é chamado** | Uma única vez, no primeiro frame após clicar em Play |
| **Propósito** | Inicializar variáveis, encontrar referências a outros objetos |
| **Retorno** | `void` (não retorna nada) |
| **Parâmetros** | Nenhum |

**✅ Use para:**
- Inicializar variáveis que dependem de outros objetos
- Buscar referências a outros objetos na cena
- Configurar estado inicial
- Mostrar mensagens de debug iniciais

**❌ NÃO use para:**
- Lógica que precisa rodar todo frame
- Criar novos objetos (faça isso no editor)

**Exemplo prático:**
```java
private GameObject player;
private double initialX;
private double initialY;

@Override
public void start() {
    // Guardar posição inicial para poder resetar depois
    initialX = transform.x;
    initialY = transform.y;
    
    // Encontrar o player na cena
    player = findObject("Player");
    
    if (player != null) {
        log("Player encontrado! Vou persegui-lo.");
    } else {
        log("AVISO: Player não encontrado na cena!");
    }
}
```

---

### `tick()`

```java
@Override
public void tick() {
    // Seu código aqui
}
```

| Aspecto | Descrição |
|---------|-----------|
| **Quando é chamado** | A cada frame, enquanto o jogo está rodando (~60 vezes/segundo) |
| **Propósito** | Lógica contínua: movimento, verificações, atualizações |
| **Retorno** | `void` (não retorna nada) |
| **Parâmetros** | Nenhum |

**✅ Use para:**
- Movimento do personagem
- Verificar input do jogador
- Atualizar estados (vida, pontuação, etc.)
- Verificar condições (morreu? ganhou?)
- IA de inimigos

**❌ NÃO use para:**
- Código pesado (cálculos complexos, I/O de arquivo)
- Inicialização (use `start()` para isso)

**Exemplo prático:**
```java
private double speed = 5.0;
private int health = 100;

@Override
public void tick() {
    // MOVIMENTO: ler input e mover
    double dx = Input.getHorizontalAxis() * speed;
    double dy = Input.getVerticalAxis() * speed;
    move(dx, dy);
    
    // AÇÃO: atirar quando pressiona espaço
    if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
        log("Atirou!");
        // lógica de tiro aqui
    }
    
    // VERIFICAÇÃO: checar se morreu
    if (health <= 0) {
        log("Game Over!");
        destroy();
    }
}
```

---

### `onCollision(GameObject other)`

```java
@Override
public void onCollision(GameObject other) {
    // Seu código aqui
}
```

| Aspecto | Descrição |
|---------|-----------|
| **Quando é chamado** | Quando este objeto colide com outro objeto |
| **Propósito** | Reagir a colisões |
| **Retorno** | `void` (não retorna nada) |
| **Parâmetros** | `other` - O GameObject com quem colidiu |

**O parâmetro `other`:**
- É uma referência ao objeto que colidiu com você
- Você pode acessar propriedades dele: `other.getName()`, `other.getType()`
- Você pode destruí-lo: `destroy(other)`

**Exemplo prático:**
```java
private int health = 100;
private int score = 0;

@Override
public void onCollision(GameObject other) {
    // Verificar QUEM colidiu usando o tipo ou nome
    
    if (other.getType().equals("Enemy")) {
        // Colidiu com inimigo - toma dano
        health -= 10;
        log("Ai! Tomei dano. Vida: " + health);
    }
    
    if (other.getType().equals("Coin")) {
        // Colidiu com moeda - coleta
        score += 10;
        destroy(other);  // Remove a moeda
        log("Moeda coletada! Score: " + score);
    }
    
    if (other.getName().equals("PortaFinal")) {
        // Colidiu com a porta - ganhou o jogo
        log("Você venceu!");
    }
}
```

---

## Variáveis de Contexto

Estas variáveis estão disponíveis automaticamente em qualquer script.

### `transform`

O `transform` dá acesso à posição, rotação e tamanho do objeto.

```java
public class Transform {
    public double x;       // Posição horizontal
    public double y;       // Posição vertical
    public double rotation; // Rotação em graus (0-360)
    public int width;      // Largura em pixels
    public int height;     // Altura em pixels
}
```

**Como usar:**
```java
@Override
public void tick() {
    // LER valores
    double minhaX = transform.x;
    double minhaY = transform.y;
    double minhaRotacao = transform.rotation;
    
    // MODIFICAR valores diretamente
    transform.x += 5;           // Move 5 pixels para a direita
    transform.y -= 3;           // Move 3 pixels para cima
    transform.rotation += 1;    // Rotaciona 1 grau
    transform.width = 100;      // Define largura
    transform.height = 50;      // Define altura
}
```

**Importante:** As alterações em `transform` são automaticamente aplicadas ao objeto no final de cada `tick()`.

---

### `gameObject`

Referência ao `GameObject` ao qual este script está anexado.

```java
@Override
public void start() {
    // Acessar propriedades do objeto
    String nome = gameObject.getName();
    String tipo = gameObject.getType();
    String id = gameObject.getId();
    
    log("Eu sou: " + nome + " do tipo " + tipo);
}
```

**Métodos disponíveis no gameObject:**

| Método | Retorno | Descrição |
|--------|---------|-----------|
| `getName()` | `String` | Nome do objeto |
| `setName(String)` | `void` | Define o nome |
| `getType()` | `String` | Tipo do objeto (nome da classe) |
| `getId()` | `String` | ID único do objeto |
| `getX()`, `getY()` | `double` | Posição |
| `getWidth()`, `getHeight()` | `int` | Tamanho |
| `getRotation()` | `double` | Rotação |

---

### `game`

Referência ao `Game`, permitindo acesso global à cena.

```java
@Override
public void tick() {
    // Obter todos os objetos da cena
    java.util.List<GameObject> todosObjetos = game.getEntities();
    
    log("Existem " + todosObjetos.size() + " objetos na cena");
}
```

---

## Métodos de Movimento

### `move(double dx, double dy)`

Move o objeto pela quantidade especificada.

```java
protected void move(double dx, double dy)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `dx` | `double` | Deslocamento horizontal (positivo = direita) |
| `dy` | `double` | Deslocamento vertical (positivo = baixo) |

**Retorno:** Nenhum (`void`)

**Exemplos:**
```java
move(5, 0);    // Move 5 pixels para a DIREITA
move(-5, 0);   // Move 5 pixels para a ESQUERDA
move(0, 5);    // Move 5 pixels para BAIXO
move(0, -5);   // Move 5 pixels para CIMA
move(3, 3);    // Move na DIAGONAL (direita-baixo)

// Uso típico com input:
double velocidade = 5.0;
double dx = Input.getHorizontalAxis() * velocidade;  // -5, 0, ou 5
double dy = Input.getVerticalAxis() * velocidade;    // -5, 0, ou 5
move(dx, dy);
```

---

### `moveTowards(double targetX, double targetY, double speed)`

Move o objeto em direção a um ponto, com velocidade constante.

```java
protected void moveTowards(double targetX, double targetY, double speed)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `targetX` | `double` | Coordenada X do destino |
| `targetY` | `double` | Coordenada Y do destino |
| `speed` | `double` | Velocidade do movimento (pixels por frame) |

**Retorno:** Nenhum (`void`)

**Como funciona:**
1. Calcula a direção até o alvo
2. Move na direção do alvo com a velocidade especificada
3. Se a distância for menor que `speed`, vai direto ao ponto

**Exemplos:**
```java
// Seguir o mouse
@Override
public void tick() {
    moveTowards(Input.getMouseX(), Input.getMouseY(), 3.0);
}

// IA de inimigo perseguindo o player
private GameObject player;

@Override
public void start() {
    player = findObject("Player");
}

@Override
public void tick() {
    if (player != null) {
        moveTowards(player.getX(), player.getY(), 2.0);
    }
}
```

---

### `rotate(double degrees)`

Rotaciona o objeto pela quantidade especificada.

```java
protected void rotate(double degrees)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `degrees` | `double` | Quantidade de graus para rotacionar |

**Retorno:** Nenhum (`void`)

**Exemplos:**
```java
rotate(5);     // Rotaciona 5 graus no sentido horário
rotate(-5);    // Rotaciona 5 graus no sentido anti-horário

// Rotação contínua (como uma hélice)
@Override
public void tick() {
    rotate(3);  // Rotaciona 3 graus por frame
}
```

---

### `lookAt(double targetX, double targetY)`

Faz o objeto "olhar" para um ponto (ajusta a rotação).

```java
protected void lookAt(double targetX, double targetY)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `targetX` | `double` | Coordenada X do ponto |
| `targetY` | `double` | Coordenada Y do ponto |

**Retorno:** Nenhum (`void`)

**Como funciona:**
- Calcula o ângulo entre o objeto e o ponto alvo
- Define `transform.rotation` para esse ângulo

**Exemplos:**
```java
// Sempre olhar para o mouse
@Override
public void tick() {
    lookAt(Input.getMouseX(), Input.getMouseY());
}

// Torre que mira no inimigo mais próximo
@Override
public void tick() {
    GameObject inimigo = findObject("Enemy");
    if (inimigo != null) {
        lookAt(inimigo.getX(), inimigo.getY());
    }
}
```

---

## Sistema de Input (Teclado e Mouse)

A classe `Input` fornece métodos estáticos para ler o estado do teclado e mouse.

### Métodos de Teclado

#### `Input.isKeyPressed(int keyCode)`

Verifica se uma tecla **está sendo pressionada** (mantida).

```java
public static boolean isKeyPressed(int keyCode)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `keyCode` | `int` | Código da tecla (use `KeyEvent.VK_*`) |

**Retorno:** `true` se a tecla está pressionada, `false` caso contrário

**Uso típico:** Movimento contínuo (enquanto segura a tecla)

```java
import java.awt.event.KeyEvent;

@Override
public void tick() {
    // Movimento enquanto SEGURA a tecla
    if (Input.isKeyPressed(KeyEvent.VK_W)) {
        move(0, -speed);  // Cima
    }
    if (Input.isKeyPressed(KeyEvent.VK_S)) {
        move(0, speed);   // Baixo
    }
    if (Input.isKeyPressed(KeyEvent.VK_A)) {
        move(-speed, 0);  // Esquerda
    }
    if (Input.isKeyPressed(KeyEvent.VK_D)) {
        move(speed, 0);   // Direita
    }
}
```

---

#### `Input.isKeyJustPressed(int keyCode)`

Verifica se uma tecla **acabou de ser pressionada** (apenas neste frame).

```java
public static boolean isKeyJustPressed(int keyCode)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `keyCode` | `int` | Código da tecla (use `KeyEvent.VK_*`) |

**Retorno:** `true` apenas no frame em que a tecla foi pressionada

**Uso típico:** Ações únicas (pular, atirar, interagir)

```java
@Override
public void tick() {
    // Pular - só uma vez por pressionamento
    if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
        log("Pulou!");
        // lógica de pulo
    }
    
    // Pausar o jogo
    if (Input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
        log("Pausando...");
    }
}
```

**Diferença entre `isKeyPressed` e `isKeyJustPressed`:**

| Situação | `isKeyPressed` | `isKeyJustPressed` |
|----------|----------------|---------------------|
| Usuário pressiona e segura espaço | `true` em todos os frames | `true` apenas no primeiro frame |
| Frame 1 (pressionou) | `true` | `true` |
| Frame 2 (segurando) | `true` | `false` |
| Frame 3 (segurando) | `true` | `false` |
| Frame 4 (soltou) | `false` | `false` |

---

#### `Input.isKeyJustReleased(int keyCode)`

Verifica se uma tecla **acabou de ser solta** (apenas neste frame).

```java
public static boolean isKeyJustReleased(int keyCode)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `keyCode` | `int` | Código da tecla (use `KeyEvent.VK_*`) |

**Retorno:** `true` apenas no frame em que a tecla foi solta

```java
@Override
public void tick() {
    // Detectar quando soltou o botão de carregar
    if (Input.isKeyJustReleased(KeyEvent.VK_SPACE)) {
        log("Soltou o espaço - disparar!");
    }
}
```

---

#### Atalhos de Movimento

Métodos de conveniência para as teclas de movimento mais comuns:

| Método | Teclas | Retorno |
|--------|--------|---------|
| `Input.isUpPressed()` | W ou ↑ | `boolean` |
| `Input.isDownPressed()` | S ou ↓ | `boolean` |
| `Input.isLeftPressed()` | A ou ← | `boolean` |
| `Input.isRightPressed()` | D ou → | `boolean` |

```java
@Override
public void tick() {
    if (Input.isUpPressed())    move(0, -speed);
    if (Input.isDownPressed())  move(0, speed);
    if (Input.isLeftPressed())  move(-speed, 0);
    if (Input.isRightPressed()) move(speed, 0);
}
```

---

#### `Input.getHorizontalAxis()` e `Input.getVerticalAxis()`

Retornam valores de eixo para movimento simplificado.

```java
public static int getHorizontalAxis()  // -1, 0, ou 1
public static int getVerticalAxis()    // -1, 0, ou 1
```

**Retornos:**

| Método | Retorno | Significado |
|--------|---------|-------------|
| `getHorizontalAxis()` | `-1` | A ou ← pressionado (esquerda) |
| `getHorizontalAxis()` | `0` | Nenhuma tecla |
| `getHorizontalAxis()` | `1` | D ou → pressionado (direita) |
| `getVerticalAxis()` | `-1` | W ou ↑ pressionado (cima) |
| `getVerticalAxis()` | `0` | Nenhuma tecla |
| `getVerticalAxis()` | `1` | S ou ↓ pressionado (baixo) |

**Uso recomendado (mais limpo):**
```java
@Override
public void tick() {
    double dx = Input.getHorizontalAxis() * speed;
    double dy = Input.getVerticalAxis() * speed;
    move(dx, dy);
}
```

---

### Métodos de Mouse

#### `Input.getMouseX()` e `Input.getMouseY()`

Retornam a posição atual do mouse na tela.

```java
public static int getMouseX()
public static int getMouseY()
```

**Retorno:** Posição em pixels

```java
@Override
public void tick() {
    int mouseX = Input.getMouseX();
    int mouseY = Input.getMouseY();
    
    // Seguir o mouse
    moveTowards(mouseX, mouseY, 5);
}
```

---

#### Botões do Mouse

| Método | Descrição | Retorno |
|--------|-----------|---------|
| `Input.isMouseLeftPressed()` | Botão esquerdo está pressionado | `boolean` |
| `Input.isMouseRightPressed()` | Botão direito está pressionado | `boolean` |
| `Input.isMouseMiddlePressed()` | Botão do meio está pressionado | `boolean` |
| `Input.isMouseLeftJustPressed()` | Botão esquerdo acabou de ser pressionado | `boolean` |
| `Input.isMouseRightJustPressed()` | Botão direito acabou de ser pressionado | `boolean` |

```java
@Override
public void tick() {
    // Atirar com clique esquerdo
    if (Input.isMouseLeftJustPressed()) {
        log("Atirou em: " + Input.getMouseX() + ", " + Input.getMouseY());
    }
    
    // Menu de contexto com clique direito
    if (Input.isMouseRightJustPressed()) {
        log("Abrir menu");
    }
}
```

---

### Códigos de Tecla Comuns

Use `KeyEvent.VK_*` para especificar teclas:

```java
import java.awt.event.KeyEvent;

// Letras
KeyEvent.VK_A, VK_B, VK_C, ... VK_Z

// Números
KeyEvent.VK_0, VK_1, VK_2, ... VK_9

// Setas
KeyEvent.VK_UP, VK_DOWN, VK_LEFT, VK_RIGHT

// Especiais
KeyEvent.VK_SPACE      // Espaço
KeyEvent.VK_ENTER      // Enter
KeyEvent.VK_ESCAPE     // Esc
KeyEvent.VK_SHIFT      // Shift
KeyEvent.VK_CONTROL    // Ctrl
KeyEvent.VK_ALT        // Alt
KeyEvent.VK_TAB        // Tab
KeyEvent.VK_BACK_SPACE // Backspace

// Função
KeyEvent.VK_F1, VK_F2, ... VK_F12
```

---

## Sistema de Áudio (IgnisSoundEngine)

O **IgnisSoundEngine** é o motor de áudio do Ignis Engine. Ele permite reproduzir efeitos sonoros e músicas de fundo nos seus jogos de forma simples e integrada aos scripts.

### Formatos Suportados

| Formato | Suporte | Observação |
|---------|---------|------------|
| WAV | ✅ Nativo | Recomendado para efeitos sonoros |
| AIFF | ✅ Nativo | Formato Apple |
| AU | ✅ Nativo | Formato Sun/Unix |

### Métodos de Efeitos Sonoros

#### `playSound(String filePath)`

Reproduz um efeito sonoro uma vez.

```java
protected void playSound(String filePath)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `filePath` | `String` | Caminho do arquivo de áudio |

**Retorno:** Nenhum (`void`)

```java
@Override
public void tick() {
    if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
        playSound("project/assets/sounds/jump.wav");
    }
}
```

---

#### `playSound(String filePath, float volume)`

Reproduz um efeito sonoro com volume personalizado.

```java
protected void playSound(String filePath, float volume)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `filePath` | `String` | Caminho do arquivo de áudio |
| `volume` | `float` | Volume de 0.0 (mudo) a 1.0 (máximo) |

**Retorno:** Nenhum (`void`)

```java
// Som mais baixo (50% do volume)
playSound("project/assets/sounds/footstep.wav", 0.5f);

// Som no volume máximo
playSound("project/assets/sounds/explosion.wav", 1.0f);
```

---

#### `playSoundWithCallback(String filePath, Runnable onComplete)`

Reproduz um efeito sonoro e executa uma ação quando terminar.

```java
protected void playSoundWithCallback(String filePath, Runnable onComplete)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `filePath` | `String` | Caminho do arquivo de áudio |
| `onComplete` | `Runnable` | Ação a executar quando o som terminar |

**Retorno:** Nenhum (`void`)

```java
// Tocar som e executar ação ao finalizar
playSoundWithCallback("project/assets/sounds/powerup.wav", () -> {
    log("Power-up ativado!");
    speed *= 2;
});
```

---

#### `stopAllSounds()`

Para todos os efeitos sonoros que estão tocando.

```java
protected void stopAllSounds()
```

**Retorno:** Nenhum (`void`)

```java
@Override
public void tick() {
    // Parar todos os sons com ESC
    if (Input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
        stopAllSounds();
        log("Todos os sons parados");
    }
}
```

---

### Métodos de Música de Fundo

#### `playMusic(String filePath)`

Reproduz música de fundo em loop contínuo.

```java
protected void playMusic(String filePath)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `filePath` | `String` | Caminho do arquivo de música |

**Retorno:** Nenhum (`void`)

```java
@Override
public void start() {
    // Iniciar música tema do jogo (em loop)
    playMusic("project/assets/music/theme.wav");
}
```

---

#### `playMusic(String filePath, boolean loop)`

Reproduz música de fundo com controle de loop.

```java
protected void playMusic(String filePath, boolean loop)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `filePath` | `String` | Caminho do arquivo de música |
| `loop` | `boolean` | `true` para repetir, `false` para tocar uma vez |

**Retorno:** Nenhum (`void`)

```java
// Música que repete
playMusic("project/assets/music/battle.wav", true);

// Música que toca apenas uma vez
playMusic("project/assets/music/victory.wav", false);
```

---

#### `pauseMusic()`

Pausa a música de fundo atual.

```java
protected void pauseMusic()
```

**Retorno:** Nenhum (`void`)

```java
@Override
public void tick() {
    if (Input.isKeyJustPressed(KeyEvent.VK_P)) {
        pauseMusic();
        log("Música pausada");
    }
}
```

---

#### `resumeMusic()`

Retoma a música de fundo que estava pausada.

```java
protected void resumeMusic()
```

**Retorno:** Nenhum (`void`)

```java
@Override
public void tick() {
    if (Input.isKeyJustPressed(KeyEvent.VK_P)) {
        if (isMusicPlaying()) {
            pauseMusic();
        } else {
            resumeMusic();
        }
    }
}
```

---

#### `stopMusic()`

Para completamente a música de fundo.

```java
protected void stopMusic()
```

**Retorno:** Nenhum (`void`)

```java
@Override
public void onCollision(GameObject other) {
    if (other.getName().equals("BossDead")) {
        stopMusic();
        playMusic("project/assets/music/victory.wav", false);
    }
}
```

---

#### `isMusicPlaying()`

Verifica se há música tocando.

```java
protected boolean isMusicPlaying()
```

**Retorno:** `true` se música está tocando, `false` caso contrário

```java
@Override
public void tick() {
    if (!isMusicPlaying()) {
        log("Nenhuma música tocando");
        playMusic("project/assets/music/ambient.wav");
    }
}
```

---

### Controle de Volume

#### `setMasterVolume(float volume)`

Define o volume master (afeta tudo: música e efeitos).

```java
protected void setMasterVolume(float volume)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `volume` | `float` | Volume de 0.0 (mudo) a 1.0 (máximo) |

**Retorno:** Nenhum (`void`)

```java
@Override
public void start() {
    setMasterVolume(0.8f);  // 80% do volume total
}
```

---

#### `setMusicVolume(float volume)`

Define o volume apenas da música de fundo.

```java
protected void setMusicVolume(float volume)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `volume` | `float` | Volume de 0.0 (mudo) a 1.0 (máximo) |

**Retorno:** Nenhum (`void`)

```java
// Música mais baixa para não atrapalhar diálogos
setMusicVolume(0.3f);
```

---

#### `setSfxVolume(float volume)`

Define o volume dos efeitos sonoros.

```java
protected void setSfxVolume(float volume)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `volume` | `float` | Volume de 0.0 (mudo) a 1.0 (máximo) |

**Retorno:** Nenhum (`void`)

```java
// Efeitos sonoros no máximo
setSfxVolume(1.0f);
```

---

### Tabela de Volumes

| Método | O que afeta | Padrão |
|--------|-------------|--------|
| `setMasterVolume()` | Tudo (música + efeitos) | 1.0 |
| `setMusicVolume()` | Apenas música de fundo | 0.8 |
| `setSfxVolume()` | Apenas efeitos sonoros | 1.0 |

**Como os volumes se combinam:**
```
Volume Final = Volume do Som × Volume da Categoria × Volume Master

Exemplo:
- Master: 0.8
- Music: 0.5
- Volume final da música: 0.8 × 0.5 = 0.4 (40%)
```

---

### Exemplos Práticos de Áudio

#### 1. Player com Sons de Movimento e Ação

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;

public class PlayerWithSound extends IgnisScript {
    
    private double speed = 5.0;
    private boolean wasMoving = false;
    
    @Override
    public void start() {
        // Iniciar música de fundo
        playMusic("project/assets/music/adventure.wav");
        setMusicVolume(0.6f);
    }
    
    @Override
    public void tick() {
        // Movimento
        double dx = Input.getHorizontalAxis() * speed;
        double dy = Input.getVerticalAxis() * speed;
        move(dx, dy);
        
        // Som de passos (apenas quando começa a andar)
        boolean isMoving = (dx != 0 || dy != 0);
        if (isMoving && !wasMoving) {
            playSound("project/assets/sounds/footstep.wav", 0.5f);
        }
        wasMoving = isMoving;
        
        // Som de pulo
        if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
            playSound("project/assets/sounds/jump.wav");
        }
        
        // Som de ataque
        if (Input.isMouseLeftJustPressed()) {
            playSound("project/assets/sounds/attack.wav");
        }
    }
}
```

#### 2. Sistema de Menu com Música

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;

public class MenuController extends IgnisScript {
    
    private boolean isPaused = false;
    
    @Override
    public void start() {
        playMusic("project/assets/music/menu_theme.wav");
    }
    
    @Override
    public void tick() {
        if (Input.isKeyJustPressed(KeyEvent.VK_ESCAPE)) {
            togglePause();
        }
        
        // Ajustar volume com + e -
        if (Input.isKeyJustPressed(KeyEvent.VK_EQUALS)) {
            setMasterVolume(1.0f);
            log("Volume: Máximo");
        }
        if (Input.isKeyJustPressed(KeyEvent.VK_MINUS)) {
            setMasterVolume(0.5f);
            log("Volume: Médio");
        }
    }
    
    private void togglePause() {
        isPaused = !isPaused;
        
        if (isPaused) {
            pauseMusic();
            playSound("project/assets/sounds/pause.wav");
            log("Jogo pausado");
        } else {
            resumeMusic();
            playSound("project/assets/sounds/unpause.wav");
            log("Jogo retomado");
        }
    }
}
```

#### 3. Inimigo com Sons de Alerta e Ataque

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;

public class EnemyWithSound extends IgnisScript {
    
    private double speed = 2.0;
    private double detectionRange = 150.0;
    private double attackRange = 30.0;
    private GameObject player;
    private boolean hasAlerted = false;
    
    @Override
    public void start() {
        player = findObject("Player");
    }
    
    @Override
    public void tick() {
        if (player == null) return;
        
        double distancia = distanceTo(player);
        
        if (distancia <= attackRange) {
            // Atacar
            playSound("project/assets/sounds/enemy_attack.wav");
        } else if (distancia <= detectionRange) {
            // Detectou o player - som de alerta (apenas uma vez)
            if (!hasAlerted) {
                playSound("project/assets/sounds/enemy_alert.wav");
                hasAlerted = true;
            }
            moveTowards(player.getX(), player.getY(), speed);
        } else {
            hasAlerted = false;  // Reset quando player sai do alcance
        }
    }
    
    @Override
    public void onCollision(GameObject other) {
        if (other.getType().equals("Bullet")) {
            playSound("project/assets/sounds/enemy_hit.wav");
            destroy(other);
        }
    }
}
```

#### 4. Coletor de Itens com Sons Diferentes

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;

public class ItemCollectorWithSound extends IgnisScript {
    
    private int coins = 0;
    private int gems = 0;
    
    @Override
    public void start() {
        log("Colete os itens!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        String tipo = other.getType();
        
        if (tipo.equals("Coin")) {
            coins++;
            playSound("project/assets/sounds/coin_collect.wav", 0.7f);
            destroy(other);
            
            // A cada 10 moedas, toca um som especial
            if (coins % 10 == 0) {
                playSound("project/assets/sounds/bonus.wav");
            }
        } else if (tipo.equals("Gem")) {
            gems++;
            playSound("project/assets/sounds/gem_collect.wav");
            destroy(other);
        } else if (tipo.equals("PowerUp")) {
            // Som com callback - ativa poder após o som
            playSoundWithCallback("project/assets/sounds/powerup.wav", () -> {
                log("Poder ativado!");
            });
            destroy(other);
        }
    }
}
```

#### 5. Troca de Música por Zona

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;

public class MusicZoneController extends IgnisScript {
    
    private String currentZone = "normal";
    
    @Override
    public void start() {
        playMusic("project/assets/music/overworld.wav");
    }
    
    @Override
    public void onCollision(GameObject other) {
        String zoneName = other.getName();
        
        // Evita trocar música se já está na mesma zona
        if (zoneName.equals(currentZone)) return;
        
        if (zoneName.equals("DungeonZone")) {
            stopMusic();
            playMusic("project/assets/music/dungeon.wav");
            currentZone = "dungeon";
            log("Entrando na dungeon...");
            
        } else if (zoneName.equals("BossZone")) {
            stopMusic();
            playMusic("project/assets/music/boss_battle.wav");
            currentZone = "boss";
            log("CHEFE APARECEU!");
            
        } else if (zoneName.equals("SafeZone")) {
            stopMusic();
            playMusic("project/assets/music/peaceful.wav");
            setMusicVolume(0.5f);  // Música mais calma
            currentZone = "safe";
            log("Zona segura");
        }
    }
}
```

---

### Resumo de Métodos de Áudio

| O que você quer fazer | Método |
|-----------------------|--------|
| Tocar efeito sonoro | `playSound("arquivo.wav")` |
| Tocar som com volume | `playSound("arquivo.wav", 0.5f)` |
| Tocar som com callback | `playSoundWithCallback("arquivo.wav", () -> { })` |
| Parar todos os efeitos | `stopAllSounds()` |
| Tocar música (loop) | `playMusic("arquivo.wav")` |
| Tocar música (uma vez) | `playMusic("arquivo.wav", false)` |
| Pausar música | `pauseMusic()` |
| Retomar música | `resumeMusic()` |
| Parar música | `stopMusic()` |
| Verificar se música toca | `isMusicPlaying()` |
| Volume geral | `setMasterVolume(0.8f)` |
| Volume da música | `setMusicVolume(0.5f)` |
| Volume dos efeitos | `setSfxVolume(1.0f)` |

---

### Localização dos Arquivos de Áudio

```
projects/
  └── SeuProjeto/
      └── project/
          └── assets/
              ├── music/
              │   ├── theme.wav
              │   ├── battle.wav
              │   └── victory.wav
              └── sounds/
                  ├── jump.wav
                  ├── coin.wav
                  └── explosion.wav
```

---

## Métodos de Busca e Interação

### `findObject(String name)`

Encontra um objeto na cena pelo nome.

```java
protected GameObject findObject(String name)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `name` | `String` | Nome do objeto a encontrar |

**Retorno:** O `GameObject` encontrado, ou `null` se não existir

```java
private GameObject player;
private GameObject porta;

@Override
public void start() {
    player = findObject("Player");
    porta = findObject("PortaSaida");
    
    if (player == null) {
        log("ERRO: Player não encontrado!");
    }
}

@Override
public void tick() {
    if (player != null) {
        moveTowards(player.getX(), player.getY(), 2);
    }
}
```

---

### `findObjectsByType(String type)`

Encontra todos os objetos de um determinado tipo.

```java
protected java.util.List<GameObject> findObjectsByType(String type)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `type` | `String` | Tipo/classe do objeto |

**Retorno:** Lista de `GameObject`s do tipo especificado (pode ser vazia)

```java
@Override
public void tick() {
    // Encontrar todos os inimigos
    java.util.List<GameObject> inimigos = findObjectsByType("Enemy");
    
    log("Existem " + inimigos.size() + " inimigos na cena");
    
    // Atacar o inimigo mais próximo
    GameObject maisProximo = null;
    double menorDistancia = Double.MAX_VALUE;
    
    for (GameObject inimigo : inimigos) {
        double dist = distanceTo(inimigo);
        if (dist < menorDistancia) {
            menorDistancia = dist;
            maisProximo = inimigo;
        }
    }
    
    if (maisProximo != null && menorDistancia < 100) {
        log("Inimigo próximo! Atacando...");
    }
}
```

---

### `distanceTo(GameObject other)`

Calcula a distância até outro objeto.

```java
protected double distanceTo(GameObject other)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `other` | `GameObject` | Objeto para calcular distância |

**Retorno:** Distância em pixels (do centro de um objeto ao centro do outro)

```java
@Override
public void tick() {
    GameObject player = findObject("Player");
    
    if (player != null) {
        double distancia = distanceTo(player);
        
        if (distancia < 50) {
            log("Player muito perto! Atacar!");
        } else if (distancia < 200) {
            log("Player detectado. Perseguindo...");
            moveTowards(player.getX(), player.getY(), 2);
        } else {
            log("Player longe demais. Patrulhando...");
        }
    }
}
```

---

### `isColliding(GameObject other)`

Verifica se está colidindo com outro objeto (colisão AABB).

```java
protected boolean isColliding(GameObject other)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `other` | `GameObject` | Objeto para verificar colisão |

**Retorno:** `true` se os objetos estão se sobrepondo

**Nota:** Usa colisão AABB (Axis-Aligned Bounding Box) - verifica se os retângulos dos objetos se sobrepõem.

```java
@Override
public void tick() {
    // Verificar colisão com todas as moedas
    java.util.List<GameObject> moedas = findObjectsByType("Coin");
    
    for (GameObject moeda : moedas) {
        if (isColliding(moeda)) {
            log("Coletou moeda!");
            destroy(moeda);
        }
    }
}
```

---

### `destroy()` e `destroy(GameObject obj)`

Destrói (remove) objetos da cena.

```java
protected void destroy()              // Destrói o próprio objeto
protected void destroy(GameObject obj) // Destrói outro objeto
```

**Retorno:** Nenhum (`void`)

**⚠️ CUIDADO:** Após chamar `destroy()`, não tente acessar o objeto destruído!

```java
private int health = 100;

@Override
public void tick() {
    if (health <= 0) {
        log("Morri!");
        destroy();  // Remove este objeto da cena
        return;     // IMPORTANTE: sair do método após destruir
    }
}

@Override
public void onCollision(GameObject other) {
    if (other.getType().equals("Bullet")) {
        destroy(other);  // Destrói a bala
        health -= 10;    // Toma dano
    }
}
```

---

### `log(String message)`

Imprime uma mensagem de debug no console.

```java
protected void log(String message)
```

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `message` | `String` | Mensagem a ser impressa |

**Retorno:** Nenhum (`void`)

**Formato da saída:** `[NomeDoScript] mensagem`

```java
@Override
public void start() {
    log("Iniciando...");
    // Saída: [PlayerMovement] Iniciando...
}

@Override
public void tick() {
    log("Posição: " + transform.x + ", " + transform.y);
    // Saída: [PlayerMovement] Posição: 100.0, 200.0
}
```

---

## Controle do Script

### `setEnabled(boolean enabled)` e `isEnabled()`

Habilita ou desabilita o script.

```java
public void setEnabled(boolean enabled)
public boolean isEnabled()
```

**Quando desabilitado:**
- `tick()` NÃO é chamado
- `onCollision()` NÃO é chamado
- O objeto ainda existe na cena

```java
private boolean pausado = false;

@Override
public void tick() {
    // Pausar/despausar com P
    if (Input.isKeyJustPressed(KeyEvent.VK_P)) {
        pausado = !pausado;
        setEnabled(!pausado);
        log(pausado ? "Script pausado" : "Script retomado");
    }
    
    // Este código só roda se o script estiver habilitado
    move(Input.getHorizontalAxis() * speed, 0);
}
```

---

### Getters de Informação

| Método | Retorno | Descrição |
|--------|---------|-----------|
| `getScriptName()` | `String` | Nome da classe do script |
| `getGameObject()` | `GameObject` | Objeto ao qual está anexado |
| `getGame()` | `Game` | Referência ao jogo |

```java
@Override
public void start() {
    log("Script: " + getScriptName());
    log("Anexado a: " + getGameObject().getName());
}
```

---

## Variáveis no Inspector

Todas as variáveis declaradas no seu script aparecem automaticamente no painel Inspector do editor.

### Tipos Suportados

| Tipo Java | Aparência no Inspector |
|-----------|------------------------|
| `int`, `Integer` | Campo numérico |
| `double`, `Double` | Campo numérico |
| `float`, `Float` | Campo numérico |
| `long`, `Long` | Campo numérico |
| `boolean`, `Boolean` | Checkbox |
| `String` | Campo de texto |

### Exemplo

```java
public class ConfigurableEnemy extends IgnisScript {
    
    // Todas essas variáveis aparecem no Inspector!
    private double speed = 3.0;
    private int health = 100;
    private int damage = 10;
    private double detectionRange = 150.0;
    private boolean canFly = false;
    private String enemyName = "Goblin";
    
    @Override
    public void tick() {
        // Use as variáveis normalmente
        // Os valores podem ser alterados no Inspector!
    }
}
```

---

## Sistema de Coordenadas

O Ignis Engine usa o sistema de coordenadas padrão de tela:

```
       (0,0)
         ┌─────────────────────────► X+ (aumenta para direita)
         │
         │
         │
         │
         │
         ▼
         Y+ (aumenta para baixo)
```

### Resumo de Direções

| Direção Visual | Operação | Código |
|----------------|----------|--------|
| ⬆️ Cima | Diminuir Y | `transform.y -= 5` ou `move(0, -5)` |
| ⬇️ Baixo | Aumentar Y | `transform.y += 5` ou `move(0, 5)` |
| ⬅️ Esquerda | Diminuir X | `transform.x -= 5` ou `move(-5, 0)` |
| ➡️ Direita | Aumentar X | `transform.x += 5` ou `move(5, 0)` |

### Por que Y aumenta para baixo?

Este é o padrão em praticamente todos os sistemas de computação gráfica:
- Monitores desenham de cima para baixo
- A origem (0,0) fica no canto superior esquerdo
- É assim em Java Swing/AWT, HTML Canvas, Unity 2D, etc.

---

## Exemplos Práticos Completos

### 1. Player com Movimento WASD

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;

public class PlayerMovement extends IgnisScript {
    
    private double speed = 5.0;
    private double sprintMultiplier = 2.0;
    
    @Override
    public void start() {
        log("Player pronto! Use WASD para mover, SHIFT para correr.");
    }
    
    @Override
    public void tick() {
        // Verificar se está correndo
        double currentSpeed = speed;
        if (Input.isKeyPressed(KeyEvent.VK_SHIFT)) {
            currentSpeed *= sprintMultiplier;
        }
        
        // Movimento
        double dx = Input.getHorizontalAxis() * currentSpeed;
        double dy = Input.getVerticalAxis() * currentSpeed;
        move(dx, dy);
    }
}
```

### 2. Inimigo que Persegue o Player

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;

public class EnemyAI extends IgnisScript {
    
    private double speed = 2.0;
    private double detectionRange = 200.0;
    private double attackRange = 30.0;
    private GameObject player;
    
    @Override
    public void start() {
        player = findObject("Player");
        if (player == null) {
            log("AVISO: Player não encontrado!");
        }
    }
    
    @Override
    public void tick() {
        if (player == null) return;
        
        double distancia = distanceTo(player);
        
        if (distancia <= attackRange) {
            // Perto o suficiente para atacar
            log("Atacando o player!");
        } else if (distancia <= detectionRange) {
            // Perseguir o player
            moveTowards(player.getX(), player.getY(), speed);
            lookAt(player.getX(), player.getY());
        } else {
            // Player muito longe, patrulhar ou ficar parado
        }
    }
}
```

### 3. Coletor de Itens

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;
import java.util.List;

public class ItemCollector extends IgnisScript {
    
    private int coins = 0;
    private int gems = 0;
    
    @Override
    public void start() {
        log("Colete moedas e gemas!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        String tipo = other.getType();
        
        if (tipo.equals("Coin")) {
            coins++;
            destroy(other);
            log("Moeda! Total: " + coins);
        } else if (tipo.equals("Gem")) {
            gems++;
            destroy(other);
            log("Gema! Total: " + gems);
        }
    }
}
```

### 4. Objeto Rotativo

```java
import com.ignis.core.IgnisScript;

public class Spinner extends IgnisScript {
    
    private double rotationSpeed = 2.0;
    private boolean clockwise = true;
    
    @Override
    public void tick() {
        double direction = clockwise ? 1 : -1;
        rotate(rotationSpeed * direction);
    }
}
```

### 5. Seguidor de Mouse

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;

public class MouseFollower extends IgnisScript {
    
    private double speed = 4.0;
    private boolean lookAtMouse = true;
    
    @Override
    public void tick() {
        int mouseX = Input.getMouseX();
        int mouseY = Input.getMouseY();
        
        moveTowards(mouseX, mouseY, speed);
        
        if (lookAtMouse) {
            lookAt(mouseX, mouseY);
        }
    }
}
```

---

## Boas Práticas

### ✅ Faça

1. **Use `start()` para inicialização**
   ```java
   private GameObject target;
   
   @Override
   public void start() {
       target = findObject("Target");  // ✅ Correto
   }
   ```

2. **Mantenha `tick()` leve** - É chamado 60+ vezes por segundo

3. **Use `isKeyJustPressed()` para ações únicas**
   ```java
   if (Input.isKeyJustPressed(KeyEvent.VK_SPACE)) {
       pular();  // ✅ Pula uma vez
   }
   ```

4. **Verifique null antes de usar referências**
   ```java
   if (player != null) {
       moveTowards(player.getX(), player.getY(), speed);
   }
   ```

5. **Use `log()` para debug**
   ```java
   log("Posição: " + transform.x + ", " + transform.y);
   ```

### ❌ Não Faça

1. **Não inicialize no construtor**
   ```java
   public MeuScript() {
       target = findObject("Target");  // ❌ Erro! Game ainda não existe
   }
   ```

2. **Não use `isKeyPressed()` para ações únicas**
   ```java
   if (Input.isKeyPressed(KeyEvent.VK_SPACE)) {
       atirar();  // ❌ Vai atirar todo frame enquanto segura!
   }
   ```

3. **Não acesse objetos após `destroy()`**
   ```java
   destroy();
   log("Minha posição: " + transform.x);  // ❌ Objeto não existe mais!
   ```

4. **Não faça operações pesadas em `tick()`**
   ```java
   @Override
   public void tick() {
       carregarArquivo();  // ❌ Muito lento para rodar todo frame!
   }
   ```

---

## Localização dos Scripts

```
projects/
  └── SeuProjeto/
      └── project/
          └── scripts/
              ├── PlayerMovement.java
              ├── EnemyAI.java
              └── ItemCollector.java
```

Os scripts são **recompilados automaticamente** quando você clica em **Play** no editor.

---

## Resumo Rápido

| O que você quer fazer | Método/Código |
|-----------------------|---------------|
| Mover o objeto | `move(dx, dy)` |
| Mover até um ponto | `moveTowards(x, y, speed)` |
| Rotacionar | `rotate(degrees)` |
| Olhar para um ponto | `lookAt(x, y)` |
| Verificar tecla pressionada | `Input.isKeyPressed(KeyEvent.VK_X)` |
| Verificar tecla acabou de pressionar | `Input.isKeyJustPressed(KeyEvent.VK_X)` |
| Posição do mouse | `Input.getMouseX()`, `Input.getMouseY()` |
| Clique do mouse | `Input.isMouseLeftJustPressed()` |
| Tocar efeito sonoro | `playSound("arquivo.wav")` |
| Tocar som com volume | `playSound("arquivo.wav", 0.5f)` |
| Tocar música (loop) | `playMusic("arquivo.wav")` |
| Pausar música | `pauseMusic()` |
| Retomar música | `resumeMusic()` |
| Parar música | `stopMusic()` |
| Volume geral | `setMasterVolume(0.8f)` |
| Volume da música | `setMusicVolume(0.5f)` |
| Volume dos efeitos | `setSfxVolume(1.0f)` |
| Encontrar objeto | `findObject("Nome")` |
| Encontrar objetos por tipo | `findObjectsByType("Tipo")` |
| Calcular distância | `distanceTo(outroObjeto)` |
| Verificar colisão | `isColliding(outroObjeto)` |
| Destruir objeto | `destroy()` ou `destroy(obj)` |
| Imprimir debug | `log("mensagem")` |
| Posição atual | `transform.x`, `transform.y` |
| Rotação atual | `transform.rotation` |
| Tamanho | `transform.width`, `transform.height` |

---

*Documentação do Ignis Engine - Última atualização: Janeiro 2026*
