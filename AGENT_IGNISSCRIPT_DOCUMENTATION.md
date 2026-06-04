# Documentação Aprimorada do AGENT Mode - IgnisScript API

## Resumo das Melhorias

O prompt do AGENT mode foi significativamente **expandido e refinado** para fornecer ao Gemini documentação precisa e detalhada sobre a API IgnisScript. Isso resolve os 5 erros que foram cometidos no script Player.java anterior.

---

## Estrutura do IgnisScript - Template Padrão

Todo script deve seguir **EXATAMENTE** esta estrutura:

```java
import com.ignis.core.IgnisScript;

public class Test extends IgnisScript {

    @Override
    public void start() { // Called once when initializing world simulation

    }

    @Override
    public void tick() { // Called once every frame
        
    }
}
```

### Explicação de cada elemento:

| Elemento | Descrição |
|----------|-----------|
| `import com.ignis.core.IgnisScript;` | **ÚNICO import obrigatório** - qualquer outro import deve ser de `com.ignis.core.*` |
| `extends IgnisScript` | **NUNCA** use `extends Script` ou outra classe |
| `@Override public void start()` | Chamado uma única vez no início da simulação - para inicialização |
| `@Override public void tick()` | Chamado a cada frame - onde colocar a lógica do jogo |
| Sem `package` | Scripts **NUNCA** devem ter declaração de package |

---

## 5 Erros Comuns Evitados

### ❌ ERRO 1: Classe Base Incorreta
**Antes (Errado):**
```java
public class Player extends Script {  // ❌ ERRADO
```

**Depois (Correto):**
```java
public class Player extends IgnisScript {  // ✅ CORRETO
```

**Por quê?** `Script` não existe. A classe base correta é `IgnisScript`.

---

### ❌ ERRO 2: Imports Errados
**Antes (Errado):**
```java
import ignis.*;  // ❌ ERRADO - pacotes não existem
```

**Depois (Correto):**
```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
```

**Por quê?** Os pacotes `ignis.*` não existem. Use sempre `com.ignis.core.*`.

---

### ❌ ERRO 3: Nome de Método Errado
**Antes (Errado):**
```java
public void update() {  // ❌ ERRADO
    // código executado a cada frame
}
```

**Depois (Correto):**
```java
@Override
public void tick() {  // ✅ CORRETO
    // código executado a cada frame
}
```

**Por quê?** O método que executa a cada frame é `tick()`, não `update()`. Deve ser `@Override`.

---

### ❌ ERRO 4: Acesso Incorreto ao Transform
**Antes (Errado):**
```java
gameObject.getTransform().translate(moveX * speed, 0);  // ❌ ERRADO
transform.x += moveX * speed;  // ❌ MÉTODO translate() NÃO EXISTE
```

**Depois (Correto):**
```java
transform.x += moveX * speed;     // ✅ CORRETO - adiciona diretamente
transform.y += moveY * speed;     // ✅ PARA MOVIMENTO EM Y
transform.rotation += rotSpeed;   // ✅ PARA ROTAÇÃO
```

**Por quê?** 
- `Transform` em IgnisScript é um campo `protected`, acesse diretamente
- `transform.translate()` não existe
- Use atribuição direta: `transform.x += valor`

---

### ❌ ERRO 5: Sistema de Input Incorreto
**Antes (Errado):**
```java
if (Input.isKeyDown(KeyEvent.VK_W)) {  // ❌ ERRADO - não é estático
```

**Depois (Correto):**
```java
if (Input.getInstance().isKeyPressed(KeyEvent.VK_W)) {  // ✅ CORRETO
```

**Por quê?** `Input` segue o padrão **Singleton**. Use `getInstance()` para obter a instância.

---

## Exemplo Completo: Player Script Correto

```java
import com.ignis.core.IgnisScript;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;

public class Player extends IgnisScript {
    private float speed = 5.0f;

    @Override
    public void start() {
        // Inicialização - pode ficar vazio se não precisar
    }

    @Override
    public void tick() {
        float moveX = 0;
        float moveY = 0;

        // Verificar entrada de teclado
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_W)) {
            moveY -= speed;  // Cima
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_S)) {
            moveY += speed;  // Baixo
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_A)) {
            moveX -= speed;  // Esquerda
        }
        if (Input.getInstance().isKeyPressed(KeyEvent.VK_D)) {
            moveX += speed;  // Direita
        }

        // Aplicar movimento ao transform
        transform.x += moveX;
        transform.y += moveY;
    }
}
```

---

## Imports Disponíveis

| Import | Uso |
|--------|-----|
| `com.ignis.core.IgnisScript` | Base para todos os scripts (obrigatório) |
| `com.ignis.core.Input` | Sistema de entrada (teclado/mouse) |
| `com.ignis.core.Game` | Acesso a dados do jogo |
| `com.ignis.core.GameObject` | Representação de objetos no jogo |
| `com.ignis.core.Transform` | Posição, rotação, escala |
| `java.awt.event.KeyEvent` | Constantes de teclado (VK_W, VK_A, etc.) |

---

## Onde Colocar os Scripts

Scripts devem ser salvos em:
```
projects/Game 01/project/scripts/
```

Exemplos de caminhos válidos:
- `projects/Game 01/project/scripts/Player.java` ✅
- `projects/Game 01/project/scripts/Enemy.java` ✅
- `projects/Game 01/project/scripts/items/Coin.java` ✅

**Nunca** devem ter uma declaração `package` no início do arquivo.

---

## Checklist para o Gemini

Quando criando um script, o Gemini deve verificar:

- [ ] Classe estende `IgnisScript` (não `Script`)
- [ ] Arquivo começa com `import com.ignis.core.IgnisScript;` (sem package)
- [ ] Tem método `start()` com `@Override`
- [ ] Tem método `tick()` com `@Override`
- [ ] Usa `transform.x`, `transform.y` (não getTransform().translate())
- [ ] Usa `Input.getInstance().isKeyPressed()` (não Input.isKeyDown())
- [ ] Arquivo é `.java` (nunca `.ignis`)
- [ ] Código é compilável e sem erros de sintaxe

---

## Integração no AGENT Mode

O prompt do AGENT mode agora inclui:

1. **Template básico** - exemplo de estrutura mínima
2. **Regras críticas** - extends, imports, métodos
3. **Exemplos de Transform** - como acessar e modificar
4. **Exemplos de Input** - como verificar teclado
5. **Lista de erros comuns** com before/after
6. **Imports disponíveis** documentados
7. **Requerimentos críticos** enfatizados

Isso garante que o Gemini tenha **contexto completo** antes de gerar código.

---

## Resultado

Com essa documentação aprimorada, o Gemini agora será capaz de:

✅ Gerar scripts IgnisScript corretos na primeira tentativa  
✅ Evitar os 5 erros que ocorreram no Player.java anterior  
✅ Usar padrões corretos de API (singleton, transform, input)  
✅ Criar código compilável sem necessidade de correções  

