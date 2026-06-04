# 🔥 IGNIS FILE FORMAT
## Especificação Oficial do Arquivo `.ignis`

**Versão:** 1.0  
**Engine:** Ignis 2D Engine (Java)  
**Tipo:** Engine reutilizável com Editor Visual  

---

## 1️⃣ Visão Geral

O arquivo **`.ignis`** é o formato oficial de projeto da **Ignis Engine**.

Ele representa **todo o estado de um projeto**, incluindo:
- Estrutura do projeto
- Cenas
- Entidades de jogo
- Propriedades específicas das entidades
- Assets (imagens, sons, etc.)

📌 O `.ignis` é **portável**, **autocontido** e pode ser carregado em qualquer computador.

---

## 2️⃣ Natureza Técnica do `.ignis`

- O `.ignis` é **tecnicamente um arquivo ZIP**
- A extensão `.ignis` é **exclusiva da engine**
- Pode ser aberto por ferramentas ZIP apenas para debug (não recomendado)

✔ Assets ficam **DENTRO** do arquivo  
✔ Nenhuma dependência de caminhos absolutos  

---

## 3️⃣ Estrutura Interna do Arquivo

```text
MeuProjeto.ignis
│
├── project.json
├── scenes/
│   └── main.scene.json
├── assets/
│   ├── images/
│   │   └── player.png
│   └── sounds/
│       └── jump.wav

```

---

## 4️⃣ `project.json` (Arquivo Raiz)

Responsável por definir informações globais do projeto.

```json
{
  "engineVersion": "0.1.0",
  "projectName": "Meu Projeto Ignis",
  "mainScene": "main.scene.json"
}

```

**Campos obrigatórios:**

* `engineVersion` → versão da engine
* `projectName` → nome do projeto
* `mainScene` → cena inicial

---

## 5️⃣ Arquivo de Cena (.scene.json)

Cada cena representa um conjunto de entidades carregadas simultaneamente.

```json
{
  "sceneName": "MainScene",
  "entities": [
    {
      "id": "player_01",
      "type": "Player",
      "position": { "x": 100, "y": 200 },
      "sprite": "assets/images/player.png",
      "properties": {
        "speed": 4.5,
        "health": 100
      }
    }
  ]
}

```

---

## 6️⃣ Entidades de Jogo (GameObjects)

### Arquitetura adotada

* Herança direta
* `GameObject` é classe abstrata
* Classes concretas: `Player`, `Enemy`, `NPC`, etc.

### Campos padrão

* `id` → identificador único
* `type` → nome da classe concreta
* `position` → posição inicial
* `sprite` → caminho relativo do asset
* `properties` → dados específicos da entidade
* `collider` → configuração do collider (opcional)

### Estrutura do Collider

```json
{
  "id": "player_01",
  "type": "Player",
  "position": { "x": 100, "y": 200 },
  "sprite": "assets/images/player.png",
  "collider": {
    "type": "AABB",
    "mode": "COLLISION",
    "enabled": true,
    "useCCD": false,
    "layer": 0,
    "collisionMask": -1,
    "offsetX": 0.0,
    "offsetY": 0.0,
    "width": 32.0,
    "height": 32.0
  },
  "properties": {
    "speed": 4.5,
    "health": 100
  }
}
```

### Tipos de Collider

| Tipo | Descrição | Propriedades Específicas |
|------|-----------|-------------------------|
| `NONE` | Sem collider | - |
| `AABB` | Retângulo não-rotacionável | `width`, `height`, `offsetX`, `offsetY` |
| `CIRCLE` | Círculo | `radius`, `offsetX`, `offsetY` |
| `POLYGON` | Polígono (SAT) | `vertices[]` (array de pontos x,y) |

### Modos de Colisão

| Modo | Descrição |
|------|-----------|
| `COLLISION` | Resposta física (push-back com MTV) |
| `TRIGGER` | Apenas dispara eventos, sem resposta física |

