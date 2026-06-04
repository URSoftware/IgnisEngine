# Guia Completo: Sistema de Colisões & Alert Debug no Ignis Engine

## 1. Sistema de Alertas (Debug Messages)

### O que é o Sistema de Alertas?

O sistema de alertas permite exibir mensagens de debug diretamente na tela do editor do Ignis Engine. As mensagens aparecem no canto superior esquerdo com fade-out automático após 3 segundos.

### Como Usar o Alert

#### Usando em Scripts (Callbacks de Colisão):

```java
public class PlayerScript extends IgnisScript {
    
    @Override
    public void onCollision(GameObject other) {
        // Exibir alerta na tela
        getGame().alert("Colidiu com: " + other.getName());
    }
    
    @Override
    public void onTrigger(GameObject other) {
        // Alertas também funcionam em triggers
        getGame().alert("Trigger ativado: " + other.getName());
    }
}
```

#### Usando Fora de Scripts (No Editor):

```java
// No Editor.java ou em qualquer lugar que tenha acesso ao Game
game.alert("Mensagem de teste");
```

### Características dos Alertas

- **Duração**: 3 segundos na tela
- **Máximo simultâneo**: 5 mensagens
- **Posição**: Canto superior esquerdo com small offset (x:15, y:35)
- **Estilo**: Fundo verde escuro, texto branco
- **Fade-out**: A mensagem desaparece suavemente no último segundo
- **Automaticamente limpo**: Mensagens expiradas são removidas automaticamente

### Exemplo Prático de Teste

```java
public class TestCollisionScript extends IgnisScript {
    
    @Override
    public void start() {
        // Testar alerta simples
        getGame().alert("Script iniciado!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        // Coletar informações úteis
        String msg = String.format(
            "Colisão com %s (pos: %.1f, %.1f)",
            other.getName(),
            other.getX(),
            other.getY()
        );
        getGame().alert(msg);
    }
    
    @Override
    public void update() {
        // Testar colisões manualmente
        if (getInput().isKeyPressed("space")) {
            getGame().alert("Espaço pressionado!");
        }
    }
}
```

---

## 2. Sistema de Colisões Avançado

### Arquitetura do Sistema de Colisões

O Ignis Engine possui um sistema de colisões que utiliza:

1. **Broad Phase Detection** (Detecção ampla)
   - Usa uma SpatialGrid para dividir o mundo em células
   - Reduz o número de comparações necessárias
   - Melhora significativa de performance

2. **Narrow Phase Detection** (Detecção precisa)
   - Compara formas específicas (Circle, Rectangle, Polygon)
   - Calcula se há sobreposição real
   - Retorna dados de colisão (MTV - Minimum Translation Vector)

3. **Collision Callbacks** (Callbacks de colisão)
   - `onCollision(GameObject other)` - Colisão física
   - `onTrigger(GameObject other)` - Área ativadora (sem física)

### Tipos de Colisão

#### 1. Colisão Física (Collision)

Dois objetos se colidem e aplicam impulsos um ao outro.

```java
// Configurar colisão física
public class RigidBodyScript extends IgnisScript {
    
    @Override
    public void start() {
        // Ativar colisão
        setCollisionEnabled(true);
        
        // Definir modo como "collision" (não trigger)
        setCollisionMode("collision");
        
        // Definir forma de colisão (Circle, Rectangle, Pentagon, etc)
        setCollisionShape("circle");
        
        getGame().alert("Colisão física ativada!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert("Batida física com: " + other.getName());
    }
}
```

#### 2. Trigger (Área Ativadora)

Um objeto que dispara callbacks quando outro o atravessa, mas não aplica física.

```java
public class TriggerScript extends IgnisScript {
    
    @Override
    public void start() {
        // Ativar colisão como trigger
        setCollisionEnabled(true);
        setCollisionMode("trigger");
        setCollisionShape("rectangle");
        
        getGame().alert("Trigger pronto para ativar!");
    }
    
    @Override
    public void onTrigger(GameObject other) {
        getGame().alert("Algo entrou no trigger: " + other.getName());
    }
}
```

### Formas de Colisão Disponíveis

```java
// As seguintes formas estão disponíveis:
setCollisionShape("circle");      // Círculo
setCollisionShape("rectangle");   // Retângulo
setCollisionShape("triangle");    // Triângulo
setCollisionShape("square");      // Quadrado
setCollisionShape("pentagon");    // Pentágono
setCollisionShape("star");        // Estrela
```

### Obtendo Informações de Colisão

