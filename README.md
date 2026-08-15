<p align="center">
  <img src="Icons/IgnisEngineBanner.jpg" alt="IgnisEngine Banner" width="250px" style="border-radius: 12px; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.3);" />
</p>

<h1 align="center">IgnisEngine</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Maven-3.9.6-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/JavaFX-17-blue?style=for-the-badge&logo=javafx&logoColor=white" alt="JavaFX" />
  <img src="https://img.shields.io/badge/MCP-Protocol-blueviolet?style=for-the-badge" alt="MCP" />
</p>

<p align="center">
  IgnisEngine e uma engine grafica 2D desenvolvida em Java puro, focada em renderizacao e na criacao simplificada de jogos bidimensionais. O projeto e estruturado em componentes bem definidos que trabalham de forma coordenada para fornecer uma plataforma completa com um editor visual integrado e suporte robusto para IA agentica atraves de Model Context Protocol (MCP).
</p>

---

## Branches de Desenvolvimento

| Branch | Estado | Descricao |
|--------|--------|-----------|
| **`main`** | Em desenvolvimento | Migracao da interface Swing para JavaFX (versao principal). Nucleo da engine e marketplace mantidos. |
| **`Legado`** | Estavel | Versao legada com interface Java Swing e integracao do marketplace. Snapshot funcional preservado. |

A migracao activa do editor para JavaFX acontece na branch `main`. A versao Swing classica permanece na branch `Legado` para referencia. Plano tecnico de migracao disponivel em [Plano de Migracao JavaFX](doc/JAVAFX_MIGRATION_PLAN.md).

---

<details>
  <summary><b>Estrutura de Diretorios</b></summary>

```text
IgnisEngine/
├── pom.xml                  # Configuracao de dependencias Maven
├── run-editor-javafx.bat    # Script de execucao rapida no Windows
├── editor_layout.json       # Configuracoes de layout salvas do editor
├── doc/                     # Documentacao tecnica detalhada (Vault)
│   ├── CONTRIBUTING.md      # Guia de contribuicao para desenvolvedores
│   ├── CODE_OF_CONDUCT.md   # Codigo de conduta da comunidade
│   ├── CHANGELOG.md         # Registro cronologico de alteracoes e releases
├── src/com/ignis/
│   ├── core/                # Core da engine (loop principal e renderizacao)
│   │   ├── Game.java        # Loop do jogo, tick/render e canvas
│   │   ├── GameObject.java  # Classe base para objetos de jogo
│   │   └── ui/              # Componentes de interface in-game
│   ├── editor/              # Integracao com IA e janelas auxiliares do editor
│   │   └── fx/              # Editor visual JavaFX (principal)
│   ├── builder/             # Compilador e empacotador de projetos
│   ├── animation/           # Sistema de animacao 2D
│   ├── imageeditor/         # Editor de imagens integrado
│   ├── audioeditor/         # Editor de audio (DAW) integrado
│   └── runtime/             # Player standalone para execucao de builds
```
</details>

---

## Componentes Principais

### Core — Motor Grafico
O coracao do IgnisEngine, responsavel por toda a logica de renderizacao e execucao fisica/logica:
* Loop de atualizacao e desenho constante (Tick/Render).
* Gerenciamento de tela com suporte a redimensionamento e modo tela cheia.
* Sistema de GameObject reutilizavel para gerenciar elementos em cena.
* Padrao arquitetural Entidade-Componente integrado ao sistema de scripts.

### Editor — Ferramenta de Modelagem
Interface visual profissional para agilizar o desenvolvimento:
* **Hierarchy:** Arvore estrutural contendo todos os objetos presentes na cena.
* **Viewport:** Area de visualizacao em tempo real do estado de renderizacao.
* **Inspector:** Editor de propriedades e atributos dinamicos do objeto selecionado.
* Salvamento automatico do layout e restauracao das preferencias do desenvolvedor via JSON.

---

## Hub de Documentacao (Vault)

Toda a documentacao tecnica detalhada do projeto esta organizada de forma modular. Abaixo esta o indice estruturado de arquivos:

### Configuracao e Contribucao
* [Guia de Configuracao de Ambiente](doc/DEVELOPER_SETUP.md): Requisitos de JDK, clonagem de submodulos, configuracoes de IDE e execucao do editor.
* [Diretrizes de Contribucao](doc/CONTRIBUTING.md): Padroes de codigo, estrategias de branches e formatacao de mensagens de commit.
* [Codigo de Conduta](doc/CODE_OF_CONDUCT.md): Principios de convivencia saudavel da comunidade de colaboradores.
* [Politica de Seguranca](doc/SECURITY.md): Diretrizes para reporte responsavel de vulnerabilidades.
* [Registro de Alteracoes (Changelog)](doc/CHANGELOG.md): Historico cronologico detalhado de melhorias e correcoes.
* [Configuracao de CI/CD](doc/CI_CD_SETUP.md): Pipeline automatizado com GitHub Actions, matriz de JDK e cache de dependencias.

### Planejamento e Arquitetura
* [Roadmap de Evolucao](doc/MASTER_ROADMAP.md): Planejamento oficial de evolucao da engine e fases de desenvolvimento.
* [Paridade de Recursos JavaFX](doc/JAVAFX_MISSING_FEATURES_PLAN.md): Planejamento e status detalhado da migracao e paridade de recursos com o editor Swing.
* [Arquitetura do Sistema](doc/ARCHITECTURE.md): Estrutura de pacotes, ciclo de vida do editor/runtime e modelo de concorrencia.
* [Inventario do Projeto](doc/PROJECT_INVENTORY.md): Auditoria completa do estado dos arquivos e modulos do repositorio.
* [Guia de ADRs (Decisoes de Arquitetura)](doc/ADR_GUIDE.md): Conceituacao e modelo de documentacao de escolhas tecnicas.
* [Diretrizes de Documentacao](doc/DOCUMENTATION_GUIDELINES.md): Padroes de organizacao de documentos, HTML no README e regras do Vault.
* [Guia de Versionamento e Releases](doc/RELEASE_GUIDE.md): Padrao de SemVer e passo a passo de publicacao de builds.
* [Especificacao do Formato .ignis](doc/IGNIS_FILE_SPEC.md): Detalhes do formato de arquivo comprimido do projeto.

