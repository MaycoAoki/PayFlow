## ADDED Requirements

### Requirement: Persistência imutável de domain events
O event store SHALL persistir domain events exclusivamente via `INSERT` na tabela `event_store`. Operações de `UPDATE` ou `DELETE` sobre eventos persistidos são proibidas.

#### Scenario: Append de evento com sequence correto
- **WHEN** `EventStore.append()` é chamado com `expectedVersion` correspondente ao último `sequence_num` do aggregate
- **THEN** o evento é persistido com `sequence_num = expectedVersion + 1` e os campos `aggregate_id`, `aggregate_type`, `event_type`, `payload` e `occurred_at` preenchidos

#### Scenario: Conflito de concorrência otimista
- **WHEN** dois processos simultâneos tentam fazer append com o mesmo `expectedVersion`
- **THEN** o segundo recebe erro de conflito (violação de `uq_aggregate_sequence`) e NÃO persiste o evento

### Requirement: Versionamento de schema de eventos
Cada evento SHALL ter um campo `_eventVersion` no `payload` JSONB. O event store SHALL suportar upcasting de eventos em versões antigas para o formato atual durante o carregamento.

#### Scenario: Evento salvo com versão de schema
- **WHEN** um domain event é persistido no event store
- **THEN** o campo `event_version` na tabela e `_eventVersion` no `payload` refletem a versão atual do schema do evento

#### Scenario: Carregamento de evento em versão antiga
- **WHEN** um evento com `_eventVersion: 1` é carregado e a versão atual é 2
- **THEN** o upcastor converte o payload para o formato v2 antes de retornar o domain event

### Requirement: Replay de eventos por aggregate
O sistema SHALL suportar carregamento de todos os eventos de um aggregate ordenados por `sequence_num`, bem como carregamento a partir de uma sequência específica.

#### Scenario: Carregamento completo de um aggregate
- **WHEN** `EventStore.loadEvents(aggregateId)` é chamado
- **THEN** retorna lista de domain events ordenados por `sequence_num` ASC, desde o primeiro evento

#### Scenario: Carregamento incremental a partir de sequência
- **WHEN** `EventStore.loadEventsSince(aggregateId, fromSequence)` é chamado com `fromSequence = 5`
- **THEN** retorna apenas eventos com `sequence_num > 5`

#### Scenario: Reconstrução de estado via replay
- **WHEN** todos os eventos de um aggregate são carregados e aplicados ao aggregate vazio via `apply(event)`
- **THEN** o estado resultante é idêntico ao estado atual projetado do aggregate

### Requirement: Interface de domínio sem acoplamento a framework
A interface `EventStore` SHALL ser definida no módulo `shared/domain` sem dependência de Spring, Micronaut ou qualquer framework. As implementações concretas ficam nos serviços de infraestrutura.

#### Scenario: Módulo shared/domain sem dependências de framework
- **WHEN** o `pom.xml` de `shared/domain` é inspecionado
- **THEN** não há dependências de `spring-*`, `micronaut-*` ou qualquer framework web/ORM
