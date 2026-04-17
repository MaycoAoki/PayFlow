## ADDED Requirements

### Requirement: Criar conta com saldo inicial
O sistema SHALL permitir a criação de uma conta bancária com `ownerId` e saldo inicial (podendo ser zero). A criação SHALL persistir um `AccountCreatedEvent` no event store e retornar o `accountId` gerado.

#### Scenario: Criação de conta com saldo zero
- **WHEN** um POST é enviado para `/accounts` com `ownerId` válido e `initialBalance: 0`
- **THEN** o sistema retorna 201 com o `accountId` e persiste `AccountCreatedEvent` no event store

#### Scenario: Criação de conta com saldo inicial positivo
- **WHEN** um POST é enviado para `/accounts` com `ownerId` válido e `initialBalance: 1000.00`
- **THEN** o sistema retorna 201, persiste `AccountCreatedEvent` e um `MoneyDepositedEvent` no event store

#### Scenario: Rejeição de saldo inicial negativo
- **WHEN** um POST é enviado para `/accounts` com `initialBalance: -1`
- **THEN** o sistema retorna 400 com mensagem de erro indicando valor inválido

### Requirement: Consultar saldo atual via projeção
O sistema SHALL retornar o saldo atual de uma conta via projeção (`account_projections`), não via query direta no event store. A resposta SHALL refletir o estado mais recente projetado a partir dos eventos.

#### Scenario: Consulta de conta existente
- **WHEN** um GET é enviado para `/accounts/{accountId}`
- **THEN** o sistema retorna 200 com `accountId`, `balance`, `currency` e `status` da projeção

#### Scenario: Consulta de conta inexistente
- **WHEN** um GET é enviado para `/accounts/{accountId}` com ID inexistente
- **THEN** o sistema retorna 404

### Requirement: Consultar histórico via event replay
O sistema SHALL retornar o histórico completo de movimentações de uma conta via replay dos domain events armazenados no event store, ordenados por `sequence_num`.

#### Scenario: Extrato de conta com movimentações
- **WHEN** um GET é enviado para `/accounts/{accountId}/events`
- **THEN** o sistema retorna 200 com lista de eventos ordenados por `sequence_num`, incluindo tipo, valor e timestamp de cada evento

#### Scenario: Extrato de conta sem movimentações
- **WHEN** um GET é enviado para `/accounts/{accountId}/events` para uma conta recém-criada
- **THEN** o sistema retorna 200 com lista contendo apenas o `AccountCreatedEvent`

### Requirement: Realizar depósito em conta
O sistema SHALL permitir crédito em uma conta ativa. O depósito SHALL ser idempotente via `Idempotency-Key` e persistir um `MoneyDepositedEvent` no event store.

#### Scenario: Depósito com sucesso
- **WHEN** um POST é enviado para `/accounts/{accountId}/deposits` com `amount` positivo
- **THEN** o sistema retorna 200, persiste `MoneyDepositedEvent` e atualiza a projeção com novo saldo

#### Scenario: Depósito idempotente
- **WHEN** dois POSTs idênticos são enviados para `/accounts/{accountId}/deposits` com o mesmo `Idempotency-Key`
- **THEN** o sistema processa apenas o primeiro e retorna a mesma resposta no segundo sem reprocessar

#### Scenario: Depósito em conta suspensa
- **WHEN** um POST é enviado para `/accounts/{accountId}/deposits` em conta com `status: SUSPENDED`
- **THEN** o sistema retorna 422 com mensagem indicando que a conta está suspensa
