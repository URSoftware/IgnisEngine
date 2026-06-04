# Sistema de Colisões do Ignis Engine - Documentação Completa

## 📋 Visão Geral

O Ignis Engine possui um **sistema de colisão avançado** com:
- Detecção de colisão automática
- Múltiplos tipos de colisores (AABB, Circle, Polygon)
- Dois modos: TRIGGER (apenas eventos) e COLLISION (com resposta física)
- Resolução de colisão com MTV (Minimum Translation Vector)
- Detecção contínua (CCD) para objetos rápidos

---

## 🎯 Como Usar Colisões em Scripts

### Passo 1: Sobrescrever o Método `onCollision()`

Todos os scripts que herdam de `IgnisScript` podem sobrescrever `onCollision()`:

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;

public class MyScript extends IgnisScript {
    
    @Override
    public void onCollision(GameObject other) {
        // Executado automaticamente quando há colisão
        System.out.println("Colidiu com: " + other.getClass().getSimpleName());
    }
}
```

### Passo 2: Configurar o GameObject com um Collider

No Editor ou via código:

```java
// Criar GameObject com collider circular
GameObject player = new GameObject(0, 0, 50, 50);
player.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
```

---

## 🔷 Tipos de Colisores

### 1. AABB (Axis-Aligned Bounding Box)
**Uso:** Retângulos alinhados aos eixos, sem rotação

```java
gameObject.setColliderType(IgnisSampleCollisions.ColliderType.AABB);
```

**Características:**
- Rápido e eficiente
- Não funciona bem com objetos rotacionados
- Ideal para: plataformas, caixas, paredes

**Exemplo - Coin Pickup:**
```java
public class CoinPickup extends IgnisScript {
    @Override
    public void onCollision(GameObject other) {
        // Verificar se colidiu com o player
        if (other.getClass().getSimpleName().equals("Player")) {
            System.out.println("Moeda coletada!");
            // gameObject.destroy(); (quando implementado)
        }
    }
}
```

---

### 2. Circle (Círculo)
**Uso:** Colisões circulares, intuitivas

```java
gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
```

**Características:**
- Baseado no raio do objeto
- Funciona bem com rotação
- Ideal para: personagens, bolas, explosões

**Exemplo - Explosão Circular:**
```java
public class Explosion extends IgnisScript {
    @Override
    public void onCollision(GameObject other) {
        System.out.println("[BOOM] Objeto " + other.getClass().getSimpleName() + " foi afetado!");
        // Aplicar dano, knockback, etc.
    }
}
```

---

### 3. Polygon (Polígono)
**Uso:** Formas complexas com múltiplos vértices

```java
gameObject.setColliderType(IgnisSampleCollisions.ColliderType.POLYGON);
```

**Características:**
- Usa SAT (Separating Axis Theorem)
- Mais preciso, porém mais lento
- Ideal para: personagens complexos, inimigos, objetos irregulares

---

## ⚙️ Modos de Colisão

### TRIGGER (Apenas Detecção)
```java
gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.TRIGGER);
```

**Comportamento:**
- Detecta colisões sem parar os objetos
- Ideal para: detecção de áreas, pickups, triggers
- Objetos podem passar através uns dos outros

**Exemplo - Área de Dano:**
```java
public class DamageArea extends IgnisScript {
    @Override
    public void onCollision(GameObject other) {
        System.out.println("[DAMAGEZONE] " + other + " entrou na área de dano!");
        // Aplicar dano sem physics
    }
}
```

### COLLISION (Com Resposta Física)
```java
gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.COLLISION);
```

**Comportamento:**
- Detecta e resolve colisões fisicamente
- Objetos não passam uns através dos outros
- Ideal para: paredes, chão, plataformas
- Usa MTV para afastar objetos

**Exemplo - Plataforma Sólida:**
```java
public class Platform extends IgnisScript {
    @Override
    public void start() {
        getGameObject().getCollider().setMode(
            IgnisSampleCollisions.CollisionMode.COLLISION
        );
    }
}
```

---

## 📍 Acessar Informações de Colisão

### Posição do Outro Objeto

```java
@Override
public void onCollision(GameObject other) {
    double otherX = other.getX();
    double otherY = other.getY();
    
    System.out.println("Colidiu em (" + otherX + ", " + otherY + ")");
}
```

### Própria Posição

```java
@Override
public void onCollision(GameObject other) {
    double myX = transform.x;  // Ou getX()
    double myY = transform.y;  // Ou getY()
    
    System.out.println("Eu estou em (" + myX + ", " + myY + ")");
}
```

### Tamanho do Objeto

```java
@Override
public void onCollision(GameObject other) {
    int width = other.getWidth();
    int height = other.getHeight();
    double rotation = other.getRotation();
    
    System.out.println("Tamanho: " + width + "x" + height + ", Rotação: " + rotation + "°");
}
```

### Distância Entre Objetos

```java
@Override
public void onCollision(GameObject other) {
    double dx = other.getX() - transform.x;
    double dy = other.getY() - transform.y;
    double distance = Math.sqrt(dx * dx + dy * dy);
    
    System.out.println("Distância: " + distance + " pixels");
}
```

---

## 🚫 ERROS COMUNS COM COLISÕES

### ❌ ERRO 1: Cálculos Manuais de Colisão
**Errado:**
```java
// Calcular distância manualmente em tick()
float distance = Math.sqrt(dx * dx + dy * dy);
if (distance < threshold) { ... }
```

**Correto:**
```java
// Usar onCollision() - o engine faz tudo
@Override
public void onCollision(GameObject other) { ... }
```

---

### ❌ ERRO 2: Não Configurar Collider
**Errado:**
```java
public class Player extends IgnisScript {
    @Override
    public void onCollision(GameObject other) { ... }
    // Sem collider configurado
}
```

**Correto:**
```java
public class Player extends IgnisScript {
    @Override
    public void start() {
        gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
    }
    
