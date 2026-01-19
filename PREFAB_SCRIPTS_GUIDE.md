# Guia de Instanciação de Prefabs em Scripts

## Visão Geral

O sistema de prefabs do Ignis Engine permite que você crie objetos dinamicamente durante o jogo através de scripts. Isso é extremamente útil para criar projéteis, inimigos, efeitos visuais e muito mais.

## Como Funciona

### 1. Método `game.instantiatePrefab()`

A classe `Game` fornece um método público que pode ser chamado por qualquer script:

```java
public GameObject instantiatePrefab(String prefabName, double x, double y)
```

**Parâmetros:**
- `prefabName` - Nome da prefab (sem a extensão .prefab.json)
- `x` - Posição X onde o objeto será criado
- `y` - Posição Y onde o objeto será criado

**Retorno:**
- Retorna o `GameObject` instanciado, ou `null` se falhar

### 2. O que acontece internamente

Quando você chama `game.instantiatePrefab()`:

1. O `PrefabManager` localiza o arquivo `.prefab.json` na pasta `project/prefabs/`
2. Deserializa o JSON e cria uma cópia do objeto com todas as propriedades
3. Gera um novo ID único para o objeto
4. Define a posição especificada (x, y)
5. Se o jogo está rodando (PLAYING), inicializa e executa os scripts do objeto
6. Adiciona o objeto à lista de entidades do jogo
7. Retorna a referência do objeto criado

### 3. Acesso no Script

Todo `IgnisScript` tem acesso à instância do `Game` através da variável `game`. Você pode chamar o método diretamente:

```java
GameObject newObject = game.instantiatePrefab("NomeDaPrefab", x, y);
```

---

## Exemplo Completo: Sistema de Arma e Projéteis

### Passo 1: Criar a Prefab do Projétil

1. No editor, crie um objeto Square pequeno (por exemplo, 10x10)
2. Renomeie para "Projectile"
3. Posicione em algum lugar visível
4. Adicione um script chamado "ProjectileMovement.java" (veja código abaixo)
5. Clique direito no objeto → **Save as Prefab** → Nome: "Projectile"

### Passo 2: Criar o Script do Projétil

**ProjectileMovement.java:**

```java
import com.ignis.core.*;

/**
 * Script que move um projétil em uma direção e o destrói
 * após sair da tela ou após um tempo limite.
 */
public class ProjectileMovement extends IgnisScript {
    
    // Velocidade do projétil (pixels por frame)
    public double speed = 5.0;
    
    // Direção: 1 = direita, -1 = esquerda
    public double direction = 1.0;
    
    // Tempo de vida máximo (em frames, 60 fps = 1 segundo)
    public int lifetime = 300; // 5 segundos
    
    private int frameCount = 0;
    
    @Override
    public void start() {
        System.out.println("Projétil disparado!");
    }
    
    @Override
    public void update() {
        // Mover o projétil
        transform.x += speed * direction;
        
        // Incrementar contador de frames
        frameCount++;
        
        // Verificar se saiu da tela
        if (transform.x < -50 || transform.x > 850 || 
            transform.y < -50 || transform.y > 650) {
            destroyObject();
            return;
        }
        
        // Verificar tempo de vida
        if (frameCount >= lifetime) {
            destroyObject();
        }
    }
    
    /**
     * Destrói este objeto removendo-o do jogo
     */
    private void destroyObject() {
        game.removeEntity(gameObject);
        System.out.println("Projétil destruído");
    }
}
```

### Passo 3: Criar o Script da Arma

**WeaponSystem.java:**

```java
import com.ignis.core.*;

/**
 * Sistema de arma que dispara projéteis usando prefabs.
 * Pressione ESPAÇO para disparar.
 */
public class WeaponSystem extends IgnisScript {
    
    // Nome da prefab do projétil
    public String projectilePrefabName = "Projectile";
    
    // Velocidade do projétil
    public double projectileSpeed = 8.0;
    
    // Direção de disparo (1 = direita, -1 = esquerda)
    public double fireDirection = 1.0;
    
    // Offset para posição de spawn (relativo ao objeto)
    public double spawnOffsetX = 30.0;
    public double spawnOffsetY = 0.0;
    
    // Cooldown entre disparos (em frames)
    public int fireCooldown = 15; // ~0.25 segundos a 60fps
    
    private int cooldownTimer = 0;
    
    @Override
    public void start() {
        System.out.println("Sistema de arma inicializado!");
        System.out.println("Pressione ESPAÇO para disparar");
    }
    
    @Override
    public void update() {
        // Reduzir cooldown
        if (cooldownTimer > 0) {
            cooldownTimer--;
        }
        
        // Verificar se o jogador pressionou espaço
        if (Input.isKeyPressed(java.awt.event.KeyEvent.VK_SPACE)) {
            if (cooldownTimer == 0) {
                fire();
                cooldownTimer = fireCooldown;
            }
        }
    }
    
    /**
     * Dispara um projétil
     */
    private void fire() {
        // Calcular posição de spawn
        double spawnX = transform.x + (spawnOffsetX * fireDirection);
        double spawnY = transform.y + spawnOffsetY;
        
        // Instanciar a prefab do projétil
        GameObject projectile = game.instantiatePrefab(
            projectilePrefabName, 
            spawnX, 
            spawnY
        );
        
        // Se o projétil foi criado com sucesso, configurar sua velocidade
        if (projectile != null) {
            // Encontrar o script ProjectileMovement no projétil
            for (IgnisScript script : projectile.getScripts()) {
                if (script.getClass().getSimpleName().equals("ProjectileMovement")) {
                    // Usar reflexão para acessar os campos públicos
                    try {
                        java.lang.reflect.Field speedField = script.getClass().getField("speed");
                        java.lang.reflect.Field directionField = script.getClass().getField("direction");
                        
                        speedField.set(script, projectileSpeed);
                        directionField.set(script, fireDirection);
                        
                    } catch (Exception e) {
                        System.err.println("Erro ao configurar projétil: " + e.getMessage());
                    }
                }
            }
            
            System.out.println("Projétil disparado em (" + spawnX + ", " + spawnY + ")");
        } else {
            System.err.println("Falha ao disparar projétil!");
        }
    }
}
```

