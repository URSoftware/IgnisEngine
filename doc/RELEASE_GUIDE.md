# Guia de Versionamento e Releases (Release & Versioning Guide)

> Guia oficial detalhando o processo de numeração de versões, geração de executáveis distribuíveis e publicação de releases na IgnisEngine.

---

## 1. Padrão de Versionamento (SemVer 2.0.0)

A IgnisEngine segue rigorosamente o padrão de **Versionamento Semântico (Semantic Versioning 2.0.0)**. O número da versão deve ser composto por três dígitos separados por pontos: `MAJOR.MINOR.PATCH`.

- **`MAJOR` (Versão Maior):** Incrementado quando são realizadas alterações incompatíveis com versões anteriores (breaking changes), tais como mudanças na sintaxe do IgnisScript ou no formato do arquivo `.ignis`.
- **`MINOR` (Versão Menor):** Incrementado quando novas funcionalidades compatíveis com versões anteriores são introduzidas (ex: novos componentes de UI in-game, novos sub-editores).
- **`PATCH` (Correção de Bug):** Incrementado quando são realizadas correções de bugs retrocompatíveis.

### Sufixos de Desenvolvimento:
- `-SNAPSHOT`: Indica uma versão em desenvolvimento ativo (ex: `1.0.1-SNAPSHOT`). Versões do Maven na branch `main` devem conter este sufixo.
- `-RC` (Release Candidate): Uma versão estável candidata à publicação final (ex: `1.1.0-RC1`).

---

## 2. Fluxo de Publicação de uma Release

Quando uma versão estável na branch `main` atinge a maturidade necessária para lançamento, as etapas abaixo devem ser seguidas para realizar a publicação:

### Passo 1: Preparação e Testes
1. Execute a suíte completa de testes locais e verifique se o projeto compila sem erros:
   ```bash
   ./mvnw clean compile
   ```
2. Certifique-se de que a branch `main` está atualizada com todos os PRs de funcionalidades desejadas integrados.

### Passo 2: Atualização de Versão no pom.xml
1. Remova o sufixo `-SNAPSHOT` do arquivo `pom.xml` para definir a versão de release final (ex: de `1.0.0-SNAPSHOT` para `1.0.0`).
2. Atualize o cabeçalho do `README.md` e os arquivos de inventário se necessário.

### Passo 3: Criação de Tag Git
1. Realize o commit das alterações de versão:
   ```bash
   git commit -am "chore: preparando release v1.0.0"
   ```
2. Crie uma tag git anotada correspondente à versão:
   ```bash
   git tag -a v1.0.0 -m "Release oficial da versao 1.0.0"
   ```
3. Envie a tag para o repositório remoto:
   ```bash
   git push origin main --tags
   ```

### Passo 4: Geração de Distribuíveis (Build)
1. Use o compilador integrado (Builder) no editor JavaFX (`FxBuildDialog`) ou execute o Maven para empacotar os arquivos finais JAR:
   ```bash
   ./mvnw clean package
   ```
2. Colete os executáveis e zip gerados na pasta de build (`target/`) para disponibilizar aos usuários finais.

### Passo 5: Atualização do Changelog e Release Notes
1. Atualize o arquivo `CHANGELOG.md` na raiz do projeto contendo a descrição de todas as melhorias e correções feitas em relação à versão anterior.
2. Crie uma nova **Release** no GitHub baseada na tag Git gerada e faça o upload dos arquivos binários finais.

---

## 3. Retomando o Desenvolvimento (Próxima Versão)

Imediatamente após o lançamento da release estável, a branch `main` deve ser atualizada para apontar para a próxima versão de desenvolvimento (incrementando o dígito `PATCH` ou `MINOR` e reinserindo o sufixo `-SNAPSHOT`):

```bash
# Exemplo: Atualizando de 1.0.0 para 1.0.1-SNAPSHOT no pom.xml
git commit -am "chore: Bump de versao para 1.0.1-SNAPSHOT"
git push origin main
```
