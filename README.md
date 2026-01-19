# IginisEngine

Motor gráfico 2D desenvolvido em Java, com foco em renderização de gráficos e criação de jogos 2D.

## Descrição

IginisEngine é um motor gráfico 2D construído em Java puro, utilizando Java 2D para renderização. O projeto é estruturado em componentes bem definidos que trabalham em conjunto para criar uma plataforma completa de desenvolvimento de jogos 2D com editor visual integrado.

## Estrutura do Projeto

```
IginisEngine/
├── src/com/ignis/
│   ├── core/           # Motor gráfico principal (núcleo da engine)
│   │   ├── Game.java           # Classe principal com renderização e loop de game
│   │   └── GameObject.java      # Classe base para objetos do jogo
│   ├── editor/         # Editor visual para modelagem de jogos
│   │   ├── Editor.java          # Editor com interface gráfica
│   │   └── settings.json        # Configurações de layout do editor
│   └── main/           # Aplicação principal (jogo desenvolvido com a engine)
│       └── Main.java
├── .mvn/wrapper/       # Maven Wrapper para builds reproduzíveis
├── pom.xml            # Configuração do Maven
├── mvnw.ps1           # Maven Wrapper script (PowerShell)
├── mvnw.cmd           # Maven Wrapper script (CMD)
├── .gitignore
└── README.md
```

## Componentes

### Core (Motor Gráfico)
O coração da IginisEngine responsável pela renderização e execução.

- Loop de renderização com buffer strategy
- Sistema de tick/render para atualização e desenho
- Suporte a janela redimensionável com modo fullscreen
- Gerenciamento de canvas 2D
- Base para criação de jogos 2D
- Sistema de GameObjects reutilizável

### Editor
Ferramenta visual profissional para desenvolvimento e modelagem de jogos.

- **Interface dividida em painéis:**
  - **Hierarchy:** Árvore de objetos do jogo
  - **Viewport:** Visualização do jogo em tempo real
  - **Inspector:** Propriedades e configurações dos objetos

- **Recursos:**
  - Salvamento automático de layout personalizado
  - Redimensionamento dinâmico de painéis
  - Persistência de preferências do usuário em JSON
  - Menu arquivo com opções de projeto e cena
  - Integração perfeita com o core da engine

### Main
Aplicação principal onde o jogo desenvolvido é executado.

- Projeto final que utiliza core e editor
- Compilação e execução do jogo desenvolvido
- Integra todos os componentes da engine

## Tecnologias

- **Linguagem:** Java 11+
- **Gráficos:** Java 2D (AWT/Swing)
- **Estrutura:** Canvas + JFrame
- **Build System:** Maven 3.9.6
- **Dependências:**
  - org.json (20231013) - Para manipulação de JSON

## Como Usar

### Compilação e Execução com Maven

```bash
# Compilar o projeto
mvnw clean compile

# Executar testes
mvnw test

# Empacotar
mvnw package

# Limpar e instalar
mvnw clean install
```

### Executar na IDE

1. Clone o repositório
2. Abra em sua IDE Java preferida (VS Code, IntelliJ, Eclipse)
3. **Para usar o editor (recomendado):** Compile e execute `src.com.ignis.editor.Editor`
   - A janela será aberta em modo fullscreen
   - O layout dos painéis será salvo automaticamente
   - Ao fechar e reabrir, o layout personalizado será restaurado
4. **Para testar o motor:** Compile e execute `src.com.ignis.core.Game`
5. **Para executar o jogo:** Compile e execute `src.com.ignis.main.Main`

## Configurações do Editor

As configurações de layout do editor são salvas automaticamente em `src/com/ignis/editor/settings.json`:

```json
{
  "mainSplitDividerLocation": 250,
  "rightSplitDividerLocation": 1229
}
```

- **mainSplitDividerLocation:** Posição da divisão entre Hierarchy e painéis direitos (pixels)
- **rightSplitDividerLocation:** Posição da divisão entre Viewport e Inspector (pixels)

## Requisitos

- Java 11 ou superior
- Maven 3.6.0+ (ou use o Maven Wrapper fornecido)

## Licença

Este projeto é parte do desenvolvimento da URSoftware.

---

Status: Em desenvolvimento

