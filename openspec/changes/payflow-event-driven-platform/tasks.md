## 1. Setup do Monorepo e Infraestrutura

- [x] 1.1 Criar estrutura de diretórios do monorepo (`shared/domain`, `services/`, `infra/`, `benchmarks/`, `docs/`)
- [x] 1.2 Criar `pom.xml` raiz com Maven multi-module e gerenciamento de versões (Java 21, Spring Boot 3.3, Micronaut 4)
- [x] 1.3 Criar `pom.xml` do módulo `shared/domain` sem dependências de framework
- [x] 1.4 Criar `infra/docker-compose.yml` com Kafka 3.7, PostgreSQL 16, Jaeger, Prometheus e Grafana
- [x] 1.5 Configurar criação automática dos tópicos Kafka (`payflow.account.events`, `payflow.transfer.events`, `payflow.transfer.commands`, `payflow.dlq`) via `kafka-topics` no startup
- [x] 1.6 Criar `infra/grafana/dashboards/payflow-overview.json` com painéis de métricas de transferência

## 2. Domínio Compartilhado (shared/domain)

- [x] 2.1 Criar value objects: `AccountId`, `TransferId` (UUID wrappers tipados) e `Money` (BigDecimal + Currency ISO 4217)
- [x] 2.2 Criar aggregate `Account` com campos `accountId`, `ownerId`, `balance`, `status`, `version` e método `apply(event)`
- [x] 2.3 Criar domain events de Account: `AccountCreatedEvent`, `MoneyDepositedEvent`, `MoneyDebitedEvent`, `MoneyDebitReversedEvent`, `MoneyCreditedEvent`, `MoneyCreditReversedEvent`
- [x] 2.4 Criar aggregate `Transfer` com máquina de estados (`INITIATED → DEBITING → CREDITING → COMPLETED / FAILED / REVERSED`) e campo `sagaState`
- [x] 2.5 Criar domain events de Transfer: `TransferInitiatedEvent`, `TransferDebitedEvent`, `TransferCreditedEvent`, `TransferCompletedEvent`, `TransferDebitFailedEvent`, `TransferCreditFailedEvent`, `TransferReversedEvent`
- [x] 2.6 Criar interface `EventStore` com métodos `append()`, `loadEvents()` e `loadEventsSince()`
- [x] 2.7 Escrever testes unitários para todas as transições de estado do aggregate `Account`
- [x] 2.8 Escrever testes unitários para todas as transições de estado da saga `Transfer` (happy path + falha débito + falha crédito + reversão)
- [x] 2.9 Escrever testes unitários para `Money`: valor negativo, overflow, moeda inválida
- [x] 2.10 Escrever testes de idempotência: aplicar o mesmo evento duas vezes não altera o estado

## 3. Event Store (PostgreSQL)

- [x] 3.1 Criar scripts SQL de migração: tabelas `event_store` e `idempotency_keys` com índices
- [x] 3.2 Implementar `PostgresEventStore` (no módulo Spring ou como módulo compartilhado) com `append()` usando INSERT e constraint `uq_aggregate_sequence`
- [x] 3.3 Implementar suporte a upcasting de eventos com campo `_eventVersion` no payload JSONB
- [x] 3.4 Implementar `loadEvents()` e `loadEventsSince()` ordenados por `sequence_num`
- [x] 3.5 Escrever testes de integração (Testcontainers + PostgreSQL) para conflito de concorrência otimista
- [x] 3.6 Escrever teste de integração para replay: carregar todos os eventos e reconstruir estado igual ao estado atual

## 4. Account Service — Implementação Spring Boot

- [x] 4.1 Criar módulo `services/account-spring` com Spring Boot 3.3 + WebFlux + Spring Data JPA
- [x] 4.2 Implementar `AccountController` (WebFlux) com endpoints: `POST /accounts`, `GET /accounts/{id}`, `GET /accounts/{id}/events`, `POST /accounts/{id}/deposits`
- [x] 4.3 Implementar `AccountService` com lógica de criação de conta, depósito e consulta
- [x] 4.4 Implementar repositório de projeção (`account_projections`, `transaction_history`) via Spring Data JPA
- [x] 4.5 Implementar `IdempotencyFilter` para verificar `Idempotency-Key` header antes de processar escritas
- [ ] 4.6 Configurar OpenTelemetry: spans em entradas de API e escritas no event store
- [x] 4.7 Configurar Micrometer para expor health checks em `/health/liveness` e `/health/readiness`
- [x] 4.8 Escrever testes de integração (Testcontainers) para todos os cenários de `account-management/spec.md`
- [x] 4.9 Escrever teste de integração para idempotência: duas requests com mesmo `Idempotency-Key` retornam mesma resposta

## 5. Transfer Service — Implementação Spring Boot

