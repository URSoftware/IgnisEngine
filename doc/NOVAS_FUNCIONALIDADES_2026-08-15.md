# Detalhamento das Novas Funcionalidades - 15/08/2026

Este documento descreve as melhorias e novos recursos implementados no motor Ignis Engine e no projeto TensuraGame na data de 15/08/2026, sob a autoria exclusiva de ThyagoToledo.

---

## 1. TensuraGame — Refinamento de HUD de Batalha e Contencao de Texto no BattleDirector

### Proposito
Resolver o estouro visual de texto longo (como analises do Grande Sabio e mensagens de status de combate) nos paineis de UI, garantindo que o layout seja estavel, responsivo e deterministico.

### O que faz
- **Algoritmo de Quebra e Truncamento de Texto (`fitToBox`):**
  - Implementada medicao de texto deterministica sem dependencia direta de `Graphics`, utilizando `FontRenderContext` e as metricas de linha da fonte (`getLineMetrics`).
  - Divide o texto por paragrafos e palavras que cabem na largura especificada do widget, limitando ao numero maximo de linhas suportado pela altura.
  - Caso o texto exceda a capacidade vertical da caixa, trunca a ultima linha com reticencias (`...`) mantendo a contencao visual perfeita.
- **Metodo `setTextFitted`:**
  - Atualiza automaticamente `UILabel` configurando `setMultiline(true)` e aplicando o texto ajustado pela rotina `fitToBox`.

---

## 2. Preservacao e Restauracao de Estado Autorado (WidgetState)

### Proposito
Garantir que as mutacoes dinamicas realizadas durante o combate nao contaminem a cena persistida do projeto `.ignis` quando o autosave do editor for acionado.

### O que faz
- **Classe `WidgetState`:**
  - Captura a fotografia completa do estado original autorado dos widgets de UI persistidos da cena (`x`, `y`, `width`, `height`, `visible`, `enabled`, `text`, `textColor`, `imagePath`, `multiline`, `progressValue`, `progressMax`).
- **Restauracao em `hide()` e `onDetach()`:**
  - Ao ocultar o HUD de duelo ou ao desanexar o script (`onDetach` acionado no Stop da simulacao), todos os widgets retornam exatamente ao estado fotografado antes do inicio da batalha.
  - Oculta o objeto pai (`canvasObject`) mantendo a integridade da cena para o proximo ciclo de edicao e gravacao.

---

## 3. Atualizacao de Dependencias e Sincronizacao da Cena

### Proposito
Manter os contratos de API e binarios de cena sincronizados entre a engine e o jogo de demonstracao.

### O que faz
- **Atualizacao de `ignis-engine-api.jar`:** Atualizado o pacote de biblioteca em `projects/TensuraGame/project/libs/` com as interfaces e classes mais recentes.
- **Sincronizacao de `TensuraGame.ignis`:** Atualizada a definicao serializada da cena do jogo com as correcoes de UI e objetos de cena.

---

## 4. Testes e Validacao

- Execucao da suite de testes automatizados da engine via Maven: **243 testes executados, 0 falhas, 0 erros** (`BUILD SUCCESS`).
- Validacao da compilacao de scripts do jogo sem erros de tipagem ou resolucao de simbolos.
