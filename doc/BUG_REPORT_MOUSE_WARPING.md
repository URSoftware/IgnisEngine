# Relatorio Tecnico — Correcao de Bugs Criticos e Auditoria de Mouse (JavaFX)

Este documento detalha a analise tecnica e resolucao do bug critico de loop infinito de coordenadas e cliques fantasmas na Viewport do editor JavaFX, a separacao rigorosa de botoes de mouse (esquerdo vs. direito) em toda a interface do editor, e o ajuste estetico da numeracao de linhas do editor de codigo.

---

## 1. Bug Critico de Loop Infinito e Cliques Fantasmas na Viewport

### Sintomas e Comportamento:
Ao arrastar entidades ou rotacionar/mover a camera perto das bordas da Viewport do editor JavaFX, o cursor entrava em um comportamento instavel de teleporte continuo e descontrolado. As coordenadas lógicas passavam a ser inundadas a taxas de milhares de eventos por segundo, resultando no congelamento total da interface visual do editor (JavaFX Application Thread bloqueada).

### Causa Raiz:
1. **Inexistencia de Peer Nativo AWT:** O componente `Game.java` estende `java.awt.Canvas`. Sob o editor JavaFX, o renderizador desenha em um `BufferedImage` off-screen, que e entao convertido para `WritableImage` e exibido em um `javafx.scene.canvas.Canvas`. Portanto, o `Canvas` do AWT nunca possui um peer nativo síncrono associado a uma janela do sistema operacional.
2. **Excecao no Warping de Mouse:** Quando o cursor atinge as margens limites, o metodo `handleMouseDrag` em `Game.java` tenta reposicionar o cursor fisicamente usando o AWT `Robot`. Para computar as coordenadas corretas de tela, ele executa `getLocationOnScreen()`. Por nao estar acoplado a um container nativo visivel AWT/Swing, esta chamada lanca uma `IllegalComponentStateException`.
3. **Loop de Eventos e Bloqueio:**
   - A excecao de runtime interrompe o fluxo normal de eventos de mouse AWT simulados, fazendo com que o estado do clique fique preso (o evento `mouseReleased` nunca e despachado para a engine).
   - Simultaneamente, as coordenadas lógicas geradas pelo JavaFX e as fisicas remapeadas (sob taxas de escala High-DPI de tela do Windows) divergem, fazendo com que o proximo frame interprete a posicao como estando dentro da margem de teleporte. Isso reinicia o fluxo recursivamente em uma thread secundaria e na thread de eventos, gerando o travamento.

### Resolucao:
Adicionamos a clausula de validacao `isShowing()` no teste condicional do Robot warping em `Game.java`:
```java
// Check for edge wrapping (infinite drag)
if (robot != null && isShowing()) {
```
Como `isShowing()` retorna `false` no Canvas AWT quando operando off-screen sob o JavaFX (mas retorna `true` no editor Swing legado), a lógica de warp de borda e desativada de forma segura sob JavaFX. Isso elimina por completo o lancamento da excecao e, consequentemente, remove o loop de coordenadas e o congelamento.

---

## 2. Auditoria e Separacao de Botoes de Mouse (Esquerdo vs. Direito)

Realizamos uma auditoria completa na interface do editor para garantir a separacao ideal da experiencia do usuario: clique esquerdo (`PRIMARY`) exclusivamente para selecao e interacoes diretas; clique direito (`SECONDARY`) exclusivamente para menus contextuais (HUDs).

### Implementacao no Portal do Editor:
1. **Hierarchy Tree:**
   - Removido o listener redundante em `tree.setOnMouseClicked` que monitorava `PRIMARY` e forçava a abertura de menus contextuais.
   - Removido o vinculo estatico de context menu do JavaFX em `tree.setContextMenu(...)` para evitar exibicoes duplicadas indesejadas.
   - Centralizada a exibicao dinamica do menu contextual no evento `tree.setOnMouseClicked` apenas quando o botao acionado for `SECONDARY` (clique direito), selecionando o objeto e apresentando as opcoes correspondentes ou menu de criacao.
2. **Assets Tree:**
   - Atualizado o listener de mouse para que cliques simples com `PRIMARY` executem apenas a selecao padrão do JavaFX TreeView.
   - Cliques duplos com `PRIMARY` executam a abertura e edicao do arquivo selecionado.
   - Cliques simples com `SECONDARY` exibem o menu contextual especifico (`buildAssetsContextMenu`).
3. **Viewport Canvas:**
   - Mantida a integridade dos listeners: clique esquerdo (`PRIMARY`) seleciona entidades e atualiza o Inspector; clique direito aciona `setOnContextMenuRequested`, selecionando a entidade sob o cursor e abrindo o HUD de opcoes.

### Implementacao nos Subeditores:
1. **Linha do Tempo de Animacoes (`FxAnimationEditor.java`):**
   - Restringido o metodo `setOnMousePressed` no Canvas da linha do tempo. Agora, qualquer interacao de scrub de reproducao ou arraste de duracao de frames e cancelada imediatamente se o botao de mouse nao for `PRIMARY`.
2. **Faixas e Clipes de Audio (`FxAudioEditor.java`):**
   - Restringido o clique no Canvas de clipes de audio. A selecao de faixas e definicao de selecao de loop por arraste sao limitadas exclusivamente ao botao `PRIMARY`.
