# SPEC — PayFlow: Especificação Técnica

**Versão:** 1.0  
**Status:** Draft  
**Relacionado ao:** PRD v1.0  
**Última atualização:** 2026-04

---

## 1. Estrutura do Monorepo

```
payflow/
├── README.md                        # Visão geral, como rodar, benchmarks
├── docs/
│   ├── architecture/
│   │   ├── c4-context.md
│   │   ├── c4-container.md
│   │   └── c4-component-transfer.md
│   └── adr/
│       ├── ADR-001-event-sourcing.md
│       ├── ADR-002-saga-orchestration.md
│       ├── ADR-003-kafka-choice.md
│       ├── ADR-004-event-versioning.md
│       └── ADR-005-spring-vs-micronaut.md
├── shared/
│   └── domain/                      # Módulo Java puro — sem framework
│       ├── src/main/java/payflow/domain/
│       │   ├── account/             # Aggregate Account
│       │   ├── transfer/            # Aggregate Transfer + Saga
│       │   └── events/              # Todos os domain events
│       └── pom.xml
├── services/
│   ├── account-spring/              # Implementação Spring Boot
│   ├── transfer-spring/             # Implementação Spring Boot
│   ├── account-micronaut/           # Implementação Micronaut
│   └── transfer-micronaut/          # Implementação Micronaut
├── infra/
│   ├── docker-compose.yml           # Kafka, Postgres, Jaeger, Prometheus, Grafana
│   └── grafana/
│       └── dashboards/
│           └── payflow-overview.json
├── benchmarks/
│   ├── k6/
│   │   └── transfer-load.js
│   └── results/
│       └── README.md                # Resultados documentados com contexto
└── .github/
    └── workflows/
        ├── ci-spring.yml
        └── ci-micronaut.yml
```

**Decisão de design:** O módulo `shared/domain` contém toda a lógica de domínio em Java puro, sem dependência de framework. Os serviços Spring e Micronaut são apenas adapters. Isso demonstra conhecimento de ports & adapters (hexagonal) e garante que a comparação entre stacks seja justa — mesma lógica, diferentes infraestruturas.

---

## 2. Stack Tecnológica

### 2.1 Compartilhado
| Componente | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java | 21 (LTS) |
| Build | Maven multi-module | 3.9+ |
| Message Broker | Apache Kafka | 3.7 |
| Banco de dados | PostgreSQL | 16 |
| Tracing | OpenTelemetry + Jaeger | latest |
| Métricas | Micrometer + Prometheus | latest |
| Visualização | Grafana | latest |
| Testes de carga | k6 | latest |
| Container | Docker + Docker Compose | - |

### 2.2 Stack Spring
| Componente | Tecnologia |
|---|---|
| Framework | Spring Boot 3.3 |
| Web | Spring WebFlux (reativo) |
| Mensageria | Spring Kafka |
| Persistência | Spring Data JPA + Hibernate |
| Testes | JUnit 5 + Testcontainers + AssertJ |
| Testes de contrato | Spring Cloud Contract |

### 2.3 Stack Micronaut
| Componente | Tecnologia |
|---|---|
| Framework | Micronaut 4.x |
| Web | Micronaut HTTP Server (Netty) |
| Mensageria | Micronaut Kafka |
| Persistência | Micronaut Data JPA |
| Testes | JUnit 5 + Testcontainers + AssertJ |
| Native image | GraalVM 21 |

**Por que WebFlux no Spring e não MVC?** Para tornar a comparação de performance com Micronaut (Netty por padrão) mais justa. Documentado no ADR-005.

---

## 3. Modelo de Domínio

### 3.1 Aggregates

#### Account
```
Account
├── accountId: UUID
├── ownerId: UUID
├── balance: Money         # Value Object — BigDecimal + Currency
├── status: AccountStatus  # ACTIVE | SUSPENDED | CLOSED
└── version: long          # Optimistic locking / event sequence
```

