# Política de Segurança (Security Policy)

> Diretrizes oficiais para reporte de vulnerabilidades e manutenção da integridade da segurança do ecossistema IgnisEngine.

---

## 1. Versões Suportadas

Abaixo estão listadas as ramificações de código (branches) que ativamente recebem correções de falhas de segurança:

| Branch | Versão | Suportada |
|---|---|---|
| **`main`** | `>= 1.0.0` | Sim (Foco Principal) |
| **`Legado`** | `< 1.0.0` | Apenas correções críticas |

Recomendamos que todos os desenvolvedores utilizem sempre a versão estável mais recente baseada na branch `main`.

---

## 2. Como Reportar uma Vulnerabilidade

Se você descobrir uma falha de segurança na IgnisEngine ou no Marketplace associado, **não** abra uma Issue pública. Em vez disso, relate a falha de forma privada seguindo o processo de divulgação responsável:

### Processo de Envio:
1. Envie um e-mail descrevendo detalhadamente a falha identificada para a equipe de segurança da URSoftware em: `security@ursoftware.org` (ou utilize o canal oficial de contato dos mantenedores).
2. Forneça no e-mail:
   - Uma descrição clara da vulnerabilidade.
   - Passos detalhados para reproduzir o problema (Proof of Concept - PoC).
   - O impacto potencial da falha (como roubo de tokens de publicação ou execução remota de código por meio de scripts de plugins).

---

## 3. Nosso Compromisso de Resposta

Ao receber um reporte de vulnerabilidade, a equipe de desenvolvimento da URSoftware compromete-se a:

1. **Confirmação de Recebimento:** Responder ao e-mail original em até **48 horas**, acusando o recebimento da notificação.
2. **Avaliação Técnica:** Analisar a severidade técnica e o impacto em nosso ecossistema.
3. **Plano de Correção:** Criar uma correção de código (patch) de forma privada.
4. **Divulgação e Correção:** Publicar a nova versão de patch da engine contendo a correção de segurança e divulgar o agradecimento ao pesquisador de segurança nas notas de release.
