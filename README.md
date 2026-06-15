# IgnisEngine

<p align="center">
  <img src="Icons/IgnisEngineBanner.jpg" alt="IgnisEngine Banner" width="250px" style="border-radius: 12px; box-shadow: 0 8px 30px rgba(0, 0, 0, 0.3);" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Maven-3.9.6-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven" />
  <img src="https://img.shields.io/badge/License-URSoftware-0078D4?style=for-the-badge&logo=azuredevops&logoColor=white" alt="License" />
  <img src="https://img.shields.io/badge/Status-In%20Development-F9A825?style=for-the-badge&logo=git&logoColor=white" alt="Status" />
</p>

IgnisEngine é uma engine gráfica 2D desenvolvida em Java puro, focada em renderização e na criação simplificada de jogos bidimensionais. O projeto é estruturado em componentes bem definidos que trabalham de forma coordenada para fornecer uma plataforma completa com um editor visual integrado.

---

## Branches de Desenvolvimento

| Branch | Estado | Descrição |
|--------|--------|-----------|
| **`main`** | Em desenvolvimento | Migração da interface Swing para JavaFX (versão principal). Núcleo da engine e marketplace mantidos. |
| **`Legado`** | Estável | Versão legada com interface Java Swing e integração do marketplace. Snapshot funcional preservado. |

A migração ativa do editor para JavaFX acontece na branch `main`. A versão Swing clássica permanece na branch `Legado` para referência. Plano técnico de migração disponível em [Plano de Migração JavaFX](doc/JAVAFX_MIGRATION_PLAN.md).

---

## Estrutura do Projeto

```text
IgnisEngine/
├── pom.xml                  # Configuração de dependências Maven
├── run-editor-javafx.bat    # Script de execução rápida no Windows
├── editor_layout.json       # Configurações de layout salvas do editor
├── doc/                     # Documentação técnica detalhada (Vault)
│   ├── CONTRIBUTING.md      # Guia de contribuição para desenvolvedores
│   ├── CODE_OF_CONDUCT.md   # Código de conduta da comunidade
│   ├── CHANGELOG.md         # Registro cronológico de alterações e releases
├── src/com/ignis/
│   ├── core/                # Core da engine (loop principal e renderização)
│   │   ├── Game.java        # Loop do jogo, tick/render e canvas
│   │   ├── GameObject.java  # Classe base para objetos de jogo
│   │   └── ui/              # Componentes de interface in-game
│   ├── editor/              # Editor visual Swing (legado)
│   │   └── fx/              # Editor visual JavaFX (moderno)
│   ├── builder/             # Compilador e empacotador de projetos
│   ├── animation/           # Sistema de animação 2D
│   ├── imageeditor/         # Editor de imagens integrado
│   ├── audioeditor/         # Editor de áudio (DAW) integrado
│   └── runtime/             # Player standalone para execução de builds
```

---

## Componentes Principais

### Core — Motor Gráfico
O coração do IgnisEngine, responsável por toda a lógica de renderização e execução física/lógica:
* Loop de atualização e desenho constante (Tick/Render).
* Gerenciamento de tela com suporte a redimensionamento e modo tela cheia.
* Sistema de GameObject reutilizável para gerenciar elements em cena.

### Editor — Ferramenta de Modelagem
Interface visual profissional para agilizar o desenvolvimento:
* **Hierarchy:** Árvore estrutural contendo todos os objetos presentes na cena.
* **Viewport:** Área de visualização em tempo real do estado de renderização.
* **Inspector:** Editor de propriedades e atributos dinâmicos do objeto selecionado.
* Salvamento automático do layout e restauração das preferências do desenvolvedor via JSON.

---

## Hub de Documentação (Vault)

Toda a documentação técnica detalhada do projeto está organizada de forma modular. Abaixo está o índice estruturado de arquivos:

### Configuração e Contribuição
* [Guia de Configuração de Ambiente](doc/DEVELOPER_SETUP.md): Requisitos de JDK, clonagem de submódulos, configurações de IDE e execução do editor.
* [Diretrizes de Contribuição](doc/CONTRIBUTING.md): Padrões de código, estratégias de branches e formatação de mensagens de commit.
* [Código de Conduta](doc/CODE_OF_CONDUCT.md): Princípios de convivência saudável da comunidade de colaboradores.
* [Política de Segurança](doc/SECURITY.md): Diretrizes para reporte responsável de vulnerabilidades.
* [Registro de Alterações (Changelog)](doc/CHANGELOG.md): Histórico cronológico detalhado de melhorias e correções.
* [Configuração de CI/CD](doc/CI_CD_SETUP.md): Pipeline automatizado com GitHub Actions, matriz de JDK e cache de dependências.

