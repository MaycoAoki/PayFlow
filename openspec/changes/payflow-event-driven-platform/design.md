## Context

PayFlow é um projeto de portfólio construído do zero para demonstrar maturidade arquitetural em engenharia de software Java/Kotlin. O contexto é um sistema de transferências financeiras — domínio onde Event Sourcing faz sentido real (auditoria imutável, replay de estado, rastreabilidade completa). O projeto implementa a mesma lógica de negócio em duas stacks JVM (Spring Boot 3 com WebFlux e Micronaut 4 com Netty) para permitir comparação honesta e documentada de trade-offs.

Não há sistema legado — é greenfield. As constraints são: Java 21, Maven multi-module, infraestrutura local via Docker Compose.

## Goals / Non-Goals

**Goals:**
- Demonstrar Event Sourcing aplicado a um domínio financeiro com replay, versionamento de schema e projeções CQRS
- Implementar saga de transferência orquestrada com compensating transactions e idempotência completa
- Comparar Spring Boot 3 (WebFlux) vs Micronaut 4 (Netty) usando a mesma lógica de domínio — benchmarks documentados
- Produzir observabilidade real: traces distribuídos end-to-end, métricas de negócio, dashboards operacionais
- Gerar ADRs que demonstrem pensamento arquitetural sênior
- README que qualquer engenheiro executa em menos de 5 minutos

**Non-Goals:**
- Interface gráfica ou frontend
- Autenticação real de usuários (mock JWT é suficiente)
- Integração com bancos reais ou PSPs
- Multi-tenancy
- Sharding do event store
- LGPD compliance (arquitetura suporta, implementação não inclusa)
- GraalVM native para Spring (custo desproporcional ao objetivo)

## Decisions

### D1: Domínio em módulo Java puro, sem framework

O módulo `shared/domain` contém aggregates, domain events, value objects e a saga sem nenhuma dependência de framework. Os serviços Spring e Micronaut são adapters (ports & adapters / hexagonal).

**Por quê:** garante que a comparação Spring vs Micronaut seja justa — mesma lógica, diferentes infraestruturas. Se o port para Micronaut for lento, o problema é no design do domínio, não na stack. Também demonstra conhecimento de arquitetura hexagonal.

**Alternativa considerada:** domínio com anotações Spring (ex: `@Entity`) — descartado porque acopla ao framework e invalida o argumento da comparação.

### D2: Saga por orquestração, não coreografia

O `TransferService` é o saga orchestrator explícito. Ele emite comandos e aguarda eventos de resposta dos serviços de conta.

**Por quê:** em um sistema financeiro, rastreabilidade é crítica. Um orchestrator centralizado torna o estado da saga sempre consultável e o fluxo de compensação explícito e auditável. Coreografia distribuiria a lógica por múltiplos consumidores, tornando o debug e o teste de edge cases mais difícil.

**Alternativa considerada:** coreografia via eventos — documentada no ADR-002 como trade-off de escalabilidade vs observabilidade.

### D3: PostgreSQL como Event Store, Kafka como bus de integração

Eventos de domínio são persistidos no PostgreSQL (event store) via `INSERT` imutável. O Kafka é usado para integração entre serviços (projeções, notificações, saga commands).

**Por quê:** PostgreSQL garante consistência transacional ao gravar eventos e emitir no Kafka (outbox pattern implícito). Kafka provê escalabilidade e replay das projeções. Usar um banco de eventos especializado (EventStoreDB) seria overkill para o objetivo de portfólio.

**Alternativa considerada:** Kafka como event store primário — descartado pela complexidade de garantir ordering e replay granular por aggregate.

### D4: Spring WebFlux (reativo) no lugar de Spring MVC

**Por quê:** para tornar a comparação de throughput e latência com Micronaut (Netty por padrão) mais justa. Spring MVC com thread-per-request teria desvantagem estrutural em benchmarks de concorrência. Documentado no ADR-005.

### D5: Idempotência via tabela `idempotency_keys` com TTL 24h

Toda operação de escrita aceita `Idempotency-Key` header. A resposta original é armazenada e retornada em retries sem reprocessamento.

**Por quê:** requisito crítico em fintech. Implementar no nível da API (antes da lógica de negócio) é a abordagem mais robusta e testável.

### D6: Estratégia de testes em 4 camadas

Unit (domínio puro) → Saga (todos os caminhos da máquina de estados) → Integration (Testcontainers com Kafka e Postgres reais) → E2E/benchmark (k6).

**Por quê:** testes de domínio são rápidos e cobrem a lógica central. Testes de saga explicitam todos os caminhos de falha. Integration com Testcontainers evita mocks que mascaram problemas de integração. k6 gera os benchmarks documentados.

## Risks / Trade-offs

- **Complexidade do Kafka outbox sem biblioteca dedicada** → Mitigação: sequência simples de `INSERT` no event store + publicação Kafka em transação local (Spring `@Transactional` / Micronaut `@Transactional`); falhas são toleráveis em portfólio demo
- **Drift entre implementações Spring e Micronaut** → Mitigação: domínio compartilhado via `shared/domain`; testes de contrato (Spring Cloud Contract) detectam divergências de API/evento
- **Benchmark influenced by environment** → Mitigação: documentar hardware, JVM flags, número de iterações k6; benchmarks em `benchmarks/results/README.md` com contexto explícito
- **GraalVM native build para Micronaut pode falhar com reflexão** → Mitigação: configurar `reflect-config.json` progressivamente; CI falha rápido se native image quebrar

## Migration Plan

Projeto greenfield — não há migração. Ordem de implementação sugerida pela SPEC:

1. Setup do monorepo e infra Docker
2. `shared/domain` com aggregates e testes de unidade
3. Event Store + Account Service (Spring)
4. Transfer Service + Saga (Spring)
5. Projector + Read Model + Observabilidade
6. Port para Micronaut
7. Testes de contrato + benchmarks + ADRs
8. Polish: README, C4, CI/CD

## Open Questions

- Usar Outbox Pattern explícito (tabela `outbox`) ou aceitar at-least-once com Kafka direto na transação? → Para portfólio, transação direta é aceitável; mencionar outbox no README como extensão natural
- Micronaut native image: incluir no CI ou apenas documentar como passo manual? → Incluir no CI é mais impressionante, mas pode tornar o build lento; decidir na semana 5
