# Roadmap do IgnisEngine

Planejamento oficial de evolução do motor gráfico. Todos os módulos devem ser implementados como componentes desacoplados, permitindo manutenção independente, expansão futura e compatibilidade com novas plataformas.

---

## Prioridade de Implementação

| # | Módulo | Status |
|---|--------|--------|
| 1 | Builder multiplataforma (Java) | Implementado — ver [BUILDER_GUIDE.md](BUILDER_GUIDE.md) |
| 2 | Sistema de exportação para C++ | Implementado (esqueleto compilável; portagem do loop nativo pendente) |
| 3 | Editor de imagens integrado | Implementado (v1) — ver [IMAGE_EDITOR_GUIDE.md](IMAGE_EDITOR_GUIDE.md); seleção/transformação pendentes |
| 4 | Sistema de animações (2D/3D) | Planejado |
| 5 | Integração Gemini (expansão do Agent Mode) | Parcial — base existente |
| 6 | Editor de áudio (DAW) | Planejado |
| 7 | Sistema de notas/documentação | Planejado |
| 8 | Plataforma Comunidade/Workshop | Planejado |
| 9 | Marketplace de plugins e assets | Planejado |
| 10 | Infraestrutura Vercel e catálogo online | Planejado |

---

## 1. Builder de Jogos

Sistema responsável pela geração dos binários finais dos jogos criados no motor.

### Objetivos

- Gerar builds para múltiplas plataformas.
- Automatizar o processo de empacotamento.
- Permitir configurações específicas por plataforma.
- Preparar o motor para publicação comercial.

### Arquitetura

O motor principal é desenvolvido em Java, porém consoles como Xbox e PlayStation não possuem suporte nativo à JVM. O Builder terá duas estratégias de compilação:

#### Build Java (Prioridade 1)

Distribuições compatíveis com JVM para:

- Windows
- Linux
- macOS

#### Build Nativo C++ (Prioridade 2)

Camada de tradução/exportação para C++, destinada a:

- Xbox
- PlayStation
- Nintendo Switch (planejamento futuro)
- Outras plataformas sem suporte à JVM

Responsabilidades do sistema de exportação:

- Converter estruturas necessárias do projeto.
- Exportar código intermediário.
- Gerar projetos C++ compiláveis.
- Permitir integração com SDKs específicos de cada console futuramente.

---

## 2. Editor de Imagens Integrado

Editor gráfico nativo acoplado ao motor.

### Funcionalidades

- Desenho 2D.
- Pintura digital.
- Edição de sprites.
- Camadas.
- Ferramentas de seleção.
- Ferramentas de transformação.
- Exportação de texturas.
- Integração direta com assets do projeto.

---

## 3. Sistema de Animação

Módulo completo de animação para conteúdo 2D e 3D.

### 2D

- Timeline.
- Keyframes.
- Sprite animation.
- Blend de animações.

### 3D

- Skeletal animation.
- Animation graph.
- State machine.
- Retargeting.
- Blend trees.

---

## 4. Integração com Gemini

Módulo de Inteligência Artificial baseado no Gemini, expandindo a base já existente do Agent Mode (ver [AI_INTEGRATION_GUIDE.md](AI_INTEGRATION_GUIDE.md) e [AGENT_MODE_GUIDE.md](AGENT_MODE_GUIDE.md)).

### Funcionalidades

- Tela para configuração de API Key.
- Armazenamento seguro da chave.
- Assistente integrado ao editor.
- Geração de scripts.
- Auxílio em programação.
- Geração de documentação.
- Sugestões de otimização.
- Auxílio na criação de assets e fluxos de trabalho.

A arquitetura deverá permitir integração futura com outros provedores de IA.

---

## 5. Editor de Áudio

Módulo semelhante a um DAW (Digital Audio Workstation).

### Funcionalidades

- Edição de áudio.
- Gravação.
- Mixagem.
- Timeline multipista.
- Efeitos sonoros.
- Equalização.
- Automação.
- Exportação de áudio.
- Integração com o sistema de áudio do motor.

Objetivo: permitir criação e edição de trilhas sonoras e efeitos sem depender de software externo.

---

## 6. Sistema de Notas

Após a implementação do sistema semelhante ao Notion, expandir para um sistema interno de documentação.

### Funcionalidades

- Páginas hierárquicas.
- Banco de conhecimento.
- Wiki do projeto.
- Organização de tarefas.
- Documentação técnica.
- Links entre páginas.
- Integração com IA.

---

## 7. Comunidade e Marketplace

Aba "Comunidade" para compartilhamento de assets, plugins e extensões criados pela comunidade.

### Backend (Vercel)

Serviço hospedado na Vercel responsável por:

- Receber apenas URLs de repositórios Git.
- Validar os repositórios.
- Armazenar metadados.
- Indexar projetos publicados.

### Assets

Compartilhamento de:

- Texturas.
- Modelos 3D.
- Sons.
- Materiais.
- Animações.

### Plugins

Compartilhamento de:

- Extensões do motor.
- Sistemas de gameplay.
- Ferramentas de produtividade.
- Bibliotecas reutilizáveis.

### Workshop

Inspirado na Steam Workshop. Cada publicação deverá possuir:

- Nome.
- Descrição.
- Autor.
- URL do repositório Git.
- Imagem de capa via URL.
- Versão.
- Dependências.
- Avaliações futuras.

### Instalação

O usuário poderá:

- Navegar pelo catálogo.
- Instalar com um clique.
- Atualizar automaticamente.
- Remover conteúdos instalados.

### Segurança

- Validação de repositórios.
- Verificação de dependências.
- Sandbox para plugins.
- Sistema de permissões.
- Análise de integridade antes da instalação.

---

## Diretrizes Arquiteturais

- Todos os módulos devem funcionar como componentes desacoplados.
- Manutenção independente por módulo.
- Compatibilidade com novas plataformas.
- Nenhuma implementação deve contradizer as decisões registradas no Vault de documentação.