**Eventos gerados por Account:**
- `AccountCreatedEvent`
- `MoneyDepositedEvent`
- `MoneyDebitedEvent`
- `MoneyDebitReversedEvent`
- `MoneyCreditedEvent`
- `MoneyCreditReversedEvent`

#### Transfer
```
Transfer
├── transferId: UUID       # Usado como chave de idempotência
├── sourceAccountId: UUID
├── targetAccountId: UUID
├── amount: Money
├── status: TransferStatus # INITIATED | DEBITING | CREDITING | COMPLETED | FAILED | REVERSED
└── sagaState: SagaState   # Estado interno da saga
```

**Eventos gerados por Transfer:**
- `TransferInitiatedEvent`
- `TransferDebitedEvent`
- `TransferCreditedEvent`
- `TransferCompletedEvent`
- `TransferDebitFailedEvent`
- `TransferCreditFailedEvent`
- `TransferReversedEvent`

### 3.2 Value Objects
- `Money`: BigDecimal amount + Currency code (ISO 4217)
- `AccountId`, `TransferId`: UUID wrappers tipados (evita primitive obsession)

---

## 4. Event Store

### 4.1 Schema

```sql
CREATE TABLE event_store (
    id           BIGSERIAL PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type   VARCHAR(200) NOT NULL,
    event_version INT        NOT NULL DEFAULT 1,
    sequence_num BIGINT      NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payload      JSONB       NOT NULL,
    metadata     JSONB,
    CONSTRAINT uq_aggregate_sequence UNIQUE (aggregate_id, sequence_num)
);

CREATE INDEX idx_event_store_aggregate ON event_store (aggregate_id, sequence_num);
CREATE INDEX idx_event_store_type ON event_store (event_type, occurred_at);
```

### 4.2 Regras de Escrita
- Escrita sempre com `INSERT` — jamais `UPDATE` ou `DELETE`
- `sequence_num` é incrementado por aggregate; violação da constraint `uq_aggregate_sequence` sinaliza conflito de concorrência otimista
- `payload` serializado como JSON com campo `_eventVersion` para suporte a upcasting

### 4.3 Leitura e Replay
```java
// Interface do domínio — sem dependência de framework
public interface EventStore {
    void append(AggregateId aggregateId, List<DomainEvent> events, long expectedVersion);
    List<DomainEvent> loadEvents(AggregateId aggregateId);
    List<DomainEvent> loadEventsSince(AggregateId aggregateId, long fromSequence);
}
```

---

## 5. Saga: Fluxo de Transferência

### 5.1 Escolha: Orquestração

A saga é orquestrada pelo `TransferService` — um saga orchestrator explícito. Escolha documentada no ADR-002.

### 5.2 Máquina de Estados

```
INITIATED
    │
    ▼
DEBITING ──── falha de débito ───► FAILED
    │
    ▼
CREDITING ─── falha de crédito ──► REVERSING_DEBIT ──► REVERSED
    │
    ▼
COMPLETED
```

### 5.3 Sequência de Eventos (happy path)

```
Cliente                TransferService         AccountService (source)    AccountService (target)
   │                        │                          │                          │
   │── POST /transfers ─────►│                          │                          │
   │                        │── DebitCommand ──────────►│                          │
   │                        │◄── MoneyDebitedEvent ─────│                          │
   │                        │── CreditCommand ──────────────────────────────────►│
   │                        │◄── MoneyCreditedEvent ────────────────────────────│
   │                        │── TransferCompletedEvent  │                          │
   │◄── 202 Accepted ───────│                          │                          │
```

### 5.4 Sequência de Eventos (falha no crédito)

```
TransferService         AccountService (source)    AccountService (target)
      │                          │                          │
      │── DebitCommand ──────────►│                          │
      │◄── MoneyDebitedEvent ─────│                          │
      │── CreditCommand ──────────────────────────────────►│
      │◄── CreditFailedEvent ─────────────────────────────│
      │── ReverseDebitCommand ───►│                          │
      │◄── MoneyDebitReversedEvent│                          │
      │── TransferReversedEvent   │                          │
```

