# PRD — PayFlow: Plataforma de Pagamentos Event-Driven

**Versão:** 1.0  
**Status:** Draft  
**Autor:** Mayco Aoki  
**Última atualização:** 2026-04

---

## 1. Visão Geral

PayFlow é uma plataforma de pagamentos fictícia (mas production-grade) que demonstra domínio em arquitetura de microserviços event-driven com CQRS/Event Sourcing, implementada em duas stacks JVM lado a lado: **Spring Boot** e **Micronaut**.

O objetivo não é construir um produto — é construir um argumento técnico convincente para recrutadores, tech leads e engenheiros sêniores que avaliam portfólios Java/Kotlin.

---

## 2. Problema que o Projeto Resolve (para o Portfólio)

Portfólios Java comuns mostram CRUD com Spring Data JPA. Isso não diferencia um sênior. O que diferencia:

- Decisões de arquitetura documentadas com trade-offs explícitos
- Event Sourcing aplicado a um domínio onde ele faz sentido real (auditoria financeira)
- Comparação prática e honesta entre Spring e Micronaut no mesmo domínio
- Observabilidade de verdade, não apenas logs no console
- Testes que provam que a arquitetura funciona, não apenas que o código compila

---

## 3. Personas do Portfólio

| Persona | O que procura | O que vai encontrar no PayFlow |
|---|---|---|
| **Tech Lead** avaliando candidatos | Maturidade arquitetural, decisões conscientes | ADRs, diagramas C4, justificativas de design |
| **Engenheiro Sênior** fazendo code review | Qualidade de código, cobertura de edge cases | Testes de contrato, testes de saga, mutation testing |
| **Recrutador técnico** triando perfis | Stack moderna, palavras-chave relevantes | README com badges, arquitetura visual, benchmarks |
| **CTO / Hiring Manager** de fintech | Conhecimento do domínio financeiro | Idempotência, compensating transactions, auditoria |

---

## 4. Escopo do MVP (6 semanas)

### 4.1 Domínio de Negócio

Sistema de transferências entre contas com os seguintes fluxos:

1. **Criação de conta** — cadastro com validação e saldo inicial
2. **Depósito** — crédito em conta com registro de evento
3. **Transferência** — débito + crédito orquestrado via saga
4. **Consulta de extrato** — projeção do read model via eventos
5. **Estorno** — compensating transaction com rastreabilidade completa

### 4.2 Fora do Escopo (MVP)

- Interface gráfica — não é projeto frontend
- Autenticação real de usuários — mock JWT é suficiente para o showcase
- Integração com bancos reais ou PSPs
- Multi-tenancy

### 4.3 Critérios de Sucesso

- [ ] Fluxo de transferência completo com saga e rollback funcionando
- [ ] Event store persistindo e reproduzindo estado via replay
- [ ] Read model atualizado de forma assíncrona via eventos Kafka
- [ ] Benchmark documentado: Spring vs Micronaut (latência p99, memória RSS, startup time)
- [ ] Cobertura ≥ 80% na lógica de domínio
- [ ] README que qualquer engenheiro sênior consegue executar em menos de 5 minutos

---

## 5. Requisitos Funcionais

### RF-01: Gestão de Contas
- Criar conta com saldo inicial (pode ser zero)
- Consultar saldo atual via projeção (não query direta no banco)
- Histórico completo de movimentações via event replay

### RF-02: Transferências
- Transferência é atômica do ponto de vista do negócio
- Deve garantir idempotência — retry seguro com mesmo `transferId`
- Em caso de falha parcial, compensating transaction disparada automaticamente
- Status da transferência consultável a qualquer momento

### RF-03: Event Store
- Todos os eventos de domínio persistidos imutavelmente
- Suporte a replay de eventos para reconstrução de estado
- Eventos versionados para suportar evolução de schema

### RF-04: Observabilidade
- Trace distribuído cobrindo o fluxo completo de transferência
- Métricas de negócio expostas: transferências/segundo, taxa de falha de saga
- Health checks diferenciados: liveness vs readiness

---

## 6. Requisitos Não-Funcionais

| Requisito | Meta | Justificativa |
|---|---|---|
| Latência p99 (transferência) | < 500ms | Benchmark realista para ambiente local |
| Startup time (Micronaut native) | < 500ms | Showcase da vantagem do Micronaut |
| Startup time (Spring JVM) | < 3s | Baseline honesto |
| Cobertura de testes (domínio) | ≥ 80% | Disciplina, não gaming de métrica |
| Idempotência nas transferências | 100% | Requisito crítico em fintech |

---

## 7. ADRs Obrigatórios

Os ADRs são parte central do portfólio — tech leads lêem ADRs para avaliar maturidade de pensamento.

- **ADR-001:** Por que Event Sourcing para este domínio?
- **ADR-002:** Orquestração vs Coreografia para a saga de transferência
- **ADR-003:** Por que Kafka e não RabbitMQ ou Redis Streams?
- **ADR-004:** Estratégia de versionamento de eventos
- **ADR-005:** Trade-offs Spring Boot vs Micronaut — análise pós-implementação

---

## 8. O que Foi Deixado de Fora (e Por Quê)

Documentar o que não foi feito — com justificativa — é sinal de senioridade.

- **Sharding do event store:** desnecessário em escala de demo; mencionado como próximo passo natural
- **LGPD compliance:** a arquitetura suporta (eventos imutáveis + projeções deletáveis), implementação não incluída
- **Rate limiting e anti-fraud:** mencionados no README como extensões naturais da arquitetura
- **GraalVM native para Spring:** custo de configuração desproporcional para o objetivo; Micronaut native cobre o ponto
