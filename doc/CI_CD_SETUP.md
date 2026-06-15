# Configuração de Integração Contínua (CI/CD Setup)

> Diretrizes e plano de implementação de pipelines de Integração Contínua (CI) e Entrega Contínua (CD) para o IgnisEngine utilizando GitHub Actions.

---

## 1. Estado Atual

Atualmente, o projeto **não possui** um pipeline de CI/CD configurado. 
As etapas de build, empacotamento e teste são realizadas de forma manual localmente pelas seguintes ferramentas:
- Compilação via Maven (`./mvnw clean compile`).
- Testes locais.
- Geração de builds através do sub-módulo Builder integrado ao editor visual.

A ausência de um pipeline automatizado aumenta o risco de introdução de regressões de código, erros de compilação em Pull Requests e atrasos no processo de release.

---

## 2. Proposta de Pipeline de Integração Contínua (CI)

Propomos a adoção de um workflow do **GitHub Actions** focado em validar commits e Pull Requests automaticamente na branch `main`.

### Objetivos do Pipeline de CI:
- **Verificação de Compilação:** Compilar a engine em múltiplos ambientes para detectar códigos que quebrem o build.
- **Matriz de Versões do JDK:** Garantir a compatibilidade do código nas versões Java 17 (mínima suportada) e Java 21 (LTS mais recente).
- **Cache de Dependências:** Otimizar o tempo de execução do pipeline guardando em cache as dependências do Maven (como `org.json` e as dependências nativas do JavaFX).

---

## 3. Configuração do Workflow YAML Proposto

Para ativar a integração contínua no repositório, sugere-se a criação do arquivo `.github/workflows/ci.yml` com a seguinte estrutura:

```yaml
name: Java CI with Maven

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    name: Build & Verify (JDK ${{ matrix.java-version }})
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java-version: [ '17', '21' ]

    steps:
    # 1. Checkout do código-fonte, incluindo submódulos
    - name: Checkout Repository
      uses: actions/checkout@v4
      with:
        submodules: recursive

    # 2. Configuração do ambiente Java
    - name: Set up JDK ${{ matrix.java-version }}
      uses: actions/setup-java@v4
      with:
        java-version: ${{ matrix.java-version }}
        distribution: 'temurin'
        cache: 'maven'

    # 3. Dar permissão de execução ao Maven Wrapper
    - name: Grant execute permission for mvnw
      run: chmod +x mvnw

    # 4. Execução da compilação e empacotamento
    - name: Build with Maven
      run: ./mvnw clean package -DskipTests
```

---

## 4. Próximos Passos e Futuro do Pipeline

### A. Integração de Testes Automatizados
Assim que a suíte de testes unitários (`JUnit`) for implementada para o Core da engine (especialmente para a serialização `.ignis` e a simulação de colisões), o comando do pipeline deve ser alterado para:
```bash
./mvnw clean verify
```
Isso assegura que nenhuma alteração de código quebre as regras físicas ou de carregamento de arquivos do motor.

### B. Entrega Contínua (CD) e Release de Builds
Em fases mais avançadas do projeto, propõe-se um segundo workflow executado em tags de versão (`v*`):
1. **Geração Automatizada de Executáveis:** O Builder seria invocado via linha de comando no GitHub Actions para gerar os empacotamentos prontos para distribuição.
2. **Release Drafts:** Upload dos executáveis gerados diretamente no GitHub Releases de forma automatizada.
