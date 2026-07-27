---
name: ignis-component-creator
description: Guia completo e procedimento padronizado para criação, serialização genérica, registro no Editor JavaFX e integração de novos componentes nativos no IgnisEngine. Use esta skill sempre que for criar ou refatorar componentes do motor.
---

# Skill: Criação de Novos Componentes no IgnisEngine

Esta skill estabelece o fluxo arquitetural e o passo a passo para criar, serializar, registrar no editor JavaFX e testar novos componentes nativos no **IgnisEngine**.

---

## 1. Visão Geral da Arquitetura Entidade-Componente (EC)

No IgnisEngine, a arquitetura de jogo segue o padrão **Entidade-Componente (EC)**:
- **`GameObject`**: A entidade container de dados e transformações (posição, rotação, escala, tags e camadas).
- **`Component` (`com.ignis.core.Component`)**: Classe abstrata base para peças modulares de comportamento ou dados anexadas a um `GameObject`.
- **Diferenciação Nativo vs. Script**:
  - Componentes nativos residem no pacote `com.ignis.*` (ex: `com.ignis.core`). O método `GameObject.isNativeComponent(component)` valida se o pacote inicia com `com.ignis.`.
  - Componentes nativos não são poluídos na lista `scriptNames` do objeto e possuem tratamento nativo no editor e no runtime.
  - Scripts de usuário estendem `IgnisScript` (que por sua vez estende `Component`) e vivem no repositório de scripts do projeto.

---

## 2. Ciclo de Vida do Componente

Todo componente implementa ou herda quatro métodos principais de ciclo de vida:

```java
public abstract class Component {
    public GameObject gameObject;

    // Chamado imediatamente ao anexar o componente ao GameObject (go.addComponent(comp))
    public void awake() {}

    // Chamado antes do primeiro frame de simulação
    public void start() {}

    // Chamado a cada frame de simulação com o delta time decorrido (em segundos)
    public void update(float deltaTime) {}

    // Chamado ao remover o componente do GameObject (go.removeComponent(comp))
    public void onDetach() {}
}
```

### Regras do Ciclo de Vida:
1. **`awake()`**: Registre receptores de sinal (`SignalReceiver`) no `SceneDispatcher` do jogo ou inicialize coleções internas transientes.
2. **`update(float deltaTime)`**: Executa lógica contínua de movimento, simulação de física ou atualização de estado durante o runtime.
3. **`onDetach()`**: Limpeza obrigatória de escutadores de eventos (`disconnect`), desregistro do sistema de colisão/física ou descarte de recursos. Evita vazamentos de memória e callbacks orfãos.

---

## 3. Serialização Genérica (`@Serialize`)

O IgnisEngine utiliza reflexão para salvar e carregar propriedades de componentes sem acoplamento hardcoded com a `Scene`.

1. **Campos Escalares e Primitivos**:
   Basta anotar o campo com `@Serialize`:
   ```java
   @Serialize
   private float speed = 100.0f;

   @Serialize
   private boolean isActive = true;
   ```
   Os métodos padrão herdados de `Component` (`saveProperties()` e `loadProperties(...)`) usam o `ScriptSerializationHelper` para salvar/restaurar estes campos no JSON da cena (`.ignis`).

2. **Propriedades Complexas (Estruturas Aninhadas)**:
   Se o componente contiver estruturas não-escalares (ex: a árvore de UI do `CanvasComponent` ou listas de nós), sobrescreva os métodos de serialização:
   ```java
   @Override
   public JSONObject saveProperties() {
       JSONObject props = super.saveProperties();
       props.put("customData", myComplexData.toJSON());
       return props;
   }

   @Override
   public void loadProperties(JSONObject props, ScriptSerializationHelper.GameObjectResolver resolver) {
       super.loadProperties(props, resolver);
       if (props.has("customData")) {
           this.myComplexData = CustomData.fromJSON(props.getJSONObject("customData"));
       }
   }
   ```

---

## 4. Passo a Passo: Criando um Novo Componente Nativo

Para criar e integrar um novo componente (exemplo: `AudioComponent` ou `InventoryComponent`):

### Passo 1: Criar a Classe do Componente
Crie o arquivo em `src/com/ignis/core/YourComponent.java`:

```java
package com.ignis.core;

import org.json.JSONObject;

public class YourComponent extends Component {

    @Serialize
    private float intensity = 1.0f;

    public YourComponent() {
    }

    @Override
    public void awake() {
        // Inicializações ou conexões com eventos do SceneDispatcher
    }

    @Override
    public void update(float deltaTime) {
        // Lógica por frame
    }

    @Override
    public void onDetach() {
        // Limpeza de eventos e recursos
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }
}
```

---

### Passo 2: Registrar no Editor JavaFX (`IgnisEditorApp.java`)

Para que o componente apareça no diálogo **Adicionar Componente...** e na hierarquia do editor JavaFX:

1. **Atualizar a lista de disponíveis em `openAddComponentDialog()`**:
   Localize o método em `src/com/ignis/editor/fx/IgnisEditorApp.java` e adicione a verificação:
   ```java
   if (go.getComponent(YourComponent.class) == null) {
       available.add("YourComponent");
   }
   ```

2. **Adicionar o tratamento de instanciação e Undo/Redo**:
   No mesmo método, no bloco de seleção do resultado do diálogo:
   ```java
   else if (selected.equals("YourComponent")) nativeComp = new YourComponent();
   ```

3. **Atualizar a montagem da árvore de hierarquia (`buildHierarchyTree`)**:
   Adicione a inclusão do sub-item para exibição correta e prevenção de duplicidade:
   ```java
   if (go.getComponent(YourComponent.class) != null) {
       goItem.getChildren().add(new TreeItem<>("YourComponent"));
   }
   ```
   E adicione `comp instanceof YourComponent` na checagem de exceção de componentes nativos da mesma função.

---

### Passo 3: Testes Unitários e Validação Clean Code

Conforme as regras do repositório, crie um teste unitário correspondente (ex: `src/com/ignis/core/YourComponentTest.java` ou em `test/`):
- Teste a adição do componente ao `GameObject`.
- Teste se `saveProperties()` e `loadProperties()` persistem os valores dos campos `@Serialize`.
- Teste se `onDetach()` limpa recursos e conexões corretamente.
- Execute `mvn test` para garantir que a suíte de testes passou sem quebrar a regressão.

---

### Passo 4: Documentar no Vault

Crie uma nota explicativa em `doc/30_libraries/ignisengine/YourComponent.md` contendo:
1. Propriedades principais (`@Serialize`).
2. Exemplo de uso via código (Scripting).
3. Links para `doc/00_MOC.md`.
