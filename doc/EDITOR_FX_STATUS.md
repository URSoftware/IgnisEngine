# Status do Editor JavaFX (JavaFX Editor Status)

> Visão geral do progresso de desenvolvimento e status da migração da interface visual do editor (Swing → JavaFX) no IgnisEngine.

---

## 1. Visão Geral da Migração

A migração da interface de usuário (UI) do editor visual do IgnisEngine foi dividida em etapas incrementais na branch `main` com o objetivo de substituir a interface clássica em Swing por uma casca nativa em **JavaFX 17**, sem a necessidade de reescrever o núcleo do motor (core).

### Status de Progresso das Fases:
- **`F0` - Infraestrutura:** Adição de dependências JavaFX ao `pom.xml`, configuração do `javafx-maven-plugin` e estrutura de pacotes. **[CONCLUÍDO]**
- **`F1` - Casca e Ponte de Render:** Substituição do JFrame principal, inicialização da thread do JavaFX e criação do viewport de renderização utilizando `SwingFXUtils`. **[CONCLUÍDO]**
- **`F2` - Painéis Principais:** Criação dos painéis nativos de Hierarchy (TreeView nativo), Inspector (formulários reativos de propriedades) e Asset Browser (navegador de arquivos). **[CONCLUÍDO]**
- **`F3` - Janelas de Ferramentas:** Migração e conexão de sub-editores integrados aos menus principais. **[CONCLUÍDO]**
- **`F3.5` - Roteamento de Entrada e Gerenciamento:** Tela de boas-vindas / recentes, operações de arquivo (salvar/abrir/fechar), reordenação e manipulação de entidades e captura fluida de eventos de teclado/mouse para o jogo. **[CONCLUÍDO]**
- **`F4` - Tema CSS e Limpeza (Próximo Passo):** Criação de temas escuros/claros unificados via CSS modular, aplicação de SplitPanes flexíveis na área de trabalho e remoção de componentes órfãos da ponte de renderização antiga. **[PLANEJADO / EM ANDAMENTO]**

---

## 2. Paridade de Features (Tabela de Status)

A tabela abaixo exibe a paridade atual do Editor JavaFX em relação ao Editor Swing clássico:

| Categoria | Funcionalidade | Status no JavaFX | Observação |
|---|---|---|---|
| **Cenas e Projetos** | FxProjectStartupDialog (Novos/Recentes) | **Funcional** | Tela inicial moderna de gerenciamento de projetos. |
| | Salvar, Abrir e Salvar Como | **Funcional** | Integração total com `IgnisProjectIO`. |
| **Hierarquia** | Hierarchy TreeView | **Funcional** | Exibição de nós de cena e seleção ativa. |
| | Criar, Duplicar, Deletar e Renomear | **Funcional** | Ações via menus de contexto (ContextMenu). |
| | Reordenar com Drag-and-Drop | **Pendente** | Ordenação manual de camadas arrastando nós. |
| **Inspetor** | Edição de Transformações e Propriedades | **Funcional** | Vinculado a campos de texto numéricos reativos. |
| | Listagem e Vinculação de Scripts | **Parcial** | Listagem de scripts ativa; vinculação via menu de contexto. |
| **Sub-Editores** | FxCodeEditor (Editor de Código) | **Funcional** | Code editor nativo com destaque e autocomplete. |
| | FxImageEditor (Editor de Sprites) | **Funcional** | Paint canvas nativo em JavaFX com camadas. |
| | FxAudioEditor (DAW) | **Funcional** | Timeline, equalizador e forma de onda nativos. |
| | FxAnimationEditor (Animações) | **Funcional** | Editor de quadros nativo JavaFX. |
| | FxCommunityWindow (Marketplace) | **Funcional** | Integração e download 1-click de plugins. |
| **Simulação** | Play / Stop / Pause | **Funcional** | Botões e mapeamento de estados da Game Thread. |
| | Roteamento de Teclado e Mouse | **Funcional** | Eventos capturados da janela JavaFX e enviados à Game Thread. |

---

## 3. A Ponte de Renderização (`SwingFXUtils`)

A cena do jogo é renderizada no motor clássico `AWT/Graphics2D` em uma thread de simulação em segundo plano. 
Para exibir essa viewport de forma fluida no editor moderno JavaFX, utiliza-se uma ponte de buffers:

1. O loop principal do jogo desenha as entidades e o grid do editor em uma `BufferedImage` na memória física do computador.
2. A thread de UI do JavaFX executa periodicamente um ciclo de atualização na classe `IgnisEditorApp` que captura a `BufferedImage` do motor de jogo.
3. Converte a imagem usando `SwingFXUtils.toFXImage(bufferedImage, writableImage)`.
4. Atualiza o elemento visual `ImageView` exibido no centro do editor.

### Limitações da Ponte de Render:
- **Sobrecarga de Conversão (Overhead):** Copiar e converter arrays de pixels AWT para texturas nativas do JavaFX na thread da UI a cada frame pode sobrecarregar a CPU em resoluções altas (como 4K), limitando o FPS.
- **Sincronização de Threads:** Requer sincronismo fino contra concorrência para evitar que a thread de UI tente ler a imagem enquanto a thread do jogo a está modificando, gerando flickering (piscadas na tela) ou quebras de memória.

---

## 4. Como Executar e Testar

### Editor Moderno (JavaFX) - Recomendado
```bash
# Windows
run-editor-javafx.bat

# Outros Sistemas
./mvnw javafx:run
```

### Editor Clássico (Swing) - Fallback
```bash
./mvnw exec:java
```
