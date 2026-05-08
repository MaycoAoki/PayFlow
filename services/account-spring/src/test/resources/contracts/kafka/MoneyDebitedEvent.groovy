import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "MoneyDebitedEvent published to payflow.account.events when debit succeeds"

    label 'moneyDebited'

    input {
        triggeredBy('triggerMoneyDebited()')
    }

    outputMessage {
        sentTo 'payflow.account.events'
        body([
            _eventType   : 'MoneyDebitedEvent',
            _eventVersion: 1,
            accountId    : $(anyNonBlankString()),
            transferId   : $(anyNonBlankString()),
            amount       : [
                amount  : $(regex('[0-9]+\\.[0-9]{2}')),
                currency: 'BRL'
            ],
            occurredAt   : $(anyNonBlankString())
        ])
        headers {
            messagingContentType(applicationJson())
        }
    }
}