- [x] 5.1 Criar módulo `services/transfer-spring` com Spring Boot 3.3 + WebFlux + Spring Kafka
- [x] 5.2 Implementar `TransferController` com endpoints: `POST /transfers`, `GET /transfers/{id}`
- [x] 5.3 Implementar `TransferSagaOrchestrator` que emite `DebitCommand` e aguarda `MoneyDebitedEvent`/`TransferDebitFailedEvent`, depois emite `CreditCommand` ou `ReverseDebitCommand`
- [x] 5.4 Implementar consumer Kafka para eventos de Account e publisher de comandos no tópico `payflow.transfer.commands`
- [ ] 5.5 Configurar propagação de W3C Trace Context nos headers Kafka para rastreamento end-to-end
- [x] 5.6 Configurar métricas de negócio: `payflow.transfers.initiated.total`, `payflow.transfers.completed.total`, `payflow.transfers.failed.total` (com tag `reason`), `payflow.transfers.reversed.total`
- [x] 5.7 Escrever testes de integração para os cenários de happy path, falha débito, falha crédito e reversão
- [x] 5.8 Escrever testes de saga com Testcontainers: todos os caminhos da máquina de estados (INITIATED→DEBITING→COMPLETED, INITIATED→FAILED, DEBITING→REVERSING→REVERSED)

## 6. Event Projector — Implementação Spring Boot

- [ ] 6.1 Criar consumer Kafka no `account-spring` (ou serviço separado) que consome `payflow.account.events`
- [ ] 6.2 Implementar `AccountProjector` que atualiza `account_projections` e insere em `transaction_history` para cada tipo de evento
- [ ] 6.3 Implementar idempotência do Projector (ex: verificar se evento já foi processado antes de atualizar)
- [ ] 6.4 Configurar gauge `payflow.projector.lag.events` para monitorar lag do consumer group
- [ ] 6.5 Escrever teste de integração que trunca projeções, reinicia o consumer no offset 0 e verifica que o estado reconstruído é igual ao estado original

## 7. Port para Micronaut

- [ ] 7.1 Criar módulo `services/account-micronaut` com Micronaut 4 + Netty + Micronaut Data JPA
- [ ] 7.2 Implementar todos os endpoints de `AccountController` equivalentes ao Spring
- [ ] 7.3 Criar módulo `services/transfer-micronaut` com Micronaut 4 + Micronaut Kafka
- [ ] 7.4 Implementar `TransferSagaOrchestrator` equivalente ao Spring
- [ ] 7.5 Configurar OpenTelemetry e Micrometer no Micronaut (mesmas métricas e spans)
- [ ] 7.6 Configurar GraalVM native image para `account-micronaut` e `transfer-micronaut` com `reflect-config.json`
- [ ] 7.7 Reusar os mesmos testes de integração do domínio (shared/domain) para validar comportamento idêntico
- [ ] 7.8 Verificar que o tempo de port (semana 5) foi rápido — se não foi, revisar acoplamento no shared/domain

## 8. Testes de Contrato e Benchmarks

- [ ] 8.1 Definir contratos Spring Cloud Contract entre Transfer Service (consumer) e Account Service (provider) para os eventos Kafka (`MoneyDebitedEvent`, `TransferDebitFailedEvent`, etc.)
- [ ] 8.2 Executar testes de contrato no CI e garantir que divergências de schema são detectadas automaticamente
- [ ] 8.3 Criar script k6 em `benchmarks/k6/transfer-load.js` para teste de carga no fluxo de transferência
- [ ] 8.4 Executar benchmarks e documentar em `benchmarks/results/README.md`: startup time, memória RSS e latência p99 para Spring Boot e Micronaut (com contexto de hardware e JVM flags)

## 9. Documentação Arquitetural (ADRs e C4)

- [ ] 9.1 Escrever `docs/adr/ADR-001-event-sourcing.md`: justificativa para Event Sourcing no domínio financeiro
- [ ] 9.2 Escrever `docs/adr/ADR-002-saga-orchestration.md`: orquestração vs coreografia — trade-offs documentados
- [ ] 9.3 Escrever `docs/adr/ADR-003-kafka-choice.md`: Kafka vs RabbitMQ vs Redis Streams
- [ ] 9.4 Escrever `docs/adr/ADR-004-event-versioning.md`: estratégia de versionamento e upcasting
- [ ] 9.5 Escrever `docs/adr/ADR-005-spring-vs-micronaut.md`: análise pós-implementação com dados reais dos benchmarks
- [ ] 9.6 Criar diagramas C4: `docs/architecture/c4-context.md`, `c4-container.md`, `c4-component-transfer.md`

## 10. CI/CD e Polish Final

- [ ] 10.1 Criar `.github/workflows/ci-spring.yml` com jobs de test (Testcontainers via Docker-in-Docker), build e push para GHCR
- [ ] 10.2 Criar `.github/workflows/ci-micronaut.yml` equivalente, incluindo native image build
- [ ] 10.3 Configurar step de benchmark no CI (apenas em main): `docker compose up`, `k6 run`, commit dos resultados
- [ ] 10.4 Configurar upload de cobertura Jacoco para Codecov e adicionar badge no README
- [ ] 10.5 Escrever `README.md` com estrutura obrigatória: badges, arquitetura, destaques técnicos, benchmarks, como rodar, ADRs, o que ficou de fora
- [ ] 10.6 Validar que `docker compose up -d` + comandos do README executa tudo em menos de 5 minutos