### Passo 4: Usar o Sistema

1. Crie ou selecione um objeto (por exemplo, o Player)
2. Adicione o script "WeaponSystem" ao objeto
3. No Inspector, você verá as variáveis públicas:
   - `projectilePrefabName`: "Projectile"
   - `projectileSpeed`: 8.0
   - `fireDirection`: 1.0 (direita) ou -1.0 (esquerda)
   - `spawnOffsetX`: 30.0
   - `spawnOffsetY`: 0.0
   - `fireCooldown`: 15
4. Pressione Play
5. Pressione ESPAÇO para disparar projéteis!

---

## Exemplos Adicionais

### Exemplo 2: Spawner de Inimigos

```java
import com.ignis.core.*;

/**
 * Spawn inimigos periodicamente em posições aleatórias
 */
public class EnemySpawner extends IgnisScript {
    
    public String enemyPrefabName = "Enemy";
    public int spawnInterval = 120; // 2 segundos a 60fps
    public int maxEnemies = 10;
    
    private int timer = 0;
    private int currentEnemyCount = 0;
    
    @Override
    public void update() {
        timer++;
        
        if (timer >= spawnInterval && currentEnemyCount < maxEnemies) {
            // Posição aleatória
            double randomX = Math.random() * 800;
            double randomY = Math.random() * 600;
            
            GameObject enemy = game.instantiatePrefab(enemyPrefabName, randomX, randomY);
            
            if (enemy != null) {
                currentEnemyCount++;
                System.out.println("Inimigo spawnou! Total: " + currentEnemyCount);
            }
            
            timer = 0;
        }
    }
}
```

### Exemplo 3: Sistema de Partículas Simples

```java
import com.ignis.core.*;

/**
 * Cria partículas em explosão quando o objeto é criado
 */
public class ParticleExplosion extends IgnisScript {
    
    public String particlePrefabName = "Particle";
    public int particleCount = 12;
    public double particleSpeed = 5.0;
    
    @Override
    public void start() {
        createExplosion();
    }
    
    private void createExplosion() {
        // Criar partículas em todas as direções
        double angleStep = 360.0 / particleCount;
        
        for (int i = 0; i < particleCount; i++) {
            double angle = Math.toRadians(i * angleStep);
            
            GameObject particle = game.instantiatePrefab(
                particlePrefabName,
                transform.x,
                transform.y
            );
            
            if (particle != null) {
                // Configurar velocidade da partícula usando um script
                // (assumindo que a partícula tem um script com campos vx e vy)
                for (IgnisScript script : particle.getScripts()) {
                    try {
                        java.lang.reflect.Field vxField = script.getClass().getField("vx");
                        java.lang.reflect.Field vyField = script.getClass().getField("vy");
                        
                        vxField.set(script, Math.cos(angle) * particleSpeed);
                        vyField.set(script, Math.sin(angle) * particleSpeed);
                    } catch (Exception e) {
                        // Campo não existe
                    }
                }
            }
        }
    }
}
```

---

## Método Auxiliar: Remover Objetos

Para destruir objetos criados dinamicamente, você pode usar:

```java
game.removeEntity(gameObject); // Remove o próprio objeto
game.removeEntity(otherObject); // Remove outro objeto
```

**Nota:** Certifique-se de verificar se o método `removeEntity` existe no Game.java. Se não, você precisará adicioná-lo.

---

## Boas Práticas

1. **Verificar se a prefab existe:** Sempre verifique se `instantiatePrefab()` retorna `null`
2. **Limpar objetos:** Destrua objetos que saem da tela para economizar memória
3. **Use cooldowns:** Evite criar muitos objetos por frame
4. **Nomes consistentes:** Use nomes de prefabs consistentes e documentados
5. **Configuração via variáveis públicas:** Exponha configurações importantes como variáveis públicas

---

## Fluxo Técnico Completo

```
Script chama game.instantiatePrefab("Projectile", x, y)
    ↓
Game.java recebe a chamada
    ↓
Game chama prefabManager.instantiatePrefab("Projectile", x, y)
    ↓
PrefabManager lê project/prefabs/Projectile.prefab.json
    ↓
Deserializa JSON → Cria GameObject com todas as propriedades
    ↓
Define posição (x, y) e gera novo ID único
    ↓
PrefabManager retorna GameObject para Game
    ↓
Game adiciona objeto à lista entities
    ↓
Se GameState == PLAYING: Inicializa scripts do objeto
    ↓
Objeto aparece no jogo e scripts começam a executar
```

---

## Conclusão

O sistema de instanciação de prefabs permite criar jogos dinâmicos com objetos gerados em tempo de execução. Use-o para:
- ✅ Sistemas de disparo/projéteis
- ✅ Spawn de inimigos
- ✅ Efeitos visuais e partículas
- ✅ Power-ups e itens coletáveis
- ✅ Qualquer objeto que precise ser criado durante o jogo

Experimente criar suas próprias prefabs e sistemas!
