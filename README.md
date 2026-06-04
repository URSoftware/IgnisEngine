<div align="center">

<p align="center">
  <img src="Icons/IgnisEngineBanner.jpg" alt="IgnisEngine Banner" width="400px" style="border-radius: 12px; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);" />
</p>

<img src="https://img.shields.io/badge/IgnisEngine-2D_Game_Engine-FF4500?style=for-the-badge&logo=openjdk&logoColor=white" alt="IgnisEngine"/>

<br/>

[![Java](https://img.shields.io/badge/Java-11+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.java.com)
[![Maven](https://img.shields.io/badge/Maven-3.9.6-C71A36?style=flat-square&logo=apachemaven&logoColor=white)](https://maven.apache.org)
[![License](https://img.shields.io/badge/License-URSoftware-0078D4?style=flat-square&logo=azuredevops&logoColor=white)](LICENSE)
[![Status](https://img.shields.io/badge/Status-In_Development-F9A825?style=flat-square&logo=git&logoColor=white)](https://github.com/URSoftware/IgnisEngine)

</div>

---

# IgnisEngine

A 2D graphics engine developed in Java, focused on rendering and 2D game creation.

## Overview

IgnisEngine is a 2D graphics engine built in pure Java using Java 2D for rendering. The project is structured into well-defined components that work together to provide a complete 2D game development platform with an integrated visual editor.

## Project Structure

```
IgnisEngine/
├── src/com/ignis/
│   ├── core/               # Main graphics engine (engine core)
│   │   ├── Game.java           # Main class with rendering and game loop
│   │   └── GameObject.java      # Base class for game objects
│   ├── editor/             # Visual editor for game modeling
│   │   ├── Editor.java          # Editor with graphical interface
│   │   └── settings.json        # Editor layout settings
│   └── main/               # Main application (game built with the engine)
│       └── Main.java
├── doc/                    # Project documentation
├── .mvn/wrapper/           # Maven Wrapper for reproducible builds
├── pom.xml                 # Maven configuration
└── README.md
```

## Components

### Core — Graphics Engine

The heart of IgnisEngine, responsible for rendering and execution.

- Rendering loop with buffer strategy
- Tick/render system for update and draw cycles
- Resizable window with fullscreen mode support
- 2D canvas management
- Base for 2D game creation
- Reusable GameObject system

---

### Editor — Visual Development Tool

A professional visual tool for game development and modeling.

- **Hierarchy** — Game object tree view
- **Viewport** — Real-time game preview
- **Inspector** — Object properties and settings

Additional features:
- Automatic custom layout saving
- Dynamic panel resizing
- User preferences persistence in JSON
- File menu with project and scene options
- Seamless integration with the engine core

---

### Main — Game Application

The main application where the developed game runs.

- Final project consuming core and editor
- Compilation and execution of the developed game
- Integrates all engine components

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 11+ |
| Graphics | Java 2D (AWT/Swing) |
| Structure | Canvas + JFrame |
| Build System | Maven 3.9.6 |
| JSON | org.json 20231013 |

## Getting Started

### Build with Maven

```bash
# Compile the project
./mvnw clean compile

# Run tests
./mvnw test

# Package
./mvnw package

# Clean and install
./mvnw clean install
```

### Run in an IDE

1. Clone the repository
   ```bash
   git clone https://github.com/URSoftware/IgnisEngine.git
   ```
2. Open in your preferred Java IDE (VS Code, IntelliJ, Eclipse)
3. **Using the editor (recommended):** Compile and run `src.com.ignis.editor.Editor`
   - The window opens in fullscreen mode
   - Panel layout is saved automatically
   - Custom layout is restored on next launch
4. **Testing the engine core:** Compile and run `src.com.ignis.core.Game`
5. **Running the game:** Compile and run `src.com.ignis.main.Main`

## Editor Configuration

Layout settings are saved automatically to `src/com/ignis/editor/settings.json`:

```json
{
  "mainSplitDividerLocation": 250,
  "rightSplitDividerLocation": 1229
}
```

| Key | Description |
|---|---|
| `mainSplitDividerLocation` | Position of the divider between Hierarchy and right panels (pixels) |
| `rightSplitDividerLocation` | Position of the divider between Viewport and Inspector (pixels) |

## Hub de Documentação

Toda a documentação técnica detalhada do projeto está modularizada na pasta [doc/](doc/). Abaixo está o índice organizado de arquivos por área de interesse:

### Inteligência Artificial e Modo Agente (AI & AGENT Mode)
* **[Guia de Início Rápido do Agente](doc/AGENT_MODE_QUICKSTART.md)**: Manual rápido de 3 passos para configurar e testar tarefas automatizadas com IA.
* **[Manual de Uso Completo](doc/AGENT_MODE_GUIDE.md)**: Guia profundo contendo exemplos práticos, operações suportadas e troubleshooting de API.
* **[Guia de Integração de IA](doc/AI_INTEGRATION_GUIDE.md)**: Configurações do modelo Google Gemini 2.5 Flash na engine.
* **[Arquitetura e Detalhes Técnicos](doc/AGENT_MODE_TECHNICAL.md)**: Estrutura do parser JSON, rate limiting, conexões e benchmarks de desempenho.
* **[Roteiro de Validação e Testes](doc/AGENT_MODE_TESTING.md)**: Casos de teste estruturados para validar o comportamento da IA.
* **[Índice de Documentação do Agente](doc/AGENT_MODE_INDEX.md)**: Sumário de relações e fluxos de leitura recomendados por perfil de usuário.
* **[Histórico de Alterações do Modo Agente](doc/AGENT_MODE_CHANGES.md)**: Resumo das melhorias estruturais implementadas.
* **[Registro de Correções do Agente](doc/AGENT_MODE_FIXES.md)**: Correções de parser e chamadas de API.
* **[Guia de Testes de IA](doc/AI_TEST_GUIDE.md)**: Procedimentos para ensaios e medições do assistente.
* **[Melhorias de Prompts de IA](doc/AGENT_PROMPT_IMPROVEMENT.md)**: Engenharia de prompts usada para guiar o assistente de forma precisa.
* **[Status Final de Implementação](doc/AGENT_MODE_STATUS.md)**: Diagnóstico final de funcionalidade e prontidão do agente.

### Programação e Scripts (IgnisScript)
* **[Guia Completo do IgnisScript](doc/IGNIS_SCRIPTS.md)**: Manual definitivo para criação de comportamento e scripts em formato Ignis.
* **[Referência da API de Scripts](doc/IGNIS_SCRIPT_API.md)**: Lista completa das chamadas de API, classes de física, entrada e controle de objetos.
* **[Manual de Consulta Rápida](doc/IGNISSCRIPT_QUICK_REFERENCE.md)**: Resumo sintático rápido das funções e loops.
* **[Manual de Prefabs e Comportamentos](doc/PREFAB_SCRIPTS_GUIDE.md)**: Criação de templates de objetos de jogo com scripts associados.
* **[Especificação de Arquivo Ignis](doc/IGNIS_FILE_SPEC.md)**: Estrutura de sintaxe de arquivo e parseamento nativo.
* **[Documentação do Scripting para Agentes](doc/AGENT_IGNISSCRIPT_DOCUMENTATION.md)**: Guia fornecido para orientar IAs a programarem scripts.
* **[Correção do Script do Jogador](doc/PLAYER_SCRIPT_FIX.md)**: Correção de bugs de movimentação do jogador.

### Sistemas Físicos e Renderização (Colisão e Câmera)
* **[Sistema de Colisões Integrado](doc/IGNIS_COLLISION_SYSTEM.md)**: Lógica do mecanismo AABB/Círculo no espaço bidimensional.
* **[Guia de Física e Colisões](doc/COLLISION_AND_ALERTS_GUIDE.md)**: Regras de restrição física, trigger de áudio e física de movimento.
* **[Integração do Sistema de Colisão](doc/COLLISION_SYSTEM_INTEGRATION.md)**: Como acoplar os listeners físicos ao motor gráfico principal.
* **[Exemplos de Scripts de Colisão](doc/EXAMPLE_COLLISION_SCRIPTS.md)**: Código de referência comentado para lógica de danos, ricochete e limites de tela.
* **[Referência Rápida de Colisão](doc/IGNIS_COLLISION_QUICKREF.md)**: Resumo de funções úteis de colisão.
* **[Sistema de Controle de Câmera](doc/CAMERA_SYSTEM_DOCS.md)**: Movimento de zoom, foco no jogador e limites de viewport da câmera 2D.

### Sistema de Alertas (Alert System)
* **[Arquitetura de Alertas Visuais](doc/ALERT_SYSTEM_IMPLEMENTATION.md)**: Sistema de notificações temporizadas do painel flutuante do Viewport.
* **[Guia de Início Rápido de Alertas](doc/ALERT_QUICK_START.md)**: Configurações rápidas para testar alertas simples.
* **[Manual de Referência de Alertas](doc/ALERT_QUICK_REFERENCE.md)**: Tabela de métodos e constantes do componente de alerta.
* **[Validação e Testes de Alertas](doc/TESTING_ALERTS.md)**: Roteiros de testes visuais e temporizações das filas de alerta.

### Históricos e Resumos Globais
* **[Sumário de Implementações](doc/CHANGES_IMPLEMENTATION_SUMMARY.md)**: Lista completa das alterações físicas feitas no core e no editor.
* **[Sumário Geral de Mudanças](doc/CHANGES_SUMMARY.md)**: Registro histórico das evoluções estruturais globais.


## Requirements

| Requirement | Minimum Version |
|---|---|
| Java | 11 or higher |
| Maven | 3.6.0+ (or use the included Maven Wrapper) |

## License

This project is part of URSoftware development.

---

<div align="center">

[![URSoftware](https://img.shields.io/badge/URSoftware-Organization-0D1117?style=flat-square&logo=github&logoColor=white)](https://github.com/URSoftware)

</div>