### Campos do Collider

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `type` | String | Tipo do collider: NONE, AABB, CIRCLE, POLYGON |
| `mode` | String | Modo: COLLISION ou TRIGGER |
| `enabled` | Boolean | Se o collider está ativo |
| `useCCD` | Boolean | Continuous Collision Detection para objetos rápidos |
| `layer` | Integer | Camada de colisão (0-31) |
| `collisionMask` | Integer | Máscara de bits para camadas válidas |
| `offsetX` | Double | Offset X relativo ao centro do objeto |
| `offsetY` | Double | Offset Y relativo ao centro do objeto |
| `width` | Double | Largura (AABB) |
| `height` | Double | Altura (AABB) |
| `radius` | Double | Raio (CIRCLE) |
| `vertices` | Array | Lista de pontos [{x,y}] (POLYGON) |

---

## 7️⃣ Fábrica de Entidades (Obrigatória)

```java
public class EntityFactory {

    public static GameObject create(String type) {
        return switch (type) {
            case "Player" -> new Player();
            case "Enemy" -> new Enemy();
            default -> throw new RuntimeException("Tipo desconhecido: " + type);
        };
    }
}

```

📌 O campo `type` no JSON **DEVE** corresponder ao nome da classe concreta.

---

## 8️⃣ Propriedades Personalizadas

Cada entidade concreta deve implementar:

```java
public abstract class GameObject {
    public abstract void loadProperties(JSONObject props);
    public abstract JSONObject saveProperties();
}

```

✔ Apenas tipos primitivos e String

❌ Nunca serializar objetos gráficos ou de renderização

---

## 9️⃣ Assets

### Regras

* Assets ficam na pasta `/assets`
* Apenas caminhos relativos são salvos
* Suporte a imagens, sons e fontes

### ❌ Proibido serializar:

* `BufferedImage`
* `Graphics`
* Objetos de renderização

---

## 🔄 10️⃣ Fluxo de Salvamento

1. Criar arquivo `.ignis`
2. Gerar `project.json`
3. Serializar cenas
4. Copiar assets para `/assets`
5. Compactar tudo em ZIP

---

## 🔁 11️⃣ Fluxo de Carregamento

1. Extrair `.ignis` para diretório temporário
2. Ler `project.json`
3. Validar `engineVersion`
4. Carregar cena principal
5. Criar entidades via `EntityFactory`
6. Recarregar assets
7. Atualizar Editor Visual

---

## 🧰 12️⃣ Classe Central de IO

```java
public class IgnisProjectIO {

    public static void save(Project project, File ignisFile) {
        // Serialização completa do projeto
    }

    public static Project load(File ignisFile) {
        // Desserialização completa do projeto
        return new Project();
    }
}

```

---

## 🧠 13️⃣ Integração com o Editor Visual

O Editor deve:

* Reconstruir Hierarchy
* Atualizar Inspector
* Renderizar Viewport em tempo real

**Funcionalidades:**

* New Project
* Open Project (`.ignis`)
* Save Project (`.ignis`)

---

## 🔐 14️⃣ Versionamento

O campo `engineVersion` é obrigatório.

Ele garante:

* Compatibilidade futura
* Migração de projetos antigos
* Evolução segura da engine

---

## 🚀 15️⃣ Diretrizes Futuras

* Possível migração para arquitetura Component-Based
* `.ignis` deve permanecer compatível
* Conversores de versão podem ser implementados

---

## ✅ Conclusão

O formato `.ignis`:

* Define a identidade da Ignis Engine
* Transforma o projeto em uma engine profissional
* É portátil, escalável e reutilizável
* Está alinhado com o editor visual atual

📌 **Este documento é a referência oficial do formato .ignis.**

```

Deseja que eu crie uma versão resumida (README) ou adicione exemplos de implementação do `IgnisProjectIO`?

```