3. **Camadas de Imagem (`FxImageEditor.java`):**
   - Acao de duplo clique sobre as camadas para exibicao do dialogo de renomeacao restrita estritamente ao botao `PRIMARY`.

---

## 3. Ajuste Estetico do FxCodeEditor

### Problema Visual:
Em temas escuros (como Dracula, Monokai e One Dark), a barra lateral de numero de linhas no editor de script exibia fundo cinza-claro ou branco com numeros ilegiveis ou invisiveis, quebrando o design premium.

### Causa:
O componente RichTextFX gera componentes graficos com a classe CSS `.line-number` e nao `.lineno`. Alem disso, a folha de estilos padrao do componente JavaFX nativo se sobrepunha as nossas definicoes devido a falta da declaracao `!important`.

### Solucao:
Atualizamos o metodo `applyTheme` em `FxCodeEditor.java` para injetar regras explicitas para `.paragraph-graphic` e `.line-number` utilizando a diretiva `!important`:
```java
".code-area .paragraph-graphic {\n" +
"    -fx-background-color: " + linenoBgHex + " !important;\n" +
"    -fx-padding: 0 5 0 5;\n" +
"}\n" +
".code-area .lineno, .code-area .line-number {\n" +
"    -fx-text-fill: " + linenoFgHex + " !important;\n" +
"    -fx-font-family: 'Consolas';\n" +
"    -fx-font-size: 14px;\n" +
"}\n"
```
Com este ajuste, a numeracao assume perfeitamente o contraste correto de cores em todos os temas de visualizacao disponiveis.

---

## 4. Loop de Selecao Infinita e Saltos de Coordenadas

### Sintomas e Comportamento:
Ao abrir o HUD de clique direito na Viewport, e em seguida clicar com o botao esquerdo em qualquer outra area (como a Viewport fora do HUD ou no painel da Hierarchy), o Inspector comecava a atualizar indefinidamente com coordenadas aleatorias e o editor comecava a alternar repetidamente e de forma descontrolada a selecao entre os GameObjects da cena (ex: Mapa, Player, Circulo).

### Causa Raiz:
1. **Perda de Evento de Mouse Release:** Ao exibir o menu de contexto (HUD) do JavaFX na Viewport, o JavaFX consome o clique de mouse fora do HUD para fechar o menu. AWT/Swing nunca recebe o evento `MOUSE_RELEASED` correspondente.
2. **Modo de Arraste Preso:** Por consequencia, o estado de arraste de gizmos em `Game.java` (`currentDragMode`) permanecia preso em `CENTER`.
3. **Salto de Coordenadas e Sobreposicao:** Ao mudar a selecao para outro objeto, o arraste continuava ativo de forma transparente no novo objeto com as coordenadas acumuladas do objeto anterior. Isso fazia com que o novo objeto saltasse instantaneamente para a posicao do anterior, sobrepondo-se a ele.
4. **Ciclo de Colisao e Selecao:** Como os objetos ficavam sobrepostos nas mesmas coordenadas, qualquer pequeno movimento do mouse disparava novas selecoes consecutivas via `game.getObjectAt(...)` para os objetos coincidentes, gerando um loop infinito.
5. **Recursão Asíncrona:** O listener de selecao do `Game` e o listener do `TreeView` da Hierarchy geravam atualizacoes de selecao cruzadas de forma asíncrona via `Platform.runLater`, perpetuando o loop de alternacao de selecao.

### Solucao:
1. **Reset do Estado de Arraste:** Criamos o metodo publico `cancelDrag()` em `Game.java` para resetar `currentDragMode` para `NONE`, restaurar o cursor padrao e notificar o encerramento do transformador para o listener do sistema de Undo.
2. **Desativacao do Arraste Preso:** Invocamos `game.cancelDrag()` ao abrir o menu de contexto da viewport e ao mudar o objeto selecionado em `setSelected(go)` no editor JavaFX.
3. **Guarda de Selecao Reentrante:** Criamos a flag boolean `suppressSelectionEvents` em `IgnisEditorApp.java`. Qualquer atualizacao de selecao do `TreeView` ou do listener do `Game` e ignorada se a flag estiver ativa. A flag e configurada como `true` durante a mudanca de selecao e redefinida para `false` no bloco `finally`, interrompendo qualquer possibilidade de recursão de eventos de selecao.

---

## 5. Abertura do Editor de Scripts Interno via Assets Browser

### Melhoria de UX:
Anteriormente, a abertura de qualquer arquivo da arvore de assets chamava o aplicativo padrao do sistema operacional. Para melhorar o fluxo de trabalho do desenvolvedor, integramos o editor de codigo interno da engine (`FxCodeEditor`) diretamente a arvore de assets:
1. **Clique Duplo:** No listener `setOnMouseClicked` da árvore de assets, se o desenvolvedor der um clique duplo com o botao esquerdo em um arquivo `.java` (script), ele sera aberto diretamente no `FxCodeEditor` interno da engine.
2. **Menu Contextual:** Adicionamos a opcao "Abrir no Editor do Ignis" no topo do menu contextual de arquivos `.java` no navegador de assets. O item padrao do sistema foi renomeado para "Abrir / Editar (Sistema)" para evitar ambiguidade.
3. **Resolucao de Nome do Script:** O metodo `openScriptInIgnisEditor` resolve o nome da classe removendo a extensao `.java` e instancia o editor usando o `ScriptManager` ativo do projeto.