---

## 6. API REST

### 6.1 Account Service

```
POST   /accounts                    Cria conta
GET    /accounts/{accountId}        Consulta saldo (via projeção)
GET    /accounts/{accountId}/events Extrato via event replay
POST   /accounts/{accountId}/deposits Realiza depósito
```

### 6.2 Transfer Service

```
POST   /transfers                   Inicia transferência
GET    /transfers/{transferId}      Consulta status da transferência
POST   /transfers/{transferId}/reversals Solicita estorno
```

### 6.3 Idempotência

Todas as operações de escrita aceitam o header `Idempotency-Key`. O valor é persistido na tabela `idempotency_keys` com TTL de 24h. Requisições repetidas com a mesma chave retornam a resposta original sem reprocessar.

```sql
CREATE TABLE idempotency_keys (
    key         VARCHAR(255) PRIMARY KEY,
    response    JSONB        NOT NULL,
    status_code INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL
);
```

---

## 7. Tópicos Kafka

| Tópico | Partições | Retenção | Produzido por | Consumido por |
|---|---|---|---|---|
| `payflow.account.events` | 6 | 7 dias | account-service | transfer-service, projector |
| `payflow.transfer.events` | 6 | 7 dias | transfer-service | projector, notifier |
| `payflow.transfer.commands` | 6 | 1 dia | transfer-service | account-service |
| `payflow.dlq` | 1 | 30 dias | qualquer consumer | - |

**Estratégia de particionamento:** `accountId` como partition key para garantir ordering por conta.

---

## 8. Read Model (Projeções)

```sql
-- Saldo atual por conta
CREATE TABLE account_projections (
    account_id   UUID PRIMARY KEY,
    owner_id     UUID        NOT NULL,
    balance      NUMERIC(19,4) NOT NULL,
    currency     CHAR(3)     NOT NULL,
    status       VARCHAR(20) NOT NULL,
    last_updated TIMESTAMPTZ NOT NULL
);

-- Histórico de movimentações
CREATE TABLE transaction_history (
    id           BIGSERIAL PRIMARY KEY,
    account_id   UUID        NOT NULL,
    transfer_id  UUID,
    type         VARCHAR(30) NOT NULL,  -- DEBIT | CREDIT | DEPOSIT | REVERSAL
    amount       NUMERIC(19,4) NOT NULL,
    balance_after NUMERIC(19,4) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL
);
```

As projeções são atualizadas por um **Projector** que consome `payflow.account.events`. O Projector é stateless e pode ser reiniciado do início do tópico para reconstruir as projeções do zero — isso é demonstrado nos testes.

---

## 9. Observabilidade

### 9.1 Traces Distribuídos
- Instrumentação via OpenTelemetry SDK
- Propagação de contexto via W3C Trace Context headers
- Exportação para Jaeger
- Span obrigatório em: toda entrada de API, toda publicação/consumo de evento Kafka, toda escrita no event store

### 9.2 Métricas (Micrometer)

```
# Métricas de negócio
payflow.transfers.initiated.total        (counter)
payflow.transfers.completed.total        (counter)
payflow.transfers.failed.total           (counter, tags: reason)
payflow.transfers.reversed.total         (counter)
payflow.transfer.duration.seconds        (histogram)

# Métricas de infraestrutura
payflow.event_store.append.duration      (histogram)
payflow.projector.lag.events             (gauge — lag do consumer Kafka)
```

### 9.3 Health Checks
```
GET /health/liveness    → UP se a aplicação está rodando
GET /health/readiness   → UP se Kafka e Postgres estão acessíveis
```

---

## 10. Estratégia de Testes

### 10.1 Pirâmide de Testes

```
         ┌─────────────────┐
         │  E2E (k6)       │  ← Benchmark + smoke test
         ├─────────────────┤
         │  Integration    │  ← Testcontainers: Kafka + Postgres reais
         ├─────────────────┤
         │  Saga Tests     │  ← Todos os caminhos da máquina de estados
         ├─────────────────┤
         │  Unit (Domain)  │  ← Aggregates, Value Objects, regras de negócio
         └─────────────────┘
```