### Planejamento e Arquitetura
* [Roadmap de Evolução](doc/MASTER_ROADMAP.md): Planejamento oficial de evolução da engine e fases de desenvolvimento.
* [Paridade de Recursos JavaFX](doc/JAVAFX_MISSING_FEATURES_PLAN.md): Planejamento e status detalhado da migração e paridade de recursos com o editor Swing.
* [Arquitetura do Sistema](doc/ARCHITECTURE.md): Estrutura de pacotes, ciclo de vida do editor/runtime e modelo de concorrência.
* [Inventário do Projeto](doc/PROJECT_INVENTORY.md): Auditoria completa do estado dos arquivos e módulos do repositório.
* [Guia de ADRs (Decisões de Arquitetura)](doc/ADR_GUIDE.md): Conceituação e modelo de documentação de escolhas técnicas.
* [Diretrizes de Documentação](doc/DOCUMENTATION_GUIDELINES.md): Padrões de organização de documentos, HTML no README e regras do Vault.
* [Guia de Versionamento e Releases](doc/RELEASE_GUIDE.md): Padrão de SemVer e passo a passo de publicação de builds.
* [Especificação do Formato .ignis](doc/IGNIS_FILE_SPEC.md): Detalhes do formato de arquivo comprimido do projeto.

### Sistemas da Engine e Viewport
* [Funcionamento do Game Loop](doc/GAME_LOOP_INTERNALS.md): Ciclo tick/render, estados da simulação e sistema de snapshots de cena.
* [Modelo de Threads](doc/THREADING_MODEL.md): Concorrência entre a Game Thread, JavaFX Application Thread e Swing EDT.
* [Guia de Câmera e Viewport](doc/CAMERA_VIEWPORT_GUIDE.md): Controles de foco, zoom, rotação, limites de tela e efeitos de tremor.
* [Guia do Sistema de UI](doc/UI_SYSTEM_GUIDE.md): Interface do usuário em canvas, componentes de UI e exemplos práticos.
* [Guia do Sistema de Áudio](doc/AUDIO_SYSTEM_GUIDE.md): Configuração de som, classes MusicPath e mixagem no editor de áudio.

### Lógica e Scripting (IgnisScript)
* [Guia Completo de Scripts](doc/IGNIS_SCRIPTS.md): Criação de comportamento, variáveis e ciclos de vida.
* [Referência da API de Scripts](doc/IGNIS_SCRIPT_API.md): Tabela de métodos e constantes do IgnisScript.
* [Manual de Consulta Rápida](doc/IGNISSCRIPT_QUICK_REFERENCE.md): Resumo sintático rápido das funções de scripting.
* [Manual de Prefabs](doc/PREFAB_SCRIPTS_GUIDE.md): Modelagem e instanciação de prefabs de objetos de jogo.

### Física e Colisões
* [Sistema de Colisões Integrado](doc/IGNIS_COLLISION_SYSTEM.md): Teoria física das colisões AABB e Circle.
* [Guia de Física de Colisões](doc/COLLISION_AND_ALERTS_GUIDE.md): Alertas de colisão e disparos físicos.
* [Exemplos de Scripts de Colisão](doc/EXAMPLE_COLLISION_SCRIPTS.md): Scripts prontos para rebote, danos e limites de tela.

### Notificações e Ferramentas
* [Sistema de Alertas Visuais](doc/ALERT_SYSTEM_IMPLEMENTATION.md): Notificações temporizadas na Viewport.
* [Guia do Builder](doc/BUILDER_GUIDE.md): Empacotamento de executáveis Java e exportação nativa C++.
* [Guia do Cliente do Marketplace](doc/MARKETPLACE_CLIENT_GUIDE.md): Integração com marketplace Next.js e download de plugins.

---

## Requisitos do Sistema

| Tecnologia | Versão Mínima | Observação |
|---|---|---|
| **Java** | 17 (LTS) | Versão oficial do projeto; JDKs mais novos compilam para 17 via release |
| **Maven** | — | Não é necessário instalar: utilize o Maven Wrapper incluso (mvnw / mvnw.cmd) |

---

## Como Rodar o Editor

### Executando pelo Terminal
Certifique-se de estar no diretório raiz do projeto:

**Windows (JavaFX):**
```cmd
run-editor-javafx.bat
```

**Outros Sistemas (JavaFX):**
```bash
./mvnw javafx:run
```

**Modo Clássico Swing (Legado):**
```bash
./mvnw exec:java
```

### Compilação e Empacotamento
```bash
# Limpar e compilar classes
./mvnw clean compile

# Empacotar em arquivo JAR executável
./mvnw package
```

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
