# Guia do Cliente do Marketplace (Marketplace Client Guide)

> Documentação oficial de integração, arquitetura e funcionamento do cliente do Marketplace online no IgnisEngine.

---

## 1. Visão Geral da Arquitetura

O sistema de Marketplace do IgnisEngine conecta o editor visual a um backend web dinâmico, permitindo aos desenvolvedores buscar, baixar e compartilhar plugins de lógica e pacotes de assets (sprites, áudio, prefabs) de forma facilitada.

A arquitetura do Marketplace é composta por três camadas:

1. **Backend Web (`ThyagoToledo/IginisMarketePlace`):** Aplicação Next.js hospedada na Vercel utilizando Neon (PostgreSQL) para banco de dados. Gerencia a autenticação via OAuth com o GitHub, o cadastro de tokens de publicação e a API REST do catálogo.
2. **Cliente Java (`MarketplaceClient`):** Classe singleton in-game (`com.ignis.marketplace.MarketplaceClient`) responsável por realizar requisições HTTP (`java.net.http.HttpClient`) para buscar itens e publicar novos pacotes.
3. **Interface de Usuário (UI):**
   - No editor moderno (JavaFX): Exibida por meio da janela nativa `FxCommunityWindow`.
   - No editor clássico (Swing): Exibida por meio da janela `CommunityFrame`.

---

## 2. Funcionalidades do Marketplace

### A. Catálogo e Busca de Pacotes
- Consome a API do backend para obter a lista atualizada de pacotes.
- **Mecanismo de Fallback (Offline Mode):** Caso a rede do usuário esteja offline, o servidor da Vercel indisponível ou a URL de conexão não configurada, o `MarketplaceClient` detecta a falha silenciosamente e carrega um catálogo de demonstração embutido em cache (`mockCatalog()`). Isso garante que a janela da comunidade nunca trave ou interrompa o uso do editor.

### B. Instalação Segura em 1-Clique
- Ao selecionar um pacote de asset ou plugin no catálogo e clicar em "Instalar", o editor executa o clone direto do repositório Git especificado no campo `gitUrl` do item para a pasta local `plugins/` ou `assets/` do projeto em execução.
- Após o download, a engine notifica a instalação com sucesso e incrementa a contagem de downloads no servidor.

### C. Publicação de Itens com Gate de Segurança
- Desenvolvedores podem enviar seus próprios pacotes para o marketplace utilizando um token de autenticação gerado na interface web.
- O backend executa testes de validação automática nos repositórios enviados antes de disponibilizá-los no catálogo de buscas (verificando a estrutura do projeto, integridade do repositório e presença de códigos maliciosos).

---

## 3. Autenticação e Token de Publicação

Para garantir a autoria e a segurança dos pacotes, o envio de novos itens exige autenticação baseada em tokens persistidos localmente no sistema operacional do usuário por meio da API `java.util.prefs.Preferences` (no nó `com/ignis/marketplace`).

### Ordem de Resolução de Tokens pelo Cliente:
1. Propriedade do sistema Java: `-Dignis.marketplace.token=...` (ideal para pipelines de CI).
2. Variável de ambiente do sistema: `IGNIS_MARKETPLACE_TOKEN`.
3. Preferências persistidas nas configurações de usuário do SO (salvo via interface gráfica do editor).

---

## 4. Endpoints da API REST do Marketplace

O cliente Java interage com o backend Next.js através das seguintes rotas:

| Endpoint | Método | Autenticação | Descrição |
|---|---|---|---|
| `/api/items` | `GET` | Nenhuma | Retorna a lista completa do catálogo em formato JSON. |
| `/api/items` | `POST` | `Bearer <token>` | Publica um novo pacote. Recebe um corpo JSON com metadados e URL Git, além do cabeçalho de autenticação. |
| `/api/items/{id}` | `POST` | Nenhuma | Notifica a realização de um download/instalação de item, incrementando as estatísticas no servidor. |
| `/publish` | `GET` | Nenhuma | Página web para publicação de novos pacotes. |
| `/account` | `GET` | Nenhuma | Área do usuário no site para login via GitHub e geração de tokens. |
