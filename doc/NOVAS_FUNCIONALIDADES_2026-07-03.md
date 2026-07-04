# Detalhamento das Novas Funcionalidades - 03/07/2026

Este documento descreve detalhadamente as melhorias e novos recursos implementados no motor Ignis Engine e em seus projetos na data de 03/07/2026 por IA, sob a autoria exclusiva de ThyagoToledo.

---

## 1. Padrao de Arquitetura Entidade-Componente (EC)

### Proposito
Estruturar o nucleo do motor IgnisEngine seguindo a arquitetura modular Entidade-Componente de forma limpa, performatica e totalmente integrada com o sistema de scripting existente, evitando a duplicacao de codigo ou a quebra de compatibilidade dos projetos.

### O que faz
- **Classe Abstrata Component (`Component.java`):** Define a base modular de dados e comportamento com o campo `public GameObject gameObject` e os metodos do ciclo de vida `awake()`, `start()` e `update(float deltaTime)`.
- **Herdabilidade de IgnisScript:** O nucleo de scripts `IgnisScript` passa a herdar diretamente de `Component`, recebendo a referencia ao dono e integrando a execucao do ciclo de vida modular.
- **Gerenciador de Ciclo de Vida:** Introduzido um estado interno `awoken` e o metodo `callAwake()` para garantir que o metodo `awake()` seja acionado exatamente uma unica vez (no carregamento ou quando o componente e acoplado). O metodo `internalTick()` foi aprimorado para rodar `awake()`, `start()`, `tick()` e `update(deltaTime)` sequencialmente.

---

## 2. Metodos Genericos e de Renderizacao no GameObject

### Proposito
Transformar o `GameObject` em um container leve de componentes, provendo metodos de consulta, adicao e delegacao de desenho.

### O que faz
- **Metodo Generico `getComponent`:** Busca e retorna o primeiro componente que corresponda a classe informada por Generics (`public <T extends Component> T getComponent(Class<T> componentClass)`).
- **Metodo `addComponent`:** Permite acoplar componentes dinamicamente a entidade.
- **Ciclo `update`:** Propaga o deltaTime da simulacao para todos os componentes anexados ativos.
- **Metodo `renderSpriteComponent`:** Metodo auxiliar que intercepta o fluxo de renderizacao principal do motor e delega o desenho para o `SpriteComponent` se este estiver acoplado ao objeto.

---

## 3. Componentes Especializados (SpriteComponent e InputComponent)

### Proposito
Desacoplar a logica visual e de movimentacao do nucleo do motor em pecas modulares independentes.

### O que faz
- **SpriteComponent (`SpriteComponent.java`):** Isola a renderizacao. Desenha a textura associada respeitando posicao, rotacao, escala e espelhamento do GameObject pai. Possui um fallback automatico implementado em `awake()` e `draw()` para carregar a textura a partir de `gameObject.getSpritePath()` caso a variavel `texture` esteja nula, garantindo retrocompatibilidade com cenas existentes e operacoes do MCP.
- **InputComponent (`InputComponent.java`):** Trata a entrada do teclado (W, A, S, D) e desloca o objeto pai suavemente baseado no delta time e velocidade parametrizada.
- **Textura de Alta Performance (`Texture2D.java`):** Wrapper de textura que carrega e armazena BufferedImage usando o resolvedor de assets do motor.
- **Integracao de Renderizacao:** Atualizado o metodo `render(Graphics g)` em `Square`, `Circle`, `Triangle`, `Star`, `Pentagon`, `Player` e `MergedShape` para desviar a renderizacao caso exista um `SpriteComponent` anexado, permitindo o desacoplamento de arte geometrica.

---

## 4. Serializacao e Inspector Reativo no Editor

### Proposito
Expor e persistir as propriedades dos novos componentes diretamente no editor JavaFX e nos arquivos de cena do projeto.

### O que faz
- **Serializacao de Texturas:** A classe `ScriptSerializationHelper` foi aprimorada para suportar a serializacao do tipo `Texture2D` salvando o caminho relativo no JSON e recarregando a textura a partir dele.
- **Seletor de Textura no Inspector:** O criador de campos no `IgnisEditorApp` foi estendido com suporte para `Texture2D.class`. Exibe uma linha com o caminho da imagem e botoes para escolher arquivos (`...`) e limpar o asset (`X`).
- **Compatibilidade do Compilador:** Ajustada a ambiguidade de tipo em `Editor.java` importando explicitamente `java.awt.Component`.
