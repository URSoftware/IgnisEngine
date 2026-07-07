# Configuração do Ambiente de Desenvolvimento (Developer Setup)

> Guia oficial para configuração de ambiente, compilação e execução do IgnisEngine.

---

## 1. Requisitos do Sistema

Antes de iniciar, certifique-se de ter instalado em sua máquina:
- **Java Development Kit (JDK) 17 ou superior:** A engine é construída utilizando Java 17 LTS. Recomendamos distribuições como Adoptium Eclipse Temurin ou Azul Zulu.
- **Git:** Para clonar o repositório principal e gerenciar os submódulos de código.
- **Maven:** O projeto já inclui o Maven Wrapper (`mvnw`), portanto não é necessário instalar o Maven globalmente no sistema.

---

## 2. Clonagem e Inicialização do Repositório

O projeto utiliza um submódulo Git para o marketplace. Para clonar o projeto completo com seus submódulos corretos, execute:

```bash
# Clonar o repositório principal
git clone https://github.com/URSoftware/IgnisEngine.git
cd IgnisEngine

# Inicializar e atualizar os submódulos
git submodule update --init --recursive
```

Se você já clonou o projeto sem o parâmetro dos submódulos, execute o seguinte comando dentro da pasta do projeto para carregar a pasta `marketplace/`:
```bash
git submodule update --init --recursive
```

---

## 3. Execução do Editor

O editor oficial da branch `main` é o JavaFX. O editor clássico Swing foi removido do `main` em 05/07/2026 e permanece disponível apenas na branch `Legado` (para rodá-lo, faça checkout dessa branch e siga o README dela).

**No Windows (Script Facilitador):**
```cmd
run-editor-javafx.bat
```
*(Este script localiza automaticamente um JDK 17+ instalado no seu sistema, corrigindo problemas caso a variável JAVA_HOME esteja apontando para um JRE ou um SDK separado do JavaFX).*

**Via Maven Wrapper (Qualquer SO):**
```bash
./mvnw javafx:run
```

---

## 4. Estrutura de Diretórios do Projeto

```text
IgnisEngine/
├── .github/              # Configurações de workflows e CI/CD
├── .mvn/                 # Binários e configurações do Maven Wrapper
├── doc/                  # Documentações técnicas do projeto (Vault)
├── Icons/                # Recursos visuais utilizados pelo editor
├── marketplace/          # Submódulo do repositório web do Marketplace (Next.js)
├── projects/             # Pasta local contendo projetos criados na engine
├── src/                  # Código-fonte Java da engine e editores
│   └── com/ignis/
│       ├── core/         # Ciclo de vida, lógica e componentes do jogo
│       │   └── ui/       # Componentes de interface in-game desenhados no canvas
│       ├── editor/       # Integração com IA e janelas auxiliares do editor
│       │   └── fx/       # Classes do editor JavaFX (principal)
│       ├── imageeditor/  # Sub-editor de pintura de imagens
│       ├── audioeditor/  # Sub-editor de DAW de áudio
│       ├── animation/    # Sistemas de animações
│       ├── builder/      # Compilador e empacotador de projetos
│       └── runtime/      # Player autônomo para rodar jogos prontos
├── pom.xml               # Arquivo de configuração de build do Maven
├── run-editor-javafx.bat # Script de execução facilitada no Windows
└── README.md             # Documento de apresentação do projeto
```

---

## 5. Configuração da IDE

### IntelliJ IDEA (Recomendado)
1. Abra a IDE e selecione **Open**.
2. Navegue até o diretório raiz do `IgnisEngine` e selecione o arquivo `pom.xml`.
3. Escolha abrir como projeto (**Open as Project**).
4. Em **File > Project Structure > Project**:
   - Defina o **SDK** para o JDK 17+.
   - Defina o **Language Level** para 17.
5. Deixe o IntelliJ importar as dependências do Maven automaticamente.
6. Para executar, crie uma configuração de execução do tipo **Maven** com a linha de comando `javafx:run`.

### VS Code
1. Certifique-se de ter instalado o pacote **Extension Pack for Java** e **Maven for Java**.
2. Abra a pasta raiz do projeto no VS Code.
3. A IDE detectará o projeto Maven automaticamente e iniciará a compilação.
4. Para executar o editor JavaFX, você pode abrir o terminal integrado e executar `./mvnw javafx:run`.

---

## 6. Resolução de Problemas (Troubleshooting)

### Erro: JDK não encontrado ou versão incorreta
Se ao rodar você receber um erro informando que o compilador ou o Java não é compatível (ex: classe com versão unsupported), verifique sua instalação:
- Certifique-se de que a variável de ambiente `JAVA_HOME` aponta para um diretório de instalação do **JDK 17 ou superior** (e não um JRE).
- No Windows, use o arquivo `run-editor-javafx.bat` que tenta encontrar caminhos comuns de JDKs instalados automaticamente no sistema se o seu `JAVA_HOME` padrão falhar.

### Erro de Módulos JavaFX (Graphics/Controls não encontrados)
Se o editor JavaFX travar ao iniciar com erros de inicialização de módulos (`Graphics/Controls not found` ou `Toolkit not initialized`):
- Verifique se a sua versão do JDK é compatível.
- Execute um ciclo de limpeza no Maven para forçar o download correto das dependências nativas correspondentes ao seu sistema operacional:
  ```bash
  ./mvnw clean compile
  ```

### Erro com permissão do Maven Wrapper no Linux/macOS
Se você tentar rodar `./mvnw` e receber um erro de permissão negada (`Permission denied`):
- Adicione permissão de execução ao arquivo:
  ```bash
  chmod +x mvnw
  ```
