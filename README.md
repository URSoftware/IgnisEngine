<div align="center">

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

## Documentation

All project documentation is located in the [`doc/`](doc/) directory.

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
