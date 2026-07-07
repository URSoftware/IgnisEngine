# Guia de Contribuição (Contributing Guide)

> Obrigado pelo interesse em contribuir com a IgnisEngine! Este documento serve de guia para configurar o ambiente, compreender as estratégias de desenvolvimento e enviar suas contribuições.

---

## 1. Estratégia de Branches e Fluxo de Trabalho

O projeto é organizado sob duas ramificações (branches) principais:

- **`main` (Padrão):** É a linha de desenvolvimento ativa e de vanguarda. Focada na migração da casca visual do editor para **JavaFX 17** e futuras paridades funcionais. Toda melhoria geral deve ter como alvo esta branch.
- **`Legado`:** Contém a versão estável original com a interface visual construída em Swing. Esta branch é mantida como um ponto de retorno seguro para correções de bugs críticos do editor clássico. Novas features de grande porte **não** devem ser enviadas para ela.

### Passos para Contribuir:
1. Faça um **Fork** do repositório.
2. Crie uma branch para a sua alteração a partir da `main`:
   ```bash
   git checkout -b feature/sua-melhoria
   # ou para correcoes:
   git checkout -b bugfix/sua-correcao
   ```
3. Implemente e teste suas mudanças localmente.
4. Faça o commit e envie para o seu Fork.
5. Abra um **Pull Request (PR)** apontando para a branch `main` do repositório oficial.

---

## 2. Padrões de Código (Coding Style)

- **Versão do Java:** O projeto utiliza e compila com **Java 17**. Não utilize APIs ou facilidades sintáticas de versões mais recentes (como Java 21) que impeçam a compilação no Java 17.
- **Formatação:** Mantenha a consistência do código existente (espaçamentos, identação padrão de 4 espaços).
- **Tratamento de Threads:** Lembre-se da regra de ouro do multithreading da engine (detalhado em `doc/THREADING_MODEL.md`):
  - Modificações em componentes visuais JavaFX devem rodar na thread de UI através de `Platform.runLater()`.
  - Evite locks síncronos longos que possam travar o game loop ou congelar a interface.

---

## 3. Padrão de Mensagens de Commit

Utilizamos o padrão de **Conventional Commits** adaptado para a língua portuguesa para manter o histórico de alterações limpo e legível. Exemplos de prefixos suportados:

- `feat:` Nova funcionalidade sendo introduzida (ex: `feat: adicionado suporte a gradiente de cor em UIButton`).
- `fix:` Correção de um bug (ex: `fix: corrigido travamento ao carregar colisores circulares vazios`).
- `docs:` Alterações exclusivamente em arquivos de documentação (ex: `docs: atualizado guia de instalacao do jdk`).
- `style:` Mudanças de formatação e estilo que não afetam a lógica do código (ex: `style: organizacao de imports em Game.java`).
- `refactor:` Alteração que melhora a estrutura do código sem mudar seu comportamento externo.

Exemplo de mensagem de commit:
```text
feat: adicionada funcao de tremor de camera no IgnisScript

- Implementado metodo cameraShake(intensidade, duracao)
- Adicionados testes basicos de duracao do tremor
- Documentada a funcao no guia de camera
```

---

## 4. A Regra de Ouro do Arquivo `.cursorrules`

> [!IMPORTANT]
> Ao finalizar qualquer bloco de trabalho coerente e estável, é obrigatório realizar o **Commit** e **Push** das alterações para o repositório remoto. Isso garante a segurança do código desenvolvido e evita perda de progresso em sessões ou ambientes remotos.

---

## 5. Executando e Testando Localmente

Antes de enviar sua contribuição, verifique se tudo compila e roda corretamente:

```bash
# Limpar compilações antigas e compilar o projeto completo
./mvnw clean compile

# Rodar o editor nativo JavaFX
./mvnw javafx:run
```

O editor Swing legado foi removido da branch `main` (05/07/2026); para testes de paridade use a branch `Legado`.


---

## 6. Onde Obter Ajuda

- **Central de Documentação (Vault):** A pasta `doc/` contém guias completos sobre todos os subsistemas da engine.
- **Relatório de Auditorias:** O arquivo `doc/PROJECT_INVENTORY.md` lista todos os arquivos do projeto e seu estado de conclusão.
- **Comunidade:** Use a aba da comunidade (`FxCommunityWindow`) integrada ao editor para conectar-se ao fórum e ao marketplace.