### 10.2 Testes Obrigatórios por Camada

**Domínio (unit):**
- Toda transição de estado do aggregate `Account`
- Toda transição de estado da saga `Transfer`
- Validações de `Money` (negativo, overflow, moeda inválida)
- Idempotência: aplicar o mesmo evento duas vezes não altera o estado

**Saga (integration):**
- Happy path completo
- Falha no débito → transfer FAILED
- Falha no crédito → debit revertido → transfer REVERSED
- Falha durante reversão (retry deve funcionar — saga é idempotente)
- Timeout de etapa (se implementado)

**API (integration com Testcontainers):**
- Idempotency-Key: duas requests iguais retornam mesma resposta
- Conflito de concorrência otimista no event store
- Replay de projeção produz estado igual à query direta

### 10.3 Testes de Contrato
Spring Cloud Contract entre Transfer Service (consumer) e Account Service (provider). Garante que a interface Kafka não quebra silenciosamente.

---

## 11. CI/CD

```yaml
# Fluxo por serviço (Spring e Micronaut separados)
on: [push, pull_request]

jobs:
  test:
    - checkout
    - setup java 21
    - mvn test (com Testcontainers via Docker-in-Docker)
    - upload coverage report (Jacoco → Codecov)

  build:
    needs: test
    - mvn package -DskipTests
    - docker build
    - push to GHCR (apenas em main)

  benchmark: (apenas em main)
    - docker compose up
    - k6 run benchmarks/k6/transfer-load.js
    - commit results to benchmarks/results/
```

---

## 12. README — Estrutura Obrigatória

O README é a primeira impressão. Estrutura mínima:

```markdown
# PayFlow

[badges: CI Spring] [badges: CI Micronaut] [badge: coverage] [badge: Java 21]

> Uma linha: o que é e qual problema demonstra resolver.

## Arquitetura
[diagrama C4 container inline ou link]

## Destaques Técnicos
- Event Sourcing com replay e projeções
- Saga orquestrada com compensating transactions
- Idempotência em todas as operações de escrita
- Spring Boot 3 (WebFlux) vs Micronaut 4 — mesma lógica, stacks diferentes

## Benchmarks
[tabela: startup time, memória RSS, latência p99 — Spring vs Micronaut]

## Como Rodar
\`\`\`bash
git clone ...
docker compose up -d        # Kafka, Postgres, Jaeger, Grafana
./mvnw -pl services/account-spring spring-boot:run
./mvnw -pl services/transfer-spring spring-boot:run
\`\`\`

## Decisões de Arquitetura
[links para ADRs]

## O que ficou de fora (e por quê)
[link para seção no PRD]
```

---

## 13. Cronograma Sugerido (6-8 semanas)

| Semana | Foco | Entregável |
|---|---|---|
| 1 | Setup do monorepo, infra Docker, módulo `shared/domain` | Aggregates + eventos compilando, testes de unidade passando |
| 2 | Event Store + Account Service (Spring) | API de contas funcionando com event sourcing |
| 3 | Transfer Service + Saga (Spring) | Fluxo de transferência end-to-end com rollback |
| 4 | Projector + Read Model + Observabilidade | Projeções funcionando, traces no Jaeger, métricas no Grafana |
| 5 | Port para Micronaut (account + transfer) | Mesma lógica rodando nas duas stacks |
| 6 | Testes de contrato + benchmarks + ADRs | k6 rodando, benchmarks documentados, todos os ADRs escritos |
| 7-8 | Polish: README, diagramas C4, CI/CD completo | Projeto pronto para divulgar |

**Nota sobre a semana 5:** o port para Micronaut deve ser rápido porque todo o domínio está em `shared/domain` sem acoplamento a framework. Se não for rápido, isso indica que a separação não foi bem feita — e é um sinal para corrigir antes de seguir.