```java
public class InfoCollisionScript extends IgnisScript {
    
    @Override
    public void onCollision(GameObject other) {
        // Informações básicas
        String name = other.getName();
        double x = other.getX();
        double y = other.getY();
        int width = other.getWidth();
        int height = other.getHeight();
        
        // Mensagem formatada
        String info = String.format(
            "Nome: %s | Pos: (%.0f, %.0f) | Tam: %dx%d",
            name, x, y, width, height
        );
        
        getGame().alert(info);
        
        // Mais informações
        if (other instanceof GameObject) {
            getGame().alert("Rotation: " + other.getRotation() + "°");
        }
    }
}
```

---

## 3. Exemplos Práticos Completos

### Exemplo 1: Sistema de Dano ao Colidir

```java
public class DamageScript extends IgnisScript {
    private int health = 100;
    private int damagePerHit = 10;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
        getGame().alert("Sistema de dano inicializado com " + health + " HP");
    }
    
    @Override
    public void onCollision(GameObject other) {
        // Receber dano ao colidir
        if (other.getName().contains("Spike") || other.getName().contains("Enemy")) {
            health -= damagePerHit;
            getGame().alert(String.format("Levou dano! HP: %d/%d", health, 100));
            
            if (health <= 0) {
                getGame().alert("Vida zerada! Game Over!");
                // Implementar respawn/gameOver aqui
            }
        }
    }
}
```

### Exemplo 2: Detecção de Zona de Segurança

```java
public class SafeZoneScript extends IgnisScript {
    private boolean isInSafeZone = false;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");  // Não tem física
        setCollisionShape("circle");
        getGame().alert("Zona segura criada!");
    }
    
    @Override
    public void onTrigger(GameObject other) {
        if (other.getName().equals("Player")) {
            isInSafeZone = true;
            getGame().alert("Você entrou na zona segura!");
        }
    }
    
    @Override
    public void update() {
        if (isInSafeZone) {
            // Recuperar saúde ou fazer algo dentro da zona
        }
    }
}
```

### Exemplo 3: Sistema de Coleta de Items

```java
public class ItemScript extends IgnisScript {
    private int itemValue = 10;
    private boolean isCollected = false;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");
        setCollisionShape("circle");
        getGame().alert("Item: +" + itemValue + " pontos");
    }
    
    @Override
    public void onTrigger(GameObject other) {
        if (!isCollected && other.getName().equals("Player")) {
            isCollected = true;
            getGame().alert("Coletou item! +" + itemValue + " pontos!");
            
            // Remover o item do mundo
            getGame().removeEntity(this.getGameObject());
        }
    }
}
```

### Exemplo 4: Detecção de Colisão com Log Detalhado

```java
public class DetailedCollisionScript extends IgnisScript {
    private int collisionCount = 0;
    
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("rectangle");
    }
    
    @Override
    public void onCollision(GameObject other) {
        collisionCount++;
        
        // Log detalhado
        String log = String.format(
            "[%d] Colisão com %s\n" +
            "Pos minha: (%.0f, %.0f)\n" +
            "Pos deles: (%.0f, %.0f)\n" +
            "Distância: %.0f",
            collisionCount,
            other.getName(),
            this.getGameObject().getX(),
            this.getGameObject().getY(),
            other.getX(),
            other.getY(),
            Math.hypot(
                other.getX() - this.getGameObject().getX(),
                other.getY() - this.getGameObject().getY()
            )
        );
        
        getGame().alert(log);
    }
}
```

---

## 4. Testando o Sistema

### Passo 1: Criar um Projeto de Teste

1. Abra o Ignis Editor
2. Crie um novo projeto chamado "CollisionTest"
3. Crie duas cenas: "TestScene" e "PhysicsScene"

### Passo 2: Configurar Objetos de Teste

#### Scene 1: Test Scene (Alertas Simples)

```java
// File: projects/Game 01/project/scripts/PlayerScript.java
public class PlayerScript extends IgnisScript {
    @Override
    public void start() {
        getGame().alert("Player iniciado!");
    }
    
    @Override
    public void update() {
        if (getInput().isKeyPressed("space")) {
            getGame().alert("Espaço pressionado!");
        }
    }
    
    @Override
    public void onCollision(GameObject other) {
        getGame().alert("Colidiu com: " + other.getName());
    }
}
```

#### Scene 2: Physics Scene (Colisões Complexas)

