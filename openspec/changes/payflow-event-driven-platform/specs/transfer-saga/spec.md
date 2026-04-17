## ADDED Requirements

### Requirement: Iniciar transferência entre contas
O sistema SHALL permitir iniciar uma transferência de valor entre duas contas distintas. A operação SHALL ser assíncrona (retorna 202 Accepted) e idempotente via `transferId` como chave de idempotência.

#### Scenario: Transferência iniciada com sucesso
- **WHEN** um POST é enviado para `/transfers` com `sourceAccountId`, `targetAccountId` e `amount` válidos
- **THEN** o sistema retorna 202 com `transferId` e `status: INITIATED`, e a saga de débito/crédito é iniciada

#### Scenario: Transferência idempotente
- **WHEN** dois POSTs idênticos são enviados para `/transfers` com o mesmo `Idempotency-Key`
- **THEN** o sistema processa apenas o primeiro e retorna a mesma resposta no segundo sem iniciar nova saga

#### Scenario: Transferência para a própria conta
- **WHEN** `sourceAccountId` e `targetAccountId` são iguais
- **THEN** o sistema retorna 400

#### Scenario: Transferência com valor zero ou negativo
- **WHEN** `amount` é zero ou negativo
- **THEN** o sistema retorna 400

### Requirement: Saga orquestrada de transferência — happy path
O `TransferService` SHALL orquestrar o fluxo completo: débito na conta de origem, crédito na conta de destino, e marcação da transferência como `COMPLETED`. Cada etapa SHALL gerar domain events persistidos no event store.

#### Scenario: Happy path completo
- **WHEN** a saga é iniciada para uma transferência com saldo suficiente em ambas as contas
- **THEN** o status evolui: `INITIATED` → `DEBITING` → `CREDITING` → `COMPLETED`, com `MoneyDebitedEvent` e `MoneyCreditedEvent` e `TransferCompletedEvent` persistidos

### Requirement: Compensating transaction por falha no crédito
Quando o crédito na conta de destino falhar, o sistema SHALL automaticamente reverter o débito na conta de origem via compensating transaction, finalizando a transferência com `status: REVERSED`.

#### Scenario: Falha no crédito — reversão automática
- **WHEN** o débito na conta de origem é bem-sucedido e o crédito na conta de destino falha
- **THEN** o sistema emite `ReverseDebitCommand`, persiste `MoneyDebitReversedEvent` na conta de origem e `TransferReversedEvent`, e o status final é `REVERSED`

#### Scenario: Saga idempotente na reversão
- **WHEN** a etapa de reversão de débito é executada mais de uma vez (ex: retry)
- **THEN** o estado final permanece `REVERSED` sem duplicação de eventos ou alteração incorreta de saldo

### Requirement: Falha no débito
Quando o débito na conta de origem falhar (ex: saldo insuficiente), o sistema SHALL marcar a transferência como `FAILED` sem tentar o crédito.

#### Scenario: Saldo insuficiente na conta de origem
- **WHEN** a saga tenta debitar valor maior que o saldo disponível
- **THEN** o sistema persiste `TransferDebitFailedEvent` e o status final é `FAILED`

### Requirement: Consultar status da transferência
O sistema SHALL permitir consultar o status atual de uma transferência a qualquer momento.

#### Scenario: Consulta de transferência em andamento
- **WHEN** um GET é enviado para `/transfers/{transferId}` durante a execução da saga
- **THEN** o sistema retorna 200 com o status atual (ex: `DEBITING`, `CREDITING`)

#### Scenario: Consulta de transferência concluída
- **WHEN** um GET é enviado para `/transfers/{transferId}` após conclusão
- **THEN** o sistema retorna 200 com `status: COMPLETED` ou `REVERSED` ou `FAILED`

### Requirement: Solicitar estorno de transferência concluída
O sistema SHALL permitir solicitar o estorno de uma transferência com `status: COMPLETED`, iniciando uma compensating transaction que reverte débito e crédito originais.

#### Scenario: Estorno de transferência concluída
- **WHEN** um POST é enviado para `/transfers/{transferId}/reversals` para uma transferência `COMPLETED`
- **THEN** o sistema inicia o fluxo de estorno, que reverte crédito e débito com rastreabilidade completa, e o status final é `REVERSED`

#### Scenario: Estorno de transferência não concluída
- **WHEN** um POST é enviado para `/transfers/{transferId}/reversals` para uma transferência `FAILED` ou `REVERSED`
- **THEN** o sistema retorna 422