    @Override
    public void onCollision(GameObject other) { ... }
}
```

---

### ❌ ERRO 3: Esquecer null check
**Errado:**
```java
@Override
public void onCollision(GameObject other) {
    System.out.println(other.getX()); // Pode ser null!
}
```

**Correto:**
```java
@Override
public void onCollision(GameObject other) {
    if (other == null) {
        System.out.println("[ERROR] null GameObject!");
        return;
    }
    System.out.println(other.getX());
}
```

---

### ❌ ERRO 4: Confundir Modes
**Errado:**
```java
// Usando TRIGGER quando precisa de física
gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.TRIGGER);
// Agora os objetos passam através uns dos outros!
```

**Correto:**
```java
// Usar COLLISION para solids, TRIGGER para detectores
if (isPlatform) {
    gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.COLLISION);
} else if (isPickup) {
    gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.TRIGGER);
}
```

---

## 📊 Filtro de Colisões (Layer e Mask)

### Configurar Camada

```java
@Override
public void start() {
    IgnisSampleCollisions.Collider collider = gameObject.getCollider();
    collider.setLayer(1);  // Este objeto está na camada 1
}
```

### Configurar Máscara (Com o quê colidir)

```java
@Override
public void start() {
    IgnisSampleCollisions.Collider collider = gameObject.getCollider();
    collider.setCollisionMask(0b0011);  // Colide com camadas 0 e 1
}
```

---

## 🎮 Exemplo Completo: Inimigo com Colisão

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisSampleCollisions;

public class Enemy extends IgnisScript {
    private java.util.Set<GameObject> collidingWith = new java.util.HashSet<>();
    private int health = 100;

    @Override
    public void start() {
        System.out.println("[ENEMY] Inicializado com " + health + " HP");
        gameObject.setColliderType(IgnisSampleCollisions.ColliderType.CIRCLE);
        gameObject.getCollider().setMode(IgnisSampleCollisions.CollisionMode.COLLISION);
    }

    @Override
    public void tick() {
        // Movimento padrão
        transform.x += 2;
    }

    @Override
    public void onCollision(GameObject other) {
        if (other == null) return;
        
        String otherName = other.getClass().getSimpleName();
        
        // Primeira colisão com este objeto
        if (!collidingWith.contains(other)) {
            collidingWith.add(other);
            System.out.println("[COLLISION] Inimigo colidiu com: " + otherName);
            
            if (otherName.equals("Bullet")) {
                health -= 25;
                System.out.println("[DAMAGE] Inimigo recebeu dano! HP: " + health);
                
                if (health <= 0) {
                    System.out.println("[DEATH] Inimigo morreu!");
                    // gameObject.destroy();
                }
            }
        }
    }
}
```

---

## 🔧 Configuração Avançada

### Offset de Collider
```java
@Override
public void start() {
    IgnisSampleCollisions.Collider collider = gameObject.getCollider();
    collider.setOffset(10, -5);  // Offset X=10, Y=-5
}
```

### Continuous Collision Detection (CCD)
Para objetos que se movem muito rápido:

```java
@Override
public void start() {
    IgnisSampleCollisions.Collider collider = gameObject.getCollider();
    collider.setUseCCD(true);  // Ativar CCD
}
```

---

## 📋 Checklist para Colisões Funcionarem

Quando criando scripts com colisão:

- [ ] Script estende `IgnisScript`
- [ ] Script sobrescreve `onCollision(GameObject other)`
- [ ] GameObject tem collider configurado (`setColliderType()`)
- [ ] Collider está no modo correto (TRIGGER ou COLLISION)
- [ ] Faz null check em `onCollision()`
- [ ] Acessa `other.getX()`, `other.getY()`, etc. corretamente
- [ ] Não faz cálculos manuais de distância em `tick()`
- [ ] Importa `IgnisSampleCollisions` se usar modos/tipos

---

## 🎯 Resumo Rápido

| Situação | Tipo de Collider | Modo | Método |
|----------|------------------|------|--------|
| Plataforma sólida | AABB | COLLISION | onCollision() |
| Personagem | Circle | COLLISION | onCollision() |
| Pickup (moeda) | Circle | TRIGGER | onCollision() |
| Área de dano | AABB | TRIGGER | onCollision() |
| Inimigo complexo | POLYGON | COLLISION | onCollision() |

