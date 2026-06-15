# Game 01 — Projeto de Teste do IgnisEngine

Este diretório contém o projeto de teste/exemplo **Game 01**, utilizado para validar as funcionalidades do motor, sub-editores, builder e o ecossistema de plugins.

---

## ⚙️ Configuração do Build (`build.json`)

As configurações de build atuais para exportação do jogo são as seguintes:

* **Nome do Jogo:** `Game 01`
* **Versão:** `1.0.0`
* **Resolução Padrão:** `1280 x 720` (HD, Proporção 16:9)
* **Tela Cheia (Fullscreen):** Desabilitado (`false`)
* **Diretório de Saída:** `build/`
* **Plataformas de Destino (Targets):** `WINDOWS`

---

## 🔌 Plugins Instalados (`project/plugins/`)

O projeto possui os seguintes plugins instalados e ativados via **Marketplace**:

| Plugin | Autor | Versão | Status de Instalação | Repositório / Git URL |
|---|---|---|---|---|
| **Advanced Physics 2D** | PhysTech | `2.1.0` | `SUCCESSFUL_SANDBOXED` | [advanced-physics-2d](https://github.com/PhysTech/advanced-physics-2d.git) |
| **Virtual Gamepad UI Overlay** | MobileDev | `1.0.5` | `SUCCESSFUL_SANDBOXED` | [virtual-gamepad-ignis](https://github.com/MobileDev/virtual-gamepad-ignis.git) |

> [!NOTE]
> Todos os plugins estão instalados em modo seguro isolado (`SANDBOXED`), garantindo conformidade com a segurança do motor.

---

## 📜 Scripts do Jogo (`project/scripts/`)

Os scripts associados aos objetos e lógica do projeto:

* **Player.java:** Script principal que controla a movimentação e estado do jogador.
* **PlayerCollisionEffect.java:** Lógica que lida com eventos de colisão física e lógica do player.
* **Test.java:** Script auxiliar de testes rápidos.

---

## 📓 Sistema de Notas (`project/notes/`)

As notas e tarefas do projeto estão integradas no arquivo `todo.json`. Com a migração para a interface em JavaFX (`FxNotesWindow`), as notas são formatadas e editadas em HTML com estilo em tema escuro integrado.
