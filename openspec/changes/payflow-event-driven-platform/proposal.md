## Why

Portfólios Java comuns demonstram apenas CRUD com Spring Data JPA, o que não diferencia engenheiros sêniores. O PayFlow resolve isso construindo uma plataforma de pagamentos production-grade que demonstra maturidade arquitetural através de Event Sourcing, CQRS, Saga pattern e observabilidade real — implementada em paralelo com Spring Boot e Micronaut para permitir comparação honesta de trade-offs entre as duas stacks JVM mais relevantes do mercado.

## What Changes

- **Novo monorepo** `payflow/` com estrutura multi-módulo Maven
- **Módulo `shared/domain`**: lógica de domínio em Java puro (sem framework), contendo aggregates `Account` e `Transfer`, todos os domain events, e a saga de transferência
- **4 serviços**: `account-spring`, `transfer-spring`, `account-micronaut`, `transfer-micronaut` — adapters sobre o mesmo domínio
- **Event Store** persistido em PostgreSQL com suporte a replay e versionamento de eventos
- **Saga de transferência orquestrada** com compensating transactions automáticas e idempotência garantida via `Idempotency-Key`
- **Read Model (projeções)** atualizadas assincronamente via Kafka
- **Observabilidade completa**: traces distribuídos (OpenTelemetry + Jaeger), métricas de negócio (Micrometer + Prometheus), dashboards Grafana
- **Benchmarks documentados**: startup time, memória RSS e latência p99 comparando Spring vs Micronaut
- **ADRs obrigatórios** (5 documentos) cobrindo as principais decisões arquiteturais
- **CI/CD** com GitHub Actions, Testcontainers e k6 para testes de carga

## Capabilities

### New Capabilities

- `account-management`: Gestão de contas com criação, consulta de saldo via projeção, histórico via event replay e depósitos — implementado em Spring Boot e Micronaut
- `transfer-saga`: Fluxo completo de transferência entre contas com saga orquestrada, compensating transactions, idempotência e consulta de status — implementado em Spring Boot e Micronaut
- `event-store`: Persistência imutável de domain events no PostgreSQL com suporte a replay, versionamento de schema e optimistic locking por aggregate
- `event-projector`: Consumidor Kafka stateless que mantém read model atualizado (saldo atual e histórico de movimentações) e pode reconstruir projeções do zero via replay
- `observability`: Instrumentação com OpenTelemetry para traces distribuídos, métricas de negócio via Micrometer e dashboards Grafana pré-configurados
- `infrastructure-setup`: Docker Compose com Kafka, PostgreSQL, Jaeger, Prometheus e Grafana; configuração de tópicos e CI/CD com GitHub Actions

### Modified Capabilities

## Impact

- **Novo projeto do zero** — não há código existente a ser modificado
- **APIs REST** expostas por 4 serviços independentes (account e transfer, em Spring e Micronaut)
- **Dependências externas**: Apache Kafka 3.7, PostgreSQL 16, OpenTelemetry, GraalVM 21 (para native image Micronaut)
- **Build**: Maven multi-module com Java 21 LTS
- **Testes**: Testcontainers (Kafka + Postgres reais), Spring Cloud Contract para contratos de evento entre serviços