```java
// Attach ao objeto Player
public class PhysicsPlayerScript extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
        getGame().alert("Player pronto para colisões!");
    }
    
    @Override
    public void onCollision(GameObject other) {
        String type = "objeto";
        if (other.getName().contains("Ball")) {
            type = "bola";
        } else if (other.getName().contains("Wall")) {
            type = "parede";
        }
        
        getGame().alert("Colidiu com " + type + ": " + other.getName());
    }
}

// Attach ao objeto Enemy
public class EnemyScript extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("collision");
        setCollisionShape("circle");
    }
    
    @Override
    public void onCollision(GameObject other) {
        if (other.getName().equals("Player")) {
            getGame().alert("Enemy interceptou Player!");
        }
    }
}

// Attach a uma zona de coleta
public class LootTriggerScript extends IgnisScript {
    @Override
    public void start() {
        setCollisionEnabled(true);
        setCollisionMode("trigger");  // Sem física
        setCollisionShape("rectangle");
        getGame().alert("Zona de loot pronta!");
    }
    
    @Override
    public void onTrigger(GameObject other) {
        if (other.getName().equals("Player")) {
            getGame().alert("Player chegou perto do loot!");
        }
    }
}
```

### Passo 3: Executar e Testar

1. Pressione Play no editor
2. Observe os alertas aparecendo na tela enquanto se move
3. Use o mouse/teclado para testar as colisões
4. Verifique as mensagens nos alertas para debug

---

## 5. Debugging com Alertas

### Técnicas Úteis

#### 1. Verificar se um Script Está Rodando

```java
@Override
public void start() {
    getGame().alert("Script " + this.getClass().getSimpleName() + " iniciado!");
}
```

#### 2. Monitorar Variáveis

```java
private int counter = 0;

@Override
public void update() {
    counter++;
    if (counter % 60 == 0) {  // A cada 1 segundo (60 frames)
        getGame().alert("contador: " + counter);
    }
}
```

#### 3. Rastrear Colisões Específicas

```java
@Override
public void onCollision(GameObject other) {
    boolean isEnemy = other.getName().toLowerCase().contains("enemy");
    boolean isWall = other.getName().toLowerCase().contains("wall");
    
    if (isEnemy) {
        getGame().alert("⚠️ Colidiu com inimigo!");
    } else if (isWall) {
        getGame().alert("Colidiu com parede");
    }
}
```

#### 4. Performance Monitoring

```java
@Override
public void update() {
    long startTime = System.currentTimeMillis();
    
    // Seu código aqui
    expensive_operation();
    
    long elapsed = System.currentTimeMillis() - startTime;
    if (elapsed > 16) {  // Mais de 1 frame a 60fps
        getGame().alert("⏱️ Update lento: " + elapsed + "ms");
    }
}
```

---

## 6. Troubleshooting

### Problema: Alertas não aparecem
- **Solução**: Verifique se `game.setEditor(editor)` foi chamado no Editor
- **Solução**: Verifique se está em modo EDITING (não PLAYING)

### Problema: Colisões não disparam
- **Solução 1**: Verifique `setCollisionEnabled(true)`
- **Solução 2**: Verifique se a forma está configurada com `setCollisionShape()`
- **Solução 3**: Verifique se os objetos realmente se sobrepõem visualmente

### Problema: Triggers não funcionam
- **Solução**: Certifique-se de usar `setCollisionMode("trigger")`
- **Solução**: Use `onTrigger()` em vez de `onCollision()`

### Problema: Muitos alertas simultâneos
- **Limite**: Máximo 5 alertas visíveis por vez
- **Solução**: Consolidar mensagens ou usar um logger separado

---

## 7. Integração com IgnisScript

### Métodos Disponíveis

```java
// Obter o Game
Game game = getGame();

// Displayar alert
game.alert("Mensagem aqui");

// Obter acesso a entidades
List<GameObject> entities = game.getEntities();

// Remover entidade
game.removeEntity(gameObject);

// Adicionar entidade
game.addEntity(gameObject);
```

### Callbacks de Colisão

```java
public class CollisionCallbacks extends IgnisScript {
    
    // Chamado quando outro objeto colide com este
    @Override
    public void onCollision(GameObject other) {
        // Implementar lógica de colisão
    }
    
    // Chamado quando outro objeto entra em um trigger
    @Override
    public void onTrigger(GameObject other) {
        // Implementar lógica de trigger
    }
}
```

---

## Conclusão

O sistema de alertas e colisões do Ignis Engine fornece ferramentas poderosas para:

✅ **Debug em Tempo Real**: Ver exatamente o que está acontecendo na tela do editor
✅ **Detecção de Colisão Robusta**: Sistema de 2 fases (broad + narrow)
✅ **Triggers Flexíveis**: Implementar lógicas de interação sem física
✅ **Callbacks Simples**: `onCollision()` e `onTrigger()` integradas

Use este guia como referência para implementar systems complexos de gameplay com colisões precisas e feedback visual!
