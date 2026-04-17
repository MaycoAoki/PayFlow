## ADDED Requirements

### Requirement: Projeção assíncrona de saldo de conta
O sistema SHALL manter a tabela `account_projections` atualizada de forma assíncrona, consumindo eventos do tópico `payflow.account.events`. O Projector SHALL ser stateless e idempotente.

#### Scenario: Atualização de saldo após depósito
- **WHEN** um `MoneyDepositedEvent` é consumido pelo Projector
- **THEN** o `balance` na tabela `account_projections` é atualizado para o novo valor e `last_updated` é atualizado

#### Scenario: Atualização de saldo após débito de transferência
- **WHEN** um `MoneyDebitedEvent` é consumido pelo Projector
- **THEN** o `balance` na tabela `account_projections` é decrementado pelo valor debitado

#### Scenario: Idempotência do Projector
- **WHEN** o mesmo evento é consumido duas vezes (ex: reprocessamento Kafka)
- **THEN** o estado da projeção permanece o mesmo após o segundo processamento (sem duplicação de ajuste de saldo)

### Requirement: Histórico de movimentações (transaction_history)
O sistema SHALL manter a tabela `transaction_history` com um registro por movimentação, incluindo tipo, valor e saldo após cada operação.

#### Scenario: Registro de movimentação de depósito
- **WHEN** um `MoneyDepositedEvent` é consumido pelo Projector
- **THEN** um registro é inserido em `transaction_history` com `type: DEPOSIT`, `amount`, `balance_after` e `occurred_at`

#### Scenario: Registro de movimentação de débito por transferência
- **WHEN** um `MoneyDebitedEvent` é consumido
- **THEN** um registro é inserido com `type: DEBIT`, `transfer_id` preenchido e `balance_after` correto

### Requirement: Reconstrução de projeções via replay do tópico Kafka
O Projector SHALL ser capaz de reconstruir as projeções do zero ao reiniciar o consumer a partir do offset 0 do tópico `payflow.account.events`.

#### Scenario: Replay completo do tópico
- **WHEN** as tabelas de projeção são truncadas e o Projector é reiniciado no offset 0
- **THEN** após consumir todos os eventos, o estado de `account_projections` e `transaction_history` é idêntico ao estado anterior ao truncamento

#### Scenario: Projeção reconstruída igual a query direta no event store
- **WHEN** o estado reconstruído via projeção é comparado ao estado calculado via replay direto do event store
- **THEN** `balance` e número de transações são idênticos