### Sistemas da Engine e Viewport
* [Funcionamento do Game Loop](doc/GAME_LOOP_INTERNALS.md): Ciclo tick/render, estados da simulacao e sistema de snapshots de cena.
* [Modelo de Threads](doc/THREADING_MODEL.md): Concorrencia entre a Game Thread, JavaFX Application Thread e Swing EDT.
* [Guia de Camera e Viewport](doc/CAMERA_VIEWPORT_GUIDE.md): Controles de foco, zoom, rotacao, limites de tela e efeitos de tremor.
* [Guia do Sistema de UI](doc/UI_SYSTEM_GUIDE.md): Interface do usuario em canvas, componentes de UI e exemplos praticos.
* [Guia do Sistema de Audio](doc/AUDIO_SYSTEM_GUIDE.md): Configuracao de som, classes MusicPath e mixagem no editor de audio.

### Logica e Scripting (IgnisScript)
* [Guia Completo de Scripts](doc/IGNIS_SCRIPTS.md): Criacao de comportamento, variaveis e ciclos de vida.
* [Referencia da API de Scripts](doc/IGNIS_SCRIPT_API.md): Tabela de metodos e constantes do IgnisScript.
* [Manual de Consulta Rapida](doc/IGNISSCRIPT_QUICK_REFERENCE.md): Resumo sintatico rapido das funcoes de scripting.
* [Manual de Prefabs](doc/PREFAB_SCRIPTS_GUIDE.md): Modelagem e instanciao de prefabs de objetos de jogo.
* [Guia de Bibliotecas de Projeto](doc/PROJECT_LIBS_GUIDE.md): Carregamento e classpath de bibliotecas JAR privadas por projeto.

### Fisica e Colisoes
* [Sistema de Colisoes Integrado](doc/IGNIS_COLLISION_SYSTEM.md): Teoria fisica das colisoes AABB e Circle.
* [Guia de Fisica de Colisoes](doc/COLLISION_AND_ALERTS_GUIDE.md): Alertas de colisao e disparos fisicos.
* [Exemplos de Scripts de Colisao](doc/EXAMPLE_COLLISION_SCRIPTS.md): Scripts prontos para rebote, danos e limites de tela.

### Notificacoes e Ferramentas
* [Sistema de Alertas Visuais](doc/ALERT_SYSTEM_IMPLEMENTATION.md): Notificacoes temporizadas na Viewport.
* [Guia do Builder](doc/BUILDER_GUIDE.md): Empacotamento de executaveis Java e exportacao nativa C++.
* [Guia do Cliente do Marketplace](doc/MARKETPLACE_CLIENT_GUIDE.md): Integracao com marketplace Next.js e download de plugins.
* [Guia do Servidor MCP](doc/MCP_SERVER_GUIDE.md): Integracao com agentes de IA e catalogo de ferramentas do Model Context Protocol.
* [Guia de Colaboracao em Tempo Real](doc/COLLABORATION_GUIDE.md): Sessao multi-usuario e sincronizacao de cena via TCP.

---

## Requisitos do Sistema

| Tecnologia | Versao Minima | Observacao |
|---|---|---|
| **Java** | 17 (LTS) | Versao oficial do projeto; JDKs mais novos compilam para 17 via release |
| **Maven** | — | Nao e necessario instalar: utilize o Maven Wrapper incluso (mvnw / mvnw.cmd) |

---

<details>
  <summary><b>Como Executar Localmente</b></summary>

### Executando pelo Terminal
Certifique-se de estar no diretorio raiz do projeto:

**Windows (JavaFX):**
```cmd
run-editor-javafx.bat
```

**Outros Sistemas (JavaFX):**
```bash
./mvnw javafx:run
```

O editor classico Swing foi removido da branch `main` e permanece disponivel apenas na branch `Legado`.

### Compilacao e Empacotamento
```bash
# Limpar e compilar classes
./mvnw clean compile

# Empacotamento em arquivo JAR executavel
./mvnw package
```
</details>

---

<details>
  <summary><b>Endpoints da API</b></summary>

O servidor MCP (Model Context Protocol) embutido expoe os seguintes endpoints HTTP locais para comunicacao com agentes de IA e o dashboard web:

- **GET `/mcp/tools`**: Lista todas as ferramentas disponiveis no motor (66 ferramentas ativas).
- **POST `/mcp/call`**: Executa uma ferramenta especifica enviando argumentos em formato JSON.
- **GET `/` ou `/index.html`**: Retorna o Dashboard Web Interativo do MCP.
</details>

---

## Autores e Organização

Este projeto é mantido pela organização **URSoftware**.

<table align="center">
  <tr>
    <td align="center">
      <a href="https://github.com/ThyagoToledo">
        <img src="https://github.com/ThyagoToledo.png?size=100" width="100px;" alt="Thyago Toledo" style="border-radius: 50%;" /><br />
        <sub><b>Thyago Toledo</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/FeronZerbana">
        <img src="https://github.com/FeronZerbana.png?size=100" width="100px;" alt="FeronZerbana" style="border-radius: 50%;" /><br />
        <sub><b>FeronZerbana</b></sub>
      </a>
    </td>
  </tr>
</table>
