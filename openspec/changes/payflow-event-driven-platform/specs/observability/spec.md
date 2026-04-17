## ADDED Requirements

### Requirement: Traces distribuídos cobrindo o fluxo completo de transferência
O sistema SHALL instrumentar com OpenTelemetry e exportar traces para Jaeger. Spans obrigatórios: toda entrada de API, toda publicação/consumo de evento Kafka e toda escrita no event store.

#### Scenario: Trace de transferência end-to-end visível no Jaeger
- **WHEN** uma transferência é iniciada via POST /transfers e concluída com sucesso
- **THEN** um trace completo é visível no Jaeger, contendo spans de: recebimento do POST, emissão de DebitCommand no Kafka, consumo no AccountService, escrita no event store, emissão de CreditCommand, consumo no AccountService destino, e TransferCompletedEvent

#### Scenario: Propagação de contexto via W3C Trace Context
- **WHEN** o TransferService publica uma mensagem no Kafka
- **THEN** o header `traceparent` (W3C Trace Context) é incluído na mensagem e o AccountService consumidor continua o mesmo trace

### Requirement: Métricas de negócio expostas via Micrometer
O sistema SHALL expor métricas de negócio via Micrometer, coletadas pelo Prometheus e visualizadas no Grafana.

#### Scenario: Contador de transferências iniciadas
- **WHEN** uma transferência é iniciada
- **THEN** o counter `payflow.transfers.initiated.total` é incrementado

#### Scenario: Contador de transferências concluídas
- **WHEN** uma transferência atinge status COMPLETED
- **THEN** o counter `payflow.transfers.completed.total` é incrementado

#### Scenario: Contador de transferências falhas por motivo
- **WHEN** uma transferência atinge status FAILED ou REVERSED
- **THEN** o counter `payflow.transfers.failed.total` é incrementado com tag `reason` (ex: `INSUFFICIENT_FUNDS`, `CREDIT_FAILED`)

#### Scenario: Histogram de duração de transferência
- **WHEN** uma transferência é concluída (sucesso ou falha)
- **THEN** `payflow.transfer.duration.seconds` registra a duração total da saga em segundos

#### Scenario: Gauge de lag do Projector
- **WHEN** o Projector está consumindo eventos do Kafka
- **THEN** `payflow.projector.lag.events` reflete o lag atual do consumer group em número de eventos

### Requirement: Health checks diferenciados liveness vs readiness
O sistema SHALL expor endpoints de health check distintos para liveness (aplicação está rodando) e readiness (dependências externas disponíveis).

#### Scenario: Liveness retorna UP quando aplicação está rodando
- **WHEN** GET /health/liveness é chamado e a aplicação está rodando normalmente
- **THEN** retorna 200 com `status: UP`

#### Scenario: Readiness retorna DOWN quando Kafka ou Postgres está indisponível
- **WHEN** GET /health/readiness é chamado e o Kafka está indisponível
- **THEN** retorna 503 com indicação do componente que está down

#### Scenario: Readiness retorna UP quando todas as dependências estão disponíveis
- **WHEN** GET /health/readiness é chamado com Kafka e Postgres disponíveis
- **THEN** retorna 200 com `status: UP`
