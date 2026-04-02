# IgnisScript - Guia Rápido para Geração de Código

## Estrutura Mínima (Copiar/Colar)

```java
import com.ignis.core.IgnisScript;

public class MyClass extends IgnisScript {
    
    @Override
    public void start() {
        // Initialization code here
    }

    @Override
    public void tick() {
        // Game logic here (called every frame)
    }
}
```

**São 3 coisas obrigatórias:**
1. `extends IgnisScript` ✅
2. `@Override public void start()` ✅
3. `@Override public void tick()` ✅

---

## Acesso ao Transform (Posição/Rotação)

```java
// LEITURA
float x = transform.x;
float y = transform.y;
float rotation = transform.rotation;

// ESCRITA (Movimento)
transform.x += 5;     // Move 5 pixels para direita
transform.y -= 3;     // Move 3 pixels para cima
transform.x = 100;    // Define posição X em 100

// ROTAÇÃO
transform.rotation += 45;  // Adiciona 45 graus
```

**NUNCA use:**
- ❌ `gameObject.getTransform().translate()`
- ❌ `transform.translate()`
- ❌ `gameObject.getPos()`

---

## Sistema de Input (Teclado)

```java
import java.awt.event.KeyEvent;

// No método tick():
if (Input.getInstance().isKeyPressed(KeyEvent.VK_W)) {
    // W key pressed
    transform.y -= 5;
}

if (Input.getInstance().isKeyPressed(KeyEvent.VK_SPACE)) {
    // Spacebar pressed
}
```

**Teclas comuns:**
- `KeyEvent.VK_W` - W
- `KeyEvent.VK_A` - A
- `KeyEvent.VK_S` - S
- `KeyEvent.VK_D` - D
- `KeyEvent.VK_SPACE` - Spacebar
- `KeyEvent.VK_UP` - Seta para cima
- `KeyEvent.VK_DOWN` - Seta para baixo
- `KeyEvent.VK_LEFT` - Seta para esquerda
- `KeyEvent.VK_RIGHT` - Seta para direita

**NUNCA use:**
- ❌ `Input.isKeyDown()`
- ❌ `Input.getKey()`
- ❌ Static `Input.method()`

---

## Exemplo Completo: Player Controller

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;

public class PlayerController extends IgnisScript {
    private float moveSpeed = 5.0f;
    private float rotateSpeed = 3.0f;

    @Override
    public void start() {
        System.out.println("Player initialized!");
    }

    @Override
    public void tick() {
        handleMovement();
        handleRotation();
    }

    private void handleMovement() {
        float moveX = 0;
        float moveY = 0;

        if (Input.getInstance().isKeyPressed(KeyEvent.VK_W)) {
            moveY -= moveSpeed;
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_A)) {
            moveX -= moveSpeed;
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_S)) {
            moveY += moveSpeed;
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_D)) {
            moveX += moveSpeed;
        }

        transform.x += moveX;
        transform.y += moveY;
    }

    private void handleRotation() {
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_LEFT)) {
            transform.rotation -= rotateSpeed;
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_RIGHT)) {
            transform.rotation += rotateSpeed;
        }
    }
}
```

---

## Regras de Ouro

| ✅ CORRETO | ❌ ERRADO |
|-----------|----------|
| `extends IgnisScript` | `extends Script` |
| `import com.ignis.core.*` | `import ignis.*` |
| `@Override public void tick()` | `public void update()` |
| `transform.x += value` | `transform.translate()` |
| `Input.getInstance()...` | `Input.isKeyDown()` |
| Sem `package` | `package scripts;` |
| `.java` files | `.ignis` files |

---

## Arquivo Syntax Check

Antes de criar um arquivo, verifique:

```
✓ Começa com import, não com package
✓ Estende IgnisScript
✓ Tem start() com @Override
✓ Tem tick() com @Override
✓ Usa transform.x/y, não getTransform()
✓ Usa Input.getInstance(), não Input.static
✓ Código é compilável (sem erros de sintaxe)
✓ Arquivo termina com }
```

---

## Imports Necessários por Tipo de Script

**Script Mínimo:**
```java
import com.ignis.core.IgnisScript;
```

**Script com Input:**
```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;
```

**Script com Game Logic:**
```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Game;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;
```

---

## Exemplo: Inimigo Simples

```java
import com.ignis.core.IgnisScript;

public class Enemy extends IgnisScript {
    private float speed = 2.0f;

    @Override
    public void start() {
        // Inicializa o inimigo
    }

    @Override
    public void tick() {
        // Movimento automático em padrão
        transform.x += speed;
        
        // Inverte direção em limites
        if (transform.x > 500 || transform.x < 0) {
            speed *= -1;
        }
    }
}
```

---

## Estrutura de Diretórios

Scripts devem ser salvos em:
```
projects/Game 01/project/scripts/
```

Exemplo:
- `projects/Game 01/project/scripts/Player.java`
- `projects/Game 01/project/scripts/Enemy.java`
- `projects/Game 01/project/scripts/items/Coin.java`

---

## Troubleshooting

**Q: Recebo erro "Symbol not found: IgnisScript"**  
A: Verifique se está usando `import com.ignis.core.IgnisScript;`

**Q: Recebo "Symbol not found: Input"**  
A: Adicione `import com.ignis.core.Input;`

**Q: Método `update()` não é chamado**  
A: Use `tick()` em vez de `update()`, com `@Override`

**Q: `translate()` não é reconhecido no Transform**  
A: Use `transform.x += value;` em vez de `transform.translate()`

---

Copie este guia no contexto para garantir código correto! ✅

