# Guia de Registros de Decisões de Arquitetura (Architecture Decision Records - ADR)

> Este guia descreve o que são ADRs, por que são essenciais para o ciclo de vida do projeto e como criar novos registros na IgnisEngine.

---

## 1. O que é um ADR?

Um **Registro de Decisão de Arquitetura (ADR)** é um documento curto de texto puro (Markdown) que captura uma decisão de design significativa, juntamente com o contexto técnico no qual foi tomada e as consequências que ela traz para o projeto.

### Por que ADRs são essenciais?
- **Preservação de Contexto:** Evitam a síndrome do "por que isso foi feito assim?", documentando os motivos que levaram a uma escolha técnica (evitando que novos desenvolvedores tentem reverter decisões sem entender os trade-offs passados).
- **Alinhamento de Equipe:** Servem como histórico oficial de decisões aprovadas pela equipe de arquitetura.
- **Ramp-up de Agentes de IA:** Permitem que assistentes de codificação IA compreendam as diretrizes arquiteturais do projeto de forma rápida e precisa.

---

## 2. Estrutura Padrão de um ADR

Cada arquivo ADR deve ser armazenado na pasta `doc/adr/` com a nomenclatura sequencial e descritiva (ex: `doc/adr/0001-migracao-javafx.md`).

O documento deve seguir esta estrutura de tópicos:

```markdown
# ADR [Número]: [Título Curto da Decisão]

- **Data:** [AAAA-MM-DD]
- **Status:** [Proposto / Aprovado / Rejeitado / Superado por ADR-XXXX]
- **Autor(es):** [Nomes]

## Contexto
Qual é o problema de engenharia ou de design de software que estamos tentando resolver?
Quais eram os fatores limitantes, requisitos de negócios e trade-offs técnicos vigentes na época?

## Decisão
Qual foi a solução técnica adotada? Descreva com clareza a decisão tomada e as alternativas descartadas.

## Consequências
Quais são as implicações positivas e negativas desta decisão?
O que muda na manutenção do código, na performance ou no processo de build a partir de agora?
```

---

## 3. Fluxo de Criação de ADRs

1. **Proposta:** Um desenvolvedor propõe uma grande mudança de arquitetura criando um ADR com status `Proposto`.
2. **Revisão:** A equipe de desenvolvimento discute a proposta em Pull Requests ou fóruns.
3. **Decisão:** O ADR é atualizado para `Aprovado` (e mergeado na `main`) ou `Rejeitado`.
4. **Evolução:** Decisões futuras que alterem uma escolha anterior criam um novo ADR (ex: `0002-uso-de-graalvm.md`) que declara formalmente que supera o ADR anterior (ex: "Supera o ADR-0001").
