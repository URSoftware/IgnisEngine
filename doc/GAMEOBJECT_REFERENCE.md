# Referência da Classe GameObject e Entidades (GameObject & Entity Reference)

> Documentação técnica oficial detalhando a hierarquia, componentes, ciclo de vida, serialização e tipos de entidades na IgnisEngine.

---

## 1. A Classe Base `GameObject`

Todas as entidades presentes no mundo físico do jogo herdam diretamente da classe abstrata **`GameObject`** (`com.ignis.core.GameObject`). 

A engine adota um modelo de **Herança com Hibridismo de Componentes**, o que significa que o comportamento estático e estrutura básica de desenho vêm de classes herdadas, enquanto comportamentos customizados e extensões lógicas vêm de componentes (Scripts, Animadores, Som, Colisores) anexados a eles.

### Propriedades Comuns a Todos os GameObjects:
- **`id` (String):** UUID único de identificação gerado na criação do objeto.
- **`name` (String):** Nome amigável de exibição (ex: "Player", "Plataforma").
- **`x`, `y` (double):** Posição espacial no mundo do jogo (coordenadas bidimensionais).
- **`width`, `height` (double):** Largura e altura da entidade em pixels.
- **`rotation` (double):** Rotação em graus (0 a 360).
- **`spritePath` (String):** Caminho relativo da imagem/textura de exibição no projeto (`assets/images/...`).
- **`visible` (boolean):** Define se a entidade será desenhada na viewport de cena.
- **`nameColor` (Color):** Cor do nome do objeto exibida na Hierarchy do editor.

---

## 2. Sistemas e Componentes Internos

Cada `GameObject` pode carregar e orquestrar os seguintes subsistemas:

### A. Scripts (`List<IgnisScript>`)
Comportamento dinâmico programado. Scripts escritos em Java anexados à entidade que executam código em frames contínuos e respondem a inputs e eventos de colisão.

### B. Animator (`com.ignis.animation.Animator`)
Gerenciador de animações 2D baseado em spritesheets e intervalos de tempo (keyframes), alterando automaticamente a textura de exibição do objeto de acordo com o estado do jogo.

### C. MusicPath (`com.ignis.core.MusicPath`)
Vincula efeitos sonoros ou trilha musical a uma entidade específica.

### D. Collider (`com.ignis.core.ui` / `com.ignis.core`)
Configuração física do colisor. A física detecta e processa colisões baseado em três geometrias de colisores:
- **`AABB`:** Caixa retangular delimitadora alinhada aos eixos (não rotacionável).
- **`CIRCLE`:** Colisor circular baseado em raio.
- **`POLYGON`:** Colisor poligonal customizado composto por múltiplos vértices arbitrários (resolvido via Teorema dos Eixos Separadores - SAT).

---

## 3. Tipos Concretos de Entidades

A engine implementa as seguintes classes concretas de objetos de jogo:

| Tipo de Entidade | Classe Java | Descrição e Comportamento de Renderização |
|---|---|---|
| **Player** | `Player` | Entidade principal controlável. Pode possuir controle de física e inputs configurados. Desenha a textura do sprite ativo. |
| **Square** | `Square` | Forma geométrica quadrada básica. Utilizada para prototipagem rápida de colisores ou obstáculos. |
| **Circle** | `Circle` | Forma geométrica circular básica. |
| **Triangle** | `Triangle` | Forma geométrica triangular básica. |
| **Pentagon** | `Pentagon` | Forma geométrica de pentágono de 5 lados. |
| **Star** | `Star` | Forma geométrica de estrela de múltiplos vértices. |
| **MergedShape** | `MergedShape` | Entidade avançada capaz de mesclar e combinar múltiplos polígonos geométricos em uma única malha de colisão física complexa. |
| **Camera** | `Camera` | Entidade invisível que controla a posição de visualização, rotação, zoom e limites da viewport da cena. |

---

## 4. O Ciclo de Vida da Entidade (Lifecycle)

Durante o gameplay (modo `PLAYING`), a cada frame do game loop, a entidade passa pelas seguintes fases:

```text
       ┌──────────────┐
       │   `tick()`   │ ──> Atualização física da posição
       └──────┬───────┘
              ▼
    ┌──────────────────┐
    │ `tickScripts()`  │ ──> Executa start() e update() dos IgnisScripts
    └──────┬───────────┘
              ▼
    ┌──────────────────┐
    │`notifyCollision()`│ ──> Dispara callbacks onCollisionEnter/Exit
    └──────┬───────────┘
              ▼
     ┌────────────────┐
     │   `render()`   │ ──> Graphics2D desenha sprites/geometrias
     └────────────────┘
```

1. **`tick()`:** Processa lógica base de física, gravidade, inércia e atualizações de transformação (`Transform`).
2. **`tickScripts(double deltaTime)`:** Executa os métodos `start()` (no primeiro frame) e `update()` de todas as classes `IgnisScript` anexadas.
3. **`notifyCollision(CollisionEvent event)`:** Dispara callbacks nos scripts caso ocorra intersecção de colisores.
4. **`render(Graphics2D g)`:** O motor desenha o sprite ou geometria vetorial básica na tela caso `visible == true`.

---

## 5. Fábrica de Entidades (`EntityFactory`)

A desserialização de arquivos `.ignis` para restaurar os objetos na cena depende inteiramente da **`EntityFactory`** (`com.ignis.core.EntityFactory`). 
Ela expõe métodos estáticos para criar entidades dinamicamente por meio de strings de texto que representam o tipo:

```java
// Exemplo de criação dinâmica na carga de cenas
String entityType = jsonObject.getString("type");
GameObject obj = EntityFactory.create(entityType);
```

> [!IMPORTANT]
> Toda nova entidade criada no código da engine deve obrigatoriamente ser registrada nos métodos `create`, `isSupported` e `getSupportedTypes` da `EntityFactory.java` para que o editor visual possa listá-la e o carregador de arquivos JSON saiba instanciá-la.

---

## 6. Serialização de Propriedades (`loadProperties` / `saveProperties`)

Toda propriedade de um `GameObject` deve ser salva ao fechar ou salvar o projeto e carregada ao abrir o editor. O fluxo de persistência funciona de forma manual para propriedades base e automática (reflexão) para scripts:

- **`saveProperties()`:** Cada classe de entidade concreta sobrescreve este método para converter seus campos específicos (como raio do círculo, vértices do polígono, velocidade do player) em um objeto `JSONObject`.
- **`loadProperties(JSONObject json)`:** Método invocado para ler o objeto JSON e preencher os valores nos campos do objeto recém-criado.
