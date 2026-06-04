# Colisões do Ignis - Guia Rápido

## Template Mínimo com Colisão

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisSampleCollisions;

public class MyScript extends IgnisScript {
    
    @Override
    public void start() {
        // Configurar tipo de colisão
        gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
    }

    @Override
    public void onCollision(GameObject other) {
        // Chamado automaticamente quando colide
        if (other == null) return;
        System.out.println("Colidiu com: " + other.getClass().getSimpleName());
    }
}
```

---

## Os 3 Tipos de Colisores

### AABB (Retângulo)
```java
gameObject.setColliderType(IgnisSampleCollisions.ColliderType.AABB);
// Uso: Plataformas, caixas, paredes
// Rápido, mas não funciona com rotação
```

### CIRCLE (Círculo)
```java
gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
// Uso: Personagens, bolas, explosões
// Funciona bem com rotação
```

### POLYGON (Polígono)
```java
gameObject.setColliderType(IgnisSampleCollisions.ColliderType.POLYGON);
// Uso: Formas complexas
// Mais preciso, mais lento
```

---

## 2 Modos de Colisão

### TRIGGER (Só Detecção)
```java
gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.TRIGGER);
// Objetos passam através uns dos outros
// Uso: Pickups, áreas de detecção
```

### COLLISION (Com Física)
```java
gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.COLLISION);
// Objetos NOT passam através (física)
// Uso: Paredes, chão, plataformas
```

---

## Acessar Dados de Colisão

```java
@Override
public void onCollision(GameObject other) {
    double x = other.getX();           // Posição X
    double y = other.getY();           // Posição Y
    int width = other.getWidth();      // Largura
    int height = other.getHeight();    // Altura
    double rotation = other.getRotation(); // Rotação
    
    String name = other.getClass().getSimpleName(); // Nome da classe
}
```

---

## Exemplo: Inimigo com Vita

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisSampleCollisions;

public class Enemy extends IgnisScript {
    private int health = 50;
    private java.util.Set<GameObject> hit = new java.util.HashSet<>();

    @Override
    public void start() {
        gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
    }

    @Override
    public void tick() {
        // Movimento ou outras lógicas
        transform.x += 1;
    }

    @Override
    public void onCollision(GameObject other) {
        if (other == null || hit.contains(other)) return;
        hit.add(other);
        
        if (other.getClass().getSimpleName().equals("Bullet")) {
            health -= 10;
            System.out.println("[HIT] Vida: " + health);
            if (health <= 0) {
                System.out.println("[DEAD]");
                // gameObject.destroy();
            }
        }
    }
}
```

---

## Exemplo: Pickup (Moeda)

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisSampleCollisions;

public class Coin extends IgnisScript {
    
    @Override
    public void start() {
        // Pickup é trigger (detecção apenas)
        gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
        gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.TRIGGER);
    }

    @Override
    public void onCollision(GameObject other) {
        if (other == null) return;
        
        if (other.getClass().getSimpleName().equals("Player")) {
            System.out.println("[COLLECTED] +1 Coin!");
            // gameObject.destroy();
        }
    }
}
```

---

## Regras de Ouro 🏆

| ✅ CORRETO | ❌ ERRADO |
|-----------|----------|
| `onCollision(other)` | Cálculos manuais de distância |
| `other.getX()` | `other.getTransform().x` |
| `if (other == null) return;` | Sem verificação null |
| `setColliderType()` no start() | Sem collider configurado |
| TRIGGER para pickups | COLLISION para pickups |
| Usar HashSet para não-spam | Verificação a cada frame |

---

## Evite Estes Erros ❌

```java
// ERRADO: Cálculos manuais
float distance = Math.sqrt(dx*dx + dy*dy);
if (distance < threshold) { ... }

// CORRETO: Usar onCollision()
@Override public void onCollision(GameObject other) { ... }
```

```java
// ERRADO: Usar getTransform()
other.getTransform().x;

// CORRETO: Usar getX()
other.getX();
```

```java
// ERRADO: Sem null check
System.out.println(other.getX());

// CORRETO: Com null check
if (other == null) return;
System.out.println(other.getX());
```

---

## Debug de Colisões

```java
@Override
public void onCollision(GameObject other) {
    System.out.println("[COLLISION DEBUG]");
    System.out.println("  Me: " + gameObject.getClass().getSimpleName() 
        + " em (" + transform.x + ", " + transform.y + ")");
    System.out.println("  Outro: " + other.getClass().getSimpleName() 
        + " em (" + other.getX() + ", " + other.getY() + ")");
    System.out.println("  Tamanho outro: " + other.getWidth() + "x" + other.getHeight());
}
```

---

## Checklist de Colisão ✓

Para scripts com colisão funcionarem:

- [ ] Importar `GameObject` e `IgnisSampleCollisions`
- [ ] Sobrescrever `onCollision(GameObject other)`
- [ ] Configurar tipo com `setColliderType()` no start()
- [ ] Fazer null check em onCollision()
- [ ] Usar `other.getX()`, `other.getY()` (NOT getTransform())
- [ ] Compilar sem erros
- [ ] GameObject tem collider no editor

---

Copie este guia quando precisar de referência rápida! ✅

