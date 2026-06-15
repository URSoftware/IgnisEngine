# Diretrizes de Documentação — IgnisEngine

Este guia estabelece os padrões e a estrutura de documentação do repositório IgnisEngine. A conformidade com estas diretrizes garante a organização do "Vault" de conhecimento e a apresentação visual premium do projeto.

---

## 1. Estrutura de Documentos

Para manter a raiz do repositório limpa e organizada, aplicamos a regra de **arquivo único na raiz**:

- **README.md (Raiz):** É o único arquivo com extensão `.md` permitido na pasta raiz do projeto. Ele serve como o **Portal e Hub Central** do repositório, contendo a apresentação geral, status do projeto, requisitos mínimos e links organizados para o Vault.
- **Pasta `doc/` (O Vault):** Todos os outros arquivos de documentação (manuais, referências de API, guias de arquitetura, guias de configuração, registros de alterações, código de conduta, etc.) devem residir exclusivamente dentro do diretório `doc/`. Nenhum outro documento markdown deve ser criado fora deste diretório.

---

## 2. Padrão Visual do README Principal

O `README.md` raiz deve possuir uma estética rica e premium para causar um impacto visual profissional. Deve conter os seguintes elementos de formatação HTML:

### A. Banner e Badges de Status
O topo da página deve incluir uma imagem/banner centralizado e badges estilizados que representam as tecnologias, compatibilidade de JDK e status do projeto:
```html
<p align="center">
  <img src="Icons/IgnisEngineBanner.jpg" alt="IgnisEngine Banner" width="250px" />
</p>
```

### B. Tabela de Autores com Avatares do GitHub
Para destacar a equipe de mantenedores e colaboradores, a seção de autores na raiz deve ser formatada como uma tabela HTML centralizada contendo os avatares de perfil com bordas arredondadas e links diretos para seus perfis:
```html
<table align="center">
  <tr>
    <td align="center">
      <a href="https://github.com/ThyagoToledo">
        <img src="https://github.com/ThyagoToledo.png?size=100" width="100px;" alt="Thyago Toledo" style="border-radius: 50%;" /><br />
        <sub><b>Thyago Toledo</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/FeronZerbana">
        <img src="https://github.com/FeronZerbana.png?size=100" width="100px;" alt="FeronZerbana" style="border-radius: 50%;" /><br />
        <sub><b>FeronZerbana</b></sub>
      </a>
    </td>
  </tr>
</table>
```

---

## 3. Padrão Técnico dos Documentos do Vault (`doc/`)

Os arquivos residentes no Vault (`doc/`) devem focar em clareza técnica e precisão. Suas diretrizes incluem:

1. **Linguagem:** Escritos de forma clara e formal em português do Brasil (pt-BR).
2. **Sem Emojis:** Evitar o uso de emojis no conteúdo dos arquivos técnicos do Vault para manter o tom profissional da documentação de engenharia.
3. **Alertas Estruturados:** Utilizar blocos de alerta padrão do GitHub (`> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, `> [!CAUTION]`) para destacar notas de implementação, advertências e boas práticas.
